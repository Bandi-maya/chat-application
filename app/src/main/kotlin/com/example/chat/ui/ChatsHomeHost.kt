package com.example.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.chat.ServiceLocator
import com.example.chat.data.services.ProtectedResourceGate
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.chats.ChatsHomeScreen
import com.example.chat.ui.chats.home.ChatsHomeNav
import com.example.chat.ui.chats.home.HomeIdentity
import kotlinx.coroutines.launch

/**
 * Shared host for ChatsHomeScreen (chats and groups destinations). Wires the
 * screen's preference, identity, row-action, call, lock and vault seams to the
 * real services. Conversation row state changes go through
 * ConversationsRepository, as Dart ChatyDataStore does (toggle and mark methods).
 */
@Composable
fun ChatsHomeHost(
    forcedType: ConversationType?,
    pageTitle: String?,
    gate: GatePrompts,
    onOpenConversation: (Conversation) -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenLockedChats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNewChat: (groupMode: Boolean, broadcastMode: Boolean) -> Unit = { _, _ -> },
    onOpenGlobalSearch: () -> Unit = {},
    onOpenMessageManagement: () -> Unit = {},
) {
    val api = ServiceLocator.apiBackend
    val prefs = ServiceLocator.preferencesController
    val repo = ServiceLocator.conversationsRepository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val l: () -> Unit = { rev++ }
        prefs.addListener(l)
        onDispose { prefs.removeListener(l) }
    }
    @Suppress("UNUSED_EXPRESSION") rev
    val me = api.currentUser
    val conversations = api.conversations
    fun find(id: String) = conversations.firstOrNull { it.id == id }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
    fun setState(id: String, field: String, value: Boolean) {
        scope.launch { try { repo.setConversationStateAsync(id, field, value) } catch (e: Exception) { toast(e.message ?: "Unable to update chat.") } }
    }
    val security = prefs.security
    ChatsHomeScreen(
        conversations = conversations,
        requiresSubscription = api.requiresSubscription,
        subscriptionMessage = api.subscriptionErrorMessage,
        onOpenConversation = onOpenConversation,
        currentUserId = me?.id ?: "",
        identity = HomeIdentity(
            displayName = me?.displayName ?: "",
            username = me?.username ?: "",
            about = me?.about ?: "",
            avatarInitials = me?.avatarInitials ?: "C",
            avatarColorHex = me?.avatarColorHex,
            avatarUrl = me?.avatarUrl,
        ),
        contacts = api.users,
        homePreferences = prefs.home,
        securityPreferences = security,
        forcedType = forcedType,
        pageTitle = pageTitle,
        messagesFor = { id -> api.getMessages(id) },
        gbBool = { key, fallback -> prefs.gbBool(key, fallback) },
        gbColor = { key -> prefs.gbColor(key) },
        isConversationLocked = { id -> prefs.isConversationLocked(id) },
        isConversationHidden = { id -> prefs.isConversationHidden(id) },
        onToggleLockConversation = { id, lock -> prefs.toggleLockConversation(id, lock) },
        onToggleGhostMode = { on -> prefs.updateHome(prefs.home.copy(ghostMode = on)) },
        verifySecretPhrase = { phrase -> ServiceLocator.localLockService.verifySecretPhrase(phrase) },
        nav = ChatsHomeNav(
            onOpenSettings = onOpenSettings,
            onOpenSecuritySettings = onOpenSecurity,
            onOpenLockedVault = onOpenLockedChats,
            onOpenNewChat = onOpenNewChat,
            onOpenGlobalSearch = onOpenGlobalSearch,
            onOpenMessageManagement = onOpenMessageManagement,
            // Dart: widget.themeController.toggleBrightness.
            onToggleBrightness = { ServiceLocator.themeController.toggleBrightness() },
            // Dart: MethodChannel('chaty/launcher_icon').restartApp -> relaunch the app task.
            onRestartApp = {
                try {
                    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        ?: throw IllegalStateException("no launch intent")
                    launch.addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    )
                    context.startActivity(launch)
                    Runtime.getRuntime().exit(0)
                } catch (e: Exception) {
                    toast("Chaty could not restart: ${e}")
                }
            },
        ),
        onTogglePin = { id -> find(id)?.let { setState(id, "pinned", !it.isPinned) } },
        onToggleMute = { id -> find(id)?.let { setState(id, "muted", !it.isMuted) } },
        onToggleArchive = { id -> find(id)?.let { setState(id, "archived", !it.isArchived) } },
        onMarkRead = { id -> scope.launch { try { repo.markAsRead(id) } catch (_: Exception) {} } },
        onMarkUnread = { id -> scope.launch { try { repo.markAsUnread(id) } catch (_: Exception) {} } },
        onMarkAllRead = { scope.launch { try { repo.markAllAsRead() } catch (_: Exception) {} } },
        onDeleteConversations = { ids ->
            // Dart ChatyDataStore.deleteConversation: custom-backend delete, then the Supabase one.
            scope.launch {
                for (id in ids) {
                    try { if (api.isAuthenticated) api.deleteConversation(id) } catch (_: Exception) {}
                    try { repo.deleteConversation(id) } catch (_: Exception) {}
                }
            }
        },
        onStartCall = { conversation, isVideo ->
            val myId = me?.id
            val otherId = conversation.participantIds.firstOrNull { it != myId }
            val other = api.users.firstOrNull { it.id == otherId }
            if (otherId != null && conversation.type == ConversationType.direct) {
                ServiceLocator.callPresentationController.showFullScreen()
                scope.launch {
                    try {
                        ServiceLocator.callSignaling.initiateCall(
                            otherId, other?.displayName ?: conversation.title,
                            other?.avatarInitials, other?.avatarColorHex, isVideo,
                        )
                    } catch (e: Exception) { toast(e.message ?: "Unable to start the call.") }
                }
            }
        },
        onSnack = { toast(it) },
        onAuthorizeConversation = { conversation, proceed ->
            scope.launch {
                val ok = ProtectedResourceGate.authorizeConversation(
                    conversationId = conversation.id,
                    isProtected = prefs.isConversationLocked(conversation.id),
                    autoLockTimeout = security.autoLockTimeout,
                    lockMethod = security.lockMethod,
                    lockService = ServiceLocator.localLockService,
                    setupPrompt = gate.setupPrompt,
                    lockPrompt = gate.lockPrompt,
                )
                if (ok) proceed()
            }
        },
        preferencesController = prefs,
        connectionHealthState = ServiceLocator.connectionHealth.state,
        lockedChatsConfigured = security.lockedConversationIds.isNotEmpty() || security.isAppLockEnabled,
        onRequestVaultAuth = { authorized ->
            scope.launch {
                val ok = ProtectedResourceGate.authorizeGeneralAction(
                    lockMethod = security.lockMethod,
                    lockService = ServiceLocator.localLockService,
                    lockPrompt = gate.lockPrompt,
                    title = "Locked Chats",
                    reason = "Authenticate to open Locked Chats",
                )
                if (ok) authorized()
            }
        },
    )
}
