package com.example.chat.ui.camera

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/** Compose ports of the two effect-render seams in the Dart tree.
 *
 *  1. EffectEngine.renderEffect: preview wrap in ColorFiltered (interpolated
 *     matrix). Native: RenderEffect.createColorFilterEffect requires API 31+;
 *     below that the preview renders UNFILTERED while the captured file is
 *     still baked with the effect (bakeEffectIntoFile) - documented deviation,
 *     matches "preview best effort, capture exact" intent.
 *  2. EffectRegistry.applyStored: static ColorFiltered wrap for a stored
 *     effect id + intensity (used by the capture confirm pane and by message
 *     bubbles rendering effect-tagged media). */

/** Compose equivalent of EffectRegistry.applyStored. */
@Composable
fun ApplyStoredEffect(effectId: String?, intensity: Double, content: @Composable () -> Unit) {
    val effect = EffectRegistry.byId(effectId)
    if (effect.id == "none" || (effect.colorMatrix == null && effect.shaderKey == null)) {
        content()
        return
    }
    val modifier = remember(effectId, intensity) { effectRenderModifier(effect, intensity) }
    androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
}

/** Original AGSL pixelate shader: snaps sample positions to a grid whose block
 *  count follows intensity (higher intensity = chunkier blocks). */
private const val PIXELATE_AGSL = """
uniform shader content;
uniform float2 size;
uniform float blocks;
half4 main(float2 fragCoord) {
    float2 block = size / blocks;
    float2 snapped = (floor(fragCoord / block) + 0.5) * block;
    return content.eval(snapped);
}
"""

/** Block count across the long edge for an intensity (72 fine .. 16 chunky). */
fun pixelateBlocks(intensity: Double): Float =
    (72.0 - 56.0 * intensity.coerceIn(0.0, 1.0)).toFloat()

/** General render dispatch: pixelate shader effects, else the color matrix. */
fun effectRenderModifier(effect: ChatyCameraEffect, intensity: Double): Modifier {
    if (effect.shaderKey == "pixelate") {
        if (Build.VERSION.SDK_INT >= 33) {
            val blocks = pixelateBlocks(intensity)
            return Modifier.graphicsLayer {
                val shader = android.graphics.RuntimeShader(PIXELATE_AGSL)
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("blocks", blocks)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            }
        }
        // Below API 33 the preview renders unfiltered while the capture bake
        // still applies the pixelate (documented deviation, same contract as
        // the matrix path below API 31).
        return Modifier
    }
    return effectMatrixModifier(effect, intensity)
}

/** Modifier applying the interpolated color matrix where the platform can. */
fun effectMatrixModifier(effect: ChatyCameraEffect, intensity: Double): Modifier {
    val matrix = ImageFxBaker.lerpedMatrix(effect, intensity)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = RenderEffect
                .createColorFilterEffect(
                    android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(matrix)),
                )
                .asComposeRenderEffect()
        }
    } else Modifier
}

/** True when live preview filtering is supported (API 31+). Below this the
 *  capture pipeline still bakes the effect into the saved file. */
val livePreviewFilterSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Preview accent color for an effect (used by pickers/glyphs). */
val ChatyCameraEffect.accentColor: Color get() = previewColor
