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
internal object IslandOverlay {
internal fun isScreenPrivate(context: Context): Boolean {
val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
return keyguard?.isKeyguardLocked == true || power?.isInteractive == false
}
internal fun suppressForPrivacy() {
remove()
com.example.chat.ui.island.ChatyIslandNativeBridge.deliverNativeEvent(
com.example.chat.ui.island.IslandNativeEvent.ClearAll,
)
}
private val main = Handler(Looper.getMainLooper())
private var windowManager: WindowManager? = null
private var privacyReceiverContext: Context? = null
private val privacyReceiver = object : BroadcastReceiver() {
override fun onReceive(context: Context, intent: Intent) {
if (intent.action == Intent.ACTION_SCREEN_OFF) suppressForPrivacy()
}
}
private var persistentRoot: FrameLayout? = null
private var islandCard: LinearLayout? = null
private var cardBackground: GradientDrawable? = null
private var contentContainer: FrameLayout? = null
private var currentWindowParams: WindowManager.LayoutParams? = null
val currentView: View?
get() = persistentRoot
private var dismissRunnable: Runnable? = null
private var tickerRunnable: Runnable? = null
private var currentState = IslandVisualState.IDLE
private val activeNotifications = mutableListOf<IslandNotificationItem>()
private var currentMedia: IslandMediaState? = null
private var expandedTabMode: ExpandedTabMode = ExpandedTabMode.MEDIA
private var activeReplyItem: IslandNotificationItem? = null
private var isAudioOutputExpanded: Boolean = false
private var currentAnimator: ValueAnimator? = null
private var currentCornerRadius: Float = 24f
fun extractVibrantColor(bitmap: Bitmap?): Int {
if (bitmap == null || bitmap.width == 0 || bitmap.height == 0) {
return Color.rgb(56, 189, 248) // Clean liquid cyan fallback
}
return try {
val sampleW = 24
val sampleH = 24
val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
var bestColor = Color.rgb(56, 189, 248)
var bestScore = -1f
val hsv = FloatArray(3)
for (x in 0 until sampleW) {
for (y in 0 until sampleH) {
val pixel = scaled.getPixel(x, y)
if (Color.alpha(pixel) < 128) continue
Color.colorToHSV(pixel, hsv)
val sat = hsv[1]
val b = hsv[2]
if (b in 0.28f..0.96f && sat > 0.22f) {
val score = sat * 2.0f + (1.0f - Math.abs(b - 0.70f))
if (score > bestScore) {
bestScore = score
bestColor = pixel
}
}
}
}
bestColor
} catch (_: Exception) {
Color.rgb(56, 189, 248)
}
}
fun darkerShade(color: Int): Int {
val factor = 0.72f
val a = Color.alpha(color)
val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
return Color.argb(a, r, g, b)
}
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
subMessages: List<String> = emptyList(),
) {
if (!DynamicIslandNotificationListener.isEnabled(context)) return
if (isScreenPrivate(context)) {
suppressForPrivacy()
return
}
val appContext = context.applicationContext ?: context
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) return
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
subMessages = subMessages,
)
if (existingIndex >= 0) {
val existing = activeNotifications[existingIndex]
val isContentChanged = existing.title != title || existing.body != body
if (isContentChanged) {
activeNotifications.removeAt(existingIndex)
activeNotifications.add(0, newItem)
} else {
activeNotifications[existingIndex] = newItem
}
} else {
activeNotifications.add(0, newItem)
}
while (activeNotifications.size > 20) {
activeNotifications.removeAt(activeNotifications.lastIndex)
}
if (isCall) {
currentState = IslandVisualState.CALL_EXPANDED
} else if (currentState == IslandVisualState.IDLE) {
currentState = IslandVisualState.COMPACT
} else if (currentState == IslandVisualState.EXPANDED) {
} else if (currentState == IslandVisualState.MEDIA_EXPANDED) {
} else if (currentState == IslandVisualState.COMPACT) {
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
persistentRoot?.context?.let { render(it, animate = true) }
} else {
persistentRoot?.context?.let { render(it, animate = true) }
}
}
fun clearAllNotifications() {
activeNotifications.clear()
activeReplyItem = null
if (currentMedia == null) {
currentState = IslandVisualState.IDLE
persistentRoot?.context?.let { render(it, animate = true) }
} else {
currentState = IslandVisualState.COMPACT
persistentRoot?.context?.let { render(it, animate = true) }
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
if (isScreenPrivate(context)) {
suppressForPrivacy()
return
}
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
if (persistentRoot == null && activeNotifications.isEmpty()) {
currentState = IslandVisualState.COMPACT
render(appContext, animate = true)
} else if (persistentRoot != null) {
render(appContext, animate = false)
}
}
fun clearMedia() {
currentMedia = null
tickerRunnable?.let(main::removeCallbacks)
if (activeNotifications.isEmpty()) {
currentState = IslandVisualState.IDLE
persistentRoot?.context?.let { render(it, animate = true) }
} else {
persistentRoot?.context?.let { render(it, animate = true) }
}
}
private fun ensureWindowManager(context: Context) {
if (privacyReceiverContext == null) {
val app = context.applicationContext
ContextCompat.registerReceiver(
app, privacyReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
ContextCompat.RECEIVER_NOT_EXPORTED,
)
privacyReceiverContext = app
}
if (windowManager == null) {
windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
}
}
private fun scheduleDismiss(context: Context, state: IslandVisualState) {
dismissRunnable?.let(main::removeCallbacks)
val hasActiveCall = activeNotifications.any { it.isCall }
val hasMediaPlaying = currentMedia?.isPlaying == true
if (hasActiveCall || hasMediaPlaying || state == IslandVisualState.REPLY || state == IslandVisualState.CALL_EXPANDED) {
return // Ongoing activity or typing, never auto dismiss
}
val timeout = when (state) {
IslandVisualState.IDLE -> 0L
IslandVisualState.COMPACT -> 8000L
IslandVisualState.EXPANDED -> 18000L
IslandVisualState.MEDIA_EXPANDED -> 18000L
IslandVisualState.CALL_EXPANDED -> 0L
IslandVisualState.REPLY -> 35000L
}
if (timeout == 0L) return
dismissRunnable = Runnable {
if (activeNotifications.isNotEmpty() && state != IslandVisualState.COMPACT) {
currentState = IslandVisualState.COMPACT
render(context, animate = true)
scheduleDismiss(context, IslandVisualState.COMPACT)
} else if (currentMedia?.isPlaying == true) {
currentState = IslandVisualState.COMPACT
render(context, animate = true)
} else {
currentState = IslandVisualState.IDLE
render(context, animate = true)
}
}
main.postDelayed(dismissRunnable!!, timeout)
}
private fun render(context: Context, animate: Boolean = true) {
if (isScreenPrivate(context)) {
suppressForPrivacy()
return
}
val wm = windowManager ?: return
val screenWidth = context.resources.displayMetrics.widthPixels
if (currentState == IslandVisualState.IDLE) {
dismissRunnable?.let(main::removeCallbacks)
if (persistentRoot != null && persistentRoot!!.isAttachedToWindow) {
if (animate) {
persistentRoot?.animate()
?.alpha(0f)
?.scaleX(0.7f)
?.scaleY(0.7f)
?.setDuration(200)
?.withEndAction {
runCatching {
if (persistentRoot?.isAttachedToWindow == true) {
wm.removeView(persistentRoot)
}
}
persistentRoot = null
islandCard = null
contentContainer = null
currentWindowParams = null
}
?.start()
} else {
runCatching { wm.removeView(persistentRoot) }
persistentRoot = null
islandCard = null
contentContainer = null
currentWindowParams = null
}
}
return
}
val primaryNotif = activeNotifications.firstOrNull()
val media = currentMedia
val unreadCount = activeNotifications.size
val isSplitMode = currentState == IslandVisualState.COMPACT && media != null && media.isPlaying && unreadCount > 0
val cutoutW = getCutoutWidth(context)
val splitPillW = dp(context, 34)
val splitSpacerW = cutoutW + dp(context, 12)
val splitWidth = (splitPillW * 2 + splitSpacerW).coerceAtMost(dp(context, 104))
val compactWidth = if (isSplitMode) {
splitWidth
} else when {
media != null && media.isPlaying -> (cutoutW + dp(context, 56)).coerceIn(dp(context, 88), dp(context, 96))
unreadCount > 1 -> (cutoutW + dp(context, 52)).coerceIn(dp(context, 84), dp(context, 92))
else -> (cutoutW + dp(context, 48)).coerceIn(dp(context, 76), dp(context, 88))
}
val targetWidth = when (currentState) {
IslandVisualState.IDLE -> 0
IslandVisualState.COMPACT -> compactWidth
IslandVisualState.EXPANDED -> (screenWidth - dp(context, 20)).coerceAtMost(dp(context, 380))
IslandVisualState.MEDIA_EXPANDED -> (screenWidth - dp(context, 20)).coerceAtMost(dp(context, 380))
IslandVisualState.CALL_EXPANDED -> (screenWidth - dp(context, 20)).coerceAtMost(dp(context, 380))
IslandVisualState.REPLY -> (screenWidth - dp(context, 20)).coerceAtMost(dp(context, 380))
}
val targetHeight = when (currentState) {
IslandVisualState.IDLE -> 0
IslandVisualState.COMPACT -> dp(context, 36)
IslandVisualState.EXPANDED -> {
if (expandedTabMode == ExpandedTabMode.MEDIA && media != null) {
dp(context, 236)
} else when {
unreadCount > 2 -> dp(context, 280)
unreadCount == 2 -> dp(context, 210)
else -> dp(context, 150)
}
}
IslandVisualState.MEDIA_EXPANDED -> dp(context, 236)
IslandVisualState.CALL_EXPANDED -> dp(context, 220)
IslandVisualState.REPLY -> dp(context, 184)
}
val targetRadius = when (currentState) {
IslandVisualState.IDLE -> dp(context, 18).toFloat()
IslandVisualState.COMPACT -> dp(context, 18).toFloat()
else -> dp(context, 28).toFloat()
}
val topOffset = getCutoutTopOffset(context)
if (persistentRoot == null || !persistentRoot!!.isAttachedToWindow) {
currentCornerRadius = targetRadius
val root = FrameLayout(context).apply {
clipChildren = false
}
val bg = GradientDrawable().apply {
if (isSplitMode) {
setColor(Color.TRANSPARENT)
setStroke(0, Color.TRANSPARENT)
} else {
setColor(if (currentState == IslandVisualState.IDLE) Color.BLACK else Color.rgb(10, 11, 15))
setStroke(
dp(context, 1),
if (currentState == IslandVisualState.IDLE) Color.argb(20, 255, 255, 255) else Color.argb(40, 255, 255, 255)
)
}
cornerRadius = currentCornerRadius
}
cardBackground = bg
val card = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
gravity = Gravity.CENTER
background = bg
elevation = if (isSplitMode) 0f else dp(context, 24).toFloat()
clipChildren = true
}
islandCard = card
val container = FrameLayout(context).apply {
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.MATCH_PARENT,
)
clipChildren = true
}
contentContainer = container
card.addView(container)
val cardParams = FrameLayout.LayoutParams(targetWidth, targetHeight).apply {
gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
}
root.addView(card, cardParams)
setupTouchInteractions(context, root, card)
@Suppress("DEPRECATION")
val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
else WindowManager.LayoutParams.TYPE_PHONE
var flags = WindowManager.LayoutParams.FLAG_SECURE or
WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
if (currentState != IslandVisualState.REPLY) {
flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
}
val winParams = WindowManager.LayoutParams(
targetWidth,
targetHeight,
windowType,
flags,
PixelFormat.TRANSLUCENT,
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
currentWindowParams = winParams
persistentRoot = root
try {
wm.addView(root, winParams)
} catch (_: Exception) {
persistentRoot = null
return
}
}
val root = persistentRoot ?: return
val card = islandCard ?: return
val container = contentContainer ?: return
val winParams = currentWindowParams ?: return
val bg = cardBackground ?: return
if (currentState == IslandVisualState.REPLY) {
winParams.flags = winParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
} else {
winParams.flags = winParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
}
winParams.y = topOffset
if (isSplitMode) {
card.background = bg
bg.setColor(Color.TRANSPARENT)
bg.setStroke(0, Color.TRANSPARENT)
card.elevation = 0f
} else if ((currentState == IslandVisualState.MEDIA_EXPANDED || (currentState == IslandVisualState.EXPANDED && expandedTabMode == ExpandedTabMode.MEDIA)) && media?.art != null) {
card.background = getMediaBackdropDrawable(context, media.art, currentCornerRadius)
card.elevation = dp(context, 24).toFloat()
} else {
card.background = bg
bg.setColor(if (currentState == IslandVisualState.IDLE) Color.BLACK else Color.rgb(10, 11, 15))
bg.setStroke(
dp(context, 1),
if (currentState == IslandVisualState.IDLE) Color.argb(20, 255, 255, 255) else Color.argb(40, 255, 255, 255)
)
card.elevation = dp(context, 24).toFloat()
}
val newView: View = when (currentState) {
IslandVisualState.IDLE -> buildIdleView(context)
IslandVisualState.COMPACT -> buildCompactView(context, primaryNotif, media, unreadCount)
IslandVisualState.EXPANDED -> {
if (expandedTabMode == ExpandedTabMode.MEDIA && media != null) {
buildMediaExpandedView(context, media)
} else {
buildExpandedStackView(context, targetHeight)
}
}
IslandVisualState.MEDIA_EXPANDED -> buildMediaExpandedView(context, media)
IslandVisualState.CALL_EXPANDED -> buildCallExpandedView(context, primaryNotif ?: activeNotifications.firstOrNull { it.isCall } ?: IslandNotificationItem("call_fallback", "com.example.chat", "Incoming Call", "Voice Call", true, null, null, emptyList(), null))
IslandVisualState.REPLY -> buildReplyView(context, activeReplyItem ?: primaryNotif)
}
val startW = winParams.width
val startH = winParams.height
val startR = currentCornerRadius
if (!animate || (startW == targetWidth && startH == targetHeight && Math.abs(startR - targetRadius) < 1f)) {
currentAnimator?.cancel()
winParams.width = targetWidth
winParams.height = targetHeight
bg.cornerRadius = targetRadius
currentCornerRadius = targetRadius
val cardLp = card.layoutParams as FrameLayout.LayoutParams
cardLp.width = targetWidth
cardLp.height = targetHeight
card.layoutParams = cardLp
container.removeAllViews()
container.addView(newView)
runCatching { wm.updateViewLayout(root, winParams) }
return
}
newView.alpha = 0f
newView.scaleX = 0.94f
newView.scaleY = 0.94f
container.addView(newView)
for (i in 0 until container.childCount - 1) {
val oldChild = container.getChildAt(i)
oldChild.animate().alpha(0f).setDuration(160).start()
}
newView.animate()
.alpha(1f)
.scaleX(1.0f)
.scaleY(1.0f)
.setDuration(240)
.withEndAction {
while (container.childCount > 1) {
container.removeViewAt(0)
}
}
.start()
currentAnimator?.cancel()
currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
duration = 280
interpolator = OvershootInterpolator(0.6f)
addUpdateListener { anim ->
val f = anim.animatedFraction
val curW = (startW + (targetWidth - startW) * f).toInt()
val curH = (startH + (targetHeight - startH) * f).toInt()
val curR = startR + (targetRadius - startR) * f
currentCornerRadius = curR
bg.cornerRadius = curR
winParams.width = curW
winParams.height = curH
val cardLp = card.layoutParams as FrameLayout.LayoutParams
cardLp.width = curW
cardLp.height = curH
card.layoutParams = cardLp
runCatching { wm.updateViewLayout(root, winParams) }
}
start()
}
}
private fun setupTouchInteractions(context: Context, root: View, card: View) {
val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
handleIslandTap(context)
return true
}
override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
if (e1 == null) return false
val dy = e2.y - e1.y
val dx = e2.x - e1.x
if (Math.abs(dy) > Math.abs(dx)) {
if (dy > dp(context, 18) && currentState == IslandVisualState.COMPACT) {
expandIsland(context)
return true
} else if (dy < -dp(context, 18) && currentState != IslandVisualState.COMPACT) {
collapseIsland(context)
return true
}
} else {
if (Math.abs(dx) > dp(context, 35)) {
handleHorizontalSwipe(context, dx > 0)
return true
}
}
return false
}
override fun onDown(e: MotionEvent): Boolean = true
})
val touchListener = View.OnTouchListener { _, event ->
if (gestureDetector.onTouchEvent(event)) {
return@OnTouchListener true
}
if (event.action == MotionEvent.ACTION_UP) {
return@OnTouchListener true
}
true
}
root.setOnTouchListener(touchListener)
card.setOnClickListener {
handleIslandTap(context)
}
}
private fun handleIslandTap(context: Context) {
val root = persistentRoot ?: return
root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
when (currentState) {
IslandVisualState.IDLE -> {
currentState = IslandVisualState.COMPACT
render(context, animate = true)
scheduleDismiss(context, currentState)
}
IslandVisualState.COMPACT -> {
expandIsland(context)
}
IslandVisualState.EXPANDED,
IslandVisualState.MEDIA_EXPANDED,
IslandVisualState.CALL_EXPANDED -> {
collapseIsland(context)
}
IslandVisualState.REPLY -> {}
}
}
private fun expandIsland(context: Context) {
val root = persistentRoot ?: return
root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
val hasActiveCall = activeNotifications.any { it.isCall }
currentState = if (hasActiveCall) {
IslandVisualState.CALL_EXPANDED
} else if (currentMedia != null && (currentMedia?.isPlaying == true || activeNotifications.isEmpty())) {
expandedTabMode = ExpandedTabMode.MEDIA
IslandVisualState.MEDIA_EXPANDED
} else {
expandedTabMode = ExpandedTabMode.NOTIFICATIONS
IslandVisualState.EXPANDED
}
render(context, animate = true)
scheduleDismiss(context, currentState)
}
private fun collapseIsland(context: Context) {
val root = persistentRoot ?: return
root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
tickerRunnable?.let(main::removeCallbacks)
isAudioOutputExpanded = false
currentState = if (activeNotifications.isEmpty() && currentMedia == null) {
IslandVisualState.IDLE
} else {
IslandVisualState.COMPACT
}
render(context, animate = true)
scheduleDismiss(context, currentState)
}
private fun handleHorizontalSwipe(context: Context, isRightSwipe: Boolean) {
val root = persistentRoot ?: return
root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
if (currentState == IslandVisualState.MEDIA_EXPANDED || (currentState == IslandVisualState.EXPANDED && expandedTabMode == ExpandedTabMode.MEDIA)) {
val media = currentMedia ?: return
if (isRightSwipe) {
runCatching { media.controller.transportControls.skipToNext() }
} else {
runCatching { media.controller.transportControls.skipToPrevious() }
}
} else {
if (activeNotifications.isNotEmpty()) {
activeNotifications.removeAt(0)
}
if (activeNotifications.isEmpty() && currentMedia == null) {
remove()
} else {
render(context, animate = true)
}
}
}
private fun getMediaBackdropDrawable(context: Context, art: Bitmap?, cornerRadius: Float): Drawable {
if (art == null || art.width <= 0 || art.height <= 0) {
return GradientDrawable().apply {
setColor(Color.rgb(10, 11, 15))
this.cornerRadius = cornerRadius
setStroke(dp(context, 1), Color.argb(40, 255, 255, 255))
}
}
return object : Drawable() {
private val artShader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
shader = artShader
}
private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.argb(145, 8, 10, 16) // Balanced frosted glass scrim: rich album artwork radiates through
}
private val specularBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
style = Paint.Style.STROKE
strokeWidth = dp(context, 1f).toFloat()
color = Color.argb(60, 255, 255, 255) // Specular edge
}
private val matrix = Matrix()
private val rectF = RectF()
override fun onBoundsChange(bounds: Rect) {
super.onBoundsChange(bounds)
rectF.set(bounds)
if (art.width > 0 && art.height > 0 && bounds.width() > 0 && bounds.height() > 0) {
val scaleX = bounds.width().toFloat() / art.width.toFloat()
val scaleY = bounds.height().toFloat() / art.height.toFloat()
val scale = Math.max(scaleX, scaleY)
matrix.reset()
matrix.setScale(scale, scale)
val dx = (bounds.width() - art.width * scale) / 2f
val dy = (bounds.height() - art.height * scale) / 2f
matrix.postTranslate(dx, dy)
artShader.setLocalMatrix(matrix)
}
}
override fun draw(canvas: Canvas) {
if (rectF.isEmpty) rectF.set(bounds)
canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, shaderPaint)
canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, scrimPaint)
canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, specularBorderPaint)
}
override fun setAlpha(alpha: Int) {
shaderPaint.alpha = alpha
scrimPaint.alpha = (145 * (alpha / 255f)).toInt()
invalidateSelf()
}
override fun setColorFilter(colorFilter: ColorFilter?) {
shaderPaint.colorFilter = colorFilter
invalidateSelf()
}
override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
}
private fun buildIdleView(context: Context): View {
val row = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT,
)
setOnClickListener {
handleIslandTap(context)
}
}
val dot = View(context).apply {
background = GradientDrawable().apply {
setColor(Color.argb(70, 255, 255, 255))
shape = GradientDrawable.OVAL
}
layoutParams = LinearLayout.LayoutParams(dp(context, 7), dp(context, 7))
}
row.addView(dot)
return row
}
private fun buildCompactView(
context: Context,
notif: IslandNotificationItem?,
media: IslandMediaState?,
count: Int,
): View {
val isSplitMode = media != null && media.isPlaying && count > 0
val row = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(0, 0, 0, 0)
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT,
)
}
if (isSplitMode) {
val vibrantColor = extractVibrantColor(media?.art)
val leftCapsule = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
background = GradientDrawable().apply {
setColor(Color.rgb(10, 11, 15))
cornerRadius = dp(context, 18).toFloat()
setStroke(dp(context, 1), Color.argb(40, 255, 255, 255))
}
setPadding(dp(context, 4), dp(context, 3), dp(context, 4), dp(context, 3))
layoutParams = LinearLayout.LayoutParams(dp(context, 34), dp(context, 36))
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
expandedTabMode = ExpandedTabMode.MEDIA
currentState = IslandVisualState.MEDIA_EXPANDED
render(context, animate = true)
scheduleDismiss(context, currentState)
}
}
val artView = ImageView(context).apply {
scaleType = ImageView.ScaleType.CENTER_CROP
if (media?.art != null) {
setImageBitmap(media.art)
} else {
setImageResource(android.R.drawable.ic_media_play)
}
background = GradientDrawable().apply {
cornerRadius = dp(context, 5).toFloat()
setStroke(dp(context, 1), Color.argb(40, 255, 255, 255))
}
clipToOutline = true
layoutParams = LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)).apply {
marginEnd = dp(context, 2)
}
}
leftCapsule.addView(artView)
val miniWaveform = WaveformVisualizerView(context).apply {
setPlaying(true)
setWaveformColor(vibrantColor)
layoutParams = LinearLayout.LayoutParams(dp(context, 10), dp(context, 10))
}
leftCapsule.addView(miniWaveform)
row.addView(leftCapsule)
val cutoutW = getCutoutWidth(context)
val centerSpacer = View(context).apply {
layoutParams = LinearLayout.LayoutParams(cutoutW + dp(context, 8), 1)
setOnClickListener {
handleIslandTap(context)
}
}
row.addView(centerSpacer)
val rightBubble = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER
background = GradientDrawable().apply {
setColor(Color.rgb(10, 11, 15))
cornerRadius = dp(context, 18).toFloat()
setStroke(dp(context, 1), Color.argb(40, 255, 255, 255))
}
setPadding(dp(context, 4), dp(context, 3), dp(context, 4), dp(context, 3))
layoutParams = LinearLayout.LayoutParams(dp(context, 34), dp(context, 36))
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
if (notif?.pendingIntent != null) {
runCatching { notif.pendingIntent.send() }
onNotificationRemoved(notif.key)
collapseIsland(context)
} else if (notif?.isCall == true) {
currentState = IslandVisualState.CALL_EXPANDED
render(context, animate = true)
scheduleDismiss(context, currentState)
} else {
expandedTabMode = ExpandedTabMode.NOTIFICATIONS
currentState = IslandVisualState.EXPANDED
render(context, animate = true)
scheduleDismiss(context, currentState)
}
}
setOnLongClickListener {
it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
if (notif?.isCall == true) {
currentState = IslandVisualState.CALL_EXPANDED
} else {
expandedTabMode = ExpandedTabMode.NOTIFICATIONS
currentState = IslandVisualState.EXPANDED
}
render(context, animate = true)
scheduleDismiss(context, currentState)
true
}
}
if (notif?.isCall == true) {
val callIcon = IslandVectorIconView(context, IslandVectorType.CALL, Color.rgb(74, 222, 128)).apply {
layoutParams = LinearLayout.LayoutParams(dp(context, 18), dp(context, 18))
}
rightBubble.addView(callIcon)
} else if (notif?.appIcon != null) {
val notifIcon = ImageView(context).apply {
scaleType = ImageView.ScaleType.CENTER_CROP
setImageDrawable(notif.appIcon)
layoutParams = LinearLayout.LayoutParams(dp(context, 18), dp(context, 18))
}
rightBubble.addView(notifIcon)
if (count > 1) {
val countBadge = TextView(context).apply {
text = "$count"
textSize = 10f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
setPadding(dp(context, 2), 0, 0, 0)
}
rightBubble.addView(countBadge)
}
} else {
val badgeTv = TextView(context).apply {
text = "$count"
textSize = 11f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
}
rightBubble.addView(badgeTv)
}
row.addView(rightBubble)
return row
}
row.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
val handleUnifiedCompactClick: (View) -> Unit = { v ->
v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
if (notif?.pendingIntent != null) {
runCatching { notif.pendingIntent.send() }
onNotificationRemoved(notif.key)
collapseIsland(context)
} else if (media != null) {
expandedTabMode = ExpandedTabMode.MEDIA
currentState = IslandVisualState.MEDIA_EXPANDED
render(context, animate = true)
scheduleDismiss(context, currentState)
} else if (notif?.isCall == true) {
currentState = IslandVisualState.CALL_EXPANDED
render(context, animate = true)
scheduleDismiss(context, currentState)
} else {
expandIsland(context)
}
}
val handleUnifiedCompactLongClick: (View) -> Boolean = { v ->
v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
expandIsland(context)
true
}
row.setOnClickListener(handleUnifiedCompactClick)
row.setOnLongClickListener(handleUnifiedCompactLongClick)
val leftContainer = FrameLayout(context).apply {
layoutParams = LinearLayout.LayoutParams(dp(context, if (count > 1) 28 else 24), dp(context, 24))
setOnClickListener(handleUnifiedCompactClick)
setOnLongClickListener(handleUnifiedCompactLongClick)
}
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
if (media?.art != null) {
setImageBitmap(media.art)
} else if (notif?.appIcon != null) {
setImageDrawable(notif.appIcon)
} else {
setImageResource(android.R.drawable.ic_dialog_info)
}
background = GradientDrawable().apply {
cornerRadius = dp(context, 6).toFloat()
setStroke(dp(context, 1), Color.argb(30, 255, 255, 255))
}
clipToOutline = true
layoutParams = FrameLayout.LayoutParams(dp(context, 22), dp(context, 22)).apply {
gravity = Gravity.CENTER_VERTICAL or Gravity.END
}
}
leftContainer.addView(iconView)
row.addView(leftContainer)
val spacer = View(context).apply {
layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
setOnClickListener {
handleIslandTap(context)
}
}
row.addView(spacer)
val rightContainer = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setOnClickListener(handleUnifiedCompactClick)
setOnLongClickListener(handleUnifiedCompactLongClick)
}
if (media != null && media.isPlaying) {
val vibrantColor = extractVibrantColor(media.art)
val waveform = WaveformVisualizerView(context).apply {
setPlaying(true)
setWaveformColor(vibrantColor)
layoutParams = LinearLayout.LayoutParams(dp(context, 26), dp(context, 14)).apply {
marginEnd = if (count > 0) dp(context, 4) else dp(context, 2)
}
}
rightContainer.addView(waveform)
}
if (count > 0) {
val badge = TextView(context).apply {
text = if (count > 1) "$count" else "●"
textSize = if (count > 1) 11f else 9f
typeface = Typeface.DEFAULT_BOLD
setTextColor(if (notif?.isCall == true) Color.rgb(74, 222, 128) else Color.WHITE)
gravity = Gravity.CENTER
background = GradientDrawable().apply {
setColor(if (count > 1) Color.argb(45, 255, 255, 255) else Color.TRANSPARENT)
cornerRadius = dp(context, 10).toFloat()
setStroke(dp(context, 1), Color.argb(30, 255, 255, 255))
}
setPadding(dp(context, 5), dp(context, 1), dp(context, 5), dp(context, 1))
}
rightContainer.addView(badge)
} else if (media == null || !media.isPlaying) {
val dot = View(context).apply {
background = GradientDrawable().apply {
setColor(Color.argb(80, 255, 255, 255))
shape = GradientDrawable.OVAL
}
layoutParams = LinearLayout.LayoutParams(dp(context, 7), dp(context, 7))
}
rightContainer.addView(dot)
}
row.addView(rightContainer)
return row
}
private fun buildCallExpandedView(context: Context, item: IslandNotificationItem): View {
val layout = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
gravity = Gravity.CENTER_HORIZONTAL
val topPad = (getCutoutTopOffset(context) - dp(context, 2)).coerceAtLeast(dp(context, 10))
setPadding(dp(context, 16), topPad, dp(context, 16), dp(context, 14))
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT,
)
}
val topRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(0, 0, 0, dp(context, 6))
}
val appIconView = ImageView(context).apply {
scaleType = ImageView.ScaleType.CENTER_CROP
if (item.appIcon != null) setImageDrawable(item.appIcon)
else setImageResource(android.R.drawable.sym_action_call)
layoutParams = LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply {
marginEnd = dp(context, 6)
}
}
topRow.addView(appIconView)
val appLabelTv = TextView(context).apply {
text = appLabel(context, item.packageName)
textSize = 11.5f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.rgb(148, 163, 184))
}
topRow.addView(appLabelTv, LinearLayout.LayoutParams(0, -2, 1f))
val callBadge = TextView(context).apply {
text = "INCOMING CALL"
textSize = 9.5f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.rgb(34, 197, 94))
background = GradientDrawable().apply {
setColor(Color.argb(35, 34, 197, 94))
cornerRadius = dp(context, 8).toFloat()
}
setPadding(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 2))
}
topRow.addView(callBadge)
layout.addView(topRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2))
val centerCol = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
gravity = Gravity.CENTER_HORIZONTAL
setPadding(0, dp(context, 4), 0, dp(context, 12))
}
val initialLetter = item.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
val avatarContainer = FrameLayout(context).apply {
background = GradientDrawable().apply {
shape = GradientDrawable.OVAL
setColor(getAvatarColorForName(item.title))
setStroke(dp(context, 2), Color.argb(120, 34, 197, 94))
}
layoutParams = LinearLayout.LayoutParams(dp(context, 54), dp(context, 54))
}
val avatarText = TextView(context).apply {
text = initialLetter
textSize = 22f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
gravity = Gravity.CENTER
layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
}
avatarContainer.addView(avatarText)
centerCol.addView(avatarContainer)
val callerNameTv = TextView(context).apply {
text = item.title
textSize = 17f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
gravity = Gravity.CENTER
isSingleLine = true
ellipsize = TextUtils.TruncateAt.END
setPadding(0, dp(context, 6), 0, 0)
}
centerCol.addView(callerNameTv)
val callSubtitleTv = TextView(context).apply {
text = if (item.body.isNotBlank() && item.body != "Call notification") item.body else "Chaty Audio Call"
textSize = 12f
setTextColor(Color.rgb(156, 163, 175))
gravity = Gravity.CENTER
isSingleLine = true
}
centerCol.addView(callSubtitleTv)
layout.addView(centerCol)
val actionsRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER
setPadding(dp(context, 20), 0, dp(context, 20), 0)
layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2)
}
val declineBtn = FrameLayout(context).apply {
background = GradientDrawable().apply {
shape = GradientDrawable.OVAL
setColor(Color.rgb(239, 68, 68))
setStroke(dp(context, 1), Color.argb(100, 255, 255, 255))
}
val declineIcon = IslandVectorIconView(context, IslandVectorType.CALL_END, Color.WHITE).apply {
layoutParams = FrameLayout.LayoutParams(dp(context, 24), dp(context, 24), Gravity.CENTER)
}
addView(declineIcon)
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
val declineAction = item.actions.firstOrNull { a ->
a.title.contains("decline", ignoreCase = true) ||
a.title.contains("dismiss", ignoreCase = true) ||
a.title.contains("hang", ignoreCase = true) ||
a.title.contains("reject", ignoreCase = true)
}
runCatching {
declineAction?.pendingIntent?.send() ?: item.pendingIntent?.send()
}
onNotificationRemoved(item.key)
}
}
actionsRow.addView(declineBtn, LinearLayout.LayoutParams(dp(context, 50), dp(context, 50)))
val spacer = View(context).apply {
layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
}
actionsRow.addView(spacer)
val acceptBtn = FrameLayout(context).apply {
background = GradientDrawable().apply {
shape = GradientDrawable.OVAL
setColor(Color.rgb(34, 197, 94))
setStroke(dp(context, 1), Color.argb(120, 255, 255, 255))
}
val acceptIcon = IslandVectorIconView(context, IslandVectorType.CALL, Color.WHITE).apply {
layoutParams = FrameLayout.LayoutParams(dp(context, 24), dp(context, 24), Gravity.CENTER)
}
addView(acceptIcon)
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
val acceptAction = item.actions.firstOrNull { a ->
a.title.contains("answer", ignoreCase = true) ||
a.title.contains("accept", ignoreCase = true) ||
a.title.contains("call", ignoreCase = true)
}
runCatching {
acceptAction?.pendingIntent?.send() ?: item.pendingIntent?.send()
}
collapseIsland(context)
}
}
actionsRow.addView(acceptBtn, LinearLayout.LayoutParams(dp(context, 50), dp(context, 50)))
layout.addView(actionsRow)
return layout
}
private fun buildExpandedStackView(context: Context, targetHeight: Int): View {
val rootLayout = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT,
)
}
if (activeNotifications.isEmpty()) {
val emptyTv = TextView(context).apply {
text = "No active notifications"
setTextColor(Color.LTGRAY)
textSize = 12f
gravity = Gravity.CENTER
}
rootLayout.addView(emptyTv)
return rootLayout
}
val showHeader = DynamicIslandNotificationListener.isHeaderEnabled(context)
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
val rightControls = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
}
if (currentMedia != null) {
val vibrantColor = extractVibrantColor(currentMedia?.art)
val ghostMusicBtn = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
background = GradientDrawable().apply {
setColor(Color.argb(35, Color.red(vibrantColor), Color.green(vibrantColor), Color.blue(vibrantColor)))
cornerRadius = dp(context, 12).toFloat()
setStroke(dp(context, 1), Color.argb(80, Color.red(vibrantColor), Color.green(vibrantColor), Color.blue(vibrantColor)))
}
setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3))
val noteIcon = IslandVectorIconView(context, IslandVectorType.MUSIC_NOTE, vibrantColor).apply {
layoutParams = LinearLayout.LayoutParams(dp(context, 12), dp(context, 12)).apply {
marginEnd = dp(context, 4)
}
}
addView(noteIcon)
val trackLabel = TextView(context).apply {
text = if (!currentMedia!!.title.isNullOrBlank()) currentMedia!!.title else "Playing"
textSize = 10.5f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
maxLines = 1
maxWidth = dp(context, 90)
ellipsize = TextUtils.TruncateAt.END
}
addView(trackLabel)
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
expandedTabMode = ExpandedTabMode.MEDIA
currentState = IslandVisualState.MEDIA_EXPANDED
render(context, animate = true)
}
}
rightControls.addView(ghostMusicBtn, LinearLayout.LayoutParams(-2, -2).apply {
marginEnd = dp(context, 8)
})
}
val clearAllBtn = TextView(context).apply {
text = "Clear All"
textSize = 11f
setTextColor(Color.rgb(180, 190, 205))
setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3))
background = GradientDrawable().apply {
setColor(Color.argb(30, 255, 255, 255))
cornerRadius = dp(context, 8).toFloat()
setStroke(dp(context, 1), Color.argb(30, 255, 255, 255))
}
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
clearAllNotifications()
}
}
rightControls.addView(clearAllBtn)
header.addView(rightControls)
rootLayout.addView(header)
val topBarHeight = dp(context, 50)
val scrollH = (targetHeight - topBarHeight).coerceAtLeast(dp(context, 90))
val scrollView = ScrollView(context).apply {
isVerticalScrollBarEnabled = false
overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
scrollH,
)
}
val itemsContainer = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
}
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
rootLayout.addView(scrollView)
return rootLayout
}
private fun buildNotificationCardView(
context: Context,
item: IslandNotificationItem,
isTop: Boolean,
): View {
val card = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
background = GradientDrawable().apply {
setColor(if (isTop) Color.argb(180, 24, 30, 42) else Color.argb(140, 18, 22, 30))
cornerRadius = dp(context, 16).toFloat()
setStroke(dp(context, 1), Color.argb(30, 255, 255, 255))
}
}
val topRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.TOP
}
val iconContainer = FrameLayout(context).apply {
background = GradientDrawable().apply {
cornerRadius = dp(context, 8).toFloat()
}
clipToOutline = true
layoutParams = LinearLayout.LayoutParams(dp(context, 32), dp(context, 32)).apply {
topMargin = dp(context, 2)
}
}
val iconView = ImageView(context).apply {
scaleType = ImageView.ScaleType.CENTER_CROP
if (item.appIcon != null) setImageDrawable(item.appIcon)
else setImageResource(android.R.drawable.ic_dialog_info)
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT,
)
}
iconContainer.addView(iconView)
topRow.addView(iconContainer)
val textCol = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
setPadding(dp(context, 10), 0, dp(context, 6), 0)
}
val titleTimeRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
}
val titleTv = TextView(context).apply {
text = item.title
textSize = 12.5f
maxLines = 1
ellipsize = TextUtils.TruncateAt.END
setTextColor(Color.WHITE)
typeface = Typeface.DEFAULT_BOLD
}
titleTimeRow.addView(titleTv, LinearLayout.LayoutParams(0, -2, 1f))
val timeTv = TextView(context).apply {
text = formatRelativeTime(item.timestamp)
textSize = 10f
setTextColor(Color.rgb(148, 163, 184))
setPadding(dp(context, 4), 0, 0, 0)
}
titleTimeRow.addView(timeTv)
textCol.addView(titleTimeRow)
var isExpanded = false
val bodyTv = TextView(context).apply {
text = item.body
textSize = 11.5f
maxLines = 2
ellipsize = TextUtils.TruncateAt.END
setTextColor(Color.rgb(203, 213, 225))
setPadding(0, dp(context, 2), 0, 0)
}
textCol.addView(bodyTv)
val subMessagesContainer = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
visibility = View.GONE
setPadding(0, dp(context, 4), 0, 0)
}
for (subMsg in item.subMessages) {
val subRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(dp(context, 6), dp(context, 3), dp(context, 6), dp(context, 3))
background = GradientDrawable().apply {
setColor(Color.argb(25, 255, 255, 255))
cornerRadius = dp(context, 8).toFloat()
}
layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2).apply {
topMargin = dp(context, 2)
bottomMargin = dp(context, 2)
}
}
val dot = View(context).apply {
background = GradientDrawable().apply {
shape = GradientDrawable.OVAL
setColor(Color.rgb(96, 165, 250))
}
layoutParams = LinearLayout.LayoutParams(dp(context, 4), dp(context, 4)).apply {
marginEnd = dp(context, 6)
}
}
val subTv = TextView(context).apply {
text = subMsg
textSize = 11f
setTextColor(Color.rgb(226, 232, 240))
maxLines = 4
ellipsize = TextUtils.TruncateAt.END
}
subRow.addView(dot)
subRow.addView(subTv, LinearLayout.LayoutParams(0, -2, 1f))
subMessagesContainer.addView(subRow)
}
textCol.addView(subMessagesContainer)
topRow.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
val rightBadgeCol = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
gravity = Gravity.CENTER_HORIZONTAL
setPadding(dp(context, 2), 0, 0, 0)
}
val initialLetter = item.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
val avatarCircle = TextView(context).apply {
text = initialLetter
textSize = 11.5f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
gravity = Gravity.CENTER
background = GradientDrawable().apply {
shape = GradientDrawable.OVAL
setColor(getAvatarColorForName(item.title))
}
layoutParams = LinearLayout.LayoutParams(dp(context, 24), dp(context, 24))
}
rightBadgeCol.addView(avatarCircle)
val chevronIcon = IslandVectorIconView(context, IslandVectorType.CHEVRON_DOWN, Color.rgb(156, 163, 175)).apply {
layoutParams = FrameLayout.LayoutParams(dp(context, 12), dp(context, 12), Gravity.CENTER)
}
val chevronBtn = FrameLayout(context).apply {
addView(chevronIcon)
setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
isExpanded = !isExpanded
bodyTv.maxLines = if (isExpanded) 12 else 2
chevronIcon.vectorType = if (isExpanded) IslandVectorType.CHEVRON_UP else IslandVectorType.CHEVRON_DOWN
chevronIcon.invalidate()
if (item.subMessages.isNotEmpty()) {
subMessagesContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
}
}
}
rightBadgeCol.addView(chevronBtn, LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)))
topRow.addView(rightBadgeCol)
card.addView(topRow)
topRow.setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
runCatching { item.pendingIntent?.send() }
onNotificationRemoved(item.key)
collapseIsland(context)
}
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
setColor(Color.argb(220, 37, 99, 235)) // Liquid Glass Accent Blue
cornerRadius = dp(context, 12).toFloat()
setStroke(dp(context, 1), Color.argb(80, 255, 255, 255))
}
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
activeReplyItem = item
currentState = IslandVisualState.REPLY
render(context, animate = true)
}
}
actionsRow.addView(replyBtn, LinearLayout.LayoutParams(-2, dp(context, 32)).apply {
marginEnd = dp(context, 6)
})
}
val filteredActions = item.actions.filter { action ->
!action.title.equals("reply", ignoreCase = true) &&
(item.replyAction == null || !action.title.contains("reply", ignoreCase = true))
}
for (action in filteredActions) {
val btn = Button(context).apply {
text = action.title
textSize = 11f
isAllCaps = false
setTextColor(Color.WHITE)
background = GradientDrawable().apply {
setColor(Color.argb(45, 255, 255, 255))
cornerRadius = dp(context, 12).toFloat()
setStroke(dp(context, 1), Color.argb(30, 255, 255, 255))
}
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
runCatching { action.pendingIntent.send() }
onNotificationRemoved(item.key)
}
}
actionsRow.addView(btn, LinearLayout.LayoutParams(-2, dp(context, 32)).apply {
marginStart = dp(context, 4)
})
}
val dismissBtn = FrameLayout(context).apply {
val closeIcon = IslandVectorIconView(context, IslandVectorType.CLOSE, Color.rgb(150, 155, 165)).apply {
layoutParams = FrameLayout.LayoutParams(dp(context, 14), dp(context, 14), Gravity.CENTER)
}
addView(closeIcon)
setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6))
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
onNotificationRemoved(item.key)
}
}
actionsRow.addView(dismissBtn, LinearLayout.LayoutParams(dp(context, 32), dp(context, 32)).apply {
marginStart = dp(context, 4)
})
card.addView(actionsRow)
var startX = 0f
var startY = 0f
var isHorizontalSwipe = false
card.setOnTouchListener { v, event ->
when (event.action) {
MotionEvent.ACTION_DOWN -> {
startX = event.rawX
startY = event.rawY
isHorizontalSwipe = false
false
}
MotionEvent.ACTION_MOVE -> {
val dx = event.rawX - startX
val dy = event.rawY - startY
if (!isHorizontalSwipe && Math.abs(dx) > dp(context, 12) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
isHorizontalSwipe = true
v.parent?.requestDisallowInterceptTouchEvent(true)
}
if (isHorizontalSwipe) {
v.translationX = dx
val fraction = (Math.abs(dx) / (v.width.toFloat() * 0.75f)).coerceIn(0f, 1f)
v.alpha = 1f - fraction * 0.7f
true
} else {
false
}
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
if (isHorizontalSwipe) {
v.parent?.requestDisallowInterceptTouchEvent(false)
val dx = event.rawX - startX
val threshold = v.width * 0.35f
if (Math.abs(dx) > threshold) {
val targetX = if (dx > 0) v.width.toFloat() else -v.width.toFloat()
v.animate()
.translationX(targetX)
.alpha(0f)
.setDuration(180)
.withEndAction {
v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
onNotificationRemoved(item.key)
}
.start()
} else {
v.animate().translationX(0f).alpha(1f).setDuration(160).start()
}
true
} else {
val dx = event.rawX - startX
val dy = event.rawY - startY
if (Math.abs(dx) < dp(context, 8) && Math.abs(dy) < dp(context, 8)) {
v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
runCatching { item.pendingIntent?.send() }
onNotificationRemoved(item.key)
collapseIsland(context)
true
} else {
false
}
}
}
else -> false
}
}
return card
}
private fun buildReplyView(context: Context, notif: IslandNotificationItem?): View {
val targetItem = notif ?: activeNotifications.firstOrNull()
val layout = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
val topPad = (getCutoutTopOffset(context) - dp(context, 4)).coerceAtLeast(dp(context, 8))
setPadding(dp(context, 14), topPad, dp(context, 14), dp(context, 10))
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT,
)
}
if (targetItem?.replyAction == null) {
currentState = IslandVisualState.EXPANDED
render(context, animate = true)
return layout
}
val headerRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(0, 0, 0, dp(context, 6))
}
val titleView = TextView(context).apply {
text = "Reply to ${targetItem.title}"
textSize = 12.5f
setTextColor(Color.rgb(160, 200, 255))
typeface = Typeface.DEFAULT_BOLD
}
headerRow.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
val closeBtn = FrameLayout(context).apply {
val closeIcon = IslandVectorIconView(context, IslandVectorType.CLOSE, Color.rgb(156, 163, 175)).apply {
layoutParams = FrameLayout.LayoutParams(dp(context, 14), dp(context, 14), Gravity.CENTER)
}
addView(closeIcon)
setPadding(dp(context, 6), dp(context, 4), dp(context, 6), dp(context, 4))
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
activeReplyItem = null
currentState = IslandVisualState.EXPANDED
render(context, animate = true)
}
}
headerRow.addView(closeBtn)
layout.addView(headerRow)
val quoteBox = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
background = GradientDrawable().apply {
setColor(Color.argb(35, 255, 255, 255))
cornerRadius = dp(context, 10).toFloat()
}
setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
-2,
).apply {
bottomMargin = dp(context, 8)
}
}
val quoteAccent = View(context).apply {
background = GradientDrawable().apply {
setColor(Color.rgb(37, 99, 235))
cornerRadius = dp(context, 2).toFloat()
}
layoutParams = LinearLayout.LayoutParams(dp(context, 3), dp(context, 26)).apply {
marginEnd = dp(context, 8)
}
}
quoteBox.addView(quoteAccent)
val quoteCol = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
}
val quoteTitleRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
}
val quoteAuthor = TextView(context).apply {
text = targetItem.title
textSize = 11f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.rgb(148, 163, 184))
}
quoteTitleRow.addView(quoteAuthor, LinearLayout.LayoutParams(0, -2, 1f))
val quoteTime = TextView(context).apply {
text = formatRelativeTime(targetItem.timestamp)
textSize = 10f
setTextColor(Color.rgb(100, 116, 139))
}
quoteTitleRow.addView(quoteTime)
quoteCol.addView(quoteTitleRow)
val quoteSnippet = TextView(context).apply {
text = targetItem.body
textSize = 11.5f
maxLines = 2
ellipsize = TextUtils.TruncateAt.END
setTextColor(Color.rgb(226, 232, 240))
}
quoteCol.addView(quoteSnippet)
quoteBox.addView(quoteCol, LinearLayout.LayoutParams(0, -2, 1f))
layout.addView(quoteBox)
val inputRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
}
val firstName = targetItem.title.split(" ").firstOrNull() ?: ""
val editText = EditText(context).apply {
hint = if (firstName.isNotEmpty()) "Reply to $firstName..." else "Type reply..."
setHintTextColor(Color.rgb(100, 116, 139))
setTextColor(Color.WHITE)
textSize = 13f
imeOptions = EditorInfo.IME_ACTION_SEND
inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
background = GradientDrawable().apply {
setColor(Color.rgb(20, 24, 32))
cornerRadius = dp(context, 14).toFloat()
setStroke(dp(context, 1), Color.argb(60, 255, 255, 255))
}
setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
}
inputRow.addView(editText, LinearLayout.LayoutParams(0, dp(context, 40), 1f))
val sendBtn = FrameLayout(context).apply {
background = GradientDrawable().apply {
setColor(Color.rgb(37, 99, 235)) // Chaty Accent Blue
cornerRadius = dp(context, 14).toFloat()
}
val sendIcon = IslandVectorIconView(context, IslandVectorType.SEND, Color.WHITE).apply {
layoutParams = FrameLayout.LayoutParams(dp(context, 18), dp(context, 18), Gravity.CENTER)
}
addView(sendIcon)
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp(context, 46), dp(context, 40)).apply {
marginStart = dp(context, 8)
})
layout.addView(inputRow)
main.postDelayed({
editText.requestFocus()
val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
}, 150)
return layout
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
private fun buildMediaExpandedView(context: Context, media: IslandMediaState?): View {
val layout = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
val topPad = (getCutoutTopOffset(context) - dp(context, 4)).coerceAtLeast(dp(context, 8))
setPadding(dp(context, 14), topPad, dp(context, 14), dp(context, 10))
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT,
)
}
if (media == null) {
val emptyTv = TextView(context).apply {
text = "No active audio playback"
setTextColor(Color.LTGRAY)
textSize = 12f
gravity = Gravity.CENTER
}
layout.addView(emptyTv)
return layout
}
val showHeader = DynamicIslandNotificationListener.isHeaderEnabled(context)
val vibrantColor = extractVibrantColor(media.art)
val darkerVibrant = darkerShade(vibrantColor)
if (isAudioOutputExpanded) {
val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
val panelHeader = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(0, 0, 0, dp(context, 10))
}
val panelTitle = TextView(context).apply {
text = "Audio Output & Control"
textSize = 13f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
}
panelHeader.addView(panelTitle, LinearLayout.LayoutParams(0, -2, 1f))
val closePanelBtn = FrameLayout(context).apply {
val closeIcon = IslandVectorIconView(context, IslandVectorType.CLOSE, Color.rgb(180, 190, 205)).apply {
layoutParams = FrameLayout.LayoutParams(dp(context, 16), dp(context, 16), Gravity.CENTER)
}
addView(closeIcon)
setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6))
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
isAudioOutputExpanded = false
render(context, animate = true)
}
}
panelHeader.addView(closePanelBtn)
layout.addView(panelHeader)
val phoneDeviceRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
background = GradientDrawable().apply {
setColor(Color.argb(50, 34, 197, 94))
cornerRadius = dp(context, 12).toFloat()
setStroke(dp(context, 1), Color.argb(100, 34, 197, 94))
}
setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
}
val speakerIcon = IslandVectorIconView(context, IslandVectorType.SPEAKER, Color.rgb(34, 197, 94)).apply {
layoutParams = LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply {
marginEnd = dp(context, 10)
}
}
phoneDeviceRow.addView(speakerIcon)
val phoneLabelCol = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
}
val devTitle = TextView(context).apply {
text = "This Phone (Speaker)"
textSize = 12.5f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
}
phoneLabelCol.addView(devTitle)
val devSub = TextView(context).apply {
text = "Playing via device speaker · Active"
textSize = 10f
setTextColor(Color.rgb(134, 239, 172))
}
phoneLabelCol.addView(devSub)
phoneDeviceRow.addView(phoneLabelCol, LinearLayout.LayoutParams(0, -2, 1f))
layout.addView(phoneDeviceRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2).apply {
bottomMargin = dp(context, 8)
})
val btDeviceRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
background = GradientDrawable().apply {
setColor(Color.argb(35, 255, 255, 255))
cornerRadius = dp(context, 12).toFloat()
setStroke(dp(context, 1), Color.argb(40, 255, 255, 255))
}
setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
runCatching {
val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
flags = Intent.FLAG_ACTIVITY_NEW_TASK
}
context.startActivity(intent)
}
}
}
val btIcon = IslandVectorIconView(context, IslandVectorType.PHONE, Color.WHITE).apply {
layoutParams = LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply {
marginEnd = dp(context, 10)
}
}
btDeviceRow.addView(btIcon)
val btLabelCol = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
}
val btTitle = TextView(context).apply {
text = "Bluetooth & Audio Output"
textSize = 12f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
}
btLabelCol.addView(btTitle)
val btSub = TextView(context).apply {
text = "Tap to switch to headphones or car"
textSize = 10f
setTextColor(Color.rgb(148, 163, 184))
}
btLabelCol.addView(btSub)
btDeviceRow.addView(btLabelCol, LinearLayout.LayoutParams(0, -2, 1f))
layout.addView(btDeviceRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2).apply {
bottomMargin = dp(context, 10)
})
val volumeRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(dp(context, 2), 0, dp(context, 2), 0)
}
val volIcon = IslandVectorIconView(context, IslandVectorType.VOLUME, Color.WHITE).apply {
layoutParams = LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply {
marginEnd = dp(context, 8)
}
}
volumeRow.addView(volIcon)
val volSeekBar = SeekBar(context).apply {
max = maxVol
progress = curVol
setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
if (fromUser) {
runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0) }
}
}
override fun onStartTrackingTouch(sb: SeekBar?) {}
override fun onStopTrackingTouch(sb: SeekBar?) {
performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}
})
}
volumeRow.addView(volSeekBar, LinearLayout.LayoutParams(0, -2, 1f))
layout.addView(volumeRow)
return layout
}
val headerRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(0, 0, 0, dp(context, 4))
}
val leftAppBadge = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
}
val appIconContainer = FrameLayout(context).apply {
background = GradientDrawable().apply {
cornerRadius = dp(context, 6).toFloat()
}
clipToOutline = true
layoutParams = LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply {
marginEnd = dp(context, 6)
}
}
val appIconView = ImageView(context).apply {
scaleType = ImageView.ScaleType.CENTER_CROP
if (media.art != null) {
setImageBitmap(media.art)
} else {
setImageResource(android.R.drawable.ic_media_play)
setColorFilter(Color.rgb(239, 68, 68)) // YouTube Red
}
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT,
)
}
appIconContainer.addView(appIconView)
leftAppBadge.addView(appIconContainer)
val appNameTv = TextView(context).apply {
text = if (media.artist.isNotBlank()) media.artist else "YouTube"
textSize = 11.5f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
}
leftAppBadge.addView(appNameTv)
headerRow.addView(leftAppBadge, LinearLayout.LayoutParams(0, -2, 1f))
val rightHeaderControls = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
}
val phoneOutputChip = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
background = GradientDrawable().apply {
setColor(Color.argb(35, 255, 255, 255))
cornerRadius = dp(context, 12).toFloat()
setStroke(dp(context, 1), Color.argb(60, 255, 255, 255))
}
setPadding(dp(context, 7), dp(context, 4), dp(context, 9), dp(context, 4))
val phoneIcon = IslandVectorIconView(context, IslandVectorType.PHONE, Color.rgb(203, 213, 225)).apply {
layoutParams = LinearLayout.LayoutParams(dp(context, 12), dp(context, 12)).apply {
marginEnd = dp(context, 5)
}
}
addView(phoneIcon)
val phoneLabel = TextView(context).apply {
text = "This phone"
textSize = 10.5f
setTextColor(Color.rgb(226, 232, 240))
}
addView(phoneLabel)
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
isAudioOutputExpanded = !isAudioOutputExpanded
render(context, animate = true)
}
}
rightHeaderControls.addView(phoneOutputChip)
if (activeNotifications.isNotEmpty()) {
val notifBadgeBtn = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
background = GradientDrawable().apply {
setColor(Color.argb(40, 255, 255, 255))
cornerRadius = dp(context, 12).toFloat()
setStroke(dp(context, 1), Color.argb(60, 255, 255, 255))
}
setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3))
val bellIcon = IslandVectorIconView(context, IslandVectorType.BELL, Color.WHITE).apply {
layoutParams = LinearLayout.LayoutParams(dp(context, 12), dp(context, 12)).apply {
marginEnd = dp(context, 4)
}
}
addView(bellIcon)
val countTv = TextView(context).apply {
text = "${activeNotifications.size} new"
textSize = 10.5f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
}
addView(countTv)
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
expandedTabMode = ExpandedTabMode.NOTIFICATIONS
currentState = IslandVisualState.EXPANDED
render(context, animate = true)
}
}
rightHeaderControls.addView(notifBadgeBtn, LinearLayout.LayoutParams(-2, -2).apply {
marginStart = dp(context, 6)
})
}
headerRow.addView(rightHeaderControls)
layout.addView(headerRow)
val trackRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(0, dp(context, 4), 0, dp(context, 4))
}
val artContainer = FrameLayout(context).apply {
background = GradientDrawable().apply {
cornerRadius = dp(context, 12).toFloat()
setStroke(dp(context, 1), Color.argb(50, 255, 255, 255))
}
clipToOutline = true
layoutParams = LinearLayout.LayoutParams(dp(context, 48), dp(context, 48))
}
val artView = ImageView(context).apply {
scaleType = ImageView.ScaleType.CENTER_CROP
if (media.art != null) {
setImageBitmap(media.art)
} else {
setImageResource(android.R.drawable.ic_media_play)
setColorFilter(Color.rgb(96, 165, 250))
}
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT,
)
}
artContainer.addView(artView)
artContainer.setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
runCatching { media.controller.sessionActivity?.send() }
}
trackRow.addView(artContainer)
val textCol = LinearLayout(context).apply {
orientation = LinearLayout.VERTICAL
setPadding(dp(context, 10), 0, dp(context, 4), 0)
}
val titleView = TextView(context).apply {
text = media.title
textSize = 14f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
isSingleLine = true
ellipsize = TextUtils.TruncateAt.MARQUEE
marqueeRepeatLimit = -1
isSelected = true
}
textCol.addView(titleView)
val artistView = TextView(context).apply {
text = media.artist
textSize = 12f
setTextColor(Color.rgb(156, 163, 175))
isSingleLine = true
ellipsize = TextUtils.TruncateAt.END
}
textCol.addView(artistView)
trackRow.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
layout.addView(trackRow)
val realtimeStartPos = DynamicIslandNotificationListener.getRealtimePosition(media.controller.playbackState)
val wavyScrubber = WavyAudioScrubberView(context).apply {
durationMs = media.duration
isPlaying = media.isPlaying
setAccentColor(vibrantColor)
progress = if (media.duration > 0) (realtimeStartPos.toFloat() / media.duration).coerceIn(0f, 1f) else 0f
onSeekListener = { targetMs ->
runCatching { media.controller.transportControls.seekTo(targetMs) }
}
}
layout.addView(wavyScrubber, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 26)))
val timeRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(dp(context, 2), 0, dp(context, 2), 0)
}
val elapsedText = TextView(context).apply {
text = formatTimestamp(realtimeStartPos)
textSize = 10f
setTextColor(Color.rgb(156, 163, 175))
}
timeRow.addView(elapsedText, LinearLayout.LayoutParams(0, -2, 1f))
val totalText = TextView(context).apply {
text = if (media.duration > 0) formatTimestamp(media.duration) else "--:--"
textSize = 10f
setTextColor(Color.rgb(156, 163, 175))
gravity = Gravity.END
}
timeRow.addView(totalText, LinearLayout.LayoutParams(-2, -2))
layout.addView(timeRow)
startMediaTicker(media, wavyScrubber, elapsedText)
val controlsRow = LinearLayout(context).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER
setPadding(0, dp(context, 6), 0, 0)
}
val prevBtn = FrameLayout(context).apply {
background = GradientDrawable().apply {
shape = GradientDrawable.OVAL
setColor(Color.argb(35, 255, 255, 255))
setStroke(dp(context, 1), Color.argb(60, 255, 255, 255))
}
val prevIcon = IslandVectorIconView(context, IslandVectorType.PREVIOUS, Color.WHITE).apply {
layoutParams = FrameLayout.LayoutParams(dp(context, 16), dp(context, 16), Gravity.CENTER)
}
addView(prevIcon)
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
runCatching { media.controller.transportControls.skipToPrevious() }
}
}
controlsRow.addView(prevBtn, LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)).apply {
marginEnd = dp(context, 20)
})
val playBtn = FrameLayout(context).apply {
background = GradientDrawable(
GradientDrawable.Orientation.TL_BR,
intArrayOf(vibrantColor, darkerVibrant)
).apply {
shape = GradientDrawable.OVAL
setStroke(dp(context, 1.5f), Color.argb(160, 255, 255, 255))
}
val playIcon = IslandVectorIconView(
context,
if (media.isPlaying) IslandVectorType.PAUSE else IslandVectorType.PLAY,
Color.WHITE
).apply {
layoutParams = FrameLayout.LayoutParams(dp(context, 22), dp(context, 22), Gravity.CENTER)
}
addView(playIcon)
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
if (media.isPlaying) {
runCatching { media.controller.transportControls.pause() }
} else {
runCatching { media.controller.transportControls.play() }
}
}
}
controlsRow.addView(playBtn, LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)))
val nextBtn = FrameLayout(context).apply {
background = GradientDrawable().apply {
shape = GradientDrawable.OVAL
setColor(Color.argb(35, 255, 255, 255))
setStroke(dp(context, 1), Color.argb(60, 255, 255, 255))
}
val nextIcon = IslandVectorIconView(context, IslandVectorType.NEXT, Color.WHITE).apply {
layoutParams = FrameLayout.LayoutParams(dp(context, 16), dp(context, 16), Gravity.CENTER)
}
addView(nextIcon)
setOnTouchListener { v, ev ->
when (ev.action) {
MotionEvent.ACTION_DOWN -> {
v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start()
false
}
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
false
}
else -> false
}
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
runCatching { media.controller.transportControls.skipToNext() }
}
}
controlsRow.addView(nextBtn, LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)).apply {
marginStart = dp(context, 20)
})
layout.addView(controlsRow)
return layout
}
private fun startMediaTicker(media: IslandMediaState, scrubber: WavyAudioScrubberView, elapsedText: TextView) {
tickerRunnable?.let(main::removeCallbacks)
if (!media.isPlaying || media.duration <= 0) return
tickerRunnable = object : Runnable {
override fun run() {
val isMediaActive = (currentState == IslandVisualState.MEDIA_EXPANDED ||
(currentState == IslandVisualState.EXPANDED && expandedTabMode == ExpandedTabMode.MEDIA)) &&
currentMedia?.isPlaying == true
if (isMediaActive) {
val realtimePos = DynamicIslandNotificationListener.getRealtimePosition(currentMedia?.controller?.playbackState)
val dur = media.duration
if (dur > 0 && !scrubber.isDragging) {
scrubber.progress = (realtimePos.toFloat() / dur).coerceIn(0f, 1f)
elapsedText.text = formatTimestamp(realtimePos.coerceAtMost(dur))
}
main.postDelayed(this, 500L)
}
}
}
main.postDelayed(tickerRunnable!!, 500L)
}
private fun formatRelativeTime(timestamp: Long): String {
val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
val sec = diff / 1000
val min = sec / 60
val hr = min / 60
return when {
sec < 45 -> "now"
min < 60 -> "${min}m"
hr < 24 -> "${hr}h"
else -> "${hr / 24}d"
}
}
private fun getAvatarColorForName(name: String): Int {
val colors = intArrayOf(
Color.rgb(236, 72, 153), // Pink (matches Image 5)
Color.rgb(168, 85, 247), // Purple
Color.rgb(59, 130, 246), // Blue
Color.rgb(16, 185, 129), // Emerald
Color.rgb(245, 158, 11), // Amber
Color.rgb(239, 68, 68),  // Red
Color.rgb(14, 165, 233), // Sky
)
val hash = Math.abs(name.hashCode())
return colors[hash % colors.size]
}
private fun formatTimestamp(ms: Long): String {
if (ms <= 0L) return "0:00"
val totalSec = ms / 1000
val min = totalSec / 60
val sec = totalSec % 60
return if (min >= 60) {
val hr = min / 60
val remMin = min % 60
String.format("%d:%02d:%02d", hr, remMin, sec)
} else {
String.format("%02d:%02d", min, sec)
}
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
val rect: Rect? = cutout?.boundingRectTop ?: cutout?.boundingRects?.firstOrNull { it.top < dp(context, 24) }
if (rect != null && !rect.isEmpty) {
val cutoutCenterY = rect.centerY()
val pillHalfHeight = dp(context, 36) / 2
return (cutoutCenterY - pillHalfHeight).coerceAtLeast(dp(context, 4))
}
}
val statusBar = statusBarHeight(context)
val pillHeight = dp(context, 36)
return ((statusBar - pillHeight) / 2).coerceAtLeast(dp(context, 4))
}
fun preview(context: Context) {
if (isScreenPrivate(context)) {
suppressForPrivacy()
return
}
val appContext = context.applicationContext ?: context
try {
val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
main.postDelayed({ runCatching { tone.release() } }, 250)
} catch (_: Exception) {}
onNotificationReceived(
context = appContext,
key = "preview_test_key_1",
packageName = appContext.packageName,
title = "Chaty Dynamic Island",
body = "Stack view ready · Tap chevron or card to expand all items",
isCall = false,
appIcon = runCatching { appContext.packageManager.getApplicationIcon(appContext.packageName) }.getOrNull(),
pendingIntent = null,
actions = emptyList(),
replyAction = null,
animateArrival = true,
subMessages = listOf(
"• Liquid Glass dynamic palette adaptation active",
"• Apple multi-activity split mode ready",
"• Incoming & active call controls enabled",
),
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
subMessages = listOf(
"Alice: I sent over the latest Figma mockups",
"Alice: Let me know if the Liquid Glass materials look right",
),
)
}
fun remove() {
privacyReceiverContext?.let { runCatching { it.unregisterReceiver(privacyReceiver) } }
privacyReceiverContext = null
activeNotifications.clear()
currentMedia = null
activeReplyItem = null
currentState = IslandVisualState.IDLE
dismissRunnable?.let(main::removeCallbacks)
tickerRunnable?.let(main::removeCallbacks)
currentAnimator?.cancel()
val root = persistentRoot
persistentRoot = null
islandCard = null
contentContainer = null
currentWindowParams = null
cardBackground = null
if (root != null) {
runCatching {
if (root.isAttachedToWindow) {
windowManager?.removeViewImmediate(root)
} else {
windowManager?.removeView(root)
}
}
}
}
private fun dp(context: Context, value: Int): Int =
(value * context.resources.displayMetrics.density).toInt()
private fun dp(context: Context, value: Float): Int =
(value * context.resources.displayMetrics.density).toInt()
private fun appLabel(context: Context, packageName: String): String {
return runCatching {
val pm = context.packageManager
val info = pm.getApplicationInfo(packageName, 0)
pm.getApplicationLabel(info).toString()
}.getOrNull() ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
private fun statusBarHeight(context: Context): Int {
val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(context, 24)
}
}
