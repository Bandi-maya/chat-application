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
) {
    init {
        require(baseMs > 0 && maxMs >= baseMs && jitterFraction in 0.0..0.9)
    }

    /** [attempt] is 0 for the first retry after a failure. */
    fun delayMs(attempt: Int): Long {
        if (attempt <= 0) return baseMs
        val raw = minOf(maxMs, baseMs shl attempt.coerceAtMost(20))
        val spread = (raw * jitterFraction).toLong()
        val jitter = ((random() * 2.0 - 1.0) * spread).toLong()
        return (raw + jitter).coerceIn(baseMs, maxMs)
    }
}
