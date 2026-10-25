package com.example.chat.data.services

import com.example.chat.domain.models.ConnectionHealth
import com.example.chat.domain.models.ConnectionHealthState
import com.example.chat.domain.models.NetworkFailureType
import com.example.chat.domain.models.NetworkTransportType
import com.example.chat.domain.models.RealtimeSocketStatus
import java.time.Instant
import kotlin.time.Duration

/** Pure, deterministic classifier: exact port of
 *  connection_health_classifier.dart, including the hysteresis rules. */
object ConnectionHealthClassifier {
    const val excellentLatencyMaxMs = 300
    const val weakLatencyMaxMs = 1500
    const val minSuccessesToUpgrade = 2
    const val minFailuresToDowngrade = 2

    /** Classifies connection health given the latest sample and historical state. */
    fun evaluate(
        previousState: ConnectionHealthState,
        hasNetwork: Boolean,
        internetReachable: Boolean,
        backendReachable: Boolean,
        realtimeStatus: RealtimeSocketStatus,
        latency: Duration?,
        transport: NetworkTransportType,
        failureType: NetworkFailureType,
        queuedMessagesCount: Int = 0,
        timestamp: Instant? = null,
    ): ConnectionHealthState {
        val now = timestamp ?: Instant.now()

        // 1. DEAD / OFFLINE check.
        if (!hasNetwork || !internetReachable) {
            return ConnectionHealthState(
                health = ConnectionHealth.offline,
                transport = transport,
                hasNetwork = hasNetwork,
                internetReachable = internetReachable,
                backendReachable = false,
                realtimeStatus = RealtimeSocketStatus.disconnected,
                latency = null,
                consecutiveFailures = previousState.consecutiveFailures + 1,
                consecutiveSuccesses = 0,
                queuedMessagesCount = queuedMessagesCount,
                lastFailureType = if (!hasNetwork) NetworkFailureType.noNetwork
                else if (failureType != NetworkFailureType.none) failureType
                else NetworkFailureType.dnsFailure,
                lastCheckedAt = now,
            )
        }

        // 2. Authentication failure check: internet is fine, auth needs recovery.
        if (failureType == NetworkFailureType.authenticationFailure) {
            return ConnectionHealthState(
                health = ConnectionHealth.weak,
                transport = transport,
                hasNetwork = hasNetwork,
                internetReachable = internetReachable,
                backendReachable = false,
                realtimeStatus = realtimeStatus,
                latency = latency,
                consecutiveFailures = previousState.consecutiveFailures + 1,
                consecutiveSuccesses = 0,
                queuedMessagesCount = queuedMessagesCount,
                lastFailureType = failureType,
                statusMessage = "Authentication required",
                lastCheckedAt = now,
            )
        }

        // 3. Raw target health from current signals.
        val targetHealth: ConnectionHealth
        val consecutiveFailures: Int
        val consecutiveSuccesses: Int

        if (!backendReachable) {
            targetHealth = ConnectionHealth.poor
            consecutiveFailures = previousState.consecutiveFailures + 1
            consecutiveSuccesses = 0
        } else {
            consecutiveFailures = 0
            consecutiveSuccesses = previousState.consecutiveSuccesses + 1

            val latMs = latency?.inWholeMilliseconds ?: 0
            targetHealth = when {
                latMs > weakLatencyMaxMs -> ConnectionHealth.poor
                latMs > excellentLatencyMaxMs ||
                    realtimeStatus == RealtimeSocketStatus.reconnecting ||
                    realtimeStatus == RealtimeSocketStatus.connecting ||
                    realtimeStatus == RealtimeSocketStatus.disconnected -> ConnectionHealth.weak
                else -> ConnectionHealth.excellent
            }
        }

        // 4. Hysteresis to prevent rapid flickering.
        val effectiveHealth: ConnectionHealth = when (targetHealth) {
            ConnectionHealth.excellent -> when (previousState.health) {
                ConnectionHealth.poor, ConnectionHealth.offline ->
                    if (consecutiveSuccesses >= minSuccessesToUpgrade) ConnectionHealth.excellent
                    else ConnectionHealth.weak
                ConnectionHealth.weak ->
                    if (consecutiveSuccesses >= minSuccessesToUpgrade) ConnectionHealth.excellent
                    else ConnectionHealth.weak
                else -> ConnectionHealth.excellent
            }
            ConnectionHealth.weak ->
                if (previousState.health == ConnectionHealth.poor) {
                    if (consecutiveSuccesses >= minSuccessesToUpgrade) ConnectionHealth.weak
                    else ConnectionHealth.poor
                } else ConnectionHealth.weak
            else ->
                // target is poor: soft-buffer a single glitch from excellent.
                if (previousState.health == ConnectionHealth.excellent &&
                    consecutiveFailures < minFailuresToDowngrade
                ) ConnectionHealth.weak
                else ConnectionHealth.poor
        }

        return ConnectionHealthState(
            health = effectiveHealth,
            transport = transport,
            hasNetwork = hasNetwork,
            internetReachable = internetReachable,
            backendReachable = backendReachable,
            realtimeStatus = realtimeStatus,
            latency = latency,
            consecutiveFailures = consecutiveFailures,
            consecutiveSuccesses = consecutiveSuccesses,
            queuedMessagesCount = queuedMessagesCount,
            lastFailureType = failureType,
            lastCheckedAt = now,
        )
    }
}
