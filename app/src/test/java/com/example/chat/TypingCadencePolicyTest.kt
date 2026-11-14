package com.example.chat

import com.example.chat.data.api.TypingCadencePolicy
import org.junit.Assert.*
import org.junit.Test

class TypingCadencePolicyTest {
    @Test fun firstInputPublishesImmediatelyButNotEveryKeystroke() {
        assertTrue(TypingCadencePolicy.shouldPublish(true, false, 0, 0))
        assertFalse(TypingCadencePolicy.shouldPublish(true, true, 2999, 0))
        assertTrue(TypingCadencePolicy.shouldPublish(true, true, 3000, 0))
    }
    @Test fun blankInputNeverBecomesPositiveHeartbeat() {
        assertFalse(TypingCadencePolicy.shouldPublish(false, true, 9000, 0))
        assertFalse(TypingCadencePolicy.shouldPublish(false, false, 9000, 0))
    }
}
