package com.example.chat.ui.chats.detail

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.chat.core.theme.ThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AttachmentGalleryItem(val uri: Uri, val name: String)

/** Read-only projection of authorized images; no background access, capture, upload or send. */
internal suspend fun readAttachmentGallery(context: Context): List<AttachmentGalleryItem> = withContext(Dispatchers.IO) {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val result = mutableListOf<AttachmentGalleryItem>()
    context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
        null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC")?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        while (cursor.moveToNext() && result.size < 300) result += AttachmentGalleryItem(
            ContentUris.withAppendedId(collection, cursor.getLong(id)), cursor.getString(name) ?: "Photo")
    }
    result
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AttachmentGallerySheet(theme: ThemeConfig, onAction: (AttachmentAction) -> Unit,
    onCamera: () -> Unit, onReviewImages: ((List<Uri>) -> Unit)?, maxSelection: Int = 30,
    secondReference: Boolean = false, onVoiceNote: () -> Unit = {},
    // Host-driven: opens half screen; the sheet grows when the user pulls up.
    heightFraction: Float = 0.5f,
    // Deterministic fixture injection. Production never provides items here.
    fixtureItems: List<AttachmentGalleryItem>? = null,
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    fun allowed() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED ||
        (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED)
    var canRead by remember { mutableStateOf(allowed()) }
    var reload by remember { mutableIntStateOf(0) }
    var images by remember { mutableStateOf(fixtureItems ?: emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { canRead = allowed(); reload++ }
    LaunchedEffect(canRead, reload, fixtureItems) {
        if (fixtureItems != null) { images = fixtureItems; return@LaunchedEffect }
        if (!canRead) { images = emptyList(); selected = emptyList(); return@LaunchedEffect }
        loading = true; failed = false
        try { images = readAttachmentGallery(context); selected = selected.filter { uri -> images.any { it.uri == uri } } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: SecurityException) { canRead = false; images = emptyList(); selected = emptyList() }
        catch (_: Exception) { failed = true; images = emptyList(); selected = emptyList() }
        finally { loading = false }
    }
    val foreground = Color(theme.primaryTextColor)
    val accent = Color(theme.accentColor)
    Column(Modifier.fillMaxWidth().fillMaxHeight(heightFraction).background(Color(theme.surfaceColor), com.example.chat.ui.kit.ChatyModalShape)
        .navigationBarsPadding().imePadding().semantics { contentDescription = "Attachment gallery sheet" }) {
        Box(Modifier.padding(10.dp).size(32.dp, 4.dp).align(Alignment.CenterHorizontally)
            .background(Color(theme.secondaryTextColor).copy(alpha = 0.4f), CircleShape))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Gallery", color = foreground, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onAction(AttachmentAction.image) }) { Text("Browse") }
            if (selected.isNotEmpty() && onReviewImages != null) Button(onClick = { onReviewImages(selected.toList()) }) { Text("Review (${selected.size})") }
        }
        if (!canRead && fixtureItems == null) {
            Text("Choose photos with Browse, or allow gallery access to see recent photos here.", color = foreground,
                modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { request.launch(if (Build.VERSION.SDK_INT >= 34) arrayOf(permission, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") else arrayOf(permission)) }) { Text("Allow gallery access") }
        }
        if (failed) Text("Gallery unavailable. Use Browse to choose photos.", color = foreground, modifier = Modifier.padding(12.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (secondReference) {
            val capsules = listOf(Triple("Gallery", Icons.Rounded.PhotoLibrary, AttachmentAction.image),
                Triple("Camera", Icons.Rounded.PhotoCamera, null), Triple("Location", Icons.Rounded.LocationOn, AttachmentAction.location),
                Triple("Contact", Icons.Rounded.Person, AttachmentAction.contact), Triple("Document", Icons.Rounded.InsertDriveFile, AttachmentAction.document),
                Triple("Poll", Icons.Rounded.Poll, AttachmentAction.poll), Triple("Audio", Icons.Rounded.GraphicEq, AttachmentAction.audio),
                Triple("Voice note", Icons.Rounded.Mic, null))
            // Wrap at large fonts rather than forcing four clipped labels into a narrow viewport.
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(8.dp),
                maxItemsInEachRow = 4, horizontalArrangement = Arrangement.SpaceEvenly) {
                for ((label, icon, action) in capsules) Column(Modifier.widthIn(min = 72.dp).clickable {
                    when (label) { "Camera" -> onCamera(); "Voice note" -> onVoiceNote(); "Gallery" -> reload++; else -> action?.let(onAction) }
                }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(28.dp), color = Color(theme.cardColor),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(theme.dividerColor))) {
                        Box(Modifier.size(60.dp, 48.dp), contentAlignment = Alignment.Center) { Icon(icon, label, tint = accent) }
                    }
                    Text(label, color = foreground, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(100.dp), modifier = Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "Recent attachment photos" },
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item(key = "camera") {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(theme.cardColor)).clickable(onClick = onCamera)
                    .semantics { contentDescription = "Open camera" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PhotoCamera, null, tint = accent, modifier = Modifier.size(36.dp))
                }
            }
            items(images, key = { it.uri.toString() }) { item ->
                val index = selected.indexOf(item.uri)
                val choose: () -> Unit = {
                    selected = if (index >= 0) selected - item.uri else if (selected.size < maxSelection.coerceIn(1, 100)) selected + item.uri else selected
                }
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(theme.cardColor))
                    .clickable(onClick = choose).semantics {
                        contentDescription = "Photo ${item.name}"; this.selected = index >= 0
                        stateDescription = if (index >= 0) "Selected ${index + 1}" else "Not selected"
                    }) {
                    AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Surface(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp), shape = CircleShape,
                        color = if (index >= 0) accent else Color.Black.copy(alpha = 0.45f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)) {
                        Box(contentAlignment = Alignment.Center) { Text(if (index >= 0) "${index + 1}" else "", color = if (index >= 0) Color(theme.onAccentColor) else Color.White) }
                    }
                }
            }
        }
        if (selected.size >= maxSelection.coerceIn(1, 100)) Text("Selection limit reached. Review or remove a photo.", color = foreground,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
        if (!secondReference) TextButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = onVoiceNote) { Icon(Icons.Rounded.Mic, null); Text("Record voice note") }
        if (!secondReference) Row(Modifier.padding(8.dp).fillMaxWidth().semantics { contentDescription = "Attachment type options" }.clip(RoundedCornerShape(28.dp)).background(Color(theme.cardColor))
            .horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            val actions = listOf(Triple("Gallery", Icons.Rounded.PhotoLibrary, AttachmentAction.image),
                Triple("File", Icons.Rounded.InsertDriveFile, AttachmentAction.document), Triple("Location", Icons.Rounded.LocationOn, AttachmentAction.location),
                Triple("Poll", Icons.Rounded.Poll, AttachmentAction.poll), Triple("Contact", Icons.Rounded.Person, AttachmentAction.contact),
                Triple("Audio", Icons.Rounded.GraphicEq, AttachmentAction.audio), Triple("Video", Icons.Rounded.Videocam, AttachmentAction.video),
                Triple("Task", Icons.Rounded.TaskAlt, AttachmentAction.task))
            for ((label, icon, action) in actions) Column(Modifier.widthIn(min = 76.dp).clickable { if (action == AttachmentAction.image) reload++ else onAction(action) }
                .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, label, tint = if (action == AttachmentAction.image) accent else foreground)
                Text(label, color = if (action == AttachmentAction.image) accent else foreground, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
