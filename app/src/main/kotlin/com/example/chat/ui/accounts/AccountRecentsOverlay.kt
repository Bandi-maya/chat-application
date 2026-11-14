package com.example.chat.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.data.api.AccountRecentChatSummary
import com.example.chat.data.api.ApiRegisteredAccountMeta
import com.example.chat.ui.chats.home.HomeAvatar
import com.example.chat.ui.chats.home.HomeCountBadge
import com.example.chat.ui.chats.home.parseAvatarColorHex
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.theme.LocalAppColors

/**
 * Recents-style full-screen account switcher, matching the approved preview:
 * opaque theme backdrop, an avatar + name label above each card, large rounded
 * mini-snapshot cards of each account's home screen in a horizontally
 * swipeable pager where the next card peeks in from the right edge, a trailing
 * "+ Add account" card, and a wide "Manage accounts" pill near the bottom that
 * opens the classic account switcher. All geometry is proportional to the
 * viewport so it follows the reference layout at any screen size.
 */
@Composable
fun AccountRecentsOverlay(
    visible: Boolean,
    accounts: List<ApiRegisteredAccountMeta>,
    activeAccountKey: String?,
    recentByAccount: Map<String, List<AccountRecentChatSummary>>,
    theme: ThemeConfig,
    isSwitching: Boolean,
    errorMessage: String?,
    onSelectAccount: (ApiRegisteredAccountMeta) -> Unit,
    onAddAccount: () -> Unit,
    onManageAccounts: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val colors = LocalAppColors.current
    val pages = remember(accounts) { AccountRecentsPages.build(accounts) }
    val startPage = AccountRecentsPages.initialPage(accounts, activeAccountKey)
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { pages.size })
    LaunchedEffect(startPage, pages.size) {
        if (pages.isNotEmpty() && pagerState.currentPage != startPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(startPage)
        }
    }

    BackHandler(enabled = !isSwitching) { onDismiss() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = maxWidth
        val screenH = maxHeight
        // Reference proportions measured from the approved preview
        // (720x1600 reference mapped onto the device viewport).
        val cardWidth = screenW * 0.875f
        val cardStartPad = screenW * 0.083f
        val cardSpacing = screenW * -0.525f
        val cardHeight = screenH * 0.597f
        val cardShape = RoundedCornerShape(16.dp)

        // Approved plain backdrop: never expose home content behind account cards.
        // Outside taps still dismiss, like the classic overlay.
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!isSwitching) onDismiss() },
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(screenH * 0.085f))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                pageSize = PageSize.Fixed(cardWidth),
                pageSpacing = cardSpacing,
                contentPadding = PaddingValues(
                    start = cardStartPad,
                    end = screenW - cardStartPad - cardWidth,
                ),
                verticalAlignment = Alignment.Top,
                userScrollEnabled = !isSwitching,
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column(Modifier.width(cardWidth)) {
                    AccountRecentsLabel(page)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .height(cardHeight)
                            .fillMaxWidth()
                            .shadow(10.dp, cardShape, clip = false)
                            .clip(cardShape)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, cardShape)
                            .clickable(enabled = !isSwitching) {
                                when {
                                    page.kind == AccountRecentsPageKind.AddAccount -> onAddAccount()
                                    page.account != null && page.account.accountKey != activeAccountKey ->
                                        onSelectAccount(page.account)
                                    else -> onDismiss()
                                }
                            },
                    ) {
                        if (page.kind == AccountRecentsPageKind.AddAccount) {
                            AddAccountSnapshot()
                        } else if (page.account != null) {
                            AccountHomeSnapshot(
                                meta = page.account,
                                recents = recentByAccount[page.account.accountKey].orEmpty(),
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    errorMessage,
                    color = colors.error,
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenW * 0.092f),
                )
            }

            Spacer(Modifier.weight(1f))

            // "Manage accounts" pill in the reference's Refresh position.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = screenW * 0.092f)
                    .shadow(8.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(colors.input)
                    .clickable(enabled = !isSwitching, onClick = onManageAccounts)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChatyIcon(
                        ChatyIconId.profile,
                        size = 20.dp,
                        color = colors.foreground,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Manage accounts",
                        color = colors.foreground,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(screenH * 0.086f))
        }
    }
}

/** Small label above each card: account avatar + name, or plus + "Add account". */
@Composable
private fun AccountRecentsLabel(page: AccountRecentsPage) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(34.dp)
            .padding(start = 22.dp),
    ) {
        if (page.kind == AccountRecentsPageKind.AddAccount || page.account == null) {
            Box(
                Modifier
                    .size(22.dp)
                    .border(1.5.dp, colors.foreground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ChatyIcon(ChatyIconId.add, size = 13.dp, color = colors.foreground)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Add account",
                color = colors.foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            val meta = page.account
            HomeAvatar(
                initials = meta.avatarInitials,
                color = parseAvatarColorHex(meta.avatarColorHex, colors.accent),
                size = 22f,
                shape = "circle",
                url = meta.avatarUrl,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                meta.displayName,
                color = colors.foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Mini snapshot of an account's home screen: mini app bar, filter chips and
 * the account's real recent chats, drawn from the same snapshot data the
 * classic account switcher already loads. Purely presentational.
 */
@Composable
private fun AccountHomeSnapshot(
    meta: ApiRegisteredAccountMeta,
    recents: List<AccountRecentChatSummary>,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize()) {
        // Mini app bar.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                "Chaty",
                color = colors.foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            HomeAvatar(
                initials = meta.avatarInitials,
                color = parseAvatarColorHex(meta.avatarColorHex, colors.accent),
                size = 24f,
                shape = "circle",
                url = meta.avatarUrl,
            )
        }

        // Filter chips, mirroring the home filter row (non-interactive).
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            listOf("All", "Unread", "Groups", "Direct").forEachIndexed { index, label ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (index == 0) colors.hover else colors.input)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        label,
                        color = if (index == 0) colors.accent else colors.foregroundSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        if (recents.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No recent chats yet",
                    color = colors.foregroundSecondary,
                    fontSize = 12.5.sp,
                )
            }
        } else {
            Column(Modifier.padding(horizontal = 14.dp)) {
                recents.take(5).forEach { summary ->
                    RecentChatSnapshotRow(summary)
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.weight(1f))
        }

        if (meta.sessionExpired) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                ChatyIcon(ChatyIconId.warning, size = 14.dp, color = colors.error)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Session expired",
                    color = colors.error,
                    fontSize = 11.5.sp,
                )
            }
        }
    }
}

@Composable
private fun RecentChatSnapshotRow(summary: AccountRecentChatSummary) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        HomeAvatar(
            initials = summary.avatarInitials,
            color = parseAvatarColorHex(summary.avatarColorHex, colors.accent),
            size = 34f,
            shape = "circle",
            url = summary.avatarUrl,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                summary.title,
                color = colors.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                summary.hasDraft -> "Draft saved"
                summary.isMuted -> "Muted"
                else -> if (summary.typeName == "group") "Group" else "Direct chat"
            }
            Text(
                subtitle,
                color = colors.foregroundSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (summary.unreadCount > 0) {
            HomeCountBadge(
                count = summary.unreadCount,
                color = colors.accent,
                textColor = colors.onAccent,
            )
        } else if (summary.isMuted) {
            ChatyIcon(ChatyIconId.mute, size = 14.dp, color = colors.foregroundSecondary)
        }
    }
}

/** The "+ Add account" card: mini app bar plus a large circled plus. */
@Composable
private fun AddAccountSnapshot() {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                "Chaty",
                color = colors.foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(88.dp)
                        .border(2.5.dp, colors.foreground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ChatyIcon(ChatyIconId.add, size = 40.dp, color = colors.foreground)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Add account",
                    color = colors.foreground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
