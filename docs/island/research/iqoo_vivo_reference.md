# Chaty Island Research: iQOO / vivo Funtouch OS & OriginOS Reference

## 1. iQOO / vivo Island Paradigms (Origin Island / Atomic Island)
- **OriginOS & Funtouch OS 14/15**: Introduced "Atomic Island" and dynamic notification bar pills wrapping around the punch-hole camera.
- **Visual Integration**: Centered directly over the display punch-hole cutout with left/right symmetrical balance.
- **Activities Supported**: Ride-hailing alerts, food delivery status, flight info, screen recording indicators, incoming calls, music playback.

## 2. iQOO OEM Aggressive Background Management & Resilience
Vivo/iQOO devices (Funtouch OS / OriginOS) enforce strict background process termination policies:
1. **Autostart Permission**: Disabled by default for non-system apps. Without this, services killed on RAM pressure will never revive.
2. **Background Power Optimization**: Default is "Smart Control" or "Restrict background battery". Must be set to "Allow high background power consumption" (Don't optimize).
3. **Lock in Recents**: Users can swipe down on the app tile in Android Overview (Recents) to lock it with a padlock icon.
4. **Notification Listener Service Rebinding**:
   - `NotificationListenerService` can disconnect silently during deep doze.
   - Solution: Implement `onListenerDisconnected()` with `requestRebind(ComponentName)`.
   - Also reconcile `activeNotifications` whenever device wakes up (`ACTION_SCREEN_ON`).

## 3. Punch-hole Geometry on iQOO Devices
- Most modern iQOO devices (iQOO 9/11/12/Neo series) feature a centered single punch-hole camera:
  - Diameter: ~3.8mm to 4.2mm (~28–32 dp).
  - Status bar height: ~32–40 dp depending on DPI and resolution.
  - Previous bug (K-6): Using `statusBarHeight * 0.18` offset placed the pill 16 dp too low, visibly detached from the camera hole.
  - Fix: Query `DisplayCutout.boundingRectTop` or `boundingRects` to position the center of the 36 dp pill directly aligned with the center Y of the cutout bounding box.
