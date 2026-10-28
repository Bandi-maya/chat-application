package com.example.chat.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.chat.ServiceLocator
import com.example.chat.core.templates.TemplateRuntimeAdapter
import com.example.chat.ui.settings.gb.GbSettingsScreen
import com.example.chat.ui.settings.gb.GbSettingsUser

/**
 * Makes the existing customization hub reachable in the native settings tree.
 * Menu items reuse existing Chaty settings and active controllers; this does not
 * activate the retired Supabase settings plane or change backend, session or MLS behavior.
 */
@Composable
fun GbSettingsHubHost(
    onBack: () -> Unit,
    onSwitch: (SettingsDestination) -> Unit,
) {
    val preferences = ServiceLocator.preferencesController
    val themeController = ServiceLocator.themeController
    val account = ServiceLocator.apiBackend.currentUser
    var nestedDestination by remember { mutableStateOf<SettingsDestination?>(null) }

    BackHandler(enabled = nestedDestination != null) { nestedDestination = null }

    val destination = nestedDestination
    if (destination != null) {
        SettingsSubHost(
            destination = destination,
            onBack = { nestedDestination = null },
            onSwitch = onSwitch,
        )
    } else {
        val runtime = remember(preferences, themeController) {
            TemplateRuntimeAdapter(
                themeController = themeController,
                preferences = preferences,
                appearance = ServiceLocator.appearanceVariantController,
            ).runtime()
        }
        val user = GbSettingsUser(
            initials = account?.avatarInitials?.takeIf { it.isNotBlank() } ?: "C",
            colorHex = account?.avatarColorHex,
            url = null,
        )
        GbSettingsScreen(
            preferences = preferences,
            themeController = themeController,
            templateController = ServiceLocator.templateController,
            templateRuntime = runtime,
            user = user,
            avatar = { avatarUser, avatarSize ->
                val fill = avatarUser.colorHex?.let { hex ->
                    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
                } ?: Color(0xFF2563EB)
                Box(
                    Modifier.size(avatarSize).clip(CircleShape).background(fill),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        avatarUser.initials.take(2).uppercase(),
                        color = Color.White,
                        fontSize = (avatarSize.value * 0.34f).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            onBack = onBack,
            onOpenPrivacy = { nestedDestination = SettingsDestination.Privacy },
            onOpenThemeEditor = { nestedDestination = SettingsDestination.ThemeEngine },
            onOpenUniversal = { nestedDestination = SettingsDestination.Universal },
            onOpenHome = { nestedDestination = SettingsDestination.HomeScreen },
            onOpenConversation = { nestedDestination = SettingsDestination.Conversation },
            onOpenNotifications = { nestedDestination = SettingsDestination.Notifications },
            onOpenMessageManagement = { onSwitch(SettingsDestination.Automation) },
            onOpenStorageMedia = { nestedDestination = SettingsDestination.StorageMedia },
            onOpenNavigationEffects = { nestedDestination = SettingsDestination.Effects },
        )
    }
}
