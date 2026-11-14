package com.example.chat.data.api

import com.example.chat.data.e2ee.SecureKeyValueStore
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** One encrypted blob per canonical account hash. Save whole stage BEFORE returning event IDs eligible for ACK. */
class EncryptedCallInboxStage(private val storage: SecureKeyValueStore) {
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private fun key(hash: String): String {
        require(hash.matches(Regex("[a-fA-F0-9]{32,128}")))
        return "chaty.call.inbox.v1.$hash"
    }
    private val terminal = setOf("end", "hangup", "decline", "reject", "busy", "cancel")
    private fun initialOffer(event: DurableCallEvent) = event.kind == "offer" && event.data.opt("ice_restart") != true
    private data class State(val events: List<DurableCallEvent>, val resolved: Map<String, Instant>)
    private suspend fun read(key: String): State {
        val raw = storage.read(key) ?: return State(emptyList(), emptyMap())
        check(raw.toByteArray(Charsets.UTF_8).size <= 2_097_152) { "Local call stage is oversized." }
        // Upgrade the original array in place only on a successful encrypted save.
        val root = if (raw.trimStart().startsWith("[")) null else JSONObject(raw)
        val rows = root?.getJSONArray("events") ?: JSONArray(raw)
        check(rows.length() <= 128)
        val resolved = root?.optJSONObject("resolved") ?: JSONObject()
        check(resolved.length() <= 128)
        return State((0 until rows.length()).map { DurableCallEvent.decode(rows.getJSONObject(it)) },
            resolved.keys().asSequence().associateWith { Instant.parse(resolved.getString(it)) })
    }
    private suspend fun save(key: String, state: State) {
        val rows = JSONArray().also { rows -> state.events.forEach { rows.put(it.encoded()) } }
        val resolved = JSONObject().also { row -> state.resolved.forEach { (call, expiry) -> row.put(call, expiry.toString()) } }
        val encoded = JSONObject().put("events", rows).put("resolved", resolved).toString()
        check(state.events.size <= 128 && state.resolved.size <= 128 && encoded.toByteArray(Charsets.UTF_8).size <= 2_097_152) { "Local call stage is full. Server inbox must retain unacknowledged events." }
        storage.write(key, encoded) // Storage failure propagates. Caller MUST NOT ACK or answer.
    }
    /** Persist the user's answer/decline intent BEFORE media or the signal. No replay may ring it again. */
    suspend fun resolveCall(accountHash: String, callId: String, now: Instant = Instant.now()) =
        locks.getOrPut(accountHash) { Mutex() }.withLock {
            require(callId.length in 1..100 && callId.none(Char::isISOControl))
            val key = key(accountHash); val state = read(key)
            val resolved = state.resolved.filterValues { it.isAfter(now) } + (callId to now.plusSeconds(65))
            save(key, State(state.events.filter { it.callId != callId && it.expiresAt.isAfter(now) }, resolved))
        }
    suspend fun stage(accountHash: String, incoming: List<DurableCallEvent>, now: Instant = Instant.now()): List<String> =
        locks.getOrPut(accountHash) { Mutex() }.withLock {
            val key = key(accountHash)
            val state = read(key)
            val resolved = state.resolved.filterValues { it.isAfter(now) }
            val old = state.events.filter { it.expiresAt.isAfter(now) && (!initialOffer(it) || it.callId !in resolved) }
            val all = old.associateBy { it.eventId }.toMutableMap()
            incoming.forEach { event ->
                val prior = all[event.eventId]
                check(prior == null || prior.encoded().toString() == event.encoded().toString()) { "Conflicting replay event." }
                if (event.expiresAt.isAfter(now) && (!initialOffer(event) || event.callId !in resolved)) all[event.eventId] = event
            }
            // Preserve terminal tombstones until expiry so a later replay cannot resurrect the offer.
            val terminalSeq = all.values.filter { it.kind in terminal }.groupBy { it.callId }.mapValues { (_, rows) -> rows.maxOf { it.sequence } }
            val retained = all.values.filter { event ->
                event.kind !in setOf("offer", "ice", "candidate") || terminalSeq[event.callId] == null
            }.sortedBy { it.sequence }
            check(retained.map { it.sequence }.distinct().size == retained.size) { "Conflicting local call ordering." }
            save(key, State(retained, resolved))
            incoming.map { it.eventId } // Expired/superseded events are safely discarded by this saved state, not treated as offers.
        }
    suspend fun pending(accountHash: String, now: Instant = Instant.now()): List<DurableCallEvent> =
        locks.getOrPut(accountHash) { Mutex() }.withLock {
            val state = read(key(accountHash))
            state.events.filter { it.expiresAt.isAfter(now) && (!initialOffer(it) || state.resolved[it.callId]?.isAfter(now) != true) }.sortedBy { it.sequence }
        }
    suspend fun forget(accountHash: String, eventIds: Set<String>) = locks.getOrPut(accountHash) { Mutex() }.withLock {
        val key = key(accountHash)
        // Do not erase terminal tombstones early; they suppress delayed offers until expiry.
        val state = read(key); val now = Instant.now()
        save(key, State(state.events.filter { it.expiresAt.isAfter(now) && (it.eventId !in eventIds || it.kind in terminal) },
            state.resolved.filterValues { it.isAfter(now) }))
    }
}
