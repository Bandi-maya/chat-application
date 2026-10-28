package com.example.chat.ui.effects

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * GB-reference list-arrival animations (reference keys key_chats_listanimation for the
 * home chat list and key_chat_animation for the conversation list, both default "0").
 * The reference mapping has fifteen values, 0 Standard, 1 Grow, 2 Cards, 3 Curl, 4 Wave,
 * 5 Flip, 6 Fly, 7 ReverseFly, 8 Helix, 9 Fan, 10 Tilt, 11 Zipper, 12 Fade, 13 Twirl,
 * 14 SlideIn. These are Compose approximations of the named effects, not exact
 * JazzyListView parity: the reference report (v8) confirms the key-to-library wiring
 * but not the rendered motion, so per-style timing/curves here are Chaty's own.
 */
object ListEntranceAnimations {
    /** Display names in the reference's 0..14 order; index 0 ("Standard") means no effect. */
    val styles = listOf(
        "Standard", "Grow", "Cards", "Curl", "Wave", "Flip", "Fly", "Reverse Fly",
        "Helix", "Fan", "Tilt", "Zipper", "Fade", "Twirl", "Slide In",
    )
}

/**
 * Animate this list item in when it arrives. Semantics, stated precisely:
 *  - Plays when an item first enters composition. LazyColumn disposes offscreen
 *    items, so scrolling an item away and back REPLAYS the entrance - this matches
 *    the reference library's per-arrival behavior.
 *  - Recomposition and list reorder do NOT replay: remember(key) state moves with
 *    the keyed item composition.
 *  - Changing the style preference never conceals content: an item that already
 *    arrived renders settled in the new style; a mid-arrival item continues from
 *    its current progress.
 *  - Honors the system animator scale (reduced motion): scale 0 renders settled.
 *  - "Standard" or unknown styles return the modifier unchanged.
 */
@Composable
fun Modifier.listEntrance(style: String, key: Any): Modifier {
    if (style == "Standard" || style !in ListEntranceAnimations.styles) return this
    val context = LocalContext.current
    val animatorScale = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    if (animatorScale == 0f) return this
    val progress = remember(key) { Animatable(0f) }
    val arrived = remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        progress.animateTo(1f, tween(durationMillis = 420))
        arrived.value = true
    }
    val p = if (arrived.value) 1f else progress.value
    return when (style) {
        "Fade" -> this.alpha(p)
        "Grow" -> this.graphicsLayer {
            val s = 0.5f + 0.5f * p
            scaleX = s; scaleY = s; alpha = p
        }
        "Cards" -> this.graphicsLayer {
            translationX = (1f - p) * 260f
            rotationZ = (1f - p) * 6f
            alpha = p
        }
        "Curl" -> this.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0f)
            rotationX = -90f * (1f - p)
            alpha = p
        }
        "Wave" -> this.graphicsLayer {
            translationY = (1f - p) * 120f
            rotationZ = (1f - p) * 4f
            alpha = p
        }
        "Flip" -> this.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0.5f)
            cameraDistance = 16f
            rotationY = -90f * (1f - p)
            alpha = p
        }
        "Fly" -> this.graphicsLayer {
            translationX = -(1f - p) * size.width
            alpha = p
        }
        "Reverse Fly" -> this.graphicsLayer {
            translationX = (1f - p) * size.width
            alpha = p
        }
        "Helix" -> this.graphicsLayer {
            rotationZ = 360f * (1f - p)
            val s = 0.3f + 0.7f * p
            scaleX = s; scaleY = s
            alpha = p
        }
        "Fan" -> this.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)
            rotationZ = -45f * (1f - p)
            alpha = p
        }
        "Tilt" -> this.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0.5f)
            rotationX = 30f * (1f - p)
            val s = 0.7f + 0.3f * p
            scaleX = s; scaleY = s
            alpha = p
        }
        "Zipper" -> this.graphicsLayer {
            translationY = (1f - p) * size.height
            alpha = p
        }
        "Twirl" -> this.graphicsLayer {
            rotationZ = 720f * (1f - p)
            val s = p
            scaleX = s; scaleY = s
            alpha = p
        }
        "Slide In" -> this.graphicsLayer {
            translationY = (1f - p) * size.height
            alpha = 0.3f + 0.7f * p
        }
        else -> this
    }
}
