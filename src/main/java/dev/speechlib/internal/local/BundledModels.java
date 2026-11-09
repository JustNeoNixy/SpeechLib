package dev.speechlib.internal.local;

import dev.speechlib.*;

import com.google.gson.*;
import dev.speechlib.internal.BundledFiles;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.URI;
import java.util.function.Consumer;
import dev.speechlib.internal.AssetDownloads;

final class BundledModels {
    record Prepared(Path model, Path vad) {}
    static Path vad(Path cache, Consumer<String> progress) throws IOException, InterruptedException {
        JsonObject manifest;
        try (var stream = BundledModels.class.getResourceAsStream("/speechlib/model.json")) {
            if (stream == null) throw new IOException("Missing speech model manifest");
            manifest = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var spec = manifest.getAsJsonObject("files").getAsJsonObject("silero_vad_v6.onnx");
        Path target = cache.resolve("ct2-base-d01c3014881c/silero_vad_v6.onnx");
        String supplied = System.getProperty("speechlib.model.directory", "");
        AssetDownloads.ensure(target, URI.create(spec.get("url").getAsString()), spec.get("sha256").getAsString(),
            spec.get("bytes").getAsLong(), supplied.isBlank() ? null : Path.of(supplied).resolve("silero_vad_v6.onnx"), progress, "speech filter");
        return target;
    }
    static Prepared prepare(Path cache, Consumer<String> progress) throws IOException, InterruptedException {
        JsonObject manifest;
        try (var stream = BundledModels.class.getResourceAsStream("/speechlib/model.json")) {
            if (stream == null) throw new IOException("Missing speech model manifest");
            manifest = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Path root = cache.resolve("ct2-base-d01c3014881c").toAbsolutePath();
        for (var entry : manifest.getAsJsonObject("files").entrySet()) {
            Path destination = root.resolve(entry.getKey()).normalize();
            if (!destination.startsWith(root)) throw new IOException("Invalid model resource path");
            var value = entry.getValue().getAsJsonObject();
            String supplied = System.getProperty("speechlib.model.directory", "");
            AssetDownloads.ensure(destination, URI.create(value.get("url").getAsString()), value.get("sha256").getAsString(),
                value.get("bytes").getAsLong(), supplied.isBlank() ? null : Path.of(supplied).resolve(entry.getKey()), progress, "Whisper " + entry.getKey());
        }
        return new Prepared(root.resolve("base"), root.resolve("silero_vad_v6.onnx"));
    }
}
