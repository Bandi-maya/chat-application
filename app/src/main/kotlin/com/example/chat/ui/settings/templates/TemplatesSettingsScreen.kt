package com.example.chat.ui.settings.templates

import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.icons.ChatyIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.templates.AppearanceVariantController
import com.example.chat.core.templates.ChatyTemplateDefinition
import com.example.chat.core.templates.ChatyTemplateId
import com.example.chat.core.templates.ChatyTemplateRegistry
import com.example.chat.core.templates.TemplateComponentType
import com.example.chat.core.templates.TemplateController
import com.example.chat.core.templates.TemplateRuntime
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.core.theme.AppliedPairPreview
import com.example.chat.ui.core.theme.appliedTemplateLayout
import com.example.chat.ui.core.theme.currentSetupLayout
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** templates_settings_screen.dart _glyphForComponent mapped to Material
 *  icons (the ChatyGlyph design-system enum has no Kotlin port; owner swaps
 *  this table when the glyph system lands). */
/** Port of _glyphForComponent (Dart ChatyGlyph table). Kotlin has no ChatyGlyph
 *  SVG port; the canonical themed renderer is drop9's ChatyIcon/ChatyIconId, so
 *  components with a semantically matching registry id render through it
 *  (chatBubble->chats, tune->tune, updates->updates, person->profile,
 *  calls->calls). The remaining three have no registry id and render the
 *  canonical TemplateComponentType.iconName Material identity
 *  (dock_rounded/home_outlined/format_list_bulleted_rounded). No silent
 *  fallback remains. */
@Composable
fun ComponentGlyph(
    component: TemplateComponentType,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    when (component) {
        TemplateComponentType.conversation ->
            ChatyIcon(ChatyIconId.chats, modifier = modifier, size = 20.dp, color = tint)
        TemplateComponentType.composer ->
            ChatyIcon(ChatyIconId.tune, modifier = modifier, size = 20.dp, color = tint)
        TemplateComponentType.updates ->
            ChatyIcon(ChatyIconId.updates, modifier = modifier, size = 20.dp, color = tint)
        TemplateComponentType.profile ->
            ChatyIcon(ChatyIconId.profile, modifier = modifier, size = 20.dp, color = tint)
        TemplateComponentType.calls ->
            ChatyIcon(ChatyIconId.calls, modifier = modifier, size = 20.dp, color = tint)
        TemplateComponentType.navigation ->
            Icon(Icons.Rounded.Dock, contentDescription = null, tint = tint, modifier = modifier)
        TemplateComponentType.home ->
            Icon(Icons.Outlined.Home, contentDescription = null, tint = tint, modifier = modifier)
        TemplateComponentType.chatList ->
            Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = null, tint = tint, modifier = modifier)
    }
}

/** Port of features/settings/templates/templates_settings_screen.dart (api
 *  3bee780d): top-level Settings screen for managing structural UI templates
 *  and component-level overrides.
 *  Consumes canonical contracts only: core.templates controller/registry
 *  (drop8/drop30) and ui.core.theme AppliedPairPreview +
 *  currentSetupLayout/appliedTemplateLayout (drop30). No duplicated
 *  canonicals. UNCOMPILED (Compose; owner CI + pixels verify).
 *  Seams (owner wires): [onOpenComponent] pushes the GROUP C
 *  ComponentOverrideScreen; [onNotify] shows snackbars; [palette] comes from
 *  ThemeController.globalTheme; [templateRuntime] is the actual shared
 *  runtime from TemplateRuntimeAdapter (owner supplied - a screen-owned
 *  default would perform parallel shared writes, so none exists). */
@Composable
fun TemplatesSettingsScreen(
    templateController: TemplateController,
    preferences: ChatyPreferencesController?,
    appearance: AppearanceVariantController?,
    palette: ThemeConfig,
    templateRuntime: TemplateRuntime,
    onBack: () -> Unit,
    onOpenComponent: (TemplateComponentType) -> Unit,
    onNotify: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // ListenableBuilder(templateController): rebuild on controller notify.
    var configVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(templateController) {
        val listener: () -> Unit = { configVersion += 1 }
        templateController.addListener(listener)
        onDispose { templateController.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") configVersion

    val config = templateController.config
    val baseTmpl = ChatyTemplateRegistry.get(config.baseTemplate)
    val overridesCount = config.componentOverrides.size

    var query by remember { mutableStateOf("") }
    val matchingTemplates = ChatyTemplateRegistry.all.values.filter { template ->
        query.isEmpty() ||
            template.name.lowercase().contains(query) ||
            template.subtitle.lowercase().contains(query) ||
            template.description.lowercase().contains(query)
    }
    val matchingComponents = TemplateComponentType.values().filter { component ->
        query.isEmpty() ||
            component.title.lowercase().contains(query) ||
            component.description.lowercase().contains(query)
    }

    var importDialogOpen by remember { mutableStateOf(false) }
    var applyCandidate by remember { mutableStateOf<ChatyTemplateDefinition?>(null) }
    var resetDialogOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        // AppBar (Dart: surface background, back button, title, actions).
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatyBackButton(onPressed = onBack)
            Text(
                "Templates",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = colors.foreground,
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(TemplateProfileCodec.encode(templateController.config)))
                    onNotify("Template profile copied. It contains appearance settings only.")
                },
            ) {
                Icon(Icons.Rounded.Upload, contentDescription = "Export template profile", tint = colors.foreground, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { importDialogOpen = true }) {
                Icon(Icons.Rounded.Download, contentDescription = "Import template profile", tint = colors.foreground, modifier = Modifier.size(20.dp))
            }
            if (overridesCount > 0 || config.baseTemplate != ChatyTemplateId.messageFirst) {
                IconButton(onClick = { resetDialogOpen = true }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset All Templates", tint = colors.foreground, modifier = Modifier.size(20.dp))
                }
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp,
                ),
            ) {
                // 1. Current Template Overview Card
                item(key = "current") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceSecondary, RoundedCornerShape(20.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "CURRENT SETUP",
                                modifier = Modifier
                                    .background(colors.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.W800,
                                color = colors.primary,
                                letterSpacing = 0.5.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            if (overridesCount > 0) {
                                Text(
                                    "$overridesCount component override${if (overridesCount == 1) "" else "s"} active",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.W700,
                                    color = colors.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            baseTmpl.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.W800,
                            color = colors.foreground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            baseTmpl.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.foregroundSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        AppliedPairPreview(
                            palette = palette,
                            layout = currentSetupLayout(templateController, preferences, appearance),
                            applyTemplateConversation = false,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Your current setup with your colours, including component overrides. Wallpaper and the finer Home UI styling are not drawn.",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.foregroundSecondary,
                        )
                    }
                }

                item(key = "search") {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.trim().lowercase() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search templates and components") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.foregroundSecondary, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = colors.foregroundSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceSecondary,
                            unfocusedContainerColor = colors.surfaceSecondary,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                        ),
                    )
                }

                // 2. Explore All 6 Full Templates
                item(key = "exploreHeader") {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Explore Templates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W800,
                        color = colors.foreground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Apply an entire visual layout or explore individual components.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.foregroundSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                }

                matchingTemplates.forEach { tmpl ->
                    val isBase = config.baseTemplate == tmpl.id
                    item(key = "tmpl-${tmpl.id.key}") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .background(colors.surfaceSecondary, RoundedCornerShape(20.dp))
                                .border(
                                    width = if (isBase) 2.dp else 1.dp,
                                    color = if (isBase) colors.primary else colors.border,
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .padding(16.dp),
                        ) {
                            Text(
                                tmpl.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.W800,
                                color = colors.foreground,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                tmpl.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.foregroundSecondary,
                            )
                            Spacer(Modifier.height(14.dp))
                            AppliedPairPreview(
                                palette = palette,
                                layout = appliedTemplateLayout(tmpl.id, preferences),
                                applyTemplateConversation = true,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "After Apply Complete Template: this layout with your current colours. Component overrides are cleared. Wallpaper and the finer Home UI styling are not drawn.",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.foregroundSecondary,
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { applyCandidate = tmpl },
                                enabled = !(isBase && overridesCount == 0),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBase) colors.surfaceElevated else colors.primary,
                                    contentColor = if (isBase) colors.foregroundSecondary else Color.White,
                                ),
                            ) {
                                Text(if (isBase && overridesCount == 0) "Currently Used" else "Apply Complete Template")
                            }
                        }
                    }
                }

                // 3. Customize By Component
                item(key = "componentsHeader") {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Customize by Component",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W800,
                        color = colors.foreground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Mix and match components from different templates independently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.foregroundSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                matchingComponents.forEach { component ->
                    val effectiveId = templateController.resolveTemplateFor(component)
                    val isOverridden = config.isOverridden(component)
                    val effectiveTmpl = ChatyTemplateRegistry.get(effectiveId)
                    item(key = "comp-${component.name}") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(colors.surfaceSecondary, RoundedCornerShape(14.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                .clickable { onOpenComponent(component) }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                ComponentGlyph(component, tint = colors.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(
                                    component.title,
                                    fontWeight = FontWeight.W700,
                                    fontSize = 14.sp,
                                    color = colors.foreground,
                                )
                                Text(
                                    "${effectiveTmpl.name} ${if (isOverridden) "• (Customized)" else "• (From base)"}",
                                    fontSize = 12.sp,
                                    color = if (isOverridden) colors.primary else colors.foregroundSecondary,
                                    fontWeight = if (isOverridden) FontWeight.W600 else FontWeight.W400,
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.foregroundSecondary)
                        }
                    }
                }

                if (query.isNotEmpty() && matchingTemplates.isEmpty() && matchingComponents.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Rounded.SearchOff, contentDescription = null, modifier = Modifier.size(34.dp), tint = colors.foregroundSecondary)
                            Spacer(Modifier.height(10.dp))
                            Text("No template or component found", color = colors.foreground, fontWeight = FontWeight.W700)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Try a template name such as Visual Social or search for composer, chat list, profile or calls.",
                                textAlign = TextAlign.Center,
                                color = colors.foregroundSecondary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (importDialogOpen) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importDialogOpen = false },
            title = { Text("Import template profile") },
            text = {
                Column {
                    Text(
                        "Paste a Chaty template profile JSON. Only template and component style choices are imported; chats, account data, credentials and keys are never included.",
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste template profile JSON") },
                        minLines = 4,
                        maxLines = 8,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = {
                                val clip = clipboard.getText()?.text
                                if (clip != null) input = clip
                            },
                        ) {
                            Text("Paste clipboard")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        importDialogOpen = false
                        scope.launch {
                            try {
                                val configuration = TemplateProfileCodec.decode(input)
                                templateController.applyConfiguration(configuration, templateRuntime)
                                onNotify("Template profile imported.")
                            } catch (e: Exception) {
                                onNotify("Import failed. Check the template JSON and try again.")
                            }
                        }
                    },
                ) {
                    Text("Validate & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { importDialogOpen = false }) { Text("Cancel") }
            },
        )
    }

    applyCandidate?.let { tmpl ->
        AlertDialog(
            onDismissRequest = { applyCandidate = null },
            title = { Text("Apply ${tmpl.name}?") },
            text = {
                Text(
                    "This applies the ${tmpl.name} structural layout across all supported components.\n\nYour account data, chats, privacy, security, and color palette are preserved.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        applyCandidate = null
                        scope.launch {
                            try {
                                templateController.applyFullTemplate(tmpl.id, templateRuntime)
                                onNotify("Applied ${tmpl.name} template.")
                            } catch (e: Exception) {
                                // Dart: error.toString().replaceFirst('Bad state: ', '')
                                onNotify(e.message ?: e.toString())
                            }
                        }
                    },
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { applyCandidate = null }) { Text("Cancel") }
            },
        )
    }

    if (resetDialogOpen) {
        AlertDialog(
            onDismissRequest = { resetDialogOpen = false },
            title = { Text("Reset Template Configuration?") },
            text = {
                Text(
                    "This will reset your structural template and all component overrides back to the default Message First layout.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        resetDialogOpen = false
                        scope.launch {
                            try {
                                templateController.resetToDefaults(templateRuntime)
                                onNotify("Templates reset to default")
                            } catch (e: Exception) {
                                onNotify(e.message ?: e.toString())
                            }
                        }
                    },
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { resetDialogOpen = false }) { Text("Cancel") }
            },
        )
    }
}
