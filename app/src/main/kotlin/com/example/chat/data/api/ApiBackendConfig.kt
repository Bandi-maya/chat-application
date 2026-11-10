package com.example.chat.data.api

import android.content.Context
import com.example.chat.BuildConfig

/** Exact port of lib/data/services/backend_config.dart (ApiBackendConfig):
 *  tenant selection + REST base URL resolution + SharedPreferences persistence. */
class ApiBackendConfig(context: Context, private val scopeLock: Any = Any(), private val onTransition: () -> Unit = {}) {
    private var generation: Long = 0
    private val prefs = context.applicationContext
        .getSharedPreferences("chaty_api_backend_config", Context.MODE_PRIVATE)

    @Volatile
    private var customTenantId: String? = BuildConfig.DOTENV_TENANT_ID.takeIf { it.isNotEmpty() }

    @Volatile
    private var useDefault: Boolean = false

    @Volatile
    private var customBaseUrl: String? = null

    @Volatile
    private var customWebSocketTemplate: String? = null

    val isUsingDefaultTenant: Boolean
        get() = useDefault || customTenantId.isNullOrBlank() || customTenantId.equals(defaultTenantId, ignoreCase = true)

    /** Dart init(): restore the persisted tenant selection. */
    fun init() = synchronized(scopeLock) {
        generation++
        try {
            useDefault = prefs.getBoolean(KEY_USE_DEFAULT_TENANT, false)
            val saved = prefs.getString(KEY_ACTIVE_TENANT_ID, null)
            if (!saved.isNullOrBlank()) {
                if (saved.trim().equals(defaultTenantId, ignoreCase = true)) {
                    useDefault = true
                    customTenantId = null
                } else {
                    customTenantId = saved.trim()
                }
            } else if (customTenantId?.trim()?.equals(defaultTenantId, ignoreCase = true) == true) {
                useDefault = true
                customTenantId = null
            }
        } catch (_: Exception) {
        }
        onTransition()
    }

    fun snapshot(): ApiRoutingSnapshot = synchronized(scopeLock) {
        ApiRoutingSnapshot(
            tenantId,
            restApiBaseUrl,
            customWebSocketTemplate?.takeIf { it.isNotBlank() }
                ?: BackendRoutingPolicy.websocketTemplate(DOTENV_WEBSOCKET_URL, WEBSOCKET_URL_OVERRIDE),
            generation,
        )
    }

    fun setTenantId(tenant: String?, useDefault: Boolean = false) = synchronized(scopeLock) {
        setRouting(tenant, useDefault, restBaseUrl = null, webSocketTemplate = null, notify = true)
    }

    /** Atomically updates tenant + optional validated custom base/websocket URL for account switching. */
    fun setRouting(
        tenant: String?,
        useDefault: Boolean = false,
        restBaseUrl: String? = null,
        webSocketTemplate: String? = null,
        notify: Boolean = true,
    ) = synchronized(scopeLock) {
        generation++
        val ed = prefs.edit()
        val effectiveDefault = useDefault || tenant.isNullOrBlank() || tenant.trim().equals(defaultTenantId, ignoreCase = true)
        this.useDefault = effectiveDefault
        ed.putBoolean(KEY_USE_DEFAULT_TENANT, effectiveDefault)
        if (effectiveDefault) {
            customTenantId = null
            ed.remove(KEY_ACTIVE_TENANT_ID)
        } else {
            val clean = tenant!!.trim()
            customTenantId = clean
            ed.putString(KEY_ACTIVE_TENANT_ID, clean)
        }
        val cleanBase = restBaseUrl?.trim()?.takeIf { it.isNotEmpty() && ApiAccountRoutingCanonicalizer.isValidEndpointUrl(it) }
            ?.let { ApiAccountRoutingCanonicalizer.normalizeBaseUrl(it) }
        customBaseUrl = cleanBase
        if (cleanBase == null) ed.remove(KEY_CUSTOM_BASE_URL) else ed.putString(KEY_CUSTOM_BASE_URL, cleanBase)

        val cleanWs = webSocketTemplate?.trim()?.takeIf { it.isNotEmpty() }
        customWebSocketTemplate = cleanWs
        if (cleanWs == null) ed.remove(KEY_CUSTOM_WS_TEMPLATE) else ed.putString(KEY_CUSTOM_WS_TEMPLATE, cleanWs)
        ed.apply()
        if (notify) onTransition()
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
        get() = customBaseUrl?.takeIf { it.isNotBlank() }
            ?: BackendRoutingPolicy.restBase(BuildConfig.DOTENV_REST_API_BASE_URL, REST_API_BASE_URL_OVERRIDE)

    companion object {
        const val KEY_ACTIVE_TENANT_ID = "chaty_active_tenant_id"
        const val KEY_USE_DEFAULT_TENANT = "chaty_use_default_tenant"
        const val KEY_CUSTOM_BASE_URL = "chaty_custom_base_url"
        const val KEY_CUSTOM_WS_TEMPLATE = "chaty_custom_ws_template"
        const val FALLBACK_DEFAULT_TENANT = BackendRoutingPolicy.DEFAULT_TENANT_ID

        /** Mirrors String.fromEnvironment overrides (none shipped in the repo). */
        val REST_API_BASE_URL_OVERRIDE: String get() = BuildConfig.REST_API_BASE_URL
        val TENANT_ID_OVERRIDE: String get() = BuildConfig.TENANT_ID
        val WEBSOCKET_URL_OVERRIDE: String get() = BackendRoutingPolicy.websocketTemplate("", BuildConfig.WEBSOCKET_URL)
        val DOTENV_WEBSOCKET_URL: String get() = BuildConfig.DOTENV_WEBSOCKET_URL
    }
}
