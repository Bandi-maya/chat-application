package com.example.chat.bubbles

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chat.ServiceLocator
import com.example.chat.ui.settings.ChatySettingsSection

/** Explicit account-scoped mode and per-chat selection. No permission is requested on entry. */
@Composable
fun ChatBubbleSettings() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        revision++
        status = if (it) "Notifications allowed. You can now choose a chat." else "Notification permission was not granted. No bubble was created."
    }
    DisposableEffect(Unit) {
        ChatBubbleController.install(context)
        val listener: () -> Unit = { revision++ }
        ChatBubbleController.addObserver(listener)
        ServiceLocator.apiBackend.addListener(listener)
        ServiceLocator.preferencesController.addListener(listener)
        onDispose {
            ChatBubbleController.removeObserver(listener)
            ServiceLocator.apiBackend.removeListener(listener)
            ServiceLocator.preferencesController.removeListener(listener)
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_EXPRESSION") revision
    val enabled = ChatBubbleController.enabledForCurrentAccount()
    val supported = Build.VERSION.SDK_INT >= 30 && ServiceLocator.apiBackend.currentAccountKey() != null
    ChatySettingsSection(
        title = "Chat bubbles over other apps",
        description = "Off by default. Native Android bubbles, separate from the in-app split workspace. Android controls their size and position.",
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row {
                Text("Enable for this account", Modifier.weight(1f))
                Switch(checked = enabled, enabled = supported, onCheckedChange = {
                    if (it) confirm = true else { ChatBubbleController.setEnabled(context, false); revision++ }
                })
            }
            if (!supported) Text("Requires Android 11 or newer and a signed-in account.")
            if (enabled) {
                Text("Only chats you choose below can float. Locking the phone, locking Chaty or changing accounts closes them. Protected chats are excluded.")
                if (!ChatBubbleController.notificationsAllowed() && Build.VERSION.SDK_INT >= 33) {
                    Button(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                }
                TextButton(onClick = { ChatBubbleController.openSystemSettings(context) }) { Text("Android bubble settings") }
                ServiceLocator.apiBackend.conversations.filterNot {
                    ServiceLocator.preferencesController.isConversationProtected(it.id) ||
                        ServiceLocator.hiddenVaultController.isConversationLocked(it.id)
                }.forEach { conversation ->
                    TextButton(onClick = {
                        status = ChatBubbleController.requestBubble(context, conversation.id)
                        revision++
                    }) { Text("Float chat: ${conversation.title}") }
                }
                status?.let { Text(it) }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Allow chat bubbles for this account?") },
        text = { Text("A chat you choose can show messages and its composer over other apps. Android may ask you to allow notifications and bubbles. No accessibility or draw-over-apps access is used. No chat opens automatically.") },
        confirmButton = { TextButton(onClick = { ChatBubbleController.setEnabled(context, true); confirm = false; revision++ }) { Text("Enable mode") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
