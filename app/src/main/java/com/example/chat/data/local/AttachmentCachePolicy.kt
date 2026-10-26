package com.example.chat.data.local

import java.security.MessageDigest

/** Pure rules for a local attachment cache (no Android deps, JVM-testable). Not wired yet. */
object AttachmentCachePolicy {
    /** View-once (and any restricted) attachments are never written to a persistent cache. */
    fun mayPersist(viewOnce: Boolean, restricted: Boolean): Boolean = !viewOnce && !restricted

    /** Tenant directory name (hashed). */
    fun tenantDir(tenantId: String): String = "t_" + sha256Hex(tenantId).take(24)

    /** User directory name under a tenant (hashed, tenant independent so deleteAccount can find it in every tenant). */
    fun userDir(userId: String): String = "u_" + sha256Hex(userId).take(24)

    /**
     * Fail-closed parse of the message metadata "view_once" value. Absent/null => not restricted. true / "true" =>
     * restricted. false / "false" => not restricted. ANYTHING else (numbers, maps, lists, other strings, blanks)
     * is malformed and treated as restricted. A future server enum needs a server contract before being mapped.
     */
    fun viewOnceRestricted(metadata: Map<String, Any?>): Boolean {
        if (!metadata.containsKey("view_once")) return false
        return when (val v = metadata["view_once"]) {
            null -> false
            is Boolean -> v
            is String -> when (v.trim().lowercase()) { "true" -> true; "false" -> false; else -> true }
            else -> true
        }
    }

    /** Relative account path "t_<hash>/u_<hash>": ids never appear in paths. */
    fun accountPath(tenantId: String, userId: String): String = tenantDir(tenantId) + "/" + userDir(userId)

    /** Entry name from conversation + message + attachment id (hashed, never a path, no URL: URLs rotate). */
    fun entryName(ref: AttachmentRef): String =
        "a_" + sha256Hex(ref.conversationId + "\u0000" + ref.messageId + "\u0000" + ref.attachmentId).take(40) + ".bin"

    /** Oldest-first eviction names so the total fits [maxBytes]. entries: name -> (size, lastAccessMs). */
    fun evictionOrder(entries: Map<String, Pair<Long, Long>>, maxBytes: Long): List<String> {
        var total = entries.values.sumOf { it.first }
        val out = ArrayList<String>()
        for ((name, v) in entries.entries.sortedBy { it.value.second }) {
            if (total <= maxBytes) break
            out += name
            total -= v.first
        }
        return out
    }

    private fun sha256Hex(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
