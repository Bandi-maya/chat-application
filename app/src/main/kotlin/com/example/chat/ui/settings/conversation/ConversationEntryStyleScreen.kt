package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

private val entryStyles = listOf(
    "Stock",
    "YOWA Style",
    "WAMOD",
    "Hangouts",
    "Simple",
    "Aran V2",
    "Mood",
    "WANH",
    "Hangouts V2",
    "Dribble",
    "Dribble V2",
    "Allo",
    "Rounded",
    "Old Stock (2016)",
    "RC-MSG Google",
    "RC-Telegram X iOS",
    "RC-IOS 11",
    "RC-Line",
    "Concept",
    "Instagram",
    "iOS 14",
    "FB Messenger",
    "RC Mess",
    "Telegram Modern",
    "iOS Clean Rounded",
    "Hangouts Minimal",
    "Simple Pill",
    "RC-Flat Floating",
    "WhatsApp Classic 3D",
    "Signal Compact Bubble"
)

private fun normalizeStyle(raw: String): String {
    val v = raw.trim()
    return if (v.isEmpty() || v == "Stock (Chaty Default)") "Stock" else v
}

private class EntryPreviewColors(
    val buttons: Color, val emoji: Color, val send: Color, val micCircle: Color,
    val textEntryBg: Color, val textEntry: Color,
)

/**
 * Port of ConversationEntryStyleScreen including its own static _buildStylePreview (the Dart
 * screen draws its own preview rather than the chat composer). UNCOMPILED (Compose).
 * Deviation: Dart BoxShadow blur uses elevation-shadow here (shadow(elevation) approximates it).
 */
@Composable
fun ConversationEntryStyleScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val v = version
    val p = preferences.conversation
    val pickers = rememberGbPickers()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val fs = config.fontScale.toFloat()
    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it) }

    fun c(x: Long?, fb: Color) = x?.let { Color(it) } ?: fb
    val uiEntryBg = c(p.uiEntryBackgroundColor, Color(config.surfaceColor))
    val pc = EntryPreviewColors(
        buttons = c(p.uiButtonsColor, Color(config.secondaryTextColor)),
        emoji = c(p.emojiButtonColor, Color(config.secondaryTextColor)),
        send = c(p.sendButtonColor, Color.White),
        micCircle = c(p.micSendBgCircleColor, Color(config.accentColor)),
        textEntryBg = c(p.textEntryBackgroundColor, colors.surfaceSecondary),
        textEntry = c(p.textEntryColor, Color(config.primaryTextColor)),
    )

    Box(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(config.surfaceColor))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
                Text("Conversation Entry style", color = Color(config.primaryTextColor), fontSize = (19f * fs).sp, fontWeight = FontWeight.W700)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
                GbLivePreviewCard(padding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)) {
                    Box(Modifier.fillMaxWidth().background(uiEntryBg, RoundedCornerShape(24.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        EntryStylePreview(normalizeStyle(p.entryStyle), pc)
                    }
                }
                Spacer(Modifier.height(14.dp))
                GbCardContainer {
                    Column {
                        GbSettingRow(
                            icon = Icons.Rounded.Style, title = "Conversation Entry style",
                            subtitle = "Change the style of the Bottom Entry in Conversation screen\nSelected: ${normalizeStyle(p.entryStyle)}",
                            hasSubScreen = true,
                            onTap = {
                                pickers.showRadio("Conversation Entry style", entryStyles, normalizeStyle(preferences.conversation.entryStyle)) { value ->
                                    preferences.updateConversation(preferences.conversation.copy(entryStyle = value))
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        snackbar.showSnackbar("$value applied to the conversation entry bar")
                                    }
                                }
                            },
                        )
                GbSettingRow(
                    icon = Icons.Rounded.Layers, title = "Conversation UI Entry Background", subtitle = "Change the Color of the Entry background",
                    colorValue = p.uiEntryBackgroundColor,
                    onColorTap = { pickColor("Entry Background Color", p.uiEntryBackgroundColor) { c -> preferences.updateConversation(preferences.conversation.copy(uiEntryBackgroundColor = c)) } },
                )
                GbSettingRow(
                    icon = Icons.Rounded.SmartButton, title = "Conversation UI Buttons Color", subtitle = "Change the Color of the Entry icons",
                    colorValue = p.uiButtonsColor,
                    onColorTap = { pickColor("UI Buttons Color", p.uiButtonsColor) { c -> preferences.updateConversation(preferences.conversation.copy(uiButtonsColor = c)) } },
                )
                GbSettingRow(
                    icon = Icons.Rounded.Mood, title = "Emoji Button Color", subtitle = "Emoji button in conversation screen color",
                    colorValue = p.emojiButtonColor,
                    onColorTap = { pickColor("Emoji Button Color", p.emojiButtonColor) { c -> preferences.updateConversation(preferences.conversation.copy(emojiButtonColor = c)) } },
                )
                GbSettingRow(
                    icon = Icons.Rounded.Send, title = "Send Button Color", subtitle = "Send/Mic color in the conversation screen",
                    colorValue = p.sendButtonColor,
                    onColorTap = { pickColor("Send Button Color", p.sendButtonColor) { c -> preferences.updateConversation(preferences.conversation.copy(sendButtonColor = c)) } },
                )
                GbSettingRow(
                    icon = Icons.Rounded.RadioButtonChecked, title = "Mic/Send Background circle", subtitle = "Change the color of the circle behind Send/Mic",
                    colorValue = p.micSendBgCircleColor,
                    onColorTap = { pickColor("Mic/Send Circle Color", p.micSendBgCircleColor) { c -> preferences.updateConversation(preferences.conversation.copy(micSendBgCircleColor = c)) } },
                )
                GbSettingRow(
                    icon = Icons.Rounded.FormatColorFill, title = "Text Entry Background", subtitle = "Text Entry Background",
                    colorValue = p.textEntryBackgroundColor,
                    onColorTap = { pickColor("Text Entry Background Color", p.textEntryBackgroundColor) { c -> preferences.updateConversation(preferences.conversation.copy(textEntryBackgroundColor = c)) } },
                )
                GbSettingRow(
                    icon = Icons.Rounded.FormatColorText, title = "Text Entry Color", subtitle = "Change text color in the conversation box",
                    colorValue = p.textEntryColor,
                    onColorTap = { pickColor("Text Entry Color", p.textEntryColor) { c -> preferences.updateConversation(preferences.conversation.copy(textEntryColor = c)) } },
                )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    pickers.Host()
}

@Composable
private fun EntryStylePreview(style: String, pc: EntryPreviewColors) {
    @Composable fun emojiI(size: Dp = 22.dp) = Icon(Icons.Outlined.EmojiEmotions, null, tint = pc.emoji, modifier = Modifier.size(size))
    @Composable fun attachI(size: Dp = 20.dp) = Icon(Icons.Rounded.AttachFile, null, tint = pc.buttons, modifier = Modifier.size(size))
    @Composable fun cameraI(size: Dp = 20.dp) = Icon(Icons.Outlined.CameraAlt, null, tint = pc.buttons, modifier = Modifier.size(size))
    @Composable fun micCircle() = Box(Modifier.size(44.dp).background(pc.micCircle, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Mic, null, tint = pc.send, modifier = Modifier.size(22.dp))
    }
    @Composable fun sp(w: Int) = Spacer(Modifier.width(w.dp))

    @Composable
    fun RowScope.field(
        radius: Int = 20,
        transparent: Boolean = false,
        border: Boolean = false,
        shadow: Boolean = false,
        leading: (@Composable () -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
    ) {
        val shape = RoundedCornerShape(radius.dp)
        var m = Modifier.weight(1f)
        if (shadow) m = m.shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.16f))
        m = m.background(if (transparent) Color.Transparent else pc.textEntryBg, shape)
        if (border) m = m.border(BorderStroke(1.dp, pc.textEntry.copy(alpha = 0.3f)), shape)
        Row(m.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); sp(8) }
            Text(
                "Message", maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = pc.textEntry.copy(alpha = 0.6f), fontSize = 15.sp, modifier = Modifier.weight(1f),
            )
            if (trailing != null) { sp(8); trailing() }
        }
    }

    @Composable
    fun miniDots(n: Int) = Row(
        Modifier.fillMaxWidth().height(22.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (k in 0 until n) {
            if (k == 0) Icon(Icons.Outlined.EmojiEmotions, null, tint = pc.emoji, modifier = Modifier.size(16.dp))
            else Icon(Icons.Rounded.Circle, null, tint = pc.buttons.copy(alpha = 0.7f), modifier = Modifier.size(5.dp))
        }
    }

    @Composable
    fun twoRow(top: @Composable () -> Unit) = Column { top(); Spacer(Modifier.height(4.dp)); miniDots(6) }

    val row: @Composable (@Composable RowScope.() -> Unit) -> Unit = { content ->
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }

    when (style) {
        "WAMOD", "Hangouts", "Rounded" -> twoRow {
            row { field(16, leading = { emojiI(18.dp) }, trailing = { attachI(18.dp) }); sp(8); micCircle() }
        }
        "Hangouts V2", "Dribble", "Dribble V2" -> Column {
            miniDots(6); Spacer(Modifier.height(4.dp))
            row { field(14); sp(8); micCircle() }
        }
        "Aran V2", "WANH" -> row { attachI(20.dp); sp(6); emojiI(20.dp); sp(6); field(16); sp(8); micCircle() }
        "Allo" -> row { field(20, leading = { attachI(18.dp) }, trailing = { emojiI(18.dp) }); sp(8); micCircle() }
        "RC-Telegram X iOS", "iOS 14" -> row { attachI(20.dp); sp(6); field(18, trailing = { emojiI(18.dp) }); sp(8); micCircle() }
        "RC-IOS 11" -> row { emojiI(20.dp); sp(6); field(18, trailing = { attachI(18.dp) }); sp(8); micCircle() }
        "RC-Line", "FB Messenger", "RC Mess", "Instagram" ->
            row { attachI(20.dp); sp(4); cameraI(20.dp); sp(6); field(18); sp(8); micCircle() }
        "Concept" -> row { field(18, trailing = { cameraI(18.dp); sp(4); emojiI(18.dp) }); sp(8); micCircle() }
        "YOWA Style", "Mood" -> row { field(22, leading = { emojiI(18.dp) }, trailing = { attachI(18.dp) }); sp(8); micCircle() }
        "Simple" -> row { field(22, trailing = { attachI(18.dp) }); sp(8); micCircle() }
        "Old Stock (2016)" -> row { field(8, leading = { emojiI(18.dp) }, trailing = { attachI(18.dp) }); sp(8); micCircle() }
        "Telegram Modern" -> row {
            Icon(Icons.Rounded.AttachFile, null, tint = pc.buttons, modifier = Modifier.size(24.dp)); sp(8)
            field(14, trailing = { emojiI(20.dp) }); sp(8); micCircle()
        }
        "iOS Clean Rounded" -> row {
            Box(Modifier.size(36.dp).background(pc.buttons, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            sp(8); field(22); sp(8); micCircle()
        }
        "Hangouts Minimal" -> row { emojiI(); sp(8); field(8, transparent = true, border = true); sp(8); attachI(22.dp); sp(8); micCircle() }
        "Simple Pill" -> row { field(24, leading = { emojiI(20.dp) }); sp(8); attachI(22.dp); sp(6); cameraI(22.dp); sp(8); micCircle() }
        "RC-Flat Floating" -> {
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier.fillMaxWidth()
                    .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                    .background(pc.textEntryBg, shape).padding(4.dp),
            ) {
                row { field(12, transparent = true, leading = { emojiI(20.dp) }, trailing = { attachI() }); micCircle() }
            }
        }
        "WhatsApp Classic 3D" ->
            row { field(22, shadow = true, leading = { emojiI(20.dp) }, trailing = { attachI(); sp(8); cameraI() }); sp(8); micCircle() }
        "Signal Compact Bubble" -> row { field(18, trailing = { attachI() }); sp(8); micCircle() }
        else -> // "RC-MSG Google", "Stock", default
            row { field(leading = { emojiI() }, trailing = { attachI(); sp(8); cameraI() }); sp(8); micCircle() }
    }
}
