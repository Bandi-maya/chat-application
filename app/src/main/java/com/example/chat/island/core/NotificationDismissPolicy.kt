package com.example.chat.island.core

/** Cancel only an explicitly eligible message after the host confirms drawing
 * its exact notification key. All state is bounded, memory-only and expires. */
class NotificationDismissPolicy(private val now: () -> Long) {
    data class Eligibility(val enabled: Boolean, val ordinaryMessage: Boolean,
        val ongoing: Boolean = false, val service: Boolean = false,
        val alarmOrCall: Boolean = false, val progress: Boolean = false,
        val groupSummary: Boolean = false, val ownPackage: Boolean = false,
        val dismissible: Boolean = true)
    private data class Pending(val deadline: Long, val receipt: Long)
    private var sequence = 0L
    private val waiting = linkedMapOf<String, Pending>()
    private val selfCancelled = linkedMapOf<String, Long>()
    private fun clean() {
        val time=now()
        waiting.entries.removeAll { it.value.deadline <= time }
        selfCancelled.entries.removeAll { it.value <= time }
    }
    fun eligible(e: Eligibility): Boolean = e.enabled && e.ordinaryMessage && !e.ongoing && !e.service &&
        !e.alarmOrCall && !e.progress && !e.groupSummary && !e.ownPackage && e.dismissible
    fun awaiting(key: String, receipt: Long?): Boolean { clean();return receipt != null && waiting[key]?.receipt == receipt }
    fun offer(key: String, e: Eligibility): Long? {
        clean(); waiting.remove(key)
        if (key.isBlank() || !eligible(e)) return null
        val receipt=++sequence
        waiting[key] = Pending(now() + 10_000, receipt)
        while(waiting.size > 256) waiting.remove(waiting.keys.first())
        return receipt
    }
    fun displayed(key: String, receipt: Long?, stillAllowed: Boolean, cancel: (String) -> Unit): Boolean {
        clean()
        val pending=waiting[key] ?: return false
        if (receipt == null || pending.receipt != receipt) return false
        waiting.remove(key)
        if (!stillAllowed) return false
        selfCancelled[key]=now()+10_000
        while(selfCancelled.size > 256) selfCancelled.remove(selfCancelled.keys.first())
        return try { cancel(key); true } catch (_: Exception) { selfCancelled.remove(key); false }
    }
    fun consumeOwnRemoval(key: String): Boolean { clean(); waiting.remove(key);return selfCancelled.remove(key)!=null }
    fun clear() { waiting.clear();selfCancelled.clear() }
}
