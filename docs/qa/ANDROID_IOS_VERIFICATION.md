# Chaty Android and iOS verification record

**Current state: not yet executed in this session.** The repository's GitHub contents interface cannot attach to the user's local Chrome process or launch their local Android/iOS runtime. Record results only after the corresponding commands/device tests have actually run.

## Android

| Check | Evidence | Result |
|---|---|---|
| Flutter analyze | Command output and commit SHA | Not run here |
| Full Flutter test suite | Test summary and commit SHA | Not run here |
| Debug/release build | Build output, artifact checksum | Not run here |
| Install and cold start | Device/emulator model, Android API, logs | Not run here |
| Auth restore/logout/re-login | Test account and observed state, secrets redacted | Not run here |
| Chat send/receive/edit/reactions/starred | Two controlled test users and verified outcomes | Not run here |
| Voice note/media permissions | Permission state and logcat evidence | Not run here |
| Incoming/outgoing WebRTC audio/video | Two clients, SDP/ICE outcome, actual media evidence | Not run here |
| Background/resume/network loss | Reproduction steps and logcat | Not run here |
| Insets/keyboard/small-large layouts | Screenshots and device dimensions | Not run here |

## iOS

| Check | Evidence | Result |
|---|---|---|
| iOS build | Build output and commit SHA | Not run here |
| Simulator/device launch | Model, OS version, logs | Not run here |
| Auth restore/logout/re-login | Observed state, secrets redacted | Not run here |
| Chat send/receive/edit/reactions/starred | Two controlled test users and verified outcomes | Not run here |
| Camera/microphone/photo permissions | Permission state and native logs | Not run here |
| Incoming/outgoing WebRTC audio/video | Two clients and verified media transport | Not run here |
| Audio interruptions/background/resume | Reproduction steps and logs | Not run here |
| Safe areas/keyboard/dynamic text | Screenshots and device dimensions | Not run here |

## Acceptance rule

A successful build is not a device test. A unit test is not a two-peer WebRTC test. A call screen showing "connected" is not proof of working audio/video; transport state and media tracks must be verified. Any unavailable device, simulator, credential, TURN service, or test account must be recorded as **blocked**, not passed.
