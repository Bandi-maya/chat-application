package com.example.chat.data.services

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.chat.BuildConfig
import com.example.chat.domain.models.ConnectionHealth
import com.example.chat.domain.models.ConnectionHealthState
import com.example.chat.domain.models.NetworkFailureType
import com.example.chat.domain.models.NetworkTransportType
import com.example.chat.domain.models.RealtimeSocketStatus
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Exact port of connection_health_service.dart: interface-name transport
 * detection (wlan/rmnet/eth/tun), DNS reachability against one.one.one.one
 * with a 2.5s timeout, a non-mutating profiles ping for backend reachability
 * and latency, a 5-sample sliding median, 30s foreground probing, 500ms
 * debounced re-checks, and lifecycle resume/pause hooks. ChangeNotifier maps
 * to [notifyChanged]; the AppLifecycleListener maps to [attachLifecycle].
 */
class ConnectionHealthService(
    /** The Supabase profiles ping; null mirrors the Dart null-client mock
     *  fallback (backend assumed reachable, 45ms sample). */
    private val backendPing: (suspend () -> Unit)? = null,
    private val notifyChanged: () -> Unit = {},
) {
    @Volatile
    var state: ConnectionHealthState = ConnectionHealthState.initial()
        private set

    val health: ConnectionHealth get() = state.health
    val isOnline: Boolean get() = state.health != ConnectionHealth.offline
    val isHealthy: Boolean get() = state.health == ConnectionHealth.excellent

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null
    private var debounceJob: Job? = null
    private val probeMutex = Mutex()
    private val probing = AtomicBoolean(false)
    private var disposed = false
    private var lifecycleOwner: LifecycleOwner? = null
    private var queuedCount = 0

    /** Sliding latency ring buffer for median calculation. */
    private val recentLatenciesMs = ArrayList<Long>()

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            scope.launch { probeConnection() }
        }

        override fun onPause(owner: LifecycleOwner) {
            debounceJob?.cancel()
        }
    }

    init {
        // Probe on startup, then every 30s in the foreground (Dart _init).
        scope.launch { probeConnection() }
        periodicJob = scope.launch {
            while (isActive) {
                delay(30_000)
                probeConnection()
            }
        }
    }

    /** Wires the Dart AppLifecycleListener behaviour to a LifecycleOwner. */
    fun attachLifecycle(owner: LifecycleOwner) {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = owner
        owner.lifecycle.addObserver(lifecycleObserver)
    }

    /** Updates the pending/queued messages count shown in diagnostics and UI. */
    fun updateQueuedCount(count: Int) {
        if (queuedCount == count) return
        queuedCount = count
        state = state.copy(queuedMessagesCount = count)
        notifyChanged()
    }

    /** Notifies the service of a Supabase Realtime status transition. */
    fun notifyRealtimeStatus(status: RealtimeSocketStatus) {
        if (disposed) return
        if (state.realtimeStatus == status) return
        state = ConnectionHealthClassifier.evaluate(
            previousState = state,
            hasNetwork = state.hasNetwork,
            internetReachable = state.internetReachable,
            backendReachable = state.backendReachable,
            realtimeStatus = status,
            latency = state.latency,
            transport = state.transport,
            failureType = if (status == RealtimeSocketStatus.disconnected)
                NetworkFailureType.realtimeDisconnected else NetworkFailureType.none,
            queuedMessagesCount = queuedCount,
        )
        notifyChanged()
    }

    /** Debounced re-check after a network switch (Dart scheduleProbe, 500ms). */
    fun scheduleProbe(delayMs: Long = 500) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMs)
            probeConnection()
        }
    }

    /** Lightweight multi-signal health check; exact port of probeConnection. */
    suspend fun probeConnection(): ConnectionHealthState {
        if (disposed) return state
        if (!probing.compareAndSet(false, true)) return state
        try {
            // 1. Detect network interfaces (same name heuristics as Dart).
            var transport = NetworkTransportType.none
            var hasNetwork = false
            try {
                val names = mutableListOf<String>()
                val interfaces = NetworkInterface.getNetworkInterfaces()
                if (interfaces != null) {
                    while (interfaces.hasMoreElements()) {
                        names.add(interfaces.nextElement().name.lowercase())
                    }
                }
                if (names.isNotEmpty()) {
                    hasNetwork = true
                    val joined = names.joinToString(" ")
                    transport = when {
                        joined.contains("wlan") || joined.contains("wi-fi") ||
                            joined.contains("wifi") -> NetworkTransportType.wifi
                        joined.contains("rmnet") || joined.contains("cell") ||
                            joined.contains("pdp") -> NetworkTransportType.cellular
                        joined.contains("eth") -> NetworkTransportType.ethernet
                        joined.contains("tun") || joined.contains("vpn") -> NetworkTransportType.vpn
                        else -> NetworkTransportType.other
                    }
                }
            } catch (e: Exception) {
                // Fallback: assume network present; reachability test decides.
                hasNetwork = true
                transport = NetworkTransportType.other
            }

            if (!hasNetwork) {
                state = ConnectionHealthClassifier.evaluate(
                    previousState = state,
                    hasNetwork = false,
                    internetReachable = false,
                    backendReachable = false,
                    realtimeStatus = RealtimeSocketStatus.disconnected,
                    latency = null,
                    transport = NetworkTransportType.none,
                    failureType = NetworkFailureType.noNetwork,
                    queuedMessagesCount = queuedCount,
                )
                notifyChanged()
                return state
            }

            // 2. Internet reachability: DNS lookup, 2.5s timeout.
            var internetReachable = false
            var failureType = NetworkFailureType.none
            try {
                val result = withTimeout(2_500) {
                    InetAddress.getAllByName("one.one.one.one")
                }
                internetReachable = result.isNotEmpty() && result.first().address.isNotEmpty()
            } catch (e: UnknownHostException) {
                failureType = NetworkFailureType.dnsFailure
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                failureType = NetworkFailureType.timeout
            } catch (e: Exception) {
                failureType = NetworkFailureType.unknown
            }

            if (!internetReachable) {
                state = ConnectionHealthClassifier.evaluate(
                    previousState = state,
                    hasNetwork = true,
                    internetReachable = false,
                    backendReachable = false,
                    realtimeStatus = RealtimeSocketStatus.disconnected,
                    latency = null,
                    transport = transport,
                    failureType = failureType,
                    queuedMessagesCount = queuedCount,
                )
                notifyChanged()
                return state
            }

            // 3. Backend (Supabase) reachability + latency, 3s timeout.
            var backendReachable = false
            var sampleLatency: Duration? = null
            val ping = backendPing
            if (ping != null) {
                try {
                    val elapsed = measureTime {
                        withTimeout(3_000) { ping() }
                    }
                    sampleLatency = elapsed
                    backendReachable = true
                    failureType = NetworkFailureType.none
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    backendReachable = false
                    failureType = NetworkFailureType.timeout
                } catch (e: Exception) {
                    val err = e.message.orEmpty().lowercase()
                    when {
                        err.contains("pgrst301") || err.contains("jwt") ||
                            err.contains("401") -> {
                            // Service is up, auth needs refresh.
                            backendReachable = true
                            failureType = NetworkFailureType.authenticationFailure
                        }
                        err.contains("429") -> {
                            backendReachable = true
                            failureType = NetworkFailureType.rateLimited
                        }
                        else -> {
                            backendReachable = false
                            failureType = NetworkFailureType.backendUnavailable
                        }
                    }
                }
            } else {
                // Mock / development standalone fallback.
                backendReachable = true
                sampleLatency = 45.milliseconds
            }

            // Sliding median latency (max 5 samples).
            sampleLatency?.let {
                recentLatenciesMs.add(it.inWholeMilliseconds)
                if (recentLatenciesMs.size > MAX_LATENCY_SAMPLES) {
                    recentLatenciesMs.removeAt(0)
                }
            }
            val effectiveLatency: Duration? = if (recentLatenciesMs.isNotEmpty()) {
                val sorted = recentLatenciesMs.sorted()
                sorted[sorted.size / 2].milliseconds
            } else null

            state = ConnectionHealthClassifier.evaluate(
                previousState = state,
                hasNetwork = hasNetwork,
                internetReachable = internetReachable,
                backendReachable = backendReachable,
                realtimeStatus = if (backendReachable)
                    RealtimeSocketStatus.connected else RealtimeSocketStatus.disconnected,
                latency = effectiveLatency,
                transport = transport,
                failureType = failureType,
                queuedMessagesCount = queuedCount,
            )

            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    "Connection",
                    "transport=${state.transport} internet=${state.internetReachable} " +
                        "backend=${state.backendReachable} " +
                        "latency=${state.latency?.inWholeMilliseconds}ms health=${state.health}",
                )
            }

            notifyChanged()
            return state
        } finally {
            probing.set(false)
        }
    }

    fun dispose() {
        disposed = true
        periodicJob?.cancel()
        debounceJob?.cancel()
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
    }

    companion object {
        private const val MAX_LATENCY_SAMPLES = 5
    }
}
