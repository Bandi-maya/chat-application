package com.example.chat.ui.chats.group_info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.OutlinedTextField
import com.example.chat.core.groups.GroupMemberPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Group details from the current conversation. Membership and security actions stay
 * unavailable until their backend contracts exist; navigation is not leaving a group. */
@Composable
fun GroupInfoScreen(
    conversation: Conversation,
    participants: List<UserProfile>,
    currentUserId: String,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    messages: List<com.example.chat.domain.models.ChatMessage> = emptyList(),
    onOpenMedia: (com.example.chat.domain.models.ChatMessage) -> Unit = {},
    onSearch: () -> Unit = {},
) {
    var showAllMedia by remember(conversation.id, currentUserId) { mutableStateOf(false) }
    if (showAllMedia) com.example.chat.ui.chats.contact_info.ConversationMediaDialog(messages, onOpenMedia, onClose = { showAllMedia = false })
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var memberQuery by remember(conversation.id, currentUserId) { mutableStateOf("") }
    val memberCount = GroupMemberPolicy.knownCount(conversation.participantIds)
    val loadedMembers = GroupMemberPolicy.members(conversation.participantIds, participants, conversation.adminIds, currentUserId, "")
    val visibleMembers = GroupMemberPolicy.members(conversation.participantIds, participants, conversation.adminIds, currentUserId, memberQuery)

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = colors.foreground)
                }
                Text(
                    "Group Info",
                    color = colors.foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    scope.launch { snackbar.showSnackbar("Group editing is not available in this build.") }
                }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit group info", tint = colors.foreground)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Group header card.
            Column(
                Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(68.dp).background(
                        Color(com.example.chat.ui.chats.contact_info.parseAvatarColorArgb(conversation.avatarColorHex, LocalThemeConfig.current.accentColor)),
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        conversation.avatarInitials ?: "GP",
                        color = colors.onPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    conversation.title,
                    color = colors.foreground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$memberCount known members",
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(16.dp))

            androidx.compose.material3.TextButton(onClick = onSearch) { Text("Search conversation") }
            androidx.compose.material3.TextButton(onClick = { showAllMedia = true }) { Text("All media, documents and links") }
            com.example.chat.ui.chats.contact_info.SectionTitle("Group Security & Settings", colors)
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                com.example.chat.ui.chats.contact_info.InfoTile(
                    icon = { Icon(Icons.Outlined.Timer, contentDescription = null, tint = colors.warning) },
                    title = "Disappearing Messages",
                    titleColor = colors.foreground,
                    subtitle = "Settings unavailable in this build",
                    trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.foregroundSecondary) },
                    onClick = { scope.launch { snackbar.showSnackbar("Disappearing message settings are not available. No setting was changed.") } },
                )
                com.example.chat.ui.chats.contact_info.InfoTile(
                    icon = { Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = colors.success) },
                    title = "Encryption Verification",
                    titleColor = colors.foreground,
                    subtitle = "Encryption has not been verified",
                    trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.foregroundSecondary) },
                    onClick = { scope.launch { snackbar.showSnackbar("No verified device-to-device safety number is available for this group.") } },
                )
            }
            Spacer(Modifier.height(12.dp))

            com.example.chat.ui.chats.contact_info.SectionTitle("Members (${loadedMembers.size} loaded / $memberCount known)", colors)
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                com.example.chat.ui.chats.contact_info.InfoTile(
                    icon = {
                        Box(
                            Modifier.background(colors.primary.copy(alpha = 0.12f), CircleShape).padding(8.dp),
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        }
                    },
                    title = "Add Member",
                    titleColor = colors.primary,
                    subtitle = "Invites are not available in this build",
                    onClick = {
                        scope.launch { snackbar.showSnackbar("No invite was created. Group invites are not available in this build.") }
                    },
                )
                OutlinedTextField(value = memberQuery, onValueChange = { memberQuery = it.take(200) },
                    label = { Text("Search members") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(12.dp))
                if (loadedMembers.size < memberCount) Text("Some member profiles are not loaded. Search covers loaded profiles only.",
                    color = colors.foregroundSecondary, modifier = Modifier.padding(12.dp))
                if (visibleMembers.isEmpty()) Text(if (memberQuery.isBlank()) "No member profiles available" else "No matching loaded members",
                    color = colors.foregroundSecondary, modifier = Modifier.padding(12.dp))
                visibleMembers.forEach { p ->
                    val isAdmin = conversation.adminIds.contains(p.id)
                    val isMe = p.id == currentUserId
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp).background(
                                Color(com.example.chat.ui.chats.contact_info.parseAvatarColorArgb(p.avatarColorHex, LocalThemeConfig.current.accentColor)),
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                p.avatarInitials,
                                color = colors.onPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isMe) "${p.displayName} (You)" else p.displayName,
                                    color = colors.foreground,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                )
                                if (isAdmin) {
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        Modifier
                                            .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text("Admin", color = colors.primary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (p.username.isNotBlank()) Text("@${p.username}", color = colors.foregroundSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { snackbar.showSnackbar("You are still a member. Leaving groups is not available in this build.") } }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, tint = colors.error)
                    Spacer(Modifier.width(12.dp))
                    Text("Leave Group", color = colors.error, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
