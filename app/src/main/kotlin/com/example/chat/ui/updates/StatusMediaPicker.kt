package com.example.chat.ui.updates

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FilePicker.pickFiles(type: image | video | audio | any) from StatusService.pickAndPublish.
 * Returns the picked file's bytes + name + MIME for StatusService.publishMediaBytes (which owns the
 * Dart validation: audio flag, empty file, size limit, upload, insert). Null when the picker is cancelled.
 */
@Composable
fun rememberStatusMediaPicker(): suspend (String) -> PickedStatusMedia? {
    val context = LocalContext.current
    var waiter by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        waiter?.complete(uri)
        waiter = null
    }
    return remember(launcher, context) {
        { mediaType: String ->
            val d = CompletableDeferred<Uri?>()
            waiter = d
            val mimes = when (mediaType) {
                "image" -> arrayOf("image/*")
                "video" -> arrayOf("video/*")
                "audio" -> arrayOf("audio/*")
                else -> arrayOf("*/*")
            }
            launcher.launch(mimes)
            val uri = d.await()
            if (uri == null) null else readPicked(context, uri)
        }
    }
}

private const val MAX_PICK_BYTES = 256L * 1024L * 1024L

private suspend fun readPicked(context: Context, uri: Uri): PickedStatusMedia = withContext(Dispatchers.IO) {
    var name = "file"
    var size = -1L
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val s = c.getColumnIndex(OpenableColumns.SIZE)
            if (n >= 0) name = c.getString(n) ?: name
            if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
        }
    }
    if (size > MAX_PICK_BYTES) throw IllegalStateException("Selected media exceeds the storage limit.")
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        // Bounded read: a provider that reports no size still cannot pull more than the cap into memory.
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_PICK_BYTES) throw IllegalStateException("Selected media exceeds the storage limit.")
            out.write(buf, 0, n)
        }
        out.toByteArray()
    } ?: throw IllegalStateException("The selected file is not accessible on this device.")
    PickedStatusMedia(bytes = bytes, displayName = name, mimeType = context.contentResolver.getType(uri))
}
