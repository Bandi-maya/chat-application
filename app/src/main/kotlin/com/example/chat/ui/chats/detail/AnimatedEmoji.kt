@file:OptIn(ExperimentalLayoutApi::class)

package com.example.chat.ui.chats.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.ChatyHapticsPort
import kotlinx.coroutines.launch

/** Display format/size level (port of parsed_emoji_span.dart's
 *  EmojiDisplayMode). */
enum class EmojiDisplayMode { inline, jumboSingle, mediumFew, mediumMany, reaction, picker }

/** Token representing either normal text or an emoji (ParsedEmojiSpan). */
sealed class ParsedEmojiSpan {
    abstract val rawText: String
    abstract val isEmoji: Boolean
    abstract val normalizedUnicode: String

    data class Text(override val rawText: String) : ParsedEmojiSpan() {
        override val isEmoji = false
        override val normalizedUnicode = rawText
    }

    data class Emoji(override val rawText: String, override val normalizedUnicode: String) :
        ParsedEmojiSpan() {
        override val isEmoji = true
    }
}

/** Exact port of ChatyEmojiParser (emoji_parser.dart): same Unicode ranges,
 *  ZWJ/flag/keycap handling, normalize() strips U+FE0E/U+FE0F like
 *  emoji_registry.dart:87-91. */
object ChatyEmojiParser {
    private val emojiRegex = Regex(
        "(?:[\\x{1F600}-\\x{1F64F}]|[\\x{1F300}-\\x{1F5FF}]|[\\x{1F680}-\\x{1F6FF}]|" +
            "[\\x{1F700}-\\x{1F77F}]|[\\x{1F780}-\\x{1F7FF}]|[\\x{1F800}-\\x{1F8FF}]|" +
            "[\\x{1F900}-\\x{1F9FF}]|[\\x{1FA00}-\\x{1FA6F}]|[\\x{1FA70}-\\x{1FAFF}]|" +
            "[\\x{2600}-\\x{26FF}]|[\\x{2700}-\\x{27BF}]|" +
            "[\\x{2B50}\\x{2B55}\\x{2934}\\x{2935}\\x{2B05}\\x{2B06}\\x{2B07}\\x{3030}\\x{303D}\\x{3297}\\x{3299}]|" +
            "[\\x{1F1E6}-\\x{1F1FF}]{2}|[0-9#*]\\uFE0F?\\u20E3)" +
            "(?:[\\x{1F3FB}-\\x{1F3FF}])?(?:[\\uFE0E\\uFE0F])?" +
            "(?:\\x{200D}(?:[\\x{1F600}-\\x{1F64F}]|[\\x{1F300}-\\x{1F5FF}]|[\\x{1F680}-\\x{1F6FF}]|" +
            "[\\x{1F900}-\\x{1F9FF}]|[\\x{1FA70}-\\x{1FAFF}]|[\\x{2600}-\\x{26FF}]|[\\x{2700}-\\x{27BF}])" +
            "(?:[\\x{1F3FB}-\\x{1F3FF}])?(?:[\\uFE0E\\uFE0F])?)*",
    )

    private fun normalize(emoji: String): String =
        if (emoji.isEmpty()) emoji
        else emoji.replace("\uFE0F", "").replace("\uFE0E", "").trim()

    fun parse(text: String): List<ParsedEmojiSpan> {
        if (text.isEmpty()) return emptyList()
        val spans = mutableListOf<ParsedEmojiSpan>()
        var lastIndex = 0
        for (match in emojiRegex.findAll(text)) {
            if (match.range.first > lastIndex) {
                spans.add(ParsedEmojiSpan.Text(text.substring(lastIndex, match.range.first)))
            }
            val raw = match.value
            spans.add(ParsedEmojiSpan.Emoji(rawText = raw, normalizedUnicode = normalize(raw)))
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            spans.add(ParsedEmojiSpan.Text(text.substring(lastIndex)))
        }
        return spans
    }

    fun emojiOnlyCount(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        var count = 0
        for (span in parse(trimmed)) {
            if (!span.isEmoji) {
                if (span.rawText.trim().isNotEmpty()) return 0
            } else {
                count++
            }
        }
        return count
    }

    fun resolveEmojiOnlyDisplayMode(text: String): EmojiDisplayMode? {
        val count = emojiOnlyCount(text)
        if (count == 0) return null
        if (count == 1) return EmojiDisplayMode.jumboSingle
        if (count <= 3) return EmojiDisplayMode.mediumFew
        if (count <= 6) return EmojiDisplayMode.mediumMany
        return EmojiDisplayMode.inline
    }
}

/** Exact port of ChatyEmojiPlaybackManager (chaty_emoji_playback_manager.dart):
 *  newly seen message emojis auto-play once; scroll/rebuild storms don't
 *  retrigger. */
object ChatyEmojiPlaybackManager {
    private val playedInstanceKeys = HashSet<String>()
    private val activelyPlayingKeys = HashSet<String>()

    private fun makeKey(messageId: String, emojiIndex: Int) = "${messageId}_$emojiIndex"

    @Synchronized
    fun shouldAutoPlay(messageId: String, emojiIndex: Int): Boolean {
        if (messageId.isEmpty()) return true
        return !playedInstanceKeys.contains(makeKey(messageId, emojiIndex))
    }

    @Synchronized
    fun notifyAnimationStarted(messageId: String, emojiIndex: Int) {
        if (messageId.isEmpty()) return
        activelyPlayingKeys.add(makeKey(messageId, emojiIndex))
    }

    @Synchronized
    fun notifyAnimationCompleted(messageId: String, emojiIndex: Int) {
        if (messageId.isEmpty()) return
        val key = makeKey(messageId, emojiIndex)
        activelyPlayingKeys.remove(key)
        playedInstanceKeys.add(key)
    }

    @Synchronized
    fun isCompleted(messageId: String, emojiIndex: Int): Boolean {
        if (messageId.isEmpty()) return false
        return playedInstanceKeys.contains(makeKey(messageId, emojiIndex))
    }

    @Synchronized
    fun resetMessage(messageId: String) {
        playedInstanceKeys.removeAll { it.startsWith("${messageId}_") }
        activelyPlayingKeys.removeAll { it.startsWith("${messageId}_") }
    }

    @Synchronized
    fun clearAll() {
        playedInstanceKeys.clear()
        activelyPlayingKeys.clear()
    }
}

/** Bounce curve of AnimatedEmojiView (animated_emoji_view.dart:58-86):
 *  360ms, 1.0 -> 1.28 easeOutBack (weight 45) -> 0.92 easeInOut (weight 30)
 *  -> 1.0 easeOut (weight 25). */
private val bounceSpec = keyframes<Float> {
    durationMillis = 360
    1.0f at 0 using EaseOutBack
    1.28f at 162 using EaseInOut
    0.92f at 270 using EaseOut
    1.0f at 360
}

/** Port of AnimatedEmojiView (animated_emoji_view.dart:135-220): procedural
 *  ChatyMotionEmoji for the 16 supported emojis, streamed Noto Lottie for the
 *  remaining 609-entry registry (ChatyAnimatedEmojiAsset), static glyph when
 *  animations are off / style non-animated / registry miss - the exact Dart
 *  chain. MediaQuery.disableAnimations maps to the animator-duration-scale
 *  check in [animationsDisabled]. */
@Composable
fun AnimatedEmojiView(
    unicode: String,
    size: Float,
    animate: Boolean = true,
    interactive: Boolean = false,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    mode: EmojiDisplayMode = EmojiDisplayMode.inline,
    messageId: String? = null,
    emojiIndex: Int = 0,
    emojiStyle: String = "Chaty Motion",
) {
    val view = LocalView.current
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(messageId, emojiIndex, animate) {
        if (!animate) return@LaunchedEffect
        val id = messageId
        if (id != null && id.isNotEmpty() &&
            ChatyEmojiPlaybackManager.shouldAutoPlay(id, emojiIndex)
        ) {
            ChatyEmojiPlaybackManager.notifyAnimationStarted(id, emojiIndex)
            try {
                scale.animateTo(1f, bounceSpec)
            } finally {
                ChatyEmojiPlaybackManager.notifyAnimationCompleted(id, emojiIndex)
            }
        }
    }

    Box(
        Modifier
            .size(size.dp)
            .semantics { contentDescription = "Emoji $unicode" }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .then(
                if (interactive || onTap != null || onLongPress != null) {
                    // Dart _handleTap: haptic + bounce replay + onTap.
                    Modifier.pointerInput(messageId, emojiIndex) {
                        detectTapGestures(
                            onTap = {
                                if (interactive) {
                                    ChatyHapticsPort.light(view)
                                    scope.launch {
                                        scale.snapTo(1f)
                                        scale.animateTo(1f, bounceSpec)
                                    }
                                }
                                onTap?.invoke()
                            },
                            onLongPress = { onLongPress?.invoke() },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Dart build(): style from prefs (universal.emojiStyle, default
        // Chaty Motion) gates animation via currentStyle.isAnimated.
        val style = ChatyEmojiStyleId.fromString(emojiStyle)
        val animationsOn = animate && style.isAnimated && !animationsDisabled()
        if (chatyMotionEmojiIsSupported(unicode)) {
            val shouldPlay = messageId?.let {
                ChatyEmojiPlaybackManager.shouldAutoPlay(it, emojiIndex)
            } ?: true
            ChatyMotionEmoji(
                unicode = unicode,
                size = size,
                animate = animationsOn,
                autoStart = shouldPlay,
                style = style,
                onCompleted = messageId?.let { id ->
                    { ChatyEmojiPlaybackManager.notifyAnimationCompleted(id, emojiIndex) }
                },
            )
        } else {
            // ChatyEmojiResolver.resolve + AnimatedEmoji fallback chain.
            val notoId = if (animationsOn) {
                ChatyEmojiAssetRegistry.findNotoId(unicode)
            } else {
                null
            }
            if (notoId != null) {
                ChatyAnimatedEmojiAsset(
                    notoId = notoId,
                    unicode = unicode,
                    size = size,
                    repeat = mode == EmojiDisplayMode.inline || mode == EmojiDisplayMode.picker,
                )
            } else {
                Text(
                    unicode,
                    fontSize = (size * 0.82f).sp,
                    lineHeight = (size * 0.82f).sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Flutter MediaQuery.disableAnimations on Android maps to the animator
 *  duration scale being zeroed (Flutter engine AccessibilityBridge). */
@Composable
private fun animationsDisabled(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** Port of AnimatedEmojiText (animated_emoji_text.dart): mixed text + emoji
 *  spans inline, or expressive jumbo/medium rows for emoji-only messages.
 *  onEmojiTap is the Dart VoidCallback shared by every emoji in the text. */
@Composable
fun AnimatedEmojiText(
    text: String,
    fontSize: Float,
    color: Color = Color.Unspecified,
    height: Float = 1.25f,
    animate: Boolean = true,
    enableExpressiveSizing: Boolean = true,
    messageId: String? = null,
    onEmojiTap: (() -> Unit)? = null,
    emojiStyle: String = "Chaty Motion",
) {
    if (text.isEmpty()) return

    if (enableExpressiveSizing) {
        val mode = ChatyEmojiParser.resolveEmojiOnlyDisplayMode(text)
        if (mode != null && mode != EmojiDisplayMode.inline) {
            val spans = ChatyEmojiParser.parse(text.trim()).filter { it.isEmoji }
            val emojiSize = when (mode) {
                EmojiDisplayMode.jumboSingle -> 64f
                EmojiDisplayMode.mediumFew -> 44f
                EmojiDisplayMode.mediumMany -> 32f
                else -> 24f
            }
            FlowRow(
                Modifier.padding(vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                spans.forEachIndexed { index, span ->
                    AnimatedEmojiView(
                        unicode = span.normalizedUnicode,
                        size = emojiSize,
                        animate = animate,
                        interactive = true,
                        onTap = onEmojiTap,
                        mode = mode,
                        messageId = messageId,
                        emojiIndex = index,
                        emojiStyle = emojiStyle,
                    )
                }
            }
            return
        }
    }

    // Inline path (Dart :50-86): RichText with WidgetSpan per emoji,
    // inlineSize = (fontSize * 1.35).clamp(16, 36), horizontal padding 1.5.
    val spans = ChatyEmojiParser.parse(text)
    val inlineSize = (fontSize * 1.35f).coerceIn(16f, 36f)
    val inlineContent = HashMap<String, InlineTextContent>()
    var emojiIdx = 0
    val annotated = buildAnnotatedString {
        for (span in spans) {
            if (!span.isEmoji) {
                append(span.rawText)
            } else {
                val id = "emoji_${emojiIdx++}"
                appendInlineContent(id, span.rawText)
            }
        }
    }
    emojiIdx = 0
    for (span in spans) {
        if (!span.isEmoji) continue
        val index = emojiIdx++
        inlineContent["emoji_$index"] = InlineTextContent(
            Placeholder(
                width = (inlineSize + 3f).sp,
                height = (inlineSize + 3f).sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Box(Modifier.size(inlineSize.dp), contentAlignment = Alignment.Center) {
                AnimatedEmojiView(
                    unicode = span.normalizedUnicode,
                    size = inlineSize,
                    animate = animate,
                    interactive = true,
                    onTap = onEmojiTap,
                    mode = EmojiDisplayMode.inline,
                    messageId = messageId,
                    emojiIndex = index,
                    emojiStyle = emojiStyle,
                )
            }
        }
    }
    BasicText(
        text = annotated,
        style = TextStyle(
            color = color,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * height).sp,
        ),
        inlineContent = inlineContent,
    )
}
