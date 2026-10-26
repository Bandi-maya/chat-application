package com.example.chat.ui.kit

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Port of lib/ui/core/design_system/chaty_motion.dart (ChatyMotion). Curves are the Flutter control points
 * (Curves.easeInOutCubic / easeOutCubic / easeInCubic / easeOutBack / easeOutQuad verified against flutter curves.dart).
 * Callers must honour [disableAnimations] and show a static state when motion is disabled.
 */
object ChatyMotion {
    const val instantMs = 80
    const val fastMs = 140
    const val baseMs = 220
    const val emphasisMs = 320

    val standard: Easing = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1.0f)
    val enter: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1.0f)
    val exit: Easing = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)
    val springSoft: Easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)
    val springSnappy: Easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
    val springGentle: Easing = CubicBezierEasing(0.2f, 1.0f, 0.25f, 1.0f)
    val microPress: Easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)

    /** Dart MediaQuery.disableAnimations on Android == "Remove animations" / animator duration scale 0. */
    fun disableAnimations(context: Context): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) { false }

    /** Dart ChatyMotion.duration(context, preferred): zero when animations are disabled. */
    fun durationMs(context: Context, preferredMs: Int = baseMs): Int = if (disableAnimations(context)) 0 else preferredMs

    fun curve(context: Context, preferred: Easing = springSnappy): Easing = if (disableAnimations(context)) LinearEasing else preferred
}

/** Reads the platform reduce-motion state once per composition entry (Dart MediaQuery.disableAnimationsOf). */
@Composable
fun rememberChatyReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) { ChatyMotion.disableAnimations(ctx) }
}
