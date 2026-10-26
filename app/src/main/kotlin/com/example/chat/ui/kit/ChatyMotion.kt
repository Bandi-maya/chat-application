package com.example.chat.ui.kit

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** Testable subscription lifecycle; read after subscribing so setup cannot miss a change. */
internal fun observeChatyMotionPolicy(
    read: () -> Boolean,
    subscribe: (onChange: () -> Unit) -> (() -> Unit),
    onValue: (Boolean) -> Unit,
): () -> Unit {
    var active = true
    val refresh = { if (active) onValue(read()) }
    val unregister = subscribe(refresh)
    refresh()
    return {
        if (active) {
            active = false
            unregister()
        }
    }
}

/** Live platform reduce-motion policy; observer is removed when this composition leaves. */
@Composable
fun rememberChatyReduceMotion(): Boolean {
    val ctx = LocalContext.current.applicationContext
    var reduced by remember(ctx) { mutableStateOf(ChatyMotion.disableAnimations(ctx)) }
    DisposableEffect(ctx) {
        val stop = observeChatyMotionPolicy(
            read = { ChatyMotion.disableAnimations(ctx) },
            subscribe = { refresh ->
                val resolver = ctx.contentResolver
                val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) { refresh() }
                }
                resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
                val unsubscribe: () -> Unit = { resolver.unregisterContentObserver(observer) }
                unsubscribe
            },
            onValue = { reduced = it },
        )
        onDispose { stop() }
    }
    return reduced
}
