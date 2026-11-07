package com.example.chat.data.network

/**
 * Pure reconnect/retry delay policy. The first retry (attempt 0) is exactly [baseMs]
 * (4s by default = the existing fixed reconnect delay), so first-failure behaviour is
 * unchanged. Later attempts back off exponentially up to [maxMs] with +/- [jitterFraction]
 * jitter, never below [baseMs] or above [maxMs]. [random] returns 0.0..1.0 (injectable).
 */
class BackoffPolicy(
    private val baseMs: Long = 4_000L,
    private val maxMs: Long = 60_000L,
    private val jitterFraction: Double = 0.2,
    private val random: () -> Double = { Math.random() },
    private val fullJitter: Boolean = false,
) {
    init {
        require(baseMs > 0 && maxMs >= baseMs && jitterFraction in 0.0..0.9)
    }

    /** [attempt] is 0 for the first retry after a failure.
     *
     *  Full-jitter mode (AWS "Exponential Backoff And Jitter", the reconnect-
     *  storm-safe variant): delay = random(0, min(cap, base * 2^n)). Every
     *  retry is uniformly spread, so a server restart never sees a thundering
     *  herd of phase-locked clients. The classic mode keeps +/- jitterFraction
     *  around the raw backoff for callers that need a stable first retry. */
    fun delayMs(attempt: Int): Long {
        if (!fullJitter && attempt <= 0) return baseMs
        val ceiling = baseMs shl attempt.coerceAtMost(20)
        if (fullJitter) {
            // Multiply the UNCAPPED backoff and clamp afterwards: random ~1
            // must be able to reach the cap itself, not cap * random.
            return (random() * ceiling).toLong().coerceIn(0L, maxMs)
        }
        val raw = minOf(maxMs, ceiling)
        val spread = (raw * jitterFraction).toLong()
        val jitter = ((random() * 2.0 - 1.0) * spread).toLong()
        return (raw + jitter).coerceIn(baseMs, maxMs)
    }
}
