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
    /** Gallery lease: issue on the ORIGINAL paperclip open (null if signed out). The sheet keeps it, and passes the same
     *  object to onPickedImages and closeGallery. Opaque: nothing in the UI can mint or alter a scope. */
    val openGallery: () -> GalleryLease? = { null },
    val closeGallery: (GalleryLease) -> Unit = {},
    /** Capture sessions for voice/camera video: open at the ORIGINAL record/entry start (null if signed out). */
    val openCaptureSession: ((onCancel: () -> Unit) -> AttachFlow?) = { null },
    val closeCaptureSession: (AttachFlow) -> Unit = {},
    val onPickedImages: (GalleryLease, List<Uri>) -> Unit = { _, _ -> },
    /** Bumped on the main dispatcher (never under the transition lock) after every transition; sheet closes and drops selection. */
    val scopeEpoch: Int = 0,
)

@Composable
fun rememberChatAttachments(conversationId: String, afterSend: () -> Unit): ChatAttachmentsBindings {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = ServiceLocator.apiBackend
    var pending by remember { mutableStateOf<CompletableDeferred<List<Uri>>?>(null) }
    var mediaReview by remember { mutableStateOf<Pair<List<com.example.chat.ui.chats.detail.MediaReviewItem>, CompletableDeferred<com.example.chat.ui.chats.detail.ReviewedMedia?>>?>(null) }
    var viewOnceAsk by remember { mutableStateOf<Pair<String, CompletableDeferred<Boolean>>?>(null) }
    val multiGate = remember { ResultGate() }; val singleGate = remember { ResultGate() }
    val pendingGate = remember { java.util.concurrent.atomic.AtomicReference<ResultGate?>(null) }   // launcher that owns the current `pending`
    val contactGate = remember { ResultGate() }; val permGate = remember { ResultGate() }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!multiGate.accept()) return@rememberLauncherForActivityResult   // late result of a cancelled flow's picker
        pending?.complete(uris); pending = null
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (!singleGate.accept()) return@rememberLauncherForActivityResult
        pending?.complete(listOfNotNull(uri)); pending = null
    }
    var contactPending by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    var contactReadPending by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
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
        if (!contactGate.accept()) return@rememberLauncherForActivityResult
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
        val max = ServiceLocator.chatMedia.maxBytes(type)
        // Guard before reading the stream: images are compressed after, so allow headroom for them.
        if (size > 0 && type != "image" && size > max) {
            throw IllegalStateException("This $type exceeds the ${max / (1024 * 1024)} MB storage limit.")
        }
        val bytes = withContext(Dispatchers.IO) {
            cr.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("The selected file is not accessible on this device.")
        }
        return PickedFile(name = name, bytes = bytes, mimeType = cr.getType(uri))
    }

    // Account scope of the running attach flow (pick -> review -> upload -> send). Captured once at the entry tap;
    // every upload/send in the flow uses exactly this immutable scope. A scope transition cancels the flow.
    var scopeEpoch by remember { mutableStateOf(0) }
    val registry = remember { val h = android.os.Handler(android.os.Looper.getMainLooper()); GalleryLeaseRegistry { r -> h.post { r() } } }
    androidx.compose.runtime.DisposableEffect(api) {
        // Listener runs synchronously under the transition lock: cancel-only (flag + completing waiters), no IO/UI.
        // Under the transition lock: registry does atomics + one Handler.post (always queued, never inline).
        val l: (com.example.chat.data.api.ApiAccountScope) -> Unit = { next ->
            registry.onTransition(next, publishEpoch = { n -> scopeEpoch = n }, cancelWaiters = {
                // a waiter that is set has a launched picker: abandon it so its late callback cannot fulfil a later flow
                pending?.let { it.complete(emptyList()); pending = null; pendingGate.getAndSet(null)?.orphan() }
                contactPending?.let { it.complete(null); contactPending = null; contactGate.orphan() }
                contactReadPending?.let { it.complete(false); contactReadPending = null; permGate.orphan() }
                mediaReview?.second?.complete(null); viewOnceAsk?.second?.complete(false); docPickerPending?.complete(null)
            })
        }
        api.addAuthScopeListener(l)
        onDispose { api.removeAuthScopeListener(l) }
    }
    suspend fun scopeForFlow(): com.example.chat.data.api.ApiAccountScope? {
        val f = kotlin.coroutines.coroutineContext[AttachFlowElement]?.flow   // ownership: only the flow's own coroutine context
        if (f != null && f.isCancelled) throw IllegalStateException("Account changed; attachment cancelled.")
        return f?.scope   // null only outside an attach flow (then the service captures once at call time)
    }

    val controller = remember(conversationId) {
        ChatAttachmentActionController(
            conversationId = conversationId,
            backend = object : AttachmentBackendPort {
                override suspend fun upload(type: String, file: PickedFile, durationSeconds: Int): MessageAttachment {
                    // Camera captures arrive as a file path (no picker bytes): read them here,
                    // matching Dart ChatMediaService.uploadFile(sourcePath:).
                    val payload = file.bytes ?: file.path?.let { p ->
                        withContext(Dispatchers.IO) { java.io.File(p).readBytes() }
                    } ?: throw IllegalStateException("The selected file is empty.")
                    val s = scopeForFlow() ?: api.authSnapshot()
                    return withContext(com.example.chat.data.api.ApiScopeElement(s)) {
                        ServiceLocator.chatMedia.uploadBytes(
                            conversationId, type, payload, file.name, file.mimeType, durationSeconds,
                        )
                    }
                }
                override suspend fun sendMessage(text: String, type: MessageType, attachment: MessageAttachment?, extraMetadata: Map<String, Any?>?) {
                    api.sendMessage(conversationId, text, type, attachment, extraMetadata, expectedScope = scopeForFlow())
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
    val contactReadPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!permGate.accept()) return@rememberLauncherForActivityResult
        contactReadPending?.complete(granted); contactReadPending = null
    }
    // Launch a picker only when that launcher has no abandoned launch still owed a callback (else a late old result is indistinguishable).
    fun launchPick(d: CompletableDeferred<List<Uri>>, multiple: Boolean, mime: String) {
        val g = if (multiple) multiGate else singleGate
        if (g.hasOrphans()) { toast("Previous picker is still closing. Try again."); d.complete(emptyList()); return }
        pending = d; pendingGate.set(g)
        if (multiple) multi.launch(mime) else single.launch(mime)
    }
    val contactsPort = object : com.example.chat.ui.chats.detail.ContactsPort {
        override suspend fun requestReadAccess(): com.example.chat.ui.chats.detail.ContactReadAccess {
            if (com.example.chat.data.services.AndroidContactsAccess.hasReadAccess(context)) {
                return com.example.chat.ui.chats.detail.ContactReadAccess.granted
            }
            val d = CompletableDeferred<Boolean>()
            if (permGate.hasOrphans()) return com.example.chat.ui.chats.detail.ContactReadAccess.denied
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
            if (contactGate.hasOrphans()) return null
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
    val onContact: () -> Unit = { scope.launch {
        val st = api.authSnapshot()
        if (st.userId.isNullOrBlank() || st.token.isNullOrBlank()) { toast("Sign in to send attachments."); return@launch }
        val flow = registry.begin(st, null) { api.authSnapshot() } ?: run { toast("Another attachment is in progress."); return@launch }
        try {
            if (flow.isCancelled) { toast("Account changed. Try again."); return@launch }
            withContext(AttachFlowElement(flow)) { controller.shareContact(contactsPort) }; afterSend()
        } finally { registry.end(flow) }
    } }
    val onMedia: (String) -> Unit = { type ->
        scope.launch {
            val started = api.authSnapshot()
            if (started.userId.isNullOrBlank() || started.token.isNullOrBlank()) { toast("Sign in to send attachments."); return@launch }
            val flow = registry.begin(started, null) { api.authSnapshot() } ?: run { toast("Another attachment is in progress."); return@launch }
            try {
            if (flow.isCancelled) { toast("Account changed. Try again."); return@launch }
            if (type == "image" || type == "video") {
                try {
                    val d = CompletableDeferred<List<Uri>>()
                    val limit = if (type == "image") ServiceLocator.preferencesController.gbDouble("Img_share_limit", 30.0).toInt().coerceIn(1, 100) else 1
                    launchPick(d, limit > 1, if (limit > 1) "image/*" else if (type == "image") "image/*" else "video/*")
                    val uris = d.await()
                    if (uris.isEmpty()) return@launch
                    check(uris.size <= limit) { "Your configured send limit is $limit." }
                    val items = uris.map { uri -> com.example.chat.ui.chats.detail.MediaReviewItem(uri.toString(), read(uri, type), type) }
                    val review = CompletableDeferred<com.example.chat.ui.chats.detail.ReviewedMedia?>()
                    mediaReview = items to review
                    val result = review.await()
                    mediaReview = null
                    if (result == null || flow.isCancelled) return@launch
                    withContext(AttachFlowElement(flow)) { controller.sendReviewedMedia(result.items.map { it.file }, type, result.caption, result.viewOnce) }
                    afterSend()
                } catch (e: Exception) { toast(e.message ?: "Unable to prepare attachment") }
                return@launch
            }
            if (flow.isCancelled) { toast("Account changed. Try again."); return@launch }
            withContext(AttachFlowElement(flow)) {
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
                    val mime = when (type) { "image" -> "image/*"; "video" -> "video/*"; "audio" -> "audio/*"; else -> "*/*" }
                    launchPick(d, multiple, mime)
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
            }
            afterSend()
            } finally { registry.end(flow) }
        }
    }
    val openGallery: () -> GalleryLease? = {
        val st = api.authSnapshot()
        if (st.userId.isNullOrBlank() || st.token.isNullOrBlank()) { toast("Sign in to send attachments."); null }
        else registry.issue(st) { api.authSnapshot() }
    }
    val closeGallery: (GalleryLease) -> Unit = { registry.release(it) }
    val openCaptureSession: (() -> Unit) -> AttachFlow? = { onCancel ->
        val st = api.authSnapshot()
        if (st.userId.isNullOrBlank() || st.token.isNullOrBlank()) { toast("Sign in to send attachments."); null }
        else registry.openSession(st, { api.authSnapshot() }, onCancel)
    }
    val closeCaptureSession: (AttachFlow) -> Unit = { registry.closeSession(it) }
    val onPickedImages: (GalleryLease, List<Uri>) -> Unit = { lease, uris ->
        scope.launch {
            // begin() consumes the lease (single use, closes queued-callback reuse); refuses if a flow runs or the lease is dead.
            val flow = registry.begin(lease.scope, lease) { api.authSnapshot() }
            if (flow == null) { toast(if (lease.isValid) "Another attachment is in progress." else "Account changed. Open the gallery again."); return@launch }
            try {
                if (api.authSnapshot() !== lease.scope) { flow.cancelled.set(true); toast("Account changed. Open the gallery again."); return@launch }
                val limit = ServiceLocator.preferencesController.gbDouble("Img_share_limit", 30.0).toInt().coerceIn(1, 100)
                if (uris.isEmpty()) return@launch
                check(uris.size <= limit) { "Your configured send limit is $limit." }
                val items = uris.map { uri -> com.example.chat.ui.chats.detail.MediaReviewItem(uri.toString(), read(uri, "image"), "image") }
                if (flow.isCancelled) return@launch
                val review = CompletableDeferred<com.example.chat.ui.chats.detail.ReviewedMedia?>()
                mediaReview = items to review
                val result = review.await()
                mediaReview = null
                if (result == null || flow.isCancelled) return@launch
                withContext(AttachFlowElement(flow)) { controller.sendReviewedMedia(result.items.map { it.file }, "image", result.caption, result.viewOnce) }
                afterSend()
            } catch (e: Exception) { toast(e.message ?: "Unable to prepare attachment") }
            finally { registry.end(flow) }
        }
    }
    mediaReview?.let { (items, deferred) ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { mediaReview = null; deferred.complete(null) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
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
    return ChatAttachmentsBindings(onMedia, onContact, onLocation, controller, openGallery, closeGallery, openCaptureSession, closeCaptureSession, onPickedImages, scopeEpoch)
}
