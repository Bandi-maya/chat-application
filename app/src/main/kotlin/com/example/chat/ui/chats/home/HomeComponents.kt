package com.example.chat.ui.chats.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times

/** Small design-system ports from chaty_kit.dart used by the home list. */

/** ChatyAvatarCore.parseColorHex: accepts '#RRGGBB', '#AARRGGBB' and
 *  '0xAARRGGBB' (Conversation.fromApi emits the latter); falls back when the
 *  value is missing or malformed. */
fun parseAvatarColorHex(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    val raw = hex.trim()
    return try {
        when {
            raw.startsWith("0x") || raw.startsWith("0X") ->
                Color(raw.substring(2).toLong(16))
            raw.startsWith("#") -> {
                val h = raw.substring(1)
                when (h.length) {
                    6 -> Color(0xFF000000L or h.toLong(16))
                    8 -> Color(h.toLong(16))
                    else -> fallback
                }
            }
            else -> fallback
        }
    } catch (_: NumberFormatException) {
        fallback
    }
}

/** ChatyAvatar shape radii (chaty_kit.dart): squircle 35%, roundedSquare
 *  22%, circle half. */
fun avatarShapeFor(shape: String, sizeDp: Float): androidx.compose.ui.graphics.Shape = when (shape) {
    "squircle" -> RoundedCornerShape(sizeDp * 0.35f)
    "roundedSquare" -> RoundedCornerShape(sizeDp * 0.22f)
    else -> CircleShape
}

/** ChatyAvatar: initials tile, white w600 label at 38% of size. */
@Composable
fun HomeAvatar(
    initials: String,
    color: Color,
    size: Float,
    shape: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(color, avatarShapeFor(shape, size)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            maxLines = 1,
            color = Color.White,
            fontWeight = FontWeight.W600,
            fontSize = (size * 0.38f).sp,
        )
    }
}

/** ChatyOnlineDot: diameter = (avatarSize * 0.28).clamp(10, 14), 2dp ring. */
@Composable
fun HomeOnlineDot(active: Boolean, avatarSize: Float, color: Color, ringColor: Color) {
    if (!active) return
    val diameter = (avatarSize * 0.28f).coerceIn(10f, 14f)
    Box(
        modifier = Modifier
            .size(diameter.dp)
            .background(ringColor, CircleShape)
            .padding(2.dp)
            .background(color, CircleShape),
    )
}

/** ChatyCountBadge: full-round pill, min 20x20, bold 12pt digits. */
@Composable
fun HomeCountBadge(count: Int, color: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .widthIn(min = 20.dp)
            .heightIn(min = 20.dp)
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            lineHeight = 12.sp,
        )
    }
}

/** ChatyTimeLabel: 12pt, w700 highlighted / w500 otherwise. */
@Composable
fun HomeTimeLabel(text: String, highlight: Boolean, color: Color, highlightColor: Color) {
    Text(
        text,
        maxLines = 1,
        color = if (highlight) highlightColor else color,
        fontSize = 12.sp,
        fontWeight = if (highlight) FontWeight.W700 else FontWeight.W500,
        lineHeight = 12.sp,
    )
}

/** ChatySectionHeader: uppercase, w800, letterSpacing 0.8. */
@Composable
fun HomeSectionHeader(text: String, color: Color, fontSize: Float = 11.5f) {
    Text(
        text.uppercase(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = 0.8.sp,
        lineHeight = fontSize.sp,
    )
}
