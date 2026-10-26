package com.example.chat.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Binding of ChatAttachmentActionController (Dart chat_attachment_actions.dart) for ChatDetailHost.
 *  Bound: image/video/document/audio pick + upload (ChatMediaService -> REST /chat/upload when the
 *  api session is authenticated) + send through ApiBackendService.sendMessage with the Dart JSON
 *  envelope, view-once sheet, share-limit and size guards.
 *  Not bound here (explicit gaps): location, poll dialog/viewer, camera screen, demo cards.
 *  Deviation: Dart opens its in-app CustomFilePickerScreen; this uses the Android system picker. */
class ChatAttachmentsBindings(
    val onMedia: (String) -> Unit,
    val onContact: () -> Unit,
    val onLocation: () -> Unit,
    val controller: com.example.chat.ui.chats.detail.ChatAttachmentActionController,
)

@Composable
fun rememberChatAttachments(conversationId: String, afterSend: () -> Unit): ChatAttachmentsBindings {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = ServiceLocator.apiBackend
    var pending by remember { mutableStateOf<CompletableDeferred<List<Uri>>?>(null) }
    var viewOnceAsk by remember { mutableStateOf<Pair<String, CompletableDeferred<Boolean>>?>(null) }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pending?.complete(uris); pending = null
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pending?.complete(listOfNotNull(uri)); pending = null
    }
    var contactPending by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
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
                    return ServiceLocator.chatMedia.uploadBytes(
                        conversationId, type, payload, file.name, file.mimeType, durationSeconds,
                    )
                }
                override suspend fun sendMessage(text: String, type: MessageType, attachment: MessageAttachment?, extraMetadata: Map<String, Any?>?) {
                    api.sendMessage(conversationId, text, type, attachment, extraMetadata)
                }
                override suspend fun sendStructuredMessage(type: String, body: String, metadata: Map<String, Any?>) {
                    ServiceLocator.gbFeatures.sendStructuredMessage(conversationId, type, body, metadata)
                }
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
    var locPending by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        locPending?.complete(r.values.any { it }); locPending = null
    }
    // Deviation: deniedForever is not distinguishable without an Activity rationale check; a denial maps to `denied`.
    val locationPort = object : com.example.chat.ui.chats.detail.LocationPort {
        private fun granted() = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        private val lm get() = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        override suspend fun isLocationServiceEnabled() = androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)
        override suspend fun checkPermission() = if (granted()) com.example.chat.ui.chats.detail.LocationPermissionState.granted else com.example.chat.ui.chats.detail.LocationPermissionState.denied
        override suspend fun requestPermission(): com.example.chat.ui.chats.detail.LocationPermissionState {
            val d = CompletableDeferred<Boolean>()
            locPending = d
            locPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
            return if (d.await()) com.example.chat.ui.chats.detail.LocationPermissionState.granted else com.example.chat.ui.chats.detail.LocationPermissionState.denied
        }
        @android.annotation.SuppressLint("MissingPermission")
        override suspend fun getCurrentPosition(): Triple<Double, Double, Double> {
            val manager = lm
            val provider = if (manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) android.location.LocationManager.GPS_PROVIDER
            else android.location.LocationManager.NETWORK_PROVIDER
            val loc = kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                val signal = androidx.core.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                androidx.core.location.LocationManagerCompat.getCurrentLocation(
                    manager, provider, signal, androidx.core.content.ContextCompat.getMainExecutor(context),
                ) { cont.resumeWith(Result.success(it)) }
            } ?: throw IllegalStateException("Could not determine your location.")
            return Triple(loc.latitude, loc.longitude, loc.accuracy.toDouble())
        }
    }
    val onLocation: () -> Unit = { scope.launch { controller.shareLocation(locationPort); afterSend() } }
    val onContact: () -> Unit = { scope.launch { controller.shareContact(contactsPort); afterSend() } }
    val onMedia: (String) -> Unit = { type ->
        scope.launch {
            controller.shareMedia(
                type = type,
                pickFiles = { multiple, maxFiles ->
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
    viewOnceAsk?.let { (t, d) ->
        AlertDialog(
            onDismissRequest = { viewOnceAsk = null; d.complete(false) },
            title = { Text("Send ${if (t == "video") "video" else "photo"}") },
            text = { Text("Choose how it should be delivered.") },
            dismissButton = { TextButton(onClick = { viewOnceAsk = null; d.complete(false) }) { Text("Send normally") } },
            confirmButton = { TextButton(onClick = { viewOnceAsk = null; d.complete(true) }) { Text("View once") } },
        )
    }
    return ChatAttachmentsBindings(onMedia, onContact, onLocation, controller)
}
