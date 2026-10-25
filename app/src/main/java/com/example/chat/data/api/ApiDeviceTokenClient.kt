package com.example.chat.data.api

/** FCM device-token plane of the api branch's ApiBackendService
 *  (lib/data/services/api_backend_service.dart:1975-2036): POST
 *  {fcm_token, device_type} to {restApiBaseUrl}/me/devices with Bearer auth
 *  and x-tenant-id, falling back to /app-users/me/devices on a 404, and the
 *  matching DELETE on unregister. Both calls swallow every failure after
 *  logging, exactly like Dart (push registration must never break chat), so
 *  this client reports outcomes through its injected logger and never
 *  throws. Context-free and JVM-testable through the [Transport] seam.
 */
class ApiDeviceTokenClient(
    private val restApiBaseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: () -> String?,
    private val transport: Transport = OkHttpTransport(),
    private val log: (String) -> Unit = {},
) {

    /** Minimal HTTP seam so the client is JVM-testable without OkHttp. */
    interface Transport {
        fun send(method: String, url: String, headers: Map<String, String>, body: String?): Int
    }

    /** Port of ApiBackendService.registerDeviceToken. */
    fun registerDeviceToken(fcmToken: String, deviceType: String = "android") {
        val bearer = token()
        if (bearer == null || fcmToken.isEmpty()) return
        val base = restApiBaseUrl().trimEnd('/')
        val headers = mapOf(
            "Authorization" to "Bearer $bearer",
            "Content-Type" to "application/json",
            "x-tenant-id" to tenantId(),
        )
        val body = """{"fcm_token":"$fcmToken","device_type":"$deviceType"}"""
        try {
            val status = transport.send("POST", "$base/me/devices", headers, body)
            if (status == 404) {
                transport.send("POST", "$base/app-users/me/devices", headers, body)
            }
            log("[ApiDeviceTokenClient] FCM token registered with backend")
        } catch (e: Exception) {
            log("[ApiDeviceTokenClient] Error registering FCM token: $e")
        }
    }

    /** Port of ApiBackendService.unregisterDeviceToken. */
    fun unregisterDeviceToken(fcmToken: String) {
        val bearer = token()
        if (bearer == null || fcmToken.isEmpty()) return
        val base = restApiBaseUrl().trimEnd('/')
        val headers = mapOf(
            "Authorization" to "Bearer $bearer",
            "x-tenant-id" to tenantId(),
        )
        try {
            val status = transport.send("DELETE", "$base/me/devices/$fcmToken", headers, null)
            if (status == 404) {
                transport.send("DELETE", "$base/app-users/me/devices/$fcmToken", headers, null)
            }
            log("[ApiDeviceTokenClient] FCM token unregistered from backend")
        } catch (e: Exception) {
            log("[ApiDeviceTokenClient] Error unregistering FCM token: $e")
        }
    }

    /** Device transport over OkHttp, mirroring package:http's post/delete. */
    class OkHttpTransport : Transport {
        private val client = okhttp3.OkHttpClient()

        override fun send(method: String, url: String, headers: Map<String, String>, body: String?): Int {
            val requestBody = body?.let {
                okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse("application/json"), it,
                )
            }
            val builder = okhttp3.Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            when (method) {
                "POST" -> builder.post(
                    requestBody ?: okhttp3.RequestBody.create(null, ByteArray(0)),
                )
                "DELETE" -> if (requestBody != null) builder.delete(requestBody) else builder.delete()
                else -> throw IllegalArgumentException("unsupported method $method")
            }
            client.newCall(builder.build()).execute().use { return it.code }
        }
    }
}
