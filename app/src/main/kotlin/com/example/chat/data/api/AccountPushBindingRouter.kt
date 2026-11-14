package com.example.chat.data.api

import android.content.Context
import com.example.chat.data.demo.DemoMode
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bindings are opaque routing hints, never auth. Only own-token responses can create a mapping. */
class AccountPushBindingRouter(context: Context, private val api: ApiBackendService,
    private val inactive: suspend (String) -> Unit,
    private val durableCalls: suspend (ApiRegisteredAccountMeta, String) -> Boolean = { _, _ -> false }) {
    private val prefs = context.applicationContext.getSharedPreferences("account_push_bindings_v1", Context.MODE_PRIVATE)
    private val registerLock = Mutex()
    private val transport = OkHttpApiTransport(allowRedirects = false)
    private fun uuid(value: String) = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    private fun current(meta: ApiRegisteredAccountMeta, token: String): Boolean = !DemoMode.active &&
        api.multiAccount.accountByKey(meta.accountKey)?.let { !it.sessionExpired && it.userId == meta.userId && it.tenantId == meta.tenantId && it.baseUrl == meta.baseUrl } == true &&
        api.multiAccount.credentialsFor(meta.accountKey)?.accessToken == token
    private fun safeBase(base: String): String? = runCatching {
        val uri = java.net.URI(base)
        base.trimEnd('/').takeIf { uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null }
    }.getOrNull()
    private fun headers(meta: ApiRegisteredAccountMeta, token: String) =
        mapOf("Authorization" to "Bearer $token", "x-tenant-id" to meta.tenantId, "Content-Type" to "application/json")

    /** Register the SAME platform token independently for each retained account; never adopt or log out. */
    suspend fun registerRetained(fcmToken: String) = withContext(Dispatchers.IO) { registerLock.withLock {
        if (DemoMode.active || fcmToken.length !in 1..4096 || fcmToken.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it !in ":-_." }) return@withLock
        val retained = api.multiAccount.accounts()
        prefs.all.keys.filter { binding -> prefs.getString(binding, null) !in retained.map { it.accountKey } }.forEach { prefs.edit().remove(it).commit() }
        retained.filter { !it.sessionExpired }.forEach { meta ->
            val credentials = api.multiAccount.credentialsFor(meta.accountKey) ?: return@forEach
            val bearer = credentials.accessToken
            val base = safeBase(meta.baseUrl) ?: return@forEach
            if (!current(meta, bearer) || credentials.expiresAtEpochMs?.let { it <= System.currentTimeMillis() } == true) return@forEach
            try {
                val body = JSONObject().put("fcm_token", fcmToken).put("device_type", "android").toString()
                var response = transport.execute("POST", "$base/me/devices", headers(meta, bearer), body)
                if (!current(meta, bearer)) return@forEach
                if (response.first == 404) response = transport.execute("POST", "$base/app-users/me/devices", headers(meta, bearer), body)
                if (!current(meta, bearer) || response.first !in 200..299) return@forEach
                val root = JSONObject(response.second)
                if (root.opt("success") != true) return@forEach
                val data = root.optJSONObject("data") ?: return@forEach
                val binding = data.optString("bindingId")
                if (uuid(binding) && data.optString("accountId") == meta.userId && data.optString("tenantId") == meta.tenantId)
                    prefs.edit().putString(binding, meta.accountKey).commit()
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { /* Retry on an existing token/login wake; never log bearer/platform token. */ }
        }
    } }

    /** Unknown/foreign/stale hint => no-op. No active-account fallback and no inactive destructive queue drain. */
    suspend fun handle(bindingId: String, callId: String? = null) = withContext(Dispatchers.IO) {
        if (DemoMode.active || !uuid(bindingId)) return@withContext
        val key = prefs.getString(bindingId, null) ?: return@withContext
        val meta = api.multiAccount.accountByKey(key) ?: return@withContext
        val credentials = api.multiAccount.credentialsFor(key) ?: return@withContext
        val bearer = credentials.accessToken
        val base = safeBase(meta.baseUrl) ?: return@withContext
        if (!current(meta, bearer)) return@withContext
        try {
            var response = transport.execute("GET", "$base/auth/device-bindings/$bindingId", headers(meta, bearer), null)
            if (!current(meta, bearer)) return@withContext
            if (response.first == 404) response = transport.execute("GET", "$base/api/v1/auth/device-bindings/$bindingId", headers(meta, bearer), null)
            if (!current(meta, bearer) || response.first != 200) return@withContext
            val data = JSONObject(response.second).optJSONObject("data") ?: return@withContext
            if (data.optString("bindingId") != bindingId || data.optString("accountId") != meta.userId || data.optString("tenantId") != meta.tenantId) return@withContext
            if (callId != null && durableCalls(meta, bearer)) return@withContext
            if (api.currentAccountKey() == key) {
                val owner = api.authSnapshot()
                if (owner.userId != meta.userId || owner.tenantId != meta.tenantId || owner.baseUrl != meta.baseUrl || owner.token != bearer) return@withContext
                if (callId == null) api.syncFromPushNudge(owner)
                else if (callId.isNotBlank()) api.pollCallSignalsAndAwait(owner)
            } else if (callId == null) inactive(key)
            // Inactive calls intentionally wait for durable peek+ACK protocol. No queue consumption here.
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { /* Unverified or unavailable: do not render notification or select another account. */ }
    }
}
