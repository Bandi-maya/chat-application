package com.example.chat.data.api

data class ApiRoutingSnapshot(val tenantId: String, val baseUrl: String, val webSocketTemplate: String, val generation: Long)

/** Immutable, opaque account/routing scope. Never log token or this snapshot. Identity is reference equality. */
class ApiAccountScope(val userId: String?, val token: String?, val tenantId: String,
    val baseUrl: String, val webSocketTemplate: String, val generation: Long)

/** All session/config mutations and final delivery share [lock]. No asynchronous work may escape a delivery. */
class ApiAccountScopeOwner(private val lock: Any, private val auth: () -> ApiAuthSnapshot,
    private val routing: () -> ApiRoutingSnapshot) {
    private var previousAuth: ApiAuthSnapshot? = null
    private var previousRouting: ApiRoutingSnapshot? = null
    private var current: ApiAccountScope? = null
    private var generation = 0L
    fun snapshot(): ApiAccountScope = synchronized(lock) {
        val a = auth(); val r = routing()
        val oldA = previousAuth; val oldR = previousRouting
        if (current == null || oldA?.generation != a.generation || oldR != r) {
            current = ApiAccountScope(a.user?.id, a.token, r.tenantId, r.baseUrl, r.webSocketTemplate, ++generation)
        }
        previousAuth = a; previousRouting = r
        current!!
    }
    /** Short synchronous callback only. No IO, deferred payloads, or reentrant account mutation. */
    fun withScope(expected: ApiAccountScope, block: () -> Unit): Boolean = synchronized(lock) {
        if (snapshot() !== expected || expected.userId.isNullOrBlank() || expected.token.isNullOrBlank()) false
        else { block(); snapshot() === expected }
    }
}
