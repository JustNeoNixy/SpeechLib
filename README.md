# SpeechLib

Local Whisper speech recognition for Minecraft mods, extracted from
[SayItem](https://github.com/JustNeoNixy/SayItem). Audio stays on the player's computer.
The small library mod downloads checked native libraries and model weights when used.

SpeechLib provides asynchronous transcription, optional microphone capture, shared
model caches, cancellation, and CPU fallback. Windows and Linux use Vulkan for GPU
profiles; macOS uses Metal. No Python, companion process, or Fabric API is required.

The first release and integration examples are being prepared. Native build sources
and third-party licenses are included in this repository.
