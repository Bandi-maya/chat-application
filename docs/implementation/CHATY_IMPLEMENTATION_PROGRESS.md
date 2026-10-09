# Chaty Implementation Progress

**Updated:** 2026-10-09  
**Branch:** `feat/global-template-customization`  
**Scope:** Runtime implementation started from the approved phased plan. This is a progress report, not a production-readiness claim.

## Implemented in this branch

### Global template studio
- Added **Global Template Studio** to Settings and Settings search.
- Exposed the existing six base templates and per-component override flow through a discoverable route.
- Added appearance-only template profile export to clipboard and validated JSON import.
- Import validates the profile kind/schema, base template, component keys and template keys before applying. Export includes only template/component style configuration; it excludes chats, account data, credentials and keys.
- Normalizes component overrides that duplicate the base template.
- Connects conversation template selections to live theme tokens for bubble style, delivery tick style and corner radius.

### Runtime component consumers
- Navigation/home/conversation already had partial consumers; those existing paths remain in use.
- Chat list now consumes template row density, avatar shape, presence-badge visibility, item height and divider visibility.
- Chat composer now consumes template action placement, corner radius, camera shortcut visibility, voice-lock indicator visibility and send/voice transition motion.
- Updates list now consumes layout mode, avatar sizing/shape treatment and card elevation.
- Profile screen now consumes header style, avatar shape and the stats-grid toggle. Displayed counts are derived from the local data store (chats, groups and contacts).
- Call controls consume the configured control corner radius; the in-app call island respects the template's floating-island option.
- Appearance preview chips display the actual selected typography and entry/exit motion values rather than empty labels.
- Template/component confirmation copy now names the selected template/component.

### Correctness and security fixes
- Message editing detects MLS-backed messages, encrypts the edited payload using the active MLS conversation state, and calls `edit_mls_message_v1` with the sender device, group, epoch and ciphertext.
- Removed the direct-table plaintext fallback from message editing. Failed edits roll back the optimistic UI state and rethrow to the caller.
- Refreshes metadata-less cached messages before choosing an edit transport, rejects deleted/decryption-failed messages, and restricts this edit path to text messages.
- Maps server metadata into `ChatMessage` so transport metadata can be used for correct message handling.
- Linked-device revocation now persists through `ContactRelationshipService.revokeDevice` before removing the device from the local cache.

### Tests added
- Added a template-controller test for normalization of imported profile overrides.
- Added a test for live theme-token application when a template is selected.

## Still outstanding — do not mark complete yet

- Complete runtime screen-by-screen walkthrough of the supplied APK on a test device/emulator; APK resource names alone are not proof of runtime behavior.
- Wire or retire the unused legacy facade methods `markStoryViewed` and `logCall`; `addStory` remains explicitly unsupported in the legacy backend facade. The current Updates screen uses `StatusService` directly, and calls use `CallSignalingService`/call history.
- Audit and implement remaining template definition properties that do not yet have a verified runtime consumer (including status audio behavior and some structural layout variants).
- Finish feature-parity slices for media, voice notes, privacy/devices and calls with real backend behavior; do not add inert controls or bypass MLS/RLS/security.
- Run the full Flutter analyze/test suite, clean Supabase migration replay, Android ARM64 build/install verification, and iOS build/device checks. Record the actual run URLs and outcomes here after CI completes.
- Perform regression, accessibility, performance, offline/reconnect and multi-device tests before declaring release readiness.

## Release gate

The feature is **in implementation**, not production-ready. Preserve MLS end-to-end encryption, Supabase RLS, existing authentication and existing working flows. Any failed validation must be fixed rather than bypassed.
