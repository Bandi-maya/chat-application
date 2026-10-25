# Flutter to Kotlin port - parity tracking

Source of truth: `api` branch @ 3bee780d (Flutter). Target: `kotlin` branch (native, Jetpack Compose).

## Slice milestones
- [x] M0: repo private, kotlin branch from api
- [ ] M1: api inventory + this matrix filled per screen/service
- [ ] M2: Gradle/Compose skeleton (this commit)
- [ ] M3: auth + session (Supabase GoTrue, chaty:// callbacks)
- [ ] M4: chats home + conversation + messaging core
- [ ] M5: MLS E2EE (reuse Rust core via JNI bindings)
- [ ] M6: calls (native WebRTC)
- [ ] M7: theming/customization engine
- [ ] M8: settings, notifications, camera, misc
- [ ] M9: parity verification + APK

## Rules
- Behavior parity over shell parity: a slice is done when the Flutter
  behavior works equivalently, not when a placeholder screen exists.
- No unrelated changes to shared/backend code (supabase/ untouched).
