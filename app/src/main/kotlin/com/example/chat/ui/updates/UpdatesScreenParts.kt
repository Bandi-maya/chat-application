package com.example.chat.ui.updates

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat.data.services.StatusRecord
import com.example.chat.data.services.StatusService
import com.example.chat.data.services.StatusViewer
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.tasks.TaskAvatar
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port of the Dart ChatyConfirmDialog usage in the updates slice:
 *  title/message/confirmLabel (+ destructive). */
@Composable
internal fun UpdatesConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    colors: ChatyColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) colors.error else colors.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Exact port of _openComposer's modal sheet: drag handle, 'New Update',
 *  expiry caption, 3-line input, Photo/Video/Audio/File actions and the
 *  'Publish Update' text button with per-action busy spinners and the
 *  confirm-before-send gate on every publish path. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdatesComposerSheet(
    statusService: StatusService,
    colors: ChatyColors,
    confirmBeforeSendingStatus: () -> Boolean,
    pickStatusMedia: suspend (mediaType: String) -> PickedStatusMedia?,
    onDismiss: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busyType by remember { mutableStateOf<String?>(null) }
    var pendingConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingConfirmKind by remember { mutableStateOf("") }

    fun publishMedia(type: String) {
        val run = {
            busyType = type
            scope.launch {
                try {
                    val picked = pickStatusMedia(type)
                    if (picked != null) {
                        statusService.publishMediaBytes(
                            bytes = picked.bytes,
                            displayName = picked.displayName,
                            mediaType = type,
                            text = text,
                            mimeType = picked.mimeType,
                            durationSeconds = picked.durationSeconds,
                        )
                        onDismiss()
                    }
                } catch (error: Throwable) {
                    onError(error)
                } finally {
                    busyType = null
                }
            }
        }
        if (confirmBeforeSendingStatus()) {
            pendingConfirmKind = "media"
            pendingConfirm = { run() }
        } else {
            run()
        }
    }

    fun publishText() {
        val run = {
            busyType = "text"
            scope.launch {
                try {
                    statusService.publishText(text)
                    onDismiss()
                } catch (error: Throwable) {
                    onError(error)
                } finally {
                    busyType = null
                }
            }
        }
        if (confirmBeforeSendingStatus()) {
            pendingConfirmKind = "text"
            pendingConfirm = { run() }
        } else {
            run()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(colors.foreground.copy(alpha = 0.2f), CircleShape),
            )
            Spacer(Modifier.height(16.dp))
            Text("New Update", color = colors.foreground, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Updates expire automatically after 24 hours.",
                color = colors.foreground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Write an update or add a caption…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row {
                ComposerAction(Icons.Rounded.Image, "Photo", busyType == "image", colors, Modifier.weight(1f)) {
                    if (busyType == null) publishMedia("image")
                }
                Spacer(Modifier.width(8.dp))
                ComposerAction(Icons.Rounded.Videocam, "Video", busyType == "video", colors, Modifier.weight(1f)) {
                    if (busyType == null) publishMedia("video")
                }
                Spacer(Modifier.width(8.dp))
                ComposerAction(Icons.Rounded.GraphicEq, "Audio", busyType == "audio", colors, Modifier.weight(1f)) {
                    if (busyType == null) publishMedia("audio")
                }
                Spacer(Modifier.width(8.dp))
                ComposerAction(Icons.Rounded.Description, "File", busyType == "document", colors, Modifier.weight(1f)) {
                    if (busyType == null) publishMedia("document")
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (busyType == null) publishText() },
                enabled = busyType == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busyType == "text") {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Publish Update")
                }
            }
        }
    }

    pendingConfirm?.let { confirm ->
        UpdatesConfirmDialog(
            title = if (pendingConfirmKind == "text") "Post text status?" else "Post this status update?",
            message = if (pendingConfirmKind == "text") {
                "This status will be visible to your contacts."
            } else {
                "This status will be shared with your contacts."
            },
            confirmLabel = "Send",
            colors = colors,
            onConfirm = {
                pendingConfirm = null
                confirm()
            },
            onDismiss = { pendingConfirm = null },
        )
    }
}

@Composable
private fun ComposerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    busy: Boolean,
    colors: ChatyColors,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceSecondary)
            .clickable(onClick = onTap)
            .padding(vertical = 10.dp, horizontal = 8.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
    }
}

/** Exact port of _openStatus's fullscreen dialog: header (back, avatar,
 *  title, delete for own), centered content, caption text, views control
 *  for own statuses. The StatusViewSession view recording with rollback
 *  ports _recordStatusView exactly. */
@Composable
internal fun StatusViewerDialog(
    status: StatusRecord,
    statusService: StatusService,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    template: com.example.chat.core.templates.UpdatesTemplate,
    viewSession: StatusViewSession,
    remoteImage: @Composable (url: String, modifier: Modifier) -> Unit,
    openUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var signedUrl by remember(status.id) { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewersOpen by remember { mutableStateOf(false) }

    LaunchedEffect(status.id) {
        if (status.hasMedia) {
            signedUrl = runCatching { statusService.createSignedUrl(status.mediaPath!!) }.getOrNull()
        }
        val viewerId = currentUser.id
        if (viewSession.tryBegin(status, viewerId)) {
            launch {
                try {
                    val published = statusService.markViewed(status)
                    if (!published) viewSession.rollback(status, viewerId)
                } catch (error: Throwable) {
                    viewSession.rollback(status, viewerId)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = colors.foreground)
                }
                Spacer(Modifier.width(8.dp))
                val owner = getUser(status.userId)
                TaskAvatar(owner?.avatarInitials ?: "U", owner?.avatarColorHex ?: "0xFF6366F1", 38, colors)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (status.userId == currentUser.id) "My Status" else owner?.displayName ?: "Contact",
                    color = colors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                if (status.userId == currentUser.id) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete status", tint = colors.error)
                    }
                }
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                StatusContent(status, signedUrl, colors, template, remoteImage, openUrl)
            }
            if (status.text.isNotEmpty()) {
                Text(
                    status.text,
                    textAlign = TextAlign.Center,
                    color = colors.foreground,
                    fontSize = 16.sp,
                    lineHeight = 22.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
                )
            }
            if (status.userId == currentUser.id) {
                ViewsControl(statusService, status, colors) { viewersOpen = true }
            }
        }
    }

    if (confirmDelete) {
        UpdatesConfirmDialog(
            title = "Delete this update?",
            message = "This update will be deleted for everyone and removed from your status.",
            confirmLabel = "Delete",
            destructive = true,
            colors = colors,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    statusService.deleteStatus(status)
                    onDismiss()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (viewersOpen) {
        StatusViewersSheet(status.id, statusService, colors) { viewersOpen = false }
    }
}

@Composable
private fun StatusContent(
    status: StatusRecord,
    signedUrl: String?,
    colors: ChatyColors,
    template: com.example.chat.core.templates.UpdatesTemplate,
    remoteImage: @Composable (url: String, modifier: Modifier) -> Unit,
    openUrl: (String) -> Unit,
) {
    if (status.mediaType == "text") {
        Text(
            status.text,
            textAlign = TextAlign.Center,
            color = colors.foreground,
            fontSize = 26.sp,
            lineHeight = 35.1.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
        return
    }
    if (signedUrl == null) {
        Text(
            "Unable to load this update.",
            color = colors.foreground.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
        return
    }
    if (status.mediaType == "audio" && template.enableStatusAudio) {
        StatusAudioPlayer(url = signedUrl, name = status.mediaName ?: "Audio update", colors = colors)
        return
    }
    if (status.mediaType == "image") {
        // Dart InteractiveViewer + ClipRRect(ChatyRadius.card): pinch/pan
        // zoom over the clipped remote image.
        var scale by remember { mutableStateOf(1f) }
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        remoteImage(
            signedUrl,
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .pointerInput(signedUrl) {
                    androidx.compose.foundation.gestures.detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        )
        return
    }
    val icon = when (status.mediaType) {
        "video" -> Icons.Rounded.PlayCircleFilled
        "audio" -> Icons.Rounded.GraphicEq
        else -> Icons.Rounded.Description
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(84.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            status.mediaName ?: status.mediaType,
            textAlign = TextAlign.Center,
            color = colors.foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { openUrl(signedUrl) }, modifier = Modifier.width(200.dp)) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Securely")
        }
    }
}

/** Port of _buildViewsControl: swipe-up or tap pill showing live viewer
 *  count, opening the viewers sheet. */
@Composable
private fun ViewsControl(
    statusService: StatusService,
    status: StatusRecord,
    colors: ChatyColors,
    onOpen: () -> Unit,
) {
    var count by remember(status.id) { mutableStateOf(0) }
    LaunchedEffect(status.id) {
        count = runCatching { statusService.viewersFor(status.id).size }.getOrDefault(0)
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceElevated.copy(alpha = 0.85f))
                .border(0.8.dp, colors.borderSubtle, RoundedCornerShape(24.dp))
                .pointerInput(status.id) {
                    // Dart onVerticalDragEnd: primaryVelocity < -150 opens
                    // the sheet. Compose reports no fling velocity here, so
                    // accumulate the drag and open on a clear upward swipe.
                    var total = 0f
                    androidx.compose.foundation.gestures.detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -120f) onOpen() },
                    ) { _, dragAmount -> total += dragAmount }
                }
                .clickable(onClick = onOpen)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.RemoveRedEye, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "$count ${if (count == 1) "view" else "views"}",
                    color = colors.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Port of _StatusViewersSheet: 'Viewed by' sheet with loading, error +
 *  retry, empty state and the viewer list (avatar, name, @username,
 *  relative time with 'ago'). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusViewersSheet(
    statusId: String,
    statusService: StatusService,
    colors: ChatyColors,
    onDismiss: () -> Unit,
) {
    var viewers by remember(statusId) { mutableStateOf<List<StatusViewer>?>(null) }
    var failed by remember(statusId) { mutableStateOf(false) }

    fun load() {
        failed = false
        viewers = null
    }
    LaunchedEffect(statusId, viewers, failed) {
        if (viewers == null && !failed) {
            viewers = runCatching { statusService.viewersFor(statusId) }
                .getOrElse {
                    failed = true
                    null
                }
        }
    }

    fun sheetRelativeTime(value: Instant): String {
        val diff = Duration.between(value, Instant.now())
        if (diff.toMinutes() < 1) return "just now"
        if (diff.toMinutes() < 60) return "${diff.toMinutes()}m ago"
        if (diff.toHours() < 24) return "${diff.toHours()}h ago"
        return "${diff.toDays()}d ago"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 480.dp)) {
            Text(
                "Viewed by",
                color = colors.foreground,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            val list = viewers
            when {
                failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    Icon(Icons.Rounded.RemoveRedEye, contentDescription = null, tint = colors.foregroundTertiary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Unable to load views", color = colors.foreground, fontWeight = FontWeight.Bold)
                    Text(
                        "Something went wrong while fetching viewers. Please try again.",
                        color = colors.foregroundSecondary,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = ::load) { Text("Retry") }
                }
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
                list.isEmpty() -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    Icon(Icons.Rounded.RemoveRedEye, contentDescription = null, tint = colors.foregroundTertiary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No views yet", color = colors.foreground, fontWeight = FontWeight.Bold)
                    Text(
                        "Views will appear here after someone sees your update.",
                        color = colors.foregroundSecondary,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(Modifier.padding(bottom = 16.dp)) {
                    items(list.size) { index ->
                        val viewer = list[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            TaskAvatar(viewer.avatarInitials, viewer.avatarColorHex, 40, colors)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    viewer.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.foreground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                )
                                Text(
                                    "@${viewer.username}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.foregroundSecondary,
                                    fontSize = 12.5.sp,
                                )
                            }
                            Text(
                                sheetRelativeTime(viewer.viewedAt),
                                color = colors.foregroundTertiary,
                                fontSize = 12.sp,
                            )
                        }
                        if (index < list.size - 1) {
                            Box(
                                Modifier
                                    .padding(start = 66.dp)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.divider),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Exact port of _StatusAudioPlayer over Android MediaPlayer: loading and
 *  failed states with the exact copy, slider seek, mm:ss labels, play/pause
 *  stadium button, seek-to-start replay at the end. */
@Composable
internal fun StatusAudioPlayer(url: String, name: String, colors: ChatyColors) {
    val player = remember(url) { MediaPlayer() }
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    var positionMs by remember(url) { mutableLongStateOf(0L) }
    var durationMs by remember(url) { mutableLongStateOf(0L) }

    fun loadSource() {
        loading = true
        failed = false
        positionMs = 0L
        durationMs = 0L
        try {
            player.reset()
            player.setDataSource(url)
            player.setOnPreparedListener {
                durationMs = it.duration.toLong()
                loading = false
            }
            player.setOnCompletionListener {
                playing = false
                positionMs = durationMs
            }
            player.setOnErrorListener { _, _, _ ->
                loading = false
                failed = true
                true
            }
            player.prepareAsync()
        } catch (_: Throwable) {
            loading = false
            failed = true
        }
    }

    LaunchedEffect(url) { loadSource() }
    LaunchedEffect(playing) {
        while (playing) {
            positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
            delay(200)
        }
    }
    DisposableEffect(url) {
        onDispose { runCatching { player.release() } }
    }

    fun togglePlayback() {
        if (loading || failed) return
        try {
            if (playing) {
                player.pause()
                playing = false
            } else {
                if (durationMs > 0 && positionMs >= durationMs - 250) {
                    player.seekTo(0)
                    positionMs = 0L
                }
                player.start()
                playing = true
            }
        } catch (_: Throwable) {
            failed = true
        }
    }

    fun timeLabel(ms: Long): String {
        val totalSeconds = ms / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    Box(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                name,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(10.dp))
            if (failed) {
                Text(
                    "Audio could not be played in Chaty.",
                    textAlign = TextAlign.Center,
                    color = colors.error,
                    fontSize = 12.sp,
                )
                TextButton(onClick = ::loadSource) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retry")
                }
            } else {
                Slider(
                    value = positionMs.toFloat().coerceIn(0f, (if (durationMs > 0) durationMs else 1).toFloat()),
                    onValueChange = { value ->
                        if (!loading && durationMs > 0) {
                            player.seekTo(value.toInt())
                            positionMs = value.toLong()
                        }
                    },
                    valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(timeLabel(positionMs), color = colors.foregroundSecondary, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(timeLabel(durationMs), color = colors.foregroundSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = ::togglePlayback, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (playing) "Pause audio" else "Play audio")
                    }
                }
            }
        }
    }
}
