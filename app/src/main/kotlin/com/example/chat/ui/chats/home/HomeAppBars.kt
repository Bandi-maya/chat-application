@file:OptIn(ExperimentalFoundationApi::class)
package com.example.chat.ui.chats.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.ScheduleSend
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WorkspacePremium
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.HomePreferences
import androidx.compose.foundation.ExperimentalFoundationApi

/** Identity shown in the home header (mapped by the integrator from whatever
 *  user object the active backend plane exposes). */
data class HomeIdentity(
    val displayName: String = "",
    val username: String = "",
    val about: String = "",
    val avatarInitials: String = "C",
    val avatarColorHex: String? = null,
    val avatarUrl: String? = null,
)

/** Navigation callbacks for destinations that live outside this slice; the
 *  integrator wires each to a real route or an honest WIP stub. */
data class ChatsHomeNav(
    val onOpenProfile: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenSecuritySettings: () -> Unit = {},
    val onOpenGlobalSearch: () -> Unit = {},
    val onOpenQr: () -> Unit = {},
    val onOpenLinkedDevices: () -> Unit = {},
    val onOpenNewChat: (groupMode: Boolean, broadcastMode: Boolean) -> Unit = { _, _ -> },
    val onOpenStarredMessages: () -> Unit = {},
    val onOpenMessageManagement: () -> Unit = {},
    val onOpenLockedVault: () -> Unit = {},
    val onOpenCameraEffects: () -> Unit = {},
    val onMessageNumber: (String) -> Unit = {},
    val onWhoCanCallMe: () -> Unit = {},
    val onShareApp: () -> Unit = {},
    val onRestartApp: () -> Unit = {},
    val onToggleBrightness: () -> Unit = {},
    val onSelectPlan: () -> Unit = {},
)

/** _ghostModePill: toggles ghost mode with the exact Dart snackbar copy. */
@Composable
fun GhostModePill(
    theme: ThemeConfig,
    isGhost: Boolean,
    onToggle: (Boolean) -> Unit,
    onSnack: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(start = 6.dp)
            .background(
                if (isGhost) Color(0x336366F1) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (isGhost) Color(0x806366F1) else Color(argbWithAlpha(theme.secondaryTextColor, 0.2)),
                RoundedCornerShape(12.dp),
            )
            .clickable {
                onToggle(!isGhost)
                onSnack(
                    if (!isGhost) {
                        "Ghost Mode Active: Last seen frozen & receipts suppressed"
                    } else {
                        "Ghost Mode Deactivated: Normal presence restored"
                    },
                )
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeGlyphIcon(
            glyph = if (isGhost) HomeGlyph.ghostActive else HomeGlyph.ghostInactive,
            size = 14.dp,
            color = if (isGhost) Color(0xFF818CF8) else Color(theme.secondaryTextColor),
        )
        if (isGhost) {
            Spacer(Modifier.width(5.dp))
            Text(
                "Ghost",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF818CF8),
            )
        }
    }
}

/** _standardAppBar: identity block (tap = locked-chats entry, long-press =
 *  vault), status line, ghost pill, header extras slot, camera / QR / search
 *  / add-account icons, ringed profile avatar (opens Settings, as Dart),
 *  brightness toggle and the canonical overflow button. */
@Composable
fun StandardHomeAppBar(
    theme: ThemeConfig,
    homePrefs: HomePreferences,
    headerStyle: HomeHeaderStyle,
    homeStyle: String,
    title: String,
    identity: HomeIdentity,
    isSearchOpen: Boolean,
    forcedTypeIsSet: Boolean,
    nav: ChatsHomeNav,
    onToggleSearch: () -> Unit,
    onToggleGhost: (Boolean) -> Unit,
    onIdentityTap: () -> Unit,
    onIdentityLongPress: () -> Unit,
    onOpenOverflowMenu: () -> Unit,
    onSnack: (String) -> Unit,
    headerExtras: (@Composable () -> Unit)? = null,
) {
    val titleFontSize = when (headerStyle) {
        HomeHeaderStyle.compact -> 19.0
        HomeHeaderStyle.prominentIdentity -> 24.0
        HomeHeaderStyle.searchForward -> 19.0
        HomeHeaderStyle.storiesFirst -> 22.0
    }
    val effectiveShowSearch = homePrefs.showSearchBar && !homePrefs.disableSearchBar &&
        homePrefs.searchPlacement.trim().lowercase() == "header action"
    val userAbout = if (identity.about.isNotEmpty()) identity.about else "Available"
    val showStatus = !homePrefs.disableStatusUnderName && userAbout.isNotEmpty()

    Row(
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .combinedClickable(onClick = { if (homePrefs.nameTapOpensLocked) onIdentityTap() }, onLongClick = onIdentityLongPress)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (homeStyle == "Classic") Color(theme.accentColor) else Color(theme.primaryTextColor),
                    fontSize = (titleFontSize * theme.fontScale).sp,
                    fontWeight = FontWeight.W800,
                    letterSpacing = if (headerStyle == HomeHeaderStyle.prominentIdentity) (-0.55).sp else (-0.2).sp,
                )
                if (showStatus) {
                    Text(
                        userAbout,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(theme.secondaryTextColor),
                        fontSize = (11 * theme.fontScale).sp,
                        fontWeight = FontWeight.W400,
                    )
                }
            }
            if (homePrefs.showGhostPill) {
                GhostModePill(theme, homePrefs.ghostMode, onToggleGhost, onSnack)
            }
            Spacer(Modifier.width(8.dp))
            headerExtras?.invoke()
        }
        if (homePrefs.showCameraIcon) {
            IconButton(onClick = nav.onOpenCameraEffects) {
                HomeGlyphIcon(glyph = HomeGlyph.camera, size = 20.dp, color = Color(theme.primaryTextColor), semanticLabel = "Camera Effects")
            }
        }
        if (homePrefs.showDesktopIcon) {
            IconButton(onClick = nav.onOpenQr) {
                HomeGlyphIcon(glyph = HomeGlyph.qrScan, size = 20.dp, color = Color(theme.primaryTextColor), semanticLabel = "QR / Scan")
            }
        }
        if (effectiveShowSearch) {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    if (isSearchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (isSearchOpen) "Close search" else "Search chats",
                    tint = Color(theme.primaryTextColor),
                )
            }
        }
        if (homePrefs.showIconAddAccount) {
            IconButton(onClick = nav.onOpenProfile) {
                Icon(Icons.Outlined.PersonAddAlt1, contentDescription = "Account settings", tint = Color(theme.primaryTextColor))
            }
        }
        // Small profile avatar -> Settings (real photo when uploaded, via
        // the shared ChatyAsyncImage wrapper).
        if (!forcedTypeIsSet) {
            // 48dp touch target and TalkBack semantics around the unchanged ringed 28dp avatar.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Settings" }
                    .clickable(onClickLabel = "Open settings", role = androidx.compose.ui.semantics.Role.Button, onClick = nav.onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .border(1.8.dp, Color(theme.accentColor), CircleShape)
                        .padding(1.8.dp),
                ) {
                    HomeAvatar(
                        initials = identity.avatarInitials,
                        color = parseAvatarColorHex(identity.avatarColorHex, Color(theme.accentColor)),
                        size = 28f,
                        shape = "circle",
                        url = identity.avatarUrl,
                    )
                }
            }
        }
        if (homePrefs.themesEnabled) {
            IconButton(onClick = nav.onToggleBrightness) {
                Icon(
                    if (theme.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = "Toggle light / dark",
                    tint = Color(theme.primaryTextColor),
                )
            }
        }
        IconButton(onClick = onOpenOverflowMenu) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = Color(theme.primaryTextColor))
        }
    }
}

/** _openHomeThreeDotMenu: anchored dropdown with the Dart item set and
 *  order. */
@Composable
fun HomeOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    theme: ThemeConfig,
    nav: ChatsHomeNav,
    onMarkAllAsRead: () -> Unit,
    onMessageANumber: () -> Unit,
) {
    val items: List<Triple<ImageVector, String, () -> Unit>> = listOf(
        Triple(Icons.Rounded.GroupAdd, "New Group") { nav.onOpenNewChat(true, false) },
        Triple(Icons.Outlined.Devices, "Linked Devices") { nav.onOpenLinkedDevices() },
        Triple(Icons.Rounded.Star, "Starred Messages") { nav.onOpenStarredMessages() },
        Triple(Icons.Outlined.ScheduleSend, "Message Scheduler") { nav.onOpenMessageManagement() },
        Triple(Icons.Outlined.Reply, "Auto Reply") { nav.onOpenMessageManagement() },
        Triple(Icons.Outlined.Dialpad, "Message a Number") { onMessageANumber() },
        Triple(Icons.Outlined.Campaign, "Mass Message") { nav.onOpenNewChat(false, true) },
        Triple(Icons.Outlined.Call, "Who can call me?") { nav.onWhoCanCallMe() },
        Triple(Icons.Rounded.DoneAll, "Mark All as Read") { onMarkAllAsRead() },
        Triple(Icons.Outlined.Share, "Share Chaty") { nav.onShareApp() },
        Triple(Icons.Rounded.RestartAlt, "Restart Chaty") { nav.onRestartApp() },
    )
    DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape,
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color(theme.cardColor),
        border = androidx.compose.foundation.BorderStroke(1.1.dp, Color(argbWithAlpha(theme.secondaryTextColor, 0.2))),
    ) {
        items.forEach { (icon, label, action) ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = Color(argbWithAlpha(theme.primaryTextColor, 0.9)))
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = Color(theme.primaryTextColor), fontSize = 15.5.sp, fontWeight = FontWeight.W600)
                    }
                },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
    }
}

/** _selectionAppBar: count, pin, delete, mute, archive, overflow
 *  (lock/unlock, mark unread/read, select all). */
@Composable
fun SelectionHomeAppBar(
    theme: ThemeConfig,
    selectedCount: Int,
    anyUnpinned: Boolean,
    anyUnmuted: Boolean,
    anyUnlocked: Boolean,
    onClearSelection: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleLock: () -> Unit,
    onMarkUnread: () -> Unit,
    onMarkRead: () -> Unit,
    onSelectAll: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(theme.cardColor))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClearSelection) {
            Icon(Icons.Rounded.Close, contentDescription = "Clear selection", tint = Color(theme.primaryTextColor))
        }
        Text(
            selectedCount.toString(),
            color = Color(theme.primaryTextColor),
            fontSize = 19.sp,
            fontWeight = FontWeight.W800,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onTogglePin) {
            Icon(
                if (anyUnpinned) Icons.Outlined.OutlinedPushPin else Icons.Rounded.PushPin,
                contentDescription = if (anyUnpinned) "Pin" else "Unpin",
                tint = Color(theme.primaryTextColor),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(theme.primaryTextColor))
        }
        IconButton(onClick = onToggleMute) {
            Icon(
                if (anyUnmuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                contentDescription = if (anyUnmuted) "Mute" else "Unmute",
                tint = Color(theme.primaryTextColor),
            )
        }
        IconButton(onClick = onToggleArchive) {
            Icon(Icons.Outlined.Archive, contentDescription = "Archive", tint = Color(theme.primaryTextColor))
        }
        Box {
            IconButton(onClick = { overflowOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More actions", tint = Color(theme.primaryTextColor))
            }
            DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                SelectionMenuItem(
                    icon = if (anyUnlocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    label = if (anyUnlocked) "Lock chat" else "Unlock chat",
                ) { overflowOpen = false; onToggleLock() }
                SelectionMenuItem(Icons.Outlined.MarkChatUnread, "Mark as unread") {
                    overflowOpen = false; onMarkUnread()
                }
                SelectionMenuItem(Icons.Outlined.MarkChatRead, "Mark as read") {
                    overflowOpen = false; onMarkRead()
                }
                SelectionMenuItem(Icons.Rounded.SelectAll, "Select all") {
                    overflowOpen = false; onSelectAll()
                }
            }
        }
    }
}

@Composable
private fun SelectionMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(label)
            }
        },
        onClick = onClick,
    )
}

/** _homeSearchField: filled card-colored field, rounded by the theme corner
 *  radius, clear button once non-empty. */
@Composable
fun HomeSearchField(
    theme: ThemeConfig,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    focusRequester: FocusRequester,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color(theme.primaryTextColor)),
        placeholder = { Text(hint, color = Color(theme.secondaryTextColor)) },
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(theme.secondaryTextColor))
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(theme.cornerRadius.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(theme.cardColor),
            unfocusedContainerColor = Color(theme.cardColor),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Color(theme.accentColor),
        ),
    )
}

/** Filter chip row (build(): tabBubbleStyle corner mapping, selected chip in
 *  accent at 18% with a 35% border, w700/w500 label weights). */
@Composable
fun HomeFilterChips(
    theme: ThemeConfig,
    tabBubbleStyle: String,
    filters: List<String>,
    selectedFilter: String,
    onSelect: (String) -> Unit,
) {
    val chipRadius = when (tabBubbleStyle) {
        "Capsule Pill" -> 20
        "Glassmorphism Glow" -> 14
        "Soft Rounded Pill" -> 10
        "Outlined Bubble" -> 18
        "Segmented Tab" -> 6
        else -> 20
    }
    val segmented = tabBubbleStyle == "Segmented Tab"
    val glow = tabBubbleStyle == "Glassmorphism Glow"
    val outlined = tabBubbleStyle == "Outlined Bubble"
    val soft = tabBubbleStyle == "Soft Rounded Pill"
    val accent = Color(theme.accentColor)
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, top = 3.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (segmented) 0.dp else 8.dp),
    ) {
        itemsIndexed(filters) { index, filter ->
            val selected = selectedFilter == filter
            // Segmented Tab joins the chips into one strip: only the ends are rounded.
            val shape = if (segmented) {
                val end = chipRadius.dp
                RoundedCornerShape(
                    topStart = if (index == 0) end else 0.dp, bottomStart = if (index == 0) end else 0.dp,
                    topEnd = if (index == filters.lastIndex) end else 0.dp,
                    bottomEnd = if (index == filters.lastIndex) end else 0.dp,
                )
            } else RoundedCornerShape(chipRadius.dp)
            val fill = when {
                outlined -> Color.Transparent
                glow && selected -> Color(argbWithAlpha(theme.accentColor, 0.22))
                glow -> Color(argbWithAlpha(theme.cardColor, 0.72))
                soft && selected -> Color(argbWithAlpha(theme.accentColor, 0.14))
                selected -> Color(argbWithAlpha(theme.accentColor, 0.18))
                else -> Color(theme.cardColor)
            }
            val borderColor = when {
                outlined && selected -> accent
                outlined -> Color(argbWithAlpha(theme.secondaryTextColor, 0.45))
                glow && selected -> Color(argbWithAlpha(theme.accentColor, 0.6))
                glow -> Color(argbWithAlpha(theme.accentColor, 0.18))
                soft -> Color.Transparent
                selected -> Color(argbWithAlpha(theme.accentColor, 0.35))
                else -> Color(theme.surfaceColor)
            }
            val borderWidth = if (outlined) 1.5.dp else 1.dp
            Box(
                modifier = Modifier
                    .then(
                        if (glow && selected) {
                            Modifier.shadow(10.dp, shape, ambientColor = accent.copy(alpha = 0.55f), spotColor = accent.copy(alpha = 0.55f))
                        } else Modifier,
                    )
                    .background(fill, shape)
                    .border(borderWidth, borderColor, shape)
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    filter,
                    color = if (selected) accent else Color(theme.secondaryTextColor),
                    fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** _buildSubscriptionRequiredBanner: amber activation banner with a
 *  Select Plan button. */
@Composable
fun SubscriptionRequiredBanner(
    theme: ThemeConfig,
    message: String?,
    onSelectPlan: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color(0x26F59E0B), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x66F59E0B), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.WorkspacePremium,
            contentDescription = null,
            tint = Color(0xFFD97706),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Workspace Activation Required",
                fontSize = 13.sp,
                fontWeight = FontWeight.W700,
                color = Color(0xFFB45309),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                message ?: "Please subscribe to a plan to start messaging.",
                fontSize = 12.sp,
                color = Color(theme.secondaryTextColor),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(Color(0xFFD97706), RoundedCornerShape(8.dp))
                .clickable(onClick = onSelectPlan)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Select Plan", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W700)
        }
    }
}

/** ChatyConfirmDialog port for destructive confirms. */
@Composable
fun HomeConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.W700) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(confirmLabel, color = Color(0xFFE11D48), fontWeight = FontWeight.W700)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** _EmptyChats / ChatyEmptyState port. */
@Composable
fun EmptyChatsState(
    theme: ThemeConfig,
    isGroups: Boolean,
    onSearch: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (isGroups) Icons.Outlined.Groups else Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = Color(theme.accentColor),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (isGroups) "No groups yet" else "No conversations yet",
            color = Color(theme.primaryTextColor),
            fontSize = 17.sp,
            fontWeight = FontWeight.W700,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isGroups) {
                "Create a group and add people to start a shared conversation."
            } else {
                "Find a user by @username or scan their Chaty QR code to start a conversation."
            },
            color = Color(theme.secondaryTextColor),
            fontSize = 13.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .background(Color(theme.accentColor), RoundedCornerShape(999.dp))
                .clickable(onClick = onSearch)
                .padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
            Text(
                "Find people",
                color = Color(theme.onAccentColor),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W700,
            )
        }
    }
}


/** The guided Locked Chats setup modal from _handleTopLeftIdentityTap's
 *  else-branch (no credential + no locked chats yet): explains the feature
 *  and routes to Privacy & Security. Exact Dart copy and step list. */
@Composable
fun LockedChatsSetupDialog(
    theme: ThemeConfig,
    onClose: () -> Unit,
    onConfigureSecurity: () -> Unit,
) {
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onClose,
        title = {
            Column {
                Text("Locked Chats", fontWeight = FontWeight.W700)
                Text(
                    "Secure conversations with biometric, PIN, or password",
                    fontSize = 13.sp,
                    color = Color(theme.secondaryTextColor),
                )
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(argbWithAlpha(theme.accentColor, 0.15)), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = Color(theme.accentColor),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Lock individual chats and keep them hidden from the main list.",
                        fontSize = 13.5.sp,
                        color = Color(theme.primaryTextColor),
                        fontWeight = FontWeight.W500,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "How to set up Locked Chats:",
                    fontWeight = FontWeight.W700,
                    fontSize = 13.5.sp,
                    color = Color(theme.accentColor),
                )
                Spacer(Modifier.height(10.dp))
                LockedSetupStep("1", "Set up your Security Credential",
                    "Choose a PIN, Pattern, or Password in Privacy & Security settings.", theme)
                Spacer(Modifier.height(8.dp))
                LockedSetupStep("2", "Lock conversations",
                    "Select any chat in your list and choose \"Lock chat\".", theme)
                Spacer(Modifier.height(8.dp))
                LockedSetupStep("3", "Open your vault anytime",
                    "Tap your name or the Chaty header at the top-left to authenticate and view locked chats.", theme)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onClose()
                onConfigureSecurity()
            }) { Text("Configure Security", fontWeight = FontWeight.W700) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Close") }
        },
    )
}

@Composable
private fun LockedSetupStep(number: String, title: String, description: String, theme: ThemeConfig) {
    Row {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color(argbWithAlpha(theme.accentColor, 0.12)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, fontSize = 12.sp, fontWeight = FontWeight.W700, color = Color(theme.accentColor))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.W600, fontSize = 13.sp, color = Color(theme.primaryTextColor))
            Spacer(Modifier.height(1.dp))
            Text(description, fontSize = 12.sp, color = Color(theme.secondaryTextColor))
        }
    }
}
