package com.example.chat.ui.settings.gb

import com.example.chat.core.preferences.GbFeatureCatalog
import com.example.chat.core.preferences.GbFeatureDefinition
import com.example.chat.core.preferences.GbFeatureKind
import java.util.Locale

/** Pure logic of gb_feature_center_screen.dart. */
object GbFeatureCenterLogic {
    /** Dart build(): category match, then case-insensitive contains on title/description/category/key. */
    fun filter(all: List<GbFeatureDefinition>, query: String, category: String?): List<GbFeatureDefinition> {
        val q = query.lowercase()
        return all.filter { item ->
            if (category != null && item.category != category) return@filter false
            if (q.isEmpty()) return@filter true
            item.title.lowercase().contains(q) || item.description.lowercase().contains(q) ||
                item.category.lowercase().contains(q) || item.key.lowercase().contains(q)
        }
    }

    val stealthBundle: Map<String, Any?> = linkedMapOf(
        "yo_want_ghostmode" to true, "yoHideSeen" to true, "yoHideStatViewV2" to true,
        "abu_saleh_toast_typing" to false, "abu_saleh_toast_online" to false, "always_online" to false,
    )
    val standardBundle: Map<String, Any?> = linkedMapOf(
        "yo_want_ghostmode" to false, "yo_want_airplanemode" to false, "yoHideSeen" to false,
        "yoHideStatViewV2" to false, "always_online" to false,
    )

    val colorPresets: List<Long> = listOf(
        0xFF000000, 0xFFFFFFFF, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777, 0xFFDC2626,
        0xFFEA580C, 0xFFCA8A04, 0xFF16A34A, 0xFF0891B2, 0xFF475569, 0xFF18181B,
    )

    /** Dart: current == 0 ? '' : '#' + hex(8, upper). */
    fun initialColorText(current: Long): String =
        if (current == 0L) "" else "#" + java.lang.Long.toHexString(current and 0xFFFFFFFFL).padStart(8, '0').uppercase(Locale.ROOT)

    /** Dart Apply button: strip first '#', then first '0x', 6 digits get FF prefix, radix-16 parse or null. */
    fun parseArgbHex(input: String): Long? {
        var text = input.trim().replaceFirst("#", "").replaceFirst("0x", "")
        if (text.length == 6) text = "FF$text"
        return text.toLongOrNull(16)
    }

    /** Dart slider label. */
    fun sliderLabel(value: Double): String =
        if (value >= 100) Math.round(value).toString()
        else String.format(Locale.US, if (value % 1.0 == 0.0) "%.0f" else "%.1f", value)

    enum class CategoryIcon { Privacy, Call, Media, Status, Notification, Text, Palette, Composer, Chat, Home, Navigation, Storage, Tune }

    fun categoryIcon(category: String): CategoryIcon {
        val v = category.lowercase()
        return when {
            v.contains("privacy") -> CategoryIcon.Privacy
            v.contains("call") -> CategoryIcon.Call
            v.contains("media") -> CategoryIcon.Media
            v.contains("status") -> CategoryIcon.Status
            v.contains("notification") || v.contains("alert") -> CategoryIcon.Notification
            v.contains("font") || v.contains("icon") -> CategoryIcon.Text
            v.contains("color") || v.contains("appearance") -> CategoryIcon.Palette
            v.contains("composer") -> CategoryIcon.Composer
            v.contains("bubble") || v.contains("conversation") -> CategoryIcon.Chat
            v.contains("home") -> CategoryIcon.Home
            v.contains("navigation") -> CategoryIcon.Navigation
            v.contains("storage") -> CategoryIcon.Storage
            else -> CategoryIcon.Tune
        }
    }

    fun enabledInCategory(items: List<GbFeatureDefinition>, gbBool: (String, Boolean) -> Boolean): Int =
        items.count { it.kind == GbFeatureKind.toggle && gbBool(it.key, it.defaultValue == true) }

    fun enabledTotal(gbFeatures: Map<String, Any?>): Int = gbFeatures.values.count { it is Boolean && it }
}
