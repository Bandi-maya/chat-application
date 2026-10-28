# GB-style customization parity: implementation and verification tracker

Target branch: `kotlin`  
Working branch: `feat/gb-customization-parity-audit`  
Reference: user-supplied modified GBWhatsApp APK, inspected statically only. No proprietary APK code/assets are copied.

## Completion contract

A feature is **Complete** only when all of the following are true:
1. The option is present in the correct Chaty settings screen with a human-readable label, help text, and valid control.
2. Its value persists across process restart and reset/default behavior is covered.
3. The option changes the relevant Chaty UI/behavior; it is not a no-op or a stored preference with no consumer.
4. Invalid/unavailable actions fail safely and communicate the limitation.
5. Unit/UI tests cover the setting and its observable effect.
6. The relevant screen is captured on emulator in light and dark mode, checked at compact and large font sizes, and compared with the reference.
7. No backend, account/session, message transport, or MLS encryption behavior is changed unless a feature explicitly requires a supported backend contract.

Do not mark a feature complete based only on its existence in `GbFeatureCatalog`.

## Current known state

- [x] Chaty Customization Hub navigation is wired on branch `kotlin`.
- [x] Existing theme, home, conversation, privacy, notification, storage/media, and navigation-effect pages are reused where available.
- [x] Build pipeline can compile the current Kotlin source and produce a debug APK (workflow run 40695258896).
- [ ] Complete GB option inventory from the reference's visible settings screens and all submenus.
- [ ] Consumer mapping for every catalog key; identify stored-only options and unsupported server-dependent options.
- [ ] Implement and test every missing consumer; no placeholder controls or generic style values.
- [ ] Screen-by-screen visual parity review from reference screenshots.
- [ ] Full emulator audit of every Chaty screen, component, setting, and each customization combination.
- [ ] Crash-free runtime test and regression run on the final candidate APK.

## Feature inventory from the current Kotlin catalog

This is an inventory of known catalog categories, **not a claim that every option works**.

- [ ] Navigation & touch effects — tap feedback, emoji effect, falling effect, effect selection.
- [ ] Chat list rows — contact/text colors, unread badge colors, online/last-seen labels, online dot, archived shortcut visibility.
- [ ] Presence & activity alerts — online, typing, status, and profile-change alerts, including foreground/background/text colors.
- [ ] Home header — home layout variant, stories strip/style, group separation, display name, ghost mode, camera shortcut, airplane mode, header colors, tab indicator.
- [ ] Privacy — read receipts, view-once behavior, forwarding label, call audience, hide privacy state, status views, anti-revoke message/status and notifications, blue-ticks-after-reply.
- [ ] Status & stories — visibility and presentation, audio status support.
- [ ] Chat bubbles & ticks — tick style, bubble shape, text size, background and text colors for incoming/outgoing messages, date labels.
- [ ] Universal behavior — heads-up alerts, online presence, high-resolution image preference.
- [ ] Calls appearance — call screen background, text, icon colors.
- [ ] Conversation header — header colors, avatar/name/call actions/status line and status-pill colors.
- [ ] Quick contact — off/left/right/floating placement.
- [ ] Universal colors — light/dark conversation backgrounds, navigation bar and list colors.
- [ ] Media limits & quality — upload-size limits, audio limit, image selection limit, multiple-document sending.
- [ ] Composer appearance — composer background and associated controls.

## Required screen-by-screen visual audit

For each screen, capture: initial state, every expanded sheet/dialog/menu, each control's changed state, reset/default state, keyboard visible, empty/loading/error states, light and dark themes, narrow phone, and large font scale.

- [ ] Customization Hub / Chaty Settings landing screen.
- [ ] Feature Center category overview, search results, filtered categories, toggle, choice, slider, color picker, action, reset confirmation.
- [ ] Universal appearance and colors.
- [ ] Home screen settings and every selectable home layout.
- [ ] Conversation settings, incoming/outgoing bubble variants, tick styles, composer.
- [ ] Privacy center and each privacy/security setting.
- [ ] Notifications and presence alerts.
- [ ] Storage/media, media limits, upload/send flows.
- [ ] Navigation/touch effects and motion preferences.
- [ ] Theme Studio and theme apply/reset flows.
- [ ] Backup/restore sheets and failure paths.
- [ ] App lock / chat lock flows and recovery/error paths.
- [ ] Calls UI and call-related preferences.
- [ ] Home, chat list, chat detail, contact/profile, status, camera/media picker, dialogs, bottom sheets, snackbars, and system-bar insets after settings are applied.

## Behavior / persistence test matrix

- [ ] Every catalog key has a declared owner/consumer or is explicitly marked unsupported and removed from the selectable UI.
- [ ] Every choice has real, supported choices; no generic `Style 1/2/3` labels unless those styles exist.
- [ ] Color inputs accept supported hex forms, reject malformed values, and preserve alpha semantics.
- [ ] Slider bounds, units, defaults, and displayed values match the feature.
- [ ] Bundled presets apply atomically and reset only intended keys.
- [ ] Values survive app restart and account switching does not leak user-specific preferences.
- [ ] Search finds labels, aliases, categories, and legacy key names.
- [ ] Permission-dependent controls explain permission state and deep-link to supported settings.
- [ ] Backend-dependent actions use Chaty's existing API/auth contracts; no Supabase/WhatsApp private endpoints are added.
- [ ] Offline, signed-out, backend error, permission denied, and unsupported-device behavior is explicit.
- [ ] Accessibility: touch target size, semantics, contrast, TalkBack labels, keyboard focus, and font scaling.
- [ ] Regression: existing login/session, chat send/receive, calls, media, notifications, encryption, and navigation unaffected.

## Evidence required before closure

- [ ] APK artifact from the exact final commit.
- [ ] Build, unit-test, instrumentation-test and emulator-shard results all successful.
- [ ] Screenshot bundle with reference/candidate pairs and documented differences.
- [ ] Machine-readable setting-to-consumer map with zero unexplained catalog keys.
- [ ] Final report lists every supported, unsupported, and deferred option explicitly.

## Important limitation

The uploaded APK has only been statically inspected for archive structure and string identifiers so far. It has not been installed or driven through its UI in this environment. Therefore exact pixel parity cannot honestly be asserted until reference screenshots or a runnable reference environment are available. The original Chaty app should implement equivalent behavior in its own architecture rather than copying proprietary APK code or assets.
