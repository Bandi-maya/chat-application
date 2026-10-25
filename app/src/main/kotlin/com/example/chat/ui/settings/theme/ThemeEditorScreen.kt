package com.example.chat.ui.settings.theme

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.templates.AppearanceVariantController
import com.example.chat.core.templates.TemplateController
import com.example.chat.core.theme.ChatyThemeManager
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.ImageThemeGenerator
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemeController
import com.example.chat.core.theme.ThemeFormatException
import com.example.chat.core.theme.ThemePresets
import com.example.chat.core.theme.generateFromImageFile
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.DeliveryState
import com.example.chat.ui.chats.detail.ChatMessageBubble
import com.example.chat.ui.core.theme.ThemePreviewCard
import com.example.chat.ui.core.theme.currentSetupLayout
import com.example.chat.ui.settings.ChatySettingsCard
import com.example.chat.ui.settings.ChatySettingsPage
import com.example.chat.ui.settings.ChatySettingsSection
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

/**
 * Port of lib/features/settings/theme_editor_screen.dart ("Theme & Design Studio" editor).
 *
 * Hooks the owner supplies (the Dart screen used file_picker / path_provider / the service locator):
 *  - [pickJsonText]: system file picker for a .json file, returns its text or null if cancelled.
 *  - [pickImageFile]: system image picker, returns a readable local File (copy of the content URI) or null.
 *  - [exportDirectory]: getApplicationDocumentsDirectory().
 *  - templateController / appearance / preferences: the registered services (nullable like Dart's isRegistered).
 * Uncompiled; no pixel check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    themeController: ThemeController,
    preferences: ChatyPreferencesController,
    templateController: TemplateController?,
    appearance: AppearanceVariantController?,
    exportDirectory: File,
    pickJsonText: suspend () -> String?,
    pickImageFile: suspend () -> File?,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var snackColor by remember { mutableStateOf<Color?>(null) }
    var current by remember { mutableStateOf(themeController.globalTheme) }
    var menuOpen by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }
    var resetOpen by remember { mutableStateOf(false) }
    // Template/prefs changes re-resolve currentSetupLayout() in Dart's build; mirror with a listener tick.
    var layoutTick by remember { mutableStateOf(0) }
    DisposableEffect(templateController, preferences) {
        val l: () -> Unit = { layoutTick++ }
        templateController?.addListener(l)
        preferences.addListener(l)
        onDispose { templateController?.removeListener(l); preferences.removeListener(l) }
    }

    fun show(msg: String, color: Color? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackColor = color
            snackbar.showSnackbar(msg)
        }
    }

    fun applyAndSave() {
        themeController.updateThemeConfig(current)
        show("Theme customization saved!", colors.success)
    }

    fun exportTheme() {
        scope.launch {
            try {
                val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ChatyThemeManager.saveThemeToFile(exportDirectory, current)
                }
                show("Theme exported to ${file.name}", colors.success)
            } catch (e: Exception) {
                show("Export failed: $e")
            }
        }
    }

    fun importTheme() {
        scope.launch {
            try {
                val content = pickJsonText() ?: return@launch
                val theme = ChatyThemeManager.validateAndImportTheme(content)
                current = theme
                themeController.updateThemeConfig(theme)
                show("Imported and applied \"${theme.name}\" successfully.", colors.success)
            } catch (e: Exception) {
                show("Import failed: ${if (e is ThemeFormatException) e.message else "Invalid theme JSON"}", colors.error)
            }
        }
    }

    fun generateFromImage() {
        scope.launch {
            try {
                val file = pickImageFile() ?: return@launch
                val generated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    ImageThemeGenerator.generateFromImageFile(file, isDark = current.isDark)
                }
                current = generated
                themeController.updateThemeConfig(generated)
                show("Palette extracted and applied from photo!", colors.success)
            } catch (e: Exception) {
                show("Could not generate a theme from that image. Try another image.")
            }
        }
    }

    fun confirmReset() {
        // Flutter HapticFeedback.mediumImpact() on Android = HapticFeedbackConstants.KEYBOARD_TAP.
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        resetOpen = true
    }

    Box(Modifier.fillMaxSize()) {
    ChatySettingsPage(
        title = "Theme & Design Studio",
        onBack = onBack,
        trailingHeaderWidget = {
            IconButton(onClick = { generateFromImage() }) { Icon(Icons.Outlined.Palette, "Extract from photo", tint = colors.foreground) }
            IconButton(onClick = { guideOpen = true }) { Icon(Icons.Outlined.CloudDownload, "Theme Packs & Download Guide", tint = colors.foreground) }
            IconButton(onClick = { importTheme() }) { Icon(Icons.Outlined.FileDownload, "Import Theme JSON", tint = colors.foreground) }
            IconButton(onClick = { exportTheme() }) { Icon(Icons.Outlined.FileUpload, "Export Theme JSON", tint = colors.foreground) }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More options", tint = colors.foreground) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Save Theme", color = colors.foreground) },
                        leadingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = colors.foreground) },
                        onClick = { menuOpen = false; applyAndSave() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Reset to Defaults", color = Color(0xFFFF5252)) },
                        leadingIcon = { Icon(Icons.Rounded.RestartAlt, null, Modifier.size(20.dp), tint = Color(0xFFFF5252)) },
                        onClick = { menuOpen = false; confirmReset() },
                    )
                }
            }
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                // Live palette preview (two real chat bubbles).
                Column(Modifier.padding(bottom = 12.dp)) {
                    ChatySettingsCard(padding = PaddingValues(12.dp)) {
                        Column {
                            ChatMessageBubble(
                                message = ChatMessage(
                                    id = "prev_1", conversationId = "c1", senderId = "contact_1",
                                    text = "Live theme palette preview",
                                    createdAt = Instant.now().minusSeconds(120),
                                    deliveryState = DeliveryState.read,
                                ),
                                isMe = false,
                                theme = current,
                                onLongPress = {},
                            )
                            Spacer(Modifier.height(8.dp))
                            ChatMessageBubble(
                                message = ChatMessage(
                                    id = "prev_2", conversationId = "c1", senderId = "user_me",
                                    text = "Warm neutral & high-contrast ready!",
                                    createdAt = Instant.now(),
                                    deliveryState = DeliveryState.read,
                                ),
                                isMe = true,
                                theme = current,
                                onLongPress = {},
                            )
                        }
                    }
                }
                ChatySettingsSection(title = "Fine-Tuning & Geometry") {
                    ChatySettingsCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Column {
                            @Composable
                            fun label(text: String) = Text(text, color = colors.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                            label("Bubble Corner Radius (${current.cornerRadius.toInt()}px)")
                            LabeledSlider(
                                label = "${current.cornerRadius.toInt()}px",
                                value = current.cornerRadius.coerceIn(4.0, 28.0).toFloat(),
                                onValueChange = { v -> current = current.copy(cornerRadius = v.toDouble()); themeController.updateThemeConfig(current) },
                                valueRange = 4f..28f, steps = 11,
                            )
                            label("Font Scale (${(current.fontScale * 100).toInt()}%)")
                            LabeledSlider(
                                label = "${(current.fontScale * 100).toInt()}%",
                                value = current.fontScale.coerceIn(0.85, 1.30).toFloat(),
                                onValueChange = { v -> current = current.copy(fontScale = v.toDouble()); themeController.updateThemeConfig(current) },
                                valueRange = 0.85f..1.30f, steps = 8,
                            )
                            label("UI Density (${(current.density * 100).toInt()}%)")
                            LabeledSlider(
                                label = "${(current.density * 100).toInt()}%",
                                value = current.density.coerceIn(0.8, 1.2).toFloat(),
                                onValueChange = { v -> current = current.copy(density = v.toDouble()); themeController.updateThemeConfig(current) },
                                valueRange = 0.8f..1.2f, steps = 7,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tick Indicator Style", Modifier.weight(1f), color = colors.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                var tickMenu by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { tickMenu = true }) {
                                        Text(current.deliveryTickStyle.displayName, color = colors.foreground, fontSize = 13.sp)
                                    }
                                    DropdownMenu(expanded = tickMenu, onDismissRequest = { tickMenu = false }, modifier = Modifier.background(colors.surface)) {
                                        DeliveryIconStyle.values().forEach { style ->
                                            DropdownMenuItem(
                                                text = { Text(style.displayName, color = colors.foreground, fontSize = 13.sp) },
                                                onClick = {
                                                    tickMenu = false
                                                    current = current.copy(deliveryTickStyle = style, tickStyle = style.displayName)
                                                    themeController.updateThemeConfig(current)
                                                    if (preferences.conversation.tickStyle != style.displayName) {
                                                        preferences.updateConversation(
                                                            preferences.conversation.copy(tickStyle = style.displayName),
                                                            logTitle = "Ticks Style",
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Each preview shows that theme's colours on your current layout, including component overrides.",
                    color = colors.foregroundSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(8.dp))

                val light = ThemePresets.all.filter { !it.isDark }
                val dark = ThemePresets.all.filter { it.isDark }
                @Composable
                fun grid(presets: List<ThemeConfig>) {
                    Box(Modifier.padding(12.dp)) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val w = maxWidth
                            val isWide = w >= 600.dp
                            val cols = if (isWide) (if (w >= 900.dp) 3 else 2) else 1
                            // mainAxisExtent = 284 * textScaler.scale(1).clamp(1.0, 1.6)
                            val fontScale = LocalDensity.current.fontScale.coerceIn(1.0f, 1.6f)
                            val layout = remember(layoutTick, current) { currentSetupLayout(templateController, preferences, appearance) }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                presets.chunked(cols).forEach { rowItems ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        rowItems.forEach { preset ->
                                            Box(Modifier.weight(1f).height((284 * fontScale).dp)) {
                                                ThemePreviewCard(
                                                    themeConfig = preset,
                                                    layout = layout,
                                                    isSelected = preset.id == current.id,
                                                    onTap = { current = preset; themeController.updateThemeConfig(preset) },
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                        repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (light.isNotEmpty()) ChatySettingsSection(title = "Light themes (${light.size})") { ChatySettingsCard { grid(light) } }
                if (light.isNotEmpty() && dark.isNotEmpty()) Spacer(Modifier.height(12.dp))
                if (dark.isNotEmpty()) ChatySettingsSection(title = "Dark themes (${dark.size})") { ChatySettingsCard { grid(dark) } }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { confirmReset() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    border = BorderStroke(1.2.dp, Color(0xFFFF5252)),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, tint = Color(0xFFFF5252))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to Default System Theme", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (resetOpen) {
        AlertDialog(
            onDismissRequest = { resetOpen = false },
            title = { Text("Reset Theme to Defaults?") },
            text = {
                Text(
                    "This will restore the factory default theme and clear your custom studio settings. " +
                        "Per-conversation themes are preserved.",
                )
            },
            dismissButton = { TextButton(onClick = { resetOpen = false }) { Text("Cancel") } },
            confirmButton = {
                Button(
                    onClick = {
                        resetOpen = false
                        themeController.resetToDefaults()
                        current = themeController.globalTheme
                        show("Theme reset to system defaults.", colors.success)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                ) { Text("Reset") }
            },
        )
    }

    if (guideOpen) {
        ModalBottomSheet(onDismissRequest = { guideOpen = false }, containerColor = colors.surface) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudDownload, null, Modifier.size(28.dp), tint = colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Theme Packs & Offline Store", color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Chaty includes 23 authentic built-in themes (WhatsApp, iOS, Midnight, AMOLED, Paper, Monochrome, and more). " +
                        "You can also download and import custom community theme files (.json) or extract palettes from wallpapers directly.",
                    color = colors.foregroundSecondary, fontSize = 14.sp, lineHeight = 19.6.sp,
                )
                Spacer(Modifier.height(20.dp))
                Row {
                    OutlinedButton(onClick = { guideOpen = false; importTheme() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Import JSON")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { guideOpen = false; generateFromImage() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Palette, null); Spacer(Modifier.width(8.dp)); Text("Photo Palette")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbar) { data ->
            Snackbar(data, containerColor = snackColor ?: androidx.compose.material3.SnackbarDefaults.color)
        }
    }
    }
}


/** Flutter Slider(label:) shows a value bubble above the thumb while it is dragged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    val colors = LocalAppColors.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    Slider(
        value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps,
        interactionSource = interaction,
        thumb = {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.SliderDefaults.Thumb(interactionSource = interaction)
                if (dragged || pressed) {
                    Box(
                        Modifier.offset(y = (-34).dp)
                            .background(colors.primary, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text(label, color = colors.onPrimary, fontSize = 12.sp) }
                }
            }
        },
    )
}
