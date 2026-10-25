package com.example.chat.ui.settings.universal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.ModeComment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.emoji.ChatyEmojiStyleId
import com.example.chat.core.emoji.ChatyMotionEmoji
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.ThemeController
import com.example.chat.ui.core.controllers.AppIconControllerContract
import com.example.chat.ui.core.controllers.BrandIconSource
import com.example.chat.ui.core.controllers.LauncherIconVariant
import com.example.chat.ui.core.controllers.title
import com.example.chat.ui.core.notifications.ChatyNotificationIconPresets
import com.example.chat.ui.settings.appicon.LauncherIconPreview
import com.example.chat.ui.settings.SettingsPage
import com.example.chat.ui.settings.SettingsSection
import com.example.chat.ui.theme.LocalAppColors
import java.io.File
import kotlinx.coroutines.launch

/** Result handed back by the host-wired font file picker (SAF OpenDocument). */
data class FontFilePick(val name: String, val source: File)

internal fun notificationGlyphIcon(key: String): ImageVector = when (key) {
    "chat_bubble_outline_rounded" -> Icons.Rounded.ChatBubbleOutline
    "chat_bubble_rounded" -> Icons.Rounded.ChatBubble
    "forum_rounded", "forum_outlined" -> Icons.Rounded.Forum
    "mode_comment_rounded", "mode_comment_outlined" -> Icons.Rounded.ModeComment
    "sms_rounded", "sms_outlined" -> Icons.Rounded.Sms
    "chat_rounded" -> Icons.Rounded.Chat
    "mark_chat_unread_rounded" -> Icons.Rounded.MarkChatUnread
    "message_rounded", "message_outlined" -> Icons.Rounded.Message
    "bubble_chart_rounded" -> Icons.Rounded.BubbleChart
    else -> Icons.Rounded.ChatBubble
}

internal fun FontStyleEntry.composeFamily(): FontFamily? = when (fontFamilyKey) {
    null -> null
    "sans-serif" -> FontFamily.SansSerif
    // No direct condensed generic family in Compose; Dart maps the custom
    // Bariol name to sans-serif-condensed, nearest generic is SansSerif.
    "sans-serif-condensed" -> FontFamily.SansSerif
    "cursive" -> FontFamily.Cursive
    "monospace" -> FontFamily.Monospace
    "sans-serif-rounded" -> FontFamily.SansSerif
    else -> FontFamily.Default
}

/**
 * Port of lib/features/settings/universal/universal_styles_screen.dart.
 *
 * [appIconController] is nullable exactly like the Dart locator.isRegistered
 * pattern: when absent, launcher tiles fall back to the stored preference
 * label and tapping shows the unavailable snackbar. [pickFontFile] is host
 * wiring for the SAF document picker (Dart file_picker); null surfaces the
 * Dart error snackbar text.
 *
 * Emoji artwork renders through the real ChatyMotionEmoji vector engine and
 * launcher tiles render the real GROUP H LauncherIconPreview
 * (ui/settings/appicon/AppBrandIcon.kt - compile dependency, lands with the
 * app-icon drop). Compose compile is CI-gated; no runtime pixels verified.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalStylesScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    themeController: ThemeController? = null,
    appIconController: AppIconControllerContract? = null,
    onMessage: (String) -> Unit = {},
    pickFontFile: (suspend () -> FontFilePick?)? = null,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = themeController?.globalTheme
    val accent = theme?.let { Color(it.accentColor) } ?: colors.primary
    val fontScale = (theme?.fontScale ?: 1.0).toFloat()

    val prefs = preferences.universal
    var showAllLauncherIcons by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(0) } // 1 notification icon, 2 font style

    // Dart initState: initialize the controller and reconcile the stored
    // preference label against the confirmed native variant (bundled only).
    LaunchedEffect(appIconController) {
        val controller = appIconController ?: return@LaunchedEffect
        try {
            controller.initialize()
            val corrected = UniversalStylesCatalog.reconcileLauncherIconPref(
                preferences.universal.launcherIcon,
                controller.launcherIcon,
                controller.brandIconSource,
            )
            if (corrected != null) {
                preferences.updateUniversal(
                    preferences.universal.copy(launcherIcon = corrected)
                )
            }
        } catch (_: Exception) {
        }
    }

    SettingsPage(title = "Styles (Look and feel)", onBack = onBack) {
        // SECTION 1: LAUNCHER ICONS
        SettingsSection(title = "LAUNCHER ICONS") {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                val widthDp = LocalConfiguration.current.screenWidthDp
                val columns = UniversalStylesCatalog.launcherGridColumns(widthDp)
                val displayed = UniversalStylesCatalog.displayedLauncherIcons(showAllLauncherIcons)
                displayed.chunked(columns).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth()) {
                        rowItems.forEach { item ->
                            val isSelected = if (appIconController != null) {
                                appIconController.launcherIcon == item.variant &&
                                    appIconController.brandIconSource == BrandIconSource.bundled
                            } else {
                                prefs.launcherIcon == item.variant.title
                            }
                            Column(
                                Modifier.weight(1f).clickable {
                                    val controller = appIconController
                                    if (controller == null) {
                                        onMessage("Launcher icon switching is unavailable on this platform.")
                                        return@clickable
                                    }
                                    if (controller.isBusy) return@clickable
                                    scope.launch {
                                        val applied = controller.applyLauncherIcon(item.variant)
                                        if (!applied) {
                                            onMessage(
                                                controller.lastError
                                                    ?: "The launcher did not confirm the selected icon."
                                            )
                                            return@launch
                                        }
                                        controller.refreshNativeLauncherState()
                                        preferences.updateUniversal(
                                            preferences.universal.copy(launcherIcon = item.variant.title)
                                        )
                                        onMessage(
                                            "Launcher icon updated: ${item.variant.title}. Some launchers may cache the previous icon briefly."
                                        )
                                    }
                                }.padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box {
                                    LauncherIconPreview(
                                        variant = item.variant,
                                        size = 44.dp,
                                        borderRadius = 14.dp,
                                    )
                                    if (isSelected) {
                                        Box(
                                            Modifier.matchParentSize()
                                                .border(2.5.dp, accent, RoundedCornerShape(14.dp))
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) colors.foreground else colors.foregroundSecondary,
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSelected) FontWeight.W800 else FontWeight.W500,
                                )
                                Spacer(Modifier.height(2.dp))
                                if (isSelected) {
                                    Box(Modifier.size(5.dp).background(accent, CircleShape))
                                }
                            }
                        }
                        repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showAllLauncherIcons = !showAllLauncherIcons }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (showAllLauncherIcons) "• Less •" else "• More •",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                    )
                }
            }
        }

        // SECTION 2: EMOJI STYLES (10 SELECTABLE STYLES)
        SettingsSection(title = "EMOJI STYLES (${ChatyEmojiStyleId.entries.size} SELECTABLE STYLES)") {
            ChatyEmojiStyleId.entries.forEach { style ->
                val currentStyleId = ChatyEmojiStyleId.fromString(prefs.emojiStyle)
                val isSelected = currentStyleId == style
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            if (isSelected) accent.copy(alpha = 0.08f) else Color.Transparent
                        )
                        .clickable {
                            preferences.updateUniversal(
                                prefs.copy(emojiStyle = style.title, emojiVariant = style.title)
                            )
                            onMessage("Emoji style set to ${style.title}")
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) accent.copy(alpha = 0.15f) else colors.surfaceSecondary
                                )
                                .border(
                                    width = if (isSelected) 1.8.dp else 1.0.dp,
                                    color = if (isSelected) accent else colors.border.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChatyMotionEmoji(
                                unicode = "😂",
                                size = 32.dp,
                                style = style,
                                animate = isSelected && style.isAnimated,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    style.title,
                                    color = theme?.let { Color(it.primaryTextColor) } ?: colors.foreground,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.W700 else FontWeight.W600,
                                )
                                if (style.isAnimated) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(accent.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            "ANIMATED",
                                            color = accent,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.W800,
                                            letterSpacing = 0.5.sp,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                style.description,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.foregroundSecondary,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row {
                                ChatyMotionEmoji(unicode = "😂", size = 20.dp, style = style, animate = false)
                                Spacer(Modifier.width(6.dp))
                                ChatyMotionEmoji(unicode = "❤️", size = 20.dp, style = style, animate = false)
                                Spacer(Modifier.width(6.dp))
                                ChatyMotionEmoji(unicode = "🔥", size = 20.dp, style = style, animate = false)
                                Spacer(Modifier.width(6.dp))
                                ChatyMotionEmoji(unicode = "👍", size = 20.dp, style = style, animate = false)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(22.dp)
                                .border(2.dp, if (isSelected) accent else colors.border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(Modifier.size(12.dp).background(accent, CircleShape))
                            }
                        }
                    }
                }
            }
        }

        // SECTION 3: CHANGE NOTIFICATION ICON & FONTS
        SettingsSection(title = null) {
            StylesNavRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Change Notification Icon",
                subtitle = "Selected: ${prefs.notificationIcon}\nThis is the icon that will appear in your status bar",
                onClick = { sheet = 1 },
            )
            StylesNavRow(
                icon = Icons.Rounded.FontDownload,
                title = "Font Style",
                subtitle = "Selected: ${prefs.fontStyle}\nChange font style for Chaty",
                onClick = { sheet = 2 },
            )
            StylesNavRow(
                icon = Icons.Rounded.FolderOpen,
                title = "Load Font...",
                subtitle = if (prefs.loadFontCustom) "Custom Font: Loaded & Active" else "Tap to select .ttf or .otf file",
                onClick = {
                    if (prefs.loadFontCustom) {
                        preferences.updateUniversal(prefs.copy(loadFontEnabled = false))
                        onMessage("Custom font disabled; reverted to app default.")
                        return@StylesNavRow
                    }
                    val picker = pickFontFile
                    if (picker == null) {
                        onMessage("Failed to load font: file picker unavailable")
                        return@StylesNavRow
                    }
                    scope.launch {
                        val picked = picker() ?: return@launch
                        val result = CustomFontLogic { context.filesDir }.install(picked.source, picked.name)
                        when (result) {
                            is CustomFontLogic.Result.Loaded -> {
                                preferences.updateUniversal(preferences.universal.copy(loadFontEnabled = true))
                                onMessage(result.message)
                            }
                            is CustomFontLogic.Result.Failure -> onMessage(result.message)
                        }
                    }
                },
            )
        }
    }

    // Dart _showNotificationIconModal
    if (sheet == 1) {
        ModalBottomSheet(onDismissRequest = { sheet = 0 }) {
            Text(
                "Change Notification Icon",
                fontSize = 17.sp * fontScale,
                fontWeight = FontWeight.W700,
                color = colors.foreground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ChatyNotificationIconPresets.values.forEach { preset ->
                val isSelected = prefs.notificationIcon == preset.name
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            preferences.updateUniversal(prefs.copy(notificationIcon = preset.name))
                            sheet = 0
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(38.dp)
                            .background(Color(preset.colorArgb).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            notificationGlyphIcon(preset.iconKey),
                            contentDescription = preset.name,
                            tint = Color(preset.colorArgb),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        preset.name,
                        fontWeight = if (isSelected) FontWeight.W700 else FontWeight.W500,
                        color = if (isSelected) colors.primary else colors.foreground,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(20.dp)
                            .border(2.dp, if (isSelected) colors.primary else colors.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Box(Modifier.size(10.dp).background(colors.primary, CircleShape))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Dart _showFontStyleModal
    if (sheet == 2) {
        ModalBottomSheet(onDismissRequest = { sheet = 0 }) {
            Text(
                "Font Style",
                fontSize = 17.sp * fontScale,
                fontWeight = FontWeight.W700,
                color = colors.foreground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            UniversalStylesCatalog.fontStyles.forEach { entry ->
                val isSelected = prefs.fontStyle == entry.name
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            preferences.updateUniversal(prefs.copy(fontStyle = entry.name))
                            sheet = 0
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.name,
                            fontWeight = FontWeight.W700,
                            fontSize = 15.sp,
                            color = if (isSelected) colors.primary else colors.foreground,
                        )
                        Text(
                            "Impossible can be achieved",
                            fontSize = 13.sp,
                            fontFamily = entry.composeFamily(),
                            fontWeight = FontWeight(entry.weight),
                            fontStyle = if (entry.italic) FontStyle.Italic else FontStyle.Normal,
                            letterSpacing = entry.letterSpacing.sp,
                            color = if (isSelected) colors.primary.copy(alpha = 0.8f) else colors.foregroundSecondary,
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StylesNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.W600)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text("›", color = colors.foregroundSecondary, fontSize = 22.sp)
    }
}
