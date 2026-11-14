package com.example.chat.ui.chats.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.chat.data.local.ChatFolder
import com.example.chat.data.local.ChatFolderStore
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.domain.models.Conversation

/** Local chat folders as a second chip row under the stock filter chips
 *  (Telegram parity, client-side only). "All" restores the stock list; a
 *  folder chip filters to its member conversations; long-press manages. */
@Composable
fun ChatFolderChips(
    theme: ThemeConfig,
    store: ChatFolderStore,
    conversations: List<Conversation>,
    selectedFolderId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision by store.revision.collectAsState()
    val folders = remember(store, revision) { store.folders() }
    var createOpen by remember(store) { mutableStateOf(false) }
    var manageFolder by remember(store) { mutableStateOf<ChatFolder?>(null) }

    val accent = Color(theme.accentColor)
    val card = Color(theme.cardColor)
    val surface = Color(theme.surfaceColor)
    val foreground = Color(theme.primaryTextColor)
    val onAccent = Color(theme.onAccentColor)

    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FolderChip("All", selectedFolderId == null, accent, card, surface, foreground, onAccent) { onSelect(null) }
        folders.forEach { folder ->
            Box(
                Modifier
                    .background(if (folder.id == selectedFolderId) accent else card, RoundedCornerShape(16.dp))
                    .border(1.dp, surface, RoundedCornerShape(16.dp))
                    .pointerInput(store, folder, selectedFolderId, onSelect) {
                        detectTapGestures(
                            onTap = { onSelect(if (selectedFolderId == folder.id) null else folder.id) },
                            onLongPress = { manageFolder = folder },
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(folder.name, color = if (folder.id == selectedFolderId) onAccent else foreground, fontSize = 13.sp)
            }
        }
        FolderChip("+ New folder", false, accent, card, surface, foreground, onAccent) { createOpen = true }
    }

    if (createOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createOpen = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(24) },
                    label = { Text("Folder name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    store.createFolder(name)?.let { onSelect(it.id) }
                    createOpen = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createOpen = false }) { Text("Cancel") } },
        )
    }

    manageFolder?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        var memberQuery by remember(folder.id) { mutableStateOf("") }
        val members = remember(store, folder.id, revision) { store.memberIds(folder.id) }
        val candidates = remember(conversations, memberQuery) {
            val q = memberQuery.trim().lowercase()
            if (q.isEmpty()) conversations
            else conversations.filter { it.title.lowercase().contains(q) }
        }
        AlertDialog(
            onDismissRequest = { manageFolder = null },
            title = { Text("Manage folder") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(24) },
                        label = { Text("Folder name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = memberQuery, onValueChange = { memberQuery = it.take(100) },
                        label = { Text("Add chats") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(top = 4.dp)) {
                        items(candidates, key = { it.id }) { conversation ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { store.toggleMember(folder.id, conversation.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = conversation.id in members,
                                    onCheckedChange = { store.toggleMember(folder.id, conversation.id) },
                                )
                                Text(conversation.title, color = foreground, fontSize = 14.sp, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.renameFolder(folder.id, name)
                    manageFolder = null
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = {
                    store.deleteFolder(folder.id)
                    if (selectedFolderId == folder.id) onSelect(null)
                    manageFolder = null
                }) { Text("Delete") }
            },
        )
    }
}

@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    accent: Color,
    card: Color,
    surface: Color,
    foreground: Color,
    onAccent: Color,
    onTap: () -> Unit,
) {
    Box(
        Modifier
            .background(if (selected) accent else card, RoundedCornerShape(16.dp))
            .border(1.dp, surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (selected) onAccent else foreground, fontSize = 13.sp)
    }
}
