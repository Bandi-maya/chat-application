package com.example.chat.island

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import android.util.LruCache

/**
 * Filter, classify, and sanitize incoming notifications (fixing K-8, K-9, K-10, K-13).
 */
class NotificationIngest(private val context: Context) {
    private val iconCache = LruCache<String, Drawable>(30)

    fun ingest(sbn: StatusBarNotification, isLocked: Boolean): MessageActivity? {
        val notification = sbn.notification ?: return null
        val flags = notification.flags

        // Drop summaries and ongoing services unless they are explicit communication
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return null

        // Skip media-style notifications (MediaSessionTracker handles them in their own lane, fixing K-8)
        val template = notification.extras.getString(Notification.EXTRA_TEMPLATE)
        if (template?.contains("MediaStyle", ignoreCase = true) == true) {
            return null
        }

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()

        if (title.isBlank() && body.isBlank()) return null

        val safeTitle = if (isLocked) "New message" else if (title.isBlank()) getAppLabel(sbn.packageName) else title
        val safeBody = if (isLocked) "Unlock device to view message" else body

        val appIcon = getAppIcon(sbn.packageName)

        // RemoteInput action extraction
        var replyAction: IslandReplyActionItem? = null
        val actions = (notification.actions ?: emptyArray()).take(3).mapNotNull { action ->
            val intent = action.actionIntent ?: return@mapNotNull null
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty() && replyAction == null) {
                replyAction = IslandReplyActionItem(
                    title = action.title?.toString()?.take(24) ?: "Reply",
                    pendingIntent = intent,
                    remoteInput = remoteInputs.first(),
                )
            }
            IslandActionItem(action.title?.toString()?.take(24) ?: "Action", intent)
        }

        return MessageActivity(
            key = sbn.key ?: "${sbn.packageName}:${sbn.id}",
            packageName = sbn.packageName,
            appIcon = appIcon,
            title = safeTitle.take(90),
            body = safeBody.take(180),
            contentIntent = notification.contentIntent,
            actions = actions,
            replyAction = replyAction,
            privacy = if (isLocked) PrivacyLevel.SENSITIVE_CONCEALED else PrivacyLevel.PUBLIC,
        )
    }

    private fun getAppIcon(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        val icon = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        if (icon != null) {
            iconCache.put(packageName, icon)
        }
        return icon
    }

    private fun getAppLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
