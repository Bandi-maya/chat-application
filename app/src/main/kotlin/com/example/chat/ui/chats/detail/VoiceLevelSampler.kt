package com.example.chat.ui.chats.detail

/** Exact port of the composer level-meter math in chat_detail_screen.dart
 *  (_ComposerState): 30 bars, 90ms polling, dBFS floor -60, silence stub 0.06,
 *  shift-left ring buffer. Bars are REAL microphone samples only - when no
 *  amplitude provider exists the Dart omits the meter, so owners pass null
 *  instead of faking values. */
object VoiceLevelMeter {
    const val BAR_COUNT = 30
    const val DB_FLOOR = -60.0
    const val MIN_STUB = 0.06
    const val POLL_INTERVAL_MS = 90L

    /** dBFS -> 0..1 with the small floor so silence still shows a stub. */
    fun normalize(db: Double): Double {
        val normalized = ((db - DB_FLOOR) / -DB_FLOOR).coerceIn(0.0, 1.0)
        return if (normalized < MIN_STUB) MIN_STUB else normalized
    }
}

/** Ring buffer of normalized levels; drive [pollOnce] every
 *  [VoiceLevelMeter.POLL_INTERVAL_MS] ms while recording (Dart Timer.periodic)
 *  and call [reset] when recording starts (Dart _startLevelPolling). */
class VoiceLevelSampler(
    private val amplitudeProvider: suspend () -> Double,
) {
    private val levels = MutableList(VoiceLevelMeter.BAR_COUNT) { 0.0 }

    fun levels(): List<Double> = levels.toList()

    fun reset() {
        levels.fill(0.0)
    }

    suspend fun pollOnce() {
        val db = amplitudeProvider()
        levels.removeAt(0)
        levels.add(VoiceLevelMeter.normalize(db))
    }
}
