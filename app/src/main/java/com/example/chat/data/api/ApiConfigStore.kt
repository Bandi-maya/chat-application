package com.example.chat.data.api

import android.content.Context

/** SharedPreferences cache for the server configuration document
 *  (non-secret policy; no credentials here). */
class ApiConfigStore(context: Context) : ApiConfigClient.ConfigCache {
    private val prefs = context.applicationContext.getSharedPreferences("chaty_server_config", Context.MODE_PRIVATE)

    override fun read(): ApiConfigClient.CachedConfig? {
        val etag = prefs.getString("etag", null) ?: return null
        val doc = prefs.getString("document", null) ?: return null
        return ApiConfigClient.CachedConfig(prefs.getLong("version", 0L), etag, doc)
    }

    override fun write(config: ApiConfigClient.CachedConfig) {
        prefs.edit()
            .putLong("version", config.version)
            .putString("etag", config.etag)
            .putString("document", config.document)
            .apply()
    }

    override fun readPending(): ApiConfigClient.CachedConfig? {
        val etag = prefs.getString("pending_etag", null) ?: return null
        val doc = prefs.getString("pending_document", null) ?: return null
        return ApiConfigClient.CachedConfig(prefs.getLong("pending_version", 0L), etag, doc)
    }

    override fun writePending(config: ApiConfigClient.CachedConfig?) {
        val editor = prefs.edit()
        if (config == null) {
            editor.remove("pending_version").remove("pending_etag").remove("pending_document")
        } else {
            editor.putLong("pending_version", config.version)
                .putString("pending_etag", config.etag)
                .putString("pending_document", config.document)
        }
        editor.apply()
    }
}
