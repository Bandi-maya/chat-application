package com.example.chat.data.api

import android.content.Context
import com.example.chat.BuildConfig

/** Exact port of lib/data/services/backend_config.dart (ApiBackendConfig):
 *  tenant selection + REST base URL resolution + SharedPreferences persistence. */
class ApiBackendConfig(context: Context, private val scopeLock: Any = Any(), private val onTransition: () -> Unit = {}) {
    private var generation: Long = 0
    fun snapshot(): ApiRoutingSnapshot = synchronized(scopeLock) {
        ApiRoutingSnapshot(tenantId, restApiBaseUrl,
            BackendRoutingPolicy.websocketTemplate(DOTENV_WEBSOCKET_URL, WEBSOCKET_URL_OVERRIDE), generation)
    }

    private val prefs = context.applicationContext
        .getSharedPreferences("chaty_api_backend_config", Context.MODE_PRIVATE)

    @Volatile
    private var customTenantId: String? = BuildConfig.DOTENV_TENANT_ID.takeIf { it.isNotEmpty() }

    @Volatile
    private var useDefault: Boolean = false

    val isUsingDefaultTenant: Boolean
        get() = useDefault || customTenantId.isNullOrBlank()

    /** Dart init(): restore the persisted tenant selection. */
    fun init() = synchronized(scopeLock) {
        generation++
        try {
            useDefault = prefs.getBoolean(KEY_USE_DEFAULT_TENANT, false)
            val saved = prefs.getString(KEY_ACTIVE_TENANT_ID, null)
            if (!saved.isNullOrBlank()) customTenantId = saved.trim()
        } catch (_: Exception) {
        }
        onTransition()
    }

    fun setTenantId(tenant: String?, useDefault: Boolean = false) = synchronized(scopeLock) {
        generation++
        this.useDefault = useDefault
        prefs.edit().putBoolean(KEY_USE_DEFAULT_TENANT, useDefault).apply()
        if (useDefault || tenant.isNullOrBlank()) {
            customTenantId = null
            prefs.edit().remove(KEY_ACTIVE_TENANT_ID).apply()
        } else {
            val clean = tenant.trim()
            customTenantId = clean
            prefs.edit().putString(KEY_ACTIVE_TENANT_ID, clean).apply()
        }
        onTransition()
    }

    fun getSavedTenantId(): String? =
        try { prefs.getString(KEY_ACTIVE_TENANT_ID, null) } catch (_: Exception) { null }

    /** Dart String.fromEnvironment default tenant; .env is the initial custom tenant. */
    val defaultTenantId: String
        get() = TENANT_ID_OVERRIDE.ifEmpty { FALLBACK_DEFAULT_TENANT }

    val tenantId: String
        get() = BackendRoutingPolicy.tenant(customTenantId.orEmpty(), null, useDefault, defaultTenantId)

    /** Public routing config is baked from root .env or Gradle/environment overrides.
     *  With none supplied, use the user's centralized public defaults. */
    val restApiBaseUrl: String
        get() = BackendRoutingPolicy.restBase(BuildConfig.DOTENV_REST_API_BASE_URL, REST_API_BASE_URL_OVERRIDE)

    companion object {
        const val KEY_ACTIVE_TENANT_ID = "chaty_active_tenant_id"
        const val KEY_USE_DEFAULT_TENANT = "chaty_use_default_tenant"
        const val FALLBACK_DEFAULT_TENANT = BackendRoutingPolicy.DEFAULT_TENANT_ID

        /** Mirrors String.fromEnvironment overrides (none shipped in the repo). */
        val REST_API_BASE_URL_OVERRIDE: String get() = BuildConfig.REST_API_BASE_URL
        val TENANT_ID_OVERRIDE: String get() = BuildConfig.TENANT_ID
        val WEBSOCKET_URL_OVERRIDE: String get() = BackendRoutingPolicy.websocketTemplate("", BuildConfig.WEBSOCKET_URL)
        val DOTENV_WEBSOCKET_URL: String get() = BuildConfig.DOTENV_WEBSOCKET_URL
    }
}
