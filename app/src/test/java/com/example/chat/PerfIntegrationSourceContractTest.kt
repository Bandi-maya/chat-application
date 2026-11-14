package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Wiring guards, not Android bitmap, lifecycle or rendering tests. */
class PerfIntegrationSourceContractTest {
    @Test fun streamBoundsDecodeDoesNotMistakeExpectedNullForFailure() {
        val s = File("app/src/main/kotlin/com/example/chat/ui/core/system/SampledBitmapDecoder.kt").readText()
        assertTrue(s.contains("val boundsStream = openStream() ?: return null"))
        assertFalse(s.contains("decodeStream(it, null, bounds) } ?: return null"))
    }
    @Test fun automationSyncGuardsActualWorkWithoutIdleTimer() {
        val s = File("app/src/main/java/com/example/chat/data/services/MessageAutomationService.kt").readText()
        assertTrue(s.contains("private suspend fun sync() = syncMutex.withLock"))
        assertTrue(s.contains("if (disposed || !appInForeground || !syncPending || currentUserId() == null) return@withLock"))
        assertFalse(s.contains("timerJob"))
        assertTrue(s.contains("lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)"))
        assertTrue(s.contains("throw cancelled"))
    }
    @Test fun bundledEmojiCacheIsByteBoundedAndDoesNotRecycleDisplayedBitmaps() {
        val s = File("app/src/main/kotlin/com/example/chat/ui/chats/detail/ChatyEmojiPack.kt").readText()
        val cache = s.substringAfter("private object StaticAssetEmojiCache").substringBefore("/**")
        assertTrue(cache.contains("4 * 1024 * 1024")); assertTrue(cache.contains("allocationByteCount"))
        val renderer = s.substringAfter("internal fun StaticAssetEmoji(")
        assertFalse(renderer.contains(".recycle()")); assertTrue(renderer.contains("SampledBitmapDecoder.decode"))
        assertTrue(renderer.contains("val cacheKey ="))
    }
    @Test fun conversationAnimationUsesExistingEngineAndLocaleAwareHoistedFormatter() {
        val s = File("app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt").readText()
        assertTrue(s.contains("effects.conversationListAnimation"))
        assertTrue(s.contains(".listEntrance(conversationListAnimation, row.anchor.id)"))
        assertTrue(s.contains("remember(dayHeaderLocale)"))
        val engine = File("app/src/main/kotlin/com/example/chat/ui/effects/ListEntranceAnimations.kt").readText()
        assertTrue(engine.contains("rememberChatyReduceMotion()"))
        assertFalse(engine.contains("Settings.Global.getFloat"))
    }
    @Test fun updatesPollingCollectionIsLifecycleBound() {
        val s = File("app/src/main/kotlin/com/example/chat/ui/updates/UpdatesScreen.kt").readText()
        assertTrue(s.contains("lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED)"))
        assertTrue(s.contains("statuses.collect { value = it }"))
        assertFalse(s.contains("statuses.collectAsState(initial = null)"))
    }
    @Test fun groupMemberMemoIncludesMembershipProfilesAdminsIdentityAndQuery() {
        val s = File("app/src/main/kotlin/com/example/chat/ui/chats/group_info/GroupInfoScreen.kt").readText()
        assertTrue(s.contains("loadedMembers = remember(conversation.participantIds, participants, conversation.adminIds, currentUserId)"))
        assertTrue(s.contains("visibleMembers = remember(conversation.participantIds, participants, conversation.adminIds, currentUserId, memberQuery)"))
        assertFalse(s.contains("LazyColumn"))
    }
    @Test fun mediaWorkspaceKeysAndPdfMemoAreInComposableScope() {
        val pdf = File("app/src/main/kotlin/com/example/chat/ui/media/PdfWorkspaceScreen.kt").readText()
        assertTrue(pdf.contains("val pdfs = remember(files) { files.filter { it.category == MediaCategory.pdf } }"))
        assertTrue(pdf.indexOf("val pdfs = remember(files)") < pdf.indexOf("LazyColumn(contentPadding"))
        assertTrue(pdf.contains("items(pdfs.size, key = { pdfs[it].id })"))
        val media = File("app/src/main/kotlin/com/example/chat/ui/media/MediaWorkspaceScreen.kt").readText()
        assertTrue(media.contains("items(files.take(15).size, key = { files[it].id })"))
    }
    @Test fun taskPriorityMemoUsesCurrentTasksAndPriorityFilter() {
        val s = File("app/src/main/kotlin/com/example/chat/ui/tasks/TasksScreen.kt").readText()
        assertTrue(s.contains("val tasks = controller.tasks"))
        assertTrue(s.contains("val filtered = remember(tasks, priorityFilter)"))
        for (priority in listOf("urgent", "high", "medium", "low")) {
            assertTrue(s.contains("task.priority == TaskPriority.$priority"))
        }
    }
    @Test fun starredMemoUsesMessageValuesRatherThanCallbackIdentity() {
        val s = File("app/src/main/kotlin/com/example/chat/ui/chats/starred/StarredMessagesScreen.kt").readText()
        assertTrue(s.contains("val messageSnapshots = conversations.map { it to messagesFor(it.id).toList() }"))
        assertTrue(s.contains("remember(messageSnapshots, query, messagesRevision)"))
        assertFalse(s.contains("remember(conversations, query, messagesFor)"))
        assertTrue(s.contains("it.isStarred && !it.isDeletedForMe"))
        assertTrue(s.contains("sortedByDescending { it.second.createdAt }"))
    }
    @Test fun stableMessageProviderCanReturnChangedValues() {
        val backend = mutableListOf("starred-a")
        val messagesFor: (String) -> List<String> = { backend.toList() }
        val conversations = listOf("chat-a")
        val before = conversations.map { it to messagesFor(it).toList() }
        backend.clear()
        val after = conversations.map { it to messagesFor(it).toList() }
        assertNotEquals(before, after)
        assertTrue(after.single().second.isEmpty())
    }
    @Test fun screenshotPreferenceUsesExistingWindowLeaseAtRoot() {
        val root = File("app/src/main/kotlin/com/example/chat/ui/AppRoot.kt").readText()
        assertTrue(root.contains("ProtectSensitiveWindow(\n        ServiceLocator.preferencesController.security.protectFromScreenshots,"))
        assertTrue(root.contains("preferencesController.addListener(prefsListener)"))
        val lease = File("app/src/main/kotlin/com/example/chat/ui/kit/ProtectSensitiveWindow.kt").readText()
        assertTrue(lease.contains("DisposableEffect(window, active)"))
        assertTrue(lease.contains("onDispose { release() }"))
        assertTrue(lease.contains("if (!lease.alreadySecure) window.clearFlags"))
    }
    @Test fun approvedMediaDoneAndPickerWiringArePresent() {
        val tile = File("app/src/main/kotlin/com/example/chat/ui/chats/detail/MediaDownloadTile.kt").readText()
        assertTrue(tile.contains("S.Done -> Color(0xFF78DC96)"))
        assertTrue(tile.contains("S.Done -> \"Downloaded\""))
        assertTrue(tile.contains("speedAndEta("))
        assertTrue(tile.contains("terminalFail || state == S.Done"))
        val preview = File("app/src/main/kotlin/com/example/chat/ui/chats/detail/MediaAttachmentPreview.kt").readText()
        assertTrue(preview.contains("LaunchedEffect(dlState, dlReq, reduceMotionForTick)"))
        assertTrue(preview.contains("if (!reduceMotionForTick) kotlinx.coroutines.delay(650)"))
        val contact = File("app/src/main/kotlin/com/example/chat/ui/chats/contact_info/ContactInfoScreen.kt").readText()
        assertTrue(contact.contains("title = \"Media auto-download\""))
        assertTrue(contact.contains("setOverride(conversation.id, value)"))
        assertTrue(contact.contains("heightIn(min = 48.dp)"))
    }
    @Test fun approvedChatListPhotoUsesExistingAvatarWithInitialsFallback() {
        val row = File("app/src/main/kotlin/com/example/chat/ui/chats/home/ConversationRow.kt").readText()
        assertTrue(row.contains("url = conversation.avatarUrl"))
        val avatar = File("app/src/main/kotlin/com/example/chat/ui/chats/home/HomeComponents.kt").readText()
        assertTrue(avatar.contains("placeholder = initialsTile"))
        assertTrue(avatar.contains("error = initialsTile"))
    }
    @Test fun messageRevisionAndHiddenSnapshotCrossStrongSkippingBoundary() {
        val root = File("app/src/main/kotlin/com/example/chat/ui/AppRoot.kt").readText()
        assertTrue(root.contains("messagesRevision = starRevision"))
        val star = File("app/src/main/kotlin/com/example/chat/ui/chats/starred/StarredMessagesScreen.kt").readText()
        assertTrue(star.contains("remember(messageSnapshots, query, messagesRevision)"))
        val host = File("app/src/main/kotlin/com/example/chat/ui/ChatsHomeHost.kt").readText()
        assertTrue(host.contains("val hiddenConversationIds = security.hiddenConversationIds.toSet()"))
        assertTrue(host.contains("isConversationHidden = { id -> id in hiddenConversationIds }"))
    }
    @Test fun inactiveCatchupFailsClosedOnPrivacyAndBlockedChannel() {
        val bridge = File("app/src/main/kotlin/com/example/chat/data/api/InactiveAccountPushBridge.kt").readText()
        assertTrue(bridge.contains("val security = profile.optJSONObject(\"security\") ?: return null"))
        assertTrue(bridge.contains("val ids = security.optJSONArray(key) ?: return null"))
        assertTrue(bridge.contains("as? Boolean ?: return null"))
        assertTrue(bridge.contains("followRedirects(false).followSslRedirects(false)"))
        val notifications = File("app/src/main/kotlin/com/example/chat/notifications/ChatyNotifications.kt").readText()
        assertTrue(notifications.contains("if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false"))
    }
    @Test fun accountRecentsAndInactivePickerKeepDistinctEntryRoutes() {
        val root = File("app/src/main/kotlin/com/example/chat/ui/AppRoot.kt").readText()
        assertTrue(root.contains("onOpenAccountSwitcher = { accountRecentsOpen = true }"))
        assertTrue(root.contains("startInManage = accountSwitcherOpen"))
        val inactive = root.substringAfter("LaunchedEffect(inactiveAccountAlert, lockRequired)").substringBefore("var splitChatState")
        assertTrue(inactive.contains("if (lockRequired) return@LaunchedEffect"))
        assertTrue(inactive.contains("accountSwitcherOpen = true"))
    }
    @Test fun unsafeSharedDraftDebounceIsNotInActiveSource() {
        val s = File("app/src/main/kotlin/com/example/chat/data/api/ApiBackendService.kt").readText()
        val setter = s.substringAfter("fun setDraft(").substringBefore("val users:")
        assertTrue(setter.contains("multiAccount.saveDraft(key, conversationId, draft)"))
        assertFalse(s.contains("pendingDraftPersist")); assertFalse(s.contains("PendingDraftBuffer"))
    }
}
