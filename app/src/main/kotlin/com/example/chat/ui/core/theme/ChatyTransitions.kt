package com.example.chat.ui.core.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Port of ChatyTransitions / ChatyPageTransitionsBuilder (lib/ui/core/theme/chaty_page_transitions.dart).
 *
 * Dart: the entering route animates its primary animation with the *entry* spec; the covered route animates its
 * secondaryAnimation with the *exit* spec. In Compose navigation terms:
 *  - [enterTransition]    new screen opening        -> entry spec (isEntry = true)
 *  - [exitTransition]     screen being covered      -> exit spec  (isEntry = false)
 *  - [popEnterTransition] covered screen returning  -> reverse of the exit motion
 *  - [popExitTransition]  screen being popped       -> reverse of the entry motion
 * Motion maths (shift*absence, 1+(scale-1)*absence, opacity=presence) is expressed through slideIn/scaleIn/fadeIn.
 * Dart route duration is MaterialPageRoute's 300 ms (assumed; verify against the Flutter version in pubspec).
 */
object ChatyTransitions {
    const val durationMs = 300

    private fun easing(c: FlutterCurve): Easing = Easing { f -> c.transform(f.toDouble()).toFloat() }

    private fun enterOf(spec: ChatyTransitionSpec): EnterTransition {
        if (spec.isNoOp) return EnterTransition.None // Dart: no wrapper at all for 'None'
        val e = easing(spec.curve)
        var t: EnterTransition = EnterTransition.None
        if (spec.shiftX != 0.0 || spec.shiftY != 0.0) {
            t += slideIn(tween(durationMs, easing = e)) { size ->
                IntOffset((size.width * spec.shiftX).roundToInt(), (size.height * spec.shiftY).roundToInt())
            }
        }
        if (spec.scale != 1.0) t += scaleIn(tween(durationMs, easing = e), initialScale = spec.scale.toFloat())
        if (spec.fade) t += fadeIn(tween(durationMs, easing = e), initialAlpha = 0f)
        return t
    }

    private fun exitOf(spec: ChatyTransitionSpec): ExitTransition {
        if (spec.isNoOp) return ExitTransition.None
        val e = easing(spec.curve)
        var t: ExitTransition = ExitTransition.None
        if (spec.shiftX != 0.0 || spec.shiftY != 0.0) {
            t += slideOut(tween(durationMs, easing = e)) { size ->
                IntOffset((size.width * spec.shiftX).roundToInt(), (size.height * spec.shiftY).roundToInt())
            }
        }
        if (spec.scale != 1.0) t += scaleOut(tween(durationMs, easing = e), targetScale = spec.scale.toFloat())
        if (spec.fade) t += fadeOut(tween(durationMs, easing = e), targetAlpha = 0f)
        return t
    }

    fun enterTransition(entry: String): EnterTransition = enterOf(ChatyTransitionSpecs.specFor(entry, isEntry = true))
    fun exitTransition(exit: String): ExitTransition = exitOf(ChatyTransitionSpecs.specFor(exit, isEntry = false))
    fun popEnterTransition(exit: String): EnterTransition = enterOf(ChatyTransitionSpecs.specFor(exit, isEntry = false))
    fun popExitTransition(entry: String): ExitTransition = exitOf(ChatyTransitionSpecs.specFor(entry, isEntry = true))

    /** For AnimatedContent-based route switchers: forward = push, !forward = pop. */
    fun contentTransform(entry: String, exit: String, forward: Boolean): ContentTransform =
        if (forward) enterTransition(entry) togetherWith exitTransition(exit)
        else popEnterTransition(exit) togetherWith popExitTransition(entry)
}
