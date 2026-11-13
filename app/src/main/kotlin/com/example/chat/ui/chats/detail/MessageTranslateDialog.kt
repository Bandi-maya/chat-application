package com.example.chat.ui.chats.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.kit.ChatyTaskField
import com.example.chat.ui.kit.ChatyTextButton

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

/** Bundled target list and on-device search; translations themselves remain online. */
@Composable
fun TranslatorLanguageDialog(
    current: String,
    onPick: (ChatTranslationPolicy.Language) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { ChatTranslationPolicy.searchLanguages(query) }
    val selected = ChatTranslationPolicy.languages.firstOrNull { it.code == current }
    val shape = RoundedCornerShape(10.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight * 0.80f)
                    .shadow(20.dp, shape).clip(shape).background(colors.surfaceElevated),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Translator language", color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${ChatTranslationPolicy.languages.size} languages · Translations use the internet",
                        color = colors.foregroundSecondary, fontSize = 13.sp)
                    ChatyTaskField(
                        value = query, onValueChange = { query = it }, placeholder = "Search languages", enabled = true,
                        leadingIcon = { ChatyIcon(ChatyIconId.search, semanticLabel = null, color = colors.foregroundSecondary) },
                    )
                    if (query.isNotEmpty()) ChatyTextButton(onClick = { query = "" }, label = "Clear search")
                }
                LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    if (query.isBlank() && selected != null) {
                        item(key = "selected-heading") { LanguageSection("Selected") }
                        item(key = "selected-row") { TranslatorLanguageRow(selected, true) { onPick(selected); onDismiss() } }
                    }
                    item(key = "results-heading") {
                        LanguageSection(if (query.isBlank()) "All languages" else "${results.size} results")
                    }
                    if (results.isEmpty()) item(key = "no-results") {
                        Text("No languages found. Try another name or language code.",
                            Modifier.padding(horizontal = 20.dp, vertical = 24.dp), color = colors.foregroundSecondary)
                    }
                    items(results, key = { it.code }) { language ->
                        TranslatorLanguageRow(language, language.code == current) { onPick(language); onDismiss() }
                    }
                }
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    ChatyTextButton(onClick = onDismiss, label = "Close", icon = Icons.Rounded.Close)
                }
            }
        }
    }
}

@Composable
private fun LanguageSection(label: String) {
    Text(label, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        color = LocalAppColors.current.foregroundSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun TranslatorLanguageRow(language: ChatTranslationPolicy.Language, selected: Boolean, onSelect: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(language.label, color = colors.foreground, fontSize = 15.sp)
            Text("${language.nativeLabel} · ${language.code}", color = colors.foregroundSecondary, fontSize = 12.sp)
        }
        if (selected) ChatyIcon(ChatyIconId.check, semanticLabel = null, color = colors.accent)
    }
}
