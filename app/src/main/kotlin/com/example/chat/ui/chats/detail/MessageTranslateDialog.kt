package com.example.chat.ui.chats.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.chat.core.translate.ChatTranslationPolicy
import com.example.chat.data.translate.GTranslateClient
import com.example.chat.ui.kit.ChatyConfirmationDialog
import com.example.chat.ui.kit.ChatyDropdownMenuItem
import com.example.chat.ui.theme.LocalAppColors

/** Chaty #35 / GTranslate: online per-message translation. Source text stays
 *  visible above the result; failures are stated plainly with a retry. */
@Composable
fun MessageTranslateDialog(
    sourceText: String,
    targetLanguage: ChatTranslationPolicy.Language,
    client: GTranslateClient,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val result by produceState<Result<String>?>(initialValue = null, sourceText, targetLanguage.code) {
        value = runCatching { client.translate(sourceText, targetLanguage.code) }
    }
    ChatyConfirmationDialog(
        onDismissRequest = onDismiss,
        title = { Text("Translate to ${targetLanguage.label}") },
        text = {
            Column {
                Text(sourceText, color = colors.foregroundSecondary, fontStyle = FontStyle.Italic)
                Spacer(Modifier.height(12.dp))
                when {
                    result == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 10.dp).height(20.dp), strokeWidth = 2.dp)
                        Text("Translating online…", color = colors.foregroundSecondary)
                    }
                    result!!.isSuccess -> Text(result!!.getOrThrow(), color = colors.foreground)
                    else -> Text(
                        result!!.exceptionOrNull()?.message ?: "Translation failed.",
                        color = colors.foreground,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Fixed GTranslate language set (en/ar/hi/fr/es); the pick persists via the host. */
@Composable
fun TranslatorLanguageDialog(
    current: String,
    onPick: (ChatTranslationPolicy.Language) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    ChatyConfirmationDialog(
        onDismissRequest = onDismiss,
        title = { Text("Translator language") },
        text = {
            Column {
                ChatTranslationPolicy.languages.forEach { language ->
                    ChatyDropdownMenuItem(
                        text = {
                            Text(
                                language.label + if (language.code == current) "  ✓" else "",
                                color = colors.foreground,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDismiss(); onPick(language) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
