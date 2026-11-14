package com.example.chat

import com.example.chat.core.preferences.LocalPreferenceChoicePolicy
import org.junit.Assert.*
import org.junit.Test

class LocalPreferenceChoicePolicyTest {
    @Test fun selectedFieldsWinEvenAgainstNewerServerValuesWhileUnselectedFieldsCanFill() {
        val local = mapOf("privacy" to mapOf("readReceipts" to false, "online" to true))
        val remote = mapOf("privacy" to mapOf("readReceipts" to true, "online" to false))
        val result = LocalPreferenceChoicePolicy.merge(remote, local, mapOf("privacy" to setOf("readReceipts")))
        assertEquals(mapOf("readReceipts" to false, "online" to false), result["privacy"])
    }
    @Test fun remoteCannotImportChoiceMarkersOrResetWholeExplicitGroup() {
        val local = mapOf("home" to mapOf("layout" to "list"))
        val remote = mapOf("home" to mapOf("layout" to "grid"), LocalPreferenceChoicePolicy.KEY to mapOf("privacy" to listOf("*")))
        val result = LocalPreferenceChoicePolicy.merge(remote, local, mapOf("home" to setOf("*")))
        assertEquals(local["home"], result["home"]); assertFalse(result.containsKey(LocalPreferenceChoicePolicy.KEY))
    }
}
