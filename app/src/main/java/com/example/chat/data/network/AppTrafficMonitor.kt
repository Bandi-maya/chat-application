package com.example.chat.data.network

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppTrafficSnapshot(
    val uploadedBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val supported: Boolean = true,
)

/**
 * Samples Android's per-UID network counters. Unlike transfer-body progress this
 * includes Chaty's realtime sockets, signaling, API requests, media and protocol
 * overhead. Counters are cumulative and survive process restarts.
 */
class AppTrafficMonitor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chaty_app_traffic_v1", Context.MODE_PRIVATE)
    private val lock = Any()
    private val uid = Process.myUid()
    private val _state = MutableStateFlow(
        AppTrafficSnapshot(
            uploadedBytes = prefs.getLong("total_tx", 0),
            downloadedBytes = prefs.getLong("total_rx", 0),
        ),
    )
    val state: StateFlow<AppTrafficSnapshot> = _state
    private var job: Job? = null
    private var lastPersistAt = 0L

    fun start(scope: CoroutineScope) {
        synchronized(lock) {
            if (job?.isActive == true) return
            job = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    sample()
                    delay(1_000L)
                }
            }
        }
    }

    private fun sample() = synchronized(lock) {
        val tx = TrafficStats.getUidTxBytes(uid)
        val rx = TrafficStats.getUidRxBytes(uid)
        if (tx < 0L || rx < 0L) {
            _state.value = _state.value.copy(supported = false)
            return
        }
        val previousTx = prefs.getLong("last_tx", -1L)
        val previousRx = prefs.getLong("last_rx", -1L)
        val txDelta = if (previousTx >= 0L && tx >= previousTx) tx - previousTx else 0L
        val rxDelta = if (previousRx >= 0L && rx >= previousRx) rx - previousRx else 0L
        val current = _state.value
        val next = current.copy(
            uploadedBytes = current.uploadedBytes + txDelta,
            downloadedBytes = current.downloadedBytes + rxDelta,
            supported = true,
        )
        _state.value = next
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPersistAt >= 5_000L || previousTx < 0L || previousRx < 0L) {
            prefs.edit()
                .putLong("last_tx", tx)
                .putLong("last_rx", rx)
                .putLong("total_tx", next.uploadedBytes)
                .putLong("total_rx", next.downloadedBytes)
                .apply()
            lastPersistAt = now
        }
    }
}
