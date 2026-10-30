package com.example.chat.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.theme.LocalAppColors

/** Snapchat-style lens explorer for Chaty's existing lens stack.
 *
 *  The shutter carousel (LensCarousel) stays the quick-switcher; this sheet
 *  is the browsable catalog the 09:32 request asks for: category chips, a
 *  Recents view, a Favorites view, and a grid of every lens in the live
 *  catalog. All state flows through the caller's [LensManager] - the same
 *  instance the carousel uses - so a pick here centers the carousel and a
 *  carousel pick is reflected here. Recents/favorites ride the manager's
 *  existing prefs delegate (LensStringListStore getStringList/setStringList,
 *  keys camera.lens.recents / camera.lens.favorites); no new storage, no
 *  backend.
 *
 *  Contracts honored:
 *  - Tier gating uses the real [LensDefinition.supportedForDevice] contract.
 *    Unsupported lenses stay visible but dimmed, locked and non-selectable
 *    ("Not supported here"), so the user can see what exists without hitting
 *    repeat-failure buttons. The carousel's hide-unsupported behavior is
 *    untouched.
 *  - "none" is selectable (clears the lens) but offers no favorite
 *    affordance, mirroring LensManager's own recents handling.
 *  - Runtime state comes from the manager: the selected tile shows the
 *    applying spinner and an honest "Couldn't load - tap to retry" caption on
 *    failure.
 *  - All tile artwork is the existing in-code glyph painter (drawLensGlyph)
 *    over the lens's accent color - original Chaty artwork only.
 *
 *  Layout: sheet container mirrors EffectPickerSheet (ChatyModalShape,
 *  surface, drag handle). Grid is adaptive (>=92dp cells), grows with the
 *  catalog; every interactive target is at least 48dp (chips, favorite
 *  toggle, card tap areas). Tight screens (mirrors
 *  CameraCaptureBottomControls) get a taller sheet and denser cells. */
@Composable
fun LensBrowserSheet(
    manager: LensManager,
    deviceTier: DeviceCapabilityTier = DeviceCapabilityTier.midRange,
    onLensApplied: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val tight = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 340 ||
        configuration.screenHeightDp < 620
    val sheetHeight = (configuration.screenHeightDp * if (tight) 0.9f else 0.8f).dp

    // LensManager listener model -> Compose recomposition.
    var managerTick by remember { mutableIntStateOf(0) }
    DisposableEffect(manager) {
        val listener: () -> Unit = { managerTick++ }
        manager.addListener(listener)
        onDispose { manager.removeListener(listener) }
    }
    // Active filter: "all", "recents", "favorites" or "category:<enum name>".
    var filter by remember { mutableStateOf("all") }

    @Suppress("UNUSED_EXPRESSION") managerTick
    val catalog = manager.catalog
    val categories = remember(catalog) { catalog.map { it.category }.distinct() }
    val supportedCount = catalog.count { it.supportedForDevice(deviceTier.asLensRequirement) }

    val lenses: List<LensDefinition> = when {
        filter == "all" -> catalog
        filter == "recents" -> manager.recents.mapNotNull { id ->
            catalog.firstOrNull { it.id == id }
        }
        filter == "favorites" -> catalog.filter { it.id in manager.favorites }
        else -> {
            val categoryName = filter.removePrefix("category:")
            catalog.filter { it.category.name == categoryName }
        }
    }

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
        Spacer(Modifier.height(8.dp))
        // Drag handle, same spec as EffectPickerSheet.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.foregroundSecondary.copy(alpha = 0.3f)),
        )
        Spacer(Modifier.height(8.dp))

        // Header: title, live availability count, current selection.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Lenses",
                color = colors.foreground,
                fontSize = 16.sp,
                fontWeight = FontWeight.W700,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "$supportedCount available",
                color = colors.foregroundSecondary,
                fontSize = 11.sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (!manager.selected.isNoLens) {
                Text(
                    "Using: ${manager.selected.name}",
                    color = colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            "Tap to apply - hold a lens to favorite",
            color = colors.foregroundTertiary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(4.dp))

        // Filter chips: All + Recents + Favorites + every category present in
        // the live catalog, in catalog order. 48dp touch targets.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LensFilterChip("All", filter == "all") { filter = "all" }
            LensFilterChip("Recent", filter == "recents") { filter = "recents" }
            LensFilterChip("Favorites", filter == "favorites") { filter = "favorites" }
            categories.forEach { category ->
                LensFilterChip(
                    label = effectCategoryLabel(category),
                    selected = filter == "category:${category.name}",
                ) { filter = "category:${category.name}" }
            }
        }
        Spacer(Modifier.height(6.dp))

        if (lenses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (filter) {
                        "recents" -> "No recently used lenses yet"
                        "favorites" -> "No favorites yet - hold a lens to favorite it"
                        else -> "No lenses here yet"
                    },
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (tight) 92.dp else 104.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(lenses.size) { index ->
                    val lens = lenses[index]
                    LensBrowserTile(
                        lens = lens,
                        supported = lens.supportedForDevice(deviceTier.asLensRequirement),
                        selected = manager.selected.id == lens.id,
                        favorite = manager.favorites.contains(lens.id),
                        runtimeState = if (manager.selected.id == lens.id) {
                            manager.runtimeState
                        } else {
                            LensRuntimeState.none
                        },
                        onSelect = {
                            manager.select(lens.id)
                            onLensApplied?.invoke()
                        },
                        onToggleFavorite = { manager.toggleFavorite(lens.id) },
                    )
                }
            }
        }
    }
}

/** Modal host mirroring EffectPickerHost: present the explorer over the
 *  caller's [LensManager] and close it once a lens is applied. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensBrowserHost(
    manager: LensManager,
    onDismiss: () -> Unit,
    deviceTier: DeviceCapabilityTier = DeviceCapabilityTier.midRange,
) {
    ModalBottomSheet(
        tonalElevation = 0.dp,
        shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LensBrowserSheet(
            manager = manager,
            deviceTier = deviceTier,
            onLensApplied = onDismiss,
        )
    }
}

@Composable
private fun LensFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp) // 48dp touch semantics
            .clickable {
                ChatyHapticsPort.selection(view)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "$label lens filter"
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) colors.primary else colors.inputFill)
                .border(
                    width = 1.dp,
                    color = if (selected) colors.primary else colors.inputBorder,
                    shape = RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                label,
                color = if (selected) colors.onPrimary else colors.foreground,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.W500,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LensBrowserTile(
    lens: LensDefinition,
    supported: Boolean,
    selected: Boolean,
    favorite: Boolean,
    runtimeState: LensRuntimeState,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val medallion = 64.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceSecondary)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.primary else colors.borderSubtle,
                shape = RoundedCornerShape(16.dp),
            )
            .pointerInput(lens.id, supported) {
                detectTapGestures(
                    onTap = {
                        if (supported) {
                            ChatyHapticsPort.selection(view)
                            onSelect()
                        }
                    },
                    onLongPress = {
                        if (supported && !lens.isNoLens) {
                            ChatyHapticsPort.threshold(view)
                            onToggleFavorite()
                        }
                    },
                )
            }
            .semantics(mergeDescendants = true) {
                contentDescription = when {
                    !supported -> "${lens.name} lens, not supported on this device"
                    selected -> "${lens.name} lens, in use"
                    else -> "${lens.name} lens"
                }
                if (!supported) disabled() else this.selected = selected
            }
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(medallion),
            contentAlignment = Alignment.Center,
        ) {
            // Medallion: accent gradient circle with the lens glyph, matching
            // the carousel's visual language.
            Box(
                modifier = Modifier
                    .size(medallion)
                    .alpha(if (supported) 1f else 0.45f)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                lens.accentColor.copy(alpha = 0.9f),
                                lens.accentColor,
                            ),
                        ),
                    )
                    .border(
                        width = if (selected) 2.5.dp else 1.dp,
                        color = if (selected) colors.primary else Color(0x3DFFFFFF),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(medallion * 0.5f)) {
                    drawLensGlyph(lens.id, Color.White, size.width, size.height)
                }
            }
            if (!supported) {
                Box(
                    modifier = Modifier
                        .size(medallion)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (selected && supported && runtimeState == LensRuntimeState.applying) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.85f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(medallion + 6.dp),
                )
            }
            if (supported && !lens.isNoLens) {
                // Favorite toggle on the card corner: 48dp touch target with a
                // smaller visual chip.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .clickable {
                            ChatyHapticsPort.selection(view)
                            onToggleFavorite()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.92f))
                            .border(1.dp, colors.borderSubtle, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (favorite) {
                                "Remove ${lens.name} from favorites"
                            } else {
                                "Add ${lens.name} to favorites"
                            },
                            tint = if (favorite) colors.primary else colors.foregroundSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            lens.name,
            color = when {
                !supported -> colors.disabledForeground
                selected -> colors.primary
                else -> colors.foreground
            },
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.W500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Text(
            when {
                !supported -> "Not supported here"
                selected && runtimeState == LensRuntimeState.failed -> "Couldn't load - tap to retry"
                else -> effectCategoryLabel(lens.category)
            },
            color = if (selected && runtimeState == LensRuntimeState.failed) {
                colors.error
            } else {
                colors.foregroundTertiary
            },
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
