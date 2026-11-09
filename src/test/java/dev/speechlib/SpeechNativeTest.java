package dev.speechlib;

import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="speechlib.native.tests",matches="true")
class SpeechNativeTest {
    @TempDir Path cache;
    @Test void sharedCpuEngineKeepsOtherOwnersAliveAndCancelsOnlyTheClosedOwnersJobs()throws Exception {
        var first=SpeechLib.builder().cacheDirectory(cache).build();
        try(var second=SpeechLib.builder().cacheDirectory(cache).build()) {
            first.initialize().get(120,TimeUnit.SECONDS);second.initialize().get(120,TimeUnit.SECONDS);
            assertEquals(SpeechBackend.CPU,second.backend());
            assertEquals(SpeechProfile.CPU,second.activeProfile());
            var original=samples();
            var obsolete=first.transcribe(original,"en");
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(!first.isRecognizing() && System.nanoTime()<until)Thread.sleep(5);
            assertTrue(first.isRecognizing());
            var next=second.transcribe(original,"en");first.close();
            assertTrue(obsolete.isCancelled());assertFalse(first.isReady());
            assertWords(next.get(90,TimeUnit.SECONDS));assertTrue(second.isReady());
            assertEquals("",second.transcribe(new float[16000],"en").get(10,TimeUnit.SECONDS));
            var full=new float[AudioSamples.MAX_SAMPLES];System.arraycopy(original,0,full,full.length-original.length,original.length);
            assertWords(second.transcribe(full,"en").get(90,TimeUnit.SECONDS));
        } finally {first.close();}
    }
    @Test void disabledGpuFallsBackBeforeDownloadingGpuWeights()throws Exception {
        String previous=System.getProperty("speechlib.gpu.disabled");System.setProperty("speechlib.gpu.disabled","true");
        try(var engine=SpeechLib.builder().cacheDirectory(cache).profile(SpeechProfile.GPU_SMALL).build()) {
            engine.initialize().get(120,TimeUnit.SECONDS);
            assertTrue(engine.isReady());assertEquals(SpeechBackend.CPU,engine.backend());
            assertEquals(SpeechProfile.CPU,engine.activeProfile());assertFalse(java.nio.file.Files.exists(cache.resolve("gpu-models")));
        } finally {if(previous==null)System.clearProperty("speechlib.gpu.disabled");else System.setProperty("speechlib.gpu.disabled",previous);}
    }
    @Test @EnabledIfSystemProperty(named="speechlib.gpu.tests",matches="true")
    void gpuModelsRunOnTheRealBackendAndRecoverAfterCancellation()throws Exception {
        for(var profile:List.of(SpeechProfile.GPU_BASE,SpeechProfile.GPU_SMALL,SpeechProfile.GPU_TURBO)) {
            try(var engine=SpeechLib.builder().cacheDirectory(cache).profile(profile).build()) {
                engine.initialize().get(180,TimeUnit.SECONDS);
                assertNotEquals(SpeechBackend.CPU,engine.backend(),engine.status());assertEquals(profile,engine.activeProfile());
                assertWords(engine.transcribe(samples(),"en").get(60,TimeUnit.SECONDS));
                var full=new float[AudioSamples.MAX_SAMPLES];var original=samples();
                System.arraycopy(original,0,full,full.length-original.length,original.length);
                assertWords(engine.transcribe(full,"en").get(60,TimeUnit.SECONDS));
                var cancelled=engine.transcribe(full,"en");Thread.sleep(50);cancelled.cancel(false);
                engine.whenIdle().get(30,TimeUnit.SECONDS);assertTrue(cancelled.isCancelled());
                assertWords(engine.transcribe(original,"en").get(60,TimeUnit.SECONDS));
            }
        }
    }
    static void assertWords(String text){text=text.toLowerCase(Locale.ROOT);assertTrue(text.contains("diamond") && text.contains("sword"),text);}
    static float[] samples()throws Exception {
        try(var input=SpeechNativeTest.class.getResourceAsStream("/audio/diamond-sword.wav");var audio=AudioSystem.getAudioInputStream(new BufferedInputStream(input))) {
            return AudioSamples.fromPcm16(audio.readAllBytes());
        }
    }
}
