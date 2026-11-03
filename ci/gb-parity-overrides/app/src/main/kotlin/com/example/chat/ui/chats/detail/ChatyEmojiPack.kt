package com.example.chat.ui.chats.detail

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.example.chat.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Separate source identities stored in the existing universal.emojiStyle preference.
 * Supplied iOS ZIP: 50 animated WebP; supplied Telegram variants: 19 animated WebP;
 * TheSub LottieFiles set: 11 Lottie animations. These are partial packs, not official
 * complete Unicode sets. Missing supplied Telegram art uses a normal glyph.
 * Animated WebP requires API 28. Drive MOV source is a separate pack with all 132 supplied folder animations.
 */
object ChatyEmojiPack {
    const val CHATY = "Chaty"
    const val IOS = "iOS"
    const val MICROSOFT = "Microsoft"
    const val OPENMOJI = "OpenMoji"
    const val TELEGRAM = "Telegram"
    const val TELEGRAM_LOTTIE = "Telegram Lottie"
    const val DRIVE_MOV = "Drive MOV animations"

    /** Picker order: Chaty, iOS, Microsoft, OpenMoji, Telegram. */
    val ORDER = listOf(CHATY, IOS, MICROSOFT, OPENMOJI, TELEGRAM, TELEGRAM_LOTTIE, DRIVE_MOV)

    /**
     * Code-point named asset packs. Files are <hex>.<ext> under [dir] (lowercase
     * hex, or uppercase when [upperHex]); a missing file falls back to Chaty (Noto)
     * for that emoji. Microsoft = Fluent Emoji Animated (MIT) converted to animated
     * WebP; OpenMoji = static colour PNG (CC BY-SA 4.0), never animated.
     */
    data class AssetPack(val id: String, val dir: String, val ext: String, val animated: Boolean, val upperHex: Boolean)

    val TELEGRAM_PACK = AssetPack(TELEGRAM, "emoji/telegram19", "webp", animated = true, upperHex = false)
    val MICROSOFT_PACK = AssetPack(MICROSOFT, "emoji/fluent_animated", "webp", animated = true, upperHex = false)
    val OPENMOJI_PACK = AssetPack(OPENMOJI, "emoji/openmoji", "png", animated = false, upperHex = true)

    fun assetPack(style: String): AssetPack? = when (style) {
        TELEGRAM -> TELEGRAM_PACK
        MICROSOFT -> MICROSOFT_PACK
        OPENMOJI -> OPENMOJI_PACK
        else -> null
    }

    /** Asset path for a single emoji in a code-point pack, or null for sequences / empty. */
    fun packAssetPath(pack: AssetPack, unicode: String): String? {
        val s = unicode.replace("\uFE0F", "")
        if (s.isEmpty() || s.codePointCount(0, s.length) != 1) return null
        val hex = Integer.toHexString(s.codePointAt(0)).let { if (pack.upperHex) it.uppercase() else it }
        return "${pack.dir}/$hex.${pack.ext}"
    }

    val driveKeys = setOf(
        "1f440",
        "1f449",
        "1f44b",
        "1f44c",
        "1f44d",
        "1f44e",
        "1f44f",
        "1f47f",
        "1f494",
        "1f495",
        "1f49e",
        "1f4aa",
        "1f525",
        "1f595",
        "1f596",
        "1f600",
        "1f601",
        "1f602",
        "1f603",
        "1f604",
        "1f605",
        "1f606",
        "1f607",
        "1f608",
        "1f609",
        "1f60a",
        "1f60b",
        "1f60c",
        "1f60d",
        "1f60e",
        "1f60f",
        "1f610",
        "1f611",
        "1f612",
        "1f613",
        "1f614",
        "1f615",
        "1f616",
        "1f617",
        "1f618",
        "1f619",
        "1f61a",
        "1f61b",
        "1f61c",
        "1f61d",
        "1f61e",
        "1f61f",
        "1f620",
        "1f621",
        "1f622",
        "1f623",
        "1f624",
        "1f625",
        "1f626",
        "1f627",
        "1f628",
        "1f629",
        "1f62a",
        "1f62b",
        "1f62c",
        "1f62d",
        "1f62e",
        "1f62f",
        "1f630",
        "1f631",
        "1f632",
        "1f633",
        "1f634",
        "1f635",
        "1f636",
        "1f637",
        "1f638",
        "1f639",
        "1f63a",
        "1f63b",
        "1f63c",
        "1f63d",
        "1f63e",
        "1f63f",
        "1f640",
        "1f641",
        "1f642",
        "1f643",
        "1f644",
        "1f645-200d-2640",
        "1f645-200d-2642",
        "1f648",
        "1f649",
        "1f64a",
        "1f64c",
        "1f64f",
        "1f910",
        "1f911",
        "1f912",
        "1f913",
        "1f914",
        "1f915",
        "1f917",
        "1f918",
        "1f919",
        "1f91e",
        "1f91f",
        "1f920",
        "1f921",
        "1f922",
        "1f923",
        "1f924",
        "1f925",
        "1f926-200d-2640",
        "1f926-200d-2642",
        "1f927",
        "1f928",
        "1f929",
        "1f92a",
        "1f92b",
        "1f92c",
        "1f92d",
        "1f92e",
        "1f92f",
        "1f937-200d-2640",
        "1f937-200d-2642",
        "1f970",
        "1f973",
        "1f974",
        "1f975",
        "1f976",
        "1f97a",
        "1f9d0",
        "2639",
        "263a",
        "270c",
        "2764",
    )
    fun driveAssetPath(unicode: String): String? {
        val normalized = unicode.replace("\uFE0F", "").replace("\uFE0E", "")
        val key = normalized.codePoints().toArray().joinToString("-") { Integer.toHexString(it) }
        return if (key in driveKeys) "emoji/drive_mov132/$key.webp" else null
    }
    fun driveBundledCount(context: android.content.Context): Int =
        runCatching { context.assets.list("emoji/drive_mov132")?.count { it.endsWith(".webp") && it.removeSuffix(".webp") in driveKeys } ?: 0 }.getOrDefault(0)

    val telegramCodePoints = setOf(0x1f60b, 0x1f973, 0x1f971, 0x1f62e, 0x1f921, 0x1f92c, 0x1f60e, 0x1f608, 0x1f611, 0x1f923, 0x1f914, 0x1f630, 0x1f61b, 0x1f61d, 0x1f641, 0x1f925, 0x1f910, 0x1f634, 0x1f62b)
    val lottieCodePoints = setOf(0x1f605, 0x1f4d6, 0x1f697, 0x1f4a1, 0x1f4f1, 0x1f4bb, 0x1f40d, 0x1f695, 0x1f321, 0x1f324, 0x274c)
    fun lottieAssetPath(unicode: String): String? {
        val normalized = unicode.replace("\uFE0F", "")
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length) != 1) return null
        val cp = normalized.codePointAt(0)
        return if (cp in lottieCodePoints) "emoji/telegram_lottie/${Integer.toHexString(cp)}.json" else null
    }
    fun lottieBundledCount(context: android.content.Context): Int =
        runCatching { context.assets.list("emoji/telegram_lottie")?.count { it.endsWith(".json") && it != "manifest.json" } ?: 0 }.getOrDefault(0)

    /** Asset path for [style]'s art of [unicode], or null when that pack has none (Chaty: no bundled asset). */
    fun assetPathFor(style: String, unicode: String): String? = when (style) {
        DRIVE_MOV -> driveAssetPath(unicode)
        IOS -> iosAssetPath(unicode)
        TELEGRAM -> unicode.replace("\uFE0F", "").let { value ->
            if (value.isNotEmpty() && value.codePointCount(0, value.length) == 1 && value.codePointAt(0) in telegramCodePoints) packAssetPath(TELEGRAM_PACK, unicode) else null
        }
        else -> assetPack(style)?.let { packAssetPath(it, unicode) }
    }

    /** Whether a style's art needs animated-image decoding (API 28+). */
    fun needsAnimatedDecoder(style: String): Boolean = style == IOS || style == DRIVE_MOV || assetPack(style)?.animated == true

    fun packBundledCount(context: android.content.Context, pack: AssetPack): Int =
        runCatching { context.assets.list(pack.dir)?.count { it.endsWith(".${pack.ext}") } }.getOrNull() ?: 0

    const val TELEGRAM_UNAVAILABLE_REASON = "Unavailable: the 19 supplied Telegram-style animations are not installed"
    const val ASSET_DIR = "emoji/ios_animated"

    /** Source facts only; the user's permission statement is in the conversation. */
    const val IOS_PACK_PROVENANCE =
        "User-supplied ios_animated_webp.zip, 50 animated WebP files. Overlaps the first 50 entries of a third-party shared Drive MOV folder. User states licensed; no independent license file supplied."

    fun normalize(style: String?): String =
        ORDER.firstOrNull { it != CHATY && it.equals(style, ignoreCase = true) } ?: CHATY

    /** Animated WebP decoding needs android.graphics.ImageDecoder (API 28+). */
    fun iosSupported(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= 28

    private val files: List<Pair<Int, String>> = listOf(
        0x1f600 to "1_grinning_face", 0x1f601 to "2_beaming_face", 0x1f602 to "3_face_with_tears",
        0x1f923 to "4_rolling_on_the_floor", 0x1f603 to "5_grinning_face_with_big_eyes",
        0x1f604 to "6_grinning_face_with_smiling_eyes", 0x1f605 to "7_grinning_face_with_sweat",
        0x1f606 to "8_grinning_squinting_face", 0x1f609 to "9_winking_face", 0x1f60a to "10_smiling_face_blushing",
        0x1f60b to "11_face_savoring_food", 0x1f60e to "12_smiling_face_with_sunglasses",
        0x1f60d to "13_smiling_face_with_heart_eyes", 0x1f618 to "14_face_blowing_a_kiss", 0x1f617 to "15_kissing_face",
        0x1f619 to "16_kissing_face_with_smiling_eyes", 0x1f61a to "17_kissing_face_with_closed_eyes",
        0x263a to "18_blushing_closed_eyes", 0x1f642 to "19_slightly_smiling_face", 0x1f917 to "20_hugging_face",
        0x1f929 to "21_star_struck", 0x1f914 to "22_thinking_face", 0x1f928 to "23_face_with_raised_eyebrow",
        0x1f610 to "24_neutral_face", 0x1f611 to "25_expressionless_face", 0x1f636 to "26_face_without_mouth",
        0x1f644 to "27_face_with_rolling_eyes", 0x1f60f to "28_smirking_face", 0x1f623 to "29_persevering_face",
        0x1f625 to "30_sad_but_relieved_face", 0x1f62e to "31_face_with_open_mouth", 0x1f910 to "32_zipper_mouth_face",
        0x1f62f to "33_hushed_face", 0x1f62a to "34_sleepy_face", 0x1f62b to "35_tired_face", 0x1f634 to "36_sleeping_face",
        0x1f60c to "37_relieved_face", 0x1f61b to "38_face_with_tongue", 0x1f61c to "39_winking_face_with_tongue",
        0x1f61d to "40_squinting_face_with_tongue", 0x1f924 to "41_drooling_face", 0x1f612 to "42_unamused_face",
        0x1f613 to "43_downcast_face", 0x1f614 to "44_pensive_face", 0x1f615 to "45_confused_face",
        0x1f643 to "46_upside_down_face", 0x1f911 to "47_money_mouth_face", 0x1f632 to "48_astonished_face",
        0x2639 to "49_frowning_face", 0x1f641 to "50_slightly_frowning_face",
    )

    private val byCodePoint: Map<Int, String> = files.toMap()

    /** Number of emojis the iOS ZIP covers. */
    val coverage: Int get() = files.size

    /** Asset path under assets/ for a single emoji, or null when the iOS pack has no art for it. */
    fun iosAssetPath(unicode: String): String? {
        val s = unicode.replace("\uFE0F", "")
        if (s.isEmpty() || s.codePointCount(0, s.length) != 1) return null
        val name = byCodePoint[s.codePointAt(0)] ?: return null
        return "$ASSET_DIR/$name.webp"
    }

    /** Whether the iOS pack can actually be applied on this device right now. */
    enum class IosState { Unsupported, AssetsMissing, Partial, Ready }

    /** Number of the 50 iOS animation files bundled under assets/[ASSET_DIR]. */
    fun iosBundledCount(context: android.content.Context): Int {
        val present = runCatching { context.assets.list(ASSET_DIR)?.toSet() }.getOrNull() ?: return 0
        return files.count { "${it.second}.webp" in present }
    }

    fun iosState(context: android.content.Context, sdk: Int = Build.VERSION.SDK_INT): IosState {
        if (!iosSupported(sdk)) return IosState.Unsupported
        val n = iosBundledCount(context)
        return when {
            n == 0 -> IosState.AssetsMissing
            n < files.size -> IosState.Partial
            else -> IosState.Ready
        }
    }

    /** Pack to use for [explicit] when it names a pack, else the style of the enclosing [LocalEmojiPack] scope. No hooks, no globals. */
    @Composable
    fun rememberStyle(explicit: String?): String {
        if (explicit != null && ORDER.any { it.equals(explicit, ignoreCase = true) }) return normalize(explicit)
        return LocalEmojiPack.current
    }
}

/**
 * Pack for views that are not handed one (reaction badge, picker). Scoped, not
 * global: the chat screen provides it for its own subtree only; anywhere
 * else (fixtures, other chats, settings previews) the default is Chaty.
 */
val LocalEmojiPack = androidx.compose.runtime.compositionLocalOf { ChatyEmojiPack.CHATY }

/**
 * Plays one iOS-pack animated WebP forever while composed (stops on leaving
 * composition, restarts on re-entry). [onUnavailable] is called once when the
 * asset cannot be decoded (file not bundled, or decode error) so the caller
 * can show the Chaty pack instead of a blank.
 */
@Composable
internal fun IosAnimatedEmoji(
    assetPath: String,
    size: Float,
    animate: Boolean = true,
    onUnavailable: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < 28) {
        LaunchedEffect(assetPath) { onUnavailable() }
        return
    }
    val context = LocalContext.current
    var drawable by remember(assetPath, animate) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(assetPath, animate) {
        val d = withContext(Dispatchers.IO) {
            runCatching {
                val src = ImageDecoder.createSource(context.assets, assetPath)
                // Decode at display density instead of the full asset size: a 36dp
                // grid cell must not hold full-size animation frames in native
                // memory. Software allocator keeps frames GC-managed so cells
                // scrolled out of composition are reclaimed without finalizer lag.
                val px = (size * context.resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(1)
                if (animate) ImageDecoder.decodeDrawable(src) { decoder, _, _ ->
                    decoder.setTargetSize(px, px)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                else android.graphics.drawable.BitmapDrawable(context.resources, ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                    decoder.setTargetSize(px, px)
                })
            }.getOrNull()
        }
        if (d == null) onUnavailable() else drawable = d
    }
    val d = drawable ?: return
    var view by remember { mutableStateOf<ImageView?>(null) }
    DisposableEffect(d) {
        onDispose {
            if (d is AnimatedImageDrawable) d.stop()
            // Do not clear a replacement drawable installed by the next composition.
            view?.takeIf { it.drawable === d }?.setImageDrawable(null)
        }
    }
    AndroidView(
        modifier = Modifier.size(size.dp),
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER }.also { v -> view = v } },
        update = { image ->
            // Attach the drawable callback before starting frame invalidation.
            if (image.drawable !== d) image.setImageDrawable(d)
            if (d is AnimatedImageDrawable && animate) {
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                if (!d.isRunning) d.start()
            }
        },
    )
}

/**
 * Shows one static pack image (OpenMoji). Never animated. [onUnavailable] is
 * called once when the file is missing or cannot be decoded.
 */
@Composable
internal fun StaticAssetEmoji(
    assetPath: String,
    size: Float,
    onUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(assetPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(assetPath) {
        val b = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(assetPath).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
        }
        if (b == null) onUnavailable() else bitmap = b
    }
    val b = bitmap ?: return
    DisposableEffect(b) {
        onDispose { if (!b.isRecycled) b.recycle() }
    }
    AndroidView(
        modifier = Modifier.size(size.dp),
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { it.setImageBitmap(b) },
    )
}
