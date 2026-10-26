package com.example.chat.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReplyAll
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RuleFolder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Exact port of lib/features/automation/auto_reply_rules_screen.dart.
 *
 *  The screen observes [AutoReplyRuleEngine] exactly like the Dart
 *  ListenableBuilder did: a listener bumps a recompose counter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReplyRulesScreen(
    engine: AutoReplyRuleEngine,
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ListenableBuilder port: recompose whenever the engine notifies.
    var listenTick by remember { mutableIntStateOf(0) }
    DisposableEffect(engine) {
        val listener: () -> Unit = { listenTick++ }
        engine.addListener(listener)
        onDispose { engine.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") listenTick

    var showAddRule by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-Reply Rules") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.Close, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddRule = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New Rule") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        val primary = MaterialTheme.colorScheme.primary
        val isMasterEnabled = engine.isMasterEnabled
        val rules = engine.rules

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = (if (isMasterEnabled) primary else Color.Gray).copy(alpha = 0.15f),
                            shape = CircleShape,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ReplyAll,
                                contentDescription = null,
                                tint = if (isMasterEnabled) primary else Color.Gray,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Auto-Reply Engine", fontWeight = FontWeight.Bold)
                            Text(
                                if (isMasterEnabled)
                                    "Active — incoming messages will trigger configured responses"
                                else
                                    "Disabled — incoming messages are ignored by auto-reply",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = isMasterEnabled,
                            onCheckedChange = { engine.setMasterEnabled(it) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "CONFIGURED RULES (${rules.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    TextButton(onClick = { showAddRule = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Rule")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (rules.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Rounded.RuleFolder,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No Auto-Reply Rules Configured",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap \"Add Rule\" to define keywords and automated responses for incoming chats.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        rule.matchType.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFF607D8B).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        rule.recipientFilter.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF607D8B),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { engine.toggleRule(index, it) },
                                )
                                IconButton(onClick = { engine.deleteRule(index) }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete Rule",
                                        tint = Color(0xFFFF5252),
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))) {
                                        append("Trigger: ")
                                    }
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                        append("\"${rule.keyword}\"")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))) {
                                        append("Response: ")
                                    }
                                    append(rule.responseMessage)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddRule) {
        AddRuleSheet(
            onDismiss = { showAddRule = false },
            onSave = { keyword, response, matchType, recipientFilter ->
                if (keyword.isEmpty() || response.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Keyword and response message cannot be empty.")
                    }
                    false
                } else {
                    engine.addRule(
                        keyword = keyword,
                        responseMessage = response,
                        matchType = matchType,
                        recipientFilter = recipientFilter,
                    )
                    showAddRule = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Auto-reply rule added.")
                    }
                    true
                }
            },
        )
    }
}

/** Port of the add-rule modal bottom sheet from auto_reply_rules_screen.dart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleSheet(
    onDismiss: () -> Unit,
    onSave: (keyword: String, response: String, matchType: String, recipientFilter: String) -> Boolean,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var keyword by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var matchType by remember { mutableStateOf("contains") }
    var recipientFilter by remember { mutableStateOf("all") }
    val primary = MaterialTheme.colorScheme.primary

    ModalBottomSheet(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ReplyAll, contentDescription = null, tint = primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Add Auto-Reply Rule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("Trigger Keyword / Phrase") },
                placeholder = { Text("e.g., hello, status, help") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = response,
                onValueChange = { response = it },
                label = { Text("Automatic Response Message") },
                placeholder = { Text("e.g., Hello! I am currently away. Will reply shortly.") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row {
                SheetDropdown(
                    label = "Match Type",
                    value = matchType,
                    options = listOf(
                        "contains" to "Contains",
                        "exact" to "Exact match",
                        "starts_with" to "Starts with",
                    ),
                    onSelected = { matchType = it },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                SheetDropdown(
                    label = "Apply To",
                    value = recipientFilter,
                    options = listOf(
                        "all" to "All Chats",
                        "direct" to "Direct Only",
                        "group" to "Groups Only",
                    ),
                    onSelected = { recipientFilter = it },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(keyword.trim(), response.trim(), matchType, recipientFilter)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Auto-Reply Rule")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == value }?.second ?: value
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(12.dp),
        )
        ExposedDropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape,
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (optionValue, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelected(optionValue)
                        expanded = false
                    },
                )
            }
        }
    }
}
