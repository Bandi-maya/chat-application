package com.example.chat.island.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Each access is a separate, optional choice. A settings visit never counts as a grant. */
enum class IslandPermissionAccess(val title: String, val explanation: String) {
    Overlay("Draw over other apps", "Overlay access lets the optional cross-app island float above other apps. Without it, use Chaty's in-app features or the optional accessibility host. You can revoke access in Android Settings."),
    Accessibility("Optional accessibility host", "This host places a tappable island above other apps. Its configuration does not retrieve screen content or inject gestures. You can use ordinary overlay access instead, or keep the cross-app island off. Revoke this access in Android Settings."),
    Notifications("Other apps' notifications", "Notification access lets Chaty read notification text and actions and media-session information to show the cross-app island and perform actions you choose. Without it, cross-app notification and media features stay off. Ordinary Chaty messaging still works. Revoke access in Android Settings.");
    fun open(context: Context) {
        val intent = when (this) {
            Overlay -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            Accessibility -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            Notifications -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            android.widget.Toast.makeText(context, "Could not open Android Settings. Open this permission manually in Settings.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun IslandPermissionDisclosure(access: IslandPermissionAccess, onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(access.title) }, text = { Text(access.explanation) },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue to Android Settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } })
}
