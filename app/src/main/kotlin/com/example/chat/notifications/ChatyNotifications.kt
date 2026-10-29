package com.example.chat.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.chat.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * System notifications for chat messages and calls, used when the app is in the background or
 * closed (FCM push) and when it is backgrounded but the WebSocket is still alive.
 *
 *  - Messages: one MessagingStyle notification per chat, tap opens the chat.
 *  - Incoming call: full-screen, ringing (insistent) call notification with Answer / Decline,
 *    shown over the lock screen. Cancelled when the call is answered, declined or ends.
 *  - Missed call: shown when an incoming call ends without being answered.
 */
object ChatyNotifications {
    const val CHANNEL_MESSAGES = "chaty_messages"
    const val CHANNEL_CALLS = "chaty_incoming_calls_v1"
    const val CHANNEL_MISSED = "chaty_missed_calls"

    const val ACTION_OPEN_CHAT = "com.example.chat.action.OPEN_CHAT"
    const val ACTION_INCOMING_CALL = "com.example.chat.action.INCOMING_CALL"
    const val ACTION_ANSWER_CALL = "com.example.chat.action.ANSWER_CALL"
    const val ACTION_DECLINE_CALL = "com.example.chat.action.DECLINE_CALL"

    const val EXTRA_ROOM_ID = "room_id"
    const val EXTRA_CALL_ID = "call_id"
    const val EXTRA_CALLER_ID = "caller_id"
    const val EXTRA_CALLER_NAME = "caller_name"
    const val EXTRA_IS_VIDEO = "is_video"

    private const val CALL_NOTIFICATION_ID = 7410
    private const val MISSED_BASE_ID = 7500

    /** True while any app screen is visible (kept by ChatyApplication). */
    @Volatile var appInForeground: Boolean = false

    /** Chat to open after a notification tap (consumed by AppRoot). */
    private val _pendingRoomId = MutableStateFlow<String?>(null)
    val pendingRoomId: StateFlow<String?> = _pendingRoomId
    fun requestOpenRoom(roomId: String?) { if (!roomId.isNullOrBlank()) _pendingRoomId.value = roomId }
    fun consumeOpenRoom() { _pendingRoomId.value = null }

    /** Call the user acted on from the notification (consumed by MainActivity once the offer is in). */
    data class PendingCallAction(val callId: String, val answer: Boolean)
    private val _pendingCallAction = MutableStateFlow<PendingCallAction?>(null)
    val pendingCallAction: StateFlow<PendingCallAction?> = _pendingCallAction
    fun requestCallAction(action: PendingCallAction) { _pendingCallAction.value = action }
    fun consumeCallAction() { _pendingCallAction.value = null }

    // ── channels ───────────────────────────────────────────────────────

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New chat messages"
                enableVibration(true)
                setShowBadge(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings for incoming voice and video calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 800, 1000, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MISSED, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Calls you did not answer"
            },
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    // ── messages ───────────────────────────────────────────────────────

    private data class Line(val messageId: String, val sender: String, val text: String, val time: Long)
    private val linesByRoom = HashMap<String, MutableList<Line>>()
    private val shownMessageIds = LinkedHashSet<String>()

    fun showMessage(
        context: Context,
        roomId: String,
        messageId: String,
        senderName: String,
        text: String,
        roomTitle: String? = null,
        isGroup: Boolean = false,
    ) {
        if (roomId.isBlank() || !canPost(context)) return
        ensureChannels(context)
        val lines = synchronized(linesByRoom) {
            if (messageId.isNotEmpty()) {
                if (!shownMessageIds.add(messageId)) return // already shown (push + socket)
                if (shownMessageIds.size > 500) shownMessageIds.remove(shownMessageIds.first())
            }
            val list = linesByRoom.getOrPut(roomId) { mutableListOf() }
            list.add(Line(messageId, senderName.ifBlank { "New message" }, text, System.currentTimeMillis()))
            while (list.size > 7) list.removeAt(0)
            list.toList()
        }
        val me = Person.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me)
        if (isGroup) {
            style.setConversationTitle(roomTitle ?: "Group")
            style.setGroupConversation(true)
        }
        for (l in lines) style.addMessage(l.text, l.time, Person.Builder().setName(l.sender).build())

        val open = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            putExtra(EXTRA_ROOM_ID, roomId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setNumber(lines.size)
            .setContentIntent(PendingIntent.getActivity(context, roomId.hashCode(), open, flags()))
            .build()
        try { NotificationManagerCompat.from(context).notify(roomId, roomId.hashCode(), notification) } catch (_: SecurityException) {}
    }

    /** The chat was opened / read on this device: drop its notification. */
    fun cancelMessages(context: Context, roomId: String) {
        synchronized(linesByRoom) { linesByRoom.remove(roomId) }
        try { NotificationManagerCompat.from(context).cancel(roomId, roomId.hashCode()) } catch (_: Exception) {}
    }

    // ── calls ──────────────────────────────────────────────────────────

    @Volatile private var ringingCallId: String? = null
    private data class RingInfo(val callerId: String, val callerName: String, val isVideo: Boolean)
    private val ringInfo = HashMap<String, RingInfo>()

    val currentlyRingingCallId: String? get() = ringingCallId

    fun showIncomingCall(context: Context, callId: String, callerId: String, callerName: String, isVideo: Boolean) {
        if (callId.isBlank() || !canPost(context)) return
        if (ringingCallId == callId) return
        ensureChannels(context)
        synchronized(ringInfo) { ringInfo[callId] = RingInfo(callerId, callerName, isVideo) }
        ringingCallId = callId

        fun activityIntent(action: String) = Intent(context, MainActivity::class.java).apply {
            this.action = action
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_IS_VIDEO, isVideo)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreen = PendingIntent.getActivity(context, 1, activityIntent(ACTION_INCOMING_CALL), flags())
        val answer = PendingIntent.getActivity(context, 2, activityIntent(ACTION_ANSWER_CALL), flags())
        val decline = PendingIntent.getBroadcast(
            context, 3,
            Intent(context, CallActionReceiver::class.java).apply {
                action = ACTION_DECLINE_CALL
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CALLER_ID, callerId)
            },
            flags(),
        )
        val caller = Person.Builder().setName(callerName.ifBlank { "Unknown" }).setImportant(true).build()
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(callerName.ifBlank { "Incoming call" })
            .setContentText(if (isVideo) "Incoming video call" else "Incoming voice call")
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer).setIsVideo(isVideo))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(45_000)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT // keep ringing
        try { NotificationManagerCompat.from(context).notify(CALL_NOTIFICATION_ID, notification) } catch (_: SecurityException) {}
    }

    /** Stops the ringing notification (answered, declined, cancelled). */
    fun cancelIncomingCall(context: Context, callId: String? = null) {
        if (callId != null && ringingCallId != null && ringingCallId != callId) return
        ringingCallId = null
        try { NotificationManagerCompat.from(context).cancel(CALL_NOTIFICATION_ID) } catch (_: Exception) {}
    }

    /** The caller hung up / nobody answered: replace the ringing notification with "Missed call". */
    fun showMissedCall(context: Context, callId: String, callerName: String? = null, isVideo: Boolean? = null) {
        val info = synchronized(ringInfo) { ringInfo.remove(callId) }
        cancelIncomingCall(context, callId)
        if (!canPost(context)) return
        ensureChannels(context)
        val name = callerName?.takeIf { it.isNotBlank() } ?: info?.callerName ?: "Unknown"
        val video = isVideo ?: info?.isVideo ?: false
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("Missed ${if (video) "video" else "voice"} call")
            .setContentText(name)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 4, open, flags()))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(MISSED_BASE_ID + (callId.hashCode() and 0xFFF), notification)
        } catch (_: SecurityException) {}
    }

    fun forgetCall(callId: String) { synchronized(ringInfo) { ringInfo.remove(callId) } }

    // ── in-app ringing (app open: the call screen shows, this plays the ringtone) ──

    private var ringtone: android.media.Ringtone? = null
    private var vibrator: android.os.Vibrator? = null

    @Synchronized
    fun startRinging(context: Context) {
        if (ringtone?.isPlaying == true) return
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (am.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL) {
                ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                    play()
                }
            }
            if (am.ringerMode != android.media.AudioManager.RINGER_MODE_SILENT) {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                val pattern = longArrayOf(0, 1000, 800)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION") v?.vibrate(pattern, 0)
                }
                vibrator = v
            }
        } catch (e: Exception) {
            android.util.Log.w("ChatyNotifications", "ringtone failed: $e")
        }
    }

    @Synchronized
    fun stopRinging() {
        try { ringtone?.stop() } catch (_: Exception) {}
        try { vibrator?.cancel() } catch (_: Exception) {}
        ringtone = null
        vibrator = null
    }
}
