package com.example.chat.island.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLanguage {
    val language = kotlinx.coroutines.flow.MutableStateFlow("")
    fun load(context: Context) { language.value = saved(context) }
    const val SYSTEM = ""
    const val ENGLISH = "en"
    const val HINDI = "hi"

    private fun storage(context: Context) = context.applicationContext
        .getSharedPreferences("chaty_island", Context.MODE_PRIVATE)

    fun saved(ctx: Context): String = storage(ctx).run {
        getString("lang", null).orEmpty()
    }

    fun set(ctx: Context, code: String) {
        val safe = code.takeIf { it in setOf(SYSTEM, ENGLISH, HINDI) } ?: SYSTEM
        storage(ctx).edit().putString("lang", safe).apply()
        language.value = safe
    }

    fun wrap(base: Context): Context = saved(base).let { selection ->
        when (selection) {
            SYSTEM -> base
            else -> {
                val selectedLocale = Locale.forLanguageTag(selection)
                base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                    setLocale(selectedLocale)
                })
            }
        }
    }
}
