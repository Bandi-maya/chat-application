# Phase 8 — Architecture Cleanup, Security Review, Release Gate

Base: `production-hardening-2026-08-22` @ `7d887c3d074304cfc591b77c9e26b396585d4fa3`

## Scope
- Replace production dependency on the `MockDataStore` compatibility name/fallback guest semantics with a production repository/facade while keeping test mocks under tests only.
- Complete per-function `SECURITY DEFINER` review and adversarial authorization matrix; do not mass-change intentional capability RPCs.
- Finish migration/schema replay parity and source-controlled migration manifest.
- Complete production audit, feature-trace matrix, settings-runtime matrix, database access matrix, and threat-model status.
- Run repository-wide static analysis, tests, migration checks, security checks, release-mode APK verification, artifact hash verification, and final no-mock/no-placeholder/no-debug-release scans.
- Integrate only already-green Phases 1–7; resolve conflicts by preserving each phase's tested behavior, never by dropping a phase's security/functional changes.

## Primary file ownership
Repository facade/DI naming cleanup, release/security tooling, audit documentation, CI/release workflows, migration reconciliation metadata, and release-only tests.

Avoid feature redesign. Phase 8 is the integration/release gate, not a place to hide unfinished product work.

## Acceptance gates
1. Production dependency graph contains no mock repository/fallback guest path.
2. Fresh schema replay matches production except documented environment configuration.
3. Privileged RPC matrix has an explicit justification and hostile-identity verification for each client-executable function.
4. All Phase 1–7 PRs are green and conflict-reviewed before integration.
5. `flutter analyze`, full tests, security/replay checks, no-mock scan, and a release-mode APK build pass on the integrated head.
6. A production APK is built separately with a persistent private signing key, a real non-`com.example.*` application ID, and production TURN credentials; CI verification signing must never be presented as store-ready signing.
7. Final merge to `main` is serialized with expected-head SHA protection and only after the integrated branch is green.

## Current verification notes
- Production presentation code now uses `ChatyDataStore`; unauthenticated guest fabrication is rejected.
- The no-production-mocks guard now rejects QA/demo call execution paths instead of permitting them behind `kDebugMode`.
- The production call screen was repaired after the first deterministic QA-removal pass exposed malformed UI brackets; the corrected source now passes analyzer on the hardened Phase-8 gate.
- The trusted-facade base runner applies QA cleanup before executing the strict no-mock guard, removing the previous intermediate-state ordering failure.
- Android release tasks now fail closed by default when production signing or a real package ID is missing. `CHATY_ALLOW_CI_DEBUG_SIGNING=true` is limited to explicit release-mode verification jobs.
- The `signed-production` workflow requires the persistent keystore, `CHATY_APPLICATION_ID`, and TURN credentials before it can emit `Chaty-production.apk`.
- Phase 8 still cannot reach 100% until Phases 1–7 are individually accepted, the integrated head passes the same gates after conflict resolution, and the signed-production job is executed with real release configuration.
