package com.example.chat.ui.chats.newchat

import com.example.chat.domain.models.UserProfile

/** Pure rules ported from new_chat_screen.dart (api branch anchor
 *  3bee780) and ApiBackendService.searchUsers
 *  (lib/data/services/api_backend_service.dart). UI-free so the JVM parity
 *  tests exercise the exact decision logic. */
object NewChatRules {

    /** Debounce applied by _onSearchChanged before firing _searchUsers. */
    const val SEARCH_DEBOUNCE_MS = 250L

    /** _finishGroupCreation title rules:
     *  - empty title + broadcast -> "Broadcast (<memberCount>)"
     *  - any other empty title -> "" (creation blocked)
     *  - broadcast title not starting with "broadcast" (case-insensitive)
     *    -> "Broadcast: <title>" */
    fun effectiveGroupTitle(rawTitle: String, isBroadcast: Boolean, memberCount: Int): String {
        var title = rawTitle.trim()
        if (title.isEmpty() && isBroadcast) title = "Broadcast ($memberCount)"
        if (title.isEmpty()) return ""
        return if (isBroadcast && !title.lowercase().startsWith("broadcast")) {
            "Broadcast: $title"
        } else {
            title
        }
    }

    /** Dart guard: `if (title.isEmpty || _selectedGroupMembers.isEmpty || _isLoading) return;` */
    fun canCreateGroup(rawTitle: String, isBroadcast: Boolean, memberCount: Int, isLoading: Boolean): Boolean =
        effectiveGroupTitle(rawTitle, isBroadcast, memberCount).isNotEmpty() && memberCount > 0 && !isLoading

    /** ApiBackendService.searchUsers local filter: after the remote
     *  loadUsers(query) the in-memory directory is matched by lowercase
     *  contains on displayName/username/email; self excluded unless
     *  includeSelf. */
    fun filterUsers(
        users: List<UserProfile>,
        query: String,
        selfId: String?,
        includeSelf: Boolean = false,
    ): List<UserProfile> {
        val q = query.lowercase().trim()
        return users.filter { u ->
            if (!includeSelf && selfId != null && u.id == selfId) {
                false
            } else if (q.isEmpty()) {
                true
            } else {
                u.displayName.lowercase().contains(q) ||
                    u.username.lowercase().contains(q) ||
                    u.email.lowercase().contains(q)
            }
        }
    }

    /** Dart epoch gate: async results apply only when `epoch == _epoch`. */
    fun isCurrentEpoch(resultEpoch: Int, currentEpoch: Int): Boolean = resultEpoch == currentEpoch

    /** _toggleMember: add when absent, remove when present. */
    fun toggleMember(selected: Set<String>, userId: String): Set<String> =
        if (selected.contains(userId)) selected - userId else selected + userId
}
