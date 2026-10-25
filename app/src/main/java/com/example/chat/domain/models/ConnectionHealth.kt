package com.example.chat.domain.models

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Exact port of lib/domain/models/connection_health.dart. */

/** The four core connection health states in Chaty. */
enum class ConnectionHealth {
    /** Green: network active, backend healthy, realtime connected, latency <= 300ms. */
    excellent,
    /** Yellow: elevated latency (300-1500ms), socket reconnect, intermittent failure. */
    weak,
    /** Red: internet reachable but Chaty backend unreachable, or latency > 1500ms. */
    poor,
    /** Dead: no network transport or no internet reachability. */
    offline,
}

/** Transport technology currently available on the device. */
enum class NetworkTransportType { wifi, cellular, ethernet, vpn, other, none }

/** Status of the Supabase Realtime WebSocket connection. */
enum class RealtimeSocketStatus { connected, connecting, reconnecting, disconnected }

/** Detailed classification of network failure reasons. */
enum class NetworkFailureType {
    none, noNetwork, timeout, dnsFailure, backendUnavailable,
    realtimeDisconnected, authenticationFailure, rateLimited, unknown,
}

/** Rich, immutable snapshot of the device's connection diagnostics. */
data class ConnectionHealthState(
    val health: ConnectionHealth,
    val transport: NetworkTransportType = NetworkTransportType.none,
    val hasNetwork: Boolean = false,
    val internetReachable: Boolean = false,
    val backendReachable: Boolean = false,
    val realtimeStatus: RealtimeSocketStatus = RealtimeSocketStatus.disconnected,
    val latency: Duration? = null,
    val consecutiveFailures: Int = 0,
    val consecutiveSuccesses: Int = 0,
    val queuedMessagesCount: Int = 0,
    val lastFailureType: NetworkFailureType = NetworkFailureType.none,
    val statusMessage: String? = null,
    val lastCheckedAt: Instant,
) {
    /** User-friendly label for display in UI headers and status sheets. */
    val displayTitle: String
        get() = when (health) {
            ConnectionHealth.excellent -> "Connected"
            ConnectionHealth.weak -> "Weak connection"
            ConnectionHealth.poor -> "Poor connection"
            ConnectionHealth.offline -> "No internet connection"
        }

    /** User-friendly explanation string. */
    val displaySubtitle: String
        get() = when (health) {
            ConnectionHealth.excellent -> "Chat service is active and fast"
            ConnectionHealth.weak -> "Messages may take longer to deliver"
            ConnectionHealth.poor ->
                if (backendReachable) "High latency or intermittent failures"
                else "Chat service is temporarily unreachable"
            ConnectionHealth.offline -> "Messages will send when you're back online"
        }

    /** Screen reader semantic label. */
    val accessibilityLabel: String
        get() = when (health) {
            ConnectionHealth.excellent -> "Connection excellent"
            ConnectionHealth.weak -> "Connection weak"
            ConnectionHealth.poor -> "Connection poor"
            ConnectionHealth.offline -> "No internet connection"
        }

    companion object {
        /** Default initial state before first probe completes. */
        fun initial(): ConnectionHealthState = ConnectionHealthState(
            health = ConnectionHealth.excellent,
            hasNetwork = true,
            internetReachable = true,
            backendReachable = true,
            realtimeStatus = RealtimeSocketStatus.connected,
            transport = NetworkTransportType.wifi,
            lastCheckedAt = Instant.now(),
        )
    }
}

/** Dart Duration? convenience for call sites that carry milliseconds. */
val ConnectionHealthState.latencyMs: Long?
    get() = latency?.inWholeMilliseconds

fun Long.toDurationMs(): Duration = this.milliseconds
