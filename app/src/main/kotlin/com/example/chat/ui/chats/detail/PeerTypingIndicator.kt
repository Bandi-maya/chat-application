package com.example.chat.ui.chats.detail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.chats.home.HomeAvatar
import com.example.chat.ui.chats.home.parseAvatarColorHex

/** Incoming transient activity only. No name is drawn and no extra network loop is started. */
@Composable
fun PeerTypingIndicator(
    initials: String,
    avatarUrl: String?,
    avatarColorHex: String?,
    theme: ThemeConfig,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val phase = if (reducedMotion) 0.5f else {
        val transition = rememberInfiniteTransition(label = "peer-typing")
        val value by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "typing-dots",
        )
        value
    }
    Row(
        modifier
            .clearAndSetSemantics { contentDescription = "Contact is typing" }
            .background(Color(theme.cardColor), RoundedCornerShape(28.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        HomeAvatar(
            initials = initials, color = parseAvatarColorHex(avatarColorHex, Color(theme.accentColor)),
            size = 28f, shape = "circle", url = avatarUrl,
        )
        Canvas(Modifier.size(width = 34.dp, height = 20.dp)) {
            repeat(3) { index ->
                val alpha = if (reducedMotion) 0.8f else {
                    val wave = kotlin.math.sin((phase * 2f - index * 0.4f) * kotlin.math.PI).toFloat()
                    (0.45f + 0.5f * ((wave + 1f) / 2f)).coerceIn(0.45f, 0.95f)
                }
                drawCircle(
                    Color(theme.primaryTextColor).copy(alpha = alpha), radius = 2.5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset((6f + index * 11f).dp.toPx(), size.height / 2),
                )
            }
        }
    }
}
