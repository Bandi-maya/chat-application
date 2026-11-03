package com.example.chat.ui.chats.contact_info

import androidx.compose.foundation.clickable
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
        Row(Modifier.fillMaxWidth()) { listOf("Media (${media.size})", "Docs (${docs.size})", "Links (${links.size})").forEachIndexed { index, label ->
            TextButton(onClick = { tab = index }, modifier = Modifier.weight(1f)) { Text(if (tab == index) "$label •" else label) }
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
        } else {
            val found = (if (tab == 0) media else docs).filter { q.isBlank() || it.attachment?.name?.contains(q, ignoreCase = true) == true || it.text.contains(q, ignoreCase = true) }
            if (found.isEmpty()) Text(if (q.isNotBlank()) "No matching items" else if (tab == 1) "No documents found" else "No media found", modifier = Modifier.padding(16.dp))
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
                TextButton(onClick = onClose) { Text("Back to info") }
                ConversationMediaIndex(messages, { message -> onClose(); onOpen(message) }, Modifier.weight(1f))
            }
        }
    }
}
