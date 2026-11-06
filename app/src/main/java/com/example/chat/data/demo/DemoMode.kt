package com.example.chat.data.demo

/**
 * Offline demo session marker (NOT in Flutter). While the server is
 * unreachable the login screen offers a clearly labelled sample session built
 * from [DemoSampleData]: synthetic ids, no network calls, no real account and
 * no real data. It never authenticates against the backend and normal auth is
 * unchanged for real accounts.
 */
object DemoMode {
    /** Ids use a prefix no backend id (uuid-shaped) can collide with. */
    const val ID_PREFIX = "demo:"
    const val USER_ID = "demo:offline-user"
    const val TOKEN = "demo:offline-token"
    const val UNAVAILABLE_MESSAGE = "Unavailable in demo - this action needs the server."

    fun isDemoUser(userId: String?): Boolean = userId == USER_ID
    fun isDemoId(id: String?): Boolean = id?.startsWith(ID_PREFIX) == true

    /**
     * True while a demo session is active in this process. Api-plane
     * transports fail closed on this flag, so demo mode is fully offline
     * (including unsigned requests), not just unauthenticated.
     */
    @Volatile var active: Boolean = false

    /** STRICT proof that a persisted REST credential pair (api_hub_token /
     *  api_hub_user) is the demo identity - BOTH sides must provably match.
     *  A mixed or damaged pair (real token + demo JSON, demo token + real
     *  JSON, unparseable payload) is NOT demo: it is never armed as demo and
     *  NEVER erased by demo cleanup, so raw real bytes always survive
     *  (peer-review mixed-store blocker). */
    fun isDemoRestSession(token: String?, userJson: String?): Boolean {
        if (token != TOKEN) return false
        val raw = userJson ?: return false
        val parsed = runCatching { org.json.JSONObject(raw).optString("id") }.getOrNull()
        return parsed != null && parsed == USER_ID
    }

    /** Exactly one side of a composed session is demo: a corrupt/mixed
     *  identity (real token + demo user, or demo token + real user). Such a
     *  session must be QUARANTINED (dropped in memory only, raw bytes kept) -
     *  never run as a network-enabled real session and never demo-armed. */
    fun isMixedRestSession(token: String?, userId: String?): Boolean =
        (token == TOKEN) != (userId == USER_ID)

    /** Fail-closed chokepoint for every backend client outside the guarded
     *  REST transport (PostgREST query/rpc, Supabase Storage, FCM device
     *  registration): while a demo session is active these clients must
     *  never dispatch to a real backend, whatever the caller. */
    fun requireRealBackend() {
        if (active) throw IllegalStateException(UNAVAILABLE_MESSAGE)
    }
}

/**
 * Thrown fail-closed when an api-plane request would carry the synthetic demo
 * token: the demo session must never reach the network (see OkHttpApiTransport).
 */
class DemoModeOfflineException : Exception(DemoMode.UNAVAILABLE_MESSAGE)
