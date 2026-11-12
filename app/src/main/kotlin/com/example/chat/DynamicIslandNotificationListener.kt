package com.example.chat

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.core.Trigger
import com.example.chat.island.engine.IslandController
import com.example.chat.island.engine.IslandMode
import com.example.chat.island.engine.LiveKind
import com.example.chat.island.engine.dominantColor
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DynamicIslandNotificationListener : NotificationListenerService() {
    private val lifetime = MainScope()
    private var bound = false
    private var receiverReady = false
    private var mediaManager: MediaSessionManager? = null
    private var players = emptyList<MediaController>()
    private val registration get() = ComponentName(this, DynamicIslandNotificationListener::class.java)
    private val fingerprints = linkedMapOf<String, Int>()
    private val timerSamples = mutableMapOf<String, Pair<Int, Long>>()
    private val callKeys = mutableSetOf<String>()
    private val settings get() = getSharedPreferences("chaty_dynamic_island", MODE_PRIVATE)
    private val masterChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshPrivacy()
        reconcile()
    }
    private val deviceState = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshPrivacy()
            if (capture()) reconcile()
        }
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> replacePlayers(list.orEmpty()) }
    private val playerChanged = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { updatePlayer() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { updatePlayer() }
        override fun onSessionDestroyed() { refreshPlayers() }
    }

    override fun onCreate() {
        super.onCreate()
        IslandSettings.init(applicationContext)
        IslandController.attach(applicationContext)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, deviceState, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverReady = true
        settings.registerOnSharedPreferenceChangeListener(masterChanged)
        refreshPrivacy()
        lifetime.launch {
            combine(IslandSettings.triggers, IslandSettings.blockedApps, IslandSettings.enabled) { _, _, _ -> Unit }
                .collect { reconcile() }
        }
    }

    private fun capture(): Boolean = isEnabled(this) && IslandSettings.enabled.value && !isScreenPrivate(this)

    private fun refreshPrivacy() {
        val suppressed = !capture()
        IslandController.setPrivacySuppressed(suppressed)
        if (suppressed) {
            fingerprints.clear()
            timerSamples.clear()
            IslandController.clearAll()
            IslandController.clearCall()
            IslandController.clearLive()
            IslandController.clearDownload()
            IslandController.dropClockTimerSilently()
            IslandController.setMedia(null)
        }
    }

    override fun onListenerConnected() {
        if (bound) disconnectMedia()
        bound = true
        mediaManager = getSystemService(MediaSessionManager::class.java)
        try { mediaManager?.addOnActiveSessionsChangedListener(sessionsChanged, registration) } catch (_: Exception) {}
        refreshPrivacy()
        refreshPlayers()
        reconcile()
    }

    private fun disconnectMedia() {
        try { mediaManager?.removeOnActiveSessionsChangedListener(sessionsChanged) } catch (_: Exception) {}
        replacePlayers(emptyList())
    }

    override fun onListenerDisconnected() {
        bound = false
        disconnectMedia()
        try { requestRebind(registration) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        bound = false
        disconnectMedia()
        settings.unregisterOnSharedPreferenceChangeListener(masterChanged)
        if (receiverReady) try { unregisterReceiver(deviceState) } catch (_: Exception) {}
        receiverReady = false
        lifetime.cancel()
        super.onDestroy()
    }

    private fun refreshPlayers() {
        val available = try { mediaManager?.getActiveSessions(registration).orEmpty() } catch (_: Exception) { emptyList() }
        replacePlayers(available)
    }

    private fun replacePlayers(list: List<MediaController>) {
        players.forEach { try { it.unregisterCallback(playerChanged) } catch (_: Exception) {} }
        players = list
        players.forEach { try { it.registerCallback(playerChanged) } catch (_: Exception) {} }
        updatePlayer()
    }

    private fun playing(player: MediaController): Boolean = player.playbackState?.state in
        setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING)

    private fun updatePlayer() {
        val allowed = players.filter { it.packageName != packageName && it.packageName !in IslandSettings.blockedApps.value }
        val player = allowed.firstOrNull { playing(it) } ?: allowed.firstOrNull()
        IslandController.media = player
        if (!capture() || !IslandSettings.isOn(Trigger.Media)) { IslandController.setMedia(null); return }
        val metadata = player?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (player == null || metadata == null || title.isNullOrBlank()) { IslandController.setMedia(null); return }
        val artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val playback = player.playbackState
        IslandController.setMedia(IslandMode.Media(title, metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            playing(player), artwork, artwork?.let { dominantColor(it) },
            metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            (playback?.position ?: 0).coerceAtLeast(0), playback?.lastPositionUpdateTime ?: 0,
            playback?.playbackSpeed ?: 1f))
    }

    private fun snapshot(): List<StatusBarNotification> = try { activeNotifications?.toList().orEmpty() } catch (_: Exception) { emptyList() }

    private fun reconcile() {
        if (!bound) return
        refreshPrivacy()
        updatePlayer()
        if (!capture()) return
        val current = snapshot().filter { it.packageName != packageName }
        callKeys.retainAll(current.map { it.key }.toSet())
        if (!IslandSettings.isOn(Trigger.Calls)) IslandController.clearCall()
        else current.firstOrNull { callNotification(it) }?.let { presentCall(it) } ?: IslandController.clearCall()
        IslandController.clearLive()
        IslandController.clearDownload()
        if (!IslandSettings.isOn(Trigger.Timer)) IslandController.dropClockTimerSilently()
        current.filter { it.packageName !in IslandSettings.blockedApps.value }.forEach {
            clockNotification(it) || liveNotification(it) || progressNotification(it)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || !capture()) return
        if (callNotification(sbn)) { presentCall(sbn); return }
        if (sbn.packageName in IslandSettings.blockedApps.value) return
        if (clockNotification(sbn) || liveNotification(sbn) || progressNotification(sbn)) return
        presentAlert(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (callKeys.remove(sbn.key)) IslandController.clearCall(sbn.key)
        IslandController.clearLive(sbn.key)
        IslandController.clearDownload(sbn.key)
        if (capture()) IslandController.clearClockTimer(sbn.key) else IslandController.dropClockTimerSilently(sbn.key)
        IslandController.notificationRemoved(sbn.key)
        timerSamples.remove(sbn.key)
        fingerprints.remove(sbn.key)
    }

    private fun appName(item: StatusBarNotification): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(item.packageName, 0)).toString()
    } catch (_: Exception) { item.packageName }

    @Suppress("DEPRECATION")
    private fun message(notification: Notification): String {
        val extras = notification.extras
        val plain = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (!plain.isNullOrBlank()) return plain
        val history = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (history != null) {
            val last = Notification.MessagingStyle.Message.getMessagesFromBundleArray(history).lastOrNull()?.text?.toString()
            if (!last.isNullOrBlank()) return last
        }
        return extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
    }

    private fun presentAlert(item: StatusBarNotification) {
        val value = item.notification
        if (!IslandSettings.isOn(Trigger.Notifications) || value.flags and Notification.FLAG_GROUP_SUMMARY != 0 || value.category == Notification.CATEGORY_TRANSPORT) return
        val includeQuiet = IslandSettings.isOn(Trigger.Quiet)
        if (!includeQuiet && (item.isOngoing || value.category in setOf(Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE))) return
        val ranking = Ranking()
        val quiet = currentRanking.getRanking(item.key, ranking) && ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
        if (quiet && !includeQuiet) return
        val title = value.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = message(value)
        if (title.isBlank() && body.isBlank()) return
        if (item.isOngoing && fingerprints.containsKey(item.key)) return
        val signature = listOf(title, body, value.`when`).hashCode()
        if (fingerprints[item.key] == signature) return
        fingerprints[item.key] = signature
        while (fingerprints.size > 256) fingerprints.remove(fingerprints.keys.first())
        val actions = value.actions.orEmpty()
        val reply = actions.firstOrNull { action -> action.remoteInputs.orEmpty().any { it.allowFreeFormInput } }
        val read = actions.firstOrNull { action ->
            val label = action.title?.toString()?.lowercase().orEmpty()
            (Build.VERSION.SDK_INT >= 28 && action.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ) || label in setOf("mark as read", "mark read")
        }?.actionIntent
        val duration = if (reply != null || read != null) 8000L else if (quiet || item.isOngoing) 3000L else 4500L
        IslandController.flash(IslandMode.Notification(appName(item), title, body, reply != null, read != null),
            durationMs = duration, open = value.contentIntent, pkg = item.packageName,
            reply = reply, markRead = read, key = item.key)
    }

    private fun words(text: CharSequence?): Set<String> = text?.toString()?.lowercase()
        ?.split(NON_LETTER)?.filter { it.isNotBlank() }?.toSet().orEmpty()

    private fun audioMode(): Int = try { getSystemService(AudioManager::class.java)?.mode ?: AudioManager.MODE_NORMAL } catch (_: Exception) { AudioManager.MODE_NORMAL }

    private fun callNotification(item: StatusBarNotification): Boolean {
        if (item.packageName == packageName) return false
        val value = item.notification
        if (value.category == Notification.CATEGORY_CALL || value.extras.containsKey("android.callType") ||
            value.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("CallStyle")) return true
        val default = try { getSystemService(TelecomManager::class.java)?.defaultDialerPackage } catch (_: Exception) { null }
        if (item.packageName !in DIALERS && item.packageName != default) return false
        val action = value.actions.orEmpty().any { words(it.title).intersect(CALL_ACTION_WORDS).isNotEmpty() }
        return item.isOngoing || value.fullScreenIntent != null || action || audioMode() in setOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_RINGTONE)
    }

    private fun presentCall(item: StatusBarNotification) {
        if (!IslandSettings.isOn(Trigger.Calls)) return
        callKeys.add(item.key)
        val value = item.notification
        fun action(matching: Set<String>) = value.actions.orEmpty().firstOrNull { words(it.title).intersect(matching).isNotEmpty() }?.actionIntent
        val accept = action(setOf("answer", "accept", "pick"))
        // Never guess an action by its position. A mislabeled action must not be fired as decline.
        val reject = action(setOf("decline", "reject", "hang", "hangup", "end", "cut", "disconnect", "dismiss"))
        val type = value.extras.getInt("android.callType", 0)
        val counting = value.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val incoming = when (type) {
            1 -> true
            2 -> false
            else -> accept != null || (!counting && (value.fullScreenIntent != null || audioMode() == AudioManager.MODE_RINGTONE))
        }
        IslandController.setCall(value.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().ifBlank { "Call" },
            incoming, accept, reject, key = item.key)
    }

    private fun clockNotification(item: StatusBarNotification): Boolean {
        if (item.packageName !in CLOCK_APPS) return false
        val value = item.notification
        val extras = value.extras
        if (extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)) {
            if (!extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)) return false
            if (!IslandSettings.isOn(Trigger.Timer)) return true
            val remaining = value.`when` - System.currentTimeMillis()
            if (remaining in 1..86_400_000L) IslandController.syncClockTimer(item.key, SystemClock.elapsedRealtime() + remaining)
            return true
        }
        IslandController.pauseClockTimer(item.key)
        if (!item.isOngoing) return false
        val text = arrayOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT)
            .joinToString(" ") { extras.getCharSequence(it)?.toString().orEmpty() }
        if (text.contains("stopwatch", true) || text.contains("स्टॉपवॉच")) return false
        val match = TIME_RE.find(text) ?: return false
        if (!IslandSettings.isOn(Trigger.Timer)) return true
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: return false
        val seconds = match.groupValues[3].toIntOrNull() ?: return false
        if (minutes > 59 || seconds > 59) return false
        val total = hours * 3600 + minutes * 60 + seconds
        val now = SystemClock.elapsedRealtime()
        val old = timerSamples[item.key]
        if (old == null || now - old.second >= 1000) {
            timerSamples[item.key] = total to now
            if (old != null && total < old.first && now - old.second < 5000) {
                IslandController.syncClockTimer(item.key, now + total * 1000L, needsBeat = true)
            } else if (old != null) IslandController.clearClockTimer(item.key)
        }
        return true
    }

    private fun liveNotification(item: StatusBarNotification): Boolean {
        if (!item.isOngoing) return false
        val kind = when (item.packageName) {
            in NAV_APPS -> LiveKind.Navigation
            in TRACK_APPS -> LiveKind.Tracking
            else -> return false
        }
        val enabled = if (kind == LiveKind.Navigation) Trigger.Navigation else Trigger.Tracking
        if (!IslandSettings.isOn(enabled)) return true
        val extras = item.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val detail = arrayOf(Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT)
            .mapNotNull { extras.getCharSequence(it)?.toString()?.trim()?.takeIf { value -> value.isNotEmpty() } }.distinct().joinToString(", ")
        if (title.isNotBlank() || detail.isNotBlank()) IslandController.setLive(IslandMode.Live(kind, appName(item), title, detail),
            item.key, item.notification.contentIntent, item.packageName)
        return true
    }

    private fun progressNotification(item: StatusBarNotification): Boolean {
        val value = item.notification
        val extras = value.extras
        val maximum = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val unknown = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        if (maximum <= 0 && !unknown) { IslandController.clearDownload(item.key); return false }
        if (value.category == Notification.CATEGORY_TRANSPORT || value.category == Notification.CATEGORY_CALL) return false
        if (!IslandSettings.isOn(Trigger.Downloads)) return true
        val percentage = if (unknown || maximum <= 0) -1 else ((extras.getInt(Notification.EXTRA_PROGRESS, 0).toLong() * 100) / maximum).toInt().coerceIn(0, 100)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val detail = message(value).ifBlank { if (percentage >= 0) "$percentage%" else "" }
        if (title.isNotBlank() || detail.isNotBlank()) IslandController.setDownload(IslandMode.Live(LiveKind.Download, appName(item), title, detail, percentage),
            item.key, value.contentIntent, item.packageName)
        return true
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("chaty_dynamic_island", Context.MODE_PRIVATE)
        fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)
        fun isHideSystemNotificationsEnabled(context: Context): Boolean = prefs(context).getBoolean("hide_system_notifications", false)
        fun isHeaderEnabled(context: Context): Boolean = prefs(context).getBoolean("show_island_header", false)
        fun setHideSystemNotifications(context: Context, hide: Boolean) { prefs(context).edit().putBoolean("hide_system_notifications", hide).apply() }
        fun setHeaderEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("show_island_header", enabled).apply() }
        fun hasNotificationAccess(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            return if (Build.VERSION.SDK_INT >= 27) manager.isNotificationListenerAccessGranted(ComponentName(context, DynamicIslandNotificationListener::class.java))
            else androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }
        fun isScreenPrivate(context: Context): Boolean {
            val awake = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive
            val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked
            return awake == false || locked == true
        }
        val NON_LETTER = Regex("[^\\p{L}]+")
        val CALL_ACTION_WORDS = setOf("answer", "accept", "decline", "reject", "hang", "hangup", "end")
        val TIME_RE = Regex("\\b(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\b")
        // Package identifiers are interoperability data, not implementation bodies.
        val CLOCK_APPS = setOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.coloros.alarmclock",
            "com.oplus.alarmclock",
            "com.oneplus.deskclock",
            "com.android.BBKClock",
            "com.huawei.deskclock",
            "com.asus.deskclock",
            "com.motorola.blur.alarmclock",
            "com.lge.clock",
            "com.sonyericsson.organizer",
            "com.nothing.deskclock"
        )
        val NAV_APPS = setOf(
            "com.google.android.apps.maps",
            "com.waze",
            "net.osmand",
            "net.osmand.plus"
        )
        val TRACK_APPS = setOf(
            "com.application.zomato",
            "in.swiggy.android",
            "com.grofers.customerapp",
            "com.zeptoconsumerapp",
            "com.ubercab",
            "com.olacabs.customer",
            "com.rapido.passenger"
        )
        val DIALERS = setOf(
            "com.android.incallui",
            "com.android.server.telecom",
            "com.android.phone",
            "com.android.dialer",
            "com.android.contacts",
            "com.android.telephony",
            "com.google.android.dialer",
            "com.google.android.apps.dialer",
            "com.google.android.contacts",
            "com.android.services.telephony",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.samsung.android.app.telephonyui",
            "com.samsung.android.contacts",
            "com.samsung.android.callassistant",
            "com.samsung.android.phone",
            "com.miui.voip",
            "com.xiaomi.phone",
            "com.miui.contacts",
            "com.miui.incallui",
            "com.xiaomi.simactivate.service",
            "com.oplus.dialer",
            "com.coloros.dialer",
            "com.oneplus.dialer",
            "com.oplus.incallui",
            "com.coloros.incallui",
            "com.oneplus.incallui",
            "com.oplus.telephony",
            "com.coloros.phone",
            "com.oneplus.contacts",
            "com.coloros.contacts",
            "com.oplus.contacts",
            "com.vivo.dialer",
            "com.vivo.incallui",
            "com.vivo.contacts",
            "com.vivo.phone",
            "com.bbk.dialer",
            "com.bbk.incallui",
            "com.iqoo.dialer",
            "com.huawei.contacts",
            "com.huawei.android.incallui",
            "com.huawei.phone",
            "com.hihonor.contacts",
            "com.hihonor.incallui",
            "com.hihonor.dialer",
            "com.motorola.dialer",
            "com.motorola.incallui",
            "com.motorola.contacts",
            "com.lenovo.dialer",
            "com.lenovo.incallui",
            "com.nothing.dialer",
            "com.nothing.incallui",
            "com.nothing.contacts",
            "com.asus.contacts",
            "com.asus.incallui",
            "com.asus.dialer",
            "com.sonyericsson.android.dialer",
            "com.sonymobile.dialer",
            "com.sonyericsson.android.incallui",
            "com.lge.incallui",
            "com.lge.contacts",
            "com.lge.dialer",
            "com.htc.contacts",
            "com.htc.incallui",
            "com.hmdglobal.incallui",
            "com.hmdglobal.dialer",
            "com.nokia.incallui",
            "com.zte.incallui",
            "com.zte.dialer",
            "com.meizu.incallui",
            "com.meizu.dialer",
            "com.transsion.incallui",
            "com.transsion.dialer",
            "com.transsion.phonedialer",
            "com.transsion.contacts",
            "com.itel.dialer",
            "com.tecno.dialer",
            "com.infinix.dialer",
            "com.lava.dialer",
            "com.micromax.dialer",
            "com.gionee.dialer",
            "com.cloudminds.dialer",
            "com.truecaller",
            "com.simplemobiletools.dialer",
            "org.fossify.phone",
            "com.contacts.phone.dialer",
            "com.drupe.swd",
            "com.hiya.stingray",
            "com.mobile.dialer",
            "com.dialer.phone"
        )
    }
}
