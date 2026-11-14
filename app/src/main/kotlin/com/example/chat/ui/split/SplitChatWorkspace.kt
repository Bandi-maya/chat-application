package com.example.chat.ui.split

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.kit.LocalChatPaneWidthDp
import com.example.chat.ui.kit.rememberAdaptiveWindowSize
import com.example.chat.ui.theme.LocalAppColors

/**
 * Split Chat Workspace container.
 * Hosts 1..4 conversation panes with isolated state holders and responsive layouts.
 */
@Composable
fun SplitChatWorkspace(
    sessionState: SplitChatSessionState,
    onUpdateSessionState: (SplitChatSessionState) -> Unit,
    onClosePane: (String) -> Unit,
    onCloseWorkspace: () -> Unit,
    onOpenFullChat: (String) -> Unit,
    paneContent: @Composable (SplitPaneItem, Modifier) -> Unit,
) {
    if (!sessionState.isActive) return

    val colors = LocalAppColors.current
    val paneStateHolder = rememberSaveableStateHolder()
    val canvasStateHolder = rememberSaveableStateHolder()
    val paneContentCurrent by rememberUpdatedState(paneContent)
    val contentByPane = remember { mutableMapOf<String, @Composable (SplitPaneItem, Modifier) -> Unit>() }
    val activeKeys = sessionState.paneItems.map { it.stateKey() }.toSet()
    contentByPane.keys.retainAll(activeKeys)
    sessionState.paneItems.forEach { item ->
        val paneKey = item.stateKey()
        contentByPane.getOrPut(paneKey) {
            movableContentOf { currentItem: SplitPaneItem, paneModifier: Modifier ->
                paneStateHolder.SaveableStateProvider(paneKey) {
                    paneContentCurrent(currentItem, paneModifier)
                }
            }
        }
    }
    val renderPane: @Composable (SplitPaneItem, Modifier) -> Unit = { item, modifier ->
        contentByPane.getValue(item.stateKey())(item, modifier)
    }
    val windowSize = rememberAdaptiveWindowSize()
    val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    val presentationMode = SplitChatLayoutPolicy.resolvePresentationMode(
        paneCount = sessionState.count,
        windowWidthDp = windowSize.widthDp.dp,
        windowHeightDp = windowSize.heightDp.dp,
        isImeVisible = isImeVisible,
    )

    // Focus handling: BackHandler dismisses focused pane or closes entire workspace
    BackHandler {
        if (sessionState.count > 1 && sessionState.focusedConversationId != null) {
            onClosePane(sessionState.focusedConversationId)
        } else {
            onCloseWorkspace()
        }
    }

    if (presentationMode == SplitPresentationMode.FLOATING_MINI) {
        val singleItem = sessionState.paneItems.first()
        MiniChatWindow(
            paneItem = singleItem,
            isMinimized = sessionState.isMiniWindowMinimized,
            onToggleMinimize = {
                onUpdateSessionState(sessionState.copy(isMiniWindowMinimized = !sessionState.isMiniWindowMinimized))
            },
            onExpandToFull = {
                onOpenFullChat(singleItem.conversationId)
            },
            onClose = {
                onClosePane(singleItem.conversationId)
            },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalChatPaneWidthDp provides maxWidth.value) {
                    renderPane(singleItem, Modifier.fillMaxSize())
                }
            }
        }
        return
    }

    // Full screen overlay for Multi-Pane Workspace
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Workspace Top Bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatyIcon(ChatyIconId.chats, color = colors.accent, semanticLabel = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Split Chat (${sessionState.count})",
                    color = colors.foreground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onUpdateSessionState(sessionState.copy(freeLayout = !sessionState.freeLayout)) }) {
                    Text(if (sessionState.freeLayout) "Grid" else "Free panes", color = colors.accent)
                }
                IconButton(onClick = onCloseWorkspace, modifier = Modifier.size(48.dp)) {
                    ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close split chat")
                }
            }

            // Free canvas is opt-in and entirely inside Chaty. It requests no system overlay permission.
            if (sessionState.freeLayout) {
                canvasStateHolder.SaveableStateProvider("free-canvas") {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val canvasWidth = maxWidth.value
                        val canvasHeight = maxHeight.value
                        sessionState.paneItems.forEachIndexed { index, item ->
                            val paneKey = item.stateKey()
                            key(paneKey) {
                                var minimized by rememberSaveable(paneKey) { mutableStateOf(false) }
                                Box(Modifier.fillMaxSize().zIndex(if (sessionState.focusedConversationId == item.conversationId) 10f else index.toFloat())) {
                                    MiniChatWindow(
                                        paneItem = item,
                                        isMinimized = minimized,
                                        onToggleMinimize = {
                                            minimized = !minimized
                                            onUpdateSessionState(sessionState.copy(focusedConversationId = item.conversationId))
                                        },
                                        onExpandToFull = { onOpenFullChat(item.conversationId) },
                                        onClose = { onClosePane(item.conversationId) },
                                        onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = item.conversationId)) },
                                        initialXFraction = index / 3f,
                                        initialYFraction = index / 3f,
                                        initialWidthDp = 320f.coerceAtMost(canvasWidth - 24f).coerceAtLeast(1f),
                                        initialHeightDp = (canvasHeight * .7f).coerceIn(240f.coerceAtMost(canvasHeight), canvasHeight.coerceAtLeast(1f)),
                                    ) {
                                        BoxWithConstraints(Modifier.fillMaxSize()) {
                                            CompositionLocalProvider(LocalChatPaneWidthDp provides maxWidth.value) {
                                                renderPane(item, Modifier.fillMaxSize())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else when (presentationMode) {
                SplitPresentationMode.COMPACT_SWITCHER -> {
                    // Tab Bar for Compact Mode
                    ScrollableTabRow(
                        selectedTabIndex = sessionState.paneItems.indexOfFirst { it.conversationId == sessionState.focusedConversationId }.coerceAtLeast(0),
                        containerColor = colors.surface,
                        contentColor = colors.accent,
                        edgePadding = 8.dp,
                    ) {
                        sessionState.paneItems.forEach { item ->
                            val isSelected = item.conversationId == sessionState.focusedConversationId
                            Tab(
                                selected = isSelected,
                                onClick = { onUpdateSessionState(sessionState.copy(focusedConversationId = item.conversationId)) },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        IconButton(onClick = { onClosePane(item.conversationId) }, modifier = Modifier.size(48.dp)) {
                                            ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close tab")
                                        }
                                    }
                                },
                            )
                        }
                    }

                    val activeItem = sessionState.paneItems.firstOrNull { it.conversationId == sessionState.focusedConversationId }
                        ?: sessionState.paneItems.first()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CompositionLocalProvider(LocalChatPaneWidthDp provides windowSize.widthDp.toFloat()) {
                            renderPane(activeItem, Modifier.fillMaxSize())
                        }
                    }
                }

                SplitPresentationMode.TWO_PANE_STACK -> {
                    val first = sessionState.paneItems[0]
                    val second = sessionState.paneItems[1]
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        PaneContainer(
                            item = first,
                            isFocused = first.conversationId == sessionState.focusedConversationId,
                            onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = first.conversationId)) },
                            onClose = { onClosePane(first.conversationId) },
                            onReorder = { direction -> onUpdateSessionState(sessionState.reordered(first.conversationId, direction)) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            CompositionLocalProvider(LocalChatPaneWidthDp provides windowSize.widthDp.toFloat()) {
                                renderPane(first, Modifier.fillMaxSize())
                            }
                        }
                        HorizontalDivider(color = colors.borderSubtle, thickness = 2.dp)
                        PaneContainer(
                            item = second,
                            isFocused = second.conversationId == sessionState.focusedConversationId,
                            onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = second.conversationId)) },
                            onClose = { onClosePane(second.conversationId) },
                            onReorder = { direction -> onUpdateSessionState(sessionState.reordered(second.conversationId, direction)) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            CompositionLocalProvider(LocalChatPaneWidthDp provides windowSize.widthDp.toFloat()) {
                                renderPane(second, Modifier.fillMaxSize())
                            }
                        }
                    }
                }

                SplitPresentationMode.THREE_PANE_GRID -> {
                    val p1 = sessionState.paneItems[0]
                    val p2 = sessionState.paneItems[1]
                    val p3 = sessionState.paneItems[2]
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            PaneContainer(
                                item = p1,
                                isFocused = p1.conversationId == sessionState.focusedConversationId,
                                onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p1.conversationId)) },
                                onClose = { onClosePane(p1.conversationId) },
                            onReorder = { direction -> onUpdateSessionState(sessionState.reordered(p1.conversationId, direction)) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2f)) {
                                    renderPane(p1, Modifier.fillMaxSize())
                                }
                            }
                            VerticalDivider(color = colors.borderSubtle, thickness = 2.dp)
                            PaneContainer(
                                item = p2,
                                isFocused = p2.conversationId == sessionState.focusedConversationId,
                                onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p2.conversationId)) },
                                onClose = { onClosePane(p2.conversationId) },
                            onReorder = { direction -> onUpdateSessionState(sessionState.reordered(p2.conversationId, direction)) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2f)) {
                                    renderPane(p2, Modifier.fillMaxSize())
                                }
                            }
                        }
                        HorizontalDivider(color = colors.borderSubtle, thickness = 2.dp)
                        PaneContainer(
                            item = p3,
                            isFocused = p3.conversationId == sessionState.focusedConversationId,
                            onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p3.conversationId)) },
                            onClose = { onClosePane(p3.conversationId) },
                            onReorder = { direction -> onUpdateSessionState(sessionState.reordered(p3.conversationId, direction)) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            CompositionLocalProvider(LocalChatPaneWidthDp provides windowSize.widthDp.toFloat()) {
                                renderPane(p3, Modifier.fillMaxSize())
                            }
                        }
                    }
                }

                SplitPresentationMode.FOUR_PANE_GRID -> {
                    val p1 = sessionState.paneItems[0]
                    val p2 = sessionState.paneItems[1]
                    val p3 = sessionState.paneItems[2]
                    val p4 = sessionState.paneItems[3]
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            PaneContainer(
                                item = p1,
                                isFocused = p1.conversationId == sessionState.focusedConversationId,
                                onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p1.conversationId)) },
                                onClose = { onClosePane(p1.conversationId) },
                            onReorder = { direction -> onUpdateSessionState(sessionState.reordered(p1.conversationId, direction)) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2f)) {
                                    renderPane(p1, Modifier.fillMaxSize())
                                }
                            }
                            VerticalDivider(color = colors.borderSubtle, thickness = 2.dp)
                            PaneContainer(
                                item = p2,
                                isFocused = p2.conversationId == sessionState.focusedConversationId,
                                onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p2.conversationId)) },
                                onClose = { onClosePane(p2.conversationId) },
                            onReorder = { direction -> onUpdateSessionState(sessionState.reordered(p2.conversationId, direction)) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2f)) {
                                    renderPane(p2, Modifier.fillMaxSize())
                                }
                            }
                        }
                        HorizontalDivider(color = colors.borderSubtle, thickness = 2.dp)
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            PaneContainer(
                                item = p3,
                                isFocused = p3.conversationId == sessionState.focusedConversationId,
                                onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p3.conversationId)) },
                                onClose = { onClosePane(p3.conversationId) },
                            onReorder = { direction -> onUpdateSessionState(sessionState.reordered(p3.conversationId, direction)) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2f)) {
                                    renderPane(p3, Modifier.fillMaxSize())
                                }
                            }
                            VerticalDivider(color = colors.borderSubtle, thickness = 2.dp)
                            PaneContainer(
                                item = p4,
                                isFocused = p4.conversationId == sessionState.focusedConversationId,
                                onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p4.conversationId)) },
                                onClose = { onClosePane(p4.conversationId) },
                            onReorder = { direction -> onUpdateSessionState(sessionState.reordered(p4.conversationId, direction)) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2f)) {
                                    renderPane(p4, Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun PaneContainer(
    item: SplitPaneItem,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onClose: () -> Unit,
    onReorder: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val reorderCurrent by rememberUpdatedState(onReorder)
    val focusCurrent by rememberUpdatedState(onFocus)
    val borderModifier = if (isFocused) {
        Modifier.border(2.dp, colors.accent)
    } else {
        Modifier
    }

    Column(modifier.then(borderModifier).clickable { onFocus() }) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (isFocused) colors.accent.copy(alpha = 0.1f) else colors.surface)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Move pane earlier") { reorderCurrent(-1); true },
                        CustomAccessibilityAction("Move pane later") { reorderCurrent(1); true },
                    )
                }
                .pointerInput(item.conversationId) {
                    var drag = Offset.Zero
                    detectDragGestures(
                        onDragStart = { drag = Offset.Zero; focusCurrent() },
                        onDragEnd = {
                            val movement = if (abs(drag.x) > abs(drag.y)) drag.x else drag.y
                            if (abs(movement) > 48.dp.toPx()) reorderCurrent(if (movement > 0) 1 else -1)
                        },
                        onDrag = { change, amount -> change.consume(); drag += amount },
                    )
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⋮⋮", color = colors.foregroundSecondary, modifier = Modifier.padding(end = 6.dp))
            Text(
                item.title,
                color = colors.foreground,
                fontSize = 12.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close pane")
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

