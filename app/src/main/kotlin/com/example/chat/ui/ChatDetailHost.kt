package com.example.chat.ui

import androidx.lifecycle.repeatOnLifecycle

import com.example.chat.ReleaseFeatures

import android.widget.Toast
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shortcut
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.chat.data.api.ApiPresenceClient
import com.example.chat.ui.chats.home.parseAvatarColorHex
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Real host for ChatDetailScreen. Mirrors how Dart ChatDetailScreen wires its
 *  services (chat_detail_screen.dart _buildChat/_executeMessageAction/_startCall/
 *  _openChatMenu) over the Dart ChatyDataStore routing: api backend first when
 *  authenticated, Supabase-backed repository side tolerated on error.
 *  Known deviations are listed in docs/chat-detail-host-deviations.md semantics:
 *  presence refresh is a 3s tick (richRealtime exposes no listener API);
 *  forward sends text only; task modal has no source-message link param. */
/** Internal synthetic instrumentation observation only; never replaces capture flow/auth.
 *  Fixture restored so the pinned candidate's RealHostVideoHoldProofTest compiles; the
 *  hold-to-record host wiring itself is NOT ported yet, so that test fails honestly at
 *  runtime until the video-hold flow is merged. */
internal object ChatVideoHoldNativeProof {
    @Volatile var onEntry: ((com.example.chat.ui.AttachFlow, com.example.chat.ui.camera.VideoHoldRequest) -> Unit)? = null
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailHost(
    conversation: Conversation,
    initialMessageId: String?,
    onBack: () -> Unit,
    /** Dart quick-contact tap: Navigator.pushReplacement(ChatDetailScreen(next.id)). */
    onOpenConversation: (Conversation) -> Unit = {},
    localOverrideAccountKey: String? = null,
) {
    val api = ServiceLocator.apiBackend
    val themes = ServiceLocator.themeController
    var visualRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(api, themes) {
        val changed: () -> Unit = { visualRevision++ }
        api.addListener(changed)
        themes.addListener(changed)
        onDispose { api.removeListener(changed); themes.removeListener(changed) }
    }
    @Suppress("UNUSED_EXPRESSION") visualRevision
    val account = api.authSnapshot()
    val accountKey = com.example.chat.core.messages.LocalMessageOverridePolicy.accountKey(
        account.tenantId, account.baseUrl, api.currentUser?.id.orEmpty(),
    )
    val chatThemeKey = accountKey?.let {
        com.example.chat.core.messages.LocalMessageOverridePolicy.key(it, conversation.id, "chat-theme")
    }
    val scopedTheme = chatThemeKey?.let(themes::getChatThemeOverride)
    com.example.chat.ui.theme.ChatyTheme(config = scopedTheme ?: LocalThemeConfig.current) {
        androidx.compose.runtime.key(chatThemeKey, account) {
            ChatDetailHostContent(conversation, initialMessageId, onBack, onOpenConversation,
                localOverrideAccountKey, chatThemeKey, scopedTheme != null)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChatDetailHostContent(
    conversation: Conversation,
    initialMessageId: String?,
    onBack: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    localOverrideAccountKey: String?,
    chatThemeKey: String?,
    hasChatThemeOverride: Boolean,
) {
    val vaultProtection = ServiceLocator.hiddenVaultController
    var vaultPrivacyRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(vaultProtection) {
        val listener: () -> Unit = { vaultPrivacyRevision++ }
        vaultProtection.addListener(listener)
        onDispose { vaultProtection.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") vaultPrivacyRevision
    com.example.chat.ui.kit.ProtectSensitiveWindow(vaultProtection.isConversationLocked(conversation.id))
    val api = ServiceLocator.apiBackend
    val p2pTransfers = ServiceLocator.p2pFileTransfers
    val liveNetworkStatus by ServiceLocator.networkMonitor.state.collectAsState()
    val liveP2pTransfers by p2pTransfers.transfers.collectAsState()
    val liveTransferItems by ServiceLocator.transferMonitor.items.collectAsState()
    // Do not keep the subscriber-gated 1s traffic sampler awake off-screen.
    val trafficOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val trafficState = ServiceLocator.appTrafficMonitor.state
    val liveAppTraffic by androidx.compose.runtime.produceState(trafficState.value, trafficState, trafficOwner) {
        trafficOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            trafficState.collect { value = it }
        }
    }
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
    // Phase 1 per-conversation encryption flag: when on, this chat's send/read
    // path rides the MLS plane (ConversationsRepository -> SecureMessageTransport
    // -> MlsE2eeService -> /e2ee/*) instead of the plaintext REST plane.
    val secureRoutingAccount = api.authSnapshot()
    var secureChannel by remember(conversation.id, secureRoutingAccount) { mutableStateOf(false) }
    var secureChannelResolved by remember(conversation.id, secureRoutingAccount) { mutableStateOf(false) }
    val secureRoutingAvailable = com.example.chat.ReleaseFeatures.EXPERIMENTAL_MLS_CHAT_ROUTING
    fun requireSendRouting() {
        check(api.authSnapshot() === secureRoutingAccount) { "Account changed. Open the chat again." }
        check(secureChannelResolved) { "Encryption preference could not be checked. Sending is blocked; reopen this chat to retry." }
        check(!secureChannel || secureRoutingAvailable) { "Secure routing is not available in this build. Nothing was sent." }
    }
    LaunchedEffect(conversation.id, secureRoutingAccount) {
        try {
            val requested = repo.isSecureChannel(conversation.id)
            api.withAuthScope(secureRoutingAccount) {
                secureChannel = requested
                secureChannelResolved = true
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            api.withAuthScope(secureRoutingAccount) {
                loadError = "Could not check encryption preference. Sending is blocked; reopen this chat to retry."
                loadingMessages = false
            }
        }
    }
    LaunchedEffect(conversation.id, loadNonce, secureChannel, secureChannelResolved) {
        // Wait for the flag so a flagged chat never flashes a plaintext fetch.
        if (!secureChannelResolved) return@LaunchedEffect
        if (secureChannel && !secureRoutingAvailable) {
            loadError = "Secure routing is unavailable in this build."
            loadingMessages = false
            return@LaunchedEffect
        }
        loadError = null
        loadingMessages = if (secureChannel) {
            repo.messagesByChatId[conversation.id].isNullOrEmpty()
        } else {
            api.getMessages(conversation.id).isEmpty()
        }
        try {
            if (secureChannel) {
                repo.loadMessages(conversation.id)
                repo.markAsRead(conversation.id)
            } else {
                if (api.isAuthenticated) api.loadMessagesIfNeeded(conversation.id, force = loadNonce > 0)
            }
            rt.trackConversation(conversation.id)
            if (!secureChannel) rt.markConversationDelivered(conversation.id)
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
    var cameraEntryFlow by remember { mutableStateOf<com.example.chat.ui.AttachFlow?>(null) }
    var cameraEntryAuth by remember { mutableStateOf<com.example.chat.ui.camera.ApiVideoAuthPort?>(null) }
    var cameraEntryPreset by remember { mutableStateOf(com.example.chat.ui.camera.CameraEntryPreset.photoBack) }
    // Composer front-video hold: one gesture = one token, created only with this open's
    // original entry authority; a stale or duplicate gesture id never touches it.
    var videoHoldRequest by remember { mutableStateOf<com.example.chat.ui.camera.VideoHoldRequest?>(null) }
    var videoHoldId by remember { mutableStateOf<Long?>(null) }
    var p2pReceiveMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var p2pReceiveLink by remember { mutableStateOf<String?>(null) }
    var p2pReceiveOriginal by remember { mutableStateOf(false) }
    var p2pActionTransfer by remember { mutableStateOf<String?>(null) }
    var p2pLocalViewer by remember { mutableStateOf<File?>(null) }
    fun openDirectMessage(message: ChatMessage) {
        val path = message.attachment?.url.orEmpty()
        val id = path.substringAfterLast('/')
        if (message.senderId == api.currentUser?.id) { p2pActionTransfer = id; return }
        val receiveAccount = api.authSnapshot()
        scope.launch {
            try {
                val saved = p2pTransfers.savedDirectTicket(id, message.senderId)
                val link: String
                val original: Boolean
                if (saved != null) { link = saved; original = true }
                else {
                    val ciphertext = message.metadata["p2p_invite_ciphertext"]?.toString()
                        ?: throw SecurityException("This transfer invitation is incomplete.")
                    val payload = ServiceLocator.mls.decryptPayload(conversation.id, ciphertext)
                    if (payload["kind"] != "chaty_p2p_invite_v1" || payload["transfer_id"]?.toString() != id) {
                        throw SecurityException("Transfer invitation does not match this media.")
                    }
                    val secret = payload["secret"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: throw SecurityException("Transfer invitation key is missing.")
                    original = payload["direct_original_v2"] == true
                    link = "chaty://transfer/" + id + "#" + secret
                    if (original) p2pTransfers.saveDirectTicket(link, message.senderId)
                }
                val verified = if (original) p2pTransfers.verifiedOriginalFile(id, message.senderId) else {
                    p2pTransfers.transfers.value.firstOrNull { it.transferId == id && it.status == "Received and verified" }
                        ?.localFilePath?.let(::File)?.takeIf { it.isFile }
                }
                check(api.authSnapshot() === receiveAccount) { "Account changed. Reopen this media." }
                if (verified != null) p2pLocalViewer = verified
                else if (p2pTransfers.transfers.value.any { it.transferId == id && it.status in setOf("Sending", "Receiving", "Connecting", "Waiting for secure data channel", "Secure channel connected", "Secure channel ready", "Requesting secure reconnect", "Reconnecting directly…", "Connection interrupted — retrying") }) {
                    p2pActionTransfer = id
                } else {
                    p2pReceiveOriginal = original
                    p2pReceiveLink = link
                    p2pReceiveMessage = message
                }
            } catch (e: Exception) { toast(e.message ?: "Unable to unlock this transfer invitation.") }
        }
    }

    val hostView = androidx.compose.ui.platform.LocalView.current
    val me = api.currentUser
    val myId = me?.id ?: ""
    val messageAccountScope = api.authSnapshot()
    val detectedLocalOverrideAccountKey = com.example.chat.core.messages.LocalMessageOverridePolicy.accountKey(
        messageAccountScope.tenantId, messageAccountScope.baseUrl, myId) ?: ""
    val effectiveLocalOverrideAccountKey = localOverrideAccountKey ?: detectedLocalOverrideAccountKey

    val live = api.conversations.firstOrNull { it.id == conversation.id } ?: conversation
    // Secure chats read decrypted MLS rows; other chats read the REST plane.
    // Recomposition is driven by the existing rev tick and post-action rev++.
    val messages = if (!secureChannelResolved) emptyList() else if (secureChannel) {
        repo.messagesByChatId[conversation.id] ?: emptyList()
    } else {
        api.getMessages(conversation.id)
    }
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
    val otherId = live.participantIds.firstOrNull { it != myId } ?: live.participantIds.firstOrNull()
    val other = api.users.firstOrNull { it.id == otherId } ?: otherId?.let { id ->
        com.example.chat.domain.models.UserProfile(
            id = id,
            displayName = live.title,
            username = live.title,
            avatarInitials = live.avatarInitials.takeIf { !it.isNullOrBlank() } ?: "C",
            avatarColorHex = live.avatarColorHex ?: "0xFF6366F1",
            about = "",
            lastSeenAt = java.time.Instant.now(),
        )
    }
    val conv = prefs.conversation
    val privacy = prefs.privacy
    fun gbB(k: String, d: Boolean) = prefs.gbBool(k, d)

    var connection by remember(conversation.id, messageAccountScope) { mutableStateOf<ContactConnectionStatus?>(null) }
    suspend fun refreshConnection() {
        if (isDirect && otherId != null) {
            try {
                val status = ServiceLocator.contactRelationships.connectionStatus(otherId, api.contactsPlane(messageAccountScope))
                if (api.authSnapshot() === messageAccountScope) connection = status
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { if (api.authSnapshot() === messageAccountScope) connection = null }
        }
    }
    LaunchedEffect(conversation.id, otherId, messageAccountScope) { refreshConnection() }
    var directOriginalSelection by remember { mutableStateOf(false) }
    val largeFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { selectedUri ->
        if (selectedUri != null) scope.launch {
            val directOriginal = directOriginalSelection
            val sendAccount = api.authSnapshot()
            var sourceOwner: android.os.ParcelFileDescriptor.AutoCloseInputStream? = null
            var transferId: String? = null
            try {
                requireSendRouting()
                check(!secureChannel) { "Direct transfer invitations are not supported in secure-routing chats yet." }
                val sourceReadPermissionPersisted = runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        selectedUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    true
                }.getOrElse {
                    context.contentResolver.persistedUriPermissions.any {
                        it.uri == selectedUri && it.isReadPermission
                    }
                }
                val details = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    var name = "video"
                    var size: Long? = null
                    context.contentResolver.query(selectedUri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                        }
                    }
                    name to size
                }
                val safeName = com.example.chat.data.services.ChatMediaService.safeFileName(details.first)
                val reportedMime = context.contentResolver.getType(selectedUri) ?: "application/octet-stream"
                val extension = safeName.substringAfterLast('.', "").lowercase()
                val isVideoFile = reportedMime.startsWith("video/") || extension in setOf("mp4", "mkv", "mov", "webm", "m4v", "avi", "3gp", "mpeg", "mpg")
                val isImageFile = reportedMime.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
                val mime = if (isVideoFile && !reportedMime.startsWith("video/")) {
                    when (extension) { "mkv" -> "video/x-matroska"; "3gp" -> "video/3gpp"; else -> "video/mp4" }
                } else if (isImageFile && !reportedMime.startsWith("image/")) {
                    when (extension) { "jpg" -> "image/jpeg"; "heic" -> "image/heic"; else -> "image/$extension" }
                } else reportedMime
                if (directOriginal && !isVideoFile && !isImageFile) throw IllegalStateException("Chaty Direct supports original images and videos only.")
                if (directOriginal && !sourceReadPermissionPersisted) throw IllegalStateException("Choose a file provider that allows saved access so Chaty Direct can resume the original after restart.")
                if (!isDirect || otherId == null) throw IllegalStateException("Large direct transfers are currently available in one-to-one chats.")
                if (connection?.callsAllowed == false) throw IllegalStateException("Accept this contact connection before sending a large file.")
                val descriptor = context.contentResolver.openFileDescriptor(selectedUri, "r")
                    ?: throw IllegalStateException("The selected file provider did not return a readable descriptor.")
                sourceOwner = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                val channel = sourceOwner!!.channel
                val size = details.second?.takeIf { it > 0L } ?: channel.size()
                if (size !in 1..com.example.chat.data.transfer.NativeP2pTransferProtocol.MAX_FILE_BYTES) {
                    throw IllegalStateException("This file is empty, not seekable, or larger than 100 GB.")
                }
                check(api.authSnapshot() === sendAccount) { "Account changed. Select the media again." }
                requireSendRouting()
                check(!secureChannel) { "Direct transfers are unavailable in secure-routing chats." }
                val link = p2pTransfers.offerFile(
                    otherId, channel, safeName, size, mime,
                    sourceOwner = sourceOwner,
                    sourceUri = selectedUri.toString().takeIf { sourceReadPermissionPersisted },
                    directOriginal = directOriginal,
                )
                sourceOwner = null // service owns the file descriptor until transfer completion
                val invite = com.example.chat.data.transfer.NativeP2pTransferProtocol.parseInvite(link)
                transferId = invite.transferId
                val secretText = android.util.Base64.encodeToString(
                    invite.secret,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
                )
                invite.secret.fill(0)
                // The invitation secret is MLS-encrypted before it is placed in chat content.
                val encryptedInvite = ServiceLocator.mls.encryptPayload(
                    conversation.id,
                    mapOf("kind" to "chaty_p2p_invite_v1", "transfer_id" to transferId, "secret" to secretText,
                        "direct_original_v2" to directOriginal),
                )
                val attachment = com.example.chat.domain.models.MessageAttachment(
                    id = transferId!!,
                    type = when { isVideoFile -> "video"; isImageFile -> "image"; else -> "document" },
                    name = safeName,
                    size = com.example.chat.data.services.ChatMediaService.formatBytes(size),
                    url = "chaty://transfer/" + transferId,
                )
                check(api.authSnapshot() === sendAccount) { "Account changed. Select the media again." }
                requireSendRouting()
                check(!secureChannel) { "Direct transfers are unavailable in secure-routing chats." }
                val sent = api.sendMessage(
                    conversation.id,
                    "",
                    type = when {
                        isVideoFile -> com.example.chat.domain.models.MessageType.video
                        isImageFile -> com.example.chat.domain.models.MessageType.image
                        else -> com.example.chat.domain.models.MessageType.document
                    },
                    attachment = attachment,
                    extraMetadata = mapOf(
                        "p2p_invite_ciphertext" to encryptedInvite.ciphertext,
                        "p2p_invite_group" to encryptedInvite.groupId,
                        "p2p_invite_epoch" to encryptedInvite.epoch,
                        "p2p_transfer_version" to 1,
                    ),
                    expectedScope = sendAccount,
                )
                if (sent == null) throw IllegalStateException("The transfer offer was created but the chat message could not be sent.")
                if (directOriginal) {
                    // The durable small chat ticket now exists; begin the existing offer event.
                    p2pTransfers.retryTransfer(transferId!!)
                }
                toast(
                    if (directOriginal) "Chaty Direct Original ready. Both phones need internet and Chaty open; no server media copy."
                    else if (sourceReadPermissionPersisted) "Secure transfer link sent."
                    else "Secure transfer link sent. Keep Chaty open: this provider does not allow restart recovery.",
                )
                rev++
            } catch (e: Exception) {
                transferId?.let { p2pTransfers.cancel(it) }
                runCatching { sourceOwner?.close() }
                toast(e.message ?: "Could not start the large-file transfer.")
            }
        }
    }
    var blockConfirm by remember { mutableStateOf(false) }
    var blockTarget by remember(conversation.id, messageAccountScope) { mutableStateOf<String?>(null) }
    var reportTarget by remember(conversation.id, messageAccountScope) { mutableStateOf<ChatMessage?>(null) }
    var safetyBusy by remember { mutableStateOf(false) }
    var safetyError by remember { mutableStateOf<String?>(null) }
    var forwardMessages by remember(conversation.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var forwardScope by remember(conversation.id) { mutableStateOf<com.example.chat.data.api.ApiAccountScope?>(null) }
    var taskEditor by remember { mutableStateOf<Triple<Boolean, ChatTask?, String?>?>(null) }
    var taskDetail by remember { mutableStateOf<ChatTask?>(null) }
    // 0 none, 1 info (ContactInfo/GroupInfo), 2 contact privacy; viewer = MediaViewerScreen target.
    var infoSearchRequest by remember(conversation.id, mediaStamp) { mutableIntStateOf(0) }
    var infoRoute by remember(conversation.id, messageAccountScope) { mutableIntStateOf(0) }
    var viewerBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var viewerMessage by remember(conversation.id, mediaStamp) { mutableStateOf<ChatMessage?>(null) }
    var pollCreateOpen by remember(conversation.id) { mutableStateOf(false) }
    var pollViewId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var appearanceOpen by remember(conversation.id) { mutableStateOf(false) }
    var infoBusy by remember(conversation.id, messageAccountScope) { mutableStateOf(false) }
    var infoConnection by remember(conversation.id, messageAccountScope) { mutableStateOf(ContactConnectionStatus()) }
    var infoBlocked by remember(conversation.id, messageAccountScope) { mutableStateOf(false) }
    val senderSafetyStore = remember(context) { context.getSharedPreferences("chaty_sender_safety_v1", android.content.Context.MODE_PRIVATE) }
    val senderSafetyKey = "continued:$effectiveLocalOverrideAccountKey:${conversation.id}:$otherId"
    var senderContinued by remember(senderSafetyKey, messageAccountScope) {
        mutableStateOf(effectiveLocalOverrideAccountKey.isNotBlank() && senderSafetyStore.getBoolean(senderSafetyKey, false))
    }

    var infoLoading by remember(conversation.id, messageAccountScope) { mutableStateOf(false) }
    var infoError by remember(conversation.id, messageAccountScope) { mutableStateOf<String?>(null) }
    var infoProfile by remember(conversation.id, messageAccountScope) { mutableStateOf<com.example.chat.domain.models.UserProfile?>(null) }

    // Presence text (Dart _buildChat).
    // Presence and typing come from the custom backend socket (ApiBackendService); the legacy
    // Supabase realtime plane (rt) is only consulted for the recording indicator.
    val activity = otherId?.let { rt.activityFor(conversation.id, it) }
    val otherTyping = otherId != null && api.isTyping(conversation.id, otherId)

    // Presence plane (research 5.2): one-shot privacy-filtered fetch on open,
    // then presence.changed keeps it live. Fail-closed: a pre-plane server
    // (or a hidden user) shows no line at all.
    var fetchedPresence by remember(conversation.id, otherId, messageAccountScope) { mutableStateOf<ApiPresenceClient.PresenceView?>(null) }
    var fetchedPresenceRevision by remember(conversation.id, otherId, messageAccountScope) { androidx.compose.runtime.mutableLongStateOf(-1L) }
    LaunchedEffect(conversation.id, otherId, messageAccountScope) {
        if (isDirect && otherId != null && api.authSnapshot() === messageAccountScope) {
            val revision = api.presenceRevisionOf(otherId)
            val result = try {
                api.fetchPresence(otherId, messageAccountScope)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ApiPresenceClient.PresenceView.Unavailable
            }
            if (api.authSnapshot() === messageAccountScope && revision == api.presenceRevisionOf(otherId)) {
                fetchedPresence = result
                fetchedPresenceRevision = revision
            }
        }
    }
    // Any newer socket privacy shape, including all-hidden, supersedes the one-shot fetch.
    val currentFetchedPresence = fetchedPresence.takeIf { otherId != null && fetchedPresenceRevision == api.presenceRevisionOf(otherId) }
    val presenceOnline = isDirect && otherId != null &&
        (api.isUserOnline(otherId) || (currentFetchedPresence is ApiPresenceClient.PresenceView.Online &&
            api.lastSeenOf(otherId) == null && api.approxPresenceOf(otherId) == null))
    val presenceText: String? = if (isDirect) {
        val fetchedLastSeen = (currentFetchedPresence as? ApiPresenceClient.PresenceView.LastSeen)?.at?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
        val fetchedApprox = (currentFetchedPresence as? ApiPresenceClient.PresenceView.Approx)?.bucket
        when {
            activity?.isRecording == true -> "recording voice message…"
            otherId == null -> null
            else -> com.example.chat.ui.chats.PresenceLineLogic.headerText(
                presenceOnline,
                api.lastSeenOf(otherId) ?: fetchedLastSeen,
                api.approxPresenceOf(otherId) ?: fetchedApprox,
            )
        }
    } else {
        if (api.anyoneTyping(conversation.id)) "someone is typing…" else "${live.participantIds.size} participants"
    }

    val wallpaperKey = remember(conversation.id, effectiveLocalOverrideAccountKey) {
        if (effectiveLocalOverrideAccountKey.isNotEmpty()) "${effectiveLocalOverrideAccountKey}:${conversation.id}" else conversation.id
    }
    val wallpaperStore = remember { context.getSharedPreferences("chaty_chat_wallpapers", 0) }
    val wallpaperInitial = remember(conversation.id, wallpaperKey) {
        try {
            val obj = JSONObject(wallpaperStore.getString("chaty_chat_wallpapers_v1", "{}") ?: "{}")
            obj.optString(wallpaperKey, "").ifEmpty { null }
                ?: if (wallpaperKey == conversation.id) null else obj.optString(conversation.id, "").ifEmpty { null }
        } catch (_: Exception) { null }
    }
    val colorOverrides = if (hasChatThemeOverride) com.example.chat.ui.chats.detail.BubbleColorOverrides() else com.example.chat.ui.chats.detail.BubbleColorOverrides(
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

    val stickerScope = api.authSnapshot()
    val attachments = rememberChatAttachments(conversation.id, afterSend = { rev++ }, secureChannel = { !secureChannelResolved || secureChannel })
    fun closeChatCamera() {
        videoHoldRequest?.close()
        videoHoldRequest = null
        videoHoldId = null
        chatCameraOpen = false
        cameraEntryFlow?.let(attachments.closeCaptureSession)
        cameraEntryFlow = null
        cameraEntryAuth = null
    }
    fun openChatCamera(preset: com.example.chat.ui.camera.CameraEntryPreset) {
        if (!secureChannelResolved || secureChannel) { toast("Camera media cannot be sent until secure-media support is ready."); return }
        closeChatCamera()
        var openedFlow: com.example.chat.ui.AttachFlow? = null
        val flow = attachments.openCaptureSession {
            if (cameraEntryFlow === openedFlow) closeChatCamera()
        } ?: return
        openedFlow = flow
        if (flow.isCancelled) { attachments.closeCaptureSession(flow); return }
        cameraEntryFlow = flow
        cameraEntryAuth = com.example.chat.ui.camera.ApiVideoAuthPort(api, flow.expectedScope)
        cameraEntryPreset = preset
        chatCameraOpen = true
    }
    DisposableEffect(api, conversation.id) {
        onDispose { videoHoldRequest?.close(); cameraEntryFlow?.let(attachments.closeCaptureSession) }
    }
    // One call entry point for the chat toolbar and contact info. Lookup failures never unlock calls.
    var callStarting by remember(conversation.id, messageAccountScope) { mutableStateOf(false) }
    fun startContactCall(isVideo: Boolean) {
        if (callStarting) return
        val targetId = otherId
        if (!isDirect || targetId == null) { toast("Direct contact calls are available for one-to-one chats."); return }
        val expected = messageAccountScope
        if (api.authSnapshot() !== expected) { toast("Account changed. Open the chat again."); return }
        callStarting = true
        scope.launch {
            try {
                // Keep the explicit outgoing block check. The server separately enforces
                // callee privacy and bidirectional blocks before forwarding the offer.
                check(!ServiceLocator.contactRelationships.isBlocked(targetId, api.contactsPlane(expected))) { "Unblock this contact before calling." }
                check(api.authSnapshot() === expected) { "Account changed. Call cancelled." }
                ServiceLocator.callPresentationController.showFullScreen()
                ServiceLocator.callSignaling.initiateCall(
                    targetId, other?.displayName ?: live.title,
                    other?.avatarInitials, other?.avatarColorHex, isVideo,
                    expectedAccountScope = expected,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (api.authSnapshot() === expected) toast(error.message ?: "Unable to verify call permission.")
            } finally { callStarting = false }
        }
    }
    val voice = rememberChatVoice(
        conversation.id,
        afterSend = { rev++ },
        clearTyping = { api.sendTyping(conversation.id, false, messageAccountScope) },
        secureChannel = { !secureChannelResolved || secureChannel },
    )
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    androidx.compose.runtime.key(mediaStamp, conversation.id) {
    ChatDetailScreen(
        conversation = live,
        ghostModeEnabled = prefs.home.ghostMode,
        onToggleGhostMode = { prefs.updateHome(prefs.home.copy(ghostMode = !prefs.home.ghostMode)) },
        secureChannelEnabled = secureChannel,
        onToggleSecureChannel = if (!secureRoutingAvailable || !secureChannelResolved) null else ({
            val next = !secureChannel
            // Close the send gate synchronously, before any suspended setup/storage work.
            secureChannelResolved = false
            voice.cancelActiveRecording()
            closeChatCamera()
            scope.launch {
                try {
                    check(api.authSnapshot() === secureRoutingAccount) { "Account changed. Open the chat again." }
                    if (next) repo.ensureSecureConversationReady(conversation.id)
                    check(api.authSnapshot() === secureRoutingAccount) { "Account changed. Open the chat again." }
                    val transition = repo.setSecureChannel(conversation.id, next, secureRoutingAccount)
                    val heldCount = transition.heldCount
                    api.withAuthScope(secureRoutingAccount) {
                        secureChannel = next
                        secureChannelResolved = true
                        rev++
                        toast(
                            if (next) "Experimental routing requested; delivery is unverified. $heldCount regular messages held, not sent. Earlier messages unchanged."
                            else "Secure routing is off on this device. New messages use the regular server-readable route."
                        )
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    // Re-read the durable truth after any failed setup/write. If unreadable,
                    // retain unknown and keep sending blocked rather than assume the old flag.
                    try {
                        val actual = repo.isSecureChannel(conversation.id)
                        api.withAuthScope(secureRoutingAccount) {
                            secureChannel = actual
                            secureChannelResolved = true
                        }
                    } catch (_: Exception) { /* Remain unresolved: no plaintext fallback. */ }
                    if (api.authSnapshot() === secureRoutingAccount) toast(e.message ?: "Could not change secure routing.")
                }
            }
            Unit
        }),
        messages = messages,
        initialMessageId = initialMessageId,
        currentUserId = myId,
        currentUserMentionHandles = listOfNotNull(me?.username?.takeIf { it.isNotBlank() }),
        localOverrideAccountKey = effectiveLocalOverrideAccountKey,
        messageSessionIdentity = messageAccountScope,
        isMessageSessionCurrent = { com.example.chat.core.messages.LocalMessageOverridePolicy.sessionCurrent(messageAccountScope, api.authSnapshot()) },
        onBack = onBack,
        onSend = { text ->
            requireSendRouting()
            if (secureChannel) {
                // Fail closed: an encrypted chat never falls back to plaintext.
                try {
                    repo.sendMessage(conversation.id, text)
                } catch (e: com.example.chat.data.messaging.SecureSendPendingException) {
                    toast("Encryption for this chat is still being set up. The message is queued and sends automatically when ready.")
                } catch (e: Exception) {
                    throw Exception("Secure send failed: ${e.message ?: e.toString()}. Nothing was sent - there is no plaintext fallback.")
                }
                rev++
            } else {
                api.sendMessage(conversation.id, text)
            }
            rt.trackConversation(conversation.id)
        },
        resolveSenderName = { id ->
            if (id == myId) "You" else (api.users.firstOrNull { it.id == id }?.displayName ?: "Team Member")
        },
        resolveQuoteSender = { id -> api.users.firstOrNull { it.id == id } },
        useScopedChatTheme = hasChatThemeOverride,
        presenceText = presenceText,
        presenceIsOnline = presenceOnline,
        peerTyping = isDirect && otherTyping && !infoBlocked && !conv.disableContactStatus && gbB("statuschat", true),
        typingAvatarInitials = if (conv.hideProfilePicture || !gbB("PicProf", true)) "" else other?.avatarInitials ?: live.avatarInitials.orEmpty(),
        typingAvatarUrl = other?.avatarUrl.takeUnless { conv.hideProfilePicture || !gbB("PicProf", true) },
        typingAvatarColorHex = other?.avatarColorHex ?: live.avatarColorHex,
        showHeaderAvatar = !conv.hideProfilePicture && gbB("PicProf", true),
        showHeaderName = !conv.hideContactName && gbB("NameProf", true),
        showHeaderCalls = !conv.hideCallButton && gbB("Conv_call_btn", true),
        showPresenceLine = !conv.disableContactStatus && gbB("statuschat", true),
        headerBarColor = conv.actionBarColor.takeUnless { hasChatThemeOverride },
        presencePillColor = (conv.contactStatusBgColor ?: prefs.gbColor("ModChatGStatusB")).takeUnless { hasChatThemeOverride },
        presenceTextColor = (conv.contactStatusTextColor ?: prefs.gbColor("ModChatGStatusT")).takeUnless { hasChatThemeOverride },
        callsAllowed = isDirect && !infoBlocked,
        onStartCall = { startContactCall(it) },
        connectionState = ServiceLocator.connectionHealth.state,
        networkStatus = liveNetworkStatus,
        appTraffic = liveAppTraffic,
        p2pTransferStates = liveP2pTransfers,
        onRetryP2pTransfer = { transferId ->
            if (!p2pTransfers.retryTransfer(transferId)) toast("Could not queue a secure retry. Check your connection and try again.")
        },
        liveTransfers = liveTransferItems,
        onProbeConnection = { try { ServiceLocator.connectionHealth.probeConnection() } catch (_: Exception) {} },
        onDraftChanged = { text ->
            val captured = messageAccountScope
            api.withAuthScope(captured) { api.setDraft(conversation.id, text) }
            scope.launch {
                if (api.authSnapshot() === captured) {
                    try { repo.setDraft(conversation.id, text) } catch (_: Exception) {}
                }
            }
        },
        onTypingChanged = { typing ->
            val allowed = privacy.typingIndicators && !prefs.home.ghostMode && !gbB("yo_want_ghostmode", false)
            // A privacy change emits only a best-effort stop, never a final positive heartbeat.
            api.sendTyping(conversation.id, typing && allowed, messageAccountScope)
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
                obj.put(wallpaperKey, id)
                wallpaperStore.edit().putString("chaty_chat_wallpapers_v1", obj.toString()).apply()
            } catch (_: Exception) {}
        },
        onToggleMute = { api.setConversationLocalState(conversation.id, "muted", !live.isMuted) },
        // Blocking needs a block-list API; deferred to the next release (ReleaseFeatures).
        onBlockContact = if (ReleaseFeatures.BLOCK_CONTACTS && isDirect && otherId != null) ({ blockTarget = otherId; safetyError = null; blockConfirm = true }) else null,
        showUnknownSenderSafety = isDirect && connection?.myAccepted == false && !senderContinued &&
            !infoBlocked && messages.any { it.senderId == otherId },
        onContinueUnknownSender = {
            api.withAuthScope(messageAccountScope) {
                if (effectiveLocalOverrideAccountKey.isNotBlank()) senderSafetyStore.edit().putBoolean(senderSafetyKey, true).apply()
                senderContinued = true
            }
        },
        onReportUnknownSender = {
            reportTarget = messages.lastOrNull { it.senderId == otherId && !it.isDeletedForEveryone && !it.isDeletedForMe }
            if (reportTarget == null) toast("Open a received message to report it.")
        },
        onReportMessage = { reportTarget = it },
        onBlockMessageSender = { blockTarget = it; safetyError = null; blockConfirm = true },
        hideDateAndName = conv.hideDateAndName,
        showDeletedContent = privacy.antiDeleteMessages || gbB("yoAntiRevoke", false),
        retainViewOnce = privacy.antiViewOnce || gbB("anti_vw_once", false),
        disableDoubleTapReaction = conv.disableDoubleTapReaction,
        doubleTapReactionEmoji = conv.doubleTapReactionEmoji,
        onDoubleTapReactionEmojiChanged = { emoji ->
            prefs.updateConversation(prefs.conversation.copy(doubleTapReactionEmoji = emoji),
                logTitle = "Double tap reaction", prevVal = prefs.conversation.doubleTapReactionEmoji, newVal = emoji)
        },
        showGroupAvatar = ServiceLocator.templateController.conversation.showAvatarInGroup,
        colorOverrides = colorOverrides,
        onEditMessage = { id, newText ->
            requireSendRouting()
            if (secureChannel) {
                // MLS messages are re-encrypted at the current epoch; the
                // repository never writes their plaintext to the legacy plane.
                try {
                    repo.editMessage(conversation.id, id, newText)
                } catch (e: Exception) {
                    throw Exception("Secure edit failed: ${e.message ?: e.toString()}. The message was not changed.")
                }
                rev++
            } else if (api.isAuthenticated) {
                api.editMessage(conversation.id, id, newText)
            }
        },
        onDeleteMessage = { id, forEveryone ->
            scope.launch {
                try { requireSendRouting() } catch (e: Exception) { toast(e.message ?: "Action blocked."); return@launch }
                if (secureChannel) {
                    try {
                        repo.deleteMessage(conversation.id, id, forEveryone)
                    } catch (e: Exception) {
                        toast("Secure delete failed: ${e.message ?: e.toString()}")
                    }
                    rev++
                } else if (api.isAuthenticated) {
                    try { api.deleteMessage(conversation.id, id) } catch (_: Exception) {}
                }
            }
        },
        onToggleStar = { id ->
            val m = messages.firstOrNull { it.id == id }
            if (m != null) api.setMessageLocalState(conversation.id, id, "starred", !m.isStarred)
        },
        onTogglePin = { id ->
            val m = messages.firstOrNull { it.id == id }
            if (m != null) api.setMessageLocalState(conversation.id, id, "pinned", !m.isPinned)
        },
        onToggleReaction = { id, emoji ->
            if (!secureChannelResolved || secureChannel) toast("Reactions are not supported for secure-routing chats yet.")
            else {
                val owner = api.authSnapshot()
                scope.launch {
                    try { api.toggleServerReaction(conversation.id, id, emoji, owner) }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (api.authSnapshot() === owner) toast(e.message ?: "Could not update reaction.")
                    }
                }
            }
        },
        onForward = { forwardScope = api.authSnapshot(); forwardMessages = listOf(it) },
        onForwardSelection = { forwardScope = api.authSnapshot(); forwardMessages = it.toList() },
        onClearConversation = {
            scope.launch {
                try {
                    if (api.isAuthenticated) api.clearConversationMessages(conversation.id)
                } catch (e: Exception) { toast(e.message ?: "Unable to clear conversation.") }
            }
        },
        onRetryMessage = { id ->
            scope.launch {
                try { requireSendRouting() } catch (e: Exception) { toast(e.message ?: "Retry blocked."); return@launch }
                if (secureChannel) {
                    // Queued MLS sends retry through the secure outbox only.
                    try { ServiceLocator.outgoingQueue.processQueue() } catch (_: Exception) {}
                    rev++
                } else {
                    // Re-queues the failed message on the REST outbox (was the old Supabase queue).
                    val ok = try { api.retryMessage(id) }
                    catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                    catch (error: Exception) { toast(error.message ?: "Retry could not be completed."); return@launch }
                    if (!ok) toast("Retry could not be completed. Check the connection or the message's reconciliation state.")
                }
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
                try { onOpenConversation(api.createOrGetDirectConversation(contact.id)) } catch (_: Exception) {}
            }
        },
        attachmentBinding = attachmentBinding,
        voiceUrlResolver = { path ->
            ServiceLocator.chatMedia.createSignedUrl(path, 3600, restApiBaseUrl = { ServiceLocator.apiBackend.config.restApiBaseUrl })
        },
        translateOption = ServiceLocator.preferencesController.universal.translateOption,
        translationLanguage = ServiceLocator.preferencesController.universal.translationLanguage,
        composerColors = if (hasChatThemeOverride) com.example.chat.ui.chats.detail.ComposerColors.resolve(theme) else com.example.chat.ui.chats.detail.ComposerColors.resolve(
            theme, conv.uiButtonsColor, conv.emojiButtonColor, conv.sendButtonColor,
            conv.micSendBgCircleColor, conv.textEntryBackgroundColor, conv.textEntryColor,
        ),
        composerTemplate = ServiceLocator.templateController.composer,
        onAttachmentAction = { action ->
            if (!secureChannelResolved || secureChannel) toast("Attachments are unavailable until secure-media support is ready.") else when (action) {
                com.example.chat.ui.chats.detail.AttachmentAction.image -> attachments.onMedia("image")
                com.example.chat.ui.chats.detail.AttachmentAction.video -> attachments.onMedia("video")
                com.example.chat.ui.chats.detail.AttachmentAction.chatyDirect -> {
                    if (!isDirect || otherId == null) toast("Chaty Direct is available in one-to-one chats.")
                    else { directOriginalSelection = true; largeFilePicker.launch(arrayOf("image/*", "video/*")) }
                }
                com.example.chat.ui.chats.detail.AttachmentAction.largeFile -> {
                    directOriginalSelection = false
                    if (!isDirect || otherId == null) toast("Large direct transfers are available in one-to-one chats.")
                    else largeFilePicker.launch(arrayOf("*/*"))
                }
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
        externalSearchRequest = infoSearchRequest,
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
                        val nextConnection = ServiceLocator.contactRelationships.connectionStatus(other.id, api.contactsPlane(messageAccountScope))
                        val nextBlocked = ServiceLocator.contactRelationships.isBlocked(other.id, api.contactsPlane(messageAccountScope))
                        if (api.authSnapshot() !== messageAccountScope) return@launch
                        infoConnection = nextConnection
                        infoBlocked = nextBlocked
                        try {
                            val loaded = api.peerProfile(other.username, other.id, messageAccountScope)
                            if (api.authSnapshot() === messageAccountScope) infoProfile = loaded
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Keep the cached profile and contact actions usable if refresh is unavailable.
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (e: Exception) { if (api.authSnapshot() === messageAccountScope) infoError = e.message ?: "Unable to load contact." }
                    finally { if (api.authSnapshot() === messageAccountScope) infoLoading = false }
                }
            }
        },
        onOpenAppearance = {
            if (chatThemeKey != null && api.authSnapshot() === messageAccountScope) appearanceOpen = true
            else toast("Sign in to customize this chat's theme.")
        },
        onPollTap = { m -> pollViewId = m.id },
        loadingMessages = loadingMessages,
        loadError = loadError,
        onRetryLoad = { loadNonce++ },
        onMediaTap = { m ->
            if (m.attachment?.url.orEmpty().startsWith("chaty://transfer/")) openDirectMessage(m)
            else { viewerBounds = null; viewerMessage = m }
        },
        onMediaTapWithBounds = { m, rect ->
            if (m.attachment?.url.orEmpty().startsWith("chaty://transfer/")) openDirectMessage(m)
            else { viewerBounds = rect; viewerMessage = m }
        },
        onViewOnceOpen = { m ->
            // Voice view-once plays inside the bubble; only image/video open
            // the full-screen viewer.
            if (m.attachment?.type != "audio" &&
                m.type != com.example.chat.domain.models.MessageType.audio
            ) {
                viewerBounds = null; viewerMessage = m
            }
        },
        onCameraTap = { openChatCamera(com.example.chat.ui.camera.CameraEntryPreset.photoBack) },
        attachmentReviewImages = attachments.onReviewImages,
        attachmentDirectSendImages = attachments.onDirectSendImages,
        onStickerSend = { sticker ->
            val expected = stickerScope
            scope.launch {
                // Stickers travel as unencrypted media; blocked in a secure chat
                // until the encrypted-media plane (Phase 2) is wired.
                if (!secureChannelResolved || secureChannel) {
                    toast("Encrypted media is not wired yet, so stickers are not sent in an encrypted chat.")
                    return@launch
                }
                try {
                    check(api.authSnapshot() === expected) { "Account changed. Open the sticker picker again." }
                    check(!expected.userId.isNullOrBlank() && !expected.token.isNullOrBlank()) { "Sign in to send stickers." }
                    check(com.example.chat.core.stickers.BundledStickerPacks.packs.any { it.id == sticker.packId && sticker in it.stickers }) { "Sticker is no longer installed." }
                    val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.assets.open(sticker.assetPath).use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) { val count = input.read(buffer); if (count < 0) break
                                check(output.size() + count <= 2 * 1024 * 1024) { "Sticker is too large." }; output.write(buffer, 0, count) }
                            output.toByteArray()
                        }
                    }
                    check(bytes.size in 1..(2 * 1024 * 1024)) { "Sticker is too large." }
                    val actualSha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    check(com.example.chat.core.stickers.BundledStickerPacks.expectedSha256[sticker.id] == actualSha) { "Sticker asset is damaged." }
                    requireSendRouting()
                    check(!secureChannel) { "Sticker upload blocked in secure-routing chat." }
                    val uploaded = api.uploadMedia("${sticker.id}.webp", bytes, expectedScope = expected)
                    val attachment = com.example.chat.domain.models.MessageAttachment(
                        id = java.util.UUID.randomUUID().toString(), type = "image", name = "${sticker.label}.webp",
                        size = com.example.chat.data.services.ChatMediaService.formatBytes(bytes.size.toLong()),
                        url = uploaded["url"]?.toString()?.takeIf { it.isNotBlank() } ?: error("Upload did not return a media URL."))
                    requireSendRouting()
                    check(!secureChannel) { "Sticker send blocked in secure-routing chat." }
                    check(api.authSnapshot() === expected) { "Account changed. Open the sticker picker again." }
                    api.sendMessage(conversation.id, "", com.example.chat.domain.models.MessageType.image,
                        attachment, com.example.chat.core.stickers.StickerMessagePolicy.metadata(sticker), expectedScope = expected)
                    rev++
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (error: Exception) { android.widget.Toast.makeText(context, error.message ?: "Unable to send sticker", android.widget.Toast.LENGTH_SHORT).show() }
            }
        },
        onGalleryTap = { attachments.onMedia("image") },
        onAudioTap = { attachments.onMedia("audio") },
        onContactTap = { attachments.onContact() },
        onLocationTap = { attachments.onLocation() },
        onVoiceTap = voice.onVoiceTap,
        onVoiceHoldStart = {
            if (!secureChannelResolved || secureChannel) toast("Voice notes are not sent in an encrypted chat yet; encrypted media is not wired.")
            else voice.onVoiceHoldStart()
        },
        onVoiceMove = voice.onVoiceMove,
        onVoiceHoldEnd = voice.onVoiceHoldEnd,
        onVideoHoldStart = { id ->
            if (videoHoldId == null) {
                openChatCamera(com.example.chat.ui.camera.CameraEntryPreset.composerFrontVideo)
                // Pin the ORIGINAL entry flow with its auth: a same-account AttachFlow cancel
                // landing before the queued UI cancel must not let a pending ready start.
                val auth = cameraEntryAuth
                val flow = cameraEntryFlow
                if (auth != null && flow != null && !flow.isCancelled) {
                    videoHoldRequest = com.example.chat.ui.camera.VideoHoldRequest {
                        !flow.isCancelled && cameraEntryFlow === flow && auth.isSignedIn(auth.snapshot())
                    }
                    videoHoldId = id
                    if (com.example.chat.BuildConfig.DEBUG) videoHoldRequest?.let { token ->
                        try { ChatVideoHoldNativeProof.onEntry?.invoke(flow, token) } catch (_: Throwable) { /* Collect-only instrumentation cannot affect host. */ }
                    }
                }
                // No capture session or cancelled flow: no token, no fake open; this id's End/Cancel are no-ops.
            }
        },
        // Slide-to-cancel is already handled composer-side (VoiceGesturePolicy.CANCEL_DX);
        // the up-lock threshold mirrors the voice policy (dy < LOCK_DY), and a stale id
        // must never reach the token.
        onVideoHoldMove = { id, _, dy ->
            if (id == videoHoldId && dy < com.example.chat.ui.chats.detail.VoiceGesturePolicy.LOCK_DY) videoHoldRequest?.lock()
        },
        onVideoHoldEnd = { id -> if (id == videoHoldId) videoHoldRequest?.release() },
        onVideoHoldCancel = { id -> if (id == videoHoldId) videoHoldRequest?.cancel() },
    )
    }
    voice.overlay()
    if (chatCameraOpen) {
        androidx.activity.compose.BackHandler { closeChatCamera() }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            val localVideoAuth = cameraEntryAuth
            val mountedCameraFlow = cameraEntryFlow
            androidx.compose.runtime.key(mountedCameraFlow) {
            com.example.chat.ui.camera.ChatyCameraCaptureScreen(
                mode = com.example.chat.ui.camera.ChatyCaptureMode.chat,
                contactName = live.title,
                callsAllowed = connection?.callsAllowed ?: true,
                videoAuthPort = localVideoAuth,
                entryPreset = cameraEntryPreset,
                videoHoldRequest = videoHoldRequest,
                onResult = { r ->
                    val flow = mountedCameraFlow
                    if (cameraEntryFlow !== flow) {
                        r.ownedResult?.close()
                        return@ChatyCameraCaptureScreen
                    }
                    chatCameraOpen = false
                    cameraEntryFlow = null
                    cameraEntryAuth = null
                    if (flow == null || flow.isCancelled) {
                        r.ownedResult?.close()
                        flow?.let(attachments.closeCaptureSession)
                    } else scope.launch {
                        try {
                        attachments.controller.sendCapturedPhoto(
                            path = r.path,
                            caption = r.caption,
                            effectId = r.effectId,
                            effectIntensity = r.effectIntensity,
                            onSent = {
                                if (flow.isCancelled) return@sendCapturedPhoto
                                try { rt.trackConversation(conversation.id) } catch (_: Exception) {}
                                com.example.chat.ui.kit.ChatyHapticsPort.success(hostView)
                                rev++
                            },
                        )
                        } finally {
                            // Transport has unwound. A cancelled original account cannot retry this capture.
                            if (flow.isCancelled) r.ownedResult?.close()
                            attachments.closeCaptureSession(flow)
                        }
                    }
                },
                onDismiss = { if (cameraEntryFlow === mountedCameraFlow) closeChatCamera() },
            )
            }
        }
    }
    if (infoRoute != 0) {
        androidx.activity.compose.BackHandler { infoRoute = if (infoRoute == 2) 1 else 0 }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            if (!isDirect) {
                com.example.chat.ui.chats.group_info.GroupInfoScreen(
                    conversation = live,
                    onSearch = { infoRoute = 0; infoSearchRequest++ },
                    messages = messages,
                    onOpenMedia = { m -> viewerBounds = null; viewerMessage = m },
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
                    contact = infoProfile ?: other,
                    onStartCall = { startContactCall(it) },
                    onShareContact = {
                        if (api.authSnapshot() === messageAccountScope) {
                            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "${other.displayName}\n@${other.username}")
                            }
                            try { context.startActivity(android.content.Intent.createChooser(share, "Share contact")) }
                            catch (_: Exception) { toast("No app is available to share this contact.") }
                        }
                    },
                    messages = messages,
                    connection = infoConnection,
                    blocked = infoBlocked,
                    loading = infoLoading,
                    loadError = infoError,
                    busy = infoBusy,
                    // Use the same privacy-filtered API presence as the chat header,
                    // not the stale legacy realtime timestamp fallback.
                    presenceLabelOverride = presenceText,
                    isOnline = { id -> id == otherId && presenceOnline },
                    lastSeenFor = { _ -> null },
                    activityFor = { cid, uid -> rt.activityFor(cid, uid) },
                    hydrateMessage = { m -> rt.hydrateMessage(m) },
                    onAccept = {
                        infoBusy = true
                        try {
                            check(api.authSnapshot() === messageAccountScope) { "Account changed." }
                            check(infoConnection.isPendingIncoming) { "There is no incoming request to accept." }
                            ServiceLocator.contactRelationships.acceptConnection(other.id, api.contactsPlane(messageAccountScope))
                            check(api.authSnapshot() === messageAccountScope) { "Account changed." }
                            refreshConnection()
                            val nextStatus = ServiceLocator.contactRelationships.connectionStatus(other.id, api.contactsPlane(messageAccountScope))
                            check(api.authSnapshot() === messageAccountScope) { "Account changed." }
                            infoConnection = nextStatus
                        } finally { infoBusy = false }
                    },
                    onSetBlocked = { next ->
                        infoBusy = true
                        try {
                            api.setUserBlocked(messageAccountScope, other.id, next)
                            check(api.authSnapshot() === messageAccountScope) { "Account changed." }
                            infoBlocked = next
                            refreshConnection()
                            val nextStatus = ServiceLocator.contactRelationships.connectionStatus(other.id, api.contactsPlane(messageAccountScope))
                            check(api.authSnapshot() === messageAccountScope) { "Account changed." }
                            infoConnection = nextStatus
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
                    onSearch = { infoRoute = 0; infoSearchRequest++ },
                    onDeletedPopToRoot = { infoRoute = 0; onBack() },
                    onBack = { infoRoute = 0 },
                )
            }
        }
    }
    if (pollCreateOpen && secureChannelResolved && !secureChannel) {
        CreatePollDialog(
            onCancel = { pollCreateOpen = false },
            onCreate = { draft ->
                pollCreateOpen = false
                scope.launch { attachments.controller.createPoll(draft); rev++ }
            },
        )
    }
    val pv = pollViewId
    if (pv != null && secureChannelResolved && !secureChannel) {
        PollViewerSheet(
            messageId = pv,
            controller = attachments.controller,
            onDismiss = { pollViewId = null; scope.launch { try { api.loadMessages(conversation.id) } catch (_: Exception) {} } },
        )
    }
    if (appearanceOpen && chatThemeKey != null) {
        androidx.activity.compose.BackHandler { appearanceOpen = false }
        var jsonWaiter by remember(messageAccountScope) { mutableStateOf<kotlinx.coroutines.CompletableDeferred<String?>?>(null) }
        var imageWaiter by remember(messageAccountScope) { mutableStateOf<kotlinx.coroutines.CompletableDeferred<File?>?>(null) }
        DisposableEffect(messageAccountScope) {
            onDispose { jsonWaiter?.cancel(); imageWaiter?.cancel() }
        }
        val jsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri ->
            val waiting = jsonWaiter
            jsonWaiter = null
            scope.launch {
                val text = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        uri?.let { value -> context.contentResolver.openInputStream(value)?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 1_048_576) { "Theme file is too large." }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray().toString(Charsets.UTF_8)
                        } }
                    } catch (_: Exception) { null }
                }
                if (api.authSnapshot() === messageAccountScope) waiting?.complete(text) else waiting?.cancel()
            }
        }
        val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        ) { uri ->
            val waiting = imageWaiter
            imageWaiter = null
            scope.launch {
                val file = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    var output: File? = null
                    try {
                        uri?.let { value ->
                            output = File.createTempFile("chat_theme_", ".image", context.cacheDir)
                            context.contentResolver.openInputStream(value)?.use { input ->
                                output!!.outputStream().use { stream ->
                                    val buffer = ByteArray(8192)
                                    var total = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        require(total <= 32L * 1024 * 1024) { "Image is too large." }
                                        stream.write(buffer, 0, count)
                                    }
                                }
                            } ?: error("Could not read image.")
                            output
                        }
                    } catch (_: Exception) { output?.delete(); null }
                }
                if (api.authSnapshot() === messageAccountScope) waiting?.complete(file)
                else { file?.delete(); waiting?.cancel() }
            }
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            com.example.chat.ui.settings.theme.ThemeEditorScreen(
                themeController = ServiceLocator.themeController,
                preferences = prefs,
                templateController = ServiceLocator.templateController,
                appearance = ServiceLocator.appearanceVariantController,
                exportDirectory = context.getExternalFilesDir(null) ?: context.filesDir,
                pickJsonText = {
                    val waiting = kotlinx.coroutines.CompletableDeferred<String?>()
                    jsonWaiter?.cancel(); jsonWaiter = waiting
                    jsonLauncher.launch(arrayOf("application/json", "text/plain"))
                    waiting.await()
                },
                pickImageFile = {
                    val waiting = kotlinx.coroutines.CompletableDeferred<File?>()
                    imageWaiter?.cancel(); imageWaiter = waiting
                    imageLauncher.launch("image/*")
                    waiting.await()
                },
                conversationKey = chatThemeKey,
                conversationTitle = live.title,
                isScopeCurrent = { api.authSnapshot() === messageAccountScope },
                onBack = { appearanceOpen = false },
            )
        }
    }
    val vm = viewerMessage?.let { requested ->
        messages.firstOrNull { it.id == requested.id && !it.isDeletedForEveryone && !it.isDeletedForMe && !it.isRevokedBySender }
            // View-once voice/audio plays in the bubble, never in the
            // full-screen viewer, whichever callback set viewerMessage.
            ?.takeIf { m ->
                !(m.metadata["view_once"] == true &&
                    (m.attachment?.type == "audio" ||
                        m.type == com.example.chat.domain.models.MessageType.audio))
            }
    }
    val va = vm?.attachment
    val localP2pFile = p2pLocalViewer
    if (localP2pFile != null && localP2pFile.isFile) {
        androidx.activity.compose.BackHandler { p2pLocalViewer = null }
        com.example.chat.ui.chats.detail.MediaViewerScreen(
            theme = theme,
            title = localP2pFile.name,
            type = when (localP2pFile.extension.lowercase()) {
                "mp4", "mkv", "mov", "webm", "m4v", "avi", "3gp", "mpeg", "mpg" -> "video"
                "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp" -> "image"
                else -> "document"
            },
            size = com.example.chat.data.services.ChatMediaService.formatBytes(localP2pFile.length()),
            storagePath = localP2pFile.toURI().toString(),
            createSignedUrl = { path, _ -> path },
            onBack = { p2pLocalViewer = null },
            providedModel = localP2pFile.toURI().toString(),
            ownedActions = true,
            onOwnedOpen = {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localP2pFile)
                val mime = when (localP2pFile.extension.lowercase()) {
                    "mp4", "m4v" -> "video/mp4"
                    "mkv" -> "video/x-matroska"
                    "mov" -> "video/quicktime"
                    "webm" -> "video/webm"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "*/*"
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
            },
            onOwnedShare = {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localP2pFile)
                val mime = when (localP2pFile.extension.lowercase()) {
                    "mp4", "m4v" -> "video/mp4"
                    "mkv" -> "video/x-matroska"
                    "mov" -> "video/quicktime"
                    "webm" -> "video/webm"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "*/*"
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime)
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(android.content.Intent.createChooser(intent, "Share video"))
            },
        )
    }
    if (vm != null && va != null) {
        androidx.activity.compose.BackHandler { viewerMessage = null }
        androidx.compose.runtime.key(attachmentBinding.session, conversation.id, vm.id) {
            val albumSequence = com.example.chat.ui.chats.detail.AlbumNavigationPolicy.sequence(
                com.example.chat.ui.chats.detail.mediaTimelineRows(messages), vm.id)
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                com.example.chat.ui.chats.detail.BoundAttachmentViewer(
                    theme, vm, attachmentBinding,
                    restricted = com.example.chat.data.local.AttachmentCachePolicy.viewOnceRestricted(vm.metadata),
                    bounds = viewerBounds, onBack = { viewerMessage = null },
                )
                if (albumSequence.size > 1) {
                    androidx.compose.foundation.layout.Row(
                        androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                            .navigationBarsPadding().padding(bottom = 72.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        val previous = com.example.chat.ui.chats.detail.AlbumNavigationPolicy.adjacent(albumSequence, vm.id, -1)
                        val next = com.example.chat.ui.chats.detail.AlbumNavigationPolicy.adjacent(albumSequence, vm.id, 1)
                        TextButton(enabled = previous != null, onClick = { previous?.let { viewerBounds = null; viewerMessage = it } }) { Text("Previous photo") }
                        Text("${albumSequence.indexOfFirst { it.id == vm.id } + 1} / ${albumSequence.size}")
                        TextButton(enabled = next != null, onClick = { next?.let { viewerBounds = null; viewerMessage = it } }) { Text("Next photo") }
                    }
                }
            }
        }
    }

    }

    p2pActionTransfer?.let { id ->
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { p2pActionTransfer = null },
            title = { Text("Direct transfer") },
            text = { Column {
                Text("Keep both phones online with Chaty open. Mobile data and Wi-Fi are supported. No media upload or relay fallback.")
                liveP2pTransfers.firstOrNull { it.transferId == id }?.let { state ->
                    Text(state.status)
                    state.error?.let { Text(it) }
                }
            } },
            confirmButton = { TextButton(onClick = {
                if (!p2pTransfers.retryTransfer(id)) {
                    val message = api.getMessages(conversation.id).firstOrNull { it.attachment?.url == "chaty://transfer/" + id }
                    if (message != null && message.senderId != api.currentUser?.id) openDirectMessage(message)
                    else toast("Keep both phones online and retry from this chat.")
                }
                p2pActionTransfer = null
            }) { Text("Retry") } },
            dismissButton = { TextButton(onClick = { p2pTransfers.cancel(id); p2pActionTransfer = null }) { Text("Cancel transfer") } })
    }
    if (p2pReceiveMessage != null && p2pReceiveLink != null) {
        val message = p2pReceiveMessage!!
        AlertDialog(
            shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { p2pReceiveMessage = null; p2pReceiveLink = null },
            title = { Text("Receive " + (message.attachment?.name ?: "video") + "?") },
            text = {
                Column {
                    Text((message.attachment?.size ?: "Original file") + " · Encrypted peer transfer")
                    if (p2pReceiveOriginal) Text("Chaty Direct Original: exact original bytes, no server media copy or relay. Both phones need internet and Chaty open.")
                    Text("Estimated download: about " + (message.attachment?.size ?: "the full file size") + ". Retries may use additional data.", color = Color(theme.secondaryTextColor))
                    Text("The file is saved in Chaty’s private storage and can be played inside the app.", color = Color(theme.secondaryTextColor))
                    if (liveNetworkStatus.metered) Text("You are on a metered connection.", color = Color(theme.secondaryTextColor))
                }
            },
            dismissButton = {
                TextButton(onClick = { p2pReceiveMessage = null; p2pReceiveLink = null }) { Text("Not now") }
            },
            confirmButton = {
                TextButton(onClick = {
                    val link = p2pReceiveLink!!
                    val name = com.example.chat.data.services.ChatMediaService.safeFileName(message.attachment?.name ?: "video")
                    val directory = File(context.filesDir, "p2p-received")
                    directory.mkdirs()
                    val destination = File(directory, if (p2pReceiveOriginal) link.substringAfter("chaty://transfer/").substringBefore('#') + "-" + name.takeLast(140) else name)
                    val offerId = link.substringAfter("chaty://transfer/").substringBefore('#')
                    val offer = p2pTransfers.pendingOffers.value.firstOrNull { it.transferId == offerId }
                    val required = offer?.sizeBytes ?: 0L
                    val free = android.os.StatFs(directory.absolutePath).availableBytes
                    if (required > 0 && free < required) {
                        toast("Not enough free storage for this file.")
                    } else if (destination.exists()) {
                        toast("A file with that name already exists in Chaty storage.")
                    } else {
                        scope.launch {
                            try {
                                p2pTransfers.acceptInvite(link, destination, message.senderId, directOriginal = p2pReceiveOriginal)
                                toast("Direct transfer ready; waiting for verified original bytes.")
                            } catch (e: Exception) { toast(e.message ?: "Could not receive this file.") }
                            p2pReceiveMessage = null
                            p2pReceiveLink = null
                        }
                    }
                }) { Text("Receive") }
            },
        )
    }
    reportTarget?.let { target ->
        com.example.chat.ui.chats.detail.ReportMessageDialog(
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details ->
                check(api.getMessages(conversation.id).any { it.id == target.id && it.senderId != myId }) { "Message is no longer available." }
                api.reportMessage(messageAccountScope, target.id, reason, details)
                api.withAuthScope(messageAccountScope) { toast("Report received by moderation.") }
            },
        )
    }
    if (blockConfirm && blockTarget != null) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { if (!safetyBusy) blockConfirm = false },
            title = { Text("Block sender?") },
            text = { Text(safetyError ?: "Ask the server to stop messages and calls from this user. This takes effect only after server confirmation.") },
            dismissButton = { TextButton(enabled = !safetyBusy, onClick = { blockConfirm = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(enabled = !safetyBusy, onClick = {
                    val target = blockTarget ?: return@TextButton
                    safetyBusy = true; safetyError = null
                    scope.launch {
                        try {
                            api.setUserBlocked(messageAccountScope, target, true)
                            api.withAuthScope(messageAccountScope) {
                                if (target == otherId) infoBlocked = true
                                blockConfirm = false; toast("User blocked.")
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (failure: Exception) { safetyError = failure.message ?: "Block was not confirmed." }
                        finally { safetyBusy = false }
                    }
                }) { Text(if (safetyBusy) "Blocking..." else "Block") }
            },
        )
    }

    forwardMessages.takeIf { it.isNotEmpty() }?.let { batch ->
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
            LaunchedEffect(batch) { toast("No other chats to forward to."); forwardMessages = emptyList() }
        } else {
            val forwardWindowSize = com.example.chat.ui.kit.rememberAdaptiveWindowSize()
            val forwardMaxHeight = com.example.chat.ui.kit.adaptiveSheetHeight(forwardWindowSize.heightDp, fraction = 0.7f)
            androidx.compose.ui.window.Dialog(onDismissRequest = { forwardMessages = emptyList() }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = forwardMaxHeight)
                        .background(Color(theme.surfaceColor), com.example.chat.ui.kit.ChatyModalShape)
                        .padding(vertical = 10.dp),
                ) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.forward,
                            semanticLabel = null,
                            color = Color(theme.accentColor),
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
                                        val expectedForwardScope = forwardScope
                                        forwardMessages = emptyList()
                                        scope.launch {
                                            val labelAllowed = ForwardSend.labelAllowed(
                                                disableForwardedLabel = forwardPrefs.privacy.disableForwardedLabel,
                                                yoDisableFwd = forwardPrefs.gbBool("yoDisableFwd"),
                                            )
                                            try {
                                                val captured = checkNotNull(expectedForwardScope) { "Forwarding account is missing." }
                                                check(api.withAuthScope(captured) {}) { "Account changed; messages not forwarded." }
                                                var confirmedCount = 0
                                                for (fm in batch) {
                                                    check(api.authSnapshot() === captured) { "Account changed; forwarding stopped." }
                                                    val payload = ForwardSend.payload(fm, labelAllowed)
                                                    if (repo.isSecureChannel(item.conversation.id)) {
                                                        check(secureRoutingAvailable) { "Secure routing is unavailable in this build. Nothing was forwarded." }
                                                        // Encrypted target chat: text forwards ride the MLS
                                                        // plane; media forwards are refused (never plaintext).
                                                        if (payload.attachment != null || payload.type != com.example.chat.domain.models.MessageType.text) {
                                                            throw IllegalStateException("Media cannot be forwarded into an encrypted chat yet; encrypted media is not wired.")
                                                        }
                                                        try {
                                                            repo.sendMessage(
                                                                item.conversation.id,
                                                                payload.text,
                                                                extraMetadata = payload.extraMetadata,
                                                            )
                                                            confirmedCount++
                                                        } catch (e: com.example.chat.data.messaging.SecureSendPendingException) {
                                                            // Queued until MLS setup completes; counts as queued, not confirmed.
                                                        }
                                                    } else {
                                                        val forwarded = api.sendMessage(
                                                            item.conversation.id,
                                                            payload.text,
                                                            payload.type,
                                                            payload.attachment,
                                                            payload.extraMetadata,
                                                            expectedScope = captured,
                                                        )
                                                        if (forwarded != null && forwarded.deliveryState != com.example.chat.domain.models.DeliveryState.queued &&
                                                            forwarded.deliveryState != com.example.chat.domain.models.DeliveryState.sending) confirmedCount++
                                                    }
                                                }
                                                if (confirmedCount == batch.size) toast("Forwarded ${batch.size} message(s) to ${item.conversation.title}.")
                                                else toast("${batch.size - confirmedCount} message(s) queued for ${item.conversation.title}.")
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
    if (ed != null && taskUser != null && secureChannelResolved && !secureChannel) {
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
    if (td != null && taskUser != null && secureChannelResolved && !secureChannel) {
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
