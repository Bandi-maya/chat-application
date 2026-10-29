package com.example.chat.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.api.ApiAccountScope
import com.example.chat.data.api.ApiBackendService
import com.example.chat.data.services.AppLockCoordinator
import com.example.chat.data.services.AudioRouteType
import com.example.chat.data.services.CallSignalingService
import com.example.chat.data.services.NotificationChannelManager
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns system notifications for the custom-API account plane. Every action is bound at build time to the EXACT
 * [ApiAccountScope] reference (never serialized; PendingIntents carry a random nonce only) and revalidated at
 * dispatch. All registry entries are dropped on every auth transition; NotificationManager post/cancel IPC is
 * serialized on ONE process-lived lane thread ([ChatyNotificationLane]) that re-checks admission at execution
 * time, so old-account alerts are cancelled in FIFO order and old-account cleanup can never cancel a new
 * account's alert. Auth-scope listeners only clear the in-memory registry and ENQUEUE a cleanup - no
 * NotificationManager call runs under the auth lock. The Coordinator is the lane's only [ChatyNotificationSink]
 * (R6: Poster notify/cancel are internal with no other caller).
 */
class ChatyNotificationCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val api: ApiBackendService,
    private val calls: CallSignalingService,
    private val prefs: ChatyPreferencesController,
    private val appLock: AppLockCoordinator,
    private val channelManager: NotificationChannelManager,
    private val isChatVaultLocked: (String) -> Boolean,
) : ChatyNotificationBridge, ChatyNotificationSink {

    private val registry = ChatyActionRegistry<ApiAccountScope>()
    @Volatile private var lastScope: ApiAccountScope? = null
    @Volatile private var lastIncomingCallId: String? = null
    private val stylePrefs get() = context.getSharedPreferences("chaty_notification_style", Context.MODE_PRIVATE)

    /** The notification lane. Created ONCE in [start] together with its single daemon thread
     *  (process-lived, never per-transition, never shut down); start() itself is called once from
     *  ServiceLocator init and is NOT idempotent. */
    private lateinit var lane: ChatyNotificationLane

    var style: ChatyNotificationStyle
        get() = ChatyNotificationStyle.sanitized(
            stylePrefs.getInt("accent", ChatyNotificationStyle().accentArgb), stylePrefs.getInt("icon", 0),
        )
        set(v) {
            val c = ChatyNotificationStyle.sanitized(v.accentArgb, v.iconVariant)
            stylePrefs.edit().putInt("accent", c.accentArgb).putInt("icon", c.iconVariant).apply()
        }

    fun start() {
        val laneExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "chaty-notification-lane").apply { isDaemon = true }
        }
        lane = ChatyNotificationLane(
            currentScope = { api.authSnapshot() },
            liveCallId = { calls.currentSession?.takeIf { !it.state.isTerminal }?.callId },
            registry = registry,
            sink = this,
            executor = SerialDeferredExecutor { command -> laneExecutor.execute { runCatching { command.run() } } },
        )
        ChatyNotificationPoster.ensureChannels(context)
        ChatyNotificationBridgeHolder.bridge = this
        api.addAuthScopeListener { next ->
            if (lastScope !== next) {
                lastScope = next
                registry.clear()
                lane.cleanupNonCurrent() // enqueue-only: NO sink IPC under the auth lock
            }
        }
        calls.addStateListener { scope.launch { onCallState() } }
        api.onIncomingMessage = { message, convoTitle, convoMuted -> scope.launch { onMessage(message, convoTitle, convoMuted) } }
        lane.reconcileOnStart()
    }

    // --- ChatyNotificationSink: the sole NotificationManager IPC surface, invoked on the lane thread only. ---

    override fun postMessage(tag: String, title: String, body: String, whenEpochMs: Long, replyNonce: String?, dismissNonce: String?) {
        ChatyNotificationPoster.postMessage(context, style, tag, title, body, whenEpochMs, replyNonce, dismissNonce)
    }

    override fun cancelMessage(tag: String) = ChatyNotificationPoster.cancelMessage(context, tag)

    override fun postIncomingCall(tag: String, callerName: String, isVideo: Boolean, declineNonce: String?) {
        ChatyNotificationPoster.postIncomingCall(context, style, callerName, isVideo, declineNonce, tag)
    }

    override fun cancelIncomingCall(tag: String) = ChatyNotificationPoster.cancelIncomingCall(context, tag)

    /** This app's active m:/c:-prefixed tags only; the lifecycle-bound foreground-service notification
     *  (SERVICE_ID=7401, untagged) and any foreign tags never appear here. */
    override fun activeTags(): List<String> {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.activeNotifications.mapNotNull { it.tag }.filter { it.startsWith("m:") || it.startsWith("c:") }
    }

    /** The live style resolver shared with message/incoming-call posts (style-fix: ongoing-call notification
     *  reads the same user selection instead of a hardcoded constant). */
    override fun currentStyle(): ChatyNotificationStyle = style

    fun cancelFor(conversationId: String) {
        // Fresh snapshot per the lane contract: only that owner's tag for this conversation can be cancelled.
        lane.cancelMessageFor(api.authSnapshot(), conversationId)
    }

    private fun foreground() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun onMessage(message: ChatMessage, convoTitle: String?, convoMuted: Boolean) {
        val account = api.authSnapshot()
        val me = account.userId
        val signedIn = !me.isNullOrBlank() && !account.token.isNullOrBlank()
        val gate = ChatyNotificationPolicy.MessageGate(
            globalEnabled = prefs.notification.enableGlobalNotifications, signedIn = signedIn,
            fromMe = message.senderId == me, conversationMuted = convoMuted, appForeground = foreground(),
            conversationOpen = api.activeConversation == message.conversationId,
            systemOrDeleted = message.type == MessageType.system || message.isDeletedForEveryone ||
                message.isDeletedForMe || message.isRevokedBySender,
        )
        if (!ChatyNotificationPolicy.shouldPostMessage(gate)) return
        val chatLocked = isChatVaultLocked(message.conversationId) ||
            prefs.security.lockedConversationIds.contains(message.conversationId)
        val detail = ChatyNotificationPolicy.detail(chatLocked, prefs.security.isAppLockEnabled)
        val raw = message.text.ifBlank { if (message.type == MessageType.image) "Photo" else "Attachment" }
        val plan = ChatyNotificationPolicy.plan(detail, convoTitle, raw) { channelManager.formatPrivacyBody(convoTitle ?: "Chaty", it) }
        val id = message.conversationId
        val reply = if (plan.offerReply) registry.register(account, ChatyNotificationPolicy.ACTION_REPLY, conversationId = id) else null
        val dismiss = if (plan.offerDismiss) registry.register(account, ChatyNotificationPolicy.ACTION_DISMISS, conversationId = id) else null
        // Build-outside rule: gates/prefs/nonces resolved here holding NO auth lock; stale-scope text lives
        // only in a dropped record. Admission is re-checked on the lane thread at execution time - a stale
        // build is dropped with its nonces and never reaches NotificationManager.
        lane.postMessage(account, id, plan.title, plan.body, message.createdAt.toEpochMilli(), reply, dismiss)
    }

    private data class CallSnap(val account: ApiAccountScope, val callId: String, val incoming: Boolean, val outgoing: Boolean,
        val video: Boolean, val name: String, val muted: Boolean, val route: AudioRouteType, val live: Boolean)

    /** Snapshot account + session together under the auth guard; build/post/cancel happens outside it. */
    private fun snapshotCall(): CallSnap? {
        val account = api.authSnapshot()
        var snap: CallSnap? = null
        api.withAuthScope(account) {
            val s = calls.currentSession ?: return@withAuthScope
            snap = CallSnap(account, s.callId, s.state == com.example.chat.data.services.CallSessionState.incoming, s.isOutgoing,
                s.isVideo, s.remoteDisplayName.trim().ifEmpty { "Chaty contact" }, s.isMuted, s.audioRoute, !s.state.isTerminal)
        }
        return snap
    }

    private fun onCallState() {
        val snap = snapshotCall()
        if (snap == null || !snap.live || !snap.incoming || snap.outgoing) {
            lastIncomingCallId?.let { lane.cancelCallFor(api.authSnapshot(), it) }
            lastIncomingCallId = null
            if (snap == null || !snap.live) registry.clearCalls()
            return
        }
        if (!foreground()) {
            val decline = registry.register(snap.account, ChatyNotificationPolicy.ACTION_DECLINE, callId = snap.callId)
            // Admission (scope + live call) is re-checked on the lane thread at execution time.
            lastIncomingCallId = snap.callId
            lane.postIncomingCall(snap.account, snap.callId, snap.name, snap.video, decline)
        } else {
            // App came to the foreground while the full-screen alert was up: the in-app call
            // UI owns the incoming call now, so the system notification is withdrawn.
            lastIncomingCallId?.let { lane.cancelCallFor(snap.account, it) }
            lastIncomingCallId = null
        }
    }

    /** Re-evaluate the live call after an app foreground/background transition (ServiceLocator
     *  refreshCallAlert): posts the full-screen alert when backgrounded, withdraws it when the
     *  in-app call UI is visible. Message alerts need no re-evaluation (per-event gating). */
    fun refreshCallState() {
        if (!::lane.isInitialized) return
        scope.launch { onCallState() }
    }

    override fun registerCallAction(action: String): Pair<String, CallActionView>? {
        val snap = snapshotCall() ?: return null
        if (!snap.live || snap.incoming) return null
        return registry.register(snap.account, action, callId = snap.callId) to CallActionView(snap.callId, snap.muted, snap.route == AudioRouteType.speaker)
    }

    private fun callOk(id: String) = ChatyNotificationPolicy.callActionAllowed(id, calls.currentSession?.callId, calls.hasActiveSession)

    override suspend fun dispatch(nonce: String?, action: String, replyText: CharSequence?) {
        val e = registry.resolve(nonce) ?: return
        if (e.action != action) return
        val account = e.scope
        // The exact scope the action was built for must still be the live one.
        if (api.authSnapshot() !== account || account.token.isNullOrBlank()) return
        when (action) {
            ChatyNotificationPolicy.ACTION_REPLY -> {
                val id = e.conversationId ?: return
                val text = ChatyNotificationPolicy.cleanReply(replyText) ?: return
                val chatLocked = isChatVaultLocked(id) || prefs.security.lockedConversationIds.contains(id)
                if (!ChatyNotificationPolicy.mayReply(appLock.lockRequired.value, prefs.security.isAppLockEnabled, chatLocked)) return
                // Scope-bound send: token/base URL come from the ORIGINAL scope, request cancelled on any auth transition.
                if (api.sendMessageForScope(account, id, text)) cancelFor(id)
            }
            ChatyNotificationPolicy.ACTION_DISMISS -> e.conversationId?.let { cancelFor(it) } // local only: no read state is sent to the server
            ChatyNotificationPolicy.ACTION_MUTE -> {
                val id = e.callId ?: return
                api.withAuthScope(account) { if (callOk(id)) calls.toggleMute() }
            }
            ChatyNotificationPolicy.ACTION_HANGUP -> {
                val id = e.callId ?: return
                api.withAuthScope(account) { if (callOk(id)) calls.endCall("ended") }
            }
            ChatyNotificationPolicy.ACTION_SPEAKER -> {
                val id = e.callId ?: return
                val cur = calls.currentSession?.takeIf { it.callId == id } ?: return
                val next = if (cur.audioRoute == AudioRouteType.speaker) AudioRouteType.earpiece else AudioRouteType.speaker
                calls.setAudioRoute(account, id, next) // service rejects stale account/call itself
            }
            ChatyNotificationPolicy.ACTION_DECLINE -> {
                val id = e.callId ?: return
                calls.declineCall(account, id)
                if (lastIncomingCallId == id) lastIncomingCallId = null
                lane.cancelCallFor(account, id)
            }
        }
    }
}
