# Chaty Island Research: Android API Feasibility & Takeover Constraints

## 1. Notification Heads-up Suppression ("Take-over Mode")
### Public Android API Reality
- In standard Android (AOSP), **there is NO public API allowing a non-system app to selectively suppress the visual heads-up floating banner of another app while keeping it in the notification shade**.
- `NotificationListenerService.cancelNotification(key)` removes the notification from **both** the heads-up display and the system notification shade.
- `NotificationListenerService.snoozeNotification(key, duration)` removes the notification temporarily from the shade, but re-alerts when the snooze timer expires.

### Architectural Strategy for Chaty Island:
1. **Mirror Mode (Default & Safe)**:
   - Does not touch third-party notifications.
   - Island displays incoming alerts and manages its own interactive stack without modifying the notification drawer.
2. **Take Over Mode (Opt-In)**:
   - Captures the `StatusBarNotification`, parses `PendingIntent` and `RemoteInput`, stores it in `IslandStore`, and calls `cancelNotification(key)` for eligible applications (e.g. messaging, email).
   - Exempt: Calls (`CATEGORY_CALL`), ongoing media, system alerts, foreground service notifications with `FLAG_NO_CLEAR`.
   - Island acts as the active heads-up inbox with inline reply and launch capabilities.
3. **Quiet Visuals via OEM Settings (Guided Deep-link)**:
   - Direct link to `Settings.ACTION_APP_NOTIFICATION_SETTINGS` where users can disable floating/pop-up banners on a per-app basis while retaining notification shade items.

## 2. WindowManager Overlay & Touch Security (Android 12+)
- `TYPE_APPLICATION_OVERLAY`:
  - Overlay windows with alpha < 1.0 or non-modal flags over secure apps (banking, credential managers) can be blocked by Android 12+'s Untrusted Touch filter (`FLAG_WINDOW_IS_OBSCURED` or `FLAG_WINDOW_IS_PARTIALLY_OBSCURED`).
  - Island must maintain 1.0 window opacity and strictly resize its overlay window dimensions to match the pill size so background touches pass unobstructed.

## 3. Cutout Detection API Matrix
- **API 30+ (Android 11+)**: `windowManager.currentWindowMetrics.windowInsets.displayCutout`.
- **API 28-29 (Android 9/10)**: `rootWindowInsets.displayCutout`.
- **Fallback**: System status bar height resource dimen `status_bar_height`.
- **Layout Flag**: `WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` (API 30+) / `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`.
- Flags: `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS | FLAG_NOT_TOUCH_MODAL`. Focusable only during Inline Reply with `FLAG_ALT_FOCUSABLE_IM`.

## 4. Restricted Settings on Android 13+ (Sideloaded APKs)
- On Android 13+, accessibility and notification listener toggles are greyed out by default with "Restricted setting" dialog for sideloaded APKs.
- Guided UX: Guide user to App Info -> Three-dot menu (`⋮`) -> "Allow restricted settings" -> Enter PIN/biometrics.
