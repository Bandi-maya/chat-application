package com.example.chat.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** Initials avatar with the caller's stored color (Dart AppAvatar usage in
 *  the call screens: initials circle colored by avatarColorHex). */
@Composable
fun CallAvatar(
    initials: String,
    colorHex: String?,
    size: Dp,
) {
    val background = parseCallAvatarColor(colorHex)
    Box(
        modifier = Modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.ifEmpty { "U" },
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.W700,
        )
    }
}

/** Accepts the avatar color formats the backend stores: "0xFF6366F1",
 *  "#6366F1", or "FF6366F1". Falls back to the Chaty indigo. */
fun parseCallAvatarColor(colorHex: String?): Color {
    if (colorHex.isNullOrBlank()) return Color(0xFF6366F1)
    val raw = colorHex.trim()
    val value = when {
        raw.startsWith("0x", ignoreCase = true) -> raw.substring(2).toLongOrNull(16)
        raw.startsWith("#") -> raw.substring(1).toLongOrNull(16)
        else -> raw.toLongOrNull(16)
    } ?: return Color(0xFF6366F1)
    val argb = if (value <= 0xFFFFFF) (0xFF000000L or value) else value
    return Color(argb)
}
