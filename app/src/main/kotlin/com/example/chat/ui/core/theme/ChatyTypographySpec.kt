package com.example.chat.ui.core.theme

/** Pure resolver mirroring main.dart _applyTypographyStyle (family / letterSpacingDelta / Monospace Accent). */
data class ChatyTypographySpec(val family: String?, val letterSpacingDelta: Double, val monospaceAccent: Boolean) {
    companion object {
        /**
         * [preset] = appearance typographyStyle; [universalPreset] = preferences.universal.fontStyle.
         * Dart quirk kept: the family uses the *effective* preset (Default/empty falls back to the universal one) but the
         * letter-spacing delta and Monospace Accent use the raw [preset].
         */
        fun resolve(preset: String, universalPreset: String?): ChatyTypographySpec {
            val effective = if (preset.isEmpty() || preset == "Default") (universalPreset ?: preset) else preset
            val family = when (effective) {
                "Editorial", "Classic" -> "serif"
                "Rounded", "Comfortaa" -> "sans-serif-rounded"
                "Technical", "TRANSFORMERS" -> "monospace"
                "ComicSans" -> "cursive"
                "Bariol" -> "sans-serif-condensed"
                "Geometric", "Product Sans", "Modern", "Business", "Creator", "Focus", "ProductSans",
                "FrutigerLTStdRoman", "Roboto-Light", "Roboto-Medium" -> "sans-serif"
                else -> null
            }
            val delta = when (preset) {
                "Compact", "Dense UI" -> -0.18
                "Editorial" -> 0.12
                "Geometric", "Product Sans" -> 0.04
                "Minimal", "Focus" -> -0.08
                else -> 0.0
            }
            return ChatyTypographySpec(family, delta, preset == "Monospace Accent")
        }
    }
}
