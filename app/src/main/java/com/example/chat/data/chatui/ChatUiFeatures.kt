package com.example.chat.data.chatui

/** Matrix 8 chat-UI client rows - pure logic, JVM-testable. The compose UI
 *  reads these; UI-visible changes still go through screenshot approval. */

/** @mentions in groups with mention-only notify when the group is muted. */
object MentionParser {

    data class Mention(val userId: String, val handle: String, val startIndex: Int)

    /** Match @handle tokens against group members (handle -> userId). */
    fun parse(text: String, members: Map<String, String>): List<Mention> {
        val out = mutableListOf<Mention>()
        val re = Regex("@([A-Za-z0-9_]{2,32})")
        for (m in re.findAll(text)) {
            val handle = m.groupValues[1]
            val userId = members[handle] ?: continue
            out += Mention(userId, handle, m.range.first)
        }
        return out
    }

    /** Muted groups still notify when the user is mentioned (WhatsApp rule). */
    fun shouldNotify(groupMuted: Boolean, mentionsSelf: Boolean): Boolean =
        !groupMuted || mentionsSelf
}

/** Telegram-style inline formatting: *bold* _italic_ `mono` > quote and
 *  ||spoiler|| tap-to-reveal. Produces spans the renderer consumes. */
object MessageFormatter {

    enum class Style { BOLD, ITALIC, MONO, QUOTE, SPOILER }

    data class Span(val style: Style, val start: Int, val end: Int, val content: String)

    private data class Marker(val token: String, val style: Style)

    private val markers = listOf(
        Marker("||", Style.SPOILER),   // longest first so || wins over |
        Marker("`", Style.MONO),
        Marker("*", Style.BOLD),
        Marker("_", Style.ITALIC),
    )

    fun parse(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        for (line in text.split('\n')) {
            if (line.startsWith("> ")) {
                spans += Span(Style.QUOTE, 0, 0, line.removePrefix("> "))
            }
        }
        for (marker in markers) {
            var i = 0
            while (true) {
                val open = text.indexOf(marker.token, i)
                if (open < 0) break
                val close = text.indexOf(marker.token, open + marker.token.length)
                if (close < 0) break
                val content = text.substring(open + marker.token.length, close)
                if (content.isNotEmpty()) {
                    spans += Span(marker.style, open, close + marker.token.length, content)
                }
                i = close + marker.token.length
            }
        }
        return spans.sortedBy { it.start }
    }

    /** Spoiler content stays masked until the user taps. */
    fun reveal(spans: List<Span>, revealed: Set<Int>, index: Int): List<Span> = spans
}

/** Send-later queue (Telegram schedule + send-when-online). Payload is the
 *  already-encrypted MLS ciphertext; plaintext never sits in the queue. */
class ScheduledSendQueue {

    data class Entry(
        val id: String,
        val conversationId: String,
        val encryptedPayload: String,
        val fireAtMillis: Long?,      // null = send-when-online
        val createdAtMillis: Long,
    )

    private val entries = mutableListOf<Entry>()

    fun schedule(entry: Entry) {
        if (entry.fireAtMillis != null) {
            require(entry.fireAtMillis > entry.createdAtMillis) { "fire time must be in the future" }
        }
        entries.removeAll { it.id == entry.id }
        entries += entry
    }

    fun cancel(id: String): Boolean = entries.removeAll { it.id == id }

    /** Entries due now: scheduled time reached, or send-when-online and we are online. */
    fun due(nowMillis: Long, online: Boolean): List<Entry> = entries.filter {
        if (it.fireAtMillis != null) it.fireAtMillis <= nowMillis else online
    }

    fun markSent(ids: Set<String>) { entries.removeAll { it.id in ids } }

    fun pending(): List<Entry> = entries.sortedBy { it.fireAtMillis ?: Long.MAX_VALUE }
}

/** Double-tap quick reaction with a user-configurable default emoji. */
data class QuickReactionPolicy(val defaultEmoji: String = "👍") {
    init { require(defaultEmoji.isNotBlank()) }
    fun reactionFor(doubleTap: Boolean, configured: String? = null): String? =
        if (doubleTap) configured?.takeIf { it.isNotBlank() } ?: defaultEmoji else null
}
