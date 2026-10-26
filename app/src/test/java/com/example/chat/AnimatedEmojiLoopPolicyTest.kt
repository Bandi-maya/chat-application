package com.example.chat

import com.example.chat.ui.chats.detail.EmojiDisplayMode
import com.example.chat.ui.chats.detail.emojiLoopsForever
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedEmojiLoopPolicyTest {
    @Test fun `every display mode loops forever including single jumbo emoji`() {
        EmojiDisplayMode.entries.forEach { assertTrue("$it must loop", emojiLoopsForever(it)) }
    }
}
