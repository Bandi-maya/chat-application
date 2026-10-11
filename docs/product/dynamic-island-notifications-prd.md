# Dynamic Island Notifications — PRD and QA Plan

## Status
- Implementation branch: `main` (per project requirement).
- UI integration: implemented in the global in-app event toast overlay and Chaty Settings → Notifications.
- Runtime verification: pending GitHub Actions results and device QA.

## Goal
Offer an opt-in, compact, animated Dynamic Island-style presentation for Chaty in-app event notifications while preserving existing notification generation, privacy gates, and event-category preferences.

## Reference and adaptation
Reference: https://github.com/nithinpjohn/Dynamic-island-notifications

The reference is a Next.js/Framer Motion web component. Chaty is a Flutter mobile app, so the interaction is adapted with Flutter animated layout/opacity/size transitions rather than copying its web-only dependencies. The island is a black rounded pill at the top safe area; tap to expand/collapse; expanded mode can show up to three recent in-app event items.

## Product requirements
1. Add a persisted `Enable Dynamic Island` switch under Chaty Settings → Notifications; default off for backward compatibility.
2. When enabled, use the island presentation instead of the existing in-app event toast. When disabled, preserve the existing toast UI.
3. Respect the existing master in-app notification switch, app-lock content concealment, and existing event-specific notification settings.
4. Incoming events display the latest title, icon, and body; tapping toggles expansion and shows up to three recent in-app events.
5. Automatically dismiss using the existing event toast duration preference.
6. Provide a preview action that emits a sample in-app event and plays a short system UI click sound.
7. Keep notification content on-device and do not add analytics or network transmission.
8. Remain responsive to screen width, safe-area insets, and accessibility text scaling.

## UX and design
- Collapsed: compact black capsule, event icon, one-line title, unread indicator.
- Expanded: wider rounded panel, message preview, and up to two recent items beneath a subtle divider.
- Motion: ease-out/spring-like width and size transition; fade/slide on arrival; avoid blocking the chat screen.
- Touch: tap the pill to expand/collapse; the rest of the screen remains interactive.
- Settings: one global style switch plus a preview action; existing event category switches remain the source of truth for which events are generated.

## Scope boundary
This implementation handles Chaty's existing **in-app event notification stream while the Flutter app UI is alive**. The cited repository is web-only and does not implement Android system notification interception. Showing a floating island over other apps or while Chaty is backgrounded would require a separate Android native NotificationListenerService/overlay feature, explicit notification-access and draw-over-other-apps permission education, OEM-specific QA, and a privacy review. This change does not claim that cross-app/system-wide behavior.

## QA checklist
- [ ] Flutter analyze and unit/widget test suite pass on the current `main` commit.
- [ ] Toggle defaults off and survives app restart through existing GB feature preference persistence.
- [ ] Toggle on: newly triggered event uses island, not standard toast.
- [ ] Toggle off: existing toast remains unchanged.
- [ ] Master in-app notifications off: no island is shown.
- [ ] Existing event-specific toggles continue to gate event creation.
- [ ] Tap expands and collapses; recent items are capped at three.
- [ ] Island dismisses after the configured toast duration.
- [ ] Preview displays the sample and plays the system click sound.
- [ ] App-lock concealment still hides notification body where enabled.
- [ ] Narrow Android device, large text scale, and top cutout/safe-area layouts do not overflow.
- [ ] Android smoke test and manual physical-device verification complete.
- [ ] Confirm no notification payloads are logged or sent to a remote service.

## Known limitation / next phase
The preview sound uses Android/iOS system UI click feedback, not a configurable ringtone asset. A dedicated tone picker and true system-wide notifications should be scoped separately rather than implying that an in-app overlay can intercept OS notifications.
