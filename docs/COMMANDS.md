# Fawkes commands

Every supported phrase, kept up to date. Phase 0: none yet — the matcher is
a stub. Phase 1 adds these 12, one at a time, flashlight first.

| # | Command | Example phrases | Status |
|---|---|---|---|
| 1 | Flashlight | "flashlight on / off", "torch on" | planned |
| 2 | Open app | "open YouTube", "open WhatsApp" | planned |
| 3 | Alarm | "set alarm for 7 AM" | planned |
| 4 | Timer | "set a timer for 10 minutes" | planned |
| 5 | Call | "call Maa" | planned |
| 6 | Time / date / battery | "what time is it", "battery level" | planned |
| 7 | Calculator | "what is 45 times 12" | planned |
| 8 | Note | "note: buy milk" / "read my notes" | planned |
| 9 | Volume / brightness | "volume up", "brightness down" | planned |
| 10 | Wi-Fi / Bluetooth panel | "open Wi-Fi settings" | planned |
| 11 | SMS | "text Rahul: on my way" | planned |
| 12 | WhatsApp (limited) | "WhatsApp Rahul: hi" | planned |

Notes:

- Wi-Fi / Bluetooth can't be toggled directly by third-party apps since
  Android 10 — Fawkes opens the system settings panel instead.
- WhatsApp blocks full automation — Fawkes opens WhatsApp with the text
  pre-filled; you tap send.
- Unrecognised phrases are logged on screen; the log becomes the Phase 2
  feature list.
