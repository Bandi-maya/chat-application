package com.example.chat.ui.chats.contact_info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.ContactConnectionStatus
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalAppColors
import java.time.Instant
import kotlinx.coroutines.launch

/** Port of contact_info_screen.dart (api branch anchor 3bee780): profile
 *  header with presence label, quick actions, connection/accept state,
 *  privacy & safety actions (block, delete chat, delete contact+chat),
 *  shared media/links/documents, and the (Dart-static) groups in common.
 *
 *  The Dart screen sources relationship/block state from
 *  ContactRelationshipService and presence from RichChatRealtimeService and
 *  hydrates messages through the realtime service; those seams arrive here
 *  as lambdas/state so the screen stays free of shared-file edits. On the
 *  REST session plane those Supabase-plane services report the same load
 *  error the Dart screen surfaces, which is the exact current behavior. */
@Composable
fun ContactInfoScreen(
    conversation: Conversation,
    contact: UserProfile,
    messages: List<ChatMessage>,
    connection: ContactConnectionStatus,
    blocked: Boolean,
    loading: Boolean,
    loadError: String?,
    busy: Boolean,
    isOnline: (String) -> Boolean,
    lastSeenFor: (String) -> Instant?,
    activityFor: (conversationId: String, userId: String) -> com.example.chat.data.realtime.ContactActivityState,
    hydrateMessage: (ChatMessage) -> ChatMessage,
    onAccept: suspend () -> Unit,
    onSetBlocked: suspend (Boolean) -> Unit,
    onDeleteConversation: () -> Unit,
    onDeleteUser: suspend () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenMedia: (ChatMessage) -> Unit,
    onSearch: () -> Unit,
    onDeletedPopToRoot: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmAction by remember { mutableStateOf<String?>(null) }

    val hydrated = remember(messages) { messages.map(hydrateMessage) }
    val media = remember(hydrated) { ContactInfoLogic.sharedMedia(hydrated) }
    val documents = remember(hydrated) { ContactInfoLogic.sharedDocuments(hydrated) }
    val links = remember(hydrated) { ContactInfoLogic.extractLinks(hydrated) }
    val activity = activityFor(conversation.id, contact.id)
    val online = isOnline(contact.id)

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.foreground)
                }
                Text(
                    "Contact Info",
                    color = colors.foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.2.dp, color = colors.primary)
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Header avatar + online dot.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box {
                    Box(
                        modifier = Modifier.size(92.dp).background(
                            Color(parseAvatarColorArgbKit(contact.avatarColorHex)),
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            contact.avatarInitials,
                            color = colors.onPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (online) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(16.dp)
                                .background(colors.background, CircleShape)
                                .padding(2.5.dp)
                                .background(colors.success, CircleShape),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                contact.displayName,
                color = colors.foreground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "@${contact.username}",
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                ContactInfoLogic.presenceLabel(
                    isRecording = activity.isRecording,
                    isTyping = activity.isTyping,
                    isOnline = online,
                    lastSeen = lastSeenFor(contact.id),
                ),
                color = if (online) colors.success else colors.foregroundSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // Quick action row: Call / Video / Search / Share.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                QuickAction(Icons.Outlined.Call, "Call") {
                    scope.launch { snackbar.showSnackbar("Starting voice call with ${contact.displayName}...") }
                }
                QuickAction(Icons.Outlined.Videocam, "Video") {
                    scope.launch { snackbar.showSnackbar("Starting video call with ${contact.displayName}...") }
                }
                QuickAction(Icons.Rounded.Search, "Search") { onSearch() }
                QuickAction(Icons.Outlined.Share, "Share") {
                    scope.launch { snackbar.showSnackbar("Sharing contact card for ${contact.displayName}...") }
                }
            }

            if (contact.about.trim().isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(12.dp),
                ) {
                    Text(contact.about, color = colors.foreground, fontSize = 14.5.sp, lineHeight = 20.sp)
                }
            }
            if (loadError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    loadError,
                    color = colors.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))

            // Connection & Calls.
            SectionTitle("Connection & Calls", colors)
            Box(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (connection.callsAllowed) Icons.Rounded.VerifiedUser else Icons.Rounded.PersonAddAlt1,
                        contentDescription = null,
                        tint = if (connection.callsAllowed) colors.success else colors.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (connection.callsAllowed) "Mutual contact verified"
                            else if (connection.isPendingIncoming) "Accept contact request"
                            else if (connection.isWaitingForOther) "Waiting for ${contact.displayName}"
                            else "Accept this contact",
                            color = colors.foreground,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Text(
                            if (connection.callsAllowed) "Encrypted voice and video calls are unlocked."
                            else "Messaging stays open. Direct calls unlock when both accept.",
                            color = colors.foregroundSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    if (!connection.myAccepted) {
                        androidx.compose.material3.Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        onAccept()
                                    } catch (error: Exception) {
                                        snackbar.showSnackbar("$error")
                                    }
                                }
                            },
                            enabled = !busy,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            modifier = Modifier.height(36.dp).width(88.dp),
                        ) {
                            Text("Accept", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(
                            if (connection.callsAllowed) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = if (connection.callsAllowed) colors.success else colors.foregroundTertiary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Privacy & Safety.
            SectionTitle("Privacy & Safety", colors)
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                InfoTile(
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null, tint = colors.primary) },
                    title = "Individual Privacy Controls",
                    titleColor = colors.foreground,
                    subtitle = "Read receipts, typing indicator, recording & online presence",
                    trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.foregroundSecondary) },
                    onClick = onOpenPrivacy,
                )
                InfoTile(
                    icon = {
                        Icon(
                            if (blocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                            contentDescription = null,
                            tint = colors.error,
                        )
                    },
                    title = if (blocked) "Unblock ${contact.displayName}" else "Block ${contact.displayName}",
                    titleColor = colors.error,
                    subtitle = if (blocked) "Allow messages from this contact again." else "Stop receiving messages from this contact.",
                    onClick = {
                        // Dart _toggleBlock: the confirm dialog only gates
                        // blocking; unblocking applies directly.
                        if (busy) return@InfoTile
                        if (blocked) {
                            scope.launch {
                                try {
                                    onSetBlocked(false)
                                } catch (error: Exception) {
                                    snackbar.showSnackbar("$error")
                                }
                            }
                        } else {
                            confirmAction = "block"
                        }
                    },
                )
                InfoTile(
                    icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = colors.error) },
                    title = "Delete Conversation",
                    titleColor = colors.error,
                    subtitle = "Remove this chat and all message history.",
                    onClick = { if (!busy) confirmAction = "deleteConversation" },
                )
                InfoTile(
                    icon = { Icon(Icons.Rounded.PersonRemove, contentDescription = null, tint = colors.error) },
                    title = "Delete Contact & Chat",
                    titleColor = colors.error,
                    subtitle = "Remove ${contact.displayName} and all conversations.",
                    onClick = { if (!busy) confirmAction = "deleteUser" },
                )
            }
            Spacer(Modifier.height(12.dp))

            // Shared Media & Files.
            SectionTitle("Shared Media & Files", colors)
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                if (media.isEmpty() && links.isEmpty() && documents.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No shared media, links or documents yet.",
                            color = colors.foregroundSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (media.isNotEmpty()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${media.size} Photos & Videos",
                            color = colors.foreground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.height(80.dp).horizontalScroll(rememberScrollState())) {
                            media.forEachIndexed { index, message ->
                                if (index > 0) Spacer(Modifier.width(8.dp))
                                val attachment = message.attachment!!
                                Column(
                                    Modifier
                                        .width(110.dp)
                                        .height(80.dp)
                                        .background(colors.surfaceSecondary, RoundedCornerShape(12.dp))
                                        .clickable { onOpenMedia(message) }
                                        .padding(8.dp),
                                ) {
                                    Icon(
                                        if (attachment.type == "video") Icons.Outlined.PlayCircle else Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        attachment.name,
                                        color = colors.foreground,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                links.take(5).forEach { link ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            link,
                            color = colors.primary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                documents.take(5).forEach { message ->
                    val attachment = message.attachment!!
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenMedia(message) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                attachment.name,
                                color = colors.foreground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(attachment.size, color = colors.foregroundSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Groups in Common (the Dart screen ships these as static entries).
            SectionTitle("Groups in Common", colors)
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                InfoTile(
                    icon = {
                        Box(Modifier.size(40.dp).background(colors.primary.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Groups, contentDescription = null, tint = colors.primary)
                        }
                    },
                    title = "Flutter Architects & Core Devs",
                    titleColor = colors.foreground,
                    subtitle = "You, ${contact.displayName}, Alex, Maya and 12 others",
                )
                InfoTile(
                    icon = {
                        Box(Modifier.size(40.dp).background(colors.secondary.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Palette, contentDescription = null, tint = colors.secondary)
                        }
                    },
                    title = "Design Systems & Motion Craft",
                    titleColor = colors.foreground,
                    subtitle = "You, ${contact.displayName}, and 4 others",
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ChatyConfirmDialog equivalents for block / delete conversation /
    // delete user, with the exact Dart copy.
    when (confirmAction) {
        "block" -> ConfirmDialog(
            title = "Block ${contact.displayName}?",
            message = "You will stop receiving new messages from this person, and they cannot message you until you unblock them.",
            confirmLabel = "Block",
            onConfirm = {
                confirmAction = null
                scope.launch {
                    try {
                        onSetBlocked(true)
                    } catch (error: Exception) {
                        snackbar.showSnackbar("$error")
                    }
                }
            },
            onDismiss = { confirmAction = null },
        )
        "deleteConversation" -> ConfirmDialog(
            title = "Delete chat with ${contact.displayName}?",
            message = "This removes this conversation and all message history.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmAction = null
                // Dart: deleteConversation is fire-and-forget, then pop to root.
                onDeleteConversation()
                onDeletedPopToRoot()
            },
            onDismiss = { confirmAction = null },
        )
        "deleteUser" -> ConfirmDialog(
            title = "Delete ${contact.displayName}?",
            message = "This removes ${contact.displayName} from your contacts, directory, and deletes the conversation.",
            confirmLabel = "Delete User",
            onConfirm = {
                confirmAction = null
                scope.launch {
                    try {
                        onDeleteUser()
                        onDeleteConversation()
                        onDeletedPopToRoot()
                    } catch (error: Exception) {
                        snackbar.showSnackbar("Failed to delete user: $error")
                    }
                }
            },
            onDismiss = { confirmAction = null },
        )
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onTap: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(colors.surfaceSecondary, RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = label, tint = colors.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = colors.foreground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SectionTitle(title: String, colors: com.example.chat.ui.theme.AppColors) {
    Text(
        title,
        color = colors.foregroundSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
internal fun InfoTile(
    icon: @Composable () -> Unit,
    title: String,
    titleColor: Color,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp)
        }
        if (trailing != null) trailing()
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.foreground) },
        text = { Text(message, color = colors.foregroundSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = colors.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.foregroundSecondary)
            }
        },
    )
}
