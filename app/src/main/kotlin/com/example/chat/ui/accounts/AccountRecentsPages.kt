package com.example.chat.ui.accounts

import com.example.chat.data.api.ApiRegisteredAccountMeta

/** Page kinds for the recents-style account switcher. */
enum class AccountRecentsPageKind { Account, AddAccount }

data class AccountRecentsPage(
    val kind: AccountRecentsPageKind,
    val account: ApiRegisteredAccountMeta?,
)

/**
 * Pure page-list model for [AccountRecentsOverlay]: one page per registered
 * account plus a trailing "+ Add account" page (also the only page when no
 * account is registered yet). Kept Compose-free so it is JVM-testable.
 */
object AccountRecentsPages {
    fun build(accounts: List<ApiRegisteredAccountMeta>): List<AccountRecentsPage> =
        accounts.map { AccountRecentsPage(AccountRecentsPageKind.Account, it) } +
            (if (accounts.size < com.example.chat.data.api.MultiAccountRegistryCoordinator.DEFAULT_MAX_ACCOUNTS)
                listOf(AccountRecentsPage(AccountRecentsPageKind.AddAccount, null)) else emptyList())

    fun initialPage(accounts: List<ApiRegisteredAccountMeta>, activeAccountKey: String?): Int {
        val idx = accounts.indexOfFirst { it.accountKey == activeAccountKey }
        return if (idx >= 0) idx else 0
    }
}

