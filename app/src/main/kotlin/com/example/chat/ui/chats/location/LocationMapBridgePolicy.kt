package com.example.chat.ui.chats.location

import java.security.SecureRandom

/** Host-side allowlist for a WebMessageListener. This is not a generic JavascriptInterface. */
class LocationMapBridgePolicy(val session: String = newSession()) {
    init { require(session.matches(Regex("[a-f0-9]{32}"))) }

    fun validate(
        origin: String,
        isMainFrame: Boolean,
        encodedLength: Int,
        fields: Map<String, Any?>,
    ): LocationMapEvent? {
        if (origin != LOCAL_ORIGIN || !isMainFrame || encodedLength !in 1..MAX_MESSAGE_LENGTH) return null
        if (fields["version"] != 1 && fields["version"] != 1.0) return null
        if (fields["session"] != session) return null
        return when (fields["type"]) {
            "ready" -> if (fields.keys == setOf("version", "type", "session")) LocationMapEvent.Ready else null
            "pinSelected" -> {
                if (fields.keys != setOf("version", "type", "session", "latitude", "longitude")) return null
                val lat = (fields["latitude"] as? Number)?.toDouble() ?: return null
                val lon = (fields["longitude"] as? Number)?.toDouble() ?: return null
                val coordinate = runCatching { LocationCoordinate(lat, lon) }.getOrNull() ?: return null
                LocationMapEvent.PinSelected(coordinate)
            }
            else -> null
        }
    }

    companion object {
        const val LOCAL_ORIGIN = "https://appassets.androidplatform.net"
        const val MAX_MESSAGE_LENGTH = 512
        private fun newSession(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

sealed class LocationMapEvent {
    data object Ready : LocationMapEvent()
    data class PinSelected(val coordinate: LocationCoordinate) : LocationMapEvent()
}
