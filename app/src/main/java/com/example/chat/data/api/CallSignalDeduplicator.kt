package com.example.chat.data.api

/** Bounded replay suppression only; NOT a server dequeue/exactly-once guarantee. */
internal class CallSignalDeduplicator {
    private val seen = linkedMapOf<String, Long>()
    @Synchronized fun clear() = seen.clear()
    @Synchronized fun accept(generation: Long, signal: Map<String, Any?>, nowMs: Long): Boolean {
        seen.entries.removeAll { nowMs - it.value > 120_000 }
        val key = "$generation:${canonical(signal)}"
        if (seen.containsKey(key)) return false
        seen[key] = nowMs
        while (seen.size > 512) seen.remove(seen.keys.first())
        return true
    }
    private fun canonical(value: Any?): String = when (value) {
        is Map<*, *> -> "M" + value.entries.sortedBy { it.key.toString() }.joinToString("") {
            val key = canonical(it.key); val item = canonical(it.value)
            "${key.length}:$key${item.length}:$item"
        }
        is List<*> -> "L" + value.joinToString("") { canonical(it).let { c -> "${c.length}:$c" } }
        is String -> "S${value.length}:$value"
        is Boolean -> "B$value"
        is Number -> "N${value.javaClass.name}:$value"
        null -> "Z"
        else -> "U${value.javaClass.name}:${value}"
    }
}
