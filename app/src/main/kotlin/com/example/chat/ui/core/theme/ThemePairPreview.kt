package com.example.chat.ui.core.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.templates.ChatListDensity
import com.example.chat.core.templates.ComposerActionPlacement
import com.example.chat.core.templates.HomeHeaderStyle
import com.example.chat.core.templates.PreviewLayout
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.domain.models.DeliveryState
import com.example.chat.ui.chats.detail.DeliveryStatusIcon
import com.example.chat.ui.chats.detail.drawBubble

/** ThemeConfig colors are ARGB Longs. */
internal fun tc(argb: Long): Color = Color(argb.toInt())

/** Dart withValues(alpha:) sets absolute alpha. */
internal fun Color.alpha(a: Float): Color = copy(alpha = a)

/** Dart TextScaler.noScaling: cancel the user's font scale for these fixed-size preview labels. */
@Composable
internal fun fixedSp(v: Float): TextUnit = (v / LocalDensity.current.fontScale).sp

/** Port of lib/ui/core/theme/theme_pair_preview.dart. Uncompiled; no pixel check. */
@Composable
fun ThemePairPreview(
    themeConfig: ThemeConfig,
    modifier: Modifier = Modifier,
    layout: PreviewLayout? = null,
    phoneHeight: Dp = 176.dp,
) {
    val phoneWidth = phoneHeight * 0.56f
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "${themeConfig.name}: home screen and conversation preview" },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        PairLabeled(
            label = if (layout == null) "Home" else "Home \u00b7 ${layout.homeStylePreset}",
            maxWidth = phoneWidth + 24.dp,
            color = tc(themeConfig.secondaryTextColor),
        ) {
            PhoneFrame(themeConfig, phoneWidth, phoneHeight) { HomeScreenMini(themeConfig, layout) }
        }
        PairLabeled(label = "Conversation", maxWidth = Dp.Unspecified, color = tc(themeConfig.secondaryTextColor)) {
            PhoneFrame(themeConfig, phoneWidth, phoneHeight) { ConversationMini(themeConfig, layout) }
        }
    }
}

@Composable
private fun PairLabeled(label: String, maxWidth: Dp, color: Color, child: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        child()
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = color,
            fontSize = fixedSp(10f),
            fontWeight = FontWeight.SemiBold,
            modifier = if (maxWidth == Dp.Unspecified) Modifier else Modifier.widthIn(max = maxWidth),
        )
    }
}

@Composable
private fun PhoneFrame(t: ThemeConfig, width: Dp, height: Dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(width * 0.14f)
    Box(
        Modifier
            .size(width, height)
            .background(tc(t.backgroundColor), shape)
            .border(1.5.dp, tc(t.secondaryTextColor).alpha(0.45f), shape)
            .clip(shape),
    ) { Column { content() } }
}

@Composable
private fun Line(color: Color, width: Dp, height: Dp) {
    Box(Modifier.size(width, height).background(color, RoundedCornerShape(height / 2)))
}

@Composable
private fun HomeScreenMini(t: ThemeConfig, l: PreviewLayout?) {
    val prominent = l?.home?.headerStyle == HomeHeaderStyle.prominentIdentity
    val searchForward = l?.home?.headerStyle == HomeHeaderStyle.searchForward
    val showStories = l?.showStories ?: false
    val rowCount = when (l?.chatList?.density) {
        ChatListDensity.compact -> 6
        ChatListDensity.comfortable -> 4
        else -> 5
    }
    val roundAvatar = (l?.chatList?.avatarShape ?: "circle") == "circle"
    val avatarShape: Shape = if (roundAvatar) CircleShape else RoundedCornerShape(4.dp)
    Column(Modifier.fillMaxWidth().heightIn(min = 0.dp)) {
        Row(
            Modifier.fillMaxWidth().height(if (prominent) 28.dp else 22.dp).background(tc(t.surfaceColor)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prominent) {
                Box(Modifier.size(14.dp).background(tc(t.accentColor).alpha(0.6f), avatarShape))
                Spacer(Modifier.width(4.dp))
            }
            if (searchForward) {
                Box(
                    Modifier.weight(1f).height(12.dp).background(tc(t.cardColor), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Search, null, Modifier.size(9.dp), tint = tc(t.secondaryTextColor)) }
            } else {
                Text(
                    "Chats", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = tc(t.primaryTextColor), fontSize = fixedSp(if (prominent) 10.5f else 9f), fontWeight = FontWeight.ExtraBold,
                )
                Icon(Icons.Rounded.Search, null, Modifier.size(10.dp), tint = tc(t.secondaryTextColor))
            }
        }
        if (showStories) {
            Row(Modifier.fillMaxWidth().height(16.dp).background(tc(t.surfaceColor)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 5) {
                    Box(
                        Modifier.padding(end = 4.dp).size(11.dp)
                            .border(1.2.dp, if (i < 2) tc(t.accentColor) else tc(t.cardColor), CircleShape),
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column {
                for (i in 0 until rowCount) {
                    Row(Modifier.padding(start = 6.dp, top = 4.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(13.dp).background(if (i == 0) tc(t.accentColor).alpha(0.85f) else tc(t.cardColor), avatarShape))
                        Spacer(Modifier.width(5.dp))
                        Column(Modifier.weight(1f)) {
                            Line(tc(t.primaryTextColor).alpha(0.8f), (24 + (i % 3) * 6).dp, 3.dp)
                            Spacer(Modifier.height(2.5.dp))
                            Line(tc(t.secondaryTextColor).alpha(0.55f), (36 - (i % 2) * 8).dp, 2.5.dp)
                        }
                        if (i < 2) Box(Modifier.size(6.dp).background(tc(t.accentColor), CircleShape))
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 5.dp).size(14.dp)
                    .background(tc(t.accentColor), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, null, Modifier.size(10.dp), tint = tc(t.outgoingTextColor)) }
        }
        NavBarMini(t, l)
    }
}

@Composable
private fun PreviewBubble(
    t: ThemeConfig, isMe: Boolean, text: String, fill: Color, textColor: Color, stroke: Color, tick: Boolean = false,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart) {
        Row(
            Modifier
                .drawBehind { drawBubble(t.bubbleStyle, isMe, fill, stroke, tc(t.accentColor)) }
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, color = textColor, fontSize = fixedSp(7.5f), modifier = Modifier.weight(1f, fill = false))
            if (tick) {
                Spacer(Modifier.width(3.dp))
                DeliveryStatusIcon(
                    style = t.deliveryTickStyle,
                    state = DeliveryState.read,
                    unreadColor = textColor.alpha(0.6f),
                    readColor = if (t.isDark) tc(t.accentColor) else tc(t.outgoingTextColor),
                    size = 8.dp,
                )
            }
        }
    }
}

@Composable
private fun ConversationMini(t: ThemeConfig, layout: PreviewLayout?) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(22.dp).background(tc(t.surfaceColor)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(11.dp).background(tc(t.cardColor), CircleShape))
            Spacer(Modifier.width(4.dp))
            Text("Maya", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = tc(t.primaryTextColor), fontSize = fixedSp(8.5f), fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.MoreVert, null, Modifier.size(10.dp), tint = tc(t.secondaryTextColor))
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 5.dp, vertical = 6.dp), verticalArrangement = Arrangement.Bottom) {
            PreviewBubble(t, false, "Lunch?", tc(t.incomingBubbleColor), tc(t.incomingTextColor), tc(t.secondaryTextColor).alpha(0.12f))
            Spacer(Modifier.height(4.dp))
            PreviewBubble(t, true, "Sounds good", tc(t.outgoingBubbleColor), tc(t.outgoingTextColor), tc(t.accentColor).alpha(0.2f), tick = true)
            Spacer(Modifier.height(4.dp))
            PreviewBubble(t, false, "At 1?", tc(t.incomingBubbleColor), tc(t.incomingTextColor), tc(t.secondaryTextColor).alpha(0.12f))
        }
        Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 5.dp)) {
            if (layout?.composer?.actionPlacement == ComposerActionPlacement.powerRow) {
                Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    for (i in 0 until 4) Box(Modifier.size(6.dp).background(tc(t.secondaryTextColor).alpha(0.6f), RoundedCornerShape(2.dp)))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (layout?.composer?.actionPlacement == ComposerActionPlacement.split) {
                    Box(Modifier.size(13.dp).background(tc(t.cardColor), CircleShape))
                    Spacer(Modifier.width(3.dp))
                }
                val r = (((layout?.composer?.cornerRadius ?: 24.0) / 24.0 * 7).coerceIn(3.0, 7.0)).toFloat().dp
                Box(
                    Modifier.weight(1f).height(13.dp)
                        .background(tc(t.surfaceColor), RoundedCornerShape(r))
                        .border(0.7.dp, tc(t.secondaryTextColor).alpha(0.25f), RoundedCornerShape(r)),
                )
                Spacer(Modifier.width(3.dp))
                Box(Modifier.size(13.dp).background(tc(t.accentColor), CircleShape))
            }
        }
    }
}

private fun navIcon(id: String): ImageVector = when (id) {
    "groups" -> Icons.Rounded.Groups
    "updates" -> Icons.Rounded.AutoStories
    "tasks" -> Icons.Rounded.TaskAlt
    "calls" -> Icons.Rounded.Call
    "settings" -> Icons.Rounded.Settings
    "desktop" -> Icons.Rounded.DesktopWindows
    else -> Icons.Rounded.ChatBubble
}

@Composable
private fun NavBarMini(t: ThemeConfig, layout: PreviewLayout?) {
    val style = layout?.bottomBarStyle ?: "Classic Label Bar"
    val ids = if (layout != null && layout.destinationIds.isNotEmpty()) layout.destinationIds else listOf("chats", "updates", "calls")
    val floating = style in setOf("Floating Pill", "Floating Dynamic Island", "Segmented Glass Dock")
    val dark = style == "Floating Dynamic Island"
    val labels = style == "Classic Label Bar"
    val raisedCenter = style == "Raised Center Action"
    val barColor = if (dark) tc(t.primaryTextColor).alpha(0.9f) else tc(t.surfaceColor)
    val inactive = if (dark) tc(t.backgroundColor).alpha(0.7f) else tc(t.secondaryTextColor).alpha(0.7f)
    val barShape: Shape = if (floating) RoundedCornerShape(10.dp) else RoundedCornerShape(0.dp)
    Row(
        Modifier
            .padding(if (floating) androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 8.dp, bottom = 3.dp) else androidx.compose.foundation.layout.PaddingValues(0.dp))
            .fillMaxWidth().height(20.dp)
            .background(barColor, barShape)
            .then(if (floating) Modifier.border(0.8.dp, tc(t.cardColor), barShape) else Modifier),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in ids.indices) {
            val active = i == 0
            val raised = raisedCenter && i == ids.size / 2
            val chip = active && style in setOf("Active Pill Chip", "Soft Square Tile", "Circle Accent Pop")
            val iconTint = if (raised) tc(t.outgoingTextColor) else if (active) tc(t.accentColor) else inactive
            val icon: @Composable () -> Unit = { Icon(navIcon(ids[i]), null, Modifier.size(if (raised) 10.dp else 9.dp), tint = iconTint) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (active && style == "Top Indicator Line") Box(Modifier.size(10.dp, 1.5.dp).background(tc(t.accentColor)))
                else if (style == "Top Indicator Line") Spacer(Modifier.height(1.5.dp))
                if (raised) {
                    Box(Modifier.size(14.dp).background(tc(t.accentColor), CircleShape), contentAlignment = Alignment.Center) { icon() }
                } else if (chip) {
                    val chipShape: Shape = when (style) {
                        "Circle Accent Pop" -> CircleShape
                        "Soft Square Tile" -> RoundedCornerShape(3.dp)
                        else -> RoundedCornerShape(8.dp)
                    }
                    Box(
                        Modifier.background(tc(t.accentColor).alpha(0.2f), chipShape)
                            .padding(horizontal = if (style == "Active Pill Chip") 6.dp else 3.dp, vertical = 1.5.dp),
                    ) { icon() }
                } else icon()
                if (labels) Box(Modifier.padding(top = 1.dp)) { Line(if (active) tc(t.accentColor) else inactive, 9.dp, 1.5.dp) }
                if (active && (style == "Bottom Indicator Dot" || style == "Curved Notch Teardrop")) {
                    Box(Modifier.padding(top = 1.dp).size(3.dp).background(tc(t.accentColor), CircleShape))
                }
            }
        }
    }
}
