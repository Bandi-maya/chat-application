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

## Observable-behavior inventory (evidence-linked to api @ 3bee780d)

Behavior parity means the same observable outcomes, including error and
suppression paths - not byte-identical source. This section records every
place where the api branch's observable behavior is a throw, a no-op, or a
legacy branch, and what the Kotlin port does, so nothing is dropped silently.

| api behavior (file, function) | Observable outcome on api | Kotlin port decision |
| --- | --- | --- |
| backend_service.dart demo-mode branches (`_isDemoMode`) | Demo fabricates local data; production (api) never enters them | Not ported; api-only behavior preserved |
| backend_service.dart addStory / markStoryViewed (non-demo) | throws UnsupportedError pointing to StatusService | Same contract: status goes through StatusService port only |
| backend_service.dart logCall (non-demo) | throws UnsupportedError (CallSignalingService owns history) | Same contract: CallHistoryService is read-only; M6 signaling owns writes |
| message_transport_compatibility_service.dart (legacy plaintext) | Registered but never called on api; plaintext send path | NOT ported by explicit agreement: MLS fail-closed, no plaintext fallback/downgrade |
| chat_media_service.dart ApiBackendService upload branch | Uses legacy REST upload only when that service is registered+authenticated | Not ported (legacy service unregistered on api); Supabase storage path ported exactly |
| chat_media_service.dart demo:// attachment paths | Returns demo placeholder/URL | Not ported (demo-only) |
| status_service.dart FilePicker / flutter_image_compress | Platform picker and compression library | Kotlin seams: callers supply bytes; compression is an optional seam with the same bypass-on-failure behavior |
| status_service.dart revocation watch (.stream + toasts) | Live status_updates stream with baseline + alert fan-out | Ported core (publish/view/delete); the stream wiring lands with the realtime wiring slice |
| message_automation_service.dart | 30s periodic + on-change sync to GbFeatureBackendService | Pending GbFeatureBackendService port; server-side trigger owns execution meanwhile (same as api backgrounding) |
| local_lock_service / protected_resource_gate / push_token_service | Platform keystore / biometric / FCM | Queued for M8 with platform wiring |
| username_login_service.dart ApiBackendService delegate | Delegates to legacy REST login | Pending decision on ApiBackendService scope (legacy) |

## Behavior tests

Focused unit tests live in app/src/test/java/com/example/chat/ParityBehaviorTest.kt
and run in CI (testDebugUnitTest) before assembleDebug. Each test names the
Dart function whose observable behavior it pins: validators, task
status/priority db asymmetries, message-type mappings, presence publication
rules, privacy publication policy, snapshot codec round-trips and unknown-enum
fallbacks, pending-send queue dedupe/cap, call-history terminal/missed
mapping, media formatting/limits. Tests are added per slice, not deferred to
M9; M9 remains the full parity verification gate.
