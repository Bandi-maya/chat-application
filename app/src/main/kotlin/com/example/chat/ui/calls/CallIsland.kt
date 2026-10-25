package com.example.chat.ui.calls

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.data.services.CallSessionState
import com.example.chat.data.services.ChatyCallSession
import kotlin.math.sin

/**
 * Port of features/calls/widgets/chaty_call_island.dart: compact
 * iOS-Dynamic-Island-inspired call indicator (~130x38dp) with live animated
 * audio waveform. Single tap expands into floating PiP / full call screen.
 */
@Composable
fun ChatyCallIsland(
    session: ChatyCallSession,
    durationSeconds: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wave by rememberInfiniteTransition(label = "islandWave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "islandWaveValue",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(38.dp)
            .background(Color(0xFF0F0F11), RoundedCornerShape(19.dp))
            .clickable { onTap() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(22.dp).background(Color(0xFF22C55E), CircleShape),
        ) {
            Icon(
                if (session.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when (session.state) {
                CallSessionState.connected -> formatCallDuration(durationSeconds)
                CallSessionState.reconnecting -> "Reconnecting"
                else -> "Calling…"
            },
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
        )
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { index ->
                val phase = (wave + (index * 0.25f)) % 1.0f
                val barHeight = 4f + sin(phase * Math.PI).toFloat() * 10f
                Box(
                    Modifier
                        .padding(horizontal = 1.2.dp)
                        .width(2.5.dp)
                        .height(barHeight.dp)
                        .background(Color(0xFF22C55E), RoundedCornerShape(1.5.dp)),
                )
            }
        }
    }
}
