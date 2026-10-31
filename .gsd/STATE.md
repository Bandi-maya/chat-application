---
updated: 2026-10-31T23:00:00+05:30
---

# Project State

## Current Position

**Milestone:** Chaty Kotlin Modernization  
**Phase:** 1 — Baseline and build recovery  
**Status:** executing  
**Plan:** 1.1 — Repair current Compose compilation errors

## Last Action

- Read-only audit of `kotlin` at baseline `5c58b2d1570b8e7ff6413c9f485af66428e400f6`.
- Confirmed current GitHub Actions run 41224280230 fails in `:app:compileDebugKotlin`.
- Exact compiler errors are in `BundleOngoingCallView.kt`: duplicate non-repeatable `@Composable` annotation at line 637 and missing `@Composable` on `ControlIconButton` at line 931.
- Native MLS build and UniFFI binding generation completed successfully before Kotlin compilation failed.
- No application tests or runtime smoke ran in that failed workflow because compilation stopped first.
- Finalized `.gsd/SPEC.md`.

## Next Steps

1. Apply the minimal annotation correction in `BundleOngoingCallView.kt`.
2. Run the Kotlin APK workflow and inspect its exact job logs.
3. Continue only after compilation succeeds; then run unit tests, instrumentation compilation and runtime smoke.
4. Update issue register, implementation progress and this state with exact commit/run evidence.

## Active Decisions

| Decision | Choice | Made | Affects |
|---|---|---|---|
| Target branch | Work directly on existing `kotlin` branch per Kotlin-specific plan | 2026-10-31 | All phases |
| Architecture | Keep Kotlin, Jetpack Compose, custom REST/WebSocket, Rust MLS/JNI | 2026-10-31 | All phases |
| Change strategy | Minimal vertical slices; preserve all features and preference keys | 2026-10-31 | All phases |
| Verification | CI/runtime evidence required; synthetic tests are not live-backend proof | 2026-10-31 | All phases |

## Blockers

- [ ] Current Compose compilation failure: duplicate annotation and missing composable annotation in `BundleOngoingCallView.kt`. Minimal fix planned.
- [ ] Physical-device/live-backend verification requires user configuration and must not be simulated.

## Concerns

- Existing documentation contains historical Flutter-only paths; always verify native Kotlin implementation before applying a plan.
- The branch has extensive pending runtime/security/UI audit findings; do not treat an issue register entry as a reproduced live defect without its evidence class.
- Preserve app ID, signing identity, user data, encryption and server authorization.

## Session Context

The repository already contains substantial PRDs, UI/UX references, architecture, QA matrices, feature inventory and Kotlin parity evidence. Reuse and reconcile these documents instead of creating duplicate competing specifications. Latest CI run: https://github.com/Bandi-maya/chat-application/actions/runs/41224280230 .
