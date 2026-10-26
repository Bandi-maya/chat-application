package com.example.chat.ui.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemeConfig

/** Dart ChatDetailScreen._isSecureSetupPendingError. */
fun isSecureSetupPendingError(raw: String): Boolean {
    val lower = raw.lowercase()
    return raw.contains("secure_setup_pending") ||
        lower.contains("mls group is not initialized") ||
        lower.contains("every conversation member must register an mls device") ||
        lower.contains("must register an mls device") ||
        lower.contains("no key packages available") ||
        raw.contains("MlsMembershipPendingException")
}

/** Dart _messagesBody non-list branches: load error (secure-setup-pending or
 *  generic with Retry), loading spinner, and the 'No messages yet' empty
 *  state. Renders nothing when there are messages (or none of the states apply). */
@Composable
fun ChatMessagesStateOverlay(
    theme: ThemeConfig,
    messagesEmpty: Boolean,
    loadingMessages: Boolean,
    loadError: String?,
    onRetryLoad: () -> Unit,
) {
    if (!messagesEmpty) return
    val primary = Color(theme.primaryTextColor)
    val secondary = Color(theme.secondaryTextColor)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            loadError != null -> Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (isSecureSetupPendingError(loadError)) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = secondary, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Waiting for secure chat setup", color = primary, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This chat is finishing its end-to-end encryption setup. " +
                            "Messages will appear here as soon as it completes.",
                        color = secondary,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = secondary, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Could not load messages", color = primary, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text(loadError, color = secondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onRetryLoad) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Retry")
                    }
                }
            }
            loadingMessages -> CircularProgressIndicator(strokeWidth = 2.dp)
            else -> Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = secondary, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(10.dp))
                Text("No messages yet", color = primary, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("Say hi to start the conversation.", color = secondary, textAlign = TextAlign.Center)
            }
        }
    }
}
