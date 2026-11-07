package com.example.chat.data.notifications

/** Matrix 13 notification preferences - pure decision logic, JVM-testable.
 *  NotificationManager glue reads these; no UI without screenshot approval. */

enum class NotificationStyle { FULL, NAME_ONLY, NO_CONTENT }

/** Per-chat overrides + quiet hours + badge rules. */
class NotificationPreferences(
    var defaultStyle: NotificationStyle = NotificationStyle.FULL,
) {
    data class ChatOverride(
        val style: NotificationStyle? = null,
        val toneUri: String? = null,
        val vibratePattern: LongArray? = null,
        val muted: Boolean = false,
    ) {
        override fun equals(other: Any?): Boolean = other is ChatOverride &&
            style == other.style && toneUri == other.toneUri &&
            (vibratePattern?.contentEquals(other.vibratePattern ?: longArrayOf()) != false) &&
            muted == other.muted
        override fun hashCode(): Int = style.hashCode()
    }

    data class QuietHours(val startMinuteOfDay: Int, val endMinuteOfDay: Int) {
        /** Handles overnight windows (e.g. 22:00-07:00). */
        fun contains(minuteOfDay: Int): Boolean =
            if (startMinuteOfDay <= endMinuteOfDay) {
                minuteOfDay in startMinuteOfDay until endMinuteOfDay
            } else {
                minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay
            }
    }

    private val overrides = mutableMapOf<String, ChatOverride>()
    var quietHours: QuietHours? = null

    fun setOverride(conversationId: String, override: ChatOverride) {
        overrides[conversationId] = override
    }

    fun overrideFor(conversationId: String): ChatOverride? = overrides[conversationId]

    fun styleFor(conversationId: String): NotificationStyle =
        overrides[conversationId]?.style ?: defaultStyle

    /** Calls break through quiet hours and mutes; messages do not. */
    fun shouldAlert(
        conversationId: String,
        isCall: Boolean,
        mentionsSelf: Boolean,
        minuteOfDay: Int?,
    ): Boolean {
        if (isCall) return true                       // call breakthrough
        if (overrides[conversationId]?.muted == true && !mentionsSelf) return false
        if (minuteOfDay != null && quietHours?.contains(minuteOfDay) == true) return false
        return true
    }

    /** Badge counts exclude muted conversations (matrix 13 row 9). */
    fun badgeCount(unreadByConversation: Map<String, Int>): Int =
        unreadByConversation.filterKeys { overrides[it]?.muted != true }.values.sum()
}
