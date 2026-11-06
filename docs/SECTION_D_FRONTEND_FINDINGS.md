# Section D frontend bug and safety findings

Source scope: effective0de33ce plus incoming-spec UI delta and this follow-up. Not a full APK/security assessment. Current Android/Gradle verification is pending; no backend or working call logic changed. Severity is impact if reproduced, not a claim of exploit or frequency.

## Blocking, not applied: stale incoming notification can decline a different call

High. app/src/main/java/com/example/chat/notifications/ChatyNotificationCoordinator.kt:241-245.
ACTION_DECLINE resolves an old nonce/account and its callId but invokes declineCall on current session without checking callId. Same-account stale notification can target a later incoming call. Non-exported receiver and scope guards help but do not prove same-call identity. Exact reproduction unrun.

Proposed patch text only, NOT applied, needs owner approval under no-working-logic rule:

```kotlin
ChatyNotificationPolicy.ACTION_DECLINE -> {
    val id = e.callId ?: return
    api.withAuthScope(account) {
        val current = calls.currentSession
        if (current?.callId == id && current.state == CallSessionState.incoming) {
            calls.declineCall()
        }
    }
    if (lastIncomingCallId == id) lastIncomingCallId = null
    lane.cancelCallFor(account, id)
}
```

Verify import of CallSessionState and exact withAuthScope semantics before execution. Test stale/current call A/B, account switch, terminal state, duplicate taps and cancelled nonce. No production safety clearance yet.

## Blocking: Mark as read is absent, Dismiss is local only

Medium, functional gap. ChatyNotificationPoster.kt:96-99 and ChatyNotificationCoordinator.kt:226. Current label is correctly Dismiss. ConversationsRepository.kt:734-749 uses mark_conversation_read legacy RPC and privacy receipt policy. Do not relabel Dismiss or bypass privacy controls. Required question: which existing authorized scoped custom-backend endpoint can mark conversation read, and should receipt-disabled local unread clearing be supported there? Frontend adapter can follow an agreed contract; no backend changes here.

## Blocking: app-closed delivery without persistent notification

High, delivery/reliability requirement. notifications/ChatyRealtimeForegroundService.kt:31-46,73-82 keeps websocket alive with startForeground and ongoing notification. No approved non-Firebase mechanism here removes notification while preserving process-dead wake delivery. Do not remove service notification. Decision question: what existing non-Firebase server/device wake mechanism is available, or may this requirement remain unavailable? Force-stop/OEM killed delivery not guaranteed.

## UI fixed in follow-up, verification pending: repeated intent while action pending

Medium. ui/calls/bundles/BundleIncomingCallView.kt:76-127. Most individual styles previously ignored actionPending even though host action gate protects service calls. Added shared pending-guarded UI callbacks to all nine styles/banner, using latest callbacks. This is UI intent suppression only, not service/state logic. New additive device test verifies tap controls pending/ready for four tap-capable styles. Current source-only contract PASS; device execution pending.

## Hypothesis only: concurrent media prepare/end cleanup

High if reproduced, not established root cause. data/services/WebRtcCallMediaController.kt:130-136,644-699. prepare invokes disposeInternal, multiple mutable native handles, no controller mutex in reviewed source. Could be protected by caller scheduling; no crash trace proves a race. Do not add a speculative lock to working media. Need sanitized native stack and overlapping prepare/end/permission-failure cycles, proving actual media prepared and no tolerated setup failure.

## Visual/runtime gaps not fixed by source contracts

Medium. Incoming CallView mapped layouts and OngoingCallScreen.kt:364-370,AudioFourControls. Narrow220dp/2x,640dpheight, three-button navigation and safe-area pixels must be checked in the combined capture. No actual pixel evidence delivered to this writer yet. Latest audio explicit-cell fit is source-only until strict pass. Source tests cannot certify48dp bounds,WCAG contrast,OEM exactness or144Hz. Two ambiguous reference tiles01/02 remain mapping-blocked for redesign; prior user-closed tasks must not be reopened or re-asked.

## Positive controls observed, not a security certification

Notification action receivers are not exported in manifest; account identity/token and action nonce are checked; reply is disallowed for locked app/vault. Incoming camera preview is local-only, no WebRTC/recorder in preview class, checks granted camera/resumed/unlocked and closes screen-off/background/dispose. Production video upgrade capability defaults remain false. These are code observations, not dynamic proof or no-leak guarantee.
