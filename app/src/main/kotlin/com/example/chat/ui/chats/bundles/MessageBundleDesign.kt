package com.example.chat.ui.chats.bundles

import androidx.compose.runtime.*
import com.example.chat.ServiceLocator
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.templates.ComposerTemplate
import com.example.chat.ui.chats.home.HomeTemplates
import com.example.chat.ui.chats.home.ChatListDensity

/** Geometry-only adaptation, never overrides theme colors or message/send behavior. */
internal data class MessageBundleDesign(val radius: Double, val rowHeight: Double, val bubble: BubbleStyleId, val divider: Boolean)
internal fun messageBundleDesign(id: String): MessageBundleDesign? = when (id) {
    "chaty_default" -> MessageBundleDesign(20.0, 76.0, BubbleStyleId.stock, false)
    "apple_ios" -> MessageBundleDesign(26.0, 80.0, BubbleStyleId.rcIos11, true)
    "samsung_one_ui" -> MessageBundleDesign(24.0, 84.0, BubbleStyleId.rcSamsBord, false)
    "google_phone_latest" -> MessageBundleDesign(30.0, 82.0, BubbleStyleId.materialized, false)
    "google_phone_classic" -> MessageBundleDesign(18.0, 72.0, BubbleStyleId.oldHangouts, true)
    "vivo_iqoo" -> MessageBundleDesign(22.0, 78.0, BubbleStyleId.rounded, false)
    "azure_ring_vertical" -> MessageBundleDesign(28.0, 80.0, BubbleStyleId.roundle, true)
    "mono_chevron_dark" -> MessageBundleDesign(12.0, 68.0, BubbleStyleId.gabiOutline, true)
    "nextcloud_talk" -> MessageBundleDesign(16.0, 74.0, BubbleStyleId.rcLine, true)
    else -> null
}

@Composable
internal fun rememberMessageBundleDesign(): MessageBundleDesign? {
    val store = remember { ServiceLocator.callPresentationPrefs }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val changed: () -> Unit = { revision++ }
        store.addListener(changed); store.initialize()
        onDispose { store.removeListener(changed) }
    }
    @Suppress("UNUSED_EXPRESSION") revision
    return messageBundleDesign(store.value.messageExperienceBundleId)
}

@Composable
internal fun messageBundleTheme(theme: ThemeConfig): ThemeConfig {
    val design = rememberMessageBundleDesign() ?: return theme
    return theme.copy(cornerRadius = design.radius, bubbleStyle = design.bubble)
}

@Composable
internal fun messageBundleHome(templates: HomeTemplates): HomeTemplates {
    val design = rememberMessageBundleDesign() ?: return templates
    return templates.copy(chatList = templates.chatList.copy(itemHeight = design.rowHeight,
        showDivider = design.divider, density = ChatListDensity.regular, referenceDefault = false))
}

@Composable
internal fun messageBundleComposer(template: ComposerTemplate): ComposerTemplate {
    val design = rememberMessageBundleDesign() ?: return template
    return template.copy(cornerRadius = design.radius)
}
