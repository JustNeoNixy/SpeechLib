package dev.speechlib.internal;

import dev.speechlib.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Shares model contexts while retaining per-caller cancellation and lifetime. */
public final class EnginePool {
    private EnginePool() {}
    private record Key(Path cache, SpeechProfile profile) {}
    private static final Map<Key, Shared> ENGINES = new HashMap<>();
    private static final class Shared {
        final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
        final SpeechEngine engine;
        int references;
        Shared(Key key) { engine = new WhisperSession(key.cache(), key.profile(), this::progress); }
        void progress(String text) {
            for (var listener : listeners) try { listener.accept(text); }
            catch (RuntimeException error) { System.getLogger("SpeechLib").log(System.Logger.Level.WARNING, "Progress callback failed", error); }
        }
    }
    public static synchronized SpeechEngine acquire(Path cache, SpeechProfile profile, Consumer<String> progress) {
        var key = new Key(cache, profile); var shared = ENGINES.computeIfAbsent(key, Shared::new);
        shared.references++; shared.listeners.add(progress); return new Handle(key, shared, progress);
    }
    private static final class Handle implements SpeechEngine {
        private final Key key;
        private final Shared shared;
        private final Consumer<String> progress;
        private final Set<CompletableFuture<?>> jobs = new HashSet<>();
        private volatile boolean closed;
        Handle(Key key, Shared shared, Consumer<String> progress) { this.key=key;this.shared=shared;this.progress=progress; }
        private synchronized <T> CompletableFuture<T> track(CompletableFuture<T> inner, boolean cancelInner) {
            if (closed) { if (cancelInner) inner.cancel(false); return unavailable(); }
            var result = new CompletableFuture<T>(); jobs.add(result);
            result.whenComplete((value,error) -> {
                synchronized (this) { jobs.remove(result); }
                if (cancelInner && result.isCancelled()) inner.cancel(false);
            });
            inner.whenComplete((value,error) -> { if (error == null) result.complete(value); else result.completeExceptionally(error); });
            return result;
        }
        private static <T> CompletableFuture<T> unavailable() { return CompletableFuture.failedFuture(new IllegalStateException("Speech handle is closed")); }
        @Override public synchronized CompletableFuture<Void> initialize() { return closed ? unavailable() : track(shared.engine.initialize(), false); }
        @Override public synchronized CompletableFuture<String> transcribe(float[] samples, String language) {
            if (closed) return unavailable();
            if (samples == null || samples.length < 3200 || samples.length > AudioSamples.MAX_SAMPLES)
                return CompletableFuture.failedFuture(new IllegalArgumentException("Provide 0.2 to 12 seconds of 16 kHz mono audio"));
            for (float value : samples) if (!Float.isFinite(value) || Math.abs(value) > 1)
                return CompletableFuture.failedFuture(new IllegalArgumentException("Audio samples must be finite and in [-1,1]"));
            if (language == null || !language.matches("[a-z]{2,3}"))
                return CompletableFuture.failedFuture(new IllegalArgumentException("Use a Whisper language code such as en, pl, or ja"));
            return track(shared.engine.transcribe(samples.clone(), language), true);
        }
        @Override public String status() { return closed ? "Speech handle closed" : shared.engine.status(); }
        @Override public boolean isReady() { return !closed && shared.engine.isReady(); }
        @Override public boolean isRecognizing() { return !closed && shared.engine.isRecognizing(); }
        @Override public int cancelledJobs() { return shared.engine.cancelledJobs(); }
        @Override public CompletableFuture<Void> whenIdle() { return closed ? unavailable() : shared.engine.whenIdle(); }
        @Override public SpeechProfile activeProfile() { return shared.engine.activeProfile(); }
        @Override public SpeechBackend backend() { return shared.engine.backend(); }
        @Override public String device() { return shared.engine.device(); }
        @Override public void close() {
            List<CompletableFuture<?>> pending;
            synchronized (this) { if (closed) return; closed=true; pending=List.copyOf(jobs); }
            pending.forEach(job -> job.cancel(false));
            synchronized (EnginePool.class) {
                shared.listeners.remove(progress);
                if (--shared.references == 0) { ENGINES.remove(key,shared); shared.engine.close(); }
            }
        }
    }
}
