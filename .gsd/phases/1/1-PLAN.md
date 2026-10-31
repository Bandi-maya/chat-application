---
phase: 1
plan: 1
wave: 1
gap_closure: true
---

# Plan 1.1: Repair the ongoing-call Compose compilation errors

## Objective

Restore the Kotlin debug build by correcting the two compiler-reported annotation errors in the ongoing-call bundle. Keep the change strictly limited to annotation placement; do not alter call state, callbacks, layout, styling, audio/video controls or feature availability.

## Context

Read:
- `.gsd/SPEC.md`
- `.gsd/STATE.md`
- `PROJECT_RULES.md`
- `docs/implementation/GBWHATSAPP_KOTLIN_BRANCH_IMPLEMENTATION_PLAN.md`
- `app/src/main/kotlin/com/example/chat/ui/calls/bundles/BundleOngoingCallView.kt`

Evidence:
- Baseline commit: `5c58b2d1570b8e7ff6413c9f485af66428e400f6`
- Failed CI run: https://github.com/Bandi-maya/chat-application/actions/runs/41224280230
- Build log: `:app:compileDebugKotlin` reports a duplicate `@Composable` at line 637 and composable invocations from an unannotated `ControlIconButton` at lines 931 and 937.

## Tasks

<task type="auto">
  <name>Correct composable annotations without changing runtime behavior</name>
  <files>
    app/src/main/kotlin/com/example/chat/ui/calls/bundles/BundleOngoingCallView.kt
  </files>
  <action>
    1. Remove the redundant `@Composable` annotation immediately before the section comment for `AzureRingVerticalOngoingCall`; retain exactly one annotation on that composable.
    2. Add `@Composable` directly to the top-level `ControlIconButton` function because its body invokes Compose `Box` and `Icon`.
    3. Do not change parameters, call sites, visual modifiers, state handling, callbacks, visibility or call behavior.
    4. Review the complete diff and ensure only these annotation changes are present.

    AVOID: broad formatting or refactoring of this large file because it would obscure the minimal compiler fix.
    USE: the smallest source patch because the failure is already isolated by the compiler.
  </action>
  <verify>
    Trigger the repository's `Build kotlin APK` workflow on `kotlin`; inspect the build job logs. The first required proof is that `:app:compileDebugKotlin` passes. If another compiler error appears, record its exact file/line before planning another task.
  </verify>
  <done>
    The duplicate annotation error is gone, `ControlIconButton` compiles as a composable, and the APK build proceeds beyond Kotlin compilation. No claim of full test/runtime success until those later workflow jobs complete.
  </done>
</task>

## Must-Haves

- [ ] Only the two compiler-reported annotation defects are changed.
- [ ] Kotlin compilation passes on the resulting commit.
- [ ] No call UI behavior, controls, callbacks or feature options are removed or altered.

## Success Criteria

- [ ] Build workflow is tied to the exact resulting commit.
- [ ] Compiler output confirms the file compiles.
- [ ] Any later test/runtime failure is separately classified and not hidden.
