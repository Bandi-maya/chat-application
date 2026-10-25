package com.example.chat.data.api

import android.content.Context

/** Exact port of lib/data/services/backend_config.dart (ApiBackendConfig):
 *  tenant selection + REST base URL resolution + SharedPreferences persistence. */
class ApiBackendConfig(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("chaty_api_backend_config", Context.MODE_PRIVATE)

    @Volatile
    private var customTenantId: String? = null

    @Volatile
    private var useDefault: Boolean = false

    val isUsingDefaultTenant: Boolean
        get() = useDefault || customTenantId.isNullOrBlank()

    /** Dart init(): restore the persisted tenant selection. */
    fun init() {
        try {
            useDefault = prefs.getBoolean(KEY_USE_DEFAULT_TENANT, false)
            val saved = prefs.getString(KEY_ACTIVE_TENANT_ID, null)
            if (!saved.isNullOrBlank()) customTenantId = saved.trim()
        } catch (_: Exception) {
        }
    }

    fun setTenantId(tenant: String?, useDefault: Boolean = false) {
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
    }

    fun getSavedTenantId(): String? =
        try { prefs.getString(KEY_ACTIVE_TENANT_ID, null) } catch (_: Exception) { null }

    /** Dart: String.fromEnvironment('TENANT_ID', defaultValue: fallback). The
     *  repo ships no dart-define override, so the fallback is the live value;
     *  OVERRIDE mirrors the env seam for future build flavors. */
    val defaultTenantId: String
        get() = TENANT_ID_OVERRIDE.ifEmpty { FALLBACK_DEFAULT_TENANT }

    val tenantId: String
        get() {
            val custom = customTenantId
            if (!useDefault && !custom.isNullOrBlank()) return custom.trim()
            return defaultTenantId
        }

    /** Dart restApiBaseUrl: customBaseUrl -> REST_API_BASE_URL env -> Android
     *  emulator loopback. The repo ships no dart-define, so the loopback
     *  default is the live value; OVERRIDE mirrors the env seam. */
    val restApiBaseUrl: String
        get() = REST_API_BASE_URL_OVERRIDE.ifEmpty { "http://10.0.2.2:8000" }

    companion object {
        const val KEY_ACTIVE_TENANT_ID = "chaty_active_tenant_id"
        const val KEY_USE_DEFAULT_TENANT = "chaty_use_default_tenant"
        const val FALLBACK_DEFAULT_TENANT = "chat-application"

        /** Mirrors String.fromEnvironment overrides (none shipped in the repo). */
        const val REST_API_BASE_URL_OVERRIDE = ""
        const val TENANT_ID_OVERRIDE = ""
    }
}
