package com.example.chat.ui.navigation

import com.example.chat.core.theme.AppNavigationMode

// The appearance variant tables/keys/indexes are canonical in
// com.example.chat.core.templates.AppearanceVariantController (Settings
// drop8); the local AppearanceTables duplicate is removed.

/** Exact port of home_preset_normalizer.dart (nav-relevant parts):
 *  canonicalizes legacy/GB-facing Home UI labels, then resolves the runtime
 *  shell mode. Unknown/custom presets return null and retain the Template
 *  Studio mode. */
object HomePresetNormalizer {

    fun homeStyle(value: String): String = when (value.trim().lowercase()) {
        "chaty", "default", "chaty default" -> "Chaty Default"
        "classic", "whatsapp old ui" -> "Classic"
        "compact", "bubbles tab style" -> "Compact"
        "cards", "one ui" -> "Cards"
        "minimal" -> "Minimal"
        "stories first", "stories-first", "ios style", "instagram style" -> "Stories First"
        "expressive" -> "Expressive"
        "productivity", "basic tab style" -> "Productivity"
        "tablet split view" -> "Tablet Split View"
        "whatsapp ui stock", "telegram style" -> "Chaty Default"
        // Navigation-shell presets also pick a matching home-list treatment.
        "whatsapp-style top bar", "whatsapp style top bar" -> "Classic"
        "floating rail" -> "Stories First"
        "3d perspective drawer" -> "Expressive"
        "modern side menu" -> "Cards"
        "curved radial menu" -> "Expressive"
        else -> "Chaty Default"
    }

    /** Resolves the selected Home UI label to the runtime shell mode. */
    fun navigationMode(value: String): AppNavigationMode? = when (value.trim().lowercase()) {
        "classic", "whatsapp old ui", "whatsapp-style top bar", "whatsapp style top bar" ->
            AppNavigationMode.topWhatsAppBar
        "cards", "one ui" -> AppNavigationMode.oneUi
        "chaty default", "whatsapp ui stock" -> AppNavigationMode.bottomNav
        "stories first", "ios style" -> AppNavigationMode.iosStyle
        "instagram style" -> AppNavigationMode.instagramStyle
        "telegram style" -> AppNavigationMode.telegramStyle
        "floating rail" -> AppNavigationMode.floatingIslandRail
        "modern side menu" -> AppNavigationMode.modernSideMenu
        "compact", "bubbles tab style" -> AppNavigationMode.gestureTabs
        "productivity", "basic tab style" -> AppNavigationMode.basicTabStyle
        "3d perspective drawer" -> AppNavigationMode.perspective3DDrawer
        "curved radial menu" -> AppNavigationMode.curvedRadialDrawer
        else -> null
    }

    /** App presentation mapping is separate from the legacy Dart resolver contract. */
    fun presentationNavigationMode(value: String): AppNavigationMode? =
        navigationMode(com.example.chat.core.preferences.HomePresentationPolicy.navigationStyle(value))

    /** True only for an explicit One UI / Cards pick; the Default presentation
     *  and stock WhatsApp UI never get the raised-centre scrolling dock. */
    fun oneUiRaisedCenterDockEnabled(homeStyle: String?): Boolean =
        homeStyle != null && com.example.chat.core.preferences.HomePresentationPolicy.isOneUi(homeStyle)

    /** Dock-variant name the bottom shell renders for an explicit One UI pick. */
    const val RAISED_CENTER_SCROLL_DOCK = "OneUiRaisedCenterScroll"

    /** Which dock the bottom shell renders: the raised-centre scrolling dock only
     *  when the One UI flag is set; every other configuration keeps its configured
     *  named dock style. Pure so the mapping/renderer contract is testable without
     *  composition. */
    fun resolvedBottomDockVariant(styleName: String, oneUiRaisedCenterDock: Boolean): String =
        if (oneUiRaisedCenterDock) RAISED_CENTER_SCROLL_DOCK else styleName

}
