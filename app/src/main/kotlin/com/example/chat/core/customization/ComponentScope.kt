package com.example.chat.core.customization

/** Port of component_scope.dart. Hierarchy of scopes from lowest to highest precedence (enum index). */
enum class CustomizationScopeType { defaultTheme, globalUser, screen, conversation, transientPreview }

data class ComponentScope private constructor(val type: CustomizationScopeType, val identifier: String? = null) {
    val precedence: Int get() = type.ordinal
    fun hasHigherPrecedenceThan(other: ComponentScope): Boolean = precedence > other.precedence
    override fun toString(): String = "ComponentScope(${type.name}${if (identifier != null) ":$identifier" else ""})"

    companion object {
        val defaultTheme = ComponentScope(CustomizationScopeType.defaultTheme)
        val globalUser = ComponentScope(CustomizationScopeType.globalUser)
        val transientPreview = ComponentScope(CustomizationScopeType.transientPreview)
        fun screen(screenName: String) = ComponentScope(CustomizationScopeType.screen, screenName)
        fun conversation(conversationId: String) = ComponentScope(CustomizationScopeType.conversation, conversationId)
    }
}
