package com.example.chat.ui.chats.detail

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun ReportMessageDialog(onDismiss: () -> Unit, onSubmit: suspend (String, String) -> Unit) {
    var reason by remember { mutableStateOf("spam") }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Report message") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text("Send the message ID, reason and your details to moderation. Message text is not added by this form.")
                ApiReasonPicker(reason) { if (!busy) reason = it }
                OutlinedTextField(value = details, onValueChange = { if (!busy && it.length <= 1000) details = it },
                    label = { Text("Details (optional)") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("report-details"), maxLines = 3)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("report-submit"), onClick = {
            busy = true; error = null
            scope.launch {
                try { onSubmit(reason, details); onDismiss() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Report was not confirmed. Try again." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Sending..." else "Send report") } },
    )
}

@Composable
private fun ApiReasonPicker(selected: String, onSelect: (String) -> Unit) {
    Column {
        for ((id, label) in listOf("spam" to "Spam", "harassment" to "Harassment", "hate" to "Hate", "sexual_content" to "Sexual content", "violence" to "Violence", "other" to "Other")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = selected == id, onClick = { onSelect(id) }, modifier = Modifier.testTag("report-reason-$id"))
                Text(label)
            }
        }
    }
}
