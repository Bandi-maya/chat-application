package com.example.chat.data.services

import com.example.chat.core.validators.ChatyValidators
import com.example.chat.data.e2ee.MlsE2eeService
import com.example.chat.data.messaging.ConversationsRepository
import com.example.chat.data.messaging.TasksRepository
import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.domain.models.PresenceState
import com.example.chat.domain.models.UserProfile
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Snapshot of the signed-in auth user (GoTrue), as _loadCurrentProfile and
 *  updateCurrentUser read it from _client.auth.currentUser. Null Dart email /
 *  phone arrive as empty strings. */
data class AuthUserSnapshot(
    val id: String,
    val email: String = "",
    val phone: String = "",
    val userMetadata: Map<String, Any?> = emptyMap(),
)

/**
 * Exact port of backend_service.dart _loadCurrentProfile / updateCurrentUser
 * / _hydrateAuthenticatedState / _refreshLoadedMessageTimelines / _initials.
 * The unawaited setPresence(online) kick after each profile load routes
 * through [PresenceService] on [scope] (the ported
 * _effectivePublishedPresence rules apply identically).
 */
class ProfileService(
    private val queryClient: PostgrestQueryClient,
    private val conversationsRepository: ConversationsRepository,
    private val tasksRepository: TasksRepository?,
    private val presenceService: PresenceService,
    private val currentAuthUser: () -> AuthUserSnapshot?,
    private val privacy: () -> PresenceService.PrivacySnapshot?,
    private val scope: CoroutineScope,
    private val mls: MlsE2eeService? = null,
    private val notifyChanged: () -> Unit = {},
) {
    var currentUser: UserProfile? = null
        private set

    @Volatile
    private var isHydrating = false

    /** Exact port of _loadCurrentProfile (row -> profile; otherwise and on
     *  error a metadata/email fallback; unawaited online presence kick). */
    suspend fun loadCurrentProfile() {
        val user = currentAuthUser() ?: return
        val emailOrNull = user.email.ifEmpty { null }

        fun fallback(): UserProfile = UserProfile(
            id = user.id,
            displayName = user.userMetadata["display_name"]?.toString()
                ?: emailOrNull?.split('@')?.first()
                ?: "Chaty User",
            username = user.userMetadata["username"]?.toString() ?: "user",
            avatarInitials = initials(emailOrNull ?: "CU"),
            avatarColorHex = "0xFF6366F1",
            about = "",
            presence = PresenceState.online,
            lastSeenAt = Instant.now(),
            isVerified = false,
            email = user.email,
            phone = user.phone,
            safetyNumber = "",
        )

        try {
            val rows = queryClient.from("profiles").select().eq("id", user.id).limit(1).execute()
            val row = rows.firstOrNull()
            val profile = if (row != null) {
                ConversationsRepository.profileFromRow(
                    row, email = user.email, phone = user.phone,
                )
            } else {
                fallback()
            }
            currentUser = profile
            conversationsRepository.usersById[profile.id] = profile
        } catch (error: Throwable) {
            val profile = fallback()
            currentUser = profile
            conversationsRepository.usersById[profile.id] = profile
        }

        // unawaited(setPresence(PresenceState.online)) on the Dart side.
        scope.launch {
            runCatching { presenceService.setPresence(PresenceState.online) }
        }
    }

    /** Exact port of updateCurrentUser's non-demo path. */
    suspend fun updateCurrentUser(updated: UserProfile) {
        val authUser = currentAuthUser()
        if (authUser == null || authUser.id != updated.id) {
            throw IllegalStateException("You can only update the signed-in profile.")
        }
        val snap = privacy()
        val update = mutableMapOf<String, Any?>(
            "username" to ChatyValidators.normalizeUsername(updated.username),
            "display_name" to updated.displayName.trim(),
            "about" to updated.about.trim(),
            "phone" to updated.phone.trim(),
            "avatar_initials" to updated.avatarInitials,
            "avatar_color_hex" to updated.avatarColorHex,
        )
        updated.avatarUrl?.let { update["avatar_url"] = it }
        updated.bannerUrl?.let { update["banner_url"] = it }
        update["presence"] = PresenceService.presenceToDatabase(
            PresenceService.effectivePublishedPresence(updated.presence, snap),
        )
        if (snap == null || !snap.freezeLastSeen) {
            update["last_seen_at"] = Instant.now().toString()
        }
        queryClient.from("profiles").eq("id", authUser.id).update(update)
        loadCurrentProfile()
        notifyChanged()
    }

    /** Exact port of _hydrateAuthenticatedState (re-entrancy guard, MLS
     *  session init + device-enrollment retry, parallel conversations/tasks
     *  load, message-timeline refresh). MLS stays fail-closed when still not
     *  ready; Dart only logs there. */
    suspend fun hydrateAuthenticatedState() {
        if (isHydrating) return
        isHydrating = true
        try {
            loadCurrentProfile()
            mls?.let { service ->
                service.initializeForCurrentSession()
                if (!service.isReady) {
                    service.retryDeviceEnrollment()
                }
            }
            coroutineScope {
                val a = launch { conversationsRepository.loadConversations(true) }
                val b = launch { tasksRepository?.loadTasks() }
                a.join(); b.join()
            }
            refreshLoadedMessageTimelines()
            notifyChanged()
        } finally {
            isHydrating = false
        }
    }

    /** Exact port of _refreshLoadedMessageTimelines (per-chat reload,
     *  failures swallowed individually). */
    suspend fun refreshLoadedMessageTimelines() {
        val ids = conversationsRepository.messagesByChatId.keys.toList()
        for (id in ids) {
            runCatching { conversationsRepository.loadMessages(id) }
        }
    }

    companion object {
        /** Exact port of _initials. */
        fun initials(name: String): String {
            val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return "CU"
            if (words.size == 1) {
                return words.first().substring(0, if (words.first().length >= 2) 2 else 1)
                    .uppercase()
            }
            return "${words.first()[0]}${words.last()[0]}".uppercase()
        }
    }
}
