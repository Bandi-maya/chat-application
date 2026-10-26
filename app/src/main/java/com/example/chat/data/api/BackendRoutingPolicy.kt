package com.example.chat.data.api

/** Public client routing only. Never a place for credentials or server secrets. */
object BackendRoutingPolicy {
    const val DEFAULT_REST_API_BASE_URL = "https://api.hub.logybyte.in"
    const val DEFAULT_WEBSOCKET_URL = "wss://api.hub.logybyte.in/api/chat/ws{userId}"
    const val DEFAULT_TENANT_ID = "chat-application"

    fun websocketTemplate(dotenv: String, buildOverride: String): String =
        dotenv.ifEmpty { buildOverride }.ifEmpty { DEFAULT_WEBSOCKET_URL }

    fun restBase(dotenv: String, buildOverride: String): String =
        dotenv.ifEmpty { buildOverride }.ifEmpty { DEFAULT_REST_API_BASE_URL }

    /** Dart loads .env first, then init() overwrites only with a nonempty saved tenant. */
    fun tenant(dotenv: String, saved: String?, useDefault: Boolean, buildDefault: String): String =
        if (useDefault) buildDefault.ifEmpty { DEFAULT_TENANT_ID }
        else saved?.takeIf { it.isNotBlank() }?.trim()
            ?: dotenv.takeIf { it.isNotBlank() }?.trim()
            ?: buildDefault.ifEmpty { DEFAULT_TENANT_ID }
}
