package com.example.chat.ui.chats.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.data.local.*
import com.example.chat.data.media.ResolvedSource
import com.example.chat.domain.models.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** This entire composition is keyed by session+conversation+message in the host. */
@Composable
internal fun BoundAttachmentViewer(theme: ThemeConfig, message: ChatMessage, binding: AttachmentConsumerBinding,
    restricted: Boolean, bounds: androidx.compose.ui.geometry.Rect?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<ResolvedSource?>(null) }
    var edited by remember { mutableStateOf<ExportToken?>(null) }
    var edit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val stopReader = remember { arrayOfNulls<() -> Unit>(1) }
    var active by remember { mutableStateOf(true) }
    val attachment = message.attachment ?: return
    // Parent invokes the registered player stop barrier BEFORE releasing the source, regardless of effect order.
    DisposableEffect(Unit) { onDispose { active = false; stopReader[0]?.invoke(); releaseConsumerSource(source); edited?.release() } }
    LaunchedEffect(Unit) {
        var pending: ResolvedSource? = null
        try {
            pending = binding.resolve(message, true)
            coroutineContext.ensureActive()
            if (active && binding.deliver(pending!!) { source = pending }) pending = null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Attachment unavailable" }
        finally { releaseConsumerSource(pending) }
    }
    fun external(purpose: ExportPurpose) {
        scope.launch {
            var token: ExportToken? = null
            var delivered = false
            try {
                val existing = edited
                val result = if (existing != null && existing.isValid()) ExportResult.Granted(existing)
                    else binding.resolver.exportCopy(binding.ref(message), binding.session, purpose,
                        attachment.name.substringAfterLast('.', "bin"), 10 * 60_000L) { binding.authorizedUrl(message) }
                if (result !is ExportResult.Granted) { error = "Export unavailable or not permitted"; return@launch }
                token = result.token
                coroutineContext.ensureActive()
                check(active && result.token.isValid())
                val intent = if (purpose == ExportPurpose.SHARE) Intent(Intent.ACTION_SEND).apply {
                    type = context.contentResolver.getType(Uri.parse(result.token.uri)) ?: "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(result.token.uri))
                } else Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(result.token.uri), context.contentResolver.getType(Uri.parse(result.token.uri)))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                check(binding.deliverIfCurrent {
                    context.startActivity(if (purpose == ExportPurpose.SHARE) Intent.createChooser(intent, "Share attachment") else intent)
                }) { "Session changed" }
                delivered = true
                if (edited === result.token) edited = null // registry timer/logout owns successful external lifetime
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "No application could open this attachment" }
            finally { if (!delivered) token?.release() }
        }
    }
    if (edit) {
        OwnedMediaEditor(binding.resolver, binding.ref(message), binding.session,
            when (attachment.type) { "video" -> EditKind.VIDEO_MP4; "audio" -> EditKind.AUDIO_MP4; else -> EditKind.IMAGE_JPEG },
            10 * 60_000L, ExportPurpose.SHARE, { binding.authorizedUrl(message) },
            onCancel = { edit = false }, onCommitted = { token ->
                var accepted = false
                try { accepted = binding.deliverIfCurrent { edited = token; edit = false } }
                finally { if (!accepted) token.release() }
            },
        )
        return
    }
    val current = source
    when {
        error != null || current is ResolvedSource.Unavailable -> Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding()) {
            Text(error ?: "Not downloaded for offline use, unavailable or access denied.")
            TextButton(onBack) { Text("Back") }
        } }
        current == null -> Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding()) { CircularProgressIndicator(); TextButton(onBack) { Text("Back") } } }
        else -> MediaViewerScreen(theme, attachment.name, attachment.type, attachment.size,
            storagePath = edited?.uri ?: current.consumerUri() ?: "verified-bytes",
            createSignedUrl = { path, _ -> path }, onBack = onBack, sourceBounds = bounds,
            restrictedViewOnce = restricted,
            providedModel = edited?.uri ?: current.consumerModel(), ownedActions = true,
            registerStopReader = { stopReader[0] = it },
            deliverPlayback = binding.deliverIfCurrent,
            onOwnedEdit = { stopReader[0]?.invoke(); edit = true }, onOwnedShare = { external(ExportPurpose.SHARE) }, onOwnedOpen = { external(ExportPurpose.OPEN) },
        )
    }
}
