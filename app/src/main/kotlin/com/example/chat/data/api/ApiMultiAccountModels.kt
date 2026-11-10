package com.example.chat.data.api

import com.example.chat.core.messages.LocalMessageOverridePolicy
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Canonicalizes tenant + REST endpoint URL + user ID into collision-free account keys and storage hashes. */
object ApiAccountRoutingCanonicalizer {
    fun normalizeTenant(tenantId: String): String = tenantId.trim().lowercase()

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return try {
            val uri = URI(trimmed)
            val scheme = (uri.scheme ?: "https").lowercase()
            val host = (uri.host ?: return trimmed.lowercase()).lowercase()
            val port = uri.port
            val includePort = port != -1 && !((scheme == "https" && port == 443) || (scheme == "http" && port == 80))
            val portSuffix = if (includePort) ":$port" else ""
            val path = uri.rawPath?.trimEnd('/').orEmpty()
            "$scheme://$host$portSuffix$path"
        } catch (_: Exception) {
            trimmed.lowercase()
        }
    }

    fun isValidEndpointUrl(baseUrl: String): Boolean {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase() ?: return false
            when (scheme) {
                "https" -> true
                "http" -> host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "10.0.2.2"
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun accountKey(tenantId: String, baseUrl: String, userId: String): String? {
        val normTenant = normalizeTenant(tenantId).ifEmpty { "default" }
        val normBase = normalizeBaseUrl(baseUrl).ifEmpty { "default" }
        val cleanUser = userId.trim()
        if (cleanUser.isEmpty()) return null
        return LocalMessageOverridePolicy.accountKey(normTenant, normBase, cleanUser)
    }

    fun accountHash(accountKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

/** Pure JVM JWT payload exp decoder (milliseconds since epoch, or null). Never logs tokens. */
object JwtExpiryDecoder {
    fun decodeExpiryEpochMs(jwt: String?): Long? {
        if (jwt.isNullOrBlank()) return null
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        return try {
            val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
            val payload = JSONObject(String(payloadBytes, Charsets.UTF_8))
            val expSec = payload.optLong("exp", 0L)
            if (expSec <= 0L) null else expSec * 1000L
        } catch (_: Exception) {
            null
        }
    }
}

/** Sanitized registry metadata for an authenticated account. Contains zero tokens or secrets. */
data class ApiRegisteredAccountMeta(
    val accountKey: String,
    val accountHash: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarInitials: String = "U",
    val avatarColorHex: String = "#00A884",
    val tenantId: String,
    val baseUrl: String,
    val webSocketTemplate: String = "",
    val isDefaultTenant: Boolean = true,
    val expiresAtEpochMs: Long? = null,
    val lastActiveEpochMs: Long = System.currentTimeMillis(),
    val sessionExpired: Boolean = false,
    val unreadChatsCount: Int = 0,
    val unreadMessagesCount: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("accountKey", accountKey)
        .put("accountHash", accountHash)
        .put("userId", userId)
        .put("username", username)
        .put("displayName", displayName)
        .put("avatarUrl", avatarUrl ?: JSONObject.NULL)
        .put("avatarInitials", avatarInitials)
        .put("avatarColorHex", avatarColorHex)
        .put("tenantId", tenantId)
        .put("baseUrl", baseUrl)
        .put("webSocketTemplate", webSocketTemplate)
        .put("isDefaultTenant", isDefaultTenant)
        .put("expiresAtEpochMs", expiresAtEpochMs ?: JSONObject.NULL)
        .put("lastActiveEpochMs", lastActiveEpochMs)
        .put("sessionExpired", sessionExpired)
        .put("unreadChatsCount", unreadChatsCount)
        .put("unreadMessagesCount", unreadMessagesCount)

    companion object {
        fun fromJson(o: JSONObject): ApiRegisteredAccountMeta? {
            val key = o.optString("accountKey", "").trim()
            val hash = o.optString("accountHash", "").trim()
            val uid = o.optString("userId", "").trim()
            if (key.isEmpty() || hash.isEmpty() || uid.isEmpty()) return null
            return ApiRegisteredAccountMeta(
                accountKey = key,
                accountHash = hash,
                userId = uid,
                username = o.optString("username", uid),
                displayName = o.optString("displayName", o.optString("username", uid)),
                avatarUrl = if (o.isNull("avatarUrl")) null else o.optString("avatarUrl", "").ifBlank { null },
                avatarInitials = o.optString("avatarInitials", uid.take(2).uppercase()).ifBlank { "U" },
                avatarColorHex = o.optString("avatarColorHex", "#00A884").ifBlank { "#00A884" },
                tenantId = o.optString("tenantId", "default"),
                baseUrl = o.optString("baseUrl", ""),
                webSocketTemplate = o.optString("webSocketTemplate", ""),
                isDefaultTenant = o.optBoolean("isDefaultTenant", true),
                expiresAtEpochMs = if (o.isNull("expiresAtEpochMs")) null else o.optLong("expiresAtEpochMs", 0L).takeIf { it > 0L },
                lastActiveEpochMs = o.optLong("lastActiveEpochMs", 0L),
                sessionExpired = o.optBoolean("sessionExpired", false),
                unreadChatsCount = o.optInt("unreadChatsCount", 0).coerceAtLeast(0),
                unreadMessagesCount = o.optInt("unreadMessagesCount", 0).coerceAtLeast(0),
            )
        }
    }
}

/** Per-account encrypted credential payload stored exclusively under its own encrypted key. */
data class ApiAccountCredentialRecord(
    val accessToken: String,
    val refreshToken: String? = null,
    val userJson: String,
    val expiresAtEpochMs: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("accessToken", accessToken)
        .put("refreshToken", refreshToken ?: JSONObject.NULL)
        .put("userJson", userJson)
        .put("expiresAtEpochMs", expiresAtEpochMs ?: JSONObject.NULL)

    companion object {
        fun fromJson(raw: String): ApiAccountCredentialRecord? = try {
            val o = JSONObject(raw)
            val token = o.optString("accessToken", "")
            val userJson = o.optString("userJson", "")
            if (token.isBlank() || userJson.isBlank()) null
            else ApiAccountCredentialRecord(
                accessToken = token,
                refreshToken = if (o.isNull("refreshToken")) null else o.optString("refreshToken", "").ifBlank { null },
                userJson = userJson,
                expiresAtEpochMs = if (o.isNull("expiresAtEpochMs")) null else o.optLong("expiresAtEpochMs", 0L).takeIf { it > 0L },
            )
        } catch (_: Exception) {
            null
        }
    }
}

/** Sanitized per-account recent conversation metadata (stored in per-account encrypted storage, never in plaintext or card semantics). */
data class AccountRecentChatSummary(
    val conversationId: String,
    val title: String,
    val typeName: String = "direct",
    val unreadCount: Int = 0,
    val lastTimestampEpochMs: Long = 0L,
    val avatarUrl: String? = null,
    val avatarInitials: String = "C",
    val avatarColorHex: String = "#00A884",
    val hasDraft: Boolean = false,
    val isMuted: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("conversationId", conversationId)
        .put("title", title)
        .put("typeName", typeName)
        .put("unreadCount", unreadCount)
        .put("lastTimestampEpochMs", lastTimestampEpochMs)
        .put("avatarUrl", avatarUrl ?: JSONObject.NULL)
        .put("avatarInitials", avatarInitials)
        .put("avatarColorHex", avatarColorHex)
        .put("hasDraft", hasDraft)
        .put("isMuted", isMuted)

    companion object {
        fun fromJson(o: JSONObject): AccountRecentChatSummary? {
            val id = o.optString("conversationId", "").trim()
            if (id.isEmpty()) return null
            return AccountRecentChatSummary(
                conversationId = id,
                title = o.optString("title", id).ifBlank { id },
                typeName = o.optString("typeName", "direct"),
                unreadCount = o.optInt("unreadCount", 0).coerceAtLeast(0),
                lastTimestampEpochMs = o.optLong("lastTimestampEpochMs", 0L),
                avatarUrl = if (o.isNull("avatarUrl")) null else o.optString("avatarUrl", "").ifBlank { null },
                avatarInitials = o.optString("avatarInitials", "C"),
                avatarColorHex = o.optString("avatarColorHex", "#00A884"),
                hasDraft = o.optBoolean("hasDraft", false),
                isMuted = o.optBoolean("isMuted", false),
            )
        }
    }
}

data class RecentCardsLayoutState(
    val orderedAccountKeys: List<String> = emptyList(),
    val selectedConversationByAccount: Map<String, String?> = emptyMap(),
    val navContextByAccount: Map<String, String?> = emptyMap(),
) {
    fun toJson(): JSONObject {
        val selectedObj = JSONObject()
        selectedConversationByAccount.forEach { (k, v) ->
            selectedObj.put(k, v ?: JSONObject.NULL)
        }
        val navObj = JSONObject()
        navContextByAccount.forEach { (k, v) ->
            navObj.put(k, v ?: JSONObject.NULL)
        }
        return JSONObject()
            .put("orderedAccountKeys", JSONArray(orderedAccountKeys))
            .put("selectedConversationByAccount", selectedObj)
            .put("navContextByAccount", navObj)
    }

    companion object {
        fun fromJson(raw: String?): RecentCardsLayoutState {
            if (raw.isNullOrBlank()) return RecentCardsLayoutState()
            return try {
                val o = JSONObject(raw)
                val orderArr = o.optJSONArray("orderedAccountKeys") ?: JSONArray()
                val order = buildList {
                    for (i in 0 until orderArr.length()) {
                        val s = orderArr.optString(i, "")
                        if (s.isNotBlank()) add(s)
                    }
                }
                val selObj = o.optJSONObject("selectedConversationByAccount") ?: JSONObject()
                val sel = mutableMapOf<String, String?>()
                selObj.keys().forEach { k ->
                    sel[k] = if (selObj.isNull(k)) null else selObj.optString(k, "").ifBlank { null }
                }
                val navObj = o.optJSONObject("navContextByAccount") ?: JSONObject()
                val nav = mutableMapOf<String, String?>()
                navObj.keys().forEach { k ->
                    nav[k] = if (navObj.isNull(k)) null else navObj.optString(k, "").ifBlank { null }
                }
                RecentCardsLayoutState(order, sel, nav)
            } catch (_: Exception) {
                RecentCardsLayoutState()
            }
        }
    }
}
