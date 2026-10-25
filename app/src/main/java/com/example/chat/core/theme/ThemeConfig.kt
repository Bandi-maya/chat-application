package com.example.chat.core.theme

/** Exact port of lib/ui/core/theme/theme_config.dart (ThemeConfig) as a
 *  pure-Kotlin model: colors are 32-bit ARGB Longs (Color.toARGB32 on the
 *  Dart side). The Compose theme adapter lands with the UI slice;
 *  toThemeData's Flutter ThemeData construction is not part of this model. */

enum class UILayoutMode { classic, compact, expressive, focus, tabletDesktop }

enum class AppNavigationMode {
    bottomNav,
    oneUi,
    iosStyle,
    instagramStyle,
    telegramStyle,
    basicTabStyle,
    topWhatsAppBar,
    floatingIslandRail,
    perspective3DDrawer,
    modernSideMenu,
    curvedRadialDrawer,
    gestureTabs,
    compactRail,
}

/** WCAG relative luminance of a 32-bit ARGB color, matching Flutter's
 *  Color.computeLuminance. */
fun argbLuminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** Flutter Color.withValues(alpha: a) — returns ARGB with replaced alpha. */
fun argbWithAlpha(argb: Long, alpha: Double): Long {
    val a = (alpha.coerceIn(0.0, 1.0) * 255).toInt() and 0xFF
    return (argb and 0x00FFFFFFL) or (a.toLong() shl 24)
}

data class ThemeConfig(
    val id: String,
    val name: String,
    /** Dart Brightness serialized as 'light'/'dark'; true = dark. */
    val isDark: Boolean,
    val accentColor: Long,
    val backgroundColor: Long,
    val surfaceColor: Long,
    val cardColor: Long,
    val primaryTextColor: Long,
    val secondaryTextColor: Long,
    val outgoingBubbleColor: Long,
    val incomingBubbleColor: Long,
    val outgoingTextColor: Long,
    val incomingTextColor: Long,
    val linkColor: Long,
    val dangerColor: Long,
    val successColor: Long,
    val cornerRadius: Double = 16.0,
    val density: Double = 1.0,
    val fontScale: Double = 1.0,
    val navigationMode: AppNavigationMode = AppNavigationMode.bottomNav,
    val layoutMode: UILayoutMode = UILayoutMode.classic,
    val bubbleStyle: BubbleStyleId = BubbleStyleId.stock,
    val deliveryTickStyle: DeliveryIconStyle = DeliveryIconStyle.rcIos11,
    val tickStyle: String = "RC iOS 11",
    val wallpaperId: String = "subtle_dots",
    val animationLevel: Double = 1.0,
    val highContrast: Boolean = false,
) {
    /** Fraction of the available width a chat bubble may occupy, per layout. */
    val bubbleMaxWidthFactor: Double
        get() = when (layoutMode) {
            UILayoutMode.classic -> 0.80
            UILayoutMode.compact -> 0.72
            UILayoutMode.expressive -> 0.86
            UILayoutMode.focus -> 0.66
            UILayoutMode.tabletDesktop -> 0.80
        }

    val onAccentColor: Long
        get() = if (argbLuminance(accentColor) > 0.5) 0xFF000000L else 0xFFFFFFFFL
    val onSurfaceColor: Long get() = primaryTextColor
    val onBackgroundColor: Long get() = primaryTextColor
    val dividerColor: Long get() = argbWithAlpha(secondaryTextColor, 0.2)
    val scaffoldBackgroundColor: Long get() = backgroundColor

    /** Dart copyWith with its fontScale guard/clamp. */
    fun copyWith(
        id: String? = null,
        name: String? = null,
        isDark: Boolean? = null,
        accentColor: Long? = null,
        backgroundColor: Long? = null,
        surfaceColor: Long? = null,
        cardColor: Long? = null,
        primaryTextColor: Long? = null,
        secondaryTextColor: Long? = null,
        outgoingBubbleColor: Long? = null,
        incomingBubbleColor: Long? = null,
        outgoingTextColor: Long? = null,
        incomingTextColor: Long? = null,
        linkColor: Long? = null,
        dangerColor: Long? = null,
        successColor: Long? = null,
        cornerRadius: Double? = null,
        density: Double? = null,
        fontScale: Double? = null,
        highContrast: Boolean? = null,
        navigationMode: AppNavigationMode? = null,
        layoutMode: UILayoutMode? = null,
        bubbleStyle: BubbleStyleId? = null,
        deliveryTickStyle: DeliveryIconStyle? = null,
        tickStyle: String? = null,
        wallpaperId: String? = null,
        animationLevel: Double? = null,
    ): ThemeConfig = copy(
        id = id ?: this.id,
        name = name ?: this.name,
        isDark = isDark ?: this.isDark,
        accentColor = accentColor ?: this.accentColor,
        backgroundColor = backgroundColor ?: this.backgroundColor,
        surfaceColor = surfaceColor ?: this.surfaceColor,
        cardColor = cardColor ?: this.cardColor,
        primaryTextColor = primaryTextColor ?: this.primaryTextColor,
        secondaryTextColor = secondaryTextColor ?: this.secondaryTextColor,
        outgoingBubbleColor = outgoingBubbleColor ?: this.outgoingBubbleColor,
        incomingBubbleColor = incomingBubbleColor ?: this.incomingBubbleColor,
        outgoingTextColor = outgoingTextColor ?: this.outgoingTextColor,
        incomingTextColor = incomingTextColor ?: this.incomingTextColor,
        linkColor = linkColor ?: this.linkColor,
        dangerColor = dangerColor ?: this.dangerColor,
        successColor = successColor ?: this.successColor,
        cornerRadius = cornerRadius ?: this.cornerRadius,
        density = density ?: this.density,
        fontScale = if (fontScale != null && fontScale > 0.0 && fontScale.isFinite())
            fontScale.coerceIn(0.5, 2.0)
        else
            this.fontScale,
        highContrast = highContrast ?: this.highContrast,
        navigationMode = navigationMode ?: this.navigationMode,
        layoutMode = layoutMode ?: this.layoutMode,
        bubbleStyle = bubbleStyle ?: this.bubbleStyle,
        deliveryTickStyle = deliveryTickStyle ?: this.deliveryTickStyle,
        tickStyle = tickStyle ?: this.tickStyle,
        wallpaperId = wallpaperId ?: this.wallpaperId,
        animationLevel = animationLevel ?: this.animationLevel,
    )

    /** Dynamically inverts brightness and adapts all colors without
     *  hardcoding presets (exact port). */
    fun toggleBrightness(): ThemeConfig {
        if (isDark) {
            val adaptedAccent = if (argbLuminance(accentColor) > 0.85) 0xFF09090BL else accentColor
            return copyWith(
                isDark = false,
                backgroundColor = 0xFFFFFFFFL,
                surfaceColor = 0xFFF4F4F5L,
                cardColor = 0xFFFFFFFFL,
                primaryTextColor = 0xFF09090BL,
                secondaryTextColor = 0xFF71717AL,
                accentColor = adaptedAccent,
                incomingBubbleColor = 0xFFF4F4F5L,
                incomingTextColor = 0xFF09090BL,
                outgoingBubbleColor = adaptedAccent,
                outgoingTextColor = if (argbLuminance(adaptedAccent) > 0.5) 0xFF000000L else 0xFFFFFFFFL,
            )
        } else {
            val adaptedAccent = if (argbLuminance(accentColor) < 0.15) 0xFFFFFFFFL else accentColor
            return copyWith(
                isDark = true,
                backgroundColor = 0xFF000000L,
                surfaceColor = 0xFF121212L,
                cardColor = 0xFF1C1C1EL,
                primaryTextColor = 0xFFFFFFFFL,
                secondaryTextColor = 0xFFA1A1AAL,
                accentColor = adaptedAccent,
                incomingBubbleColor = 0xFF18181BL,
                incomingTextColor = 0xFFF4F4F5L,
                outgoingBubbleColor = if (argbLuminance(adaptedAccent) > 0.85) 0xFF27272AL else adaptedAccent,
                outgoingTextColor = 0xFFFFFFFFL,
            )
        }
    }

    val hasContrastIssue: Boolean
        get() {
            val textRatio = contrastRatio(primaryTextColor, backgroundColor)
            val secondaryTextRatio = contrastRatio(secondaryTextColor, backgroundColor)
            val textOnSurfaceRatio = contrastRatio(primaryTextColor, surfaceColor)
            val outgoingBubbleRatio = contrastRatio(outgoingTextColor, outgoingBubbleColor)
            val incomingBubbleRatio = contrastRatio(incomingTextColor, incomingBubbleColor)
            return textRatio < 4.5 ||
                secondaryTextRatio < 3.0 ||
                textOnSurfaceRatio < 3.5 ||
                outgoingBubbleRatio < 3.5 ||
                incomingBubbleRatio < 3.5
        }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "brightness" to (if (isDark) "dark" else "light"),
        "accentColor" to accentColor,
        "backgroundColor" to backgroundColor,
        "surfaceColor" to surfaceColor,
        "cardColor" to cardColor,
        "primaryTextColor" to primaryTextColor,
        "secondaryTextColor" to secondaryTextColor,
        "outgoingBubbleColor" to outgoingBubbleColor,
        "incomingBubbleColor" to incomingBubbleColor,
        "outgoingTextColor" to outgoingTextColor,
        "incomingTextColor" to incomingTextColor,
        "linkColor" to linkColor,
        "dangerColor" to dangerColor,
        "successColor" to successColor,
        "cornerRadius" to cornerRadius,
        "density" to density,
        "fontScale" to fontScale,
        "highContrast" to highContrast,
        "navigationMode" to navigationMode.name,
        "layoutMode" to layoutMode.name,
        "bubbleStyle" to bubbleStyle.name,
        "deliveryTickStyle" to deliveryTickStyle.name,
        "tickStyle" to tickStyle,
        "wallpaperId" to wallpaperId,
        "animationLevel" to animationLevel,
    )

    companion object {
        /** (L1 + 0.05) / (L2 + 0.05) */
        fun contrastRatio(foreground: Long, background: Long): Double {
            val lum1 = argbLuminance(foreground)
            val lum2 = argbLuminance(background)
            val brightest = maxOf(lum1, lum2)
            val darkest = minOf(lum1, lum2)
            return (brightest + 0.05) / (darkest + 0.05)
        }

        /** Corruption-tolerant read: every invalid field falls back to the
         *  monochrome-dark family defaults, exactly like the Dart factory. */
        fun fromMap(map: Map<String, Any?>): ThemeConfig {
            val bubbleStyleRaw = map["bubbleStyle"] as String?
            val tickStyleRaw = (map["deliveryTickStyle"] as String?) ?: (map["tickStyle"] as String?)
            return ThemeConfig(
                id = (map["id"] as? String)?.takeIf { it.isNotEmpty() } ?: "monochromeDark",
                name = (map["name"] as? String)?.takeIf { it.isNotEmpty() } ?: "Monochrome Dark",
                isDark = map["brightness"] != "light",
                accentColor = colorOf(map["accentColor"], 0xFFFFFFFFL),
                backgroundColor = colorOf(map["backgroundColor"], 0xFF000000L),
                surfaceColor = colorOf(map["surfaceColor"], 0xFF121212L),
                cardColor = colorOf(map["cardColor"], 0xFF1C1C1EL),
                primaryTextColor = colorOf(map["primaryTextColor"], 0xFFFFFFFFL),
                secondaryTextColor = colorOf(map["secondaryTextColor"], 0xFFA1A1AAL),
                outgoingBubbleColor = colorOf(map["outgoingBubbleColor"], 0xFF27272AL),
                incomingBubbleColor = colorOf(map["incomingBubbleColor"], 0xFF18181BL),
                outgoingTextColor = colorOf(map["outgoingTextColor"], 0xFFFFFFFFL),
                incomingTextColor = colorOf(map["incomingTextColor"], 0xFFF4F4F5L),
                linkColor = colorOf(map["linkColor"], 0xFF60A5FAL),
                dangerColor = colorOf(map["dangerColor"], 0xFFEF4444L),
                successColor = colorOf(map["successColor"], 0xFF22C55EL),
                cornerRadius = doubleOf(map["cornerRadius"], 16.0),
                density = doubleOf(map["density"], 1.0),
                fontScale = doubleOf(map["fontScale"], 1.0).coerceIn(0.5, 2.0),
                highContrast = map["highContrast"] == true,
                navigationMode = enumByName<AppNavigationMode>(map["navigationMode"], AppNavigationMode.bottomNav),
                layoutMode = enumByName<UILayoutMode>(map["layoutMode"], UILayoutMode.classic),
                bubbleStyle = bubbleStyleIdFromString(bubbleStyleRaw),
                deliveryTickStyle = deliveryIconStyleFromString(tickStyleRaw),
                tickStyle = tickStyleRaw ?: "RC iOS 11",
                wallpaperId = (map["wallpaperId"] as? String) ?: "subtle_dots",
                animationLevel = doubleOf(map["animationLevel"], 1.0).coerceIn(0.0, 1.0),
            )
        }

        private fun colorOf(value: Any?, fallback: Long): Long {
            if (value is Number) return value.toLong()
            if (value is String) value.toLongOrNull()?.let { return it }
            return fallback
        }

        private fun doubleOf(value: Any?, fallback: Double): Double {
            if (value is Number) return value.toDouble()
            if (value is String) value.toDoubleOrNull()?.let { return it }
            return fallback
        }

        private inline fun <reified T : Enum<T>> enumByName(name: Any?, fallback: T): T {
            if (name is String) {
                for (v in enumValues<T>()) {
                    if (v.name == name) return v
                }
            }
            return fallback
        }
    }
}
