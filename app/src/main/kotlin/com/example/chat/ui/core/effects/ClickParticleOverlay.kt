package com.example.chat.ui.core.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyMotion
import kotlin.random.Random

/**
 * Port of ClickParticleOverlay (lib/ui/core/widgets/click_particle_overlay.dart): wraps the whole app, observes pointer
 * downs WITHOUT consuming them (Dart Listener translucent), spawns 6 emoji particles per tap when
 * preferences.effects.enableClickParticles, and is suppressed under platform reduced motion. Particles age by a fixed
 * 0.016 per frame like the Dart ticker (not wall-clock), and the frame loop only runs while particles exist.
 * Painting is non-interactive. Uncompiled/unverified in pixels: the emoji font and exact baseline offset come from the
 * platform text stack.
 */
@Composable
fun ClickParticleOverlay(
    preferences: ChatyPreferencesController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val particles: SnapshotStateList<ClickParticle> = remember { mutableStateListOf() }
    var frame by remember { mutableIntStateOf(0) }
    val random = remember { Random.Default }
    val prefs by rememberUpdatedState(preferences)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val hasParticles = particles.isNotEmpty()

    LaunchedEffect(hasParticles) {
        if (!hasParticles) return@LaunchedEffect
        while (particles.isNotEmpty()) {
            androidx.compose.runtime.withFrameNanos { }
            ClickParticleSim.tick(particles)
            frame += 1
        }
    }

    Box(
        modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.changedToDown() } ?: continue
                    val fx = prefs.effects
                    if (!fx.enableClickParticles) continue
                    if (ChatyMotion.disableAnimations(context)) continue
                    particles.addAll(
                        ClickParticleSim.spawn(down.position.x, down.position.y, fx.clickParticleSymbol, fx.clickParticleSpeed, random),
                    )
                }
            }
        },
    ) {
        content()
        if (hasParticles) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val f = frame // redraw each frame
                for (p in particles) {
                    // Dart TextPainter ignores the text scaler: size is logical px, so cancel Compose's fontScale.
                    val sp = (ClickParticleSim.fontSizeDp(p) / density.fontScale).sp
                    val travel = p.age * 1.5f * density.density
                    drawText(
                        measurer, p.symbol,
                        topLeft = Offset(p.x + p.vx * travel, p.y + p.vy * travel),
                        style = TextStyle(fontSize = sp, color = Color.White.copy(alpha = p.opacity)),
                    )
                }
            }
        }
    }
}
