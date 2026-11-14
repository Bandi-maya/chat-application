package com.example.chat.bubbles

/** Fail closed. A bubble never switches accounts or unlocks a protected chat. */
internal object BubbleAccessPolicy {
    fun allowed(
        enabled: Boolean, authenticated: Boolean, sameScope: Boolean,
        sameAccount: Boolean, deviceLocked: Boolean, appLocked: Boolean,
        protectedConversation: Boolean, conversationExists: Boolean,
    ): Boolean = enabled && authenticated && sameScope && sameAccount &&
        !deviceLocked && !appLocked && !protectedConversation && conversationExists
}
