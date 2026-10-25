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
    if (effect.id == "none" || effect.colorMatrix == null) {
        content()
        return
    }
    val modifier = remember(effectId, intensity) { effectMatrixModifier(effect, intensity) }
    androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
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
