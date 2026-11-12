package com.example.chat.island.core

import com.example.chat.R
import androidx.annotation.StringRes

import com.example.chat.island.engine.*

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

enum class Trigger(val key: String, @StringRes val label: Int) {
    Notifications("notif", R.string.trigger_notifications),
    Media("media", R.string.trigger_media),
    Calls("call", R.string.trigger_calls),
    Charging("charge", R.string.trigger_charging),
    Timer("timer", R.string.trigger_timer),
    Devices("device", R.string.trigger_devices),
    Battery("battery", R.string.trigger_battery),
    Toggles("toggles", R.string.trigger_toggles),
    Navigation("nav", R.string.trigger_navigation),
    Tracking("track", R.string.trigger_tracking),
    Downloads("download", R.string.trigger_downloads),
    Quiet("quiet", R.string.trigger_quiet),
}

enum class IslandKind(val key: String, @StringRes val label: Int, val popup: Boolean) {
    Notification("notif", R.string.banner_notification, true),
    Charging("charging", R.string.banner_charging, true),
    Unplugged("unplug", R.string.banner_unplugged, true),
    LowBattery("low", R.string.banner_low, true),
    Device("device", R.string.banner_device, true),
    Silent("silent", R.string.banner_silent, true),
    Vibrate("vibrate", R.string.banner_vibrate, true),
    Dnd("dnd", R.string.banner_dnd, true),
    Flashlight("torch", R.string.banner_flash, true),
    Call("call", R.string.banner_call, false),
    Media("media", R.string.banner_music, false),
    Timer("timer", R.string.banner_timer, false),
    Navigation("nav", R.string.banner_nav, false),
    Tracking("track", R.string.banner_tracking, false),
    Download("download", R.string.banner_download, false),
}

data class KindStyle(
    val w: Float = 1f,
    val h: Float = 1f,
    val text: Float = 1f,
    val corner: Float = -1f,
    val durationSec: Int = 0,
    val cardW: Float = 1f,
    val cardH: Float = 1f,
)

fun kindOf(m: IslandMode): IslandKind? = when (m) {
    is IslandMode.Notification -> IslandKind.Notification
    is IslandMode.Charging -> if (m.connected) IslandKind.Charging else IslandKind.Unplugged
    is IslandMode.LowBattery -> IslandKind.LowBattery
    is IslandMode.Device -> IslandKind.Device
    is IslandMode.Toggle -> when (m.kind) {
        ToggleKind.Silent -> IslandKind.Silent
        ToggleKind.Vibrate -> IslandKind.Vibrate
        ToggleKind.Dnd -> IslandKind.Dnd
        ToggleKind.Flashlight -> IslandKind.Flashlight
    }
    is IslandMode.Call -> IslandKind.Call
    is IslandMode.Media -> IslandKind.Media
    is IslandMode.Timer -> IslandKind.Timer
    is IslandMode.Live -> when (m.kind) {
        LiveKind.Navigation -> IslandKind.Navigation
        LiveKind.Tracking -> IslandKind.Tracking
        LiveKind.Download -> IslandKind.Download
    }
    IslandMode.Idle -> null
}

enum class SwipeDir { Up, Down, Left, Right }

enum class SwipeAction(val key: String, @StringRes val label: Int) {
    None("none", R.string.swipe_act_none),
    Dismiss("dismiss", R.string.swipe_act_dismiss),
    ClearAll("clear", R.string.swipe_act_clear),
    Expand("expand", R.string.swipe_act_expand),
    OpenApp("open", R.string.swipe_act_open),
    NextTrack("next", R.string.swipe_act_next),
    PrevTrack("prev", R.string.swipe_act_prev),
    PlayPause("playpause", R.string.swipe_act_playpause);

    companion object {
        fun from(key: String?, def: SwipeAction) = entries.firstOrNull { it.key == key } ?: def
    }
}

object IslandSettings {
    private interface Stored {
        fun read(source: SharedPreferences)
        fun write(target: SharedPreferences.Editor)
        fun restore()
    }
    private val stored = arrayListOf<Stored>()
    private var storage: SharedPreferences? = null
    private fun <T : Any> setting(key: String, initial: T): MutableStateFlow<T> {
        val state = MutableStateFlow(initial)
        stored.add(object : Stored {
            @Suppress("UNCHECKED_CAST")
            override fun read(source: SharedPreferences) {
                state.value = try {
                    (when (initial) {
                        is Float -> source.getFloat(key, initial)
                        is Int -> source.getInt(key, initial)
                        is Boolean -> source.getBoolean(key, initial)
                        else -> initial
                    }) as T
                } catch (_: ClassCastException) { initial }
            }
            override fun write(target: SharedPreferences.Editor) {
                when (val value = state.value) {
                    is Float -> target.putFloat(key, value)
                    is Int -> target.putInt(key, value)
                    is Boolean -> target.putBoolean(key, value)
                }
            }
            override fun restore() { state.value = initial }
        })
        return state
    }
    val scale = setting("scale", 1f)
    val widthScale = setting("w", 1f)
    val heightScale = setting("h", 1f)
    val offsetX = setting("x", 0f)
    val offsetY = setting("y", 10f)
    val corner = setting("corner", 1f)
    val animSpeed = setting("speed", 1f)
    val glow = setting("glow", true)
    val haptics = setting("haptics", true)
    val theme = setting("theme", 0)
    val accentSource = setting("accentSource", 0)
    val accentSeed = setting("accentSeed", 0xFF7B3DF5.toInt())
    val bgStyle = setting("bgStyle", 0)
    val bgColor = setting("bgColor", 0xFF14121C.toInt())
    val islandAppAccent = setting("islandAppAccent", false)
    val autoCollapseSec = setting("autoCollapse", 6)
    val idlePill = setting("idlePill", true)
    val swipeSkip = setting("swipeSkip", true)
    val stickyNotif = setting("stickyNotif", false)
    val onboarded = setting("onboarded", false)
    val enabled = setting("enabled", true)
    val kindStyles = MutableStateFlow<Map<String, KindStyle>>(emptyMap())
    val triggers = MutableStateFlow(Trigger.entries.map { it.key }.toSet())
    val blockedApps = MutableStateFlow<Set<String>>(emptySet())
    val swipeUp = MutableStateFlow(SwipeAction.Dismiss)
    val swipeDown = MutableStateFlow(SwipeAction.None)
    val swipeLeft = MutableStateFlow(SwipeAction.NextTrack)
    val swipeRight = MutableStateFlow(SwipeAction.PrevTrack)

    fun swipeFlow(d: SwipeDir): MutableStateFlow<SwipeAction> = when (d) {
        SwipeDir.Left -> swipeLeft
        SwipeDir.Right -> swipeRight
        SwipeDir.Up -> swipeUp
        SwipeDir.Down -> swipeDown
    }

    @Synchronized fun init(ctx: Context) {
        if (storage != null) return
        val source = ctx.applicationContext.getSharedPreferences("chaty_island", Context.MODE_PRIVATE)
        storage = source
        stored.forEach { it.read(source) }
        val all = Trigger.entries.map { it.key }.toSet()
        val saved = source.getStringSet("triggers", null)
        val known = source.getStringSet("triggersSeen", setOf("notif", "media", "call", "charge", "timer", "device")).orEmpty()
        triggers.value = if (saved == null) all else (saved + (all - known)).intersect(all)
        blockedApps.value = source.getStringSet("blocked", emptySet()).orEmpty().toSet()
        kindStyles.value = IslandKind.entries.associate { kind ->
            val prefix = "bn_${kind.key}_"
            kind.key to KindStyle(
                w = source.getFloat(prefix + "w", 1f), h = source.getFloat(prefix + "h", 1f),
                text = source.getFloat(prefix + "t", 1f), corner = source.getFloat(prefix + "c", -1f),
                durationSec = source.getInt(prefix + "d", 0),
                cardW = source.getFloat(prefix + "cw", 1f), cardH = source.getFloat(prefix + "ch", 1f))
        }
        SwipeDir.entries.forEach { direction ->
            val flow = swipeFlow(direction)
            val savedAction = source.getString("swipe${direction.name}", null)
            flow.value = SwipeAction.from(savedAction, flow.value)
            if (direction in setOf(SwipeDir.Left, SwipeDir.Right) && savedAction == null && !swipeSkip.value) flow.value = SwipeAction.None
        }
    }

    @Synchronized fun save() {
        val edit = storage?.edit() ?: return
        stored.forEach { it.write(edit) }
        edit.putStringSet("blocked", blockedApps.value.toSet())
        edit.putStringSet("triggers", triggers.value.toSet())
        edit.putStringSet("triggersSeen", Trigger.entries.map { it.key }.toSet())
        SwipeDir.entries.forEach { edit.putString("swipe${it.name}", swipeFlow(it).value.key) }
        IslandKind.entries.forEach { kind ->
            val prefix = "bn_${kind.key}_"
            val style = kindStyle(kind)
            edit.putFloat(prefix + "w", style.w)
            edit.putFloat(prefix + "h", style.h)
            edit.putFloat(prefix + "t", style.text)
            edit.putFloat(prefix + "c", style.corner)
            edit.putInt(prefix + "d", style.durationSec)
            edit.putFloat(prefix + "cw", style.cardW)
            edit.putFloat(prefix + "ch", style.cardH)
        }
        edit.apply()
    }

    fun isOn(t: Trigger): Boolean = triggers.value.contains(t.key)
    fun kindStyle(t: IslandKind): KindStyle = kindStyles.value.getOrDefault(t.key, KindStyle())
    fun setKindStyle(t: IslandKind, style: KindStyle) {
        kindStyles.value = kindStyles.value.toMutableMap().apply { put(t.key, style) }
    }
    fun kindDurationMs(m: IslandMode): Long? {
        val category = kindOf(m) ?: return null
        val seconds = kindStyle(category).durationSec
        return if (seconds > 0) seconds.toLong() * 1000 else null
    }
    fun setTrigger(t: Trigger, on: Boolean) {
        triggers.value = triggers.value.toMutableSet().apply { if (on) add(t.key) else remove(t.key) }
        save()
    }
    fun setBlocked(pkg: String, blocked: Boolean) {
        blockedApps.value = blockedApps.value.toMutableSet().apply { if (blocked) add(pkg) else remove(pkg) }
        save()
    }
    fun preset(size: Float, w: Float, h: Float) {
        scale.value = size.coerceIn(0.5f, 1.8f)
        widthScale.value = w.coerceIn(0.6f, 1.6f)
        heightScale.value = h.coerceIn(0.7f, 1.6f)
        save()
    }
    fun resetShape() { preset(1f, 1f, 1f) }
    fun resetKinds() { kindStyles.value = emptyMap(); save() }
    fun resetLook() {
        corner.value = 1f; animSpeed.value = 1f
        glow.value = true; haptics.value = true
        save()
    }
    fun reset() {
        offsetX.value = 0f; offsetY.value = 10f
        resetShape()
    }
    fun resetAll() {
        // Keep activation, setup completion, trigger choices and blocked apps.
        val wasEnabled = enabled.value
        val setupComplete = onboarded.value
        stored.forEach { it.restore() }
        enabled.value = wasEnabled; onboarded.value = setupComplete
        kindStyles.value = emptyMap()
        swipeUp.value = SwipeAction.Dismiss; swipeDown.value = SwipeAction.None
        swipeLeft.value = SwipeAction.NextTrack; swipeRight.value = SwipeAction.PrevTrack
        save()
    }
}
