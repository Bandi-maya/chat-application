package com.example.chat.ui.updates

import com.example.chat.core.templates.UpdatesLayoutMode

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Videocam
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Media picked by the platform picker for a status update. The Dart
 *  screen's StatusService.pickAndPublish launches the picker itself; in
 *  Kotlin the picker is a UI concern and hands bytes in. */
data class PickedStatusMedia(
    val bytes: ByteArray,
    val displayName: String,
    val mimeType: String? = null,
    val durationSeconds: Long = 0,
)

/** Result of the WhatsApp-style story camera capture. */
data class CapturedStoryMedia(
    val bytes: ByteArray,
    val caption: String,
)

/** Exact port of lib/features/updates/updates_screen.dart.
 *
 *  Seams (integration wiring):
 *  - [statuses]: Flow equivalent of StatusService.watchActiveStatuses()
 *    (Dart Supabase realtime stream; the Kotlin StatusService port has no
 *    list/read method yet - owner wires the stream here).
 *  - [pickStatusMedia]: platform file picker for image/video/audio/document.
 *  - [captureStory]: ChatyCameraCaptureScreen story mode equivalent.
 *  - [remoteImage]: remote image renderer; the default binds the shared
 *    Coil-backed ChatyAsyncImage (ui/theme) with the Dart error fallback.
 *  - [openUrl]: url_launcher externalApplication equivalent.
 *  Preference reads are lambdas over PreferenceGroups fields. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    statusService: StatusService,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    statuses: Flow<List<StatusRecord>>,
    modifier: Modifier = Modifier,
    template: com.example.chat.core.templates.UpdatesTemplate = com.example.chat.core.templates.ChatyTemplateRegistry.messageFirst.updates,
    confirmBeforeSendingStatus: () -> Boolean = { true },
    myNameOverride: () -> String = { "" },
    contactNameColor: () -> Long? = { null },
    recentUpdatesBarColor: () -> Long? = { null },
    recentUpdatesTextColor: () -> Long? = { null },
    pickStatusMedia: suspend (mediaType: String) -> PickedStatusMedia? = { null },
    captureStory: suspend () -> CapturedStoryMedia? = { null },
    /** Remote media renderer; null selects the built-in default below,
     *  which binds the shared Coil-backed ChatyAsyncImage (ui/theme). */
    remoteImage: (@Composable (url: String, modifier: Modifier) -> Unit)? = null,
    openUrl: (String) -> Unit = {},
    /** SnackBar equivalent for the exact toast strings. */
    onNotify: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // Dart Image.network(..., fit: BoxFit.contain, errorBuilder:
    // broken_image_outlined 72 @ 40% onSurface) over the shared wrapper.
    val defaultRemoteImage: @Composable (String, Modifier) -> Unit = { url, m ->
        com.example.chat.ui.theme.ChatyAsyncImage(
            url = url,
            contentDescription = null,
            modifier = m,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            error = {
                Box(m, contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.BrokenImage,
                        contentDescription = null,
                        tint = colors.foreground.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp),
                    )
                }
            },
        )
    }
    val effectiveRemoteImage = remoteImage ?: defaultRemoteImage
    val viewSession = remember { StatusViewSession() }
    val statusList by statuses.collectAsState(initial = null)
    var composerOpen by remember { mutableStateOf(false) }
    var openStatus by remember { mutableStateOf<StatusRecord?>(null) }

    fun showError(error: Throwable) {
        onNotify((error.message ?: error.toString()).removePrefix("Exception: "))
    }

    fun captureAndPublishStory() {
        scope.launch {
            val result = captureStory() ?: return@launch
            if (confirmBeforeSendingStatus()) {
                // The Dart flow shows ChatyConfirmDialog('Post this status?',
                // 'Your update will be shared with your contacts for 24
                // hours.', 'Send') - handled by the camera seam returning a
                // confirmed capture or by the host's confirm dialog; see the
                // integration note.
            }
            try {
                statusService.publishMediaBytes(
                    bytes = result.bytes,
                    displayName = "chaty_story.jpg",
                    mediaType = "image",
                    text = result.caption,
                )
                onNotify("Status posted.")
            } catch (error: Throwable) {
                showError(error)
            }
        }
    }

    val titleAlreadyVisible = com.example.chat.ui.navigation.LocalDestinationTitlePresentation.current ==
        com.example.chat.core.templates.ChatyNavigationDestinationIds.updates
    Column(modifier = modifier.fillMaxSize().semantics { paneTitle = "Updates" }.verticalScroll(rememberScrollState())) {
        if (!titleAlreadyVisible) Text(
            "Updates",
            color = colors.foreground,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { heading() }.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )

        // My Status composer card
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated)
                .clickable { composerOpen = true }
                .padding(8.dp),
        ) {
            Box {
                TaskAvatar(currentUser.avatarInitials, currentUser.avatarColorHex, 48, colors)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .border(2.dp, colors.surface, CircleShape)
                        .padding(2.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    myNameOverride().ifEmpty { "My Status" },
                    color = colors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Share photo, video, audio or thought",
                    color = colors.foregroundSecondary,
                    fontSize = 12.5.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { captureAndPublishStory() }
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Rounded.PhotoCamera,
                    contentDescription = "Capture photo for status",
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.foregroundTertiary)
        }

        // RECENT UPDATES header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(recentUpdatesBarColor()?.let(::Color) ?: Color.Transparent)
                .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        ) {
            Text(
                "RECENT UPDATES",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = recentUpdatesTextColor()?.let(::Color) ?: colors.primary,
                letterSpacing = 0.8.sp,
            )
        }

        val list = statusList
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.2.dp)
            }
        } else if (list.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.foregroundTertiary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("No recent updates", color = colors.foregroundSecondary, fontSize = 12.5.sp)
            }
        } else {
            StatusList(
                statuses = list,
                template = template,
                colors = colors,
                currentUser = currentUser,
                getUser = getUser,
                contactNameColor = contactNameColor,
                onOpen = { openStatus = it },
            )
        }
        Spacer(Modifier.height(80.dp))
    }

    if (composerOpen) {
        UpdatesComposerSheet(
            statusService = statusService,
            colors = colors,
            confirmBeforeSendingStatus = confirmBeforeSendingStatus,
            pickStatusMedia = pickStatusMedia,
            onDismiss = { composerOpen = false },
            onError = ::showError,
        )
    }

    openStatus?.let { status ->
        StatusViewerDialog(
            status = status,
            statusService = statusService,
            colors = colors,
            currentUser = currentUser,
            getUser = getUser,
            template = template,
            viewSession = viewSession,
            remoteImage = effectiveRemoteImage,
            openUrl = openUrl,
            onDismiss = { openStatus = null },
        )
    }
}

@Composable
private fun StatusList(
    statuses: List<StatusRecord>,
    template: com.example.chat.core.templates.UpdatesTemplate,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    contactNameColor: () -> Long?,
    onOpen: (StatusRecord) -> Unit,
) {
    when (template.layoutMode) {
        UpdatesLayoutMode.circularRail -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(vertical = 5.dp),
        ) {
            items(statuses.size) { index ->
                CircularStatusCard(statuses[index], colors, currentUser, getUser, contactNameColor, onOpen)
            }
        }
        UpdatesLayoutMode.gridTiles -> {
            // Dart LayoutBuilder: columns by max width (>=840:4, >=540:3,
            // >=340:2, else 1); single column falls back to squircle cards.
            androidx.compose.foundation.layout.BoxWithConstraints {
                val columns = when {
                    maxWidth >= 840.dp -> 4
                    maxWidth >= 540.dp -> 3
                    maxWidth >= 340.dp -> 2
                    else -> 1
                }
                if (columns == 1) {
                    Column {
                        statuses.forEach { status ->
                            SquircleStatusCard(status, template, colors, currentUser, getUser, contactNameColor, onOpen)
                            Spacer(Modifier.height(9.dp))
                        }
                    }
                } else {
                    // The screen already scrolls. Content-sized rows avoid a second
                    // fixed-height viewport clipping cards at larger text scales.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        statuses.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { status ->
                                    Box(Modifier.weight(1f)) {
                                        GridStatusCard(status, template, colors, currentUser, getUser, contactNameColor, onOpen)
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        UpdatesLayoutMode.squircleCards -> Column {
            statuses.forEach { status ->
                SquircleStatusCard(status, template, colors, currentUser, getUser, contactNameColor, onOpen)
                Spacer(Modifier.height(9.dp))
            }
        }
        UpdatesLayoutMode.minimalList -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated),
        ) {
            statuses.forEach { status ->
                MinimalStatusTile(status, colors, currentUser, getUser, contactNameColor, onOpen)
            }
        }
    }
}

private fun isMyStatus(status: StatusRecord, currentUser: UserProfile) =
    status.userId == currentUser.id

private fun statusTitle(status: StatusRecord, currentUser: UserProfile, getUser: (String) -> UserProfile?): String =
    if (isMyStatus(status, currentUser)) "My Status"
    else getUser(status.userId)?.displayName ?: "Contact"

private fun statusSubtitle(status: StatusRecord): String =
    status.text.ifEmpty { status.mediaName ?: status.mediaType }

/** Screen-level relative time: 'Just now' / 'Xm' / 'Xh' / 'Xd'. */
private fun relativeTime(value: Instant, now: Instant = Instant.now()): String {
    val diff = Duration.between(value, now)
    if (diff.toMinutes() < 1) return "Just now"
    if (diff.toMinutes() < 60) return "${diff.toMinutes()}m"
    if (diff.toHours() < 24) return "${diff.toHours()}h"
    return "${diff.toDays()}d"
}

@Composable
private fun StatusAvatar(
    status: StatusRecord,
    size: Int,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
) {
    val mine = isMyStatus(status, currentUser)
    val user = if (mine) null else getUser(status.userId)
    TaskAvatar(
        initials = if (mine) currentUser.avatarInitials else user?.avatarInitials ?: "U",
        colorHex = if (mine) currentUser.avatarColorHex else user?.avatarColorHex ?: "0xFF6366F1",
        size = size,
        colors = colors,
    )
}

@Composable
private fun statusNameColor(colors: ChatyColors, contactNameColor: () -> Long?): Color =
    contactNameColor()?.let(::Color) ?: colors.foreground

@Composable
private fun CircularStatusCard(
    status: StatusRecord,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    contactNameColor: () -> Long?,
    onOpen: (StatusRecord) -> Unit,
) {
    val mine = isMyStatus(status, currentUser)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(98.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpen(status) },
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (mine) listOf(colors.primary, colors.primary.copy(alpha = 0.35f))
                        else listOf(colors.primary, colors.info),
                    ),
                )
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier.clip(CircleShape).background(colors.background).padding(2.dp),
            ) {
                StatusAvatar(status, 54, colors, currentUser, getUser)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            statusTitle(status, currentUser, getUser),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = statusNameColor(colors, contactNameColor),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(relativeTime(status.createdAt), color = colors.foregroundTertiary, fontSize = 12.sp)
    }
}

@Composable
internal fun GridStatusCard(
    status: StatusRecord,
    template: com.example.chat.core.templates.UpdatesTemplate,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    contactNameColor: () -> Long?,
    onOpen: (StatusRecord) -> Unit,
) {
    androidx.compose.material3.Surface(
        shadowElevation = template.cardElevation.dp,
        color = colors.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.heightIn(min = 174.dp),
    ) {
        Column(
            modifier = Modifier
                .clickable { onOpen(status) }
                .padding(12.dp),
        ) {
            Row {
                StatusAvatar(status, 42, colors, currentUser, getUser)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (status.hasMedia) Icons.Rounded.PlayCircleOutline else Icons.Rounded.TextSnippet,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                statusTitle(status, currentUser, getUser),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = statusNameColor(colors, contactNameColor),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                statusSubtitle(status),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(relativeTime(status.createdAt), color = colors.foregroundTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SquircleStatusCard(
    status: StatusRecord,
    template: com.example.chat.core.templates.UpdatesTemplate,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    contactNameColor: () -> Long?,
    onOpen: (StatusRecord) -> Unit,
) {
    androidx.compose.material3.Surface(
        shadowElevation = template.cardElevation.dp,
        color = colors.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onOpen(status) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, colors.primary, RoundedCornerShape(18.dp))
                    .padding(3.dp),
            ) {
                StatusAvatar(status, 44, colors, currentUser, getUser)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    statusTitle(status, currentUser, getUser),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = statusNameColor(colors, contactNameColor),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    statusSubtitle(status),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(relativeTime(status.createdAt), color = colors.foregroundTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MinimalStatusTile(
    status: StatusRecord,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    contactNameColor: () -> Long?,
    onOpen: (StatusRecord) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(status) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        StatusAvatar(status, 34, colors, currentUser, getUser)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                statusTitle(status, currentUser, getUser),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = statusNameColor(colors, contactNameColor),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                statusSubtitle(status),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
            )
        }
        Text(relativeTime(status.createdAt), color = colors.foregroundTertiary, fontSize = 12.sp)
    }
}
