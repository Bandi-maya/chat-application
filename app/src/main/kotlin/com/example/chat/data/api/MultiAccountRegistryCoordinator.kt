package com.example.chat.data.api

import com.example.chat.data.demo.DemoMode
import com.example.chat.domain.models.Conversation

/**
 * Pure JVM-testable coordinator managing registered accounts, lossless legacy single-account
 * migration, per-account credentials/drafts/recent-summaries, token refresh outside locks,
 * and independent account removal without disturbing remaining sessions.
 */
class MultiAccountRegistryCoordinator(
    private val store: ApiMultiAccountStore,
    private val maxAccounts: Int = DEFAULT_MAX_ACCOUNTS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()

    fun accounts(): List<ApiRegisteredAccountMeta> = synchronized(lock) {
        val all = store.loadRegistry()
        val order = store.loadLayoutState().orderedAccountKeys
        if (order.isEmpty()) return@synchronized all
        val byKey = all.associateBy { it.accountKey }
        val ordered = order.mapNotNull { byKey[it] }
        val remaining = all.filter { it.accountKey !in order }
        ordered + remaining
    }

    fun activeAccountKey(): String? = synchronized(lock) { store.loadActiveAccountKey() }

    fun activeAccountMeta(): ApiRegisteredAccountMeta? = synchronized(lock) {
        val key = store.loadActiveAccountKey() ?: return@synchronized null
        store.loadRegistry().firstOrNull { it.accountKey == key }
    }

    fun accountByKey(accountKey: String): ApiRegisteredAccountMeta? = synchronized(lock) {
        store.loadRegistry().firstOrNull { it.accountKey == accountKey }
    }

    fun credentialsFor(accountKey: String): ApiAccountCredentialRecord? = synchronized(lock) {
        val meta = store.loadRegistry().firstOrNull { it.accountKey == accountKey } ?: return@synchronized null
        store.loadCredentials(meta.accountHash)
    }

    /** Losslessly migrates an existing single-account install from [legacyStore] without calling
     *  clear() on it or logging the user out. Rejects explicitly cleared stores or DemoMode pairs. */
    fun migrateLegacyIfNeeded(
        legacyStore: ApiSessionStore,
        routing: ApiRoutingSnapshot,
        isDefaultTenant: Boolean,
    ): ApiRegisteredAccountMeta? = synchronized(lock) {
        if (legacyStore.wasExplicitlyCleared()) return@synchronized null
        val token = legacyStore.loadToken()?.takeIf { it.isNotBlank() } ?: return@synchronized null
        val userJson = legacyStore.loadUserJson()?.takeIf { it.isNotBlank() } ?: return@synchronized null
        if (DemoMode.isDemoRestSession(token, userJson)) return@synchronized null
        val user = runCatching { apiUserFromJson(userJson) }.getOrNull() ?: return@synchronized null
        val key = ApiAccountRoutingCanonicalizer.accountKey(routing.tenantId, routing.baseUrl, user.id)
            ?: return@synchronized null

        val existing = store.loadRegistry()
        if (existing.isNotEmpty()) {
            // Reconcile if a test harness seeded a distinct legacy user without wiping registry,
            // only if the legacy credentials don't match the current active key's stored creds.
            val currentActive = store.loadActiveAccountKey()
            val match = existing.firstOrNull { it.accountKey == key }
            if (match != null) return@synchronized match
            val activeMeta = existing.firstOrNull { it.accountKey == currentActive }
            val activeCreds = activeMeta?.let { store.loadCredentials(it.accountHash) }
            if (activeCreds?.accessToken == token) return@synchronized activeMeta
        }

        val authResult = ApiAuthResult(
            token = token,
            user = user,
            refreshToken = legacyStore.loadRefreshToken()?.takeIf { it.isNotBlank() },
        )
        registerAuthenticatedAccount(authResult, routing, isDefaultTenant)
    }

    /** Registers or refreshes metadata+credentials for a real authenticated account and sets it active.
     *  Never persists DemoMode sessions. Enforces [maxAccounts] cap for brand-new accounts. */
    fun registerAuthenticatedAccount(
        authResult: ApiAuthResult,
        routing: ApiRoutingSnapshot,
        isDefaultTenant: Boolean,
    ): ApiRegisteredAccountMeta? = synchronized(lock) {
        val userJson = apiUserToJson(authResult.user)
        if (DemoMode.isDemoRestSession(authResult.token, userJson)) return@synchronized null
        val normTenant = ApiAccountRoutingCanonicalizer.normalizeTenant(routing.tenantId)
            .ifEmpty { BackendRoutingPolicy.DEFAULT_TENANT_ID }
        val normBase = ApiAccountRoutingCanonicalizer.normalizeBaseUrl(routing.baseUrl)
        val key = ApiAccountRoutingCanonicalizer.accountKey(normTenant, normBase, authResult.user.id)
            ?: return@synchronized null
        val hash = ApiAccountRoutingCanonicalizer.accountHash(key)

        val current = store.loadRegistry().toMutableList()
        val existingIdx = current.indexOfFirst { it.accountKey == key }
        if (existingIdx == -1 && current.size >= maxAccounts) {
            return@synchronized null
        }

        val expMs = JwtExpiryDecoder.decodeExpiryEpochMs(authResult.token)
        val now = nowMs()
        val prev = current.getOrNull(existingIdx)
        val meta = ApiRegisteredAccountMeta(
            accountKey = key,
            accountHash = hash,
            userId = authResult.user.id,
            username = authResult.user.username,
            displayName = authResult.user.displayName.ifBlank { authResult.user.username },
            avatarUrl = authResult.user.avatarUrl,
            avatarInitials = authResult.user.avatarInitials.ifBlank {
                authResult.user.displayName.ifBlank { authResult.user.username }.take(2).uppercase()
            },
            avatarColorHex = authResult.user.avatarColorHex,
            tenantId = normTenant,
            baseUrl = normBase,
            webSocketTemplate = routing.webSocketTemplate,
            isDefaultTenant = isDefaultTenant,
            expiresAtEpochMs = expMs,
            lastActiveEpochMs = now,
            sessionExpired = false,
            unreadChatsCount = prev?.unreadChatsCount ?: 0,
            unreadMessagesCount = prev?.unreadMessagesCount ?: 0,
        )

        val credRecord = ApiAccountCredentialRecord(
            accessToken = authResult.token,
            refreshToken = authResult.refreshToken ?: prev?.let { store.loadCredentials(it.accountHash)?.refreshToken },
            userJson = userJson,
            expiresAtEpochMs = expMs,
        )
        store.saveCredentials(hash, credRecord)

        if (existingIdx >= 0) current[existingIdx] = meta else current.add(0, meta)
        store.saveRegistry(current)
        store.saveActiveAccountKey(key)

        val layout = store.loadLayoutState()
        if (key !in layout.orderedAccountKeys) {
            store.saveLayoutState(layout.copy(orderedAccountKeys = listOf(key) + layout.orderedAccountKeys))
        }
        meta
    }

    /** Updates non-secret profile display fields & unread badge summary on an existing registered account. */
    fun updateAccountProfileAndUnread(
        accountKey: String,
        user: ApiUserProfile? = null,
        unreadChatsCount: Int? = null,
        unreadMessagesCount: Int? = null,
    ) = synchronized(lock) {
        val list = store.loadRegistry().toMutableList()
        val idx = list.indexOfFirst { it.accountKey == accountKey }
        if (idx < 0) return@synchronized
        val prev = list[idx]
        list[idx] = prev.copy(
            username = user?.username ?: prev.username,
            displayName = user?.displayName?.ifBlank { user.username } ?: prev.displayName,
            avatarUrl = user?.avatarUrl ?: prev.avatarUrl,
            avatarInitials = user?.avatarInitials ?: prev.avatarInitials,
            avatarColorHex = user?.avatarColorHex ?: prev.avatarColorHex,
            lastActiveEpochMs = nowMs(),
            unreadChatsCount = unreadChatsCount ?: prev.unreadChatsCount,
            unreadMessagesCount = unreadMessagesCount ?: prev.unreadMessagesCount,
        )
        if (user != null) {
            store.loadCredentials(prev.accountHash)?.let { creds ->
                store.saveCredentials(prev.accountHash, creds.copy(userJson = apiUserToJson(user)))
            }
        }
        store.saveRegistry(list)
    }

    fun markActive(accountKey: String) = synchronized(lock) {
        val list = store.loadRegistry().toMutableList()
        val idx = list.indexOfFirst { it.accountKey == accountKey }
        if (idx < 0) return@synchronized
        list[idx] = list[idx].copy(lastActiveEpochMs = nowMs())
        store.saveRegistry(list)
        store.saveActiveAccountKey(accountKey)
    }

    fun markSessionExpired(accountKey: String, expired: Boolean) = synchronized(lock) {
        val list = store.loadRegistry().toMutableList()
        val idx = list.indexOfFirst { it.accountKey == accountKey }
        if (idx < 0) return@synchronized
        list[idx] = list[idx].copy(sessionExpired = expired)
        store.saveRegistry(list)
    }

    /** Refreshes a specific account using [refreshCall] outside any global lock; marks ONLY that
     *  account expired on Rejected, rotates tokens atomically on Ok, preserves others untouched. */
    suspend fun refreshAccountIfNeeded(
        accountKey: String,
        force: Boolean = false,
        marginSeconds: Long = 300L,
        refreshCall: suspend (baseUrl: String, tenantId: String, refreshToken: String) -> ApiAuthClient.RefreshResult,
    ): Boolean {
        val snapshot = synchronized(lock) {
            val meta = store.loadRegistry().firstOrNull { it.accountKey == accountKey } ?: return@synchronized null
            val creds = store.loadCredentials(meta.accountHash) ?: return@synchronized null
            meta to creds
        } ?: return false

        val (meta, creds) = snapshot
        val refreshToken = creds.refreshToken ?: return false
        val now = nowMs()
        val exp = creds.expiresAtEpochMs ?: JwtExpiryDecoder.decodeExpiryEpochMs(creds.accessToken)
        if (!force && exp != null && (exp - now) > marginSeconds * 1000L) {
            return true
        }

        return when (val result = refreshCall(meta.baseUrl, meta.tenantId, refreshToken)) {
            is ApiAuthClient.RefreshResult.Ok -> {
                synchronized(lock) {
                    val rotated = result.refreshToken ?: refreshToken
                    val newExp = JwtExpiryDecoder.decodeExpiryEpochMs(result.accessToken)
                    store.saveCredentials(
                        meta.accountHash,
                        creds.copy(accessToken = result.accessToken, refreshToken = rotated, expiresAtEpochMs = newExp),
                    )
                    val list = store.loadRegistry().toMutableList()
                    val idx = list.indexOfFirst { it.accountKey == accountKey }
                    if (idx >= 0) {
                        list[idx] = list[idx].copy(expiresAtEpochMs = newExp, sessionExpired = false)
                        store.saveRegistry(list)
                    }
                }
                true
            }
            ApiAuthClient.RefreshResult.Rejected -> {
                markSessionExpired(accountKey, true)
                false
            }
            ApiAuthClient.RefreshResult.Unavailable -> !meta.sessionExpired
        }
    }

    /** Independently removes [accountKey] and its secrets. Returns the fallback active account if
     *  the removed account was currently active, or null if no accounts remain. */
    fun removeAccount(accountKey: String): ApiRegisteredAccountMeta? = synchronized(lock) {
        val current = store.loadRegistry().toMutableList()
        val idx = current.indexOfFirst { it.accountKey == accountKey }
        if (idx < 0) return@synchronized activeAccountMeta()
        val removed = current.removeAt(idx)
        store.clearAccountData(removed.accountHash)
        store.saveRegistry(current)

        val prevLayout = store.loadLayoutState()
        store.saveLayoutState(
            prevLayout.copy(
                orderedAccountKeys = prevLayout.orderedAccountKeys.filterNot { it == accountKey },
                selectedConversationByAccount = prevLayout.selectedConversationByAccount - accountKey,
                navContextByAccount = prevLayout.navContextByAccount - accountKey,
            ),
        )

        val wasActive = store.loadActiveAccountKey() == accountKey
        if (!wasActive) {
            val activeKey = store.loadActiveAccountKey()
            return@synchronized current.firstOrNull { it.accountKey == activeKey }
        }

        val fallback = current
            .filter { !it.sessionExpired }
            .maxByOrNull { it.lastActiveEpochMs }
            ?: current.maxByOrNull { it.lastActiveEpochMs }
        store.saveActiveAccountKey(fallback?.accountKey)
        fallback
    }

    fun saveRecentConversations(
        accountKey: String,
        summaries: List<AccountRecentChatSummary>,
    ) = synchronized(lock) {
        val meta = store.loadRegistry().firstOrNull { it.accountKey == accountKey } ?: return@synchronized
        store.saveRecentSummaries(meta.accountHash, summaries.take(MAX_RECENT_CARDS_PER_ACCOUNT))
    }

    fun recentConversationsFor(accountKey: String): List<AccountRecentChatSummary> = synchronized(lock) {
        val meta = store.loadRegistry().firstOrNull { it.accountKey == accountKey } ?: return@synchronized emptyList()
        store.loadRecentSummaries(meta.accountHash)
    }

    fun orderedAccountKeys(): List<String> = synchronized(lock) {
        val regKeys = store.loadRegistry().map { it.accountKey }
        val order = store.loadLayoutState().orderedAccountKeys.filter { it in regKeys }
        val extras = regKeys.filter { it !in order }
        order + extras
    }

    fun updateRecentCardOrder(orderedKeys: List<String>) = synchronized(lock) {
        val regKeys = store.loadRegistry().map { it.accountKey }.toSet()
        val cleaned = orderedKeys.filter { it in regKeys }.distinct()
        val prev = store.loadLayoutState()
        store.saveLayoutState(prev.copy(orderedAccountKeys = cleaned))
    }

    fun setAccountNavigationState(
        accountKey: String,
        selectedConversationId: String? = null,
        navContext: String? = null,
    ) = synchronized(lock) {
        val prev = store.loadLayoutState()
        val updatedSel = if (selectedConversationId != null) {
            prev.selectedConversationByAccount + (accountKey to selectedConversationId.ifBlank { null })
        } else {
            prev.selectedConversationByAccount
        }
        val updatedNav = if (navContext != null) {
            prev.navContextByAccount + (accountKey to navContext.ifBlank { null })
        } else {
            prev.navContextByAccount
        }
        store.saveLayoutState(prev.copy(selectedConversationByAccount = updatedSel, navContextByAccount = updatedNav))
    }

    fun selectedConversationFor(accountKey: String): String? = synchronized(lock) {
        store.loadLayoutState().selectedConversationByAccount[accountKey]
    }

    fun navContextFor(accountKey: String): String? = synchronized(lock) {
        store.loadLayoutState().navContextByAccount[accountKey]
    }

    fun saveDraft(accountKey: String, conversationId: String, text: String) = synchronized(lock) {
        val meta = store.loadRegistry().firstOrNull { it.accountKey == accountKey } ?: return@synchronized
        val current = store.loadEncryptedDrafts(meta.accountHash).toMutableMap()
        if (text.isBlank()) current.remove(conversationId) else current[conversationId] = text
        store.saveEncryptedDrafts(meta.accountHash, current)
    }

    fun draftFor(accountKey: String, conversationId: String): String? = synchronized(lock) {
        val meta = store.loadRegistry().firstOrNull { it.accountKey == accountKey } ?: return@synchronized null
        store.loadEncryptedDrafts(meta.accountHash)[conversationId]
    }

    fun allDraftsFor(accountKey: String): Map<String, String> = synchronized(lock) {
        val meta = store.loadRegistry().firstOrNull { it.accountKey == accountKey } ?: return@synchronized emptyMap()
        store.loadEncryptedDrafts(meta.accountHash)
    }

    companion object {
        const val DEFAULT_MAX_ACCOUNTS = 5
        const val MAX_RECENT_CARDS_PER_ACCOUNT = 6

        fun summarizeConversations(
            conversations: List<Conversation>,
            drafts: Map<String, String>,
            isHiddenOrLocked: (String) -> Boolean,
        ): List<AccountRecentChatSummary> = conversations
            .asSequence()
            .filter { !it.isArchived && !isHiddenOrLocked(it.id) }
            .sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.lastMessageTime ?: java.time.Instant.EPOCH })
            .take(MAX_RECENT_CARDS_PER_ACCOUNT)
            .map { conv ->
                AccountRecentChatSummary(
                    conversationId = conv.id,
                    title = conv.title,
                    typeName = conv.type.name,
                    unreadCount = conv.unreadCount.coerceAtLeast(0),
                    lastTimestampEpochMs = conv.lastMessageTime?.toEpochMilli() ?: 0L,
                    avatarUrl = null,
                    avatarInitials = conv.avatarInitials ?: "C",
                    avatarColorHex = conv.avatarColorHex ?: "#00A884",
                    hasDraft = !drafts[conv.id].isNullOrBlank() || !conv.draftText.isNullOrBlank(),
                    isMuted = conv.isMuted,
                )
            }
            .toList()
    }
}
