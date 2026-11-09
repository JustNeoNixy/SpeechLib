package dev.speechlib.example;

import dev.speechlib.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Connect pressed()/released() to your mod's keybinds and close() to client shutdown. */
public final class VoiceCommands implements AutoCloseable {
    private final SpeechEngine engine=SpeechLib.create(SpeechProfile.GPU_SMALL);
    private final MicrophoneCapture microphone=new MicrophoneCapture(engine);
    private final Executor mainThread;
    private final Consumer<String> transcript;
    private final Consumer<Throwable> error;
    public VoiceCommands(Executor mainThread,Consumer<String> transcript,Consumer<Throwable> error) {
        this.mainThread=mainThread;this.transcript=transcript;this.error=error;
    }
    public CompletableFuture<Void> prepare(){return engine.initialize();}
    public void pressed(){
        microphone.start("en").whenComplete((text,failure)->mainThread.execute(()->{
            if(failure==null)transcript.accept(text);else error.accept(failure);
        }));
    }
    public void released(){microphone.finish();}
    @Override public void close(){microphone.close();engine.close();}
}
