package com.example.chat

import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.preferences.LockCredentialStore
import com.example.chat.core.preferences.PreferencesAuthSource
import com.example.chat.core.preferences.PreferencesBlobStore
import com.example.chat.core.preferences.RemoteSettingsRow
import com.example.chat.core.preferences.RemoteSettingsStore
import com.example.chat.data.services.GbFeatureBackendService
import com.example.chat.domain.models.AutoReplyRule
import com.example.chat.ui.automation.AutoReplyRuleEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests written from the Dart engine's observable behavior in
 *  lib/features/automation/auto_reply_rule_engine.dart. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoReplyRuleEngineParityTest {

    private class FakeBlobStore : PreferencesBlobStore {
        val blobs = mutableMapOf<String?, Map<String, Any?>>()
        var storedId: String? = null
        override suspend fun load(userId: String?) = blobs[userId] ?: emptyMap()
        override suspend fun hasScoped(userId: String) = blobs.containsKey(userId)
        override suspend fun save(userId: String?, data: Map<String, Any?>) { blobs[userId] = data }
        override suspend fun clearGlobal() { blobs.remove(null) }
        override suspend fun storedUserId(): String? = storedId
        override suspend fun setStoredUserId(userId: String?) { storedId = userId }
    }

    private class FakeRemote : RemoteSettingsStore {
        var row: RemoteSettingsRow? = null
        override suspend fun fetch(userId: String): RemoteSettingsRow? = row
        override suspend fun push(userId: String, snapshot: Map<String, Any?>) {}
    }

    private class FakeLock : LockCredentialStore {
        val credentials = mutableMapOf<String, Pair<String, Int?>>()
        override suspend fun hasCredential(method: String) = credentials.containsKey(method)
        override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {
            credentials[method] = secret to pinLength
        }
    }

    private class FakeAuth(var userId: String? = null) : PreferencesAuthSource {
        override fun currentUserId(): String? = userId
        override fun addListener(listener: (String?) -> Unit) {}
        override fun removeListener(listener: (String?) -> Unit) {}
    }

    private var nowValue = 1_700_000_000_000L

    private fun engineFixture(
        rules: List<AutoReplyRule>,
        masterEnabled: Boolean = true,
    ): Pair<ChatyPreferencesController, AutoReplyRuleEngine> {
        val scope = TestScope(UnconfinedTestDispatcher())
        val controller = ChatyPreferencesController(
            store = FakeBlobStore(),
            remote = FakeRemote(),
            lockStore = FakeLock(),
            auth = FakeAuth("u1"),
            scope = scope,
            now = { nowValue },
        )
        controller.updateAutomation(
            controller.automation.copy(
                enableAutoReply = masterEnabled,
                autoReplyRules = rules,
            ),
        )
        // Null clients: every backend call follows the Dart "Supabase
        // unavailable" branch (silent no-op), so the engine is testable on JVM.
        val backend = GbFeatureBackendService(
            queryClient = null,
            rpcClient = null,
            currentUserId = { "u1" },
        )
        val engine = AutoReplyRuleEngine(
            prefs = controller,
            backend = backend,
            scope = scope,
            now = { nowValue },
        )
        return controller to engine
    }

    private fun rule(
        keyword: String,
        matchType: String = "contains",
        recipientFilter: String = "all",
        enabled: Boolean = true,
        response: String = "auto response",
    ) = AutoReplyRule(
        id = "rule_test_$keyword",
        keyword = keyword,
        responseMessage = response,
        matchType = matchType,
        recipientFilter = recipientFilter,
        enabled = enabled,
    )

    @Test fun `master disabled matches nothing`() {
        val (_, engine) = engineFixture(listOf(rule("hello")), masterEnabled = false)
        assertNull(engine.evaluate("hello", isDirect = true))
    }

    @Test fun `blank message matches nothing`() {
        val (_, engine) = engineFixture(listOf(rule("hello")))
        assertNull(engine.evaluate("   ", isDirect = true))
        assertNull(engine.evaluate("", isDirect = true))
    }

    @Test fun `disabled rule is skipped`() {
        val (_, engine) = engineFixture(listOf(rule("hello", enabled = false)))
        assertNull(engine.evaluate("hello", isDirect = true))
    }

    @Test fun `contains match is case-insensitive and trims`() {
        val (_, engine) = engineFixture(listOf(rule("Hello")))
        val matched = engine.evaluate("  well HELLO there  ", isDirect = true)
        assertEquals("rule_test_Hello", matched?.id)
    }

    @Test fun `exact match requires full equality`() {
        val (_, engine) = engineFixture(listOf(rule("hello", matchType = "exact")))
        assertNull(engine.evaluate("hello there", isDirect = true))
        assertEquals("rule_test_hello", engine.evaluate("  HELLO ", isDirect = true)?.id)
    }

    @Test fun `starts_with match`() {
        val (_, engine) = engineFixture(listOf(rule("hello", matchType = "starts_with")))
        assertEquals("rule_test_hello", engine.evaluate("hello there", isDirect = true)?.id)
        assertNull(engine.evaluate("say hello", isDirect = true))
    }

    @Test fun `unknown match type falls back to contains`() {
        val (_, engine) = engineFixture(listOf(rule("ell", matchType = "regex")))
        assertEquals("rule_test_ell", engine.evaluate("hello", isDirect = true)?.id)
    }

    @Test fun `empty keyword is skipped`() {
        val (_, engine) = engineFixture(listOf(rule("   ")))
        assertNull(engine.evaluate("hello", isDirect = true))
    }

    @Test fun `direct filter requires direct message`() {
        val (_, engine) = engineFixture(listOf(rule("hi", recipientFilter = "direct")))
        assertNull(engine.evaluate("hi", isDirect = false))
        assertEquals("rule_test_hi", engine.evaluate("hi", isDirect = true)?.id)
    }

    @Test fun `contacts filter requires direct message`() {
        val (_, engine) = engineFixture(listOf(rule("hi", recipientFilter = "Contacts")))
        assertNull(engine.evaluate("hi", isDirect = false))
        assertEquals("rule_test_hi", engine.evaluate("hi", isDirect = true)?.id)
    }

    @Test fun `group filter requires group message`() {
        val (_, engine) = engineFixture(listOf(rule("hi", recipientFilter = "group")))
        assertNull(engine.evaluate("hi", isDirect = true))
        assertEquals("rule_test_hi", engine.evaluate("hi", isDirect = false)?.id)
    }

    @Test fun `all filter matches both`() {
        val (_, engine) = engineFixture(listOf(rule("hi", recipientFilter = "all")))
        assertEquals("rule_test_hi", engine.evaluate("hi", isDirect = true)?.id)
        assertEquals("rule_test_hi", engine.evaluate("hi", isDirect = false)?.id)
    }

    @Test fun `first matching rule wins`() {
        val (_, engine) = engineFixture(
            listOf(
                rule("hello", response = "first"),
                rule("hello", response = "second"),
            ),
        )
        assertEquals("first", engine.evaluate("hello", isDirect = true)?.responseMessage)
    }

    @Test fun `addRule appends enabled rule with timestamped id`() {
        val (controller, engine) = engineFixture(emptyList())
        engine.addRule(keyword = "  status ", responseMessage = " busy ", matchType = "exact")
        val rules = controller.automation.autoReplyRules
        assertEquals(1, rules.size)
        assertEquals("status", rules[0].keyword)
        assertEquals("busy", rules[0].responseMessage)
        assertTrue(rules[0].enabled)
        assertTrue(rules[0].id.startsWith("rule_${nowValue}_"))
    }

    @Test fun `toggleRule flips enabled and ignores out of range`() {
        val (controller, engine) = engineFixture(listOf(rule("a")))
        engine.toggleRule(0, false)
        assertFalse(controller.automation.autoReplyRules[0].enabled)
        engine.toggleRule(5, true) // no crash, no change
        engine.toggleRule(-1, true)
        assertEquals(1, controller.automation.autoReplyRules.size)
    }

    @Test fun `deleteRule removes and ignores out of range`() {
        val (controller, engine) = engineFixture(listOf(rule("a"), rule("b")))
        engine.deleteRule(0)
        assertEquals(listOf("b"), controller.automation.autoReplyRules.map { it.keyword })
        engine.deleteRule(9)
        assertEquals(1, controller.automation.autoReplyRules.size)
    }

    @Test fun `setMasterEnabled flips gate`() {
        val (controller, engine) = engineFixture(listOf(rule("a")), masterEnabled = false)
        assertFalse(engine.isMasterEnabled)
        engine.setMasterEnabled(true)
        assertTrue(controller.automation.enableAutoReply)
        assertTrue(engine.isMasterEnabled)
    }

    @Test fun `processInboundMessage sends matched response`() {
        val scope = TestScope(UnconfinedTestDispatcher())
        val controller = ChatyPreferencesController(
            store = FakeBlobStore(),
            remote = FakeRemote(),
            lockStore = FakeLock(),
            auth = FakeAuth("u1"),
            scope = scope,
            now = { nowValue },
        )
        controller.updateAutomation(
            controller.automation.copy(
                enableAutoReply = true,
                autoReplyRules = listOf(rule("hello", response = "hey there")),
            ),
        )
        val sent = mutableListOf<Pair<String, String>>()
        val engine = AutoReplyRuleEngine(
            prefs = controller,
            backend = GbFeatureBackendService(null, null) { "u1" },
            scope = scope,
            sendReply = { conversationId, text -> sent.add(conversationId to text) },
            now = { nowValue },
        )
        kotlinx.coroutines.test.runTest {
            assertTrue(engine.processInboundMessage("c1", "say hello", isDirect = true))
            assertFalse(engine.processInboundMessage("c1", "unrelated", isDirect = true))
        }
        assertEquals(listOf("c1" to "hey there"), sent)
    }
}
