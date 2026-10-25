package com.example.chat.ui.settings.home

import com.example.chat.core.theme.AppNavigationMode

/** Pure tables/logic of HeaderSettingsScreen (header_settings_screen.dart): home UI style list,
 *  style -> navigation mode, saved homeStyle -> selected style, descriptions. The Dart
 *  AppNavigationMode.compactRail case maps to BASIC TAB STYLE (enum member added by owner).
 */
object HomeUiStyles {
    val homeUiStyles = listOf(
        "ONE UI", "WhatsApp UI Stock", "IOS STYLE", "BUBBLES TAB STYLE", "BASIC TAB STYLE",
        "WhatsApp OLD UI", "Floating Rail", "3D Perspective Drawer", "Modern Side Menu",
        "Curved Radial Menu", "Instagram Style", "Telegram Style",
    )
    val storiesStyles = listOf("Instagram", "Facebook", "Stock", "Circular", "Squircle", "Card", "Wide Card")
    val pager3dStyles = listOf("Cube 3D", "Accordion Fold", "Card Flip", "Depth Zoom", "Stack Rotate")
    val tabBubbleStyles = listOf("Capsule Pill", "Glassmorphism Glow", "Soft Rounded Pill", "Outlined Bubble", "Segmented Tab")
    val searchPlacementOptions = listOf("Header action", "Below header", "Above chat filters")

    /** [current] = ThemeController.navigationMode, returned by Dart's default branch. */
    fun navigationModeForHomeUiStyle(style: String, current: AppNavigationMode): AppNavigationMode =
        when (style.trim().lowercase()) {
            "one ui" -> AppNavigationMode.oneUi
            "whatsapp ui stock" -> AppNavigationMode.bottomNav
            "ios style" -> AppNavigationMode.iosStyle
            "bubbles tab style" -> AppNavigationMode.gestureTabs
            "basic tab style" -> AppNavigationMode.basicTabStyle
            "whatsapp old ui", "whatsapp-style top bar", "whatsapp style top bar" -> AppNavigationMode.topWhatsAppBar
            "floating rail" -> AppNavigationMode.floatingIslandRail
            "3d perspective drawer" -> AppNavigationMode.perspective3DDrawer
            "modern side menu" -> AppNavigationMode.modernSideMenu
            "curved radial menu" -> AppNavigationMode.curvedRadialDrawer
            "instagram style" -> AppNavigationMode.instagramStyle
            "telegram style" -> AppNavigationMode.telegramStyle
            else -> current
        }

    fun selectedHomeUiStyle(homeStyle: String, current: AppNavigationMode): String {
        val saved = homeStyle.trim()
        if (saved in homeUiStyles) return saved
        when (saved.lowercase()) {
            "classic" -> return "WhatsApp OLD UI"
            "chaty default" -> return "WhatsApp UI Stock"
            "cards" -> return "ONE UI"
            "stories first" -> return "IOS STYLE"
            "compact" -> return "BUBBLES TAB STYLE"
            "productivity" -> return "BASIC TAB STYLE"
        }
        return when (current) {
            AppNavigationMode.oneUi -> "ONE UI"
            AppNavigationMode.iosStyle -> "IOS STYLE"
            AppNavigationMode.instagramStyle -> "Instagram Style"
            AppNavigationMode.telegramStyle -> "Telegram Style"
            AppNavigationMode.basicTabStyle -> "BASIC TAB STYLE"
            AppNavigationMode.topWhatsAppBar -> "WhatsApp OLD UI"
            AppNavigationMode.floatingIslandRail -> "Floating Rail"
            AppNavigationMode.perspective3DDrawer -> "3D Perspective Drawer"
            AppNavigationMode.modernSideMenu -> "Modern Side Menu"
            AppNavigationMode.curvedRadialDrawer -> "Curved Radial Menu"
            AppNavigationMode.gestureTabs -> "BUBBLES TAB STYLE"
            AppNavigationMode.compactRail -> "BASIC TAB STYLE"
            AppNavigationMode.bottomNav -> "WhatsApp UI Stock"
        }
    }

    fun description(style: String): String = when (style) {
        "ONE UI" -> "Large title, spacious rows, and One UI bottom navigation"
        "WhatsApp UI Stock" -> "Classic conversation rows with a standard bottom bar"
        "IOS STYLE" -> "Minimal iOS-inspired icon dock and airy stories header"
        "BUBBLES TAB STYLE" -> "Swipe-first pages with circular bubble destinations"
        "BASIC TAB STYLE" -> "Compact square icon-and-label tabs"
        "WhatsApp OLD UI" -> "Classic WhatsApp top tabs aligned with the header"
        "Floating Rail" -> "Floating vertical icon rail"
        "3D Perspective Drawer" -> "Perspective-shifted content with a side drawer"
        "Modern Side Menu" -> "Labeled side drawer with identity header"
        "Curved Radial Menu" -> "Circular icon positions inside a themed curved menu"
        "Instagram Style" -> "Clean icon-only bottom dock with creator-style hierarchy"
        "Telegram Style" -> "Compact labeled navigation dock inspired by Telegram"
        else -> "Home layout and navigation preview"
    }
}
