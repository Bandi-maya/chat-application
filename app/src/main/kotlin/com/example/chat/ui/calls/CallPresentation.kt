package com.example.chat.ui.calls

import com.example.chat.data.services.CallSignalingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Authoritative modes for rendering active call state in Chaty.
 *  Exact port of call_presentation_controller.dart CallPresentationMode. */
enum class CallPresentationMode {
    /** No call presentation active or call is ended. */
    none,

    /** Full-screen OngoingCallScreen is active; in-app floating PiP and Call Island are strictly hidden. */
    fullScreen,

    /** Compact iOS-Dynamic-Island-inspired call indicator at top-center. */
    inAppIsland,

    /** In-app floating draggable video PiP window. */
    inAppVideoPip,

    /** Android OS native Picture-in-Picture window owns the presentation. */
    systemPip,

    /** Minimized to Android OS foreground service call notification. */
    backgroundNotification,
}

/** Chaty-designed call screen layouts for incoming and ongoing calls.
 *  Exact port of call_screen_template.dart. [wireName] is the Dart enum
 *  .name persisted in preferences. */
enum class CallScreenTemplate(val wireName: String, val displayName: String, val description: String) {
    chatyClassic("chatyClassic", "Chaty Classic", "Centered avatar with floating action buttons"),
    sheet("sheet", "Sheet", "Rounded bottom sheet carrying the call actions"),
    card("card", "Card", "A single elevated card with all call controls"),
    edge("edge", "Edge", "Minimal chrome, controls pinned to the screen edges");

    companion object {
        val all: List<CallScreenTemplate> = entries

        fun fromName(name: String?): CallScreenTemplate =
            entries.firstOrNull { it.wireName == name } ?: chatyClassic
    }
}

/** Device-local call presentation preferences. Exact port of
 *  call_presentation_preferences.dart CallPresentationPreferences. */
data class CallPresentationPreferences(
    val dynamicIslandEnabled: Boolean = true,
    val pictureInPictureEnabled: Boolean = true,
    val lowDataUsageEnabled: Boolean = false,
    /** Persisted CallScreenTemplate name for the incoming call screen. */
    val incomingCallTemplate: String = "chatyClassic",
    /** Persisted CallScreenTemplate name for the ongoing call screen. */
    val ongoingCallTemplate: String = "chatyClassic",
)

/** Persistence seam so the store stays JVM-testable; the Android adapter
 *  (SharedPreferences) lives in CallPresentationAndroid.kt. */
interface CallPresentationPrefsStorage {
    suspend fun getBool(key: String): Boolean?
    suspend fun getString(key: String): String?
    suspend fun putBool(key: String, value: Boolean): Boolean
    suspend fun putString(key: String, value: String): Boolean
}

/**
 * Device-local call presentation preferences. Exact port of
 * CallPresentationPreferencesStore: same keys, same defaults, serialized
 * write queue, publish only after storage succeeds.
 *
 * These settings intentionally live outside the account privacy model: they
 * describe capabilities and presentation choices for this physical device.
 */
class CallPresentationPreferencesStore(
    private val storage: CallPresentationPrefsStorage,
    private val scope: CoroutineScope,
) {
    companion object {
        const val DYNAMIC_ISLAND_KEY = "chaty.calls.dynamic_island_enabled.v1"
        const val PICTURE_IN_PICTURE_KEY = "chaty.calls.picture_in_picture_enabled.v1"
        const val LOW_DATA_USAGE_KEY = "chaty.calls.low_data_usage_enabled.v1"
        const val INCOMING_TEMPLATE_KEY = "chaty.calls.incoming_template.v1"
        const val ONGOING_TEMPLATE_KEY = "chaty.calls.ongoing_template.v1"
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { for (l in listeners) l() }

    @Volatile
    var value: CallPresentationPreferences = CallPresentationPreferences()
        private set

    @Volatile
    var isInitialized: Boolean = false
        private set

    private val writeMutex = Mutex()
    private var initializeStarted = false

    /** Dart initialize(): one-time guarded load. */
    fun initialize() {
        if (initializeStarted) return
        initializeStarted = true
        scope.launch {
            value = CallPresentationPreferences(
                dynamicIslandEnabled = storage.getBool(DYNAMIC_ISLAND_KEY) ?: true,
                pictureInPictureEnabled = storage.getBool(PICTURE_IN_PICTURE_KEY) ?: true,
                lowDataUsageEnabled = storage.getBool(LOW_DATA_USAGE_KEY) ?: false,
                incomingCallTemplate = storage.getString(INCOMING_TEMPLATE_KEY) ?: "chatyClassic",
                ongoingCallTemplate = storage.getString(ONGOING_TEMPLATE_KEY) ?: "chatyClassic",
            )
            isInitialized = true
            notifyListeners()
        }
    }

    fun setDynamicIslandEnabled(enabled: Boolean) = setBool(
        key = DYNAMIC_ISLAND_KEY,
        enabled = enabled,
        read = { it.dynamicIslandEnabled },
        update = { it.copy(dynamicIslandEnabled = enabled) },
    )

    fun setPictureInPictureEnabled(enabled: Boolean) = setBool(
        key = PICTURE_IN_PICTURE_KEY,
        enabled = enabled,
        read = { it.pictureInPictureEnabled },
        update = { it.copy(pictureInPictureEnabled = enabled) },
    )

    fun setLowDataUsageEnabled(enabled: Boolean) = setBool(
        key = LOW_DATA_USAGE_KEY,
        enabled = enabled,
        read = { it.lowDataUsageEnabled },
        update = { it.copy(lowDataUsageEnabled = enabled) },
    )

    fun setIncomingCallTemplate(template: String) = setString(
        key = INCOMING_TEMPLATE_KEY,
        template = template,
        read = { it.incomingCallTemplate },
        update = { it.copy(incomingCallTemplate = template) },
    )

    /** Dart name kept for parity: persists the ONGOING call template. */
    fun setOutgoingCallTemplate(template: String) = setString(
        key = ONGOING_TEMPLATE_KEY,
        template = template,
        read = { it.ongoingCallTemplate },
        update = { it.copy(ongoingCallTemplate = template) },
    )

    private fun setString(
        key: String,
        template: String,
        read: (CallPresentationPreferences) -> String,
        update: (CallPresentationPreferences) -> CallPresentationPreferences,
    ) {
        initialize()
        scope.launch {
            writeMutex.withLock {
                if (read(value) == template) return@withLock
                val saved = storage.putString(key, template)
                if (!saved) {
                    throw IllegalStateException("Unable to save this call screen template.")
                }
                value = update(value)
                notifyListeners()
            }
        }
    }

    private fun setBool(
        key: String,
        enabled: Boolean,
        read: (CallPresentationPreferences) -> Boolean,
        update: (CallPresentationPreferences) -> CallPresentationPreferences,
    ) {
        initialize()
        scope.launch {
            writeMutex.withLock {
                if (read(value) == enabled) return@withLock
                val saved = storage.putBool(key, enabled)
                if (!saved) {
                    throw IllegalStateException("Unable to save this call presentation preference.")
                }
                // Publish only after storage succeeds. Build from the latest
                // state so simultaneous changes to different preferences
                // cannot clobber each other.
                value = update(value)
                notifyListeners()
            }
        }
    }
}

/**
 * Single authoritative manager for the active call UI presentation state
 * machine. Exact port of CallPresentationController. App lifecycle and the
 * native PiP report are injected methods (onAppLifecycleChanged /
 * reportNativePipMode) so the policy is JVM-testable; the Android wiring
 * (ProcessLifecycleOwner, MainActivity PiP callback) belongs to the
 * integration owner.
 */
class CallPresentationController(
    private val callService: CallSignalingService,
    private val preferences: CallPresentationPreferencesStore,
) {
    enum class AppLifecycle { resumed, backgrounded, detached }

    var mode: CallPresentationMode = CallPresentationMode.none
        private set

    private var fullScreenPresentations = 0
    private var isBackgrounded = false

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    private fun notifyListeners() { for (l in listeners) l() }

    /** Dart constructor body: subscribe to call + preference changes. */
    fun start() {
        callService.addStateListener(::handleCallStateChanged)
        preferences.addListener(::reconcilePresentationMode)
        preferences.initialize()
        handleCallStateChanged()
    }

    val isFullScreen: Boolean get() = mode == CallPresentationMode.fullScreen
    val isInAppVideoPip: Boolean get() = mode == CallPresentationMode.inAppVideoPip
    val isInAppIsland: Boolean get() = mode == CallPresentationMode.inAppIsland
    val isSystemPip: Boolean get() = mode == CallPresentationMode.systemPip
    val isBackgroundNotification: Boolean get() = mode == CallPresentationMode.backgroundNotification
    val isNone: Boolean get() = mode == CallPresentationMode.none

    /** Invoked when OngoingCallScreen is mounted or pushed to full-screen. */
    fun showFullScreen() {
        fullScreenPresentations = 1
        mode = CallPresentationMode.fullScreen
        publishMode()
        notifyListeners()
    }

    /** Invoked when OngoingCallScreen is unmounted or the user navigates back into Chaty. */
    fun minimizeToPipOrIsland() {
        fullScreenPresentations = 0
        val session = callService.currentSession
        mode = if (session == null || !session.isActive) {
            CallPresentationMode.none
        } else if (session.isVideo && preferences.value.pictureInPictureEnabled) {
            CallPresentationMode.inAppVideoPip
        } else if (preferences.value.dynamicIslandEnabled) {
            CallPresentationMode.inAppIsland
        } else {
            CallPresentationMode.none
        }
        publishAndNotify()
    }

    /** Transition from Video PiP to Call Island. */
    fun collapseToIsland() {
        if (mode == CallPresentationMode.inAppVideoPip) {
            mode = if (preferences.value.dynamicIslandEnabled) {
                CallPresentationMode.inAppIsland
            } else {
                CallPresentationMode.none
            }
            publishAndNotify()
        }
    }

    /** Transition from Call Island back to Floating PiP (if video call) or full screen. */
    fun expandFromIsland() {
        val session = callService.currentSession
        mode = if (session == null || !session.isActive) {
            CallPresentationMode.none
        } else if (session.isVideo) {
            CallPresentationMode.inAppVideoPip
        } else {
            CallPresentationMode.fullScreen
        }
        publishAndNotify()
    }

    /** Expand floating Video PiP to full screen. */
    fun expandToFullScreen() {
        val session = callService.currentSession
        if (session == null || !session.isActive || isBackgrounded) return
        mode = CallPresentationMode.fullScreen
        fullScreenPresentations = 1
        publishAndNotify()
    }

    /** Force a specific presentation mode if allowable. */
    fun setMode(nextMode: CallPresentationMode) {
        val session = callService.currentSession
        if (nextMode != CallPresentationMode.none &&
            (session == null || !session.isActive)
        ) {
            return
        }
        if (nextMode == CallPresentationMode.inAppVideoPip && session?.isVideo != true) {
            return
        }
        if (nextMode == CallPresentationMode.systemPip) {
            // Native PiP must be confirmed through reportNativePipMode().
            return
        }
        if (mode == nextMode) return
        mode = nextMode
        publishAndNotify()
    }

    /** Synchronizes presentation with the native Android PiP callback. */
    fun reportNativePipMode(active: Boolean) {
        val session = callService.currentSession
        if (active) {
            if (!preferences.value.pictureInPictureEnabled) return
            if (session == null || !session.isActive || !session.isVideo) return
            mode = CallPresentationMode.systemPip
            publishAndNotify()
            return
        }
        if (mode != CallPresentationMode.systemPip) return
        mode = if (isBackgrounded) {
            CallPresentationMode.backgroundNotification
        } else {
            CallPresentationMode.none
        }
        reconcilePresentationMode()
    }

    /** Dart didChangeAppLifecycleState. */
    fun onAppLifecycleChanged(state: AppLifecycle) {
        isBackgrounded = state == AppLifecycle.backgrounded || state == AppLifecycle.detached
        reconcilePresentationMode()
    }

    private fun handleCallStateChanged() {
        reconcilePresentationMode()
    }

    private fun reconcilePresentationMode() {
        val session = callService.currentSession
        val isActive = session != null && session.isActive

        if (!isActive) {
            if (mode != CallPresentationMode.none) {
                mode = CallPresentationMode.none
                fullScreenPresentations = 0
                publishAndNotify()
            }
            return
        }

        if (isBackgrounded) {
            // Until native PiP is confirmed, the foreground notification is the
            // truthful system-level presentation for both audio and video.
            val targetBg = if (mode == CallPresentationMode.systemPip) {
                CallPresentationMode.systemPip
            } else {
                CallPresentationMode.backgroundNotification
            }
            if (mode != targetBg) {
                mode = targetBg
                publishAndNotify()
            }
            return
        }

        if (fullScreenPresentations > 0) {
            if (mode != CallPresentationMode.fullScreen) {
                mode = CallPresentationMode.fullScreen
                publishAndNotify()
            }
            return
        }

        // Call is active in foreground:
        if (mode == CallPresentationMode.inAppIsland &&
            preferences.value.dynamicIslandEnabled
        ) {
            return // Preserve user's minimized choice
        }

        val targetMode =
            if (session!!.isVideo && preferences.value.pictureInPictureEnabled) {
                CallPresentationMode.inAppVideoPip
            } else if (preferences.value.dynamicIslandEnabled) {
                CallPresentationMode.inAppIsland
            } else {
                CallPresentationMode.none
            }

        if (mode != targetMode) {
            mode = targetMode
            publishAndNotify()
        }
    }

    fun dispose() {
        callService.removeStateListener(::handleCallStateChanged)
        preferences.removeListener(::reconcilePresentationMode)
        if (presentationModeSignal == mode) {
            presentationModeSignal = CallPresentationMode.none
        }
    }

    private fun publishMode() {
        if (presentationModeSignal != mode) {
            presentationModeSignal = mode
        }
    }

    private fun publishAndNotify() {
        publishMode()
        notifyListeners()
    }

    companion object {
        /** Process-wide presentation signal used by globally hosted call
         *  surfaces (Dart ValueNotifier<CallPresentationMode>). */
        @Volatile
        var presentationModeSignal: CallPresentationMode = CallPresentationMode.none
            private set
    }
}
