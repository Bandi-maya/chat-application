package com.example.chat.ui.split

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.example.chat.domain.models.Conversation

/**
 * Representation of an active pane inside the Split Chat Workspace.
 */
data class SplitPaneItem(
    val conversationId: String,
    val title: String,
    val accountNamespace: String? = null,
    val isGroup: Boolean = false,
)

/**
 * Split Chat presentation layout modes computed adaptively.
 */
enum class SplitPresentationMode {
    FLOATING_MINI,      // 1 chat pane floating in movable window over the underlying screen
    TWO_PANE_STACK,     // 2 chat panes stacked vertically (or side-by-side depending on aspect)
    THREE_PANE_GRID,    // 3 chat panes: 2 on top, 1 wide bottom
    FOUR_PANE_GRID,     // 4 chat panes: 2x2 grid
    COMPACT_SWITCHER,   // Fallback for tight height or small window with tabs
}

/**
 * Persistent state of the active Split Chat session.
 * Stores up to 4 conversation IDs so state survives configuration change.
 */
data class SplitChatSessionState(
    val paneItems: List<SplitPaneItem> = emptyList(),
    val focusedConversationId: String? = null,
    val isMiniWindowMinimized: Boolean = false,
) {
    val count: Int get() = paneItems.size
    val isActive: Boolean get() = paneItems.isNotEmpty()

    companion object {
        val Saver: Saver<SplitChatSessionState, Bundle> = object : Saver<SplitChatSessionState, Bundle> {
            override fun SaverScope.save(value: SplitChatSessionState): Bundle {
                return Bundle().apply {
                    putStringArrayList("conversationIds", ArrayList(value.paneItems.map { it.conversationId }))
                    putStringArrayList("titles", ArrayList(value.paneItems.map { it.title }))
                    putStringArrayList("accounts", ArrayList(value.paneItems.map { it.accountNamespace ?: "" }))
                    putBooleanArray("isGroups", value.paneItems.map { it.isGroup }.toBooleanArray())
                    putString("focusedId", value.focusedConversationId)
                    putBoolean("isMinimized", value.isMiniWindowMinimized)
                }
            }

            override fun restore(value: Bundle): SplitChatSessionState {
                val ids = value.getStringArrayList("conversationIds") ?: return SplitChatSessionState()
                val titles = value.getStringArrayList("titles") ?: ArrayList()
                val accounts = value.getStringArrayList("accounts") ?: ArrayList()
                val isGroups = value.getBooleanArray("isGroups") ?: BooleanArray(ids.size)
                val items = ids.mapIndexed { idx, id ->
                    SplitPaneItem(
                        conversationId = id,
                        title = titles.getOrNull(idx) ?: "Chat",
                        accountNamespace = accounts.getOrNull(idx)?.takeIf { it.isNotEmpty() },
                        isGroup = isGroups.getOrNull(idx) ?: false,
                    )
                }
                val focused = value.getString("focusedId")
                val minimized = value.getBoolean("isMinimized", false)
                return SplitChatSessionState(
                    paneItems = items.take(4),
                    focusedConversationId = focused ?: items.firstOrNull()?.conversationId,
                    isMiniWindowMinimized = minimized,
                )
            }
        }
    }
}

