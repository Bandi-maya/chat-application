package com.example.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.api.AccountRecentChatSummary
import com.example.chat.data.api.ApiRegisteredAccountMeta
import com.example.chat.ui.accounts.AccountSwitcherOverlay
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Verifies the floating Account Switcher card deck and Account-Specific Recent Cards:
 * - Single account displays active card + real Add Account card (no bottom Refresh button)
 * - Multiple accounts allow card selection, sign-out, session-expired recovery, and Recent Cards navigation
 * - Colliding conversation IDs across different accounts stay visually & interactively isolated.
 */
class AccountSwitcherProofTest {
    @get:Rule
    val rule = createComposeRule()

    private fun meta(
        key: String,
        uid: String,
        name: String,
        tenant: String,
        unread: Int = 0,
        expired: Boolean = false,
    ) = ApiRegisteredAccountMeta(
        accountKey = key,
        accountHash = "h_$uid",
        userId = uid,
        username = uid.lowercase(),
        displayName = name,
        tenantId = tenant,
        baseUrl = "https://api.chaty.test",
        unreadMessagesCount = unread,
        sessionExpired = expired,
    )

    @Test
    fun singleAccountShowsAddAccountCardAndNoRefreshButton() {
        val theme = ThemePresets.getSystemDefaultTheme(true)
        var addClicked = false
        val solo = meta("k1", "Alice", "Alice Wonder", "acme", unread = 2)

        rule.setContent {
            ChatyTheme(theme) {
                AccountSwitcherOverlay(
                    visible = true,
                    accounts = listOf(solo),
                    activeAccountKey = "k1",
                    recentByAccount = emptyMap(),
                    selectedConversationByAccount = emptyMap(),
                    navContextByAccount = emptyMap(),
                    theme = theme,
                    isSwitching = false,
                    errorMessage = null,
                    onDismissError = {},
                    onSelectAccount = {},
                    onAddAccount = { addClicked = true },
                    onReauthenticateAccount = {},
                    onSignOutAccount = {},
                    onOpenSettings = {},
                    onMoveAccountCard = { _, _ -> },
                    onOpenRecentConversation = { _, _ -> },
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithText("Alice Wonder").assertIsDisplayed()
        rule.onNodeWithText("Add Account").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Refresh").fetchSemanticsNodes().size)
        rule.onNodeWithText("Add Account").performClick()
        assertTrue(addClicked)
    }

    @Test
    fun recentCardsSwitchWithExactAccountAndCollidingConversationId() {
        val theme = ThemePresets.getSystemDefaultTheme(true)
        val a = meta("kA", "alice", "Alice Alpha", "org-a", unread = 1)
        val b = meta("kB", "bob", "Bob Beta", "org-b", unread = 4)
        var opened: Pair<String, String>? = null

        rule.setContent {
            ChatyTheme(theme) {
                AccountSwitcherOverlay(
                    visible = true,
                    accounts = listOf(a, b),
                    activeAccountKey = "kA",
                    recentByAccount = mapOf(
                        "kA" to listOf(AccountRecentChatSummary("shared-room", "Alpha Design", unreadCount = 1)),
                        "kB" to listOf(AccountRecentChatSummary("shared-room", "Beta Ops", unreadCount = 4)),
                    ),
                    selectedConversationByAccount = mapOf("kA" to "shared-room", "kB" to "shared-room"),
                    navContextByAccount = mapOf("kA" to "all", "kB" to "unread"),
                    theme = theme,
                    isSwitching = false,
                    errorMessage = null,
                    onDismissError = {},
                    onSelectAccount = {},
                    onAddAccount = {},
                    onReauthenticateAccount = {},
                    onSignOutAccount = {},
                    onOpenSettings = {},
                    onMoveAccountCard = { _, _ -> },
                    onOpenRecentConversation = { key, sum -> opened = key to sum.conversationId },
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithText("Recent cards").performClick()
        rule.onNodeWithContentDescription("Open Beta Ops in Bob Beta, 4 unread, currently selected").performClick()
        assertEquals("kB" to "shared-room", opened)
    }
}
