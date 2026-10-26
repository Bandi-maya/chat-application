package com.example.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
    val rt = ServiceLocator.richRealtime
    val note = remember(conversationId) {
        VoiceNoteController(
            conversationId = conversationId,
            recorder = AndroidPcmVoiceRecorderPort(context.applicationContext),
            staging = AndroidVoiceStagingPort(context.applicationContext),
            uploadAudio = { path, name, seconds ->
                val bytes = withContext(Dispatchers.IO) { java.io.File(path).readBytes() }
                ServiceLocator.chatMedia.uploadBytes(conversationId, "audio", bytes, name, "audio/mp4", seconds)
            },
            sendAudioMessage = { attachment ->
                ServiceLocator.apiBackend.sendMessage(conversationId, "", MessageType.audio, attachment)
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
            setRecordingIndicator = { v -> try { rt.setRecording(conversationId, v) } catch (_: Exception) {} },
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
                Box(Modifier.fillMaxSize().padding(bottom = 76.dp), contentAlignment = Alignment.BottomCenter) {
                    Row(
                        Modifier.background(Color(0xE6202020), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("● %d:%02d".format(ui.seconds / 60, ui.seconds % 60), color = Color.White)
                        VoiceLevelMeterBar(
                            levels = levels,
                            accent = Color(ServiceLocator.themeController.globalTheme.accentColor),
                            modifier = Modifier.width(140.dp),
                        )
                        if (ui.locked) {
                            TextButton(onClick = { session.cancelVoice() }, enabled = !ui.busy) { Text("Cancel", color = Color(0xFFFF8A80)) }
                            TextButton(onClick = { session.finishVoice() }, enabled = !ui.busy) { Text("Send", color = Color.White) }
                        } else {
                            Text("Slide left to cancel, up to lock", color = Color(0xB3FFFFFF))
                        }
                    }
                }
            }
        },
    )
}
