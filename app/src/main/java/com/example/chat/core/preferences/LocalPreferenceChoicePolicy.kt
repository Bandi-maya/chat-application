package com.example.chat.core.preferences

/** Local choices override remote defaults without importing another installation's override markers. */
object LocalPreferenceChoicePolicy {
    const val KEY = "local_choice_fields_v1"
    fun captureFields(before: Map<String, Any?>, after: Map<String, Any?>, groups: Set<String>): Map<String, Set<String>> =
        groups.associateWith { group ->
            val old = before[group] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val next = after[group] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            next.keys.filterIsInstance<String>().filter { old[it] != next[it] }.toSet()
        }.filterValues { it.isNotEmpty() }
    fun merge(remote: Map<String, Any?>, local: Map<String, Any?>, choices: Map<String, Set<String>>): Map<String, Any?> {
        val merged = remote.toMutableMap()
        merged.remove(KEY)
        choices.forEach { (group, fields) ->
            val localGroup = local[group]
            if ("*" in fields || localGroup !is Map<*, *>) {
                if (local.containsKey(group)) merged[group] = localGroup
            } else {
                val target = ((remote[group] as? Map<*, *>)?.entries?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }?.toMap() ?: emptyMap()).toMutableMap()
                fields.forEach { field -> if (localGroup.containsKey(field)) target[field] = localGroup[field] }
                merged[group] = target
            }
        }
        return merged
    }
}
