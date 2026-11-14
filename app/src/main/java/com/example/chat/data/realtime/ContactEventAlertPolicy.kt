package com.example.chat.data.realtime

import com.example.chat.domain.models.ContactEventAlertOverride

/** Which realtime contact event a popup would announce. */
enum class ContactEventKind { ONLINE, TYPING, RECORDING }

/**
 * Pure resolution for the per-contact realtime event popups (contact online,
 * typing started, recording started) in RichChatRealtimeService.
 *
 * A contact override can pin an event "always" or "never", but never beats a
 * safety guard: popups stay silent for locked/hidden (protected) chats, for
 * muted chats and for the chat currently open on screen, where the header
 * already shows the same state inline. The in-app-notification master switch
 * is enforced by EventToastOverlay at render time, so it is intentionally not
 * re-checked here. Presence the server hides (privacy-filtered directory)
 * never reaches this policy at all, so hidden presence cannot leak a popup.
 */
object ContactEventAlertPolicy {

    fun shouldPopup(
        globalEventEnabled: Boolean,
        contactOverride: ContactEventAlertOverride?,
        kind: ContactEventKind,
        conversationProtected: Boolean,
        conversationMuted: Boolean,
        conversationOpen: Boolean,
    ): Boolean {
        if (conversationProtected || conversationMuted || conversationOpen) return false
        val mode = when (kind) {
            ContactEventKind.ONLINE -> contactOverride?.online
            ContactEventKind.TYPING -> contactOverride?.typing
            ContactEventKind.RECORDING -> contactOverride?.recording
        }
        return when (mode) {
            ContactEventAlertOverride.MODE_ALWAYS -> true
            ContactEventAlertOverride.MODE_NEVER -> false
            else -> globalEventEnabled
        }
    }
}
