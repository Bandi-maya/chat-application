package com.example.chat.ui.chats.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.chat.core.stickers.StickerAsset
import com.example.chat.core.stickers.StickerPack
import com.example.chat.core.stickers.StickerCatalog
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.kit.ChatyConfirmationDialog

/** Bundled source packs only. Search never silently opens a remote catalog. */
@Composable
internal fun ClusteredStickerPicker(
    packs: List<StickerPack>, theme: ThemeConfig,
    onConfirmSend: ((StickerAsset) -> Unit)?,
    unavailableReason: String? = null,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember(packs) { mutableStateOf<StickerAsset?>(null) }
    val results = remember(packs, query) { runCatching { StickerCatalog.search(packs, query) }.getOrDefault(emptyList()) }
    val foreground = Color(theme.primaryTextColor)
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(value = query, onValueChange = { query = it.take(200) },
            label = { Text("Search related stickers") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(8.dp))
        if (packs.isEmpty()) Text("No sticker packs are installed.", color = foreground, modifier = Modifier.padding(16.dp))
        else if (results.isEmpty()) Text("No matching stickers in your installed packs.", color = foreground, modifier = Modifier.padding(16.dp))
        unavailableReason?.let { Text(it, color = foreground, modifier = Modifier.padding(horizontal = 12.dp)) }
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().weight(1f)) {
            results.forEach { pack ->
                item(key = "heading:${pack.id}", span = { GridItemSpan(maxLineSpan) }) {
                    Column { Text(pack.title, color = foreground, modifier = Modifier.padding(12.dp))
                        Text(pack.attribution, color = Color(theme.secondaryTextColor), modifier = Modifier.padding(horizontal = 12.dp)) }
                }
                items(pack.stickers, key = { "sticker:${it.id}" }) { sticker ->
                    Column(Modifier.clickable { selected = sticker }.padding(8.dp)) {
                        StickerArtwork(sticker, 72f)
                        Text(sticker.label, color = foreground, maxLines = 2)
                    }
                }
            }
        }
    }
    selected?.let { sticker ->
        ChatyConfirmationDialog(onDismissRequest = { selected = null }, title = { Text(sticker.label) },
            text = { Column { StickerArtwork(sticker, 180f); Text("Preview from ${packs.firstOrNull { it.id == sticker.packId }?.title.orEmpty()}") } },
            confirmButton = { TextButton(enabled = onConfirmSend != null, onClick = {
                if (packs.any { it.id == sticker.packId && sticker in it.stickers }) onConfirmSend?.invoke(sticker)
                selected = null
            }) { Text("Send sticker") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } })
    }
}

@Composable
private fun StickerArtwork(sticker: StickerAsset, size: Float) {
    var failed by remember(sticker.assetPath) { mutableStateOf(false) }
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
    val poster = if (sticker.packId == "chaty-news-clean-v1") sticker.assetPath.replace("/animated/", "/posters/").replace(".webp", ".png") else sticker.assetPath
    if (failed) Text("Sticker preview unavailable")
    else if (sticker.animated && android.os.Build.VERSION.SDK_INT >= 28) IosAnimatedEmoji(sticker.assetPath, size, animate = foreground && !reduced, onUnavailable = { failed = true })
    else StaticAssetEmoji(poster, size, onUnavailable = { failed = true })
}
