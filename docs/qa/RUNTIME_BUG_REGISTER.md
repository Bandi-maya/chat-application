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
- Status: Fixed-awaiting-retest. Latest Android CI must pass before marking verified; cold-start/session restoration still requires runtime testing on Android and iOS.
