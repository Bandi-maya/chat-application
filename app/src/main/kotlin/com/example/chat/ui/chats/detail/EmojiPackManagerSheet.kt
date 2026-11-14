package com.example.chat.ui.chats.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.data.packs.ContentPackStore
import com.example.chat.core.theme.ThemeConfig
import kotlinx.coroutines.launch

/**
 * Pack manager (WhatsApp/Telegram parity): every server-listed emoji pack with
 * its size, an explicit Download / Update action with live progress, and Remove
 * to reclaim storage. Hosted by the emoji-style settings screen; when the host
 * has not configured a pack server the sheet says so and does nothing else.
 * Mirrored behaviours: per-pack download + thumbnails-first catalog (Telegram,
 * core.telegram.org/stickers), explicit download/remove with progress (WhatsApp
 * sample sticker app, github.com/WhatsApp/stickers).
 */
@Composable
fun EmojiPackManagerSheet(
    theme: ThemeConfig,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = Color(theme.primaryTextColor)
    val secondary = Color(theme.secondaryTextColor)
    val accent = Color(theme.accentColor)
    val store = rememberPackStore()
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Emoji packs", color = foreground, fontWeight = FontWeight.W800, fontSize = 20.sp,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }

        if (store == null) {
            Text(
                "Emoji packs are not configured yet. The pack server address is missing.",
                color = foreground,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return@Column
        }

        val revision by store.revision.collectAsState()
        val downloads by store.downloads.collectAsState()
        var checked by remember(store) { mutableStateOf(false) }
        var failure by remember(store) { mutableStateOf<String?>(null) }
        LaunchedEffect(store) {
            try { store.refreshManifest() }
            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { failure = "Could not load packs. Try again later." }
            finally { checked = true }
        }
        val packs = remember(revision, checked) { store.manifest }
        failure?.let { Text(it, color = secondary) }

        // All disk reads are recomputed from the renderer-visible revision +
        // manifest, off the main thread: installed version, pending update,
        // install state per pack, and the cached-bytes footer.
        val packStates by produceState<Map<String, Triple<String?, String?, ContentPackStore.PackInstallState>>>(
            emptyMap(), revision, packs,
        ) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                packs.associate { pack ->
                    pack.id to Triple(store.installedVersion(pack.id), store.updateAvailable(pack.id), store.stateFor(pack.id))
                }
            }
        }
        val cachedBytes by produceState(0L, revision) { value = store.cachedBytes() }

        if (packs.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                if (!checked) CircularProgressIndicator(Modifier.size(20.dp))
                Text(if (checked) "No packs are available from the configured server." else "Checking for packs...", color = secondary, modifier = Modifier.padding(start = 10.dp))
            }
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(packs, key = { it.id }) { pack ->
                val (installed, update, _) = packStates[pack.id] ?: Triple(null, null, ContentPackStore.PackInstallState.NOT_INSTALLED)
                val progress = downloads[pack.id]
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(theme.cardColor))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pack.name, color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                buildString {
                                    append(formatPackBytes(pack.totalBytes))
                                    append(" · v").append(pack.version)
                                    if (installed != null) append(" · installed v").append(installed)
                                },
                                color = secondary, fontSize = 12.sp,
                            )
                        }
                        when {
                            progress != null -> Text("Downloading…", color = secondary, fontSize = 13.sp)
                            update != null -> TextButton(onClick = {
                                scope.launch {
                                    try { if (!store.downloadPack(pack.id)) failure = "Pack download failed. Your current emoji fallback is unchanged." }
                                    catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                                    catch (_: Exception) { failure = "Pack download failed. Try again later." }
                                }
                            }) { Text("Update") }
                            installed != null -> TextButton(onClick = {
                                scope.launch { store.removePack(pack.id) }
                            }) { Text("Remove") }
                            else -> TextButton(onClick = {
                                scope.launch {
                                    try { if (!store.downloadPack(pack.id)) failure = "Pack download failed. Your current emoji fallback is unchanged." }
                                    catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                                    catch (_: Exception) { failure = "Pack download failed. Try again later." }
                                }
                            }) { Text("Download") }
                        }
                    }
                    if (progress != null) {
                        // Determinate bar, WhatsApp-pack style.
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp)
                                .clip(RoundedCornerShape(3.dp)).background(Color(theme.surfaceColor)),
                        ) {
                            Box(
                                Modifier.fillMaxWidth(progress).fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp)).background(accent),
                            )
                        }
                    }
                }
            }
        }

        Text(
            "Packs download once from your server and stay on this device. Cached: ${formatPackBytes(cachedBytes)}.",
            color = secondary, fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

private fun formatPackBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
