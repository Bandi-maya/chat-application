package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import com.example.chat.ui.theme.ChatyAsyncImage
import kotlinx.coroutines.launch
import java.io.File

/** Exact port of lib/features/media/ui/workspace/media_workspace_screen.dart.
 *
 *  Navigation: Dart pushed CustomFilePickerScreen / PdfWorkspaceScreen /
 *  DocumentReaderScreen via Navigator; here the host supplies [onOpenFileExplorer],
 *  [onOpenPdfStudio], [onOpenDocument]. Return-from-push refresh is handled by
 *  the host calling [MediaLibraryService.refreshLibrary].
 *  Service wiring: Dart used the GetIt locator; the owner wires ServiceLocator.
 *  Image.file thumbnails use the shared Coil-backed ChatyAsyncImage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaWorkspaceScreen(
    playerCoordinator: MediaPlayerCoordinator,
    library: MediaLibraryService,
    sanitizer: FileSanitizationService,
    onOpenFileExplorer: () -> Unit = {},
    onOpenPdfStudio: () -> Unit = {},
    onOpenDocument: (MediaFileDescriptor) -> Unit = {},
    onExpandPlayer: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val view = androidx.compose.ui.platform.LocalView.current

    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(library) {
        val listener: () -> Unit = { tick++ }
        library.addListener(listener)
        onDispose { library.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    LaunchedEffect(Unit) { library.refreshLibrary() }

    val isDark = isSystemInDarkTheme()

    Scaffold(
        containerColor = if (isDark) Color(0xFF0F131A) else Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) Color(0xFF131822) else Color.White,
                ),
                title = { Text("Media Studio & Files", fontWeight = FontWeight.W700, fontSize = 16.sp) },
                actions = {
                    IconButton(onClick = {
                        ChatyHapticsPort.light(view)
                        scope.launch {
                            sanitizer.cleanExpiredTempFiles(maxAgeMs = 0)
                            library.refreshLibrary()
                            snackbar.showSnackbar("Temporary media cache cleaned successfully.")
                        }
                    }) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = "Clean temporary cache")
                    }
                    IconButton(onClick = { library.refreshLibrary() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh library")
                    }
                    Spacer(Modifier.width(6.dp))
                },
            )
        },
    ) { padding ->
        val files = library.files
        val totalBytes = files.fold(0L) { sum, f -> sum + f.sizeBytes }
        val breakdown = library.getStorageBreakdown()

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 90.dp),
            ) {
                item { StorageOverviewCard(isDark, totalBytes, breakdown) }
                item { Spacer(Modifier.height(20.dp)) }

                item {
                    Text(
                        "STUDIO WORKSPACES",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.W800,
                            color = Color.Gray,
                        ),
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Row {
                        Box(Modifier.weight(1f)) {
                            ActionCard(
                                icon = ChatyIconType.folder,
                                title = "File Explorer",
                                subtitle = "Browse all media",
                                color = Color(0xFF38BDF8),
                                isDark = isDark,
                                onTap = onOpenFileExplorer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            ActionCard(
                                icon = ChatyIconType.pdf,
                                title = "PDF Studio",
                                subtitle = "Convert & manage",
                                color = Color(0xFFEF4444),
                                isDark = isDark,
                                onTap = onOpenPdfStudio,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }

                item {
                    Text(
                        "RECENT ATTACHMENTS & FILES",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.W800,
                            color = Color.Gray,
                        ),
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }

                if (files.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color(0xFF161C26) else Color.White)
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("No files or media found in Chaty storage.") }
                    }
                } else {
                    items(files.take(15).size) { idx ->
                        val file = files[idx]
                        Box(
                            Modifier
                                .springInteractiveScale(0.98f)
                                .clickable {
                                    if (file.category == MediaCategory.audio) {
                                        scope.launch {
                                            playerCoordinator.playTrack(
                                                file,
                                                queue = files.filter { it.category == MediaCategory.audio },
                                            )
                                        }
                                    } else {
                                        onOpenDocument(file)
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isDark) Color(0xFF161C26) else Color.White)
                                    .border(
                                        width = 1.dp,
                                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FileThumb(file)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.W700,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        "${file.humanReadableSize} • ${file.category.name.uppercase()}",
                                        fontSize = 11.sp,
                                        color = if (isDark) Color.White.copy(alpha = 0.54f) else Color.Black.copy(alpha = 0.54f),
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.Gray)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Mini Player pinned at bottom if active
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                ChatyMiniPlayer(coordinator = playerCoordinator, onExpand = onExpandPlayer)
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(isDark: Boolean, totalBytes: Long, breakdown: Map<MediaCategory, Long>) {
    val mb = totalBytes / (1024.0 * 1024.0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.25f else 0.04f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.25f else 0.04f),
            )
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) Color(0xFF161C26) else Color.White)
            .border(
                width = 1.dp,
                color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Chaty Media Storage",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${"%.1f".format(mb)} MB Used",
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(14.dp))
        // Multi-segmented bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            StorageSegment(breakdown[MediaCategory.image] ?: 0, totalBytes, Color(0xFF38BDF8))
            StorageSegment(breakdown[MediaCategory.video] ?: 0, totalBytes, Color(0xFFA855F7))
            StorageSegment(breakdown[MediaCategory.audio] ?: 0, totalBytes, Color(0xFF10B981))
            StorageSegment(breakdown[MediaCategory.pdf] ?: 0, totalBytes, Color(0xFFEF4444))
            StorageSegment(breakdown[MediaCategory.document] ?: 0, totalBytes, Color(0xFFF59E0B))
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LegendDot("Photos", Color(0xFF38BDF8))
            LegendDot("Videos", Color(0xFFA855F7))
            LegendDot("Audio", Color(0xFF10B981))
            LegendDot("PDFs", Color(0xFFEF4444))
            LegendDot("Docs", Color(0xFFF59E0B))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StorageSegment(bytes: Long, total: Long, color: Color) {
    if (total == 0L || bytes == 0L) return
    Box(modifier = Modifier.weight(bytes.toFloat()).fillMaxSize().background(color))
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun ActionCard(
    icon: ChatyIconType,
    title: String,
    subtitle: String,
    color: Color,
    isDark: Boolean,
    onTap: () -> Unit,
) {
    Box(Modifier.springInteractiveScale(0.96f).clickable(onClick = onTap)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.04f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.04f),
                )
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0xFF161C26) else Color.White)
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { ChatyMediaIcon(icon = icon, size = 20.dp, color = color) }
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.W700, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun FileThumb(file: MediaFileDescriptor) {
    if (file.category == MediaCategory.image && file.exists) {
        ChatyAsyncImage(
            url = File(file.path).toURI().toString(),
            contentDescription = file.name,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            error = { Icon(Icons.Rounded.Image, contentDescription = null) },
        )
        return
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(Color.Gray.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ChatyMediaIcon(
            icon = when (file.category) {
                MediaCategory.audio -> ChatyIconType.audio
                MediaCategory.pdf -> ChatyIconType.pdf
                else -> ChatyIconType.document
            },
            size = 18.dp,
            color = Color(0xFF448AFF), // Colors.blueAccent
        )
    }
}
