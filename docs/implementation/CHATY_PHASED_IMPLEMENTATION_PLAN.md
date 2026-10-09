# Chaty Phased Implementation Plan

## Operating method
Work in small vertical slices. Before each slice, inspect current code and tests; after it, run targeted tests, full analyze/tests, inspect diff, and document actual results. Keep work on a feature branch and use reviewable pull requests. Do not attempt a single giant rewrite.

## Phase 0 — Discovery and baseline
- Read README, architecture docs, all relevant current code, migrations, RLS policies, workflows and tests.
- Complete actual APK runtime screen capture when device/emulator is available; mark inferred items clearly.
- Build a route-to-file, feature-to-service, and setting-to-component map.
- Capture baseline analyze/test/build/CI status.
- Deliverables: source map, current known issues, screenshot register, baseline report.
- Exit: no implementation assumptions remain undocumented for Phase 1.

## Phase 1 — P0 correctness and security
- Fix MLS edit path: encrypt edited payload using current MLS group/epoch/device and call `edit_mls_message_v1`.
- Remove direct plaintext update fallback for MLS messages; rollback optimistic state on error.
- Trace and repair story publishing/view tracking and call-history facade no-ops using existing services.
- Verify linked-device revocation is server-backed and invalidates enrollment appropriately.
- Fix hosted Supabase Auth security preflight; do not bypass checks.
- Add tests and rerun Flutter analyze/tests and security checks.
- Exit: all P0 security/correctness tests pass.

## Phase 2 — Typed template foundation
- Add immutable versioned profile, canonical defaults, validator and theme resolver.
- Migrate existing appearance keys without losing values.
- Add repository, preview/apply/reset transaction semantics and unit tests.
- Keep cloud sync out until local correctness is proven.
- Exit: migration, validation, rollback and persistence tests pass.

## Phase 3 — Shared UI component adoption
Implement and test in this order:
1. App shell/navigation/header.
2. Settings rows, switches, color/font/enum pickers, dialogs/sheets.
3. Chat list row, avatar, presence and unread badges.
4. Message bubble, timestamp/date divider, receipt icon, reply preview and reaction.
5. Composer, attachment tray and voice controls.
6. Media/document/audio rows and previews.
7. Status list/viewer/composer.
8. Call history and call-screen presentation.
9. Profile cards, menus and remaining dialogs.
Each step must use profile tokens and preserve domain state.
- Exit: every exposed style has at least one real consumer and cross-screen regression coverage.

## Phase 4 — Settings and template UX
- Build category navigation and search.
- Build preset gallery, real component preview, custom profile save/duplicate/rename/delete.
- Implement category/full reset, validated import/export, migration warnings and recovery.
- Add optional cloud sync only with account-scoped RLS and documented conflict behavior.
- Exit: settings end-to-end tests and accessibility review pass.

## Phase 5 — Feature parity slices
Use the APK-derived inventory and current Chaty source to classify every item. Prioritize legitimate features:
- Messaging actions and offline reliability.
- Media/document and voice-note lifecycle.
- Status publish/view/expiry.
- Call lifecycle and history.
- Account/privacy/linked devices.
- Search, archive, pin/star, groups and notifications.
Do not add a setting if the actual backend/platform behavior is unsupported. Each feature gets a separate traceability row, tests and review.
- Exit: all approved requirements implemented or explicitly marked unsupported/out-of-scope.

## Phase 6 — QA, performance and release hardening
- Run unit/widget/golden/integration tests and Supabase migration/RLS checks.
- Test Android and iOS insets, permissions, lifecycle, audio routes, notifications, calls and recording.
- Profile long message histories and rapid settings changes.
- Audit secrets/logging, imported files, account switching and offline recovery.
- Fix CI security preflight and required workflows.
- Exit: all P0/P1 acceptance criteria pass; known limitations documented; release candidate approved.

## Phase sequencing rules
- Do not start broad screen refactors before the template profile and shared component contracts are stable.
- Do not enable cloud sync before local migration/atomic apply tests pass.
- Do not claim iOS parity without an iOS build and device/simulator verification.
- Keep every phase independently reviewable and revertible.
