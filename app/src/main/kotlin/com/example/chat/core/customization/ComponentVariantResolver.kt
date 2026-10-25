package com.example.chat.core.customization

/** Port of component_variant_resolver.dart (the BuildContext factory lives in the Compose helper). */
class ComponentVariantResolver(registry: ChatyComponentRegistry? = null) {
    val registry: ChatyComponentRegistry = registry ?: ChatyComponentRegistry.instance

    fun resolve(
        componentId: ComponentId,
        snapshot: CustomizationSnapshot,
        context: VariantContext,
        scope: ComponentScope = ComponentScope.globalUser,
        previewSnapshot: CustomizationSnapshot? = null,
    ): ComponentSpec {
        var candidateId = ""
        if (scope == ComponentScope.transientPreview && previewSnapshot != null) {
            candidateId = extractVariant(componentId, previewSnapshot)
        }
        if (candidateId.isEmpty() && scope.type == CustomizationScopeType.conversation && scope.identifier != null) {
            snapshot.conversationOverrides[scope.identifier]?.let { o -> o[componentId.name]?.let { candidateId = it } }
        }
        if (candidateId.isEmpty() && scope.type == CustomizationScopeType.screen && scope.identifier != null) {
            snapshot.screenOverrides[scope.identifier]?.let { o -> o[componentId.name]?.let { candidateId = it } }
        }
        if (candidateId.isEmpty()) candidateId = extractVariant(componentId, snapshot)
        if (candidateId.isEmpty()) candidateId = registry.getDefaultVariantId(componentId)
        return registry.nearestCompatible(componentId, candidateId, context)
    }

    private fun extractVariant(id: ComponentId, s: CustomizationSnapshot): String = when (id) {
        ComponentId.navigation -> s.navigationVariant
        ComponentId.header -> s.headerVariant
        ComponentId.composer -> s.composerVariant
        ComponentId.bubble -> s.bubbleVariant
        ComponentId.tick -> s.tickVariant
        ComponentId.chatList -> s.chatListVariant
        ComponentId.reactionRail -> s.reactionVariant
        ComponentId.modal, ComponentId.sheet, ComponentId.actionSheet, ComponentId.contextMenu -> s.popupVariant
        ComponentId.button -> s.buttonVariant
        ComponentId.textField -> s.textFieldVariant
        ComponentId.avatar -> s.avatarVariant
        ComponentId.wallpaper -> s.wallpaperId
        else -> ""
    }
}
