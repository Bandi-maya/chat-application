# Prd

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

## Goal
Evidence-driven improvement of existing Chaty by morning review: real functionality/security/accessibility first, frontend consistency next, effects last. Preserve architecture/components/business logic and working backend. No mock replacing production, no unauthorized real sends/calls/GPS/profile edits. Synthetic fixtures are QA tools only.

## Functional requirements
### UI-01 Design tokens and shared controls
Freeze semantic colors, type sizes, spacing, corner shapes, icon IDs and 48dp hit regions. Use the same row, switch, selector, sheet and error components across settings and real screens. Keep existing skins selectable.

Acceptance: Light/dark + 200% text; no overlap, missing labels or color-only state; on/off switches distinct.

Boundary: Audit current tokens first; version shared changes before dependent patches.

### UI-02 Home header and all navigation layouts
Audit all 12 named home styles. Keep identity, search, tabs and actions within safe bounds; preserve user destination order, hidden destinations and back-stack behavior. Circular stories remain the confirmed default preference.

Acceptance: Every primary and overflow destination reachable at narrow portrait, landscape and large text; no tap-through or displaced following rows.

Boundary: Depends on UI-01; do not replace navigation models independently.

### UI-03 Conversation header, rows and selection
Keep GB-informed header and long-press/reaction structure, existing Chaty behavior and extra actions. Stabilize avatar/name/action spacing and selection toolbar hierarchy.

Acceptance: Long names, offline state, large text, keyboard open and multi-selection remain usable; call buttons dispatch once; back/cancel restore state.

Boundary: Use synthetic conversations, not real messages.

### UI-04 Bubbles, ticks, emoji and composer
Apply selected bubbles, ticks, emoji picker and input style to production chat and paired previews from the same models. Preserve original options and distinct silhouettes.

Acceptance: Every catalog option has a labeled light/dark contact sheet plus actual chat fixture; long text, reply, media, pending/failed/send states and RTL do not clip. Cancel preserves saved values.

Boundary: Next4 unit proof does not replace actual pixels; avoid duplicate catalog ownership.

### UI-05 Settings registry and route audit
Generate a destination/control inventory from current source, not filenames. Map each root row, search result, nested sheet and canonical route to its production host and setting consumer.

Acceptance: Open each destination, change each supported control, Apply, Back, reopen and recreate; saved value, preview and actual target agree. Unsupported state names the limit rather than faking success.

Boundary: 39 registry definitions and 16 canonical route strings are a seed, not all settings.

### UI-06 Themes, templates, universal styles and icons
Use paired home + conversation previews, stable style/icon IDs, draft editing and explicit Apply/Cancel. Audit precedence: global theme, template, component override, local setting.

Acceptance: Select/apply/reopen/relaunch + reset + import invalid profile; no accidental reset of other components; launcher alias change leaves one intended launcher entry.

Boundary: Depends on UI-01 and UI-05; preserve user components and existing selections.

### CALL-01 Nonconnecting calls: diagnose before polish
Trace synthetic outgoing/incoming lifecycle: permission, signaling, offer/answer, ICE, media readiness, platform call integration, end/timeout. Redact logs. Investigate existing implementation before changing libraries.

Acceptance: Two controlled peers establish bidirectional audio, reject/hang up/timeout recover, denied microphone fails safely, reconnect and foreground/background transitions are recorded. A local fake signaling test alone is not live call proof.

Boundary: User report is confirmed; technical root cause remains unknown. No unapproved calls to contacts or paid TURN services.

### CALL-02 Call activity capsule, inspired by Apple
Provide compact ongoing-call state with truthful status and timer, tap to return to call, expanded essential controls, immediate cleanup on end. Respect cutouts, insets and privacy; use Android-supported notification/call surfaces for background.

Acceptance: No timer before connected/media state; mute/end apply once; return route correct; no overlap, stale overlay or private caller data on locked screen. Denied optional overlay permission leaves core calling usable.

Boundary: Depends on CALL-01 state contract. Android adaptation, not Apple ActivityKit or a claim to native iPhone Dynamic Island.

### MEDIA-01 Camera, preview, editing and attachment flow
Keep cancel vs send explicit; fit editor/trim controls above keyboard/system bars; preserve original files and policy restrictions.

Acceptance: Permission deny/regrant, recreation, EXIF orientation, crop/trim apply/cancel, white/dark media, long filenames, cache expiry and restricted/view-once actions covered.

Boundary: No real sends; generated fixtures only.

### MAP-01 GeoLibre production route
Remove extra blank pre-map gate on explicit map opening; visible map with Back, Locate, zoom, 2D/3D and layers. Keep compact provider notice/required attribution and explicit-only GPS.

Acceptance: Actual chat entry opens production route with visible map pixels and functioning controls; network failure shows recoverable state; closing removes renderer; no startup preload or GPS without Locate permission.

Boundary: Local Next5 patch unbuilt at evidence cut. Headless package subset is not full GeoLibre GIS app parity.

### TASK-01 Tasks and Kanban
Audit create/edit/list/board/due-date/filter states and preserve task data. Keep empty, loading, validation, success and failure states consistent.

Acceptance: Synthetic create -> edit -> list/board reflects changes; invalid input, no data, long title and recreation covered; cancel never commits.

Boundary: End-to-end coverage unverified at evidence cut.

### PROFILE-01 Profile and contacts
Keep edit profile in the intended settings/header flow; consistent avatar/banner cropping, form labels and save/discard behavior.

Acceptance: Synthetic edit, invalid/long names, denied gallery/camera and restore after recreation; account/session boundaries maintained.

Boundary: No edits to real profile/contact records during audit.

### UPDATES-01 Stories and updates
Audit story draft, media selection and viewer navigation, progress, pause and expiry/error states. Preserve circular-story preference.

Acceptance: Draft cancel never publishes; long captions, failed media, timeout, theme change and back/reopen covered with synthetic fixture.

Boundary: No real story posting.

### SEC-01 Security and privacy verification
Map existing controls to MASVS storage, crypto, auth, network, platform, code, resilience and privacy. Inspect permissions, exported components, WebView bridges, sensitive logs, backup/export and account isolation. Keep existing crypto/backend.

Acceptance: No plaintext secrets in logs or exported files; locked/view-once data absent from overlays; denied permission safe; logout/account switch revokes caches/leases; backups/imports tested without production data.

Boundary: A checklist is not certification. Do not reuse unknown GB binary security code or weaken TLS/permissions to pass tests.

### QA-01 Integrated audit and delivery gate
Merge bounded patches onto one pinned source, compile, run tests, inspect emulator pixels and retain bug IDs. Produce artifact hash/signing evidence and readable source delivery.

Acceptance: Every required row has build/device/config/steps/expected/actual/evidence/status; blockers explicit; no uninstall/data-clear request to solve signing mismatch.

Boundary: All component patches must be tested together in one pinned candidate. No blanket zero-bug or all-screen claim.


## Data/state and rollout
Retain service/repository security boundaries. UI emits events/observes state; same models in preview/production. Apply commits draft once; cancel discards. Account/logout/lifecycle invalidates stale callbacks. Checkpoint first; bounded patches; integrated candidate; build/unit/instrumentation/actual pixels; signing and migration gate; issue register and rollback. No overnight rewrite to imitate sample architecture.

## Open decisions
Free relay availability, deployed server call path, expected full GeoLibre viewer vs subset, scoped screenshot privacy, atomic token migration, physical/OEM camera/audio/push behavior. Continue independent safe fixes while unresolved. Morning report separates implemented/tested/blocked and records exact build.


## Existing architecture and current behavior
Kotlin/Compose Android app with MainActivity/AppRoot shell, ServiceLocator, current preferences/theme/template models, custom REST/WebSocket services and native Rust/UniFFI encryption bridge. Keep current architecture unless a verified issue requires a scoped change. Source-presence is not runtime activation: legacy Supabase preference/realtime wiring remains but active behavior unknown. Map production route previously inserted a blocking gate; calls transport requires signaling/ICE/media separately from UI state.

## Screen and component inventory
See audit/SCREEN_INVENTORY.md for source-derived candidates (not reachable/tested screen count), audit/COMPONENT_INVENTORY.md for component lanes and implementation/TEST_MATRIX.md for declared instrumentation. Every root/nested/search destination and setting consumer needs route+round-trip validation.13 categories/39 registry definitions/16 route strings and12 home styles are source seeds, not exhaustive audited coverage.

## Confirmed versus suspected issues
Observed phone MAP-01 and user-reported CALL-01 are distinct. Current-source findings and synthetic-service reproductions appear with evidence/status in audit/ISSUE_REGISTER.md. Two ICE ordering/queue defects reproduce in service fixtures;62 passing tests use fake media callbacks, not native WebRTC. Contrast, preview and lifecycle leads need actual pixels/runtime before visual or crash claims. No newly introduced regression proven yet, not proof of absence.

## Security findings
Current source confirms plaintext token persistence/no wired migrating store and deep-link filters without handlers; runtime exploit/activation unknown. Notification destructive cancellation correction is source-tested, not Android-accepted. MASVS verification and account/cache boundaries remain required. Global FLAG_SECURE and atomic token migration not approved for blind rollout. See audit/SECURITY_FINDINGS.md.

## Testing strategy
Unit tests isolate models/codecs; controlled service integration verifies state/events; installed emulator instrumentation verifies production hosts; actual pixels verify layout; physical/network tests verify OEM/hardware/deployed calls. Record build/certificate/config and expected/actual/evidence for each.01:42 declared100/33 inventory is not the current frozen count. Next5 launched01:49, no build/test/runtime result at this checkpoint.

## Dependencies and integration
See implementation/DEPENDENCY_GRAPH.md. Shared token/state/permission contracts precede dependent polish; call state precedes capsule status. Merge bounded patches after checking shared manifest, AppRoot, ServiceLocator and build configuration changes. No free relay verified; do not add paid service or fake relay credentials.

## Rollback and release safety
Checkpoint git/user diff before risk; preserve previous source/artifact with digest/certificate. Revert the smallest bounded patch if validation fails, without destructive reset or deleting user data. Verify certificate/migration compatibility before install recommendation. No uninstall/data-clear instruction to resolve mismatch; native encryption history must survive any supported update path.

## Outstanding blockers
Full emulator/production-route results, deployed/native call connection, actual map pixels/lifecycle, complete settings apply/reopen audit, physical/OEM/push behavior, safe signing/update route, GB current binary comparison and security migration atomicity. Morning review is an evidence review, not blanket release approval.


## Runtime outcome update,01:54 IST
Next4 runtime job failed during Android test compilation, BEFORE app installation/instrumentation: AttachmentFileCacheTest.kt:295 unresolved EditSession/outputFile reference. No app started, no runtime XML or app visual passes. This is a test-source compile failure, not an observed app crash. Native and main application build and1006 JVM unit passes remain valid. Source: https://github.com/Bandi-maya/chat-application/actions/runs/40389044496/job/121812020284 . A missing EditSession import is the test compile cause; outputFile is a cascade error. No production defect is shown by this compile failure. Next5 launch was recorded at01:49; no completed outcome is recorded in this review.
