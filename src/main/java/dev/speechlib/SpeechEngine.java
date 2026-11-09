package dev.speechlib;

import java.util.concurrent.CompletableFuture;

public interface SpeechEngine extends AutoCloseable {
    /** Downloads verified assets and initializes asynchronously. Never join this on the game thread. */
    CompletableFuture<Void> initialize();
    /** Transcribes 0.2 to 12 seconds of mono 16 kHz samples in [-1,1]. Cancel the future to abort this job. */
    CompletableFuture<String> transcribe(float[] samples, String language);
    String status();
    boolean isReady();
    boolean isRecognizing();
    int cancelledJobs();
    CompletableFuture<Void> whenIdle();
    default SpeechProfile activeProfile() { return SpeechProfile.CPU; }
    default SpeechBackend backend() { return SpeechBackend.CPU; }
    default String device() { return "CPU"; }
    /** Releases this caller's ownership and cancels its jobs without stopping other mods. */
    @Override void close();
}
