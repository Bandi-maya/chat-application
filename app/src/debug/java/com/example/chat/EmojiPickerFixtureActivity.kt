package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.chats.detail.ChatyEmojiPickerSheet
import com.example.chat.ui.chats.detail.EmojiRecentCache
import com.example.chat.ui.theme.ChatyTheme
import java.util.concurrent.CopyOnWriteArrayList

/** debug-only host for the production ChatyEmojiPickerSheet (recents tab seeded with two emojis in an
 *  in-memory cache). Picks are recorded locally; nothing is sent. */
class EmojiPickerFixtureActivity : ComponentActivity() {
    companion object { val picked = CopyOnWriteArrayList<String>() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = ThemePresets.getSystemDefaultTheme(platformIsLight = true)
        setContent {
            ChatyTheme(config = theme) {
                val cache = remember {
                    EmojiRecentCache().apply {
                        initialize()
                        recordUsage("\u2764\uFE0F", "Red heart")
                        recordUsage("\uD83D\uDE02", "Face with tears of joy")
                    }
                }
                ChatyEmojiPickerSheet(
                    theme = theme, reactionMode = false, composerMode = false,
                    recentCache = cache, onEmojiPicked = { picked.add(it) },
                )
            }
        }
    }
}
