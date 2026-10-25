package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToPhotos
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import com.example.chat.ui.theme.ChatyAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Exact port of lib/features/media/ui/picker/custom_file_picker_screen.dart.
 *
 *  Grid/list picker over MediaLibraryService with multi-select, search,
 *  category filter pills, device browse (file_picker -> OpenMultipleDocuments),
 *  and an attachment summary sheet. Result delivery: Dart popped the route
 *  with the file list; here [onSendAttachments]. NOTE (faithful quirk): the
 *  Dart caption field is collected but never returned - the sheet only pops a
 *  bool, so the caption is dropped there too.
 *  Blur: floating dock BackdropFilter -> translucent approximation, same Haze
 *  story as the mini player (dep pending).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomFilePickerScreen(
    libraryService: MediaLibraryService,
    allowMultiple: Boolean = true,
    initialCategory: MediaCategory? = null,
    title: String = "Files & Media",
    isWorkspaceMode: Boolean = false,
    onOpenDocument: (MediaFileDescriptor) -> Unit = {},
    onSendAttachments: (List<MediaFileDescriptor>) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()

    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(libraryService) {
        val listener = { tick++ }
        libraryService.addListener(listener)
        onDispose { libraryService.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    var searchText by remember { mutableStateOf("") }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isGridView by remember { mutableStateOf(true) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var summaryFiles by remember { mutableStateOf<List<MediaFileDescriptor>?>(null) }

    LaunchedEffect(Unit) {
        if (initialCategory != null) libraryService.setFilter(initialCategory)
        libraryService.refreshLibrary()
    }

    fun toggleSelect(item: MediaFileDescriptor) {
        ChatyHapticsPort.selection(view)
        val paths = selectedPaths.toMutableSet()
        if (paths.contains(item.path)) {
            paths.remove(item.path)
            if (paths.isEmpty() && !isWorkspaceMode) isSelectionMode = false
        } else {
            if (!allowMultiple) paths.clear()
            paths.add(item.path)
            isSelectionMode = true
        }
        selectedPaths = paths
    }

    fun confirmAndReturn(files: List<MediaFileDescriptor>) {
        if (files.isEmpty()) return
        summaryFiles = files
    }

    // file_picker allowMultiple -> OpenMultipleDocuments / OpenDocument
    val devicePicker = rememberLauncherForActivityResult(
        if (allowMultiple) ActivityResultContracts.OpenMultipleDocuments()
        else ActivityResultContracts.OpenDocument(),
    ) { result ->
        val uris = when (result) {
            is List<*> -> result.filterIsInstance<android.net.Uri>()
            is android.net.Uri -> listOf(result)
            else -> emptyList()
        }
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val context = view.context
                val descriptors = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        // Copy into cache so descriptors carry real paths,
                        // matching file_picker's Android behavior.
                        val name = "devpick_${System.currentTimeMillis()}_${uris.indexOf(uri)}"
                        val out = File(context.cacheDir, name)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            out.outputStream().use { input.copyTo(it) }
                        } ?: return@mapNotNull null
                        val classification = FileTypeDetector.classifyFile(
                            headerBytesOf = {
                                out.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
                            },
                            filePath = out.absolutePath,
                        )
                        MediaFileDescriptor(
                            id = out.absolutePath,
                            name = out.name,
                            path = out.absolutePath,
                            sizeBytes = out.length(),
                            mimeType = classification.mimeType,
                            category = classification.category,
                            modifiedAt = java.time.Instant.ofEpochMilli(out.lastModified()),
                        )
                    }
                }
                if (descriptors.isEmpty()) return@launch
                if (isWorkspaceMode) {
                    libraryService.refreshLibrary()
                } else {
                    confirmAndReturn(descriptors)
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("Failed to access device files: $e")
            }
        }
    }

    fun pickFromDeviceStorage() {
        ChatyHapticsPort.light(view)
        devicePicker.launch(arrayOf("*/*"))
    }

    fun openFile(item: MediaFileDescriptor) {
        if (item.path.startsWith("demo/") || !item.exists) {
            toggleSelect(item)
            return
        }
        onOpenDocument(item)
    }

    Scaffold(
        containerColor = if (isDark) Color(0xFF0F131A) else Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isDark) Color(0xFF131822).copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.85f),
                    ),
                    title = {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.W700, letterSpacing = (-0.2).sp,
                            ),
                        )
                    },
                    actions = {
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = if (isGridView) "List view" else "Grid view",
                            )
                        }
                        IconButton(onClick = { pickFromDeviceStorage() }) {
                            Icon(Icons.Rounded.AddToPhotos, contentDescription = "Browse device files")
                        }
                        Spacer(Modifier.width(6.dp))
                    },
                )
                // Search input
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            libraryService.setSearchQuery(it)
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        placeholder = { Text("Search files, documents, photos...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchText = ""
                                    libraryService.setSearchQuery("")
                                }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) Color(0xFF1A212E) else Color(0xFFEDF2F7),
                            unfocusedContainerColor = if (isDark) Color(0xFF1A212E) else Color(0xFFEDF2F7),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Category Filter Pills
                FilterPills(
                    isDark = isDark,
                    active = libraryService.activeCategoryFilter,
                    onSelect = { cat ->
                        ChatyHapticsPort.selection(view)
                        libraryService.setFilter(cat)
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                libraryService.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                libraryService.filteredFiles.isEmpty() -> {
                    EmptyState(isDark, onBrowse = { pickFromDeviceStorage() })
                }
                else -> {
                    val items = libraryService.filteredFiles
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 12.dp,
                                bottom = if (selectedPaths.isNotEmpty()) 90.dp else 20.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(items.size) { index ->
                                val item = items[index]
                                GridItem(
                                    item = item,
                                    isSelected = selectedPaths.contains(item.path),
                                    showCheck = isSelectionMode || selectedPaths.contains(item.path),
                                    isDark = isDark,
                                    modifier = Modifier.aspectRatio(0.85f),
                                    onTap = {
                                        if (isSelectionMode || !isWorkspaceMode) toggleSelect(item)
                                        else openFile(item)
                                    },
                                    onLongPress = { toggleSelect(item) },
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 12.dp,
                                bottom = if (selectedPaths.isNotEmpty()) 90.dp else 20.dp,
                            ),
                        ) {
                            items(items.size) { index ->
                                val item = items[index]
                                ListItemRow(
                                    item = item,
                                    isSelected = selectedPaths.contains(item.path),
                                    showCheck = isSelectionMode || selectedPaths.contains(item.path),
                                    isDark = isDark,
                                    onTap = {
                                        if (isSelectionMode || !isWorkspaceMode) toggleSelect(item)
                                        else openFile(item)
                                    },
                                    onLongPress = { toggleSelect(item) },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    if (selectedPaths.isNotEmpty()) {
                        FloatingActionDock(
                            count = selectedPaths.size,
                            isWorkspaceMode = isWorkspaceMode,
                            isDark = isDark,
                            onDeselect = { selectedPaths = emptySet() },
                            onConfirm = {
                                confirmAndReturn(items.filter { selectedPaths.contains(it.path) })
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                        )
                    }
                }
            }
        }
    }

    // Attachment summary sheet
    summaryFiles?.let { files ->
        var caption by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { summaryFiles = null },
            containerColor = if (isDark) Color(0xFF141923) else Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp)
                        .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Send ${files.size} ${if (files.size == 1) "Attachment" else "Attachments"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                    )
                    Spacer(Modifier.weight(1f))
                    val totalMb = files.fold(0L) { sum, f -> sum + f.sizeBytes } / (1024.0 * 1024.0)
                    Text(
                        "%.2f MB total".format(totalMb),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(80.dp)) {
                    items(files.size) { index ->
                        val f = files[index]
                        Column(
                            modifier = Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color(0xFF1E2433) else Color(0xFFF1F5F9))
                                .border(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CategoryIcon(f.category, size = 24)
                            Spacer(Modifier.height(4.dp))
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp, fontWeight = FontWeight.W600)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a message or caption...") },
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    OutlinedButton(
                        onClick = { summaryFiles = null },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Cancel") }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            summaryFiles = null
                            onSendAttachments(files)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Send Now") }
                }
            }
        }
    }
}

@Composable
private fun FilterPills(isDark: Boolean, active: MediaCategory?, onSelect: (MediaCategory?) -> Unit) {
    val categories = listOf<Pair<MediaCategory?, String>>(
        null to "All",
        MediaCategory.image to "Images",
        MediaCategory.video to "Videos",
        MediaCategory.audio to "Audio",
        MediaCategory.pdf to "PDFs",
        MediaCategory.document to "Docs",
        MediaCategory.archive to "Archives",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(36.dp),
    ) {
        items(categories.size) { idx ->
            val (cat, label) = categories[idx]
            val isSelected = active == cat
            Box(
                modifier = Modifier
                    .springInteractiveScale(0.96f)
                    .clickable { onSelect(cat) }
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else (if (isDark) Color(0xFF1A212E) else Color.White),
                    )
                    .border(
                        1.dp,
                        if (isSelected) Color.Transparent
                        else (if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)),
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.W700 else FontWeight.W500,
                    color = if (isSelected) Color.White
                    else (if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridItem(
    item: MediaFileDescriptor,
    isSelected: Boolean,
    showCheck: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier = modifier
            .springInteractiveScale(0.97f)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .shadow(
                8.dp, RoundedCornerShape(14.dp),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.04f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.04f),
            )
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color(0xFF161C26) else Color.White)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ItemPreview(item) }
            Spacer(Modifier.height(4.dp))
            Text(
                item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                fontWeight = FontWeight.W600,
                color = if (isDark) Color.White else Color(0xFF1E293B),
            )
            Text(
                item.humanReadableSize,
                fontSize = 9.sp,
                color = if (isDark) Color.White.copy(alpha = 0.54f) else Color.Black.copy(alpha = 0.54f),
            )
        }
        if (showCheck) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.4f),
                        CircleShape,
                    )
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListItemRow(
    item: MediaFileDescriptor,
    isSelected: Boolean,
    showCheck: Boolean,
    isDark: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springInteractiveScale(0.98f)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color(0xFF161C26) else Color.White)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { ItemPreview(item) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.W600, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "${item.humanReadableSize} • ${item.extension.uppercase()}",
                fontSize = 11.sp,
                color = if (isDark) Color.White.copy(alpha = 0.54f) else Color.Black.copy(alpha = 0.54f),
            )
        }
        if (showCheck) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        CircleShape,
                    )
                    .border(
                        1.5.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else (if (isDark) Color.White.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.38f)),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ItemPreview(item: MediaFileDescriptor) {
    if (item.category == MediaCategory.image && item.exists) {
        ChatyAsyncImage(
            url = File(item.path).toURI().toString(),
            contentDescription = item.name,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            error = { CategoryIcon(item.category) },
        )
        return
    }
    CategoryIcon(item.category)
}

@Composable
private fun CategoryIcon(category: MediaCategory, size: Int = 32) {
    when (category) {
        MediaCategory.image -> Icon(Icons.Rounded.Image, contentDescription = null,
            tint = Color(0xFF38BDF8), modifier = Modifier.size(size.dp))
        MediaCategory.video -> ChatyMediaIcon(icon = ChatyIconType.video, size = size.dp, color = Color(0xFFA855F7))
        MediaCategory.audio -> ChatyMediaIcon(icon = ChatyIconType.audio, size = size.dp, color = Color(0xFF10B981))
        MediaCategory.pdf -> ChatyMediaIcon(icon = ChatyIconType.pdf, size = size.dp, color = Color(0xFFEF4444))
        MediaCategory.document -> ChatyMediaIcon(icon = ChatyIconType.document, size = size.dp, color = Color(0xFF3B82F6))
        MediaCategory.archive -> ChatyMediaIcon(icon = ChatyIconType.archive, size = size.dp, color = Color(0xFFF59E0B))
        MediaCategory.other -> Icon(Icons.Rounded.InsertDriveFile, contentDescription = null,
            tint = Color.Gray, modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun FloatingActionDock(
    count: Int,
    isWorkspaceMode: Boolean,
    isDark: Boolean,
    onDeselect: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(22.dp))
            .background(if (isDark) Color(0xFF1A2230).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.9f))
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f),
                RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count selected", fontWeight = FontWeight.W700, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDeselect) { Text("Deselect") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onConfirm, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isWorkspaceMode) "Share / Open" else "Attach")
        }
    }
}

@Composable
private fun EmptyState(isDark: Boolean, onBrowse: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(if (isDark) Color(0xFF1E2638) else Color(0xFFEDF2F7), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("No Files Found", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700))
        Spacer(Modifier.height(6.dp))
        Text(
            "Select \"Browse device files\" to import photos, videos, or documents from your phone.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBrowse, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.FileOpen, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Browse Files")
        }
    }
}
