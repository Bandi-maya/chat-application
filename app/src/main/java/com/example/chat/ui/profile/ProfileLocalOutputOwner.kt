package com.example.chat.ui.profile

/** Durable index of local outputs, never active external writers. Register BEFORE file creation.
 * Ownership transfer between local picker and draft does not erase this index. */
internal class ProfileLocalOutputOwner(
    private val persist: (Set<String>) -> Unit,
    private val delete: (String) -> Boolean,
    restored: Set<String> = emptySet(),
) {
    private val paths = restored.toMutableSet()
    @Synchronized fun record(path: String) {
        if (path in paths) return
        val next = paths + path
        persist(next)
        paths.add(path)
    }
    @Synchronized fun remove(path: String) {
        if (path !in paths) return
        check(delete(path)) { "Profile photo cleanup blocked. Retry cleanup before another photo." }
        val next = paths - path
        // Keep the tombstone if persistence fails even after the file was deleted.
        persist(next)
        paths.remove(path)
    }
    /** Only called once on a NEW process, before any local producer is registered. */
    @Synchronized fun restoreCleanup() {
        var firstFailure: Exception? = null
        paths.toList().forEach { path ->
            try { remove(path) } catch (error: Exception) { if (firstFailure == null) firstFailure = error }
        }
        firstFailure?.let { throw it }
    }
    @Synchronized fun contains(path: String) = path in paths
    @Synchronized fun snapshot(): Set<String> = paths.toSet()
}
