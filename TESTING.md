# Fawkes testing checklist

Test on the Galaxy F17 5G after every command lands — the emulator mic
doesn't reflect reality. Tick before every release tag.

## Phase 0 — scaffold

- [ ] CI builds a debug APK on push to main
- [ ] APK installs on the F17
- [ ] First launch asks for microphone permission
- [ ] First-run model download: progress shows, completes, retry works if it fails
- [ ] Fresh install with app data cleared repeats the first-run flow cleanly
- [ ] Low-storage warning shows when free space is under 150 MB
- [ ] Mic button with no model opens the download screen
- [ ] Mic button with model installed logs the stub matcher result

## Per command (Phase 1+)

- [ ] Works in a quiet room
- [ ] Works with the TV on
- [ ] Fails gracefully with no permission
- [ ] Fails gracefully with no match
- [ ] Spoken confirmation plays

## Phase 1 — core loop (0.3.0)

- [ ] Tap mic → speak "flashlight on" → torch turns on, voice confirms
- [ ] "open YouTube" opens the app; "open it again" reopens it
- [ ] "set alarm for 7 AM" creates the alarm; "set a timer for 10 minutes" starts one
- [ ] "call Maa" opens the dialer with the right number; "call him" reuses it
- [ ] "what time is it", "battery level", "what is 45 times 12" all answer
- [ ] "note buy milk" then "read my notes" reads it back
- [ ] "volume up/down", "brightness up" (grant the settings permission when asked)
- [ ] "open Wi-Fi settings" opens the panel
- [ ] "text Rahul on my way" opens the compose screen prefilled
- [ ] "WhatsApp Rahul hi" opens the WhatsApp chat prefilled
- [ ] Listening stops on its own after ~12 s of silence

## Phase 2 — hands-free + polish (0.3.0)

- [ ] Wake word on in Settings → "Hey Fawkes" raises the tap-to-talk notification
- [ ] "remind me in 1 minute to test" fires a notification in a minute
- [ ] Reminder survives a reboot (re-scheduled by the boot receiver)
- [ ] "play / pause / next" controls the music app
- [ ] "SOS" blinks the torch in Morse, three cycles; "stop SOS" cancels it
- [ ] Custom command: create "good night", say it, both steps run
- [ ] Widget on the home screen opens Fawkes listening; last-heard updates
- [ ] Quick Settings tile opens Fawkes listening
- [ ] Settings: voice speed changes the spoken rate; dark theme follows the system

## Phase 3 — smart layer (0.3.0)

- [ ] Routine: create "good morning", say it, steps run in order
- [ ] Hindi model downloads; "टॉर्च चालू करो" turns the torch on
- [ ] "call him" / "text her" resolve to the last contact without a name
- [ ] "ask …" with no model explains how to import one
- [ ] "ask …" with a Gemma model imported answers (slow — allow a minute)

## v0.3.1 — bug fixes

- [ ] Wake word on → "Hey Fawkes" → "flashlight on" → torch turns on, no tap
- [ ] After a background command, the next "Hey Fawkes" works again
- [ ] Tapping the wake notification still opens the app listening (fallback)
- [ ] Continuous listen on in the app → commands chain without retapping
- [ ] Continuous listen off → one command, then it stops (old behaviour)
- [ ] "ask what is the capital of France" gives a short coherent answer
  (with the Gemma 2B IT model — a base model will still ramble)
- [ ] Wake word survives overnight: battery exemption + Never sleeping apps set

## v0.3.2 — listening accuracy

- [ ] A command the small model mishears still runs when an alternative
  guess matches (log shows `[matched "..."]`)
- [ ] Better-accuracy English model downloads (~128 MB) and becomes active;
  home screen shows "English (better accuracy)"
- [ ] Settings switch toggles between standard and better-accuracy models
- [ ] Commands are noticeably more reliable on the lgraph model in a quiet room
- [ ] Wake word still works with the lgraph model active
- [ ] Low-storage warning shows when free space is under 450 MB for the
  lgraph download
