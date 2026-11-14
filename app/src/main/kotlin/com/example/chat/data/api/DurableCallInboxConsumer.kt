package com.example.chat.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Exactly one consumer instance per account plane. WS is a wake only; it never bypasses ordered peek. */
class DurableCallInboxConsumer(private val stage: EncryptedCallInboxStage) {
    private val owners = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private data class Retry(val failures: Int, val nextNanos: Long)
    private val retries = java.util.concurrent.ConcurrentHashMap<String, Retry>()
    private fun retry(accountHash: String): Outcome {
        val count = ((retries[accountHash]?.failures ?: 0) + 1).coerceAtMost(5)
        val waitSeconds = (1L shl count).coerceAtMost(30)
        retries[accountHash] = Retry(count, System.nanoTime() + waitSeconds * 1_000_000_000L)
        return Outcome.RETRY_REQUIRED
    }
    enum class Outcome { UNSUPPORTED, STALE, CAUGHT_UP, RETRY_REQUIRED }
    /** Caller chooses account credentials before entry. This NEVER adopts/switches accounts or answers a call. */
    suspend fun consume(accountHash: String, client: DurableCallInboxClient, current: () -> Boolean,
        prepareRecovery: suspend () -> Unit = {}, stagedReady: suspend (List<DurableCallEvent>) -> Unit): Outcome = withContext(Dispatchers.IO) {
        owners.getOrPut(accountHash) { Mutex() }.withLock {
            if (!current()) return@withLock Outcome.STALE
            if (retries[accountHash]?.nextNanos?.let { System.nanoTime() < it } == true) return@withLock Outcome.RETRY_REQUIRED
            if (!client.supports()) return@withLock if (current()) Outcome.UNSUPPORTED else Outcome.STALE
            try {
                repeat(5) { // Bounded per wake, at most 500 events. No private-account polling timer.
                    if (!current()) return@withLock Outcome.STALE
                    val page = client.peek()
                    if (!current()) return@withLock Outcome.STALE
                    if (page.events.isEmpty()) {
                        if (page.hasMore) return@withLock retry(accountHash) // Invalid/nonadvancing page.
                        val pending = stage.pending(accountHash)
                        if (pending.isNotEmpty()) prepareRecovery()
                        if (current()) stagedReady(pending)
                        retries.remove(accountHash)
                        return@withLock Outcome.CAUGHT_UP
                    }
                    prepareRecovery() // Durable wake BEFORE encrypted stage and server ACK, only for actual work.
                    if (!current()) return@withLock Outcome.STALE
                    val ackIds = stage.stage(accountHash, page.events)
                    if (!current()) return@withLock Outcome.STALE
                    // All events now survive process death in the account's encrypted store.
                    // ACK only retires server events; never counts as answer/decline/delivery.
                    if (!client.acknowledge(ackIds)) return@withLock retry(accountHash)
                    if (!current()) return@withLock Outcome.STALE
                    stagedReady(stage.pending(accountHash)) // Callback owns idempotent UI/service admission.
                    if (!page.hasMore) { retries.remove(accountHash); return@withLock Outcome.CAUGHT_UP }
                }
                retry(accountHash)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { retry(accountHash) } // No ACK after save failure; retained stage survives failed ACK.
        }
    }
}
