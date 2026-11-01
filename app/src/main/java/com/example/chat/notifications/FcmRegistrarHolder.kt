package com.example.chat.notifications

import com.example.chat.data.api.FcmTokenRegistrar

/** Process-wide bridge used by the framework-created FirebaseMessagingService. */
object FcmRegistrarHolder {
    @Volatile
    var registrar: FcmTokenRegistrar? = null
}
