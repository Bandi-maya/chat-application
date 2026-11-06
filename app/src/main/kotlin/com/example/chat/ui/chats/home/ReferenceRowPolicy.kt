package com.example.chat.ui.chats.home

import com.example.chat.core.templates.ChatyTemplateId
import com.example.chat.core.templates.TemplateComponentType
import com.example.chat.core.templates.UserTemplateConfiguration

/** Presentation-only adapter. No saved template or row preference is rewritten. */
fun resolveHomeTemplates(
    configuration: UserTemplateConfiguration,
    referencePresentation: Boolean,
    chatList: com.example.chat.core.templates.ChatListTemplate,
): HomeTemplates {
    if (configuration.baseTemplate == ChatyTemplateId.messageFirst &&
        !configuration.isOverridden(TemplateComponentType.chatList)) {
        return if (referencePresentation) referenceDefaultHomeTemplates else defaultHomeTemplates
    }
    return HomeTemplates(defaultHomeTemplates.headerStyle, ChatListTemplate(
        density = ChatListDensity.valueOf(chatList.density.name),
        avatarShape = chatList.avatarShape,
        showPresenceBadge = chatList.showPresenceBadge,
        showDivider = chatList.showDivider,
        itemHeight = chatList.itemHeight,
    ))
}
