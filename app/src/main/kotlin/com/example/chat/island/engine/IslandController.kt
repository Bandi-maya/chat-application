package com.example.chat.island.engine

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.core.SwipeAction
import com.example.chat.island.core.SwipeDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

// These public value types are the compatibility boundary used by Chaty's listener and UI.
sealed interface IslandMode {
    data object Idle : IslandMode
    data class Notification(val app: String, val title: String, val text: String,
        val canReply: Boolean = false, val canMarkRead: Boolean = false) : IslandMode
    data class Charging(val percent: Int, val connected: Boolean = true) : IslandMode
    data class LowBattery(val percent: Int) : IslandMode
    data class Device(val name: String, val headphones: Boolean) : IslandMode
    data class Toggle(val kind: ToggleKind, val on: Boolean) : IslandMode
    data class Call(val name: String, val incoming: Boolean, val since: Long,
        val canAnswer: Boolean = false, val canDecline: Boolean = false,
        val canMute: Boolean = false, val canSpeaker: Boolean = false) : IslandMode
    data class Timer(val remainingSec: Int, val totalSec: Int, val external: Boolean = false) : IslandMode
    data class Media(val title: String, val artist: String, val playing: Boolean,
        val art: Bitmap? = null, val accent: Int? = null, val durationMs: Long = 0,
        val positionMs: Long = 0, val updatedAt: Long = 0, val speed: Float = 1f,
        // Source player identity, only ever populated from a live MediaController.
        // Previews and demos leave both empty so the card never claims a real session.
        val appPackage: String? = null, val hasSession: Boolean = false,
        val canPlayPause: Boolean = false, val canPrevious: Boolean = false,
        val canNext: Boolean = false, val canSeek: Boolean = false) : IslandMode
    data class Live(val kind: LiveKind, val app: String, val title: String, val text: String,
        val progress: Int = -1) : IslandMode
}

enum class ToggleKind { Silent, Vibrate, Dnd, Flashlight }
enum class LiveKind { Navigation, Tracking, Download }
enum class IslandLevel { Compact, Banner, Expanded, Large }

internal data class Notice(
    val content: IslandMode,
    val lifetime: Long,
    val presentation: IslandLevel,
    val source: Source = Source(),
    val replyAction: Notification.Action? = null,
    val readAction: PendingIntent? = null,
    val notificationKey: String? = null,
    val seen: Boolean = false,
    val persistent: Boolean = false,
    val displayReceipt: Long? = null,
)

internal data class Source(val intent: PendingIntent? = null, val packageName: String? = null)

/** Queue policy is separate from Android actions and clocks so it can be tested directly. */
internal class NoticeQueue(private val capacity: Int = 5) {
    private val entries = mutableListOf<Notice>()
    val size: Int get() = entries.size
    fun clear() { entries.clear() }
    fun take(): Notice? = if (entries.isEmpty()) null else entries.removeAt(0)
    fun takeUnseen(): Notice? {
        val index = entries.indexOfFirst { !it.seen }
        return if (index == -1) null else entries.removeAt(index)
    }
    fun discardTransients() { entries.removeAll { !it.seen } }
    fun removeSeen(key: String) { entries.removeAll { it.seen && it.notificationKey == key } }
    fun offer(notice: Notice, park: Boolean = false) {
        val duplicate = entries.indexOfFirst { sameNotice(it.content, notice.content) }
        if (duplicate >= 0) entries.removeAt(duplicate)
        if (park) entries.add(0, notice.copy(seen = true, presentation = IslandLevel.Compact))
        else entries.add(notice)
        while (entries.size > capacity) entries.removeAt(if (park) entries.lastIndex else 0)
    }
}

internal fun sameNotice(a: IslandMode, b: IslandMode): Boolean {
    if (a is IslandMode.Notification) return b is IslandMode.Notification && a.app == b.app && a.title == b.title
    return a.javaClass == b.javaClass
}

object IslandController {
    private val dispatcher = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val tasks = mutableMapOf<String, Job>()
    private var context: Context? = null
    private val display = MutableStateFlow<IslandMode>(IslandMode.Idle)
    private val expansion = MutableStateFlow(IslandLevel.Compact)
    private val privacy = MutableStateFlow(false)
    private val editor = MutableStateFlow(false)
    private val count = MutableStateFlow(0)
    private val miniPlayer = MutableStateFlow<IslandMode.Media?>(null)
    val mode = display.asStateFlow()
    data class NotificationFrame(val key: String, val revision: Long, val receipt: Long?)
    private var notificationRevision = 0L
    private var frameNotice: Notice? = null
    private val notificationFrame = MutableStateFlow<NotificationFrame?>(null)
    val displayedNotificationFrame = notificationFrame.asStateFlow()
    // The window acknowledges a drawn frame, not merely a queued controller payload.
    var notificationDrawn: ((String, Long?) -> Unit)? = null
    fun acknowledgeNotificationDrawn(frame: NotificationFrame) {
        if (!privacy.value && notificationFrame.value == frame && display.value is IslandMode.Notification) {
            notificationDrawn?.invoke(frame.key, frame.receipt)
        }
    }
    val level = expansion.asStateFlow()
    val privacySuppressed = privacy.asStateFlow()
    val replying = editor.asStateFlow()
    val queued = count.asStateFlow()
    val secondary = miniPlayer.asStateFlow()
    var media: MediaController? = null

    private val waiting = NoticeQueue()
    private var notice: Notice? = null
    private var closing = false
    private var dismissed = false
    private var song: IslandMode.Media? = null
    private var songExpired = false
    private var call: IslandMode.Call? = null
    private var callId: String? = null
    private var answer: PendingIntent? = null
    private var decline: PendingIntent? = null
    private var callMute: PendingIntent? = null
    private var callSpeaker: PendingIntent? = null
    private var countdown: IslandMode.Timer? = null
    private var activity: Pair<String, IslandMode.Live>? = null
    private var activitySource = Source()
    private val transfers = linkedMapOf<String, Pair<IslandMode.Live, Source>>()
    private data class Clock(val key: String?, val deadline: Long, val total: Int, val heartbeat: Boolean, var lastBeat: Long)
    private var clock: Clock? = null

    fun attach(ctx: Context) { context = ctx.applicationContext }

    // Demo entry points use local sample state only, never real notification actions.
    fun demoNotification() { flash(IslandMode.Notification("Chaty demo", "Maya", "Your island is ready")) }
    fun demoCharging() { flash(IslandMode.Charging(82)) }
    fun demoTimer(seconds: Int) { startTimer(seconds) }
    fun demoMedia() { setMedia(IslandMode.Media("Demo song", "Chaty preview", true)) }
    fun demoIncomingCall() { setCall("Demo caller", true, null, null, canAnswer = true) }
    fun demoEndCall() { clearCall() }
    fun demoEarbudsConnected() { flash(IslandMode.Device("Wireless earbuds", true)) }
    fun demoLowBattery() { flash(IslandMode.LowBattery(15)) }
    fun demoSilentMode() { flash(IslandMode.Toggle(ToggleKind.Silent, true)) }
    fun demoFlashlight() { flash(IslandMode.Toggle(ToggleKind.Flashlight, true)) }
    fun demoNavigation() { setLive(IslandMode.Live(LiveKind.Navigation, "Maps demo", "Turn left", "12 min remaining"), "preview-nav", null, null) }
    fun demoOrderTracking() { setLive(IslandMode.Live(LiveKind.Tracking, "Delivery demo", "Order on the way", "Arriving in 12 min"), "preview-order", null, null) }
    fun demoDownload() { setDownload(IslandMode.Live(LiveKind.Download, "Download demo", "sample.mp4", "45 MB of 120 MB", 38), "preview-download", null, null) }
    fun clearLiveActivities() { clearLive(); clearDownload() }
    fun demoQueuedNotifications(count: Int) {
        repeat(count.coerceIn(0, 5)) { index -> flash(IslandMode.Notification("Chaty demo", "Sender ${index + 1}", "Sample notification ${index + 1}")) }
    }
    fun demoNavigationWithMusic() { demoMedia(); demoNavigation() }
    fun clearMusic() { setMedia(null) }


    private fun cancel(name: String) { tasks.remove(name)?.cancel() }
    private fun after(name: String, milliseconds: Long, block: () -> Unit) {
        cancel(name)
        tasks[name] = dispatcher.launch { delay(milliseconds.coerceAtLeast(1)); block() }
    }
    private fun queueChanged() { count.value = waiting.size }

    private fun download(): IslandMode.Live? {
        val first = transfers.values.firstOrNull()?.first ?: return null
        return if (transfers.size < 2) first else first.copy(text = "${first.text}  (+${transfers.size - 1})")
    }

    private fun render() {
        val music = song.takeUnless { songExpired || privacy.value }
        miniPlayer.value = music
        val next = when {
            privacy.value -> IslandMode.Idle
            call != null -> call!!
            notice != null -> notice!!.content
            dismissed -> IslandMode.Idle
            countdown != null -> countdown!!
            activity != null -> activity!!.second
            transfers.isNotEmpty() -> download()!!
            music != null -> music
            else -> IslandMode.Idle
        }
        val boundary = (display.value == IslandMode.Idle) != (next == IslandMode.Idle)
        display.value = next
        val shownNotice = notice?.takeIf { next === it.content && next is IslandMode.Notification }
        if (shownNotice !== frameNotice) {
            frameNotice = shownNotice
            notificationRevision++
        }
        notificationFrame.value = shownNotice?.notificationKey?.let { NotificationFrame(it, notificationRevision, shownNotice.displayReceipt) }
        if (boundary && notice == null) setLevel(IslandLevel.Compact)
    }

    private fun setLevel(value: IslandLevel) {
        expansion.value = value
        scheduleCollapse()
    }

    private fun scheduleCollapse() {
        cancel("collapse")
        val seconds = IslandSettings.autoCollapseSec.value
        if (seconds < 1 || expansion.value == IslandLevel.Compact || editor.value) return
        after("collapse", seconds * 1000L) {
            if (call == null && !editor.value && (notice == null || notice?.persistent == true)) {
                expansion.value = IslandLevel.Compact
                yieldSticky()
            }
        }
    }

    init {
        dispatcher.launch { IslandSettings.autoCollapseSec.collect { scheduleCollapse() } }
    }

    fun setPrivacySuppressed(suppressed: Boolean) {
        if (privacy.value == suppressed) return
        privacy.value = suppressed
        if (suppressed) {
            editor.value = false
            clock?.let { runClock(it) } // A single deadline wait replaces second-by-second rendering.
            setLevel(IslandLevel.Compact)
        } else {
            clock?.let { runClock(it) } // Recompute from the original deadline on screen/user-present.
        }
        render()
    }

    private fun begin(item: Notice) {
        cancel("banner")
        closing = false
        notice = item
        dismissed = false
        render()
        setLevel(if (item.seen) IslandLevel.Compact else item.presentation)
        if (item.persistent && item.seen) {
            after("banner", 60) { yieldSticky() }
        } else after("banner", item.lifetime) {
            if (item.persistent) {
                notice = item.copy(seen = true)
                setLevel(IslandLevel.Compact)
                yieldSticky()
            } else finishNotice()
        }
    }

    private fun yieldSticky() {
        val held = notice ?: return
        if (!held.persistent || editor.value) return
        val fresh = waiting.takeUnseen() ?: return
        waiting.offer(held, park = true)
        queueChanged()
        begin(fresh)
    }

    private fun finishNotice() {
        if (closing) return
        editor.value = false
        closing = true
        setLevel(IslandLevel.Compact)
        after("banner", 350) {
            notice = null
            closing = false
            val next = waiting.take()
            queueChanged()
            if (next == null) render() else begin(next)
        }
    }

    fun flash(m: IslandMode, durationMs: Long = 4500, level: IslandLevel = IslandLevel.Banner,
        open: PendingIntent? = null, pkg: String? = null, reply: Notification.Action? = null,
        markRead: PendingIntent? = null, key: String? = null, displayReceipt: Long? = null) {
        if (call != null) return
        val duration = if (level == IslandLevel.Banner) IslandSettings.kindDurationMs(m) ?: durationMs else durationMs
        val next = Notice(m, duration, level, Source(open, pkg), reply, markRead, key,
            persistent = IslandSettings.stickyNotif.value && m is IslandMode.Notification && pkg != null,
            displayReceipt = displayReceipt)
        val current = notice
        if (current == null || (!editor.value && sameNotice(current.content, m))) {
            begin(next)
            return
        }
        if (current.persistent && expansion.value == IslandLevel.Compact && !editor.value) {
            waiting.offer(current, park = true)
            queueChanged()
            begin(next)
        } else {
            waiting.offer(next)
            queueChanged()
        }
    }

    fun notificationRemoved(key: String) {
        waiting.removeSeen(key)
        queueChanged()
        if (notice?.notificationKey == key && notice?.persistent == true && !editor.value) finishNotice()
    }

    fun toggle() {
        if (editor.value || privacy.value) return
        when (expansion.value) {
            IslandLevel.Compact -> setLevel(IslandLevel.Expanded)
            IslandLevel.Banner -> {
                val current = display.value
                if (current !is IslandMode.Notification || current.canReply || current.canMarkRead) {
                    val item = notice
                    if (item != null) begin(item.copy(lifetime = 7000, presentation = IslandLevel.Expanded, seen = false))
                    else setLevel(IslandLevel.Expanded)
                } else {
                    openCurrent()
                    finishNotice()
                }
            }
            else -> {
                openCurrent()
                if (notice == null) setLevel(IslandLevel.Compact) else finishNotice()
            }
        }
    }

    fun large() {
        if (editor.value || display.value == IslandMode.Idle || privacy.value) return
        val target = if (expansion.value == IslandLevel.Large) IslandLevel.Expanded else IslandLevel.Large
        val item = notice
        if (item == null) setLevel(target)
        else begin(item.copy(presentation = target, lifetime = 9000, seen = false))
    }

    fun dismiss() {
        when {
            editor.value -> cancelReply()
            notice != null -> finishNotice()
            expansion.value != IslandLevel.Compact -> setLevel(IslandLevel.Compact)
            call == null -> { dismissed = true; render() }
        }
    }

    fun clearAll() {
        waiting.clear()
        queueChanged()
        when {
            editor.value -> cancelReply()
            notice != null -> finishNotice()
            else -> setLevel(IslandLevel.Compact)
        }
    }

    private fun musicControlsAvailable(): Boolean = media != null &&
        (display.value is IslandMode.Media || ((display.value is IslandMode.Timer || display.value is IslandMode.Live) && song != null))

    fun swipe(dir: SwipeDir) {
        when (IslandSettings.swipeFlow(dir).value) {
            SwipeAction.None -> {}
            SwipeAction.Dismiss -> dismiss()
            SwipeAction.ClearAll -> clearAll()
            SwipeAction.Expand -> if (!editor.value && expansion.value == IslandLevel.Compact) toggle()
            SwipeAction.OpenApp -> if (!editor.value && openCurrent()) {
                if (notice == null) setLevel(IslandLevel.Compact) else finishNotice()
            }
            SwipeAction.NextTrack -> if (musicControlsAvailable()) next()
            SwipeAction.PrevTrack -> if (musicControlsAvailable()) prev()
            SwipeAction.PlayPause -> if (musicControlsAvailable()) playPause()
        }
    }

    private fun send(action: PendingIntent?): Boolean {
        if (action == null) return false
        return try {
            val owner = context
            if (owner == null) action.send()
            else {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 34) options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                action.send(owner, 0, null, null, null, null, options.toBundle())
            }
            true
        } catch (_: Exception) { false }
    }

    private fun open(source: Source): Boolean {
        if (send(source.intent)) return true
        val owner = context ?: return false
        val packageName = source.packageName ?: return false
        return try {
            val request = owner.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            owner.startActivity(request)
            true
        } catch (_: Exception) { false }
    }

    private fun openCurrent(): Boolean = when (val shown = display.value) {
        is IslandMode.Notification -> open(notice?.source ?: Source())
        is IslandMode.Media -> open(Source(media?.sessionActivity, media?.packageName))
        is IslandMode.Live -> open(if (shown.kind == LiveKind.Download) transfers.values.firstOrNull()?.second ?: Source() else activitySource)
        else -> false
    }

    /** Explicit notification action. Failed/cancelled intents keep the card visible. */
    fun openNotification() {
        if (privacy.value || editor.value || display.value !is IslandMode.Notification) return
        if (openCurrent()) finishNotice()
    }

    fun openMedia() {
        open(Source(media?.sessionActivity, media?.packageName))
        if (notice == null) setLevel(IslandLevel.Compact)
    }

    fun setMedia(m: IslandMode.Media?) {
        val previous = song
        if (previous != null && m != null && previous.playing && m.playing &&
            previous.copy(positionMs = m.positionMs, updatedAt = m.updatedAt) == m &&
            previous.updatedAt != 0L && m.updatedAt != 0L) {
            val projection = previous.positionMs + ((m.updatedAt - previous.updatedAt) * previous.speed).toLong()
            if (abs(projection - m.positionMs) < 1500L) return
        }
        val newActivity = previous?.title != m?.title || previous?.playing != m?.playing
        if (m == null || newActivity) dismissed = false
        song = m
        if (m == null || m.playing) {
            cancel("paused")
            songExpired = false
        } else if (previous == null || newActivity) {
            songExpired = false
            after("paused", 10_000) { songExpired = true; render() }
        }
        if (m == null && display.value is IslandMode.Media) setLevel(IslandLevel.Compact)
        render()
    }

    fun playPause() {
        val controller = media ?: return
        when (controller.playbackState?.state) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> controller.transportControls.pause()
            else -> controller.transportControls.play()
        }
    }
    fun prev() { media?.transportControls?.skipToPrevious() }
    fun next() { media?.transportControls?.skipToNext() }
    fun seekTo(ms: Long) { media?.transportControls?.seekTo(ms) }
    fun skip(dir: Int) {
        if (display.value is IslandMode.Media) { if (dir > 0) next() else prev() }
    }

    fun setCall(name: String, incoming: Boolean, answer: PendingIntent?, decline: PendingIntent?,
        key: String? = null, canAnswer: Boolean = answer != null,
        connectedSince: Long? = null, mute: PendingIntent? = null, speaker: PendingIntent? = null) {
        val old = call
        this.answer = answer
        this.decline = decline
        this.callMute = mute
        this.callSpeaker = speaker
        if (key != null) callId = key
        // External notifications must provide an actual running chronometer.
        // Non-incoming alone can mean outgoing ringing, never start a timer from it.
        val start = connectedSince?.takeIf { !incoming && it > 0L } ?: 0L
        call = IslandMode.Call(name, incoming, start, canAnswer && answer != null, decline != null,
            mute != null, speaker != null)
        val held = notice
        if (held?.persistent == true) waiting.offer(held, park = true)
        waiting.discardTransients()
        queueChanged()
        notice = null
        closing = false
        editor.value = false
        dismissed = false
        cancel("banner")
        render()
        if (incoming) setLevel(IslandLevel.Expanded)
        else if (old == null || old.incoming) setLevel(IslandLevel.Compact)
    }

    fun clearCall(key: String? = null) {
        if (call == null || (key != null && callId != null && key != callId)) return
        call = null
        callId = null
        answer = null
        decline = null
        callMute = null
        callSpeaker = null
        setLevel(IslandLevel.Compact)
        val next = waiting.take()
        queueChanged()
        if (next == null) render() else begin(next)
    }
    fun answerCall() { send(answer) }
    fun declineCall() { send(decline) }
    fun toggleCallMute() { send(callMute) }
    fun toggleCallSpeaker() { send(callSpeaker) }

    fun markRead() {
        if (send(notice?.readAction)) {
            editor.value = false
            finishNotice()
        }
    }
    fun startReply() {
        if (notice?.replyAction == null || privacy.value) return
        cancel("banner")
        cancel("collapse")
        editor.value = true
    }
    fun cancelReply() {
        if (editor.value) { editor.value = false; finishNotice() }
    }
    fun sendReply(text: String) {
        if (text.isBlank() || privacy.value) return
        val action = notice?.replyAction ?: return
        val owner = context ?: return
        val inputs = action.remoteInputs ?: return
        val sent = try {
            val results = Bundle()
            for (input in inputs) results.putCharSequence(input.resultKey, text)
            val delivery = Intent().apply { addFlags(Intent.FLAG_RECEIVER_FOREGROUND) }
            RemoteInput.addResultsToIntent(inputs, delivery, results)
            if (Build.VERSION.SDK_INT >= 28) RemoteInput.setResultsSource(delivery, RemoteInput.SOURCE_FREE_FORM_INPUT)
            action.actionIntent.send(owner, 0, delivery)
            true
        } catch (_: Exception) { false }
        editor.value = false
        if (!sent) finishNotice()
        else {
            notice = null
            flash(IslandMode.Notification("Sent", "Reply sent", text), durationMs = 1800)
        }
    }

    private fun secondsUntil(deadline: Long, now: Long): Int = ((deadline - now).coerceAtLeast(0) + 999L).div(1000L).toInt()
    private fun runClock(value: Clock) {
        cancel("timer")
        clock = value
        if (privacy.value) {
            // No polling and no wakelock. Android may delay this while asleep.
            tasks["timer"] = dispatcher.launch {
                delay((value.deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1))
                if (clock === value) {
                    clock = null
                    countdown = null
                    render()
                    if (!value.heartbeat) flash(IslandMode.Notification("Timer", "Time's up!", "Timer complete"))
                }
            }
            return
        }
        tasks["timer"] = dispatcher.launch {
            while (clock === value) {
                val now = SystemClock.elapsedRealtime()
                val remaining = secondsUntil(value.deadline, now)
                if (remaining < 1) {
                    clock = null
                    countdown = null
                    render()
                    flash(IslandMode.Notification("Timer", "Time's up!", if (value.key == null) "${value.total} sec complete" else "Timer complete"))
                    break
                }
                if (value.heartbeat && now - value.lastBeat > 3500) {
                    clock = null
                    countdown = null
                    setLevel(IslandLevel.Compact)
                    render()
                    break
                }
                countdown = IslandMode.Timer(remaining, value.total, value.key != null)
                render()
                delay((value.deadline - now - (remaining - 1L) * 1000L).coerceAtLeast(1))
            }
        }
    }

    fun startTimer(seconds: Int) {
        dismissed = false
        val now = SystemClock.elapsedRealtime()
        runClock(Clock(null, now + seconds * 1000L, seconds, false, now))
    }
    fun stopTimer() {
        if (clock?.key != null) { dismissed = true; setLevel(IslandLevel.Compact); render() }
        else dropTimer()
    }
    fun syncClockTimer(key: String, endAt: Long, needsBeat: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val old = clock
        if (old?.key == key) {
            old.lastBeat = now
            if (abs(endAt - old.deadline) < 1500) return
        }
        val remaining = secondsUntil(endAt, now)
        if (remaining == 0) return
        if (old?.key != key) dismissed = false
        val total = if (old?.key == key) maxOf(old.total, remaining) else remaining
        runClock(Clock(key, endAt, total, needsBeat, now))
    }
    private fun matchesClock(key: String?): Boolean = clock?.key?.let { key == null || key == it } == true
    private fun dropTimer() {
        cancel("timer")
        clock = null
        countdown = null
        setLevel(IslandLevel.Compact)
        render()
    }
    fun clearClockTimer(key: String? = null) {
        if (!matchesClock(key)) return
        val remaining = clock!!.deadline - SystemClock.elapsedRealtime()
        dropTimer()
        if (remaining in 0L..2000L) flash(IslandMode.Notification("Timer", "Time's up!", "Timer complete"))
    }
    fun dropClockTimerSilently(key: String? = null) { if (matchesClock(key)) dropTimer() }
    fun pauseClockTimer(key: String) {
        if (matchesClock(key) && clock?.heartbeat == false) clearClockTimer(key)
    }

    fun setLive(m: IslandMode.Live, key: String, open: PendingIntent?, pkg: String?) {
        if (activity?.first == key && activity?.second == m) return
        if (activity?.second?.app != m.app) dismissed = false
        activity = key to m
        activitySource = Source(open, pkg)
        render()
    }
    fun clearLive(key: String? = null) {
        if (activity == null || (key != null && key != activity?.first)) return
        activity = null
        activitySource = Source()
        if (display.value is IslandMode.Live) setLevel(IslandLevel.Compact)
        render()
    }
    fun setDownload(m: IslandMode.Live, key: String, open: PendingIntent?, pkg: String?) {
        if (transfers[key]?.first == m) return
        if (!transfers.containsKey(key)) dismissed = false
        transfers[key] = m to Source(open, pkg)
        render()
    }
    fun clearDownload(key: String? = null) {
        if (key == null) transfers.clear() else transfers.remove(key)
        if (transfers.isEmpty() && (display.value as? IslandMode.Live)?.kind == LiveKind.Download) setLevel(IslandLevel.Compact)
        render()
    }
}
