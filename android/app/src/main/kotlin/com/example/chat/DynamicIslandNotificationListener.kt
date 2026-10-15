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
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat

/**
 * Camera-cutout aware NotificationListenerService for Chaty Dynamic Island.
 * Manages persistent notification stack aggregation, deduplication, active notifications reconciliation,
 * media sessions, gestures (tap to toggle/expand, vertical scroll, horizontal swipe-to-dismiss),
 * and direct inline replies via RemoteInput.
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

        // Reconcile active notifications on connect
        try {
            val active = activeNotifications ?: emptyArray()
            for (sbn in active) {
                processStatusBarNotification(sbn, isInitialReconcile = true)
            }
        } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
            activeMediaController?.unregisterCallback(mediaCallback)
            activeMediaController = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestRebind(ComponentName(this, DynamicIslandNotificationListener::class.java))
            }
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
        processStatusBarNotification(sbn, isInitialReconcile = false)
    }

    private fun processStatusBarNotification(sbn: StatusBarNotification, isInitialReconcile: Boolean) {
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

        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = keyguard?.isKeyguardLocked ?: false
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

        val notificationKey = sbn.key ?: "${sbn.packageName}:${sbn.id}"
        Handler(Looper.getMainLooper()).post {
            IslandOverlay.onNotificationReceived(
                context = applicationContext,
                key = notificationKey,
                packageName = sbn.packageName,
                title = safeTitle.take(90),
                body = safeBody.take(180),
                isCall = isCall,
                appIcon = appIcon,
                pendingIntent = pendingIntent,
                actions = actions,
                replyAction = replyAction,
                animateArrival = !isInitialReconcile,
            )
            MainActivity.dispatchDynamicIslandNotification(mapOf(
                "key" to notificationKey,
                "packageName" to sbn.packageName,
                "title" to safeTitle.take(90),
                "body" to safeBody.take(180),
                "isCall" to isCall,
            ))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notificationKey = sbn.key ?: "${sbn.packageName}:${sbn.id}"
        Handler(Looper.getMainLooper()).post {
            IslandOverlay.onNotificationRemoved(notificationKey)
            MainActivity.dispatchDynamicIslandRemoved(notificationKey)
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
    IDLE,
    COMPACT,
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

    // Reply focus target
    private var activeReplyItem: IslandNotificationItem? = null

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
        animateArrival: Boolean = true,
    ) {
        if (!DynamicIslandNotificationListener.isEnabled(context)) return
        val appContext = context.applicationContext ?: context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) return

        // Stable identity deduplication & stack insertion:
        // If an item with the same stable key exists, update in-place or move to top if new content
        val existingIndex = activeNotifications.indexOfFirst { it.key == key }
        val newItem = IslandNotificationItem(
            key = key,
            packageName = packageName,
            title = title,
            body = body,
            isCall = isCall,
            appIcon = appIcon,
            pendingIntent = pendingIntent,
            actions = actions,
            replyAction = replyAction,
        )

        if (existingIndex >= 0) {
            val existing = activeNotifications[existingIndex]
            val isContentChanged = existing.title != title || existing.body != body
            if (isContentChanged) {
                activeNotifications.removeAt(existingIndex)
                activeNotifications.add(0, newItem)
            } else {
                // Update metadata (icons/actions/intents) in-place without resetting scroll or stack order
                activeNotifications[existingIndex] = newItem
            }
        } else {
            // New entry: push to top of stack
            activeNotifications.add(0, newItem)
        }

        // Keep at most 20 active notifications in the stack
        while (activeNotifications.size > 20) {
            activeNotifications.removeAt(activeNotifications.lastIndex)
        }

        // If user is actively typing a reply, don't kick them out of reply state
        if (currentState != IslandVisualState.REPLY) {
            currentState = if (isCall) IslandVisualState.EXPANDED else IslandVisualState.COMPACT
        }

        ensureWindowManager(appContext)
        render(appContext, animate = animateArrival)
        scheduleDismiss(appContext, currentState)
    }

    fun onNotificationRemoved(key: String) {
        activeNotifications.removeAll { it.key == key }
        if (activeReplyItem?.key == key) {
            activeReplyItem = null
            if (currentState == IslandVisualState.REPLY) {
                currentState = IslandVisualState.COMPACT
            }
        }

        if (activeNotifications.isEmpty() && currentMedia == null) {
            currentState = IslandVisualState.IDLE
            currentView?.context?.let { render(it, animate = true) }
        } else {
            currentView?.context?.let { render(it, animate = true) }
        }
    }

    fun clearAllNotifications() {
        activeNotifications.clear()
        activeReplyItem = null
        if (currentMedia == null) {
            currentState = IslandVisualState.IDLE
            currentView?.context?.let { render(it, animate = true) }
        } else {
            currentState = IslandVisualState.COMPACT
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
            currentState = IslandVisualState.IDLE
            currentView?.context?.let { render(it, animate = true) }
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
            IslandVisualState.IDLE -> 0L
            IslandVisualState.COMPACT -> 7000L
            IslandVisualState.EXPANDED -> 16000L
            IslandVisualState.MEDIA_EXPANDED -> 16000L
            IslandVisualState.REPLY -> 35000L
        }

        if (timeout == 0L) return

        dismissRunnable = Runnable {
            if (activeNotifications.isNotEmpty() && state != IslandVisualState.COMPACT) {
                // Settle back to compact pill rather than disappearing abruptly
                currentState = IslandVisualState.COMPACT
                render(context, animate = true)
                scheduleDismiss(context, IslandVisualState.COMPACT)
            } else if (currentMedia?.isPlaying == true) {
                currentState = IslandVisualState.COMPACT
                render(context, animate = true)
            } else {
                // When notifications clear, blend with camera cutout in adaptive idle state
                currentState = IslandVisualState.IDLE
                render(context, animate = true)
            }
        }
        main.postDelayed(dismissRunnable!!, timeout)
    }

    private fun render(context: Context, animate: Boolean = true) {
        val wm = windowManager ?: return
        val screenWidth = context.resources.displayMetrics.widthPixels

        val primaryNotif = activeNotifications.firstOrNull()
        val media = currentMedia
        val unreadCount = activeNotifications.size

        // Calculate target dimensions
        val compactWidth = (if (unreadCount > 1) dp(context, 142) else dp(context, 126)).coerceAtLeast(dp(context, 100))
        val targetWidth = when (currentState) {
            IslandVisualState.IDLE -> dp(context, 38).coerceAtLeast(getCutoutWidth(context) + dp(context, 8))
            IslandVisualState.COMPACT -> compactWidth
            IslandVisualState.EXPANDED -> (screenWidth - dp(context, 20)).coerceAtMost(dp(context, 370))
            IslandVisualState.MEDIA_EXPANDED -> (screenWidth - dp(context, 20)).coerceAtMost(dp(context, 370))
            IslandVisualState.REPLY -> (screenWidth - dp(context, 20)).coerceAtMost(dp(context, 370))
        }

        val targetHeight = when (currentState) {
            IslandVisualState.IDLE -> dp(context, 32)
            IslandVisualState.COMPACT -> dp(context, 36)
            IslandVisualState.EXPANDED -> {
                when {
                    unreadCount > 2 -> dp(context, 260)
                    unreadCount == 2 -> dp(context, 190)
                    else -> dp(context, 134)
                }
            }
            IslandVisualState.MEDIA_EXPANDED -> dp(context, 146)
            IslandVisualState.REPLY -> dp(context, 136)
        }

        val cornerRadius = when (currentState) {
            IslandVisualState.IDLE -> dp(context, 16).toFloat()
            IslandVisualState.COMPACT -> dp(context, 18).toFloat()
            else -> dp(context, 24).toFloat()
        }

        // Root container with layered glassmorphism border & shadow adaptive to camera bezel
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val hPad = when (currentState) {
                IslandVisualState.IDLE -> dp(context, 4)
                IslandVisualState.COMPACT -> dp(context, 6)
                else -> dp(context, 12)
            }
            val vPad = when (currentState) {
                IslandVisualState.IDLE -> dp(context, 2)
                IslandVisualState.COMPACT -> dp(context, 3)
                else -> dp(context, 8)
            }
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                // Adaptive deep obsidian blending seamlessly with the camera notch bezel
                setColor(if (currentState == IslandVisualState.IDLE) Color.BLACK else Color.rgb(10, 11, 15))
                this.cornerRadius = cornerRadius
                setStroke(
                    dp(context, 1),
                    if (currentState == IslandVisualState.IDLE) Color.argb(20, 255, 255, 255) else Color.argb(45, 255, 255, 255)
                )
            }
            // Elevate to maximum depth to stay above system indicators & status elements
            elevation = dp(context, 24).toFloat()
        }

        // Build view content based on state
        when (currentState) {
            IslandVisualState.IDLE -> buildIdleView(context, root)
            IslandVisualState.COMPACT -> buildCompactView(context, root, primaryNotif, media, unreadCount)
            IslandVisualState.EXPANDED -> buildExpandedStackView(context, root, targetHeight)
            IslandVisualState.MEDIA_EXPANDED -> buildMediaExpandedView(context, root, media)
            IslandVisualState.REPLY -> buildReplyView(context, root, activeReplyItem ?: primaryNotif)
        }

        // Window Params - peak z-index priority over battery/time/status bar
        @Suppress("DEPRECATION")
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        if (currentState != IslandVisualState.REPLY) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        setupTouchInteractions(context, root)

        try {
            val old = currentView
            if (old != null && old.isAttachedToWindow) {
                if (animate) {
                    val initialWidth = old.width.takeIf { it > 0 } ?: targetWidth
                    currentAnimator?.cancel()
                    currentAnimator = ValueAnimator.ofInt(initialWidth, targetWidth).apply {
                        duration = 220
                        interpolator = OvershootInterpolator(0.6f)
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

    private fun buildIdleView(context: Context, root: LinearLayout) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }

        // Camera dot indicator blending into hole-punch notch
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(70, 255, 255, 255))
                shape = GradientDrawable.OVAL
            }
            layoutParams = LinearLayout.LayoutParams(dp(context, 7), dp(context, 7))
        }
        row.addView(dot)
        root.addView(row)
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

        // Left leading side: Stack layer edge hint + App Icon or Media Art
        val leftContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, if (count > 1) 28 else 24), dp(context, 24)).apply {
                marginStart = dp(context, 2)
            }
        }

        // If multiple notifications exist, render subtle stacked card edge behind icon
        if (count > 1) {
            val stackUnderlay = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.argb(80, 50, 60, 80))
                    cornerRadius = dp(context, 6).toFloat()
                }
                layoutParams = FrameLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                }
            }
            leftContainer.addView(stackUnderlay)
        }

        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (media != null && media.art != null) {
                setImageBitmap(media.art)
            } else if (notif?.appIcon != null) {
                setImageDrawable(notif.appIcon)
            } else {
                setImageResource(android.R.drawable.ic_dialog_info)
            }
            layoutParams = FrameLayout.LayoutParams(dp(context, 22), dp(context, 22)).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
            }
        }
        leftContainer.addView(iconView)
        row.addView(leftContainer)

        // Middle spacer: camera cutout area
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        row.addView(spacer)

        // Right trailing side: Unread badge count or Music Visualizer
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
                setTextColor(if (notif?.isCall == true) Color.rgb(74, 222, 128) else Color.rgb(130, 195, 255))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (count > 1) Color.argb(55, 37, 99, 235) else Color.TRANSPARENT)
                    cornerRadius = dp(context, 10).toFloat()
                }
                setPadding(dp(context, 5), dp(context, 1), dp(context, 5), dp(context, 1))
            }
            row.addView(badge, LinearLayout.LayoutParams(-2, -2).apply {
                marginEnd = dp(context, 4)
            })
        }

        root.addView(row)
    }

    /**
     * Builds the persistent multi-notification stacked scrollable view
     */
    private fun buildExpandedStackView(context: Context, root: LinearLayout, targetHeight: Int) {
        if (activeNotifications.isEmpty()) return

        // Header bar with unread stack count and Clear All action
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4), 0, dp(context, 4), dp(context, 6))
        }

        val headerTitle = TextView(context).apply {
            text = "Notifications (${activeNotifications.size})"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(180, 190, 210))
        }
        header.addView(headerTitle, LinearLayout.LayoutParams(0, -2, 1f))

        val clearAllBtn = TextView(context).apply {
            text = "Clear All"
            textSize = 11f
            setTextColor(Color.rgb(140, 150, 170))
            setPadding(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 2))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 255, 255, 255))
                cornerRadius = dp(context, 8).toFloat()
            }
            setOnClickListener {
                clearAllNotifications()
            }
        }
        header.addView(clearAllBtn)
        root.addView(header)

        // Scrollable list container for stacked cards
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (targetHeight - dp(context, 48)).coerceAtLeast(dp(context, 80)),
            )
        }

        val itemsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Render each notification card in stack order
        for ((index, item) in activeNotifications.withIndex()) {
            val card = buildNotificationCardView(context, item, isTop = index == 0)
            itemsContainer.addView(card)

            if (index < activeNotifications.size - 1) {
                val divider = View(context).apply {
                    setBackgroundColor(Color.argb(20, 255, 255, 255))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(context, 1),
                    ).apply {
                        topMargin = dp(context, 6)
                        bottomMargin = dp(context, 6)
                    }
                }
                itemsContainer.addView(divider)
            }
        }

        scrollView.addView(itemsContainer)
        root.addView(scrollView)
    }

    private fun buildNotificationCardView(
        context: Context,
        item: IslandNotificationItem,
        isTop: Boolean,
    ): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6))
            background = GradientDrawable().apply {
                setColor(if (isTop) Color.argb(40, 30, 36, 48) else Color.argb(20, 20, 24, 32))
                cornerRadius = dp(context, 14).toFloat()
            }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (item.appIcon != null) setImageDrawable(item.appIcon)
            else setImageResource(android.R.drawable.ic_dialog_info)
        }
        topRow.addView(iconView, LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)))

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), 0, dp(context, 6), 0)
        }
        textCol.addView(TextView(context).apply {
            text = item.title
            textSize = 12.5f
            maxLines = 1
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        textCol.addView(TextView(context).apply {
            text = item.body
            textSize = 11.5f
            maxLines = 2
            setTextColor(Color.rgb(195, 200, 210))
        })
        topRow.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))

        // Open source app on tap
        topRow.setOnClickListener {
            runCatching { item.pendingIntent?.send() }
            onNotificationRemoved(item.key)
        }
        card.addView(topRow)

        // Actions row (Reply button + notification native actions)
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(context, 6), 0, 0)
        }

        if (item.replyAction != null) {
            val replyBtn = Button(context).apply {
                text = "Reply"
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(37, 99, 235)) // Chaty Accent Blue
                    cornerRadius = dp(context, 12).toFloat()
                }
                setOnClickListener {
                    activeReplyItem = item
                    currentState = IslandVisualState.REPLY
                    render(context, animate = true)
                }
            }
            actionsRow.addView(replyBtn, LinearLayout.LayoutParams(-2, dp(context, 32)).apply {
                marginEnd = dp(context, 6)
            })
        }

        for (action in item.actions) {
            val btn = Button(context).apply {
                text = action.title
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(28, 32, 40))
                    cornerRadius = dp(context, 12).toFloat()
                }
                setOnClickListener {
                    runCatching { action.pendingIntent.send() }
                    onNotificationRemoved(item.key)
                }
            }
            actionsRow.addView(btn, LinearLayout.LayoutParams(-2, dp(context, 32)).apply {
                marginStart = dp(context, 4)
            })
        }

        // Dismiss single notification button
        val dismissBtn = TextView(context).apply {
            text = "✕"
            textSize = 12f
            setTextColor(Color.rgb(150, 155, 165))
            gravity = Gravity.CENTER
            setPadding(dp(context, 6), dp(context, 4), dp(context, 6), dp(context, 4))
            setOnClickListener {
                onNotificationRemoved(item.key)
            }
        }
        actionsRow.addView(dismissBtn, LinearLayout.LayoutParams(dp(context, 32), dp(context, 32)).apply {
            marginStart = dp(context, 4)
        })

        card.addView(actionsRow)
        return card
    }

    private fun buildReplyView(context: Context, root: LinearLayout, notif: IslandNotificationItem?) {
        val targetItem = notif ?: activeNotifications.firstOrNull()
        if (targetItem?.replyAction == null) {
            currentState = IslandVisualState.EXPANDED
            render(context, animate = true)
            return
        }

        val titleView = TextView(context).apply {
            text = "Reply to ${targetItem.title}"
            textSize = 12.5f
            setTextColor(Color.rgb(160, 200, 255))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(context, 6))
        }
        root.addView(titleView)

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val editText = EditText(context).apply {
            hint = "Type reply..."
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            textSize = 13f
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 24, 32))
                cornerRadius = dp(context, 12).toFloat()
                setStroke(dp(context, 1), Color.argb(60, 255, 255, 255))
            }
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
        }
        inputRow.addView(editText, LinearLayout.LayoutParams(0, dp(context, 38), 1f))

        val sendBtn = Button(context).apply {
            text = "Send"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(37, 99, 235))
                cornerRadius = dp(context, 12).toFloat()
            }
            setOnClickListener {
                val replyText = editText.text.toString().trim()
                if (replyText.isNotEmpty()) {
                    sendDirectReply(context, targetItem.replyAction, replyText)
                }
                activeReplyItem = null
                currentState = IslandVisualState.COMPACT
                render(context, animate = true)
                scheduleDismiss(context, IslandVisualState.COMPACT)
            }
        }
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(-2, dp(context, 38)).apply {
            marginStart = dp(context, 8)
        })

        root.addView(inputRow)

        // Request keyboard focus smoothly
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

        // Transport controls
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

                        // Swipe Down -> Expand to notification stack view
                        if (deltaY > dp(context, 18) && currentState == IslandVisualState.COMPACT) {
                            currentState = if (currentMedia != null && activeNotifications.isEmpty())
                                IslandVisualState.MEDIA_EXPANDED else IslandVisualState.EXPANDED
                            render(context, animate = true)
                            scheduleDismiss(context, currentState)
                            return true
                        }

                        // Swipe Up -> Collapse to compact pill (never destroys stack)
                        if (deltaY < -dp(context, 18) && currentState != IslandVisualState.COMPACT) {
                            currentState = IslandVisualState.COMPACT
                            render(context, animate = true)
                            scheduleDismiss(context, currentState)
                            return true
                        }

                        // Swipe Horizontal -> Dismiss current top notification
                        if (Math.abs(deltaX) > dp(context, 40) && Math.abs(deltaY) < dp(context, 35)) {
                            if (activeNotifications.isNotEmpty()) {
                                activeNotifications.removeAt(0)
                            }
                            if (activeNotifications.isEmpty() && currentMedia == null) {
                                remove()
                            } else {
                                render(context, animate = true)
                            }
                            return true
                        }

                        // Simple Tap: toggle between Idle / Compact <-> Expanded stack
                        if (Math.abs(deltaX) < dp(context, 12) && Math.abs(deltaY) < dp(context, 12)) {
                            when (currentState) {
                                IslandVisualState.IDLE -> {
                                    currentState = IslandVisualState.COMPACT
                                    render(context, animate = true)
                                    scheduleDismiss(context, currentState)
                                }
                                IslandVisualState.COMPACT -> {
                                    currentState = if (currentMedia != null && activeNotifications.isEmpty()) {
                                        IslandVisualState.MEDIA_EXPANDED
                                    } else {
                                        IslandVisualState.EXPANDED
                                    }
                                    render(context, animate = true)
                                    scheduleDismiss(context, currentState)
                                }
                                IslandVisualState.EXPANDED,
                                IslandVisualState.MEDIA_EXPANDED -> {
                                    // Tap collapses back to compact pill without wiping notifications
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

    private fun getCutoutWidth(context: Context): Int {
        val wm = windowManager ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val metrics = wm.currentWindowMetrics
            val cutout: android.view.DisplayCutout? = metrics.windowInsets.displayCutout
            val rect: Rect? = cutout?.boundingRectTop ?: cutout?.boundingRects?.firstOrNull()
            if (rect != null && !rect.isEmpty) {
                return rect.width()
            }
        }
        return dp(context, 32)
    }

    private fun getCutoutTopOffset(context: Context): Int {
        val wm = windowManager ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val metrics = wm.currentWindowMetrics
            val cutout: android.view.DisplayCutout? = metrics.windowInsets.displayCutout
            val rect: Rect? = cutout?.boundingRectTop ?: cutout?.boundingRects?.firstOrNull { it.top < dp(context, 16) }
            if (rect != null && !rect.isEmpty) {
                val cutoutCenterY = rect.centerY()
                val pillHalfHeight = dp(context, 36) / 2
                return (cutoutCenterY - pillHalfHeight).coerceAtLeast(dp(context, 2))
            }
        }
        val statusBar = statusBarHeight(context)
        return ((statusBar - dp(context, 36)) / 2).coerceAtLeast(dp(context, 4))
    }

    fun preview(context: Context) {
        val appContext = context.applicationContext ?: context
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            main.postDelayed({ runCatching { tone.release() } }, 250)
        } catch (_: Exception) {}

        // Add 2 realistic sample notifications to demonstrate persistent stack behavior
        onNotificationReceived(
            context = appContext,
            key = "preview_test_key_1",
            packageName = appContext.packageName,
            title = "Chaty Dynamic Island",
            body = "Stack view ready · Tap to expand all items",
            isCall = false,
            appIcon = runCatching { appContext.packageManager.getApplicationIcon(appContext.packageName) }.getOrNull(),
            pendingIntent = null,
            actions = emptyList(),
            replyAction = null,
            animateArrival = true,
        )

        onNotificationReceived(
            context = appContext,
            key = "preview_test_key_2",
            packageName = appContext.packageName,
            title = "Alice Morgan",
            body = "Are we still meeting at 5 PM for the design review?",
            isCall = false,
            appIcon = runCatching { appContext.packageManager.getApplicationIcon(appContext.packageName) }.getOrNull(),
            pendingIntent = null,
            actions = emptyList(),
            replyAction = null,
            animateArrival = true,
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
