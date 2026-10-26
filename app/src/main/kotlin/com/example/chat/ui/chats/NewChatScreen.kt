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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var searchText by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    var results by remember { mutableStateOf(contacts) }
    var isCreatingGroup by remember { mutableStateOf(startInGroupMode || startInBroadcastMode) }
    var isBroadcast by remember { mutableStateOf(startInBroadcastMode) }
    var isLoading by remember { mutableStateOf(false) }
    var epoch by remember { mutableIntStateOf(0) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    var sheetUser by remember { mutableStateOf<UserProfile?>(null) }
    var deleteCandidate by remember { mutableStateOf<UserProfile?>(null) }

    fun errorText(error: Throwable): String =
        (error.message ?: error.toString()).removePrefix("Exception: ")

    // _loadInitialUsers: remote directory load; failure only clears loading.
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            results = searchUsersRemote("")
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    fun runSearch(query: String, searchEpoch: Int) {
        scope.launch {
            try {
                val users = searchUsersRemote(query)
                if (!NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                results = users
                isLoading = false
            } catch (error: Exception) {
                if (!NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                isLoading = false
                snackbar.showSnackbar(errorText(error))
            }
        }
    }

    // _onSearchChanged: cancel debounce; empty query restores contacts;
    // otherwise epoch++ + 250ms debounce before the remote search.
    fun onSearchChanged(value: String) {
        searchText = value
        debounceJob?.cancel()
        val query = value.trim()
        if (query.isEmpty()) {
            results = contacts
            isLoading = false
            return
        }
        val nextEpoch = ++epoch
        isLoading = true
        debounceJob = scope.launch {
            delay(NewChatRules.SEARCH_DEBOUNCE_MS)
            runSearch(query, nextEpoch)
        }
    }

    fun finishGroupCreation() {
        val title = NewChatRules.effectiveGroupTitle(groupName, isBroadcast, selectedMembers.size)
        if (!NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading)) return
        scope.launch {
            isLoading = true
            try {
                val conversation = createGroup(title, selectedMembers.toList())
                onOpenConversation(conversation)
            } catch (error: Exception) {
                isLoading = false
                snackbar.showSnackbar(errorText(error))
            }
        }
    }

    fun openDirectChat(user: UserProfile) {
        if (isLoading) return
        scope.launch {
            isLoading = true
            try {
                val conversation = openDirectConversation(user)
                onOpenConversation(conversation)
            } catch (error: Exception) {
                isLoading = false
                snackbar.showSnackbar(errorText(error))
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
                    if (isCreatingGroup) {
                        isCreatingGroup = false
                        isBroadcast = false
                        selectedMembers = emptySet()
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = colors.foreground)
                }
                Text(
                    if (isCreatingGroup) {
                        if (isBroadcast) "New Broadcast (${selectedMembers.size})" else "New Group (${selectedMembers.size})"
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
                        onClick = { finishGroupCreation() },
                        enabled = selectedMembers.isNotEmpty(),
                    ) {
                        Text(
                            "Create",
                            color = if (selectedMembers.isNotEmpty()) colors.primary else colors.foregroundTertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (isCreatingGroup) {
                    NewChatInput(
                        value = groupName,
                        onValueChange = { groupName = it },
                        hint = if (isBroadcast) "Enter broadcast list name..." else "Enter group subject...",
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
                NewChatInput(
                    value = searchText,
                    onValueChange = { onSearchChanged(it) },
                    hint = "Search @username or name...",
                    leading = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.foregroundTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailing = if (searchText.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchChanged("") }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = colors.foregroundSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            if (!isCreatingGroup) {
                NewChatEntryCard(
                    title = "Create New Group",
                    subtitle = "Start an encrypted group with multiple members",
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
            when {
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
                            "No results for \"${searchText.trim()}\"",
                            color = colors.foreground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No contacts or users matched your search keyword.",
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
                            "Search contacts",
                            color = colors.foreground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Enter a name or @username above to start an encrypted conversation.",
                            color = colors.foregroundSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        itemsIndexed(results, key = { _, u -> u.id }) { index, user ->
                            val isSelected = selectedMembers.contains(user.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isCreatingGroup) {
                                            selectedMembers = NewChatRules.toggleMember(selectedMembers, user.id)
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
                                        "@${user.username}${if (user.about.isNotEmpty()) " • ${user.about}" else ""}",
                                        color = colors.foregroundSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isCreatingGroup) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedMembers = NewChatRules.toggleMember(selectedMembers, user.id)
                                        },
                                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                                            checkedColor = colors.primary,
                                        ),
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.foregroundTertiary,
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
    }

    // _onUserLongPress sheet: Start Chat / Delete User.
    val sheetTarget = sheetUser
    if (sheetTarget != null) {
        ModalBottomSheet(
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
                        isLoading = true
                        try {
                            deleteUser(deleteTarget.id)
                            try {
                                results = searchUsersRemote("")
                            } catch (_: Exception) {
                            }
                            snackbar.showSnackbar("${deleteTarget.displayName} deleted")
                        } catch (error: Exception) {
                            snackbar.showSnackbar("Failed to delete user: $error")
                        } finally {
                            isLoading = false
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
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = chevronTint)
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
