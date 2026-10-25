package com.example.chat.core.emoji

/**
 * Exact port of lib/core/emoji/chaty_emoji_style.dart (api branch, anchor
 * 3bee780d0e5a0e1df2e44f6d5b9f6c691d10a682). Pure JVM.
 *
 * The 10 distinct, visually differentiated Emoji Styles for Chaty: 4
 * independently drawn Chaty original styles and 6 verified
 * open-source/source-informed styles.
 *
 * NOTE: the styled artwork renderer (chaty_motion_emoji.dart, 674 lines of
 * procedural vector drawing) is NOT part of this file; this is the style
 * registry consumed by pickers and settings.
 */
enum class ChatyEmojiStyleId(
    val title: String,
    val description: String,
    val samplePreview: String,
    val isAnimated: Boolean,
) {
    chatyMotion(
        "Chaty Motion",
        "Original expressive animated vector emoji with procedural physics and morphing expressions.",
        "✨ 😂 ❤️",
        true,
    ),
    chatyInk(
        "Chaty Ink",
        "Original hand-drawn manga & calligraphic ink outlines with expressive comic character.",
        "✒️ 😜 💥",
        true,
    ),
    chatySoft3D(
        "Chaty Soft 3D",
        "Original dimensional clay-morphism with multi-stop radial highlights and soft bevels.",
        "🧸 🤩 💖",
        true,
    ),
    chatyPixel(
        "Chaty Pixel",
        "Original 8-bit stepped pixel grid with retro arcade block aesthetics.",
        "👾 🕹️ 🌟",
        false,
    ),
    openMoji(
        "OpenMoji",
        "Verified CC BY-SA 4.0: 2px solid black contours with flat unshaded color fills.",
        "🙌 🌟 🎈",
        false,
    ),
    twemoji(
        "Twemoji",
        "Verified CC BY 4.0: Modern flat vector silhouettes with signature capsule eyes.",
        "🎉 💯 🚀",
        false,
    ),
    notoColor(
        "Noto Color",
        "Verified SIL OFL 1.1: Google Material soft linear gradients and circular geometry.",
        "😃 💖 👍",
        true,
    ),
    fluentColor(
        "Fluent Color",
        "Verified MIT: Microsoft Fluent soft volumetric illumination and curved arches.",
        "🥳 😻 🔥",
        true,
    ),
    blobmoji(
        "Blobmoji",
        "Verified Apache 2.0: Classic Android asymmetric teardrop jelly-blob silhouette.",
        "🍮 🫠 💫",
        true,
    ),
    emojiTwo(
        "EmojiTwo",
        "Verified CC BY 4.0: Illustrated comic-book emoji with glossy rim highlights.",
        "😎 🍕 🏆",
        false,
    );

    companion object {
        // Backward compatibility static aliases for previously stored identifiers & code
        val systemDefault: ChatyEmojiStyleId = chatyMotion
        val twemojiColor: ChatyEmojiStyleId = twemoji
        val iosInspired: ChatyEmojiStyleId = chatySoft3D
        val oneUiInspired: ChatyEmojiStyleId = notoColor
        val soft3D: ChatyEmojiStyleId = chatySoft3D
        val minimalOutline: ChatyEmojiStyleId = chatyInk

        /** Resolves an emoji style ID from stored preference strings, handling
         *  both new canonical titles and legacy saved settings. */
        fun fromString(value: String?): ChatyEmojiStyleId {
            if (value == null) return chatyMotion
            val normalized = value.trim().lowercase().replace(Regex("[\\s_\\-]+"), "")

            when (normalized) {
                "systemdefault", "system" -> return chatyMotion
                "twemojicolor", "twemoji" -> return twemoji
                "iosinspired", "ios" -> return chatySoft3D
                "oneuiinspired", "oneui" -> return notoColor
                "soft3d" -> return chatySoft3D
                "minimaloutline" -> return chatyInk
                "chatymotion", "motion" -> return chatyMotion
                "chatyink", "ink" -> return chatyInk
                "chatypixel", "pixel" -> return chatyPixel
                "openmoji" -> return openMoji
                "notocolor", "noto" -> return notoColor
                "fluentcolor", "fluent" -> return fluentColor
                "blobmoji", "blob" -> return blobmoji
                "emojitwo" -> return emojiTwo
            }

            for (style in entries) {
                val styleNorm = style.name.lowercase().replace(Regex("[\\s_\\-]+"), "")
                val titleNorm = style.title.lowercase().replace(Regex("[\\s_\\-]+"), "")
                if (normalized == styleNorm || normalized == titleNorm) {
                    return style
                }
            }
            return chatyMotion
        }
    }
}
