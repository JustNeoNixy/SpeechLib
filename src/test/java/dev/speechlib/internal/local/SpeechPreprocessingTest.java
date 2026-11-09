package dev.speechlib.internal.local;

import com.google.gson.*;
import dev.speechlib.AudioSamples;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SpeechPreprocessingTest {
    @TempDir Path directory;
    @Test void everySupportedLanguageHasADistinctTokenInTheActualWhisperModel() throws Exception {
        JsonObject manifest;
        try (var stream = getClass().getResourceAsStream("/speechlib/model.json")) {
            manifest = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String name = "base/tokenizer.json";
        var spec = manifest.getAsJsonObject("files").getAsJsonObject(name);
        String supplied = System.getProperty("speechlib.model.directory", "");
        Path tokenizerFile = directory.resolve("actual-tokenizer.json");
        dev.speechlib.internal.AssetDownloads.ensure(tokenizerFile, java.net.URI.create(spec.get("url").getAsString()),
            spec.get("sha256").getAsString(), spec.get("bytes").getAsLong(),
            supplied.isBlank() ? null : Path.of(supplied).resolve(name), ignored -> {}, "Whisper vocabulary");
        var tokenizer = new WhisperTokenizer(tokenizerFile);
        var languageTokens = new HashSet<Integer>();
        var vocabulary=JsonParser.parseString(Files.readString(tokenizerFile)).getAsJsonObject();
        var tokens=new TreeSet<>(vocabulary.getAsJsonObject("model").getAsJsonObject("vocab").keySet());
        for(var token:vocabulary.getAsJsonArray("added_tokens"))tokens.add(token.getAsJsonObject().get("content").getAsString());
        for (String token : tokens) {
            if(!token.matches("<\\|[a-z]{2,3}\\|>"))continue;
            String language=token.substring(2,token.length()-2);
            int[] prefix = tokenizer.prefix(language);
            assertEquals(4, prefix.length); assertTrue(languageTokens.add(prefix[1]), language);
        }
        assertTrue(languageTokens.size()>=99);
    }
    @Test void javaLogMelMatchesThePreviousPythonFeatureExtractor() throws Exception {
        float[] actual;
        try (var stream = getClass().getResourceAsStream("/audio/diamond-sword.wav");
             var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(stream))) {
            actual = MelSpectrogram.compute(AudioSamples.fromPcm16(audio.readAllBytes()));
        }
        var golden = json("mel-reference.json");
        for (var sample : golden.getAsJsonArray("samples")) {
            var point = sample.getAsJsonObject();
            int index = point.get("band").getAsInt() * 3000 + point.get("frame").getAsInt();
            assertEquals(point.get("value").getAsFloat(), actual[index], 0.0004f, "mel index " + index);
        }
        int frames = golden.get("frames").getAsInt();
        for (int band = 0; band < 80; band++) assertEquals(0, actual[band * 3000 + frames]);
    }
    @Test void byteVocabularyPreservesPolishAndJapaneseAndValidatesLanguage() throws Exception {
        var golden = json("tokenizer-reference.json");
        Path file = directory.resolve("tokenizer.json"); Files.writeString(file, golden.toString());
        var tokenizer = new WhisperTokenizer(file);
        for (var value : golden.getAsJsonArray("cases")) {
            var sample = value.getAsJsonObject();
            int[] ids = sample.getAsJsonArray("ids").asList().stream().mapToInt(JsonElement::getAsInt).toArray();
            assertEquals(sample.get("text").getAsString(), tokenizer.decode(ids, ids.length));
        }
        assertEquals(4, tokenizer.prefix("pl").length);
        assertEquals(4, tokenizer.prefix("ja").length);
        assertThrows(IllegalArgumentException.class, () -> tokenizer.prefix("zzz"));
    }
    private JsonObject json(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/audio/" + name)) {
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
