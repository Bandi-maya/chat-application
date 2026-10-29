# GBWhatsApp Reference Gap Audit — Kotlin Branch Implementation Plan

**Target branch:** `kotlin`  
**Reference:** supplied GBWhatsApp APK static-analysis report and the repository's existing `GBWHATSAPP_APK_SCREEN_AND_FEATURE_INVENTORY.md`.  
**Scope:** Native Android Kotlin/Jetpack Compose implementation only. Do not port Flutter/Dart files into this branch.

## Rules

1. Make all code and documentation commits directly on `kotlin`. Do not merge or cherry-pick the previous Flutter-oriented PR branch.
2. Preserve the existing Kotlin architecture: `CustomizationController`, `ChatyComponentRegistry`, `ComponentVariantResolver`, `TemplateController`, `ChatyPreferencesController`, MLS E2EE, authorization gates, and the existing Compose screen/test fixtures.
3. Before changing a feature, trace its UI event through controller/state, persistence, runtime consumer, and tests. Reuse existing components and state models; do not add a second parallel settings system.
4. The APK report is static evidence only. Treat strings/resources as candidates, not proof of runtime behavior. Record actual APK walkthrough evidence separately.
5. Do not copy proprietary GBWhatsApp assets/branding or implement privacy/security bypasses, reversible PIN storage, TLS weakening, unauthorized message automation, or claims of identity verification without real cryptographic verification.
6. Do not add live previews to individual settings screens unless the actual reference interaction and the product requirement explicitly require one. Prefer selection state and a static applied-state reference.

## Phased implementation

### P0 — Establish Kotlin baseline and reproducible evidence
- Record the exact `kotlin` HEAD, Gradle/JDK/Android SDK requirements, and existing CI status.
- Run the repository's canonical Gradle compile, unit tests, Android instrumentation tests, lint/static checks, and release build.
- Use existing proof tests and fixtures before adding new test infrastructure.
- Maintain a baseline failure list; distinguish pre-existing failures from regressions.

**Gate:** baseline commands and known failures are documented; no unrelated feature work until compile/test blockers are understood.

### P1 — Customization/settings wiring audit
Inspect the native equivalents in:
- `app/src/main/java/com/example/chat/core/customization/`
- `app/src/main/java/com/example/chat/core/templates/`
- `app/src/main/java/com/example/chat/core/preferences/`
- `app/src/main/kotlin/com/example/chat/PreferencesWiring.kt`
- Compose settings screens and their tests under `app/src/main` and `app/src/androidTest`.

For each setting, trace: visible option → selected state → save/apply → persisted value → runtime consumer → restore after process restart → reset isolation. Check unsupported enum values, duplicate controls, stale defaults, misleading preview labels, and controls that update UI but have no runtime effect.

**Gate:** every audited option has a source path, persistence owner, runtime consumer, and regression test or an explicit unsupported status.

### P2 — Home, tabs, chat list, conversation, composer
- Compare supported layout/template options with the actual Kotlin renderers.
- Verify home/header/tab selection, navigation ordering, chat-list density/avatar/presence/divider settings, conversation bubbles, composer controls, and delivery ticks.
- Verify narrow-screen, font-scale, dark/light theme, rotation/process recreation, and touch target behavior.
- Ensure all supported options render and unsupported values safely fall back without crashing.

**Gate:** tests prove selection, rendering, persistence, restoration, and reset behavior for each supported option.

### P3 — Security, privacy, and account trust
- Trace app lock and protected-route authorization through the native `LocalLockService`/lock coordinator and UI gates.
- Verify encryption claims are distinct from contact identity verification. Never show a hardcoded safety number or “verified” state without a real verification protocol.
- Verify privacy/notification settings are enforced by runtime services and server authorization, not merely hidden in the UI.
- Preserve MLS key handling and server-authoritative revocation/deletion semantics.

**Gate:** security tests cover allowed/denied routes, incorrect credentials, lifecycle transitions, state restoration, and truthful status labels.

### P4 — Messages, media, calls, statuses, notifications
- Trace edit/delete/reply/forward/reaction, media selection/download/export, call lifecycle, status/story, notification controls, and linked-device actions to their authoritative service.
- Confirm idempotency and failure recovery for send/call operations; no success toast before the operation is confirmed.
- Check permissions, cache bounds, retries, offline behavior, and privacy-sensitive logging.
- Do not imitate unsafe mod-only bypasses. Provide legitimate equivalents where the backend and platform support them.

**Gate:** each flow has success, failure, cancellation, permission-denied, offline/retry, and lifecycle coverage where applicable.

### P5 — Reference parity and interaction QA
- Walk the reference APK screen-by-screen on a test device and record screen, gesture, state change, and resulting behavior.
- Compare the same flow against the Kotlin app at matching device size/font scale.
- Use screenshot/pixel tests for stable visual contracts and Espresso/instrumentation tests for interactions; do not treat static resource counts as screen counts.
- Log each mismatch with severity, reproduction steps, expected result, actual result, owner/file, and regression test.

**Gate:** P0/P1/P2 bugs are fixed or explicitly blocked; every claimed parity item has runtime evidence.

### P6 — Release readiness
- Run clean Gradle build, unit tests, instrumentation/proof tests, lint, migration/contract checks, and release APK generation from the exact final Kotlin commit.
- Install the generated APK on an emulator/device and smoke-test cold launch, sign-in/session restore, home, chat, customization persistence, app lock, media, and calls.
- Record APK SHA-256, commit SHA, test reports, known limitations, and rollback path.

**Gate:** no claimed pass without a completed command/report tied to the final commit.

## Audit record template

| Field | Required evidence |
|---|---|
| Screen / control | Exact Kotlin screen and control |
| Reference behavior | Runtime observation or clearly marked static-only inference |
| Current implementation | Compose UI, controller, storage key, runtime consumer |
| Gap / severity | Reproduction steps and user impact |
| Fix | Minimal change using existing architecture |
| Regression | Unit/instrumentation test name |
| Verification | Command, final commit SHA, pass/fail |
| Status | Confirmed / fixed-awaiting-test / verified / blocked |

## Initial focus

Start with the existing Kotlin customization and template registry rather than Flutter files. Prioritize settings that appear selectable but do not affect a runtime consumer, preference values that are not restored, unsupported values that silently map to a different effect, false security/verification labels, and interactions that report success before persistence or server confirmation.
