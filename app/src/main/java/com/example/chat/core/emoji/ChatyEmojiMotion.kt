package com.example.chat.core.emoji

import kotlin.math.sin

/**
 * Pure motion/geometry math for the ChatyMotionEmoji renderer, extracted 1:1
 * from lib/core/emoji/chaty_motion_emoji.dart so it is JVM-testable. All
 * canvas-space math operates on the Dart 64x64 logical canvas.
 */
object ChatyEmojiMotion {

    const val LOGICAL_CANVAS = 64.0

    /** Flutter Canvas.rotate / CustomPainter angles are radians; Compose
     *  rotate / drawArc / Path.addArc use degrees. */
    fun radToDeg(rad: Double): Double = rad * 180.0 / Math.PI

    // ---- Joy ----
    /** nod = sin(t * pi * 4) * 2.2 */
    fun joyNod(t: Double): Double = sin(t * Math.PI * 4) * 2.2

    /** Tear animation: phase = (t * 2) % 1; alpha = sin(phase * pi) clamped 0..1;
     *  offsets l = (-22 - phase*4, +2 + phase*12), r = (+22 + phase*4, same y). */
    fun tearPhase(t: Double): Double = (t * 2) % 1.0
    fun tearAlpha(phase: Double): Double = sin(phase * Math.PI).coerceIn(0.0, 1.0)
    fun tearLeftOffset(centerX: Double, centerY: Double, phase: Double): Pair<Double, Double> =
        (centerX - 22 - (phase * 4)) to (centerY + 2 + (phase * 12))
    fun tearRightOffset(centerX: Double, centerY: Double, phase: Double): Pair<Double, Double> =
        (centerX + 22 + (phase * 4)) to (centerY + 2 + (phase * 12))

    /** Pixel-face tear Y: center.dy - 2 + t * 12 */
    fun pixelTearY(centerY: Double, t: Double): Double = centerY - 2 + (t * 12)

    // ---- Heart ----
    /** beat = (sin(t * pi * 6) * 0.12).clamp(-0.15, 0.15); scale = 1 + beat */
    fun heartScale(t: Double): Double =
        1.0 + (sin(t * Math.PI * 6) * 0.12).coerceIn(-0.15, 0.15)

    // ---- Fire ----
    /** flicker = sin(t * pi * 8) * 1.8 */
    fun fireFlicker(t: Double): Double = sin(t * Math.PI * 8) * 1.8

    // ---- Thumbs up ----
    /** bounce = (sin(t * pi * 4) * 2.0).clamp(-2.0, 2.0) */
    fun thumbBounce(t: Double): Double = (sin(t * Math.PI * 4) * 2.0).coerceIn(-2.0, 2.0)

    // ---- Celebration ----
    /** burst = t * 14 */
    fun celebrationBurst(t: Double): Double = t * 14

    // ---- Surprise ----
    /** mouthScale = 1 + sin(t * pi * 4) * 0.15 */
    fun surpriseMouthScale(t: Double): Double = 1.0 + sin(t * Math.PI * 4) * 0.15

    // ---- Sad ----
    /** tearY = center.dy - 2 + t * 16 */
    fun sadTearY(centerY: Double, t: Double): Double = centerY - 2 + (t * 16)

    // ---- Waving ----
    /** waveAngle (radians) = sin(t * pi * 6) * 0.35 */
    fun waveAngleRad(t: Double): Double = sin(t * Math.PI * 6) * 0.35

    // ---- Gradient geometry (verified against Flutter gradient.dart createShader) ----

    /**
     * Flutter RadialGradient.createShader: center = alignment.withinRect(rect)
     * = (rect.left + halfW + ax * halfW, rect.top + halfH + ay * halfH).
     * [rect] is (left, top, width, height) in logical px.
     */
    fun radialCenter(rectLeft: Double, rectTop: Double, rectW: Double, rectH: Double, ax: Double, ay: Double): Pair<Double, Double> =
        (rectLeft + rectW / 2.0 + ax * rectW / 2.0) to (rectTop + rectH / 2.0 + ay * rectH / 2.0)

    /** Flutter RadialGradient.createShader: radiusPx = radius * rect.shortestSide. */
    fun radialRadius(radiusFraction: Double, rectShortestSide: Double): Double =
        radiusFraction * rectShortestSide

    /** Flutter LinearGradient topCenter point within rect. */
    fun linearTopCenter(rectLeft: Double, rectTop: Double, rectW: Double): Pair<Double, Double> =
        (rectLeft + rectW / 2.0) to rectTop

    /** Flutter LinearGradient bottomCenter point within rect. */
    fun linearBottomCenter(rectLeft: Double, rectTop: Double, rectW: Double, rectH: Double): Pair<Double, Double> =
        (rectLeft + rectW / 2.0) to (rectTop + rectH)

    /** Rect.fromCircle(center, radius) as (left, top, width, height). */
    fun rectFromCircle(centerX: Double, centerY: Double, radius: Double): DoubleArray =
        doubleArrayOf(centerX - radius, centerY - radius, radius * 2, radius * 2)

    // ---- Unicode handling (Dart widget API) ----

    /** Dart build(): unicode.replaceAll(FE0F, '').replaceAll(FE0E, '').trim() */
    fun cleanUnicode(unicode: String): String =
        unicode.replace("\uFE0F", "").replace("\uFE0E", "").trim()

    /** Dart ChatyMotionEmoji.isSupported. */
    fun isSupported(unicode: String): Boolean {
        val raw = unicode.trim()
        val clean = cleanUnicode(raw)
        return SUPPORTED.contains(clean) || SUPPORTED.contains(raw)
    }

    private val SUPPORTED = setOf(
        "😂", "🤣", "❤️", "❤", "😍", "💖", "🔥", "👍",
        "🎉", "🥳", "🤔", "😮", "😲", "😢", "😭", "👋",
    )
}
