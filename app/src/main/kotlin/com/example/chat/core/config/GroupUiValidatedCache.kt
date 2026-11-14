package com.example.chat.core.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** Account-private validated response only. Cache-Control:no-cache requires revalidation before reuse. */
interface GroupUiValidatedCache {
    data class Entry(val body: String, val etag: String)
    fun read(): Entry?
    fun write(entry: Entry)
}
class EncryptedGroupUiValidatedCache(context: Context, private val accountHash: String) : GroupUiValidatedCache {
    init { require(accountHash.matches(Regex("[a-f0-9]{64}"))) }
    private val prefs by lazy {
        val app = context.applicationContext
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(app, "chaty_group_ui_validated_v1", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    override fun read(): GroupUiValidatedCache.Entry? = runCatching {
        val raw = prefs.getString(accountHash, null) ?: return null
        check(raw.length <= 16_384)
        val row = JSONObject(raw)
        val body = row.getString("body")
        val etag = row.getString("etag")
        check(GroupUiConfigPolicy.parse(body) != null && validEtag(etag))
        GroupUiValidatedCache.Entry(body, etag)
    }.getOrNull()
    override fun write(entry: GroupUiValidatedCache.Entry) {
        require(GroupUiConfigPolicy.parse(entry.body) != null && validEtag(entry.etag))
        check(prefs.edit().putString(accountHash, JSONObject().put("body", entry.body).put("etag", entry.etag).toString()).commit())
    }
    companion object {
        fun validEtag(value: String): Boolean = value.length in 2..256 && value.startsWith('"') && value.endsWith('"') &&
            value.none { it.isISOControl() } && '"' !in value.substring(1, value.length - 1)
    }
}
