package com.example.chat

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat

/**
 * Enhanced, camera-cutout aware NotificationListenerService for Chaty Dynamic Island.
 * Manages notification events, unread badge aggregation, media sessions, and direct replies.
 */
class DynamicIslandNotificationListener : NotificationListenerService() {
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeMediaController: MediaController? = null

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            syncActiveMedia()
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            syncActiveMedia()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers?.firstOrNull())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(this, DynamicIslandNotificationListener::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsListener, component)
            val controllers = mediaSessionManager?.getActiveSessions(component)
            updateActiveController(controllers?.firstOrNull())
        } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
            activeMediaController?.unregisterCallback(mediaCallback)
            activeMediaController = null
        } catch (_: Exception) {}
    }

    private fun updateActiveController(controller: MediaController?) {
        if (activeMediaController?.sessionToken == controller?.sessionToken) return
        activeMediaController?.unregisterCallback(mediaCallback)
        activeMediaController = controller
        activeMediaController?.registerCallback(mediaCallback)
        syncActiveMedia()
    }

    private fun syncActiveMedia() {
        val controller = activeMediaController ?: run {
            IslandOverlay.clearMedia()
            return
        }
        val playbackState = controller.playbackState
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val metadata = controller.metadata

        if (!isPlaying && metadata == null) {
            IslandOverlay.clearMedia()
            return
        }

        val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Playing Audio"
        val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: controller.packageName

        var art: Bitmap? = metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (art == null) {
            art = metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
        }

        val duration = metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playbackState?.position ?: 0L

        IslandOverlay.updateMedia(
            applicationContext,
            title = title,
            artist = artist,
            art = art,
            isPlaying = isPlaying,
            duration = duration,
            position = position,
            controller = controller,
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !isEnabled(this)) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        val isCall = notification.category == Notification.CATEGORY_CALL ||
            notification.category == Notification.CATEGORY_MISSED_CALL ||
            (notification.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
                (notification.category == Notification.CATEGORY_CALL ||
                    title.contains("call", ignoreCase = true)))

        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val locked = keyguard.isKeyguardLocked
        val safeTitle = if (locked) "New notification" else if (title.isBlank()) appLabel(sbn.packageName) else title
        val safeBody = if (locked) "Unlock your phone to view details"
            else if (body.isBlank()) (if (isCall) "Call notification" else "New notification") else body
        val pendingIntent = notification.contentIntent

        // Resolve real app icon
        val appIcon = runCatching { packageManager.getApplicationIcon(sbn.packageName) }.getOrNull()

        // Extract actions & RemoteInput reply action if present
        var replyAction: IslandReplyAction? = null
        val actions = (notification.actions ?: emptyArray()).take(2).mapNotNull { action ->
            val actionIntent = action.actionIntent ?: return@mapNotNull null
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty() && replyAction == null) {
                val input = remoteInputs.first()
                replyAction = IslandReplyAction(
                    title = action.title?.toString()?.take(24) ?: "Reply",
                    pendingIntent = actionIntent,
                    remoteInput = input,
                )
            }
            IslandAction(action.title?.toString()?.take(24) ?: "Action", actionIntent)
        }

        Handler(Looper.getMainLooper()).post {
            IslandOverlay.onNotificationReceived(
                context = applicationContext,
                key = sbn.key ?: "${sbn.packageName}:${sbn.id}",
                packageName = sbn.packageName,
                title = safeTitle.take(90),
                body = safeBody.take(180),
                isCall = isCall,
                appIcon = appIcon,
                pendingIntent = pendingIntent,
                actions = actions,
                replyAction = replyAction,
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        Handler(Looper.getMainLooper()).post {
            IslandOverlay.onNotificationRemoved(sbn.key ?: "${sbn.packageName}:${sbn.id}")
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences("chaty_dynamic_island", Context.MODE_PRIVATE)
                .getBoolean("enabled", false)

        fun hasNotificationAccess(context: Context): Boolean {
            val expected = ComponentName(context, DynamicIslandNotificationListener::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}

internal data class IslandAction(val title: String, val pendingIntent: PendingIntent)

internal data class IslandReplyAction(
    val title: String,
    val pendingIntent: PendingIntent,
    val remoteInput: RemoteInput,
)

internal data class IslandNotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val body: String,
    val isCall: Boolean,
    val appIcon: Drawable?,
    val pendingIntent: PendingIntent?,
    val actions: List<IslandAction>,
    val replyAction: IslandReplyAction?,
    val timestamp: Long = System.currentTimeMillis(),
)

internal data class IslandMediaState(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val duration: Long,
    val position: Long,
    val controller: MediaController,
)

/** State machine for the dynamic island overlay */
internal enum class IslandVisualState {
    COMPACT,
    PREVIEW,
    EXPANDED,
    REPLY,
    MEDIA_EXPANDED,
}

internal object IslandOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var currentView: View? = null
    private var dismissRunnable: Runnable? = null

    // State & Content
    private var currentState = IslandVisualState.COMPACT
    private val activeNotifications = mutableListOf<IslandNotificationItem>()
    private var currentMedia: IslandMediaState? = null

    private var lastPostedKey = ""
    private var lastPostedTime = 0L

    // Animation dimensions
    private var currentAnimator: ValueAnimator? = null

    fun onNotificationReceived(
        context: Context,
        key: String,
        packageName: String,
        title: String,
        body: String,
        isCall: Boolean,
        appIcon: Drawable?,
        pendingIntent: PendingIntent?,
        actions: List<IslandAction>,
        replyAction: IslandReplyAction?,
    ) {
        if (!DynamicIslandNotificationListener.isEnabled(context)) return
        val appContext = context.applicationContext ?: context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) return

        val notifKey = "$packageName:$title:$body"
        val now = System.currentTimeMillis()
        if (notifKey == lastPostedKey && (now - lastPostedTime) < 1500L) {
            return
        }
        lastPostedKey = notifKey
        lastPostedTime = now

        // Remove existing item with same key and insert newest on top
        activeNotifications.removeAll { it.key == key }
        activeNotifications.add(0, IslandNotificationItem(
            key = key,
            packageName = packageName,
            title = title,
            body = body,
            isCall = isCall,
            appIcon = appIcon,
            pendingIntent = pendingIntent,
            actions = actions,
            replyAction = replyAction,
        ))

        // Keep at most 10 active notifications
        if (activeNotifications.size > 10) {
            activeNotifications.removeAt(activeNotifications.lastIndex)
        }

        currentState = if (isCall) IslandVisualState.PREVIEW else IslandVisualState.COMPACT
        ensureWindowManager(appContext)
        render(appContext, animate = true)
        scheduleDismiss(appContext, currentState)
    }

    fun onNotificationRemoved(key: String) {
        activeNotifications.removeAll { it.key == key }
        if (activeNotifications.isEmpty() && currentMedia == null) {
            remove()
        } else {
            currentView?.context?.let { render(it, animate = true) }
        }
    }

    fun updateMedia(
        context: Context,
        title: String,
        artist: String,
        art: Bitmap?,
        isPlaying: Boolean,
        duration: Long,
        position: Long,
        controller: MediaController,
    ) {
        if (!DynamicIslandNotificationListener.isEnabled(context)) return
        val appContext = context.applicationContext ?: context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) return

        currentMedia = IslandMediaState(
            title = title,
            artist = artist,
            art = art,
            isPlaying = isPlaying,
            duration = duration,
            position = position,
            controller = controller,
        )

        ensureWindowManager(appContext)
        if (currentView == null && activeNotifications.isEmpty()) {
            currentState = IslandVisualState.COMPACT
            render(appContext, animate = true)
        } else if (currentView != null) {
            render(appContext, animate = false)
        }
    }

    fun clearMedia() {
        currentMedia = null
        if (activeNotifications.isEmpty()) {
            remove()
        } else {
            currentView?.context?.let { render(it, animate = true) }
        }
    }

    private fun ensureWindowManager(context: Context) {
        if (windowManager == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        }
    }

    private fun scheduleDismiss(context: Context, state: IslandVisualState) {
        dismissRunnable?.let(main::removeCallbacks)
        val hasActiveCall = activeNotifications.any { it.isCall }
        val hasMediaPlaying = currentMedia?.isPlaying == true

        if (hasActiveCall || hasMediaPlaying || state == IslandVisualState.REPLY) {
            return // Ongoing activity or typing, do not auto dismiss
        }

        val timeout = when (state) {
            IslandVisualState.COMPACT -> 6000L
            IslandVisualState.PREVIEW -> 10000L
            IslandVisualState.EXPANDED -> 15000L
            IslandVisualState.MEDIA_EXPANDED -> 15000L
            IslandVisualState.REPLY -> 30000L
        }

        dismissRunnable = Runnable {
            if (activeNotifications.isNotEmpty() && state != IslandVisualState.COMPACT) {
                // Settle back to compact rather than disappearing abruptly
                currentState = IslandVisualState.COMPACT
                render(context, animate = true)
                scheduleDismiss(context, IslandVisualState.COMPACT)
            } else if (currentMedia?.isPlaying == true) {
                currentState = IslandVisualState.COMPACT
                render(context, animate = true)
            } else {
                remove()
            }
        }
        main.postDelayed(dismissRunnable!!, timeout)
    }

    private fun render(context: Context, animate: Boolean = true) {
        val wm = windowManager ?: return
        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels

        val primaryNotif = activeNotifications.firstOrNull()
        val media = currentMedia
        val unreadCount = activeNotifications.size

        // Calculate target dimensions
        // COMPACT: camera-aware compact pill (~126dp x 36dp)
        // EXPANDED / PREVIEW: ~360dp max
        val targetWidth = when (currentState) {
            IslandVisualState.COMPACT -> dp(context, 126).coerceAtLeast(dp(context, 100))
            IslandVisualState.PREVIEW -> (screenWidth - dp(context, 32)).coerceAtMost(dp(context, 360))
            IslandVisualState.EXPANDED -> (screenWidth - dp(context, 24)).coerceAtMost(dp(context, 360))
            IslandVisualState.MEDIA_EXPANDED -> (screenWidth - dp(context, 24)).coerceAtMost(dp(context, 360))
            IslandVisualState.REPLY -> (screenWidth - dp(context, 24)).coerceAtMost(dp(context, 360))
        }

        val targetHeight = when (currentState) {
            IslandVisualState.COMPACT -> dp(context, 36)
            IslandVisualState.PREVIEW -> dp(context, 78)
            IslandVisualState.EXPANDED -> dp(context, if (activeNotifications.size > 1) 180 else 130)
            IslandVisualState.MEDIA_EXPANDED -> dp(context, 142)
            IslandVisualState.REPLY -> dp(context, 130)
        }

        val cornerRadius = when (currentState) {
            IslandVisualState.COMPACT -> dp(context, 18).toFloat()
            else -> dp(context, 26).toFloat()
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                if (currentState == IslandVisualState.COMPACT) dp(context, 8) else dp(context, 14),
                if (currentState == IslandVisualState.COMPACT) dp(context, 4) else dp(context, 10),
                if (currentState == IslandVisualState.COMPACT) dp(context, 8) else dp(context, 14),
                if (currentState == IslandVisualState.COMPACT) dp(context, 4) else dp(context, 10),
            )
            background = GradientDrawable().apply {
                setColor(Color.rgb(8, 9, 12)) // Obsidian black
                this.cornerRadius = cornerRadius
                setStroke(dp(context, 1), Color.argb(40, 255, 255, 255))
            }
            elevation = dp(context, 12).toFloat()
        }

        // Build view content based on state
        when (currentState) {
            IslandVisualState.COMPACT -> buildCompactView(context, root, primaryNotif, media, unreadCount)
            IslandVisualState.PREVIEW -> buildPreviewView(context, root, primaryNotif)
            IslandVisualState.EXPANDED -> buildExpandedView(context, root, primaryNotif)
            IslandVisualState.MEDIA_EXPANDED -> buildMediaExpandedView(context, root, media)
            IslandVisualState.REPLY -> buildReplyView(context, root, primaryNotif)
        }

        // Window Params
        @Suppress("DEPRECATION")
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        if (currentState != IslandVisualState.REPLY) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        // Measure cutout camera offset
        val topOffset = getCutoutTopOffset(context)

        val params = WindowManager.LayoutParams(
            targetWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            flags,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = topOffset
        }

        // Gesture handling
        setupTouchInteractions(context, root)

        try {
            val old = currentView
            if (old != null && old.isAttachedToWindow) {
                if (animate) {
                    val initialWidth = old.width.takeIf { it > 0 } ?: targetWidth
                    currentAnimator?.cancel()
                    currentAnimator = ValueAnimator.ofInt(initialWidth, targetWidth).apply {
                        duration = 240
                        interpolator = OvershootInterpolator(0.7f)
                        addUpdateListener { animator ->
                            val currentW = animator.animatedValue as Int
                            params.width = currentW
                            runCatching { wm.updateViewLayout(old, params) }
                        }
                        start()
                    }
                }
                wm.removeViewImmediate(old)
            }
            currentView = root
            wm.addView(root, params)
        } catch (_: Exception) {
            currentView = null
        }
    }

    private fun buildCompactView(
        context: Context,
        root: LinearLayout,
        notif: IslandNotificationItem?,
        media: IslandMediaState?,
        count: Int,
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }

        // Left leading side: App icon or music art
        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (media != null && media.art != null) {
                setImageBitmap(media.art)
            } else if (notif?.appIcon != null) {
                setImageDrawable(notif.appIcon)
            } else {
                setImageResource(android.R.drawable.ic_dialog_info)
            }
        }
        val iconSize = dp(context, 24)
        val iconLp = LinearLayout.LayoutParams(iconSize, iconSize).apply {
            marginStart = dp(context, 4)
        }
        row.addView(iconView, iconLp)

        // Middle spacer: camera cutout area
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        row.addView(spacer)

        // Right trailing side: Unread badge or Music Visualizer
        if (media != null && media.isPlaying) {
            val visualizer = TextView(context).apply {
                text = "ılı"
                textSize = 13f
                setTextColor(Color.rgb(120, 210, 255))
                gravity = Gravity.CENTER
            }
            row.addView(visualizer, LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)).apply {
                marginEnd = dp(context, 4)
            })
        } else {
            val badge = TextView(context).apply {
                text = if (count > 1) "$count" else "●"
                textSize = if (count > 1) 11f else 9f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (notif?.isCall == true) Color.rgb(74, 222, 128) else Color.rgb(120, 190, 255))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (count > 1) Color.argb(45, 120, 190, 255) else Color.TRANSPARENT)
                    cornerRadius = dp(context, 10).toFloat()
                }
                setPadding(dp(context, 4), dp(context, 1), dp(context, 4), dp(context, 1))
            }
            row.addView(badge, LinearLayout.LayoutParams(-2, -2).apply {
                marginEnd = dp(context, 4)
            })
        }

        root.addView(row)
    }

    private fun buildPreviewView(context: Context, root: LinearLayout, notif: IslandNotificationItem?) {
        if (notif == null) return
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (notif.appIcon != null) setImageDrawable(notif.appIcon)
            else setImageResource(android.R.drawable.ic_dialog_info)
        }
        row.addView(iconView, LinearLayout.LayoutParams(dp(context, 32), dp(context, 32)))

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 10), 0, dp(context, 8), 0)
        }
        textCol.addView(TextView(context).apply {
            text = notif.title
            textSize = 13f
            maxLines = 1
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        textCol.addView(TextView(context).apply {
            text = notif.body
            textSize = 12f
            maxLines = 1
            setTextColor(Color.rgb(190, 195, 205))
        })
        row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))

        val hint = TextView(context).apply {
            text = if (notif.isCall) "Call" else "now"
            textSize = 10f
            setTextColor(if (notif.isCall) Color.rgb(74, 222, 128) else Color.GRAY)
        }
        row.addView(hint)

        root.addView(row)
    }

    private fun buildExpandedView(context: Context, root: LinearLayout, notif: IslandNotificationItem?) {
        if (notif == null) return
        buildPreviewView(context, root, notif)

        // Actions row (Reply / Call back / Dismiss)
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(context, 10), 0, 0)
        }

        if (notif.replyAction != null) {
            val replyBtn = Button(context).apply {
                text = "Reply"
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(32, 40, 52))
                    cornerRadius = dp(context, 14).toFloat()
                }
                setOnClickListener {
                    currentState = IslandVisualState.REPLY
                    render(context, animate = true)
                }
            }
            actionsRow.addView(replyBtn, LinearLayout.LayoutParams(-2, dp(context, 34)).apply {
                marginEnd = dp(context, 6)
            })
        }

        notif.actions.forEach { action ->
            val btn = Button(context).apply {
                text = action.title
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(28, 30, 36))
                    cornerRadius = dp(context, 14).toFloat()
                }
                setOnClickListener {
                    runCatching { action.pendingIntent.send() }
                    remove()
                }
            }
            actionsRow.addView(btn, LinearLayout.LayoutParams(-2, dp(context, 34)).apply {
                marginStart = dp(context, 4)
            })
        }

        root.addView(actionsRow)
    }

    private fun buildReplyView(context: Context, root: LinearLayout, notif: IslandNotificationItem?) {
        if (notif?.replyAction == null) return
        val titleView = TextView(context).apply {
            text = "Reply to ${notif.title}"
            textSize = 12f
            setTextColor(Color.rgb(160, 190, 240))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(context, 6))
        }
        root.addView(titleView)

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val editText = EditText(context).apply {
            hint = "Type message..."
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            textSize = 13f
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 22, 28))
                cornerRadius = dp(context, 12).toFloat()
                setStroke(dp(context, 1), Color.argb(50, 255, 255, 255))
            }
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
        }
        inputRow.addView(editText, LinearLayout.LayoutParams(0, dp(context, 38), 1f))

        val sendBtn = Button(context).apply {
            text = "Send"
            textSize = 11f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(37, 99, 235)) // Blue primary
                cornerRadius = dp(context, 12).toFloat()
            }
            setOnClickListener {
                val replyText = editText.text.toString().trim()
                if (replyText.isNotEmpty()) {
                    sendDirectReply(context, notif.replyAction, replyText)
                }
                currentState = IslandVisualState.COMPACT
                render(context, animate = true)
                scheduleDismiss(context, IslandVisualState.COMPACT)
            }
        }
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(-2, dp(context, 38)).apply {
            marginStart = dp(context, 8)
        })

        root.addView(inputRow)

        // Request keyboard focus
        main.postDelayed({
            editText.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun sendDirectReply(context: Context, replyAction: IslandReplyAction, replyText: String) {
        try {
            val replyBundle = Bundle().apply {
                putCharSequence(replyAction.remoteInput.resultKey, replyText)
            }
            val intent = Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            RemoteInput.addResultsToIntent(arrayOf(replyAction.remoteInput), intent, replyBundle)
            replyAction.pendingIntent.send(context, 0, intent)
        } catch (_: Exception) {}
    }

    private fun buildMediaExpandedView(context: Context, root: LinearLayout, media: IslandMediaState?) {
        if (media == null) return
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val artView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (media.art != null) setImageBitmap(media.art)
            else setImageResource(android.R.drawable.ic_media_play)
        }
        topRow.addView(artView, LinearLayout.LayoutParams(dp(context, 46), dp(context, 46)))

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 10), 0, dp(context, 8), 0)
        }
        textCol.addView(TextView(context).apply {
            text = media.title
            textSize = 13.5f
            maxLines = 1
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        textCol.addView(TextView(context).apply {
            text = media.artist
            textSize = 11.5f
            maxLines = 1
            setTextColor(Color.LTGRAY)
        })
        topRow.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(topRow)

        // Transport controls (Prev / Play-Pause / Next)
        val controlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 8), 0, 0)
        }

        val prevBtn = TextView(context).apply {
            text = "⏮"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener {
                runCatching { media.controller.transportControls.skipToPrevious() }
            }
        }
        controlsRow.addView(prevBtn, LinearLayout.LayoutParams(dp(context, 44), dp(context, 36)))

        val playBtn = TextView(context).apply {
            text = if (media.isPlaying) "⏸" else "▶"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener {
                if (media.isPlaying) {
                    runCatching { media.controller.transportControls.pause() }
                } else {
                    runCatching { media.controller.transportControls.play() }
                }
            }
        }
        controlsRow.addView(playBtn, LinearLayout.LayoutParams(dp(context, 54), dp(context, 36)))

        val nextBtn = TextView(context).apply {
            text = "⏭"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener {
                runCatching { media.controller.transportControls.skipToNext() }
            }
        }
        controlsRow.addView(nextBtn, LinearLayout.LayoutParams(dp(context, 44), dp(context, 36)))

        root.addView(controlsRow)
    }

    private fun setupTouchInteractions(context: Context, root: View) {
        root.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = event.rawX - downX
                        val deltaY = event.rawY - downY

                        // Swipe Down -> Expand to full view
                        if (deltaY > dp(context, 20) && currentState == IslandVisualState.COMPACT) {
                            currentState = if (currentMedia != null && activeNotifications.isEmpty())
                                IslandVisualState.MEDIA_EXPANDED else IslandVisualState.EXPANDED
                            render(context, animate = true)
                            scheduleDismiss(context, currentState)
                            return true
                        }

                        // Swipe Up -> Collapse to compact
                        if (deltaY < -dp(context, 20) && currentState != IslandVisualState.COMPACT) {
                            currentState = IslandVisualState.COMPACT
                            render(context, animate = true)
                            scheduleDismiss(context, currentState)
                            return true
                        }

                        // Swipe Horizontal -> Dismiss current notification
                        if (Math.abs(deltaX) > dp(context, 45) && Math.abs(deltaY) < dp(context, 35)) {
                            if (activeNotifications.isNotEmpty()) {
                                activeNotifications.removeAt(0)
                            }
                            if (activeNotifications.isEmpty() && currentMedia == null) {
                                remove()
                            } else {
                                currentState = IslandVisualState.COMPACT
                                render(context, animate = true)
                            }
                            return true
                        }

                        // Simple Tap
                        if (Math.abs(deltaX) < dp(context, 10) && Math.abs(deltaY) < dp(context, 10)) {
                            when (currentState) {
                                IslandVisualState.COMPACT -> {
                                    currentState = if (currentMedia != null && activeNotifications.isEmpty()) {
                                        IslandVisualState.MEDIA_EXPANDED
                                    } else {
                                        IslandVisualState.PREVIEW
                                    }
                                    render(context, animate = true)
                                    scheduleDismiss(context, currentState)
                                }
                                IslandVisualState.PREVIEW -> {
                                    val notif = activeNotifications.firstOrNull()
                                    runCatching { notif?.pendingIntent?.send() }
                                    remove()
                                }
                                IslandVisualState.EXPANDED,
                                IslandVisualState.MEDIA_EXPANDED -> {
                                    currentState = IslandVisualState.COMPACT
                                    render(context, animate = true)
                                    scheduleDismiss(context, currentState)
                                }
                                IslandVisualState.REPLY -> {}
                            }
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    private fun getCutoutTopOffset(context: Context): Int {
        val statusBar = statusBarHeight(context)
        // Camera cutout insets: nestle island symmetrically inside top edge
        return (statusBar * 0.18f).toInt().coerceAtLeast(dp(context, 4))
    }

    fun preview(context: Context) {
        val appContext = context.applicationContext ?: context
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            main.postDelayed({ runCatching { tone.release() } }, 250)
        } catch (_: Exception) {}

        onNotificationReceived(
            context = appContext,
            key = "preview_test_key",
            packageName = appContext.packageName,
            title = "Chaty Dynamic Island",
            body = "Compact pill active · Tap or swipe down to expand",
            isCall = false,
            appIcon = runCatching { appContext.packageManager.getApplicationIcon(appContext.packageName) }.getOrNull(),
            pendingIntent = null,
            actions = listOf(IslandAction("Dismiss", PendingIntent.getBroadcast(
                appContext, 0, Intent("chaty.island.DISMISS"),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            ))),
            replyAction = null,
        )
    }

    fun remove() {
        dismissRunnable?.let(main::removeCallbacks)
        val view = currentView
        currentView = null
        if (view != null) {
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(view)
                } else {
                    windowManager?.removeView(view)
                }
            }
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(context, 24)
    }
}
