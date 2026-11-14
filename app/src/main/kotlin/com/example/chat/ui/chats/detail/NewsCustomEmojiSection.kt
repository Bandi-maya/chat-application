package com.example.chat.ui.chats.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig

/** Stable custom-emoji identity. Never insert these IDs as Unicode or send them as stickers. */
internal data class NewsCustomEmoji(val id: String, val packId: String = "chaty-news-clean-v1") {
    val assetPath get() = "emoji/news/animated/news_${id}.webp"
    val posterPath get() = "emoji/news/posters/news_${id}.png"
}
internal object NewsCustomEmojiCatalog {
    val items = (1..100).map { NewsCustomEmoji(it.toString().padStart(3, '0')) }
}

internal object CustomEmojiPanelTokens {
    val target = 48.dp
    val artwork = 30f
    val sidePadding = 8.dp
}

/** Frontend preview only. Selection stays local until the custom-emoji payload is implemented. */
@Composable
internal fun NewsCustomEmojiSection(theme: ThemeConfig, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text("News emoji pack is saved for your server. Downloading is not connected yet.", color = Color(theme.primaryTextColor))
        Text("Custom emoji sending is not connected.", color = Color(theme.secondaryTextColor), fontSize = 11.sp)
    }
}

@Composable
private fun NewsCustomEmojiArtwork(item: NewsCustomEmoji, size: Float) {
    Text("N", color = Color.Gray, fontSize = (size * .6f).sp)
}

@Composable
internal fun CustomEmojiPackStrip(theme: ThemeConfig, newsSelected: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        listOf(false to "Emoji", true to "News").forEach { (news, label) ->
            Row(Modifier.heightIn(min = CustomEmojiPanelTokens.target)
                .background(if (newsSelected == news) Color(theme.accentColor).copy(alpha = .12f) else Color.Transparent, CircleShape)
                .clickable { onSelect(news) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (news) NewsCustomEmojiArtwork(NewsCustomEmojiCatalog.items.first(), 24f)
                Text(label, color = if (newsSelected == news) Color(theme.accentColor) else Color(theme.secondaryTextColor),
                    fontSize = 13.sp, modifier = Modifier.padding(start = if (news) 6.dp else 0.dp))
            }
        }
    }
}
