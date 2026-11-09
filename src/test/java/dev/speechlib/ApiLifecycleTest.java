package dev.speechlib;

import java.nio.file.*;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ApiLifecycleTest {
    @TempDir Path directory;
    @Test void handlesAndRecordersAreLazyAndRejectUseAfterClose() {
        Path cache=directory.resolve("not-created");
        var engine=SpeechLib.builder().cacheDirectory(cache).build();
        var microphone=new MicrophoneCapture(engine);
        assertFalse(Files.exists(cache));assertFalse(engine.isReady());assertFalse(microphone.isBusy());
        assertThrows(CompletionException.class,()->microphone.start("en").join());
        assertFalse(Files.exists(cache));
        microphone.close();engine.close();engine.close();
        assertThrows(CompletionException.class,()->engine.initialize().join());
        assertThrows(CompletionException.class,()->engine.transcribe(new float[16000],"en").join());
        assertThrows(CompletionException.class,()->microphone.start("en").join());
    }
    @Test void invalidAudioFailsBeforeDownloadingOrLoadingNativeCode() {
        Path cache=directory.resolve("not-created");
        try(var engine=SpeechLib.builder().cacheDirectory(cache).build()) {
            assertThrows(CompletionException.class,()->engine.transcribe(null,"en").join());
            assertThrows(CompletionException.class,()->engine.transcribe(new float[100],"en").join());
            assertThrows(CompletionException.class,()->engine.transcribe(new float[AudioSamples.MAX_SAMPLES+1],"en").join());
            var audio=new float[16000];audio[42]=Float.NaN;
            assertThrows(CompletionException.class,()->engine.transcribe(audio,"en").join());
            audio[42]=2;
            assertThrows(CompletionException.class,()->engine.transcribe(audio,"en").join());
            audio[42]=0;
            assertThrows(CompletionException.class,()->engine.transcribe(audio,"english").join());
            assertFalse(Files.exists(cache));
        }
    }
}
