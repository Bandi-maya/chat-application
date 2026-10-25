package com.example.chat.ui.settings.navigation

/** Filter rule ported from settings_search_delegate.dart (api branch anchor
 *  3bee780): query trimmed + lowercased; empty query returns everything;
 *  otherwise contains-match over title, category, description and each
 *  keyword (each lowercased). */
object SettingsSearchLogic {

    fun <T> filter(
        query: String,
        all: List<T>,
        title: (T) -> String,
        category: (T) -> String,
        description: (T) -> String,
        keywords: (T) -> List<String>,
    ): List<T> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return all
        return all.filter { item ->
            title(item).lowercase().contains(clean) ||
                category(item).lowercase().contains(clean) ||
                description(item).lowercase().contains(clean) ||
                keywords(item).any { it.lowercase().contains(clean) }
        }
    }
}
