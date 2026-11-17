# Fawkes

An offline Android voice assistant. You speak, it does things on your phone —
flashlight, alarms, calls, notes — with no account, no server, and no audio
ever leaving the phone.

Named for the phoenix. Like one, it runs entirely on its own.

## Status

Phase 0 (scaffold): the app builds, asks for the microphone, and downloads
the offline voice model on first run. Voice commands land in Phase 1,
flashlight first. See [PLAN.md](PLAN.md) for the full plan.

## How it works

```
Your voice → Microphone → Vosk (offline speech-to-text, on the phone)
    → Command Matcher (keyword rules)
    → Action Executor (Android intents + system APIs)
    → Android TTS speaks a confirmation
```

- **Speech-to-text:** [Vosk](https://alphacephei.com/vosk/) (`vosk-android:0.3.75`),
  small English model `vosk-model-small-en-us-0.15` (~41 MB).
- **Speech output:** Android TextToSpeech, built in and offline.
- **Privacy:** the only network use in the app is the one-time model download
  from alphacephei.com. No audio, no analytics, no account. (Long-term the
  model can be bundled in the APK at build time and the INTERNET permission
  dropped entirely.)

## Build

Requirements: JDK 17, Android SDK with API 35, Gradle 8.x.

```sh
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

No Gradle wrapper is committed yet; CI installs Gradle via
`gradle/actions/setup-gradle`. A wrapper will be added when the project
settles.

## Install

Grab the APK from [Releases](https://github.com/ishantsil56-cell/Fawkes/releases)
— or add the repo to [Obtainium](https://obtainium.imranr.dev/) for update
notifications. On first launch, download the voice model (~41 MB) and grant
microphone permission.

Target phone: Samsung Galaxy F17 5G (Android 15, 6 GB RAM). Min SDK 26
(Android 8.0).

## Commands

See [docs/COMMANDS.md](docs/COMMANDS.md). Phase 1 ships 12 commands.

## License

MIT — see [LICENSE](LICENSE).
