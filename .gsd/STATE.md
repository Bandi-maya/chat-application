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

1. Inspect the Kotlin APK workflow triggered by `e8d335a3b4d1bf99971aad3f8007845388588b35`.
2. Confirm `:app:compileDebugKotlin` passes; investigate any new compile errors from exact logs.
3. If compilation passes, inspect unit tests, instrumentation compilation and runtime smoke.
4. Update issue register, implementation progress and this state with exact commit/run evidence.

## Active Decisions

| Decision | Choice | Made | Affects |
|---|---|---|---|
| Target branch | Work directly on existing `kotlin` branch per Kotlin-specific plan | 2026-10-31 | All phases |
| Architecture | Keep Kotlin, Jetpack Compose, custom REST/WebSocket, Rust MLS/JNI | 2026-10-31 | All phases |
| Change strategy | Minimal vertical slices; preserve all features and preference keys | 2026-10-31 | All phases |
| Verification | CI/runtime evidence required; synthetic tests are not live-backend proof | 2026-10-31 | All phases |

## Blockers

- [x] Current Compose compilation failure source fix committed: removed duplicate `@Composable` before `AzureRingVerticalOngoingCall` and annotated `ControlIconButton`.
- [ ] CI verification of the source fix is pending on the new trigger commit `e8d335a3b4d1bf99971aad3f8007845388588b35`.
- [ ] Physical-device/live-backend verification requires user configuration and must not be simulated.

## Concerns

- Existing documentation contains historical Flutter-only paths; always verify native Kotlin implementation before applying a plan.
- The branch has extensive pending runtime/security/UI audit findings; do not treat an issue register entry as a reproduced live defect without its evidence class.
- Preserve app ID, signing identity, user data, encryption and server authorization.

## Session Context

The repository already contains substantial PRDs, UI/UX references, architecture, QA matrices, feature inventory and Kotlin parity evidence. Reuse and reconcile these documents instead of creating duplicate competing specifications. Latest CI run: https://github.com/Bandi-maya/chat-application/actions/runs/41224280230 .
