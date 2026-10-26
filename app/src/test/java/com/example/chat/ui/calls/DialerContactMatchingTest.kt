package com.example.chat.ui.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialerContactMatchingTest {
    @Test fun emptyQueryShowsContacts() { assertTrue(matchesDialerContact("", "Bandi Maya", "")) }
    @Test fun namesAndFormattedNumbersKeepWorking() {
        assertTrue(matchesDialerContact("maya", "Bandi Maya", ""))
        assertTrue(matchesDialerContact("995167", "Bandi Maya", "+91 99516 72843"))
    }
    @Test fun digitKeysMatchTheLettersPrintedBelowThem() {
        assertTrue(matchesDialerContact("22634", "Bandi Maya", ""))
        assertTrue(matchesDialerContact("6292", "Bandi Maya", ""))
        assertTrue(matchesDialerContact("77779999", "Pqrs Wxyz", ""))
        assertFalse(matchesDialerContact("111", "Bandi Maya", ""))
    }
    @Test fun symbolsDoNotMatchEveryName() {
        assertFalse(matchesDialerContact("*", "Bandi Maya", ""))
        assertFalse(matchesDialerContact("#", "Bandi Maya", ""))
        assertFalse(matchesDialerContact("+", "Bandi Maya", ""))
    }
    @Test fun unrelatedNameDoesNotMatch() { assertFalse(matchesDialerContact("22634", "Alice", "")) }
}
