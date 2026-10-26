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

/** Port of group_info_screen.dart (api branch anchor 3bee780): group header,
 *  security/settings rows (static in Dart), member list with Admin badges,
 *  Add Member mock invite, and Leave Group. Dart pops twice and shows a
 *  'Left group.' snackbar via the app-level ScaffoldMessenger with no
 *  backend call, so [onLeave] must pop Group Info + the chat and surface
 *  that snackbar from the surviving destination. */
@Composable
fun GroupInfoScreen(
    conversation: Conversation,
    participants: List<UserProfile>,
    currentUserId: String,
    onLeave: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

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
                    scope.launch { snackbar.showSnackbar("Edit group name / avatar modal") }
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
                    "${conversation.participantIds.size} Members • End-to-End Encrypted",
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(16.dp))

            com.example.chat.ui.chats.contact_info.SectionTitle("Group Security & Settings", colors)
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                com.example.chat.ui.chats.contact_info.InfoTile(
                    icon = { Icon(Icons.Outlined.Timer, contentDescription = null, tint = colors.warning) },
                    title = "Disappearing Messages",
                    titleColor = colors.foreground,
                    subtitle = "7 days active",
                    trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.foregroundSecondary) },
                    onClick = {},
                )
                com.example.chat.ui.chats.contact_info.InfoTile(
                    icon = { Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = colors.success) },
                    title = "Encryption Verification",
                    titleColor = colors.foreground,
                    subtitle = "Group prekeys authenticated",
                    trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.foregroundSecondary) },
                    onClick = {},
                )
            }
            Spacer(Modifier.height(12.dp))

            com.example.chat.ui.chats.contact_info.SectionTitle("${participants.size} Members", colors)
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
                    subtitle = "Invite via username or direct prekey share",
                    onClick = {
                        scope.launch { snackbar.showSnackbar("Mock invite link copied to clipboard.") }
                    },
                )
                participants.forEach { p ->
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
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isMe) "${p.displayName} (You)" else p.displayName,
                                    color = colors.foreground,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
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
                            Text("@${p.username}", color = colors.foregroundSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onLeave() }
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
