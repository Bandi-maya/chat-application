package com.example.chat

import com.example.chat.data.api.ApiRegisteredAccountMeta
import com.example.chat.ui.accounts.AccountRecentsPageKind
import com.example.chat.ui.accounts.AccountRecentsPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * JVM proofs for the recents-style account switcher page model:
 * one page per registered account plus a trailing "+ Add account" page,
 * which is also the only page when no account is registered yet.
 */
class AccountRecentsPagesTest {

    private fun meta(key: String, name: String = key) = ApiRegisteredAccountMeta(
        accountKey = key,
        accountHash = "hash-$key",
        userId = "user-$key",
        username = name,
        displayName = name,
        tenantId = "default",
        baseUrl = "https://example.invalid",
    )

    @Test
    fun `no accounts yields a single Add page`() {
        val pages = AccountRecentsPages.build(emptyList())
        assertEquals(1, pages.size)
        assertEquals(AccountRecentsPageKind.AddAccount, pages[0].kind)
        assertNull(pages[0].account)
    }

    @Test
    fun `every account gets a page and the Add page trails`() {
        val accounts = listOf(meta("a"), meta("b"), meta("c"))
        val pages = AccountRecentsPages.build(accounts)
        assertEquals(4, pages.size)
        accounts.forEachIndexed { index, account ->
            assertEquals(AccountRecentsPageKind.Account, pages[index].kind)
            assertSame(account, pages[index].account)
        }
        assertEquals(AccountRecentsPageKind.AddAccount, pages[3].kind)
        assertNull(pages[3].account)
    }

    @Test
    fun `initial page is the active account`() {
        val accounts = listOf(meta("a"), meta("b"), meta("c"))
        assertEquals(1, AccountRecentsPages.initialPage(accounts, "b"))
    }

    @Test
    fun `initial page falls back to the first page when active key is missing`() {
        val accounts = listOf(meta("a"), meta("b"))
        assertEquals(0, AccountRecentsPages.initialPage(accounts, "unknown"))
        assertEquals(0, AccountRecentsPages.initialPage(accounts, null))
        assertEquals(0, AccountRecentsPages.initialPage(emptyList(), null))
    }
}
