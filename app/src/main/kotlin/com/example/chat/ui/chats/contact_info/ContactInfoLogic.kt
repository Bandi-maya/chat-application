package com.example.chat.ui.chats.contact_info

import com.example.chat.domain.models.ChatMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Pure helpers ported from contact_info_screen.dart (api branch anchor
 *  3bee780): presence label, shared media/document split, link extraction. */
object ContactInfoLogic {

    /** _presenceLabel: recording -> typing -> online -> last-seen strings,
     *  with the Dart 'Last seen hidden' fallback when no timestamp exists. */
    fun presenceLabel(
        isRecording: Boolean,
        isTyping: Boolean,
        isOnline: Boolean,
        lastSeen: Instant?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (isRecording) return "recording voice message…"
        if (isTyping) return "typing…"
        if (isOnline) return "Online"
        if (lastSeen == null) return "Last seen hidden"
        val local = lastSeen.atZone(zone)
        val hh = local.hour.toString().padStart(2, '0')
        val mm = local.minute.toString().padStart(2, '0')
        val today = now.atZone(zone).toLocalDate()
        return if (local.toLocalDate() == today) {
            "Last seen today at $hh:$mm"
        } else {
            "Last seen ${local.dayOfMonth}/${local.monthValue}/${local.year} at $hh:$mm"
        }
    }

    /** An index must not bypass deletion, view-once or no-copy rules in the message surface. */
    fun indexable(message: ChatMessage): Boolean = !message.isDeletedForEveryone && !message.isDeletedForMe &&
        !message.isRevokedBySender && !com.example.chat.data.local.AttachmentCachePolicy.viewOnceRestricted(message.metadata) &&
        message.metadata["no_save"] != true && message.metadata["protected"] != true &&
        message.metadata["restricted"] != true

    /** _media getter: attachment type image or video. */
    fun sharedMedia(messages: List<ChatMessage>): List<ChatMessage> =
        messages.filter { indexable(it) && (it.attachment?.type == "image" || it.attachment?.type == "video") }

    /** _documents getter: attachment type document. */
    fun sharedDocuments(messages: List<ChatMessage>): List<ChatMessage> =
        messages.filter { indexable(it) && it.attachment?.type == "document" }

    private val LINK_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    /** _links getter: every http(s) URL across message texts, deduped with
     *  Dart LinkedHashSet (insertion-order) semantics. */
    fun extractLinks(messages: List<ChatMessage>): List<String> {
        val links = LinkedHashSet<String>()
        for (message in messages.filter(::indexable)) {
            for (match in LINK_REGEX.findAll(message.text)) {
                if (match.value.isNotEmpty()) links.add(match.value)
            }
        }
        return links.toList()
    }
}
