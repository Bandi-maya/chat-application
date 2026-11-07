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
    fun onSyncNudge()

    /** Incoming-call ring (calls matrix "REST fallback for ring when socket is
     *  down"): the nudge carries only the call id - a routing handle, not
     *  content. The handler fetches the queued signal over the authenticated
     *  channel and surfaces the full-screen ring. Default no-op keeps fakes
     *  and older handlers honest. */
    fun onIncomingCallNudge(callId: String) { }
}

object PushWakeHolder {
    @Volatile
    var handler: PushWakeHandler? = null
}
