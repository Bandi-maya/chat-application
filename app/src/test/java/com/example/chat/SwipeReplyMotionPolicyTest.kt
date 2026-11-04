package com.example.chat

import com.example.chat.ui.chats.detail.SwipeReplyMotionPolicy
import org.junit.Assert.*
import org.junit.Test

class SwipeReplyMotionPolicyTest {
    @Test fun directBeforeThresholdAndContinuousAfter() {
        assertEquals(20f, SwipeReplyMotionPolicy.offset(20f, 48f, 68f), 0f)
        assertEquals(48f, SwipeReplyMotionPolicy.offset(48f, 48f, 68f), 0f)
        val offset = SwipeReplyMotionPolicy.offset(60f, 48f, 68f)
        assertTrue(offset > 48f && offset < 60f)
    }
    @Test fun boundedAndInvalidInputs() {
        assertEquals(0f, SwipeReplyMotionPolicy.offset(-10f, 48f, 68f), 0f)
        assertEquals(0f, SwipeReplyMotionPolicy.offset(Float.NaN, 48f, 68f), 0f)
        assertEquals(68f, SwipeReplyMotionPolicy.offset(10000f, 48f, 68f), 0f)
    }
}
