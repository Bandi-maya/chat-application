package com.example.chat.ui.chats.contact_info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.domain.models.ChatMessage

/** Titles and metadata only. Actual opening stays on the existing protected viewer path. */
@Composable
fun ConversationMediaIndex(messages: List<ChatMessage>, onOpen: (ChatMessage) -> Unit, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val safe = remember(messages) { messages.filter(ContactInfoLogic::indexable).sortedByDescending { it.createdAt } }
    val media = remember(safe) { ContactInfoLogic.sharedMedia(safe) }
    val docs = remember(safe) { ContactInfoLogic.sharedDocuments(safe) }
    val links = remember(safe) { ContactInfoLogic.extractLinks(safe) }
    val q = query.trim()
    Column(modifier.fillMaxWidth()) {
        val colors = LocalAppColors.current
        // GB "All media" tabs: plain labels, primary label + 2dp underline on the selected tab.
        Row(Modifier.fillMaxWidth()) { listOf("Media", "Docs", "Links").forEachIndexed { index, label ->
            Column(Modifier.weight(1f).clickable { tab = index }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = if (tab == index) colors.primary else colors.foregroundSecondary,
                    fontSize = 15.sp, fontWeight = if (tab == index) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 10.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (tab == index) colors.primary else Color.Transparent))
            }
        } }
        OutlinedTextField(value = query, onValueChange = { query = it.take(200) }, label = { Text("Search shared items") },
            modifier = Modifier.fillMaxWidth().padding(8.dp), singleLine = true)
        Text("Loaded chat data only. Restricted and deleted content is excluded.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
        if (tab == 2) {
            val found = links.filter { q.isBlank() || it.contains(q, ignoreCase = true) }
            if (found.isEmpty()) Text(if (q.isBlank()) "No links found" else "No matching links", modifier = Modifier.padding(16.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) { items(found, key = { it }) { link ->
                // Select/copy explicit text. No automatic network preview, fetch or external URL open.
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(link, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            } }
        } else if (tab == 0) {
            // GB All-media grid: 3 columns, square tiles, video duration badge.
            val found = media.filter { q.isBlank() || it.attachment?.name?.contains(q, ignoreCase = true) == true || it.text.contains(q, ignoreCase = true) }
            if (found.isEmpty()) Text(if (q.isNotBlank()) "No matching items" else "No media found", modifier = Modifier.padding(16.dp))
            if (found.isNotEmpty()) Text("RECENT", color = colors.foregroundSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items(found, key = { it.id }) { message ->
                    val a = message.attachment!!
                    Box(Modifier.aspectRatio(1f).background(colors.surfaceSecondary).clickable { onOpen(message) }) {
                        Icon(if (a.type == "video") Icons.Outlined.PlayCircle else Icons.Outlined.Image,
                            contentDescription = null, tint = colors.foregroundTertiary, modifier = Modifier.align(Alignment.Center).size(28.dp))
                        if (a.type == "video" && a.durationSeconds > 0) {
                            Row(Modifier.align(Alignment.BottomStart).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("${a.durationSeconds / 60}:${(a.durationSeconds % 60).toString().padStart(2, '0')}", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else {
            val found = docs.filter { q.isBlank() || it.attachment?.name?.contains(q, ignoreCase = true) == true || it.text.contains(q, ignoreCase = true) }
            if (found.isEmpty()) Text(if (q.isNotBlank()) "No matching items" else "No documents found", modifier = Modifier.padding(16.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) { items(found, key = { it.id }) { message ->
                val a = message.attachment!!
                Column(Modifier.fillMaxWidth().clickable { onOpen(message) }.padding(16.dp)) {
                    Text(a.name.ifBlank { if (a.type == "video") "Video" else "Photo" })
                    Text(listOfNotNull(a.size.takeIf { it.isNotBlank() }, if (a.type == "video" && a.durationSeconds > 0) "${a.durationSeconds / 60}:${(a.durationSeconds % 60).toString().padStart(2, '0')}" else null).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                }
            } }
        }
    }
}

@Composable
fun ConversationMediaDialog(messages: List<ChatMessage>, onOpen: (ChatMessage) -> Unit, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = LocalAppColors.current.foreground) }
                    Text("All media", color = LocalAppColors.current.foreground, fontSize = 19.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                ConversationMediaIndex(messages, { message -> onClose(); onOpen(message) }, Modifier.weight(1f))
            }
        }
    }
}
