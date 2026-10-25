package com.example.chat.ui.automation

import com.example.chat.data.services.GbFeatureBackendService
import com.example.chat.data.services.ServerScheduledMessage
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Exact port of lib/features/automation/scheduled_message_service.dart.
 *
 *  Service managing scheduled message queues and background dispatch.
 *  ChangeNotifier is ported as the same listener pattern used by
 *  ChatyPreferencesController.
 */
class ScheduledMessageService(
    private val backend: GbFeatureBackendService,
    private val scope: CoroutineScope,
) {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    private var _messages: List<ServerScheduledMessage> = emptyList()
    private var _isLoading = false
    private var _lastError: String? = null

    val messages: List<ServerScheduledMessage> get() = _messages
    val isLoading: Boolean get() = _isLoading
    val lastError: String? get() = _lastError

    /** Refreshes the list of scheduled messages from the backend. */
    suspend fun refresh(): List<ServerScheduledMessage> {
        _isLoading = true
        _lastError = null
        notifyListeners()

        try {
            val results = backend.getScheduledMessages()
            _messages = results
            return _messages
        } catch (e: Exception) {
            _lastError = e.toString()
            // Dart: debugPrint('ScheduledMessageService refresh note: $e')
            return _messages
        } finally {
            _isLoading = false
            notifyListeners()
        }
    }

    /** Schedules a message for future delivery at [scheduledAt]. */
    suspend fun scheduleMessage(
        conversationId: String,
        body: String,
        scheduledAt: ZonedDateTime,
    ): String {
        val id = backend.scheduleMessage(
            conversationId = conversationId,
            body = body,
            scheduledAt = scheduledAt.toInstant(),
        )
        scope.launch { refresh() }
        return id
    }

    /** Cancels a pending scheduled message by [id]. */
    suspend fun cancelScheduledMessage(id: String) {
        backend.cancelScheduledMessage(id)
        _messages = _messages.filterNot { it.id == id }
        notifyListeners()
        scope.launch { refresh() }
    }
}
