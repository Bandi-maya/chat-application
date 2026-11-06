package com.example.chat.data.media

import android.content.Context

/** One-time view-once education flags (GB/stock show a sender and a receiver
 *  NUX exactly once per device). App-level preference, not account data. */
class ViewOnceNuxStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("chaty_view_once", Context.MODE_PRIVATE)

    fun needsSenderNux(): Boolean = !prefs.getBoolean(KEY_SENDER, false)
    fun markSenderSeen() {
        prefs.edit().putBoolean(KEY_SENDER, true).apply()
    }

    fun needsReceiverNux(): Boolean = !prefs.getBoolean(KEY_RECEIVER, false)
    fun markReceiverSeen() {
        prefs.edit().putBoolean(KEY_RECEIVER, true).apply()
    }

    private companion object {
        const val KEY_SENDER = "sender_nux_seen"
        const val KEY_RECEIVER = "receiver_nux_seen"
    }
}
