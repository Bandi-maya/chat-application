package com.example.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.chat.ServiceLocator
import com.example.chat.data.services.AndroidPcmVoiceRecorderPort
import com.example.chat.data.services.AndroidVoiceStagingPort
import com.example.chat.domain.models.MessageType
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.data.services.ChatMediaService
import com.example.chat.data.network.TransferDirection
import com.example.chat.data.network.TransferKind
import com.example.chat.data.network.TransferStatus
import com.example.chat.ui.chats.detail.VoiceLevelMeter
import com.example.chat.ui.chats.detail.VoiceLevelMeterBar
import com.example.chat.ui.chats.detail.VoiceLevelSampler
import com.example.chat.ui.chats.detail.VoiceNoteController
import com.example.chat.ui.chats.detail.VoiceRecordConfig
import com.example.chat.ui.chats.detail.VoiceSessionController
import com.example.chat.ui.chats.detail.VoiceUiState
import com.example.chat.ui.kit.ChatyMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Binding of VoiceNoteController/VoiceSessionController (Dart voice_note_service.dart and the
 *  screen's _beginVoice/_finishVoice/_cancelVoice) for ChatDetailHost. Bound: MediaRecorder capture,
 *  RECORD_AUDIO runtime prompt, record/lock/cancel/send, streamed upload via the custom
 *  ApiBackendService REST /chat/upload endpoint and send with the Dart JSON envelope. Capture runs the record_android 2.1.2
 *  default engine port (AndroidPcmVoiceRecorderPort: PCM AudioRecord + AGC/AEC/NS on the record
 *  session + MediaCodec AAC-LC + MediaMuxer .m4a), and the overlay renders the Dart
 *  ChatyVoiceLevelMeter port fed by VoiceLevelSampler at the Dart 90ms poll.
 *  Deviations: the recording status strip is a host overlay (ChatDetailScreen has no recording
 *  composer parameters); recording indicator goes through richRealtime
 *  (Supabase RPC, tolerated on failure like Dart); the first press that triggers the permission
 *  prompt does not start recording, the user presses again after granting. */
class ChatVoiceBindings(
    val onVoiceTap: () -> Unit,
    val onVoiceHoldStart: () -> Unit,
    val onVoiceMove: (Offset) -> Unit,
    val onVoiceHoldEnd: () -> Unit,
    val overlay: @Composable () -> Unit,
)

@Composable
fun rememberChatVoice(conversationId: String, afterSend: () -> Unit, clearTyping: () -> Unit): ChatVoiceBindings {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember(conversationId) { mutableStateOf(VoiceUiState()) }
    val note = remember(conversationId) {
        VoiceNoteController(
            conversationId = conversationId,
            recorder = AndroidPcmVoiceRecorderPort(context.applicationContext),
            staging = AndroidVoiceStagingPort(context.applicationContext),
            uploadAudio = { path, name, seconds ->
                val file = java.io.File(path)
                val transferId = ServiceLocator.transferMonitor.begin(
                    TransferDirection.upload,
                    TransferKind.audio,
                    file.length(),
                    name,
                )
                try {
                    val uploaded = ServiceLocator.apiBackend.uploadMedia(file) { sent, total ->
                        ServiceLocator.transferMonitor.update(transferId, sent, total)
                    }
                    val mediaUrl = uploaded["url"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: uploaded["path"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("Custom media endpoint returned no media URL.")
                    ServiceLocator.transferMonitor.update(transferId, file.length(), file.length())
                    ServiceLocator.transferMonitor.finish(transferId)
                    MessageAttachment(
                        id = java.util.UUID.randomUUID().toString(),
                        type = "audio",
                        name = name,
                        size = ChatMediaService.formatBytes(file.length()),
                        url = mediaUrl,
                        durationSeconds = seconds,
                    )
                } catch (error: Throwable) {
                    ServiceLocator.transferMonitor.finish(transferId, TransferStatus.failed)
                    throw error
                }
            },
            sendAudioMessage = { attachment ->
                ServiceLocator.apiBackend.sendMessage(conversationId, "", MessageType.audio, attachment)
            },
            recordConfig = {
                val uplink = ServiceLocator.networkMonitor.state.value.upstreamKbps
                val voiceBitrate = when {
                    uplink in 1..128 -> 32_000
                    uplink in 129..400 -> 48_000
                    uplink in 401..900 -> 64_000
                    else -> 96_000
                }
                VoiceRecordConfig(bitRate = voiceBitrate)
            },
        )
    }
    // Dart _ComposerState._startLevelPolling: reset on record start, then poll every
    // VoiceLevelMeter.POLL_INTERVAL_MS; bars are real microphone samples only.
    val sampler = remember(conversationId) { VoiceLevelSampler { note.currentLevelDbfs() } }
    var levels by remember(conversationId) { mutableStateOf(List(VoiceLevelMeter.BAR_COUNT) { 0.0 }) }
    LaunchedEffect(ui.recording) {
        if (ui.recording) {
            sampler.reset()
            levels = sampler.levels()
            while (isActive) {
                sampler.pollOnce()
                levels = sampler.levels()
                delay(VoiceLevelMeter.POLL_INTERVAL_MS)
            }
        }
    }
    val session = remember(conversationId) {
        VoiceSessionController(
            voice = note,
            scope = scope,
            setRecordingIndicator = { v -> ServiceLocator.apiBackend.sendRecording(conversationId, v) },
            clearTyping = clearTyping,
            onSent = { afterSend() },
            showError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
            onState = { ui = it },
        )
    }
    DisposableEffect(session) { onDispose { session.cancelVoice() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
    }
    fun begin(locked: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            session.beginVoice(locked)
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    return ChatVoiceBindings(
        onVoiceTap = { if (ui.recording && ui.locked) session.finishVoice() else begin(true) },
        onVoiceHoldStart = { origin = Offset.Zero; begin(false) },
        onVoiceMove = { o -> session.onDrag(o.x, o.y) },
        onVoiceHoldEnd = { session.onRelease() },
        overlay = {
            if (ui.recording) {
                // Previous card UI restored, docked over the composer and lifted
                // clear of the system gesture bar (was a floating pill at a fixed
                // 76.dp offset that overlapped the composer and message list).
                Box(
                    Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    VoiceRecordingCard(
                        state = ui,
                        levels = levels,
                        onDelete = session::cancelVoice,
                        onPause = session::togglePause,
                        onSend = session::finishVoice,
                    )
                }
            }
        },
    )
}


/** Restored recording card (the pre-pill UI): real sampled levels and elapsed
 *  time, delete/pause/send controls when locked. No decorative waveform. */
@Composable
private fun VoiceRecordingCard(
    state: VoiceUiState,
    levels: List<Double>,
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
