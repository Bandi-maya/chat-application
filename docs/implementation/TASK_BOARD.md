# Task Board

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

Work packages, not scheduling/completion receipts. Each work package has a bounded component scope; integration checks combine the results.

## UI-01 P1: Design tokens and shared controls
Component area:Shared primitives

Freeze semantic colors, type sizes, spacing, corner shapes, icon IDs and 48dp hit regions. Use the same row, switch, selector, sheet and error components across settings and real screens. Keep existing skins selectable.

Acceptance:Light/dark + 200% text; no overlap, missing labels or color-only state; on/off switches distinct.

Dependency/boundary:Audit current tokens first; version shared changes before dependent patches.

Status: reconcile actual patch + result ledger; not complete by this document.

## UI-02 P1: Home header and all navigation layouts
Component area:Navigation

Audit all 12 named home styles. Keep identity, search, tabs and actions within safe bounds; preserve user destination order, hidden destinations and back-stack behavior. Circular stories remain the confirmed default preference.

Acceptance:Every primary and overflow destination reachable at narrow portrait, landscape and large text; no tap-through or displaced following rows.

Dependency/boundary:Depends on UI-01; do not replace navigation models independently.

Status: reconcile actual patch + result ledger; not complete by this document.

## UI-03 P1: Conversation header, rows and selection
Component area:Conversation UI

Keep GB-informed header and long-press/reaction structure, existing Chaty behavior and extra actions. Stabilize avatar/name/action spacing and selection toolbar hierarchy.

Acceptance:Long names, offline state, large text, keyboard open and multi-selection remain usable; call buttons dispatch once; back/cancel restore state.

Dependency/boundary:Use synthetic conversations, not real messages.

Status: reconcile actual patch + result ledger; not complete by this document.

## UI-04 P1: Bubbles, ticks, emoji and composer
Component area:Chat customization

Apply selected bubbles, ticks, emoji picker and input style to production chat and paired previews from the same models. Preserve original options and distinct silhouettes.

Acceptance:Every catalog option has a labeled light/dark contact sheet plus actual chat fixture; long text, reply, media, pending/failed/send states and RTL do not clip. Cancel preserves saved values.

Dependency/boundary:Next4 unit proof does not replace actual pixels; avoid duplicate catalog ownership.

Status: reconcile actual patch + result ledger; not complete by this document.

## UI-05 P1: Settings registry and route audit
Component area:Settings

Generate a destination/control inventory from current source, not filenames. Map each root row, search result, nested sheet and canonical route to its production host and setting consumer.

Acceptance:Open each destination, change each supported control, Apply, Back, reopen and recreate; saved value, preview and actual target agree. Unsupported state names the limit rather than faking success.

Dependency/boundary:39 registry definitions and 16 canonical route strings are a seed, not all settings.

Status: reconcile actual patch + result ledger; not complete by this document.

## UI-06 P1: Themes, templates, universal styles and icons
Component area:Themes

Use paired home + conversation previews, stable style/icon IDs, draft editing and explicit Apply/Cancel. Audit precedence: global theme, template, component override, local setting.

Acceptance:Select/apply/reopen/relaunch + reset + import invalid profile; no accidental reset of other components; launcher alias change leaves one intended launcher entry.

Dependency/boundary:Depends on UI-01 and UI-05; preserve user components and existing selections.

Status: reconcile actual patch + result ledger; not complete by this document.

## CALL-01 P0: Nonconnecting calls: diagnose before polish
Component area:Calls

Trace synthetic outgoing/incoming lifecycle: permission, signaling, offer/answer, ICE, media readiness, platform call integration, end/timeout. Redact logs. Investigate existing implementation before changing libraries.

Acceptance:Two controlled peers establish bidirectional audio, reject/hang up/timeout recover, denied microphone fails safely, reconnect and foreground/background transitions are recorded. A local fake signaling test alone is not live call proof.

Dependency/boundary:User report is confirmed; technical root cause remains unknown. No unapproved calls to contacts or paid TURN services.

Status: reconcile actual patch + result ledger; not complete by this document.

## CALL-02 P1: Call activity capsule, inspired by Apple
Component area:Calls and call capsule

Provide compact ongoing-call state with truthful status and timer, tap to return to call, expanded essential controls, immediate cleanup on end. Respect cutouts, insets and privacy; use Android-supported notification/call surfaces for background.

Acceptance:No timer before connected/media state; mute/end apply once; return route correct; no overlap, stale overlay or private caller data on locked screen. Denied optional overlay permission leaves core calling usable.

Dependency/boundary:Depends on CALL-01 state contract. Android adaptation, not Apple ActivityKit or a claim to native iPhone Dynamic Island.

Status: reconcile actual patch + result ledger; not complete by this document.

## MEDIA-01 P1: Camera, preview, editing and attachment flow
Component area:Media

Keep cancel vs send explicit; fit editor/trim controls above keyboard/system bars; preserve original files and policy restrictions.

Acceptance:Permission deny/regrant, recreation, EXIF orientation, crop/trim apply/cancel, white/dark media, long filenames, cache expiry and restricted/view-once actions covered.

Dependency/boundary:No real sends; generated fixtures only.

Status: reconcile actual patch + result ledger; not complete by this document.

## MAP-01 P1: GeoLibre production route
Component area:Maps

Remove extra blank pre-map gate on explicit map opening; visible map with Back, Locate, zoom, 2D/3D and layers. Keep compact provider notice/required attribution and explicit-only GPS.

Acceptance:Actual chat entry opens production route with visible map pixels and functioning controls; network failure shows recoverable state; closing removes renderer; no startup preload or GPS without Locate permission.

Dependency/boundary:Local Next5 patch unbuilt at evidence cut. Headless package subset is not full GeoLibre GIS app parity.

Status: reconcile actual patch + result ledger; not complete by this document.

## TASK-01 P1: Tasks and Kanban
Component area:Tasks

Audit create/edit/list/board/due-date/filter states and preserve task data. Keep empty, loading, validation, success and failure states consistent.

Acceptance:Synthetic create -> edit -> list/board reflects changes; invalid input, no data, long title and recreation covered; cancel never commits.

Dependency/boundary:End-to-end coverage unverified at evidence cut.

Status: reconcile actual patch + result ledger; not complete by this document.

## PROFILE-01 P1: Profile and contacts
Component area:Profiles

Keep edit profile in the intended settings/header flow; consistent avatar/banner cropping, form labels and save/discard behavior.

Acceptance:Synthetic edit, invalid/long names, denied gallery/camera and restore after recreation; account/session boundaries maintained.

Dependency/boundary:No edits to real profile/contact records during audit.

Status: reconcile actual patch + result ledger; not complete by this document.

## UPDATES-01 P1: Stories and updates
Component area:Updates

Audit story draft, media selection and viewer navigation, progress, pause and expiry/error states. Preserve circular-story preference.

Acceptance:Draft cancel never publishes; long captions, failed media, timeout, theme change and back/reopen covered with synthetic fixture.

Dependency/boundary:No real story posting.

Status: reconcile actual patch + result ledger; not complete by this document.

## SEC-01 P0: Security and privacy verification
Component area:Security review

Map existing controls to MASVS storage, crypto, auth, network, platform, code, resilience and privacy. Inspect permissions, exported components, WebView bridges, sensitive logs, backup/export and account isolation. Keep existing crypto/backend.

Acceptance:No plaintext secrets in logs or exported files; locked/view-once data absent from overlays; denied permission safe; logout/account switch revokes caches/leases; backups/imports tested without production data.

Dependency/boundary:A checklist is not certification. Do not reuse unknown GB binary security code or weaken TLS/permissions to pass tests.

Status: reconcile actual patch + result ledger; not complete by this document.

## QA-01 P0: Integrated audit and delivery gate
Component area:Build and validation

Merge bounded patches onto one pinned source, compile, run tests, inspect emulator pixels and retain bug IDs. Produce artifact hash/signing evidence and readable source delivery.

Acceptance:Every required row has build/device/config/steps/expected/actual/evidence/status; blockers explicit; no uninstall/data-clear request to solve signing mismatch.

Dependency/boundary:All component patches must be tested together in one pinned candidate. No blanket zero-bug or all-screen claim.

Status: reconcile actual patch + result ledger; not complete by this document.

