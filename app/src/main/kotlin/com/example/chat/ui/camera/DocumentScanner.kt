package com.example.chat.ui.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}

/** Google Play services' on-device paper detector/perspective/corner editor.
 * BASE_WITH_FILTER excludes the FULL mode stain/finger cleaning tools.
 * SDK confirmation returns to Chaty's caption review, never directly sends. */
@Composable
fun rememberDocumentScanner(
    onDocument: (String) -> Unit,
    onBusy: (Boolean) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    val latestDocument = rememberUpdatedState(onDocument)
    val latestBusy = rememberUpdatedState(onBusy)
    val latestError = rememberUpdatedState(onError)
    DisposableEffect(Unit) {
        alive.set(true)
        onDispose { alive.set(false) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (!alive.get()) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            if (alive.get()) latestBusy.value(false) // Cancel leaves the existing draft and original files unchanged.
        } else {
            val uri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri
            if (uri == null) {
                if (alive.get()) latestBusy.value(false)
                latestError.value("The scanner returned no page. Try again or use Photo.")
            } else scope.launch {
                var file: File? = null
                try {
                    val path = withContext(Dispatchers.IO) {
                        val folder = File(context.cacheDir, "chaty_scans").apply { mkdirs() }
                        val dest = File.createTempFile("scan_", ".jpg", folder).also { file = it }
                        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open scanned page")
                        input.use { source -> dest.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                val read = source.read(buffer)
                                if (read < 0) break
                                total += read
                                check(total <= 32L * 1024 * 1024) { "Scanned page is too large" }
                                output.write(buffer, 0, read)
                            }
                        } }
                        check(dest.length() > 0) { "Scanned page is empty" }
                        dest.absolutePath
                    }
                    if (alive.get()) latestDocument.value(path) else file?.delete()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    file?.delete()
                    throw cancelled
                } catch (_: Exception) {
                    file?.delete()
                    latestError.value("Could not import the scanned page. Your original is unchanged; try again.")
                } finally { if (alive.get()) latestBusy.value(false) }
            }
        }
    }
    return {
        val activity = context.hostActivity()
        if (activity == null) latestError.value("Document scanner needs an open camera screen.")
        else {
            latestBusy.value(true)
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER)
                .build()
            val activityReference = java.lang.ref.WeakReference(activity)
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { sender ->
                    val active = activityReference.get()
                    if (!alive.get() || active == null || active.isFinishing || active.isDestroyed) return@addOnSuccessListener
                    try { launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                    catch (_: Exception) {
                        if (alive.get()) latestBusy.value(false)
                        latestError.value("Scanner could not open. Use Photo or retry after updating Google Play services.")
                    }
                }
                .addOnFailureListener {
                    if (!alive.get()) return@addOnFailureListener
                    if (alive.get()) latestBusy.value(false)
                    latestError.value("Document scanner is unavailable. It needs supported Google Play services and an initial download. You can still use Photo; no automatic paper crop was applied.")
                }
        }
    }
}
