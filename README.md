# Fawkes

An offline Android voice assistant. You speak, it does things on your phone —
flashlight, alarms, calls, notes — with no account, no server, and no audio
ever leaving the phone.

Named for the phoenix. Like one, it runs entirely on its own.

## Status

0.3.2 — Phase 1, 2 and 3 are in: live voice commands (flashlight, apps,
alarms, calls, notes, music, SOS), the "Hey Fawkes" wake word, reminders,
custom commands and routines, a widget and Quick Settings tile, Hindi
voice model support, conversation memory, and on-device AI answers via a
user-imported Gemma model. 0.3.1 fixed two bugs: the wake word now runs
commands hands-free (no tap — say the command right after "Hey Fawkes"),
with a continuous-listen toggle in the app, and LLM answers now use
Gemma's chat template with a lower temperature, so they answer instead
of rambling. 0.3.2 improves listening accuracy: the recognizer tries
Vosk's 3 best guesses for every command, and a bigger English voice model
(lgraph, 128 MB) is available as a download for noticeably better hearing.
0.3.3 adds the Indian English voice model (en-in, 1 GB) as a third English
option, for accents the US models keep mishearing; the wake word falls
back to plain spotting on models that don't support grammar. 0.4.0
switches command listening to Whisper (base, int8, via sherpa-onnx) — it
hears accents the Vosk models keep missing; Vosk stays for the wake word
and as a fallback when Whisper isn't downloaded yet. See
[PLAN.md](PLAN.md) for the full plan and
[docs/COMMANDS.md](docs/COMMANDS.md) for every command.

## How it works

```
Your voice → Microphone → Whisper (offline speech-to-text, on the phone)
    → Command Matcher (keyword rules)
    → Action Executor (Android intents + system APIs)
    → Android TTS speaks a confirmation
```

- **Speech-to-text:** [Whisper](https://github.com/openai/whisper) (base,
  int8, ~161 MB) via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
  1.13.3, with Silero VAD finding the end of each utterance — speak, pause,
  and it transcribes. [Vosk](https://alphacephei.com/vosk/)
  (`vosk-android:0.3.75`) still runs the "Hey Fawkes" wake word and is the
  fallback when Whisper isn't downloaded: small English model
  `vosk-model-small-en-us-0.15` (~41 MB), bigger English model
  `vosk-model-en-us-0.22-lgraph` (~128 MB, more accurate), Indian English
  model `vosk-model-en-in-0.5` (~1 GB, trained on Indian English), and
  small Hindi model `vosk-model-small-hi-0.22` (~44 MB). The English Vosk
  model is switchable in Settings. The command matcher tries Vosk's 3 best
  guesses for every utterance on the Vosk path.
- **Speech output:** Android TextToSpeech, built in and offline.
- **On-device AI:** MediaPipe LLM Inference (`tasks-genai:0.10.14`) with a
  user-imported Gemma 2B model — answers "ask …" questions fully offline.
- **Privacy:** the only network use in the app is the one-time model download
  (Whisper from huggingface.co, Vosk from alphacephei.com, the VAD model
  from github.com). No audio, no analytics, no account. (Long-term the
  models can be bundled in the APK at build time and the INTERNET
  permission dropped entirely.)

## Build

Requirements: JDK 17, Android SDK with API 35, Gradle 8.x.

sherpa-onnx (the Whisper runtime) ships no Maven artifact, so the build
needs its release AAR in `app/libs/` first — CI fetches it automatically;
for a local build, run:

```sh
mkdir -p app/libs
curl -fSL -o app/libs/sherpa-onnx-static-link-onnxruntime-1.13.3.aar \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.3/sherpa-onnx-static-link-onnxruntime-1.13.3.aar
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

No Gradle wrapper is committed yet; CI installs Gradle via
`gradle/actions/setup-gradle`. A wrapper will be added when the project
settles.

## Install

Grab the APK from [Releases](https://github.com/ishantsil56-cell/Fawkes/releases)
— or add the repo to [Obtainium](https://obtainium.imranr.dev/) for update
notifications. On first launch, download the Whisper voice model (~161 MB,
recommended) and grant microphone permission. The small Vosk English model
(~41 MB) works as a lighter alternative and runs the wake word.

Target phone: Samsung Galaxy F17 5G (Android 15, 6 GB RAM). Min SDK 26
(Android 8.0).

## Commands

See [docs/COMMANDS.md](docs/COMMANDS.md) — 12 core commands plus wake word,
reminders, music, SOS, custom commands, routines, Hindi and on-device AI.

## License

MIT — see [LICENSE](LICENSE).
