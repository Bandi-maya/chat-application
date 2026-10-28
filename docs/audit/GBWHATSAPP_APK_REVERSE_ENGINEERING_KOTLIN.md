# GB WhatsApp APK Audit — Kotlin Branch

**Target repository:** `Bandi-maya/chat-application`  
**Target branch:** `kotlin`  
**Reference APK supplied by user:** https://drive.google.com/file/d/1u-Q66KSNLA7E67Q-6TybmRRwO83yPLk7/view?usp=drivesdk  
**Status:** Not yet inspected in this environment. Do not record observations until the APK is downloaded, installed, and interacted with.

## Mission

Use the user-supplied GB WhatsApp APK as a behavioral and visual reference for Chaty's native Kotlin/Jetpack Compose branch. Inspect the actual installed APK version; do not infer details from other versions, screenshots, or assumptions. Implement supported equivalent behavior in Chaty while preserving existing functionality, encryption, backend routing, app identity, data, and security guarantees.

This document is a work checklist, not evidence that reverse engineering, implementation, or runtime verification has already happened.

## Non-negotiable safeguards

- Preserve all current changes and user data. Never reset, force-push, delete unrelated files, or change package identity/signing to work around problems.
- Inspect the current `kotlin` branch status and latest commit before editing. Keep a clean, auditable diff and use a separate working branch if appropriate.
- Do not copy proprietary source code, credentials, private API secrets, protected assets, or bypass authentication/security. Inspect behavior and public/authorized interfaces only.
- Do not activate historical or dead backend paths just to imitate a reference. Use the active Kotlin backend, MLS fail-closed paths, and existing security architecture.
- Do not create a second message input. Emoji selection must insert at the cursor in the existing composer and preserve draft text.
- Do not show a live preview unless the reference's actual interaction does so.
- Do not claim any feature passed without runtime evidence. Do not produce a release APK until the implementation and required checks are complete.
- If the Drive file cannot be fetched by the execution environment, ask for the APK to be attached/uploaded into the coding environment or made downloadable without account access; continue auditing repository code meanwhile.

## Stage 0 — Confirm the APK and baseline

1. Download the exact user-supplied APK using an authorized method.
2. Record file name, byte size, SHA-256, package ID, version name/code, min/target SDK if accessible, and APK split status.
3. Scan with appropriate local security tooling; do not execute unknown binaries outside an isolated emulator.
4. Install it in an isolated emulator/profile. Record Android version, screen size/density, locale, permissions, network conditions, and test-account constraints.
5. Inspect the current `kotlin` branch, working tree, relevant recent commits, CI workflows, and existing docs including `docs/kotlin-parity.md`, `docs/KOTLIN_CONFIGURATION.md`, `docs/EXISTING_FEATURE_INVENTORY.md`, `docs/FEATURE_PRESERVATION_LEDGER.md`, `docs/QA_TEST_MATRIX.md`, and `docs/settings-wiring-matrix.md`.
6. Run baseline unit/instrumented tests and a build before changing code. Save exact commands and results.

## Stage 1 — Inspect every relevant reference screen and interaction

Record screenshots, screen hierarchy/accessibility information where available, and step-by-step interaction outcomes for every accessible in-scope screen. Tap each visible control; record navigation, selection, persistence, error states, permissions, animation, and back/cancel behavior.

Inventory at least:
- Main chats, tabs, navigation, search, app bar, drawers/rails, overflow menus.
- Main settings and all settings subpages.
- Privacy: last seen, online, typing, recording, read receipts, blocked contacts, contact-specific rules where present.
- Notifications, notification channels, badges, online alerts if present.
- Themes, layouts, templates, wallpaper/background, color picker, fonts, selection/apply/cancel/reset behavior.
- Message composer, emoji picker, emoji style selection, stickers/GIFs, cursor behavior, attachments, voice recording/playback/cancel/lock-to-record.
- Camera, gallery, image/video preview, capture, retake, send flow.
- Voice/video calls, incoming/outgoing/connecting/reconnecting/ended states, call controls and history.
- Screen sharing and its Android system consent flow if present and accessible.
- Location/map sharing and permission/error behavior.
- Chat lock, PIN/pattern/biometrics, lock timeout, recovery and restart behavior.
- Contact/group information, media/document viewers, storage/backup where present.
- Loading, empty, offline, denied-permission, network-error, and confirmation states.

If a screen is absent, inaccessible, or requires an unavailable service/account, mark it **NOT OBSERVED** or **BLOCKED** and explain why. Never fabricate parity.

## Stage 2 — Compare against Chaty Kotlin

Create a component-by-component matrix with columns:
`Reference screen/action | Observed result/evidence | Kotlin screen/component/file | Current behavior | Difference | Proposed change | Backend/platform dependency | Test case | Status`.

Map shared behavior to existing Kotlin/Compose components and preference/state owners before coding. Extend existing components rather than introducing duplicates. Inspect settings wiring and feature-preservation records before altering preference keys or data models.

## Stage 3 — Priority implementation

1. Build stability, crashes, data integrity, security regressions.
2. Shared settings and persisted customization state.
3. Theme/layout/template/color selectors, with reference-matched preview behavior.
4. Existing message composer and emoji insertion/rendering; recording lifecycle.
5. Privacy and presence/typing behavior using real supported backend state.
6. Notification configuration and supported notification flows.
7. Camera, attachments, maps/location.
8. Calls, connection state, screen sharing only when supported by real service/API infrastructure and Android permissions.
9. Chat lock/security.
10. Motion/3D-inspired interactions and accessibility polish.

For each change: inspect source → implement the smallest compatible change → add/update tests → run the relevant tests → launch in emulator → exercise the interaction → inspect logs/screenshots → test adjacent flows for regressions.

## Design and accessibility research

Use current official guidance:
- Apple Human Interface Guidelines: https://developer.apple.com/design/human-interface-guidelines/
- Apple accessibility guidance: https://developer.apple.com/design/human-interface-guidelines/accessibility
- Android accessibility principles: https://developer.android.com/guide/topics/ui/accessibility/principles
- Android accessibility guidance: https://developer.android.com/guide/topics/ui/accessibility

Apply relevant principles such as clear hierarchy, consistency, system feedback, error prevention, recognition over recall, Fitts's Law, Hick-Hyman Law, Gestalt grouping, cognitive-load reduction, adequate touch targets, screen-reader semantics, text scaling, non-color-only status indicators, contrast, reduced motion, and resilient permission/error states. Respect Android platform conventions when implementing the native Kotlin app; Apple guidance is a design reference, not a reason to break Android expectations.

For every meaningful UI change, document the observed problem, reference evidence, principle, implementation, accessibility effect, risk, and verification.

## Required test evidence

- Baseline and final build command/output.
- Unit test and instrumented test counts with failures/skips.
- Emulator screenshots for the key reference and Chaty states.
- Interaction results for each inspected component.
- Preference persistence after navigation and process/app restart.
- Permission-denied, offline, retry, and lifecycle cases where relevant.
- Logs for crashes, exceptions, layout overflow, permission failures, network failures, and resource cleanup.
- Regression checks for chat, authentication, backend routing, MLS encryption, attachments, and calls.
- Confirm no unintended change to app ID, signing, data, environment secrets, backend schema, or protected encryption paths.

## Completion status definitions

- **PASS** — implemented and verified with recorded evidence.
- **PARTIAL** — some behavior works; acceptance criteria remain.
- **BLOCKED** — requires a missing APK access path, account, permission, API, service, or device capability.
- **FAIL** — tested and not working.
- **NOT VERIFIED** — evidence is insufficient.

## Required final deliverables

1. `docs/audit/GBWHATSAPP_APK_SCREEN_INVENTORY.md`
2. `docs/audit/GBWHATSAPP_CHATY_COMPARISON_MATRIX.md`
3. `docs/audit/GBWHATSAPP_UI_UX_FINDINGS.md`
4. `docs/audit/GBWHATSAPP_IMPLEMENTATION_LOG.md`
5. `docs/audit/GBWHATSAPP_VERIFICATION_MATRIX.md`
6. Updated feature-preservation ledger and Kotlin parity record, only where supported by verified evidence.
7. Final report listing files changed, tests run, verified functionality, remaining defects, and blockers.

Do not write the deliverables as completed until their evidence exists. Work through the loop until all feasible acceptance criteria pass or each remaining blocker is documented.