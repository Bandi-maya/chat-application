package com.example.chat.ui.settings.appicon

import android.content.Context

/** SharedPreferences-backed AppIconPreferenceStore. Keys are owned by
 *  AppIconController and are verbatim the Dart ones
 *  (chaty_launcher_icon_v1, chaty_brand_source_v1,
 *  chaty_custom_brand_icon_path_v1, chaty_custom_icon_library_v2,
 *  chaty_active_custom_icon_v2).
 *
 *  The backing FILE name is a native-side choice: the Dart app reads the
 *  shared_preferences plugin file ("FlutterSharedPreferences"), which does
 *  not exist in the native Kotlin build, so there is no cross-runtime file
 *  to stay compatible with. */
class SharedPrefsAppIconStore(context: Context) : AppIconPreferenceStore {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        const val FILE = "chaty_launcher_icons"
    }
}
