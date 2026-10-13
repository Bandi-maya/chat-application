package com.example.chat.island

import android.os.Handler
import android.os.Looper

/**
 * Thread-safe single source of truth for all active island activities.
 * Enforces priority resolution, strict stack deduplication, and max 20 items cap.
 */
class IslandStore(private val onStateChanged: (IslandSnapshot) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val messages = mutableListOf<MessageActivity>()
    private var activeCall: CallActivity? = null
    private var activeMedia: MediaActivity? = null
    private var activeCharging: ChargingActivity? = null
    private var activeChatyLive: ChatyLiveActivity? = null

    private var currentMode: IslandMode = IslandMode.HIDDEN
    private var activeReplyKey: String? = null

    data class IslandSnapshot(
        val mode: IslandMode,
        val primaryActivity: IslandActivity?,
        val secondaryActivity: IslandActivity?,
        val messages: List<MessageActivity>,
        val activeCall: CallActivity?,
        val activeMedia: MediaActivity?,
        val activeCharging: ChargingActivity?,
        val replyTarget: MessageActivity?,
    )

    private fun dispatch() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dispatch() }
            return
        }

        val primary = resolvePrimaryActivity()
        val secondary = resolveSecondaryActivity(primary)

        val replyTarget = activeReplyKey?.let { key ->
            messages.firstOrNull { it.key == key }
        }

        val snapshot = IslandSnapshot(
            mode = currentMode,
            primaryActivity = primary,
            secondaryActivity = secondary,
            messages = messages.toList(),
            activeCall = activeCall,
            activeMedia = activeMedia,
            activeCharging = activeCharging,
            replyTarget = replyTarget,
        )
        onStateChanged(snapshot)
    }

    fun upsertMessage(message: MessageActivity) {
        val index = messages.indexOfFirst { it.key == message.key }
        if (index >= 0) {
            val old = messages[index]
            val contentChanged = old.title != message.title || old.body != message.body
            if (contentChanged) {
                messages.removeAt(index)
                messages.add(0, message)
            } else {
                messages[index] = message
            }
        } else {
            messages.add(0, message)
        }

        while (messages.size > 20) {
            messages.removeAt(messages.lastIndex)
        }

        if (currentMode != IslandMode.REPLY && currentMode != IslandMode.EXPANDED_CALL) {
            currentMode = IslandMode.COMPACT
        }
        dispatch()
    }

    fun removeMessage(key: String) {
        messages.removeAll { it.key == key }
        if (activeReplyKey == key) {
            activeReplyKey = null
            if (currentMode == IslandMode.REPLY) {
                currentMode = IslandMode.COMPACT
            }
        }
        if (messages.isEmpty() && activeCall == null && activeMedia == null && activeCharging == null) {
            currentMode = IslandMode.HIDDEN
        }
        dispatch()
    }

    fun clearAllMessages() {
        messages.clear()
        activeReplyKey = null
        if (activeCall == null && activeMedia == null && activeCharging == null) {
            currentMode = IslandMode.HIDDEN
        } else {
            currentMode = IslandMode.COMPACT
        }
        dispatch()
    }

    fun setCall(call: CallActivity?) {
        activeCall = call
        if (call != null) {
            currentMode = IslandMode.COMPACT
        } else if (messages.isEmpty() && activeMedia == null && activeCharging == null) {
            currentMode = IslandMode.HIDDEN
        }
        dispatch()
    }

    fun setMedia(media: MediaActivity?) {
        activeMedia = media
        if (media != null && currentMode == IslandMode.HIDDEN) {
            currentMode = IslandMode.COMPACT
        } else if (media == null && messages.isEmpty() && activeCall == null && activeCharging == null) {
            currentMode = IslandMode.HIDDEN
        }
        dispatch()
    }

    fun setCharging(charging: ChargingActivity?) {
        activeCharging = charging
        if (charging != null) {
            currentMode = IslandMode.COMPACT
        } else if (messages.isEmpty() && activeCall == null && activeMedia == null) {
            currentMode = IslandMode.HIDDEN
        }
        dispatch()
    }

    fun setMode(mode: IslandMode) {
        if (currentMode != mode) {
            currentMode = mode
            if (mode != IslandMode.REPLY) {
                activeReplyKey = null
            }
            dispatch()
        }
    }

    fun startReply(key: String) {
        val target = messages.firstOrNull { it.key == key } ?: return
        if (target.replyAction != null) {
            activeReplyKey = key
            currentMode = IslandMode.REPLY
            dispatch()
        }
    }

    fun reset() {
        messages.clear()
        activeCall = null
        activeMedia = null
        activeCharging = null
        activeChatyLive = null
        activeReplyKey = null
        currentMode = IslandMode.HIDDEN
        dispatch()
    }

    private fun resolvePrimaryActivity(): IslandActivity? {
        val candidates = mutableListOf<IslandActivity>()
        activeCall?.let { candidates.add(it) }
        activeChatyLive?.let { candidates.add(it) }
        messages.firstOrNull()?.let { candidates.add(it) }
        activeMedia?.let { candidates.add(it) }
        activeCharging?.let { candidates.add(it) }

        return candidates.minByOrNull { it.priority }
    }

    private fun resolveSecondaryActivity(primary: IslandActivity?): IslandActivity? {
        if (primary == null) return null
        val candidates = mutableListOf<IslandActivity>()
        if (activeCall != null && activeCall != primary) candidates.add(activeCall!!)
        if (activeChatyLive != null && activeChatyLive != primary) candidates.add(activeChatyLive!!)
        val topMsg = messages.firstOrNull()
        if (topMsg != null && topMsg != primary) candidates.add(topMsg)
        if (activeMedia != null && activeMedia != primary) candidates.add(activeMedia!!)
        if (activeCharging != null && activeCharging != primary) candidates.add(activeCharging!!)

        return candidates.minByOrNull { it.priority }
    }
}
