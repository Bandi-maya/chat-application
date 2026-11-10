package com.example.chat.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.data.api.AccountRecentChatSummary
import com.example.chat.data.api.ApiRegisteredAccountMeta
import com.example.chat.data.api.MultiAccountRegistryCoordinator
import com.example.chat.ui.chats.home.HomeAvatar
import com.example.chat.ui.chats.home.HomeCountBadge
import com.example.chat.ui.chats.home.parseAvatarColorHex
import com.example.chat.ui.kit.ChatyConfirmationDialog
import com.example.chat.ui.kit.LocalReferenceLayoutPresentation
import com.example.chat.ui.theme.LocalAppColors

enum class AccountSwitcherTab { Accounts, RecentByAccount }

/**
 * Floating rounded-card account switcher inspired by the reference dark/bordered card design.
 * Adapts to live window bounds (`BoxWithConstraints` + safe/IME insets), supports single-account
 * + Add Account card layout, multi-account responsive card grid, session-expired/loading/error/empty
 * states, account-specific recent cards, and intentionally includes NO Refresh button at the bottom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountSwitcherOverlay(
    visible: Boolean,
    accounts: List<ApiRegisteredAccountMeta>,
    activeAccountKey: String?,
    recentByAccount: Map<String, List<AccountRecentChatSummary>>,
    selectedConversationByAccount: Map<String, String?>,
    navContextByAccount: Map<String, String?>,
    theme: ThemeConfig,
    isSwitching: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onSelectAccount: (ApiRegisteredAccountMeta) -> Unit,
    onAddAccount: () -> Unit,
    onReauthenticateAccount: (ApiRegisteredAccountMeta) -> Unit,
    onSignOutAccount: (ApiRegisteredAccountMeta) -> Unit,
    onOpenSettings: () -> Unit,
    onMoveAccountCard: (accountKey: String, delta: Int) -> Unit,
    onOpenRecentConversation: (accountKey: String, summary: AccountRecentChatSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val colors = LocalAppColors.current
    val animationsDisabled = LocalReferenceLayoutPresentation.current.animationsDisabled
    val enterTransition = if (animationsDisabled) {
        fadeIn(tween(80))
    } else {
        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
            scaleIn(tween(240, easing = FastOutSlowInEasing), initialScale = 0.94f, transformOrigin = TransformOrigin(0.85f, 0.05f))
    }
    val exitTransition = if (animationsDisabled) {
        fadeOut(tween(60))
    } else {
        fadeOut(tween(170)) + scaleOut(tween(170), targetScale = 0.96f, transformOrigin = TransformOrigin(0.85f, 0.05f))
    }

    var activeTab by remember { mutableStateOf(AccountSwitcherTab.Accounts) }
    var signOutTarget by remember { mutableStateOf<ApiRegisteredAccountMeta?>(null) }

    BackHandler(enabled = !isSwitching) { onDismiss() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.foreground.copy(alpha = 0.42f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isSwitching) onDismiss() },
            )
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val wideLayout = maxWidth >= 520.dp
        val maxDialogWidth = if (maxWidth >= 720.dp) 640.dp else 540.dp
        val maxDialogHeight = (maxHeight - 12.dp).coerceAtLeast(180.dp)
        val surfaceColor = androidx.compose.ui.graphics.lerp(colors.surfaceElevated, colors.primary, 0.03f)

        AnimatedVisibility(
            visible = visible,
            enter = enterTransition,
            exit = exitTransition,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = maxDialogWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .semantics { paneTitle = "Account switcher" },
                shape = RoundedCornerShape(24.dp),
                color = surfaceColor,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                    // Header bar (title, settings icon shortcut, close icon)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Accounts",
                                color = colors.foreground,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (accounts.size <= 1) "Switch workspace or add another account"
                                else "${accounts.size} signed-in accounts on this device",
                                color = colors.foregroundSecondary,
                                fontSize = 12.5.sp,
                            )
                        }
                        IconButton(
                            onClick = {
                                onDismiss()
                                onOpenSettings()
                            },
                            enabled = !isSwitching,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = colors.foreground,
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            enabled = !isSwitching,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Close account switcher",
                                tint = colors.foreground,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Tab pill: Accounts vs Account-Specific Recent Cards
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceSecondary, RoundedCornerShape(14.dp))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SwitcherTabPill(
                            label = "Accounts (${accounts.size})",
                            selected = activeTab == AccountSwitcherTab.Accounts,
                            theme = theme,
                            onClick = { activeTab = AccountSwitcherTab.Accounts },
                            modifier = Modifier.weight(1f),
                        )
                        SwitcherTabPill(
                            label = "Recent cards",
                            selected = activeTab == AccountSwitcherTab.RecentByAccount,
                            theme = theme,
                            onClick = { activeTab = AccountSwitcherTab.RecentByAccount },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(argbWithAlpha(theme.dangerColor, 0.12)),
                            border = BorderStroke(1.dp, Color(argbWithAlpha(theme.dangerColor, 0.35))),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(theme.dangerColor),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    errorMessage,
                                    color = Color(theme.dangerColor),
                                    fontSize = 12.5.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onDismissError) {
                                    Text("Dismiss", color = Color(theme.dangerColor), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (isSwitching) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(argbWithAlpha(theme.accentColor, 0.10)), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(theme.accentColor),
                            )
                            Text(
                                "Switching account safely...",
                                color = colors.foreground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Scrollable cards body (Zero Refresh button in footer!)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        when (activeTab) {
                            AccountSwitcherTab.Accounts -> {
                                val canAddMore = accounts.size < MultiAccountRegistryCoordinator.DEFAULT_MAX_ACCOUNTS
                                if (accounts.isEmpty()) {
                                    EmptyAccountsCard(theme = theme, onAddAccount = onAddAccount)
                                } else if (accounts.size == 1 && wideLayout) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        AccountCard(
                                            meta = accounts.first(),
                                            isActive = accounts.first().accountKey == activeAccountKey,
                                            theme = theme,
                                            busy = isSwitching,
                                            onSelect = { onSelectAccount(accounts.first()) },
                                            onReauthenticate = { onReauthenticateAccount(accounts.first()) },
                                            onRequestSignOut = { signOutTarget = accounts.first() },
                                            modifier = Modifier.weight(1f),
                                        )
                                        AddAccountCard(
                                            theme = theme,
                                            enabled = !isSwitching,
                                            onClick = onAddAccount,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                } else {
                                    val columns = when {
                                        this@BoxWithConstraints.maxWidth >= 680.dp -> 2
                                        wideLayout -> 2
                                        else -> 1
                                    }
                                    val rows = (accounts + listOfNotNull(if (canAddMore) null else null))
                                    if (columns == 1) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            accounts.forEach { meta ->
                                                AccountCard(
                                                    meta = meta,
                                                    isActive = meta.accountKey == activeAccountKey,
                                                    theme = theme,
                                                    busy = isSwitching,
                                                    onSelect = { onSelectAccount(meta) },
                                                    onReauthenticate = { onReauthenticateAccount(meta) },
                                                    onRequestSignOut = { signOutTarget = meta },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                            if (canAddMore) {
                                                AddAccountCard(
                                                    theme = theme,
                                                    enabled = !isSwitching,
                                                    onClick = onAddAccount,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                    } else {
                                        val chunked = accounts.chunked(columns)
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            chunked.forEach { rowItems ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                ) {
                                                    rowItems.forEach { meta ->
                                                        AccountCard(
                                                            meta = meta,
                                                            isActive = meta.accountKey == activeAccountKey,
                                                            theme = theme,
                                                            busy = isSwitching,
                                                            onSelect = { onSelectAccount(meta) },
                                                            onReauthenticate = { onReauthenticateAccount(meta) },
                                                            onRequestSignOut = { signOutTarget = meta },
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                    }
                                                    if (rowItems.size < columns && canAddMore) {
                                                        AddAccountCard(
                                                            theme = theme,
                                                            enabled = !isSwitching,
                                                            onClick = onAddAccount,
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                    } else if (rowItems.size < columns) {
                                                        Spacer(Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                            if (canAddMore && accounts.size % columns == 0) {
                                                AddAccountCard(
                                                    theme = theme,
                                                    enabled = !isSwitching,
                                                    onClick = onAddAccount,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                    }
                                    if (!canAddMore) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Account limit (${MultiAccountRegistryCoordinator.DEFAULT_MAX_ACCOUNTS}) reached. Sign out of an account to add another.",
                                            color = colors.foregroundSecondary,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }

                            AccountSwitcherTab.RecentByAccount -> {
                                AccountRecentCardsSection(
                                    accounts = accounts,
                                    activeAccountKey = activeAccountKey,
                                    recentByAccount = recentByAccount,
                                    selectedConversationByAccount = selectedConversationByAccount,
                                    navContextByAccount = navContextByAccount,
                                    theme = theme,
                                    busy = isSwitching,
                                    onMoveAccountCard = onMoveAccountCard,
                                    onOpenRecentConversation = onOpenRecentConversation,
                                    onReauthenticateAccount = onReauthenticateAccount,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    signOutTarget?.let { target ->
        ChatyConfirmationDialog(
            onDismissRequest = { signOutTarget = null },
            title = { Text("Sign out ${target.displayName}?") },
            text = {
                Text(
                    if (target.accountKey == activeAccountKey && accounts.size > 1) {
                        "This removes ${target.displayName}'s session from this device and switches to your next available account."
                    } else {
                        "This removes ${target.displayName}'s credentials and local message cache access for this device."
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { signOutTarget = null }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val confirmed = target
                        signOutTarget = null
                        onSignOutAccount(confirmed)
                    },
                ) {
                    Text("Sign out", color = Color(theme.dangerColor), fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun SwitcherTabPill(
    label: String,
    selected: Boolean,
    theme: ThemeConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(theme.accentColor) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color(theme.onAccentColor) else colors.foregroundSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountCard(
    meta: ApiRegisteredAccountMeta,
    isActive: Boolean,
    theme: ThemeConfig,
    busy: Boolean,
    onSelect: () -> Unit,
    onReauthenticate: () -> Unit,
    onRequestSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val shortId = if (meta.userId.length > 6) "...${meta.userId.takeLast(6)}" else meta.userId
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !busy && !isActive, role = Role.Button) {
                if (meta.sessionExpired) onReauthenticate() else onSelect()
            },
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) {
            Color(argbWithAlpha(theme.accentColor, if (theme.isDark) 0.14 else 0.08))
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
                Box(
                    modifier = if (isActive) {
                        Modifier
                            .border(2.dp, Color(theme.accentColor), CircleShape)
                            .padding(2.dp)
                    } else Modifier,
                ) {
                    HomeAvatar(
                        initials = meta.avatarInitials,
                        color = parseAvatarColorHex(meta.avatarColorHex, Color(theme.accentColor)),
                        size = 42f,
                        shape = "circle",
                        url = meta.avatarUrl,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        meta.displayName,
                        color = colors.foreground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "@${meta.username}",
                        color = colors.foregroundSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta.unreadMessagesCount > 0) {
                    HomeCountBadge(
                        count = meta.unreadMessagesCount,
                        color = Color(theme.accentColor),
                        textColor = Color(theme.onAccentColor),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onRequestSignOut,
                    enabled = !busy,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Outlined.Logout,
                        contentDescription = "Sign out ${meta.displayName}",
                        tint = colors.foregroundSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Workspace: ${meta.tenantId}",
                    color = colors.foregroundSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(colors.borderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
                Text(
                    "ID: $shortId",
                    color = colors.foregroundSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(colors.borderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
                if (isActive && !meta.sessionExpired) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(argbWithAlpha(theme.accentColor, 0.16)), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color(theme.accentColor),
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Active",
                            color = Color(theme.accentColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (meta.sessionExpired) {
                    Text(
                        "Session expired",
                        color = Color(theme.dangerColor),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color(argbWithAlpha(theme.dangerColor, 0.14)), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            if (!isActive || meta.sessionExpired) {
                Spacer(Modifier.height(10.dp))
                if (meta.sessionExpired) {
                    Button(
                        onClick = onReauthenticate,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(theme.dangerColor),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Sign in again", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onSelect,
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(theme.accentColor)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Switch account", color = Color(theme.accentColor), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddAccountCard(
    theme: ThemeConfig,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceSecondary,
        border = BorderStroke(1.dp, Color(argbWithAlpha(theme.accentColor, 0.45))),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).heightIn(min = 108.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(argbWithAlpha(theme.accentColor, 0.15)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = Color(theme.accentColor),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Add Account",
                color = colors.foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Sign in to another profile or workspace",
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun EmptyAccountsCard(
    theme: ThemeConfig,
    onAddAccount: () -> Unit,
) {
    val colors = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceSecondary,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "No signed-in accounts stored",
                color = colors.foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Add a real Chaty account to switch workspaces seamlessly.",
                color = colors.foregroundSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAddAccount,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Add Account", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
