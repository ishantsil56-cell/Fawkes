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
