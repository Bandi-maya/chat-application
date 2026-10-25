package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Exact port of lib/features/media/ui/pdf/pdf_workspace_screen.dart.
 *
 *  Platform seams:
 *  - file_picker -> ActivityResultContracts (PickMultipleImages / OpenDocument)
 *  - path_provider documents dir -> context.filesDir
 *  - share_plus -> ACTION_SEND via FileProvider (owner: FileProvider entry in
 *    manifest + res/xml file_paths, authority "\${packageId}.fileprovider")
 *  Navigation: DocumentReaderScreen open is delegated via [onOpenDocument].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfWorkspaceScreen(
    library: MediaLibraryService,
    pdfService: PdfProcessingService,
    onOpenDocument: (MediaFileDescriptor) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val snackbar = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()

    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(library) {
        val listener: () -> Unit = { tick++ }
        library.addListener(listener)
        onDispose { library.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    var isProcessing by remember { mutableStateOf(false) }
    var processingStatus by remember { mutableStateOf("") }
    var successSheet by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        library.setFilter(MediaCategory.pdf)
        library.refreshLibrary()
    }

    fun runConversion(type: ConversionType, paths: List<String>, status: String, successTitle: String) {
        if (paths.isEmpty()) return
        isProcessing = true
        processingStatus = status
        scope.launch {
            try {
                val docDir = context.filesDir
                val now = System.currentTimeMillis()
                val request = ConversionRequest(
                    id = now.toString(),
                    type = type,
                    sourceFilePaths = paths,
                    outputDirectory = docDir.path,
                    outputFileName = if (type == ConversionType.imagesToPdf)
                        "Chaty_Images_$now.pdf" else "Chaty_Document_$now.pdf",
                )
                val result = when (type) {
                    ConversionType.imagesToPdf -> pdfService.convertImagesToPdf(request)
                    else -> pdfService.convertTextToPdf(request)
                }
                isProcessing = false
                if (result.success && result.outputFilePaths.isNotEmpty()) {
                    ChatyHapticsPort.threshold(view)
                    library.refreshLibrary()
                    successSheet = successTitle to result.outputFilePaths.first()
                } else {
                    snackbar.showSnackbar(result.errorMessage ?: "Conversion failed.")
                }
            } catch (e: Exception) {
                isProcessing = false
                snackbar.showSnackbar("Error: $e")
            }
        }
    }

    // file_picker: FileType.image, allowMultiple: true
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9, ActivityResultContracts.PickVisualMedia.ImageOnly)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // Persisted permission + copy into cache so the clean-room PDF builder
        // can read plain file paths (Dart received real paths from file_picker).
        val paths = uris.mapNotNull { uri -> copyToCache(context, uri, "imgpick") }
        runConversion(ConversionType.imagesToPdf, paths, "Converting ${paths.size} images to PDF...", "Image to PDF Complete")
    }
    // file_picker: FileType.custom ['txt','md','json','csv']
    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = copyToCache(context, uri, "txtpick") ?: return@rememberLauncherForActivityResult
        runConversion(ConversionType.textToPdf, listOf(path), "Converting text to PDF...", "Text to PDF Complete")
    }

    Scaffold(
        containerColor = if (isDark) Color(0xFF0F131A) else Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) Color(0xFF131822) else Color.White,
                ),
                title = { Text("PDF Workspace & Tools", fontWeight = FontWeight.W700, fontSize = 16.sp) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(18.dp)) {
                item {
                    Text(
                        "CONVERSION UTILITIES",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.1.sp, fontWeight = FontWeight.W800, color = Color.Gray,
                        ),
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Row {
                        Box(Modifier.weight(1f)) {
                            ToolCard(ChatyIconType.convert, "Images to PDF", "Merge photos into document", Color(0xFF38BDF8), isDark) {
                                ChatyHapticsPort.light(view)
                                imagePicker.launch("image/*")
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            ToolCard(ChatyIconType.document, "Text to PDF", "Format txt/md as PDF", Color(0xFF10B981), isDark) {
                                ChatyHapticsPort.light(view)
                                textPicker.launch(arrayOf("text/plain", "text/markdown", "application/json", "text/csv", "text/*"))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "YOUR PDF DOCUMENTS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 1.1.sp, fontWeight = FontWeight.W800, color = Color.Gray,
                            ),
                        )
                        IconButton(onClick = { library.refreshLibrary() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }

                val pdfs = library.files.filter { it.category == MediaCategory.pdf }
                if (pdfs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color(0xFF161C26) else Color.White)
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("No PDFs stored in Chaty yet. Convert images or import one above!") }
                    }
                } else {
                    items(pdfs.size) { idx ->
                        val item = pdfs[idx]
                        Box(Modifier.springInteractiveScale(0.98f).clickable { onOpenDocument(item) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isDark) Color(0xFF161C26) else Color.White)
                                    .border(
                                        1.dp,
                                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                        RoundedCornerShape(14.dp),
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { ChatyMediaIcon(icon = ChatyIconType.pdf, size = 20.dp, color = Color(0xFFEF4444)) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.W700, fontSize = 13.sp)
                                    val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .withZone(ZoneId.systemDefault())
                                        .format(item.modifiedAt)
                                    Text(
                                        "${item.humanReadableSize} • $stamp",
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

            // Processing progress overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.54f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isDark) Color(0xFF1E2638) else Color.White)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(processingStatus, fontWeight = FontWeight.W700)
                    }
                }
            }
        }
    }

    // Success sheet
    successSheet?.let { (title, outputPath) ->
        ModalBottomSheet(
            onDismissRequest = { successSheet = null },
            containerColor = if (isDark) Color(0xFF141923) else Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            val file = File(outputPath)
            val sizeStr = if (file.exists()) "%.1f KB".format(file.length() / 1024.0) else ""
            Column(modifier = Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(54.dp).background(Color(0xFF22C55E).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.height(12.dp))
                Text(title, fontWeight = FontWeight.W800, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text("${file.name} • $sizeStr", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(20.dp))
                Row {
                    OutlinedButton(
                        onClick = {
                            successSheet = null
                            // share_plus -> ACTION_SEND + FileProvider
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            successSheet = null
                            val descriptor = MediaFileDescriptor(
                                id = outputPath,
                                name = file.name,
                                path = outputPath,
                                sizeBytes = file.length(),
                                mimeType = "application/pdf",
                                category = MediaCategory.pdf,
                                modifiedAt = java.time.Instant.ofEpochMilli(file.lastModified()),
                            )
                            onOpenDocument(descriptor)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.RemoveRedEye, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open PDF")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
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
                    10.dp, RoundedCornerShape(16.dp),
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.04f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.04f),
                )
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0xFF161C26) else Color.White)
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                    RoundedCornerShape(16.dp),
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

/** Copies a picked content Uri into the app cache and returns the file path,
 *  matching file_picker's real-path behavior on Android. */
private fun copyToCache(context: android.content.Context, uri: android.net.Uri, prefix: String): String? {
    return try {
        val name = "${prefix}_${System.currentTimeMillis()}"
        val out = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return null
        out.absolutePath
    } catch (e: Exception) {
        null
    }
}
