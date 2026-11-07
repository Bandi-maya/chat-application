package com.example.chat.data.polls

/** Matrix 16 polls + live location - pure logic, JVM-testable.
 *  Polls and live-location updates travel as MLS-encrypted message payloads;
 *  the server only relays them, so no new backend surface is needed. */

class Poll(val id: String, val question: String, val options: List<String>, val isQuiz: Boolean = false) {
    private val votes = mutableMapOf<String, Int>()   // voterId -> optionIndex
    var closed = false
        private set

    fun vote(voterId: String, optionIndex: Int): Boolean {
        if (closed || optionIndex !in options.indices) return false
        votes[voterId] = optionIndex                  // revote replaces
        return true
    }

    fun tally(): List<Int> = options.indices.map { i -> votes.values.count { it == i } }

    fun close() { closed = true }

    fun totalVoters(): Int = votes.size
}

/** Live location durations (15m / 1h / 8h) with manual stop. */
object LiveLocationPolicy {
    val ALLOWED_DURATIONS_MINUTES = listOf(15, 60, 480)

    fun endMillis(startMillis: Long, durationMinutes: Int): Long {
        require(durationMinutes in ALLOWED_DURATIONS_MINUTES) { "duration must be 15m, 1h or 8h" }
        return startMillis + durationMinutes * 60_000L
    }

    fun isLive(startMillis: Long, durationMinutes: Int, nowMillis: Long, manuallyStopped: Boolean): Boolean =
        !manuallyStopped && nowMillis < endMillis(startMillis, durationMinutes)
}

/** Playback speeds for voice notes (matrix 15 row 4). */
object PlaybackSpeedPolicy {
    val SPEEDS = listOf(1.0f, 1.5f, 2.0f)

    fun nextSpeed(current: Float): Float {
        val i = SPEEDS.indexOf(current)
        return SPEEDS[(if (i < 0) 0 else i + 1) % SPEEDS.size]
    }
}
