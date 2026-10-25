package com.example.chat.ui.island

import androidx.compose.ui.graphics.Color
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cancelable timer handle; port of Dart Timer for the island controller. */
fun interface IslandTimerHandle {
    fun cancel()
}

/** Scheduler seam for the Dart Timer calls; coroutine-based by default,
 *  fake-driven in JVM tests. */
fun interface IslandTimerScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): IslandTimerHandle

    companion object {
        fun coroutine(scope: CoroutineScope): IslandTimerScheduler =
            IslandTimerScheduler { delayMs, action ->
                val job: Job = scope.launch {
                    delay(delayMs)
                    action()
                }
                IslandTimerHandle { job.cancel() }
            }
    }
}

/** Bridge seam for the Dart MethodChannel('chaty/dynamic_island').
 *
 *  The native side (integration owner) implements this; the controller owns
 *  only the exact channel semantics. All config calls follow the Dart
 *  catch-and-default behavior. */
interface IslandNativeBridge {
    /** Incoming channel method 'onNativeNotificationPosted'. */
    fun setNotificationEventHandler(handler: (IslandNativeEvent) -> Unit)
    /** Outgoing channel method 'clearAll' (fire-and-forget, errors swallowed). */
    fun clearAll()
    suspend fun setHideSystemNotifications(hide: Boolean): Boolean
    suspend fun isHideSystemNotificationsEnabled(): Boolean
    suspend fun setHeaderEnabled(enabled: Boolean): Boolean
    suspend fun isHeaderEnabled(): Boolean
}

/** Typed form of the inbound MethodChannel calls. */
sealed interface IslandNativeEvent {
    data class NotificationPosted(
        val key: String?,
        val isCall: Boolean,
        val title: String?,
        val body: String?,
    ) : IslandNativeEvent
    data class NotificationRemoved(val key: String?) : IslandNativeEvent
    data object ClearAll : IslandNativeEvent
}

/** Exact port of lib/features/island/controllers/dynamic_island_controller.dart.
 *
 *  Controller orchestrating the 4-state Dynamic Island state machine,
 *  priority queue, auto-collapse timers, and bidirectional native sync.
 *  ChangeNotifier is ported as the same listener pattern used by
 *  ChatyPreferencesController.
 */
class DynamicIslandController(
    private val nativeBridge: IslandNativeBridge? = null,
    private val timerScheduler: IslandTimerScheduler,
    private val scope: CoroutineScope,
    private val now: () -> Instant = { Instant.now() },
) {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    private var _mode = IslandMode.idle
    val mode: IslandMode get() = _mode

    // Active stack of payloads sorted by priority, then timestamp descending
    private val _queue = mutableListOf<DynamicIslandPayload>()
    val queue: List<DynamicIslandPayload> get() = _queue.toList()

    private var _carouselIndex = 0
    val carouselIndex: Int get() = _carouselIndex

    val activePayload: DynamicIslandPayload?
        get() {
            if (_queue.isEmpty()) return null
            if (_carouselIndex < _queue.size) {
                return _queue[_carouselIndex]
            }
            return _queue.first()
        }

    val secondaryPayload: DynamicIslandPayload?
        get() = if (_queue.size > 1) _queue[1] else null

    // Sub-view toggle when expanded (matching reference repo: 'player' vs 'notifications')
    private var _activeSubView = "primary"
    val activeSubView: String get() = _activeSubView

    private var collapseTimer: IslandTimerHandle? = null
    private var dismissTimer: IslandTimerHandle? = null
    private var _isPinned = false
    val isPinned: Boolean get() = _isPinned

    // OriginOS / iQOO Drag & Go drop zone state
    private var _isDragTargetActive = false
    val isDragTargetActive: Boolean get() = _isDragTargetActive

    companion object {
        const val EXPANDED_AUTO_COLLAPSE_MS: Long = 4500
        const val COMPACT_IDLE_TIMEOUT_MS: Long = 7000
    }

    private var nativeEventCounter = 0L

    init {
        nativeBridge?.setNotificationEventHandler { event ->
            when (event) {
                is IslandNativeEvent.NotificationPosted -> {
                    postPayload(
                        DynamicIslandPayload(
                            id = event.key ?: "native_${now().toEpochMilli()}_${nativeEventCounter++}",
                            type = if (event.isCall) {
                                IslandPayloadType.incomingCall
                            } else {
                                IslandPayloadType.chatMessage
                            },
                            title = event.title ?: "Notification",
                            message = event.body ?: "",
                            timestamp = now(),
                            iconEmoji = "💬",
                        ),
                    )
                }
                is IslandNativeEvent.NotificationRemoved -> {
                    val key = event.key
                    if (key != null) {
                        removePayload(key)
                    }
                }
                IslandNativeEvent.ClearAll -> clearAll()
            }
        }
    }

    /** Posts or updates a payload in the priority queue. */
    fun postPayload(payload: DynamicIslandPayload, autoExpand: Boolean = true) {
        val existingIndex = _queue.indexOfFirst { it.id == payload.id }
        if (existingIndex >= 0) {
            _queue[existingIndex] = payload
        } else {
            _queue.add(payload)
        }

        sortQueue()

        // Determine state transition
        if (_queue.size >= 2 && _queue[0].isOngoingActivity) {
            _mode = IslandMode.split
        } else if (autoExpand) {
            _mode = IslandMode.expanded
            scheduleAutoCollapse()
        } else if (_mode == IslandMode.idle) {
            _mode = IslandMode.compact
            scheduleDismissTimer()
        }

        notifyListeners()
    }

    /** Removes a payload by its ID. */
    fun removePayload(id: String) {
        _queue.removeAll { it.id == id }
        reconcileStateAfterRemoval()
        notifyListeners()
    }

    /** Removes all payloads matching a predicate. */
    fun removePayloadsWhere(test: (DynamicIslandPayload) -> Boolean) {
        _queue.removeAll(test)
        reconcileStateAfterRemoval()
        notifyListeners()
    }

    /** Clears all payloads and transitions to idle. */
    fun clearAll() {
        _queue.clear()
        _mode = IslandMode.idle
        cancelTimers()
        notifyListeners()
        try {
            nativeBridge?.clearAll()
        } catch (_: Exception) {
        }
    }

    /** Transitions to expanded state. */
    fun expand(subView: String? = null) {
        if (_queue.isEmpty()) return
        _mode = IslandMode.expanded
        _activeSubView = subView
            ?: if (activePayload?.type == IslandPayloadType.media ||
                activePayload?.type == IslandPayloadType.voiceNotePlayback
            ) {
                "player"
            } else {
                "primary"
            }
        cancelTimers()
        if (!_isPinned) {
            scheduleAutoCollapse()
        }
        notifyListeners()
    }

    /** Collapses to compact or split mode. */
    fun collapse() {
        if (_queue.isEmpty()) {
            _mode = IslandMode.idle
        } else if (_queue.size >= 2 && _queue[0].isOngoingActivity) {
            _mode = IslandMode.split
        } else {
            _mode = IslandMode.compact
        }
        cancelTimers()
        scheduleDismissTimer()
        notifyListeners()
    }

    /** Directly set mode. */
    fun setMode(newMode: IslandMode) {
        if (_mode == newMode) return
        _mode = newMode
        if (newMode == IslandMode.expanded) {
            scheduleAutoCollapse()
        } else if (newMode == IslandMode.compact) {
            scheduleDismissTimer()
        }
        notifyListeners()
    }

    /** Toggles between expanded and collapsed. */
    fun toggleExpanded() {
        if (_mode == IslandMode.expanded) {
            collapse()
        } else {
            expand()
        }
    }

    /** Switches active expanded sub-view ('player' or 'notifications'). */
    fun setSubView(subView: String) {
        _activeSubView = subView
        scheduleAutoCollapse()
        notifyListeners()
    }

    /** Pins the island open (e.g. while user is typing a reply). */
    fun pinOpen(pinned: Boolean) {
        _isPinned = pinned
        if (pinned) {
            cancelTimers()
        } else {
            scheduleAutoCollapse()
        }
        notifyListeners()
    }

    /** Dismisses the active primary payload and promotes the next in queue. */
    fun dismissActive() {
        if (_queue.isNotEmpty()) {
            val indexToRemove = if (_carouselIndex < _queue.size) _carouselIndex else 0
            val removed = _queue.removeAt(indexToRemove)
            removed.onDismiss?.invoke()
            if (_carouselIndex >= _queue.size && _queue.isNotEmpty()) {
                _carouselIndex = _queue.size - 1
            } else if (_queue.isEmpty()) {
                _carouselIndex = 0
            }
            reconcileStateAfterRemoval()
            notifyListeners()
        }
    }

    /** Advances to the next concurrent task in the Multi-Task Carousel (OriginOS feature). */
    fun nextCarouselItem() {
        if (_queue.size <= 1) return
        _carouselIndex = (_carouselIndex + 1) % _queue.size
        scheduleAutoCollapse()
        notifyListeners()
    }

    /** Goes back to the previous concurrent task in the Multi-Task Carousel. */
    fun prevCarouselItem() {
        if (_queue.size <= 1) return
        _carouselIndex = (_carouselIndex - 1 + _queue.size) % _queue.size
        scheduleAutoCollapse()
        notifyListeners()
    }

    /** Sets the active carousel index directly. */
    fun setCarouselIndex(index: Int) {
        if (index >= 0 && index < _queue.size) {
            _carouselIndex = index
            scheduleAutoCollapse()
            notifyListeners()
        }
    }

    /** Toggles the Drag & Go target dock active state (OriginOS Atomic Island feature). */
    fun setDragTargetActive(active: Boolean) {
        if (_isDragTargetActive == active) return
        _isDragTargetActive = active
        notifyListeners()
    }

    /** Handles content dropped into the Island's Drag & Go target zone. */
    fun handleDroppedData(data: Any?) {
        _isDragTargetActive = false
        postPayload(
            DynamicIslandPayload(
                id = "drag_drop_${now().toEpochMilli()}",
                type = IslandPayloadType.systemAlert,
                title = "Content Attached",
                message = "Ready to share in active conversation",
                timestamp = now(),
                accentColor = Color(0xFF00F0FF),
                isBandiIcon = true,
            ),
            autoExpand = true,
        )
    }

    /** Posts a Smart Clipboard Intent chip (OriginOS Atomic Island feature). */
    fun postClipboardIntent(
        title: String,
        message: String,
        actionLabel: String,
        onAction: () -> Unit,
    ) {
        postPayload(
            DynamicIslandPayload(
                id = "clipboard_${now().toEpochMilli()}",
                type = IslandPayloadType.clipboardIntent,
                title = title,
                message = message,
                clipboardContent = message,
                clipboardActionLabel = actionLabel,
                onClipboardAction = onAction,
                timestamp = now(),
                accentColor = Color(0xFFF59E0B),
                isBandiIcon = true,
            ),
            autoExpand = false,
        )
    }

    private fun sortQueue() {
        _queue.sortWith { a, b ->
            val pComp = a.priority.compareTo(b.priority)
            if (pComp != 0) pComp else b.timestamp.compareTo(a.timestamp)
        }
    }

    private fun reconcileStateAfterRemoval() {
        cancelTimers()
        if (_queue.isEmpty()) {
            _mode = IslandMode.idle
        } else if (_queue.size >= 2 && _queue[0].isOngoingActivity) {
            _mode = IslandMode.split
        } else if (_queue.first().isOngoingActivity) {
            _mode = IslandMode.compact
        } else {
            _mode = IslandMode.compact
            scheduleDismissTimer()
        }
    }

    private fun scheduleAutoCollapse() {
        collapseTimer?.cancel()
        if (activePayload?.isOngoingActivity == true || _isPinned) {
            return // Ongoing activities don't collapse abruptly
        }
        collapseTimer = timerScheduler.schedule(EXPANDED_AUTO_COLLAPSE_MS) {
            if (_mode == IslandMode.expanded) {
                collapse()
            }
        }
    }

    private fun scheduleDismissTimer() {
        dismissTimer?.cancel()
        if (activePayload?.isOngoingActivity == true || _isPinned) {
            return // Ongoing activities stay in compact
        }
        dismissTimer = timerScheduler.schedule(COMPACT_IDLE_TIMEOUT_MS) {
            if (_queue.isNotEmpty() && !_queue.first().isOngoingActivity) {
                _queue.clear()
                _mode = IslandMode.idle
                notifyListeners()
            }
        }
    }

    private fun cancelTimers() {
        collapseTimer?.cancel()
        dismissTimer?.cancel()
    }

    // ── Native channel configuration (Dart MethodChannel invoke calls) ──────

    /** Configures whether system shade notifications are hidden when Dynamic Island is active. */
    suspend fun setHideSystemNotifications(hide: Boolean): Boolean {
        return try {
            nativeBridge?.setHideSystemNotifications(hide) ?: hide
        } catch (_: Exception) {
            hide
        }
    }

    /** Checks whether system shade notifications are currently suppressed. */
    suspend fun isHideSystemNotificationsEnabled(): Boolean {
        return try {
            nativeBridge?.isHideSystemNotificationsEnabled() ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** Configures whether the segmented tab switcher header is displayed in the island. */
    suspend fun setHeaderEnabled(enabled: Boolean): Boolean {
        return try {
            nativeBridge?.setHeaderEnabled(enabled) ?: enabled
        } catch (_: Exception) {
            enabled
        }
    }

    /** Checks whether the segmented tab switcher header is enabled. */
    suspend fun isHeaderEnabled(): Boolean {
        return try {
            nativeBridge?.isHeaderEnabled() ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun dispose() {
        cancelTimers()
    }
}
