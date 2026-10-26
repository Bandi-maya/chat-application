package com.example.chat.data.local

import android.content.Context

/** User-adjustable limit for the local offline-media store (encrypted bytes on disk). Default 1 GB (user-approved). */
object MediaCacheSettings {
    val OPTIONS_MB: List<Int> = listOf(500, 1024, 2048)
    const val DEFAULT_MB = 1024
    private const val PREFS = "chaty_prefs"
    private const val KEY = "chaty_media_cache_limit_mb"

    fun limitMb(context: Context): Int {
        val v = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, DEFAULT_MB)
        return if (v in OPTIONS_MB) v else DEFAULT_MB
    }

    fun limitBytes(context: Context): Long = limitMb(context) * 1024L * 1024L

    fun setLimitMb(context: Context, mb: Int) {
        if (mb !in OPTIONS_MB) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, mb).apply()
    }
}
