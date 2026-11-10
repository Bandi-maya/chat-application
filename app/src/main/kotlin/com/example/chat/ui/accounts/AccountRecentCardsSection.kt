package com.example.chat.ui.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.data.api.AccountRecentChatSummary
import com.example.chat.data.api.ApiRegisteredAccountMeta
import com.example.chat.ui.chats.home.HomeAvatar
import com.example.chat.ui.chats.home.HomeCountBadge
import com.example.chat.ui.chats.home.parseAvatarColorHex
import com.example.chat.ui.theme.LocalAppColors

/**
 * Account-specific recent conversation cards. Every card strictly renders only that
 * account's identity, persisted navigation/selection context, unread total, and synced
 * conversation list (never synthetic demo items or cross-account collisions).
 */
@Composable
fun AccountRecentCardsSection(
    accounts: List<ApiRegisteredAccountMeta>,
    activeAccountKey: String?,
    recentByAccount: Map<String, List<AccountRecentChatSummary>>,
    selectedConversationByAccount: Map<String, String?>,
    navContextByAccount: Map<String, String?>,
    theme: ThemeConfig,
    busy: Boolean,
    onMoveAccountCard: (accountKey: String, delta: Int) -> Unit,
    onOpenRecentConversation: (accountKey: String, summary: AccountRecentChatSummary) -> Unit,
    onReauthenticateAccount: (ApiRegisteredAccountMeta) -> Unit,
) {
    val colors = LocalAppColors.current
    if (accounts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Sign in to view account-specific recent conversations.",
                color = colors.foregroundSecondary,
                fontSize = 13.5.sp,
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        accounts.forEachIndexed { index, meta ->
            val isActive = meta.accountKey == activeAccountKey
            val recents = recentByAccount[meta.accountKey].orEmpty()
            val selectedConvId = selectedConversationByAccount[meta.accountKey]
            val navContext = navContextByAccount[meta.accountKey]

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (isActive) {
                    Color(argbWithAlpha(theme.accentColor, if (theme.isDark) 0.10 else 0.06))
                } else {
                    colors.surfaceSecondary
                },
                border = BorderStroke(
                    width = if (isActive) 1.5.dp else 1.dp,
                    color = if (isActive) Color(theme.accentColor) else colors.border,
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomeAvatar(
                            initials = meta.avatarInitials,
                            color = parseAvatarColorHex(meta.avatarColorHex, Color(theme.accentColor)),
                            size = 36f,
                            shape = "circle",
                            url = meta.avatarUrl,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    meta.displayName,
                                    color = colors.foreground,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (isActive) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Active",
                                        color = Color(theme.onAccentColor),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(Color(theme.accentColor), CircleShape)
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "@${meta.username}",
                                    color = colors.foregroundSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    meta.tenantId,
                                    color = colors.foregroundSecondary,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .background(colors.borderSubtle, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                                if (!navContext.isNullOrBlank()) {
                                    Text(
                                        navContext.replaceFirstChar { it.uppercase() },
                                        color = Color(theme.accentColor),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .background(Color(argbWithAlpha(theme.accentColor, 0.14)), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                        if (meta.unreadMessagesCount > 0) {
                            HomeCountBadge(
                                count = meta.unreadMessagesCount,
                                color = Color(theme.accentColor),
                                textColor = Color(theme.onAccentColor),
                            )
                        }
                        if (accounts.size > 1) {
                            IconButton(
                                onClick = { onMoveAccountCard(meta.accountKey, -1) },
                                enabled = !busy && index > 0,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.ArrowUpward,
                                    contentDescription = "Move ${meta.displayName} card earlier",
                                    tint = if (index > 0) colors.foregroundSecondary else colors.disabledForeground,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { onMoveAccountCard(meta.accountKey, +1) },
                                enabled = !busy && index < accounts.lastIndex,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.ArrowDownward,
                                    contentDescription = "Move ${meta.displayName} card later",
                                    tint = if (index < accounts.lastIndex) colors.foregroundSecondary else colors.disabledForeground,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    when {
                        meta.sessionExpired -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(argbWithAlpha(theme.dangerColor, 0.12)),
                                border = BorderStroke(1.dp, Color(argbWithAlpha(theme.dangerColor, 0.35))),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = !busy, role = Role.Button) { onReauthenticateAccount(meta) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "Session expired for this account.",
                                        color = Color(theme.dangerColor),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "Sign in again",
                                        color = Color(theme.accentColor),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        recents.isEmpty() -> {
                            Text(
                                "No synced conversations yet.",
                                color = colors.foregroundSecondary,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                recents.take(4).forEach { item ->
                                    val isSelected = item.conversationId == selectedConvId
                                    val safeSummaryLabel = buildString {
                                        append("Open ").append(item.title).append(" in ").append(meta.displayName)
                                        if (item.unreadCount > 0) append(", ").append(item.unreadCount).append(" unread")
                                        if (isSelected) append(", currently selected")
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) Color(argbWithAlpha(theme.accentColor, 0.14))
                                                else colors.surface.copy(alpha = if (theme.isDark) 0.55f else 0.9f),
                                            )
                                            .border(
                                                width = if (isSelected) 1.dp else 0.5.dp,
                                                color = if (isSelected) Color(theme.accentColor) else colors.borderSubtle,
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .clearAndSetSemantics { contentDescription = safeSummaryLabel }
                                            .clickable(enabled = !busy, role = Role.Button) {
                                                onOpenRecentConversation(meta.accountKey, item)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        HomeAvatar(
                                            initials = item.avatarInitials,
                                            color = parseAvatarColorHex(item.avatarColorHex, Color(theme.accentColor)),
                                            size = 32f,
                                            shape = "circle",
                                            url = item.avatarUrl,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                item.title,
                                                color = colors.foreground,
                                                fontSize = 13.5.sp,
                                                fontWeight = if (item.unreadCount > 0 || isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    if (item.typeName == "group") "Group" else "Direct",
                                                    color = colors.foregroundSecondary,
                                                    fontSize = 11.sp,
                                                )
                                                if (item.hasDraft) {
                                                    Text(
                                                        "Draft",
                                                        color = Color(theme.accentColor),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                }
                                                if (isSelected) {
                                                    Text(
                                                        "Last open",
                                                        color = Color(theme.accentColor),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                    )
                                                }
                                            }
                                        }
                                        if (item.isMuted) {
                                            Icon(
                                                Icons.Outlined.NotificationsOff,
                                                contentDescription = null,
                                                tint = colors.foregroundSecondary,
                                                modifier = Modifier.size(15.dp).padding(end = 4.dp),
                                            )
                                        }
                                        if (item.unreadCount > 0) {
                                            HomeCountBadge(
                                                count = item.unreadCount,
                                                color = if (item.isMuted) colors.foregroundSecondary else Color(theme.accentColor),
                                                textColor = Color(theme.onAccentColor),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
