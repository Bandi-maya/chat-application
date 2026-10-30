package com.example.chat.ui.calls.bundles

import androidx.compose.ui.graphics.Color

/**
 * The Six selectable Call Experience Bundles for Chaty.
 */
enum class CallBundleId(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val badge: String? = null,
) {
    CHATY_DEFAULT(
        id = "chaty_default",
        displayName = "Chaty Default",
        subtitle = "Speaker-focused video, participant grid, invitation screen & compact call cards",
        badge = "Default",
    ),
    APPLE_IOS(
        id = "apple_ios",
        displayName = "Apple iOS Call Experience",
        subtitle = "Lock-screen slider, incoming/outgoing screens, active call controls & iOS layout",
    ),
    SAMSUNG_ONE_UI(
        id = "samsung_one_ui",
        displayName = "Samsung One UI",
        subtitle = "Full-screen calls, pop-up, mini-pop-up & Samsung reachable active call controls",
    ),
    GOOGLE_PHONE_LATEST(
        id = "google_phone_latest",
        displayName = "Google Phone Latest",
        subtitle = "Modern Pixel-style call experience and reachable Material 3 bottom-sheet controls",
    ),
    GOOGLE_PHONE_CLASSIC(
        id = "google_phone_classic",
        displayName = "Google Phone Classic",
        subtitle = "Legacy Google incoming-call gestures, classic dialer style & circular swipe controls",
    ),
    VIVO_IQOO(
        id = "vivo_iqoo",
        displayName = "Vivo / iQOO Native Style",
        subtitle = "OriginOS & FuntouchOS-inspired incoming calls, outgoing calls & vibrant active controls",
    );

    companion object {
        val all: List<CallBundleId> = entries

        fun fromId(rawId: String?): CallBundleId =
            entries.firstOrNull { it.id == rawId } ?: CHATY_DEFAULT
    }
}

/** Visual design tokens specific to each Call Experience Bundle. */
data class CallBundleThemeTokens(
    val bundleId: CallBundleId,
    val primaryAccent: Color,
    val acceptColor: Color,
    val declineColor: Color,
    val surfaceBackground: Color,
    val cardBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val controlBarBackground: Color,
    val pillCornerRadiusDp: Int,
    val cardCornerRadiusDp: Int,
    val iconButtonSizeDp: Int,
    val isGlassmorphic: Boolean = false,
)

/** Theme registry returning specific tokens for each bundle. */
object CallBundleRegistry {
    val ChatyDefaultTokens = CallBundleThemeTokens(
        bundleId = CallBundleId.CHATY_DEFAULT,
        primaryAccent = Color(0xFF8B5CF6), // Royal violet
        acceptColor = Color(0xFF10B981),   // Emerald green
        declineColor = Color(0xFFEF4444),  // Bright crimson
        surfaceBackground = Color(0xFF0F172A), // Dark slate navy
        cardBackground = Color(0xFF1E293B),
        primaryText = Color(0xFFF8FAFC),
        secondaryText = Color(0xFF94A3B8),
        controlBarBackground = Color(0xD91E293B),
        pillCornerRadiusDp = 24,
        cardCornerRadiusDp = 20,
        iconButtonSizeDp = 56,
        isGlassmorphic = true,
    )

    val AppleIosTokens = CallBundleThemeTokens(
        bundleId = CallBundleId.APPLE_IOS,
        primaryAccent = Color(0xFF0A84FF), // iOS system blue
        acceptColor = Color(0xFF34C759),   // iOS system green
        declineColor = Color(0xFFFF3B30),  // iOS system red
        surfaceBackground = Color(0xFF1C1C1E), // Deep dark iOS gray
        cardBackground = Color(0xFF2C2C2E),
        primaryText = Color(0xFFFFFFFF),
        secondaryText = Color(0xFF8E8E93),
        controlBarBackground = Color(0xE62C2C2E),
        pillCornerRadiusDp = 30,
        cardCornerRadiusDp = 24,
        iconButtonSizeDp = 64,
        isGlassmorphic = true,
    )

    val SamsungOneUiTokens = CallBundleThemeTokens(
        bundleId = CallBundleId.SAMSUNG_ONE_UI,
        primaryAccent = Color(0xFF2E86DE), // One UI cobalt blue
        acceptColor = Color(0xFF00C853),   // Pure green
        declineColor = Color(0xFFD50000),  // Vivid red
        surfaceBackground = Color(0xFF121417), // Deep charcoal
        cardBackground = Color(0xFF20242A),
        primaryText = Color(0xFFFFFFFF),
        secondaryText = Color(0xFFA0A6B2),
        controlBarBackground = Color(0xF020242A),
        pillCornerRadiusDp = 20,
        cardCornerRadiusDp = 28,
        iconButtonSizeDp = 58,
        isGlassmorphic = false,
    )

    val GoogleLatestTokens = CallBundleThemeTokens(
        bundleId = CallBundleId.GOOGLE_PHONE_LATEST,
        primaryAccent = Color(0xFF6750A4), // Material 3 deep violet
        acceptColor = Color(0xFF388E3C),   // Material green
        declineColor = Color(0xFFD32F2F),  // Material red
        surfaceBackground = Color(0xFF141218), // M3 dark surface
        cardBackground = Color(0xFF2B2930),
        primaryText = Color(0xFFE6E0E9),
        secondaryText = Color(0xFFCAC4D0),
        controlBarBackground = Color(0xFF211F26),
        pillCornerRadiusDp = 28,
        cardCornerRadiusDp = 28,
        iconButtonSizeDp = 60,
        isGlassmorphic = false,
    )

    val GoogleClassicTokens = CallBundleThemeTokens(
        bundleId = CallBundleId.GOOGLE_PHONE_CLASSIC,
        primaryAccent = Color(0xFF009688), // Classic dialer Teal 500
        acceptColor = Color(0xFF4CAF50),   // Classic green
        declineColor = Color(0xFFF44336),  // Classic red
        surfaceBackground = Color(0xFF004D40), // Classic Teal 900
        cardBackground = Color(0xFF00695C),
        primaryText = Color(0xFFFFFFFF),
        secondaryText = Color(0xFFB2DFDB),
        controlBarBackground = Color(0xD9004D40),
        pillCornerRadiusDp = 16,
        cardCornerRadiusDp = 12,
        iconButtonSizeDp = 56,
        isGlassmorphic = false,
    )

    val VivoIqooTokens = CallBundleThemeTokens(
        bundleId = CallBundleId.VIVO_IQOO,
        primaryAccent = Color(0xFF00C4B4), // OriginOS neon cyan
        acceptColor = Color(0xFF00E676),   // Neon green
        declineColor = Color(0xFFFF1744),  // Neon coral red
        surfaceBackground = Color(0xFF0D1B2A), // Deep oceanic midnight
        cardBackground = Color(0xFF1B263B),
        primaryText = Color(0xFFFFFFFF),
        secondaryText = Color(0xFF778DA9),
        controlBarBackground = Color(0xCC1B263B),
        pillCornerRadiusDp = 24,
        cardCornerRadiusDp = 22,
        iconButtonSizeDp = 58,
        isGlassmorphic = true,
    )

    fun getTokens(bundleId: CallBundleId): CallBundleThemeTokens = when (bundleId) {
        CallBundleId.CHATY_DEFAULT -> ChatyDefaultTokens
        CallBundleId.APPLE_IOS -> AppleIosTokens
        CallBundleId.SAMSUNG_ONE_UI -> SamsungOneUiTokens
        CallBundleId.GOOGLE_PHONE_LATEST -> GoogleLatestTokens
        CallBundleId.GOOGLE_PHONE_CLASSIC -> GoogleClassicTokens
        CallBundleId.VIVO_IQOO -> VivoIqooTokens
    }
}

