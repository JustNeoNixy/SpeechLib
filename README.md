# SpeechLib

Local Whisper speech recognition for Minecraft mods, extracted from
[SayItem](https://github.com/JustNeoNixy/SayItem). Audio stays on the player's computer.
The small library mod downloads checked native libraries and model weights when used.

SpeechLib is a normal **Fabric mod jar**. Players install it alongside the mod that
uses it. It bundles no other mods or Fabric API and needs no Python or companion app.
It requires Fabric Loader and Java 21+. SayItem integration is checked on Minecraft
26.1.2, 26.2, and 26.3; the public API uses no Minecraft internals.

## Add to a project

For Fabric/Loom 26.x, add this to `build.gradle`:

```groovy
repositories {
    maven { url = 'https://justneonixy.github.io/SpeechLib/maven' }
}
dependencies {
    implementation 'dev.speechlib:speechlib:1.0.0'
}
```

Older Loom projects can use `modImplementation` in place of `implementation`.
Add `"speechlib": ">=1.0.0"` to your `fabric.mod.json`'s existing `depends` object.
Keep SpeechLib as a separate dependency; do not shade or nest it in your jar.
When publishing on Modrinth, mark SpeechLib as a required dependency there too.
Your own mod can still require Fabric API separately.

## Use the API

Transcribe a `float[] samples` containing mono, 16 kHz audio in [-1, 1]:

```java
import dev.speechlib.*;

SpeechEngine speech = SpeechLib.create(SpeechProfile.GPU_SMALL);
speech.initialize()
    .thenCompose(ready -> speech.transcribe(samples, "pl"))
    .thenAccept(text -> client.execute(() -> handleTranscript(text)))
    .exceptionally(error -> { client.execute(() -> reportError(error)); return null; });
```

`client`, `handleTranscript`, and `reportError` belong to your mod. Initialization
and callbacks run off the game thread; schedule game changes on its main thread.
Never block that thread with `join()` or `get()`. Recordings support 0.2 to 12 seconds.
Use `AudioSamples.fromPcm16(bytes)` for signed little-endian PCM16 input.

For optional push-to-talk, create a `MicrophoneCapture` after preparing the engine:

```java
MicrophoneCapture mic = new MicrophoneCapture(speech);
// Speak key pressed, once speech.isReady():
mic.start("pl").thenAccept(text -> client.execute(() -> handleTranscript(text)));
// Speak key released:
mic.finish();
// Client shutdown:
mic.close();
speech.close();
```

Handle errors on the microphone future too. `mic.cancel()` or cancelling a
transcription future aborts that request. Close your handle when finished;
other mods' shared sessions keep working. A [complete example](examples/dev/speechlib/example/VoiceCommands.java)
is compiled with the library. SpeechLib supplies no keybinds, HUD, or game rules.

## Models and platforms

Profiles are `CPU`, `GPU_BASE`, `GPU_SMALL`, and `GPU_TURBO`. GPU failures fall back
to CPU Base; check `backend()`, `activeProfile()`, `device()`, and `status()` after
initialization. Larger models can improve recognition while taking longer.

| Profile | Model download | Recommended free VRAM for speech |
| --- | ---: | ---: |
| CPU Base | 145 MB | None |
| GPU Base | 148 MB | About 1 GB |
| GPU Small | 190 MB | About 1 GB |
| GPU Turbo | 574 MB | About 2 GB |

These are planning estimates with headroom, not guaranteed minimums. Minecraft,
textures, and shaders need additional memory. On Apple Silicon, GPU allocations
come from shared system RAM. Download size is smaller than working memory usage.
Low free VRAM or sharing a busy GPU can cause stalls; a larger model is not always faster.

- CPU: Windows x64, Linux x64/ARM64, macOS x64/ARM64, using CTranslate2 INT8.
- GPU: Vulkan on Windows x64 and Linux x64/ARM64; Metal on macOS x64/ARM64.
  Linux GPU builds target Ubuntu 24.04 or compatible systems. GPU drivers are required.

Assets are downloaded once, SHA-256 checked, and cached in `.speechlib/` for offline
reuse across mods. Existing verified SayItem models are reused without changing
the original files. Building a handle opens no microphone and downloads nothing
until speech is requested. Audio is never uploaded. GPU recognition was exercised
on an RX 570; Linux/macOS GPU binaries are build/load checked without a physical
GPU performance claim. Whisper can still misrecognize speech.

Build with `./gradlew build`; Windows uses `.\gradlew.bat`. Native integration tests
use `-Pnative_tests=true`, with `-Pgpu_tests=true` for a physical GPU. Native source
and third-party licenses are included. Extracted from SayItem under MIT.
