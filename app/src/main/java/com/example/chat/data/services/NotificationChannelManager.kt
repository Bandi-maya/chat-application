package com.example.chat.data.services

import android.content.Context
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.domain.models.Conversation
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Exact port of lib/data/services/notification_channel_manager.dart
 * (NotificationChannelManager) badge-sync scope: unread-badge computation,
 * the disableBadgeCounter / enableGlobalNotifications gate, the education
 * flag, and the persisted badge record.
 *
 * Persisted record parity: the Dart class writes through the
 * shared_preferences plugin, whose Android storage is the
 * "FlutterSharedPreferences" file with keys prefixed "flutter.". This port
 * reads and writes the exact same file + keys so the Kotlin record is
 * byte-identical to the Dart one.
 *
 * Android platform note (verified against the Dart tree): nothing in the
 * Dart native android/ sources reads `chaty_unread_badge_count`, and no
 * badge plugin (flutter_app_badger / ShortcutBadger) exists in pubspec.
 * Stock Android has no launcher-badge COUNT API: launcher badges derive
 * from posted notifications on channels with setShowBadge(true); the
 * numeric count shown is an OEM-launcher decision. So the persisted count
 * is the complete source-faithful behavior - there is no launcher call to
 * port, and none is invented here.
 *
 * Deferred (out of this scope, flagged to main): deep-link dispatch
 * (NotificationPayload, NotificationTapDeduplicator, handleNotificationTap,
 * deepLinks stream) belongs with push/notification routing.
 */
data class ChatyNotificationChannel(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int, // 1-5
    val enableLights: Boolean = true,
    val enableVibration: Boolean = true,
    val showBadge: Boolean = true,
)

class NotificationChannelManager(
    private val context: Context,
    private val preferences: ChatyPreferencesController,
    /** Dart dataStore.conversations. */
    private val conversations: () -> Collection<Conversation>,
) {
    companion object {
        private const val PREFS_FILE = "FlutterSharedPreferences"
        private const val EDUCATION_SHOWN_KEY = "flutter.chaty_notif_education_shown"
        private const val BADGE_COUNT_KEY = "flutter.chaty_unread_badge_count"
    }

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** Dart ChangeNotifier.addListener/removeListener. */
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { for (l in listeners) l() }

    var badgeCount: Int = 0
        private set
    var educationShown: Boolean = false
        private set

    init {
        // Dart ctor: dataStore.addListener(syncUnreadBadgeCount) is wired at
        // the composition root (ConversationsRepository.notifyChanged ->
        // syncUnreadBadgeCount); preferences.addListener lives here.
        preferences.addListener { syncUnreadBadgeCount() }
    }

    /** Dart initialize(): restore the persisted record, then sync. */
    fun initialize() {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        educationShown = prefs.getBoolean(EDUCATION_SHOWN_KEY, false)
        badgeCount = prefs.getInt(BADGE_COUNT_KEY, 0)
        syncUnreadBadgeCount()
    }

    /** Dart markEducationShown(): first-launch notification education modal completed. */
    fun markEducationShown() {
        educationShown = true
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(EDUCATION_SHOWN_KEY, true).apply()
        notifyListeners()
    }

    /**
     * Dart syncUnreadBadgeCount(): real unread total across non-muted
     * conversations, gated on global notifications + badge counter pref;
     * persists the count and notifies listeners.
     */
    fun syncUnreadBadgeCount() {
        if (!preferences.notification.enableGlobalNotifications ||
            preferences.universal.disableBadgeCounter
        ) {
            badgeCount = 0
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                .edit().putInt(BADGE_COUNT_KEY, 0).apply()
            notifyListeners()
            return
        }

        var totalUnread = 0
        for (convo in conversations()) {
            if (!convo.isMuted) {
                totalUnread += convo.unreadCount
            }
        }

        badgeCount = totalUnread
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putInt(BADGE_COUNT_KEY, badgeCount).apply()
        notifyListeners()
    }

    /** Dart clearBadge(): clear on logout or reset. */
    fun clearBadge() {
        badgeCount = 0
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putInt(BADGE_COUNT_KEY, 0).apply()
        notifyListeners()
    }

    /**
     * Dart formatPrivacyBody(): notification body per privacy preference
     * (Preview ON / OFF).
     */
    fun formatPrivacyBody(
        senderName: String,
        content: String,
        isCall: Boolean = false,
    ): String {
        if (!preferences.notification.showMessagePreview) {
            return if (isCall) "Incoming call" else "New message"
        }
        return content
    }

    /** Dart dispose(). */
    fun dispose() {
        listeners.clear()
    }
}
