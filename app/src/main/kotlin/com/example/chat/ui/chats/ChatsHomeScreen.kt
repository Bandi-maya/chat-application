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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.HomePresetNormalizer
import com.example.chat.domain.models.Conversation
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
    currentUserId: String = "",
    identity: HomeIdentity = HomeIdentity(),
    contacts: List<UserProfile> = emptyList(),
    homePreferences: HomePreferences = HomePreferences(),
    securityPreferences: SecurityPreferences = SecurityPreferences(),
    forcedType: ConversationType? = null,
    pageTitle: String? = null,
    templates: HomeTemplates = defaultHomeTemplates,
    presenceFor: (conversationId: String, otherUserId: String) -> HomePresence? = { _, _ -> null },
    isOnline: (String) -> Boolean = { false },
    messagesFor: (String) -> List<ChatMessage> = { emptyList() },
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
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var selectedFilter by remember { mutableStateOf("All") }
    var overflowOpen by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMessageNumber by remember { mutableStateOf(false) }
    var messageNumberText by remember { mutableStateOf("") }
    var showWhoCanCallMe by remember { mutableStateOf(false) }
    var showLockedSetup by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }

    // 'Who can call me?' reuses the shared privacy dialog when the real
    // preferences controller is wired; otherwise the nav seam stands.
    val effectiveNav = (if (preferencesController != null) {
        nav.copy(onWhoCanCallMe = { showWhoCanCallMe = true })
    } else {
        nav
    }).copy(onOpenCameraEffects = { nav.onOpenCameraEffects(); showEffects = true })
    // Connection UI defaults to the shared detail-slice components once the
    // owner wires the health snapshot; explicit slots still win.
    // No Home strip banner: connection state is shown only by the signal icon in the header (headerExtras),
    // the single tap target for Connection Health. An explicitly supplied slot is still honored.
    val effectiveConnectionBanner: (@Composable () -> Unit)? = connectionBanner
    val effectiveHeaderExtras: (@Composable () -> Unit)? = headerExtras
        ?: connectionHealthState?.let { state ->
            @Composable {
                com.example.chat.ui.chats.detail.ConnectionHealthIndicator(
                    health = state.health,
                    size = 15.dp,
                    onTap = onOpenConnectionDetail,
                )
            }
        }

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
    val homeStyle = HomePresetNormalizer.homeStyle(homePrefs.homeStyle)
    val spec = HomeStyleSpec.resolve(homeStyle, rawHomeUiStyle)
    val searchPlacement = resolveSearchPlacement(homePrefs.searchPlacement, homePrefs.disableSearchBar)
    val searchEnabled = homePrefs.showSearchBar
    val storiesStyle = homePrefs.storiesStyle
    val storyLook = chatyStoryLookFor(storiesStyle)
    val storyShowOnlineRing = storiesStyle != "Minimal"
    val storyCardWrap = storiesStyle == "Card" || storiesStyle == "Card View" || homePrefs.carouselView
    val showStoriesStrip = homePrefs.enableStoriesStrip || spec.storiesFirst
    val storyCompact = storiesStyle == "Compact"
    val storyTileWidth = if (storyCardWrap) 74.0 else if (storyCompact) 48.0 else 58.0
    val storyNameFontSize = if (storyCompact) 9.0 else 10.5

    val visibleConversations = filterHomeConversations(
        conversations, searchQuery, selectedFilter, forcedType, isConversationHidden,
    )
    val entries = buildHomeEntries(
        visibleConversations, homeStyle, spec, isSearchOpen,
        homePrefs.hideArchivedChats, gbBool("key_mas_hide_archive_home", false),
        homePrefs.archiveChatsOnTop,
    )
    val filters = homeFilters(forcedType)

    val headerStyle = effectiveHomeHeaderStyle(rawHomeUiStyle, homeStyle, templates.headerStyle)

    // P4 large-title collapse: 0 expanded -> 1 collapsed, driven by the
    // primary list's vertical scroll only.
    val largeTitleCollapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 44f).coerceIn(0f, 1f)
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
                    leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val number = messageNumberText.trim()
                    showMessageNumber = false
                    messageNumberText = ""
                    if (number.isNotEmpty()) nav.onMessageNumber(number)
                }) { Text("Chat", fontWeight = FontWeight.W700) }
            },
            dismissButton = {
                TextButton(onClick = { showMessageNumber = false; messageNumberText = "" }) {
                    Text("Cancel")
                }
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
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = {
                    (
                        fadeIn(tween(230, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))) +
                            slideInVertically(
                                tween(230, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)),
                            ) { (it * 0.035f).toInt() }
                        ) togetherWith
                        fadeOut(tween(230, easing = CubicBezierEasing(0.64f, 0f, 0.92f, 0f)))
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
                    )
                } else {
                    Box {
                        StandardHomeAppBar(
                            theme = theme,
                            homePrefs = homePrefs,
                            headerStyle = headerStyle,
                            homeStyle = homeStyle,
                            title = barTitle,
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
                        Box(Modifier.align(Alignment.TopEnd)) {
                            HomeOverflowMenu(
                                expanded = overflowOpen,
                                onDismiss = { overflowOpen = false },
                                theme = theme,
                                nav = effectiveNav,
                                onMarkAllAsRead = {
                                    onMarkAllRead()
                                    onSnack("Marked all chats as read")
                                },
                                onMessageANumber = { showMessageNumber = true },
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
            // P4: iOS collapsing LARGE title.
            if (!isSelectionMode && pageTitle != null) {
                Box(
                    modifier = Modifier
                        .height(((1 - effectiveTitleCollapse) * 44).dp)
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
                if (showStoriesStrip && !spec.storiesHidden && forcedType != ConversationType.group) {
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
                        onSelect = { filter ->
                            // Same fail-closed wrap as the other vault
                            // entries (source relies on destination auth).
                            if (filter == "Vault") onRequestVaultAuth { nav.onOpenLockedVault() } else selectedFilter = filter
                        },
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
                            entries = entries,
                            theme = theme,
                            homePrefs = homePrefs,
                            spec = spec,
                            templates = templates,
                            currentUserId = currentUserId,
                            contacts = contacts,
                            selectedIds = selectedIds,
                            isSelectionMode = isSelectionMode,
                            presenceFor = presenceFor,
                            messagesFor = messagesFor,
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
                                SplitQuickPane(
                                    theme = theme,
                                    pinned = conversations.filter { it.isPinned && !it.isArchived },
                                    groups = conversations.filter {
                                        it.type == ConversationType.group && !it.isArchived
                                    },
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
    }
}

/** _conversationsList: sections, archived entry and swipe-wrapped rows. */
@Composable
private fun HomeConversationList(
    entries: List<HomeEntry>,
    theme: com.example.chat.core.theme.ThemeConfig,
    homePrefs: HomePreferences,
    spec: HomeStyleSpec,
    templates: HomeTemplates,
    currentUserId: String,
    contacts: List<UserProfile>,
    selectedIds: Set<String>,
    isSelectionMode: Boolean,
    presenceFor: (String, String) -> HomePresence?,
    messagesFor: (String) -> List<ChatMessage>,
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
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 8.dp, top = 0.dp, end = 8.dp, bottom = 96.dp,
        ),
    ) {
        items(
            count = entries.size,
            key = { index ->
                when (val entry = entries[index]) {
                    is HomeEntry.Section -> "section_${entry.label}"
                    is HomeEntry.Archived -> "archived_entry"
                    is HomeEntry.Item -> entry.conversation.id
                }
            },
        ) { index ->
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
                        Icon(
                            Icons.Outlined.Archive,
                            contentDescription = null,
                            tint = Color(theme.secondaryTextColor),
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
                        messagesFor(conversation.id).lastOrNull()?.deliveryState
                    } else {
                        null
                    }
                    HomeSwipeActions(
                        enabled = !isSelectionMode,
                        backgroundColor = Color(theme.backgroundColor),
                        leadingActions = if (conversation.type == ConversationType.direct) {
                            listOf(
                                HomeSwipeAction(
                                    icon = Icons.Rounded.Call,
                                    label = "Call",
                                    color = Color(theme.successColor),
                                    onTriggered = { onStartCall(conversation, false) },
                                ),
                                HomeSwipeAction(
                                    icon = Icons.Rounded.Videocam,
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
                                icon = if (conversation.isPinned) Icons.Rounded.PushPin else Icons.Outlined.OutlinedPushPin,
                                label = if (conversation.isPinned) "Unpin" else "Pin",
                                color = Color(theme.accentColor),
                                onTriggered = { onTogglePin(conversation.id) },
                            ),
                            HomeSwipeAction(
                                icon = if (conversation.isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                label = if (conversation.isMuted) "Unmute" else "Mute",
                                color = Color(0xFFF59E0B),
                                onTriggered = { onToggleMute(conversation.id) },
                            ),
                            HomeSwipeAction(
                                icon = Icons.Outlined.Archive,
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
                            chatList = templates.chatList,
                            density = spec.tileDensity,
                            isDark = theme.isDark,
                            cardRows = spec.cardRows,
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

private fun rawIsTelegram(homeStyle: String): Boolean =
    homeStyle.trim().lowercase() == "telegram style"
