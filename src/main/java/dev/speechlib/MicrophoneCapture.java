package dev.speechlib;

import javax.sound.sampled.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Optional push-to-talk recorder. No device is opened before start(). Does not own the speech engine. */
public final class MicrophoneCapture implements AutoCloseable {
    private static final AudioFormat FORMAT = new AudioFormat(16000,16,1,true,false);
    private final SpeechEngine engine;
    private volatile Capture current;
    private boolean closed;
    private static final class Capture {
        final CompletableFuture<String> result = new CompletableFuture<>();
        volatile boolean recording = true;
        volatile TargetDataLine line;
        volatile CompletableFuture<String> request;
        void cancel() {
            recording=false;
            var active=line; if (active != null) active.close();
            var pending=request; if (pending != null) pending.cancel(false);
        }
    }
    public MicrophoneCapture(SpeechEngine engine) { this.engine=Objects.requireNonNull(engine); }
    /** Empty string selects the system default microphone. */
    public static List<String> microphones() {
        var devices=new ArrayList<String>(); devices.add("");
        var info=new DataLine.Info(TargetDataLine.class,FORMAT);
        for (var mixer:AudioSystem.getMixerInfo()) if(AudioSystem.getMixer(mixer).isLineSupported(info)) devices.add(mixer.getName());
        return List.copyOf(devices);
    }
    public boolean isRecording() { var capture=current;return capture!=null && capture.recording && !capture.result.isDone(); }
    public boolean isBusy() { var capture=current;return capture!=null && !capture.result.isDone(); }
    public CompletableFuture<String> start(String language) { return start(language,"",()->{},text->{}); }
    /** Callbacks run on a recording worker. Call finish() when the player releases their speak key. */
    public synchronized CompletableFuture<String> start(String language,String microphone,Runnable processing,Consumer<String> status) {
        Objects.requireNonNull(microphone);Objects.requireNonNull(processing);Objects.requireNonNull(status);
        if(closed) return CompletableFuture.failedFuture(new IllegalStateException("Microphone capture is closed"));
        if(isBusy()) return CompletableFuture.failedFuture(new IllegalStateException("A recording is already active"));
        if(!engine.isReady()) return CompletableFuture.failedFuture(new IllegalStateException(engine.status()));
        var capture=new Capture();current=capture;
        capture.result.whenComplete((value,error)->{if(capture.result.isCancelled())capture.cancel();});
        Thread.ofVirtual().name("speechlib-microphone").start(()->{
            try {
                var info=new DataLine.Info(TargetDataLine.class,FORMAT);
                TargetDataLine active;
                if(microphone.isBlank()) active=(TargetDataLine)AudioSystem.getLine(info);
                else {
                    var mixer=Arrays.stream(AudioSystem.getMixerInfo()).filter(m->m.getName().equals(microphone)).findFirst()
                        .orElseThrow(()->new IOException("Selected microphone not found"));
                    active=(TargetDataLine)AudioSystem.getMixer(mixer).getLine(info);
                }
                byte[] pcm;
                try(active) {
                    capture.line=active;
                    if(capture.result.isCancelled())return;
                    active.open(FORMAT);active.start();status.accept("Listening");
                    var audio=new ByteArrayOutputStream();byte[] buffer=new byte[1024];
                    while(capture.recording && !capture.result.isCancelled() && audio.size()<AudioSamples.MAX_SAMPLES*2) {
                        int count=active.read(buffer,0,Math.min(buffer.length,AudioSamples.MAX_SAMPLES*2-audio.size()));
                        if(count>0)audio.write(buffer,0,count);
                    }
                    pcm=audio.toByteArray();
                } finally {capture.line=null;capture.recording=false;}
                if(capture.result.isCancelled())return;
                if(pcm.length<6400)throw new IOException("Record at least 0.2 seconds of speech");
                processing.run();status.accept("Recognizing locally");
                capture.request=engine.transcribe(AudioSamples.fromPcm16(pcm),language);
                if(capture.result.isCancelled()){capture.request.cancel(false);return;}
                capture.request.whenComplete((answer,error)->{
                    if(error==null)capture.result.complete(answer);else capture.result.completeExceptionally(error);
                });
            } catch(Exception error){capture.result.completeExceptionally(error);}
            finally {capture.recording=false;}
        });
        return capture.result;
    }
    public void finish(){var capture=current;if(capture!=null)capture.recording=false;}
    public void cancel(){var capture=current;if(capture!=null)capture.result.cancel(false);}
    @Override public synchronized void close(){closed=true;cancel();}
}
