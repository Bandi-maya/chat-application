package com.example.chat.ui.icons

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

/** How a [ChatyIcon] motion plays: Idle = static (default), Once = one shot on a real event, Loop = repeat while the real state lasts. */
enum class ChatyIconMotionState { Idle, Play, Loop }

/**
 * Pure, state-driven one-shot motion for icons. Nothing here runs by itself: a consumer starts a motion only when the
 * REAL state it describes changes (send accepted, lock state flips, screen opened, ...) and samples [pose] with
 * progress t in 0..1 over [durationMs]. With reduced motion every pose is the identity, so the static icon is shown.
 * Every pose is the identity at t = 1, so the static icon is the rest state. Units: dx/dy in icon grid units (24 = icon size), rotation in radians.
 */
data class IconPose(
    val dx: Double = 0.0, val dy: Double = 0.0, val scale: Double = 1.0, val rotation: Double = 0.0, val alpha: Double = 1.0,
    /** Lock only: shackle lift (grid units, up is negative) and swing around its right leg (radians). */
    val shackleLift: Double = 0.0, val shackleSwing: Double = 0.0,
) { companion object { val Identity = IconPose() } }

object ChatyIconMotion {
    private fun clamp(t: Double) = t.coerceIn(0.0, 1.0)
    private fun easeOut(t: Double) = 1 - Math.pow(1 - clamp(t), 3.0)
    private fun easeIn(t: Double) = Math.pow(clamp(t), 3.0)
    private fun easeInOut(t: Double) = clamp(t).let { 3 * it * it - 2 * it * it * it }

    private fun pulse(p: Double): Double = when {
        p <= 0.0 || p >= 1.0 -> 0.0
        p < 0.35 -> easeOut(p / 0.35)
        else -> 1.0 - easeInOut((p - 0.35) / 0.65)
    }

    private fun rock(p: Double): Double = when {
        p <= 0.0 || p >= 1.0 -> 0.0
        p < 0.3 -> easeOut(p / 0.3)
        p < 0.65 -> 1.0 - 1.5 * easeInOut((p - 0.3) / 0.35)
        else -> -0.5 * (1.0 - easeInOut((p - 0.65) / 0.35))
    }

    /** Icons that have a motion. Everything else is static, there are no idle or universal bounces. */
    val animated: Set<ChatyIconId> = setOf(
        ChatyIconId.send, ChatyIconId.lock, ChatyIconId.unlock, ChatyIconId.settings, ChatyIconId.favourite,
        ChatyIconId.favouriteFilled, ChatyIconId.sync, ChatyIconId.callEnd,
        ChatyIconId.chats, ChatyIconId.updates, ChatyIconId.calls, ChatyIconId.profile, ChatyIconId.pin, ChatyIconId.unpin,
    )

    fun hasMotion(icon: ChatyIconId) = icon in animated

    fun durationMs(icon: ChatyIconId): Int = when (icon) {
        ChatyIconId.send -> 520
        ChatyIconId.lock, ChatyIconId.unlock -> 360
        ChatyIconId.settings -> 520
        ChatyIconId.favourite, ChatyIconId.favouriteFilled -> 300
        ChatyIconId.callEnd -> 320
        ChatyIconId.sync -> 900 // one turn; a consumer may repeat it ONLY while the real sync state lasts
        ChatyIconId.chats, ChatyIconId.pin -> 360
        ChatyIconId.updates -> 420
        ChatyIconId.calls, ChatyIconId.unpin -> 400
        ChatyIconId.profile -> 300
        else -> 0
    }

    /** Pose of the icon itself at progress [t]. Send departs forward and away, then the next one arrives from behind. */
    fun pose(icon: ChatyIconId, t: Double, reducedMotion: Boolean): IconPose {
        if (reducedMotion || !hasMotion(icon)) return IconPose.Identity
        val p = clamp(t)
        return (when (icon) {
            // New one-shots act on the whole glyph. Profile and updates are bounded design adaptations,
            // not isolated head/ring animation. Explicit endpoints avoid floating-point residue.
            ChatyIconId.chats -> IconPose(dy = -2.0 * pulse(p))
            ChatyIconId.updates -> IconPose(scale = 1.0 + 0.08 * pulse(p))
            ChatyIconId.calls -> IconPose(rotation = (PI / 22.5) * rock(p))
            ChatyIconId.profile -> IconPose(dy = -1.0 * pulse(p))
            ChatyIconId.pin -> IconPose(dy = 2.0 * pulse(p))
            ChatyIconId.unpin -> IconPose(dy = -2.0 * pulse(p), rotation = -(PI / 12.0) * pulse(p))
            ChatyIconId.send -> when {
                p < 0.1 -> IconPose(scale = 1 - 0.06 * easeOut(p / 0.1))
                p < 0.5 -> { val u = easeIn((p - 0.1) / 0.4); IconPose(dx = 22 * u, dy = -22 * u, scale = 0.94 - 0.55 * u, alpha = 1 - Math.pow(u, 1.5)) }
                p < 0.58 -> IconPose(alpha = 0.0)
                else -> { val u = easeOut((p - 0.58) / 0.42); IconPose(dx = -20 * (1 - u), dy = 20 * (1 - u), scale = 0.55 + 0.45 * u, alpha = minOf(1.0, u * 1.6)) }
            }
            ChatyIconId.settings -> IconPose(rotation = 2 * PI * easeInOut(p)) // full turn: rest pose equals the static icon in every theme
            ChatyIconId.lock -> { val o = 1 - easeInOut(p); IconPose(shackleLift = -2.5 * o, shackleSwing = 0.55 * o) }
            // The unlock art is already open, so the motion starts from the closed look and settles into the open art (identity at p = 1).
            ChatyIconId.unlock -> { val o = 1 - easeInOut(p); IconPose(shackleLift = 2.0 * o, shackleSwing = -0.45 * o) }
            ChatyIconId.favourite, ChatyIconId.favouriteFilled ->
                IconPose(scale = 1 + 0.18 * exp(-6 * p) * Math.sin(10 * p).coerceAtLeast(0.0))
            ChatyIconId.callEnd -> if (p >= 1.0) IconPose.Identity
                else IconPose(rotation = (PI * 0.75) * easeOut(p) * (if (p > 0.8) 1 - (p - 0.8) / 0.2 else 1.0))
            ChatyIconId.sync -> IconPose(rotation = 2 * PI * p)
            else -> IconPose.Identity
        }).normalized()
    }
}

/** Turns -0.0 into 0.0 so a rest pose compares equal to [IconPose.Identity]. */
private fun IconPose.normalized() = IconPose(dx + 0.0, dy + 0.0, scale + 0.0, rotation + 0.0, alpha + 0.0, shackleLift + 0.0, shackleSwing + 0.0)
