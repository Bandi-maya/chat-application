package com.example.chat.ui.split

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val windowSize = rememberAdaptiveWindowSize()
    val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    val presentationMode = SplitChatLayoutPolicy.resolvePresentationMode(
        paneCount = sessionState.count,
        windowWidthDp = windowSize.widthDp,
        windowHeightDp = windowSize.heightDp,
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
            CompositionLocalProvider(LocalChatPaneWidthDp provides 320.dp) {
                paneContent(singleItem, Modifier.fillMaxSize())
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
                ChatyIcon(ChatyIconId.chat, color = colors.accent, semanticLabel = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Split Chat (${sessionState.count})",
                    color = colors.foreground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCloseWorkspace, modifier = Modifier.size(32.dp)) {
                    ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close split chat")
                }
            }

            // Workspace Layout according to Presentation Mode
            when (presentationMode) {
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
                                        IconButton(onClick = { onClosePane(item.conversationId) }, modifier = Modifier.size(16.dp)) {
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
                        CompositionLocalProvider(LocalChatPaneWidthDp provides windowSize.widthDp) {
                            paneContent(activeItem, Modifier.fillMaxSize())
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
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            CompositionLocalProvider(LocalChatPaneWidthDp provides windowSize.widthDp) {
                                paneContent(first, Modifier.fillMaxSize())
                            }
                        }
                        HorizontalDivider(color = colors.borderSubtle, thickness = 2.dp)
                        PaneContainer(
                            item = second,
                            isFocused = second.conversationId == sessionState.focusedConversationId,
                            onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = second.conversationId)) },
                            onClose = { onClosePane(second.conversationId) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            CompositionLocalProvider(LocalChatPaneWidthDp provides windowSize.widthDp) {
                                paneContent(second, Modifier.fillMaxSize())
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
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2)) {
                                    paneContent(p1, Modifier.fillMaxSize())
                                }
                            }
                            VerticalDivider(color = colors.borderSubtle, thickness = 2.dp)
                            PaneContainer(
                                item = p2,
                                isFocused = p2.conversationId == sessionState.focusedConversationId,
                                onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p2.conversationId)) },
                                onClose = { onClosePane(p2.conversationId) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2)) {
                                    paneContent(p2, Modifier.fillMaxSize())
                                }
                            }
                        }
                        HorizontalDivider(color = colors.borderSubtle, thickness = 2.dp)
                        PaneContainer(
                            item = p3,
                            isFocused = p3.conversationId == sessionState.focusedConversationId,
                            onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p3.conversationId)) },
                            onClose = { onClosePane(p3.conversationId) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            CompositionLocalProvider(LocalChatPaneWidthDp provides windowSize.widthDp) {
                                paneContent(p3, Modifier.fillMaxSize())
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
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2)) {
                                    paneContent(p1, Modifier.fillMaxSize())
                                }
                            }
                            VerticalDivider(color = colors.borderSubtle, thickness = 2.dp)
                            PaneContainer(
                                item = p2,
                                isFocused = p2.conversationId == sessionState.focusedConversationId,
                                onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p2.conversationId)) },
                                onClose = { onClosePane(p2.conversationId) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2)) {
                                    paneContent(p2, Modifier.fillMaxSize())
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
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2)) {
                                    paneContent(p3, Modifier.fillMaxSize())
                                }
                            }
                            VerticalDivider(color = colors.borderSubtle, thickness = 2.dp)
                            PaneContainer(
                                item = p4,
                                isFocused = p4.conversationId == sessionState.focusedConversationId,
                                onFocus = { onUpdateSessionState(sessionState.copy(focusedConversationId = p4.conversationId)) },
                                onClose = { onClosePane(p4.conversationId) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                CompositionLocalProvider(LocalChatPaneWidthDp provides (windowSize.widthDp / 2)) {
                                    paneContent(p4, Modifier.fillMaxSize())
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
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
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.title,
                color = colors.foreground,
                fontSize = 12.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(20.dp)) {
                ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close pane")
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

