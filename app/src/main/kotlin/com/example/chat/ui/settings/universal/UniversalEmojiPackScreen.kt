package com.example.chat.ui.settings.universal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.chats.detail.AnimatedEmojiView
import com.example.chat.ui.chats.detail.ChatyEmojiPack
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.settings.rememberPreferencesVersion
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * Emoji pack chooser: Chaty (animated Noto, default), iOS, Microsoft, OpenMoji, supplied Telegram WebP and TheSub Lottie sets. Writes the
 * existing universal.emojiStyle preference; chat bubbles, the picker and
 * reaction badges read it live. Colors come from the theme config.
 * Source integration requires compile-only CI validation.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UniversalEmojiPackScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    onMessage: (String) -> Unit = {},
) {
    rememberPreferencesVersion(preferences)
    val config = LocalThemeConfig.current
    val view = LocalView.current
    val active = ChatyEmojiPack.normalize(preferences.universal.emojiStyle)
    val accent = Color(config.accentColor)
    val surface = Color(config.surfaceColor)
    val primaryText = Color(config.primaryTextColor)
    val secondaryText = Color(config.secondaryTextColor)

    var downloadsOpen by remember { mutableStateOf(false) }
    if (com.example.chat.data.packs.ChatyPackServer.DOWNLOADABLE_PACKS_ENABLED && downloadsOpen) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { downloadsOpen = false }) {
        com.example.chat.ui.chats.detail.EmojiPackManagerSheet(config, { downloadsOpen = false }, Modifier.height(480.dp))
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val packStore = com.example.chat.ui.chats.detail.rememberPackStore()
    val revision = packStore?.revision?.collectAsState()?.value ?: 0
    val remoteCounts by produceState<Map<String, Int>>(emptyMap(), packStore, revision) {
        val store = packStore
        value = if (store == null) emptyMap() else store.manifest.associate { pack ->
            pack.id to (if (store.installedVersion(pack.id) == pack.version) pack.files.size else 0)
        }
    }
    val iosBundled = remoteCounts["ios_animated"] ?: 0
    val iosState = when {
        !ChatyEmojiPack.iosSupported() -> ChatyEmojiPack.IosState.Unsupported
        iosBundled == 0 -> ChatyEmojiPack.IosState.AssetsMissing
        iosBundled < ChatyEmojiPack.coverage -> ChatyEmojiPack.IosState.Partial
        else -> ChatyEmojiPack.IosState.Ready
    }
    val sdkOk = ChatyEmojiPack.iosSupported()
    val msBundled = remoteCounts["fluent_animated"] ?: 0
    val driveBundled = remoteCounts["drive_mov132"] ?: 0
    val lottieBundled = remoteCounts["telegram_lottie"] ?: 0
    val telegramBundled = remoteCounts["telegram19"] ?: 0
    val omBundled = 50 // This lightweight subset is retained in the APK.

    class Entry(val id: String, val title: String, val subtitle: String, val enabled: Boolean)
    val entries = listOf(
        Entry(
            ChatyEmojiPack.CHATY, "Chaty",
            "Light default: 50 bundled static OpenMoji glyphs; all other emojis use the device's Unicode glyphs. No network download.", true,
        ),
        Entry(
            ChatyEmojiPack.IOS, "iOS - supplied 50",
            when (iosState) {
                ChatyEmojiPack.IosState.Ready -> "iOS style animated faces (${ChatyEmojiPack.coverage} emojis)"
                ChatyEmojiPack.IosState.Partial -> "iOS style animated faces ($iosBundled of ${ChatyEmojiPack.coverage} emojis installed)"
                ChatyEmojiPack.IosState.AssetsMissing -> "Not downloaded. Use Download or manage extra packs."
                ChatyEmojiPack.IosState.Unsupported -> "Unavailable: animated iOS emojis need Android 9 or newer"
            },
            iosState == ChatyEmojiPack.IosState.Ready || iosState == ChatyEmojiPack.IosState.Partial,
        ),
        Entry(
            ChatyEmojiPack.MICROSOFT, "Microsoft",
            when {
                !sdkOk -> "Unavailable: animated Microsoft emojis need Android 9 or newer"
                msBundled == 0 -> "Not downloaded. Use Download or manage extra packs."
                else -> "Microsoft Fluent animated emojis ($msBundled installed, others use normal Unicode glyphs)"
            },
            sdkOk && msBundled > 0,
        ),
        Entry(
            ChatyEmojiPack.OPENMOJI, "OpenMoji",
            if (omBundled == 0) "Unavailable: the OpenMoji files are not installed in this build"
            else "OpenMoji static emojis, not animated ($omBundled installed, others use normal Unicode glyphs)",
            omBundled > 0,
        ),
        Entry(ChatyEmojiPack.DRIVE_MOV, "Drive MOV animations", if (!sdkOk) "Unavailable: these animations need Android 9 or newer" else "$driveBundled of 132 source animations installed. This separate source overlaps the iOS ZIP's first 50 designs; missing animations use normal glyphs.", sdkOk && driveBundled > 0),
        Entry(ChatyEmojiPack.TELEGRAM_LOTTIE, "Telegram-style Lottie - TheSub", "$lottieBundled of 11 supplied animations. Separate style; other emojis use normal glyphs. LottieFiles License; not official Telegram art.", lottieBundled == 11),
        Entry(ChatyEmojiPack.TELEGRAM, "Telegram - supplied pack", when {
            !sdkOk -> "Unavailable: these animations need Android 9 or newer"
            telegramBundled != 19 -> ChatyEmojiPack.TELEGRAM_UNAVAILABLE_REASON
            else -> "19 supplied animations. Other emojis keep their normal glyphs. Yawn is enlarged from a small GIF and is softer."
        }, sdkOk && telegramBundled == 19),
    )
    val effective = entries.firstOrNull { it.id == active && it.enabled }?.id ?: ChatyEmojiPack.CHATY

    fun choose(entry: Entry) {
        if (entry.id == active || !entry.enabled) return
        ChatyHapticsPort.selection(view)
        preferences.updateUniversal(
            preferences.universal.copy(emojiStyle = entry.id),
            logTitle = "Emoji pack", prevVal = active, newVal = entry.id,
        )
        onMessage("${entry.title} emoji pack selected")
    }

    UniversalScaffold(title = "Emoji pack", onBack = onBack) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (com.example.chat.data.packs.ChatyPackServer.DOWNLOADABLE_PACKS_ENABLED) androidx.compose.material3.TextButton(onClick = { downloadsOpen = true }) { Text("Download or manage extra packs") }
            // Extra entries are paused, not deleted; saved style preferences are not rewritten.
            for (e in entries.filter { com.example.chat.data.packs.ChatyPackServer.DOWNLOADABLE_PACKS_ENABLED || it.id == ChatyEmojiPack.CHATY }) {
                PackCard(
                    title = e.title, subtitle = e.subtitle, enabled = e.enabled,
                    pack = e.id, selected = effective == e.id,
                    accent = accent, surface = surface, primaryText = primaryText, secondaryText = secondaryText,
                    onClick = { choose(e) },
                )
            }
            Text(
                if (active != effective)
                    "$active is saved as your choice but is not available here, so Chaty emojis are shown."
                else
                    if (active == ChatyEmojiPack.DRIVE_MOV) "User-supplied third-party shared Drive MOV source. Converted from the source animations, not an official or complete Unicode pack. Missing artwork uses normal glyphs." else if (active == ChatyEmojiPack.TELEGRAM_LOTTIE) "TheSub on LottieFiles, LottieFiles License. Telegram-style recreations, not official Telegram art. Other emojis use normal glyphs." else if (active == ChatyEmojiPack.TELEGRAM) "Telegram emoji designs; meii pack on emoji.gg. Higher-quality variants from supplied files; not an official or complete Telegram pack. Other emojis use normal glyphs, not substitute artwork." else "Emojis a pack does not include are shown in the Chaty style. Animated emojis loop continuously. OpenMoji is a static pack.",
                color = secondaryText, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PackCard(
    title: String, subtitle: String, enabled: Boolean, pack: String, selected: Boolean,
    accent: Color, surface: Color, primaryText: Color, secondaryText: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(surface, RoundedCornerShape(16.dp))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else secondaryText.copy(alpha = 0.3f)), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Disabled packs show no sample: it would only be the Chaty fallback.
            if (enabled) for (e in if (pack == ChatyEmojiPack.DRIVE_MOV) listOf("🤯", "🤷‍♂️", "🔥") else if (pack == ChatyEmojiPack.TELEGRAM) listOf("🥳", "🤔", "😴") else if (pack == ChatyEmojiPack.TELEGRAM_LOTTIE) listOf("😅", "📖", "🚗") else listOf("😂", "😍", "😴")) {
                AnimatedEmojiView(unicode = e, size = 34f, emojiStyle = pack)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = primaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = secondaryText, fontSize = 12.5.sp)
        }
        if (selected) Text("Selected", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
