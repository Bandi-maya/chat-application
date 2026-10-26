package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.chats.detail.AnimatedEmojiText
import com.example.chat.ui.theme.ChatyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** debug-only host for the single-emoji loop proof. Renders one emoji-only
 *  message through the production AnimatedEmojiText -> AnimatedEmojiView ->
 *  ChatyAnimatedEmojiAsset path (jumboSingle mode) in a lazy list with filler
 *  rows so the test can scroll it away and back. No backend, no network
 *  (the test swaps in a bundled composition), nothing is sent. */
class EmojiLoopFixtureActivity : ComponentActivity() {

    @Volatile var scope: CoroutineScope? = null
    @Volatile var scrollTo: (suspend (Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatyTheme(config = ThemePresets.getSystemDefaultTheme(platformIsLight = true)) {
                val state = rememberLazyListState()
                val s = rememberCoroutineScope()
                scope = s
                scrollTo = { i -> state.scrollToItem(i) }
                LazyColumn(state = state) {
                    item(key = "emoji") {
                        AnimatedEmojiText(text = "\uD83D\uDE02", fontSize = 16f, messageId = "fixture-msg")
                    }
                    items((1..60).toList()) { Text("Filler row $it") }
                }
            }
        }
    }

    fun scrollToIndex(i: Int) {
        val sc = scope ?: return
        val f = scrollTo ?: return
        sc.launch { f(i) }
    }
}
