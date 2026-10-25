package com.example.chat.ui.settings.templates

import com.example.chat.core.templates.ChatyTemplateId

/** template_preview_widget.dart accent per template id (pure, JVM tested). */
fun templatePreviewAccent(id: ChatyTemplateId): Long = when (id) {
    ChatyTemplateId.visualSocial -> 0xFFE45D9C
    ChatyTemplateId.stream -> 0xFF12A7B8
    ChatyTemplateId.cameraFirst -> 0xFFF0A43A
    ChatyTemplateId.messageFirst -> 0xFF4388F5
    ChatyTemplateId.powerChat -> 0xFF20A985
    ChatyTemplateId.community -> 0xFF8067E8
}
