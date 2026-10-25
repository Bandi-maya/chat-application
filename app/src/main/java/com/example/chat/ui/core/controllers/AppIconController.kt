package com.example.chat.ui.core.controllers

/**
 * Contract for the Android launcher-icon controller, ported from
 * lib/ui/core/controllers/app_icon_controller.dart (enums + variant metadata
 * verbatim). The controller IMPLEMENTATION (activity-alias bridge via
 * PackageManager) is owned by the app-icon worker / integration owner; this
 * file is the shared seam so consumers (Universal styles screen, app icon
 * settings screen) compile against one canonical contract. Pure JVM.
 */

enum class LauncherIconVariant { bird, warm, outline, obsidian, glass, signal, fold }

enum class BrandIconSource { bundled, custom }

/** Port of LauncherIconVariantMetadata extension. */
val LauncherIconVariant.id: String get() = name

val LauncherIconVariant.title: String
    get() = when (this) {
        LauncherIconVariant.bird -> "Swift Flight"
        LauncherIconVariant.warm -> "Warm Signature"
        LauncherIconVariant.outline -> "Warm Outline"
        LauncherIconVariant.obsidian -> "Obsidian"
        LauncherIconVariant.glass -> "Spatial Glass"
        LauncherIconVariant.signal -> "Signal"
        LauncherIconVariant.fold -> "Fold"
    }

val LauncherIconVariant.subtitle: String
    get() = when (this) {
        LauncherIconVariant.bird -> "Minimalist aerodynamic soaring bird silhouette"
        LauncherIconVariant.warm -> "Warm Neutral ivory field with soft-depth dimensional Chaty mark"
        LauncherIconVariant.outline -> "Clean editorial graphite contour on warm canvas"
        LauncherIconVariant.obsidian -> "Near-black field with metallic ivory communication glyph"
        LauncherIconVariant.glass -> "Layered translucent glass facets with soft reflections"
        LauncherIconVariant.signal -> "Warm presence pulse geometry with core Chaty mark"
        LauncherIconVariant.fold -> "Origami dimensional folded conversation surfaces"
    }

val LauncherIconVariant.androidAlias: String get() = name

fun launcherIconVariantFromId(value: String?): LauncherIconVariant = when (value) {
    "bird", "swift" -> LauncherIconVariant.bird
    "original" -> LauncherIconVariant.warm
    "minimal" -> LauncherIconVariant.outline
    "midnight" -> LauncherIconVariant.obsidian
    "bubble" -> LauncherIconVariant.glass
    "ocean" -> LauncherIconVariant.signal
    "violet" -> LauncherIconVariant.fold
    else -> LauncherIconVariant.entries.firstOrNull { it.id == value } ?: LauncherIconVariant.bird
}

/**
 * Seam matching the public surface of the Dart AppIconController consumed by
 * the Universal styles screen. Dart access was optional (locator.isRegistered);
 * Kotlin consumers receive this as a nullable reference with the same semantics.
 */
interface AppIconControllerContract {
    val launcherIcon: LauncherIconVariant
    val brandIconSource: BrandIconSource
    val isBusy: Boolean
    val lastError: String?

    suspend fun initialize()
    suspend fun refreshNativeLauncherState(notify: Boolean = true)

    /** Returns true when the launcher confirmed the alias switch. */
    suspend fun applyLauncherIcon(variant: LauncherIconVariant): Boolean

    fun addListener(listener: () -> Unit)
    fun removeListener(listener: () -> Unit)
}
