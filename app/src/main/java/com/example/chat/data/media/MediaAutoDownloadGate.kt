package com.example.chat.data.media

import com.example.chat.data.network.NetworkStatusMonitor

/** Android bridge for AutoDownloadPolicy (media matrix): maps the live
 *  NetworkStatusMonitor reading onto the pure policy, holds user rules and
 *  per-conversation overrides in SharedPreferences. UI asks shouldAuto()
 *  before starting an attachment download; tap-to-download always works. */
class MediaAutoDownloadGate(
    private val network: NetworkStatusMonitor,
    private val prefs: android.content.SharedPreferences,
) {
    var rules: AutoDownloadPolicy.Rules
        get() = AutoDownloadPolicy.Rules(
            dataSaverEnabled = prefs.getBoolean(KEY_SAVER, false),
        )
        set(value) {
            prefs.edit().putBoolean(KEY_SAVER, value.dataSaverEnabled).apply()
        }

    fun overrideFor(conversationId: String): AutoDownloadPolicy.Override =
        when (prefs.getString("$KEY_OVR$conversationId", null)) {
            "always" -> AutoDownloadPolicy.Override.ALWAYS
            "never" -> AutoDownloadPolicy.Override.NEVER
            else -> AutoDownloadPolicy.Override.DEFAULT
        }

    fun setOverride(conversationId: String, value: AutoDownloadPolicy.Override) {
        prefs.edit().putString(
            "$KEY_OVR$conversationId",
            when (value) {
                AutoDownloadPolicy.Override.ALWAYS -> "always"
                AutoDownloadPolicy.Override.NEVER -> "never"
                AutoDownloadPolicy.Override.DEFAULT -> null
            },
        ).apply()
    }

    fun shouldAuto(
        conversationId: String,
        kind: AutoDownloadPolicy.MediaKind,
        sizeBytes: Long?,
    ): Boolean {
        val status = network.state.value
        val net = when {
            !status.online -> AutoDownloadPolicy.Network.OFFLINE
            status.transport == "wifi" || status.transport == "ethernet" -> AutoDownloadPolicy.Network.WIFI
            // Android marks roaming as metered+cellular; NetworkStatusMonitor
            // exposes metered only, so roaming falls under cellular rules with
            // the data saver applied - the strictest safe reading.
            else -> AutoDownloadPolicy.Network.CELLULAR
        }
        return AutoDownloadPolicy.shouldAutoDownload(
            net, kind, sizeBytes, rules, overrideFor(conversationId))
    }

    companion object {
        private const val KEY_SAVER = "media_data_saver"
        private const val KEY_OVR = "media_autodl_"
    }
}
