package com.example.chat.ui.profile

import java.io.Closeable
import java.io.File

/** Local picker files only. Retire blocks new work; existing readers/writers release before delete.
 * A transferred crop belongs to the draft, not this owner. No timers or account identifiers. */
internal class ProfilePickFiles(
    private val record: (File) -> Unit = {},
    private val delete: (File) -> Unit = { it.delete(); Unit },
) : Closeable {
    private val owned = linkedMapOf<File, Int>()
    private var retired = false
    @Synchronized fun isActive() = !retired
    @Synchronized fun track(file: File) {
        check(!retired) { "Profile picker was closed." }
        record(file)
        owned.putIfAbsent(file, 0)
    }
    @Synchronized fun tryBegin(file: File): Boolean {
        if (retired || !owned.containsKey(file)) return false
        owned[file] = owned.getValue(file) + 1
        return true
    }
    @Synchronized fun begin(file: File) {
        check(!retired && owned.containsKey(file)) { "Profile picker was closed." }
        owned[file] = owned.getValue(file) + 1
    }
    @Synchronized fun end(file: File) {
        val count = owned[file] ?: return
        check(count > 0)
        owned[file] = count - 1
        if (retired && count == 1) { delete(file); owned.remove(file) }
    }
    @Synchronized fun remove(file: File) {
        check((owned[file] ?: 0) == 0) { "Profile file still in use." }
        delete(file)
        owned.remove(file)
    }
    @Synchronized fun transfer(file: File): Boolean {
        if (retired || owned[file] != 0) return false
        owned.remove(file)
        return true
    }
    override fun close() = synchronized(this) {
        retired = true
        owned.filterValues { it == 0 }.keys.toList().forEach { delete(it); owned.remove(it) }
        Unit
    }
}
