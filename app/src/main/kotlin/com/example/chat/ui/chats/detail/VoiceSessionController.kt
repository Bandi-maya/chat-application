package com.example.chat.ui.chats.detail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Exact port of the voice-note drag policy in chat_detail_screen.dart
 *  (_handleVoiceDrag / _handleVoiceRelease):
 *  - dx < -70 cancels (fire-and-forget), dy < -55 locks, release while
 *    unlocked finishes (sends). Offsets are logical px from the long-press
 *    origin. */
object VoiceGesturePolicy {
    const val CANCEL_DX = -70f
    const val LOCK_DY = -55f

    enum class DragOutcome { none, cancel, lock }

    fun onDrag(dx: Float, dy: Float, recording: Boolean, locked: Boolean): DragOutcome {
        if (!recording) return DragOutcome.none
        if (dx < CANCEL_DX) return DragOutcome.cancel
        if (dy < LOCK_DY && !locked) return DragOutcome.lock
        return DragOutcome.none
    }

    /** True when release should finish (send): recording and not locked. */
    fun onReleaseSends(recording: Boolean, locked: Boolean): Boolean = recording && !locked
}

/** UI-facing state mirror of the Dart screen's voice fields
 *  (_recording/_recordLocked/_voiceBusy/_voiceSeconds). */
data class VoiceUiState(
    val recording: Boolean = false,
    val locked: Boolean = false,
    val busy: Boolean = false,
    val seconds: Int = 0,
)

/** Exact port of the screen-level voice orchestration in
 *  chat_detail_screen.dart (_beginVoice/_finishVoice/_cancelVoice +
 *  drag/release handlers), with the realtime indicator, typing indicator,
 *  tracking/scroll and snackbar effects behind seams:
 *  - setRecordingIndicator <- _realtime.setRecording(conversationId, value).
 *    NOTE: the custom REST/WS backend has no recording-indicator transport;
 *    wire a no-op until one exists (same status as typing, reported upstream).
 *  - clearTyping <- fires only when the composer had published typing.
 *  - onSent <- _realtime.trackConversation + _scrollToBottom after a send.
 *  - showError <- SnackBar text; [describeError] maps the failure to the
 *    Dart display string (Dart shows 'Exception: ...' / strips the prefix
 *    depending on the call site; Android maps to the exception message).
 *  Fire-and-forget Dart `unawaited(...)` calls run through [scope]. */
class VoiceSessionController(
    private val voice: VoiceNoteController,
    private val scope: CoroutineScope,
    private val setRecordingIndicator: suspend (Boolean) -> Unit = {},
    private val clearTyping: () -> Unit = {},
    private val onSent: suspend () -> Unit = {},
    private val showError: (String) -> Unit = {},
    private val describeError: (Throwable) -> String = { it.message ?: it.toString() },
    private val onState: (VoiceUiState) -> Unit = {},
) {
    var state = VoiceUiState()
        private set

    private fun update(transform: (VoiceUiState) -> VoiceUiState) {
        state = transform(state)
        onState(state)
    }

    /** Dart _beginVoice(locked). */
    fun beginVoice(locked: Boolean) {
        if (state.busy || state.recording) return
        // Dart clears a published typing indicator before starting.
        clearTyping()
        update { it.copy(busy = true) }
        scope.launch {
            try {
                voice.start()
                setRecordingIndicator(true)
                update { it.copy(recording = true, locked = locked, seconds = 0) }
                startSecondsTicker()
            } catch (error: Throwable) {
                runCatching { setRecordingIndicator(false) }
                showError(describeError(error))
            } finally {
                update { it.copy(busy = false) }
            }
        }
    }

    /** Dart _finishVoice(): stop + upload + send; on success track + scroll. */
    fun finishVoice() {
        if (!state.recording || state.busy) return
        update { it.copy(busy = true) }
        scope.launch {
            try {
                val sent = voice.stopAndSend()
                if (sent) onSent()
            } catch (error: Throwable) {
                showError("Could not send voice note. ${describeError(error)}")
            } finally {
                runCatching { setRecordingIndicator(false) }
                update { VoiceUiState() }
            }
        }
    }

    /** Dart _cancelVoice(). */
    fun cancelVoice() {
        if (!state.recording || state.busy) return
        update { it.copy(busy = true) }
        scope.launch {
            try {
                voice.cancel()
            } catch (error: Throwable) {
                showError("Could not cancel voice note. ${describeError(error)}")
            } finally {
                // Dart: cleanup failure only logs; state still resets.
                runCatching { setRecordingIndicator(false) }
                update { VoiceUiState() }
            }
        }
    }

    /** Dart _handleVoiceDrag. */
    fun onDrag(dx: Float, dy: Float) {
        when (VoiceGesturePolicy.onDrag(dx, dy, state.recording, state.locked)) {
            VoiceGesturePolicy.DragOutcome.cancel -> cancelVoice()
            VoiceGesturePolicy.DragOutcome.lock -> update { it.copy(locked = true) }
            VoiceGesturePolicy.DragOutcome.none -> Unit
        }
    }

    /** Dart _handleVoiceRelease. */
    fun onRelease() {
        if (VoiceGesturePolicy.onReleaseSends(state.recording, state.locked)) finishVoice()
    }

    private fun startSecondsTicker() {
        scope.launch {
            while (state.recording) {
                kotlinx.coroutines.delay(1000L)
                if (state.recording) update { it.copy(seconds = it.seconds + 1) }
            }
        }
    }
}
