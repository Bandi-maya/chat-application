package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import com.example.chat.ui.theme.ChatyAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Exact port of lib/features/media/ui/document/document_reader_screen.dart.
 *
 *  In-app reader for pdf/text/image/video + fallback for office/archive.
 *  Page counts are estimated from extracted line counts exactly as Dart does
 *  (35 lines/page pdf, 45 lines/page text) - not real PDF pagination.
 *  Platform seams: share_plus/url_launcher -> ACTION_SEND / ACTION_VIEW with
 *  FileProvider (owner: manifest + file_paths, same entry as PdfWorkspace).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReaderScreen(
    file: MediaFileDescriptor,
    pdfService: PdfProcessingService,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var textContent by remember { mutableStateOf("") }
    var isLoadingContent by remember { mutableStateOf(true) }
    var contentError by remember { mutableStateOf<String?>(null) }
    var searchMatches by remember { mutableStateOf<List<DocumentSearchMatch>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var showDetails by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(file.path) {
        isLoadingContent = true
        val diskFile = File(file.path)
        if (!diskFile.exists()) {
            isLoadingContent = false
            contentError = "File does not exist on disk: ${file.path}"
            return@LaunchedEffect
        }
        try {
            if (file.category == MediaCategory.pdf) {
                val extracted = withContext(Dispatchers.IO) { pdfService.extractTextFromPdf(file.path) }
                val lines = extracted.split("\n").size
                totalPages = max(1, ceil(lines / 35.0).toInt())
                textContent = extracted
            } else if (file.category == MediaCategory.document ||
                file.extension == "txt" || file.extension == "md" ||
                file.extension == "json" || file.extension == "csv"
            ) {
                val content = withContext(Dispatchers.IO) { diskFile.readText() }
                textContent = content
                totalPages = max(1, ceil(content.split("\n").size / 45.0).toInt())
            }
        } catch (e: Exception) {
            contentError = "Could not read file contents: $e"
        }
        isLoadingContent = false
    }

    fun onSearchChanged(query: String) {
        searchQuery = query
        currentMatchIndex = 0
        if (query.isNotEmpty() && textContent.isNotEmpty()) {
            val regex = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
            val matches = mutableListOf<DocumentSearchMatch>()
            var idx = 0
            for (m in regex.findAll(textContent)) {
                val start = max(0, m.range.first - 20)
                val end = min(textContent.length, m.range.last + 1 + 20)
                val snippet = textContent.substring(start, end).replace("\n", " ")
                matches.add(
                    DocumentSearchMatch(
                        pageNumber = ceil(m.range.first / max(1.0, textContent.length / totalPages.toDouble()))
                            .toInt().coerceIn(1, totalPages),
                        matchIndex = idx++,
                        snippet = snippet,
                        startOffset = m.range.first,
                        endOffset = m.range.last + 1,
                    ),
                )
            }
            searchMatches = matches
        } else {
            searchMatches = emptyList()
        }
    }

    fun nextMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
        currentPage = searchMatches[currentMatchIndex].pageNumber
    }

    fun prevMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
        currentPage = searchMatches[currentMatchIndex].pageNumber
    }

    fun shareFile() {
        ChatyHapticsPort.light(view)
        val diskFile = File(file.path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", diskFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType.ifEmpty { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun openExternal() {
        ChatyHapticsPort.light(view)
        val diskFile = File(file.path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", diskFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType.ifEmpty { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("No external application found to open this file.") }
        }
    }

    Scaffold(
        containerColor = if (isDark) Color(0xFF0F131A) else Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) Color(0xFF131822) else Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back")
                    }
                },
                title = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { onSearchChanged(it) },
                            placeholder = { Text("Search text in document...") },
                            singleLine = true,
                            trailingIcon = {
                                if (searchMatches.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${currentMatchIndex + 1}/${searchMatches.size}",
                                            fontSize = 12.sp, color = Color.Gray,
                                        )
                                        IconButton(onClick = { prevMatch() }) {
                                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                                        }
                                        IconButton(onClick = { nextMatch() }) {
                                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                                        }
                                    }
                                }
                            },
                        )
                    } else {
                        Text(file.name, fontSize = 15.sp, fontWeight = FontWeight.W700,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearching = !isSearching
                        if (!isSearching) {
                            searchQuery = ""
                            searchMatches = emptyList()
                        }
                    }) {
                        Icon(
                            if (isSearching) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (isSearching) "Close search" else "Search text",
                        )
                    }
                    IconButton(onClick = { shareFile() }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share")
                    }
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (totalPages > 1) {
                                DropdownMenuItem(text = { Text("Jump to page...") }, onClick = {
                                    menuOpen = false; showJumpDialog = true
                                })
                            }
                            DropdownMenuItem(text = { Text("Open in external app") }, onClick = {
                                menuOpen = false; openExternal()
                            })
                            DropdownMenuItem(text = { Text("File Details") }, onClick = {
                                menuOpen = false; showDetails = true
                            })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF131822) else Color.White)
                        .border(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.12f),
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { currentPage-- }, enabled = currentPage > 1) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous page")
                    }
                    Box(
                        modifier = Modifier
                            .springInteractiveScale(0.95f)
                            .clickable { showJumpDialog = true }
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color(0xFF1A2230) else Color(0xFFEDF2F7))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text("Page $currentPage of $totalPages", fontWeight = FontWeight.W700, fontSize = 13.sp)
                    }
                    IconButton(onClick = { currentPage++ }, enabled = currentPage < totalPages) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next page")
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                contentError != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null,
                            tint = Color(0xFFFF5252), modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(contentError!!, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { openExternal() }) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Try External App")
                        }
                    }
                }
                isLoadingContent -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                file.category == MediaCategory.image -> {
                    // InteractiveViewer minScale 0.8 maxScale 5.0
                    var zoom by remember { mutableStateOf(1f) }
                    var offsetX by remember { mutableStateOf(0f) }
                    var offsetY by remember { mutableStateOf(0f) }
                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(0.8f, 5f)
                        offsetX += panChange.x
                        offsetY += panChange.y
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().transformable(transformState),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChatyAsyncImage(
                            url = File(file.path).toURI().toString(),
                            contentDescription = file.name,
                            modifier = Modifier.graphicsLayer {
                                scaleX = zoom; scaleY = zoom
                                translationX = offsetX; translationY = offsetY
                            },
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                file.category == MediaCategory.video -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ChatyVideoPlayerWidget(
                            videoPath = file.path,
                            title = file.name,
                            autoPlay = true,
                        )
                    }
                }
                file.category == MediaCategory.pdf || file.category == MediaCategory.document -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    12.dp, RoundedCornerShape(16.dp),
                                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.3f else 0.04f),
                                    spotColor = Color.Black.copy(alpha = if (isDark) 0.3f else 0.04f),
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color(0xFF151B26) else Color.White)
                                .border(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(20.dp),
                        ) {
                            SelectionContainer {
                                Text(
                                    textContent.ifEmpty { "Document is empty." },
                                    fontSize = 14.sp,
                                    lineHeight = (14 * 1.6).sp,
                                    fontFamily = if (file.extension == "json" || file.extension == "csv")
                                        FontFamily.Monospace else null,
                                    color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                                )
                            }
                        }
                    }
                }
                else -> {
                    // Office / Archive / Unsupported fallback
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    if (isDark) Color(0xFF1E2638) else Color(0xFFEDF2F7),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChatyMediaIcon(
                                icon = if (file.category == MediaCategory.archive) ChatyIconType.archive
                                else ChatyIconType.document,
                                size = 38.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            file.name,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${file.humanReadableSize} • ${file.mimeType}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "This document format is best viewed with an external application or productivity suite.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xDD000000),
                            ),
                        )
                        Spacer(Modifier.height(24.dp))
                        Row {
                            OutlinedButton(onClick = { openExternal() }) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open External")
                            }
                            Spacer(Modifier.width(14.dp))
                            Button(onClick = { shareFile() }) {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Share File")
                            }
                        }
                    }
                }
            }
        }
    }

    // Page jump dialog
    if (showJumpDialog) {
        var jumpText by remember { mutableStateOf("$currentPage") }
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                OutlinedTextField(
                    value = jumpText,
                    onValueChange = { jumpText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("Enter page (1 - $totalPages)") },
                    suffix = { Text("of $totalPages") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    jumpText.toIntOrNull()?.let { target ->
                        if (target in 1..totalPages) currentPage = target
                    }
                    showJumpDialog = false
                }) { Text("Jump") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            },
        )
    }

    // File details sheet
    if (showDetails) {
        ModalBottomSheet(tonalElevation = 0.dp, dragHandle = null,
            onDismissRequest = { showDetails = false },
            containerColor = if (isDark) Color(0xFF141923) else Color.White,
            shape = com.example.chat.ui.kit.ChatyModalShape,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "File Information",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                )
                Spacer(Modifier.height(14.dp))
                InfoRow("File Name", file.name)
                InfoRow("Size", file.humanReadableSize)
                InfoRow("MIME Type", file.mimeType)
                InfoRow("Category", file.category.name.uppercase())
                InfoRow("Path", file.path)
                InfoRow(
                    "Modified",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault()).format(file.modifiedAt),
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    OutlinedButton(onClick = { showDetails = false; openExternal() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open External")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { showDetails = false; shareFile() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Share File")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            fontWeight = FontWeight.W600,
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.width(90.dp),
        )
        Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}
