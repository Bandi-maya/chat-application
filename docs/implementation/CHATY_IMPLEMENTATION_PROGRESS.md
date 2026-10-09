# Chaty Implementation Progress

**Updated:** 2026-10-09  
**Branch:** `feat/global-template-customization`  
**Scope:** Runtime implementation started from the approved phased plan. This is a progress report, not a production-readiness claim.

## Implemented in this branch

### Global template studio
- Added **Global Template Studio** to Settings and Settings search.
- Exposed the existing six base templates and per-component override flow through a discoverable route.
- Added appearance-only template profile export to clipboard and validated JSON import.
- Added a persistent Navigation Destinations editor: reorder up to four primary tabs, move screens into More, and retain every supported destination. The same settings remain available from the always-visible Chats overflow menu.
- Import validates the profile kind/schema, base template, component keys and template keys before applying. Export includes only template/component style configuration; it excludes chats, account data, credentials and keys.
- Normalizes component overrides that duplicate the base template.
- Connects conversation template selections to live theme tokens for bubble style, delivery tick style and corner radius.

### Runtime component consumers
- Navigation/home/conversation already had partial consumers; those existing paths remain in use.
- Chat list now consumes template row density, avatar shape, presence-badge visibility, item height and divider visibility.
- Chat composer now consumes template action placement, corner radius, camera shortcut visibility, voice-lock indicator visibility and send/voice transition motion.
- Updates now renders distinct circular rail, responsive grid tiles, squircle cards, and minimal-list layouts; the audio template option plays audio updates in-app using the app's existing audio dependency.
- Home & Navigation settings now exposes all navigation modes, including Compact Rail, and stays reactive when settings change.
- Navigation template order, primary/overflow placement, configured height and center quick action are connected to the runtime shell. Manual destination order persists, validates unique IDs, and cannot hide a screen.
- The Chats overflow menu is always available. Compact phone widths now open a grouped safe-area-aware quick-access sheet; wider screens keep the anchored popup. Both use the same action handler for camera effects, linked devices, themes, templates, Home & Navigation, Navigation destinations and all settings.
- Added the UI reference research catalog with a curated 76-reference landscape, consolidated unique UX patterns, and documented platform/accessibility/performance acceptance criteria. The sources were studied for patterns only; no new component library was installed.
- Template Studio now filters templates and component overrides from a single search field, shows an explicit no-results state, and is constrained to a readable maximum width on large screens. Individual component pages use previews based on the selected template's real navigation IDs, composer placement, bubble/tick style, chat-row density, Updates layout, profile header and call-control settings rather than placeholder labels.
- Added dependency-free original vector glyphs for the home overflow menu, Template Studio controls and primary navigation; the same glyph set is widget-tested at multiple sizes. The rest of the app still uses existing icons and the broader app-wide SVG-source/icon/motion pass remains outstanding.
- Added a live Starred Messages screen, linked from both responsive home overflow presentations and settings search. It loads conversation message snapshots, filters by conversation/message/type text, opens the containing conversation, and removes stars through the existing per-user message-state backend path; it does not claim exact-message deep-linking yet.
- Profile screen now renders structurally distinct compact, centered-identity and banner-with-avatar layouts. Avatar shape and the stats-grid toggle work; displayed counts come from the local data store (chats, groups and contacts).
- Call controls consume the configured control corner radius. `enableFloatingIsland` gates the island, with the existing global call capsule retained as the safe fallback so active-call controls remain available.
- Appearance preview chips display the actual selected typography and entry/exit motion values rather than empty labels.
- Template/component confirmation copy now names the selected template/component.

### Correctness and security fixes
- Message editing detects MLS-backed messages, encrypts the edited payload using the active MLS conversation state, and calls `edit_mls_message_v1` with the sender device, group, epoch and ciphertext.
- Removed the direct-table plaintext fallback from message editing. Failed edits roll back the optimistic UI state and rethrow to the caller.
- Refreshes metadata-less cached messages before choosing an edit transport, rejects deleted/decryption-failed messages, and restricts this edit path to text messages.
- Maps server metadata into `ChatMessage` so transport metadata can be used for correct message handling.
- Linked-device revocation now persists through `ContactRelationshipService.revokeDevice` before removing the device from the local cache.

### Tests added
- Added template-controller tests for import normalization, live theme-token application, navigation-mode mapping, scoped component overrides, destination ordering persistence/reset, and invalid/duplicate destination rejection. Added vector-glyph widget coverage and UI contract tests for responsive overflow routing, template search and non-placeholder component previews.

## Still outstanding — do not mark complete yet

- Complete runtime screen-by-screen walkthrough of the supplied APK on a test device/emulator; APK resource names alone are not proof of runtime behavior.
- Continue consolidating the legacy status/call facades: `ChatyDataStore.addStory` and `markStoryViewed` now delegate to the production `StatusService`; the unused backend-level status/call methods now fail explicitly instead of silently succeeding. The visible Updates screen uses `StatusService`, and real calls use `CallSignalingService`/call history.
- Complete the runtime walkthrough of each APK screen and menu on a device/emulator, then compare its behavior with the resource-derived inventory. Static resource presence is not proof of runtime parity.
- Finish feature-parity slices for media, voice notes, privacy/devices and calls with real backend behavior; do not add inert controls or bypass MLS/RLS/security.
- Re-run analyzer/tests and migration/build validation on the latest branch head after the newest UI slices; the most recent observed green analysis/test run predates the navigation-destination editor and distinct status layouts.
- Complete iOS build/device checks, then regression-test Android/iOS on small phones, large phones, tablets/foldables and split-window sizes.
- Audit all APK-derived feature categories still outside this customization slice, including full media/voice-note variants, privacy/device workflows and call edge cases; do not add inert controls or bypass MLS/RLS/security.
- The requested end-to-end custom SVG/icon overhaul, per-interaction motion pass, 144 Hz profiling, and physical-device performance profiling are not complete. Do not claim those targets until measured.

## Release gate

The feature is **in implementation**, not production-ready. Preserve MLS end-to-end encryption, Supabase RLS, existing authentication and existing working flows. Any failed validation must be fixed rather than bypassed.
