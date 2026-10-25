package com.example.chat.core.customization

/** Persistence seam: SharedPreferences string at [CustomizationController.storageKey]. */
interface CustomizationStateStore {
    suspend fun getString(key: String): String?
    suspend fun setString(key: String, value: String)
}

/** Port of customization_controller.dart (ChangeNotifier -> synchronous listeners). The Dart constructor kicks
 *  off an async _load(); here the owner must call [load] once at startup (Dart's isLoaded gates nothing else). */
class CustomizationController(private val store: CustomizationStateStore) {
    var snapshot: CustomizationSnapshot = CustomizationSnapshot.defaultSnapshot; private set
    var previewSnapshot: CustomizationSnapshot? = null; private set
    private val history = CustomizationHistory()
    var isLoaded: Boolean = false; private set

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyListeners() { listeners.forEach { it() } }

    val effectiveSnapshot: CustomizationSnapshot get() = previewSnapshot ?: snapshot
    val isPreviewing: Boolean get() = previewSnapshot != null
    val isDirty: Boolean get() = previewSnapshot != null && previewSnapshot !== snapshot
    val canUndo: Boolean get() = history.canUndo

    suspend fun load() {
        val raw = try { store.getString(storageKey) } catch (_: Exception) { null }
        if (!raw.isNullOrEmpty()) {
            try {
                CustomizationSnapshot.decode(raw)?.let { snapshot = CustomizationMigration.migrate(it.toJson()) }
            } catch (_: Exception) {}
        }
        isLoaded = true
        notifyListeners()
    }

    fun stage(preview: CustomizationSnapshot) { previewSnapshot = preview; notifyListeners() }

    fun cancelPreview() {
        if (previewSnapshot != null) { previewSnapshot = null; notifyListeners() }
    }

    private suspend fun persist() = store.setString(storageKey, snapshot.encode())

    suspend fun commit(description: String = "Updated customization") {
        val target = previewSnapshot ?: snapshot
        history.record(ComponentScope.globalUser, snapshot, target, description)
        snapshot = target
        previewSnapshot = null
        notifyListeners()
        persist()
    }

    suspend fun undo() {
        val item = history.popUndo() ?: return
        snapshot = item.previous
        previewSnapshot = null
        notifyListeners()
        persist()
    }

    suspend fun setVariant(componentId: ComponentId, variantId: String, immediate: Boolean = false) {
        val e = effectiveSnapshot
        val updated = when (componentId) {
            ComponentId.navigation -> e.copyWith(navigationVariant = variantId)
            ComponentId.header -> e.copyWith(headerVariant = variantId)
            ComponentId.composer -> e.copyWith(composerVariant = variantId)
            ComponentId.bubble -> e.copyWith(bubbleVariant = variantId)
            ComponentId.tick -> e.copyWith(tickVariant = variantId)
            ComponentId.chatList -> e.copyWith(chatListVariant = variantId)
            ComponentId.reactionRail -> e.copyWith(reactionVariant = variantId)
            ComponentId.modal, ComponentId.sheet, ComponentId.actionSheet, ComponentId.contextMenu ->
                e.copyWith(popupVariant = variantId)
            ComponentId.button -> e.copyWith(buttonVariant = variantId)
            ComponentId.textField -> e.copyWith(textFieldVariant = variantId)
            ComponentId.avatar -> e.copyWith(avatarVariant = variantId)
            ComponentId.wallpaper -> e.copyWith(wallpaperId = variantId)
            else -> return
        }
        stage(updated)
        if (immediate) commit()
    }

    suspend fun setConversationOverride(conversationId: String, componentId: ComponentId, variantId: String) {
        val current = LinkedHashMap(snapshot.conversationOverrides)
        val conv = LinkedHashMap(current[conversationId] ?: emptyMap())
        conv[componentId.name] = variantId
        current[conversationId] = conv
        snapshot = snapshot.copyWith(conversationOverrides = current)
        notifyListeners()
        persist()
    }

    suspend fun resetConversation(conversationId: String) {
        val current = LinkedHashMap(snapshot.conversationOverrides)
        current.remove(conversationId)
        snapshot = snapshot.copyWith(conversationOverrides = current)
        notifyListeners()
        persist()
    }

    suspend fun resetAll() {
        history.record(ComponentScope.defaultTheme, snapshot, CustomizationSnapshot.defaultSnapshot, "Reset all customizations to defaults")
        snapshot = CustomizationSnapshot.defaultSnapshot
        previewSnapshot = null
        notifyListeners()
        persist()
    }

    companion object { const val storageKey = "chaty.customization.snapshot_v1" }
}
