package com.example.chat.ui.settings.universal

import com.example.chat.ui.core.controllers.BrandIconSource
import com.example.chat.ui.core.controllers.LauncherIconVariant
import com.example.chat.ui.core.controllers.title

/**
 * Pure catalog data for the Universal styles screen, ported verbatim from
 * lib/features/settings/universal/universal_styles_screen.dart. Colors are
 * ARGB longs; icon keys are the Dart Icons.* getter names, mapped to Material
 * icons in the Compose layer.
 */

data class LauncherIconEntry(
    val name: String,
    val variant: LauncherIconVariant,
    val colorArgb: Long,
    val iconKey: String,
)

data class FontStyleEntry(
    val name: String,
    val fontFamilyKey: String?,   // Dart generic family: null = default, else sans-serif/-condensed/cursive/monospace/sans-serif-rounded
    val weight: Int,              // FontWeight w100..w900 (Dart normal=400)
    val italic: Boolean = false,
    val letterSpacing: Double = 0.0,
)

object UniversalStylesCatalog {

    // Each visible option maps to one distinct Android launcher alias.
    // Do not map multiple labels to a shared alias: it makes the UI claim a
    // different icon was selected even though Android displays the same artwork.
    val launcherIcons: List<LauncherIconEntry> = listOf(
        LauncherIconEntry("Swift Flight", LauncherIconVariant.bird, 0xFF25D366, "air_rounded"),
        LauncherIconEntry("Warm Signature", LauncherIconVariant.warm, 0xFFB18B67, "chat_bubble_rounded"),
        LauncherIconEntry("Warm Outline", LauncherIconVariant.outline, 0xFFD8C7B2, "chat_bubble_outline_rounded"),
        LauncherIconEntry("Obsidian", LauncherIconVariant.obsidian, 0xFF202124, "mode_comment_rounded"),
        LauncherIconEntry("Spatial Glass", LauncherIconVariant.glass, 0xFF75D5D9, "bubble_chart_rounded"),
        LauncherIconEntry("Signal", LauncherIconVariant.signal, 0xFF18B5D1, "graphic_eq_rounded"),
        LauncherIconEntry("Fold", LauncherIconVariant.fold, 0xFFEF5AB7, "layers_rounded"),
    )

    val fontStyles: List<FontStyleEntry> = listOf(
        FontStyleEntry("Default", null, 400),
        FontStyleEntry("Roboto-Light", null, 300),
        FontStyleEntry("Roboto-Medium", null, 600),
        FontStyleEntry("ProductSans", "sans-serif", 500, letterSpacing = 0.2),
        FontStyleEntry("FrutigerLTStdRoman", "sans-serif", 400),
        FontStyleEntry("Bariol", "sans-serif-condensed", 400),
        FontStyleEntry("ComicSans", "cursive", 500),
        FontStyleEntry("BEBASNEUE", null, 900, letterSpacing = 1.5),
        FontStyleEntry("Comfortaa", "sans-serif-rounded", 600),
        FontStyleEntry("TRANSFORMERS", "monospace", 800, letterSpacing = 1.2),
        FontStyleEntry("HappyGiraffe", null, 500, italic = true),
    )

    /** Dart: _showAllLauncherIcons ? all : take(6). */
    fun displayedLauncherIcons(showAll: Boolean): List<LauncherIconEntry> =
        if (showAll) launcherIcons else launcherIcons.take(6)

    /** Dart grid sizing: width < 380 ? 4 : 5 columns. */
    fun launcherGridColumns(widthDp: Int): Int = if (widthDp < 380) 4 else 5

    /** Dart grid aspect: width < 380 ? 0.62 : 0.78. */
    fun launcherGridAspect(widthDp: Int): Float = if (widthDp < 380) 0.62f else 0.78f

    /**
     * Dart _initializeAndSyncLauncherIcon reconcile: when the brand source is
     * bundled and the stored preference label differs from the controller's
     * confirmed variant title, the preference must be corrected so a stale
     * legacy label does not claim an icon the system is not showing.
     * Returns the corrected preference value, or null when no change is needed
     * (including when the source is not bundled, where Dart returns early).
     */
    fun reconcileLauncherIconPref(
        currentPref: String,
        confirmedVariant: LauncherIconVariant,
        source: BrandIconSource,
    ): String? {
        if (source != BrandIconSource.bundled) return null
        val title = confirmedVariant.title
        return if (currentPref != title) title else null
    }
}

/**
 * Pure custom-font install logic, ported from the Load Font row of
 * universal_styles_screen.dart: validate non-empty file, copy into
 * docs/custom_font_<name>. JVM-testable.
 */
class CustomFontLogic(private val docsDir: () -> java.io.File) {

    sealed class Result {
        data class Loaded(val fileName: String, val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    fun install(source: java.io.File, fileName: String): Result {
        if (!source.exists() || source.length() == 0L) {
            return Result.Failure("Invalid or empty font file.")
        }
        return try {
            val dest = java.io.File(docsDir(), "custom_font_$fileName")
            source.copyTo(dest, overwrite = true)
            Result.Loaded(fileName, "Custom font \"$fileName\" loaded successfully!")
        } catch (e: Exception) {
            Result.Failure("Failed to load font: $e")
        }
    }
}
