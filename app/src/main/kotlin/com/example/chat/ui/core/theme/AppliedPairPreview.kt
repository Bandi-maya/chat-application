package com.example.chat.ui.core.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.templates.AppearanceVariantController
import com.example.chat.core.templates.ChatyNavigationDestinationIds
import com.example.chat.core.templates.ChatyTemplateId
import com.example.chat.core.templates.PreviewLayout
import com.example.chat.core.templates.TemplateController
import com.example.chat.core.templates.UserTemplateConfiguration
import com.example.chat.core.templates.themeWithTemplateConversation
import com.example.chat.core.theme.ThemeConfig

private fun hiddenDestinations(prefs: ChatyPreferencesController): Set<String> = buildSet {
    if (prefs.gbBool("nav_hide_tasks")) add(ChatyNavigationDestinationIds.tasks)
    if (prefs.gbBool("nav_hide_updates")) add(ChatyNavigationDestinationIds.updates)
}

/**
 * Dart currentSetupLayout(): the Dart version reads the service locator (isRegistered checks); here the
 * registered services are passed as nullable params, and the owner supplies them from ServiceLocator.
 */
fun currentSetupLayout(
    templateController: TemplateController?,
    preferences: ChatyPreferencesController?,
    appearance: AppearanceVariantController?,
): PreviewLayout = PreviewLayout.resolve(
    templateController?.config ?: UserTemplateConfiguration(),
    bottomBarStyle = appearance?.bottomBarStyle,
    hiddenDestinationIds = if (preferences == null) emptySet() else hiddenDestinations(preferences),
    homeStylePreset = preferences?.home?.homeStyle,
    showStories = preferences?.home?.enableStoriesStrip,
)

fun appliedTemplateLayout(id: ChatyTemplateId, preferences: ChatyPreferencesController?): PreviewLayout =
    PreviewLayout.resolve(
        UserTemplateConfiguration(baseTemplate = id, componentOverrides = emptyMap()),
        hiddenDestinationIds = if (preferences == null) emptySet() else hiddenDestinations(preferences),
    )

/** Port of applied_pair_preview.dart AppliedPairPreview. */
@Composable
fun AppliedPairPreview(
    palette: ThemeConfig,
    layout: PreviewLayout,
    applyTemplateConversation: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = if (applyTemplateConversation) themeWithTemplateConversation(palette, layout.conversation) else palette
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ThemePairPreview(theme, layout = layout) }
}
