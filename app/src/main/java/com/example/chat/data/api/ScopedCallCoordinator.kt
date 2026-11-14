package com.example.chat.data.api

import kotlinx.coroutines.*

/** Lock order: auth transition lock -> stateLock. Never the reverse. No IO under either lock.
 * Auth listeners must call transition synchronously for every new immutable snapshot. */
class ScopedCallCoordinator(
    private val scope: CoroutineScope,
    private val current: () -> ApiAccountScope,
    private val guarded: (ApiAccountScope, () -> Unit) -> Boolean,
    private val clientFor: (ApiAccountScope, CallSignalScope) -> ApiCallSignalClient,
    private val cancelRequests: () -> Unit,
    private val deliver: (ApiAccountScope, Map<String, Any?>) -> Unit,
    private val clock: () -> Long,
    private val legacyAllowed: (ApiAccountScope) -> Boolean = { true },
    private val inboxWake: (ApiAccountScope) -> Unit = {},
) {
    private val stateLock = Any()
    private var account: ApiAccountScope? = null
    private var client: ApiCallSignalClient? = null
    private var requestScope: CallSignalScope? = null
    private var polling: Job? = null
    private val posts = mutableSetOf<Job>()
    private var foreground = false
    private var nextPoll = 0L
    private var failures = 0
    private val dedup = CallSignalDeduplicator()
    /** Called under auth lock. Cancellation/state changes only. */
    fun transition(next: ApiAccountScope) = synchronized(stateLock) {
        if (account === next) return@synchronized
        polling?.cancel(); polling = null; posts.forEach { it.cancel() }; posts.clear(); cancelRequests()
        account = next; client = null; requestScope = null; nextPoll = 0; failures = 0; dedup.clear()
    }
    fun foreground(value: Boolean) = synchronized(stateLock) {
        foreground = value
        if (!value) { polling?.cancel(); polling = null; posts.forEach { it.cancel() }; posts.clear(); cancelRequests() }
    }
    private fun prepare(expected: ApiAccountScope): Pair<ApiCallSignalClient, CallSignalScope>? = synchronized(stateLock) {
        if (account !== expected || expected.userId.isNullOrBlank() || expected.token.isNullOrBlank()) return@synchronized null
        if (client == null) {
            requestScope = CallSignalScope(expected.userId!!, expected.baseUrl, expected.token!!, expected.generation, expected.tenantId)
            client = clientFor(expected, requestScope!!)
        }
        client!! to requestScope!!
    }
    fun socketSignal(expected: ApiAccountScope, signal: Map<String, Any?>) {
        if (!legacyAllowed(expected)) { inboxWake(expected); return }
        guarded(expected) {
            synchronized(stateLock) {
                if (account === expected && dedup.accept(expected.generation, signal, clock())) deliver(expected, signal)
            }
        }
    }
    fun send(expected: ApiAccountScope, target: String, signal: Map<String, Any?>) {
        guarded(expected) {
            val prepared = prepare(expected) ?: return@guarded
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                try { prepared.first.send(prepared.second, target, signal) }
                finally { synchronized(stateLock) { posts.remove(job) } }
            }
            synchronized(stateLock) { if (account === expected) posts.add(job) else job.cancel() }
            job.start()
        }
    }
    /** One-shot poll for a push ring nudge (calls matrix "REST fallback for
     *  ring when socket is down"): the app may be backgrounded when FCM wakes
     *  it, so this bypasses the foreground gate and the backoff clock. Shares
     *  the same single-flight slot and dedup as the foreground cadence. */
    fun pollNow(expected: ApiAccountScope = current()): Job? {
        if (!legacyAllowed(expected)) { inboxWake(expected); return null }
        var active: Job? = null
        guarded(expected) {
            val prepared = prepare(expected) ?: return@guarded
            synchronized(stateLock) {
                if (account !== expected) return@synchronized
                val existing = polling
                if (existing?.isActive == true) {
                    active = existing
                    return@synchronized
                }
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    val result = prepared.first.poll(prepared.second)
                    guarded(expected) {
                        synchronized(stateLock) {
                            if (account !== expected) return@synchronized
                            if (result is ApiCallSignalClient.PollResult.Signals) {
                                result.signals.forEach { signal ->
                                    if (dedup.accept(expected.generation, signal, clock())) deliver(expected, signal)
                                }
                            }
                        }
                    }
                }
                polling = job
                active = job
                job.start()
            }
        }
        return active
    }

    /** Invoke from existing cadence; never creates another loop. */
    fun tick() {
        val expected = current()
        if (!legacyAllowed(expected)) { inboxWake(expected); return }
        guarded(expected) {
            val prepared = prepare(expected) ?: return@guarded
            synchronized(stateLock) {
                if (!foreground || account !== expected || polling?.isActive == true || clock() < nextPoll) return@synchronized
                polling = scope.launch(start = CoroutineStart.LAZY) {
                    val result = prepared.first.poll(prepared.second)
                    guarded(expected) {
                        synchronized(stateLock) {
                            if (account !== expected || !foreground) return@synchronized
                            when (result) {
                                is ApiCallSignalClient.PollResult.Signals -> {
                                    failures = 0; nextPoll = clock() + 4000
                                    result.signals.forEach { signal ->
                                        if (dedup.accept(expected.generation, signal, clock())) deliver(expected, signal)
                                    }
                                }
                                ApiCallSignalClient.PollResult.Failed -> { failures = (failures + 1).coerceAtMost(4); nextPoll = clock() + (4000L shl failures) }
                                ApiCallSignalClient.PollResult.Unauthorized, ApiCallSignalClient.PollResult.Unavailable -> nextPoll = Long.MAX_VALUE
                                else -> Unit
                            }
                        }
                    }
                }.also { it.start() }
            }
        }
    }
}
