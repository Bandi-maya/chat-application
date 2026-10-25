package com.example.chat.ui.calls

import android.content.Context

/** SharedPreferences-backed storage for CallPresentationPreferencesStore;
 *  same keys as the Dart shared_preferences writes. */
class AndroidCallPresentationPrefsStorage(context: Context) : CallPresentationPrefsStorage {
    private val prefs = context.applicationContext
        .getSharedPreferences("chaty_call_presentation", Context.MODE_PRIVATE)

    override suspend fun getBool(key: String): Boolean? =
        if (prefs.contains(key)) prefs.getBoolean(key, false) else null

    override suspend fun getString(key: String): String? =
        if (prefs.contains(key)) prefs.getString(key, null) else null

    override suspend fun putBool(key: String, value: Boolean): Boolean =
        prefs.edit().putBoolean(key, value).commit()

    override suspend fun putString(key: String, value: String): Boolean =
        prefs.edit().putString(key, value).commit()
}
