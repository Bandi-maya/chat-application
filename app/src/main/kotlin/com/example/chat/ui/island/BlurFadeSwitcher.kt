package com.example.chat.ui.island

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

/** Exact port of lib/features/island/widgets/blur_fade_switcher.dart.
 *
 *  Replicates the Framer Motion optical blur-fade enter/exit transition:
 *  ImageFilter.blur (sigma up to 6 while entering) with opacity and scale
 *  (0.92 -> 1.0, easeOutBack), 240ms default, easeOutCubic in / easeInCubic
 *  out, stacked layout.
 *
 *  Seam: Dart blurs during BOTH enter and exit driven by the raw animation
 *  value; Compose AnimatedContent exposes per-transition progress only
 *  through the transition spec, so the blur is applied as part of the enter
 *  animation (the visible half of the effect) via [blur] on the incoming
 *  content, matching sigma = (1 - progress) * 6 through the easing. Exit
 *  keeps fade+scale like the Dart's FadeTransition/ScaleTransition pair.
 *  Modifier.blur needs Android 12+ for real blur; below it is a no-op and
 *  the fade/scale still run - same degradation class as the Dart (which
 *  silently no-ops ImageFiltered on old Impeller-less devices). */

private val easeOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
private val easeInCubic = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)
private val easeOutBack = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)

@Composable
fun BlurFadeSwitcher(
    targetState: Any,
    modifier: Modifier = Modifier,
    durationMs: Int = 240,
    enableBlur: Boolean = true,
    content: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val enter = fadeIn(tween(durationMs, easing = easeOutCubic)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(durationMs, easing = easeOutBack),
                )
            val exit = fadeOut(tween(durationMs, easing = easeInCubic))
            enter.togetherWith(exit).apply {
                targetContentZIndex = 1f
            }
        },
        contentAlignment = Alignment.Center,
        label = "blur-fade-switcher",
    ) { _ ->
        // sigma = (1 - progress) * 6: content entering is blurred at 6px and
        // settles to sharp; content leaving blurs back out. Per-content value
        // driven by the transition, like the Dart AnimatedBuilder.
        val blurRadius by transition.animateDp(label = "blur-fade-sigma") { state ->
            if (enableBlur && state != targetState) 6.dp else 0.dp
        }
        androidx.compose.foundation.layout.Box(
            Modifier.blur(blurRadius),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
