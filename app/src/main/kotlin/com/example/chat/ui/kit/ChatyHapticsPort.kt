package com.example.chat.ui.kit

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Exact Android mapping of the Dart ChatyHaptics grammar
 *  (lib/ui/core/design_system/chaty_haptics.dart).
 *
 *  Flutter's engine maps HapticFeedback types to view constants
 *  (shell/platform/android PlatformPlugin.java, verified against the live
 *  source): selectionClick -> CLOCK_TICK, lightImpact -> VIRTUAL_KEY,
 *  mediumImpact -> KEYBOARD_TAP, heavyImpact -> CONTEXT_CLICK (API 23+),
 *  standard -> LONG_PRESS. Compose's own HapticFeedbackType lacks these on
 *  the project's Compose BOM, so this goes through View.performHapticFeedback
 *  exactly like the Flutter engine does. */
object ChatyHapticsPort {

    /** ChatyHaptics.selection() = selectionClick. */
    fun selection(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** ChatyHaptics.light() = lightImpact. */
    fun light(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** ChatyHaptics.threshold() = mediumImpact. */
    fun threshold(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** ChatyHaptics.success() = lightImpact. */
    fun success(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** ChatyHaptics.warning() = mediumImpact. */
    fun warning(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** ChatyHaptics.error() = heavyImpact (CONTEXT_CLICK, API 23+ like Flutter). */
    fun error(view: View) {
        if (Build.VERSION.SDK_INT >= 23) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }
}
