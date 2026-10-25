package com.example.chat.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/** Exact port of lib/features/camera/effects/widgets/effect_picker_sheet.dart.
 *
 *  Modal-bottom-sheet carousel for browsing, selecting, and adjusting the
 *  effect set: 360dp sheet, drag handle, intensity slider (only when the
 *  active effect is not 'none'), scrollable category tabs (All + 9), and a
 *  horizontal effect row per tab.
 *
 *  Seams:
 *  - showModalBottomSheet -> caller wraps this composable in Compose
 *    ModalBottomSheet (the sheet container itself is ported here, including
 *    its fixed 360dp height and top-rounded surface).
 *  - ChatyRadius/ChatySpacing tokens are not yet ported to the Kotlin
 *    design system (owner/theme area); exact values are inlined from
 *    lib/ui/core/design_system/tokens/app_tokens.dart and documented at
 *    the use sites (xxl=24, full=999, sm=8, xs=4, xl=24, lg=20, md=12).
 *  - locator<EffectEngine>() -> the engine is passed in by the caller
 *    (ServiceLocator wiring is owner-side). */

private val SHEET_RADIUS = 24.dp // ChatyRadius.xxl
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
) {
    val colors = LocalAppColors.current
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
            .height(360.dp)
            .clip(RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS))
            .background(colors.surface)
            .border(
                width = 1.dp,
                color = colors.borderSubtle,
                shape = RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS),
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

        // Intensity slider (active effect != none).
        @Suppress("UNUSED_EXPRESSION") engineTick
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.W600,
                )
                androidx.compose.material3.Slider(
                    value = engine.intensity.toFloat(),
                    onValueChange = { engine.setIntensity(it.toDouble()) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(engine.intensity * 100).toInt()}%",
                    color = colors.foreground,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }

        // Category tabs: All + pickerCategories, scrollable, start-aligned.
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = selectedTab,
            contentColor = colors.primary,
            edgePadding = 0.dp,
        ) {
            androidx.compose.material3.Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("All") },
            )
            pickerCategories.forEachIndexed { index, category ->
                androidx.compose.material3.Tab(
                    selected = selectedTab == index + 1,
                    onClick = { selectedTab = index + 1 },
                    text = { Text(effectCategoryLabel(category)) },
                )
            }
        }

        // Effects row for the active tab (TabBarView -> single visible page).
        val list = if (selectedTab == 0) {
            EffectRegistry.allEffects
        } else {
            EffectRegistry.getByCategory(pickerCategories[selectedTab - 1])
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), // lg/md
            modifier = Modifier.weight(1f),
        ) {
            items(list.size) { i ->
                val effect = list[i]
                val isSelected = engine.activeEffect.id == effect.id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(end = 12.dp) // ChatySpacing.md
                        .clickable { engine.selectEffect(effect) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(effect.previewColor.copy(alpha = 0.18f))
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
                            color = if (isSelected) colors.primary else effect.previewColor,
                            size = 26.dp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        effect.name,
                        color = if (isSelected) colors.primary else colors.foreground,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) {
                            androidx.compose.ui.text.font.FontWeight.Bold
                        } else {
                            androidx.compose.ui.text.font.FontWeight.W500
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(68.dp),
                    )
                }
            }
        }
    }
}
