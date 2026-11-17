# Offline Android Voice Assistant — Full Plan

**Name:** Fawkes (the phoenix — picked 17 Nov 2026).
**Goal:** an Android app that listens to voice commands and carries out tasks on the phone — 100% offline. No account, no server, no internet permission, no cost.
**Built with:** Kotlin (native Android) + GitHub + GitHub Actions (same build-and-release flow as IRIS).

---

## 1. How it works

```
Your voice → Microphone → Vosk (offline speech-to-text, on the phone)
    → Command Matcher (keyword rules: "flashlight on", "open YouTube")
    → Action Executor (Android intents + system APIs)
    → Android TTS speaks a confirmation ("Flashlight on")
```

No audio ever leaves the phone. No backend exists to maintain.

---

## 2. Tech stack (verified, Nov 2026)

| Piece | Choice | Notes |
|---|---|---|
| Language | Kotlin, native Android | Not Expo — the system actions need native APIs |
| IDE | Android Studio (free) | Latest stable |
| Min SDK | 26 (Android 8.0) | Covers ~95% of active phones |
| Target SDK | 34/35 | Play Store requirement if ever published there |
| Speech-to-text | Vosk (Apache-2.0, free) | `com.alphacephei:vosk-android:0.3.75@aar` + `net.java.dev.jna:jna:5.18.1@aar` — the `@aar` matters, without it the native libs are missing |
| Voice model | `vosk-model-small-en-us-0.15` (~41 MB) | From alphacephei.com/vosk/models. Hindi model `vosk-model-small-hi-0.22` can be added later |
| Speech output | Android TextToSpeech | Built in, works offline |
| Local storage | Room (notes, reminders, custom commands) | Or plain files for v1 — Room is cleaner |
| UI | Jetpack Compose or XML Views | Compose is the modern choice; Views is simpler if learning |
| CI / releases | GitHub Actions | Build debug + release APK on every push/tag, same pattern as IRIS |
| Reference project | github.com/alphacep/vosk-android-demo | Official Vosk demo — copy its setup, don't start from zero |

**Model rule:** never commit the 41 MB model to git. Download it on first run inside the app (with a progress bar), or fetch it with a script at build time. Add `*.zip` model folders to `.gitignore`.

---

## 3. GitHub repo setup

**Repo:** `ishantsil56-cell/Fawkes` (public or private — your call).

```
<app-name>/
├── app/
│   └── src/main/
│       ├── java/com/<you>/<app>/
│       │   ├── MainActivity.kt
│       │   ├── voice/        # Vosk wrapper, model download, mic handling
│       │   ├── commands/     # Intent matcher + one file per action group
│       │   ├── tts/          # Speak-back helper
│       │   ├── data/         # Room database (notes, reminders, customs)
│       │   └── ui/           # Screens: home, command list, settings
│       ├── res/              # Layouts, strings, icon
│       └── AndroidManifest.xml
├── docs/
│   └── COMMANDS.md           # Every supported phrase, kept up to date
├── .github/workflows/
│   └── build-apk.yml         # CI: build APK on push, attach to Releases on tags
├── PLAN.md                   # This file
├── README.md                 # What it is, how to build, how to install
├── .gitignore
└── LICENSE                   # MIT — simple, lets anyone use it
```

**Branches:** `main` = stable, only working code. `dev` = daily work. Feature branches per command group (`flashlight`, `wake-word`), merged when tested on your phone.

**CI (copy the IRIS pattern):** on push → install JDK 17 → Gradle build → upload debug APK as an artifact. On a version tag (`v0.1.0`) → build release APK → attach to a GitHub Release. You install from the Release, same as IRIS via Obtainium.

---

## 4. Features by phase

### Phase 0 — Setup (Day 1)
- [ ] Pick the app name + package name (`com.ishantsil.fawkes`)
- [ ] Create the GitHub repo, push the Android Studio scaffold
- [ ] Add README, LICENSE (MIT), `.gitignore` (models, keystore, local.properties)
- [ ] Add the CI workflow — prove it builds an APK before writing any feature
- [ ] Add Vosk dependency + first-run model download screen

### Phase 1 — MVP: listen, understand, act (Week 1–2)
Core loop working end to end: tap mic → speak → action happens → voice confirms.

| # | Command | Example phrases | How it works |
|---|---|---|---|
| 1 | Flashlight | "flashlight on / off", "torch on" | CameraManager.setTorchMode |
| 2 | Open app | "open YouTube", "open WhatsApp" | PackageManager lookup + launch intent |
| 3 | Alarm | "set alarm for 7 AM" | AlarmClock.ACTION_SET_ALARM intent |
| 4 | Timer | "set a timer for 10 minutes" | AlarmClock.ACTION_SET_TIMER intent |
| 5 | Call | "call Maa" | ACTION_DIAL (no permission) or ACTION_CALL (needs CALL_PHONE) |
| 6 | Time / date / battery | "what time is it", "battery level" | System APIs, spoken answer |
| 7 | Calculator | "what is 45 times 12" | Parse + evaluate locally |
| 8 | Note | "note: buy milk" / "read my notes" | Room database, TTS reads back |
| 9 | Volume / brightness | "volume up", "brightness down" | AudioManager / Settings |
| 10 | Wi-Fi / Bluetooth panel | "open Wi-Fi settings" | Settings Panel intent (see §7 — direct toggle is blocked on Android 10+) |
| 11 | SMS | "text Rahul: on my way" | SmsManager (SEND_SMS permission) or messaging-app intent |
| 12 | WhatsApp (limited) | "WhatsApp Rahul: hi" | Opens WhatsApp with text pre-filled — you tap send (WhatsApp blocks full automation) |

Also in Phase 1: conversation log on screen (what it heard → what it did), a settings screen (voice speed, model status), and a help screen listing every command.

### Phase 2 — Hands-free + polish (Week 3–4)
- [ ] Wake word ("Hey Vox") — Vosk keyword spotting in a foreground service; battery cost is real, so make it toggleable
- [ ] Reminders ("remind me in 30 minutes to drink water") — AlarmManager + notification
- [ ] Music control ("play / pause / next") — MediaSession
- [ ] SOS flashlight (Morse SOS blink) — fun, and genuinely useful on a trek
- [ ] Custom commands — user defines "good night" → flashlight off + alarm set + volume down
- [ ] Home-screen widget + Quick Settings tile (tap to listen)
- [ ] App icon + proper name + dark theme

### Phase 3 — Smart layer (optional, later)
- [ ] On-device LLM (Google Gemma via MediaPipe) for free-form questions — heavy (~2 GB model), slow on budget phones; only if Phase 1–2 feel solid
- [ ] Routines ("good morning" runs 4 actions in sequence)
- [ ] Hindi voice model as a second language option
- [ ] Conversation memory (remembers "call him" = last person named)

### Phase 4 — Release
- [ ] GitHub Releases as the main distribution (free, no card — same as IRIS)
- [ ] Obtainium link so users get update notifications
- [ ] F-Droid: possible later, but note F-Droid builds everything from source and Vosk ships a prebuilt binary — it would need a separate Vosk-free flavour. Don't chase this in v1.

---

## 5. Permissions (ask only when the command needs them)

| Permission | Needed for | When to ask |
|---|---|---|
| RECORD_AUDIO | Listening at all | First launch |
| CALL_PHONE | Direct calling | First "call" command (or use dialer, no permission) |
| SEND_SMS | Direct texting | First "text" command (or use messaging intent, no permission) |
| POST_NOTIFICATIONS | Reminders | First reminder set |
| SCHEDULE_EXACT_ALARM | Reminders on time | First reminder set (Android 12+) |
| FOREGROUND_SERVICE_MICROPHONE | Wake word listening | When wake word is enabled (Phase 2) |
| **No INTERNET permission** | — | Deliberately absent in v1. That's the privacy promise. |

---

## 6. Command matcher design (keep it simple)

No AI in v1. Rules, in order:
1. Normalise: lowercase, strip filler words ("please", "hey").
2. Match patterns in priority order: exact command phrases first, then keyword contains.
3. Extract values with small parsers: time ("7 AM", "in 10 minutes"), contact name (match against contacts), app name (fuzzy match installed apps), numbers for calculator.
4. If no match: say "I don't know that one yet" and log the phrase — the log becomes your Phase 2 feature list.
5. Every command returns a spoken confirmation, so you know it worked without looking.

---

## 7. Android limits to know before you build

- **Wi-Fi / Bluetooth can't be toggled directly** by third-party apps since Android 10. The correct move is opening the system settings panel (`Settings.Panel.ACTION_WIFI`) — one tap from the user. Don't fight this; design around it.
- **Background mic is restricted.** Wake-word listening needs a foreground service with a visible notification. Android will kill background listeners.
- **Battery optimisation** will pause the wake-word service. The app must ask the user to exempt it (and explain why).
- **Vosk accuracy** on the small model is good for short commands in a quiet room, weaker in noise or with heavy accents. Keep commands short and distinct; test with your own voice.
- **Doze mode** delays exact alarms unless you use `setExactAndAllowWhileIdle` and the user grants the exact-alarm permission.

---

## 8. Testing plan

- Test on your own phone (Galaxy F17 5G) after every command lands — emulator mic doesn't reflect reality.
- Per command: works in a quiet room / works with TV on / fails gracefully with no permission / fails gracefully with no match.
- Test the first-run flow fresh (clear app data): model downloads, progress shows, retry works if the download fails.
- Test low storage: model needs ~100 MB unpacked — warn if space is low.
- Keep a `TESTING.md` checklist in the repo; tick it before every release tag.

---

## 9. Timeline (evenings + weekends)

| Phase | Time | Outcome |
|---|---|---|
| Phase 0 — setup | 1 day | Repo + CI building an empty APK |
| Phase 1 — MVP | 1–2 weeks | 12 commands working by voice |
| Phase 2 — polish | 1–2 weeks | Wake word, reminders, widget, custom commands |
| Phase 3 — smart layer | Later, optional | On-device LLM, Hindi, routines |
| Phase 4 — release | 1 day | v1.0.0 on GitHub Releases |

If Kotlin is new to you, add ~1 week at the start for Kotlin basics (Google's free Kotlin course + the official Android Basics course). The Vosk demo app is the fastest teacher — run it first, then build on it.

---

## 10. Cost

**₹0.** Android Studio free, Kotlin free, Vosk + models free (Apache-2.0), GitHub free, GitHub Actions free tier (2,000 minutes/month — far more than this needs), distribution via GitHub Releases free. No card anywhere. (Play Store would be $25 one-time — not needed; GitHub Releases + Obtainium is the route, like IRIS.)

---

## 11. Risks

- **Scope creep** — the #1 killer. Phase 1 is 12 commands, nothing more, until they all work.
- **Vosk accuracy frustration** — mitigate with short, distinct commands and a visible "what I heard" line so you can see mishears.
- **Wake-word battery drain** — Phase 2 only, toggleable, with an honest battery note in settings.
- **Android version differences** — test on your own phone first; emulator second.

---

## 12. First steps (today)

1. Pick the name.
2. Create the repo `ishantsil56-cell/<name>` on GitHub.
3. In Android Studio: New Project → Empty Activity (Kotlin) → min SDK 26.
4. Push it, add the CI workflow, watch the first APK build.
5. Clone/run the Vosk demo app (github.com/alphacep/vosk-android-demo) on your phone to hear offline recognition working before writing a line of voice code.
6. Then build Phase 1, one command at a time, flashlight first.

---

## Appendix A — Target device: Samsung Galaxy F17 5G

The plan is built for this phone first. Verified specs (Nov 2026):

- **Chipset:** Exynos 1330 octa-core (2× Cortex-A78 @ 2.4 GHz + 6× Cortex-A55 @ 2.0 GHz), 5nm
- **RAM:** 6 GB (his variant — 128 GB storage)
- **OS:** Android 15, One UI 7 (upgradable to Android 16 / One UI 8.5)
- **Display:** 6.7" FHD+ Super AMOLED, 90 Hz
- **Battery:** 5,000 mAh

**What this means for the build:**

- **Vosk runs fine.** The small model (~41 MB) is made for exactly this class of phone — loads in 1–2 seconds, recognition is real-time on the Exynos 1330. No performance work needed in Phase 1.
- **Phase 3 (on-device LLM) is tight on 6 GB but testable.** A 2B model needs ~2 GB free RAM; with 6 GB total it's feasible but slow, so test the smallest model first and measure before committing. If it's too slow, Phase 3 stays parked — Phases 1–2 don't need it.
- **Android 15 rules apply:** foreground service types must be declared in the manifest (microphone type for the wake-word service), exact-alarm permission is a runtime ask, and background mic access needs the foreground service + notification from Phase 2 onward.
- **Samsung-specific setup (do this on the phone after installing):**
  1. Settings → Battery → Background usage limits → **Never sleeping apps** → add the assistant. One UI kills background apps aggressively — without this, the wake-word service (Phase 2) will die.
  2. Settings → Battery → turn off **Adaptive battery** restrictions for the app (or add it to the unrestricted list).
  3. Settings → Device care → **Auto optimisation** — make sure it doesn't include the assistant.
  4. Settings → Advanced features → **Side button** → double-press → open the assistant app. Fastest way to launch it hands-on.
  5. TTS engine: Samsung's own TTS is the default; Google TTS is also available. The app should use the system default and let the user switch in settings.
- **Bixby doesn't conflict.** The assistant is a separate app; Bixby stays on the long-press side button unless you remap it.
- **Testing device = this phone.** Every command gets tested on the F17 itself (see §8). The emulator is only for layout checks.

*Plan written 17 Nov 2026. Vosk versions verified against the official vosk-android-demo (0.3.75 + JNA 5.18.1, Nov 2026). Re-check versions when you start — pin what's in the demo repo that day.*
