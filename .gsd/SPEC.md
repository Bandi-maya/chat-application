# Chaty Kotlin Modernization — Project Specification

> **Status**: `FINALIZED`
> **Target branch**: `kotlin`
> **Baseline commit**: `5c58b2d1570b8e7ff6413c9f485af66428e400f6`
> **Updated**: 2026-10-31

## Vision

Continue evolving Chaty as a reliable, polished native Android messaging application built with Kotlin and Jetpack Compose. Improve visual quality, accessibility, responsiveness, reliability and feature completeness while preserving every existing user-facing capability, persisted preference, backend contract, and security boundary.

## Goals

1. **Preserve and stabilize** — Keep all existing screens, routes, settings, theme variants, app identity, local data, REST/WebSocket contracts, MLS encryption, call permissions and notification behavior intact.
2. **Correctness before polish** — Resolve current build/test blockers and verified P0/P1 correctness or security defects before broad UI redesign.
3. **Consistent experience** — Improve shared design tokens, navigation, chat list, conversation, composer, settings, media and call surfaces using the existing Compose architecture.
4. **Real customization** — Ensure every exposed customization option has a stable identifier, persistence owner, real runtime consumer, preview/apply/cancel behavior and regression coverage.
5. **Evidence-led delivery** — Research relevant platform guidance per work package, maintain PRDs/architecture/flows/validation records, and verify changes with reproducible builds, tests and screenshots where available.

## Non-Goals

- No wholesale rewrite or migration away from the current Kotlin/Compose architecture.
- No Flutter/Dart reintroduction into this native branch.
- No proprietary GBWhatsApp source, branding or assets; APK references are limited to lawful feature-category and interaction inspiration.
- No privacy/security bypasses, plaintext fallback for MLS messages, weakened TLS/RLS/authentication, fake-success callbacks, or unsupported backend behavior.
- No change to application ID, signing identity, backend schema, encryption protocol, existing preference keys or user data format without a separately reviewed migration plan.
- No new third-party dependency unless a documented gap proves it necessary and compatibility/security review approves it.
- No claim of live-backend, two-device, physical-device, iOS, or visual parity without corresponding evidence.

## Constraints

- Implement on the existing `kotlin` branch as specified by the Kotlin-branch implementation plan.
- Follow `PROJECT_RULES.md`: SPEC → PLAN → EXECUTE → VERIFY → COMMIT; one task per commit.
- Preserve existing feature options and persisted identifiers; add migrations only when backward-compatible and tested.
- Keep the current custom REST/WebSocket backend and Rust MLS/JNI/UniFFI path authoritative.
- Treat synthetic fixtures and emulator tests as synthetic evidence, not live service proof.
- Do not uninstall, clear app data, rotate signing keys or alter package identity to work around update or verification issues.
- Use Android platform patterns and Compose APIs compatible with the versions pinned by the project; do not upgrade dependencies incidentally.

## Success Criteria

- [ ] The latest Kotlin branch builds successfully for the configured Android target.
- [ ] Unit and instrumentation test suites compile and pass; baseline failures are distinguished from regressions.
- [ ] Every source-confirmed P0/P1 issue is fixed and verified or explicitly blocked with evidence and owner.
- [ ] Existing routes, settings, customization options, persisted values, MLS and backend contracts remain intact.
- [ ] Each customization option audited has a traceable state → persistence → runtime-consumer path or a clearly stated supported limitation.
- [ ] Key user flows have success, failure, cancellation, offline/permission-denied and lifecycle coverage as applicable.
- [ ] UI changes have screenshot/pixel evidence at supported sizes and accessibility checks; no visual-parity claim is made from source inspection alone.
- [ ] PRDs, architecture/flow notes, reference research, task plans, regression matrix and implementation evidence remain current.

## User Stories

### As a Chaty user
- I want the app to look cohesive and modern across its screens and selectable layouts.
- I want every setting I select to persist and visibly affect the intended runtime component.
- I want messaging, media, calls, notifications and account protection to remain reliable and secure.

### As a maintainer
- I want each change to be small, traceable, independently verifiable and reversible.
- I want CI and test evidence tied to the exact commit being reported.

## Technical Requirements

| Requirement | Priority | Notes |
|---|---|---|
| Kotlin/Compose build and test health | Must-have | Repair current compile blockers before feature work |
| MLS E2EE and secure backend contracts | Must-have | No plaintext fallback or authorization weakening |
| Feature and preference preservation | Must-have | No removal or key renaming without migration |
| Settings/customization runtime wiring | Must-have | Real consumers and persistence tests |
| Accessibility and adaptive UI | Should-have | Contrast, semantics, touch targets, large text, RTL and window size |
| Motion and visual refinement | Should-have | Respect reduced motion and existing selectable styles |
| Additional feature parity | Should-have | Only where backend/platform supports real behavior |

---

This specification is finalized for incremental implementation. Each phase plan must narrow scope and define its own acceptance tests before source edits.
