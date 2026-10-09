# Chaty runtime bug register

**Status:** Initial runtime audit has not yet been executed against the user's live Chrome session or Android/iOS runtimes. Do not treat this template as evidence that there are zero defects.

## Issue record template

Copy this section for every reproducible issue.

### BUG-XXX — Short description

- Severity: P0 / P1 / P2 / P3
- Platform: Chrome / Android / iOS / shared
- Screen and route:
- Current commit:
- First observed at:
- Preconditions/test account:
- Exact reproduction steps:
  1.
  2.
  3.
- Expected behavior:
- Actual behavior:
- Runtime error and stack:
- Network request/response (redacted):
- Native logs/device details:
- Root cause:
- Fix and files:
- Regression test:
- Retest evidence:
- Status: Open / Fixed-awaiting-retest / Verified / Blocked
- Remaining risk:

## Severity definitions

- **P0:** Security/privacy breach, authorization bypass, encryption failure, data corruption, or widespread startup crash.
- **P1:** Authentication, messaging, calls, or navigation core flow unusable; unrecoverable crash or loss of user data.
- **P2:** Major screen/component malfunction, settings not persisted, repeated runtime exceptions, or platform-specific regression.
- **P3:** Minor visual, accessibility, responsiveness, or performance issue.

## Triage rules

1. Record the original failure before applying a fix.
2. Identify whether it is an app exception, failed backend operation, platform/plugin failure, permission denial, transient connectivity event, or harmless development-only warning.
3. Do not mark fixed until the same reproduction steps pass and a regression test is added where feasible.
4. Do not suppress errors or add fake-success fallbacks to make the audit green.
5. Keep credentials, tokens, cookies, message plaintext, and encryption material out of the report.

## Known issue from repository audit

### AUTH-BOOTSTRAP-001 — Authenticated shell selected before backend hydration

- Severity: P1
- Platform: Shared Flutter application startup
- Screen and route: Root MaterialApp home
- Root cause: The root route previously treated a non-null Supabase current session as sufficient to render MainNavigationShell before ChatyBackendService.initialize() completed profile/session hydration.
- Fix: Root route now uses an explicit AuthBootstrapDestination policy and shows a loading/retry surface until backend initialization completes. Raw backend error details are logged locally but not shown to the user.
- Regression test: test/auth_bootstrap_policy_test.dart covers initialized/uninitialized and authenticated/unauthenticated combinations.
- Status: Static analysis and the full Flutter test suite passed on commit 29f570f476678c554896859bf4569a9551b96df3. Cold-start/session restoration still requires the pending Android/iOS runtime smoke and physical-device verification.

### ANALYZER-001 — Unused legacy menu helpers and disconnected responsive overflow sheet

- Severity: P2
- Platform: Shared Flutter UI
- Files: lib/features/chats/chat_detail_screen.dart, lib/features/chats/chats_home_screen.dart
- Evidence: The Android verification workflow's Dart analyzer reported an unused chat popup helper, an unused responsive overflow flag, and an unused home overflow sheet.
- Root cause: The home overflow sheet had been implemented but not connected to the three-dot menu; the desktop popup's Starred Messages item only displayed a snackbar. A legacy chat menu helper was no longer used after migration to ChatyMenuSheet.
- Fix: Wire widths below 600 logical pixels to the existing scrollable overflow sheet, route the desktop Starred Messages item to the real screen, and remove the obsolete helper.
- Regression coverage: The source inventory CI remains active; fresh Flutter analyze and UI interaction tests must confirm the change.
- Status: Dart analysis and the full Flutter test suite passed on commit 29f570f476678c554896859bf4569a9551b96df3. A visual interaction retest on Android/iOS is still pending.

### UI-SETTINGS-001 — Advanced appearance controls had no runtime consumer

- Severity: P2
- Platform: Shared Flutter UI
- Evidence: The runtime settings consumer audit reported ModCallsBackground, ModCallsIconColors, ModCallsTextColor, ModChatBubbleText, ModChatBubbleTextLeft, date_left_color, date_right_color, and text_size_pick as catalog-only settings.
- Fix: MessageBubble now reads the current preference controller for outgoing/incoming text color, timestamp color, and message text size. OngoingCallScreen now reads the call background, text, and icon color settings and applies them to the call surfaces and controls.
- Regression coverage: Runtime consumer audit plus Android Flutter analysis/full tests.
- Status: Runtime consumer audit, Dart analysis, and the full Flutter test suite passed on commit 29f570f476678c554896859bf4569a9551b96df3. Visual confirmation on running Android/iOS screens remains pending.

### CI-INTEGRATION-001 — Phase-specific tests ran against a non-integrated feature branch

- Severity: P2
- Platform: CI
- Evidence: The production gate tried to run encrypted-attachment, encrypted-outbox, account-purge, and phase-specific tests that are only copied in by the integration/phases-1-8 reconciliation step. That reconciliation is intentionally skipped for this feature branch, so the workflow failed with missing test files.
- Fix: Gate those phase-specific test steps on the integration/phases-1-8 branch while keeping current-branch analysis, the full available Flutter test suite, migration checks, security checks, and APK build enabled for ordinary PRs.
- Status: Verified in the latest completed integration-gate analysis and full Flutter test steps on commit 29f570f476678c554896859bf4569a9551b96df3; release APK build was still running when this record was updated.
