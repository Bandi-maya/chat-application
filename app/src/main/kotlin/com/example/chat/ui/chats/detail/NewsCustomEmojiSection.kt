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
    var selected by remember { mutableStateOf<NewsCustomEmoji?>(null) }
    val foreground = Color(theme.primaryTextColor)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("News Emoji", color = foreground, modifier = Modifier.weight(1f), fontSize = 14.sp)
            Text("100", color = Color(theme.secondaryTextColor), fontSize = 12.sp)
        }
        Text("Preview only · custom emoji sending is not connected",
            color = Color(theme.secondaryTextColor), fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(CustomEmojiPanelTokens.target),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = CustomEmojiPanelTokens.sidePadding)) {
            items(NewsCustomEmojiCatalog.items, key = { it.id }) { item ->
                Box(Modifier.height(CustomEmojiPanelTokens.target).fillMaxWidth()
                    .semantics { contentDescription = "News emoji ${item.id}, preview only" }
                    .clickable { selected = item }, contentAlignment = Alignment.Center) {
                    NewsCustomEmojiArtwork(item, CustomEmojiPanelTokens.artwork)
                }
            }
        }
        selected?.let { item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                NewsCustomEmojiArtwork(item, 24f)
                Text("News ${item.id} selected · not sent", color = foreground, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun NewsCustomEmojiArtwork(item: NewsCustomEmoji, size: Float) {
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var foreground by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var failed by remember(item.id) { mutableStateOf(false) }
    if (failed) Text("·", color = Color.Gray)
    else if (android.os.Build.VERSION.SDK_INT >= 28 && !reduced) {
        IosAnimatedEmoji(item.assetPath, size, animate = foreground, onUnavailable = { failed = true })
    } else StaticAssetEmoji(item.posterPath, size, onUnavailable = { failed = true })
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
