package com.example.chat.island

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * High-performance state renderer for IslandView.
 * Directly applies IslandStore updates to the persistent view tree without tearing down the root.
 */
class IslandRenderer(
    private val context: Context,
    private val windowController: IslandWindowController,
    private val store: IslandStore,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null
    private var gestureArbiter: IslandGestureArbiter? = null

    fun render(snapshot: IslandStore.IslandSnapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { render(snapshot) }
            return
        }

        if (snapshot.mode == IslandMode.HIDDEN) {
            windowController.detach()
            return
        }

        val root = windowController.attachIfNeeded() ?: return
        setupGestures(root)

        val screenWidth = context.resources.displayMetrics.widthPixels
        val cutoutWidth = windowController.getCutoutWidth()

        when (snapshot.mode) {
            IslandMode.COMPACT -> {
                val widthPx = dp(if (snapshot.messages.size > 1) IslandTokens.COMPACT_STACKED_WIDTH_DP else IslandTokens.COMPACT_DEFAULT_WIDTH_DP)
                    .coerceAtLeast(cutoutWidth + dp(64))
                val heightPx = dp(IslandTokens.COMPACT_HEIGHT_DP)
                windowController.updateBounds(widthPx, heightPx, focusableForIme = false)
                root.applyBackgroundStyle(IslandTokens.COLOR_COMPACT_BG, android.graphics.Color.TRANSPARENT, dp(18).toFloat())
                renderCompact(root.container, snapshot, cutoutWidth)
                scheduleDismiss(7000L)
            }
            IslandMode.EXPANDED_STACK -> {
                val widthPx = (screenWidth - dp(IslandTokens.EXPANDED_HORIZONTAL_MARGIN_DP * 2))
                    .coerceAtMost(dp(IslandTokens.EXPANDED_MAX_WIDTH_DP))
                val heightPx = dp(when {
                    snapshot.messages.size > 2 -> 260
                    snapshot.messages.size == 2 -> 190
                    else -> 136
                })
                windowController.updateBounds(widthPx, heightPx, focusableForIme = false)
                root.applyBackgroundStyle(IslandTokens.COLOR_EXPANDED_BG, IslandTokens.COLOR_EXPANDED_STROKE, dp(32).toFloat())
                renderExpandedStack(root.container, snapshot)
                scheduleDismiss(16000L)
            }
            IslandMode.REPLY -> {
                val widthPx = (screenWidth - dp(IslandTokens.EXPANDED_HORIZONTAL_MARGIN_DP * 2))
                    .coerceAtMost(dp(IslandTokens.EXPANDED_MAX_WIDTH_DP))
                val heightPx = dp(IslandTokens.EXPANDED_REPLY_HEIGHT_DP)
                windowController.updateBounds(widthPx, heightPx, focusableForIme = true)
                root.applyBackgroundStyle(IslandTokens.COLOR_EXPANDED_BG, IslandTokens.COLOR_EXPANDED_STROKE, dp(32).toFloat())
                renderReply(root.container, snapshot)
                cancelDismiss()
            }
            IslandMode.EXPANDED_MEDIA -> {
                val widthPx = (screenWidth - dp(IslandTokens.EXPANDED_HORIZONTAL_MARGIN_DP * 2))
                    .coerceAtMost(dp(IslandTokens.EXPANDED_MAX_WIDTH_DP))
                val heightPx = dp(IslandTokens.EXPANDED_MEDIA_HEIGHT_DP)
                windowController.updateBounds(widthPx, heightPx, focusableForIme = false)
                root.applyBackgroundStyle(IslandTokens.COLOR_EXPANDED_BG, IslandTokens.COLOR_EXPANDED_STROKE, dp(32).toFloat())
                renderExpandedMedia(root.container, snapshot)
                scheduleDismiss(16000L)
            }
            IslandMode.EXPANDED_CALL -> {
                val widthPx = (screenWidth - dp(IslandTokens.EXPANDED_HORIZONTAL_MARGIN_DP * 2))
                    .coerceAtMost(dp(IslandTokens.EXPANDED_MAX_WIDTH_DP))
                val heightPx = dp(IslandTokens.EXPANDED_CALL_HEIGHT_DP)
                windowController.updateBounds(widthPx, heightPx, focusableForIme = false)
                root.applyBackgroundStyle(IslandTokens.COLOR_EXPANDED_BG, IslandTokens.COLOR_EXPANDED_STROKE, dp(32).toFloat())
                renderExpandedCall(root.container, snapshot)
                cancelDismiss()
            }
            IslandMode.EXPANDED_CHARGING -> {
                val widthPx = (screenWidth - dp(IslandTokens.EXPANDED_HORIZONTAL_MARGIN_DP * 2))
                    .coerceAtMost(dp(IslandTokens.EXPANDED_MAX_WIDTH_DP))
                val heightPx = dp(IslandTokens.EXPANDED_CHARGING_HEIGHT_DP)
                windowController.updateBounds(widthPx, heightPx, focusableForIme = false)
                root.applyBackgroundStyle(IslandTokens.COLOR_EXPANDED_BG, IslandTokens.COLOR_EXPANDED_STROKE, dp(32).toFloat())
                renderExpandedCharging(root.container, snapshot)
                scheduleDismiss(5000L)
            }
            else -> {}
        }
    }

    private fun setupGestures(root: IslandView) {
        if (gestureArbiter == null) {
            gestureArbiter = IslandGestureArbiter(context, object : IslandGestureArbiter.Callbacks {
                override fun onTap() {
                    val s = store
                    // Toggle compact <-> expanded
                    // If in compact, expand top item
                }
                override fun onLongPress() {
                    // Expand
                }
                override fun onSwipeDown() {
                    // Expand
                }
                override fun onSwipeUp() {
                    store.setMode(IslandMode.COMPACT)
                }
                override fun onSwipeHorizontal(directionRight: Boolean) {
                    store.clearAllMessages()
                }
            })
            root.setOnTouchListener { _, event ->
                gestureArbiter?.onTouchEvent(event) ?: false
            }
        }
    }

    private fun renderCompact(container: LinearLayout, snapshot: IslandStore.IslandSnapshot, cutoutWidth: Int) {
        container.removeAllViews()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
            setPadding(dp(8), 0, dp(8), 0)
        }

        val primary = snapshot.primaryActivity
        val leadingIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            if (primary is MessageActivity && primary.appIcon != null) {
                setImageDrawable(primary.appIcon)
            } else if (primary is CallActivity) {
                setColorFilter(IslandTokens.COLOR_CALL_GREEN)
            } else {
                setBackgroundColor(IslandTokens.COLOR_ACTION_BLUE)
            }
        }
        row.addView(leadingIcon)

        // Center cutout bridge spacer
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1.0f)
        }
        row.addView(spacer)

        val trailingView = TextView(context).apply {
            setTextColor(IslandTokens.COLOR_TEXT_PRIMARY)
            textSize = 11f
            text = if (snapshot.messages.size > 1) "${snapshot.messages.size}" else "•"
        }
        row.addView(trailingView)

        container.addView(row)
    }

    private fun renderExpandedStack(container: LinearLayout, snapshot: IslandStore.IslandSnapshot) {
        container.removeAllViews()
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        for (msg in snapshot.messages) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val titleView = TextView(context).apply {
                text = msg.title
                textSize = IslandTokens.titleSizeSp()
                setTextColor(IslandTokens.COLOR_TEXT_PRIMARY)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val bodyView = TextView(context).apply {
                text = msg.body
                textSize = IslandTokens.bodySizeSp()
                setTextColor(IslandTokens.COLOR_TEXT_SECONDARY)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            item.addView(titleView)
            item.addView(bodyView)

            if (msg.replyAction != null) {
                val replyBtn = Button(context).apply {
                    text = "Reply"
                    textSize = 12f
                    setOnClickListener {
                        store.startReply(msg.key)
                    }
                }
                item.addView(replyBtn)
            }
            list.addView(item)
        }
        scroll.addView(list)
        container.addView(scroll)
    }

    private fun renderReply(container: LinearLayout, snapshot: IslandStore.IslandSnapshot) {
        container.removeAllViews()
        val target = snapshot.replyTarget ?: return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val titleView = TextView(context).apply {
            text = "Replying to ${target.title}"
            setTextColor(IslandTokens.COLOR_TEXT_PRIMARY)
            textSize = 13f
        }
        val input = EditText(context).apply {
            hint = "Type message..."
            setHintTextColor(IslandTokens.COLOR_TEXT_TERTIARY)
            setTextColor(IslandTokens.COLOR_TEXT_PRIMARY)
            textSize = 14f
            maxLines = 3
        }
        val sendBtn = Button(context).apply {
            text = "Send"
            setOnClickListener {
                val replyAction = target.replyAction
                val text = input.text.toString().trim()
                if (replyAction != null && text.isNotEmpty()) {
                    IslandReplyManager.sendReply(context, replyAction, text) { success ->
                        if (success) {
                            store.removeMessage(target.key)
                            store.setMode(IslandMode.COMPACT)
                        }
                    }
                }
            }
        }

        layout.addView(titleView)
        layout.addView(input)
        layout.addView(sendBtn)
        container.addView(layout)
    }

    private fun renderExpandedMedia(container: LinearLayout, snapshot: IslandStore.IslandSnapshot) {
        container.removeAllViews()
        val media = snapshot.activeMedia ?: return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val title = TextView(context).apply {
            text = media.title
            setTextColor(IslandTokens.COLOR_TEXT_PRIMARY)
            textSize = 14f
        }
        val artist = TextView(context).apply {
            text = media.artist
            setTextColor(IslandTokens.COLOR_TEXT_SECONDARY)
            textSize = 12f
        }
        layout.addView(title)
        layout.addView(artist)
        container.addView(layout)
    }

    private fun renderExpandedCall(container: LinearLayout, snapshot: IslandStore.IslandSnapshot) {
        container.removeAllViews()
        val call = snapshot.activeCall ?: return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val name = TextView(context).apply {
            text = call.callerName
            setTextColor(IslandTokens.COLOR_CALL_GREEN)
            textSize = 15f
        }
        layout.addView(name)
        container.addView(layout)
    }

    private fun renderExpandedCharging(container: LinearLayout, snapshot: IslandStore.IslandSnapshot) {
        container.removeAllViews()
        val chg = snapshot.activeCharging ?: return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val text = TextView(context).apply {
            text = "Charging ${chg.percent}% · ${chg.pluggedType}"
            setTextColor(IslandTokens.COLOR_CHARGING_GREEN)
            textSize = 14f
        }
        layout.addView(text)
        container.addView(layout)
    }

    private fun scheduleDismiss(delayMs: Long) {
        cancelDismiss()
        dismissRunnable = Runnable {
            store.setMode(IslandMode.HIDDEN)
        }
        mainHandler.postDelayed(dismissRunnable!!, delayMs)
    }

    private fun cancelDismiss() {
        dismissRunnable?.let(mainHandler::removeCallbacks)
        dismissRunnable = null
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

private fun IslandTokens.titleSizeSp() = 14f
private fun IslandTokens.bodySizeSp() = 13f
