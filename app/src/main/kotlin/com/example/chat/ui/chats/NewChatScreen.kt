package com.example.chat.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.GroupWork
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Search
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.newchat.NewChatRules
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port of new_chat_screen.dart (api branch anchor 3bee780): contact/user
 *  directory search with the 250ms debounce + epoch gate, direct-chat open,
 *  group/broadcast member selection with the exact Dart title rules, and the
 *  long-press Start Chat / Delete User sheet.
 *
 *  All backend access arrives as suspend lambdas so this screen never
 *  touches shared state directly; AppRoot wires them to ApiBackendService
 *  (searchUsersRemote / createOrGetDirectConversation / createGroupRoom /
 *  deleteUser). Skeleton/empty/no-results states mirror the Dart
 *  ChatySkeletonTile / ChatyEmptyState / ChatyNoResultsState widgets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    contacts: List<UserProfile>,
    startInGroupMode: Boolean = false,
    startInBroadcastMode: Boolean = false,
    searchUsersRemote: suspend (String) -> List<UserProfile>,
    openDirectConversation: suspend (UserProfile) -> Conversation,
    createGroup: suspend (String, List<String>) -> Conversation,
    deleteUser: suspend (String) -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onBack: () -> Unit,
    // Contacts plane (research 5.3): username search -> request -> inbox.
    // Null = pre-plane server: the screen keeps its legacy tap-to-chat flow.
    loadContactRelations: (suspend () -> Pair<List<com.example.chat.data.api.ApiContactsClient.ContactEntry>, List<com.example.chat.data.api.ApiContactsClient.ContactRequestItem>>)? = null,
    sendContactRequest: (suspend (String) -> Unit)? = null,
    respondContactRequest: (suspend (String, Boolean) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val configContext = androidx.compose.ui.platform.LocalContext.current
    val configApi = com.example.chat.ServiceLocator.apiBackend
    val configOwner = remember { configApi.authSnapshot() }
    // Never let an old contact selection become an operation on a newly selected account.
    val currentOnBack by androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.runtime.DisposableEffect(configApi, configOwner) {
        val listener: (com.example.chat.data.api.ApiAccountScope) -> Unit = { next ->
            if (next !== configOwner) scope.launch { currentOnBack() }
        }
        configApi.addAuthScopeListener(listener)
        onDispose { configApi.removeAuthScopeListener(listener) }
    }
    val choiceStore = remember(configOwner) { configApi.currentAccountHash()?.let { hash ->
        runCatching { com.example.chat.core.config.GroupUiChoicesStore(configContext, hash) }.getOrNull()
    } }
    var groupUi by remember(configOwner) { mutableStateOf(com.example.chat.core.config.GroupUiConfigPolicy.defaults) }
    LaunchedEffect(configOwner) {
        val defaults = configApi.groupUiDefaults(configOwner)
        val choices = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { choiceStore?.read() }
        if (configApi.authSnapshot() === configOwner) groupUi = choices?.applyTo(defaults) ?: defaults
    }

    var searchText by remember { mutableStateOf("") }
    var groupDetails by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    val memberNames = remember { mutableMapOf<String, String>() }
    var groupName by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    var results by remember { mutableStateOf(emptyList<UserProfile>()) }
    var acceptedContacts by remember { mutableStateOf(emptyList<UserProfile>()) }
    var contactLoadError by remember { mutableStateOf<String?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isCreatingGroup by remember { mutableStateOf(startInGroupMode || startInBroadcastMode) }
    var isBroadcast by remember { mutableStateOf(startInBroadcastMode) }
    var queryLoading by remember { mutableStateOf(false) }
    var isSubmittingGroup by remember { mutableStateOf(false) }
    val isLoading = queryLoading || isSubmittingGroup
    var epoch by remember { mutableIntStateOf(0) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    var sheetUser by remember { mutableStateOf<UserProfile?>(null) }
    var deleteCandidate by remember { mutableStateOf<UserProfile?>(null) }

    fun errorText(error: Throwable): String =
        (error.message ?: error.toString()).removePrefix("Exception: ")

    // Contacts plane state (research 5.3). Empty/absent on pre-plane servers.
    var contactIds by remember { mutableStateOf(setOf<String>()) }
    var contactUsernames by remember { mutableStateOf(setOf<String>()) }
    var contactRequests by remember { mutableStateOf(listOf<com.example.chat.data.api.ApiContactsClient.ContactRequestItem>()) }
    var requestedUsernames by remember { mutableStateOf(setOf<String>()) }
    var respondingRequestIds by remember { mutableStateOf(setOf<String>()) }

    fun isContact(user: UserProfile): Boolean =
        user.id in contactIds || user.username.lowercase() in contactUsernames

    fun contactProfile(entry: com.example.chat.data.api.ApiContactsClient.ContactEntry): UserProfile =
        contacts.firstOrNull { it.id == entry.userId } ?: UserProfile(
            id = entry.userId,
            displayName = entry.displayName.ifBlank { entry.username },
            username = entry.username,
            avatarInitials = entry.displayName.ifBlank { entry.username }.take(2).uppercase(),
            avatarColorHex = "0xFF6366F1",
            about = entry.about,
            avatarUrl = entry.avatarUrl,
            lastSeenAt = java.time.Instant.EPOCH,
        )

    // Empty search is the accepted-contacts list, never the workspace directory.
    suspend fun refreshAcceptedContacts() {
        queryLoading = true
        contactLoadError = null
        try {
            if (loadContactRelations != null) {
                val (entries, inbox) = loadContactRelations()
                if (configApi.authSnapshot() !== configOwner) return
                contactIds = entries.map { it.userId }.toSet()
                contactUsernames = entries.map { it.username.lowercase() }.toSet()
                acceptedContacts = entries.map(::contactProfile)
                contactRequests = inbox
            } else {
                contactLoadError = "The contacts list is unavailable on this server. Search a username explicitly."
            }
            if (searchText.isBlank()) results = acceptedContacts
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) {
            contactLoadError = "Could not load contacts. ${errorText(error)}"
        } finally {
            queryLoading = false
        }
    }

    LaunchedEffect(Unit) { refreshAcceptedContacts() }

    fun respondToRequest(req: com.example.chat.data.api.ApiContactsClient.ContactRequestItem, accept: Boolean) {
        if (req.requestId in respondingRequestIds) return
        val responder = respondContactRequest
        respondingRequestIds = respondingRequestIds + req.requestId
        scope.launch {
            try {
                checkNotNull(responder) { "Contact requests are unavailable on this server." }
                responder.invoke(req.requestId, accept)
                contactRequests = contactRequests.filterNot { it.requestId == req.requestId }
                if (accept) {
                    contactIds = contactIds + req.from.userId
                    contactUsernames = contactUsernames + req.from.username.lowercase()
                    acceptedContacts = (acceptedContacts + contactProfile(req.from)).distinctBy { it.id }
                    if (searchText.isBlank()) results = acceptedContacts
                }
                snackbar.showSnackbar(if (accept) "${req.from.displayName} added to your contacts" else "Request declined")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbar.showSnackbar(errorText(error))
            } finally { respondingRequestIds = respondingRequestIds - req.requestId }
        }
    }

    fun runSearch(query: String, searchEpoch: Int) {
        scope.launch {
            try {
                val users = searchUsersRemote(query)
                if (configApi.authSnapshot() !== configOwner) return@launch
                if (!NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                results = users
                queryLoading = false
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (error: Exception) {
                if (!NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                queryLoading = false
                results = emptyList()
                searchError = errorText(error)
                snackbar.showSnackbar(searchError!!)
            }
        }
    }

    // _onSearchChanged: cancel debounce; empty query restores contacts;
    // otherwise epoch++ + 250ms debounce before the remote search.
    fun onSearchChanged(value: String) {
        searchText = value
        searchError = null
        debounceJob?.cancel()
        val query = value.trim()
        val nextEpoch = ++epoch // Also invalidate an in-flight search when cleared.
        if (query.isEmpty()) {
            results = acceptedContacts
            queryLoading = false
            return
        }
        results = emptyList() // Never leave a stale result actionable while a new username is checked.
        queryLoading = true
        debounceJob = scope.launch {
            delay(NewChatRules.SEARCH_DEBOUNCE_MS)
            runSearch(query, nextEpoch)
        }
    }

    fun finishGroupCreation() {
        val title = NewChatRules.effectiveGroupTitle(groupName, isBroadcast, selectedMembers.size)
        if (!NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading)) return
        if (!groupDetails || isSubmittingGroup || configApi.authSnapshot() !== configOwner) return
        if (groupName.length > groupUi.nameMaxLength) { createError = "Group name must be at most ${groupUi.nameMaxLength} characters."; return }
        val capturedMembers = selectedMembers.toList()
        isSubmittingGroup = true // Independent of search/contact loaders, which may finish concurrently.
        createError = null
        scope.launch {
            try {
                val conversation = createGroup(title, capturedMembers)
                if (configApi.authSnapshot() === configOwner) onOpenConversation(conversation)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (error: Exception) {
                createError = errorText(error)
                snackbar.showSnackbar(errorText(error))
            } finally { isSubmittingGroup = false }
        }
    }

    fun openDirectChat(user: UserProfile) {
        if (isLoading || configApi.authSnapshot() !== configOwner) return
        scope.launch {
            queryLoading = true
            try {
                val conversation = openDirectConversation(user)
                if (configApi.authSnapshot() === configOwner) onOpenConversation(conversation)
            } catch (error: Exception) {
                queryLoading = false
                snackbar.showSnackbar(errorText(error))
            }
        }
    }

    val selectedSection: @Composable () -> Unit = {
                if (isCreatingGroup) {
                    Text(if (groupDetails) "2 of 2 · Review group" else "1 of 2 · Select people", color = colors.foregroundSecondary)
                    if (groupUi.showSelectedCount) Text("${selectedMembers.size} people selected", color = colors.foregroundSecondary)
                    val selectedNames: @Composable () -> Unit = {
                        selectedMembers.forEach { id ->
                            TextButton(onClick = { if (!isLoading) selectedMembers = selectedMembers - id }) {
                                Text("${memberNames[id] ?: contacts.firstOrNull { it.id == id }?.displayName ?: "Selected member"} · Remove")
                            }
                        }
                    }
                    if (groupUi.selectedDisplay == "list") Column { selectedNames() }
                    else androidx.compose.foundation.lazy.LazyRow {
                        items(selectedMembers.toList(), key = { it }) { id ->
                            TextButton(onClick = { if (!isLoading) selectedMembers = selectedMembers - id }) {
                                Text("${memberNames[id] ?: contacts.firstOrNull { it.id == id }?.displayName ?: "Selected member"} · Remove")
                            }
                        }
                    }
                    createError?.let { Text(it, color = colors.error) }
                }
    }
    val searchSection: @Composable () -> Unit = {
                if (!groupDetails) NewChatInput(
                    value = searchText,
                    onValueChange = { onSearchChanged(it) },
                    hint = if (isCreatingGroup) groupUi.searchLabel else "Enter exact @username...",
                    leading = {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search,
                            semanticLabel = null,
                            color = colors.foregroundTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailing = if (searchText.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchChanged("") }, modifier = Modifier.size(32.dp)) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close,
                                    semanticLabel = "Clear search",
                                    color = colors.foregroundSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
    }
    val contactsSection: @Composable () -> Unit = {
            when {
                groupDetails -> Unit
                isLoading && results.isEmpty() -> {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        items(8) { SkeletonTile() }
                    }
                }
                results.isEmpty() && searchText.trim().isNotEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(
                            searchError?.let { "Could not check username" } ?: "No account with that exact username",
                            color = colors.foreground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            searchError ?: "Check the full username. Names and email addresses are not searched.",
                            color = colors.foregroundSecondary,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { onSearchChanged("") }) {
                            Text("Clear search", color = colors.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                results.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PersonSearch,
                            contentDescription = null,
                            tint = colors.foregroundTertiary,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (contactLoadError == null) "No contacts yet" else "Contacts unavailable",
                            color = colors.foreground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            contactLoadError ?: "Accepted contacts appear here. Search a username to send a request.",
                            color = colors.foregroundSecondary,
                            fontSize = 13.sp,
                        )
                        if (contactLoadError != null && loadContactRelations != null) {
                            TextButton(onClick = { scope.launch { refreshAcceptedContacts() } }) {
                                Text("Retry", color = colors.accent)
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        itemsIndexed(results, key = { index, u -> "${u.id}_$index" }) { index, user ->
                            val isSelected = selectedMembers.contains(user.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isLoading || groupDetails) return@clickable
                                        if (isCreatingGroup) {
                                            memberNames[user.id] = user.displayName
                                            selectedMembers = NewChatRules.toggleMember(selectedMembers, user.id)
                                        } else if (loadContactRelations != null && !isContact(user)) {
                                            // Contacts plane: non-contacts go
                                            // through the request flow first.
                                            sheetUser = user
                                        } else {
                                            openDirectChat(user)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NewChatAvatar(user)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        user.displayName,
                                        color = colors.foreground,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = (-0.2).sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "@${user.username}" +
                                            (if (isContact(user)) " • contact" else if (user.username.lowercase() in requestedUsernames) " • requested" else "") +
                                            (if (user.about.isNotEmpty()) " • ${user.about}" else ""),
                                        color = colors.foregroundSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isCreatingGroup) {
                                    Checkbox(
                                        checked = isSelected,
                                        enabled = !isLoading && !groupDetails,
                                        onCheckedChange = {
                                            memberNames[user.id] = user.displayName
                                            selectedMembers = NewChatRules.toggleMember(selectedMembers, user.id)
                                        },
                                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                                            checkedColor = colors.primary,
                                        ),
                                    )
                                } else {
                                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
                                        semanticLabel = null,
                                        color = colors.foregroundTertiary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (index < results.size - 1) {
                                Box(
                                    Modifier
                                        .padding(start = 64.dp)
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(colors.divider),
                                )
                            }
                        }
                    }
                }
            }
    }
    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (isLoading) return@IconButton
                    if (isCreatingGroup && groupDetails) {
                        groupDetails = false
                    } else if (isCreatingGroup) {
                        isCreatingGroup = false
                        isBroadcast = false
                        selectedMembers = emptySet()
                    } else {
                        onBack()
                    }
                }) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Back", color = colors.foreground)
                }
                Text(
                    if (isCreatingGroup) {
                        if (isBroadcast) "New Broadcast (${selectedMembers.size})" else "${groupUi.title} (${selectedMembers.size})"
                    } else {
                        "New Conversation"
                    },
                    color = colors.foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.2.dp,
                        color = colors.primary,
                        modifier = Modifier.padding(horizontal = 16.dp).size(18.dp),
                    )
                }
                if (isCreatingGroup && !isLoading) {
                    TextButton(
                        onClick = {
                            if (!groupDetails) {
                                debounceJob?.cancel(); epoch++; queryLoading = false
                                groupDetails = true; createError = null
                            } else finishGroupCreation()
                        },
                        enabled = selectedMembers.isNotEmpty() && (!groupDetails || NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading)),
                    ) {
                        Text(
                            if (groupDetails) groupUi.createLabel else groupUi.nextLabel,
                            color = if (selectedMembers.isNotEmpty()) colors.primary else colors.foregroundTertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        },
    ) { padding ->
        // R2/R3: lists capped at 720dp centered on larger windows (approved
        // responsive plan 8 Nov 2026).
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().widthIn(max = 720.dp)) {
            if (isCreatingGroup && !groupDetails) {
                groupUi.order.forEach { section ->
                    when (section) {
                        "search" -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { searchSection() }
                        "selected" -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { selectedSection() }
                        "contacts" -> Box(Modifier.fillMaxWidth().weight(1f)) { contactsSection() }
                    }
                }
            } else {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                selectedSection()
                if (isCreatingGroup && groupDetails) {
                    Text("Review the people and name before creating. This sends one group-creation request. Standard chats are not end-to-end encrypted in this build.", color = colors.foregroundSecondary)
                    NewChatInput(
                        value = groupName,
                        onValueChange = { groupName = it.take(groupUi.nameMaxLength); createError = null },
                        hint = if (isBroadcast) "Enter broadcast list name..." else groupUi.nameLabel,
                        leading = {
                            Icon(
                                if (isBroadcast) Icons.Rounded.Cast else Icons.Rounded.GroupWork,
                                contentDescription = null,
                                tint = if (isBroadcast) colors.info else colors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                searchSection()

            }
            if (!isCreatingGroup) {
                NewChatEntryCard(
                    title = "Create New Group",
                    subtitle = "Choose people, then review the group name",
                    icon = { tint ->
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    },
                    iconTint = colors.primary,
                    chevronTint = colors.foregroundTertiary,
                    onClick = {
                        isCreatingGroup = true
                        isBroadcast = false
                    },
                )
                NewChatEntryCard(
                    title = "New Broadcast List",
                    subtitle = "Send broadcast messages to multiple contacts at once",
                    icon = { tint ->
                        Icon(Icons.Rounded.Cast, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    },
                    iconTint = colors.info,
                    chevronTint = colors.foregroundTertiary,
                    onClick = {
                        isCreatingGroup = true
                        isBroadcast = true
                    },
                )
                Spacer(Modifier.height(4.dp))
            }
            // Incoming contact requests inbox (research 5.3: Signal's
            // message-request pattern applied to contacts).
            if (!isCreatingGroup && contactRequests.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Contact requests",
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    contactRequests.forEach { req ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    req.from.displayName,
                                    color = colors.foreground,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("@${req.from.username}", color = colors.foregroundSecondary, fontSize = 12.sp)
                            }
                            TextButton(onClick = { respondToRequest(req, true) }) {
                                Text("Accept", color = colors.primary, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { respondToRequest(req, false) }) {
                                Text("Decline", color = colors.error)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            contactsSection()
            }
        }
        }
    }

    // _onUserLongPress sheet: Start Chat / Delete User.
    val sheetTarget = sheetUser
    if (sheetTarget != null) {
        ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp,
            onDismissRequest = { sheetUser = null },
            containerColor = colors.surface,
            shape = com.example.chat.ui.kit.ChatyModalShape,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    sheetTarget.displayName,
                    color = colors.foreground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )
                if (sendContactRequest != null && !isContact(sheetTarget)) {
                    if (sheetTarget.username.lowercase() in requestedUsernames) {
                        SheetAction(label = "Contact request pending", tint = colors.foregroundTertiary, onClick = {})
                    } else {
                        SheetAction(
                            label = "Send Contact Request",
                            tint = colors.primary,
                            onClick = {
                                val target = sheetTarget
                                sheetUser = null
                                scope.launch {
                                    try {
                                        sendContactRequest(target.username)
                                        requestedUsernames = requestedUsernames + target.username.lowercase()
                                        snackbar.showSnackbar("Request sent to ${target.displayName}")
                                    } catch (error: Exception) {
                                        snackbar.showSnackbar(errorText(error))
                                    }
                                }
                            },
                        )
                    }
                }
                SheetAction(
                    label = "Start Chat",
                    tint = colors.primary,
                    onClick = {
                        sheetUser = null
                        openDirectChat(sheetTarget)
                    },
                )
                SheetAction(
                    label = "Delete User",
                    tint = colors.error,
                    onClick = {
                        sheetUser = null
                        deleteCandidate = sheetTarget
                    },
                )
            }
        }
    }

    // ChatyConfirmDialog for Delete User.
    val deleteTarget = deleteCandidate
    if (deleteTarget != null) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { deleteCandidate = null },
            containerColor = colors.surface,
            title = { Text("Delete ${deleteTarget.displayName}?", color = colors.foreground) },
            text = {
                Text(
                    "This user will be permanently removed from your directory and workspace.",
                    color = colors.foregroundSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteCandidate = null
                    scope.launch {
                        queryLoading = true
                        try {
                            deleteUser(deleteTarget.id)
                            acceptedContacts = acceptedContacts.filterNot { it.id == deleteTarget.id }
                            results = results.filterNot { it.id == deleteTarget.id }
                            snackbar.showSnackbar("${deleteTarget.displayName} deleted")
                        } catch (error: Exception) {
                            snackbar.showSnackbar("Failed to delete user: $error")
                        } finally {
                            queryLoading = false
                        }
                    }
                }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel", color = colors.foregroundSecondary)
                }
            },
        )
    }
}

/** ChatyInput stand-in for this slice: filled rounded field with leading /
 *  trailing slots, matching the Dart widget's look on the default theme. */
@Composable
private fun NewChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.inputFill, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.foreground, fontSize = 14.5.sp),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(hint, color = colors.foregroundTertiary, fontSize = 14.5.sp)
                }
                inner()
            },
        )
        if (trailing != null) trailing()
    }
}

@Composable
private fun NewChatEntryCard(
    title: String,
    subtitle: String,
    icon: @Composable (Color) -> Unit,
    iconTint: Color,
    chevronTint: Color,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(iconTint.copy(alpha = 0.12f), CircleShape)
                .padding(8.dp),
        ) {
            icon(iconTint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp)
        }
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = chevronTint)
    }
}

/** AppAvatar + ChatyOnlineDot port (app_avatar.dart / chaty_kit.dart):
 *  initials circle painted with the parsed avatarColorHex, presence dot
 *  bottom-right (offset 1dp past the edge), diameter (size*0.28) clamped to
 *  10..14dp, 2dp ring in the surrounding surface color, presence-colored
 *  (online success, away warning, typing primary, offline tertiary). The
 *  Dart call site passes showOnlineBadge: true, so the dot always renders. */
@Composable
private fun NewChatAvatar(user: UserProfile) {
    val colors = LocalAppColors.current
    val presenceColor = when (user.presence) {
        com.example.chat.domain.models.PresenceState.online -> colors.success
        com.example.chat.domain.models.PresenceState.away -> colors.warning
        com.example.chat.domain.models.PresenceState.typing -> colors.primary
        com.example.chat.domain.models.PresenceState.offline -> colors.foregroundTertiary
    }
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    Color(com.example.chat.ui.chats.contact_info.parseAvatarColorArgb(user.avatarColorHex, LocalThemeConfig.current.accentColor)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                user.avatarInitials,
                color = colors.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 1.dp, y = 1.dp)
                .size(((42f * 0.28f).coerceIn(10f, 14f)).dp)
                .border(2.dp, colors.background, CircleShape)
                .background(presenceColor, CircleShape),
        )
    }
}

/** ChatySkeletonTile stand-in: static placeholder row (Dart renders a
 *  shimmering avatar + two bars). */
@Composable
private fun SkeletonTile() {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(colors.borderSubtle, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Box(Modifier.width(140.dp).height(12.dp).background(colors.borderSubtle, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(220.dp).height(10.dp).background(colors.borderSubtle, RoundedCornerShape(5.dp)))
        }
    }
}

@Composable
private fun SheetAction(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        label,
        color = tint,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
