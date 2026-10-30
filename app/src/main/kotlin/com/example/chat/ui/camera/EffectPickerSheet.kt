package com.example.chat.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.theme.LocalAppColors

/** Snapchat-style filter browsing sheet, grown from the exact port of
 *  lib/features/camera/effects/widgets/effect_picker_sheet.dart.
 *
 *  Preserved from the Dart port: sheet container with drag handle, intensity
 *  slider (only when the active effect is not 'none'), scrollable category
 *  tabs, and a horizontal swatch row per tab.
 *
 *  S3 browse-UX additions:
 *  - Recents and Favorites tabs ahead of the category tabs, fed by the
 *    engine's existing recentEffectIds/favoriteEffectIds sets. The engine
 *    keeps those in memory today; persisting them is an EffectEngine-owner
 *    change, not this sheet's.
 *  - Long-press a swatch to favorite/unfavorite it (Snapchat gesture), with a
 *    heart badge on favorited swatches; tap selects, as before. One haptic
 *    per action via ChatyHapticsPort.
 *  - Performance-fallback banner driven by the real engine contract
 *    (isEngineDisabled/restorePerformanceMode) instead of silently showing a
 *    row the engine will ignore.
 *  - Responsive sizing: tight screens (mirrors CameraCaptureBottomControls)
 *    get a shorter sheet and smaller swatches; every touch target stays at
 *    least 48dp.
 *  - Optional [onBrowseLenses] header shortcut so a caller can bridge into
 *    the lens explorer (LensBrowserSheet) without this file knowing the lens
 *    wiring. Existing callers pass nothing and see no new UI.
 *
 *  Seams (unchanged):
 *  - The caller wraps this composable in Compose ModalBottomSheet.
 *  - The engine is passed in by the caller (ServiceLocator wiring is
 *    owner-side).
 *  - ChatyRadius/ChatySpacing tokens are not ported; values inlined as
 *    documented at the use sites (full=999, sm=8, xs=4, xl=24, lg=20,
 *    md=12). */

private val HANDLE_RADIUS = 999.dp // ChatyRadius.full

/** Categories shown as tabs, in the Dart order (All is prepended). */
private val pickerCategories = listOf(
    EffectCategory.beauty,
    EffectCategory.cinematic,
    EffectCategory.retro,
    EffectCategory.film,
    EffectCategory.glow,
    EffectCategory.faceAR,
    EffectCategory.celebration,
    EffectCategory.cyber,
    EffectCategory.background,
)

fun effectCategoryLabel(category: EffectCategory): String = when (category) {
    EffectCategory.beauty -> "Beauty"
    EffectCategory.cinematic -> "Cinematic"
    EffectCategory.retro -> "Retro"
    EffectCategory.film -> "Film"
    EffectCategory.glow -> "Glow"
    EffectCategory.faceAR -> "Face AR"
    EffectCategory.celebration -> "Fun"
    EffectCategory.cyber -> "Cyber"
    EffectCategory.background -> "Background"
    EffectCategory.portrait -> "Portrait"
    EffectCategory.color -> "Color"
}

@Composable
fun EffectPickerSheet(
    engine: EffectEngine,
    modifier: Modifier = Modifier,
    onBrowseLenses: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val tight = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 340 ||
        configuration.screenHeightDp < 680
    val sheetHeight = if (tight) 316.dp else 360.dp
    val swatch = if (tight) 52.dp else 60.dp
    val labelWidth = if (tight) 60.dp else 68.dp

    // ListenableBuilder: recompose whenever the engine notifies.
    var engineTick by remember { mutableIntStateOf(0) }
    DisposableEffect(engine) {
        val listener: () -> Unit = { engineTick++ }
        engine.addListener(listener)
        onDispose { engine.removeListener(listener) }
    }
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .clip(com.example.chat.ui.kit.ChatyModalShape)
            .background(colors.surface)
            .border(
                width = 1.dp,
                color = colors.borderSubtle,
                shape = com.example.chat.ui.kit.ChatyModalShape,
            ),
    ) {
        Spacer(Modifier.height(8.dp)) // ChatySpacing.sm
        // Drag handle: 38x4, foregroundSecondary @ 0.3, fully rounded.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(HANDLE_RADIUS))
                .background(colors.foregroundSecondary.copy(alpha = 0.3f)),
        )
        Spacer(Modifier.height(4.dp)) // ChatySpacing.xs

        @Suppress("UNUSED_EXPRESSION") engineTick

        // Performance fallback: the engine is ignoring non-none filters right
        // now; surface the real state with its real recovery action.
        if (engine.isEngineDisabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Filters paused to keep the camera smooth",
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    ChatyHapticsPort.selection(view)
                    engine.restorePerformanceMode()
                }) {
                    Text("Resume", color = colors.primary, fontSize = 12.sp)
                }
            }
        }

        // Optional bridge into the lens explorer; only rendered when the
        // caller supplies the wiring.
        if (onBrowseLenses != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Filters",
                    color = colors.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W700,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    ChatyHapticsPort.selection(view)
                    onBrowseLenses()
                }) {
                    Text(
                        "Browse lenses",
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                    )
                }
            }
        }

        // Intensity slider (active effect != none).
        if (engine.activeEffect.id != "none") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp), // ChatySpacing.xl
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Intensity",
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                )
                Slider(
                    value = engine.intensity.toFloat(),
                    onValueChange = { engine.setIntensity(it.toDouble()) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(engine.intensity * 100).toInt()}%",
                    color = colors.foreground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Tabs: All + Recents + Favorites + pickerCategories, scrollable.
        val tabCount = 3 + pickerCategories.size
        val activeTab = selectedTab.coerceIn(0, tabCount - 1)
        ScrollableTabRow(
            selectedTabIndex = activeTab,
            contentColor = colors.primary,
            edgePadding = 0.dp,
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("All") },
            )
            Tab(
                selected = activeTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Recents") },
            )
            Tab(
                selected = activeTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Favorites") },
            )
            pickerCategories.forEachIndexed { index, category ->
                Tab(
                    selected = activeTab == index + 3,
                    onClick = { selectedTab = index + 3 },
                    text = { Text(effectCategoryLabel(category)) },
                )
            }
        }

        val favorites = engine.favoriteEffectIds
        val list: List<ChatyCameraEffect> = when (activeTab) {
            0 -> EffectRegistry.allEffects
            1 -> engine.recentEffectIds.mapNotNull { id ->
                EffectRegistry.allEffects.firstOrNull { it.id == id }
            }
            2 -> EffectRegistry.allEffects.filter { it.id in favorites }
            else -> EffectRegistry.getByCategory(pickerCategories[activeTab - 3])
        }

        if (list.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (activeTab == 2) {
                        "No favorites yet - hold a filter to favorite it"
                    } else {
                        "Nothing here yet"
                    },
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), // lg/md
                modifier = Modifier.weight(1f),
            ) {
                items(list.size) { i ->
                    val effect = list[i]
                    val isSelected = engine.activeEffect.id == effect.id
                    val isFavorite = favorites.contains(effect.id)
                    // INTEGRATOR (honest availability): effects with no working renderer
                    // (segmentation bg_* no-ops) — disabled, dimmed, never selectable.
                    val unavailable = effect.colorMatrix == null && effect.shaderKey == null &&
                    !effect.isFaceTrackingRequired && effect.id != "none"
                    val captureOnly = effect.isSegmentationRequired && !unavailable
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(end = 12.dp) // ChatySpacing.md
                            .pointerInput(effect.id) {
                                detectTapGestures(
                                    onTap = {
                                        if (!unavailable) {
                                            ChatyHapticsPort.selection(view)
                                            engine.selectEffect(effect)
                                        }
                                    },
                                    onLongPress = {
                                        if (effect.id != "none") {
                                            ChatyHapticsPort.threshold(view)
                                            engine.toggleFavorite(effect.id)
                                        }
                                    },
                                )
                            }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${effect.name} filter"
                                this.selected = isSelected
                                if (unavailable) stateDescription = "Not available yet" else if (captureOnly) stateDescription = "Applies to the captured photo"
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(swatch)
                                    .clip(CircleShape)
                                    .background(effect.previewColor.copy(alpha = if (unavailable) 0.06f else 0.18f))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) colors.primary else colors.border,
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                EffectGlyph(
                                    id = effect.id,
                                    category = effect.category,
                                    color = when {
                                        unavailable -> colors.disabledForeground
                                        isSelected -> colors.primary
                                        else -> effect.previewColor
                                    },
                                    size = if (tight) 22.dp else 26.dp,
                                )
                            }
                            if (isFavorite) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(colors.surface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            effect.name,
                            color = if (isSelected) colors.primary else colors.foreground,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.W500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(labelWidth),
                        )
                    }
                }
            }
        }
    }
}
