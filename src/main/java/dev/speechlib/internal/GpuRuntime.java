package dev.speechlib.internal;

import dev.speechlib.*;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

/** Ordinary GPU libraries and models downloaded into the shared speech cache. */
public final class GpuRuntime {
    private GpuRuntime() {}
    public static boolean supportedPlatform() {
        if (Boolean.getBoolean("speechlib.gpu.disabled")) return false;
        try { return java.util.Set.of("windows-x64","linux-x64","linux-arm64","macos-x64","macos-arm64").contains(RuntimeBundles.platform()); }
        catch (IOException ignored) { return false; }
    }
    private static JsonObject index() throws IOException {
        try (var stream = GpuRuntime.class.getResourceAsStream("/speechlib/gpu-index.json")) {
            if (stream == null) throw new IOException("Missing GPU speech manifest");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    public static Path prepare(Path cache, Consumer<String> progress) throws IOException, InterruptedException {
        if (!supportedPlatform()) throw new IOException("GPU speech is unavailable on this platform");
        String platform=RuntimeBundles.platform();
        var spec=index().getAsJsonObject("runtimes").getAsJsonObject(platform);
        if(spec==null)throw new IOException("Missing GPU runtime for "+platform);
        return RuntimeBundles.install(cache, platform+"-gpu", spec, "speechlib.gpu.bundle", progress);
    }
    public static SpeechBackend backend(){return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac")?SpeechBackend.METAL:SpeechBackend.VULKAN;}
    public static Path model(Path cache, SpeechProfile profile, Consumer<String> progress) throws IOException, InterruptedException {
        String key = switch (profile) { case GPU_TURBO -> "turbo"; case GPU_SMALL -> "small"; default -> "base"; };
        var spec = index().getAsJsonObject("models").getAsJsonObject(key);
        String filename = spec.get("filename").getAsString();
        Path target = cache.resolve("gpu-models").resolve(spec.get("sha256").getAsString().substring(0, 12) + "-" + filename);
        String supplied = System.getProperty("speechlib.gpu.model.directory", "");
        AssetDownloads.ensure(target, URI.create(spec.get("url").getAsString()), spec.get("sha256").getAsString(),
            spec.get("bytes").getAsLong(), supplied.isBlank() ? null : Path.of(supplied).resolve(filename), progress, "Whisper " + key);
        return target;
    }
}
