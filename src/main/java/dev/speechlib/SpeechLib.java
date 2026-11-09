package dev.speechlib;

import dev.speechlib.internal.EnginePool;
import dev.speechlib.internal.GpuRuntime;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

/** Entry point for local speech. Creating a handle does not download data or open a microphone. */
public final class SpeechLib {
    private SpeechLib() {}
    public static SpeechEngine create() { return builder().build(); }
    public static SpeechEngine create(SpeechProfile profile) { return builder().profile(profile).build(); }
    public static Builder builder() { return new Builder(); }
    /** Checks the OS/architecture support, not whether a physical GPU is available. */
    public static boolean supportsGpuPlatform() { return GpuRuntime.supportedPlatform(); }
    public static Path defaultCache() { return FabricLoader.getInstance().getGameDir().resolve(".speechlib"); }

    public static final class Builder {
        private SpeechProfile profile = SpeechProfile.CPU;
        private Path cache;
        private Consumer<String> progress = text -> {};
        private Builder() {}
        public Builder profile(SpeechProfile value) { profile = Objects.requireNonNull(value); return this; }
        public Builder cacheDirectory(Path value) { cache = Objects.requireNonNull(value); return this; }
        /** Callbacks run on speech/download workers. Schedule Minecraft UI changes on its main thread. */
        public Builder progress(Consumer<String> value) { progress = Objects.requireNonNull(value); return this; }
        public SpeechEngine build() {
            Path target = (cache == null ? defaultCache() : cache).toAbsolutePath().normalize();
            return EnginePool.acquire(target, profile, progress);
        }
    }
}
