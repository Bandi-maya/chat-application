package com.example.chat.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.example.chat.R

/**
 * Builds and posts system notifications. Channels are created once with the importance's system defaults;
 * sound, vibration and DND stay under the user's control in Android settings (never overridden here).
 */
object ChatyNotificationPoster {

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(ChatyNotificationPolicy.CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New chat messages"; enableVibration(true); setShowBadge(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(ChatyNotificationPolicy.CH_INCOMING_CALL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming voice and video calls"; enableVibration(true); setShowBadge(false)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            },
        )
    }

    private fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun smallIcon(style: ChatyNotificationStyle, call: Boolean = false): Int =
        if (call) R.drawable.ic_notif_call
        else if (style.iconVariant == ChatyNotificationStyle.ICON_BELL) R.drawable.ic_notif_bell else R.drawable.ic_notif_chat

    private fun broadcast(context: Context, action: String, nonce: String, mutable: Boolean = false): PendingIntent {
        val i = Intent(action).setClass(context, ChatyNotificationActionReceiver::class.java)
            .putExtra(ChatyNotificationPolicy.EXTRA_NONCE, nonce)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
        return PendingIntent.getBroadcast(context, nonce.hashCode(), i, flags)
    }

    /** Opens the existing launch activity (normal auth, app lock and incoming-call UI apply there). */
    fun openApp(context: Context, req: Int): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } ?: return null
        return PendingIntent.getActivity(context, req, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** replyNonce/readNonce null means that action is not offered. [tag] carries the lane's canonical
     *  m:<generation>:<conversationId> tag. R6: internal - the Coordinator's sink implementation and the
     *  fixture are the only callers; no other call site may post or cancel. */
    internal fun postMessage(
        context: Context, style: ChatyNotificationStyle, tag: String, title: String, body: String,
        timestampMillis: Long, replyNonce: String?, readNonce: String?,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)
        val nid = ChatyNotificationPolicy.messageNotificationId(tag)
        val sender = Person.Builder().setName(title).build()
        val msgStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            .addMessage(body, timestampMillis, sender)
        val b = NotificationCompat.Builder(context, ChatyNotificationPolicy.CH_MESSAGES)
            .setSmallIcon(smallIcon(style)).setColor(style.accentArgb)
            .setStyle(msgStyle).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setGroup("chaty_messages")
            .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(context, nid))
        if (replyNonce != null) {
            b.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notif_reply, "Reply", broadcast(context, ChatyNotificationPolicy.ACTION_REPLY, replyNonce, mutable = true))
                    .addRemoteInput(RemoteInput.Builder(ChatyNotificationPolicy.KEY_REPLY_TEXT).setLabel("Reply").build())
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false).setAuthenticationRequired(true).build(),
            )
        }
        if (readNonce != null) {
            b.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notif_dismiss, "Dismiss", broadcast(context, ChatyNotificationPolicy.ACTION_DISMISS, readNonce))
                    .setShowsUserInterface(false).setAuthenticationRequired(true).build(),
            )
        }
        try { NotificationManagerCompat.from(context).notify(tag, nid, b.build()) } catch (_: SecurityException) {}
    }

    internal fun cancelMessage(context: Context, tag: String) {
        NotificationManagerCompat.from(context).cancel(tag, ChatyNotificationPolicy.messageNotificationId(tag))
    }

    /** Incoming call. The only action OPENS the app (accept happens in-app after auth/lock/permissions).
     *  [tag] is the lane's canonical c:<generation>:<callId> tag (null for the fixture/direct calls). */
    internal fun postIncomingCall(context: Context, style: ChatyNotificationStyle, callerName: String, isVideo: Boolean, declineNonce: String?, tag: String? = null) {
        if (!canPost(context)) return
        ensureChannels(context)
        val open = openApp(context, ChatyNotificationPolicy.INCOMING_CALL_ID)
        val b = NotificationCompat.Builder(context, ChatyNotificationPolicy.CH_INCOMING_CALL)
            .setSmallIcon(smallIcon(style, call = true)).setColor(style.accentArgb)
            .setContentTitle(callerName)
            .setContentText(if (isVideo) "Incoming video call" else "Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true).setOnlyAlertOnce(true).setTimeoutAfter(60_000)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
        if (declineNonce != null) b.addAction(R.drawable.ic_notif_hangup, "Decline", broadcast(context, ChatyNotificationPolicy.ACTION_DECLINE, declineNonce))
        if (open != null) b.addAction(R.drawable.ic_notif_call, "Open to answer", open)
        try { NotificationManagerCompat.from(context).notify(tag, ChatyNotificationPolicy.INCOMING_CALL_ID, b.build()) } catch (_: SecurityException) {}
    }

    internal fun cancelIncomingCall(context: Context, tag: String? = null) {
        NotificationManagerCompat.from(context).cancel(tag, ChatyNotificationPolicy.INCOMING_CALL_ID)
    }

    /** Mute/Unmute and Hang up on the quiet ongoing-call notification. Speaker/Earpiece toggles the route. */
    fun addOngoingCallActions(context: Context, builder: NotificationCompat.Builder, bridge: ChatyNotificationBridge?) {
        val mute = bridge?.registerCallAction(ChatyNotificationPolicy.ACTION_MUTE) ?: return
        val hang = bridge.registerCallAction(ChatyNotificationPolicy.ACTION_HANGUP) ?: return
        builder.addAction(
            if (mute.second.muted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic,
            if (mute.second.muted) "Unmute" else "Mute",
            broadcast(context, ChatyNotificationPolicy.ACTION_MUTE, mute.first),
        )
        val spk = bridge.registerCallAction(ChatyNotificationPolicy.ACTION_SPEAKER) ?: return
        builder.addAction(R.drawable.ic_notif_speaker, if (spk.second.onSpeaker) "Earpiece" else "Speaker", broadcast(context, ChatyNotificationPolicy.ACTION_SPEAKER, spk.first))
        builder.addAction(R.drawable.ic_notif_hangup, "Hang up", broadcast(context, ChatyNotificationPolicy.ACTION_HANGUP, hang.first))
        builder.setCategory(NotificationCompat.CATEGORY_CALL)
    }
}
