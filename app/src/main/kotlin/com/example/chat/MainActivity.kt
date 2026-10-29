package com.example.chat

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.chat.ui.AppRoot

/**
 * Native Kotlin entry point for the Chaty port.
 *
 * Parity target: the Flutter app's lib/main.dart on the api branch
 * (commit 3bee780d). AppRoot wraps itself in ChatyTheme driven by the ported
 * ThemeController; see docs/kotlin-parity.md for the tracking matrix.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // BiometricPrompt host for the app-lock adapter (FragmentActivity extends ComponentActivity).
        com.example.chat.platform.LockPromptHost.attach(this)
        // registerForActivityResult must run before STARTED (launcher-icon pickers).
        ServiceLocator.appIconBridge.register(this)
        setContent {
            AppRoot()
        }
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** Taps on chat / call notifications (see notifications/ChatyNotifications). */
    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val N = com.example.chat.notifications.ChatyNotifications
        when (intent?.action) {
            N.ACTION_OPEN_CHAT -> N.requestOpenRoom(intent.getStringExtra(N.EXTRA_ROOM_ID))
            N.ACTION_INCOMING_CALL, N.ACTION_ANSWER_CALL -> {
                showOverLockScreen()
                val callId = intent.getStringExtra(N.EXTRA_CALL_ID).orEmpty()
                if (callId.isEmpty()) return
                // The app is open now: the in-app incoming-call screen (and ringer) takes over.
                N.cancelIncomingCall(this, callId)
                ServiceLocator.apiBackend.onPushMessage(null) // make sure the socket is up for the offer
                if (intent.action == N.ACTION_ANSWER_CALL) {
                    N.requestCallAction(N.PendingCallAction(callId, answer = true))
                }
            }
            else -> intent?.getStringExtra(N.EXTRA_ROOM_ID)?.let { N.requestOpenRoom(it) }
        }
    }

    private var lockScreenListener: (() -> Unit)? = null

    /** Lets the incoming-call screen appear over the lock screen and wake the display. Undone as
     *  soon as the call is over, so chats are never shown over the lock screen. */
    private fun showOverLockScreen() {
        if (lockScreenListener == null) {
            val calls = ServiceLocator.callSignaling
            var sawSession = false
            val l: () -> Unit = {
                runOnUiThread {
                    if (calls.hasActiveSession) sawSession = true
                    else if (sawSession) clearOverLockScreen()
                }
            }
            lockScreenListener = l
            calls.addStateListener(l)
            // Safety net: if the offer never arrives, stop showing over the lock screen.
            window.decorView.postDelayed({ if (!calls.hasActiveSession) clearOverLockScreen() }, 60_000)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    override fun onDestroy() {
        lockScreenListener?.let { ServiceLocator.callSignaling.removeStateListener(it) }
        lockScreenListener = null
        com.example.chat.platform.LockPromptHost.detach(this)
        // Dart AppLifecycleState.detached equivalent: the activity is going away for good (not a
        // rotation/config change), so persist a terminal call state instead of a stranded row.
        if (isFinishing && !isChangingConfigurations) {
            try { ServiceLocator.callLifecycle.onAppDetached() } catch (e: Exception) { android.util.Log.w("MainActivity", "call detach cleanup failed: $e") }
        }
        super.onDestroy()
    }

    private fun clearOverLockScreen() {
        lockScreenListener?.let { ServiceLocator.callSignaling.removeStateListener(it) }
        lockScreenListener = null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    // Dart native_call_pip_service modeChanges: forward the system PiP state.
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ServiceLocator.nativeCallPip.onPipModeChanged(isInPictureInPictureMode)
    }
}
