package com.example.chat.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.chat.ServiceLocator
import com.example.chat.data.local.BoundedFileReader
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.AttachmentBackendPort
import com.example.chat.ui.chats.detail.ChatAttachmentActionController
import com.example.chat.ui.chats.detail.PickedFile
import com.example.chat.ui.media.CustomFilePickerScreen
import com.example.chat.ui.media.DocumentReaderScreen
import com.example.chat.ui.media.FileSanitizationService
import com.example.chat.ui.media.MediaCategory
import com.example.chat.ui.media.MediaFileDescriptor
import com.example.chat.ui.media.MediaLibraryService
import com.example.chat.ui.media.PdfProcessingService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Binding of ChatAttachmentActionController (Dart chat_attachment_actions.dart) for ChatDetailHost.
 *  Bound: image/video/document/audio pick + upload (ChatMediaService -> REST /chat/upload when the
 *  api session is authenticated) + send through ApiBackendService.sendMessage with the Dart JSON
 *  envelope, view-once sheet, share-limit and size guards.
 *  Not bound here (explicit gaps): poll dialog/viewer.
 *  document shareMedia routes to the in-app CustomFilePickerScreen (Dart
 *  chat_attachment_actions.dart:92 Navigator.push), not the system picker. */
class ChatAttachmentsBindings(
    val onMedia: (String) -> Unit,
    val onContact: () -> Unit,
    val onLocation: () -> Unit,
    val controller: com.example.chat.ui.chats.detail.ChatAttachmentActionController,
    val onReviewImages: (List<Uri>) -> Unit,
    val onDirectSendImages: (List<Uri>) -> Unit,
    /** Capture sessions for voice/camera video: open at the ORIGINAL record/entry start (null if signed out). */
    val openCaptureSession: ((onCancel: () -> Unit) -> com.example.chat.ui.AttachFlow?) = { null },
    val closeCaptureSession: (com.example.chat.ui.AttachFlow) -> Unit = {},
)

@Composable
fun rememberChatAttachments(conversationId: String, afterSend: () -> Unit): ChatAttachmentsBindings {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = ServiceLocator.apiBackend
    // Capture-session scope guard (voice/camera video): sessions open under the current account
    // scope and are atomically cancelled on any account transition.
    val captureRegistry = remember { val h = android.os.Handler(android.os.Looper.getMainLooper()); com.example.chat.ui.GalleryLeaseRegistry { r -> h.post { r() } } }
    androidx.compose.runtime.DisposableEffect(api) {
        val l: (com.example.chat.data.api.ApiAccountScope) -> Unit = { next ->
            captureRegistry.onTransition(next, publishEpoch = { }, cancelWaiters = { })
        }
        api.addAuthScopeListener(l)
        onDispose { api.removeAuthScopeListener(l) }
    }
    var pending by remember { mutableStateOf<CompletableDeferred<List<Uri>>?>(null) }
    var mediaReview by remember { mutableStateOf<Pair<List<com.example.chat.ui.chats.detail.MediaReviewItem>, CompletableDeferred<com.example.chat.ui.chats.detail.ReviewedMedia?>>?>(null) }
    var viewOnceAsk by remember { mutableStateOf<Pair<String, CompletableDeferred<Boolean>>?>(null) }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pending?.complete(uris); pending = null
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pending?.complete(listOfNotNull(uri)); pending = null
    }
    var contactPending by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    var docPickerPending by remember { mutableStateOf<CompletableDeferred<List<MediaFileDescriptor>?>?>(null) }
    // Dart custom_file_picker_screen.dart:33 `final _libraryService = MediaLibraryService();`
    // with path_provider sources (media_library_service.dart:48-51): app documents dir + temp dir.
    val mediaLibrary = remember {
        MediaLibraryService(libraryDirectories = { listOf(context.filesDir, context.cacheDir) })
    }
    // Dart document_reader_screen.dart:29 `final _pdfService = PdfProcessingService();`
    // sanitizer workspace = path_provider temp dir (file_sanitization_service.dart).
    val pdfService = remember {
        PdfProcessingService(FileSanitizationService(tempDirectory = { context.cacheDir }))
    }
    var readerDoc by remember { mutableStateOf<MediaFileDescriptor?>(null) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        contactPending?.complete(uri); contactPending = null
    }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    suspend fun read(uri: Uri, type: String): PickedFile {
        val cr = context.contentResolver
        var name = "file"
        var size = -1L
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val s = c.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0) name = c.getString(n) ?: name
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
            }
        }
        val max = if (type == "image") 50L * 1024 * 1024 else ServiceLocator.chatMedia.maxBytes(type)
        // Guard before reading the stream: images are compressed after, so allow headroom for them.
        if (size > 0 && size > max) {
            throw IllegalStateException("This $type exceeds the ${max / (1024 * 1024)} MB storage limit.")
        }
        // Copy the provider URI to an app-private temporary file using a bounded buffer.
        // This preserves access after the picker closes without materializing large media in RAM.
        val staged = withContext(Dispatchers.IO) {
            val target = java.io.File.createTempFile("chaty-outgoing-", ".part", context.cacheDir)
            try {
                cr.openInputStream(uri)?.use { input ->
                    target.outputStream().buffered(64 * 1024).use { output ->
                        input.copyTo(output, 64 * 1024)
                    }
                } ?: throw IllegalStateException("The selected file is not accessible on this device.")
                val finalName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(100).ifBlank { "attachment" }
                val finalFile = java.io.File(context.cacheDir, "chaty-outgoing-${java.util.UUID.randomUUID()}-$finalName")
                check(target.renameTo(finalFile)) { "Unable to stage the selected attachment." }
                finalFile
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
        if (staged.length() <= 0L) {
            staged.delete()
            throw IllegalStateException("The selected file is empty.")
        }
        if (staged.length() > max) {
            staged.delete()
            throw IllegalStateException("This $type exceeds the ${max / (1024 * 1024)} MB storage limit.")
        }
        return PickedFile(name = name, path = staged.absolutePath, mimeType = cr.getType(uri))
    }

    val controller = remember(conversationId) {
        ChatAttachmentActionController(
            conversationId = conversationId,
            backend = object : AttachmentBackendPort {
                override suspend fun upload(type: String, file: PickedFile, durationSeconds: Int): MessageAttachment {
                    // Camera captures arrive as a file path (no picker bytes): read them here,
                    // matching Dart ChatMediaService.uploadFile(sourcePath:).
                    val stagedFile = file.path?.let { java.io.File(it) }
                    if (api.isAuthenticated && stagedFile != null) {
                        var uploadTarget = stagedFile
                        var compressedTemp: java.io.File? = null
                        if (type == "image") {
                            val compressed = com.example.chat.ui.media.ImageSendCompressor.compressFile(stagedFile)
                            if (compressed !== stagedFile) {
                                compressedTemp = compressed
                                uploadTarget = compressed
                            }
                        }
                        try {
                            val uploaded = api.uploadMedia(uploadTarget) { _, _ -> }
                            val effectiveName = if (type == "image" && compressedTemp != null) {
                                if (file.name.contains('.')) "${file.name.substringBeforeLast('.')}.jpg" else "${file.name}.jpg"
                            } else {
                                file.name
                            }
                            return MessageAttachment(
                                id = java.util.UUID.randomUUID().toString(),
                                type = type,
                                name = effectiveName,
                                size = com.example.chat.data.services.ChatMediaService.formatBytes(uploadTarget.length()),
                                url = uploaded["url"]?.toString() ?: throw IllegalStateException("Upload response did not contain a media URL."),
                                durationSeconds = durationSeconds,
                            )
                        } finally {
                            compressedTemp?.delete()
                            if (stagedFile.parentFile == context.cacheDir && stagedFile.name.startsWith("chaty-outgoing-")) {
                                stagedFile.delete()
                            }
                        }
                    }
                    // The legacy Supabase storage adapter accepts ByteArray only.
                    // Never let a 100–700 MB configured media limit turn into an
                    // equally large heap allocation: keep this compatibility route
                    // to a strict 16 MB buffer. The API route above is file-streamed.
                    val payload = try {
                        file.bytes ?: stagedFile?.let { source ->
                            val bufferLimit = minOf(
                                ServiceLocator.chatMedia.maxBytes(type),
                                BoundedFileReader.MAX_IN_MEMORY_UPLOAD_BYTES,
                            )
                            if (source.length() > bufferLimit) {
                                throw IllegalStateException(
                                    "This upload route buffers at most 16 MB in memory. Use Send Large File where available, or choose a smaller file.",
                                )
                            }
                            withContext(Dispatchers.IO) { BoundedFileReader.read(source, bufferLimit) }
                        } ?: throw IllegalStateException("The selected file is empty.")
                    } catch (error: Throwable) {
                        if (stagedFile?.parentFile == context.cacheDir && stagedFile.name.startsWith("chaty-outgoing-")) {
                            stagedFile.delete()
                        }
                        throw error
                    }
                    try {
                        return ServiceLocator.chatMedia.uploadBytes(
                            conversationId, type, payload, file.name, file.mimeType, durationSeconds,
                        )
                    } finally {
                        if (stagedFile?.parentFile == context.cacheDir && stagedFile.name.startsWith("chaty-outgoing-")) {
                            stagedFile.delete()
                        }
                    }
                }
                override suspend fun sendMessage(text: String, type: MessageType, attachment: MessageAttachment?, extraMetadata: Map<String, Any?>?) {
                    api.sendMessage(conversationId, text, type, attachment, extraMetadata)
                }
                override suspend fun sendStructuredMessage(type: String, body: String, metadata: Map<String, Any?>) {
                    // Location / contact go to backend_python as a normal text message: the body already
                    // carries the maps link or the contact details. (Was a Supabase call that never ran.)
                    api.sendMessage(conversationId, body)
                }
                // Polls are hidden in this release (ReleaseFeatures.POLLS); these stay on the old backend.
                override suspend fun createPoll(question: String, options: List<String>, allowMultiple: Boolean) {
                    ServiceLocator.gbFeatures.createPoll(conversationId, question, options, allowMultiple)
                }
                override suspend fun getPoll(messageId: String): Map<String, Any?>? = ServiceLocator.gbFeatures.getPoll(messageId)
                override suspend fun votePoll(messageId: String, optionId: String) {
                    ServiceLocator.gbFeatures.votePoll(messageId, optionId)
                }
                override suspend fun reloadConversation() { api.loadMessages(conversationId) }
            },
            toast = { toast(it) },
            imageShareLimit = { ServiceLocator.preferencesController.gbDouble("Img_share_limit", 30.0).toInt() },
            moreDocsSend = { ServiceLocator.preferencesController.gbBool("key_more_docs_send", false) },
        )
    }

    // Dart shareContact (chat_attachment_actions.dart:212): READ_CONTACTS runtime permission
    // (manifest entry added with this patch) decides the canRead path; the system picker stands in
    // for FlutterContacts.native.showPicker. Android has no "limited" contacts access, so the
    // permission result maps to granted/denied only.
    var contactReadPending by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val contactReadPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactReadPending?.complete(granted); contactReadPending = null
    }
    val contactsPort = object : com.example.chat.ui.chats.detail.ContactsPort {
        override suspend fun requestReadAccess(): com.example.chat.ui.chats.detail.ContactReadAccess {
            if (com.example.chat.data.services.AndroidContactsAccess.hasReadAccess(context)) {
                return com.example.chat.ui.chats.detail.ContactReadAccess.granted
            }
            val d = CompletableDeferred<Boolean>()
            contactReadPending = d
            contactReadPermission.launch(android.Manifest.permission.READ_CONTACTS)
            return if (d.await()) {
                com.example.chat.ui.chats.detail.ContactReadAccess.granted
            } else {
                com.example.chat.ui.chats.detail.ContactReadAccess.denied
            }
        }
        override suspend fun showPicker(canRead: Boolean): com.example.chat.ui.chats.detail.PickedContactRef? {
            val d = CompletableDeferred<Uri?>()
            contactPending = d
            contactPicker.launch(null)
            val uri = d.await() ?: return null
            val (id, name) = com.example.chat.data.services.AndroidContactsAccess.readPickedContact(context, uri)
            return com.example.chat.ui.chats.detail.PickedContactRef(id, name)
        }
        override suspend fun fetchDetails(id: String): com.example.chat.ui.chats.detail.ContactDetails? =
            com.example.chat.data.services.AndroidContactsAccess.fetchContactDetails(context, id)
    }
    // NEW GeoLibre map viewer, loaded only after explicit user action, never during normal chat.
    var locationPickerOpen by remember(conversationId, api.currentUser?.id) { mutableStateOf(false) }
    val liveConversation by androidx.compose.runtime.rememberUpdatedState(conversationId)
    val onLocation: () -> Unit = { locationPickerOpen = true }
    val locationAccount = api.currentUser?.id
    if (locationPickerOpen && locationAccount != null) {
        com.example.chat.ui.chats.location.MapViewerScreen(
            context = context,
            accountId = locationAccount,
            conversationId = conversationId,
            currentScope = { api.currentUser?.id to liveConversation },
            onDismiss = { locationPickerOpen = false },
            onAttributionLink = { uri ->
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
            },
        )
    }
    val onContact: () -> Unit = { scope.launch { controller.shareContact(contactsPort); afterSend() } }
    val onMedia: (String) -> Unit = { type ->
        scope.launch {
            if (type == "image" || type == "video") {
                try {
                    val d = CompletableDeferred<List<Uri>>()
                    pending = d
                    val limit = if (type == "image") ServiceLocator.preferencesController.gbDouble("Img_share_limit", 30.0).toInt().coerceIn(1, 100) else 1
                    if (limit > 1) multi.launch("image/*") else single.launch(if (type == "image") "image/*" else "video/*")
                    val uris = d.await()
                    if (uris.isEmpty()) return@launch
                    check(uris.size <= limit) { "Your configured send limit is $limit." }
                    val items = uris.map { uri -> com.example.chat.ui.chats.detail.MediaReviewItem(uri.toString(), read(uri, type), type) }
                    val review = CompletableDeferred<com.example.chat.ui.chats.detail.ReviewedMedia?>()
                    mediaReview = items to review
                    val result = review.await() ?: return@launch
                    controller.sendReviewedMedia(result.items.map { it.file }, type, result.caption, result.viewOnce)
                    afterSend()
                } catch (e: Exception) { toast(e.message ?: "Unable to prepare attachment") }
                return@launch
            }
            controller.shareMedia(
                type = type,
                pickFiles = { multiple, maxFiles ->
                    if (type == "document") {
                        // Dart shareMedia('document'): Navigator.push -> CustomFilePickerScreen(
                        // initialCategory: document, title: 'Documents & Files'), multi-pick.
                        val d = CompletableDeferred<List<MediaFileDescriptor>?>()
                        docPickerPending = d
                        return@shareMedia d.await()?.map {
                            PickedFile(name = it.name, path = it.path, mimeType = it.mimeType)
                        }
                    }
                    val d = CompletableDeferred<List<Uri>>()
                    pending = d
                    val mime = when (type) { "image" -> "image/*"; "video" -> "video/*"; "audio" -> "audio/*"; else -> "*/*" }
                    if (multiple) multi.launch(mime) else single.launch(mime)
                    val uris = d.await()
                    if (uris.isEmpty()) null else {
                        if (uris.size > maxFiles && maxFiles != Int.MAX_VALUE) {
                            throw IllegalStateException("You selected ${uris.size} files; your configured send limit is $maxFiles.")
                        }
                        uris.map { read(it, type) }
                    }
                },
                confirmViewOnce = { t ->
                    val d = CompletableDeferred<Boolean>()
                    viewOnceAsk = t to d
                    d.await()
                },
            )
            afterSend()
        }
    }
    mediaReview?.let { (items, deferred) ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { mediaReview = null; deferred.complete(null) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
        ) {
            com.example.chat.ui.chats.detail.MediaReviewScreen(
                items,
                onCancel = { mediaReview = null; deferred.complete(null) },
                onSend = { result -> mediaReview = null; deferred.complete(result) },
            )
        }
    }
    viewOnceAsk?.let { (t, d) ->
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { viewOnceAsk = null; d.complete(false) },
            title = { Text("Send ${if (t == "video") "video" else "photo"}") },
            text = { Text("Choose how it should be delivered.") },
            dismissButton = { TextButton(onClick = { viewOnceAsk = null; d.complete(false) }) { Text("Send normally") } },
            confirmButton = { TextButton(onClick = { viewOnceAsk = null; d.complete(true) }) { Text("View once") } },
        )
    }
    docPickerPending?.let { d ->
        CustomFilePickerScreen(
            libraryService = mediaLibrary,
            allowMultiple = true,
            initialCategory = MediaCategory.document,
            title = "Documents & Files",
            // Dart custom_file_picker_screen.dart:745-756 _openFile: real files push
            // DocumentReaderScreen; demo/missing entries toggle selection inside the picker.
            onOpenDocument = { f -> readerDoc = f },
            onSendAttachments = { files -> docPickerPending = null; d.complete(files) },
            onClose = { docPickerPending = null; d.complete(null) },
        )
    }
    readerDoc?.let { f ->
        DocumentReaderScreen(
            file = f,
            pdfService = pdfService,
            onClose = { readerDoc = null },
        )
    }
    // Gallery-sheet review flow: same staging, review and send path as onMedia("image"),
    // fed by the sheet's own MediaStore grid instead of the system picker.
    val onReviewImages: (List<Uri>) -> Unit = { uris ->
        scope.launch {
            try {
                if (uris.isEmpty()) return@launch
                val limit = ServiceLocator.preferencesController.gbDouble("Img_share_limit", 30.0).toInt().coerceIn(1, 100)
                check(uris.size <= limit) { "Your configured send limit is $limit." }
                val items = uris.map { uri -> com.example.chat.ui.chats.detail.MediaReviewItem(uri.toString(), read(uri, "image"), "image") }
                val review = CompletableDeferred<com.example.chat.ui.chats.detail.ReviewedMedia?>()
                mediaReview = items to review
                val result = review.await() ?: return@launch
                controller.sendReviewedMedia(result.items.map { it.file }, "image", result.caption, result.viewOnce)
                afterSend()
            } catch (e: Exception) { toast(e.message ?: "Unable to prepare attachment") }
        }
    }
    // Gallery-sheet direct send: stage and send immediately, no review step.
    val onDirectSendImages: (List<Uri>) -> Unit = { uris ->
        scope.launch {
            try {
                if (uris.isEmpty()) return@launch
                val files = uris.map { uri -> read(uri, "image") }
                controller.sendReviewedMedia(files, "image", "", false)
                afterSend()
            } catch (e: Exception) { toast(e.message ?: "Unable to send") }
        }
    }
    val openCaptureSession: (() -> Unit) -> com.example.chat.ui.AttachFlow? = { onCancel ->
        val st = api.authSnapshot()
        if (st.userId.isNullOrBlank() || st.token.isNullOrBlank()) { toast("Sign in to send attachments."); null }
        else captureRegistry.openSession(st, { api.authSnapshot() }, onCancel)
    }
    val closeCaptureSession: (com.example.chat.ui.AttachFlow) -> Unit = { captureRegistry.closeSession(it) }
    return ChatAttachmentsBindings(onMedia, onContact, onLocation, controller, onReviewImages, onDirectSendImages, openCaptureSession, closeCaptureSession)
}
