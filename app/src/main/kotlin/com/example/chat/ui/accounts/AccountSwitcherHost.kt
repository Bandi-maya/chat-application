package com.example.chat.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.chat.ServiceLocator
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.data.api.AccountRecentChatSummary
import com.example.chat.data.api.ApiBackendService
import com.example.chat.data.api.ApiRegisteredAccountMeta
import com.example.chat.data.services.ProtectedResourceGate
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.GatePrompts
import com.example.chat.ui.auth.LoginScreen
import kotlinx.coroutines.launch

/**
 * Coordinates the floating Account Switcher overlay, Account-Specific Recent Cards navigation,
 * atomic switching, and the real Add-Account authentication flow outside AppRoot.kt.
 */
@Composable
fun AccountSwitcherHost(
    open: Boolean,
    startInManage: Boolean = false,
    onDismiss: () -> Unit,
    theme: ThemeConfig,
    gate: GatePrompts,
    refreshRevision: Int,
    onOpenSettings: () -> Unit,
    onSwitchedAccount: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onToast: (String) -> Unit,
) {
    val api = ServiceLocator.apiBackend
    val multi = api.multiAccount
    val prefs = ServiceLocator.preferencesController
    val scope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf<List<ApiRegisteredAccountMeta>>(emptyList()) }
    var activeKey by remember { mutableStateOf<String?>(null) }
    var recentByAccount by remember { mutableStateOf<Map<String, List<AccountRecentChatSummary>>>(emptyMap()) }
    var selectedByAccount by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var navByAccount by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    fun reloadSnapshot() {
        api.persistAccountConversationSnapshot()
        val list = multi.accounts()
        val key = api.currentAccountKey() ?: multi.activeAccountKey()
        val layout = api.multiAccountStore.loadLayoutState()
        val recents = LinkedHashMap<String, List<AccountRecentChatSummary>>()
        list.forEach { meta ->
            recents[meta.accountKey] = multi.recentConversationsFor(meta.accountKey)
        }
        accounts = list
        activeKey = key
        recentByAccount = recents
        selectedByAccount = layout.selectedConversationByAccount
        navByAccount = layout.navContextByAccount
    }

    LaunchedEffect(open, refreshRevision) {
        if (open) reloadSnapshot()
    }

    var isSwitching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var addingAccount by rememberSaveable { mutableStateOf(false) }
    // Recents-style card view first; the classic overlay is the "Manage accounts" destination.
    var reauthIdentifier by rememberSaveable { mutableStateOf("") }
    var reauthWorkspace by rememberSaveable { mutableStateOf<String?>(null) }
    var manageMode by rememberSaveable { mutableStateOf(startInManage) }
    LaunchedEffect(open) { if (open) manageMode = startInManage }

    fun startAddAccount() {
        reauthIdentifier = ""
        reauthWorkspace = null
        errorMessage = null
        addingAccount = true
    }

    fun selectAccount(target: ApiRegisteredAccountMeta) {
        if (isSwitching) return
        scope.launch {
            isSwitching = true
            errorMessage = null
            try {
                when (val result = api.switchToAccount(target.accountKey)) {
                    is ApiBackendService.AccountSwitchResult.Switched -> {
                        onSwitchedAccount()
                        onDismiss()
                    }
                    is ApiBackendService.AccountSwitchResult.AlreadyActive -> {
                        onDismiss()
                    }
                    is ApiBackendService.AccountSwitchResult.SessionExpired -> {
                        errorMessage = result.message
                        reloadSnapshot()
                    }
                    is ApiBackendService.AccountSwitchResult.Failed -> {
                        errorMessage = result.message
                        reloadSnapshot()
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unable to switch accounts."
            } finally {
                isSwitching = false
            }
        }
    }

    fun resolveConversationForActiveAccount(summary: AccountRecentChatSummary): Conversation {
        val existing = api.conversations.firstOrNull { it.id == summary.conversationId }
        if (existing != null) return existing
        return Conversation(
            id = summary.conversationId,
            type = if (summary.typeName == "group") ConversationType.group else ConversationType.direct,
            title = summary.title,
            participantIds = emptyList(),
            avatarInitials = summary.avatarInitials,
            avatarColorHex = summary.avatarColorHex,
            lastMessageText = "",
            lastMessageTime = java.time.Instant.ofEpochMilli(summary.lastTimestampEpochMs),
            lastMessageSenderId = "",
            unreadCount = summary.unreadCount,
            isMuted = summary.isMuted,
        )
    }

    AccountRecentsOverlay(
        visible = open && !addingAccount && !manageMode,
        accounts = accounts,
        activeAccountKey = activeKey,
        recentByAccount = recentByAccount,
        theme = theme,
        isSwitching = isSwitching,
        errorMessage = errorMessage,
        onSelectAccount = { target -> selectAccount(target) },
        onAddAccount = { startAddAccount() },
        onManageAccounts = { manageMode = true },
        onDismiss = onDismiss,
    )

    AccountSwitcherOverlay(
        visible = open && !addingAccount && manageMode,
        accounts = accounts,
        activeAccountKey = activeKey,
        recentByAccount = recentByAccount,
        selectedConversationByAccount = selectedByAccount,
        navContextByAccount = navByAccount,
        theme = theme,
        isSwitching = isSwitching,
        errorMessage = errorMessage,
        onDismissError = { errorMessage = null },
        onSelectAccount = { target -> selectAccount(target) },
        onAddAccount = { startAddAccount() },
        onReauthenticateAccount = { meta ->
            reauthIdentifier = meta.username
            reauthWorkspace = if (meta.isDefaultTenant) null else meta.tenantId
            errorMessage = null
            addingAccount = true
        },
        onSignOutAccount = { target ->
            if (isSwitching) return@AccountSwitcherOverlay
            scope.launch {
                isSwitching = true
                errorMessage = null
                try {
                    val hadMultiple = accounts.size > 1
                    api.signOutAccount(target.accountKey)
                    if (!hadMultiple || multi.accounts().isEmpty()) {
                        onDismiss()
                    } else {
                        onSwitchedAccount()
                        reloadSnapshot()
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Unable to sign out account."
                } finally {
                    isSwitching = false
                }
            }
        },
        onOpenSettings = onOpenSettings,
        onMoveAccountCard = { key, delta ->
            val currentOrder = accounts.map { it.accountKey }.toMutableList()
            val idx = currentOrder.indexOf(key)
            val dest = idx + delta
            if (idx >= 0 && dest in currentOrder.indices) {
                val moved = currentOrder.removeAt(idx)
                currentOrder.add(dest, moved)
                multi.updateRecentCardOrder(currentOrder)
                reloadSnapshot()
            }
        },
        onOpenRecentConversation = { accountKey, summary ->
            if (isSwitching) return@AccountSwitcherOverlay
            scope.launch {
                isSwitching = true
                errorMessage = null
                try {
                    if (accountKey != activeKey) {
                        when (val sw = api.switchToAccount(accountKey)) {
                            is ApiBackendService.AccountSwitchResult.Switched -> onSwitchedAccount()
                            is ApiBackendService.AccountSwitchResult.AlreadyActive -> Unit
                            is ApiBackendService.AccountSwitchResult.SessionExpired -> {
                                errorMessage = sw.message
                                reauthIdentifier = sw.meta.username
                                reauthWorkspace = if (sw.meta.isDefaultTenant) null else sw.meta.tenantId
                                addingAccount = true
                                reloadSnapshot()
                                return@launch
                            }
                            is ApiBackendService.AccountSwitchResult.Failed -> {
                                errorMessage = sw.message
                                reloadSnapshot()
                                return@launch
                            }
                        }
                    }
                    multi.setAccountNavigationState(accountKey, selectedConversationId = summary.conversationId)
                    val conv = resolveConversationForActiveAccount(summary)
                    val sec = prefs.security
                    val locked = prefs.isConversationLocked(conv.id) ||
                        ServiceLocator.hiddenVaultService.isConversationLocked(conv.id)
                    val authorized = if (!locked) true else {
                        ProtectedResourceGate.authorizeConversation(
                            conversationId = conv.id,
                            isProtected = true,
                            autoLockTimeout = sec.autoLockTimeout,
                            lockMethod = sec.lockMethod,
                            lockService = ServiceLocator.localLockService,
                            setupPrompt = gate.setupPrompt,
                            lockPrompt = gate.lockPrompt,
                        )
                    }
                    if (authorized) {
                        onDismiss()
                        onOpenConversation(conv)
                    } else {
                        onToast("Authentication required to open locked conversation.")
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Unable to open conversation."
                } finally {
                    isSwitching = false
                }
            }
        },
        onDismiss = onDismiss,
    )

    if (addingAccount) {
        BackHandler { addingAccount = false }
        Box(Modifier.fillMaxSize()) {
            LoginScreen(
                onBack = { addingAccount = false },
                onLoggedIn = { _ ->
                    ServiceLocator.appLock.onSignedIn()
                    addingAccount = false
                    onSwitchedAccount()
                    onDismiss()
                },
                onForgotPassword = {
                    onToast("Use Forgot Password on sign-out or reset via workspace admin.")
                },
                onRegister = {
                    onToast("Sign out or register a new profile from Welcome to onboard.")
                },
                onRegisterOrganization = {
                    onToast("Register a new workspace from Welcome to onboard.")
                },
                loadSavedTenant = {
                    (reauthWorkspace ?: api.config.getSavedTenantId()) to
                        (if (reauthWorkspace != null) false else api.config.isUsingDefaultTenant)
                },
                login = { identifier, password, tenant, isDefaultTenant ->
                    val meta = api.loginForAddAccount(identifier, password, tenant, isDefaultTenant)
                    meta.userId
                },
                demoLogin = null,
                initialIdentifier = reauthIdentifier,
                initialWorkspace = reauthWorkspace,
            )
        }
    }
}
