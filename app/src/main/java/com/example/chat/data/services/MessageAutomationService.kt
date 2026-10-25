package com.example.chat.data.services

import com.example.chat.domain.models.MessageAutomationPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private var syncing = false
    private val onPreferencesChanged: () -> Unit = { launchSync() }

    init {
        addListener(onPreferencesChanged)
        // Timer.periodic(30s) + immediate kickoff, same as Dart.
        scope.launch {
            while (isActive) {
                delay(30_000)
                sync()
            }
        }
        launchSync()
    }

    private fun launchSync() {
        scope.launch { sync() }
    }

    private suspend fun sync() {
        if (syncing || currentUserId() == null) return
        syncing = true
        try {
            backend.synchronizeAutomation(automation())
        } catch (_: Exception) {
            // Account sync retries on the next preference change / periodic pass.
        } finally {
            syncing = false
        }
    }

    /** Kept for presentation compatibility. Incoming auto-reply execution now
     *  happens in the database trigger after the incoming message is committed. */
    fun handleIncomingMessageAutoReply(conversationId: String, text: String) {
        launchSync()
    }

    fun dispose() {
        removeListener(onPreferencesChanged)
    }
}
