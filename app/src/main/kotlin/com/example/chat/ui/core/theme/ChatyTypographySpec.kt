package com.example.chat.ui.core.theme

/** Pure resolver mirroring main.dart _applyTypographyStyle (family / letterSpacingDelta / Monospace Accent). */
data class ChatyTypographySpec(val family: String?, val letterSpacingDelta: Double, val monospaceAccent: Boolean, val weight: Int? = null, val italic: Boolean = false) {
    companion object {
        /**
         * [preset] = appearance typographyStyle; [universalPreset] = preferences.universal.fontStyle.
         * System Default appearance delegates to the universal font choice. The same effective
         * preset controls family, weight, italic and spacing so selection and preview agree.
         */
        fun resolve(preset: String, universalPreset: String?): ChatyTypographySpec {
            val effective = if (preset.isBlank() || preset == "Default" || preset == "System Default") (universalPreset ?: preset) else preset
            val family = when (effective) {
                "Editorial", "Classic" -> "serif"
                "Rounded" -> "sans-serif-rounded"
                // OFL-bundled real faces (res/font, SIL OFL 1.1 - licenses in
                // assets/font-licenses/); these two names previously rendered only
                // generic-family approximations while claiming the real font.
                "Comfortaa" -> "ofl-comfortaa"
                "BEBASNEUE" -> "ofl-bebasneue"
                "Technical", "TRANSFORMERS" -> "monospace"
                "ComicSans" -> "cursive"
                "Bariol" -> "sans-serif-condensed"
                "Geometric", "Product Sans", "Modern", "Business", "Creator", "Focus", "ProductSans",
                "FrutigerLTStdRoman", "Roboto-Light", "Roboto-Medium" -> "sans-serif"
                else -> null
            }
            val delta = when (effective) {
                "Compact", "Dense UI" -> -0.18
                "Editorial" -> 0.12
                "Geometric", "Product Sans" -> 0.04
                "Minimal", "Focus" -> -0.08
                "ProductSans" -> 0.2
                "BEBASNEUE" -> 1.5
                "TRANSFORMERS" -> 1.2
                else -> 0.0
            }
            val weight = when (effective) {
                "Roboto-Light" -> 300
                "Roboto-Medium", "Comfortaa" -> 600
                "ProductSans", "ComicSans", "HappyGiraffe" -> 500
                "BEBASNEUE" -> 900
                "TRANSFORMERS" -> 800
                else -> null
            }
            return ChatyTypographySpec(family, delta, effective == "Monospace Accent", weight, effective == "HappyGiraffe")
        }
    }
}
