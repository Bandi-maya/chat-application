package com.example.chat.ui.chats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
import androidx.compose.material.icons.rounded.Videocam
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.kit.ChatyTextButton
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.HomePresetNormalizer
import com.example.chat.ui.effects.listEntrance
import com.example.chat.domain.models.Conversation
import com.example.chat.ui.kit.ChatyAdaptiveLogic
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.HomePreferences
import com.example.chat.domain.models.SecurityPreferences
import com.example.chat.domain.models.UserProfile
import com.example.chat.domain.models.ChatMessage
import com.example.chat.ui.chats.home.ArchivedChatsScreen
import com.example.chat.ui.chats.home.ChatsHomeNav
import com.example.chat.ui.chats.home.ConversationRow
import com.example.chat.ui.chats.home.EmptyChatsState
import com.example.chat.ui.chats.home.HomeConfirmDialog
import com.example.chat.ui.chats.home.HomeEntry
import com.example.chat.ui.chats.home.HomeFilterChips
import com.example.chat.ui.chats.home.HomeIdentity
import com.example.chat.ui.chats.home.HomeOverflowMenu
import com.example.chat.ui.chats.home.HomePresence
import com.example.chat.ui.chats.home.HomeSearchField
import com.example.chat.ui.chats.home.HomeSectionHeader
import com.example.chat.ui.chats.home.HomeStyleSpec
import com.example.chat.ui.chats.home.HomeSwipeAction
import com.example.chat.ui.chats.home.HomeSwipeActions
import com.example.chat.ui.chats.home.LockedChatsSetupDialog
import com.example.chat.ui.chats.home.HomeTemplates
import com.example.chat.ui.chats.home.SelectionHomeAppBar
import com.example.chat.ui.chats.home.SplitQuickPane
import com.example.chat.ui.chats.home.StandardHomeAppBar
import com.example.chat.ui.chats.home.StoryStrip
import com.example.chat.ui.chats.home.SubscriptionRequiredBanner
import com.example.chat.ui.chats.home.buildHomeEntries
import com.example.chat.ui.chats.home.chatyStoryLookFor
import com.example.chat.ui.chats.home.chatyStoryStripHeight
import com.example.chat.ui.chats.home.compactBarTitle
import com.example.chat.ui.chats.home.defaultHomeTemplates
import com.example.chat.ui.chats.home.displayConversationTitle
import com.example.chat.ui.chats.home.effectiveHomeHeaderStyle
import com.example.chat.ui.chats.home.filterHomeConversations
import com.example.chat.ui.chats.home.homeFilters
import com.example.chat.ui.chats.home.resolveSearchPlacement
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Full port of lib/features/chats/chats_home_screen.dart (3072 lines):
 *  search with placements + secret-phrase vault entry, selection mode with
 *  bulk pin/delete/mute/archive/lock/read-state actions, sectioned
 *  PINNED/UNREAD/MESSAGES ordering with Productivity unread-first, archive
 *  entry top/bottom, filter chips, stories strip, iOS large-title collapse,
 *  WA-style swipe actions (call/video + pin/mute/archive), Tablet Split
 *  View, ghost pill, overflow menu and subscription banner.
 *
 *  The original four parameters keep their meaning so the existing AppRoot
 *  wiring compiles unchanged; every new dependency has a default that
 *  degrades to the Dart behavior for absent data (empty presence, no locks).
 *
 *  Honest boundaries (later slices, not dropped): ProtectedResourceGate auth
 *  before opening a locked chat, the locked-chats guided-setup modal,
 *  ChatyGlyph font icons (emoji stand-ins), network avatars (integrator's
 *  Coil batch), GlobalConnectionBanner/ConnectionHealthIndicator (slots
 *  provided), NotificationPermissionSheet (host seam below, W8 owns the UI),
 *  swipe-call relationship checks (calls slice), GB story *content* (the
 *  strip lists contacts exactly like Dart). */
@Composable
fun ChatsHomeScreen(
    conversations: List<Conversation>,
    requiresSubscription: Boolean,
    subscriptionMessage: String?,
    onOpenConversation: (Conversation) -> Unit,
    /** R7 expanded list-detail: the open conversation renders beside the list. */
    expandedListDetail: Boolean = false,
    paneConversation: Conversation? = null,
    chatPane: (@Composable (Conversation) -> Unit)? = null,
    currentUserId: String = "",
    /** Opaque auth generation, not merely uid: resets pending selection/dialog state across account transitions. */
    accountSessionIdentity: Any? = currentUserId,
    folderAccountKey: String = "",
    identity: HomeIdentity = HomeIdentity(),
    contacts: List<UserProfile> = emptyList(),
    homePreferences: HomePreferences = HomePreferences(),
    securityPreferences: SecurityPreferences = SecurityPreferences(),
    forcedType: ConversationType? = null,
    pageTitle: String? = null,
    templates: HomeTemplates = if (com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePreferences.homeStyle)) com.example.chat.ui.chats.home.referenceDefaultHomeTemplates else defaultHomeTemplates,
    presenceFor: (conversationId: String, otherUserId: String) -> HomePresence? = { _, _ -> null },
    isOnline: (String) -> Boolean = { false },
    messagesFor: (String) -> List<ChatMessage> = { emptyList() },
    /** Cheap tick accessor; when the host provides it, rows avoid copying the
     *  whole message list via messagesFor on every recomposition. */
    lastDeliveryStateFor: ((String) -> com.example.chat.domain.models.DeliveryState?)? = null,
    gbBool: (String, Boolean) -> Boolean = { _, fallback -> fallback },
    gbColor: (String) -> Long? = { null },
    isConversationLocked: (String) -> Boolean = { false },
    isConversationHidden: (String) -> Boolean = { false },
    onToggleLockConversation: (String, Boolean) -> Unit = { _, _ -> },
    onToggleGhostMode: (Boolean) -> Unit = {},
    verifySecretPhrase: suspend (String) -> Boolean = { false },
    nav: ChatsHomeNav = ChatsHomeNav(),
    onOpenArchived: () -> Unit = {},
    onTogglePin: (String) -> Unit = {},
    onToggleMute: (String) -> Unit = {},
    onToggleArchive: (String) -> Unit = {},
    onMarkRead: (String) -> Unit = {},
    onMarkUnread: (String) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onDeleteConversations: (List<String>) -> Unit = {},
    onStartCall: (Conversation, isVideo: Boolean) -> Unit = { _, _ -> },
    onSnack: (String) -> Unit = {},
    onAuthorizeConversation: (Conversation, proceed: () -> Unit) -> Unit = { _, proceed -> proceed() },
    preferencesController: com.example.chat.core.preferences.ChatyPreferencesController? = null,
    connectionHealthState: com.example.chat.domain.models.ConnectionHealthState? = null,
    onOpenConnectionDetail: () -> Unit = {},
    lockedChatsConfigured: Boolean = false,
    onRequestVaultAuth: (onAuthorized: () -> Unit) -> Unit = {},
    onShowNotificationPermissionIfNeeded: () -> Unit = {},
    headerExtras: (@Composable () -> Unit)? = null,
    connectionBanner: (@Composable () -> Unit)? = null,
    headerBottom: (@Composable () -> Unit)? = null,
) {
    val bundleTemplates = com.example.chat.ui.chats.bundles.messageBundleHome(templates)
    val theme = com.example.chat.ui.chats.bundles.messageBundleTheme(LocalThemeConfig.current)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }

    var searchQuery by remember(accountSessionIdentity) { mutableStateOf("") }
    var isSearchOpen by remember(accountSessionIdentity) { mutableStateOf(false) }
    var selectedIds by remember(accountSessionIdentity) { mutableStateOf(setOf<String>()) }
    var selectedFilter by remember(accountSessionIdentity) { mutableStateOf("All") }
    var overflowOpen by remember(accountSessionIdentity) { mutableStateOf(false) }
    var showDeleteConfirm by remember(accountSessionIdentity) { mutableStateOf(false) }
    var showMessageNumber by remember(accountSessionIdentity) { mutableStateOf(false) }
    var messageNumberText by remember(accountSessionIdentity) { mutableStateOf("") }
    var showWhoCanCallMe by remember(accountSessionIdentity) { mutableStateOf(false) }
    var showLockedSetup by remember(accountSessionIdentity) { mutableStateOf(false) }
    var showArchived by remember(accountSessionIdentity) { mutableStateOf(false) }
    var showEffects by remember(accountSessionIdentity) { mutableStateOf(false) }

    // 'Who can call me?' reuses the shared privacy dialog when the real
    // preferences controller is wired; otherwise the nav seam stands.
    val effectiveNav = (if (preferencesController != null) {
        nav.copy(onWhoCanCallMe = { showWhoCanCallMe = true })
    } else {
        nav
    }).copy(onOpenCameraEffects = { nav.onOpenCameraEffects(); showEffects = true })
    // Connection health is available in the overflow, not the header signal lane.
    // Preserve explicit non-connection host slots.
    val effectiveConnectionBanner: (@Composable () -> Unit)? = connectionBanner
    val effectiveHeaderExtras: (@Composable () -> Unit)? = headerExtras

    val isSelectionMode = selectedIds.isNotEmpty()

    // initState: NotificationPermissionSheet.showIfNeeded (W8 owns the UI;
    // this is only the host seam).
    LaunchedEffect(Unit) { onShowNotificationPermissionIfNeeded() }

    // PopScope(canPop: !selectionMode): back clears the selection first.
    BackHandler(enabled = isSearchOpen) { isSearchOpen = false; searchQuery = "" }
    BackHandler(enabled = isSelectionMode) { selectedIds = emptySet() }

    // ---- build() derivations, in the Dart source's order ----
    val homePrefs = homePreferences
    val rawHomeUiStyle = homePrefs.homeStyle.trim().lowercase()
    val homeStyle = com.example.chat.core.preferences.HomePresentationPolicy.listStyle(homePrefs.homeStyle)
    val referenceRows = com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle) && bundleTemplates.chatList.referenceDefault
    val spec = HomeStyleSpec.resolve(homeStyle, rawHomeUiStyle).let {
        if (referenceRows) it.copy(showSections = false) else it
    }
    val searchPlacement = resolveSearchPlacement(homePrefs.searchPlacement, homePrefs.disableSearchBar)
    val searchEnabled = homePrefs.showSearchBar
    val storiesStyle = com.example.chat.core.preferences.HomePresentationPolicy.effectiveStoriesStyle(
        homePrefs.separateChatsAndGroups, homePrefs.storiesStyle,
    )
    val storyLook = chatyStoryLookFor(storiesStyle)
    val storyShowOnlineRing = storiesStyle != "Minimal"
    val storyCardWrap = storiesStyle == "Card" || storiesStyle == "Card View" || homePrefs.carouselView
    // User directive: no stories strip on the home screen unless he explicitly
    // enables it - separation/template no longer force it on.
    val showStoriesStrip = homePrefs.enableStoriesStrip
    val storyCompact = storiesStyle == "Compact"
    val storyTileWidth = if (storyCardWrap) 74.0 else if (storyCompact) 48.0 else 58.0
    val storyNameFontSize = if (storyCompact) 9.0 else 10.5

    // Snapshot privacy values on every composition. Captured mutable controller state may
    // change without changing a lambda's identity, especially with strong skipping enabled.
    val hiddenConversationIds = conversations.asSequence().map { it.id }.filter(isConversationHidden).toSet()

    // G7: local, account-scoped chat folders. Membership never leaves the
    // device; the folder filter applies after the stock filter/search pass.
    val folderStoreContext = androidx.compose.ui.platform.LocalContext.current
    val chatFolderStore = remember(accountSessionIdentity, folderAccountKey) {
        com.example.chat.data.local.ChatFolderStore(folderStoreContext, folderAccountKey.ifBlank { "unavailable" })
    }
    val chatFolderRevision by chatFolderStore.revision.collectAsState()
    var selectedFolderId by remember(accountSessionIdentity, folderAccountKey) { mutableStateOf<String?>(null) }

    val visibleConversations = remember(accountSessionIdentity, folderAccountKey, chatFolderStore, conversations, searchQuery, selectedFilter, forcedType, hiddenConversationIds, selectedFolderId, chatFolderRevision) {
        val base = filterHomeConversations(
            conversations, searchQuery, selectedFilter, forcedType, hiddenConversationIds::contains,
        )
        val folderId = selectedFolderId
        if (folderId == null) base else chatFolderStore.memberIds(folderId).let { ids -> base.filter { it.id in ids } }
    }
    val gbHideArchiveHome = gbBool("key_mas_hide_archive_home", false)
    val entries = remember(
        visibleConversations, homeStyle, spec, isSearchOpen,
        homePrefs.hideArchivedChats, gbHideArchiveHome, homePrefs.archiveChatsOnTop,
    ) {
        buildHomeEntries(
            visibleConversations, homeStyle, spec, isSearchOpen,
            homePrefs.hideArchivedChats, gbHideArchiveHome,
            homePrefs.archiveChatsOnTop,
        )
    }
    val filters = homeFilters(forcedType)

    val headerStyle = effectiveHomeHeaderStyle(rawHomeUiStyle, homeStyle, bundleTemplates.headerStyle)

    // P4 large-title collapse: 0 expanded -> 1 collapsed, driven by the
    // primary list's vertical scroll only.
    val titleCollapseDistancePx = with(LocalDensity.current) { 44.dp.toPx() }
    val largeTitleCollapse by remember(listState, titleCollapseDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / titleCollapseDistancePx).coerceIn(0f, 1f)
        }
    }
    val effectiveTitleCollapse = if (isSelectionMode || isSearchOpen) 1f else largeTitleCollapse

    val barTitle = compactBarTitle(
        homeStyle = homeStyle,
        headerStyle = headerStyle,
        pageTitle = pageTitle,
        setMyName = homePrefs.setMyName,
        myNameOverride = homePrefs.myNameOverride,
        userDisplayName = identity.displayName,
        userUsername = identity.username,
        largeTitleShown = effectiveTitleCollapse < 0.5f,
    )

    // Focus only after the search field has been composed and attached; requesting it inside the
    // click handler threw "FocusRequester is not initialized" (field not composed yet) and killed the app.
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
        }
    }

    fun toggleSearch() {
        isSearchOpen = !isSearchOpen
        if (!isSearchOpen) searchQuery = ""
    }

    fun handleConversationTap(conversation: Conversation) {
        if (isSelectionMode) {
            selectedIds = if (selectedIds.contains(conversation.id)) {
                selectedIds - conversation.id
            } else {
                selectedIds + conversation.id
            }
            return
        }
        onAuthorizeConversation(conversation) { onOpenConversation(conversation) }
    }

    fun togglePinSelected() {
        val selected = conversations.filter { selectedIds.contains(it.id) }
        val pin = selected.any { !it.isPinned }
        selected.forEach { if (it.isPinned != pin) onTogglePin(it.id) }
        selectedIds = emptySet()
    }

    fun toggleMuteSelected() {
        val selected = conversations.filter { selectedIds.contains(it.id) }
        val mute = selected.any { !it.isMuted }
        selected.forEach { if (it.isMuted != mute) onToggleMute(it.id) }
        selectedIds = emptySet()
    }

    if (showDeleteConfirm) {
        val count = selectedIds.size
        HomeConfirmDialog(
            title = "Delete $count chat${if (count == 1) "" else "s"}?",
            message = "This removes the selected conversations for this account. " +
                "Shared media objects are not silently deleted.",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteConversations(selectedIds.toList())
                selectedIds = emptySet()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showEffects) com.example.chat.ui.camera.EffectPickerHost(onDismiss = { showEffects = false })

    if (showArchived) {
        // Dart _archivedTile: Navigator.push(_ArchivedChatsScreen). Hosted as a full-screen dialog so system
        // Back pops it; opening a conversation replaces this host's route (list position not restored).
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showArchived = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true,
            ),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                ArchivedChatsScreen(
                    isConversationHidden = isConversationHidden,
                    conversations = conversations,
                    theme = theme,
                    homePrefs = homePreferences,
                    onBack = { showArchived = false },
                    onOpenConversation = { conversation ->
                        showArchived = false
                        onAuthorizeConversation(conversation) { onOpenConversation(conversation) }
                    },
                    onToggleArchive = onToggleArchive,
                    onDeleteConversations = onDeleteConversations,
                )
            }
        }
    }

    if (showWhoCanCallMe && preferencesController != null) {
        com.example.chat.ui.settings.privacy.WhoCanCallMeDialog(
            preferences = preferencesController,
            onDismiss = { showWhoCanCallMe = false },
        )
    }

    if (showLockedSetup) {
        LockedChatsSetupDialog(
            theme = theme,
            onClose = { showLockedSetup = false },
            onConfigureSecurity = nav.onOpenSecuritySettings,
        )
    }

    if (showMessageNumber) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showMessageNumber = false },
            title = {
                Column {
                    Text("Message a number", fontWeight = FontWeight.W700)
                    Text(
                        "Start a direct chat without saving contact",
                        fontSize = 13.sp,
                        color = Color(theme.secondaryTextColor),
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = messageNumberText,
                    onValueChange = { messageNumberText = it },
                    label = { Text("Phone number") },
                    placeholder = { Text("Include country code (+1...)") },
                    leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callAudio, semanticLabel = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                )
            },
            confirmButton = {
                ChatyTextButton(
                    onClick = {
                        val number = messageNumberText.trim()
                        showMessageNumber = false
                        messageNumberText = ""
                        if (number.isNotEmpty()) nav.onMessageNumber(number)
                    },
                    label = "Chat",
                    fontWeight = FontWeight.W700,
                )
            },
            dismissButton = {
                ChatyTextButton(
                    onClick = { showMessageNumber = false; messageNumberText = "" },
                    label = "Cancel",
                )
            },
        )
    }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            effectiveConnectionBanner?.invoke()
            if (requiresSubscription) {
                SubscriptionRequiredBanner(theme, subscriptionMessage, nav.onSelectPlan)
            }
            // Header swap with the Dart AnimatedSwitcher timing (230ms,
            // easeOutCubic in / easeInCubic out, slight upward slide).
            val headerReduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
            val headerSwapMs = if (headerReduceMotion) 0 else 230
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = {
                    (
                        fadeIn(tween(headerSwapMs, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))) +
                            slideInVertically(
                                tween(headerSwapMs, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)),
                            ) { (it * 0.035f).toInt() }
                        ) togetherWith
                        fadeOut(tween(headerSwapMs, easing = CubicBezierEasing(0.64f, 0f, 0.92f, 0f)))
                },
                label = "home-header",
            ) { selectionMode ->
                if (selectionMode) {
                    val selected = conversations.filter { selectedIds.contains(it.id) }
                    SelectionHomeAppBar(
                        theme = theme,
                        selectedCount = selectedIds.size,
                        anyUnpinned = selected.any { !it.isPinned },
                        anyUnmuted = selected.any { !it.isMuted },
                        anyUnlocked = selectedIds.any { !isConversationLocked(it) },
                        onClearSelection = { selectedIds = emptySet() },
                        onTogglePin = { togglePinSelected() },
                        onDelete = { showDeleteConfirm = true },
                        onToggleMute = { toggleMuteSelected() },
                        onToggleArchive = {
                            selectedIds.toList().forEach(onToggleArchive)
                            selectedIds = emptySet()
                        },
                        onToggleLock = {
                            val ids = selectedIds.toList()
                            val lock = ids.any { !isConversationLocked(it) }
                            ids.forEach { onToggleLockConversation(it, lock) }
                            selectedIds = emptySet()
                        },
                        onMarkUnread = {
                            selectedIds.forEach(onMarkUnread)
                            selectedIds = emptySet()
                        },
                        onMarkRead = {
                            selectedIds.forEach(onMarkRead)
                            selectedIds = emptySet()
                        },
                        onSelectAll = { selectedIds = selectedIds + visibleConversations.map { it.id } },
                        onSplitChat = {
                            nav.onOpenSplitChat?.invoke(selectedIds.toList())
                            selectedIds = emptySet()
                        },
                    )
                } else {
                    Box {
                        StandardHomeAppBar(
                            theme = theme,
                            homePrefs = homePrefs,
                            headerStyle = headerStyle,
                            homeStyle = homeStyle,
                            title = if (forcedType != null) pageTitle ?: barTitle else barTitle,
                            identity = identity,
                            isSearchOpen = isSearchOpen,
                            forcedTypeIsSet = forcedType != null,
                            nav = effectiveNav,
                            onToggleSearch = { toggleSearch() },
                            onToggleGhost = onToggleGhostMode,
                            // _handleTopLeftIdentityTap: with Locked Chats
                            // configured the vault stays behind the auth gate
                            // (fail-closed seam; owner wires the real gate),
                            // otherwise the guided setup modal explains it.
                            onIdentityTap = {
                                if (lockedChatsConfigured) {
                                    onRequestVaultAuth { nav.onOpenLockedVault() }
                                } else {
                                    showLockedSetup = true
                                }
                            },
                            // Source deviation, deliberate: Dart long-press
                            // opens the vault directly, but no delivered
                            // Kotlin slice carries a destination-side auth
                            // gate, so every entry path goes through the
                            // fail-closed seam until the shared gate lands.
                            onIdentityLongPress = {
                                onRequestVaultAuth { nav.onOpenLockedVault() }
                            },
                            onOpenOverflowMenu = { overflowOpen = true },
                            onSnack = onSnack,
                            headerExtras = effectiveHeaderExtras,
                        )
                        Box(Modifier.align(Alignment.BottomEnd)) {
                            HomeOverflowMenu(
                                expanded = overflowOpen,
                                onDismiss = { overflowOpen = false },
                                theme = theme,
                                nav = effectiveNav,
                                menuPreferences = homePrefs,
                                onMarkAllAsRead = {
                                    onMarkAllRead()
                                    onSnack("Marked all chats as read")
                                },
                                onMessageANumber = { showMessageNumber = true },
                                onOpenConnectionDetail = onOpenConnectionDetail.takeIf { connectionHealthState != null },
                                onOpenArchived = { showArchived = true },
                            )
                        }
                    }
                }
            }
            if (!isSelectionMode) headerBottom?.invoke()
            if (!isSelectionMode && searchEnabled && searchPlacement == "Below header") {
                HomeSearchField(
                    theme = theme,
                    value = searchQuery,
                    onValueChange = { value ->
                        searchQuery = value
                        scope.launch {
                            if (verifySecretPhrase(value)) {
                                searchQuery = ""
                                isSearchOpen = false
                                nav.onOpenLockedVault()
                            }
                        }
                    },
                    hint = if (forcedType == ConversationType.group) "Search groups…" else "Search chats and messages…",
                    focusRequester = searchFocus,
                )
            }
            // The supplied Default / OLD / iOS headers already own their single title.
            // Preserve the large-title treatment only for other Home presentations.
            if (!isSelectionMode && pageTitle != null &&
                !com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle) &&
                rawHomeUiStyle !in setOf("ios style", "whatsapp old ui")) {
                Box(
                    modifier = Modifier
                        // Collapse drives height to exactly 0; the expanded height scales
                        // with font scale (sp->dp) so large text is never pinned to 44dp.
                        .height(with(LocalDensity.current) { (44f * theme.fontScale.toFloat()).sp.toDp() } * (1 - effectiveTitleCollapse))
                        .alpha(1 - effectiveTitleCollapse),
                ) {
                    Text(
                        pageTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(theme.primaryTextColor),
                        fontSize = (32 * theme.fontScale).sp,
                        fontWeight = FontWeight.W800,
                        letterSpacing = (-0.5).sp,
                        lineHeight = (32 * theme.fontScale).sp,
                        modifier = Modifier
                            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 6.dp)
                            .align(Alignment.CenterStart)
                            .clickable {
                                if (securityPreferences.hideLockedChats && securityPreferences.entryByAppTitle) {
                                    // Source opens the vault directly here and
                                    // relies on destination-side auth; no such
                                    // gate exists in the Kotlin slice yet, so
                                    // this entry also fails closed.
                                    onRequestVaultAuth { nav.onOpenLockedVault() }
                                }
                            },
                    )
                }
            }
            val defaultPanel = com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle)
            com.example.chat.ui.navigation.ReferenceLayoutContentSurface(
                rounded = defaultPanel, isDark = theme.isDark, lightSurface = Color(theme.surfaceColor),
                surfaceOverride = preferencesController?.universal?.listBackgroundColor?.let(::Color),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
            val listColumnContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
                if (!isSelectionMode) {
                    if (searchEnabled && searchPlacement == "Header action" && isSearchOpen) {
                        HomeSearchField(
                            theme = theme,
                            value = searchQuery,
                            onValueChange = { value ->
                                searchQuery = value
                                scope.launch {
                                    if (verifySecretPhrase(value)) {
                                        searchQuery = ""
                                        isSearchOpen = false
                                        nav.onOpenLockedVault()
                                    }
                                }
                            },
                            hint = if (forcedType == ConversationType.group) "Search groups…" else "Search chats and messages…",
                            focusRequester = searchFocus,
                        )
                    }
                    if (showStoriesStrip && (!spec.storiesHidden || homePrefs.separateChatsAndGroups) && forcedType != ConversationType.group) {
                        StoryStrip(
                            contacts = contacts,
                            theme = theme,
                            look = storyLook,
                            stripHeight = chatyStoryStripHeight(storyLook, spec.storiesHeight),
                            storiesAvatar = spec.storiesAvatar,
                            storiesStyle = storiesStyle,
                            storyCardWrap = storyCardWrap,
                            storyShowOnlineRing = storyShowOnlineRing,
                            storyCompact = storyCompact,
                            storyTileWidth = storyTileWidth,
                            storyNameFontSize = storyNameFontSize,
                            isOnline = isOnline,
                        )
                    }
                    if (searchEnabled && searchPlacement == "Above chat filters") {
                        HomeSearchField(
                            theme = theme,
                            value = searchQuery,
                            onValueChange = { value ->
                                searchQuery = value
                                // Dart _homeSearchField is shared by all placements: onChanged runs
                                // _checkSecretSearchPhrase everywhere, so this placement unlocks too.
                                scope.launch {
                                    if (verifySecretPhrase(value)) {
                                        searchQuery = ""
                                        isSearchOpen = false
                                        nav.onOpenLockedVault()
                                    }
                                }
                            },
                            hint = if (forcedType == ConversationType.group) "Search groups…" else "Search chats and messages…",
                            focusRequester = searchFocus,
                        )
                    }
                    if (spec.showFilters && !homePrefs.hideChatSortList) {
                        HomeFilterChips(
                            theme = theme,
                            tabBubbleStyle = homePrefs.tabBubbleStyle,
                            filters = filters,
                            selectedFilter = selectedFilter,
                            onSelect = { filter -> selectedFilter = filter },
                        )
                        if (folderAccountKey.isNotBlank()) com.example.chat.ui.chats.folders.ChatFolderChips(
                            theme = theme,
                            store = chatFolderStore,
                            conversations = conversations.filter { it.id !in hiddenConversationIds },
                            selectedFolderId = selectedFolderId,
                            onSelect = { selectedFolderId = it },
                        )
                    }
                }
                if (visibleConversations.isEmpty()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyChatsState(
                            theme = theme,
                            isGroups = forcedType == ConversationType.group,
                            onSearch = nav.onOpenGlobalSearch,
                        )
                    }
                } else {
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val listContent: @Composable () -> Unit = {
                            HomeConversationList(
                                listAnimation = preferencesController?.effects?.homeListAnimation ?: "Standard",
                                entries = entries,
                                theme = theme,
                                homePrefs = homePrefs,
                                spec = spec,
                                bundleTemplates = bundleTemplates,
                                currentUserId = currentUserId,
                                contacts = contacts,
                                selectedIds = selectedIds,
                                isSelectionMode = isSelectionMode,
                                presenceFor = presenceFor,
                                messagesFor = messagesFor,
                                lastDeliveryStateFor = lastDeliveryStateFor,
                                gbBool = gbBool,
                                gbColor = gbColor,
                                isConversationLocked = isConversationLocked,
                                onOpenArchived = { onOpenArchived(); showArchived = true },
                                onRowTap = { handleConversationTap(it) },
                                onRowLongPress = { conversation ->
                                    selectedIds = if (selectedIds.contains(conversation.id)) {
                                        selectedIds - conversation.id
                                    } else {
                                        selectedIds + conversation.id
                                    }
                                },
                                onTogglePin = onTogglePin,
                                onToggleMute = onToggleMute,
                                onToggleArchive = onToggleArchive,
                                onStartCall = onStartCall,
                                listState = listState,
                            )
                        }
                        // 'Tablet Split View': list + quick pane on wide layouts;
                        // narrow layouts fall back to the single column.
                        if (spec.splitView && maxWidth >= 640.dp) {
                            Row {
                                Box(Modifier.width(360.dp)) { listContent() }
                                // VerticalDivider(width: 1, color: surfaceColor)
                                Spacer(
                                    Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(Color(theme.surfaceColor)),
                                )
                                Box(Modifier.weight(1f)) {
                                    val splitPinned = remember(conversations, hiddenConversationIds) {
                                        conversations.filter { it.isPinned && !it.isArchived && it.id !in hiddenConversationIds }
                                    }
                                    val splitGroups = remember(conversations, hiddenConversationIds) {
                                        conversations.filter {
                                            it.type == ConversationType.group && !it.isArchived && it.id !in hiddenConversationIds
                                        }
                                    }
                                    SplitQuickPane(
                                        theme = theme,
                                        pinned = splitPinned,
                                        groups = splitGroups,
                                        onOpen = { handleConversationTap(it) },
                                    )
                                }
                            }
                        } else {
                            listContent()
                        }
                    }
                }
            }
            // R7 (responsive plan): at expanded width the chat list becomes a
            // fixed 360-400dp pane with the open chat beside it (WhatsApp
            // tablet behavior); otherwise the single column is unchanged.
            if (chatPane != null && expandedListDetail) {
                val paneWidthDp = ChatyAdaptiveLogic.homePaneWidth(
                    com.example.chat.ui.kit.LocalChatyWindowMetrics.current.widthDp,
                )
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(paneWidthDp.dp).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize(), content = listColumnContent)
                    }
                    Spacer(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(theme.surfaceColor)),
                    )
                    Box(Modifier.weight(1f)) {
                        val paneConv = paneConversation
                        if (paneConv != null) {
                            chatPane(paneConv)
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Select a chat",
                                    color = Color(theme.secondaryTextColor),
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), content = listColumnContent)
            }
            }
        }
    }
}

/** _conversationsList: sections, archived entry and swipe-wrapped rows. */
@Composable
private fun HomeConversationList(
    listAnimation: String = "Standard",
    entries: List<HomeEntry>,
    theme: com.example.chat.core.theme.ThemeConfig,
    homePrefs: HomePreferences,
    spec: HomeStyleSpec,
    bundleTemplates: HomeTemplates,
    currentUserId: String,
    contacts: List<UserProfile>,
    selectedIds: Set<String>,
    isSelectionMode: Boolean,
    presenceFor: (String, String) -> HomePresence?,
    messagesFor: (String) -> List<ChatMessage>,
    lastDeliveryStateFor: ((String) -> com.example.chat.domain.models.DeliveryState?)? = null,
    gbBool: (String, Boolean) -> Boolean,
    gbColor: (String) -> Long?,
    isConversationLocked: (String) -> Boolean,
    onOpenArchived: () -> Unit,
    onRowTap: (Conversation) -> Unit,
    onRowLongPress: (Conversation) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onStartCall: (Conversation, Boolean) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = if (bundleTemplates.chatList.referenceDefault && com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle)) 0.dp else 8.dp,
            top = 0.dp,
            end = if (bundleTemplates.chatList.referenceDefault && com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle)) 0.dp else 8.dp,
            bottom = 96.dp,
        ),
    ) {
        items(
            count = entries.size,
            key = { index ->
                // Stable identities, never index-derived (Compose best practices):
                // an insert/archive/reorder must not change another row's key, or
                // Compose discards its state and replays entrance animations.
                when (val entry = entries[index]) {
                    is HomeEntry.Section -> "section_${entry.label}"
                    is HomeEntry.Archived -> "archived_entry"
                    is HomeEntry.Item -> entry.conversation.id
                }
            },
        ) { index ->
            val entranceKey = when (val entry = entries[index]) {
                is HomeEntry.Section -> "section_${entry.label}"
                is HomeEntry.Archived -> "archived_entry"
                is HomeEntry.Item -> entry.conversation.id
            }
            Box(Modifier.listEntrance(listAnimation, entranceKey)) {
            when (val entry = entries[index]) {
                is HomeEntry.Archived -> {
                    // _archivedTile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenArchived)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.archive,
                            semanticLabel = null,
                            color = Color(theme.secondaryTextColor),
                            modifier = Modifier.width(22.dp).height(22.dp),
                        )
                        Spacer(Modifier.width(24.dp))
                        Text(
                            "Archived",
                            color = Color(theme.primaryTextColor),
                            fontSize = (16 * theme.fontScale).sp,
                            fontWeight = FontWeight.W700,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            entry.archivedCount.toString(),
                            color = Color(theme.accentColor),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W700,
                        )
                    }
                }
                is HomeEntry.Section -> {
                    Box(Modifier.padding(start = 12.dp, top = 7.dp, end = 12.dp, bottom = 4.dp)) {
                        HomeSectionHeader(
                            text = entry.label,
                            color = Color(theme.secondaryTextColor),
                            fontSize = 10.5f,
                        )
                    }
                }
                is HomeEntry.Item -> {
                    val conversation = entry.conversation
                    val otherId = conversation.participantIds.firstOrNull { it != currentUserId } ?: ""
                    val lastOwn = if (conversation.lastMessageSenderId == currentUserId) {
                        lastDeliveryStateFor?.invoke(conversation.id)
                            ?: messagesFor(conversation.id).lastOrNull()?.deliveryState
                    } else {
                        null
                    }
                    HomeSwipeActions(
                        enabled = !isSelectionMode,
                        backgroundColor = com.example.chat.ui.navigation.LocalReferenceContentSurface.current
                            ?: Color(theme.backgroundColor),
                        leadingActions = if (conversation.type == ConversationType.direct) {
                            listOf(
                                HomeSwipeAction(
                                    icon = com.example.chat.ui.icons.ChatyIconId.callAudio,
                                    label = "Call",
                                    color = Color(theme.successColor),
                                    onTriggered = { onStartCall(conversation, false) },
                                ),
                                HomeSwipeAction(
                                    icon = com.example.chat.ui.icons.ChatyIconId.callVideo,
                                    label = "Video",
                                    color = Color(theme.accentColor),
                                    onTriggered = { onStartCall(conversation, true) },
                                ),
                            )
                        } else {
                            emptyList()
                        },
                        actions = listOf(
                            HomeSwipeAction(
                                icon = if (conversation.isPinned) com.example.chat.ui.icons.ChatyIconId.unpin else com.example.chat.ui.icons.ChatyIconId.pin,
                                label = if (conversation.isPinned) "Unpin" else "Pin",
                                color = Color(theme.accentColor),
                                onTriggered = { onTogglePin(conversation.id) },
                            ),
                            HomeSwipeAction(
                                icon = if (conversation.isMuted) com.example.chat.ui.icons.ChatyIconId.unmute else com.example.chat.ui.icons.ChatyIconId.mute,
                                label = if (conversation.isMuted) "Unmute" else "Mute",
                                color = com.example.chat.ui.core.theme.ChatyColors(theme).warning,
                                onTriggered = { onToggleMute(conversation.id) },
                            ),
                            HomeSwipeAction(
                                icon = com.example.chat.ui.icons.ChatyIconId.archive,
                                label = "Archive",
                                color = Color(theme.secondaryTextColor),
                                onTriggered = { onToggleArchive(conversation.id) },
                            ),
                        ),
                    ) {
                        ConversationRow(
                            conversation = conversation,
                            theme = theme,
                            homePrefs = homePrefs,
                            chatList = bundleTemplates.chatList,
                            density = spec.tileDensity,
                            isDark = theme.isDark,
                            cardRows = spec.cardRows && !com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle),
                            isTelegramRows = rawIsTelegram(homePrefs.homeStyle),
                            displayTitle = displayConversationTitle(
                                conversation,
                                homePrefs.hideUnsavedNumbers,
                                currentUserId,
                                contacts.map { it.id }.toSet(),
                            ),
                            presence = presenceFor(conversation.id, otherId),
                            currentUserId = currentUserId,
                            lastOwnMessageState = lastOwn,
                            selected = selectedIds.contains(conversation.id),
                            locked = isConversationLocked(conversation.id),
                            elapsedTime = homePrefs.elapsedTime,
                            gbOnlineChat = gbBool("onlinechat", true),
                            gbOnlineDotChat = gbBool("onlineDotchat", true),
                            gbModOnlineColor = gbColor("ModOnlineColor"),
                            gbModLastSeenColor = gbColor("ModlastseenColor"),
                            gbOnlineDotColor = gbColor("onlineDotchatColor"),
                            gbHomeCounterBk = gbColor("HomeCounterBK"),
                            gbHomeCounterText = gbColor("HomeCounterText"),
                            onTap = { onRowTap(conversation) },
                            onLongPress = { onRowLongPress(conversation) },
                        )
                    }
                }
            }
            }
        }
    }
}

private fun rawIsTelegram(homeStyle: String): Boolean =
    homeStyle.trim().lowercase() == "telegram style"
