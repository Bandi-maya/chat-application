package com.example.chat.ui.chats

import com.example.chat.ReleaseFeatures

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.VideoCameraFront
import androidx.compose.material.icons.rounded.StarBorder
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chat.core.templates.ComposerActionPlacement
import com.example.chat.core.templates.ComposerTemplate
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.ConnectionHealthState
import com.example.chat.data.network.AppTrafficSnapshot
import com.example.chat.data.network.NetworkStatusMonitor
import com.example.chat.data.network.TransferItem
import com.example.chat.data.transfer.P2pFileTransferService
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.QuickReplyTemplate
import com.example.chat.domain.models.TaskStatus
import com.example.chat.ui.chats.detail.AttachmentAction
import com.example.chat.ui.chats.detail.QuickContactEntry
import com.example.chat.ui.chats.detail.QuickContactSidebar
import com.example.chat.ui.chats.detail.AttachmentSheetContent
import com.example.chat.ui.chats.detail.BubbleColorOverrides
import com.example.chat.ui.chats.detail.ChatCommandParser
import com.example.chat.ui.kit.AppContextMenu
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.kit.ContextMenuItem
import com.example.chat.ui.kit.ContextMenuSection
import com.example.chat.ui.kit.ReactionRail
import com.example.chat.ui.chats.detail.ChatCommandType
import com.example.chat.ui.chats.detail.ChatWallpaper
import com.example.chat.ui.chats.detail.ChatyEmojiComposerAction
import com.example.chat.ui.chats.detail.ChatEntryBar
import com.example.chat.ui.chats.detail.ChatyEmojiPickerSheet
import com.example.chat.ui.chats.detail.ComposerColors
import com.example.chat.ui.chats.detail.ConnectionDetailSheetContent
import com.example.chat.ui.chats.detail.ChatMessageBubble
import com.example.chat.ui.chats.detail.ConnectionHealthIndicator
import com.example.chat.ui.chats.detail.GlobalConnectionBanner
import com.example.chat.ui.chats.detail.MessageActionRegistry
import com.example.chat.ui.chats.detail.MessageActionType
import com.example.chat.ui.chats.detail.actionIcon
import androidx.compose.material3.HorizontalDivider
import com.example.chat.ui.chats.detail.RemoteImageSlot
import com.example.chat.ui.chats.detail.chatyRemoteImage
import com.example.chat.ui.theme.LocalThemeConfig
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Full port of lib/features/chats/chat_detail_screen.dart's conversation
 *  surface: header (normal + multi-select + in-chat search with match
 *  navigation), wallpaper layer + per-chat picker, connection banner and
 *  health indicator, call buttons, message list with anchored long-press
 *  context menu and reaction rail, reply/edit banners, quick-replies
 *  overlay, drafts + typing publish, scroll-to-bottom FAB with unseen
 *  count, attachment sheet, emoji picker (composer + reaction modes over
 *  a Unicode v18 catalog), connection diagnostics dialog, and the stock
 *  integrated composer with send/mic morph.
 *
 *  The original signature (conversation, messages, currentUserId, onBack,
 *  onSend) is preserved so AppRoot compiles unchanged; every new behavior is
 *  an optional callback the integration owner wires to the shared backend /
 *  preferences seams. Defaults match the Dart fallbacks.
 *
 *  Known source-faithful simplifications pending owner seams:
 *  - Reply send drops the replyTo linkage until ApiBackendService.sendMessage
 *    gains reply params (reported gap); the banner UI is complete.
 *  - Star/pin/reactions/forward are dataStore-local in Dart; callbacks are
 *    provided here, wiring belongs to the owner (reported gap).
 *  - Presence line, voice recording, drafts, typing indicators, wallpaper,
 *    in-chat search and quick replies are preference/service features owned
 *    by other slices; presence renders when presenceText is supplied. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversation: Conversation,
    messages: List<ChatMessage>,
    currentUserId: String,
    localOverrideAccountKey: String = "",
    messageSessionIdentity: Any = currentUserId,
    isMessageSessionCurrent: () -> Boolean = { false },
    onBack: () -> Unit,
    onSend: suspend (String) -> Unit,
    resolveSenderName: (String) -> String = { senderId ->
        if (senderId == currentUserId) "You" else "Team Member"
    },
    resolveQuoteSender: (String) -> com.example.chat.domain.models.UserProfile? = { null },
    presenceText: String? = null,
    presenceIsOnline: Boolean = false,
    showHeaderAvatar: Boolean = true,
    showHeaderName: Boolean = true,
    showHeaderCalls: Boolean = true,
    showPresenceLine: Boolean = true,
    headerBarColor: Long? = null,
    presencePillColor: Long? = null,
    presenceTextColor: Long? = null,
    callsAllowed: Boolean = true,
    onStartCall: (isVideo: Boolean) -> Unit = {},
    connectionState: ConnectionHealthState? = null,
    onProbeConnection: suspend () -> Unit = {},
    networkStatus: NetworkStatusMonitor.Status? = null,
    p2pTransferStates: List<P2pFileTransferService.TransferState> = emptyList(),
    liveTransfers: List<TransferItem> = emptyList(),
    appTraffic: AppTrafficSnapshot? = null,
    onRetryP2pTransfer: (String) -> Unit = {},
    onDraftChanged: (String) -> Unit = {},
    onTypingChanged: (Boolean) -> Unit = {},
    quickReplies: List<QuickReplyTemplate> = emptyList(),
    wallpaperType: String = "Pattern",
    wallpaperPatternId: String? = null,
    wallpaperPath: String? = null,
    wallpaperProfilePic: Boolean = false,
    wallpaperAvatarUrl: String? = null,
    onWallpaperSelected: ((String) -> Unit)? = null,
    onOpenAppearance: (() -> Unit)? = null,
    onToggleMute: (() -> Unit)? = null,
    ghostModeEnabled: Boolean = false,
    onToggleGhostMode: (() -> Unit)? = null,
    onBlockContact: (() -> Unit)? = null,
    onReportMessage: (ChatMessage) -> Unit = {},
    onBlockMessageSender: (String) -> Unit = {},
    hideDateAndName: Boolean = false,
    showDeletedContent: Boolean = false,
    retainViewOnce: Boolean = false,
    disableDoubleTapReaction: Boolean = false,
    doubleTapReactionEmoji: String = "❤️",
    onDoubleTapReactionEmojiChanged: ((String) -> Unit)? = null,
    showGroupAvatar: Boolean = true,
    colorOverrides: BubbleColorOverrides? = null,
    remoteImage: RemoteImageSlot? = chatyRemoteImage,
    onEditMessage: suspend (messageId: String, newText: String) -> Unit = { _, _ -> },
    onCreateTask: (initialTitle: String?) -> Unit = {},
    onDeleteMessage: (messageId: String, forEveryone: Boolean) -> Unit = { _, _ -> },
    onToggleStar: (messageId: String) -> Unit = {},
    onTogglePin: (messageId: String) -> Unit = {},
    onToggleReaction: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onForward: (ChatMessage) -> Unit = {},
    onForwardSelection: ((List<ChatMessage>) -> Unit)? = null,
    onClearConversation: () -> Unit = {},
    onRetryMessage: (messageId: String) -> Unit = {},
    onOpenInfo: () -> Unit = {},
    externalSearchRequest: Int = 0,
    onCreateTaskFromMessage: (ChatMessage) -> Unit = {},
    // Dart dataStore.tasks slice + task callbacks (chat_detail_screen.dart:2966-3058);
    // integration wires these to the tasks controller/detail/modal surfaces.
    tasks: List<ChatTask> = emptyList(),
    onOpenTaskDetails: (ChatTask) -> Unit = {},
    onEditTask: (ChatTask) -> Unit = {},
    onUpdateTaskStatus: (taskId: String, status: TaskStatus) -> Unit = { _, _ -> },
    onDeleteTask: (taskId: String) -> Unit = {},
    // Dart ChatDetailScreen.initialMessageId (chat_detail_screen.dart:65): deep-link
    // target from the starred-messages screen; jumped to once messages arrive.
    initialMessageId: String? = null,
    onAttachmentAction: (AttachmentAction) -> Unit = {},
    attachmentReviewImages: ((List<android.net.Uri>) -> Unit)? = null,
    attachmentDirectSendImages: ((List<android.net.Uri>) -> Unit)? = null,
    onStickerSend: ((com.example.chat.core.stickers.StickerAsset) -> Unit)? = null,
    onCameraTap: () -> Unit = {},
    onVoiceTap: () -> Unit = {},
    onVoiceHoldStart: () -> Unit = {},
    onVoiceMove: (Offset) -> Unit = {},
    onVoiceHoldEnd: () -> Unit = {},
    onVideoHoldStart: ((Long) -> Unit)? = null,
    onVideoHoldMove: (Long, Float, Float) -> Unit = { _, _, _ -> },
    onVideoHoldEnd: (Long) -> Unit = {},
    onVideoHoldCancel: (Long) -> Unit = {},
    onGalleryTap: (() -> Unit)? = null,
    onAudioTap: (() -> Unit)? = null,
    onLocationTap: (() -> Unit)? = null,
    onContactTap: (() -> Unit)? = null,
    entryStyle: String = "Stock",
    composerColors: ComposerColors? = null,
    composerTemplate: ComposerTemplate = ComposerTemplate(ComposerActionPlacement.integrated),
    // Global conversation prefs (preferencesController.conversation): emoji picker
    // colors, composer bar bg, bubble prefs - Dart `conv` / `convPrefs` consumers.
    convPrefs: ConversationPreferences = ConversationPreferences(),
    disableSwipeToExit: Boolean = false,
    quickContacts: List<QuickContactEntry> = emptyList(),
    onOpenConversation: (String) -> Unit = {},
    homeAvatarShape: String = "circle",
    universalEmojiStyle: String = "Chaty Motion",
    translateOption: String = "Server + No outside apps",
    translationLanguage: String = "Show All",
    onMediaTap: (ChatMessage) -> Unit = {},
    onMediaTapWithBounds: ((ChatMessage, Rect) -> Unit)? = null,
    attachmentBinding: com.example.chat.ui.chats.detail.AttachmentConsumerBinding? = null,
    onVoicePlay: (MessageAttachment) -> Unit = {},
    voiceUrlResolver: (suspend (String) -> String)? = null,
    onViewOnceOpen: (ChatMessage) -> Unit = {},
    onPollTap: (ChatMessage) -> Unit = {},
    loadingMessages: Boolean = false,
    loadError: String? = null,
    onRetryLoad: () -> Unit = {},
    // Test-only hook for debug fixtures (MenuAlbumProofActivity): opens the header
    // overflow menu on first composition. Default keeps product behavior unchanged.
    initialChatMenuOpen: Boolean = false,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        com.example.chat.ui.chats.detail.LocalEmojiPack provides
            com.example.chat.ui.chats.detail.ChatyEmojiPack.normalize(universalEmojiStyle),
    ) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Dart initState: the composer restores the persisted draft.
    var composerText by remember(conversation.id) { mutableStateOf(conversation.draftText) }
    var sending by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedIds by remember(conversation.id) { mutableStateOf<Set<String>>(emptySet()) }
    val contextMenuMessageState = remember { mutableStateOf<ChatMessage?>(null) }
    var contextMenuMessage by contextMenuMessageState
    var singleTapMessage by remember(conversation.id) { mutableStateOf<ChatMessage?>(null) }
    var infoMessage by remember(conversation.id) { mutableStateOf<ChatMessage?>(null) }
    var showStarredInChat by remember(conversation.id) { mutableStateOf(false) }
    val shareContext = androidx.compose.ui.platform.LocalContext.current
    val localMessageStore = remember(localOverrideAccountKey, conversation.id) {
        com.example.chat.core.messages.LocalMessageOverrideStore(shareContext.applicationContext, localOverrideAccountKey, conversation.id)
    }
    var localMessageRevision by remember(currentUserId, conversation.id, messageSessionIdentity) { mutableIntStateOf(0) }
    var localEditMessageId by remember(currentUserId, conversation.id, messageSessionIdentity) { mutableStateOf<String?>(null) }
    var localEditText by remember(currentUserId, conversation.id, messageSessionIdentity) { mutableStateOf("") }
    var choosingDoubleTapEmoji by remember(currentUserId, conversation.id, messageSessionIdentity) { mutableStateOf(false) }
    var quoteMessageId by remember(currentUserId, conversation.id, messageSessionIdentity) { mutableStateOf<String?>(null) }

    var copySelectionMessage by remember(conversation.id) { mutableStateOf<ChatMessage?>(null) }
    var taskMenuTarget by remember { mutableStateOf<Pair<ChatTask, Rect>?>(null) }
    var contextMenuAnchor by remember { mutableStateOf<Rect?>(null) }
    var attachmentSheetOpen by remember { mutableStateOf(false) }
    var chatMenuOpen by remember { mutableStateOf(initialChatMenuOpen) }
    var openedViewOnceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingBelowCount by remember { mutableIntStateOf(0) }
    var localPinMenu by remember(conversation.id, messageSessionIdentity) { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatchIndex by remember { mutableIntStateOf(0) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var wallpaperOverride by remember(conversation.id) { mutableStateOf(wallpaperPatternId) }
    var wallpaperPickerOpen by remember { mutableStateOf(false) }
    var clearConfirmOpen by remember { mutableStateOf(false) }
    var emojiComposerOpen by remember { mutableStateOf(false) }
    val emojiReactionTargetState = remember { mutableStateOf<ChatMessage?>(null) }
    var emojiReactionTarget by emojiReactionTargetState
    var connectionSheetOpen by remember { mutableStateOf(false) }
    var connectionTesting by remember { mutableStateOf(false) }
    val menuView = LocalView.current
    val menuConfig = LocalConfiguration.current
    val menuDensity = LocalDensity.current
    var typingPublished by remember { mutableStateOf(false) }
    var typingIdleJob by remember { mutableStateOf<Job?>(null) }
    val searchFocusRequester = remember { FocusRequester() }

    val listState = rememberLazyListState()
    val selectedMessages = messages.filter { it.id in selectedIds }
    val isSelectionMode = selectedMessages.isNotEmpty()
    val selectionActions = com.example.chat.ui.chats.detail.SelectionActionPolicy.available(selectedMessages)
    androidx.activity.compose.BackHandler(isSelectionMode) { selectedIds = emptySet() }

    // One projection owns both rendered rows and all scroll destinations.
    // Selection and menus do not change projection or tile geometry. Group identity comes from
    // the validated album envelope, never adjacency or timestamps.
    val timelineRows = com.example.chat.ui.chats.detail.mediaTimelineRows(messages)
    val senderClusters = com.example.chat.ui.chats.detail.SenderClusterPolicy.forRows(timelineRows, java.time.ZoneId.systemDefault())
    fun selectionUnit(messageId: String): Set<String> =
        com.example.chat.ui.chats.detail.AlbumSelectionPolicy.unit(timelineRows, messageId)
    fun toggleSelectionUnit(messageId: String) {
        selectedIds = com.example.chat.ui.chats.detail.AlbumSelectionPolicy.toggle(timelineRows, selectedIds, messageId)
    }
    val timelineZone = java.time.ZoneId.systemDefault()
    var pendingScrollTarget by remember(conversation.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingScrollTarget, timelineRows) {
        val target = pendingScrollTarget ?: return@LaunchedEffect
        val rowIndex = com.example.chat.ui.chats.detail.ChatTimelinePositionPolicy.rowIndex(timelineRows, target)
        if (rowIndex >= 0) listState.animateScrollToItem(rowIndex)
        pendingScrollTarget = null
    }
    var openedAtLatest by remember(conversation.id) { mutableStateOf(false) }
    LaunchedEffect(conversation.id, messages.isNotEmpty()) {
        if (!openedAtLatest && messages.isNotEmpty()) {
            openedAtLatest = true
            if (initialMessageId == null) {
                com.example.chat.ui.chats.detail.ChatTimelinePositionPolicy.latestRowIndex(timelineRows)?.let {
                    listState.scrollToItem(it)
                }
            }
        }
    }

    // Dart _isNearBottom: the list counts as pinned while the last item is
    // visible (with one item of slack for fractional last-row visibility).
    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 2
        }
    }

    fun scrollToBottom(animate: Boolean = true) {
        if (messages.isEmpty()) return
        scope.launch {
            if (animate) {
                listState.animateScrollToItem(timelineRows.lastIndex)
            } else {
                listState.scrollToItem(timelineRows.lastIndex)
            }
        }
    }

    // New messages: follow the bottom when pinned, else count the unseen.
    var previousCount by remember { mutableIntStateOf(messages.size) }
    LaunchedEffect(messages.size) {
        val delta = messages.size - previousCount
        previousCount = messages.size
        if (delta <= 0) return@LaunchedEffect
        if (isNearBottom) {
            scrollToBottom()
            pendingBelowCount = 0
        } else {
            pendingBelowCount += delta
        }
    }

    // Dart _performInChatSearch: case-insensitive substring over message
    // text in timeline order; first match auto-highlights.
    val searchMatchedIds = messages.let { msgs ->
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else msgs.filter { it.text.lowercase().contains(q) }.map { it.id }
    }

    var highlightClearJob by remember { mutableStateOf<Job?>(null) }
    fun flashHighlight(messageId: String, durationMs: Long = 1800L) {
        highlightClearJob?.cancel()
        highlightedMessageId = messageId
        highlightClearJob = scope.launch {
            delay(durationMs)
            if (highlightedMessageId == messageId) highlightedMessageId = null
        }
    }

    fun highlightAndJump(messageId: String) {
        flashHighlight(messageId)
        pendingScrollTarget = messageId
    }

    // Dart call-site lookup (chat_detail_screen.dart:2966-2984): firstWhere with a
    // synthesized placeholder task in orElse, so a linked id missing from the tasks
    // list still yields a card with message-derived fields (dueAt = createdAt + 3d).
    fun resolveTask(message: ChatMessage): ChatTask? = message.linkedTaskId?.let { id ->
        tasks.firstOrNull { it.id == id } ?: ChatTask(
            id = id,
            sourceConversationId = conversation.id,
            sourceMessageId = message.id,
            title = message.text,
            description = "",
            creatorId = message.senderId,
            assigneeIds = emptyList(),
            dueAt = message.createdAt.plus(Duration.ofDays(3)),
            createdAt = message.createdAt,
            updatedAt = message.createdAt,
        )
    }

    // Dart _toggleTaskStatus: lightImpact haptic + completed<->inbox flip (source :1200).
    fun toggleTask(task: ChatTask) {
        ChatyHapticsPort.light(menuView)
        onUpdateTaskStatus(
            task.id,
            if (task.status == TaskStatus.completed) TaskStatus.inbox else TaskStatus.completed,
        )
    }

    fun performSearch(query: String) {
        searchQuery = query
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            searchMatchIndex = 0
            highlightedMessageId = null
            return
        }
        val matched = messages.filter { it.text.lowercase().contains(q) }.map { it.id }
        searchMatchIndex = if (matched.isNotEmpty()) 1 else 0
        matched.firstOrNull()?.let(::highlightAndJump)
    }

    fun jumpToMatch(index: Int) {
        if (searchMatchedIds.isEmpty()) return
        val clamped = index.coerceIn(1, searchMatchedIds.size)
        searchMatchIndex = clamped
        highlightAndJump(searchMatchedIds[clamped - 1])
    }

    fun toggleSearch() {
        searchOpen = !searchOpen
        if (!searchOpen) {
            searchQuery = ""
            searchMatchIndex = 0
            highlightedMessageId = null
        }
    }

    // Dart _highlightMessage/_jumpToMessage share one cancelable highlight timer
    // (_searchHighlightTimer): 1800ms for search flashes, 3s for the initial jump.
    // Dart initialMessageId flow (chat_detail_screen.dart:172, :205-210,
    // _jumpToMessage): once messages are present, jump to the target, highlight
    // it for 3s, and snackbar when the message no longer exists.
    var pendingInitialJump by remember { mutableStateOf(initialMessageId) }
    LaunchedEffect(pendingInitialJump, messages.isNotEmpty()) {
        val target = pendingInitialJump ?: return@LaunchedEffect
        if (messages.isEmpty()) return@LaunchedEffect
        pendingInitialJump = null
        val index = messages.indexOfFirst { it.id == target }
        if (index < 0) {
            snackbarHostState.showSnackbar("This message is no longer available.")
            return@LaunchedEffect
        }
        flashHighlight(target, durationMs = 3000L)
        pendingScrollTarget = target
    }
    LaunchedEffect(conversation.id, messageSessionIdentity, externalSearchRequest) {
        if (externalSearchRequest > 0 && isMessageSessionCurrent()) {
            searchOpen = true
            searchQuery = ""
            searchMatchIndex = 0
        }
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    // Dart _handleComposerChanged: persist draft, publish typing with a 5s
    // idle reset, and open the quick-reply overlay on '#'.
    fun handleComposerChanged(value: String) {
        composerText = value
        onDraftChanged(value)
        val shouldType = value.trim().isNotEmpty()
        if (shouldType && !typingPublished) {
            typingPublished = true
            onTypingChanged(true)
        }
        if (!shouldType && typingPublished) {
            typingPublished = false
            onTypingChanged(false)
        }
        typingIdleJob?.cancel()
        if (shouldType) {
            typingIdleJob = scope.launch {
                delay(5000)
                if (typingPublished) {
                    typingPublished = false
                    onTypingChanged(false)
                }
            }
        }
    }

    // Dart _onMessageLongPress: a rect-less long press synthesizes a 100x40
    // anchor at screen center (logical px -> window px here).
    fun syntheticCenterRect(): Rect {
        val wPx = with(menuDensity) { menuConfig.screenWidthDp.dp.toPx() }
        val hPx = with(menuDensity) { menuConfig.screenHeightDp.dp.toPx() }
        val rw = with(menuDensity) { 100.dp.toPx() }
        val rh = with(menuDensity) { 40.dp.toPx() }
        return Rect(
            wPx / 2f - rw / 2f,
            hPx / 2f - rh / 2f,
            wPx / 2f + rw / 2f,
            hPx / 2f + rh / 2f,
        )
    }

    // Dart _onMessageLongPressWithRect: selection haptic, then the anchored
    // AppContextMenu with reaction rail (always; the api-branch detail
    // screen has no bottom-sheet branch).
    fun openMessageMenu(message: ChatMessage, rect: Rect?) {
        ChatyHapticsPort.selection(menuView)
        selectedIds = selectedIds + selectionUnit(message.id)
        contextMenuMessage = message
        contextMenuAnchor = rect ?: syntheticCenterRect()
    }

    fun runAction(selected: ChatMessage, type: MessageActionType) {
        val message = messages.firstOrNull { it.id == selected.id } ?: return
        // Anchored menus and the single-tap dialog share the same protected-content rules.
        val capabilities = com.example.chat.ui.chats.detail.SelectionActionPolicy.available(listOf(message))
        val required = when (type) {
            MessageActionType.copy -> com.example.chat.ui.chats.detail.SelectionAction.copy
            MessageActionType.forward -> com.example.chat.ui.chats.detail.SelectionAction.forward
            MessageActionType.star -> com.example.chat.ui.chats.detail.SelectionAction.star
            else -> null
        }
        if (required != null && required !in capabilities) return
        when (type) {
            MessageActionType.reply -> replyTarget = message
            MessageActionType.copy -> {
                clipboard.setText(AnnotatedString(message.text))
                scope.launch { snackbarHostState.showSnackbar("Message copied") }
            }
            MessageActionType.edit -> {
                // Dart sets the editing id + text and keeps the reply target.
                editingMessage = message
                handleComposerChanged(message.text)
            }
            MessageActionType.star -> onToggleStar(message.id)
            MessageActionType.pin -> onTogglePin(message.id)
            MessageActionType.forward -> {
                val ids = selectionUnit(message.id)
                val batch = messages.filter { it.id in ids }
                if (batch.size > 1) {
                    if (onForwardSelection != null && com.example.chat.ui.chats.detail.SelectionAction.forward in
                        com.example.chat.ui.chats.detail.SelectionActionPolicy.available(batch)) onForwardSelection(batch)
                    else scope.launch { snackbarHostState.showSnackbar("Forwarding this group is not available here.") }
                } else onForward(message)
            }
            MessageActionType.select -> selectedIds = selectedIds + selectionUnit(message.id)
            // Dart emits MessageActionType.info but _executeMessageAction has
            // no info case (default: break) - the row is a source no-op.
            MessageActionType.info -> infoMessage = message
            MessageActionType.task -> onCreateTaskFromMessage(message)
            MessageActionType.deleteForMe -> onDeleteMessage(message.id, false)
            MessageActionType.deleteForEveryone -> onDeleteMessage(message.id, true)
            MessageActionType.translate -> {
                // Dart _executeMessageAction translate: informational snackbar only.
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Translation is not configured. Choose a supported translation provider in Settings.",
                    )
                }
            }
            MessageActionType.report -> if (message.senderId != currentUserId) onReportMessage(message)
            MessageActionType.blockUser -> if (message.senderId != currentUserId) onBlockMessageSender(message.senderId)
            else -> Unit
        }
    }

    // Dart _sendMessage: edit branch restores the draft on failure; a
    // leading /task command routes to task creation instead of sending;
    // the reply target is cleared once the send starts.
    fun sendCurrent() {
        val text = composerText.trim()
        if (text.isEmpty() || sending) return
        val editTarget = editingMessage
        if (editTarget != null) {
            sending = true
            handleComposerChanged("")
            editingMessage = null
            replyTarget = null
            scope.launch {
                try {
                    onEditMessage(editTarget.id, text)
                } catch (error: Exception) {
                    editingMessage = editTarget
                    composerText = text
                    snackbarHostState.showSnackbar(
                        error.message?.removePrefix("Exception: ") ?: "Could not edit message",
                    )
                } finally {
                    sending = false
                }
            }
            return
        }
        val command = ChatCommandParser.parse(text)
        if (command.type == ChatCommandType.task) {
            handleComposerChanged("")
            onCreateTask(command.argument.ifEmpty { null })
            return
        }
        sending = true
        handleComposerChanged("")
        replyTarget = null
        scope.launch {
            try {
                onSend(text)
            } catch (error: Exception) {
                snackbarHostState.showSnackbar(
                    error.message?.removePrefix("Exception: ") ?: "Could not send message",
                )
            } finally {
                sending = false
            }
        }
    }

    // Dart chat_detail_screen.dart:2210-2292 - quick-contact rail shown when the
    // per-chat toggle is on AND the screen is at least 720dp wide; bg is the
    // custom color unless unset (0xFF000000), where it falls back to the surface
    // color at sidebarOpacity.
    val showQuickRail = convPrefs.enableQuickContactSidebar &&
        LocalConfiguration.current.screenWidthDp >= 720
    val railBg = if (convPrefs.quickContactBgColor != 0xFF000000L) {
        Color(convPrefs.quickContactBgColor)
    } else {
        Color(theme.surfaceColor).copy(alpha = convPrefs.sidebarOpacity.toFloat())
    }
    val railBottomAnchored = convPrefs.quickContactSidebarPosition == "Bottom"

    // Dart :2295-2311 - drag-to-pop: a rightward fling past 300 px/s pops unless
    // universal.disableSwipeToExit is on. onDragStopped receives px/s.
    val swipeExitModifier = if (disableSwipeToExit) {
        Modifier
    } else {
        Modifier.draggable(
            state = rememberDraggableState { },
            orientation = Orientation.Horizontal,
            onDragStopped = { velocity ->
                if (velocity > 300f) onBack()
            },
        )
    }

    Row(Modifier.fillMaxSize().then(swipeExitModifier)) {
        // Dart :2287-2292 - rail side comes from convPrefs.sidebarPosition;
        // quickContactSidebarPosition only anchors contacts top/bottom.
        if (showQuickRail && convPrefs.sidebarPosition == "Left") {
            QuickContactSidebar(
                theme = theme,
                contacts = quickContacts,
                backgroundColor = railBg,
                textColor = Color(convPrefs.quickContactTextColor),
                bottomAnchored = railBottomAnchored,
                avatarShape = homeAvatarShape,
                onOpen = { onOpenConversation(it.id) },
            )
        }
        Box(Modifier.weight(1f)) {
    Scaffold(
        modifier = Modifier.testTag("chat-screen-root"),
        containerColor = Color(theme.backgroundColor),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            if (isSelectionMode) {
                SelectionHeader(theme, selectedMessages.size) { selectedIds = emptySet() }
            } else if (searchOpen) {
                SearchHeader(
                    theme = theme,
                    query = searchQuery,
                    onQueryChange = ::performSearch,
                    matchIndex = searchMatchIndex,
                    matchCount = searchMatchedIds.size,
                    onPrevious = { jumpToMatch(searchMatchIndex - 1) },
                    onNext = { jumpToMatch(searchMatchIndex + 1) },
                    onClose = {
                        if (searchQuery.isNotEmpty()) {
                            performSearch("")
                        } else {
                            toggleSearch()
                        }
                    },
                    onBack = ::toggleSearch,
                    focusRequester = searchFocusRequester,
                )
            } else {
                ConversationHeader(
                    theme = theme,
                    conversation = conversation,
                    presenceText = presenceText,
                    presenceIsOnline = presenceIsOnline,
                    showHeaderAvatar = showHeaderAvatar,
                    showHeaderName = showHeaderName,
                    showPresenceLine = showPresenceLine,
                    showHeaderCalls = showHeaderCalls,
                    headerBarColor = headerBarColor,
                    presencePillColor = presencePillColor,
                    presenceTextColor = presenceTextColor,
                    callsAllowed = callsAllowed,
                    onStartCall = onStartCall,
                    connectionState = connectionState,
                    onConnectionDetailsTap = { connectionSheetOpen = true },
                    onBack = onBack,
                    onOpenInfo = onOpenInfo,
                    menuOpen = chatMenuOpen,
                    onMenuOpenChange = { chatMenuOpen = it },
                    onSearch = {
                        chatMenuOpen = false
                        toggleSearch()
                    },
                    onOpenWallpaperPicker = {
                        chatMenuOpen = false
                        wallpaperPickerOpen = true
                    },
                    onOpenAppearance = onOpenAppearance,
                    onToggleMute = onToggleMute,
                    ghostModeEnabled = ghostModeEnabled,
                    onToggleGhostMode = onToggleGhostMode,
                    onBlockContact = onBlockContact,
                    onClearConversation = {
                        chatMenuOpen = false
                        clearConfirmOpen = true
                    },
                )
            }

            val localPinned = com.example.chat.core.messages.LocalPinnedMessagePolicy.latest(messages)
            if (localPinned != null) {
                Box(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().background(Color(theme.cardColor))
                        .clickable { localPinMenu = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pinned on this device", color = Color(theme.accentColor), fontSize = 11.sp)
                            Text(localPinned.text.ifBlank { localPinned.attachment?.name ?: "Message" },
                                color = Color(theme.primaryTextColor), maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    androidx.compose.material3.DropdownMenu(expanded = localPinMenu,
                        onDismissRequest = { localPinMenu = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Go to message") }, onClick = {
                            localPinMenu = false
                            if (isMessageSessionCurrent() && messages.any { it.id == localPinned.id }) {
                                flashHighlight(localPinned.id, durationMs = 2500L); pendingScrollTarget = localPinned.id
                            }
                        })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Unpin on this device") }, onClick = {
                            localPinMenu = false
                            if (isMessageSessionCurrent() && messages.any { it.id == localPinned.id && it.isPinned }) onTogglePin(localPinned.id)
                        })
                    }
                }
            }

            // No strip banner in chats: the header signal icon is the only Connection Health tap target.

            Box(Modifier.weight(1f).fillMaxWidth()) {
                ChatWallpaper(
                    theme = theme,
                    wallpaperType = wallpaperType,
                    patternId = wallpaperOverride ?: theme.wallpaperId,
                    profileColorHex = conversation.avatarColorHex,
                    imagePath = wallpaperPath,
                    profilePicWallpaper = wallpaperProfilePic,
                    avatarUrl = wallpaperAvatarUrl,
                )
                // Compact album geometry stays stable while selecting; effects still use each real message ID.
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("chat-message-list"),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(
                        timelineRows,
                        key = { _, row -> row.anchor.id },
                        contentType = { _, row -> if (row.messages.size > 1) "media-album" else row.anchor.type },
                    ) { rowIndex, row ->
                        Column(Modifier.fillMaxWidth()) {
                        if (com.example.chat.ui.chats.detail.ChatTimelinePositionPolicy.startsDay(timelineRows, rowIndex, timelineZone)) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(com.example.chat.ui.chats.detail.ChatTimelinePositionPolicy.dayAt(timelineRows, rowIndex, timelineZone)
                                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")),
                                    color = Color(theme.secondaryTextColor), fontSize = 11.sp,
                                    modifier = Modifier.background(Color(theme.cardColor), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        val message = row.anchor
                        val isMine = message.senderId == currentUserId
                        val localText = remember(message, localOverrideAccountKey, conversation.id, localMessageRevision) { localMessageStore.text(message) }
                        if (localText != null) Text("Modified locally - only on this device", color = Color(theme.secondaryTextColor), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 16.dp))
                        val bubble: @Composable () -> Unit = { ChatMessageBubble(
                            message = if (localText != null) message.copy(text = localText) else message,
                            isMe = isMine,
                            theme = theme,
                            // Dart: poll messages ([POLL] prefix) open the poll sheet on bubble tap.
                            modifier = if (message.text.startsWith("[POLL] ")) {
                                Modifier.pointerInput(message.id) {
                                    detectTapGestures(onTap = { onPollTap(message) })
                                }
                            } else {
                                Modifier
                            },
                            senderName = if (conversation.isGroup && !isMine) {
                                resolveSenderName(message.senderId)
                            } else {
                                null
                            },
                            showGroupAvatar = showGroupAvatar,
                            senderCluster = senderClusters[rowIndex],
                            isSenderAdmin = conversation.isGroup &&
                                conversation.adminIds.contains(message.senderId),
                            makeTextSelectable = convPrefs.makeTextSelectable,
            enableAnimatedEmojis = convPrefs.enableAnimatedEmojis,
            emojiStyle = universalEmojiStyle,
                            groupAdminIcon = convPrefs.groupAdminIcon,
                            hideAdminNameIcon = convPrefs.hideAdminNameIcon,
                            voicePlayButtonColor = convPrefs.voiceNotePlayButtonColor?.let(::Color),
                            voicePlayingBarColor = convPrefs.voiceNotePlayingBarColor?.let(::Color),
                            voicePlaybackSpeed = convPrefs.voicePlaybackSpeed,
                            voiceUrlResolver = voiceUrlResolver,
                            attachmentBinding = attachmentBinding,
                            p2pTransferState = p2pTransferStates.firstOrNull { transfer ->
                                transfer.transferId == message.attachment?.id ||
                                    transfer.transferId == message.attachment?.url?.substringAfterLast('/')
                            },
                            isSelected = selectedIds.contains(message.id) ||
                                contextMenuMessage?.id == message.id ||
                                highlightedMessageId == message.id,
                            showDeletedContent = showDeletedContent,
                            retainViewOnce = retainViewOnce,
                            viewOnceOpened = openedViewOnceIds.contains(message.id),
                            colorOverrides = colorOverrides,
                            remoteImage = remoteImage,
                            onLongPress = { openMessageMenu(message, null) },
                            onLongPressWithRect = { rect -> openMessageMenu(message, rect) },
                            onBubbleTap = { rect ->
                                if (isSelectionMode) toggleSelectionUnit(message.id)
                                else if (!convPrefs.disableMoreOptionsFromBubble) singleTapMessage = message
                            },
                            mediaContent = if (row.messages.size > 1) ({
                                com.example.chat.ui.chats.detail.CompactAlbumPreview(row.messages, attachmentBinding, remoteImage, voiceUrlResolver,
                                    onOpen = { item, rect -> if (isSelectionMode) toggleSelectionUnit(item.id) else if (rect != null && onMediaTapWithBounds != null) onMediaTapWithBounds(item, rect) else onMediaTap(item) },
                                    onMenu = { item, rect -> openMessageMenu(item, rect) },
                                    onReply = { item -> replyTarget = item },
                                    onReact = if (disableDoubleTapReaction || !ReleaseFeatures.MESSAGE_REACTIONS) null else ({ item ->
                                        if (com.example.chat.ui.chats.detail.SelectionAction.star in com.example.chat.ui.chats.detail.SelectionActionPolicy.available(listOf(item))) onToggleReaction(item.id, doubleTapReactionEmoji)
                                    }))
                            }) else null,
                            onSwipeReply = if (row.messages.size > 1) null else ({ replyTarget = message }),
                            onDoubleTap = if (disableDoubleTapReaction || !ReleaseFeatures.MESSAGE_REACTIONS || row.messages.size > 1 || com.example.chat.ui.chats.detail.SelectionAction.star !in com.example.chat.ui.chats.detail.SelectionActionPolicy.available(listOf(message))) {
                                null
                            } else {
                                { onToggleReaction(message.id, doubleTapReactionEmoji) }
                            },
                            onReactionTap = { emoji -> if (ReleaseFeatures.MESSAGE_REACTIONS && com.example.chat.ui.chats.detail.SelectionAction.star in com.example.chat.ui.chats.detail.SelectionActionPolicy.available(listOf(message))) onToggleReaction(message.id, emoji) },
                            onMediaTapWithBounds = { rect -> onMediaTapWithBounds?.invoke(message, rect) ?: onMediaTap(message) },
                            onMediaTap = if (message.attachment == null) {
                                null
                            } else {
                                { onMediaTap(message) }
                            },
                            onVoicePlay = { attachment -> onVoicePlay(attachment) },
                            onViewOnceOpen = {
                                openedViewOnceIds = openedViewOnceIds + message.id
                                onViewOnceOpen(message)
                            },
                            onRetry = if (isMine && message.deliveryState == DeliveryState.failed) {
                                { onRetryMessage(message.id) }
                            } else {
                                null
                            },
                            task = resolveTask(message),
                            onTaskTap = {
                                // Dart onTaskTap: resolved task -> details; otherwise create
                                // modal seeded with message text/id (source :2985-3003).
                                val currentTask = resolveTask(message)
                                if (currentTask != null) {
                                    onOpenTaskDetails(currentTask)
                                } else {
                                    onCreateTaskFromMessage(message)
                                }
                            },
                            onTaskToggle = { resolveTask(message)?.let(::toggleTask) },
                            onTaskMenu = { rect ->
                                resolveTask(message)?.let { taskMenuTarget = it to rect }
                            },
                        ) }
                        if (isSelectionMode) {
                            Box(Modifier.fillMaxWidth().background(
                                if (message.id in selectedIds) Color(0x447F7F7F) else Color.Transparent,
                            )) {
                                bubble()
                                Box(Modifier.matchParentSize().pointerInput(message.id, selectedIds) {
                                    detectTapGestures(onTap = {
                                        toggleSelectionUnit(message.id)
                                    })
                                })
                            }
                        } else {
                            bubble()
                        }
                        }
                    }
                }

                ChatMessagesStateOverlay(
                    theme = theme,
                    messagesEmpty = messages.isEmpty(),
                    loadingMessages = loadingMessages,
                    loadError = loadError,
                    onRetryLoad = onRetryLoad,
                )

                // Scroll-to-bottom FAB with unseen-count badge.
                val fabVisible = !isNearBottom && messages.isNotEmpty()
                val fabScale by animateFloatAsState(if (fabVisible) 1f else 0.4f, label = "fabScale")
                val fabAlpha by animateFloatAsState(if (fabVisible) 1f else 0f, label = "fabAlpha")
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 12.dp)
                        .size(48.dp).testTag("chat-scroll-to-latest")
                        .scale(fabScale)
                        .alpha(fabAlpha)
                        .background(Color(theme.cardColor), CircleShape)
                        .border(1.dp, Color(theme.surfaceColor), CircleShape)
                        .clickable(
                            enabled = fabVisible,
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClickLabel = "Scroll to latest messages",
                        ) {
                            scrollToBottom()
                            pendingBelowCount = 0
                        }
                        .then(if (!fabVisible) Modifier.clearAndSetSemantics { } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        tint = Color(theme.primaryTextColor),
                        modifier = Modifier.size(27.dp),
                    )
                    if (pendingBelowCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-20).dp)
                                .background(Color(theme.accentColor), CircleShape)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "$pendingBelowCount",
                                color = Color(theme.onAccentColor),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Quick replies overlay (Dart _showQuickReplyOverlay section).
            if (composerText.contains("#") && quickReplies.isNotEmpty()) {
                QuickReplyOverlay(
                    theme = theme,
                    replies = quickReplies,
                    onPick = { reply -> handleComposerChanged(reply.content) },
                )
            }

            // Edit banner (Dart _editingMessageId section).
            editingMessage?.let { editing ->
                BannerRow(
                    theme = theme,
                    icon = {
                        Icon(Icons.Rounded.Edit, contentDescription = null,
                            tint = Color(theme.accentColor), modifier = Modifier.size(18.dp))
                    },
                    title = "Editing message",
                    titleColor = Color(theme.accentColor),
                    subtitle = editing.text,
                    onClose = {
                        editingMessage = null
                        handleComposerChanged("")
                    },
                )
            }

            // Reply banner (Dart _replyTarget section).
            replyTarget?.let { target ->
                BannerRow(
                    theme = theme,
                    icon = {
                        Icon(Icons.Rounded.Reply, contentDescription = null,
                            tint = Color(theme.accentColor), modifier = Modifier.size(18.dp))
                    },
                    title = "Replying to ${resolveSenderName(target.senderId)}",
                    titleColor = Color(theme.accentColor),
                    subtitle = target.text,
                    onClose = { replyTarget = null },
                )
            }

            // Dart composer bar container (chat_detail_screen.dart:3233): bg =
            // conv.uiEntryBackgroundColor ?: surfaceColor, top border cardColor.
            val composerBarBg = convPrefs.uiEntryBackgroundColor?.let(::Color)
                ?: Color(theme.surfaceColor)
            Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(composerBarBg)
                    .drawBehind {
                        drawLine(
                            color = Color(theme.cardColor),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                ChatEntryBar(
                    entryStyle = entryStyle,
                    colors = composerColors ?: ComposerColors.resolve(theme),
                    template = composerTemplate,
                    theme = theme,
                    text = composerText,
                    onTextChange = ::handleComposerChanged,
                    emojiPickerOpen = emojiComposerOpen,
                    onEmoji = { emojiComposerOpen = true },
                    onAttach = { attachmentSheetOpen = true },
                    onCamera = onCameraTap,
                    onGallery = onGalleryTap,
                    onAudio = onAudioTap,
                    onLocation = onLocationTap,
                    onContact = onContactTap,
                    onVoice = onVoiceTap,
                    onVoiceHoldStart = onVoiceHoldStart,
                    onVoiceMove = onVoiceMove,
                    onVoiceHoldEnd = onVoiceHoldEnd,
                    onVideoHoldStart = onVideoHoldStart,
                    onVideoHoldMove = onVideoHoldMove,
                    onVideoHoldEnd = onVideoHoldEnd,
                    onVideoHoldCancel = onVideoHoldCancel,
                    onSend = ::sendCurrent,
                )
            }

            }

            // GB-style emoji panel: docked under the message box at half height;
            // the composer stays on top and picks append into it directly.
            if (emojiComposerOpen) {
                androidx.activity.compose.BackHandler { emojiComposerOpen = false }
                ChatyEmojiPickerSheet(
                    theme = theme,
                    reactionMode = false,
                    composerMode = true,
                    onStickerSend = onStickerSend?.let { send -> { sticker -> if (isMessageSessionCurrent()) send(sticker) } },
                    stickerSessionKey = messageSessionIdentity,
                    headerColor = convPrefs.emojiHeaderColor?.let(::Color),
                    headerIconsColor = convPrefs.emojiHeaderIconsColor?.let(::Color),
                    backgroundColor = convPrefs.emojiPickerBgColor?.let(::Color),
                    onEmojiPicked = { emoji ->
                        if (emoji.isNotEmpty()) handleComposerChanged(composerText + emoji)
                    },
                    onComposerDone = { emojiComposerOpen = false },
                )
            }
        }
                if (isSelectionMode) {
                    Box(Modifier.align(Alignment.Center).padding(horizontal = 8.dp)) {
                SelectionActions(
                    theme = theme,
                    actions = if (onForwardSelection != null || selectedMessages.size == 1) selectionActions
                        else selectionActions - com.example.chat.ui.chats.detail.SelectionAction.forward,
                    onCopy = {
                        val msgs = messages
                            .filter { selectedIds.contains(it.id) }
                            .joinToString("\n") { m ->
                                if (hideDateAndName) {
                                    m.text
                                } else {
                                    val timeStr = selectionCopyFormatter.format(
                                        m.createdAt.atZone(ZoneId.systemDefault()),
                                    )
                                    "[$timeStr, ${resolveSenderName(m.senderId)}]: ${m.text}"
                                }
                            }
                        clipboard.setText(AnnotatedString(msgs))
                        selectedIds = emptySet()
                        scope.launch { snackbarHostState.showSnackbar("Messages copied") }
                    },
                    onForward = {
                        onForwardSelection?.invoke(selectedMessages)
                            ?: selectedMessages.singleOrNull()?.let(onForward)
                        selectedIds = emptySet()
                    },
                    onStar = {
                        selectedIds.forEach(onToggleStar)
                        selectedIds = emptySet()
                    },
                    onDelete = {
                        selectedIds.forEach { onDeleteMessage(it, false) }
                        selectedIds = emptySet()
                    },
                )
                    }
                }
        }
    }

    singleTapMessage?.let { selected -> messages.firstOrNull { it.id == selected.id } }?.let { tapped ->
        val available = com.example.chat.ui.chats.detail.SelectionActionPolicy.available(listOf(tapped))
        val standard = com.example.chat.ui.chats.detail.SelectionAction.star in available
        val textAllowed = com.example.chat.ui.chats.detail.SelectionAction.copy in available
        com.example.chat.ui.chats.detail.MessageMoreOptionsDialog(
            options = listOf(
                com.example.chat.ui.chats.detail.MessageMoreOption("Modify Chat", localOverrideAccountKey.isNotBlank() && isMessageSessionCurrent() && com.example.chat.core.messages.LocalMessageOverridePolicy.editable(tapped)) {
                    localEditMessageId = tapped.id
                    localEditText = localMessageStore.text(tapped) ?: tapped.text
                },
                com.example.chat.ui.chats.detail.MessageMoreOption("Copy", textAllowed) { runAction(tapped, MessageActionType.copy) },
                com.example.chat.ui.chats.detail.MessageMoreOption("Copy Selection", textAllowed) { copySelectionMessage = tapped },
                com.example.chat.ui.chats.detail.MessageMoreOption("Translate", false) {},
                com.example.chat.ui.chats.detail.MessageMoreOption("Change Translator Language", false) {},
                com.example.chat.ui.chats.detail.MessageMoreOption("interaction", ReleaseFeatures.MESSAGE_REACTIONS && standard) { emojiReactionTarget = tapped },
                com.example.chat.ui.chats.detail.MessageMoreOption("Double Tap Reaction", ReleaseFeatures.MESSAGE_REACTIONS && onDoubleTapReactionEmojiChanged != null) { choosingDoubleTapEmoji = true },
                com.example.chat.ui.chats.detail.MessageMoreOption("Image quote", textAllowed) { quoteMessageId = tapped.id },
                com.example.chat.ui.chats.detail.MessageMoreOption("Share", textAllowed) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, tapped.text)
                    }
                    try { shareContext.startActivity(android.content.Intent.createChooser(intent, "Share message")) }
                    catch (_: android.content.ActivityNotFoundException) {
                        scope.launch { snackbarHostState.showSnackbar("No app is available to share this message.") }
                    }
                },
                com.example.chat.ui.chats.detail.MessageMoreOption(if (tapped.isStarred) "Unstar" else "Star", standard) { onToggleStar(tapped.id) },
                com.example.chat.ui.chats.detail.MessageMoreOption("Starred messages", true) { showStarredInChat = true },
                com.example.chat.ui.chats.detail.MessageMoreOption("Notify When Message Read", false) {},
                com.example.chat.ui.chats.detail.MessageMoreOption("Message info", true) { infoMessage = tapped },
            ),
            onDismiss = { singleTapMessage = null },
        )
    }
    quoteMessageId?.let { id -> messages.firstOrNull { it.id == id } }
        ?.takeIf { com.example.chat.ui.chats.detail.SelectionAction.copy in com.example.chat.ui.chats.detail.SelectionActionPolicy.available(listOf(it)) }?.let { target ->
        com.example.chat.ui.chats.detail.MessageQuoteDialog(target, resolveQuoteSender(target.senderId), resolveSenderName(target.senderId),
            currentMessage = { if (isMessageSessionCurrent()) messages.firstOrNull { it.id == target.id } else null }, onClose = { quoteMessageId = null })
    }
    localEditMessageId?.let { id -> messages.firstOrNull { it.id == id } }
        ?.takeIf { com.example.chat.core.messages.LocalMessageOverridePolicy.editable(it) }?.let { target ->
        com.example.chat.ui.chats.detail.LocalMessageEditDialog(
            value = localEditText, onValueChanged = { if (it.length <= 12000) localEditText = it },
            onDismiss = { localEditMessageId = null },
            onRestore = { if (isMessageSessionCurrent()) { localMessageStore.reset(target.id); localMessageRevision++ }; localEditMessageId = null },
            onSave = { if (isMessageSessionCurrent() && localMessageStore.save(target, localEditText)) localMessageRevision++; localEditMessageId = null },
        )
    }
    com.example.chat.ui.chats.detail.MessageDetailDialogs(
        messages = messages, showStarred = showStarredInChat,
        copyMessageId = copySelectionMessage?.id, infoMessageId = infoMessage?.id,
        onCloseStarred = { showStarredInChat = false },
        onCloseCopy = { copySelectionMessage = null }, onCloseInfo = { infoMessage = null },
        onJumpToMessage = { id -> highlightedMessageId = id; pendingScrollTarget = id; showStarredInChat = false },
    )

    // Keep the anchored menu's generated Compose code out of the large conversation method.
    AnchoredMessageContextMenu(
        messageState = contextMenuMessageState,
        anchorRect = contextMenuAnchor,
        currentUserId = currentUserId,
        theme = theme,
        onAction = ::runAction,
        onToggleReaction = onToggleReaction,
        reactionTargetState = emojiReactionTargetState,
    )

    // Dart _showTaskContextMenu (chat_detail_screen.dart:1208-1254): section titled
    // with the task title, anchored at the chip rect, no reaction rail.
    taskMenuTarget?.let { (menuTask, taskAnchor) ->
        val taskCompleted = menuTask.status == TaskStatus.completed
        AppContextMenu(
            onDismiss = { taskMenuTarget = null },
            sections = listOf(
                ContextMenuSection(
                    title = menuTask.title,
                    items = buildList {
                        add(
                            ContextMenuItem(
                                icon = if (taskCompleted) {
                                    Icons.Rounded.Replay
                                } else {
                                    Icons.Outlined.CheckCircle
                                },
                                label = if (taskCompleted) "Reopen task" else "Mark completed",
                                onTap = { toggleTask(menuTask) },
                            ),
                        )
                        add(
                            ContextMenuItem(
                                icon = Icons.Outlined.Edit,
                                label = "Edit task",
                                onTap = { onEditTask(menuTask) },
                            ),
                        )
                        add(
                            ContextMenuItem(
                                icon = Icons.Rounded.OpenInNew,
                                label = "Task details",
                                onTap = { onOpenTaskDetails(menuTask) },
                            ),
                        )
                        if (menuTask.sourceMessageId != null) {
                            add(
                                ContextMenuItem(
                                    icon = Icons.Outlined.ChatBubbleOutline,
                                    label = "Go to source message",
                                    // Dart _highlightMessage: highlight only, no scroll jump.
                                    onTap = { menuTask.sourceMessageId?.let(::flashHighlight) },
                                ),
                            )
                        }
                        add(
                            ContextMenuItem(
                                icon = Icons.Rounded.DeleteOutline,
                                label = "Delete task",
                                isDestructive = true,
                                onTap = { onDeleteTask(menuTask.id) },
                            ),
                        )
                    },
                ),
            ),
            backgroundColor = Color(theme.surfaceColor),
            primaryTextColor = Color(theme.primaryTextColor),
            secondaryTextColor = Color(theme.secondaryTextColor),
            destructiveColor = Color(theme.dangerColor),
            anchorRect = taskAnchor,
        )
    }

    if (attachmentSheetOpen) {
        // Default model: the attachment gallery sheet (opens half height; pulling up
        // grows it toward full height). The tile picker stays available behind the
        // Settings "New Attachment Picker UI" option (conversation.newAttachmentPickerUi).
        val attachmentSheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = convPrefs.newAttachmentPickerUi,
        )
        ModalBottomSheet(
            sheetState = attachmentSheetState,
            tonalElevation = 0.dp, dragHandle = null, shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { attachmentSheetOpen = false },
            containerColor = Color.Transparent,
        ) {
            if (convPrefs.newAttachmentPickerUi) {
                AttachmentSheetContent(
                    theme = theme,
                    onAction = { action ->
                        attachmentSheetOpen = false
                        onAttachmentAction(action)
                    },
                )
            } else {
                com.example.chat.ui.chats.detail.AttachmentGalleryViewport(attachmentSheetState) {
                    com.example.chat.ui.chats.detail.AttachmentGallerySheet(
                        theme = theme,
                        // The wrapper follows the actual visible viewport, not targetValue.
                        heightFraction = 1.0f,
                        onAction = { action ->
                            attachmentSheetOpen = false
                            onAttachmentAction(action)
                        },
                        onCamera = {
                            attachmentSheetOpen = false
                            onCameraTap()
                        },
                        onReviewImages = attachmentReviewImages?.let { review ->
                            { uris ->
                                attachmentSheetOpen = false
                                review(uris)
                            }
                        },
                        onDirectSendImages = attachmentDirectSendImages?.let { send ->
                            { uris ->
                                attachmentSheetOpen = false
                                send(uris)
                            }
                        },
                    )
                }
            }
        }
    }

    // Per-chat wallpaper picker (Dart _openWallpaperPicker options).
    if (wallpaperPickerOpen) {
        ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp, shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { wallpaperPickerOpen = false },
            containerColor = Color.Transparent,
        ) {
            WallpaperPickerSheet(
                theme = theme,
                currentId = wallpaperOverride ?: theme.wallpaperId,
                onSelect = { id ->
                    wallpaperOverride = id
                    wallpaperPickerOpen = false
                    onWallpaperSelected?.invoke(id)
                },
            )
        }
    }

    if (choosingDoubleTapEmoji && ReleaseFeatures.MESSAGE_REACTIONS && onDoubleTapReactionEmojiChanged != null) {
        ModalBottomSheet(onDismissRequest = { choosingDoubleTapEmoji = false }, containerColor = Color.Transparent) {
            Column {
                Text("Choose the default double-tap reaction for conversations")
                if (disableDoubleTapReaction) Text("Double-tap reactions are disabled in Conversation settings. Choosing an emoji does not turn them on.")
                ChatyEmojiPickerSheet(theme = theme, reactionMode = true, composerMode = false,
                    onEmojiPicked = { emoji ->
                        if (emoji.isNotEmpty()) onDoubleTapReactionEmojiChanged(emoji)
                        choosingDoubleTapEmoji = false
                    })
            }
        }
    }

    // Reaction emoji picker (Dart ChatyEmojiPicker.show reactionMode).
    emojiReactionTarget?.let { selected -> messages.firstOrNull { it.id == selected.id } }
        ?.takeIf { ReleaseFeatures.MESSAGE_REACTIONS && com.example.chat.ui.chats.detail.SelectionAction.star in com.example.chat.ui.chats.detail.SelectionActionPolicy.available(listOf(it)) }?.let { target ->
        ModalBottomSheet(tonalElevation = 0.dp, dragHandle = null, shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { emojiReactionTarget = null },
            containerColor = Color.Transparent,
        ) {
            ChatyEmojiPickerSheet(
                theme = theme,
                reactionMode = true,
                composerMode = false,
                headerColor = convPrefs.emojiHeaderColor?.let(::Color),
                headerIconsColor = convPrefs.emojiHeaderIconsColor?.let(::Color),
                backgroundColor = convPrefs.emojiPickerBgColor?.let(::Color),
                onEmojiPicked = { emoji ->
                    if (emoji.isNotEmpty() && ReleaseFeatures.MESSAGE_REACTIONS) onToggleReaction(target.id, emoji)
                    emojiReactionTarget = null
                },
            )
        }
    }

    // Connection diagnostics dialog (Dart ConnectionDetailSheet.show).
    if (connectionSheetOpen) {
        connectionState?.let { state ->
            Dialog(onDismissRequest = { connectionSheetOpen = false }) {
                ConnectionDetailSheetContent(
                    state = state,
                    theme = theme,
                    testing = connectionTesting,
                    onRunTest = {
                        if (!connectionTesting) {
                            connectionTesting = true
                            scope.launch {
                                try {
                                    onProbeConnection()
                                } finally {
                                    connectionTesting = false
                                }
                            }
                        }
                    },
                    onDismiss = { connectionSheetOpen = false },
                    networkStatus = networkStatus,
                    p2pTransferStates = p2pTransferStates,
                    liveTransfers = liveTransfers,
                    appTraffic = appTraffic,
                    onRetryP2pTransfer = onRetryP2pTransfer,
                )
            }
        }
    }

    // Clear conversation confirmation (Dart _clearConversation).
    if (clearConfirmOpen) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { clearConfirmOpen = false },
            title = { Text("Clear conversation?") },
            text = { Text("All messages will be removed from this chat for you.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearConfirmOpen = false
                        onClearConversation()
                        openedViewOnceIds = emptySet()
                        pendingBelowCount = 0
                        scope.launch { snackbarHostState.showSnackbar("Conversation cleared") }
                    },
                ) {
                    Text("Clear", color = Color(theme.dangerColor))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }
        } // Box(weight 1f) around Scaffold
        if (showQuickRail && convPrefs.sidebarPosition == "Right") {
            QuickContactSidebar(
                theme = theme,
                contacts = quickContacts,
                backgroundColor = railBg,
                textColor = Color(convPrefs.quickContactTextColor),
                bottomAnchored = railBottomAnchored,
                avatarShape = homeAvatarShape,
                onOpen = { onOpenConversation(it.id) },
            )
        }
    } // Row(quick rail + chat)
    } // Selected emoji pack applies to picker, reactions and the message subtree.
}

private val selectionCopyFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
private fun ConversationHeader(
    theme: ThemeConfig,
    conversation: Conversation,
    presenceText: String?,
    presenceIsOnline: Boolean,
    showHeaderAvatar: Boolean,
    showHeaderName: Boolean,
    showPresenceLine: Boolean,
    showHeaderCalls: Boolean,
    headerBarColor: Long?,
    presencePillColor: Long?,
    presenceTextColor: Long?,
    callsAllowed: Boolean,
    onStartCall: (Boolean) -> Unit,
    connectionState: ConnectionHealthState?,
    onConnectionDetailsTap: () -> Unit,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onOpenAppearance: (() -> Unit)?,
    onToggleMute: (() -> Unit)?,
    ghostModeEnabled: Boolean,
    onToggleGhostMode: (() -> Unit)?,
    onBlockContact: (() -> Unit)?,
    onClearConversation: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(headerBarColor ?: theme.surfaceColor))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Rounded.ChevronLeft,
                contentDescription = "Back",
                tint = Color(theme.primaryTextColor),
            )
        }
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .heightIn(min = 48.dp)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Open conversation info", onClick = onOpenInfo),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showHeaderAvatar) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                conversation.avatarColorHex?.let { parseHexColor(it) }
                                    ?: Color(theme.accentColor),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            conversation.avatarInitials
                                ?: conversation.title.take(2).uppercase(),
                            color = Color(theme.onAccentColor),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (conversation.isDirect && presenceIsOnline) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 1.dp, y = 1.dp)
                                .size(11.dp)
                                .background(Color(theme.surfaceColor), CircleShape)
                                .padding(2.dp)
                                .background(Color(theme.successColor), CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                if (showHeaderName) {
                    Text(
                        conversation.title,
                        color = Color(theme.primaryTextColor),
                        fontSize = (15.5 * theme.fontScale).sp,
                        fontWeight = FontWeight.W800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showPresenceLine && presenceText != null) {
                    // Dart: pill only when a background color is set (8x2 padding, top margin 2, fully rounded).
                    Text(
                        presenceText,
                        color = Color(
                            presenceTextColor
                                ?: if (presenceIsOnline) theme.successColor else theme.secondaryTextColor,
                        ),
                        fontSize = (10.5 * theme.fontScale).sp,
                        fontWeight = FontWeight.W500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (presencePillColor == null) {
                            Modifier
                        } else {
                            Modifier
                                .padding(top = 2.dp)
                                .background(Color(presencePillColor), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        },
                    )
                }
            }
        }
        if (showHeaderCalls) {
            val callTint = if (callsAllowed) {
                Color(theme.primaryTextColor)
            } else {
                Color(theme.secondaryTextColor)
            }
            IconButton(onClick = { onStartCall(false) }) {
                Icon(
                    Icons.Rounded.Call,
                    contentDescription = if (callsAllowed) {
                        "Voice call"
                    } else {
                        "Voice call • contact acceptance required"
                    },
                    tint = callTint,
                )
            }
            IconButton(onClick = { onStartCall(true) }) {
                Icon(
                    Icons.Rounded.VideoCameraFront,
                    contentDescription = if (callsAllowed) {
                        "Video call"
                    } else {
                        "Video call • contact acceptance required"
                    },
                    tint = callTint,
                )
            }
        }
        connectionState?.let { state ->
            Box(Modifier.padding(horizontal = 4.dp)) {
                ConnectionHealthIndicator(
                    health = state.health,
                    size = 15.dp,
                    onTap = onConnectionDetailsTap,
                )
            }
        }
        Box {
            IconButton(modifier = Modifier.testTag("chat-options"), onClick = { onMenuOpenChange(true) }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Chat options",
                    tint = Color(theme.primaryTextColor),
                )
            }
            var moreMenuPage by remember(menuOpen) { mutableStateOf(false) }
            DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }, rememberKey = "chat-header-menu") {
                val menuIconTint = Color(theme.secondaryTextColor)
                if (!moreMenuPage) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Person, null, tint = menuIconTint, modifier = Modifier.size(20.dp)) },
                        text = {
                            Text(
                                if (conversation.isGroup) "View group info" else "View contact info",
                            )
                        },
                        onClick = {
                            onMenuOpenChange(false)
                            onOpenInfo()
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = menuIconTint, modifier = Modifier.size(20.dp)) },
                        text = { Text("Search") },
                        onClick = onSearch,
                    )
                    if (onToggleGhostMode != null) {
                        DropdownMenuItem(
                            modifier = Modifier.testTag("chat-ghost-toggle"),
                            text = { Text(if (ghostModeEnabled) "Turn off Ghost mode" else "Turn on Ghost mode") },
                            onClick = { onMenuOpenChange(false); onToggleGhostMode() },
                        )
                    }
                    if (onToggleMute != null) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(if (conversation.isMuted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff, null, tint = menuIconTint, modifier = Modifier.size(20.dp)) },
                            text = {
                                Text(
                                    if (conversation.isMuted) {
                                        "Unmute notifications"
                                    } else {
                                        "Mute notifications"
                                    },
                                )
                            },
                            onClick = {
                                onMenuOpenChange(false)
                                onToggleMute()
                            },
                        )
                    }
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Image, null, tint = menuIconTint, modifier = Modifier.size(20.dp)) },
                        text = { Text("Chat wallpaper") },
                        onClick = onOpenWallpaperPicker,
                    )
                    if (onOpenAppearance != null) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Outlined.Palette, null, tint = menuIconTint, modifier = Modifier.size(20.dp)) },
                            text = { Text("Chat theme") },
                            onClick = {
                                onMenuOpenChange(false)
                                onOpenAppearance()
                            },
                        )
                    }
                    HorizontalDivider(thickness = 0.8.dp, color = menuIconTint.copy(alpha = 0.15f))
                    DropdownMenuItem(
                        text = { Text("More") },
                        trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = menuIconTint, modifier = Modifier.size(20.dp)) },
                        onClick = { moreMenuPage = true },
                    )
                } else {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = menuIconTint, modifier = Modifier.size(20.dp)) },
                        text = { Text("Back") },
                        onClick = { moreMenuPage = false },
                    )
                    HorizontalDivider(thickness = 0.8.dp, color = menuIconTint.copy(alpha = 0.15f))
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = Color(theme.dangerColor), modifier = Modifier.size(20.dp)) },
                        text = { Text("Clear conversation", color = Color(theme.dangerColor)) },
                        onClick = onClearConversation,
                    )
                    if (conversation.isDirect && onBlockContact != null) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Outlined.Block, null, tint = Color(theme.dangerColor), modifier = Modifier.size(20.dp)) },
                            text = { Text("Block contact", color = Color(theme.dangerColor)) },
                            onClick = {
                                onMenuOpenChange(false)
                                onBlockContact()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The production message menu, split out to bound generated Compose register pressure.
 *  State stays owned by ChatDetailScreen; actions and appearance are unchanged. */
@Composable
private fun AnchoredMessageContextMenu(
    messageState: androidx.compose.runtime.MutableState<ChatMessage?>,
    anchorRect: Rect?,
    currentUserId: String,
    theme: ThemeConfig,
    onAction: (ChatMessage, MessageActionType) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    reactionTargetState: androidx.compose.runtime.MutableState<ChatMessage?>,
) {
    val menuMessage = messageState.value
    var extraActions by remember(menuMessage?.id) { mutableStateOf(false) }
    if (menuMessage != null) {
        val available = MessageActionRegistry.getAvailableActions(
            message = menuMessage, isMe = menuMessage.senderId == currentUserId,
        )
        val order = listOf(MessageActionType.reply, MessageActionType.forward, MessageActionType.copy,
            MessageActionType.edit, MessageActionType.deleteForMe, MessageActionType.info, MessageActionType.report, MessageActionType.blockUser)
        val primary = order.mapNotNull { type -> available.firstOrNull { it.type == type } }
        val secondary = available.filter { it.type !in order }
        val actions = if (extraActions) secondary else primary
        AppContextMenu(
            onDismiss = { messageState.value = null },
            sections = listOf(ContextMenuSection(items = buildList {
                if (extraActions) add(ContextMenuItem(label = "Back", onTap = {
                    messageState.value = menuMessage; extraActions = false
                }))
                actions.forEach { act -> add(ContextMenuItem(
                    icon = actionIcon(act, menuMessage),
                    label = if (act.type == MessageActionType.deleteForMe) "Delete for me" else act.label,
                    isDestructive = act.isDestructive,
                    onTap = { onAction(menuMessage, act.type) },
                )) }
                if (!extraActions && secondary.isNotEmpty()) add(ContextMenuItem(label = "More", onTap = {
                    // AppContextMenu dismisses before its callback; reopen the SAME anchored selection menu.
                    messageState.value = menuMessage; extraActions = true
                }, trailing = { Icon(Icons.Rounded.ChevronRight, null) }))
            })),
            backgroundColor = Color(theme.surfaceColor),
            primaryTextColor = Color(theme.primaryTextColor),
            secondaryTextColor = Color(theme.secondaryTextColor),
            destructiveColor = Color(theme.dangerColor),
            // User directive: the single-message selection menu presents
            // bottom-center (sheet), not anchored mid-screen at the bubble.
            anchorRect = null,
            // Reactions are deferred to the next release (see ReleaseFeatures).
            reactionRail = if (ReleaseFeatures.MESSAGE_REACTIONS) {
                ReactionRail(
                    emojis = listOf("❤️", "👍", "😂", "😮", "😢", "🙏"),
                    onReact = { emoji -> onToggleReaction(menuMessage.id, emoji) },
                    onAdd = { reactionTargetState.value = menuMessage },
                )
            } else {
                null
            },
        )
    }

}

@Composable
private fun SelectionHeader(theme: ThemeConfig, count: Int, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(theme.surfaceColor)), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, "Cancel selection", tint = Color(theme.primaryTextColor))
        }
        Text("$count selected", color = Color(theme.primaryTextColor), fontWeight = FontWeight.W700, fontSize = 16.sp)
    }
}

@Composable
private fun SelectionActions(
    theme: ThemeConfig,
    actions: Set<com.example.chat.ui.chats.detail.SelectionAction>,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.background(Color(theme.surfaceColor), RoundedCornerShape(14.dp)).padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        if (com.example.chat.ui.chats.detail.SelectionAction.forward in actions) SelectionActionButton(theme, "Forward", onForward) {
            Icon(Icons.AutoMirrored.Rounded.Forward, null, tint = Color(theme.primaryTextColor))
        }
        if (com.example.chat.ui.chats.detail.SelectionAction.copy in actions) SelectionActionButton(theme, "Copy", onCopy) {
            Icon(Icons.Rounded.ContentCopy, null, tint = Color(theme.primaryTextColor))
        }
        if (com.example.chat.ui.chats.detail.SelectionAction.delete in actions) SelectionActionButton(theme, "Delete", onDelete) {
            Icon(Icons.Rounded.DeleteOutline, null, tint = Color(theme.primaryTextColor))
        }
        if (com.example.chat.ui.chats.detail.SelectionAction.star in actions) SelectionActionButton(theme, "Star", onStar) {
            Icon(Icons.Rounded.StarBorder, null, tint = Color(theme.primaryTextColor))
        }
    }
}

@Composable
private fun SelectionActionButton(theme: ThemeConfig, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Text(label, color = Color(theme.primaryTextColor), fontSize = 11.sp)
        }
    }
}

@Composable
private fun BannerRow(
    theme: ThemeConfig,
    icon: @Composable () -> Unit,
    title: String,
    titleColor: Color,
    subtitle: String,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(theme.cardColor))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 11.5.sp, fontWeight = FontWeight.W700)
            Text(
                subtitle,
                color = Color(theme.secondaryTextColor),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancel",
                tint = Color(theme.primaryTextColor), modifier = Modifier.size(18.dp))
        }
    }
}

/** In-chat search app bar (Dart _isInChatSearchOpen AppBar): back, query
 *  field, "i of N" match counter, previous/next match, close-or-clear. */
@Composable
private fun SearchHeader(
    theme: ThemeConfig,
    query: String,
    onQueryChange: (String) -> Unit,
    matchIndex: Int,
    matchCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(theme.surfaceColor))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Rounded.ChevronLeft,
                contentDescription = "Back",
                tint = Color(theme.primaryTextColor),
            )
        }
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search conversation…",
                    color = Color(theme.secondaryTextColor),
                    fontSize = (15.5 * theme.fontScale).sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = Color(theme.primaryTextColor),
                    fontSize = (15.5 * theme.fontScale).sp,
                ),
                cursorBrush = SolidColor(Color(theme.accentColor)),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (matchCount > 0) {
            Text(
                "$matchIndex of $matchCount",
                color = Color(theme.secondaryTextColor),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Previous match",
                    tint = Color(theme.primaryTextColor),
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Next match",
                    tint = Color(theme.primaryTextColor),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Clear search",
                tint = Color(theme.primaryTextColor),
            )
        }
    }
}

/** Quick replies overlay (Dart _showQuickReplyOverlay block): opened by '#'
 *  in the composer, lists the automation quick replies, tap fills the
 *  composer with the reply content. */
@Composable
private fun QuickReplyOverlay(
    theme: ThemeConfig,
    replies: List<QuickReplyTemplate>,
    onPick: (QuickReplyTemplate) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .heightIn(max = 180.dp)
            .background(Color(theme.cardColor), RoundedCornerShape(14.dp))
            .border(1.dp, Color(theme.surfaceColor), RoundedCornerShape(14.dp)),
    ) {
        LazyColumn {
            items(replies.size) { index ->
                val reply = replies[index]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(reply.shortcut) {
                            detectTapGestures(onTap = { onPick(reply) })
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "${reply.shortcut} — ${reply.title}",
                        color = Color(theme.primaryTextColor),
                        fontWeight = FontWeight.W700,
                        fontSize = 12.5.sp,
                    )
                    Text(
                        reply.content,
                        color = Color(theme.secondaryTextColor),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Per-chat wallpaper picker (Dart _openWallpaperPicker): fixed pattern
 *  options with a check on the current selection. */
@Composable
private fun WallpaperPickerSheet(
    theme: ThemeConfig,
    currentId: String,
    onSelect: (String) -> Unit,
) {
    val options = listOf(
        "none" to "Plain",
        "subtle_dots" to "Subtle Dots",
        "geometric" to "Geometric",
        "gradient_mesh" to "Gradient Mesh",
        "constellation" to "Constellation",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Color(theme.surfaceColor),
                com.example.chat.ui.kit.ChatyModalShape,
            )
            .padding(vertical = 14.dp),
    ) {
        Text(
            "Chat wallpaper",
            color = Color(theme.primaryTextColor),
            fontWeight = FontWeight.W800,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 4.dp),
        )
        options.forEach { (id, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(id) { detectTapGestures(onTap = { onSelect(id) }) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    color = Color(theme.primaryTextColor),
                    modifier = Modifier.weight(1f),
                )
                if (currentId == id) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color(theme.accentColor),
                    )
                }
            }
        }
        Box(Modifier.padding(bottom = 8.dp))
    }
}

/** Stock integrated composer (Dart _Composer._buildInputRow, default
 *  template: integrated placement, 24dp corners, send/mic morph, camera
 *  shortcut shown). Voice capture and the emoji picker are owner seams. */
@Composable
/** Parse the Dart colorHex formats ('0xFF6366F1', '#6366F1') into a Color. */
private fun parseHexColor(hex: String): Color? {
    val cleaned = hex.removePrefix("0x").removePrefix("0X").removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return null
    return if (cleaned.length <= 6) {
        Color(0xFF000000L or value)
    } else {
        Color(value)
    }
}
