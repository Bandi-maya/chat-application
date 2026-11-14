package com.example.chat

import com.example.chat.data.api.ApiUnreadPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiUnreadPolicyTest {
    @Test fun incomingNewMessageIncrementsOnce() = assertEquals(3, ApiUnreadPolicy.afterRealtime(2, true, false, false, true))
    @Test fun duplicateDoesNotIncrement() = assertEquals(2, ApiUnreadPolicy.afterRealtime(2, false, false, false, true))
    @Test fun ownEchoDoesNotIncrement() = assertEquals(2, ApiUnreadPolicy.afterRealtime(2, true, true, false, true))
    @Test fun visibleConversationIsRead() = assertEquals(0, ApiUnreadPolicy.afterRealtime(2, true, false, true, true))
    @Test fun systemOrDeletedMessageDoesNotIncrement() = assertEquals(2, ApiUnreadPolicy.afterRealtime(2, true, false, false, false))
    @Test fun negativeCountsAreClamped() = assertEquals(1, ApiUnreadPolicy.afterRealtime(-3, true, false, false, true))
    @Test fun integerLimitDoesNotOverflow() = assertEquals(Int.MAX_VALUE, ApiUnreadPolicy.afterRealtime(Int.MAX_VALUE, true, false, false, true))
}
