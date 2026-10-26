package com.example.chat.ui.core.theme

import kotlin.math.abs

/** Flutter curve (pure data + evaluator, JVM-testable). Cubic = Flutter Cubic(a,b,c,d); ThreePoint = Flutter ThreePointCubic. */
sealed class FlutterCurve {
    abstract fun transform(t: Double): Double

    data class Cubic(val a: Double, val b: Double, val c: Double, val d: Double) : FlutterCurve() {
        private fun evalX(t: Double, p1: Double, p2: Double) = 3 * p1 * (1 - t) * (1 - t) * t + 3 * p2 * (1 - t) * t * t + t * t * t
        override fun transform(t: Double): Double {
            // Same bisection as Flutter's Cubic.transformInternal.
            var start = 0.0
            var end = 1.0
            while (true) {
                val mid = (start + end) / 2
                val estimate = evalX(mid, a, c)
                if (abs(t - estimate) < 0.001) return evalX(mid, b, d)
                if (estimate < t) start = mid else end = mid
            }
        }
    }

    data class ThreePoint(
        val a1x: Double, val a1y: Double, val b1x: Double, val b1y: Double,
        val mx: Double, val my: Double,
        val a2x: Double, val a2y: Double, val b2x: Double, val b2y: Double,
    ) : FlutterCurve() {
        override fun transform(t: Double): Double {
            val first = t < mx
            val scaleX = if (first) mx else 1.0 - mx
            val scaleY = if (first) my else 1.0 - my
            val scaledT = (t - (if (first) 0.0 else mx)) / scaleX
            return if (first) {
                Cubic(a1x / scaleX, a1y / scaleY, b1x / scaleX, b1y / scaleY).transform(scaledT) * scaleY
            } else {
                Cubic((a2x - mx) / scaleX, (a2y - my) / scaleY, (b2x - mx) / scaleX, (b2y - my) / scaleY).transform(scaledT) * scaleY + my
            }
        }
    }

    companion object {
        // Values verified against flutter/lib/src/animation/curves.dart (stable).
        val easeOutCubic = Cubic(0.215, 0.61, 0.355, 1.0)
        val easeInCubic = Cubic(0.55, 0.055, 0.675, 0.19)
        val easeOut = Cubic(0.0, 0.0, 0.58, 1.0)
        val easeIn = Cubic(0.42, 0.0, 1.0, 1.0)
        val easeInOut = Cubic(0.42, 0.0, 0.58, 1.0)
        val easeOutQuad = Cubic(0.25, 0.46, 0.45, 0.94)
        val fastOutSlowIn = Cubic(0.4, 0.0, 0.2, 1.0)
        val linearToEaseOut = Cubic(0.35, 0.91, 0.33, 0.97)
        val easeOutBack = Cubic(0.175, 0.885, 0.32, 1.275)
        val slowMiddle = Cubic(0.15, 0.85, 0.85, 0.15)
        val easeInOutCubicEmphasized = ThreePoint(0.05, 0.0, 0.133333, 0.06, 0.166666, 0.4, 0.208333, 0.82, 0.25, 1.0)
    }
}

/** Dart _TransitionSpec: shift is the fractional "absent" offset, scale the "absent" scale. */
data class ChatyTransitionSpec(
    val shiftX: Double = 0.0,
    val shiftY: Double = 0.0,
    val scale: Double = 1.0,
    val fade: Boolean = true,
    val curve: FlutterCurve = FlutterCurve.easeOutCubic,
) {
    /** Dart: pure no-op styles must not wrap the child at all. */
    val isNoOp: Boolean get() = shiftX == 0.0 && shiftY == 0.0 && scale == 1.0 && !fade
}

/** Exact port of ChatyPageTransitionsBuilder._specFor (lib/ui/core/theme/chaty_page_transitions.dart). */
object ChatyTransitionSpecs {
    private val none = ChatyTransitionSpec(0.0, 0.0, 1.0, false)

    fun specFor(style: String, isEntry: Boolean): ChatyTransitionSpec = when (style) {
        "Fade" -> ChatyTransitionSpec(0.0, 0.0, 1.0, true)
        "Fade + Slide" -> ChatyTransitionSpec(0.0, if (isEntry) 0.05 else -0.04, 1.0, true)
        "Slide Right" -> ChatyTransitionSpec(1.0, 0.0, 1.0, false, FlutterCurve.easeOutCubic)
        "Slide Left" -> ChatyTransitionSpec(-1.0, 0.0, 1.0, false, FlutterCurve.easeOutCubic)
        "Slide Up" -> ChatyTransitionSpec(0.0, 1.0, 1.0, false, FlutterCurve.easeOutCubic)
        "Slide Down" -> ChatyTransitionSpec(0.0, -1.0, 1.0, false, FlutterCurve.easeOutCubic)
        "Scale In", "Scale Out" -> ChatyTransitionSpec(0.0, 0.0, 0.88, true)
        "Soft Zoom" -> ChatyTransitionSpec(0.0, 0.0, 1.08, true, FlutterCurve.easeOut)
        "Shared Axis X" -> ChatyTransitionSpec(if (isEntry) 0.16 else -0.16, 0.0, 1.0, true)
        "Shared Axis Y" -> ChatyTransitionSpec(0.0, if (isEntry) 0.16 else -0.16, 1.0, true)
        "Fade Through" -> ChatyTransitionSpec(0.0, 0.0, 0.985, true, FlutterCurve.fastOutSlowIn)
        "Cupertino Push", "Cupertino Pop" -> ChatyTransitionSpec(1.0, 0.0, 1.0, false, FlutterCurve.linearToEaseOut)
        "Spring Push", "Spring Pop" -> ChatyTransitionSpec(0.3, 0.0, 1.0, false, FlutterCurve.easeOutBack)
        "Soft Reveal", "Soft Conceal" -> ChatyTransitionSpec(0.0, 0.0, 1.05, true)
        "Card Lift", "Card Drop" -> ChatyTransitionSpec(
            0.0, 0.06, 0.96, true, if (isEntry) FlutterCurve.easeOutCubic else FlutterCurve.easeInCubic,
        )
        "Blur-free Reveal" -> ChatyTransitionSpec(0.0, 0.0, 1.10, false, FlutterCurve.easeOutQuad)
        "Quick Snap" -> ChatyTransitionSpec(
            0.0, 0.03, 1.02, true, if (isEntry) FlutterCurve.easeOut else FlutterCurve.easeIn,
        )
        "Gentle Drift" -> ChatyTransitionSpec(if (isEntry) 0.05 else -0.05, 0.02, 1.0, true, FlutterCurve.slowMiddle)
        "Focus In", "Focus Out" -> ChatyTransitionSpec(0.0, 0.0, 0.92, true, FlutterCurve.easeInOutCubicEmphasized)
        "Cross Fade" -> ChatyTransitionSpec(0.0, 0.0, 1.0, true, FlutterCurve.easeInOut)
        else -> none // 'None' and unknown names
    }

    /** Dart _TransitionMotion maths for progress p (0..1 route/secondary animation value after the spec curve). */
    fun presence(curvedValue: Double, asEntry: Boolean): Double = (if (asEntry) curvedValue else 1 - curvedValue).coerceIn(0.0, 1.0)
}
