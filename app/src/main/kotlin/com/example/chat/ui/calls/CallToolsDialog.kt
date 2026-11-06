package com.example.chat.ui.calls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** No recorder is started here. Record explains the consent and capture gates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CallToolsDialog(
    held: Boolean, canHold: Boolean, canNotifyHold: Boolean,
    onHold: () -> Unit, onReaction: (String) -> Unit, onDismiss: () -> Unit,
) {
    var detail by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Call tools") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = {
                    if (canHold) onHold() else detail = "Local hold is unavailable on this call's audio provider. Audio has not been changed."
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (held) "Resume audio" else "Hold audio") }
                Text(if (canNotifyHold) "Pause your microphone and what you hear. The other person sees a hold notice." else "Local pause only: pauses your microphone and what you hear. The other person will not receive a hold notice until server and peer support is verified.")
                TextButton(onClick = { detail = "Add call is pending multi-call support. The current call stays connected. Starting the existing new-call flow would replace this call, so it is not used here." }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add call - pending") }
                TextButton(onClick = { detail = "Recording needs both people's agreement, a visible recording indicator and a verified capture of both sides. Ask to record will be available only when those checks and server/peer support are ready. Nothing is recording now. A microphone-only voice recorder is not call recording." }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Record - consent and capture pending") }
                Text("Reactions")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("👍", "❤️", "😂", "👏", "🎉", "👋").forEach { emoji ->
                        TextButton(onClick = { onReaction(emoji) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text(emoji) }
                    }
                }
                Text("If the other person cannot receive reactions, yours is shown only on this device. Reactions disappear after a few seconds.")
                detail?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
