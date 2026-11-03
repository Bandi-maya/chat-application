package com.example.chat.core.preferences

/** Presentation only: never rewrite saved style IDs when swapping default/One UI layouts. */
object HomePresentationPolicy {
    /** Preserve explicit navigation choices in the legacy GB settings write path. */
    fun storedStyle(value: String): String {
        val named = listOf("Chaty Default", "ONE UI", "WhatsApp UI Stock", "IOS STYLE", "BUBBLES TAB STYLE",
            "BASIC TAB STYLE", "WhatsApp OLD UI", "Floating Rail", "3D Perspective Drawer", "Modern Side Menu",
            "Curved Radial Menu", "Instagram Style", "Telegram Style")
        return named.firstOrNull { it.equals(value.trim(), ignoreCase = true) }
            ?: if (isDefault(value)) "Chaty Default" else HomePresetNormalizer.homeStyle(value)
    }
    fun isDefault(value: String): Boolean = value.trim().lowercase() in setOf("chaty", "default", "chaty default")
    fun isOneUi(value: String): Boolean = value.trim().lowercase() in setOf("cards", "one ui")
    fun listStyle(value: String): String = when {
        isDefault(value) -> "Cards"
        isOneUi(value) -> "Chaty Default"
        else -> HomePresetNormalizer.homeStyle(value)
    }
    fun navigationStyle(value: String): String = when {
        isDefault(value) -> "ONE UI"
        isOneUi(value) -> "WhatsApp UI Stock"
        else -> value
    }

    /** Separation forces Instagram stories without overwriting the user's stored story choice. */
    fun effectiveStoriesStyle(separateChatsAndGroups: Boolean, storedStyle: String): String =
        if (separateChatsAndGroups) "Instagram" else storedStyle

    fun showStoriesStrip(separateChatsAndGroups: Boolean, enabled: Boolean, styleStoriesFirst: Boolean): Boolean =
        separateChatsAndGroups || enabled || styleStoriesFirst

}
