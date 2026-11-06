package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.chats.detail.AnimatedEmojiView
import com.example.chat.ui.theme.ChatyTheme
import kotlinx.coroutines.delay

/** Shared phase marker so the instrumentation test (same process) can sample memory per phase. */
object EmojiPackGridProbe {
    @Volatile var phase: String = "idle"   // idle -> visible -> scrolledOff -> back -> done
}

/**
 * debug-only perf/coverage fixture: a grid of the 50-emoji set drawn with an EXPLICIT pack
 * (intent extra "pack": Chaty|iOS|Microsoft|OpenMoji). It self-drives: visible 6s, scroll far
 * away (items leave composition and must release their drawables) 4s, scroll back 3s, done.
 * Synthetic data only, nothing sent, no network beyond what the chosen pack already does.
 */
class EmojiPackGridFixtureActivity : ComponentActivity() {
    private val set = listOf(
        0x1f600, 0x1f601, 0x1f602, 0x1f923, 0x1f603, 0x1f604, 0x1f605, 0x1f606, 0x1f609, 0x1f60a,
        0x1f60b, 0x1f60e, 0x1f60d, 0x1f618, 0x1f617, 0x1f619, 0x1f61a, 0x263a, 0x1f642, 0x1f917,
        0x1f929, 0x1f914, 0x1f928, 0x1f610, 0x1f611, 0x1f636, 0x1f644, 0x1f60f, 0x1f623, 0x1f625,
        0x1f62e, 0x1f910, 0x1f62f, 0x1f62a, 0x1f62b, 0x1f634, 0x1f60c, 0x1f61b, 0x1f61c, 0x1f61d,
        0x1f924, 0x1f612, 0x1f613, 0x1f614, 0x1f615, 0x1f643, 0x1f911, 0x1f632, 0x2639, 0x1f641,
    ).map { String(Character.toChars(it)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pack = intent.getStringExtra("pack") ?: "Chaty"
        val theme = ThemePresets.getSystemDefaultTheme(platformIsLight = true)
        // 50 visible-set cells, then 400 filler rows so scrolling away really leaves composition.
        val cells = List(set.size + 400) { set[it % set.size] }
        setContent {
            ChatyTheme(config = theme) {
                val state = rememberLazyGridState()
                LaunchedEffect(Unit) {
                    EmojiPackGridProbe.phase = "visible"; delay(6_000)
                    state.scrollToItem(cells.lastIndex); EmojiPackGridProbe.phase = "scrolledOff"; delay(4_000)
                    state.scrollToItem(0); EmojiPackGridProbe.phase = "back"; delay(3_000)
                    EmojiPackGridProbe.phase = "done"
                }
                LazyVerticalGrid(columns = GridCells.Fixed(8), state = state) {
                    items(cells) { e -> AnimatedEmojiView(unicode = e, size = 36f, emojiStyle = pack) }
                }
            }
        }
    }
}
