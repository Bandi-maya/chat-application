package com.example.chat.ui.chats.detail

import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType

/** A platform-picked file handed to the controller (Dart MediaFileDescriptor /
 *  file_picker result). [bytes] is what ChatMediaService.uploadBytes consumes;
 *  [path] is kept for audio/voice uploads that stream from disk. */
data class PickedFile(
    val name: String,
    val bytes: ByteArray? = null,
    val path: String? = null,
    val mimeType: String? = null,
)

/** Dart _PollDraft. */
data class PollDraft(
    val question: String,
    val options: List<String>,
    val allowMultiple: Boolean,
) {
    companion object {
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 12
        const val QUESTION_MAX_LENGTH = 240
        const val OPTION_MAX_LENGTH = 120

        /** Dart _CreatePollDialog submit guard: trimmed question non-empty and
         *  at least two non-blank options (extra options are dropped). */
        fun normalized(question: String, options: List<String>, allowMultiple: Boolean): PollDraft? {
            val q = question.trim()
            val opts = options.map { it.trim() }.filter { it.isNotEmpty() }
            if (q.isEmpty() || opts.size < MIN_OPTIONS) return null
            return PollDraft(q, opts, allowMultiple)
        }
    }
}

/** Dart location permission ladder (geolocator). */
enum class LocationPermissionState { denied, deniedForever, granted }

interface LocationPort {
    suspend fun isLocationServiceEnabled(): Boolean
    suspend fun checkPermission(): LocationPermissionState
    suspend fun requestPermission(): LocationPermissionState
    suspend fun getCurrentPosition(): Triple<Double, Double, Double> // lat, lon, accuracyMeters
}

/** Dart flutter_contacts access ladder. */
enum class ContactReadAccess { granted, limited, denied }

data class PickedContactRef(val id: String?, val displayName: String?)

data class ContactDetails(
    val displayName: String?,
    val phones: List<String>,
    val emails: List<String>,
)

interface ContactsPort {
    suspend fun requestReadAccess(): ContactReadAccess
    /** showPicker(properties: phone+email when readable, null otherwise). */
    suspend fun showPicker(canRead: Boolean): PickedContactRef?
    /** FlutterContacts.get(id, properties: phone,email); null keeps the ref. */
    suspend fun fetchDetails(id: String): ContactDetails?
}

/** Backend effect seams - every one maps onto an EXISTING Dart call; the
 *  owner binds them to the selected backend's real endpoints (or reports the
 *  endpoint missing; none are invented here). */
interface AttachmentBackendPort {
    /** ChatMediaService.uploadFile: (type, file) -> stored attachment. */
    suspend fun upload(type: String, file: PickedFile, durationSeconds: Int = 0): MessageAttachment

    /** dataStore.sendMessage(text, type, attachment, extraMetadata). */
    suspend fun sendMessage(
        text: String,
        type: MessageType,
        attachment: MessageAttachment? = null,
        extraMetadata: Map<String, Any?>? = null,
    )

    /** GbFeatureBackendService.sendStructuredMessage (location/contact). */
    suspend fun sendStructuredMessage(type: String, body: String, metadata: Map<String, Any?>)

    /** GbFeatureBackendService.createPoll. */
    suspend fun createPoll(question: String, options: List<String>, allowMultiple: Boolean)

    /** Supabase rpc 'get_poll' (p_message_id) -> row map. */
    suspend fun getPoll(messageId: String): Map<String, Any?>?

    /** GbFeatureBackendService.votePoll. */
    suspend fun votePoll(messageId: String, optionId: String)

    /** dataStore.ensureConversationLoaded(conversationId). */
    suspend fun reloadConversation()
}

/** Exact port of lib/features/messages/chat_attachment_actions.dart's flow
 *  logic (validation, limits, payload shapes, error/toast strings). All UI
 *  (view-once sheet, poll dialog, pickers) and platform effects (toasts,
 *  location, contacts) are injected seams; the controller is pure JVM.
 *  conversationId is carried for the backend port binding (Dart instance
 *  field); effect closures capture it at wiring time. */
class ChatAttachmentActionController(
    val conversationId: String,
    private val backend: AttachmentBackendPort,
    private val toast: (String) -> Unit,
    /** gbDouble('Img_share_limit', 30) preference seam. */
    private val imageShareLimit: () -> Int = { 30 },
    /** gbBool('key_more_docs_send') preference seam. */
    private val moreDocsSend: () -> Boolean = { false },
    private val now: () -> Long = System::currentTimeMillis,
) {
    // ------------------------------------------------------------------ media

    /** Dart shareMedia(). [pickFiles] plays the platform picker for the type
     *  (document -> multi custom picker; image/video/audio -> single or multi
     *  per prefs); [confirmViewOnce] plays the view-once sheet for single
     *  image/video sends. Returns after the flow completes (never throws:
     *  Dart catches everything into a toast). */
    suspend fun shareMedia(
        type: String,
        pickFiles: suspend (multiple: Boolean, maxFiles: Int) -> List<PickedFile>?,
        confirmViewOnce: suspend (type: String) -> Boolean,
    ) {
        try {
            if (type == "document") {
                // Dart: CustomFilePickerScreen(document) multi-pick branch.
                val picked = pickFiles(true, Int.MAX_VALUE)
                if (picked.isNullOrEmpty()) return
                for (item in picked) {
                    val attachment = backend.upload("document", item)
                    backend.sendMessage("", MessageType.document, attachment)
                }
                toast("${picked.size} ${if (picked.size == 1) "document" else "documents"} sent.")
                return
            }
            val imageLimit = imageShareLimit().coerceIn(1, 100)
            val multiple = (type == "image" && imageLimit > 1) ||
                (type == "document" && moreDocsSend())
            if (multiple) {
                val items = pickFiles(true, if (type == "image") imageLimit else 30).orEmpty()
                for (item in items) {
                    val attachment = backend.upload(type, item)
                    backend.sendMessage("", messageTypeFor(type), attachment)
                }
                if (items.isNotEmpty()) {
                    toast("${items.size} ${if (items.size == 1) "file" else "files"} sent.")
                }
            } else {
                val picked = pickFiles(false, 1)?.firstOrNull() ?: return
                val attachment = backend.upload(type, picked)
                var viewOnce = false
                if (type == "image" || type == "video") {
                    viewOnce = confirmViewOnce(type)
                }
                backend.sendMessage(
                    "",
                    messageTypeFor(type),
                    attachment,
                    if (viewOnce) mapOf("view_once" to true) else null,
                )
            }
        } catch (error: Throwable) {
            toast("Unable to send $type: ${error.message ?: error}")
        }
    }

    // --------------------------------------------------------------- location

    /** Dart shareLocation(): permission ladder with the exact error strings,
     *  then a structured 'location' message and a conversation reload. */
    suspend fun shareLocation(location: LocationPort) {
        try {
            if (!location.isLocationServiceEnabled()) {
                throw IllegalStateException(
                    "Location services are disabled. Enable GPS and try again.",
                )
            }
            var permission = location.checkPermission()
            if (permission == LocationPermissionState.denied) {
                permission = location.requestPermission()
            }
            if (permission == LocationPermissionState.denied) {
                throw IllegalStateException("Location permission was denied.")
            }
            if (permission == LocationPermissionState.deniedForever) {
                throw IllegalStateException(
                    "Location permission is permanently denied. Enable it in Android app settings.",
                )
            }
            val (latitude, longitude, accuracy) = location.getCurrentPosition()
            val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"
            backend.sendStructuredMessage(
                "location",
                "📍 Location\n$mapsUrl",
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "accuracy_meters" to accuracy,
                    "maps_url" to mapsUrl,
                ),
            )
            backend.reloadConversation()
            toast("Location sent.")
        } catch (error: Throwable) {
            toast("Unable to share location: ${error.message ?: error}")
        }
    }

    // --------------------------------------------------------------- contacts

    /** Dart shareContact(): read permission decides whether the picker/fetch
     *  carry phone+email properties; body is the 👤/📞/✉️ line format. */
    suspend fun shareContact(contacts: ContactsPort) {
        try {
            val access = contacts.requestReadAccess()
            val canRead = access == ContactReadAccess.granted || access == ContactReadAccess.limited
            val picked = contacts.showPicker(canRead) ?: return
            val details = if (canRead && picked.id != null) {
                contacts.fetchDetails(picked.id)
            } else {
                null
            }
            val name = (details?.displayName ?: picked.displayName)?.trim()
                .let { if (it.isNullOrEmpty()) "Contact" else it }
            val phones = (details?.phones ?: emptyList())
                .map { it.trim() }.filter { it.isNotEmpty() }
            val emails = (details?.emails ?: emptyList())
                .map { it.trim() }.filter { it.isNotEmpty() }
            val body = buildContactBody(name, phones, emails)
            backend.sendStructuredMessage(
                "contact",
                body,
                mapOf("contact_name" to name, "phones" to phones, "emails" to emails),
            )
            backend.reloadConversation()
            toast("Contact sent.")
        } catch (error: Throwable) {
            toast("Unable to share contact: ${error.message ?: error}")
        }
    }

    // ------------------------------------------------------------------ polls

    /** Dart createPoll(): the dialog produces a validated [PollDraft]; the
     *  controller only runs the backend call + toast. */
    suspend fun createPoll(draft: PollDraft) {
        try {
            backend.createPoll(draft.question, draft.options, draft.allowMultiple)
            backend.reloadConversation()
            toast("Poll created.")
        } catch (error: Throwable) {
            toast("Unable to create poll: ${error.message ?: error}")
        }
    }

    /** Dart _PollViewerSheet._load(): rpc 'get_poll'; a non-map payload is the
     *  'Poll data is unavailable.' error. */
    suspend fun loadPoll(messageId: String): PollState {
        val raw = backend.getPoll(messageId)
            ?: throw IllegalStateException("Poll data is unavailable.")
        return PollState.from(raw)
    }

    /** Dart _vote(): rethrows nothing; on failure shows 'Unable to vote: ...'
     *  and leaves the previous state in place. Returns the refreshed state on
     *  success, null on failure (error surfaced via [toast]). */
    suspend fun votePoll(messageId: String, optionId: String): PollState? {
        return try {
            backend.votePoll(messageId, optionId)
            loadPoll(messageId)
        } catch (error: Throwable) {
            toast("Unable to vote: ${error.message ?: error}")
            null
        }
    }

    // ------------------------------------------------------------------ voice

    /** Dart recordVoiceNote()'s post-sheet half: upload the staged recording
     *  then send it as an audio message. */
    suspend fun sendRecordedVoiceNote(path: String, durationSeconds: Int) {
        try {
            val attachment = backend.upload(
                "audio",
                PickedFile(name = "voice_note_${now()}.m4a", path = path),
                durationSeconds,
            )
            backend.sendMessage("", MessageType.audio, attachment)
            toast("Voice note sent.")
        } catch (error: Throwable) {
            toast("Unable to send voice note: ${error.message ?: error}")
        }
    }

    // ----------------------------------------------------------------- camera

    /** Dart _captureAndSendPhoto()'s post-capture half: upload the captured
     *  image, send with caption + effect metadata, then [onSent] (Dart:
     *  trackConversation + scrollToBottom + ChatyHaptics.success). */
    suspend fun sendCapturedPhoto(
        path: String,
        caption: String,
        effectId: String,
        effectIntensity: Double,
        onSent: suspend () -> Unit = {},
    ) {
        try {
            val attachment = backend.upload("image", PickedFile(name = path.substringAfterLast('/'), path = path))
            backend.sendMessage(
                caption,
                MessageType.image,
                attachment,
                mapOf("effect" to effectId, "effect_intensity" to effectIntensity.toString()),
            )
            onSent()
        } catch (error: Throwable) {
            toast("Could not send photo. ${error.message ?: error}")
        }
    }

    // ------------------------------------------------------------------ demos

    /** Dart sendDemoDocument(). */
    suspend fun sendDemoDocument() {
        try {
            backend.sendMessage(
                "",
                MessageType.document,
                MessageAttachment(
                    id = "demo_doc_spec",
                    type = "document",
                    name = "Project_Architecture_Spec.pdf",
                    size = "2.4 MB",
                    url = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
                ),
            )
            backend.reloadConversation()
            toast("📄 Demo document sent.")
        } catch (error: Throwable) {
            toast("Unable to send demo document: ${error.message ?: error}")
        }
    }

    /** Dart sendDemoAudio(). */
    suspend fun sendDemoAudio() {
        try {
            backend.sendMessage(
                "",
                MessageType.audio,
                MessageAttachment(
                    id = "demo_audio_track",
                    type = "audio",
                    name = "Hangova - Anirudh (Audio Track).mp3",
                    size = "4.8 MB",
                    url = "https://actions.google.com/sounds/v1/ambiences/rain_heavy.ogg",
                    durationSeconds = 225,
                ),
                mapOf(
                    "artist" to "Anirudh Ravichander",
                    "title" to "Hangova",
                    "album" to "Vettaiyan",
                    "duration" to 225,
                ),
            )
            backend.reloadConversation()
            toast("🎵 Demo music track sent.")
        } catch (error: Throwable) {
            toast("Unable to send demo audio: ${error.message ?: error}")
        }
    }

    /** Dart sendDemoCallCard(). */
    suspend fun sendDemoCallCard() {
        try {
            backend.sendMessage(
                "📞 Voice call ended • 4m 12s",
                MessageType.system,
                null,
                mapOf(
                    "call_id" to "demo_call_${now()}",
                    "call_type" to "voice",
                    "direction" to "incoming",
                    "status" to "completed",
                    "duration_seconds" to 252,
                ),
            )
            backend.reloadConversation()
            toast("📞 Demo call card sent.")
        } catch (error: Throwable) {
            toast("Unable to send demo call card: ${error.message ?: error}")
        }
    }

    /** Dart sendDemoTask(). */
    suspend fun sendDemoTask() {
        try {
            backend.sendMessage(
                "Review Dynamic Island animations & specular shaders",
                MessageType.taskCard,
                null,
                mapOf(
                    "task_title" to "Validate Dynamic Island animations",
                    "status" to "todo",
                    "priority" to "high",
                ),
            )
            backend.reloadConversation()
            toast("📋 Demo task card sent.")
        } catch (error: Throwable) {
            toast("Unable to send demo task: ${error.message ?: error}")
        }
    }

    /** Dart sendDemoLocation(). */
    suspend fun sendDemoLocation() {
        try {
            val latitude = 37.7749
            val longitude = -122.4194
            val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"
            backend.sendMessage(
                "📍 Market Street, San Francisco, CA\n$mapsUrl",
                MessageType.location,
                null,
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "accuracy_meters" to 5.0,
                    "maps_url" to mapsUrl,
                ),
            )
            backend.reloadConversation()
            toast("📍 Demo location sent.")
        } catch (error: Throwable) {
            toast("Unable to send demo location: ${error.message ?: error}")
        }
    }

    companion object {
        /** Dart isPollMessage. */
        fun isPollMessage(text: String): Boolean = text.startsWith("[POLL] ")

        /** Dart _messageType (default branch is document). */
        fun messageTypeFor(type: String): MessageType = when (type) {
            "image" -> MessageType.image
            "video" -> MessageType.video
            "audio" -> MessageType.audio
            else -> MessageType.document
        }

        /** Dart contact body: '👤 name', then up to 5 '📞 phone' lines, then
         *  up to 3 '✉️ email' lines. */
        fun buildContactBody(name: String, phones: List<String>, emails: List<String>): String {
            val lines = mutableListOf("👤 $name")
            phones.take(5).forEach { lines.add("📞 $it") }
            emails.take(3).forEach { lines.add("✉️ $it") }
            return lines.joinToString("\n")
        }
    }
}

/** Parsed _PollViewerSheet state (options with votes/voted_by_me, totals). */
data class PollState(
    val question: String,
    val options: List<PollOption>,
    val totalVotes: Int,
    val allowMultiple: Boolean,
) {
    data class PollOption(
        val id: String,
        val label: String,
        val votes: Int,
        val votedByMe: Boolean,
    ) {
        /** Dart LinearProgressIndicator value: votes / totalVotes clamped 0..1. */
        fun progress(totalVotes: Int): Double =
            if (totalVotes == 0) 0.0 else (votes.toDouble() / totalVotes).coerceIn(0.0, 1.0)
    }

    /** Dart footer: 'N total vote(s)' + ' • multiple answers allowed'. */
    val footerText: String
        get() = "$totalVotes total vote${if (totalVotes == 1) "" else "s"}" +
            if (allowMultiple) " • multiple answers allowed" else ""

    companion object {
        fun from(raw: Map<String, Any?>): PollState {
            val rawOptions = raw["options"]
            val options = (rawOptions as? List<*>).orEmpty().mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                PollOption(
                    id = map["id"]?.toString() ?: "",
                    label = map["label"]?.toString() ?: "Option",
                    votes = (map["votes"] as? Number)?.toInt() ?: 0,
                    votedByMe = map["voted_by_me"] == true,
                )
            }
            return PollState(
                question = raw["question"]?.toString() ?: "Poll",
                options = options,
                totalVotes = (raw["total_votes"] as? Number)?.toInt() ?: 0,
                allowMultiple = raw["allow_multiple"] == true,
            )
        }
    }
}
