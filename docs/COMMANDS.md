# Fawkes commands

Every supported phrase, kept up to date. 0.3.0 ships Phase 1, 2 and 3 —
everything below is live. Unrecognised phrases are logged on screen; the
log becomes the next feature list.

## Phase 1 — the 12 core commands

| # | Command | Example phrases |
|---|---|---|
| 1 | Flashlight | "flashlight on / off", "torch on" |
| 2 | Open app | "open YouTube", "open it again" |
| 3 | Alarm | "set alarm for 7 AM" |
| 4 | Timer | "set a timer for 10 minutes" |
| 5 | Call | "call Maa", "call him" |
| 6 | Time / date / battery | "what time is it", "battery level" |
| 7 | Calculator | "what is 45 times 12" |
| 8 | Note | "note buy milk", "read my notes", "clear notes" |
| 9 | Volume / brightness | "volume up", "brightness down" |
| 10 | Wi-Fi / Bluetooth panel | "open Wi-Fi settings" |
| 11 | SMS | "text Rahul on my way" |
| 12 | WhatsApp (limited) | "WhatsApp Rahul hi" |

## Phase 2 — hands-free + polish

| Feature | Example phrases |
|---|---|
| Wake word | "Hey Fawkes" (toggle in Settings; tap the notification to talk) |
| Reminders | "remind me in 30 minutes to drink water", "remind me at 7 PM to call Maa" |
| Music control | "play", "pause", "next", "previous" |
| SOS flashlight | "SOS" (blinks ... --- ... three times), "stop SOS" |
| Custom commands | "create custom command good night runs flashlight off and set alarm for 7 AM" |
| Widget | Home-screen widget: tap to talk, shows the last thing heard |
| Quick Settings tile | "Listen with Fawkes" tile: tap to open and listen |
| Settings | Voice speed, language, wake word, models, on-device AI |
| Help screen | Full command list, from the Help button |
| Dark theme | Follows the system (Material3 DayNight) |

## Phase 3 — smart layer

| Feature | Example phrases |
|---|---|
| Routines | "create routine good morning runs flashlight on then open YouTube" |
| Hindi voice model | Download in Settings; "टॉर्च चालू करो", "समय क्या है" |
| Conversation memory | "call him", "text her", "open it again" (last person / app) |
| On-device AI | "ask what is the capital of France" (needs a Gemma model — see below) |

## Notes and limits

- Wi-Fi / Bluetooth can't be toggled directly by third-party apps since
  Android 10 — Fawkes opens the system settings panel instead.
- WhatsApp blocks full automation — Fawkes opens WhatsApp with the text
  pre-filled; you tap send.
- SMS opens the messaging app's compose screen with the text pre-filled;
  you tap send. (Sending silently needs SEND_SMS and the default-SMS-app
  role, which Fawkes deliberately does not take.)
- Calls open the dialer with the number filled in; tap call. (Direct
  dialling needs CALL_PHONE, which Fawkes deliberately does not take.)
- Names are matched by first name ("text Rahul ..."). Multi-word names
  don't resolve yet.
- Brightness needs a one-time "modify system settings" grant; Fawkes
  opens the settings page for it the first time you ask.
- Reminders need exact-alarm permission (Android 12+); Fawkes opens the
  settings page for it the first time you set one.
- The wake word costs battery and Samsung may still kill it — exempt
  Fawkes from battery optimisation (button in Settings). Saying
  "Hey Fawkes" raises a notification; tap it to talk (Android blocks
  apps from opening themselves from the background).
- Custom commands and routines run up to 6 steps each, in order. Steps
  can't call other custom commands (no nesting).
- Hindi: the Hindi model recognises Hindi speech, and the flashlight,
  time and battery commands understand Hindi phrases. The rest of the
  commands are English for now.
- On-device AI: Gemma's licence requires accepting it on Kaggle, so the
  model file is not bundled. Download gemma-2b-it-gpu-int4.bin from
  Kaggle (free account), import it in Settings, then ask away. Answers
  are slow on this phone (30–60 s for the first answer) and need about
  2 GB of free RAM.
