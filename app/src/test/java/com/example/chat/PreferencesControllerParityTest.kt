package com.example.chat

import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.preferences.HomePresetNormalizer
import com.example.chat.core.preferences.LockCredentialStore
import com.example.chat.core.preferences.PreferencesAuthSource
import com.example.chat.core.preferences.PreferencesBlobStore
import com.example.chat.core.preferences.PreferencesMigrator
import com.example.chat.core.preferences.RemoteSettingsRow
import com.example.chat.core.preferences.RemoteSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests written from the Dart controller's observable behavior in
 *  lib/ui/core/controllers/preferences_controller.dart +
 *  preferences_migrator.dart + home_preset_normalizer.dart. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesControllerParityTest {

    private class FakeBlobStore : PreferencesBlobStore {
        val blobs = mutableMapOf<String?, Map<String, Any?>>()
        var storedId: String? = null
        var clearedGlobal = 0
        override suspend fun load(userId: String?) = blobs[userId] ?: emptyMap()
        override suspend fun hasScoped(userId: String) = blobs.containsKey(userId)
        override suspend fun save(userId: String?, data: Map<String, Any?>) { blobs[userId] = data }
        override suspend fun clearGlobal() { clearedGlobal++; blobs.remove(null) }
        override suspend fun storedUserId(): String? = storedId
        override suspend fun setStoredUserId(userId: String?) { storedId = userId }
    }

    private class FakeRemote : RemoteSettingsStore {
        var row: RemoteSettingsRow? = null
        val pushes = mutableListOf<Pair<String, Map<String, Any?>>>()
        override suspend fun fetch(userId: String): RemoteSettingsRow? = row
        override suspend fun push(userId: String, snapshot: Map<String, Any?>) { pushes.add(userId to snapshot) }
    }

    private class FakeLock : LockCredentialStore {
        val credentials = mutableMapOf<String, Pair<String, Int?>>()
        override suspend fun hasCredential(method: String) = credentials.containsKey(method)
        override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {
            credentials[method] = secret to pinLength
        }
    }

    private class FakeAuth(var userId: String? = null) : PreferencesAuthSource {
        val listeners = mutableListOf<(String?) -> Unit>()
        override fun currentUserId(): String? = userId
        override fun addListener(listener: (String?) -> Unit) { listeners.add(listener) }
        override fun removeListener(listener: (String?) -> Unit) { listeners.remove(listener) }
        fun emit(newUserId: String?) { userId = newUserId; listeners.toList().forEach { it(newUserId) } }
    }

    private data class Fixture(
        val store: FakeBlobStore,
        val remote: FakeRemote,
        val lock: FakeLock,
        val auth: FakeAuth,
        val controller: ChatyPreferencesController,
    )

    private var nowValue = 1_700_000_000_000L

    private fun fixture(userId: String? = "u1", withRemote: Boolean = true): Fixture {
        val store = FakeBlobStore(); val remote = FakeRemote()
        val lock = FakeLock(); val auth = FakeAuth(userId)
        val scope = TestScope(UnconfinedTestDispatcher())
        val c = ChatyPreferencesController(
            store = store,
            remote = if (withRemote) remote else null,
            lockStore = lock,
            auth = auth,
            scope = scope,
            now = { nowValue },
        )
        return Fixture(store, remote, lock, auth, c)
    }

    // ── migrator ─────────────────────────────────────────────────────────

    @Test fun `v1 blob strips plaintext secrets and reports pin length`() {
        val raw = mapOf<String, Any?>(
            "schemaVersion" to 1,
            "security" to mapOf("pinCode" to "123456", "password" to "pw", "isAppLockEnabled" to true),
        )
        val m = PreferencesMigrator.migrate(raw)
        assertEquals(mapOf("PIN" to "123456", "Password" to "pw"), m.legacySecrets)
        assertEquals(6, m.legacyPinLength)
        assertTrue(m.migratedSecrets)
        @Suppress("UNCHECKED_CAST")
        val sec = m.data["security"] as Map<String, Any?>
        assertFalse(sec.containsKey("pinCode"))
        assertFalse(sec.containsKey("password"))
        assertEquals(true, sec["isAppLockEnabled"])
        assertEquals(2, m.data["schemaVersion"])
        assertFalse(PreferencesMigrator.rawHasLegacySecrets(m.data))
        assertTrue(PreferencesMigrator.rawHasLegacySecrets(raw))
    }

    @Test fun `unversioned non-empty blob reads as v1, empty as current`() {
        assertEquals(1, PreferencesMigrator.migrate(mapOf("home" to mapOf("ghostMode" to true))).fromVersion)
        assertEquals(2, PreferencesMigrator.migrate(emptyMap()).fromVersion)
    }

    // ── init + migration to secure storage ───────────────────────────────

    @Test fun `init migrates legacy secrets into lock store and rewrites blob`() {
        val f = fixture(userId = "u1", withRemote = false)
        f.store.blobs["u1"] = mapOf<String, Any?>(
            "schemaVersion" to 1,
            "security" to mapOf("pinCode" to "4321"),
        )
        // controller created before blob set; re-run via auth switch to reload
        f.auth.emit(null); f.auth.emit("u1")
        assertEquals("4321" to 4, f.lock.credentials["pin"])
        @Suppress("UNCHECKED_CAST")
        val rewritten = f.store.blobs["u1"] as Map<String, Any?>
        assertEquals(2, rewritten["schemaVersion"])
        assertFalse((rewritten["security"] as Map<String, Any?>).containsKey("pinCode"))
    }

    // ── legacy global blob adoption ──────────────────────────────────────

    @Test fun `unclaimed legacy global blob is adopted into user namespace then retired`() {
        val f = fixture(userId = "u1", withRemote = false)
        f.store.blobs[null] = mapOf<String, Any?>(
            "schemaVersion" to 2,
            "home" to mapOf("ghostMode" to true),
        )
        f.auth.emit(null); f.auth.emit("u1")
        assertTrue(f.controller.home.ghostMode)
        assertEquals(1, f.store.clearedGlobal)
        assertNull(f.store.blobs[null])
        @Suppress("UNCHECKED_CAST")
        val scoped = f.store.blobs["u1"] as Map<String, Any?>
        assertEquals(true, (scoped["home"] as Map<String, Any?>)["ghostMode"])
    }

    @Test fun `claimed legacy global blob is never adopted by a different account`() {
        val f = fixture(userId = "u2", withRemote = false)
        f.store.storedId = "u1" // blob belongs to u1
        f.store.blobs[null] = mapOf<String, Any?>(
            "schemaVersion" to 2,
            "home" to mapOf("ghostMode" to true),
        )
        f.auth.emit(null); f.auth.emit("u2")
        assertFalse("other account must not inherit", f.controller.home.ghostMode)
        assertEquals(1, f.store.clearedGlobal) // still retired
    }

    // ── auth state changes ───────────────────────────────────────────────

    @Test fun `same-user auth event only reconciles, user switch resets memory`() {
        val f = fixture(userId = "u1", withRemote = true)
        f.remote.row = null // no row -> pushes snapshot on reconcile
        f.controller.updateGbFeature("yoHideSeen", true)
        assertFalse(f.controller.privacy.readReceipts)
        val pushesBefore = f.remote.pushes.size
        f.auth.emit("u1") // same session
        assertFalse("same user keeps memory", f.controller.privacy.readReceipts)
        assertTrue("reconcile ran", f.remote.pushes.size > pushesBefore)
        f.store.blobs["u2"] = emptyMap()
        f.auth.emit("u2") // account switch
        assertTrue("memory reset on switch", f.controller.privacy.readReceipts)
        assertFalse(f.controller.gbBool("yoHideSeen", fallback = false))
    }

    // ── timestamp reconciliation ─────────────────────────────────────────

    @Test fun `strictly newer local pushes instead of applying stale remote`() {
        val f = fixture(userId = "u1", withRemote = true)
        f.controller.updateGbFeature("yoHideSeen", true) // lastLocalMutation = now
        f.remote.row = RemoteSettingsRow(
            settings = mapOf<String, Any?>("privacy" to mapOf("readReceipts" to true)),
            updatedAt = (nowValue - 10_000).toString(), // remote 10s older
        )
        val pushesBefore = f.remote.pushes.size
        kotlinx.coroutines.test.runTest { f.controller.syncFromRemote() }
        assertTrue("local pushed", f.remote.pushes.size > pushesBefore)
        assertFalse("stale remote not applied", f.controller.privacy.readReceipts)
    }

    @Test fun `newer remote is applied and saved locally`() {
        val f = fixture(userId = "u1", withRemote = true)
        f.remote.row = RemoteSettingsRow(
            settings = mapOf<String, Any?>(
                "schemaVersion" to 2,
                "local_updated_at" to (nowValue + 60_000).toString(),
                "privacy" to mapOf("readReceipts" to false),
            ),
            updatedAt = (nowValue + 60_000).toString(),
        )
        kotlinx.coroutines.test.runTest { f.controller.syncFromRemote() }
        assertFalse("remote applied", f.controller.privacy.readReceipts)
        @Suppress("UNCHECKED_CAST")
        val saved = f.store.blobs["u1"] as Map<String, Any?>
        assertEquals(false, (saved["privacy"] as Map<String, Any?>)["readReceipts"])
    }

    @Test fun `remote blob with legacy secrets is purged back to cloud`() {
        val f = fixture(userId = "u1", withRemote = true)
        f.remote.row = RemoteSettingsRow(
            settings = mapOf<String, Any?>(
                "schemaVersion" to 1,
                "local_updated_at" to (nowValue + 60_000).toString(),
                "security" to mapOf("patternCode" to "12358"),
            ),
            updatedAt = (nowValue + 60_000).toString(),
        )
        val pushesBefore = f.remote.pushes.size
        kotlinx.coroutines.test.runTest { f.controller.syncFromRemote() }
        assertEquals("12358" to null, f.lock.credentials["pattern"])
        assertTrue("purged snapshot re-pushed", f.remote.pushes.size > pushesBefore)
    }

    // ── gb aliases ───────────────────────────────────────────────────────

    @Test fun `ghost mode alias cascades privacy flags only when enabling`() {
        val f = fixture(userId = "u1", withRemote = false)
        f.controller.updateGbFeature("yo_want_ghostmode", true)
        assertTrue(f.controller.home.ghostMode)
        assertTrue(f.controller.privacy.freezeLastSeen)
        assertFalse(f.controller.privacy.readReceipts)
        assertFalse(f.controller.privacy.typingIndicators)
        assertFalse(f.controller.privacy.recordingIndicators)
        assertTrue(f.controller.privacy.hideViewStatus)
    }

    @Test fun `legacy quickcontact alias maps Off and Left exactly like Dart`() {
        val f = fixture(userId = "u1", withRemote = false)
        f.controller.updateGbFeature("abu_saleh_quickcontact", "Left")
        assertTrue(f.controller.conversation.enableQuickContactSidebar)
        assertEquals("Left", f.controller.conversation.sidebarPosition)
        f.controller.updateGbFeature("abu_saleh_quickcontact", "Off")
        assertFalse(f.controller.conversation.enableQuickContactSidebar)
        assertEquals("Right", f.controller.conversation.sidebarPosition)
    }

    @Test fun `home and stories style aliases pass through the normalizer`() {
        val f = fixture(userId = "u1", withRemote = false)
        f.controller.updateGbFeature("ui_home_styleV3", "WhatsApp-Style Top Bar")
        assertEquals("Classic", f.controller.home.homeStyle)
        f.controller.updateGbFeature("home_stories_style", "squircle")
        assertEquals("Squircle", f.controller.home.storiesStyle)
        // and the normalizer itself
        assertEquals("Chaty Default", HomePresetNormalizer.homeStyle("telegram style"))
        assertEquals("Chaty Default", HomePresetNormalizer.homeStyle("something custom"))
        assertEquals("Circular", HomePresetNormalizer.storiesStyle("STOCK"))
    }

    // ── visibility heal ──────────────────────────────────────────────────

    @Test fun `visibility keys heal once then marker prevents re-heal`() {
        val f = fixture(userId = "u1", withRemote = false)
        f.store.blobs["u1"] = mapOf<String, Any?>(
            "schemaVersion" to 2,
            "gbFeatures" to mapOf("PicProf" to false, "NameProf" to false),
        )
        f.auth.emit(null); f.auth.emit("u1")
        assertFalse("healed keys removed", f.controller.gbFeatures.containsKey("PicProf"))
        assertEquals(true, f.controller.gbFeatures["chaty_visibility_heal_v1"])
        // deliberate later choice survives: set PicProf, reload, no re-heal
        f.controller.updateGbFeature("PicProf", false)
        f.auth.emit(null); f.auth.emit("u1")
        assertEquals(false, f.controller.gbFeatures["PicProf"])
    }

    // ── favorites, history, locks ────────────────────────────────────────

    @Test fun `favorites toggle and history caps at 50`() {
        val f = fixture(userId = "u1", withRemote = false)
        f.controller.toggleFavorite("privacy")
        assertTrue(f.controller.isFavorite("privacy"))
        f.controller.toggleFavorite("privacy")
        assertFalse(f.controller.isFavorite("privacy"))
        repeat(60) { f.controller.updateGbFeature("key_$it", true) }
        assertEquals(50, f.controller.history().size)
        assertEquals("gb:key_59", f.controller.history().first().key)
    }

    @Test fun `hiding a chat also locks it and complete unlock removes both`() {
        val f = fixture(userId = "u1", withRemote = false)
        f.controller.toggleHideConversation("c1", hide = true)
        assertTrue(f.controller.isConversationHidden("c1"))
        assertTrue(f.controller.isConversationLocked("c1"))
        assertTrue(f.controller.isConversationProtected("c1"))
        f.controller.unlockConversationCompletely("c1")
        assertFalse(f.controller.isConversationProtected("c1"))
    }

    // ── snapshot shape ───────────────────────────────────────────────────

    @Test fun `snapshot carries schema version and all group keys`() {
        val f = fixture(userId = "u1", withRemote = false)
        val s = f.controller.snapshot()
        assertEquals(2, s["schemaVersion"])
        assertEquals(nowValue, s["local_updated_at"])
        for (k in listOf("privacy","security","home","conversation","notification","automation","effects","universal","status","toasts","gbFeatures","favorites")) {
            assertTrue("missing $k", s.containsKey(k))
        }
    }
}
