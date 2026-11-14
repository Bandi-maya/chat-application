package com.example.chat.notifications

/** Push-agnostic wake interface (push matrix; FCM approved 2026-11-07).
 *
 *  A nudge from ANY transport (today: content-free FCM data message; later
 *  possibly a UnifiedPush distributor) only means "check in". The handler
 *  fetches and decrypts over the authenticated channel; the nudge itself
 *  carries no content, identifiers or metadata. ServiceLocator installs the
 *  real handler; tests install a fake.
 */
interface PushWakeHandler {
    suspend fun onAccountSyncNudgeAndWait(bindingId: String) {}
    suspend fun onAccountIncomingCallNudgeAndWait(bindingId: String, callId: String) {}

    fun onSyncNudge()

    /** Bounded-wait variant for FCM worker threads so Android keeps the wake
     *  lock alive until message sync and notification posting complete. */
    suspend fun onSyncNudgeAndWait() { onSyncNudge() }

    /** Incoming-call ring (calls matrix "REST fallback for ring when socket is
     *  down"): the nudge carries only the call id - a routing handle, not
     *  content. The handler fetches the queued signal over the authenticated
     *  channel and surfaces the full-screen ring. Default no-op keeps fakes
     *  and older handlers honest. */
    fun onIncomingCallNudge(callId: String) { }

    /** Bounded-wait variant for FCM incoming-call worker threads. */
    suspend fun onIncomingCallNudgeAndWait(callId: String) { onIncomingCallNudge(callId) }
}

object PushWakeHolder {
    @Volatile
    var handler: PushWakeHandler? = null
}
