package com.example.chat.ui.chats.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Opened ONLY by an explicit receiver tap. State is memory-only, never rememberSaveable. */
@Composable
internal fun TemporaryViewOnceTextViewer(
    messageId: String,
    open: suspend () -> String,
    onDismiss: () -> Unit,
) {
    var closed by remember(messageId) { mutableStateOf(false) }
    var body by remember(messageId) { mutableStateOf<String?>(null) }
    var loading by remember(messageId) { mutableStateOf(true) }
    var unavailable by remember(messageId) { mutableStateOf(false) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(messageId, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { closed = true; body = null; latestDismiss() }
        }
        lifecycle.addObserver(observer)
        onDispose { closed = true; body = null; lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(messageId) {
        try {
            val received = open()
            if (!closed && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) body = received
        }
        catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            unavailable = true // A response loss may already have consumed it. Never replay.
        } finally { loading = false }
    }
    Dialog(onDismissRequest = { closed = true; body = null; onDismiss() }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface {
            Column(Modifier.padding(24.dp)) {
                Text("View once message")
                when {
                    loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                    unavailable -> Text("No longer available", Modifier.padding(vertical = 16.dp))
                    else -> Text(body.orEmpty(), Modifier.padding(vertical = 16.dp))
                }
                TextButton(onClick = { closed = true; body = null; onDismiss() }) { Text("Close") }
            }
        }
    }
}
