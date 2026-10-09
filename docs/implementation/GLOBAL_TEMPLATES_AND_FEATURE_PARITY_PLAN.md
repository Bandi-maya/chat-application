# Chaty — Global Templates, Customization and Feature Parity Plan

## Goal

Make Chaty a coherent, user-configurable messaging application with consistent settings-driven templates across the whole Flutter app. Use GBWhatsApp/WhatsApp only as a functional reference for user-facing capabilities and familiar interaction patterns. Do not copy proprietary assets, branding, private implementation, or bypass platform/security controls.

This is an implementation plan, not a claim that every item below is already implemented. Work must be delivered in small verified phases on `feat/global-template-customization`, never by replacing the app wholesale.

## Current verified baseline

- Flutter/Dart client, Supabase backend, OpenMLS-backed E2EE service, WebRTC call signaling/media code, and 48 SQL migrations are present.
- `AppearanceVariantController` persists navigation style, bottom-bar style, app icon style, notification icon style, typography, entry motion and exit motion with SharedPreferences.
- The chat detail and chat home screens are large files; refactor incrementally without changing behavior.
- `ChatyBackendService.editMessage` currently optimistically changes local text, calls legacy `edit_chat_message`, and falls back to a direct table update. This is not compatible with an MLS ciphertext-only edit flow. The repository contains `MlsE2eeService.encryptEditedPayload` and the `edit_mls_message_v1` RPC.
- `ChatyBackendService.addStory` throws UnsupportedError; `markStoryViewed` and `logCall` are empty. A separate call-history service and status/media services exist and should be the authoritative implementation.
- The recorded Android verification run at commit `bf112d76b51611b237ab7664e3bdc85bce1d97a7` passed Flutter analyze and tests but failed the hosted Supabase Auth preflight, so APK build steps were skipped.

## Product rules

1. Keep existing user data, routes, Supabase RLS, authentication, and encryption protocol intact.
2. Never fall back to plaintext for a message that is already encrypted.
3. Keep templates as typed, versioned preferences; validate every value and provide a one-tap reset.
4. A setting is not “implemented” unless it visibly affects the intended UI, survives restart, works on Android and iOS, and has a test.
5. Do not use local-only mutation to imply server success. Optimistic changes must roll back on error.
6. Prefer shared components and design tokens over per-screen custom styling.
7. Feature switches must not bypass permissions, privacy, app-store rules, or security checks.

## Global customization architecture

Create a typed `ChatyAppearanceProfile` and a `ChatyTemplateRepository` that stores a schema-versioned profile in local preferences, with optional authenticated per-user cloud sync only after conflict and privacy behavior is defined. Migrate existing `appearance.*` preferences into the profile without losing settings.

Template groups:

- **App shell:** bottom navigation style/order/visibility, tab labels, header style, compact/comfortable density, navigation transitions.
- **Brand/theme:** light/dark/system, accent and semantic colors, surfaces, backgrounds, gradients, borders, radii, elevation, wallpaper, contrast.
- **Typography/accessibility:** font family where available, scale, line height, message density, reduce motion, contrast and larger tap targets.
- **Chats:** incoming/outgoing bubble shapes, colors, tails, spacing, timestamps, date separators, read/delivery ticks, quoted/replied messages, reactions, pinned/starred states, typing indicator, composer layout and attachment tray.
- **Media:** image/video preview layout, gallery grid, voice-note waveform and playback controls, document rows, download behavior.
- **Calls:** voice/video incoming screen, call controls layout, mini-call/PiP presentation, call history cards.
- **Updates/status:** status rings, status viewer controls, privacy indicators and status composer.
- **Lists and profile:** avatar shape/size, presence badges, unread counters, list density, profile cards.
- **Notifications:** in-app presentation and supported notification icon/badge options. OS notification capabilities remain platform-controlled.
- **Privacy and security:** privacy controls that are genuinely supported by backend/platform; never expose false switches for unsupported features.
- **Templates:** Default, Classic, Minimal, Compact, High Contrast, and user-created custom profiles. Preview before applying, reset category/all, import/export validated JSON without secrets.

Every template option must be connected to a real component. Avoid displaying inert option catalogs.

## Feature parity checklist

Use this as a gap-analysis checklist, not as a claim that all features exist.

### Messaging
- One-to-one and group conversations; create/edit group; member/admin roles; group description and invite links where supported.
- Text, emoji, reply/quote, forward, edit, delete for me/everyone, reactions, copy, select multiple, pin/star, message search, in-chat search.
- Delivery/read state, typing/presence, unread counts, mute/archive, drafts, pagination and scroll-to-latest.
- Reliable offline queue, idempotent send, retry UI, duplicate prevention, reconnect reconciliation.
- E2EE on supported message types with clear, truthful encryption status; no plaintext downgrade.

### Attachments and voice
- Camera/gallery/file picker, image/video/document previews, upload/download progress, cancel/retry, size/type validation.
- Voice-note record/pause/resume/cancel/send/playback, waveform, duration, audio focus, permission-denied handling.
- Cross-platform permission flows and lifecycle cleanup on Android/iOS.

### Calls
- Audio/video calls, incoming/outgoing/ringing/connected/ended states, decline/busy/missed handling, mute, camera toggle, speaker route, supported Bluetooth route, camera switch, PiP/foreground service where supported.
- ICE restart/reconnect, timeout, network loss, permission denial, cleanup on navigation/app lifecycle.
- Server-authoritative call history and access control; never log fake calls from UI state.

### Status/updates
- Create/view status, image/video/text where supported, expiry, view list/privacy rules, mark viewed server-side, mute/hide, correct media upload lifecycle.
- Do not label placeholder status functions as complete.

### Accounts, devices and privacy
- Email/username login, registration, password reset, session restore, account/profile editing, blocked users, report flows where implemented.
- Linked-device registration/revocation must persist server-side and invalidate access/key enrollment as appropriate.
- Privacy settings must map to real server/platform behavior and be covered by tests.

### Settings and customization
- Account, privacy, security, notifications, chats, storage/data, language, appearance, templates, linked devices, help/about.
- Searchable settings index, descriptions, live preview, reset, persistent values, migration, accessibility.
- Settings must not show controls that do nothing.

## Immediate engineering phases

### Phase A — correctness and security
1. Replace the legacy plaintext message-edit path with MLS payload encryption and `edit_mls_message_v1`. Use the current conversation's MLS group/epoch/ciphertext and active sender-device identity. Roll back optimistic text on failure. If the message is not an MLS message, use only an explicitly supported legacy path.
2. Remove the direct `messages` table plaintext edit fallback for encrypted rows.
3. Route status publishing/views and call history through their authoritative services; eliminate silent no-op facade methods.
4. Trace linked-device revocation to the server RPC and test server state after revocation.
5. Fix and rerun hosted Supabase Auth preflight; build and archive the Android test APK only after required security checks pass.

### Phase B — template foundation
1. Define a typed, versioned appearance profile and migration from current SharedPreferences keys.
2. Build shared theme tokens and selectors for shell, headers, bubbles, composer, list density, avatars, badges, and motion.
3. Add live preview/apply/reset and persistence tests.
4. Wire every visible customization option to a consuming widget. Remove inert chips and dead options.
5. Add template export/import with schema validation; exclude credentials, tokens, private keys, and account data.

### Phase C — component coverage
1. Extract chat composer, message bubble, reply preview, attachment tray, media preview, call controls, status card/viewer, chat list row, and profile header.
2. Refactor giant screens incrementally, preserving route/state behavior.
3. Add golden/widget tests for each template in light/dark mode and compact/large text.

### Phase D — parity and reliability
1. Complete the checklist above using current backend contracts; add migrations/RPCs only when required and with RLS/security review.
2. Add integration tests for offline queue, E2EE send/edit/delete, multi-device synchronization, status view, call lifecycle, account switching, and device revocation.
3. Run `dart format`, `flutter analyze`, `flutter test`, migration ledger checks, production-mock checks, Android build, and iOS build/test where a macOS runner is available.
4. Validate real devices and permissions. Record results and known gaps.

## Definition of done

A feature is complete only when: UI flow is wired end-to-end; data persists correctly; backend authorization is enforced; encryption is preserved; loading/empty/error/offline states are present; Android and iOS behavior is accounted for; automated tests pass; and the user can change applicable templates without breaking navigation or functionality.

## First acceptance tests

- Edit an MLS message: recipient sees the new decrypted content, server stores ciphertext only, unauthorized edits fail, and network failure restores the old local text.
- Change bottom navigation, header, bubble, composer, typography and wallpaper settings; navigate across all tabs; restart the app; selections remain consistent.
- Reset one category and reset all; unrelated settings remain unchanged for category reset.
- Mark a status viewed and verify the server state, then reopen on another device.
- End a call and verify exactly one authoritative call-history record.
- Revoke a linked device and verify it is absent from active device records and cannot continue sending as an active device.
- CI must pass the hosted Auth preflight before producing the release/test APK.
