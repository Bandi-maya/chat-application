package com.example.chat.ui.camera

import androidx.compose.ui.semantics.selected
import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Exact port of lib/features/camera/lens/lens_carousel.dart.
 *
 *  Snapchat-style horizontally centered lens carousel.
 *
 *  Contract (preserved):
 *  - The item closest to the viewport center IS the selected lens.
 *  - Scale interpolates continuously from each item's true distance to center
 *    while scrolling (far 0.78 -> center 1.00).
 *  - Ballistic motion settles on an item boundary; tapping an off-center item
 *    animates it into the center.
 *  - One light haptic per actual index change.
 *
 *  Native deviations (documented):
 *  - Dart CenterSnapPhysics (spring mass 0.5 / stiffness 320 / ratio 1.05 with
 *    a 0.14 velocity projection) maps to Compose's rememberSnapFlingBehavior,
 *    which also always settles on an item boundary but with its own decay
 *    curve. The settle-on-boundary guarantee is preserved; the exact spring
 *    feel is the platform standard.
 *  - Item extent uses dp (density independent) where Dart used logical px;
 *    identical on Flutter's logical-pixel model. */
@Composable
fun LensCarousel(
    manager: LensManager,
    onSelectionChanged: ((LensDefinition) -> Unit)? = null,
    deviceTier: DeviceCapabilityTier = DeviceCapabilityTier.midRange,
) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val shortestSideDp = min(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
    val selectedDiameter = max(52f, min(76f, shortestSideDp * 0.115f)).dp
    val slotExtent = selectedDiameter + 18.dp
    val slotPx = with(density) { slotExtent.toPx() }
    val viewportWidthDp = configuration.screenWidthDp.dp

    // Lenses the current device cannot run are hidden entirely rather than
    // offered as repeat-failure buttons (requirement 71).
    val catalog = remember(manager, deviceTier) {
        manager.catalog.filter { lens ->
            lens.isNoLens || lens.supportedForDevice(deviceTier.asLensRequirement)
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var currentIndex by remember { mutableIntStateOf(0) }
    var hapticRearmAtMs by remember { mutableLongStateOf(0L) }
    // Bridge the manager's listener model into Compose state.
    var managerSelectedId by remember { mutableStateOf(manager.selected.id) }
    DisposableEffect(manager) {
        val listener = { managerSelectedId = manager.selected.id }
        manager.addListener(listener)
        onDispose { manager.removeListener(listener) }
    }

    // Start centered on the manager's current selection.
    LaunchedEffect(manager, catalog) {
        val index = catalog.indexOfFirst { it.id == manager.selected.id }
        if (index >= 0) {
            currentIndex = index
            listState.scrollToItem(index)
        }
    }

    // Keep visuals in sync when selection changes elsewhere.
    LaunchedEffect(managerSelectedId) {
        val index = catalog.indexOfFirst { it.id == manager.selected.id }
        if (index < 0) return@LaunchedEffect
        currentIndex = index
        listState.animateScrollToItem(index)
    }

    // Selection follows the true scroll position: nearest slot to the offset.
    LaunchedEffect(listState, catalog) {
        snapshotFlow {
            listState.firstVisibleItemIndex * slotPx + listState.firstVisibleItemScrollOffset
        }.collectLatest { offsetPx ->
            val nearest = (offsetPx / slotPx).roundToInt().coerceIn(0, catalog.size - 1)
            if (nearest == currentIndex || catalog.isEmpty()) return@collectLatest
            currentIndex = nearest
            val now = System.currentTimeMillis()
            if (now >= hapticRearmAtMs) {
                ChatyHapticsPort.selection(view)
                hapticRearmAtMs = now + 120
            }
            val lens = catalog[nearest]
            manager.select(lens.id)
            onSelectionChanged?.invoke(lens)
        }
    }

    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        contentPadding = PaddingValues(horizontal = (viewportWidthDp - slotExtent) / 2),
        modifier = Modifier.height(selectedDiameter + 26.dp),
    ) {
        items(catalog.size) { index ->
            val lens = catalog[index]
            val selected = index == currentIndex
            Box(
                modifier = Modifier
                    .size(width = slotExtent, height = selectedDiameter + 26.dp)
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                LensCarouselItem(
                    lens = lens,
                    index = index,
                    selected = selected,
                    diameter = selectedDiameter,
                    slotPx = slotPx,
                    viewportWidthPx = with(density) { viewportWidthDp.toPx() },
                    scrollOffsetPx = listState.firstVisibleItemIndex * slotPx +
                        listState.firstVisibleItemScrollOffset,
                    runtimeState = manager.runtimeState,
                    onTap = {
                        // Dart _centerOn: select, then animate into the center
                        // (240ms easeOutCubic ~ Compose item animation).
                        if (index < catalog.size) {
                            val l = catalog[index]
                            manager.select(l.id)
                            onSelectionChanged?.invoke(l)
                            scope.launch { listState.animateScrollToItem(index) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LensCarouselItem(
    lens: LensDefinition,
    index: Int,
    selected: Boolean,
    diameter: androidx.compose.ui.unit.Dp,
    slotPx: Float,
    viewportWidthPx: Float,
    scrollOffsetPx: Float,
    runtimeState: LensRuntimeState,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    // True distance-to-center interpolation (requirement 10):
    // far 0.78 -> near ~0.90 -> center exactly 1.00, recomputed every frame.
    val itemCenterInViewport = index * slotPx + slotPx * 0.5f - scrollOffsetPx
    val viewportCenter = viewportWidthPx / 2f
    val normalizedDistance =
        abs((itemCenterInViewport - viewportCenter) / slotPx).coerceIn(0f, 1f)
    val scale = 0.78f + 0.22f * (1f - normalizedDistance)
    val sizePx = with(density) { diameter.toPx() } * scale

    Box(
        modifier = Modifier
            .size(with(density) { sizePx.toDp() })
            .pointerInput(index) { detectTapGestures(onTap = { onTap() }) }
            .drawBehind {
                val r = sizePx / 2f
                // accent gradient circle approximation: vertical gradient fill
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(lens.accentColor.copy(alpha = 0.9f), lens.accentColor),
                        start = Offset(0f, 0f),
                        end = Offset(sizePx, sizePx),
                    ),
                    radius = r,
                    center = center,
                )
                drawCircle(
                    color = if (selected) Color.White.copy(alpha = 0.9f) else Color(0x3DFFFFFF),
                    radius = r,
                    center = center,
                    style = Stroke(width = if (selected) 2.4f * density.density else 1f * density.density),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(with(density) { (sizePx * 0.5f).toDp() })) {
            drawLensGlyph(lens.id, Color.White, size.width, size.height)
        }
        if (selected && runtimeState == LensRuntimeState.applying) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.85f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(with(density) { (sizePx + 8 * density.density).toDp() }),
            )
        }
    }
}

/** Exact port of _LensGlyphPainter: original vector-style lens marks painted
 *  in code (48x48 viewport, scaled, stroke width 3, round caps/joins). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLensGlyph(
    lensId: String,
    color: Color,
    width: Float,
    height: Float,
) {
    val scale = min(width, height) / 48f
    withTransform({
        translate((width - 48 * scale) / 2, (height - 48 * scale) / 2)
        scale(scale, scale, Offset.Zero)
    }) {
        val stroke = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (lensId) {
            "lens_glasses_classic" -> {
                drawRoundRect(color, Offset(2f, 14f), androidx.compose.ui.geometry.Size(19f, 16f), androidx.compose.ui.geometry.CornerRadius(6f, 6f), style = stroke)
                drawRoundRect(color, Offset(27f, 14f), androidx.compose.ui.geometry.Size(19f, 16f), androidx.compose.ui.geometry.CornerRadius(6f, 6f), style = stroke)
                drawLine(color, Offset(21f, 19f), Offset(27f, 19f), strokeWidth = 3f, cap = StrokeCap.Round)
                drawLine(color, Offset(2f, 17f), Offset(0f, 13f), strokeWidth = 3f, cap = StrokeCap.Round)
                drawLine(color, Offset(46f, 17f), Offset(48f, 13f), strokeWidth = 3f, cap = StrokeCap.Round)
            }
            "lens_sunglasses_aviator" -> {
                val left = Path().apply {
                    moveTo(2f, 12f); quadraticTo(12f, 8f, 22f, 13f)
                    lineTo(19f, 29f); quadraticTo(11f, 38f, 6f, 28f); close()
                }
                val right = Path().apply {
                    moveTo(26f, 13f); quadraticTo(36f, 8f, 46f, 12f)
                    lineTo(42f, 28f); quadraticTo(37f, 38f, 29f, 29f); close()
                }
                drawPath(left, color); drawPath(right, color)
                drawPath(left, color, style = stroke); drawPath(right, color, style = stroke)
                drawLine(color, Offset(22f, 14f), Offset(26f, 14f), strokeWidth = 3f, cap = StrokeCap.Round)
            }
            "lens_dog_ears" -> {
                val left = Path().apply {
                    moveTo(8f, 25f); quadraticTo(-1f, 3f, 13f, 5f)
                    quadraticTo(23f, 13f, 20f, 27f); close()
                }
                val right = Path().apply {
                    moveTo(28f, 27f); quadraticTo(25f, 11f, 36f, 5f)
                    quadraticTo(50f, 4f, 40f, 26f); close()
                }
                drawPath(left, color); drawPath(right, color)
                drawOval(color, Offset(14f, 24f), androidx.compose.ui.geometry.Size(20f, 15f), style = stroke)
                drawCircle(color, 2f, Offset(24f, 29f))
            }
            "lens_hearts_float" -> {
                drawHeart(Offset(7f, 10f), 0.32f, color)
                drawHeart(Offset(25f, 20f), 0.48f, color)
                drawHeart(Offset(36f, 7f), 0.25f, color)
            }
            "lens_sparkles" -> {
                drawStar(Offset(13f, 17f), 9f, color)
                drawStar(Offset(34f, 11f), 7f, color)
                drawStar(Offset(32f, 33f), 4f, color)
            }
            "lens_angel_halo" -> {
                drawOval(
                    color,
                    Offset(24f - 17f, 10f - 5f),
                    androidx.compose.ui.geometry.Size(34f, 10f),
                    style = stroke,
                )
                drawArc(
                    color,
                    startAngle = (Math.PI * 1.05 * 180.0 / Math.PI).toFloat(),
                    sweepAngle = (Math.PI * 0.9 * 180.0 / Math.PI).toFloat(),
                    useCenter = false,
                    topLeft = Offset(7f, 16f),
                    size = androidx.compose.ui.geometry.Size(34f, 28f),
                    style = stroke,
                )
            }
            "lens_crown_gold", "lens_neon_crown" -> {
                val crown = Path().apply {
                    moveTo(5f, 35f); lineTo(8f, 14f); lineTo(19f, 24f); lineTo(24f, 6f)
                    lineTo(30f, 24f); lineTo(41f, 14f); lineTo(43f, 35f); close()
                }
                drawPath(crown, color, style = stroke)
                drawLine(color, Offset(7f, 39f), Offset(42f, 39f), strokeWidth = 3f, cap = StrokeCap.Round)
            }
            "lens_blush_glow" -> {
                drawCircle(color, 7f, Offset(12f, 26f), style = stroke)
                drawCircle(color, 7f, Offset(36f, 26f), style = stroke)
                drawArc(
                    color,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(18f, 24f),
                    size = androidx.compose.ui.geometry.Size(12f, 10f),
                    style = stroke,
                )
                drawStar(Offset(24f, 10f), 5f, color)
            }
            else -> {
                drawCircle(color, 17f, Offset(24f, 24f), style = stroke)
                drawCircle(color, 5f, Offset(24f, 24f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(
    origin: Offset,
    scale: Float,
    color: Color,
) {
    val path = Path().apply {
        moveTo(origin.x, origin.y + 14 * scale)
        cubicTo(
            origin.x - 24 * scale, origin.y - 1 * scale,
            origin.x - 16 * scale, origin.y - 13 * scale,
            origin.x, origin.y - 6 * scale,
        )
        cubicTo(
            origin.x + 16 * scale, origin.y - 13 * scale,
            origin.x + 24 * scale, origin.y - 1 * scale,
            origin.x, origin.y + 14 * scale,
        )
        close()
    }
    drawPath(path, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color,
) {
    val path = Path()
    for (i in 0 until 8) {
        val angle = -Math.PI / 2 + i * Math.PI / 4
        val r = if (i % 2 == 0) radius else radius * 0.28f
        val x = center.x + (cos(angle) * r).toFloat()
        val y = center.y + (sin(angle) * r).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

