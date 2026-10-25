package com.example.chat.ui.settings.universal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.theme.LocalThemeConfig

/** Shared frame of the universal settings screens (Dart Scaffold + AppBar +
 *  SafeArea(top: false) + ListView padding h14 v12): background coloured
 *  body, surface coloured 56dp app bar with the padded ChatyBackButton and a
 *  19 * fontScale w700 title. Optional app-bar actions (the icons screen's
 *  Reset button).
 *
 *  UNCOMPILED (Compose) - CI is the compiler check. */
@Composable
internal fun UniversalScaffold(
    title: String,
    onBack: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val config = LocalThemeConfig.current
    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        Row(
            Modifier.fillMaxWidth()
                .background(Color(config.surfaceColor))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text(
                title,
                color = Color(config.primaryTextColor),
                fontSize = (19f * config.fontScale.toFloat()).sp,
                fontWeight = FontWeight.W700,
            )
            if (actions != null) {
                Spacer(Modifier.weight(1f))
                actions()
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            content = content,
        )
    }
}

/** Port of lib/features/settings/universal/universal_screen.dart: one GB
 *  card with the six Universal sub-screens. Destinations are lambdas; the
 *  root owner routes them (Styles/Backup belong to other settings groups).
 *
 *  UNCOMPILED (Compose) - CI is the compiler check. */
@Composable
fun UniversalScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    onOpenColors: () -> Unit,
    onOpenStyles: () -> Unit,
    onOpenChatyIcons: () -> Unit,
    onOpenHideMedia: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenGeneralSettings: () -> Unit,
) {
    UniversalScaffold(title = "Universal", onBack = onBack) {
        GbCardContainer {
            GbSettingRow(
                icon = Icons.Rounded.Palette,
                title = "Colors",
                subtitle = "Universal Color, Actionbar, Background, Bars",
                hasSubScreen = true,
                onTap = onOpenColors,
            )
            GbSettingRow(
                icon = Icons.Rounded.Style,
                title = "Styles (Look and feel)",
                subtitle = "Launcher icons, Emoji variant, Notification icon, Font style",
                hasSubScreen = true,
                onTap = onOpenStyles,
            )
            GbSettingRow(
                icon = Icons.Rounded.AutoAwesomeMosaic,
                title = "Universal Chaty Icons",
                subtitle = "10 custom icon themes, live preview, individual catalogue",
                hasSubScreen = true,
                onTap = onOpenChatyIcons,
            )
            GbSettingRow(
                icon = Icons.Rounded.VisibilityOff,
                title = "Hide Media from gallery",
                subtitle = "Photos, Videos, GIFs",
                hasSubScreen = true,
                onTap = onOpenHideMedia,
            )
            GbSettingRow(
                icon = Icons.Rounded.SettingsBackupRestore,
                title = "Backup and restore",
                subtitle = "Chaty Data, Media, Clear cache",
                hasSubScreen = true,
                onTap = onOpenBackupRestore,
            )
            GbSettingRow(
                icon = Icons.Rounded.Tune,
                title = "Settings",
                subtitle = "Translation, Cards, Audio notifications, Image mods",
                hasSubScreen = true,
                onTap = onOpenGeneralSettings,
            )
        }
    }
}
