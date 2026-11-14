package com.example.chat.data.services

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.chat.domain.models.MessageAutomationPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

/** Exact port of lib/data/services/message_automation_service.dart.
 *
 *  Keeps the account-side automation configuration synchronized with Supabase.
 *  Scheduled execution and auto-replies are performed server-side so they keep
 *  working when the mobile process is backgrounded or terminated.
 *
 *  Seams (Dart dependencies not yet ported):
 *  - ChatyPreferencesController -> [automation] provider + [addListener]/[removeListener]
 *  - Supabase.instance.client.auth.currentUser -> [currentUserId]
 *  - ChatyDataStore -> constructor-only on Dart; not needed by any method, omitted.
 */
class MessageAutomationService(
    private val automation: () -> MessageAutomationPreferences,
    private val currentUserId: () -> String?,
    private val backend: GbFeatureBackendService,
    private val scope: CoroutineScope,
    private val addListener: (() -> Unit) -> Unit,
    private val removeListener: (() -> Unit) -> Unit,
) {
    private val syncMutex = Mutex()
    private var disposed = false
    private var lifecycleOwner: LifecycleOwner? = null

    /** Set when a push may be needed: on preference change, on service start
     *  (covers sign-in after init), and after a failed pass (retry). Cleared
     *  at the START of a sync attempt, so a change landing mid-flight re-sets
     *  it and survives that attempt's completion. */
    @Volatile
    private var syncPending = true

    /** Retrying a failed push is a foreground affordance: a backgrounded user
     *  is not editing rules, and the next resume (or process start) re-kicks. */
    @Volatile
    private var appInForeground = true

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            appInForeground = true
            if (syncPending) launchSync()
        }

        override fun onPause(owner: LifecycleOwner) {
            appInForeground = false
        }
    }

    /** Wire foreground/resume behavior (same pattern as ConnectionHealthService). */
    fun attachLifecycle(owner: LifecycleOwner) {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = owner
        appInForeground = owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        owner.lifecycle.addObserver(lifecycleObserver)
    }

    private val onPreferencesChanged: () -> Unit = {
        syncPending = true
        launchSync()
    }

    init {
        addListener(onPreferencesChanged)
        // Changes, initial load, incoming events and resume trigger sync.
        // A failed push stays pending until the next event; no idle retry timer.
        launchSync()
    }

    private fun launchSync() {
        scope.launch { sync() }
    }

    private suspend fun sync() = syncMutex.withLock {
        if (disposed || !appInForeground || !syncPending || currentUserId() == null) return@withLock
        syncPending = false
        try {
            backend.synchronizeAutomation(automation())
        } catch (cancelled: CancellationException) {
            syncPending = true
            throw cancelled
        } catch (_: Exception) {
            syncPending = true
        }
    }

    /** Kept for presentation compatibility. Incoming auto-reply execution now
     *  happens in the database trigger after the incoming message is committed. */
    fun handleIncomingMessageAutoReply(conversationId: String, text: String) {
        launchSync()
    }

    fun dispose() {
        disposed = true
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        removeListener(onPreferencesChanged)
    }
}
