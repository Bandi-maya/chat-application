# Flutter to Kotlin port - parity tracking

Source of truth: `api` branch @ 3bee780d (Flutter). Target: `kotlin` branch (native Android, Jetpack Compose).
Inventory RECONCILED against origin/api @ 3bee780d exactly (475 Dart files, 332 lib, 95 test, ~85 screens): the counts below are api's own. api and themes differ in only 38 files (backend services, auth screens, preferences, native scaffolding).

## Subsystem inventory (Flutter source)
- features/settings: 48 files (largest surface: studios, selectors, call settings)
- features/media: 18 | features/camera: 11 | features/auth: 11
- features/chats: 10 | features/messages: 9 | features/calls: 8
- features/island: 8 | features/profile: 4 | features/automation: 4
- features/tasks: 3 | features/ui_lab: 3 | features/updates: 2 | features/search: 2
- features/vault, subscription, onboarding, notifications: 1 each
- data/services: 32 (backend, MLS, calls signaling, snapshot cache, push, etc.)
- core: emoji 12, audio 9, design_system 7, services 3, media 1
- Backend: supabase/ (schema + RPCs, reused unchanged - port is a client rewrite)
- Native engines: third_party Rust MLS (openmls via flutter_rust_bridge) -> JNI reuse planned; flutter_webrtc -> native WebRTC

## Slice milestones
- [x] M0: repo private; kotlin branch from api @ 3bee780d
- [x] M2: Gradle/Compose skeleton (settings.gradle.kts, app module, manifest with chaty:// callbacks, Compose shell, baseline theme)
- [x] M1: exact api tree reconciliation (475 dart / 332 lib / 95 test); per-screen matrix rows continue as slices land
- [ ] M3: auth + session (GoTrue REST, chaty:// login-callback/reset-password)
- [ ] M4: chats home + conversation + messaging core (Supabase REST/Realtime)
- [ ] M5: MLS E2EE (Rust core via JNI)
- [ ] M6: calls (native WebRTC)
- [ ] M7: theming/customization engine (bubbles, ticks, entry styles, icon packs)
- [ ] M8: settings (48 files), media, camera, notifications, misc
- [ ] M9: parity verification + APK

## Rules
- Behavior parity over shell parity: a slice is done when Flutter behavior works equivalently, not when a placeholder exists.
- No unrelated changes; supabase/ and shared backend stay untouched.
- No "exact copy" claim until M9 verification passes.
