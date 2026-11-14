package com.example.chat.bubbles

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.chat.R
import com.example.chat.ServiceLocator
import com.example.chat.data.api.ApiAccountScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Native Android conversation bubbles. No overlay service, accessibility access or auto-activation. */
object ChatBubbleController {
    internal data class Ticket(val id: String, val roomId: String, val account: String, val scope: ApiAccountScope)
    private const val CHANNEL = "chaty_explicit_bubbles"
    internal const val EXTRA_TICKET = "bubble_ticket"
    private val tickets = ConcurrentHashMap<String, Ticket>()
    private val observers = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("chaty_bubble_mode", Context.MODE_PRIVATE)
    private var installed = false

    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        app = context.applicationContext
        // Tickets deliberately do not survive process death. Remove their OS surfaces on bootstrap.
        prefs.getStringSet("published", emptySet()).orEmpty().toSet().forEach(::removeSurface)
        prefs.edit().remove("published").apply()
        ServiceLocator.apiBackend.addAuthScopeListener { next ->
            // This callback runs under the auth transition lock: cancellation only, no IO or UI.
            val stale = tickets.values.filter { it.scope !== next }
            stale.forEach { tickets.remove(it.id) }
            main.post { stale.forEach { removeSurface(it.id) }; notifyObservers() }
        }
        ServiceLocator.preferencesController.addListener { invalidateUnsafe() }
        ServiceLocator.hiddenVaultController.addListener { invalidateUnsafe() }
        ServiceLocator.appScope.launch {
            ServiceLocator.appLock.lockRequired.collect { if (it) invalidateAll() }
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) invalidateAll()
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun enabledForCurrentAccount(): Boolean {
        val key = ServiceLocator.apiBackend.currentAccountHash() ?: return false
        return installed && prefs.getBoolean("enabled:$key", false)
    }

    /** Called only by the visible settings switch, never by notification or lifecycle events. */
    fun setEnabled(context: Context, enabled: Boolean) {
        install(context)
        val key = ServiceLocator.apiBackend.currentAccountHash() ?: return
        prefs.edit().putBoolean("enabled:$key", enabled).apply()
        if (!enabled) invalidateAll()
        notifyObservers()
    }

    internal fun resolve(id: String?): Ticket? = id?.let { tickets[it] }?.takeIf(::allowed)
    internal fun allowed(ticket: Ticket): Boolean {
        val api = ServiceLocator.apiBackend
        return BubbleAccessPolicy.allowed(
            enabled = enabledForCurrentAccount(), authenticated = api.isAuthenticated,
            sameScope = api.authSnapshot() === ticket.scope,
            sameAccount = api.currentAccountKey() == ticket.account,
            deviceLocked = (app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked,
            appLocked = ServiceLocator.appLock.lockRequired.value,
            protectedConversation = ServiceLocator.preferencesController.isConversationProtected(ticket.roomId) ||
                ServiceLocator.hiddenVaultController.isConversationLocked(ticket.roomId),
            conversationExists = api.conversations.any { it.id == ticket.roomId },
        )
    }

    /** User selected this chat in settings. Returns an honest status, not a promise the OS bubbled it. */
    fun requestBubble(context: Context, roomId: String): String {
        install(context)
        if (Build.VERSION.SDK_INT < 30) return "Android 11 or newer is required for this mode."
        if (!enabledForCurrentAccount()) return "Enable chat bubbles first."
        if (!notificationsAllowed()) return "Allow Chaty notifications in Android settings first."
        val api = ServiceLocator.apiBackend
        val account = api.currentAccountKey() ?: return "Sign in to your account first."
        val ticket = Ticket(UUID.randomUUID().toString(), roomId, account, api.authSnapshot())
        if (!allowed(ticket)) return "Unlock Chaty and select an unprotected chat from the current account."
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Chat bubbles", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Only conversations you explicitly choose to float over other apps"
            setShowBadge(false)
        })
        tickets.values.filter { it.roomId == roomId && it.account == account }.forEach { close(it.id) }
        tickets[ticket.id] = ticket
        val target = Intent(app, ChatBubbleActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse("chaty-bubble:${ticket.id}"))
            .putExtra(EXTRA_TICKET, ticket.id)
        val open = PendingIntent.getActivity(app, 0, target, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val icon = IconCompat.createWithResource(app, R.drawable.ic_notif_chat)
        // No message text, avatar, account key or session secrets are exported into shortcut metadata.
        val person = Person.Builder().setName("Chaty conversation").setKey(ticket.id).build()
        val shortcut = ShortcutInfoCompat.Builder(app, ticket.id).setShortLabel("Chaty conversation")
            .setIcon(icon).setPerson(person).setLongLived(true)
            .setCategories(setOf("com.logybyte.chaty.BUBBLE_CONVERSATION"))
            .setIntent(Intent(app, com.example.chat.MainActivity::class.java).setAction(Intent.ACTION_VIEW)).build()
        try {
            ShortcutManagerCompat.pushDynamicShortcut(app, shortcut)
            val bubble = NotificationCompat.BubbleMetadata.Builder(open, icon).setDesiredHeight(600)
                .setAutoExpandBubble(false).setSuppressNotification(false).build()
            val notification = NotificationCompat.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_notif_chat)
                .setContentTitle("Chaty conversation").setContentText("Tap to open your selected chat")
                .setContentIntent(open).setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setStyle(NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
                    .addMessage("Open selected conversation", System.currentTimeMillis(), person))
                .setShortcutId(ticket.id).setBubbleMetadata(bubble).setOnlyAlertOnce(true)
                .setAutoCancel(false).setVisibility(NotificationCompat.VISIBILITY_SECRET).build()
            if (!allowed(ticket)) { close(ticket.id); return "Account or lock changed. Choose the chat again." }
            val ids = prefs.getStringSet("published", emptySet()).orEmpty().toMutableSet().apply { add(ticket.id) }
            prefs.edit().putStringSet("published", ids).apply()
            NotificationManagerCompat.from(app).notify(ticket.id, 1, notification)
        } catch (_: Exception) {
            close(ticket.id)
            return "Android could not create this bubble. Check Chaty's notification and bubble settings."
        }
        return "Chat notification created. Android decides whether it appears as a bubble."
    }

    fun notificationsAllowed(): Boolean = installed && NotificationManagerCompat.from(app).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun openSystemSettings(context: Context) {
        val action = if (Build.VERSION.SDK_INT >= 30) Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS
        val intent = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
        }
    }

    internal fun addObserver(listener: () -> Unit) { observers.add(listener) }
    internal fun removeObserver(listener: () -> Unit) { observers.remove(listener) }
    private fun notifyObservers() { observers.forEach { it() } }
    internal fun close(id: String) {
        tickets.remove(id)
        main.post { removeSurface(id); notifyObservers() }
    }
    private fun invalidateUnsafe() { tickets.values.filterNot(::allowed).forEach { close(it.id) } }
    private fun invalidateAll() { tickets.keys.toList().forEach(::close) }
    private fun removeSurface(id: String) {
        NotificationManagerCompat.from(app).cancel(id, 1)
        ShortcutManagerCompat.removeDynamicShortcuts(app, listOf(id))
        if (Build.VERSION.SDK_INT >= 30) ShortcutManagerCompat.removeLongLivedShortcuts(app, listOf(id))
        val ids = prefs.getStringSet("published", emptySet()).orEmpty().toMutableSet().apply { remove(id) }
        prefs.edit().putStringSet("published", ids).apply()
    }
}
