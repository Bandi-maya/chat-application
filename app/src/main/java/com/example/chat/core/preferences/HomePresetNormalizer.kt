package com.example.chat.core.preferences

/** Exact port of the label-canonicalizing half of
 *  lib/ui/core/controllers/home_preset_normalizer.dart. The navigationMode
 *  half resolves UI shell modes and lands with the M7 theming slice. */
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

    fun storiesStyle(value: String): String = when (value.trim().lowercase()) {
        "cards", "card" -> "Card"
        "squircle" -> "Squircle"
        "compact" -> "Compact"
        "minimal" -> "Minimal"
        "instagram", "facebook", "stock", "circular" -> "Circular"
        else -> "Circular"
    }
}
