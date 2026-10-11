package com.example.chat

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * User-enabled notification listener for Chaty's system-wide Dynamic Island.
 * Android requires the user to grant Notification Access explicitly in Settings.
 */
class DynamicIslandNotificationListener : NotificationListenerService() {
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
        val icon = if (isCall) "☎" else "●"
        val safeTitle = if (title.isBlank()) appLabel(sbn.packageName) else title
        val safeBody = if (body.isBlank()) (if (isCall) "Call notification" else "New notification") else body
        val pendingIntent = notification.contentIntent
        Handler(Looper.getMainLooper()).post {
            IslandOverlay.show(
                applicationContext,
                safeTitle.take(90),
                safeBody.take(180),
                icon,
                sbn.packageName,
                isCall,
                pendingIntent,
                (notification.actions ?: emptyArray()).take(2).mapNotNull { action ->
                    val actionIntent = action.actionIntent ?: return@mapNotNull null
                    IslandAction(action.title?.toString()?.take(24) ?: "Action", actionIntent)
                },
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Keep the transient island independent of notification shade dismissal.
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

/** Lightweight native overlay. No notification content is persisted or uploaded. */
internal object IslandOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var currentView: View? = null
    private var dismissRunnable: Runnable? = null
    private var expanded = false
    private var latestTitle = ""
    private var latestBody = ""
    private var latestIcon = "●"
    private var latestPackage = ""
    private var latestCall = false
    private var latestPendingIntent: PendingIntent? = null\n    private var latestActions: List<IslandAction> = emptyList()

    fun show(
        context: Context,
        title: String,
        body: String,
        icon: String,
        packageName: String,
        isCall: Boolean,
        pendingIntent: PendingIntent?,
        actions: List<IslandAction> = emptyList(),
    ) {
        if (!DynamicIslandNotificationListener.isEnabled(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return
        latestTitle = title
        latestBody = body
        latestIcon = icon
        latestPackage = packageName
        latestCall = isCall
        latestPendingIntent = pendingIntent\n        latestActions = actions\n        expanded = false
        dismissRunnable?.let(main::removeCallbacks)
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        render(context)
        val timeout = if (isCall) 12000L else 5500L
        dismissRunnable = Runnable { remove() }
        main.postDelayed(dismissRunnable!!, timeout)
    }

    private fun render(context: Context) {
        val wm = windowManager ?: return
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 15), dp(context, 10), dp(context, 15), dp(context, 10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(8, 9, 11))
                cornerRadius = dp(context, if (expanded) 24 else 32).toFloat()
                setStroke(dp(context, 1), Color.argb(45, 255, 255, 255))
            }
            elevation = dp(context, 12).toFloat()
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val glyph = TextView(context).apply {
            text = latestIcon
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(if (latestCall) Color.rgb(74, 222, 128) else Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (latestCall) Color.argb(35, 74, 222, 128) else Color.rgb(35, 37, 42))
            }
        }
        row.addView(glyph, LinearLayout.LayoutParams(dp(context, 30), dp(context, 30)))
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 10), 0, dp(context, 8), 0)
        }
        textColumn.addView(TextView(context).apply {
            text = latestTitle
            textSize = 12.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        if (expanded) {
            textColumn.addView(TextView(context).apply {
                text = latestBody
                textSize = 12f
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(context, 3), 0, 0)
            })
            textColumn.addView(TextView(context).apply {
                text = if (latestCall) "Tap to return to call" else "Tap to open notification"
                textSize = 10.5f
                setTextColor(Color.rgb(120, 190, 255))
                setPadding(0, dp(context, 5), 0, 0)
            })
        }
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        val dot = TextView(context).apply {
            text = "●"
            textSize = 8f
            setTextColor(if (latestCall) Color.rgb(74, 222, 128) else Color.rgb(120, 190, 255))
        }
        row.addView(dot)
        root.addView(row)
        if (expanded && latestActions.isNotEmpty()) {
            val actionsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(context, 8), 0, 0)
            }
            latestActions.forEach { action ->
                val button = Button(context).apply {
                    text = action.title
                    isAllCaps = false
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(37, 40, 46))
                        cornerRadius = dp(context, 14).toFloat()
                        setStroke(dp(context, 1), Color.argb(45, 255, 255, 255))
                    }
                    setPadding(dp(context, 8), 0, dp(context, 8), 0)
                    setOnClickListener {
                        runCatching { action.pendingIntent.send() }
                        remove()
                    }
                }
                val lp = LinearLayout.LayoutParams(-2, dp(context, 38))
                lp.marginStart = dp(context, 6)
                actionsRow.addView(button, lp)
            }
            root.addView(actionsRow)
        }
        root.setOnClickListener {
            if (!expanded) {
                expanded = true
                render(context)
            } else {
                runCatching { latestPendingIntent?.send() }
                remove()
            }
        }
        val params = WindowManager.LayoutParams(
            (context.resources.displayMetrics.widthPixels - dp(context, 32)).coerceAtMost(dp(context, 390)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(context, 8) + statusBarHeight(context)
        }
        try {
            val old = currentView
            if (old != null) runCatching { wm.removeView(old) }
            currentView = root
            wm.addView(root, params)
        } catch (_: Exception) {
            currentView = null
        }
    }

    fun preview(context: Context) {
        show(context, "Chaty Dynamic Island", "Preview notification · Tap to expand", "✦", context.packageName, false, null, emptyList())
    }

    fun remove() {
        dismissRunnable?.let(main::removeCallbacks)
        val view = currentView
        currentView = null
        if (view != null) runCatching { windowManager?.removeView(view) }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(context, 24)
    }
}
