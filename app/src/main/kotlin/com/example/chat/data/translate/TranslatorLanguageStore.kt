package com.example.chat.data.translate

import android.content.Context
import com.example.chat.core.translate.ChatTranslationPolicy

/** Persists the per-device translator target language. App-level preference,
 *  not account data; independent of the shared preferences controller. */
class TranslatorLanguageStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("chaty_translate", Context.MODE_PRIVATE)

    fun target(): String =
        ChatTranslationPolicy.normalizeTarget(prefs.getString(KEY_TARGET, null))

    fun setTarget(code: String) {
        prefs.edit().putString(KEY_TARGET, ChatTranslationPolicy.normalizeTarget(code)).apply()
    }

    private companion object {
        const val KEY_TARGET = "target_language"
    }
}
