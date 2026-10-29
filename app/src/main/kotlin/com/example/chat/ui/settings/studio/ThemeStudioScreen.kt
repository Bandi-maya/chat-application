package com.example.chat.ui.settings.studio

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemeController
import com.example.chat.core.theme.studio.ThemeStudioLogic
import com.example.chat.domain.models.DeliveryState
import com.example.chat.ui.chats.detail.DeliveryStatusIcon
import com.example.chat.ui.kit.BentoCard
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.kit.MorphingSegmentItem
import com.example.chat.ui.kit.MorphingSegmentedControl
import com.example.chat.ui.kit.SpecularGlassCard
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/**
 * Port of lib/features/settings/theme_studio_screen.dart (api @ 3bee780d):
 * the Theme Studio visual customization suite - live interactive bubble
 * preview with ambient canvas particles, bubble gradient editor, 3D
 * transition picker, particle engine controls, tick-style catalog and
 * theme JSON import/export. Save behavior mirrors _saveAndApplyTheme
 * exactly (theme update, conditional tick sync, effects persistence).
 *
 * Preview dependencies the Settings owner retains (AppliedPairPreview /
 * currentSetupLayout) arrive as the [homePreview] slot; until delivered,
 * the section keeps its Dart title and caption with a neutral frame.
 * File picking and theme-file saving are platform seams supplied by the
 * caller ([pickThemeJson], [saveThemeJson]); ChatyThemeManager handles
 * validation/serialization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioScreen(
    themeController: ThemeController,
    preferencesController: ChatyPreferencesController,
    onBack: () -> Unit,
    onOpenComponentStyles: () -> Unit,
    /** Returns picked theme JSON content, or null when cancelled. */
    pickThemeJson: suspend () -> String?,
    /** Saves the exported JSON, returns the saved file name. Throws on failure. */
    saveThemeJson: suspend (json: String) -> String,
    exportThemeJson: (theme: ThemeConfig) -> String =
        com.example.chat.core.theme.ChatyThemeManager::exportTheme,
    importThemeJson: (json: String) -> ThemeConfig =
        com.example.chat.core.theme.ChatyThemeManager::validateAndImportTheme,
    /** Dart locator reads; nullable like the AppliedPairPreview contract's
     *  currentSetupLayout (null = Dart's isRegistered=false defaults). */
    templateController: com.example.chat.core.templates.TemplateController? = null,
    appearanceVariantController: com.example.chat.core.templates.AppearanceVariantController? = null,
) {
    val theme = LocalThemeConfig.current
    val appColors = LocalAppColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var current by remember { mutableStateOf(themeController.globalTheme) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeParticleEffect by remember {
        mutableStateOf(
            preferencesController.effects.fallingParticleObject.takeIf {
                it in ThemeStudioLogic.PARTICLE_TYPES
            } ?: "Stars",
        )
    }
    var particlesEnabled by remember {
        mutableStateOf(preferencesController.effects.enableFallingParticles)
    }
    var gradientStart by remember { mutableLongStateOf(current.outgoingBubbleColor) }
    var gradientEnd by remember { mutableLongStateOf(current.accentColor) }
    var tickChanged by remember { mutableStateOf(false) }

    // Exact _saveAndApplyTheme.
    fun saveAndApply() {
        ChatyHapticsPort.error(view) // HapticFeedback.heavyImpact (CONTEXT_CLICK)
        val updatedTheme = current.copyWith(
            outgoingBubbleColor = gradientStart,
            accentColor = gradientEnd,
        )
        themeController.updateThemeConfig(updatedTheme)
        if (ThemeStudioLogic.tickSyncNeeded(
                tickChanged,
                preferencesController.conversation.tickStyle,
                updatedTheme.tickStyle,
            )
        ) {
            preferencesController.updateConversation(
                preferencesController.conversation.copy(tickStyle = updatedTheme.tickStyle),
                logTitle = "Ticks Style",
            )
        }
        val fx = preferencesController.effects
        preferencesController.updateEffects(
            fx.copy(
                enableFallingParticles = particlesEnabled,
                fallingParticleObject = activeParticleEffect,
            ),
            logTitle = "Theme Studio Particle Engine",
        )
        scope.launch { snackbar.showSnackbar("Theme Studio configuration applied & saved!") }
    }

    fun exportTheme() {
        ChatyHapticsPort.threshold(view) // HapticFeedback.mediumImpact (KEYBOARD_TAP)
        scope.launch {
            try {
                val name = saveThemeJson(exportThemeJson(current))
                snackbar.showSnackbar("Theme exported to: $name")
            } catch (e: Throwable) {
                snackbar.showSnackbar("Export failed: ${e.message ?: e}")
            }
        }
    }

    fun importTheme() {
        ChatyHapticsPort.light(view)
        scope.launch {
            try {
                val content = pickThemeJson() ?: return@launch
                val imported = importThemeJson(content)
                current = imported
                gradientStart = imported.outgoingBubbleColor
                gradientEnd = imported.accentColor
                snackbar.showSnackbar("Successfully loaded theme: ${imported.name}")
            } catch (e: Throwable) {
                snackbar.showSnackbar("Theme import rejected: ${e.message ?: e}")
            }
        }
    }

    Scaffold(
        containerColor = Color(theme.backgroundColor),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = "Back",
                            tint = Color(theme.primaryTextColor),
                        )
                    }
                },
                title = {
                    Text(
                        "Theme Studio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(theme.primaryTextColor),
                    )
                },
                actions = {
                    IconButton(onClick = { exportTheme() }) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = "Export Theme JSON", tint = Color(theme.primaryTextColor))
                    }
                    IconButton(onClick = { importTheme() }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Import Theme JSON", tint = Color(theme.primaryTextColor))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            // _buildBottomActionBar.
            SpecularGlassCard(
                borderRadius = 20.dp,
                padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .springInteractiveScale()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(gradientStart), Color(gradientEnd))))
                        .clickable { saveAndApply() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Apply & Save Theme",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Morphing segmented tab bar.
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                MorphingSegmentedControl(
                    selectedValue = selectedTab,
                    items = listOf(
                        MorphingSegmentItem(0, "Bubbles", Icons.Outlined.ChatBubbleOutline),
                        MorphingSegmentItem(1, "Particles", Icons.Rounded.AutoAwesome),
                        MorphingSegmentItem(2, "Ticks", Icons.Rounded.DoneAll),
                    ),
                    onSelected = {
                        ChatyHapticsPort.selection(view)
                        selectedTab = it
                    },
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            ) {
                when (selectedTab) {
                    0 -> BubblesTab(
                        current = current,
                        onCurrentChange = { current = it },
                        gradientStart = gradientStart,
                        gradientEnd = gradientEnd,
                        onGradientChange = { s, e -> gradientStart = s; gradientEnd = e },
                    )
                    1 -> ParticlesTab(
                        activeEffect = activeParticleEffect,
                        onEffectChange = { activeParticleEffect = it },
                        enabled = particlesEnabled,
                        onEnabledChange = { particlesEnabled = it },
                    )
                    2 -> TicksTab(
                        current = current,
                        onSelect = { style ->
                            tickChanged = true
                            current = current.copyWith(
                                deliveryTickStyle = style,
                                tickStyle = style.displayName,
                            )
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                HomePreviewSection(
                    palette = themeController.globalTheme,
                    templateController = templateController,
                    preferencesController = preferencesController,
                    appearanceVariantController = appearanceVariantController,
                )
                Spacer(Modifier.height(16.dp))
                // _buildComponentStylesEntry.
                Surface(
                    color = Color(theme.cardColor),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onOpenComponentStyles,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.DashboardCustomize,
                            contentDescription = null,
                            tint = Color(theme.accentColor),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Component styles",
                                color = Color(theme.primaryTextColor),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Navigation, header, composer, overlays, motion and density",
                                color = Color(theme.secondaryTextColor),
                                fontSize = 12.sp,
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color(theme.secondaryTextColor),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BubblesTab(
    current: ThemeConfig,
    onCurrentChange: (ThemeConfig) -> Unit,
    gradientStart: Long,
    gradientEnd: Long,
    onGradientChange: (Long, Long) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val appColors = LocalAppColors.current
    Text("Gradient Colorways", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(theme.primaryTextColor))
    Spacer(Modifier.height(10.dp))
    // Wrap-like preset grid: 3 per row (Dart Wrap spacing 10, tile 105dp).
    val presets = ThemeStudioLogic.GRADIENT_PRESETS
    presets.chunked(3).forEach { rowItems ->
        Row(Modifier.padding(bottom = 10.dp)) {
            for (preset in rowItems) {
                val isSelected = gradientStart == preset.start && gradientEnd == preset.end
                Box(
                    Modifier
                        .width(105.dp)
                        .padding(end = 10.dp)
                        .springInteractiveScale()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(preset.start), Color(preset.end))))
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier,
                        )
                        .clickable { onGradientChange(preset.start, preset.end) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        preset.name,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    BentoCard(
        title = "Bubble Geometry & Font Scale",
        subtitle = "Adjust radius curvature and message typography scale",
        headerLeading = { Icon(Icons.Rounded.RoundedCorner, contentDescription = null) },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Corner Radius", fontSize = 13.sp, color = Color(theme.primaryTextColor))
            Text(
                "${current.cornerRadius.toInt()}px",
                fontWeight = FontWeight.Bold,
                color = Color(theme.primaryTextColor),
            )
        }
        Slider(
            value = current.cornerRadius.coerceIn(ThemeStudioLogic.CORNER_RADIUS_MIN, ThemeStudioLogic.CORNER_RADIUS_MAX).toFloat(),
            onValueChange = { onCurrentChange(current.copyWith(cornerRadius = it.toDouble())) },
            valueRange = ThemeStudioLogic.CORNER_RADIUS_MIN.toFloat()..ThemeStudioLogic.CORNER_RADIUS_MAX.toFloat(),
            steps = ThemeStudioLogic.CORNER_RADIUS_DIVISIONS - 1,
            colors = SliderDefaults.colors(
                activeTrackColor = Color(theme.accentColor),
                inactiveTrackColor = appColors.border,
                thumbColor = Color(theme.accentColor),
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Font Scale Factor", fontSize = 13.sp, color = Color(theme.primaryTextColor))
            Text(
                "${(current.fontScale * 100).toInt()}%",
                fontWeight = FontWeight.Bold,
                color = Color(theme.primaryTextColor),
            )
        }
        Slider(
            value = current.fontScale.coerceIn(ThemeStudioLogic.FONT_SCALE_MIN, ThemeStudioLogic.FONT_SCALE_MAX).toFloat(),
            onValueChange = { onCurrentChange(current.copyWith(fontScale = it.toDouble())) },
            valueRange = ThemeStudioLogic.FONT_SCALE_MIN.toFloat()..ThemeStudioLogic.FONT_SCALE_MAX.toFloat(),
            steps = ThemeStudioLogic.FONT_SCALE_DIVISIONS - 1,
            colors = SliderDefaults.colors(
                activeTrackColor = Color(theme.accentColor),
                inactiveTrackColor = appColors.border,
                thumbColor = Color(theme.accentColor),
            ),
        )
    }
}

@Composable
private fun ParticlesTab(
    activeEffect: String,
    onEffectChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val appColors = LocalAppColors.current
    Text("Falling Particle Effects", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(theme.primaryTextColor))
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Enable falling particles", fontWeight = FontWeight.SemiBold, color = Color(theme.primaryTextColor))
            Text("Apply the selected effect to the configured Home/Chat scope.", fontSize = 12.sp, color = Color(theme.secondaryTextColor))
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
    Spacer(Modifier.height(12.dp))
    ThemeStudioLogic.PARTICLE_TYPES.chunked(2).forEach { rowItems ->
        Row(Modifier.padding(bottom = 10.dp)) {
            for (type in rowItems) {
                val isSelected = activeEffect == type
                Box(
                    Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                        .springInteractiveScale()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(theme.accentColor) else Color(theme.surfaceColor))
                        .border(
                            1.dp,
                            if (isSelected) Color(theme.accentColor) else appColors.border,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onEffectChange(type) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        type,
                        color = if (isSelected) Color.White else Color(theme.primaryTextColor),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Text(
        "Only effects supported by the app runtime are listed. Changes are saved when you tap Apply & Save Theme.",
        fontSize = 12.sp,
        color = appColors.secondaryText,
    )
}

@Composable
private fun TicksTab(current: ThemeConfig, onSelect: (DeliveryIconStyle) -> Unit) {
    val theme = LocalThemeConfig.current
    val appColors = LocalAppColors.current
    // Exact style list from _buildTicksTab.
    val styles = listOf(
        DeliveryIconStyle.rcIos11,
        DeliveryIconStyle.material3Rounded,
        DeliveryIconStyle.bbmV2,
        DeliveryIconStyle.cirCheck,
        DeliveryIconStyle.circlePrint,
        DeliveryIconStyle.greenTick,
        DeliveryIconStyle.wings,
        DeliveryIconStyle.batman,
        DeliveryIconStyle.doubleSword,
        DeliveryIconStyle.glowingDots,
    )
    Text("Message Tick Style Catalog", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(theme.primaryTextColor))
    Spacer(Modifier.height(10.dp))
    // GridView 2 columns, aspect ratio 2.2, spacing 10.
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val itemWidth = (screenWidth - 32 - 10) / 2
    val itemHeight = itemWidth / 2.2
    styles.chunked(2).forEach { rowItems ->
        Row(Modifier.padding(bottom = 10.dp)) {
            for (style in rowItems) {
                val isSelected = current.deliveryTickStyle == style
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(itemWidth.dp)
                        .height(itemHeight.dp)
                        .padding(end = 10.dp)
                        .springInteractiveScale()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) Color(theme.accentColor).copy(alpha = 0.15f) else Color(theme.surfaceColor),
                        )
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) Color(theme.accentColor) else appColors.border,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelect(style) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    DeliveryStatusIcon(
                        style = style,
                        state = DeliveryState.read,
                        unreadColor = appColors.foregroundTertiary,
                        readColor = Color(theme.accentColor),
                        size = 20.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        style.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(theme.accentColor) else Color(theme.primaryTextColor),
                    )
                }
            }
        }
    }
}

/** _buildHomePreview: AppliedPairPreview of the SAVED global theme (not the
 *  in-progress studio edits) on the current setup layout, consuming the
 *  Settings owner's drop-30 preview contract. */
@Composable
private fun HomePreviewSection(
    palette: ThemeConfig,
    templateController: com.example.chat.core.templates.TemplateController?,
    preferencesController: ChatyPreferencesController,
    appearanceVariantController: com.example.chat.core.templates.AppearanceVariantController?,
) {
    val theme = LocalThemeConfig.current
    val layout = com.example.chat.ui.core.theme.currentSetupLayout(
        templateController = templateController,
        preferences = preferencesController,
        appearance = appearanceVariantController,
    )
    Column {
        Text(
            "Home and conversation preview",
            color = Color(theme.primaryTextColor),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        com.example.chat.ui.core.theme.AppliedPairPreview(
            palette = palette,
            layout = layout,
            applyTemplateConversation = false,
        )
        Text(
            "Your current colours on your current layout, including component overrides. Wallpaper and the finer Home UI styling are not drawn.",
            textAlign = TextAlign.Center,
            color = Color(theme.secondaryTextColor),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}
