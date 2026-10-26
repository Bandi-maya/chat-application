package com.example.chat.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chat.ui.chats.detail.ChatAttachmentActionController
import com.example.chat.ui.chats.detail.PollDraft
import com.example.chat.ui.chats.detail.PollState
import kotlinx.coroutines.launch

/** Port of Dart _CreatePollDialog (chat_attachment_actions.dart): question
 *  (max 240), 2..12 options (max 120 each), allow-multiple switch, and the
 *  same submit guard via PollDraft.normalized (null = ignore tap). */
@Composable
fun CreatePollDialog(onCancel: () -> Unit, onCreate: (PollDraft) -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multiple by remember { mutableStateOf(false) }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onCancel,
        title = { Text("Create poll") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { if (it.length <= 240) question = it },
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth(),
                )
                options.forEachIndexed { index, value ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { if (it.length <= 120) options[index] = it },
                            label = { Text("Option ${index + 1}") },
                            modifier = Modifier.weight(1f),
                        )
                        if (options.size > 2) {
                            IconButton(onClick = { options.removeAt(index) }) {
                                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove option")
                            }
                        }
                    }
                }
                if (options.size < 12) {
                    TextButton(onClick = { options.add("") }) {
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add option")
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow multiple answers", Modifier.weight(1f))
                    Switch(checked = multiple, onCheckedChange = { multiple = it })
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = {
                val draft = PollDraft.normalized(question, options.toList(), multiple)
                if (draft != null) onCreate(draft)
            }) { Text("Create") }
        },
    )
}

/** Port of Dart _PollViewerSheet: loads get_poll, tap an option to vote
 *  (refreshes on success, controller toasts 'Unable to vote: ...' on failure),
 *  progress bars votes/total, footer 'N total vote(s) [• multiple answers allowed]'. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollViewerSheet(
    messageId: String,
    controller: ChatAttachmentActionController,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember(messageId) { mutableStateOf<PollState?>(null) }
    var error by remember(messageId) { mutableStateOf<String?>(null) }
    var loading by remember(messageId) { mutableStateOf(true) }
    LaunchedEffect(messageId) {
        loading = true
        try { state = controller.loadPoll(messageId) } catch (e: Throwable) { error = e.message ?: e.toString() }
        loading = false
    }
    ModalBottomSheet(shape = com.example.chat.ui.kit.ChatyModalShape, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp).navigationBarsPadding()) {
            val s = state
            when {
                loading -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null && s == null -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text(error ?: "") }
                s != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Poll, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(s.question, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.options.forEach { option ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (option.votedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(14.dp),
                                    )
                                    .clickable {
                                        scope.launch { controller.votePoll(messageId, option.id)?.let { state = it } }
                                    }
                                    .padding(12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(option.label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    if (option.votedByMe) Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(19.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${option.votes}")
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { option.progress(s.totalVotes).toFloat() }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(s.footerText)
                }
            }
        }
    }
}
