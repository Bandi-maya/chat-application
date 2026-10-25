package com.example.chat.core.templates

import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString

/** Port of lib/ui/core/templates/applied_preview_model.dart (pure, JVM-testable). */
fun themeWithTemplateConversation(current: ThemeConfig, conversation: ConversationTemplate): ThemeConfig =
    current.copy(
        bubbleStyle = bubbleStyleIdFromString(conversation.bubbleStyle),
        deliveryTickStyle = deliveryIconStyleFromString(conversation.tickStyle),
        tickStyle = conversation.tickStyle,
        cornerRadius = conversation.bubbleCornerRadius,
    )

data class PreviewLayout(
    val navigation: NavigationTemplate,
    val home: HomeTemplate,
    val chatList: ChatListTemplate,
    val conversation: ConversationTemplate,
    val composer: ComposerTemplate,
    val bottomBarStyle: String,
    val destinationIds: List<String>,
    val homeStylePreset: String,
    val showStories: Boolean,
) {
    companion object {
        fun resolve(
            config: UserTemplateConfiguration,
            bottomBarStyle: String? = null,
            hiddenDestinationIds: Set<String> = emptySet(),
            homeStylePreset: String? = null,
            showStories: Boolean? = null,
        ): PreviewLayout {
            fun of(c: TemplateComponentType) = ChatyTemplateRegistry.get(config.resolveFor(c))
            val navigation = of(TemplateComponentType.navigation).navigation
            val home = of(TemplateComponentType.home).home
            val ids = (config.navigationPrimaryDestinationIds ?: navigation.primaryDestinationIds)
                .filter { it !in hiddenDestinationIds }
            return PreviewLayout(
                navigation = navigation,
                home = home,
                chatList = of(TemplateComponentType.chatList).chatList,
                conversation = of(TemplateComponentType.conversation).conversation,
                composer = of(TemplateComponentType.composer).composer,
                bottomBarStyle = bottomBarStyle ?: navigation.bottomBarStyleName,
                destinationIds = ids.take(5),
                homeStylePreset = homeStylePreset ?: home.homeStylePreset,
                showStories = showStories ?: home.showStoriesStrip,
            )
        }
    }
}

data class AppliedPreviewModel(val theme: ThemeConfig, val layout: PreviewLayout) {
    companion object {
        fun resolve(palette: ThemeConfig, config: UserTemplateConfiguration, applyTemplateConversation: Boolean): AppliedPreviewModel {
            val layout = PreviewLayout.resolve(config)
            return AppliedPreviewModel(
                theme = if (applyTemplateConversation) themeWithTemplateConversation(palette, layout.conversation) else palette,
                layout = layout,
            )
        }
    }
}
