package com.example.chat.ui.chats.detail

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Chaty #36: per-participant group name colors derived from the theme, never
 *  hard-coded. The hue starts at the theme accent and hops by golden-ratio
 *  steps keyed on a stable, versioned sender-id hash, so every participant
 *  keeps one distinct color in light and dark themes; the result is then
 *  adjusted until it meets WCAG 4.5:1 contrast against the actual bubble
 *  surface, so names stay readable in both themes. All inputs are theme
 *  tokens (accent + surface) - no fixed palette is hard-coded here.
 *  Pure Kotlin: JVM-testable. */
object ParticipantColorPolicy {

    /** Golden-angle hue hop per distinct sender keeps adjacent senders apart. */
    private const val GOLDEN_ANGLE = 137.508f

    /** WCAG minimum for normal-size text. */
    internal const val MIN_CONTRAST = 4.5f

    /** Versioned identity scheme: bumping this intentionally recolors
     *  everyone, so a future palette migration never silently shifts colors. */
    private const val IDENTITY_VERSION = "sender:v1:"

    fun colorFor(senderId: String, accentColor: Long, surfaceColor: Long): Long {
        val accentHsv = FloatArray(3)
        argbToHsv(accentColor, accentHsv)
        val hash = stableHash(senderId)
        val hue = (accentHsv[0] + hash * GOLDEN_ANGLE) % 360f
        val dark = relativeLuminance(surfaceColor) < 0.35f
        val saturation = accentHsv[1].coerceIn(0.45f, 0.72f)
        val startValue = if (dark) {
            accentHsv[2].coerceIn(0.78f, 0.95f)
        } else {
            accentHsv[2].coerceIn(0.52f, 0.72f)
        }
        return ensureContrast(hue, saturation, startValue, dark, surfaceColor)
    }

    /** Deterministically walks brightness away from the surface until the
     *  label clears 4.5:1, desaturating as a last resort, then falls back to
     *  the opposite extreme if it contrasts better. For any surface, white or
     *  black always clears 4.5:1, so a passing color always exists. */
    private fun ensureContrast(
        hue: Float,
        saturation: Float,
        startValue: Float,
        dark: Boolean,
        surfaceColor: Long,
    ): Long {
        var s = saturation
        var v = startValue
        var color = hsvToArgb(hue, s, v)
        var steps = 0
        while (contrastRatio(color, surfaceColor) < MIN_CONTRAST && steps < 20) {
            v = if (dark) (v + 0.05f).coerceAtMost(1f) else (v - 0.05f).coerceAtLeast(0f)
            color = hsvToArgb(hue, s, v)
            steps++
        }
        steps = 0
        while (contrastRatio(color, surfaceColor) < MIN_CONTRAST && steps < 9) {
            s = (s - 0.05f).coerceAtLeast(0f)
            color = hsvToArgb(hue, s, v)
            steps++
        }
        if (contrastRatio(color, surfaceColor) < MIN_CONTRAST) {
            val opposite = hsvToArgb(hue, s, if (dark) 0f else 1f)
            if (contrastRatio(opposite, surfaceColor) > contrastRatio(color, surfaceColor)) {
                color = opposite
            }
        }
        return color
    }

    /** WCAG contrast ratio between two ARGB colors, 1..21. */
    internal fun contrastRatio(a: Long, b: Long): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** Order-independent, stable across processes and runtimes. SHA-256 of the
     *  versioned identity gives a well-distributed 32-bit hash; the identity
     *  hashes the immutable sender id, never the display name. */
    internal fun stableHash(id: String): Int {
        val identity = IDENTITY_VERSION + id.trim()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
        val hash = ByteBuffer.wrap(digest, 0, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .int.toLong() and 0xFFFFFFFFL
        return (hash % 997L).toInt()
    }

    internal fun argbToHsv(argb: Long, out: FloatArray) {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        var h = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        if (h < 0f) h += 360f
        out[0] = h
        out[1] = if (max == 0f) 0f else delta / max
        out[2] = max
    }

    internal fun hsvToArgb(h: Float, s: Float, v: Float): Long {
        val c = v * s
        val x = c * (1f - kotlin.math.abs(((h / 60f) % 2f) - 1f))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt() % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val ai = 0xFFL
        val ri = ((r + m) * 255f).toInt().coerceIn(0, 255).toLong()
        val gi = ((g + m) * 255f).toInt().coerceIn(0, 255).toLong()
        val bi = ((b + m) * 255f).toInt().coerceIn(0, 255).toLong()
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    internal fun relativeLuminance(argb: Long): Float {
        fun channel(v: Long): Float {
            val c = v / 255f
            return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * channel((argb shr 16) and 0xFF) +
            0.7152f * channel((argb shr 8) and 0xFF) +
            0.0722f * channel(argb and 0xFF)
    }
}
