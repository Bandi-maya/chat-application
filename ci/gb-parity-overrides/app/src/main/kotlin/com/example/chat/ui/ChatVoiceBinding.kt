package com.example.chat.ui

import android.Manifest
import android.os.Handler
import android.os.Looper
import com.example.chat.data.api.ApiAccountScope
import com.example.chat.data.api.ApiScopeElement
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.graphicsLayer
import com.example.chat.ui.kit.ChatyMotion
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.chat.ServiceLocator
import com.example.chat.data.services.AndroidPcmVoiceRecorderPort
import com.example.chat.data.services.AndroidVoiceStagingPort
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.VoiceLevelMeter
import com.example.chat.ui.chats.detail.VoiceLevelMeterBar
import com.example.chat.ui.chats.detail.VoiceLevelSampler
import com.example.chat.ui.chats.detail.VoiceNoteController
import com.example.chat.ui.chats.detail.VoiceSessionController
import com.example.chat.ui.chats.detail.VoiceUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Binding of VoiceNoteController/VoiceSessionController (Dart voice_note_service.dart and the
 *  screen's _beginVoice/_finishVoice/_cancelVoice) for ChatDetailHost. Bound: MediaRecorder capture,
 *  RECORD_AUDIO runtime prompt, record/lock/cancel/send, upload via ChatMediaService (REST
 *  /chat/upload) and send with the Dart JSON envelope. Capture runs the record_android 2.1.2
 *  default engine port (AndroidPcmVoiceRecorderPort: PCM AudioRecord + AGC/AEC/NS on the record
 *  session + MediaCodec AAC-LC + MediaMuxer .m4a), and the overlay renders the Dart
 *  ChatyVoiceLevelMeter port fed by VoiceLevelSampler at the Dart 90ms poll.
 *  Deviations: the recording card replaces the composer in place (ChatDetailScreen swaps it
 *  into the composer slot while [ChatVoiceBindings.recording]); recording indicator goes through richRealtime
 *  (Supabase RPC, tolerated on failure like Dart); the first press that triggers the permission
 *  prompt does not start recording, the user presses again after granting. */
class ChatVoiceBindings(
    val onVoiceTap: () -> Unit,
    val onVoiceHoldStart: () -> Unit,
    val onVoiceMove: (Offset) -> Unit,
    val onVoiceHoldEnd: () -> Unit,
    // True while a capture session is active; the host swaps the composer for
    // the card in place instead of floating a panel above the message box.
    val recording: Boolean,
    val overlay: @Composable () -> Unit,
)

@Composable
fun rememberChatVoice(conversationId: String, afterSend: () -> Unit, clearTyping: () -> Unit, referenceTwo: Boolean = false): ChatVoiceBindings {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = ServiceLocator.apiBackend
    var voiceEpoch by remember(conversationId) { mutableStateOf(0) }
    val expected = remember(conversationId, voiceEpoch) { AtomicReference<ApiAccountScope?>(null) }
    fun requireScope(): ApiAccountScope {
        val captured = checkNotNull(expected.get()) { "Recording account is missing." }
        check(api.withAuthScope(captured) {}) { "Recording account changed." }
        return captured
    }
    var ui by remember(conversationId, voiceEpoch) { mutableStateOf(VoiceUiState()) }
    val note = remember(conversationId, voiceEpoch) {
        VoiceNoteController(
            conversationId = conversationId,
            recorder = AndroidPcmVoiceRecorderPort(context.applicationContext),
            staging = AndroidVoiceStagingPort(context.applicationContext),
            validateSession = { requireScope(); Unit },
            uploadAudio = { path, name, seconds ->
                val captured = requireScope()
                val bytes = withContext(Dispatchers.IO) { java.io.File(path).readBytes() }
                requireScope()
                withContext(ApiScopeElement(captured)) {
                    ServiceLocator.chatMedia.uploadBytes(conversationId, "audio", bytes, name, "audio/mp4", seconds)
                }
            },
            sendAudioMessage = { attachment ->
                api.sendMessage(conversationId, "", MessageType.audio, attachment, expectedScope = requireScope())
            },
        )
    }
    // Dart _ComposerState._startLevelPolling: reset on record start, then poll every
    // VoiceLevelMeter.POLL_INTERVAL_MS; bars are real microphone samples only.
    val sampler = remember(conversationId, voiceEpoch) { VoiceLevelSampler { note.currentLevelDbfs() } }
    var levels by remember(conversationId, voiceEpoch) { mutableStateOf(List(VoiceLevelMeter.BAR_COUNT) { 0.0 }) }
    LaunchedEffect(ui.recording, ui.paused) {
        if (ui.recording && !ui.paused) {
            if (ui.seconds == 0) sampler.reset()
            levels = sampler.levels()
            while (isActive) {
                sampler.pollOnce()
                levels = sampler.levels()
                delay(VoiceLevelMeter.POLL_INTERVAL_MS)
            }
        }
    }
    val session = remember(conversationId, voiceEpoch) {
        VoiceSessionController(
            voice = note,
            scope = scope,
            setRecordingIndicator = {}, // Custom backend has no recording-indicator endpoint.
            clearTyping = clearTyping,
            onSent = { afterSend() },
            showError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
            onState = { next ->
                ui = next
                if (!next.recording && !next.busy) expected.get()?.let { expected.compareAndSet(it, null) }
            },
        )
    }
    DisposableEffect(session) {
        val initialEpoch = voiceEpoch
        val handler = Handler(Looper.getMainLooper())
        val listener: (ApiAccountScope) -> Unit = { next ->
            val captured = expected.get()
            if (captured != null && next !== captured) {
                note.invalidate() // Atomic only, safe under the auth lock.
                handler.post { if (voiceEpoch == initialEpoch) voiceEpoch++ }
            }
        }
        api.addAuthScopeListener(listener)
        onDispose {
            api.removeAuthScopeListener(listener)
            note.invalidate()
            // Dedicated cleanup survives composition scope cancellation and waits for busy start/stop.
            val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            cleanup.launch { try { note.dispose() } finally { cleanup.cancel() } }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
    }
    fun begin(locked: Boolean) {
        // Consult the owning session, not a potentially stale rendered frame.
        if (session.state.recording || session.state.busy || note.isInvalidated) return
        val captured = api.authSnapshot()
        var acquired = false
        if (!api.withAuthScope(captured) { acquired = expected.compareAndSet(null, captured) } || !acquired) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            session.beginVoice(locked)
        } else {
            // The prompt is not recording authority. No delayed grant callback can start capture.
            expected.compareAndSet(captured, null)
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return ChatVoiceBindings(
        onVoiceTap = { if (!ui.recording) begin(true) },
        onVoiceHoldStart = { begin(false) },
        onVoiceMove = { o -> session.onDrag(o.x, o.y) },
        onVoiceHoldEnd = { session.onRelease() },
        recording = ui.recording,
        overlay = {
            if (ui.recording) {
                VoiceRecordingCard(ui, levels, referenceTwo,
                    onDelete = session::cancelVoice,
                    onPause = session::togglePause,
                    onSend = session::finishVoice)
            }
        },
    )
}

/** Real sampled levels and active elapsed only. No decorative/fabricated waveform. */
@Composable
private fun VoiceRecordingCard(
    state: VoiceUiState,
    levels: List<Double>,
    referenceTwo: Boolean,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    onSend: () -> Unit,
) {
    val theme = ServiceLocator.themeController.globalTheme
    val accent = Color(theme.accentColor)
    val foreground = Color(theme.primaryTextColor)
    val context = LocalContext.current
    val reduced = ChatyMotion.durationMs(context, ChatyMotion.instantMs) == 0
    Column(Modifier.fillMaxWidth().background(Color(theme.surfaceColor), RoundedCornerShape(28.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.locked) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text((if (state.paused) "Paused · " else "Recording · ") + "%d:%02d".format(state.seconds / 60, state.seconds % 60), color = foreground)
                VoiceLevelMeterBar(levels, accent, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete, enabled = !state.busy, modifier = Modifier.size(52.dp).background(Color(theme.cardColor), CircleShape)) {
                    Icon(Icons.Default.Delete, "Delete recording", tint = foreground)
                }
                Button(onClick = onPause, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
                    Icon(if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (state.paused) "Resume recording" else "Pause recording")
                    Text(if (state.paused) "Resume" else "Pause")
                }
                IconButton(onClick = onSend, enabled = !state.busy, modifier = Modifier.size(52.dp).background(accent, CircleShape)) {
                    Icon(Icons.Default.Send, "Send recording", tint = Color(theme.onAccentColor))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("●", color = Color(0xFFE65B64))
                Text("%d:%02d".format(state.seconds / 60, state.seconds % 60), color = foreground)
                if (state.locked) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDelete, enabled = !state.busy) { Text("Delete") }
                    TextButton(onClick = onSend, enabled = !state.busy) { Text("Send") }
                } else {
                    Text("‹ Slide to cancel", color = foreground, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, "Slide up to lock recording", tint = foreground)
                        // The scale follows real microphone samples; reduced motion removes scaling.
                        val scale = if (reduced) 1f else 1.08f + (levels.lastOrNull() ?: 0.0).toFloat() * .14f
                        Box(Modifier.size(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(accent, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Mic, "Recording audio", tint = foreground)
                        }
                    }
                }
            }
        }
    }
}
