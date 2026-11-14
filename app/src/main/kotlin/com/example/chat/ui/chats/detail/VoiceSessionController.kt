package com.example.chat.ui.chats.detail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

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
    val paused: Boolean = false,
    /** Finished recording waiting in the review step; recording is false while set. */
    val review: VoiceNoteController.Clip? = null,
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
    private var ticker: Job? = null
    private var sendAfterReview = false
    private var starting = false
    private var releasedDuringStart = false
    private var canceledDuringStart = false
    private var lockedDuringStart = false
    var state = VoiceUiState()
        private set

    private fun update(transform: (VoiceUiState) -> VoiceUiState) {
        state = transform(state)
        if (!state.recording) { ticker?.cancel(); ticker = null }
        onState(state)
    }

    /** Dart _beginVoice(locked). */
    fun beginVoice(locked: Boolean) {
        if (state.busy || state.recording || state.review != null) return
        // Dart clears a published typing indicator before starting.
        starting = true
        releasedDuringStart = false
        canceledDuringStart = false
        lockedDuringStart = locked
        clearTyping()
        update { it.copy(busy = true) }
        scope.launch {
            try {
                voice.start()
                if (canceledDuringStart) {
                    voice.cancel()
                    update { VoiceUiState() }
                } else {
                    setRecordingIndicator(true)
                    update { it.copy(recording = true, locked = lockedDuringStart, seconds = 0) }
                    startSecondsTicker()
                }
            } catch (error: Throwable) {
                runCatching { setRecordingIndicator(false) }
                showError(describeError(error))
            } finally {
                starting = false
                update { it.copy(busy = false) }
                if (releasedDuringStart && !canceledDuringStart && !state.locked) finishVoice()
            }
        }
    }

    /** Dart _finishVoice(): stop + upload + send; on success track + scroll. */
    fun finishVoice() {
        if (!state.recording || state.busy) return
        // After a Pause and Resume there are several parts: join them in review, then send from there.
        if (voice.hasSegments) { sendAfterReview = true; beginReview(); return }
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

    fun togglePause() {
        if (!state.recording || !state.locked || state.busy) return
        update { it.copy(busy = true) }
        scope.launch {
            try {
                voice.togglePause()
                update { it.copy(paused = voice.isPaused, seconds = (voice.elapsedMs / 1000L).toInt()) }
            } catch (error: Throwable) {
                showError("Could not change recording pause. ${describeError(error)}")
                // A failed native transition cannot leave a seemingly usable recording.
                runCatching { voice.cancel() }
                update { VoiceUiState() }
            } finally { update { it.copy(busy = false) } }
        }
    }


    /** Paused locked recording -> stop and open the review step. */
    fun beginReview() {
        if (!state.recording || !state.locked || state.busy) return
        update { it.copy(busy = true) }
        scope.launch {
            try {
                val clip = voice.pauseForReview()
                runCatching { setRecordingIndicator(false) }
                if (clip == null) {
                    showError("Recording is too short.")
                    update { VoiceUiState() }
                } else {
                    update { VoiceUiState(review = clip) }
                    if (clip.joinFailed) showError("Could not join the recording parts. Tap Retry.")
                }
            } catch (error: Throwable) {
                showError("Could not open the recording. ${describeError(error)}")
                runCatching { setRecordingIndicator(false) }
                update { VoiceUiState() }
            } finally {
                update { it.copy(busy = false) }
                val auto = sendAfterReview; sendAfterReview = false
                if (auto && state.review?.joinFailed == false) sendReview()
            }
        }
    }

    fun sendReview() {
        val clip = state.review ?: return
        if (state.busy || clip.joinFailed) return
        update { it.copy(busy = true) }
        scope.launch {
            try {
                if (voice.sendClip(clip)) { update { VoiceUiState() }; onSent() }
            } catch (error: Throwable) {
                // The clip stays in review so Send can be tried again.
                showError("Could not send voice note. ${describeError(error)}")
                if (voice.isInvalidated) deleteReview()
            } finally { update { it.copy(busy = false) } }
        }
    }

    /** Leaving the screen: stop any recording and drop a pending review file. */
    fun dispose() { cancelVoice(); deleteReview() }

    fun deleteReview() {
        val had = state.review != null
        scope.launch { runCatching { voice.discardReview() } }
        if (had) update { VoiceUiState() }
    }

    /** Try joining the parts again; all parts stay on disk until sent or deleted. */
    fun retryJoin() {
        val clip = state.review ?: return
        if (state.busy || !clip.joinFailed) return
        update { it.copy(busy = true) }
        scope.launch {
            try {
                val next = voice.retryJoin(clip)
                update { it.copy(review = next) }
                if (next.joinFailed) showError("Still could not join the parts. Try again, or Resume and Send later.")
            } catch (error: Throwable) {
                showError("Could not join the parts. ${describeError(error)}")
            } finally { update { it.copy(busy = false) } }
        }
    }

    /** Review -> back to recording: a new segment starts and the recording sheet returns unchanged. */
    fun resumeRecording() {
        if (state.review == null || state.busy) return
        update { it.copy(busy = true) }
        scope.launch {
            try {
                voice.resumeSegment()
                if (!voice.isRecording) throw IllegalStateException("Recording could not resume.")
                setRecordingIndicator(true)
                update { VoiceUiState(recording = true, locked = true, seconds = (voice.elapsedMs / 1000L).toInt()) }
                startSecondsTicker()
            } catch (error: Throwable) {
                // Failure keeps the review so nothing is lost; the user can still send or delete.
                showError("Could not resume recording. ${describeError(error)}")
            } finally { update { it.copy(busy = false) } }
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
        if (starting) {
            if (dx < VoiceGesturePolicy.CANCEL_DX) canceledDuringStart = true
            else if (dy < VoiceGesturePolicy.LOCK_DY) lockedDuringStart = true
            return
        }
        when (VoiceGesturePolicy.onDrag(dx, dy, state.recording, state.locked)) {
            VoiceGesturePolicy.DragOutcome.cancel -> cancelVoice()
            VoiceGesturePolicy.DragOutcome.lock -> update { it.copy(locked = true) }
            VoiceGesturePolicy.DragOutcome.none -> Unit
        }
    }

    /** Dart _handleVoiceRelease. */
    fun onRelease() {
        if (starting) { releasedDuringStart = true; return }
        if (VoiceGesturePolicy.onReleaseSends(state.recording, state.locked)) finishVoice()
    }

    private fun startSecondsTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (state.recording) {
                kotlinx.coroutines.delay(100L)
                if (state.recording) update { it.copy(seconds = (voice.elapsedMs / 1000L).toInt()) }
            }
        }
    }
}
