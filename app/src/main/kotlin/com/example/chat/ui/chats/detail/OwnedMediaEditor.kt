package com.example.chat.ui.chats.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.data.local.*
import com.example.chat.data.media.AttachmentSourceResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** HOLD next-snapshot integration wrapper. Never receives an arbitrary input/output file from UI.
 * Owner must key this screen by the LIVE account session generation and close it on session change.
 * No input token can be returned as edited output. Registry commit mints the new token. */
@Composable
internal fun OwnedMediaEditor(
    resolver: AttachmentSourceResolver,
    ref: AttachmentRef,
    expectedSession: String,
    kind: EditKind,
    maxLifetimeMs: Long,
    purpose: ExportPurpose,
    authorizedUrl: suspend () -> String,
    onCancel: () -> Unit,
    onCommitted: (ExportToken) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var edit by remember(ref, expectedSession) { mutableStateOf<EditSession?>(null) }
    var error by remember(ref, expectedSession) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var active by remember(ref, expectedSession) { mutableStateOf(true) }
    var commitJob by remember(ref, expectedSession) { mutableStateOf<Job?>(null) }
    DisposableEffect(ref, expectedSession) {
        onDispose { active = false; commitJob?.cancel(); edit?.let(resolver::abortEdit); edit = null }
    }
    LaunchedEffect(ref, expectedSession, kind) {
        var pending: EditSession? = null
        try {
            when (val result = resolver.beginEdit(ref, expectedSession, kind, maxLifetimeMs, authorizedUrl)) {
                is EditResult.Started -> {
                    pending = result.session
                    coroutineContext.ensureActive()
                    if (active) { edit = pending; pending = null }
                }
                is EditResult.Denied -> error = result.reason
                EditResult.Miss -> error = "Attachment is not available. Open it online first."
                EditResult.Failed -> error = "Could not prepare an editable copy."
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Could not prepare an editable copy." }
        finally { pending?.let(resolver::abortEdit) }
    }
    fun cancel() { active = false; commitJob?.cancel(); edit?.let(resolver::abortEdit); edit = null; onCancel() }
    fun commit() {
        val session = edit ?: return
        if (busy) return
        busy = true
        commitJob = scope.launch {
            var output: ExportToken? = null
            var delivered = false
            try {
                // Native content validation is authoritative inside store.commitEdit, off main thread.
                coroutineContext.ensureActive()
                when (val committed = resolver.commitEdit(session, ref, expectedSession, purpose, maxLifetimeMs)) {
                    is ExportResult.Granted -> {
                        output = committed.token
                        edit = null
                        coroutineContext.ensureActive()
                        check(active && committed.token.isValid()) { "Session or export expired." }
                        onCommitted(committed.token); delivered = true
                    }
                    is ExportResult.Denied -> error = committed.reason
                    else -> error = "Could not commit edited media."
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not commit edited media." }
            finally {
                if (!delivered) { output?.release(); resolver.abortEdit(session); edit = null }
                busy = false
            }
        }
    }
    androidx.activity.compose.BackHandler(onBack = ::cancel)
    val current = edit
    when {
        busy -> Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            CircularProgressIndicator(); Text("Verifying edited copy")
            TextButton(::cancel) { Text("Cancel") }
        } }
        error != null -> Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Text(error!!); TextButton(::cancel) { Text("Close") }
        } }
        current == null -> Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            CircularProgressIndicator(); TextButton(::cancel) { Text("Cancel") }
        } }
        kind == EditKind.IMAGE_JPEG -> ImageEditorScreen(current.inputUri, ::cancel,
            onApply = { file -> if (file == current.outputFile) commit() else { resolver.abortEdit(current); error = "Unexpected editor output." } },
            outputFile = current.outputFile,
        )
        else -> MediaTrimScreen(current.inputUri, kind == EditKind.VIDEO_MP4, ::cancel,
            onApply = { result -> if (result.file == current.outputFile) commit() else { resolver.abortEdit(current); error = "Unexpected editor output." } },
            outputFile = current.outputFile,
        )
    }
}
