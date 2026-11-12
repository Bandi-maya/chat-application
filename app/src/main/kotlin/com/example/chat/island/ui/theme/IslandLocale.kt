package com.example.chat.island.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.example.chat.island.core.AppLanguage

@Composable
fun IslandLocale(content: @Composable () -> Unit) {
    val base = LocalContext.current
    remember(base) { AppLanguage.load(base); true }
    val language by AppLanguage.language.collectAsState()
    val configuration = LocalConfiguration.current
    val localized = remember(base, language, configuration) { AppLanguage.wrap(base) }
    CompositionLocalProvider(LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration, content = content)
}
