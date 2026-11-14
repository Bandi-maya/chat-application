package com.example.chat.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import com.example.chat.ui.kit.ChatyActionButton
import com.example.chat.ui.kit.ChatyTextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.tasks.TaskAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Exact port of lib/features/search/global_search_screen.dart.
 *
 *  Seams:
 *  - [searchUsers]: ChatyDataStore.searchUsersRemote - on the api (custom
 *    REST backend) branch this delegates to ApiBackendService.searchUsers
 *    (loadUsers(query) over GET /chat/users?search=, then local filter
 *    excluding self).
 *  - [openDirectConversation]: ChatyDataStore.getOrCreateDirectConversation -
 *    on the api branch ApiBackendService.createOrGetDirectConversation
 *    (POST /chat/rooms/direct {target_user_id}; 402 -> subscription error).
 *  - [verifySecretPhrase]: LocalLockService.verifySecretPhrase - reveals the
 *    locked-chats vault when the typed query matches the secret phrase.
 *  - [isConversationHidden]: preferences hidden-locked-chats privacy rule.
 *  - [onOpenLockedChats] / [onOpenConversation] (push) /
 *    [onOpenConversationReplace] (pushReplacement): navigation wiring.
 *  - [onNotify]: SnackBar equivalent for the exact error strings. */
@Composable
fun GlobalSearchScreen(
    colors: ChatyColors,
    conversations: List<Conversation>,
    searchUsers: suspend (String) -> List<UserProfile>,
    openDirectConversation: suspend (UserProfile) -> Conversation,
    modifier: Modifier = Modifier,
    verifySecretPhrase: suspend (String) -> Boolean = { false },
    isConversationHidden: (String) -> Boolean = { false },
    onBack: () -> Unit = {},
    onOpenLockedChats: () -> Unit = {},
    onOpenConversation: (String) -> Unit = {},
    onOpenConversationReplace: (String) -> Unit = {},
    onNotify: (String) -> Unit = {},
    // Ordinary search never renders protected chat content. The owner must
    // supply the real account-scoped privacy predicate and session identity.
    isConversationProtected: (String) -> Boolean = { false },
    searchSessionIdentity: Any? = null,
    isSearchSessionCurrent: () -> Boolean = { true },
) {
    val scope = rememberCoroutineScope()
    val requestGuard = remember(searchSessionIdentity) { SearchRequestGuard() }
    var query by remember(searchSessionIdentity) { mutableStateOf("") }
    var matchedUsers by remember(searchSessionIdentity) { mutableStateOf<List<UserProfile>>(emptyList()) }
    // Derive local matches from the live list on every composition, not a
    // snapshot taken when the query changed. Privacy can change mid-search.
    val matchedConversations = if (isSearchSessionCurrent()) {
        LocalConversationSearch.filter(conversations, query) { id ->
            isConversationHidden(id) || isConversationProtected(id)
        }
    } else emptyList()
    var isSearching by remember(searchSessionIdentity) { mutableStateOf(false) }
    var isOpeningChat by remember(searchSessionIdentity) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun cleanError(error: Throwable): String =
        (error.message ?: error.toString()).removePrefix("Exception: ")

    fun performRemoteSearch(searchQuery: String, requestId: Int) {
        if (!isSearchSessionCurrent()) return
        scope.launch {
            try {
                val users = searchUsers(searchQuery)
                if (!requestGuard.isCurrent(requestId) || !isSearchSessionCurrent()) return@launch
                matchedUsers = requestGuard.deduplicate(users) { it.id }
                isSearching = false
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!requestGuard.isCurrent(requestId) || !isSearchSessionCurrent()) return@launch
                isSearching = false
                onNotify("Search failed: ${cleanError(error)}")
            }
        }
    }

    // Exact port of _queueSearch: invalidate in-flight work first, check the
    // secret phrase, run the local filter, gate remote search on length >= 2,
    // then debounce 280ms.
    var debounceJob by remember(searchSessionIdentity) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun queueSearch(raw: String) {
        debounceJob?.cancel()
        val trimmed = raw.trim()
        val requestId = requestGuard.begin()
        matchedUsers = emptyList()
        if (!isSearchSessionCurrent()) { isSearching = false; return }

        if (trimmed.isEmpty()) { isSearching = false; return }
        isSearching = trimmed.length >= 2
        debounceJob = scope.launch {
            // A vault secret is not a remote username. Wait for local
            // verification before any remote search, even if it is slow.
            val isMatch = try {
                verifySecretPhrase(trimmed)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestGuard.isCurrent(requestId) && isSearchSessionCurrent()) {
                    isSearching = false
                    onNotify("Unable to check private search. Try again.")
                }
                return@launch
            }
            if (!requestGuard.isCurrent(requestId) || !isSearchSessionCurrent()) return@launch
            if (isMatch) {
                requestGuard.begin()
                query = ""
                matchedUsers = emptyList()
                isSearching = false
                onOpenLockedChats()
                return@launch
            }
            if (trimmed.length < 2) { isSearching = false; return@launch }
            delay(280)
            if (requestGuard.isCurrent(requestId) && isSearchSessionCurrent()) {
                performRemoteSearch(trimmed, requestId)
            }
        }
    }

    fun startConversationWith(user: UserProfile) {
        if (isOpeningChat || !isSearchSessionCurrent()) return
        isOpeningChat = true
        scope.launch {
            try {
                val conversation = openDirectConversation(user)
                if (isSearchSessionCurrent() && !isConversationHidden(conversation.id) &&
                    !isConversationProtected(conversation.id)) {
                    onOpenConversationReplace(conversation.id)
                } else {
                    isOpeningChat = false
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!isSearchSessionCurrent()) return@launch
                isOpeningChat = false
                onNotify("Unable to open chat: ${cleanError(error)}")
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(searchSessionIdentity) {
        onDispose { requestGuard.begin(); debounceJob?.cancel() }
    }

    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        // ChatyAppBar equivalent: back + the 40dp pill search field + the
        // progress/clear action.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Back", color = colors.foreground)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(colors.inputFill)
                    .padding(horizontal = 16.dp),
            ) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search,
                    semanticLabel = null,
                    color = colors.foreground.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        queueSearch(it)
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.foreground, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "Search @username, people, groups...",
                                    color = colors.foreground.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            if (isSearching || isOpeningChat) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp).size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else if (query.isNotEmpty()) {
                IconButton(onClick = {
                    query = ""
                    queueSearch("")
                }) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.clear, semanticLabel = "Clear", color = colors.foreground)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        if (query.trim().isEmpty()) {
            // _buildEmptyPrompt
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(40.dp),
            ) {
                Icon(
                    Icons.Rounded.AlternateEmail,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("Username Discovery", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Type an @username to find and message another Chaty user without exposing phone numbers.",
                    color = colors.foreground.copy(alpha = 0.65f),
                    fontSize = 12.5.sp,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (isSearchSessionCurrent() && matchedUsers.isNotEmpty()) {
                    item {
                        SearchSection("People & Usernames", colors) {
                            matchedUsers.forEach { user ->
                                UserTile(
                                    user = user,
                                    colors = colors,
                                    isOpeningChat = isOpeningChat,
                                    onMessage = { startConversationWith(user) },
                                )
                            }
                        }
                    }
                }
                if (matchedConversations.isNotEmpty()) {
                    item {
                        SearchSection("Chats & Groups", colors) {
                            matchedConversations.forEach { conversation ->
                                ConversationTile(
                                    conversation = conversation,
                                    colors = colors,
                                    onOpen = {
                                        if (isSearchSessionCurrent() && !isConversationHidden(conversation.id) &&
                                            !isConversationProtected(conversation.id)) {
                                            onOpenConversation(conversation.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                if (!isSearching && matchedUsers.isEmpty() && matchedConversations.isEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        ) {
                            Text(
                                "No results for \"${query.trim()}\"",
                                color = colors.foreground,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "No usernames, contacts, or conversations match your search.",
                                color = colors.foregroundSecondary,
                                fontSize = 12.5.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            ChatyTextButton(
                                onClick = {
                                    query = ""
                                    queueSearch("")
                                },
                                label = "Clear search",
                            )
                        }
                    }
                }
            }
        }
    }
}

/** ChatyGroupedSection equivalent: section header + grouped card. */
@Composable
private fun SearchSection(
    title: String,
    colors: ChatyColors,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            title,
            color = colors.foregroundSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated),
        ) {
            content()
        }
    }
}

@Composable
private fun UserTile(
    user: UserProfile,
    colors: ChatyColors,
    isOpeningChat: Boolean,
    onMessage: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        TaskAvatar(user.avatarInitials, user.avatarColorHex, 42, colors)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.displayName,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    color = colors.foreground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.isVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Verified, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
                }
            }
            Text(
                "@${user.username}${if (user.about.isNotEmpty()) " • ${user.about}" else ""}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.foreground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        ChatyActionButton(
            onClick = onMessage,
            modifier = Modifier.width(90.dp),
        ) {
            if (isOpeningChat) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("Message", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ConversationTile(
    conversation: Conversation,
    colors: ChatyColors,
    onOpen: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        TaskAvatar(conversation.avatarInitials ?: "CH", conversation.avatarColorHex ?: "0xFF6366F1", 42, colors)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                conversation.title,
                color = colors.foreground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Text(
                conversation.lastMessageText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.foreground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
            semanticLabel = null,
            color = colors.foreground.copy(alpha = 0.35f),
        )
    }
}
