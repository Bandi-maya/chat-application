package com.example.chat.island.engine

import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.core.Trigger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class IslandOverlay(private val host: Context, private val windowType: Int) {
    private val windows = host.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var session: WindowSession? = null
    val shown: Boolean get() = session != null

    private inner class WindowSession {
        val lifetime = MainScope()
        val tree = WindowTree()
        val content = ComposeView(host)
        val signals = SystemSignals(host) { position() }
        val layout = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = windowType
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.LEFT
            // Ordinary status must not block screenshots of the entire display.
            // Private modes still suppress content immediately and retain protection.
            flags = (if (IslandController.privacySuppressed.value) WindowManager.LayoutParams.FLAG_SECURE else 0) or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        fun install() {
            tree.start()
            content.setViewTreeLifecycleOwner(tree)
            content.setViewTreeSavedStateRegistryOwner(tree)
            content.setViewTreeViewModelStoreOwner(tree)
            content.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            content.setContent { com.example.chat.island.ui.theme.IslandLocale { OverlayContent() } }
            content.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) IslandController.cancelReply()
                false
            }
            content.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> position() }
            signals.start()
            windows.addView(content, layout)
            lifetime.launch {
                combine(IslandSettings.offsetX, IslandSettings.offsetY) { x, y -> x to y }.collect { position() }
            }
            lifetime.launch {
                IslandController.privacySuppressed.collect { privateContent ->
                    val secure = WindowManager.LayoutParams.FLAG_SECURE
                    layout.flags = if (privateContent) layout.flags or secure else layout.flags and secure.inv()
                    update()
                }
            }
            lifetime.launch {
                IslandController.replying.collect { typing ->
                    val focus = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    val outside = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    layout.flags = if (typing) (layout.flags and focus.inv()) or outside
                        else (layout.flags or focus) and outside.inv()
                    update()
                }
            }
        }

        fun position() {
            if (content.width == 0) return
            val pixels = host.resources.displayMetrics.density
            @Suppress("DEPRECATION")
            val screenWidth = if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.bounds.width()
                else host.resources.displayMetrics.widthPixels
            val left = (screenWidth - content.width) / 2 + (IslandSettings.offsetX.value * pixels).toInt()
            val top = (IslandSettings.offsetY.value * pixels).toInt()
            if (layout.x == left && layout.y == top) return
            layout.x = left
            layout.y = top
            update()
        }

        private fun update() {
            if (content.isAttachedToWindow) try { windows.updateViewLayout(content, layout) } catch (_: Exception) {}
        }

        fun close() {
            lifetime.cancel()
            signals.close()
            try { if (content.isAttachedToWindow) windows.removeViewImmediate(content) } catch (_: Exception) {}
            content.disposeComposition()
            tree.close()
        }
    }

    fun show() {
        if (session != null) return
        val next = WindowSession()
        session = next
        try { next.install() } catch (_: Exception) {
            session = null
            next.close()
        }
    }

    fun hide() {
        val previous = session ?: return
        session = null
        previous.close()
    }
}

@Composable
private fun OverlayContent() {
    val privateScreen by IslandController.privacySuppressed.collectAsState()
    val enabled by IslandSettings.enabled.collectAsState()
    val mode by IslandController.mode.collectAsState()
    val idle by IslandSettings.idlePill.collectAsState()
    // Privacy removes the content immediately; it is never left in an exit animation.
    if (privateScreen || !enabled || (mode == IslandMode.Idle && !idle)) return
    val notificationFrame by IslandController.displayedNotificationFrame.collectAsState()
    val view = LocalView.current
    val level by IslandController.level.collectAsState()
    val scale by IslandSettings.scale.collectAsState()
    val width by IslandSettings.widthScale.collectAsState()
    val height by IslandSettings.heightScale.collectAsState()
    val corner by IslandSettings.corner.collectAsState()
    val speed by IslandSettings.animSpeed.collectAsState()
    val glow by IslandSettings.glow.collectAsState()
    val haptics by IslandSettings.haptics.collectAsState()
    val appAccent by IslandSettings.islandAppAccent.collectAsState()
    val seed by IslandSettings.accentSeed.collectAsState()
    val editor by IslandController.replying.collectAsState()
    val waiting by IslandController.queued.collectAsState()
    val music by IslandController.secondary.collectAsState()
    DynamicIslandUi(modifier = Modifier.drawWithContent {
        drawContent()
        // Posting runs after this draw pass. Revalidate at callback time: a
        // privacy/settings/call transition may have replaced the payload.
        val frame = notificationFrame
        if (frame != null && size.width > 0 && size.height > 0) view.post {
            if (view.isAttachedToWindow && view.isShown && view.windowVisibility == android.view.View.VISIBLE) {
                IslandController.acknowledgeNotificationDrawn(frame)
            }
        }
    }, mode = mode, level = level, scale = scale, widthScale = width, heightScale = height,
        cornerFactor = corner, animSpeed = speed, glow = glow, haptics = haptics,
        onClick = { IslandController.toggle() }, onLongPress = { IslandController.large() },
        onDismiss = { IslandController.dismiss() }, onOpenNotification = { IslandController.openNotification() }, onPrev = { IslandController.prev() },
        onNext = { IslandController.next() }, onPlayPause = { IslandController.playPause() },
        onSeek = { IslandController.seekTo(it) }, onAnswer = { IslandController.answerCall() },
        onDecline = { IslandController.declineCall() }, onMarkRead = { IslandController.markRead() },
        onReply = { IslandController.startReply() }, onSendReply = { IslandController.sendReply(it) },
        onCancelReply = { IslandController.cancelReply() }, replying = editor,
        secondary = music, queued = waiting, onOpenMedia = { IslandController.openMedia() },
        onSwipe = { IslandController.swipe(it) }, appAccent = if (appAccent) Color(seed) else null)
}

private class WindowTree : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val events = LifecycleRegistry(this)
    private val state = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = events
    override val savedStateRegistry get() = state.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    fun start() {
        state.performAttach()
        state.performRestore(null)
        events.currentState = Lifecycle.State.RESUMED
    }
    fun close() {
        events.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/** Captures only device-state signals. No accessibility content or screen pixels are read. */
private class SystemSignals(private val context: Context, private val configurationChanged: () -> Unit) {
    private var registered = false
    private var torchRegistered = false
    private var initialBattery = true
    private var lowBand = 100
    private var ringer = -1
    private var interruption = -1
    private val lamps = mutableMapOf<String, Boolean>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { receive(intent) }
    }
    private val torch = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            val before = lamps.put(cameraId, enabled)
            if (before != null && before != enabled) emit(Trigger.Toggles, IslandMode.Toggle(ToggleKind.Flashlight, enabled), 2000)
        }
    }

    private fun <T> service(type: Class<T>): T? = context.getSystemService(type)
    private fun privacy() {
        val awake = service(PowerManager::class.java)?.isInteractive == true
        val locked = service(KeyguardManager::class.java)?.isKeyguardLocked != false
        IslandController.setPrivacySuppressed(!awake || locked)
    }
    private fun emit(trigger: Trigger, mode: IslandMode, duration: Long = 3500) {
        if (IslandSettings.enabled.value && !IslandController.privacySuppressed.value && IslandSettings.isOn(trigger)) {
            IslandController.flash(mode, durationMs = duration)
        }
    }

    fun start() {
        privacy()
        ringer = service(AudioManager::class.java)?.ringerMode ?: -1
        interruption = service(NotificationManager::class.java)?.currentInterruptionFilter ?: -1
        val filter = IntentFilter()
        arrayOf(Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED, Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_HEADSET_PLUG, BluetoothDevice.ACTION_ACL_CONNECTED, AudioManager.RINGER_MODE_CHANGED_ACTION,
            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED, Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_USER_PRESENT, Intent.ACTION_CONFIGURATION_CHANGED).forEach { filter.addAction(it) }
        // Bluetooth broadcasts come from a privileged system process, not necessarily the system UID.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
        try {
            service(CameraManager::class.java)?.registerTorchCallback(torch, Handler(Looper.getMainLooper()))
            torchRegistered = true
        } catch (_: Exception) {}
    }

    fun close() {
        if (registered) {
            registered = false
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
        if (torchRegistered) {
            torchRegistered = false
            try { service(CameraManager::class.java)?.unregisterTorchCallback(torch) } catch (_: Exception) {}
        }
    }

    private fun receive(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT -> privacy()
            Intent.ACTION_CONFIGURATION_CHANGED -> configurationChanged()
            Intent.ACTION_BATTERY_CHANGED -> battery(intent)
            Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                val percentage = service(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                if (percentage >= 0) emit(Trigger.Charging, IslandMode.Charging(percentage, intent.action == Intent.ACTION_POWER_CONNECTED))
            }
            Intent.ACTION_HEADSET_PLUG -> if (intent.getIntExtra("state", 0) == 1 && !intent.getBooleanExtra("initial", false)) {
                emit(Trigger.Devices, IslandMode.Device(intent.getStringExtra("name") ?: "Wired headphones", true), 3000)
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> bluetooth(intent)
            AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                val value = service(AudioManager::class.java)?.ringerMode ?: return
                if (value == ringer) return
                ringer = value
                val kind = if (value == AudioManager.RINGER_MODE_VIBRATE) ToggleKind.Vibrate else ToggleKind.Silent
                emit(Trigger.Toggles, IslandMode.Toggle(kind, value != AudioManager.RINGER_MODE_NORMAL), 2500)
            }
            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                val value = service(NotificationManager::class.java)?.currentInterruptionFilter ?: return
                if (value == interruption || value == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return
                interruption = value
                emit(Trigger.Toggles, IslandMode.Toggle(ToggleKind.Dnd, value != NotificationManager.INTERRUPTION_FILTER_ALL), 2500)
            }
        }
    }

    private fun battery(intent: Intent) {
        val capacity = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        if (capacity <= 0 || level < 0) return
        val percentage = level * 100 / capacity
        val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val band = if (charging || percentage > 20) 100 else if (percentage > 10) 20 else 10
        val changed = !initialBattery && band < lowBand
        initialBattery = false
        lowBand = band
        if (changed) emit(Trigger.Battery, IslandMode.LowBattery(percentage), 5000)
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun bluetooth(intent: Intent) {
        val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val name = try { device?.name } catch (_: SecurityException) { null }
        val headphones = try { device?.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
            catch (_: SecurityException) { false }
        emit(Trigger.Devices, IslandMode.Device(name ?: "Bluetooth device", headphones))
    }
}
