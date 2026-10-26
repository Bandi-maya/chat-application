package com.example.chat.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shortcut
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ServiceLocator
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ContactConnectionStatus
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.chats.ChatDetailScreen
import com.example.chat.ui.chats.detail.ForwardSend
import com.example.chat.ui.chats.detail.ForwardTargets
import com.example.chat.ui.chats.home.HomeAvatar
import com.example.chat.ui.chats.home.HomeFormatters
import com.example.chat.ui.chats.home.parseAvatarColorHex
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Real host for ChatDetailScreen. Mirrors how Dart ChatDetailScreen wires its
 *  services (chat_detail_screen.dart _buildChat/_executeMessageAction/_startCall/
 *  _openChatMenu) over the Dart ChatyDataStore routing: api backend first when
 *  authenticated, Supabase-backed repository side tolerated on error.
 *  Known deviations are listed in docs/chat-detail-host-deviations.md semantics:
 *  presence refresh is a 3s tick (richRealtime exposes no listener API);
 *  forward sends text only; task modal has no source-message link param. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailHost(
    conversation: Conversation,
    initialMessageId: String?,
    onBack: () -> Unit,
    /** Dart quick-contact tap: Navigator.pushReplacement(ChatDetailScreen(next.id)). */
    onOpenConversation: (Conversation) -> Unit = {},
) {
    val api = ServiceLocator.apiBackend
    val prefs = ServiceLocator.preferencesController
    val repo = ServiceLocator.conversationsRepository
    val rt = ServiceLocator.richRealtime
    val tasksController = ServiceLocator.tasksController
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = LocalThemeConfig.current
    var rev by remember { mutableIntStateOf(0) }
    DisposableEffect(conversation.id) {
        val l: () -> Unit = { rev++ }
        api.addListener(l)
        prefs.addListener(l)
        tasksController.addListener(l)
        onDispose { api.removeListener(l); prefs.removeListener(l); tasksController.removeListener(l) }
    }
    // richRealtime has no listener API: refresh presence/typing on a short tick.
    LaunchedEffect(conversation.id) {
        try { rt.trackConversation(conversation.id) } catch (_: Exception) {}
        while (true) { delay(3000); rev++ }
    }
    LaunchedEffect(conversation.id) { try { tasksController.loadTasks() } catch (_: Exception) {} }
    // Dart _loadConversation: offline-first loading flag, remote refresh, delivered receipt, error + Retry.
    var loadingMessages by remember(conversation.id) { mutableStateOf(true) }
    var loadError by remember(conversation.id) { mutableStateOf<String?>(null) }
    var loadNonce by remember(conversation.id) { mutableIntStateOf(0) }
    LaunchedEffect(conversation.id, loadNonce) {
        loadError = null
        loadingMessages = api.getMessages(conversation.id).isEmpty()
        try {
            if (api.isAuthenticated) api.loadMessages(conversation.id)
            rt.trackConversation(conversation.id)
            rt.markConversationDelivered(conversation.id)
            loadingMessages = false
        } catch (e: Exception) {
            loadingMessages = false
            loadError = (e.message ?: e.toString()).replaceFirst("Exception: ", "")
        }
    }
    @Suppress("UNUSED_EXPRESSION") rev

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
    // Dart _captureAndSendPhoto (chat_detail_screen.dart:473): composer camera
    // icon opens the full-screen capture flow in chat mode.
    var chatCameraOpen by remember { mutableStateOf(false) }
    val hostView = androidx.compose.ui.platform.LocalView.current
    val me = api.currentUser
    val myId = me?.id ?: ""
    val live = api.conversations.firstOrNull { it.id == conversation.id } ?: conversation
    val messages = api.getMessages(conversation.id)
    // Refresh epoch outside the auth lock, then confirm the SAME captured account and pure epoch together.
    fun captureMediaBinding(): com.example.chat.ui.chats.detail.AttachmentConsumerBinding? {
        val account = api.authSnapshot()
        val stamp = ServiceLocator.attachmentResolver.currentSession() ?: return null
        var coherent = false
        api.withAuthScope(account) { coherent = ServiceLocator.attachmentResolver.isEpochCurrent(stamp) }
        if (!coherent) return null
        return com.example.chat.ui.chats.detail.AttachmentConsumerBinding(stamp, ServiceLocator.attachmentResolver,
            { ServiceLocator.attachmentResolver.currentSession() },
            deliverIfCurrent = { block ->
                var delivered = false
                api.withAuthScope(account) {
                    if (ServiceLocator.attachmentResolver.isEpochCurrent(stamp)) { block(); delivered = true }
                }
                delivered
            },
        ) { message ->
            val path = message.attachment?.url ?: throw IllegalStateException("Missing media path")
            ServiceLocator.chatMedia.createSignedUrl(path, 3600) { account.baseUrl }
        }
    }
    var capturedMedia by remember { mutableStateOf(captureMediaBinding()) }
    LaunchedEffect(conversation.id) {
        while (true) {
            val next = captureMediaBinding()
            if (next?.session != capturedMedia?.session) capturedMedia = next
            kotlinx.coroutines.delay(150)
        }
    }
    val mediaStamp = capturedMedia?.session
    val attachmentBinding = capturedMedia ?: com.example.chat.ui.chats.detail.AttachmentConsumerBinding(
        "", ServiceLocator.attachmentResolver, { null }, deliverIfCurrent = { _ -> false },
    ) { throw IllegalStateException("Media identity unavailable") }

    val isDirect = live.type == ConversationType.direct
    val otherId = live.participantIds.firstOrNull { it != myId }
    val other = api.users.firstOrNull { it.id == otherId }
    val conv = prefs.conversation
    val privacy = prefs.privacy
    fun gbB(k: String, d: Boolean) = prefs.gbBool(k, d)

    var connection by remember(conversation.id) { mutableStateOf<ContactConnectionStatus?>(null) }
    suspend fun refreshConnection() {
        if (isDirect && otherId != null) {
            try { connection = ServiceLocator.contactRelationships.connectionStatus(otherId) } catch (_: Exception) {}
        }
    }
    LaunchedEffect(conversation.id, otherId) { refreshConnection() }
    var gateOpen by remember { mutableStateOf(false) }
    var blockConfirm by remember { mutableStateOf(false) }
    var forwardMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var taskEditor by remember { mutableStateOf<Triple<Boolean, ChatTask?, String?>?>(null) }
    var taskDetail by remember { mutableStateOf<ChatTask?>(null) }
    // 0 none, 1 info (ContactInfo/GroupInfo), 2 contact privacy; viewer = MediaViewerScreen target.
    var infoRoute by remember(conversation.id) { mutableIntStateOf(0) }
    var viewerBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var viewerMessage by remember(conversation.id, mediaStamp) { mutableStateOf<ChatMessage?>(null) }
    var pollCreateOpen by remember(conversation.id) { mutableStateOf(false) }
    var pollViewId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var appearanceOpen by remember(conversation.id) { mutableStateOf(false) }
    var infoBusy by remember { mutableStateOf(false) }
    var infoConnection by remember(conversation.id) { mutableStateOf(ContactConnectionStatus()) }
    var infoBlocked by remember(conversation.id) { mutableStateOf(false) }
    var infoLoading by remember(conversation.id) { mutableStateOf(false) }
    var infoError by remember(conversation.id) { mutableStateOf<String?>(null) }

    // Presence text (Dart _buildChat).
    val activity = otherId?.let { rt.activityFor(conversation.id, it) }
    val presenceOnline = isDirect && otherId != null && rt.isOnline(otherId)
    val presenceText: String? = if (isDirect) {
        when {
            activity?.isRecording == true -> "recording voice message…"
            activity?.isTyping == true -> "typing…"
            otherId == null -> null
            presenceOnline -> "online"
            else -> HomeFormatters.formatLastSeen(rt.lastSeenFor(otherId)).ifEmpty { "last seen hidden" }
        }
    } else {
        if (activity?.isTyping == true) "someone is typing…" else "${live.participantIds.size} participants"
    }

    val wallpaperStore = remember { context.getSharedPreferences("chaty_chat_wallpapers", 0) }
    val wallpaperInitial = remember(conversation.id) {
        try { JSONObject(wallpaperStore.getString("chaty_chat_wallpapers_v1", "{}") ?: "{}").optString(conversation.id, "").ifEmpty { null } }
        catch (_: Exception) { null }
    }
    val colorOverrides = com.example.chat.ui.chats.detail.BubbleColorOverrides(
        rightBubbleColor = conv.rightBubbleColor, leftBubbleColor = conv.leftBubbleColor,
        rightChatBubbleTextColor = conv.rightChatBubbleTextColor, leftChatBubbleTextColor = conv.leftChatBubbleTextColor,
        rightBubbleTimeColor = conv.rightBubbleTimeColor, leftBubbleTimeColor = conv.leftBubbleTimeColor,
        messageTextSize = conv.messageTextSize, groupParticipantNameColor = conv.groupParticipantNameColor,
        quotedBackgroundColor = conv.quotedBackgroundColor, quotedDividerColor = conv.quotedDividerColor,
        quotedNameColor = conv.quotedNameColor, quotedMessageColor = conv.quotedMessageColor,
        deletedMessageIconColor = conv.deletedMessageIconColor,
        infoBalloonsBgColor = conv.infoBalloonsBgColor, infoBalloonsTextColor = conv.infoBalloonsTextColor,
    )
    val convTasks = tasksController.let { api.tasks.filter { t -> t.sourceConversationId == conversation.id } }

    val taskUser = me?.let {
        com.example.chat.domain.models.UserProfile(
            id = it.id, displayName = it.displayName, username = it.username,
            avatarInitials = it.avatarInitials, avatarColorHex = it.avatarColorHex, about = it.about,
            lastSeenAt = java.time.Instant.now(), email = it.email, phone = it.phone, avatarUrl = it.avatarUrl,
        )
    }
    val lookup: (String) -> com.example.chat.domain.models.UserProfile? = { id -> api.users.firstOrNull { it.id == id } }

    val attachments = rememberChatAttachments(conversation.id) { rev++ }
    val voice = rememberChatVoice(
        conversation.id,
        afterSend = { rev++ },
        clearTyping = { scope.launch { try { ServiceLocator.gbFeatures.setTyping(conversation.id, false) } catch (_: Exception) {} } },
    )
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    androidx.compose.runtime.key(mediaStamp, conversation.id) {
    ChatDetailScreen(
        conversation = live,
        messages = messages,
        initialMessageId = initialMessageId,
        currentUserId = myId,
        onBack = onBack,
        onSend = { text ->
            api.sendMessage(conversation.id, text)
            rt.trackConversation(conversation.id)
        },
        resolveSenderName = { id ->
            if (id == myId) "You" else (api.users.firstOrNull { it.id == id }?.displayName ?: "Team Member")
        },
        presenceText = presenceText,
        presenceIsOnline = presenceOnline,
        showHeaderAvatar = !conv.hideProfilePicture && gbB("PicProf", true),
        showHeaderName = !conv.hideContactName && gbB("NameProf", true),
        showHeaderCalls = !conv.hideCallButton && gbB("Conv_call_btn", true),
        showPresenceLine = !conv.disableContactStatus && gbB("statuschat", true),
        headerBarColor = conv.actionBarColor,
        presencePillColor = conv.contactStatusBgColor ?: prefs.gbColor("ModChatGStatusB"),
        presenceTextColor = conv.contactStatusTextColor ?: prefs.gbColor("ModChatGStatusT"),
        callsAllowed = connection?.callsAllowed ?: true,
        onStartCall = { isVideo ->
            if (isDirect && otherId != null) {
                scope.launch {
                    val status = try { ServiceLocator.contactRelationships.connectionStatus(otherId) } catch (_: Exception) { null }
                    if (status != null) connection = status
                    if (status != null && !status.callsAllowed) {
                        gateOpen = true
                    } else {
                        ServiceLocator.callPresentationController.showFullScreen()
                        try {
                            ServiceLocator.callSignaling.initiateCall(
                                otherId, other?.displayName ?: live.title,
                                other?.avatarInitials, other?.avatarColorHex, isVideo,
                            )
                        } catch (e: Exception) { toast(e.message ?: "Unable to start the call.") }
                    }
                }
            } else {
                toast("Direct contact calls are available for one-to-one chats.")
            }
        },
        connectionState = ServiceLocator.connectionHealth.state,
        onProbeConnection = { try { ServiceLocator.connectionHealth.probeConnection() } catch (_: Exception) {} },
        onDraftChanged = { text -> scope.launch { try { repo.setDraft(conversation.id, text) } catch (_: Exception) {} } },
        onTypingChanged = { typing ->
            val allowed = privacy.typingIndicators && !prefs.home.ghostMode && !gbB("yo_want_ghostmode", false)
            if (allowed) scope.launch { try { ServiceLocator.gbFeatures.setTyping(conversation.id, typing) } catch (_: Exception) {} }
        },
        quickReplies = prefs.automation.quickReplies,
        wallpaperType = conv.wallpaperType,
        wallpaperPatternId = wallpaperInitial ?: ServiceLocator.themeController.globalTheme.wallpaperId,
        wallpaperPath = conv.wallpaperPath,
        wallpaperProfilePic = conv.profilePicWallpaper,
        wallpaperAvatarUrl = other?.avatarUrl,
        onWallpaperSelected = { id ->
            try {
                val obj = JSONObject(wallpaperStore.getString("chaty_chat_wallpapers_v1", "{}") ?: "{}")
                obj.put(conversation.id, id)
                wallpaperStore.edit().putString("chaty_chat_wallpapers_v1", obj.toString()).apply()
            } catch (_: Exception) {}
        },
        onToggleMute = {
            scope.launch {
                try { repo.setConversationStateAsync(conversation.id, "muted", !live.isMuted) }
                catch (e: Exception) { toast(e.message ?: "Unable to update chat.") }
            }
        },
        onBlockContact = if (isDirect && otherId != null) ({ blockConfirm = true }) else null,
        hideDateAndName = conv.hideDateAndName,
        showDeletedContent = privacy.antiDeleteMessages || gbB("yoAntiRevoke", false),
        retainViewOnce = privacy.antiViewOnce || gbB("anti_vw_once", false),
        disableDoubleTapReaction = conv.disableDoubleTapReaction,
        doubleTapReactionEmoji = conv.doubleTapReactionEmoji,
        showGroupAvatar = ServiceLocator.templateController.conversation.showAvatarInGroup,
        colorOverrides = colorOverrides,
        onEditMessage = { id, newText ->
            if (api.isAuthenticated) api.editMessage(conversation.id, id, newText)
            repo.editMessage(conversation.id, id, newText)
        },
        onDeleteMessage = { id, forEveryone ->
            scope.launch {
                if (api.isAuthenticated) { try { api.deleteMessage(conversation.id, id) } catch (_: Exception) {} }
                try { repo.deleteMessage(conversation.id, id, forEveryone) } catch (_: Exception) {}
            }
        },
        onToggleStar = { id ->
            val m = messages.firstOrNull { it.id == id }
            if (m != null) scope.launch {
                try { repo.setMessageStateAsync(conversation.id, id, "starred", !m.isStarred) } catch (e: Exception) { toast(e.message ?: "Unable to update message.") }
            }
        },
        onTogglePin = { id ->
            val m = messages.firstOrNull { it.id == id }
            if (m != null) scope.launch {
                try { repo.setMessageStateAsync(conversation.id, id, "pinned", !m.isPinned) } catch (e: Exception) { toast(e.message ?: "Unable to update message.") }
            }
        },
        onToggleReaction = { id, emoji ->
            repo.optimisticToggleReaction(conversation.id, id, emoji)
            scope.launch { try { repo.toggleReactionAsync(conversation.id, id, emoji) } catch (_: Exception) {} }
        },
        onForward = { forwardMessage = it },
        onClearConversation = {
            scope.launch {
                try {
                    if (api.isAuthenticated) api.clearConversationMessages(conversation.id)
                    repo.clearConversation(conversation.id)
                } catch (e: Exception) { toast(e.message ?: "Unable to clear conversation.") }
            }
        },
        onRetryMessage = { id ->
            scope.launch {
                val ok = try { ServiceLocator.outgoingQueue.retryMessage(id) } catch (_: Exception) { false }
                if (!ok) toast("Retry failed — check your connection.")
            }
        },
        onCreateTask = { title -> taskEditor = Triple(true, null, title) },
        onCreateTaskFromMessage = { m -> taskEditor = Triple(true, null, m.text) },
        tasks = convTasks,
        onOpenTaskDetails = { taskDetail = it },
        onEditTask = { taskEditor = Triple(true, it, null) },
        onUpdateTaskStatus = { id, status -> scope.launch { try { tasksController.updateTaskStatus(id, status) } catch (e: Exception) { toast(e.message ?: "Unable to update task.") } } },
        onDeleteTask = { id -> scope.launch { try { tasksController.deleteTask(id) } catch (e: Exception) { toast(e.message ?: "Unable to delete task.") } } },
        entryStyle = com.example.chat.ui.chats.detail.normalizeEntryStyle(conv.entryStyle),
        convPrefs = ServiceLocator.preferencesController.conversation,
        disableSwipeToExit = ServiceLocator.preferencesController.universal.disableSwipeToExit,
        homeAvatarShape = ServiceLocator.preferencesController.home.avatarShape,
        universalEmojiStyle = ServiceLocator.preferencesController.universal.emojiStyle,
        // Dart dataStore.contacts: api.users except the current user (chaty_data_store.dart:206-214).
        quickContacts = api.users.filter { it.id != api.currentUser?.id }.map {
            com.example.chat.ui.chats.detail.QuickContactEntry(it.id, it.displayName, it.avatarInitials, it.avatarColorHex)
        },
        // Dart: dataStore.getOrCreateDirectConversation(contact) then pushReplacement; errors swallowed.
        onOpenConversation = { contactId ->
            val contact = api.users.firstOrNull { it.id == contactId }
            if (contact != null) scope.launch {
                try { onOpenConversation(repo.getOrCreateDirectConversationAsync(contact)) } catch (_: Exception) {}
            }
        },
        attachmentBinding = attachmentBinding,
        voiceUrlResolver = { path ->
            ServiceLocator.chatMedia.createSignedUrl(path, 3600, restApiBaseUrl = { ServiceLocator.apiBackend.config.restApiBaseUrl })
        },
        translateOption = ServiceLocator.preferencesController.universal.translateOption,
        translationLanguage = ServiceLocator.preferencesController.universal.translationLanguage,
        composerColors = com.example.chat.ui.chats.detail.ComposerColors.resolve(
            theme, conv.uiButtonsColor, conv.emojiButtonColor, conv.sendButtonColor,
            conv.micSendBgCircleColor, conv.textEntryBackgroundColor, conv.textEntryColor,
        ),
        composerTemplate = ServiceLocator.templateController.composer,
        onAttachmentAction = { action ->
            when (action) {
                com.example.chat.ui.chats.detail.AttachmentAction.image -> attachments.onMedia("image")
                com.example.chat.ui.chats.detail.AttachmentAction.video -> attachments.onMedia("video")
                com.example.chat.ui.chats.detail.AttachmentAction.document -> attachments.onMedia("document")
                com.example.chat.ui.chats.detail.AttachmentAction.audio -> attachments.onMedia("audio")
                com.example.chat.ui.chats.detail.AttachmentAction.location -> attachments.onLocation()
                com.example.chat.ui.chats.detail.AttachmentAction.contact -> attachments.onContact()
                com.example.chat.ui.chats.detail.AttachmentAction.poll -> pollCreateOpen = true
                com.example.chat.ui.chats.detail.AttachmentAction.task -> taskEditor = Triple(true, null, null)
                // Demo sandbox dock (Dart chat_detail_screen.dart:1288-1309 -> chat_attachment_actions
                // sendDemo*); controller ports are exact, afterSend = rev++ like the other arms.
                com.example.chat.ui.chats.detail.AttachmentAction.demoDocument -> scope.launch { attachments.controller.sendDemoDocument(); rev++ }
                com.example.chat.ui.chats.detail.AttachmentAction.demoAudio -> scope.launch { attachments.controller.sendDemoAudio(); rev++ }
                com.example.chat.ui.chats.detail.AttachmentAction.demoCall -> scope.launch { attachments.controller.sendDemoCallCard(); rev++ }
                com.example.chat.ui.chats.detail.AttachmentAction.demoTask -> scope.launch { attachments.controller.sendDemoTask(); rev++ }
                com.example.chat.ui.chats.detail.AttachmentAction.demoLocation -> scope.launch { attachments.controller.sendDemoLocation(); rev++ }
                else -> toast("This attachment type is not connected in this build yet.")
            }
        },
        onOpenInfo = {
            if (!isDirect) infoRoute = 1
            else if (other != null) {
                infoRoute = 1
                infoLoading = true
                infoError = null
                infoConnection = ContactConnectionStatus()
                scope.launch {
                    try {
                        // Dart ContactInfoScreen._load commits both results only if both succeed.
                        // Keep its fresh default separate from the chat call-gate cache on error.
                        val nextConnection = ServiceLocator.contactRelationships.connectionStatus(other.id)
                        val nextBlocked = ServiceLocator.contactRelationships.isBlocked(other.id)
                        infoConnection = nextConnection
                        infoBlocked = nextBlocked
                    } catch (e: Exception) { infoError = e.message ?: "Unable to load contact." }
                    infoLoading = false
                }
            }
        },
        onOpenAppearance = { appearanceOpen = true },
        onPollTap = { m -> pollViewId = m.id },
        loadingMessages = loadingMessages,
        loadError = loadError,
        onRetryLoad = { loadNonce++ },
        onMediaTap = { m -> viewerBounds = null; viewerMessage = m },
        onMediaTapWithBounds = { m, rect -> viewerBounds = rect; viewerMessage = m },
        onViewOnceOpen = { m -> viewerBounds = null; viewerMessage = m },
        onCameraTap = { chatCameraOpen = true },
        onGalleryTap = { attachments.onMedia("image") },
        onAudioTap = { attachments.onMedia("audio") },
        onContactTap = { attachments.onContact() },
        onLocationTap = { attachments.onLocation() },
        onVoiceTap = voice.onVoiceTap,
        onVoiceHoldStart = voice.onVoiceHoldStart,
        onVoiceMove = voice.onVoiceMove,
        onVoiceHoldEnd = voice.onVoiceHoldEnd,
    )
    }
    voice.overlay()
    if (chatCameraOpen) {
        androidx.activity.compose.BackHandler { chatCameraOpen = false }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            com.example.chat.ui.camera.ChatyCameraCaptureScreen(
                mode = com.example.chat.ui.camera.ChatyCaptureMode.chat,
                contactName = live.title,
                onResult = { r ->
                    chatCameraOpen = false
                    scope.launch {
                        attachments.controller.sendCapturedPhoto(
                            path = r.path,
                            caption = r.caption,
                            effectId = r.effectId,
                            effectIntensity = r.effectIntensity,
                            onSent = {
                                try { rt.trackConversation(conversation.id) } catch (_: Exception) {}
                                com.example.chat.ui.kit.ChatyHapticsPort.success(hostView)
                                rev++
                            },
                        )
                    }
                },
                onDismiss = { chatCameraOpen = false },
            )
        }
    }
    if (infoRoute != 0) {
        androidx.activity.compose.BackHandler { infoRoute = if (infoRoute == 2) 1 else 0 }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            if (!isDirect) {
                com.example.chat.ui.chats.group_info.GroupInfoScreen(
                    conversation = live,
                    participants = live.participantIds.mapNotNull { id -> api.users.firstOrNull { it.id == id } },
                    currentUserId = myId,
                    onLeave = { infoRoute = 0; onBack() },
                    onBack = { infoRoute = 0 },
                )
            } else if (other != null && infoRoute == 2) {
                com.example.chat.ui.chats.contact_info.ContactPrivacyScreen(
                    contact = other,
                    privacyFor = { id -> ServiceLocator.contactRelationships.privacyFor(id) },
                    savePrivacy = { v -> ServiceLocator.contactRelationships.savePrivacy(v) },
                    onBack = { infoRoute = 1 },
                )
            } else if (other != null) {
                com.example.chat.ui.chats.contact_info.ContactInfoScreen(
                    conversation = live,
                    contact = other,
                    messages = messages,
                    connection = infoConnection,
                    blocked = infoBlocked,
                    loading = infoLoading,
                    loadError = infoError,
                    busy = infoBusy,
                    isOnline = { id -> rt.isOnline(id) },
                    lastSeenFor = { id -> rt.lastSeenFor(id) },
                    activityFor = { cid, uid -> rt.activityFor(cid, uid) },
                    hydrateMessage = { m -> rt.hydrateMessage(m) },
                    onAccept = {
                        infoBusy = true
                        try {
                            ServiceLocator.contactRelationships.acceptConnection(other.id)
                            refreshConnection()
                            infoConnection = ServiceLocator.contactRelationships.connectionStatus(other.id)
                        } finally { infoBusy = false }
                    },
                    onSetBlocked = { next ->
                        infoBusy = true
                        try {
                            ServiceLocator.contactRelationships.setBlocked(other.id, next)
                            infoBlocked = next
                            refreshConnection()
                            infoConnection = ServiceLocator.contactRelationships.connectionStatus(other.id)
                        } finally { infoBusy = false }
                    },
                    onDeleteConversation = {
                        // Dart dataStore.deleteConversation: api first when authenticated, then repository.
                        scope.launch {
                            if (api.isAuthenticated) { try { api.deleteConversation(live.id) } catch (_: Exception) {} }
                            try { repo.deleteConversation(live.id) } catch (_: Exception) {}
                        }
                    },
                    onDeleteUser = { if (api.isAuthenticated) api.deleteUser(other.id) },
                    onOpenPrivacy = { infoRoute = 2 },
                    onOpenMedia = { m -> viewerMessage = m },
                    onSearch = { infoRoute = 0 },
                    onDeletedPopToRoot = { infoRoute = 0; onBack() },
                    onBack = { infoRoute = 0 },
                )
            }
        }
    }
    if (pollCreateOpen) {
        CreatePollDialog(
            onCancel = { pollCreateOpen = false },
            onCreate = { draft ->
                pollCreateOpen = false
                scope.launch { attachments.controller.createPoll(draft); rev++ }
            },
        )
    }
    val pv = pollViewId
    if (pv != null) {
        PollViewerSheet(
            messageId = pv,
            controller = attachments.controller,
            onDismiss = { pollViewId = null; scope.launch { try { api.loadMessages(conversation.id) } catch (_: Exception) {} } },
        )
    }
    if (appearanceOpen) {
        androidx.activity.compose.BackHandler { appearanceOpen = false }
        val customization = com.example.chat.ui.core.customization.rememberLoadedCustomizationController()
        val overlayController = com.example.chat.ui.core.components.overlays.rememberChatyOverlayController()
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            com.example.chat.ui.settings.appearance.ComponentStylesScreen(
                controller = customization,
                overlayController = overlayController,
                onBack = { appearanceOpen = false },
                conversationId = conversation.id,
                conversationTitle = live.title,
            )
        }
    }
    val vm = viewerMessage?.let { requested ->
        messages.firstOrNull { it.id == requested.id && !it.isDeletedForEveryone && !it.isDeletedForMe && !it.isRevokedBySender }
    }
    val va = vm?.attachment
    if (vm != null && va != null) {
        androidx.activity.compose.BackHandler { viewerMessage = null }
        androidx.compose.runtime.key(attachmentBinding.session, conversation.id, vm.id) {
            com.example.chat.ui.chats.detail.BoundAttachmentViewer(
                theme, vm, attachmentBinding,
                restricted = com.example.chat.data.local.AttachmentCachePolicy.viewOnceRestricted(vm.metadata),
                bounds = viewerBounds, onBack = { viewerMessage = null },
            )
        }
    }

    }

    if (gateOpen) {
        val st = connection ?: ContactConnectionStatus()
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { gateOpen = false },
            title = { Text(if (st.callsAllowed) "Calls are unlocked" else "Contact acceptance required") },
            text = {
                Text(
                    if (st.callsAllowed) "Both people accepted this contact connection."
                    else if (st.isWaitingForOther) "You accepted ${other?.displayName ?: live.title}. Calls will unlock after they accept you too."
                    else "Chatting is available now. Voice and video calls unlock only after both people accept the contact request.",
                )
            },
            confirmButton = {
                if (!st.myAccepted) {
                    TextButton(onClick = {
                        gateOpen = false
                        scope.launch {
                            try { if (otherId != null) ServiceLocator.contactRelationships.acceptConnection(otherId); refreshConnection() }
                            catch (e: Exception) { toast("$e") }
                        }
                    }) { Text("Accept contact") }
                } else TextButton(onClick = { gateOpen = false }) { Text("Done") }
            },
        )
    }
    if (blockConfirm) {
        val name = other?.displayName ?: live.title
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { blockConfirm = false },
            title = { Text("Block contact?") },
            text = { Text("$name will no longer be able to send you messages or call you.") },
            dismissButton = { TextButton(onClick = { blockConfirm = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    blockConfirm = false
                    scope.launch {
                        try {
                            if (otherId != null) ServiceLocator.contactRelationships.setBlocked(otherId, true)
                            toast("$name was blocked.")
                        } catch (_: Exception) { toast("Could not block this contact.") }
                    }
                }) { Text("Block") }
            },
        )
    }
    forwardMessage?.let { fm ->
        // Dart _openForwardSheet (chat_detail_screen.dart:2337-2519): sectioned targets
        // (ForwardTargets), 5/250 limit, and the full-envelope send - text, type and
        // attachment by reference plus {'forwarded': labelAllowed} metadata
        // (ForwardSend). Deviation kept from the previous wiring: the sheet container
        // stays an AlertDialog instead of Dart's modal bottom sheet.
        val forwardPrefs = ServiceLocator.preferencesController
        val forwardItems = ForwardTargets.build(
            conversations = api.conversations,
            currentConversationId = conversation.id,
            messageCount = { id -> api.getMessages(id).size },
            increaseForwardLimit = forwardPrefs.universal.increaseForwardLimit,
            hideFrequentlyContacted = forwardPrefs.home.hideFrequentlyContacted,
            hideRecentChats = forwardPrefs.home.hideRecentChats,
            hideOtherContacts = forwardPrefs.home.hideOtherContacts,
        )
        if (forwardItems.isEmpty()) {
            LaunchedEffect(fm) { toast("No other chats to forward to."); forwardMessage = null }
        } else {
            // Dart showModalBottomSheet (chat_detail_screen.dart:2402-2499): isScrollControlled,
            // surfaceColor sheet, top rounded 18, max height 0.7x screen, shortcut icon + limit
            // title, sectioned avatar rows.
            ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp,
                onDismissRequest = { forwardMessage = null },
                containerColor = Color(theme.surfaceColor),
                shape = com.example.chat.ui.kit.ChatyModalShape,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.7f).dp),
                ) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Shortcut,
                            contentDescription = null,
                            tint = Color(theme.accentColor),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Forward to (Limit: ${ForwardTargets.maxChats(forwardPrefs.universal.increaseForwardLimit)})",
                            color = Color(theme.primaryTextColor),
                            fontWeight = FontWeight.W800,
                            fontSize = 15.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn {
                        items(forwardItems.size) { index ->
                            val item = forwardItems[index]
                            val showHeader = index == 0 || forwardItems[index - 1].section != item.section
                            Column {
                                if (showHeader) {
                                    Text(
                                        item.section.uppercase(),
                                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
                                        color = Color(theme.secondaryTextColor),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.W800,
                                        letterSpacing = 0.7.sp,
                                    )
                                }
                                TextButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        forwardMessage = null
                                        scope.launch {
                                            val labelAllowed = ForwardSend.labelAllowed(
                                                disableForwardedLabel = forwardPrefs.privacy.disableForwardedLabel,
                                                yoDisableFwd = forwardPrefs.gbBool("yoDisableFwd"),
                                            )
                                            val payload = ForwardSend.payload(fm, labelAllowed)
                                            try {
                                                api.sendMessage(
                                                    item.conversation.id,
                                                    payload.text,
                                                    payload.type,
                                                    payload.attachment,
                                                    payload.extraMetadata,
                                                )
                                                toast("Forwarded to ${item.conversation.title}.")
                                            } catch (e: Exception) {
                                                toast("Could not forward: ${e.message}")
                                            }
                                        }
                                    },
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        HomeAvatar(
                                            initials = item.conversation.avatarInitials
                                                ?: item.conversation.title.take(2).uppercase(),
                                            color = parseAvatarColorHex(
                                                item.conversation.avatarColorHex,
                                                Color(0xFF6366F1),
                                            ),
                                            size = 36f,
                                            shape = "circle",
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            item.conversation.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color(theme.primaryTextColor),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    val ed = taskEditor
    if (ed != null && taskUser != null) {
        com.example.chat.ui.tasks.TaskCreateEditModal(
            controller = tasksController,
            colors = com.example.chat.ui.tasks.TaskColors(ServiceLocator.themeController.globalTheme),
            conversations = api.conversations,
            currentUser = taskUser,
            getUser = lookup,
            sourceConversationId = conversation.id,
            existingTask = ed.second,
            initialTitle = ed.third,
            onDismiss = { taskEditor = null },
            onSaved = { msg -> taskEditor = null; toast(msg) },
            onError = { msg -> toast(msg) },
        )
    }
    val td = taskDetail
    if (td != null && taskUser != null) {
        com.example.chat.ui.tasks.TaskDetailScreen(
            task = td,
            controller = tasksController,
            colors = com.example.chat.ui.tasks.TaskColors(ServiceLocator.themeController.globalTheme),
            currentUser = taskUser,
            getUser = lookup,
            onBack = { taskDetail = null },
            onEditTask = { taskEditor = Triple(true, it, null) },
        )
    }
}
