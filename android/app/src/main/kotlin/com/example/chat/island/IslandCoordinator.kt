package com.example.chat.island

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification

/**
 * Singleton coordinator bridging NotificationListenerService, MediaSession, Battery,
 * and the persistent IslandRenderer.
 */
object IslandCoordinator {
    private var isInitialized = false
    private lateinit var store: IslandStore
    private lateinit var windowController: IslandWindowController
    private lateinit var renderer: IslandRenderer
    private lateinit var notificationIngest: NotificationIngest
    private var batteryTracker: BatteryTracker? = null

    fun initialize(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext ?: context

        windowController = IslandWindowController(appContext)
        notificationIngest = NotificationIngest(appContext)

        store = IslandStore { snapshot ->
            renderer.render(snapshot)
        }

        renderer = IslandRenderer(appContext, windowController, store)

        batteryTracker = BatteryTracker(appContext) { chargingActivity ->
            store.setCharging(chargingActivity)
        }
        batteryTracker?.start()

        isInitialized = true
    }

    fun onNotificationPosted(sbn: StatusBarNotification, isLocked: Boolean) {
        if (!isInitialized) return
        val msg = notificationIngest.ingest(sbn, isLocked) ?: return
        store.upsertMessage(msg)
    }

    fun onNotificationRemoved(key: String) {
        if (!isInitialized) return
        store.removeMessage(key)
    }

    fun updateMedia(media: MediaActivity?) {
        if (!isInitialized) return
        store.setMedia(media)
    }

    fun reset() {
        if (!isInitialized) return
        store.reset()
        windowController.detach()
    }

    fun preview(context: Context) {
        initialize(context)
        store.upsertMessage(
            MessageActivity(
                key = "preview_1",
                packageName = context.packageName,
                appIcon = null,
                title = "Chaty Island",
                body = "Interactive dynamic island stack is active.",
            ),
        )
        store.upsertMessage(
            MessageActivity(
                key = "preview_2",
                packageName = context.packageName,
                appIcon = null,
                title = "Alice Morgan",
                body = "Let's review the new spring animations!",
            ),
        )
    }
}
