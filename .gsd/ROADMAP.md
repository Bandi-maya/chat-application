---
milestone: chaty-kotlin-modernization
version: 1.0.0
updated: 2026-10-31T23:00:00+05:30
---

# Roadmap

> **Current Phase:** 1 — Baseline and build recovery
> **Status:** executing

## Must-Haves

- [ ] Build and test the current Kotlin branch on the exact head.
- [ ] Resolve source-confirmed compile/test blockers without unrelated behavior changes.
- [ ] Preserve all existing features, settings, persisted data, MLS and backend contracts.
- [ ] Fix or explicitly block all verified P0/P1 issues with reproducible evidence.
- [ ] Complete traceability for customization state, persistence and runtime consumers.
- [ ] Deliver screen-level UI/UX improvements with screenshot and accessibility evidence.

## Phases

### Phase 1: Baseline and build recovery
**Status:** 🔄 In Progress  
**Objective:** Establish current CI truth, resolve compile blockers, and produce a repeatable baseline.  
**Requirements:** Build health, preservation, evidence-led delivery.

**Plans:**
- [ ] Plan 1.1: Repair current Compose compilation errors in the ongoing-call bundle.
- [ ] Plan 1.2: Re-run build, unit tests, instrumentation compilation and runtime smoke; record baseline.

### Phase 2: P0/P1 correctness and security
**Status:** ⬜ Not Started  
**Objective:** Diagnose calls, auth/session, MLS, attachment authorization and privacy defects from current source and tests.  
**Depends on:** Phase 1.

**Plans:**
- [ ] Plan 2.1: Triage P0/P1 issue register and security findings against current Kotlin source.
- [ ] Plan 2.2: Fix one confirmed defect per independently tested vertical slice.

### Phase 3: Customization and design-system wiring
**Status:** ⬜ Not Started  
**Objective:** Audit settings registry, theme/template resolution, persistence, and actual screen consumers.  
**Depends on:** Phases 1–2.

**Plans:**
- [ ] Plan 3.1: Build settings-to-consumer traceability matrix.
- [ ] Plan 3.2: Correct disconnected or inconsistent customization options without removing choices.

### Phase 4: Screen-by-screen UI/UX refinement
**Status:** ⬜ Not Started  
**Objective:** Improve home/navigation, chat list, conversation, composer, settings, media and calls using shared components.  
**Depends on:** Phase 3.

**Plans:**
- [ ] Plan 4.1: Home, navigation and chat-list responsive polish.
- [ ] Plan 4.2: Conversation, bubbles, composer and selection polish.
- [ ] Plan 4.3: Settings, media and call surfaces polish.

### Phase 5: Feature parity and platform integrations
**Status:** ⬜ Not Started  
**Objective:** Close legitimate feature gaps only where backend and Android platform contracts support real behavior.  
**Depends on:** Phase 4.

**Plans:**
- [ ] Plan 5.1: Media, camera, documents, voice and status flow audit.
- [ ] Plan 5.2: Calls, notifications, Dynamic Island bridge, contacts and device integrations.

### Phase 6: Release verification
**Status:** ⬜ Not Started  
**Objective:** Complete accessibility, performance, migration, security, regression and artifact verification.  
**Depends on:** Phase 5.

**Plans:**
- [ ] Plan 6.1: Full regression and device matrix.
- [ ] Plan 6.2: Release artifact, checksum, signing and rollback report.

## Progress Summary

| Phase | Status | Plans | Complete |
|---|---|---:|---:|
| 1 | 🔄 | 0/2 | — |
| 2 | ⬜ | 0/2 | — |
| 3 | ⬜ | 0/2 | — |
| 4 | ⬜ | 0/3 | — |
| 5 | ⬜ | 0/2 | — |
| 6 | ⬜ | 0/2 | — |

## Execution Rules

- One bounded task per commit; verify the commit before starting the next task.
- No source implementation until the phase plan is written.
- Use the existing Kotlin implementation plan and feature-preservation ledger as constraints, reconciling historical Flutter-only references against actual Kotlin source.
- Where device/runtime proof is unavailable, mark the item pending rather than passed.
