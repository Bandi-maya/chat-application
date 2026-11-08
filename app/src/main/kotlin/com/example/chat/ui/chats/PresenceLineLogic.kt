package com.example.chat.ui.chats

import com.example.chat.ui.chats.home.HomeFormatters
import java.time.Instant
import java.time.ZoneId

/** Chat-header presence line (research 5.2): the server decides what a
 *  viewer may see - online, an exact last-seen instant, or a Telegram-style
 *  approximation bucket when the target hides presence. Fail-closed: an
 *  unknown bucket or no data yields no line at all. Typing already replaces
 *  this text in the header, so it is not handled here. */
object PresenceLineLogic {

    /** WhatsApp/Telegram-style wording for the server's approximation
     *  buckets (report 5.2). Unknown buckets hide the line. */
    fun approxBucketText(bucket: String?): String? = when (bucket) {
        "recently" -> "last seen recently"
        "within_week" -> "last seen within a week"
        "within_month" -> "last seen within a month"
        "long_ago" -> "last seen a long time ago"
        else -> null
    }

    /** Precedence mirrors the server's: an online target is simply "online";
     *  an approximation beats a stale exact instant (the server only sends
     *  one of them, but live events and the one-shot fetch can race). */
    fun headerText(
        online: Boolean,
        lastSeen: Instant?,
        approxBucket: String?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        if (online) return "online"
        approxBucketText(approxBucket)?.let { return it }
        if (lastSeen != null) {
            return HomeFormatters.formatLastSeen(lastSeen, now, zone).ifEmpty { null }
        }
        return null
    }
}
