package com.example.chat

import java.io.File
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Tapping Search closed the app: FocusRequester.requestFocus() ran inside the click
 *  handler before the search field was composed ("FocusRequester is not initialized").
 *  Focus must be requested from an effect, guarded, after the field is attached. */
class SearchFocusCrashRegressionTest {
    private fun src(rel: String): String {
        val roots = listOf("app/src/main/kotlin/com/example/chat/ui/", "src/main/kotlin/com/example/chat/ui/")
        return roots.map { File(it + rel) }.first { it.exists() }.readText()
    }

    @Test
    fun homeSearchToggleDoesNotRequestFocusInClickHandler() {
        val s = src("chats/ChatsHomeScreen.kt")
        assertFalse(Regex("""onToggleSearch\s*=\s*\{[^}]*requestFocus""").containsMatchIn(s))
        assertTrue(s.contains("LaunchedEffect(isSearchOpen)"))
    }

    @Test
    fun allSearchFocusRequestsAreGuarded() {
        listOf("chats/ChatsHomeScreen.kt", "search/GlobalSearchScreen.kt", "chats/ChatDetailScreen.kt").forEach {
            src(it).lines().filter { l -> l.contains(".requestFocus()") }
                .forEach { l -> assertTrue("$it: $l", l.contains("runCatching")) }
        }
    }
}
