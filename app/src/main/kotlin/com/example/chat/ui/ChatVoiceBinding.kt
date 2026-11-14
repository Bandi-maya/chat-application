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
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
    /** Invalidates any in-progress or staged recording (e.g. when encryption
     *  is turned on mid-recording): the session fails its next validation and
     *  is discarded, never uploaded. */
    val cancelActiveRecording: () -> Unit = {},
    val overlay: @Composable () -> Unit,
)

@Composable
fun rememberChatVoice(conversationId: String, afterSend: () -> Unit, clearTyping: () -> Unit, secureChannel: () -> Boolean = { false }): ChatVoiceBindings {
    val secureChannelNow by rememberUpdatedState(secureChannel)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember(conversationId) { mutableStateOf(VoiceUiState()) }
    // GB/stock parity: view-once toggle for voice notes, chosen in the locked
    // recording UI before send; lives outside VoiceUiState because the session
    // overwrites that state on every tick.
    var voiceViewOnce by remember(conversationId) { mutableStateOf(false) }
    // Scope captured when a recording starts; upload and send are refused if the account changed.
    val recordScope = remember(conversationId) { java.util.concurrent.atomic.AtomicReference<com.example.chat.data.api.ApiAccountScope?>(null) }
    val note = remember(conversationId) {
        VoiceNoteController(
            conversationId = conversationId,
            joinSegments = { parts, out -> withContext(Dispatchers.IO) { com.example.chat.ui.chats.detail.AndroidAacJoin.join(parts, out) } },
            validateSession = {
                // Turning encryption on mid-recording invalidates the session:
                // the note is discarded, never uploaded plaintext.
                check(!secureChannelNow()) { "Encryption was turned on while recording; the voice note was discarded." }
                val started = recordScope.get()
                if (started != null) check(ServiceLocator.apiBackend.authSnapshot().generation == started.generation) {
                    "Account changed while recording."
                }
            },
            recorder = AndroidPcmVoiceRecorderPort(context.applicationContext),
            staging = AndroidVoiceStagingPort(context.applicationContext),
            uploadAudio = { path, name, seconds ->
                // Guard BEFORE any byte leaves the device: a secure chat never
                // uploads plaintext audio to REST /chat/upload. The AUTHORITATIVE
                // check is inside tracker.begin: it re-reads the durable flag
                // (persisted-first repo state, not this possibly-stale UI lambda)
                // after its dispatch-lock suspension, so a toggle that completed
                // just before now still refuses the upload, and a toggle that
                // comes after is blocked while the upload is registered.
                // The expected identity is the scope captured when RECORDING
                // started - not a fresh snapshot: an account switch between
                // requireActive() and here must not upload the old recording
                // under the new account. Checked before AND after begin's
                // suspension (generation is the scope-identity discriminator,
                // same as validateSession and the transition listener).
                val scopeNow = recordScope.get()
                    ?: throw IllegalStateException("No recording session; the voice note was not uploaded.")
                check(ServiceLocator.apiBackend.authSnapshot().generation == scopeNow.generation) {
                    "Account changed after recording; the voice note was discarded."
                }
                val endUpload = com.example.chat.data.messaging.AttachmentUploadTracker.begin(
                    com.example.chat.data.messaging.ConversationDispatchLocks.accountKey(
                        scopeNow.userId, scopeNow.tenantId, scopeNow.baseUrl),
                    conversationId,
                )
                // Everything after begin() stays inside the counter cleanup:
                // a throw from file setup, the transfer ledger or the scope
                // revalidation still unregisters the upload.
                try {
                    check(ServiceLocator.apiBackend.authSnapshot().generation == scopeNow.generation) {
                        "Account changed while the upload was starting; nothing was uploaded."
                    }
                    val file = java.io.File(path)
                    val transferId = ServiceLocator.transferMonitor.begin(
                        TransferDirection.upload,
                        TransferKind.audio,
                        file.length(),
                        name,
                    )
                    try {
                        val uploaded = ServiceLocator.apiBackend.uploadMediaForScope(scopeNow, file) { sent, total ->
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
                } finally {
                    endUpload()
                }
            },
            sendAudioMessage = { attachment ->
                // A secure chat never sends unencrypted voice notes; encrypted
                // media is Phase 2 and not wired yet. (The recording itself is
                // blocked earlier, at the hold-to-record entry point.)
                if (secureChannelNow()) throw IllegalStateException("Voice notes are not sent in an encrypted chat yet; encrypted media is not wired.")
                // Bind the send to the recording's account: sendMessage's own
                // default would capture a FRESH scope after requireActive(),
                // which is exactly the switch window this guard exists for.
                val started = recordScope.get()
                    ?: throw IllegalStateException("No recording session; the voice note was not sent.")
                check(ServiceLocator.apiBackend.authSnapshot().generation == started.generation) {
                    "Account changed after recording; the voice note was discarded."
                }
                ServiceLocator.apiBackend.sendMessage(
                    conversationId,
                    "",
                    MessageType.audio,
                    attachment,
                    extraMetadata = if (voiceViewOnce) mapOf("view_once" to true) else null,
                    expectedScope = started,
                )
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
            onSent = {
                voiceViewOnce = false
                afterSend()
            },
            showError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
            onState = { ui = it },
        )
    }
    DisposableEffect(session) { onDispose { session.dispose() } }
    // Synchronous account-transition guard: the voice controller stops accepting upload/send.
    DisposableEffect(note) {
        val l: (com.example.chat.data.api.ApiAccountScope) -> Unit = { next ->
            val started = recordScope.get()
            if (started != null && next.generation != started.generation) note.invalidate()
        }
        ServiceLocator.apiBackend.addAuthScopeListener(l)
        onDispose { ServiceLocator.apiBackend.removeAuthScopeListener(l) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
    }
    fun begin(locked: Boolean) {
        if (secureChannelNow()) {
            Toast.makeText(context, "Voice notes are not sent in an encrypted chat yet; encrypted media is not wired.", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            note.rearm()
            recordScope.set(ServiceLocator.apiBackend.authSnapshot())
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
        cancelActiveRecording = { note.invalidate() },
        overlay = {
            if (ui.review != null) {
                Box(
                    Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    VoiceReviewCard(
                        clip = ui.review!!,
                        busy = ui.busy,
                        viewOnce = voiceViewOnce,
                        onToggleViewOnce = { voiceViewOnce = !voiceViewOnce },
                        onDelete = { voiceViewOnce = false; session.deleteReview() },
                        onSend = session::sendReview,
                        onResume = session::resumeRecording,
                        onRetryJoin = session::retryJoin,
                    )
                }
            } else if (ui.recording) {
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
                        onDelete = {
                            voiceViewOnce = false
                            session.cancelVoice()
                        },
                        // Pause pill opens the review step: stop, keep the file, Listen / Send.
                        onPause = session::beginReview,
                        onSend = session::finishVoice,
                        viewOnce = voiceViewOnce,
                        onToggleViewOnce = { voiceViewOnce = !voiceViewOnce },
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
    viewOnce: Boolean = false,
    onToggleViewOnce: () -> Unit = {},
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
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.delete, semanticLabel = "Delete recording", color = foreground)
                }
                Button(onClick = onPause, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
                    Icon(if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (state.paused) "Resume recording" else "Pause recording")
                    Text(if (state.paused) "Resume" else "Pause")
                }
                // GB/stock view-once toggle for the voice note about to be sent.
                IconButton(
                    onClick = onToggleViewOnce,
                    enabled = !state.busy,
                    modifier = Modifier.size(52.dp).background(
                        if (viewOnce) accent else Color(theme.cardColor),
                        CircleShape,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.LooksOne,
                        if (viewOnce) "View once on. The voice message plays once." else "Send as view once voice message",
                        tint = if (viewOnce) Color(theme.onAccentColor) else foreground,
                    )
                }
                IconButton(onClick = onSend, enabled = !state.busy, modifier = Modifier.size(52.dp).background(accent, CircleShape)) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.send, semanticLabel = "Send recording", color = Color(theme.onAccentColor))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("●", color = Color(0xFFE65B64))
                Text("%d:%02d".format(state.seconds / 60, state.seconds % 60), color = foreground)
                Text("‹ Slide to cancel", color = foreground, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock, semanticLabel = "Slide up to lock recording", color = foreground)
                    // The scale follows real microphone samples; reduced motion removes scaling.
                    val scale = if (reduced) 1f else 1.08f + (levels.lastOrNull() ?: 0.0).toFloat() * .14f
                    Box(Modifier.size(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(accent, CircleShape), contentAlignment = Alignment.Center) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.microphone, semanticLabel = "Recording audio", color = foreground)
                    }
                }
            }
        }
    }
}


/** After-recording review: same sheet and Delete / pill / Send row as the recording card.
 *  Chaty icons and theme colors only. Reduced motion removes the scrub and press animation. */
@Composable
private fun VoiceReviewCard(
    clip: VoiceNoteController.Clip,
    busy: Boolean,
    viewOnce: Boolean,
    onToggleViewOnce: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    onResume: () -> Unit,
    onRetryJoin: () -> Unit,
) {
    val theme = ServiceLocator.themeController.globalTheme
    val accent = Color(theme.accentColor)
    val foreground = Color(theme.primaryTextColor)
    val onAccent = Color(theme.onAccentColor)
    val context = LocalContext.current
    val reduced = ChatyMotion.durationMs(context, ChatyMotion.instantMs) == 0
    val fadeMs = if (reduced) 0 else 150
    val enter = remember(clip.path) { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(clip.path) { enter.animateTo(1f, androidx.compose.animation.core.tween(fadeMs)) }
    val player = remember(clip.path) { android.media.MediaPlayer() }
    var ready by remember(clip.path) { mutableStateOf(false) }
    var playing by remember(clip.path) { mutableStateOf(false) }
    var positionMs by remember(clip.path) { mutableStateOf(0) }
    var totalMs by remember(clip.path) { mutableStateOf(clip.durationSeconds * 1000) }
    var speed by remember(clip.path) { mutableStateOf(1f) }
    var error by remember(clip.path) { mutableStateOf<String?>(null) }
    var peaks by remember(clip.path) { mutableStateOf<List<Float>?>(null) }
    LaunchedEffect(clip.path) {
        peaks = runCatching { com.example.chat.ui.chats.detail.decodeVoiceWaveform(java.io.File(clip.path)) }.getOrNull()
    }
    // Audio focus: take it for playback, give it back on pause, end and dispose.
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val focusRequest = remember {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change == android.media.AudioManager.AUDIOFOCUS_LOSS ||
                    change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    runCatching { if (player.isPlaying) player.pause() }; playing = false
                }
            }.build()
    }
    DisposableEffect(clip.path) {
        onDispose {
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
            runCatching { player.release() }
        }
    }
    LaunchedEffect(playing) {
        while (playing) { positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs); delay(100L) }
    }
    fun togglePlay() {
        error = null
        try {
            if (!ready) {
                player.setDataSource(clip.path); player.prepare(); ready = true
                totalMs = player.duration.coerceAtLeast(1)
                player.setOnCompletionListener {
                    playing = false; positionMs = 0
                    runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
                }
            }
            if (player.isPlaying) {
                player.pause(); playing = false
                audioManager.abandonAudioFocusRequest(focusRequest)
            } else {
                if (audioManager.requestAudioFocus(focusRequest) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    error = "Audio is in use by another app."; return
                }
                if (positionMs >= totalMs - 50) { player.seekTo(0); positionMs = 0 }
                runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
                player.start(); playing = true
            }
        } catch (e: Exception) {
            playing = false; error = e.message ?: "Audio unavailable"
        }
    }
    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        if (playing) runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
    }
    fun seekTo(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * totalMs).toInt()
        positionMs = target
        if (ready) runCatching { player.seekTo(target) }
    }
    fun fmt(ms: Int) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
    val bars = peaks ?: List(VoiceLevelMeter.BAR_COUNT * 2) { 0.35f }
    val progress = if (totalMs > 0) positionMs.toFloat() / totalMs else 0f
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = enter.value }.background(Color(theme.surfaceColor), RoundedCornerShape(28.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(fmt(positionMs), color = foreground)
            // Scrub waveform: tap or drag to seek. Played part uses the foreground color.
            androidx.compose.foundation.Canvas(
                Modifier.weight(1f).heightIn(min = 48.dp).fillMaxWidth()
                    .pointerInput(clip.path) {
                        detectTapGestures { o -> seekTo(o.x / size.width) }
                    }
                    .pointerInput(clip.path) {
                        detectHorizontalDragGestures { change, _ -> seekTo(change.position.x / size.width) }
                    },
            ) {
                val n = bars.size
                val step = size.width / n
                val barW = (step * 0.55f).coerceAtLeast(2f)
                for (i in 0 until n) {
                    val h = (bars[i].coerceIn(0.08f, 1f)) * size.height
                    val x = i * step + step / 2
                    drawLine(
                        color = if (i.toFloat() / n <= progress) foreground else foreground.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2),
                        end = androidx.compose.ui.geometry.Offset(x, (size.height + h) / 2),
                        strokeWidth = barW,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
                drawCircle(foreground, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(progress * size.width, size.height / 2))
            }
            Text(fmt(totalMs), color = foreground.copy(alpha = 0.6f))
            TextButton(onClick = ::cycleSpeed, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (speed == 1f) "1x" else "${speed}x", color = foreground)
            }
        }
        error?.let { Text(it, color = foreground, fontSize = 12.sp) }
        if (clip.joinFailed) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Parts could not be joined. Send is off until they are.", color = foreground, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetryJoin, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.refresh, semanticLabel = "Retry joining recording parts", color = foreground)
                Text("Retry", color = foreground)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDelete, enabled = !busy, modifier = Modifier.size(52.dp).background(Color(theme.cardColor), CircleShape)) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.delete, semanticLabel = "Delete recording", color = foreground)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = ::togglePlay, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
                    androidx.compose.animation.Crossfade(targetState = playing, animationSpec = androidx.compose.animation.core.tween(fadeMs), label = "voice-play-pause") { isPlaying ->
                        com.example.chat.ui.icons.ChatyIcon(
                            if (isPlaying) com.example.chat.ui.icons.ChatyIconId.pause else com.example.chat.ui.icons.ChatyIconId.playArrow,
                            semanticLabel = if (isPlaying) "Pause playback" else "Play recording", color = onAccent,
                        )
                    }
                    Text(if (playing) "Pause" else "Listen")
                }
                Button(onClick = {
                    runCatching { if (player.isPlaying) player.pause() }; playing = false
                    runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
                    onResume()
                }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.microphone, semanticLabel = "Resume recording", color = onAccent)
                    Text("Resume")
                }
            }
            IconButton(
                onClick = onToggleViewOnce, enabled = !busy,
                modifier = Modifier.size(52.dp).background(if (viewOnce) accent else Color(theme.cardColor), CircleShape),
            ) {
                com.example.chat.ui.icons.ChatyIcon(
                    com.example.chat.ui.icons.ChatyIconId.numberOne,
                    semanticLabel = if (viewOnce) "View once on. The voice message plays once." else "Send as view once voice message",
                    color = if (viewOnce) onAccent else foreground,
                )
            }
            IconButton(onClick = onSend, enabled = !busy && !clip.joinFailed, modifier = Modifier.size(52.dp).background(accent.copy(alpha = if (clip.joinFailed) 0.4f else 1f), CircleShape)) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.send, semanticLabel = "Send recording", color = onAccent)
            }
        }
    }
}
