package com.example.chat.data.translate

import com.example.chat.core.translate.ChatTranslationPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Client-side online translation (GTranslate approach). No Chaty backend
 *  involvement; failures surface honestly to the caller. Results are cached
 *  in memory for the process lifetime, bounded. */
class GTranslateClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun translate(text: String, target: String): String = withContext(Dispatchers.IO) {
        val key = target + "" + text
        cache[key]?.let { return@withContext it }
        val url = ChatTranslationPolicy.endpoint(text, target)
        val request = Request.Builder().url(url).get()
            .header("User-Agent", "Chaty/1.0 (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Translation failed (HTTP ${response.code})")
            val body = response.body?.string() ?: throw IOException("Translation failed (empty response)")
            ChatTranslationPolicy.parseResponse(body)
                ?: throw IOException("Translation failed (unexpected response)")
        }.also { translated ->
            if (cache.size > 400) cache.clear()
            cache[key] = translated
        }
    }
}
