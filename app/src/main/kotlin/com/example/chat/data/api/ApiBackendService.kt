package com.example.chat.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android holder for the REST backend: ApiBackendConfig (tenant + base URL),
 *  ApiAuthClient (endpoints), ApiAuthSession (api_hub_token/api_hub_user
 *  persistence). Mirrors the api branch's ApiBackendService auth surface; the
 *  ChatyBackendService facade prefers this session, so splash/home routing
 *  reads [isAuthenticated]/[currentUser] from here first. */
class ApiBackendService(context: Context) {

    val config = ApiBackendConfig(context)

    private val prefs = context.applicationContext
        .getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)

    private val sessionStore = object : ApiSessionStore {
        override fun loadToken(): String? =
            try { prefs.getString("api_hub_token", null) } catch (_: Exception) { null }
        override fun loadUserJson(): String? =
            try { prefs.getString("api_hub_user", null) } catch (_: Exception) { null }
        override fun save(token: String, userJson: String) {
            try {
                prefs.edit()
                    .putString("api_hub_token", token)
                    .putString("api_hub_user", userJson)
                    .apply()
            } catch (_: Exception) {
            }
        }
        override fun clear() {
            try { prefs.edit().remove("api_hub_token").remove("api_hub_user").apply() } catch (_: Exception) {}
        }
    }

    private val auth = ApiAuthClient(
        baseUrl = { config.restApiBaseUrl },
        tenantId = { config.tenantId },
        transport = OkHttpApiTransport(),
    )

    private val session = ApiAuthSession(sessionStore)

    val isAuthenticated: Boolean get() = session.isAuthenticated
    val currentUser: ApiUserProfile? get() = session.currentUser
    val token: String? get() = session.token

    private var initialized = false

    /** Dart initialize(): config restore + session restore (once). */
    fun initialize() {
        if (initialized) return
        initialized = true
        config.init()
        session.restore()
    }

    /** UsernameLoginService.login: strip leading @, apply tenant selection,
     *  then delegate to ApiBackendService.login. */
    suspend fun login(identifier: String, password: String, tenant: String?, isDefaultTenant: Boolean): ApiUserProfile =
        withContext(Dispatchers.IO) {
            val value = identifier.trim().replace(Regex("^@+"), "")
            if (isDefaultTenant) {
                config.setTenantId(null, useDefault = true)
            } else if (!tenant.isNullOrBlank()) {
                config.setTenantId(tenant.trim(), useDefault = false)
            }
            val result = auth.login(value, password)
            session.adopt(result)
            result.user
        }

    suspend fun register(email: String, username: String, password: String): ApiUserProfile =
        withContext(Dispatchers.IO) {
            val result = auth.register(email, username, password)
            session.adopt(result)
            result.user
        }

    suspend fun forgotPassword(email: String): String = withContext(Dispatchers.IO) {
        auth.forgotPassword(email)
    }

    /** ApiBackendService.registerOrganization: on success applies the new
     *  tenant (Dart setTenantId) and adopts the returned admin session. */
    suspend fun registerOrganization(
        tenantId: String,
        organizationName: String,
        adminUsername: String,
        adminEmail: String,
        adminPassword: String,
        adminFullName: String,
        allowUserRegistration: Boolean = true,
    ): Triple<ApiUserProfile, String, String> = withContext(Dispatchers.IO) {
        val (result, orgMap) = auth.registerOrganization(
            tenantId = tenantId,
            organizationName = organizationName,
            adminUsername = adminUsername,
            adminEmail = adminEmail,
            adminPassword = adminPassword,
            adminFullName = adminFullName,
            allowUserRegistration = allowUserRegistration,
        )
        var orgId = tenantId.trim()
        var resolvedOrgName = organizationName.trim()
        if (orgMap != null) {
            orgId = (orgMap["uid"] ?: orgMap["tenantId"] ?: orgId).toString()
            resolvedOrgName = (orgMap["name"] ?: resolvedOrgName).toString()
            try {
                val orgJson = org.json.JSONObject()
                for ((k, v) in orgMap) orgJson.put(k, v)
                prefs.edit().putString("api_hub_org", orgJson.toString()).apply()
            } catch (_: Exception) {
            }
        }
        config.setTenantId(tenantId.trim())
        session.adopt(result)
        Triple(result.user, orgId, resolvedOrgName)
    }

    suspend fun checkOrganization(workspace: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        auth.checkOrganization(workspace)
    }

    fun signOut() = session.signOut()
}
