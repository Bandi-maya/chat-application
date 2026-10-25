package com.example.chat.ui.settings.universal

import com.example.chat.ui.icons.ChatyIconId

/** Pure port of the state, catalog and filtering logic of
 *  lib/features/settings/universal/universal_chaty_icons_screen.dart.
 *  JVM-testable; the Compose screen holds only widgets. */
object UniversalIconsLogic {

    /** Dart _categories, exact order. */
    val categories: List<String> = listOf(
        "All", "Navigation", "Messaging", "Calls", "Camera", "Status", "Settings",
    )

    /** Dart _coreContactIcons (14), exact order. */
    val coreContactIcons: List<ChatyIconId> = listOf(
        ChatyIconId.search,
        ChatyIconId.back,
        ChatyIconId.more,
        ChatyIconId.camera,
        ChatyIconId.cameraFlip,
        ChatyIconId.cameraShutter,
        ChatyIconId.callAudio,
        ChatyIconId.callVideo,
        ChatyIconId.send,
        ChatyIconId.attachment,
        ChatyIconId.microphone,
        ChatyIconId.favourite,
        ChatyIconId.settings,
        ChatyIconId.messageCheckDouble,
    )

    /** Dart _categorizedIcons, exact ids and order per category. */
    val categorizedIcons: Map<String, List<ChatyIconId>> = mapOf(
        "Navigation" to listOf(
            ChatyIconId.chats,
            ChatyIconId.updates,
            ChatyIconId.calls,
            ChatyIconId.profile,
            ChatyIconId.tasks,
            ChatyIconId.groups,
            ChatyIconId.desktop,
        ),
        "Messaging" to listOf(
            ChatyIconId.send,
            ChatyIconId.emoji,
            ChatyIconId.attachment,
            ChatyIconId.attachmentPlus,
            ChatyIconId.microphone,
            ChatyIconId.pin,
            ChatyIconId.archive,
            ChatyIconId.favourite,
            ChatyIconId.delete,
            ChatyIconId.reply,
            ChatyIconId.forward,
            ChatyIconId.copy,
            ChatyIconId.share,
        ),
        "Calls" to listOf(
            ChatyIconId.callAudio,
            ChatyIconId.callVideo,
            ChatyIconId.callEnd,
            ChatyIconId.cameraSwitch,
            ChatyIconId.screenShare,
            ChatyIconId.speaker,
        ),
        "Camera" to listOf(
            ChatyIconId.camera,
            ChatyIconId.cameraFlip,
            ChatyIconId.cameraShutter,
            ChatyIconId.cameraFlashOn,
            ChatyIconId.gallery,
        ),
        "Status" to listOf(
            ChatyIconId.signal,
            ChatyIconId.messageCheckSingle,
            ChatyIconId.messageCheckDouble,
            ChatyIconId.messageClock,
        ),
        "Settings" to listOf(
            ChatyIconId.more,
            ChatyIconId.search,
            ChatyIconId.settings,
            ChatyIconId.tune,
            ChatyIconId.lock,
            ChatyIconId.close,
            ChatyIconId.check,
        ),
    )

    private val camelBoundary = Regex("([a-z])([A-Z])")

    /** Dart _iconDisplayName: split camelCase, capitalize each word. */
    fun iconDisplayName(icon: ChatyIconId): String =
        icon.name
            .replace(camelBoundary, "$1 $2")
            .split(" ")
            .joinToString(" ") { w -> w[0].uppercase() + w.substring(1) }

    /** Dart _filteredIcons: 'All' (or an unknown category) means every icon;
     *  the trimmed lowercase query matches against the enum name. */
    fun filteredIcons(selectedCategory: String, query: String): List<ChatyIconId> {
        val q = query.trim().lowercase()
        val base: List<ChatyIconId> =
            if (selectedCategory == "All") ChatyIconId.entries
            else categorizedIcons[selectedCategory] ?: ChatyIconId.entries
        if (q.isEmpty()) return base
        return base.filter { it.name.lowercase().contains(q) }
    }

    /** Dart: _microSizePreview ? 20.0 : 26.0. */
    fun previewSize(microSizePreview: Boolean): Float = if (microSizePreview) 20f else 26f
}
