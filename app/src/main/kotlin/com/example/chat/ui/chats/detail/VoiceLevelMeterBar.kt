package com.example.chat.ui.chats.detail

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.chat.ui.kit.ChatyMotion

/** Exact port of ChatyVoiceLevelMeter (composer_components.dart:124): 26dp row of
 *  equally weighted bars; each bar is 3dp wide, height 4+20*level.clamp(0,1) dp,
 *  accent color at alpha 0.45+0.55*level.clamp(0,1), 2dp corner radius, animated
 *  with ChatyMotion.instant (80ms) + ChatyMotion.enter curve (both reduce to no
 *  animation when the device disables animations). Semantics: label "Live voice
 *  level", read-only, bars excluded. Levels come from [VoiceLevelSampler]; callers
 *  pass real microphone samples only (Dart omits the meter without a provider). */
@Composable
fun VoiceLevelMeterBar(
    levels: List<Double>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val durationMs = ChatyMotion.durationMs(context, ChatyMotion.instantMs)
    val curve = ChatyMotion.curve(context, ChatyMotion.enter)
    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Live voice level"
        },
    ) {
        Row(
            modifier = Modifier
                .height(26.dp)
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (rawLevel in levels) {
                val level = rawLevel.coerceIn(0.0, 1.0)
                val barHeight by animateDpAsState(
                    targetValue = (4 + 20 * level).dp,
                    animationSpec = tween(durationMillis = durationMs, easing = curve),
                    label = "voiceLevelHeight",
                )
                val barAlpha by animateFloatAsState(
                    targetValue = (0.45 + 0.55 * level).toFloat(),
                    animationSpec = tween(durationMillis = durationMs, easing = curve),
                    label = "voiceLevelAlpha",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(barHeight)
                            .background(accent.copy(alpha = barAlpha), RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}
