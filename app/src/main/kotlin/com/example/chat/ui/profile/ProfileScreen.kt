package com.example.chat.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Wallpaper
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.ChatyAsyncImage
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

/** Port of template_models.dart ProfileHeaderStyle. */
enum class ProfileHeaderStyle { compactHeader, centeredIdentity, bannerWithAvatar }

/**
 * Port of lib/features/profile/profile_screen.dart (api @ 3bee780d): the
 * root "Profile" destination with banner/avatar header, optional stats
 * grid, Account and App sections and the log-out entry with the shared
 * confirmChatyLogout dialog copy.
 *
 * TemplateController/profile-template values (headerStyle, showStatsGrid,
 * avatarShape) arrive as parameters until the template slice lands; the
 * Dart defaults are the parameter defaults. Navigation and service calls
 * stay outside: the caller supplies callbacks (same convention as the
 * auth/chats slices), so the integration owner wires ServiceLocator.
 */
@Composable
fun ProfileScreen(
    user: UserProfile,
    chatsCount: Int,
    groupsCount: Int,
    contactsCount: Int,
    // preferencesController.home.myNameOverride equivalent.
    displayNameOverride: String = "",
    headerStyle: ProfileHeaderStyle = ProfileHeaderStyle.bannerWithAvatar,
    showStatsGrid: Boolean = true,
    avatarShape: String = "circle",
    onEditProfile: () -> Unit,
    // Null hides the banner-edit affordance: the custom backend has no
    // banner-upload endpoint (api branch uploads banners via Supabase
    // storage only), so the owner passes null until one exists.
    onEditBanner: (() -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onOpenPrivacyCenter: () -> Unit,
    onOpenNotifications: () -> Unit,
    onLogout: () -> Unit,
) {
    val theme = LocalThemeConfig.current
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // Exact display-name fallback chain from profile_screen.dart (pure,
    // JVM-tested in ProfileDisplayNameParityTest).
    val displayName = com.example.chat.core.profile.ProfileDisplayName.effectiveDisplayName(
        myNameOverride = displayNameOverride,
        displayName = user.displayName,
        username = user.username,
    )

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ChatySettingsPage title/subtitle.
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    "Profile",
                    color = Color(theme.primaryTextColor),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                )
                Text(
                    "Account, status and app entry points",
                    color = Color(theme.secondaryTextColor),
                    fontSize = 13.sp,
                )
            }

            ProfileHeader(
                initials = user.avatarInitials,
                colorHex = user.avatarColorHex,
                avatarUrl = user.avatarUrl,
                bannerUrl = user.bannerUrl,
                displayName = displayName,
                username = user.username,
                about = user.about,
                headerStyle = headerStyle,
                avatarShape = avatarShape,
                onEdit = onEditProfile,
                onEditBanner = onEditBanner,
            )

            if (showStatsGrid) {
                Spacer(Modifier.height(8.dp))
                ProfileStatsGrid(chats = chatsCount, groups = groupsCount, contacts = contactsCount)
            }
            Spacer(Modifier.height(8.dp))

            SettingsSection(title = "Account") {
                SettingsTile(
                    icon = Icons.Outlined.AlternateEmail,
                    title = "Username",
                    subtitle = "@${user.username}",
                )
                if (user.about.isNotEmpty()) {
                    SettingsTile(
                        icon = Icons.Outlined.Info,
                        title = "About",
                        subtitle = user.about,
                    )
                }
            }

            SettingsSection(title = "App") {
                SettingsTile(
                    icon = Icons.Outlined.Settings,
                    title = "Settings",
                    subtitle = "Account, privacy, appearance and app behavior",
                    showChevron = true,
                    onClick = onOpenSettings,
                )
                SettingsTile(
                    icon = Icons.Outlined.NotificationsNone,
                    title = "Notifications",
                    subtitle = "Toasts, alerts and previews",
                    showChevron = true,
                    onClick = onOpenNotifications,
                )
                SettingsTile(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Privacy Center",
                    subtitle = "Presence, receipts and anti-delete options",
                    showChevron = true,
                    onClick = onOpenPrivacyCenter,
                )
            }
            Spacer(Modifier.height(8.dp))

            SettingsSection {
                SettingsTile(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    iconTint = Color(theme.dangerColor),
                    title = "Log out",
                    subtitle = "Sign out of Chaty on this device",
                    onClick = { showLogoutConfirm = true },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLogoutConfirm) {
        // Exact confirmChatyLogout copy from profile_actions.dart.
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out of Chaty?") },
            text = {
                Text("Your account will be signed out on this device. Your chats and account data remain on the server.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(theme.dangerColor)),
                ) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileHeader(
    initials: String,
    colorHex: String,
    avatarUrl: String?,
    bannerUrl: String?,
    displayName: String,
    username: String,
    about: String,
    headerStyle: ProfileHeaderStyle,
    avatarShape: String,
    onEdit: () -> Unit,
    onEditBanner: (() -> Unit)?,
) {
    val theme = LocalThemeConfig.current
    val screenWidth = LocalConfiguration.current.screenWidthDp
    // Exact sizing from profile_screen.dart.
    val bannerHeight = when (headerStyle) {
        ProfileHeaderStyle.compactHeader -> 128
        // (width - 32) * 9/16, the 16:9 frame of the banner crop dialog.
        else -> ((screenWidth - 32) * 9 / 16)
    }
    val avatarSize = when (headerStyle) {
        ProfileHeaderStyle.compactHeader -> 64
        ProfileHeaderStyle.centeredIdentity -> 96
        ProfileHeaderStyle.bannerWithAvatar -> 92
    }
    when (headerStyle) {
        ProfileHeaderStyle.compactHeader -> CompactHeader(
            initials, colorHex, avatarUrl, displayName, username, about,
            avatarSize, avatarShape, onEdit, onEditBanner,
        )
        ProfileHeaderStyle.centeredIdentity -> CenteredIdentityHeader(
            initials, colorHex, avatarUrl, bannerUrl, displayName, username, about,
            avatarSize, bannerHeight, avatarShape, onEdit, onEditBanner,
        )
        ProfileHeaderStyle.bannerWithAvatar -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            ) {
                BannerSurface(bannerUrl = bannerUrl, height = bannerHeight)
                if (onEditBanner != null) {
                    IconButton(
                        onClick = onEditBanner,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 12.dp)
                            .size(31.dp)
                            .background(
                                Color(theme.surfaceColor).copy(alpha = 0.85f),
                                CircleShape,
                            )
                            .border(1.dp, LocalAppColors.current.borderSubtle, CircleShape),
                    ) {
                        Icon(
                            Icons.Rounded.Wallpaper,
                            contentDescription = "Change profile banner",
                            tint = Color(theme.primaryTextColor),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 44.dp)
                        .background(Color(theme.backgroundColor), avatarShapeOf(avatarShape, avatarSize + 8))
                        .padding(4.dp),
                ) {
                    ChatyAvatar(
                        initials = initials,
                        colorHex = colorHex,
                        url = avatarUrl,
                        size = avatarSize,
                        shape = avatarShape,
                    )
                }
            }
            Spacer(Modifier.height(52.dp))
            Spacer(Modifier.height(14.dp))
            Text(
                displayName,
                color = Color(theme.primaryTextColor),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "@$username",
                color = Color(theme.secondaryTextColor),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
            )
            if (about.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    about,
                    color = Color(theme.secondaryTextColor),
                    fontSize = 13.sp,
                    lineHeight = 17.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            EditPill(onClick = onEdit)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CompactHeader(
    initials: String,
    colorHex: String,
    avatarUrl: String?,
    displayName: String,
    username: String,
    about: String,
    avatarSize: Int,
    avatarShape: String,
    onEdit: () -> Unit,
    onEditBanner: (() -> Unit)?,
) {
    val theme = LocalThemeConfig.current
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .background(Color(theme.surfaceColor), avatarShapeOf(avatarShape, avatarSize))
                .border(1.dp, LocalAppColors.current.borderSubtle, avatarShapeOf(avatarShape, avatarSize))
                .padding(3.dp),
        ) {
            ChatyAvatar(initials, colorHex, avatarUrl, avatarSize, avatarShape)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    displayName,
                    color = Color(theme.primaryTextColor),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 21.6.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (onEditBanner != null) {
                    IconButton(onClick = onEditBanner, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Wallpaper,
                            contentDescription = "Change profile banner",
                            tint = Color(theme.secondaryTextColor),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "@$username",
                color = Color(theme.secondaryTextColor),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (about.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    about,
                    color = Color(theme.secondaryTextColor),
                    fontSize = 12.5.sp,
                    lineHeight = 16.9.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            EditPill(onClick = onEdit)
        }
    }
}

@Composable
private fun CenteredIdentityHeader(
    initials: String,
    colorHex: String,
    avatarUrl: String?,
    bannerUrl: String?,
    displayName: String,
    username: String,
    about: String,
    avatarSize: Int,
    bannerHeight: Int,
    avatarShape: String,
    onEdit: () -> Unit,
    onEditBanner: (() -> Unit)?,
) {
    val theme = LocalThemeConfig.current
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            BannerSurface(bannerUrl = bannerUrl, height = bannerHeight)
            if (onEditBanner != null) {
                IconButton(
                    onClick = onEditBanner,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(theme.surfaceColor).copy(alpha = 0.88f), CircleShape),
                ) {
                    Icon(
                        Icons.Outlined.Wallpaper,
                        contentDescription = "Change profile banner",
                        tint = Color(theme.primaryTextColor),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .background(Color(theme.backgroundColor), avatarShapeOf(avatarShape, avatarSize + 8))
                .border(1.dp, LocalAppColors.current.borderSubtle, avatarShapeOf(avatarShape, avatarSize + 8))
                .padding(4.dp),
        ) {
            ChatyAvatar(initials, colorHex, avatarUrl, avatarSize, avatarShape)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            displayName,
            color = Color(theme.primaryTextColor),
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "@$username",
            color = Color(theme.secondaryTextColor),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        if (about.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                about,
                color = Color(theme.secondaryTextColor),
                fontSize = 13.sp,
                lineHeight = 18.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            EditPill(onClick = onEdit)
            if (onEditBanner != null) {
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.OutlinedButton(onClick = onEditBanner) {
                    Icon(Icons.Outlined.Wallpaper, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Banner")
                }
            }
        }
    }
}

@Composable
internal fun BannerSurface(bannerUrl: String?, height: Int) {
    val theme = LocalThemeConfig.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(theme.cardColor))
            .border(1.dp, LocalAppColors.current.borderSubtle, RoundedCornerShape(18.dp)),
    ) {
        ChatyAsyncImage(
            url = bannerUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            placeholder = { BannerFallback() },
            error = { BannerFallback() },
        )
    }
}

/** Token-driven placeholder shown when no banner has been uploaded yet. */
@Composable
private fun BannerFallback() {
    val theme = LocalThemeConfig.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(theme.accentColor).copy(alpha = 0.18f),
                        Color(theme.cardColor),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Wallpaper,
            contentDescription = null,
            tint = Color(theme.secondaryTextColor).copy(alpha = 0.65f),
            modifier = Modifier.size(30.dp),
        )
    }
}

/** Avatar with the Dart shape rule: circle, squircle (0.32) or
 *  roundedSquare (0.22) corner radius, network/file image with an
 *  initials fallback (ChatyNetworkAvatar in app_avatar.dart). */
@Composable
internal fun ChatyAvatar(
    initials: String,
    colorHex: String,
    url: String?,
    size: Int,
    shape: String,
) {
    val theme = LocalThemeConfig.current
    val clipShape = avatarShapeOf(shape, size)
    val fallbackColor = runCatching {
        Color(android.graphics.Color.parseColor(colorHex.toComposeHex()))
    }.getOrDefault(Color(theme.accentColor))
    val initialsContent: @Composable () -> Unit = {
        Text(
            initials.ifEmpty { "U" },
            color = fallbackColor,
            fontSize = (size * 0.34).sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
    Box(
        Modifier
            .size(size.dp)
            .clip(clipShape)
            .background(fallbackColor.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center,
    ) {
        // Blank/null URL renders the initials fallback via error ?: placeholder
        // (ChatyAsyncImage contract), matching the Dart errorBuilder rule.
        ChatyAsyncImage(
            url = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            placeholder = initialsContent,
            error = initialsContent,
        )
    }
}

/** '0xFF6366F1' style hex -> '#FF6366F1' for android.graphics.Color. */
private fun String.toComposeHex(): String =
    if (startsWith("0x") || startsWith("0X")) "#" + drop(2) else this

internal fun avatarShapeOf(shape: String, size: Int): RoundedCornerShape = when (shape) {
    "squircle" -> RoundedCornerShape((size * 0.32).dp)
    "roundedSquare" -> RoundedCornerShape((size * 0.22).dp)
    else -> RoundedCornerShape((size / 2).dp)
}

@Composable
private fun ProfileStatsGrid(chats: Int, groups: Int, contacts: Int) {
    val theme = LocalThemeConfig.current
    val stats = listOf(
        Triple("Chats", chats, Icons.Outlined.ChatBubbleOutline),
        Triple("Groups", groups, Icons.Outlined.Groups),
        Triple("Contacts", contacts, Icons.Outlined.PeopleOutline),
    )
    Row(Modifier.padding(horizontal = 12.dp)) {
        for ((label, value, icon) in stats) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .background(Color(theme.surfaceColor), RoundedCornerShape(14.dp))
                    .border(1.dp, LocalAppColors.current.borderSubtle, RoundedCornerShape(14.dp))
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, contentDescription = null, tint = Color(theme.accentColor), modifier = Modifier.size(17.dp))
                Spacer(Modifier.height(5.dp))
                Text(
                    value.toString(),
                    color = Color(theme.primaryTextColor),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                )
                Text(
                    label,
                    color = Color(theme.secondaryTextColor),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Small bordered "Edit profile" pill with the 120ms press-scale of the
 *  Dart _EditPill. */
@Composable
internal fun EditPill(onClick: () -> Unit) {
    val theme = LocalThemeConfig.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .scale(if (pressed) 0.96f else 1f)
            .alpha(if (pressed) 0.82f else 1f)
            .background(Color(theme.cardColor), RoundedCornerShape(999.dp))
            .border(1.dp, LocalAppColors.current.borderSubtle, RoundedCornerShape(999.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color(theme.accentColor), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            "Edit profile",
            color = Color(theme.primaryTextColor),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun SettingsSection(
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val theme = LocalThemeConfig.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (title != null) {
            Text(
                title,
                color = Color(theme.secondaryTextColor),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(theme.surfaceColor), RoundedCornerShape(14.dp))
                .border(1.dp, LocalAppColors.current.borderSubtle, RoundedCornerShape(14.dp)),
        ) { content() }
    }
}

@Composable
internal fun SettingsTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val theme = LocalThemeConfig.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint ?: Color(theme.accentColor),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(theme.primaryTextColor), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = Color(theme.secondaryTextColor),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color(theme.secondaryTextColor).copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
