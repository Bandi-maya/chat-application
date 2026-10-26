package com.example.chat.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Observes validated internet connectivity. [state] drives: immediate realtime
 * reconnect on regain (reset BackoffPolicy) and pausing retries while offline.
 * Requires ACCESS_NETWORK_STATE (normal permission, no prompt).
 */
class NetworkStatusMonitor(context: Context) {
    data class Status(val online: Boolean, val metered: Boolean)

    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _state = MutableStateFlow(current())
    val state: StateFlow<Status> = _state

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _state.value = current() }
        override fun onLost(network: Network) { _state.value = current() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _state.value = current() }
    }

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Idempotent: a second start() does not register the callback again. */
    fun start() {
        if (!started.compareAndSet(false, true)) { _state.value = current(); return }
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
        }
        _state.value = current()
    }

    fun stop() { if (started.compareAndSet(true, false)) runCatching { cm.unregisterNetworkCallback(callback) } }

    private fun current(): Status {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        return Status(online, cm.isActiveNetworkMetered)
    }
}
