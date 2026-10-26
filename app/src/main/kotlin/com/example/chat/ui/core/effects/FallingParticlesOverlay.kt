package com.example.chat.ui.core.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.rememberChatyReduceMotion
import com.example.chat.ui.settings.rememberPreferencesVersion
import kotlin.random.Random
import kotlinx.coroutines.isActive

/**
 * Port of FallingParticlesOverlay (lib/ui/core/widgets/falling_particles_overlay.dart). One item is added per frame up
 * to 15; each frame y += speed (frame-based like the Dart controller listener, not wall-clock); items wrap at y > 1.05.
 * Honours scope ('Home only' / 'Chat only' / 'Both') against [currentScope] and the platform reduce-motion setting
 * (loop stops; items already present stay frozen, new ones are never spawned - same as the Dart code). main.dart
 * always passes currentScope = "Home". The frame loop does not run while the effect is disabled (Dart ticks and clears).
 */
@Composable
fun FallingParticlesOverlay(
    preferences: ChatyPreferencesController,
    currentScope: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    rememberPreferencesVersion(preferences)
    val fxNow = preferences.effects
    val reduceMotion = rememberChatyReduceMotion()
    val items = remember { mutableListOf<FallingItem>() }
    var frame by remember { mutableIntStateOf(0) }
    val random = remember { Random.Default }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    LaunchedEffect(reduceMotion, fxNow.enableFallingParticles) {
        if (!fxNow.enableFallingParticles) {
            if (items.isNotEmpty()) { items.clear(); frame += 1 }
            return@LaunchedEffect
        }
        if (reduceMotion) return@LaunchedEffect
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { }
            val fx = preferences.effects // live read each frame, like Dart
            if (FallingSim.step(items, fx.enableFallingParticles, fx.fallingParticleScope, fx.fallingParticleObject, currentScope, random)) {
                frame += 1
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        content()
        val active = FallingSim.active(fxNow.enableFallingParticles, fxNow.fallingParticleScope, currentScope)
        if (active) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                for (item in items) {
                    val sp = (FallingSim.fontSizeDp(item) / density.fontScale).sp
                    drawText(
                        measurer, item.symbol,
                        topLeft = Offset((item.x * size.width).toFloat(), (item.y * size.height).toFloat()),
                        style = TextStyle(fontSize = sp, color = Color.White.copy(alpha = item.opacity.toFloat())),
                    )
                }
            }
        }
    }
}
