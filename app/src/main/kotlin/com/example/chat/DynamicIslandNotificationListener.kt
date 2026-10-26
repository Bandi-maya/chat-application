package com.example.chat

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
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
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat

/**
 * Camera-cutout aware NotificationListenerService for Chaty Dynamic Island.
 * Manages persistent notification stack aggregation, deduplication, active notifications reconciliation,
 * media sessions, gestures (tap to toggle/expand, vertical scroll, horizontal swipe-to-dismiss),
 * and direct inline replies via RemoteInput.
 */
class DynamicIslandNotificationListener : NotificationListenerService() {
    private var screenReceiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                // Remove before the device sleeps; never retain previously
                // unlocked notification/media contents across a lock cycle.
                IslandOverlay.suppressForPrivacy()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
    }

    private fun isPrivateScreen(): Boolean = IslandOverlay.isScreenPrivate(this)

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
        if (!isEnabled(this)) return
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
        IslandOverlay.remove()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
            activeMediaController?.unregisterCallback(mediaCallback)
            activeMediaController = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isEnabled(this)) {
                requestRebind(ComponentName(this, DynamicIslandNotificationListener::class.java))
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
            activeMediaController?.unregisterCallback(mediaCallback)
        } catch (_: Exception) {}
        IslandOverlay.remove()
        super.onDestroy()
    }

    private fun updateActiveController(controller: MediaController?) {
        if (activeMediaController?.sessionToken == controller?.sessionToken) return
        activeMediaController?.unregisterCallback(mediaCallback)
        activeMediaController = controller
        activeMediaController?.registerCallback(mediaCallback)
        syncActiveMedia()
    }

    private fun syncActiveMedia() {
        if (!isEnabled(this)) return
        if (isPrivateScreen()) {
            IslandOverlay.suppressForPrivacy()
            return
        }
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
        val position = getRealtimePosition(playbackState)

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
        if (!isEnabled(this)) return
        if (isPrivateScreen()) {
            IslandOverlay.suppressForPrivacy()
            return
        }
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        val subMessages: List<String> = runCatching {
            val charSeqArr = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (charSeqArr != null && charSeqArr.isNotEmpty()) {
                charSeqArr.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        val isCall = notification.category == Notification.CATEGORY_CALL ||
            notification.category == Notification.CATEGORY_MISSED_CALL ||
            (notification.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
                (notification.category == Notification.CATEGORY_CALL ||
                    title.contains("call", ignoreCase = true)))

        // Strictly filter out non-call ongoing services, background syncs, and silent phantoms (e.g. WhatsApp Web / Checking for messages)
        if (!isCall) {
            val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) ||
                (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) ||
                (notification.flags and Notification.FLAG_NO_CLEAR != 0)
            if (isOngoing) return

            @Suppress("DEPRECATION")
            if (notification.priority < Notification.PRIORITY_DEFAULT) return

            val scanText = "$title $body".lowercase()
            val isSyncPhantom = scanText.contains("checking for new messages") ||
                scanText.contains("whatsapp web") ||
                scanText.contains("backup in progress") ||
                scanText.contains("backing up") ||
                scanText.contains("syncing messages") ||
                scanText.contains("preparing backup") ||
                scanText.contains("chat backup") ||
                scanText.contains("running in the background")
            if (isSyncPhantom) return
        }

        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = keyguard?.isKeyguardLocked ?: false
        val safeTitle = if (locked) "New notification" else if (title.isBlank()) appLabel(sbn.packageName) else title
        val safeBody = if (locked) "Unlock your phone to view details"
            else if (body.isBlank()) (if (isCall) "Call notification" else "New notification") else body
        val pendingIntent = notification.contentIntent

        // Resolve real app icon
        val appIcon = runCatching { packageManager.getApplicationIcon(sbn.packageName) }.getOrNull()

        // Extract actions & RemoteInput reply action with strict deduplication
        var replyAction: IslandReplyAction? = null
        val rawActions = notification.actions ?: emptyArray()
        for (action in rawActions) {
            val actionIntent = action.actionIntent ?: continue
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty() && replyAction == null) {
                val input = remoteInputs.first()
                replyAction = IslandReplyAction(
                    title = action.title?.toString()?.take(24) ?: "Reply",
                    pendingIntent = actionIntent,
                    remoteInput = input,
                )
                break
            }
        }

        // Deduplicate: filter out actions whose title is "Reply" or matches replyAction intent
        val actions = rawActions.take(4).mapNotNull { action ->
            val actionIntent = action.actionIntent ?: return@mapNotNull null
            val actionTitle = action.title?.toString()?.trim() ?: return@mapNotNull null
            if (actionTitle.equals("reply", ignoreCase = true) || (replyAction != null && actionIntent == replyAction.pendingIntent)) {
                return@mapNotNull null
            }
            IslandAction(actionTitle.take(24), actionIntent)
        }

        // If enabled, silence/hide notification from the Android system notification shade so it ONLY displays in Dynamic Island
        if (isHideSystemNotificationsEnabled(this) && !isCall && Settings.canDrawOverlays(this)) {
            runCatching { cancelNotification(sbn.key) }
        }

        val notificationKey = sbn.key ?: "${sbn.packageName}:${sbn.id}"
        Handler(Looper.getMainLooper()).post {
            if (!isEnabled(this)) return@post
            // Recheck after queueing: the screen can lock after ingestion.
            if (isPrivateScreen()) {
                IslandOverlay.suppressForPrivacy()
                return@post
            }
            IslandOverlay.onNotificationReceived(
                context = applicationContext,
                key = notificationKey,
                packageName = sbn.packageName,
                title = safeTitle.take(90),
                body = safeBody.take(180),
                isCall = isCall,
                appIcon = appIcon,
                pendingIntent = pendingIntent,
                actions = if (locked) emptyList() else actions,
                replyAction = if (locked) null else replyAction,
                animateArrival = !isInitialReconcile,
                subMessages = if (locked) emptyList() else subMessages,
            )
            com.example.chat.ui.island.ChatyIslandNativeBridge.deliverNativeEvent(
                com.example.chat.ui.island.IslandNativeEvent.NotificationPosted(
                    notificationKey, isCall, safeTitle.take(90), safeBody.take(180),
                ),
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || !isEnabled(this)) return
        val notificationKey = sbn.key ?: "${sbn.packageName}:${sbn.id}"
        Handler(Looper.getMainLooper()).post {
            IslandOverlay.onNotificationRemoved(notificationKey)
            com.example.chat.ui.island.ChatyIslandNativeBridge.deliverNativeEvent(
                com.example.chat.ui.island.IslandNativeEvent.NotificationRemoved(notificationKey),
            )
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences("chaty_dynamic_island", Context.MODE_PRIVATE)
                .getBoolean("enabled", false)

        fun isHideSystemNotificationsEnabled(context: Context): Boolean =
            context.getSharedPreferences("chaty_dynamic_island", Context.MODE_PRIVATE)
                .getBoolean("hide_system_notifications", false)

        fun isHeaderEnabled(context: Context): Boolean =
            context.getSharedPreferences("chaty_dynamic_island", Context.MODE_PRIVATE)
                .getBoolean("show_island_header", false)

        fun getRealtimePosition(playbackState: PlaybackState?): Long {
            if (playbackState == null) return 0L
            var pos = playbackState.position
            if (playbackState.state == PlaybackState.STATE_PLAYING) {
                val lastUpdate = playbackState.lastPositionUpdateTime
                val now = SystemClock.elapsedRealtime()
                if (lastUpdate > 0) {
                    val diff = (now - lastUpdate) * playbackState.playbackSpeed
                    pos += diff.toLong()
                }
            }
            return pos.coerceAtLeast(0L)
        }

        fun setHideSystemNotifications(context: Context, hide: Boolean) {
            context.getSharedPreferences("chaty_dynamic_island", Context.MODE_PRIVATE)
                .edit().putBoolean("hide_system_notifications", hide).apply()
        }

        fun setHeaderEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences("chaty_dynamic_island", Context.MODE_PRIVATE)
                .edit().putBoolean("show_island_header", enabled).apply()
        }

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
    val subMessages: List<String> = emptyList(),
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
    CALL_EXPANDED,
}

internal enum class ExpandedTabMode {
    MEDIA,
    NOTIFICATIONS,
}

enum class IslandVectorType {
    PLAY,
    PAUSE,
    PREVIOUS,
    NEXT,
    PHONE,
    SPEAKER,
    BELL,
    CALL,
    CALL_END,
    CLOSE,
    SEND,
    MUSIC_NOTE,
    CHEVRON_DOWN,
    CHEVRON_UP,
    VOLUME,
}

/**
 * Pure 144Hz-ready custom vector icon renderer.
 * Eliminates all emojis from the Dynamic Island, rendering bespoke anti-aliased geometry on Canvas.
 */
class IslandVectorIconView(
    context: Context,
    var vectorType: IslandVectorType,
    var iconColor: Int = Color.WHITE,
) : View(context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = iconColor
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = iconColor
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    fun setColor(color: Int) {
        iconColor = color
        fillPaint.color = color
        strokePaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        path.reset()
        when (vectorType) {
            IslandVectorType.PLAY -> {
                val left = w * 0.32f
                val right = w * 0.72f
                val top = h * 0.22f
                val bottom = h * 0.78f
                path.moveTo(left, top)
                path.lineTo(right, h * 0.5f)
                path.lineTo(left, bottom)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            IslandVectorType.PAUSE -> {
                val barW = w * 0.16f
                val barH = h * 0.52f
                val top = (h - barH) / 2f
                val bottom = top + barH
                val radius = barW / 2f

                val left1 = w * 0.28f
                canvas.drawRoundRect(left1, top, left1 + barW, bottom, radius, radius, fillPaint)

                val left2 = w * 0.56f
                canvas.drawRoundRect(left2, top, left2 + barW, bottom, radius, radius, fillPaint)
            }
            IslandVectorType.PREVIOUS -> {
                val barW = w * 0.12f
                val top = h * 0.24f
                val bottom = h * 0.76f
                val radius = barW / 2f
                val leftBar = w * 0.22f
                canvas.drawRoundRect(leftBar, top, leftBar + barW, bottom, radius, radius, fillPaint)

                path.moveTo(w * 0.78f, top)
                path.lineTo(w * 0.38f, h * 0.5f)
                path.lineTo(w * 0.78f, bottom)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            IslandVectorType.NEXT -> {
                val barW = w * 0.12f
                val top = h * 0.24f
                val bottom = h * 0.76f
                val radius = barW / 2f

                path.moveTo(w * 0.22f, top)
                path.lineTo(w * 0.62f, h * 0.5f)
                path.lineTo(w * 0.22f, bottom)
                path.close()
                canvas.drawPath(path, fillPaint)

                val rightBar = w * 0.66f
                canvas.drawRoundRect(rightBar, top, rightBar + barW, bottom, radius, radius, fillPaint)
            }
            IslandVectorType.PHONE -> {
                val padX = w * 0.26f
                val padY = h * 0.16f
                val cornerR = w * 0.10f
                canvas.drawRoundRect(padX, padY, w - padX, h - padY, cornerR, cornerR, strokePaint)
                val lineY = h * 0.25f
                canvas.drawLine(w * 0.42f, lineY, w * 0.58f, lineY, strokePaint)
            }
            IslandVectorType.SPEAKER -> {
                val left = w * 0.24f
                val top = h * 0.35f
                val bottom = h * 0.65f
                path.moveTo(left, top)
                path.lineTo(left + w * 0.18f, top)
                path.lineTo(w * 0.62f, h * 0.20f)
                path.lineTo(w * 0.62f, h * 0.80f)
                path.lineTo(left + w * 0.18f, bottom)
                path.lineTo(left, bottom)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            IslandVectorType.BELL -> {
                val cx = w * 0.5f
                path.moveTo(cx - w * 0.26f, h * 0.68f)
                path.lineTo(cx + w * 0.26f, h * 0.68f)
                path.quadTo(cx + w * 0.22f, h * 0.34f, cx, h * 0.20f)
                path.quadTo(cx - w * 0.22f, h * 0.34f, cx - w * 0.26f, h * 0.68f)
                canvas.drawPath(path, strokePaint)
                canvas.drawCircle(cx, h * 0.77f, w * 0.08f, fillPaint)
            }
            IslandVectorType.CALL -> {
                path.moveTo(w * 0.25f, h * 0.40f)
                path.quadTo(w * 0.50f, h * 0.18f, w * 0.75f, h * 0.40f)
                path.lineTo(w * 0.68f, h * 0.56f)
                path.quadTo(w * 0.50f, h * 0.36f, w * 0.32f, h * 0.56f)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            IslandVectorType.CALL_END -> {
                path.moveTo(w * 0.22f, h * 0.56f)
                path.quadTo(w * 0.50f, h * 0.76f, w * 0.78f, h * 0.56f)
                path.lineTo(w * 0.70f, h * 0.40f)
                path.quadTo(w * 0.50f, h * 0.58f, w * 0.30f, h * 0.40f)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            IslandVectorType.CLOSE -> {
                val pad = w * 0.28f
                canvas.drawLine(pad, pad, w - pad, h - pad, strokePaint)
                canvas.drawLine(w - pad, pad, pad, h - pad, strokePaint)
            }
            IslandVectorType.SEND -> {
                path.moveTo(w * 0.18f, h * 0.20f)
                path.lineTo(w * 0.86f, h * 0.50f)
                path.lineTo(w * 0.18f, h * 0.80f)
                path.lineTo(w * 0.38f, h * 0.50f)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            IslandVectorType.MUSIC_NOTE -> {
                val headX = w * 0.36f
                val headY = h * 0.70f
                val rx = w * 0.16f
                val ry = h * 0.12f
                canvas.drawOval(headX - rx, headY - ry, headX + rx, headY + ry, fillPaint)
                val stemX = headX + rx * 0.75f
                val stemTop = h * 0.22f
                canvas.drawLine(stemX, headY, stemX, stemTop, strokePaint)
                path.moveTo(stemX, stemTop)
                path.quadTo(stemX + w * 0.22f, stemTop + h * 0.08f, stemX + w * 0.20f, stemTop + h * 0.26f)
                canvas.drawPath(path, strokePaint)
            }
            IslandVectorType.CHEVRON_DOWN -> {
                path.moveTo(w * 0.22f, h * 0.38f)
                path.lineTo(w * 0.50f, h * 0.64f)
                path.lineTo(w * 0.78f, h * 0.38f)
                canvas.drawPath(path, strokePaint)
            }
            IslandVectorType.CHEVRON_UP -> {
                path.moveTo(w * 0.22f, h * 0.64f)
                path.lineTo(w * 0.50f, h * 0.38f)
                path.lineTo(w * 0.78f, h * 0.64f)
                canvas.drawPath(path, strokePaint)
            }
            IslandVectorType.VOLUME -> {
                val left = w * 0.20f
                val top = h * 0.35f
                val bottom = h * 0.65f
                path.moveTo(left, top)
                path.lineTo(left + w * 0.18f, top)
                path.lineTo(w * 0.52f, h * 0.20f)
                path.lineTo(w * 0.52f, h * 0.80f)
                path.lineTo(left + w * 0.18f, bottom)
                path.lineTo(left, bottom)
                path.close()
                canvas.drawPath(path, fillPaint)
                val arcRect1 = RectF(w * 0.45f, h * 0.32f, w * 0.72f, h * 0.68f)
                canvas.drawArc(arcRect1, -45f, 90f, false, strokePaint)
                val arcRect2 = RectF(w * 0.55f, h * 0.20f, w * 0.88f, h * 0.80f)
                canvas.drawArc(arcRect2, -45f, 90f, false, strokePaint)
            }
        }
    }
}

/**
 * High-performance 12-bar live audio equalizer waveform visualizer.
 * Oscillates with sinusoidal phase offsets at 60fps when playback is active.
 */
class WaveformVisualizerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(56, 189, 248) // Electric cyan-blue
        style = Paint.Style.FILL
    }
    private var isPlaying = false
    private val barCount = 12
    private val barHeights = FloatArray(barCount) { 0.22f }
    private var animator: ValueAnimator? = null

    fun setWaveformColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
        if (playing) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                for (i in 0 until barCount) {
                    val phase = i * 0.55f + fraction * Math.PI.toFloat()
                    barHeights[i] = (0.18f + 0.78f * Math.abs(Math.sin(phase.toDouble()).toFloat())).coerceIn(0.15f, 1.0f)
                }
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        for (i in 0 until barCount) {
            barHeights[i] = 0.20f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val totalSpacing = (barCount - 1) * 3f
        val barWidth = ((w - totalSpacing) / barCount).coerceAtLeast(2f)
        val radius = barWidth / 2f

        for (i in 0 until barCount) {
            val left = i * (barWidth + 3f)
            val barH = (h * barHeights[i]).coerceAtLeast(radius * 2f)
            val top = (h - barH) / 2f
            val right = left + barWidth
            val bottom = top + barH
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}

/**
 * Dynamic Material You / Kombai Wavy Audio Scrubber.
 * Renders an animated sinusoidal wave for the played audio segment, a vertical pill thumb,
 * and a sleek unplayed track. Supports interactive scrub touch drag seeking with haptics.
 */
class WavyAudioScrubberView(context: Context) : View(context) {
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(56, 189, 248) // Electric cyan-blue
        style = Paint.Style.STROKE
        strokeWidth = dp(3.5f)
        strokeCap = Paint.Cap.ROUND
    }

    fun setAccentColor(color: Int) {
        playedPaint.color = color
        invalidate()
    }

    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val wavePath = Path()
    private var wavePhase: Float = 0f
    private var phaseAnimator: ValueAnimator? = null

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var durationMs: Long = 0L
    var isPlaying: Boolean = false
        set(value) {
            field = value
            if (value) startWaveAnimation() else stopWaveAnimation()
        }

    var isDragging: Boolean = false
        private set

    var onSeekListener: ((Long) -> Unit)? = null

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    fun setPlayingState(playing: Boolean) {
        isPlaying = playing
    }

    private fun startWaveAnimation() {
        if (phaseAnimator?.isRunning == true) return
        phaseAnimator?.cancel()
        phaseAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { anim ->
                wavePhase = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopWaveAnimation() {
        phaseAnimator?.cancel()
        phaseAnimator = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val centerY = h / 2f
        val thumbX = (w * progress).coerceIn(0f, w)

        // Draw played segment (sine wave if playing or subtle static wave if paused)
        wavePath.reset()
        val amp = dp(3.5f)
        val wavelength = dp(24f)

        if (thumbX > 0f) {
            wavePath.moveTo(0f, centerY)
            var x = 0f
            val step = dp(2f)
            while (x <= thumbX) {
                val y = if (isPlaying) {
                    centerY + amp * Math.sin((x / wavelength * 2 * Math.PI - wavePhase).toDouble()).toFloat()
                } else {
                    centerY + (amp * 0.4f) * Math.sin((x / wavelength * 2 * Math.PI).toDouble()).toFloat()
                }
                wavePath.lineTo(x, y)
                x += step
            }
            wavePath.lineTo(thumbX, centerY)
            canvas.drawPath(wavePath, playedPaint)
        }

        // Draw unplayed segment (straight track)
        if (thumbX < w) {
            canvas.drawLine(thumbX, centerY, w, centerY, unplayedPaint)
        }

        // Draw Scrubber Thumb: modern vertical pill
        val thumbHalfW = dp(2.25f)
        val thumbHalfH = dp(8f)
        val thumbLeft = (thumbX - thumbHalfW).coerceIn(0f, w - thumbHalfW * 2f)
        val thumbTop = centerY - thumbHalfH
        val thumbRight = thumbLeft + thumbHalfW * 2f
        val thumbBottom = centerY + thumbHalfH
        canvas.drawRoundRect(thumbLeft, thumbTop, thumbRight, thumbBottom, thumbHalfW, thumbHalfW, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                progress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    progress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    progress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                    if (durationMs > 0) {
                        val targetMs = (progress * durationMs).toLong()
                        onSeekListener?.invoke(targetMs)
                    }
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWaveAnimation()
    }
}
