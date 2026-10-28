package com.example.chat.ui.effects

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
 * Animate this item in when it enters composition (which for LazyColumn items is when
 * it scrolls into view, matching the reference's "arrival" behavior). Standard or
 * unknown styles return the modifier unchanged, so existing behavior is preserved.
 */
fun Modifier.listEntrance(style: String, key: Any): Modifier = composed(key1 = key, key2 = style) {
    if (style == "Standard" || style !in ListEntranceAnimations.styles) return@composed this@composed
    // Honor the system animator scale: 0 (animators off / reduced motion) renders
    // items settled, matching platform accessibility expectations.
    val context = LocalContext.current
    val animatorScale = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    if (animatorScale == 0f) return@composed this@composed
    // remember(key): state moves with the LazyColumn item key, so recomposition and
    // reorder keep progress (no replay); replay happens only when an item newly
    // enters composition, matching the reference's "arrival" behavior.
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(durationMillis = 420)) }
    val p = progress.value
    when (style) {
        "Fade" -> this@composed.alpha(p)
        "Grow" -> this@composed.graphicsLayer {
            val s = 0.5f + 0.5f * p
            scaleX = s; scaleY = s; alpha = p
        }
        "Cards" -> this@composed.graphicsLayer {
            translationX = (1f - p) * 260f
            rotationZ = (1f - p) * 6f
            alpha = p
        }
        "Curl" -> this@composed.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0f)
            rotationX = -90f * (1f - p)
            alpha = p
        }
        "Wave" -> this@composed.graphicsLayer {
            translationY = (1f - p) * 120f
            rotationZ = (1f - p) * 4f
            alpha = p
        }
        "Flip" -> this@composed.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0.5f)
            cameraDistance = 16f
            rotationY = -90f * (1f - p)
            alpha = p
        }
        "Fly" -> this@composed.graphicsLayer {
            translationX = -(1f - p) * size.width
            alpha = p
        }
        "Reverse Fly" -> this@composed.graphicsLayer {
            translationX = (1f - p) * size.width
            alpha = p
        }
        "Helix" -> this@composed.graphicsLayer {
            rotationZ = 360f * (1f - p)
            val s = 0.3f + 0.7f * p
            scaleX = s; scaleY = s
            alpha = p
        }
        "Fan" -> this@composed.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)
            rotationZ = -45f * (1f - p)
            alpha = p
        }
        "Tilt" -> this@composed.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0.5f)
            rotationX = 30f * (1f - p)
            val s = 0.7f + 0.3f * p
            scaleX = s; scaleY = s
            alpha = p
        }
        "Zipper" -> this@composed.graphicsLayer {
            translationY = (1f - p) * size.height
            alpha = p
        }
        "Twirl" -> this@composed.graphicsLayer {
            rotationZ = 720f * (1f - p)
            val s = p
            scaleX = s; scaleY = s
            alpha = p
        }
        "Slide In" -> this@composed.graphicsLayer {
            translationY = (1f - p) * size.height
            alpha = 0.3f + 0.7f * p
        }
        else -> this@composed
    }
}
