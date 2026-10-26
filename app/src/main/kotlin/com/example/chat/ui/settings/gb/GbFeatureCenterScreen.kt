package com.example.chat.ui.settings.gb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.preferences.GbFeatureCatalog
import com.example.chat.core.preferences.GbFeatureDefinition
import com.example.chat.core.preferences.GbFeatureKind
import com.example.chat.ui.kit.ChatyBackButton
import kotlinx.coroutines.launch

private fun categoryImage(c: GbFeatureCenterLogic.CategoryIcon): ImageVector = when (c) {
    GbFeatureCenterLogic.CategoryIcon.Privacy -> Icons.Outlined.Shield
    GbFeatureCenterLogic.CategoryIcon.Call -> Icons.Outlined.Call
    GbFeatureCenterLogic.CategoryIcon.Media -> Icons.Outlined.PermMedia
    GbFeatureCenterLogic.CategoryIcon.Status -> Icons.Outlined.AutoStories
    GbFeatureCenterLogic.CategoryIcon.Notification -> Icons.Outlined.Notifications
    GbFeatureCenterLogic.CategoryIcon.Text -> Icons.Rounded.TextFields
    GbFeatureCenterLogic.CategoryIcon.Palette -> Icons.Outlined.Palette
    GbFeatureCenterLogic.CategoryIcon.Composer -> Icons.Rounded.EditNote
    GbFeatureCenterLogic.CategoryIcon.Chat -> Icons.Rounded.ChatBubbleOutline
    GbFeatureCenterLogic.CategoryIcon.Home -> Icons.Outlined.Home
    GbFeatureCenterLogic.CategoryIcon.Navigation -> Icons.Outlined.SpaceDashboard
    GbFeatureCenterLogic.CategoryIcon.Storage -> Icons.Outlined.Storage
    GbFeatureCenterLogic.CategoryIcon.Tune -> Icons.Rounded.Tune
}

/**
 * Port of GbFeatureCenterScreen. Dart's 'mas_key_cleanlog_blocklist' action deletes the current user's rows from the
 * Supabase blocked_users table. This port does NOT touch Supabase: [onClearBlockList] is an owner-wired suspend hook
 * on the custom backend; return false when there is no signed-in user (Dart returns silently), throw on failure
 * (Dart toasts 'Unable to clear block list: $error'). Until wired, that action is a gap.
 * Uses stock Material3 colorScheme like the Dart screen (surfaceContainerLow/primaryContainer...).
 * UNCOMPILED (Compose), no pixel check.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GbFeatureCenterScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    onClearBlockList: suspend () -> Boolean = { false },
) {
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val v = version
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var colorEdit by remember { mutableStateOf<GbFeatureDefinition?>(null) }
    var actionEdit by remember { mutableStateOf<GbFeatureDefinition?>(null) }
    var choiceEdit by remember { mutableStateOf<Pair<GbFeatureDefinition, String>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    fun toast(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
    fun applyBundle(values: Map<String, Any?>, title: String) {
        preferences.updateGbFeatures(values, title)
        toast("$title applied")
    }
    fun runAction(item: GbFeatureDefinition) {
        if (item.key == "clear_logs") {
            preferences.clearPreferenceHistory()
            toast("Local preference history cleared.")
            return
        }
        if (item.key == "mas_key_cleanlog_blocklist") {
            scope.launch {
                try {
                    if (onClearBlockList()) toast("Your block list was cleared.")
                } catch (e: Exception) {
                    toast("Unable to clear block list: $e")
                }
            }
            return
        }
        actionEdit = item
    }

    val definitions = GbFeatureCenterLogic.filter(GbFeatureCatalog.all, query, category)
    val browsingCategories = category == null && query.isEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Box(Modifier.padding(8.dp)) {
                        ChatyBackButton(onPressed = { if (category != null) category = null else onBack() })
                    }
                },
                title = { Text(category ?: "Advanced Features") },
                actions = {
                    IconButton(onClick = { confirmReset = true }) { Icon(Icons.Rounded.RestartAlt, "Reset advanced features") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (browsingCategories) {
                Column(
                    Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth()
                        .background(scheme.primaryContainer.copy(alpha = 0.38f), RoundedCornerShape(20.dp))
                        .border(1.dp, scheme.outlineVariant, RoundedCornerShape(20.dp)).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, null, tint = scheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("${GbFeatureCatalog.all.size} advanced controls", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.W800)
                        Text("${GbFeatureCenterLogic.enabledTotal(preferences.gbFeatures)} enabled", color = scheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Settings are grouped by purpose. Open one category at a time instead of scanning one very long list.",
                        color = scheme.onSurfaceVariant, lineHeight = 21.6.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { applyBundle(GbFeatureCenterLogic.stealthBundle, "Stealth privacy bundle") }) {
                            Icon(Icons.Rounded.VisibilityOff, null); Spacer(Modifier.width(8.dp)); Text("Stealth bundle")
                        }
                        OutlinedButton(onClick = { applyBundle(GbFeatureCenterLogic.standardBundle, "Standard connectivity bundle") }) {
                            Icon(Icons.Rounded.Wifi, null); Spacer(Modifier.width(8.dp)); Text("Standard mode")
                        }
                    }
                }
            }
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it; query = it.trim() },
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth(),
                placeholder = { Text(if (category == null) "Search all advanced settings" else "Search in $category") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isEmpty()) null else ({
                    IconButton(onClick = { searchText = ""; query = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainerLow, unfocusedContainerColor = scheme.surfaceContainerLow,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                ),
            )
            Box(Modifier.weight(1f)) {
                when {
                    browsingCategories -> LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 30.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(GbFeatureCatalog.categories) { cat ->
                            val catItems = GbFeatureCatalog.all.filter { it.category == cat }
                            val enabled = GbFeatureCenterLogic.enabledInCategory(catItems) { k, f -> preferences.gbBool(k, f) }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().clickable { category = cat }.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(42.dp).background(scheme.primaryContainer, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                                        Icon(categoryImage(GbFeatureCenterLogic.categoryIcon(cat)), null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(21.dp))
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(cat, fontWeight = FontWeight.W800, fontSize = 16.sp)
                                        Text("$enabled enabled \u2022 ${catItems.size} settings", fontSize = 14.sp, color = scheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                    definitions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No matching settings") }
                    else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 30.dp)) {
                        items(definitions, key = { it.key + it.category }) { item ->
                            FeatureTile(
                                definition = item, preferences = preferences,
                                onColor = { colorEdit = item }, onAction = { runAction(item) },
                                onChoose = { cur -> if (item.options.isNotEmpty()) choiceEdit = item to cur },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- color dialog (Dart _editColor) ----
    colorEdit?.let { item ->
        val current = (preferences.gbFeatures[item.key] as? Number)?.toLong() ?: 0L
        var text by remember(item.key) { mutableStateOf(GbFeatureCenterLogic.initialColorText(current)) }
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { colorEdit = null },
            title = { Text(item.title) },
            text = {
                Column(Modifier.width(360.dp)) {
                    Text(item.description)
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (color in GbFeatureCenterLogic.colorPresets) {
                            Box(
                                Modifier.size(34.dp).background(Color(color), CircleShape)
                                    .border(1.dp, scheme.outlineVariant, CircleShape)
                                    .clickable { preferences.updateGbFeature(item.key, color); colorEdit = null },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("ARGB hex") }, placeholder = { Text("#FF6366F1") })
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { preferences.updateGbFeature(item.key, 0L); colorEdit = null }) { Text("Theme default") }
                    TextButton(onClick = { colorEdit = null }) { Text("Cancel") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    GbFeatureCenterLogic.parseArgbHex(text)?.let { preferences.updateGbFeature(item.key, it); colorEdit = null }
                }) { Text("Apply") }
            },
        )
    }

    // ---- configuration value dialog (Dart _runAction fallthrough) ----
    actionEdit?.let { item ->
        var input by remember(item.key) { mutableStateOf(preferences.gbString(item.key)) }
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { actionEdit = null },
            title = { Text(item.title) },
            text = {
                Column {
                    Text(item.description)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Configuration value" ) })
                }
            },
            dismissButton = { TextButton(onClick = { actionEdit = null }) { Text("Cancel") } },
            confirmButton = { Button(onClick = { preferences.updateGbFeature(item.key, input.trim()); actionEdit = null }) { Text("Save") } },
        )
    }

    // ---- choice sheet (Dart _FeatureTile._choose) ----
    choiceEdit?.let { (item, current) ->
        com.example.chat.ui.kit.ChatySingleChoiceModal(
            title = "Select ${item.title}", value = current, options = item.options, labelBuilder = { it },
            description = item.description, onDismiss = { choiceEdit = null },
            onApply = { chosen -> preferences.updateGbFeature(item.key, chosen); choiceEdit = null },
        )
    }

    if (confirmReset) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset advanced features?") },
            text = { Text("This resets advanced controls to Chaty defaults. Existing chats and server data are not deleted.") },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            confirmButton = { Button(onClick = { confirmReset = false; preferences.resetGbFeatures() }) { Text("Reset") } },
        )
    }
}

@Composable
private fun FeatureTile(
    definition: GbFeatureDefinition,
    preferences: ChatyPreferencesController,
    onColor: () -> Unit,
    onAction: () -> Unit,
    onChoose: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var onTap: (() -> Unit)? = null
    val trailing: @Composable () -> Unit
    when (definition.kind) {
        GbFeatureKind.toggle -> trailing = {
            Switch(
                checked = preferences.gbBool(definition.key, definition.defaultValue == true),
                onCheckedChange = { preferences.updateGbFeature(definition.key, it) },
            )
        }
        GbFeatureKind.slider -> {
            val value = preferences.gbDouble(definition.key, (definition.defaultValue as? Number)?.toDouble() ?: definition.min)
                .coerceIn(definition.min, definition.max)
            trailing = {
                Row(Modifier.width(132.dp), verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = value.toFloat(), valueRange = definition.min.toFloat()..definition.max.toFloat(),
                        onValueChange = { preferences.updateGbFeature(definition.key, it.toDouble()) }, modifier = Modifier.weight(1f),
                    )
                    Text(GbFeatureCenterLogic.sliderLabel(value), Modifier.width(36.dp), textAlign = TextAlign.End, fontSize = 11.sp)
                }
            }
        }
        GbFeatureKind.choice -> {
            val current = preferences.gbString(definition.key, definition.defaultValue?.toString() ?: "")
            onTap = { onChoose(current) }
            trailing = {
                Text(current, Modifier.width(130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.primary, fontWeight = FontWeight.W700)
            }
        }
        GbFeatureKind.color -> {
            val color = preferences.gbColor(definition.key)
            onTap = onColor
            trailing = {
                Box(
                    Modifier.size(34.dp).background(color?.let { Color(it) } ?: scheme.surfaceContainerHighest, CircleShape)
                        .border(1.dp, scheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (color == null) Icon(Icons.Outlined.Palette, null, modifier = Modifier.size(17.dp)) }
            }
        }
        GbFeatureKind.action -> {
            onTap = onAction
            trailing = { Icon(Icons.Rounded.ChevronRight, null) }
        }
    }
    Card(
        modifier = Modifier.padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().let { m -> onTap?.let { m.clickable(onClick = it) } ?: m }.padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(definition.title, fontWeight = FontWeight.W700, fontSize = 16.sp)
                Text(definition.description, Modifier.padding(top = 3.dp), maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}
