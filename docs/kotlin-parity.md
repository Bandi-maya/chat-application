# Flutter to Kotlin parity record

Source: `api@3bee780d`. Target: `kotlin`. This replaces the early slice checklist, which incorrectly described the active custom backend as pending legacy work. It is a code and evidence inventory, not a claim of fully proven runtime equivalence.

## Implemented source areas

- Native Compose auth, org bootstrap, shared post-auth session adoption, secure-session persistence, JWT fallback, custom REST and WebSocket backend.
- Tenant selection and public client routing configuration from root `.env` and Gradle/environment overrides. See `KOTLIN_CONFIGURATION.md`. No real environment values are committed. Rebuild to apply them.
- Chat home, navigation styles, search, conversation/detail/contact views, selection, reply, complete forwarding envelope, unread/badge paths and connection/block state.
- Rust MLS core through JNI and fail-closed message paths. No plaintext fallback was added.
- Contact permission gate, picker, quick contact rail and contact details; document picker/reader, profile crop/compression, media paths, voice recording and inline voice playback.
- WebRTC call plumbing, call signaling, permission gates, call notification/foreground service, wake/Wi-Fi locks and lifecycle callbacks.
- Settings and persisted-value consumers, privacy rules, app lock setup/reload flow, theme/navigation selectors, emoji styles/assets/licenses and native island bridge/icons.
- FCM token registration/startup seam. Firebase provisioning remains user-deferred, not silently enabled or presented as verified.

These statements describe implemented code, not live two-party, physical-device or exhaustive visual proof. The UI uses native Android equivalents and must not be called a pixel-perfect copy based on unit tests.

## Reachability and intentional non-activation

The source uses the custom ApiBackendService for active chat/auth paths. Historical Supabase services, status streams, recovery paths and demo-only paths are not a license to activate otherwise unreachable behavior in Kotlin. The custom backend's capabilities and the source's unsupported/no-op paths remain the boundary. Shared backend/schema and api/main branches are not changed.

The source Contact Info search action closes the view and returns a result that its caller ignores; Kotlin keeps that reachable outcome. Source utilityBar resolves to compactRail; the native large-screen rail selection now includes that mapping.

## Evidence checkpoint

The build at `6c609002a3b393cb01090faf466441dbe6d45037` passed exactly 783 unit tests in 73 suites, with zero failures, errors or skips, and produced an APK. Its runtime smoke also passed 20 instrumented tests with zero failures/skips. Two saved-synthetic-session cold starts used changed PIDs (2729 -> 2836), zero fatal errors, and restart rc=0.

The preceding emulator run at `eb4fd2ff` executed 20 tests: 19 passed, one failed because Contact Info's toolbar/title was absent. Actual screenshots showed its body had opened. The same run cold-started twice with changed PIDs and no fatal error, using a synthetic saved session. Follow-up toolbar, bottom inset and atomic connection-state fixes in `6c609002` are now verified in the emulator captures: Contact Info title/back is visible, the unaccepted state is not falsely verified on an authentication error, and Home dock/FAB is above system navigation. This establishes this synthetic scenario, not exhaustive design parity.

The cumulative environment/tablet build `14eae12ed1aa95652da3050322a01988bd7a20fc` passed exactly 786 unit tests in 74 suites, with zero failures, errors or skips; APK assembly succeeded. This includes root `.env` build wiring, three new routing/tenant/WebSocket tests, and the utilityBar tablet mapping/predicate correction. Its runtime run also passed 20 instrumented tests with zero failures/skips, two saved-synthetic-session cold starts (PIDs 2690 -> 2796), zero fatal errors and restart rc=0. Fresh pixels show seeded ChatDetailHost messages/composer, Contact Info back/title and unaccepted error state, and Home dock/FAB above system navigation. Tablet mapping is source/unit checked, not a complete tablet visual proof.

Current evidence links:
- Build/unit: https://github.com/Bandi-maya/chat-application/actions/runs/40298889568/job/121491604694
- Runtime/pixel capture: https://github.com/Bandi-maya/chat-application/actions/runs/40298889568/job/121495664506

This is a source-ready-for-configured-testing checkpoint, not a statement that every live/backend/device/design behavior below has been verified. Documentation-only evidence updates do not change the tested code.

## Still unverified, not silently dropped

- Live backend login, two-party messaging/upload/receipts and production MLS transport against the user's configuration.
- Physical camera/video effects, real microphone/AAC playback, audio routes and background/foreground calling behavior.
- Remote emoji fetch/loading on a device with normal network access.
- Exhaustive screen-by-screen design comparison, including tablet layouts.
- Conditional presence/typing refresh timing: the host currently has a three-second revision tick. The source-dead Supabase plane was not activated merely to remove this conditional timing difference.

Tests use synthetic emulator accounts, fake transports and seeded data. They do not change real users, sign up, send live messages/calls/uploads, or disclose real contacts.

## Installation and handoff

Application ID/version remain unchanged. The older delivered debug APK and the later CI APK have different signing certificates, so an in-place update is not established. Do not uninstall, clear data or change identity to hide that issue. The user now wants source completion first, will supply their own environment and rebuild/test. That does not waive truthful verification limits or imply their installed app has no saved data.


## Flutter-only cleanup

The user explicitly approved removal now in a direct reply after being told the shared Rust encryption/bridge dependency would remain. Exactly 779 reviewed tracked paths were removed from kotlin: unused Flutter app Dart/tests/platform runners, Dart package metadata/wrappers/examples/hooks, and ten Flutter-specific build/patch workflows. No unreviewed wildcard removal of legacy tools/QA/history was done. Root README now names the Kotlin entrypoint and native build/setup route.

The cleanup net diff was checked against the explicit manifest. Native app/, mls-native/, shared Rust engine, backend/schema, assets and native ci/ trees were unchanged by deletion; api/main were unchanged. The shared Rust crate at third_party/openmls_android/rust is used by mls-native and currently requires FRB annotations/generated code. It and its licenses/notices are intentionally retained. This does not mean a Flutter application is still required to build :app. Preference compatibility identifiers and historical source references are also retained.

Post-cleanup verification passed at code head `4d5bb180c4d952ed1b6ecae6d3b52ef4ecaaae6c`, run 40304449978: native MLS/UniFFI generation, exactly 786 unit tests in 74 suites (zero failures/errors/skips), APK assembly, 20 instrumented tests (zero failures/skips), and two saved-synthetic-session cold starts (PIDs 2694 -> 2799, zero fatal errors, restart rc=0). Fresh actual pixels were inspected: configured/restarted Home dock and FAB sit above system navigation, seeded ChatDetailHost renders messages/composer, and Contact Info shows back/title and unaccepted/auth-error state.

- Post-cleanup build: https://github.com/Bandi-maya/chat-application/actions/runs/40304449978/job/121509983384
- Post-cleanup runtime/pixels: https://github.com/Bandi-maya/chat-application/actions/runs/40304449978/job/121515226361

The root README follow-up and this evidence update are documentation-only. The cleanup outcome is complete; configured live-backend/device/design validation remains separate and is not established by the synthetic tests. No environment secrets, backend, encryption logic, application ID, signing certificate or installed app data were changed.


## User-supplied routing defaults (later requested change)

The user supplied and confirmed centralized public defaults: REST_API_BASE_URL=https://api.hub.logybyte.in, WEBSOCKET_URL=wss://api.hub.logybyte.in/api/chat/ws{userId}, TENANT_ID=chat-application. BackendRoutingPolicy.kt is the single code location. Nonempty root .env values override build properties/environment, which override these defaults; saved tenant selection retains its behavior. The WebSocket placeholder is substituted literally with no added slash, as confirmed. This supersedes earlier emulator-default notes in the historical evidence sections.

Synthetic emulator CI writes explicit loopback REST/WebSocket values before its build, so fake sessions do not reach the public server. No live login, message, upload, call or backend mutation was performed. Verification of this later configuration change is pending separately from the completed cleanup run.
