# Kotlin Branch Quality Scorecard

**Review date:** 2026-11-01  
**Branch:** `kotlin`  
**Scope:** source review and targeted changes in navigation, notification activity, local authentication, call permissions and call notification actions.  
**Evidence boundary:** GitHub source inspection and regression-test additions. The current CI run and Android device verification are not yet complete.

## Rating scale

- **5/5:** implementation complete, automated checks green, device/runtime acceptance complete, and no known material blocker in the stated scope.
- **4/5:** source implementation is in place and the main regression paths are covered, but device/runtime or independent security verification remains.
- **3/5:** partial implementation or known product/architecture gap remains.
- **2/5:** major flow is incomplete or has unresolved correctness risk.
- **1/5:** not implemented or not verified.

A score is an evidence rating, not a promise that software is bug-free. A 5/5 rating is withheld when the required evidence does not exist.

## Current scores

| Area | Score | Evidence and remaining gap |
|---|---:|---|
| Home navigation separation | 4/5 | Dedicated Notifications route is wired separately from Settings for Chats and Groups; system Back and sign-out route are handled. Device navigation/pixel verification is pending. |
| Notification activity UI | 3/5 | Clean separated-row activity screen, empty state, clear confirmation, settings shortcut and thread-safe local event feed. Feed is in-memory and intentionally is not a persistent server notification archive. |
| Notification settings separation | 4/5 | Notification preferences remain in Settings and are opened explicitly from the inbox settings action. End-to-end settings persistence/device test remains pending. |
| Local authentication | 4/5 | Keystore-backed API and GoTrue session stores, durable GoTrue sign-out tombstone, PBKDF2 credential hashes, constant-time comparison, cooldown and protected-resource checks are present. Device and adversarial testing remain pending. |
| Call permission handling | 4/5 | Microphone/camera permissions are re-read from Android after the permission callback; stale positive callback regression test added. Real permission dialogs across API levels remain unverified. |
| Call notification actions | 4/5 | Ongoing-call notification exposes Hang up; the action carries the originating call ID and the receiver checks the active session before ending it. Instrumentation proof and actual two-party hang-up still need CI/device execution. |
| Backend contract stability | 5/5 for this patch | No REST route, WebSocket message shape, auth endpoint, signaling payload or database contract was changed. This is not a rating of backend security or availability. |
| Full-app UI/UX consistency | 3/5 | Targeted notification/header improvements are in place. The entire screen inventory has not been pixel-reviewed or tested for accessibility, responsive layout and all theme variants. |
| Release/runtime assurance | 1/5 | Current Kotlin build/test workflow is pending; no complete emulator/device acceptance or independent penetration test is available in this review. |

## Implemented in this review

- Home header Notifications icon routes to a dedicated in-app activity screen, not Notification Settings.
- Chats and Groups hosts both route to the dedicated screen.
- Notification Settings remains a distinct Settings destination.
- The activity screen uses a single list with separators, an explicit empty state, a settings shortcut and a confirmation before clearing the local activity feed.
- Local notification activity storage now synchronizes list mutations/snapshots and uses UUID event IDs.
- GoTrue sign-out commits a durable encrypted tombstone before clearing legacy DataStore values. A stale legacy session cannot be migrated after sign-out.
- Call media permission flow checks Android's actual grant state again after the runtime prompt.
- Ongoing-call Hang up action is bound to the originating call ID; stale actions do not end a different active call.
- Security flow snackbar callback recursion is removed.

## Known blockers — do not mark 5/5 yet

1. Standard REST one-to-one/group messages are not end-to-end encrypted by the active message path. The MLS/OpenMLS engine is not wired into ordinary REST login/message delivery.
2. Standard cloud attachments remain server-readable; the large peer-transfer path has separate encrypted chunk protection and is not proof of universal attachment E2EE.
3. Legacy Supabase auth/data/realtime/RPC integrations still coexist with the custom API backend; feature-by-feature authority and migration remain to be resolved.
4. Call-history persistence remains disabled until a supported backend contract exists.
5. The notification activity list is local/in-memory only. A persistent notification archive requires an approved storage/backend contract; none is invented here.
6. Screen-sharing, foreground-service behavior, notification actions, process death, permission revocation and account switching still require device-level tests across supported Android API levels.
7. The complete UI inventory, accessibility audit, responsive/tablet review, theme matrix, release artifact inspection and independent penetration test are not complete.

## Acceptance gate for 5/5

- Latest debug APK compilation and unit tests pass on the exact branch head.
- Instrumentation tests pass on supported API levels, including notification actions, permission denial/revocation, app lock, sign-out migration, process restart and screen sharing.
- Real two-device call/message/media flows pass against the existing backend without contract changes.
- No critical/high findings remain in the scoped security review; storage, network, crypto, auth, platform, privacy and resilience are reviewed.
- UI screenshots and accessibility checks pass for phone, tablet, light/dark and supported user themes.
- Release APK is inspected for debug fixtures, test endpoints, exported component mistakes, backup exposure and signing/version correctness.

**Current overall assessment:** targeted improvements are committed, but the evidence does not support a whole-app 5/5 claim yet.
