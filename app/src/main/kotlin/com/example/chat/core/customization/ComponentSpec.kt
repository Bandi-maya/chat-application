package com.example.chat.core.customization

/** Port of lib/ui/core/customization/chaty_component_spec.dart, component_capabilities.dart and
 *  performance_profile.dart (api 3bee780d). Distinct from the app-level core/templates engine. */

/** All canonical customizable component families in Chaty (declaration order preserved; Dart `.name` == [name]). */
enum class ComponentId {
    navigation, header, composer, bubble, tick, chatList, reactionRail, emojiPicker, attachmentTray,
    modal, sheet, actionSheet, contextMenu, toast, emptyState, button, textField, avatar, wallpaper,
    callCapsule, pip, cameraControls,
}

/** Declares the estimated rendering complexity of a visual variant. */
enum class PerformanceCost { low, medium, high }

data class PerformanceProfile(
    val cost: PerformanceCost = PerformanceCost.low,
    val allowsBackdropFilter: Boolean = true,
    val allowsBoxShadows: Boolean = true,
    val allowsParticles: Boolean = true,
    val allowsContinuousAnimation: Boolean = true,
) {
    companion object {
        val lowCost = PerformanceProfile(PerformanceCost.low, false, true, false, false)
        val standard = PerformanceProfile(PerformanceCost.medium, true, true, true, false)
        val highFidelity = PerformanceProfile(PerformanceCost.high, true, true, true, true)
    }
}

/** Flutter Brightness. */
enum class Brightness { light, dark }

data class ComponentCapabilities(
    val compactWidth: Boolean = true,
    val expandedWidth: Boolean = true,
    val dark: Boolean = true,
    val light: Boolean = true,
    val highContrast: Boolean = true,
    val largeText: Boolean = true,
    val reducedMotion: Boolean = true,
    val pointerInput: Boolean = true,
    val keyboardInput: Boolean = true,
    val touchInput: Boolean = true,
    val rtl: Boolean = true,
    val dynamicColor: Boolean = true,
) {
    fun satisfies(required: ComponentCapabilities): Boolean {
        if (required.compactWidth && !compactWidth) return false
        if (required.expandedWidth && !expandedWidth) return false
        if (required.dark && !dark) return false
        if (required.light && !light) return false
        if (required.highContrast && !highContrast) return false
        if (required.largeText && !largeText) return false
        if (required.reducedMotion && !reducedMotion) return false
        if (required.pointerInput && !pointerInput) return false
        if (required.keyboardInput && !keyboardInput) return false
        if (required.touchInput && !touchInput) return false
        if (required.rtl && !rtl) return false
        if (required.dynamicColor && !dynamicColor) return false
        return true
    }

    companion object { val universal = ComponentCapabilities() }
}

/** Runtime context evaluated by the variant resolver. Compose-side factory: see [rememberVariantContext]. */
data class VariantContext(
    val availableWidth: Double,
    val availableHeight: Double,
    val brightness: Brightness,
    val isHighContrast: Boolean = false,
    val isReducedMotion: Boolean = false,
    val textScaleFactor: Double = 1.0,
    val isRtl: Boolean = false,
) {
    val isCompact: Boolean get() = availableWidth < 600.0
    val isMedium: Boolean get() = availableWidth >= 600.0 && availableWidth < 840.0
    val isExpanded: Boolean get() = availableWidth >= 840.0
}

/** Metadata contract describing a specific visual variant in a component family. */
data class ComponentSpec(
    val componentId: ComponentId,
    val variantId: String,
    val displayName: String,
    val description: String = "",
    val capabilities: ComponentCapabilities = ComponentCapabilities.universal,
    val performanceCost: PerformanceCost = PerformanceCost.low,
    val defaultMotionProfile: String = "standard",
) {
    fun supports(context: VariantContext): Boolean {
        if (context.isCompact && !capabilities.compactWidth) return false
        if (context.isExpanded && !capabilities.expandedWidth) return false
        if (context.isReducedMotion && !capabilities.reducedMotion) return false
        if (context.isHighContrast && !capabilities.highContrast) return false
        if (context.isRtl && !capabilities.rtl) return false
        return true
    }
}
