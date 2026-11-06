# P-03: ChatyRealtimeForegroundService FGS type - options for Bandi

Current: `ChatyRealtimeForegroundService` declared `foregroundServiceType="remoteMessaging"`
(+ FOREGROUND_SERVICE_REMOTE_MESSAGING permission). It maintains the app's
persistent WebSocket (wss://api.hub.logybyte.in) for real-time message delivery.
No code change made, per instruction.

Policy reference (verified 2026-11-04):
https://support.google.com/googleplay/android-developer/answer/13392821

## Options

1. Keep `remoteMessaging`
   - Play's definition covers relaying messages FROM the user's device TO
     ANOTHER DEVICE (e.g. companion/hand-off continuity). A client-to-server
     socket for the user's own message delivery is a stretch of that
     definition, so the Play declaration (video + description) is likely to be
     questioned.
   - Delivery impact: none (status quo, service runs as today).

2. Switch to `specialUse`
   - Declare "maintains persistent connection for real-time message delivery
     where push is unavailable". Approval is discretionary and needs strong
     justification; without FCM (banned) the justification is real but not
     guaranteed to pass.
   - Delivery impact: none if approved; if rejected, blocks the release.

3. Switch to `dataSync`
   - Closer wording for server sync, BUT Android 15+ caps dataSync FGS at
     ~6 hours per 24-hour window, after which the service is stopped.
   - Delivery impact: REAL DEGRADATION - the socket would die after the daily
     budget, and background message delivery would silently stop for heavy-use
     days. Not recommended for an always-on messenger.

4. Remove the FGS entirely; rely on push + WorkManager
   - The policy-clean path Google steers toward. But FCM is banned (Bandi),
     and without a push channel, background delivery effectively stops; a
     free UnifiedPush distributor could fill the gap but adds a dependency
     and server work (backend change - out of frontend scope).
   - Delivery impact: SEVERE without a push replacement.

## Recommendation for the decision
If a release must ship soon with background delivery intact: keep option 1 and
prepare the declaration carefully, or try option 2 with the honest
justification. Long-term healthy path is option 4 with a non-Firebase push
channel, which needs backend work and Bandi's call on the Firebase ban.
