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
 *  Not bound here (explicit gaps): location, contact, poll dialog/viewer, camera screen, demo cards.
 *  Deviation: Dart opens its in-app CustomFilePickerScreen; this uses the Android system picker. */
class ChatAttachmentsBindings(
    val onMedia: (String) -> Unit,
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
                override suspend fun upload(type: String, file: PickedFile, durationSeconds: Int): MessageAttachment =
                    ServiceLocator.chatMedia.uploadBytes(
                        conversationId, type, file.bytes ?: throw IllegalStateException("The selected file is empty."),
                        file.name, file.mimeType, durationSeconds,
                    )
                override suspend fun sendMessage(text: String, type: MessageType, attachment: MessageAttachment?, extraMetadata: Map<String, Any?>?) {
                    api.sendMessage(conversationId, text, type, attachment, extraMetadata)
                }
                override suspend fun sendStructuredMessage(type: String, body: String, metadata: Map<String, Any?>) {
                    ServiceLocator.gbFeatures.sendStructuredMessage(conversationId, type, body, metadata)
                }
                override suspend fun createPoll(question: String, options: List<String>, allowMultiple: Boolean) {
                    ServiceLocator.gbFeatures.createPoll(conversationId, question, options, allowMultiple)
                }
                override suspend fun getPoll(messageId: String): Map<String, Any?>? = null
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
    return ChatAttachmentsBindings(onMedia)
}
