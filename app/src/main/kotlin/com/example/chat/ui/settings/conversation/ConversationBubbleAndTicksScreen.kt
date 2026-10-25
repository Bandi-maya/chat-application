package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.PersonPin
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.ReadMore
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.GbSwitchTile
import com.example.chat.ui.kit.rememberGbPickers
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString
import com.example.chat.core.theme.gbBubbleVectorInterpretations
import com.example.chat.core.theme.gbTickVectorInterpretations
import com.example.chat.domain.models.DeliveryState
import com.example.chat.ui.chats.detail.DeliveryStatusIcon
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.ChatyBubbleContainer
import com.example.chat.ui.kit.ChatyOptionGallery
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSliderTile
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

private val tickStyleIds: List<DeliveryIconStyle> = listOf(
    DeliveryIconStyle.stock,
    DeliveryIconStyle.allo,
    DeliveryIconStyle.alien,
    DeliveryIconStyle.altCrayonV2,
    DeliveryIconStyle.altCrayon,
    DeliveryIconStyle.aran,
    DeliveryIconStyle.bar,
    DeliveryIconStyle.bat,
    DeliveryIconStyle.bbm,
    DeliveryIconStyle.blend,
    DeliveryIconStyle.ngAndroidBot,
    DeliveryIconStyle.bpgCircle,
    DeliveryIconStyle.gbCircles,
    DeliveryIconStyle.samyadeepCircles,
    DeliveryIconStyle.coalfield,
    DeliveryIconStyle.colorSimple,
    DeliveryIconStyle.ctoon,
    DeliveryIconStyle.eclipsis,
    DeliveryIconStyle.faceSwitch,
    DeliveryIconStyle.feet,
    DeliveryIconStyle.flatCircles,
    DeliveryIconStyle.gifcon,
    DeliveryIconStyle.googleV2,
    DeliveryIconStyle.google,
    DeliveryIconStyle.grw,
    DeliveryIconStyle.sidzyHd,
    DeliveryIconStyle.svHeart,
    DeliveryIconStyle.hike,
    DeliveryIconStyle.inbox,
    DeliveryIconStyle.insect,
    DeliveryIconStyle.gbIos,
    DeliveryIconStyle.jalopy,
    DeliveryIconStyle.jessy,
    DeliveryIconStyle.joaquins,
    DeliveryIconStyle.jokerTraffic,
    DeliveryIconStyle.letter,
    DeliveryIconStyle.like,
    DeliveryIconStyle.loveSwitch,
    DeliveryIconStyle.materialV2,
    DeliveryIconStyle.materialSwitch,
    DeliveryIconStyle.messenger,
    DeliveryIconStyle.mickeyHands,
    DeliveryIconStyle.minions,
    DeliveryIconStyle.newWaca,
    DeliveryIconStyle.neyHeart,
    DeliveryIconStyle.nhsc,
    DeliveryIconStyle.nazaNh,
    DeliveryIconStyle.nlCircle,
    DeliveryIconStyle.oldWaca,
    DeliveryIconStyle.pacman,
    DeliveryIconStyle.pixelated,
    DeliveryIconStyle.pokemon,
    DeliveryIconStyle.quinn,
    DeliveryIconStyle.snapChat,
    DeliveryIconStyle.sDot,
    DeliveryIconStyle.seasons,
    DeliveryIconStyle.smile,
    DeliveryIconStyle.star,
    DeliveryIconStyle.flatSwitch,
    DeliveryIconStyle.timeBomb,
    DeliveryIconStyle.jokerTraffic2,
    DeliveryIconStyle.treble,
    DeliveryIconStyle.twitterConcept,
    DeliveryIconStyle.wbg,
    DeliveryIconStyle.x,
    DeliveryIconStyle.pointer,
    DeliveryIconStyle.ticTicCircle2,
    DeliveryIconStyle.iceCream,
    DeliveryIconStyle.pony,
    DeliveryIconStyle.kitty,
    DeliveryIconStyle.flower,
    DeliveryIconStyle.sticker,
    DeliveryIconStyle.rcIos11,
    DeliveryIconStyle.bbmV2,
    DeliveryIconStyle.bwTicks,
    DeliveryIconStyle.cirCheck,
    DeliveryIconStyle.circlePrint,
    DeliveryIconStyle.gabCircle,
    DeliveryIconStyle.gabFace,
    DeliveryIconStyle.gabIflo,
    DeliveryIconStyle.greenTick,
    DeliveryIconStyle.ios2,
    DeliveryIconStyle.letterCircle,
    DeliveryIconStyle.rcAlo,
    DeliveryIconStyle.rcTick,
    DeliveryIconStyle.triangle,
    DeliveryIconStyle.vantCircle,
    DeliveryIconStyle.wings,
    DeliveryIconStyle.trafficLights,
    DeliveryIconStyle.stars,
    DeliveryIconStyle.hearts,
    DeliveryIconStyle.batman,
    DeliveryIconStyle.doubleSword,
    DeliveryIconStyle.glowingDots,
    DeliveryIconStyle.material3Rounded,
)

private val bubbleStyleIds: List<BubbleStyleId> = listOf(
    BubbleStyleId.stock,
    BubbleStyleId.facebookMessenger,
    BubbleStyleId.oldHangouts,
    BubbleStyleId.threeD,
    BubbleStyleId.rounded,
    BubbleStyleId.materialized,
    BubbleStyleId.md,
    BubbleStyleId.samyadeepCrayonAlt,
    BubbleStyleId.newHangouts4,
    BubbleStyleId.waPaperRedesigned,
    BubbleStyleId.transparent,
    BubbleStyleId.twitter,
    BubbleStyleId.whatsappLb,
    BubbleStyleId.bbm,
    BubbleStyleId.ios,
    BubbleStyleId.hike,
    BubbleStyleId.textra,
    BubbleStyleId.goSms,
    BubbleStyleId.bubbleDrop,
    BubbleStyleId.samyadeepDual,
    BubbleStyleId.plusMessengerEd,
    BubbleStyleId.waChatOn,
    BubbleStyleId.telegram,
    BubbleStyleId.inBubble,
    BubbleStyleId.bryed,
    BubbleStyleId.fold,
    BubbleStyleId.foldV2,
    BubbleStyleId.ilkhang,
    BubbleStyleId.line,
    BubbleStyleId.popzup,
    BubbleStyleId.doodleHang,
    BubbleStyleId.aranbor,
    BubbleStyleId.win,
    BubbleStyleId.mood,
    BubbleStyleId.crMessenger,
    BubbleStyleId.rcBurbuja2,
    BubbleStyleId.rcBurbuja5,
    BubbleStyleId.threeDV2,
    BubbleStyleId.kitty,
    BubbleStyleId.amor,
    BubbleStyleId.rcSamsBord,
    BubbleStyleId.rcIos11,
    BubbleStyleId.rcGoogleAssistan,
    BubbleStyleId.gabiOutline,
    BubbleStyleId.gabiSqua,
    BubbleStyleId.gabiDot,
    BubbleStyleId.rcLine,
    BubbleStyleId.gabiDot2,
    BubbleStyleId.roundle,
    BubbleStyleId.gabyRon,
    BubbleStyleId.cvIos,
    BubbleStyleId.rcPal,
    BubbleStyleId.rcIosLine,
    BubbleStyleId.rcImLine,
    BubbleStyleId.rcGoSms,
    BubbleStyleId.rcTranLine,
    BubbleStyleId.rcImess,
    BubbleStyleId.gabiMess,
    BubbleStyleId.rcFancy,
)

private fun conv(v: Long?, fallback: Color) = v?.let { Color(it) } ?: fallback

/**
 * Port of ConversationBubbleAndTicksScreen (_ConversationBubbleAndTicksScreenState).
 * Tick key: gbIos/gbCircles persist by enum name, others by displayName.
 * Bubble set: bubbleStyle (bubbleShape is a read-only alias in Kotlin) + updateGbFeatures
 * {'bubble_style'} with logTitle 'Bubbles Style'. UNCOMPILED (Compose); tick preview paints the
 * 16dp W2 icon inside a 22x16 box (Dart paints the painter on a 22x16 canvas).
 */
@Composable
fun ConversationBubbleAndTicksScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
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
    val accent = Color(config.accentColor)
    val fs = config.fontScale.toFloat()

    val convBg = conv(p.convBackgroundColor, colors.surface)
    val rightBubbleBg = conv(p.rightBubbleColor, accent)
    val rightTextCol = conv(p.rightChatBubbleTextColor, Color.White)
    val rightTimeCol = conv(p.rightBubbleTimeColor, Color.White.copy(alpha = 0.7f))
    val leftBubbleBg = conv(p.leftBubbleColor, colors.surfaceSecondary)
    val leftTextCol = conv(p.leftChatBubbleTextColor, Color(config.primaryTextColor))
    val leftTimeCol = conv(p.leftBubbleTimeColor, Color(config.secondaryTextColor))
    val quotedBg = conv(p.quotedBackgroundColor, colors.surface)
    val quotedDivider = conv(p.quotedDividerColor, accent)
    val quotedName = conv(p.quotedNameColor, accent)
    val quotedMsg = conv(p.quotedMessageColor, Color(config.secondaryTextColor))
    val textSize = p.messageTextSize.toFloat()

    fun setTickStyle(id: DeliveryIconStyle) {
        val key = if (id == DeliveryIconStyle.gbIos || id == DeliveryIconStyle.gbCircles) id.name else id.displayName
        preferences.updateConversation(preferences.conversation.copy(tickStyle = key))
    }
    fun setBubbleStyle(id: BubbleStyleId) {
        val value = id.displayName
        preferences.updateConversation(preferences.conversation.copy(bubbleStyle = value))
        preferences.updateGbFeatures(mapOf("bubble_style" to value), logTitle = "Bubbles Style")
    }

    @Composable
    fun tickPreview(id: DeliveryIconStyle, color: Color) {
        Box(Modifier.width(22.dp).height(16.dp), contentAlignment = Alignment.Center) {
            DeliveryStatusIcon(style = id, state = DeliveryState.read, unreadColor = color, readColor = color, size = 16.dp)
        }
    }

    val pickers = rememberGbPickers()
    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it) }

    val leftShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)

    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        Row(
            Modifier.fillMaxWidth().background(Color(config.surfaceColor))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text("Bubbles And Ticks", color = Color(config.primaryTextColor), fontSize = (19f * fs).sp, fontWeight = FontWeight.W700)
        }
        Box(Modifier.padding(14.dp, 10.dp, 14.dp, 4.dp)) {
            GbLivePreviewCard(padding = PaddingValues(12.dp)) {
                Column(
                    Modifier.fillMaxWidth().background(convBg, RoundedCornerShape(16.dp)).padding(12.dp),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Column(
                            Modifier.padding(bottom = 6.dp).widthIn(max = 240.dp)
                                .background(leftBubbleBg, leftShape).padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("Tyler Durden", color = accent, fontSize = 11.sp, fontWeight = FontWeight.W700)
                            Spacer(Modifier.height(2.dp))
                            Text("Hello, I am Tyler Durden", color = leftTextCol, fontSize = textSize.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Row(
                            Modifier.padding(bottom = 6.dp).widthIn(max = 240.dp)
                                .background(leftBubbleBg, leftShape).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Do you like my theme?", color = leftTextCol, fontSize = textSize.sp, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp))
                            Text("07:11 PM", color = leftTimeCol, fontSize = 10.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.widthIn(max = 250.dp)) {
                            ChatyBubbleContainer(
                                styleId = bubbleStyleIdFromString(p.bubbleStyle),
                                isMe = true, fillColor = rightBubbleBg, accentColor = accent,
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Column(
                                        Modifier.padding(bottom = 4.dp)
                                            .background(quotedBg, RoundedCornerShape(8.dp))
                                            .drawBehind {
                                                // Border(left: 3.5) over rounded 8 box
                                                drawRect(quotedDivider, Offset.Zero, androidx.compose.ui.geometry.Size(3.5.dp.toPx(), size.height))
                                            }
                                            .padding(6.dp),
                                    ) {
                                        Text("Tyler Durden", color = quotedName, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text("Do you like my theme?", color = quotedMsg, fontSize = 11.sp)
                                    }
                                    Text("Yes, your theme looks awesome!", color = rightTextCol, fontSize = textSize.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("07:11 PM", color = rightTimeCol, fontSize = 10.sp)
                                        Spacer(Modifier.width(4.dp))
                                        tickPreview(deliveryIconStyleFromString(p.tickStyle), rightTimeCol)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            GbCardContainer(headerText = "Styles (Look and feel)") {
                Column {
                    ChatyOptionGallery(
                        title = "Ticks style",
                        subtitle = "Tap a style. The preview above updates right away.",
                        values = tickStyleIds,
                        selected = deliveryIconStyleFromString(p.tickStyle),
                        labelOf = { id -> if (id in gbTickVectorInterpretations) "${id.displayName} (vector redraw)" else id.displayName },
                        onSelected = { setTickStyle(it) },
                    ) { id -> tickPreview(id, accent) }
                    ChatyOptionGallery(
                        title = "Bubbles style",
                        subtitle = "Tap a shape to preview it in the conversation.",
                        values = bubbleStyleIds,
                        selected = bubbleStyleIdFromString(p.bubbleStyle),
                        labelOf = { id -> if (id in gbBubbleVectorInterpretations) "${id.displayName} (vector redraw)" else id.displayName },
                        onSelected = { setBubbleStyle(it) },
                    ) { id ->
                        Box(Modifier.width(64.dp)) {
                            ChatyBubbleContainer(
                                styleId = id, isMe = true,
                                fillColor = accent.copy(alpha = 0.85f), accentColor = accent,
                            ) { Text("Hi", color = Color.White, fontSize = 11.sp) }
                        }
                    }
                    GbSliderTile(
                        title = "Message Text Size",
                        subtitle = "Set chat text font size in points",
                        value = p.messageTextSize.toFloat(),
                        min = 10f, max = 26f, divisions = 16,
                        onChanged = { preferences.updateConversation(preferences.conversation.copy(messageTextSize = it.toDouble())) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            GbCardContainer(headerText = "Colors") {
                Column {
                    GbSettingRow(
                        icon = Icons.Rounded.Wallpaper, title = "Conversation Background Color", subtitle = "Change background color of conversation, when set to \"no wallpaper\"",
                        colorValue = p.convBackgroundColor,
                        onColorTap = { pickColor("Conversation Background Color", p.convBackgroundColor) { c -> preferences.updateConversation(preferences.conversation.copy(convBackgroundColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.ChatBubble, title = "Right Bubble Color", subtitle = "Change right chat bubble color",
                        colorValue = p.rightBubbleColor,
                        onColorTap = { pickColor("Right Bubble Color", p.rightBubbleColor) { c -> preferences.updateConversation(preferences.conversation.copy(rightBubbleColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Right Chat Bubble Text Color", subtitle = "Sets Right chat bubble text color",
                        colorValue = p.rightChatBubbleTextColor,
                        onColorTap = { pickColor("Right Bubble Text Color", p.rightChatBubbleTextColor) { c -> preferences.updateConversation(preferences.conversation.copy(rightChatBubbleTextColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.AccessTime, title = "Right Bubble time color", subtitle = "Change the color of the time inside the right bubble",
                        colorValue = p.rightBubbleTimeColor,
                        onColorTap = { pickColor("Right Bubble Time Color", p.rightBubbleTimeColor) { c -> preferences.updateConversation(preferences.conversation.copy(rightBubbleTimeColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.ChatBubbleOutline, title = "Left Bubble Color", subtitle = "Change left chat bubble color",
                        colorValue = p.leftBubbleColor,
                        onColorTap = { pickColor("Left Bubble Color", p.leftBubbleColor) { c -> preferences.updateConversation(preferences.conversation.copy(leftBubbleColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Left Chat Bubble Text Color", subtitle = "Sets Left chat bubble text color",
                        colorValue = p.leftChatBubbleTextColor,
                        onColorTap = { pickColor("Left Bubble Text Color", p.leftChatBubbleTextColor) { c -> preferences.updateConversation(preferences.conversation.copy(leftChatBubbleTextColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.AccessTime, title = "Left Bubble time color", subtitle = "Change the color of the time inside the left bubble",
                        colorValue = p.leftBubbleTimeColor,
                        onColorTap = { pickColor("Left Bubble Time Color", p.leftBubbleTimeColor) { c -> preferences.updateConversation(preferences.conversation.copy(leftBubbleTimeColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.DeleteOutline, title = "Deleted Message Icon Color", subtitle = "Change the color of the revoked icon on deleted messages",
                        colorValue = p.deletedMessageIconColor,
                        onColorTap = { pickColor("Deleted Message Icon Color", p.deletedMessageIconColor) { c -> preferences.updateConversation(preferences.conversation.copy(deletedMessageIconColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalSplit, title = "Quoted Divider Color", subtitle = "Color of the vertical bar beside quote",
                        colorValue = p.quotedDividerColor,
                        onColorTap = { pickColor("Quoted Divider Color", p.quotedDividerColor) { c -> preferences.updateConversation(preferences.conversation.copy(quotedDividerColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.PersonPin, title = "Quoted Name Color", subtitle = "Sender name color inside quoted reply",
                        colorValue = p.quotedNameColor,
                        onColorTap = { pickColor("Quoted Name Color", p.quotedNameColor) { c -> preferences.updateConversation(preferences.conversation.copy(quotedNameColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.Notes, title = "Quoted Message Color", subtitle = "Quoted body text color",
                        colorValue = p.quotedMessageColor,
                        onColorTap = { pickColor("Quoted Message Color", p.quotedMessageColor) { c -> preferences.updateConversation(preferences.conversation.copy(quotedMessageColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.Layers, title = "Quoted Background Color", subtitle = "Background of quote container",
                        colorValue = p.quotedBackgroundColor,
                        onColorTap = { pickColor("Quoted Background Color", p.quotedBackgroundColor) { c -> preferences.updateConversation(preferences.conversation.copy(quotedBackgroundColor = c)) } },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.SelectAll, title = "Make Text Selectable", subtitle = "Enable chat bubble text selection",
                        value = p.makeTextSelectable,
                        onChanged = { preferences.updateConversation(preferences.conversation.copy(makeTextSelectable = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.ReadMore, title = "Remove Read more...", subtitle = "Remove the Read more... button and display full long text",
                        value = p.removeReadMore,
                        onChanged = { preferences.updateConversation(preferences.conversation.copy(removeReadMore = it)) },
                    )
                }
            }
        }
    }
    pickers.Host()
}
