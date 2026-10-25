package com.example.chat.data.api

/** Port of the api branch's FirebaseNotificationService token lifecycle
 *  (lib/core/services/firebase_notification_service.dart:120-172):
 *  _registerWithBackends / syncTokenOnLogin / unregisterOnLogout. The
 *  FirebaseMessaging hooks live in ChatyFirebaseMessagingService; this class
 *  owns only the dual-registration policy so it stays context-free and
 *  JVM-testable with both backend seams injected.
 *
 *  Dart registers with two backends: api-hub (registerDeviceToken) and
 *  PushTokenService (the Supabase device registry). The Supabase registry
 *  is not yet ported, so the second registration is an optional seam:
 *  when absent, only api-hub is used. Both registrations swallow failures
 *  after logging, exactly like Dart.
 */
class FcmTokenRegistrar(
    private val apiDevices: ApiDeviceTokenClient,
    /** Supabase PushTokenService.registerPlatformToken equivalent; null while
     *  the Supabase device registry remains unported. */
    private val pushRegistry: ((String) -> Unit)? = null,
    private val platform: String = "android",
    private val log: (String) -> Unit = {},
) {
    @Volatile
    private var fcmToken: String? = null

    /** FirebaseMessagingService.onNewToken and the initial getToken() both
     *  funnel here: remember the token and register it with every backend
     *  that is currently available (Dart _registerWithBackends). */
    fun onTokenReceived(token: String) {
        fcmToken = token
        registerWithBackends(token)
    }

    /** Dart _registerWithBackends: api-hub first (only when authenticated,
     *  which ApiDeviceTokenClient enforces through its null-token guard),
     *  then the Supabase registry; each failure is logged, never thrown. */
    fun registerWithBackends(token: String) {
        try {
            apiDevices.registerDeviceToken(token, deviceType = platform)
        } catch (e: Exception) {
            log("[FcmTokenRegistrar] Failed to register token with api-hub: $e")
        }
        val registry = pushRegistry
        if (registry != null) {
            try {
                registry(token)
            } catch (e: Exception) {
                log("[FcmTokenRegistrar] Failed to register token with PushTokenService: $e")
            }
        }
    }

    /** Dart syncTokenOnLogin: re-push the remembered token, or wait for the
     *  next onTokenReceived when none has been delivered yet. */
    fun syncTokenOnLogin() {
        val token = fcmToken
        if (!token.isNullOrEmpty()) registerWithBackends(token)
    }

    /** Dart unregisterOnLogout: api-hub DELETE for the remembered token. */
    fun unregisterOnLogout() {
        val token = fcmToken
        if (!token.isNullOrEmpty()) {
            try {
                apiDevices.unregisterDeviceToken(token)
            } catch (_: Exception) {
            }
        }
    }
}
