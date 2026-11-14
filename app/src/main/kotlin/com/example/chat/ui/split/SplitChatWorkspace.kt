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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
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
    val windowStateHolder = rememberSaveableStateHolder()
    var dockExpanded by rememberSaveable { mutableStateOf(true) }
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
    // Focus handling: BackHandler dismisses focused pane or closes entire workspace
    BackHandler {
        if (sessionState.count > 1 && sessionState.focusedConversationId != null) {
            onClosePane(sessionState.focusedConversationId)
        } else {
            onCloseWorkspace()
        }
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
                IconButton(onClick = { dockExpanded = !dockExpanded }, modifier = Modifier.size(48.dp)) {
                    ChatyIcon(if (dockExpanded) ChatyIconId.chevronDown else ChatyIconId.chevronUp,
                        color = colors.foregroundSecondary,
                        semanticLabel = if (dockExpanded) "Hide minimized chat bubbles" else "Show minimized chat bubbles")
                }
                IconButton(onClick = onCloseWorkspace, modifier = Modifier.size(48.dp)) {
                    ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close split chat")
                }
            }

            // Reuse the existing mini-window component for one or multiple chats.
            if (sessionState.isActive) {
                canvasStateHolder.SaveableStateProvider("free-canvas") {
                    val dockKeys = sessionState.paneItems.map { it.stateKey() }
                    var minimizedKeys by rememberSaveable(sessionState.paneItems.firstOrNull()?.accountNamespace) { mutableStateOf(if (sessionState.count == 1 && sessionState.isMiniWindowMinimized) dockKeys else emptyList<String>()) }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val canvasWidth = maxWidth.value
                        val canvasHeight = maxHeight.value
                        val minimizedItems = sessionState.paneItems.filter { it.stateKey() in minimizedKeys }
                        Box(Modifier.fillMaxSize().padding(bottom = if (minimizedItems.isEmpty() || !dockExpanded) 0.dp else 72.dp)) {
                            sessionState.paneItems.forEachIndexed { index, item ->
                                val paneKey = item.stateKey()
                                key(paneKey) {
                                    if (paneKey !in minimizedKeys) {
                                        windowStateHolder.SaveableStateProvider(paneKey) {
                                        Box(Modifier.fillMaxSize().zIndex(if (sessionState.focusedConversationId == item.conversationId) 10f else index.toFloat())) {
                                            MiniChatWindow(
                                                paneItem = item,
                                                isMinimized = false,
                                                onToggleMinimize = { minimizedKeys = minimizedKeys + paneKey; dockExpanded = true },
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
                        }
                        if (minimizedItems.isNotEmpty() && dockExpanded) {
                            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp)
                                .background(colors.surfaceElevated).horizontalScroll(rememberScrollState()).padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically) {
                                minimizedItems.forEach { item ->
                                    Box(Modifier.size(56.dp).clip(CircleShape).background(colors.surface)
                                        .border(1.dp, colors.accent, CircleShape)
                                        .semantics { contentDescription = "Restore ${item.title}" }
                                        .clickable {
                                            minimizedKeys = minimizedKeys - item.stateKey()
                                            onUpdateSessionState(sessionState.copy(focusedConversationId = item.conversationId))
                                        }, contentAlignment = Alignment.Center) {
                                        Text(item.title.take(2).uppercase(), color = colors.foreground, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
