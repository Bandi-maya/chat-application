package com.example.chat.data.transfer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.chat.data.network.TransferDirection
import com.example.chat.data.network.TransferKind
import com.example.chat.data.network.TransferMonitor
import com.example.chat.data.network.TransferStatus
import com.example.chat.data.network.NetworkStatusMonitor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Native direct WebRTC DataChannel transport for large Chaty files.
 *
 * Signaling uses the existing authenticated call_signal WebSocket event. The
 * 256-bit transfer secret is NEVER sent through signaling: it lives only in
 * the chaty:// link fragment. Every data chunk is AES-GCM authenticated by
 * NativeP2pTransferProtocol and the final file is checked against SHA-256.
 *
 * Direct-only by default. A TURN server can be injected by the application;
 * no public TURN credentials or unauthenticated relay are invented here.
 */
class P2pFileTransferService(
    context: Context,
    private val scope: CoroutineScope,
    private val currentUserId: () -> String?,
    private val sendSignal: (targetUserId: String, data: Map<String, Any?>) -> Boolean,
    private val transferMonitor: TransferMonitor,
    private val iceServers: suspend () -> List<PeerConnection.IceServer> = { defaultIceServers() },
    /** Current link estimate; used only to tune bounded chunk windows and buffering. */
    private val networkStatus: () -> NetworkStatusMonitor.Status? = { null },
) {
    data class PendingOffer(
        val transferId: String,
        val senderId: String,
        val name: String,
        val sizeBytes: Long,
        val mimeType: String,
        val expiresAtEpochSeconds: Long,
    )

    data class TransferState(
        val transferId: String,
        val peerUserId: String,
        val fileName: String,
        val totalBytes: Long,
        val transferredBytes: Long = 0,
        val direction: TransferDirection,
        val status: String,
        val error: String? = null,
        val localFilePath: String? = null,
    ) {
        val progress: Float
            get() = if (totalBytes <= 0) 0f else (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    private data class WireManifest(
        val transferId: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val chunkBytes: Int,
        val sha256Hex: String,
        val noncePrefix: ByteArray,
        val expiresAtEpochSeconds: Long,
    ) {
        fun protocol() = NativeP2pTransferProtocol.Manifest(
            transferId, displayName, mimeType, sizeBytes, chunkBytes, sha256Hex, noncePrefix, expiresAtEpochSeconds,
        )
        fun json() = JSONObject()
            .put("transfer_id", transferId).put("name", displayName).put("mime", mimeType)
            .put("size", sizeBytes).put("chunk", chunkBytes).put("sha256", sha256Hex)
            .put("nonce", b64(noncePrefix)).put("expires", expiresAtEpochSeconds)
        companion object {
            fun parse(value: Any?): WireManifest {
                val j = value as? Map<*, *> ?: throw SecurityException("Transfer manifest missing")
                val id = j["transfer_id"]?.toString() ?: throw SecurityException("Transfer id missing")
                val name = j["name"]?.toString() ?: "video"
                val mime = j["mime"]?.toString() ?: "application/octet-stream"
                val digest = j["sha256"]?.toString() ?: ""
                val nonceText = j["nonce"]?.toString() ?: throw SecurityException("Transfer nonce missing")
                if (id.length > 64 || name.length > 180 || mime.length > 120 || digest.length != 64 || nonceText.length > 64) {
                    throw SecurityException("Transfer manifest fields exceed limits")
                }
                return WireManifest(
                    id, name, mime,
                    (j["size"] as? Number)?.toLong() ?: j["size"]?.toString()?.toLongOrNull() ?: 0L,
                    (j["chunk"] as? Number)?.toInt() ?: j["chunk"]?.toString()?.toIntOrNull() ?: 0,
                    digest, unb64(nonceText),
                    (j["expires"] as? Number)?.toLong() ?: j["expires"]?.toString()?.toLongOrNull() ?: 0L,
                )
            }
        }
    }

    private data class Session(
        val id: String,
        val peerUserId: String,
        val localUserId: String,
        val file: File?,
        val manifest: NativeP2pTransferProtocol.Manifest,
        val secret: ByteArray,
        val direction: TransferDirection,
        val directOriginal: Boolean = false,
        var outgoingControlSequence: Long = 0,
        var incomingControlSequence: Long = 0,
        var sourceChannel: FileChannel? = null,
        var sourceOwner: java.io.Closeable? = null,
        var sourceUri: String? = null,
        var peer: PeerConnection? = null,
        var channel: DataChannel? = null,
        var ledger: ChunkReceiptLedger? = null,
        var destination: File? = null,
        var finalDestination: File? = null,
        var monitorId: String? = null,
        val sentChunkIndices: java.util.BitSet = java.util.BitSet(),
        var sentBytes: Long = 0,
        var receivedBytes: Long = 0,
        var chunksSinceSync: Int = 0,
        var receiverFinalized: Boolean = false,
        var restartAttempts: Int = 0,
        var restartInProgress: Boolean = false,
        var remoteDescriptionSet: Boolean = false,
        var signalingReady: Boolean = false,
        val queuedCandidates: MutableList<IceCandidate> = mutableListOf(),
        val pendingLocalCandidates: MutableList<IceCandidate> = mutableListOf(),
        val receiveMutex: Mutex = Mutex(),
        val sendMutex: Mutex = Mutex(),
        val requestMutex: Mutex = Mutex(),
    )

    private val appContext = context.applicationContext
    private val resumePrefs by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "chaty_p2p_sender_resume",
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private val restoringUploads = ConcurrentHashMap.newKeySet<String>()
    private val factory: PeerConnectionFactory
    private val sessions = ConcurrentHashMap<String, Session>()
    private val incoming = ConcurrentHashMap<String, Pair<PendingOffer, Map<String, Any?>>>()
    private val incomingCandidates = ConcurrentHashMap<String, MutableList<Pair<String, IceCandidate>>>()
    private val _pendingOffers = MutableStateFlow<List<PendingOffer>>(emptyList())
    val pendingOffers: StateFlow<List<PendingOffer>> = _pendingOffers.asStateFlow()
    private val _transfers = MutableStateFlow<List<TransferState>>(emptyList())
    val transfers: StateFlow<List<TransferState>> = _transfers.asStateFlow()

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).setEnableInternalTracer(false).createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        scope.launch(Dispatchers.IO) { restorePersistedUploadSnapshots() }
    }

    /** Creates the P2P offer and returns a link whose fragment is secret material. */
    suspend fun offerFile(
        targetUserId: String,
        file: File,
        mimeType: String,
        linkExpirySeconds: Long = 7L * 24L * 60L * 60L,
    ): String = withContext(Dispatchers.IO) {
        val localUserId = currentUserId() ?: throw SecurityException("Sign in before sending a file.")
        require(targetUserId.isNotBlank() && targetUserId != localUserId) { "Choose a valid recipient" }
        val invite = NativeP2pTransferProtocol.createInvite()
        val manifest = NativeP2pTransferProtocol.createManifest(
            invite, file, mimeType, System.currentTimeMillis() / 1000L + linkExpirySeconds,
            chunkBytes = preferredChunkBytes(),
        )
        val auth = NativeP2pTransferProtocol.authenticateManifest(manifest, invite.secret)
        val wireManifest = WireManifest(manifest.transferId, manifest.displayName, manifest.mimeType, manifest.sizeBytes, manifest.chunkBytes, manifest.sha256Hex, manifest.noncePrefix, manifest.expiresAtEpochSeconds)
        val session = Session(
            invite.transferId, targetUserId, localUserId, file, manifest, invite.secret.copyOf(),
            TransferDirection.upload, sourceUri = file.toURI().toString(),
        )
        try {
            persistSenderResume(session)
        } catch (error: Throwable) {
            invite.secret.fill(0)
            session.secret.fill(0)
            throw error
        }
        sessions[invite.transferId] = session
        val monitorId = transferMonitor.begin(
            TransferDirection.upload, when {
                mimeType.startsWith("video/") -> TransferKind.video
                mimeType.startsWith("image/") -> TransferKind.image
                mimeType.startsWith("audio/") -> TransferKind.audio
                else -> TransferKind.document
            }, manifest.sizeBytes, manifest.displayName,
        )
        session.monitorId = monitorId
        publish(session, "Waiting for recipient")
        try {
            val peer = createPeer(session)
            val init = DataChannel.Init().apply { ordered = true }
            val channel = peer.createDataChannel("chaty-file-v1", init)
                ?: throw IllegalStateException("Could not create transfer channel")
            session.channel = channel
            observeChannel(session, channel, isSender = true)
            val offer = createDescription(peer, offer = true)
            if (!sendSignalScoped(session, mapOf(
                "signal_type" to "file_transfer_offer",
                "call_id" to invite.transferId,
                "transfer_id" to invite.transferId,
                "sdp" to offer.description,
                "manifest" to mapOf("transfer_id" to wireManifest.transferId, "name" to wireManifest.displayName, "mime" to wireManifest.mimeType, "size" to wireManifest.sizeBytes, "chunk" to wireManifest.chunkBytes, "sha256" to wireManifest.sha256Hex, "nonce" to b64(wireManifest.noncePrefix), "expires" to wireManifest.expiresAtEpochSeconds),
                "manifest_auth" to b64(auth),
            ))) throw IllegalStateException("Chaty signaling is offline; retry when connected")
            session.signalingReady = true
            flushLocalCandidates(session)
            invite.toUri().also { invite.secret.fill(0) }
        } catch (error: Throwable) {
            clearSenderResume(invite.transferId)
            invite.secret.fill(0)
            fail(session, error.message ?: "Unable to start transfer")
            throw error
        }
    }

    /** Zero-copy source path for ACTION_OPEN_DOCUMENT: the seekable provider descriptor stays open until send completes. */
    suspend fun offerFile(
        targetUserId: String,
        source: FileChannel,
        displayName: String,
        sizeBytes: Long,
        mimeType: String,
        sourceOwner: java.io.Closeable? = null,
        linkExpirySeconds: Long = 7L * 24L * 60L * 60L,
        sourceUri: String? = null,
        directOriginal: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val localUserId = currentUserId() ?: throw SecurityException("Sign in before sending a file.")
        require(targetUserId.isNotBlank() && targetUserId != localUserId) { "Choose a valid recipient" }
        if (directOriginal) {
            require(mimeType.startsWith("image/") || mimeType.startsWith("video/")) { "Chaty Direct supports original images/videos only" }
            require(!sourceUri.isNullOrBlank()) { "Saved original access is required for durable resume" }
        }

        val invite = NativeP2pTransferProtocol.createInvite()
        val manifest = NativeP2pTransferProtocol.createManifest(
            invite, source, displayName, sizeBytes, mimeType,
            System.currentTimeMillis() / 1000L + linkExpirySeconds,
            chunkBytes = preferredChunkBytes(),
        )
        val auth = NativeP2pTransferProtocol.authenticateManifest(manifest, invite.secret)
        val wireManifest = WireManifest(manifest.transferId, manifest.displayName, manifest.mimeType, manifest.sizeBytes,
            manifest.chunkBytes, manifest.sha256Hex, manifest.noncePrefix, manifest.expiresAtEpochSeconds)
        val session = Session(
            invite.transferId, targetUserId, localUserId, null, manifest, invite.secret.copyOf(),
            TransferDirection.upload, directOriginal = directOriginal, sourceChannel = source, sourceOwner = sourceOwner, sourceUri = sourceUri,
        )
        try {
            persistSenderResume(session)
        } catch (error: Throwable) {
            invite.secret.fill(0)
            session.secret.fill(0)
            runCatching { (sourceOwner ?: source).close() }
            throw error
        }
        sessions[invite.transferId] = session
        val kind = when {
            mimeType.startsWith("video/") -> TransferKind.video
            mimeType.startsWith("image/") -> TransferKind.image
            mimeType.startsWith("audio/") -> TransferKind.audio
            else -> TransferKind.document
        }
        session.monitorId = transferMonitor.begin(TransferDirection.upload, kind, manifest.sizeBytes, manifest.displayName)
        publish(session, "Waiting for recipient")
        if (directOriginal) {
            // Ticket first, negotiation later. Existing restart events recover offline peers.
            return@withContext invite.toUri().also { invite.secret.fill(0) }
        }
        try {
            val peer = createPeer(session)
            val channel = peer.createDataChannel(if (session.directOriginal) ChatyDirectProtocol.CHANNEL else "chaty-file-v1", DataChannel.Init().apply { ordered = true })
                ?: throw IllegalStateException("Could not create transfer channel")
            session.channel = channel
            observeChannel(session, channel, isSender = true)
            val offer = createDescription(peer, offer = true)
            if (!sendSignalScoped(session, mapOf(
                "signal_type" to "file_transfer_offer", "call_id" to invite.transferId,
                "transfer_id" to invite.transferId, "sdp" to offer.description,
                "manifest" to mapOf(
                    "transfer_id" to wireManifest.transferId, "name" to wireManifest.displayName,
                    "mime" to wireManifest.mimeType, "size" to wireManifest.sizeBytes,
                    "chunk" to wireManifest.chunkBytes, "sha256" to wireManifest.sha256Hex,
                    "nonce" to b64(wireManifest.noncePrefix), "expires" to wireManifest.expiresAtEpochSeconds,
                ),
                "manifest_auth" to b64(auth),
            ))) throw IllegalStateException("Chaty signaling is offline; retry when connected")
            session.signalingReady = true
            flushLocalCandidates(session)
            invite.toUri().also { invite.secret.fill(0) }
        } catch (error: Throwable) {
            clearSenderResume(invite.transferId)
            invite.secret.fill(0)
            fail(session, error.message ?: "Unable to start transfer")
            throw error
        }
    }

    /** Called for scoped call_signal events. Unknown call events are ignored. */
    suspend fun handleRemoteSignal(signal: Map<String, Any?>) {
        val kind = signal["signal_type"]?.toString() ?: return
        if (!kind.startsWith("file_transfer_")) return
        val id = signal["transfer_id"]?.toString() ?: return
        if (signal["call_id"]?.toString() != id) return
        val canonicalId = runCatching { java.util.UUID.fromString(id).toString() }.getOrNull()
        if (canonicalId != id || senderIdInvalid(signal["sender_id"]?.toString() ?: signal["caller_id"]?.toString())) return
        val sender = (signal["sender_id"] ?: signal["caller_id"])?.toString() ?: return
        if (sender.isBlank() || sender == currentUserId()) return
        when (kind) {
            "file_transfer_offer" -> {
                val manifest = WireManifest.parse(signal["manifest"])
                if (manifest.transferId != id || manifest.sizeBytes !in 1..NativeP2pTransferProtocol.MAX_FILE_BYTES) return
                if (manifest.chunkBytes !in (16 * 1024)..(64 * 1024) || manifest.noncePrefix.size != 4) return
                if (!manifest.sha256Hex.matches(Regex("[0-9a-f]{64}"))) return
                if (manifest.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L) return
                if (signal["sdp"]?.toString().orEmpty().length !in 1..256_000) return
                if (manifest.displayName.length > 180 || manifest.mimeType.length > 120) return
                if (incoming.size >= MAX_PENDING_OFFERS && !incoming.containsKey(id)) return
                val offer = PendingOffer(id, sender, manifest.displayName, manifest.sizeBytes, manifest.mimeType, manifest.expiresAtEpochSeconds)
                val activeReceiver = sessions[id]?.takeIf { it.peerUserId == sender && it.direction == TransferDirection.download }
                if (activeReceiver != null) {
                    val peer = activeReceiver.peer ?: return
                    val currentManifest = activeReceiver.manifest
                    if (manifest.transferId != currentManifest.transferId ||
                        manifest.displayName != currentManifest.displayName ||
                        manifest.mimeType != currentManifest.mimeType ||
                        manifest.sizeBytes != currentManifest.sizeBytes ||
                        manifest.chunkBytes != currentManifest.chunkBytes ||
                        manifest.sha256Hex != currentManifest.sha256Hex ||
                        (activeReceiver.directOriginal && manifest.expiresAtEpochSeconds != currentManifest.expiresAtEpochSeconds) ||
                        !manifest.noncePrefix.contentEquals(currentManifest.noncePrefix)
                    ) return
                    val authentication = signal["manifest_auth"]?.toString()?.takeIf { it.length <= 128 }
                        ?: throw SecurityException("Transfer authentication missing")
                    NativeP2pTransferProtocol.verifyManifest(
                        activeReceiver.manifest, unb64(authentication), activeReceiver.secret,
                    )
                    activeReceiver.remoteDescriptionSet = false
                    activeReceiver.signalingReady = false
                    setRemoteDescription(peer, SessionDescription.Type.OFFER, remoteSdp(activeReceiver, signal["sdp"]?.toString() ?: return))
                    activeReceiver.remoteDescriptionSet = true
                    flushCandidates(activeReceiver)
                    val answer = createDescription(peer, offer = false)
                    if (sendSignalScoped(activeReceiver, mapOf("signal_type" to "file_transfer_answer", "call_id" to id, "transfer_id" to id, "sdp" to answer.description))) {
                        activeReceiver.signalingReady = true
                        activeReceiver.restartAttempts = 0
                        flushLocalCandidates(activeReceiver)
                        publish(activeReceiver, "Reconnected — resuming")
                    }
                } else {
                    incoming[id] = offer to signal
                    _pendingOffers.value = incoming.values.map { it.first }.sortedByDescending { it.expiresAtEpochSeconds }
                }
            }
            "file_transfer_answer" -> {
                val session = sessions[id]?.takeIf { it.peerUserId == sender && it.direction == TransferDirection.upload } ?: return
                val sdp = signal["sdp"]?.toString().orEmpty()
                if (sdp.length !in 1..256_000) return
                if (sdp.isNotBlank()) {
                    setRemoteDescription(session.peer ?: return, SessionDescription.Type.ANSWER, remoteSdp(session, sdp))
                    session.remoteDescriptionSet = true
                }
                flushCandidates(session)
                session.restartAttempts = 0
                publish(session, "Connecting")
            }
            "file_transfer_ice" -> {
                val candidate = signal["candidate"]?.toString().orEmpty()
                if (candidate.isBlank() || candidate.length > 4096) return
                val ice = IceCandidate(signal["sdp_mid"]?.toString(), (signal["sdp_mline"] as? Number)?.toInt() ?: 0, candidate)
                val session = sessions[id]?.takeIf { it.peerUserId == sender }
                if (session == null) {
                    if (!incomingCandidates.containsKey(id) && incomingCandidates.size >= MAX_PENDING_OFFERS) return
                    // ICE is untrusted until tied to the sender whose authenticated
                    // offer is accepted. Keep sender identity with every queued item.
                    val queue = incomingCandidates.getOrPut(id) {
                        java.util.Collections.synchronizedList(mutableListOf())
                    }
                    synchronized(queue) {
                        if (queue.size < 256 && queue.none { it.first == sender && it.second.sdp == ice.sdp }) {
                            queue.add(sender to ice)
                        }
                    }
                } else if (!session.remoteDescriptionSet) session.queuedCandidates.add(ice)
                else if (!session.directOriginal || !ChatyDirectProtocol.isRelayCandidate(ice.sdp)) session.peer?.addIceCandidate(ice)
            }
            "file_transfer_restart_request" -> {
                val active = sessions[id]?.takeIf { it.peerUserId == sender && it.direction == TransferDirection.upload }
                if (active != null) {
                    active.restartAttempts = 0
                    active.restartInProgress = false
                    restartIce(active)
                } else {
                    // The sender process may have died. Re-open the persisted,
                    // app-granted source URI and re-offer the same manifest so
                    // the recipient's durable chunk ledger can continue.
                    restoreSenderSession(id, sender)
                }
            }
            "file_transfer_cancel" -> {
                sessions[id]?.takeIf { it.peerUserId == sender }?.let {
                    if (it.direction == TransferDirection.upload) clearSenderResume(id)
                    else discardPartialReceive(it)
                    fail(it, "Sender cancelled this transfer")
                }
                val pending = incoming[id]?.first
                if (pending?.senderId == sender) {
                    incoming.remove(id)
                    incomingCandidates.remove(id)
                    refreshPending()
                }
            }
        }
    }

    /** User taps Receive on the in-chat transfer card and selects a private destination. */
    suspend fun acceptInvite(link: String, destination: File, senderUserId: String? = null, directOriginal: Boolean = false): Boolean {
        val invite = NativeP2pTransferProtocol.parseInvite(link)
        var pending = incoming[invite.transferId]
        if (pending == null && !senderUserId.isNullOrBlank() && currentUserId() != null) {
            // Ask the live sender to re-offer; the receiver ledger resumes only authenticated chunks.
            sendSignal(senderUserId, mapOf(
                "signal_type" to "file_transfer_restart_request",
                "call_id" to invite.transferId,
                "transfer_id" to invite.transferId,
                "request_nonce" to java.util.UUID.randomUUID().toString(),
            ))
        }
        var waitSteps = 0
        while (pending == null && waitSteps < 20) {
            kotlinx.coroutines.delay(500)
            pending = incoming[invite.transferId]
            waitSteps++
        }
        val (offer, signal) = pending ?: run {
            invite.secret.fill(0)
            throw IllegalStateException("The sender is not online yet. Keep Chaty open and try again when the offer arrives.")
        }
        if (offer.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L) {
            invite.secret.fill(0)
            incoming.remove(invite.transferId)
            incomingCandidates.remove(invite.transferId)
            refreshPending()
            throw SecurityException("This transfer link has expired")
        }
        if (!senderUserId.isNullOrBlank() && senderUserId != offer.senderId) {
            invite.secret.fill(0)
            incoming.remove(invite.transferId)
            incomingCandidates.remove(invite.transferId)
            refreshPending()
            throw SecurityException("Transfer sender does not match the chat message sender.")
        }
        val wire = try {
            WireManifest.parse(signal["manifest"])
        } catch (error: Throwable) {
            invite.secret.fill(0)
            throw error
        }
        val manifest = try {
            wire.protocol()
        } catch (error: Throwable) {
            invite.secret.fill(0)
            throw error
        }
        try {
            val authentication = signal["manifest_auth"]?.toString()?.takeIf { it.length <= 128 }
                ?: throw SecurityException("Transfer authentication missing")
            NativeP2pTransferProtocol.verifyManifest(manifest, unb64(authentication), invite.secret)
        } catch (error: Throwable) {
            invite.secret.fill(0)
            throw error
        }
        if (manifest.transferId != invite.transferId) {
            invite.secret.fill(0)
            throw SecurityException("Transfer manifest does not match the invitation.")
        }
        val safeDestination = try {
            validatePrivateReceiveDestination(appContext.filesDir, destination).also { safe ->
                val availableBytes = android.os.StatFs(safe.parentFile!!.absolutePath).availableBytes
                if (availableBytes < manifest.sizeBytes) {
                    throw IllegalStateException("Not enough free storage to receive this file.")
                }
            }
        } catch (error: Throwable) {
            invite.secret.fill(0)
            throw error
        }
        val localUserId = currentUserId() ?: run {
            invite.secret.fill(0)
            throw SecurityException("Sign in before receiving a file.")
        }

        // Validate and create all on-disk paths before copying the invitation
        // secret into a long-lived session object.
        val resumeKey = invite.transferId + "-" + manifest.sha256Hex.take(16)
        val partFile = File(safeDestination.parentFile!!, "." + resumeKey + ".part")
        if (partFile.canonicalFile.parentFile != safeDestination.parentFile ||
            (partFile.exists() && partFile.canonicalFile != partFile.absoluteFile)
        ) {
            invite.secret.fill(0)
            throw SecurityException("Partial receive file path is not safe.")
        }

        val appFilesRoot = appContext.filesDir.canonicalFile
        val ledgerDirectory = File(appFilesRoot, "p2p-ledger")
        if (!ledgerDirectory.exists() && !ledgerDirectory.mkdirs()) {
            invite.secret.fill(0)
            throw IllegalStateException("Unable to create private transfer ledger.")
        }
        val safeLedgerDirectory = ledgerDirectory.canonicalFile
        if (!safeLedgerDirectory.isDirectory || safeLedgerDirectory.name != "p2p-ledger" ||
            safeLedgerDirectory.parentFile != appFilesRoot
        ) {
            invite.secret.fill(0)
            throw SecurityException("Private transfer ledger path is not trusted.")
        }
        val ledgerFile = File(safeLedgerDirectory, resumeKey + ".state")
        if (ledgerFile.canonicalFile.parentFile != safeLedgerDirectory ||
            (ledgerFile.exists() && ledgerFile.canonicalFile != ledgerFile.absoluteFile)
        ) {
            invite.secret.fill(0)
            throw SecurityException("Transfer ledger file path is not safe.")
        }
        val ledger = try {
            ChunkReceiptLedger(ledgerFile, manifest.chunkCount).also {
                if (!partFile.isFile) it.clear()
            }
        } catch (error: Throwable) {
            invite.secret.fill(0)
            throw error
        }
        val receivedBytes = try {
            minOf(
                ledger.receivedCount().toLong() * manifest.chunkBytes.toLong(),
                manifest.sizeBytes,
            )
        } catch (error: Throwable) {
            invite.secret.fill(0)
            runCatching { ledger.clear() }
            throw error
        }

        val transferKind = when {
            manifest.mimeType.startsWith("video/") -> TransferKind.video
            manifest.mimeType.startsWith("image/") -> TransferKind.image
            manifest.mimeType.startsWith("audio/") -> TransferKind.audio
            else -> TransferKind.document
        }
        val monitorId = try {
            transferMonitor.begin(
                TransferDirection.download, transferKind, manifest.sizeBytes, manifest.displayName,
            )
        } catch (error: Throwable) {
            invite.secret.fill(0)
            runCatching { ledger.clear() }
            throw error
        }

        val session = Session(
            invite.transferId,
            offer.senderId,
            localUserId,
            null,
            manifest,
            invite.secret.copyOf(),
            TransferDirection.download,
            directOriginal = directOriginal,
            destination = partFile,
            finalDestination = safeDestination,
        ).apply {
            this.ledger = ledger
            this.receivedBytes = receivedBytes
            this.monitorId = monitorId
        }
        invite.secret.fill(0)
        incomingCandidates.remove(invite.transferId)?.let { candidates ->
            val matchingCandidates = synchronized(candidates) {
                candidates.filter { (candidateSender, _) -> candidateSender == offer.senderId }
            }
            matchingCandidates.forEach { (_, candidate) -> session.queuedCandidates.add(candidate) }
        }
        if (directOriginal) {
            val record = JSONObject().put("local_user_id", localUserId).put("peer_user_id", offer.senderId)
                .put("part", partFile.absolutePath).put("ledger", ledgerFile.absolutePath)
            if (!resumePrefs.edit().putString("receiver_" + session.id, record.toString()).commit()) {
                session.secret.fill(0)
                throw IllegalStateException("Could not save Direct receive recovery state")
            }
        }
        sessions[invite.transferId] = session
        return try {
            val peer = createPeer(session)
            setRemoteDescription(peer, SessionDescription.Type.OFFER, remoteSdp(session, signal["sdp"]?.toString() ?: throw SecurityException("Offer missing")))
            session.remoteDescriptionSet = true
            flushCandidates(session)
            val answer = createDescription(peer, offer = false)
            if (!sendSignalScoped(session, mapOf(
                "signal_type" to "file_transfer_answer", "call_id" to invite.transferId, "transfer_id" to invite.transferId, "sdp" to answer.description,
            ))) throw IllegalStateException("Chaty signaling is offline")
            session.signalingReady = true
            flushLocalCandidates(session)
            incoming.remove(invite.transferId); refreshPending()
            publish(session, "Waiting for secure data channel")
            true
        } catch (error: Throwable) {
            fail(session, error.message ?: "Could not accept transfer")
            throw error
        }
    }

    /** User-requested retry after an outage; sender descriptors can be restored after process death. */
    fun retryTransfer(transferId: String): Boolean {
        val session = sessions[transferId]
        if (session == null) {
            val record = runCatching { loadSenderResumeRecord(transferId) }.getOrNull() ?: return false
            if (currentUserId() != record.optString("local_user_id")) return false
            scope.launch { restoreSenderSession(transferId, record.optString("peer_user_id")) }
            return true
        }
        if (currentUserId() != session.localUserId) return false
        session.restartAttempts = 0
        session.restartInProgress = false
        if (session.direction == TransferDirection.upload) {
            if (session.peer == null) scope.launch {
                try {
                    startRestoredSenderOffer(session)
                } catch (error: Throwable) {
                    fail(session, error.message ?: "Could not restart sender transfer")
                }
            } else restartIce(session)
        } else {
            session.remoteDescriptionSet = false
            session.signalingReady = false
            val queued = sendSignalScoped(session, mapOf(
                "signal_type" to "file_transfer_restart_request",
                "call_id" to session.id,
                "transfer_id" to session.id,
                "request_nonce" to java.util.UUID.randomUUID().toString(),
            ))
            publish(session, "Requesting secure reconnect")
            if (!queued) return false
        }
        return true
    }
    fun cancel(transferId: String) {
        val session = sessions[transferId]
        if (session == null) {
            val senderRecord = runCatching { loadSenderResumeRecord(transferId) }.getOrNull()
            clearSenderResume(transferId)
            if (senderRecord != null && senderRecord.optString("local_user_id") == currentUserId()) {
                sendSignal(senderRecord.optString("peer_user_id"), mapOf("signal_type" to "file_transfer_cancel", "call_id" to transferId, "transfer_id" to transferId))
                _transfers.value = _transfers.value.map { if (it.transferId == transferId) it.copy(status = "Cancelled", error = null) else it }
            }
            val receipt = runCatching { resumePrefs.getString("receiver_" + transferId, null)?.let(::JSONObject) }.getOrNull()
            if (receipt != null && receipt.optString("local_user_id") == currentUserId()) {
                val root = appContext.filesDir.canonicalFile
                val part = File(receipt.optString("part")).canonicalFile
                val ledger = File(receipt.optString("ledger")).canonicalFile
                if (part.parentFile == File(root, "p2p-received").canonicalFile && part.name.startsWith("." + transferId + "-")) part.delete()
                if (ledger.parentFile == File(root, "p2p-ledger").canonicalFile && ledger.name.startsWith(transferId + "-")) ledger.delete()
                resumePrefs.edit().remove("receiver_" + transferId).commit()
                sendSignal(receipt.optString("peer_user_id"), mapOf("signal_type" to "file_transfer_cancel", "call_id" to transferId, "transfer_id" to transferId))
                _transfers.value = _transfers.value.map { if (it.transferId == transferId) it.copy(status = "Cancelled", error = null) else it }
            }
            incoming.remove(transferId)
            incomingCandidates.remove(transferId)
            refreshPending()
            return
        }
        if (session.direction == TransferDirection.upload) {
            clearSenderResume(transferId)
        } else {
            discardPartialReceive(session)
        }
        sendSignalScoped(session, mapOf("signal_type" to "file_transfer_cancel", "call_id" to transferId, "transfer_id" to transferId))
        fail(session, "Cancelled")
    }

    /** Removes only the private partial file, never the user's selected final destination. */
    private fun discardPartialReceive(session: Session) {
        if (session.direction != TransferDirection.download) return
        session.destination?.let { runCatching { it.delete() } }
        session.ledger?.let { runCatching { it.clear() } }
        if (session.directOriginal) resumePrefs.edit().remove("receiver_" + session.id).commit()
    }

    private fun remoteSdp(session: Session, sdp: String): String =
        if (session.directOriginal) ChatyDirectProtocol.directSdp(sdp) else sdp

    private suspend fun createPeer(session: Session): PeerConnection {
        val servers = iceServers().ifEmpty { defaultIceServers() }
        val safeServers = if (session.directOriginal) servers.flatMap { server ->
            server.urls.filter(ChatyDirectProtocol::allowedIceUrl).map { PeerConnection.IceServer.builder(it).createIceServer() }
        } else servers
        val config = PeerConnection.RTCConfiguration(safeServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        val peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null && (!session.directOriginal || !ChatyDirectProtocol.isRelayCandidate(candidate.sdp))) {
                    if (session.signalingReady) sendCandidate(session, candidate)
                    else if (session.pendingLocalCandidates.size < 256) session.pendingLocalCandidates.add(candidate)
                }
            }
            override fun onDataChannel(channel: DataChannel?) {
                if (channel != null && session.direction == TransferDirection.download) {
                    if (session.directOriginal && channel.label() != ChatyDirectProtocol.CHANNEL) {
                        channel.close()
                        fail(session, "Update both phones: incompatible Chaty Direct version")
                        return
                    }
                    session.channel = channel
                    observeChannel(session, channel, isSender = false)
                }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        session.restartAttempts = 0
                        publish(session, "Secure channel connected")
                        if (session.direction == TransferDirection.download) scope.launch { requestMissing(session) }
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        publish(session, "Connection interrupted — retrying")
                        scope.launch {
                            kotlinx.coroutines.delay(3_000)
                            if (session.peer?.connectionState() == PeerConnection.PeerConnectionState.DISCONNECTED) requestIceRestart(session)
                        }
                    }
                    PeerConnection.PeerConnectionState.FAILED -> requestIceRestart(session)
                    PeerConnection.PeerConnectionState.CLOSED -> Unit
                    else -> Unit
                }
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.FAILED) requestIceRestart(session)
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
            override fun onRenegotiationNeeded() {}
        }) ?: throw IllegalStateException("Could not create WebRTC peer connection")
        session.outgoingControlSequence = 0
        session.incomingControlSequence = 0
        session.peer = peer
        if (session.directOriginal) scope.launch {
            kotlinx.coroutines.delay(30_000)
            if (sessions[session.id] === session && session.peer === peer && session.channel?.state() != DataChannel.State.OPEN) {
                fail(session, "Direct unavailable: keep both phones open and retry. No upload or relay was used.")
            }
        }
        return peer
    }

    private fun sendControl(session: Session, message: JSONObject): Boolean = synchronized(session) {
        val channel = session.channel ?: return@synchronized false
        val body = message.toString()
        val wire = if (session.directOriginal) {
            val sequence = ++session.outgoingControlSequence
            JSONObject().put("version", 2).put("sequence", sequence).put("body", body)
                .put("mac", b64(ChatyDirectProtocol.controlMac(session.secret, session.id, session.localUserId, sequence, body))).toString()
        } else body
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(wire.toByteArray(Charsets.UTF_8)), false))
    }

    private fun observeChannel(session: Session, channel: DataChannel, isSender: Boolean) {
        if (session.directOriginal && channel.label() != ChatyDirectProtocol.CHANNEL) {
            fail(session, "Update both phones: incompatible Chaty Direct version")
            return
        }
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                if (channel.state() == DataChannel.State.OPEN) {
                    publish(session, if (isSender) "Secure channel ready" else "Receiving")
                    if (!isSender) scope.launch { requestMissing(session) }
                }
                if (channel.state() == DataChannel.State.CLOSED) publish(session, "Channel closed")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) {
                    val size = buffer.data.remaining()
                    val overhead = if (session.directOriginal) ChatyDirectProtocol.OVERHEAD else 8 + 12 + 16
                    if (size <= overhead || size > session.manifest.chunkBytes + overhead) {
                        scope.launch { fail(session, "Invalid transfer chunk frame") }
                        return
                    }
                    val bytes = ByteArray(size)
                    buffer.data.get(bytes)
                    if (!isSender) scope.launch { receiveChunk(session, bytes) }
                    return
                }
                val controlSize = buffer.data.remaining()
                if (controlSize !in 1..MAX_CONTROL_FRAME_BYTES) {
                    scope.launch { fail(session, "Invalid transfer control frame") }
                    return
                }
                val controlBytes = ByteArray(controlSize)
                buffer.data.get(controlBytes)
                val text = try {
                    Charsets.UTF_8.decode(ByteBuffer.wrap(controlBytes)).toString()
                } finally {
                    controlBytes.fill(0)
                }
                val message = try {
                    val envelope = JSONObject(text)
                    if (session.directOriginal) {
                        val sequence = envelope.getLong("sequence")
                        val body = envelope.getString("body")
                        val expected = ChatyDirectProtocol.controlMac(session.secret, session.id, session.peerUserId, sequence, body)
                        val supplied = unb64(envelope.getString("mac"))
                        val valid = envelope.optInt("version") == 2 && sequence > session.incomingControlSequence &&
                            java.security.MessageDigest.isEqual(expected, supplied)
                        expected.fill(0)
                        if (!valid) throw SecurityException("Direct peer control authentication failed")
                        session.incomingControlSequence = sequence
                        JSONObject(body)
                    } else envelope
                } catch (error: Throwable) {
                    if (session.directOriginal) fail(session, "Update both phones or retry: Direct authentication failed")
                    return
                }
                when (message.optString("kind")) {
                    "request" -> if (isSender) scope.launch {
                        runCatching { sendRequested(session, message.optJSONArray("indices")) }
                            .onFailure { fail(session, it.message ?: "Original source could not be sent") }
                    }
                    "complete" -> if (isSender) completeSender(session)
                    "pause" -> publish(session, "Peer paused")
                }
            }
        })
    }

    private suspend fun requestMissing(session: Session) = session.requestMutex.withLock {
        val channel = session.channel ?: return@withLock
        val ledger = session.ledger ?: return@withLock
        while (channel.state() == DataChannel.State.OPEN && session.peer?.connectionState() == PeerConnection.PeerConnectionState.CONNECTED && !ledger.isComplete()) {
            // Small windows keep DataChannel buffering bounded on 1G/2G links; do not resend a slow batch after 10 seconds.
            val indices = ledger.missingIndices(maxRequestChunks())
            if (indices.isEmpty()) break
            val json = JSONObject().put("kind", "request").put("indices", JSONArray().apply { indices.forEach { put(it) } })
            if (!sendControl(session, json)) {
                kotlinx.coroutines.delay(150)
                continue
            }
            // Sender responds in ordered batches. Wait for a batch before requesting more.
            val before = ledger.receivedCount()
            val expectedAfterBatch = before + indices.size
            var waitCount = 0
            while (ledger.receivedCount() < expectedAfterBatch && !ledger.isComplete() && channel.state() == DataChannel.State.OPEN && session.peer?.connectionState() == PeerConnection.PeerConnectionState.CONNECTED && waitCount < SLOW_LINK_BATCH_WAIT_ITERATIONS) {
                kotlinx.coroutines.delay(250)
                waitCount++
            }
            if (ledger.receivedCount() < expectedAfterBatch) {
                publish(session, "Waiting for sender / network")
                kotlinx.coroutines.delay(1_000)
            }
        }
        if (ledger.isComplete()) runCatching { finalizeReceiver(session) }
            .onFailure { fail(session, it.message ?: "Final verification failed") }
    }

    private suspend fun sendRequested(session: Session, array: JSONArray?) {
        session.sendMutex.lock()
        try {
        val file = session.file
        val sourceChannel = session.sourceChannel
        if (file == null && sourceChannel == null) return
        val channel = session.channel ?: return
        if (channel.state() != DataChannel.State.OPEN) return
        val count = minOf(array?.length() ?: 0, MAX_REQUEST_CHUNKS)
        for (i in 0 until count) {
            if (channel.state() != DataChannel.State.OPEN) break
            var backpressureWaits = 0
            while (channel.bufferedAmount() > maxBufferedBytes() && channel.state() == DataChannel.State.OPEN && backpressureWaits < 250) {
                kotlinx.coroutines.delay(40)
                backpressureWaits++
            }
            if (backpressureWaits >= 250 || channel.state() != DataChannel.State.OPEN) {
                publish(session, "Paused by network backpressure")
                break
            }
            val index = array?.optLong(i, -1L) ?: -1L
            if (index !in 0 until session.manifest.chunkCount) continue
            val packet = if (session.directOriginal) {
                val bytes = withContext(Dispatchers.IO) {
                    if (file != null) java.io.RandomAccessFile(file, "r").use { ChatyDirectProtocol.encryptChunk(it.channel, session.manifest, session.secret, index) }
                    else ChatyDirectProtocol.encryptChunk(sourceChannel!!, session.manifest, session.secret, index)
                }
                ByteBuffer.wrap(bytes)
            } else {
            val encrypted = withContext(Dispatchers.IO) {
                if (file != null) NativeP2pTransferProtocol.encryptChunk(file, session.manifest, session.secret, index)
                else NativeP2pTransferProtocol.encryptChunk(sourceChannel!!, session.manifest, session.secret, index)
            }
            val packet = ByteBuffer.allocate(8 + encrypted.nonce.size + encrypted.ciphertextAndTag.size).order(ByteOrder.BIG_ENDIAN)
            packet.putLong(index)
            packet.put(encrypted.nonce)
            packet.put(encrypted.ciphertextAndTag)
            packet.flip()
                packet
            }
            if (!channel.send(DataChannel.Buffer(packet, true))) {
                publish(session, "Network backpressure — waiting")
                break
            }
            if (!session.sentChunkIndices[index.toInt()]) {
                session.sentChunkIndices.set(index.toInt())
                session.sentBytes = minOf(session.manifest.sizeBytes, session.sentBytes + minOf(session.manifest.chunkBytes.toLong(), session.manifest.sizeBytes - index * session.manifest.chunkBytes))
            }
            session.monitorId?.let { transferMonitor.update(it, session.sentBytes, session.manifest.sizeBytes) }
            publish(session, "Sending")
        }
        } finally {
            session.sendMutex.unlock()
        }
    }

    private suspend fun receiveChunk(session: Session, bytes: ByteArray) = session.receiveMutex.withLock {
        if (bytes.size < 8 + 12 + 16) return@withLock
        try {
            val count = if (session.directOriginal) withContext(Dispatchers.IO) {
                val ledger = session.ledger ?: throw IllegalStateException("Resume ledger missing")
                val part = session.destination ?: throw IllegalStateException("Destination missing")
                val index = ChatyDirectProtocol.writeChunk(part, session.manifest, session.secret, bytes)
                NativeP2pTransferProtocol.syncFile(part)
                ledger.markReceived(index)
                ledger.flush()
                ledger.receivedCount()
            } else {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val index = buffer.long
            if (index !in 0 until session.manifest.chunkCount) throw SecurityException("Invalid chunk index")
            val nonce = ByteArray(12).also(buffer::get)
            val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
            val ledger = session.ledger ?: throw IllegalStateException("Resume ledger missing")
            val legacyCount = withContext(Dispatchers.IO) {
                if (!ledger.hasReceived(index)) {
                    val part = session.destination ?: throw IllegalStateException("Destination missing")
                    NativeP2pTransferProtocol.writeChunk(
                        part, session.manifest, session.secret,
                        NativeP2pTransferProtocol.EncryptedChunk(index, nonce, encrypted),
                    )
                    session.chunksSinceSync++
                    val willComplete = ledger.receivedCount() + 1L >= session.manifest.chunkCount
                    if (session.chunksSinceSync >= 16 || willComplete) {
                        NativeP2pTransferProtocol.syncFile(part)
                        session.chunksSinceSync = 0
                    }
                    ledger.markReceived(index)
                }
                ledger.receivedCount()
            }
                legacyCount
            }
            session.receivedBytes = count * session.manifest.chunkBytes
            session.receivedBytes = minOf(session.receivedBytes, session.manifest.sizeBytes)
            session.monitorId?.let { transferMonitor.update(it, session.receivedBytes, session.manifest.sizeBytes) }
            publish(session, "Receiving")
        } catch (error: Throwable) {
            fail(session, error.message ?: "File chunk failed verification")
        }
    }

    private suspend fun finalizeReceiver(session: Session) {
        if (session.receiverFinalized) return
        session.receiverFinalized = true
        val part = session.destination ?: return
        val target = session.finalDestination ?: return
        if (!withContext(Dispatchers.IO) { NativeP2pTransferProtocol.verifyCompletedFile(part, session.manifest) }) {
            fail(session, "File integrity check failed; received file was discarded")
            part.delete()
            session.ledger?.clear()
            return
        }
        if (target.exists()) throw IllegalStateException("A file with this name already exists")
        target.parentFile?.mkdirs()
        if (!part.renameTo(target)) throw IllegalStateException("Cannot finalize received file")
        session.ledger?.flush()
        if (session.directOriginal) {
            val verified = JSONObject().put("local_user_id", session.localUserId).put("peer_user_id", session.peerUserId)
                .put("path", target.absolutePath).put("size", session.manifest.sizeBytes).put("sha256", session.manifest.sha256Hex)
            if (!resumePrefs.edit().putString("received_" + session.id, verified.toString()).commit()) {
                target.renameTo(part)
                throw IllegalStateException("Could not save verified file receipt; retry this transfer")
            }
        }
        sendControl(session, JSONObject().put("kind", "complete"))
        if (session.directOriginal) resumePrefs.edit().remove("receiver_" + session.id).commit()
        session.monitorId?.let { transferMonitor.finish(it) }
        publish(session, "Received and verified", target.absolutePath)
        session.secret.fill(0)
        session.channel?.close()
        session.peer?.close()
        sessions.remove(session.id)
    }

    private fun completeSender(session: Session) {
        clearSenderResume(session.id)
        session.monitorId?.let { transferMonitor.finish(it) }
        publish(session, "Sent")
        session.channel?.close()
        session.peer?.close()
        runCatching { (session.sourceOwner ?: session.sourceChannel)?.close() }
        session.secret.fill(0)
        sessions.remove(session.id)
    }

    /** Local encrypted ticket cache avoids replaying an already-consumed MLS application message. */
    fun savedDirectTicket(transferId: String, peerUserId: String): String? {
        val raw = resumePrefs.getString("ticket_" + transferId, null) ?: return null
        val record = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (record.optString("local_user_id") != currentUserId() || record.optString("peer_user_id") != peerUserId) return null
        return record.optString("link").takeIf { it.isNotBlank() }
    }
    fun saveDirectTicket(link: String, peerUserId: String) {
        val invite = NativeP2pTransferProtocol.parseInvite(link)
        try {
            val localId = currentUserId() ?: throw SecurityException("Sign in to save the Direct ticket")
            val record = JSONObject().put("local_user_id", localId).put("peer_user_id", peerUserId).put("link", link)
            check(resumePrefs.edit().putString("ticket_" + invite.transferId, record.toString()).commit()) { "Could not save Direct receive ticket" }
        } finally { invite.secret.fill(0) }
    }

    suspend fun verifiedOriginalFile(transferId: String, peerUserId: String): File? = withContext(Dispatchers.IO) {
        val raw = resumePrefs.getString("received_" + transferId, null) ?: return@withContext null
        val record = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext null
        if (record.optString("local_user_id") != currentUserId() || record.optString("peer_user_id") != peerUserId) return@withContext null
        val file = File(record.optString("path"))
        val directory = File(appContext.filesDir, "p2p-received").canonicalFile
        val safe = file.canonicalFile
        if (directory.parentFile != appContext.filesDir.canonicalFile || directory.name != "p2p-received" ||
            safe.parentFile != directory || safe != file.absoluteFile || !safe.isFile || safe.length() != record.optLong("size")) return@withContext null
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        safe.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; if (n > 0) digest.update(buffer, 0, n) }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (hash != record.optString("sha256")) null else safe
    }

    fun disposeAll() {
        sessions.values.toList().forEach { session ->
            // Never delete a user-selected source file; discard only private partial receive data on account change.
            session.destination?.let { runCatching { it.delete() } }
            session.ledger?.let { runCatching { it.clear() } }
            fail(session, "Account signed out")
        }
        incoming.clear()
        incomingCandidates.clear()
        scope.launch(Dispatchers.IO) { runCatching { resumePrefs.edit().clear().commit() } }
        _pendingOffers.value = emptyList()
    }

    private suspend fun createDescription(peer: PeerConnection, offer: Boolean, iceRestart: Boolean = false): SessionDescription =
            suspendCancellableCoroutine { continuation ->
                val observer = object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription?) {
                        if (description == null) continuation.resumeWithException(IllegalStateException("SDP missing"))
                        else peer.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() { continuation.resume(description) }
                            override fun onSetFailure(error: String?) { continuation.resumeWithException(IllegalStateException(error ?: "setLocalDescription failed")) }
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(error: String?) {}
                        }, description)
                    }
                    override fun onCreateFailure(error: String?) { continuation.resumeWithException(IllegalStateException(error ?: "SDP create failed")) }
                    override fun onSetSuccess() {}
                    override fun onSetFailure(error: String?) {}
                }
                val constraints = MediaConstraints().apply {
                    if (iceRestart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                }
                if (offer) peer.createOffer(observer, constraints) else peer.createAnswer(observer, constraints)
            }

    private suspend fun setRemoteDescription(peer: PeerConnection, type: SessionDescription.Type, sdp: String) {
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            peer.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() { continuation.resume(Unit) }
                override fun onSetFailure(error: String?) { continuation.resumeWithException(IllegalStateException(error ?: "setRemoteDescription failed")) }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(error: String?) {}
            }, SessionDescription(type, sdp))
        }
    }

    private fun requestIceRestart(session: Session) {
        if (session.direction == TransferDirection.upload) {
            restartIce(session)
        } else if (!session.restartInProgress && session.restartAttempts < MAX_ICE_RESTARTS) {
            session.restartAttempts++
            session.remoteDescriptionSet = false
            sendSignalScoped(session, mapOf(
                "signal_type" to "file_transfer_restart_request",
                "call_id" to session.id,
                "transfer_id" to session.id,
                "request_nonce" to java.util.UUID.randomUUID().toString(),
            ))
            publish(session, "Waiting for sender to reconnect")
        } else if (session.restartAttempts >= MAX_ICE_RESTARTS) {
            publish(session, "Connection could not recover", error = "Tap Retry direct transfer when your connection is stable.")
        }
    }

    private fun restartIce(session: Session) {
        if (session.restartInProgress || session.restartAttempts >= MAX_ICE_RESTARTS) {
            if (session.restartAttempts >= MAX_ICE_RESTARTS) publish(session, "Connection could not recover", error = "Retry the transfer from the chat.")
            return
        }
        session.restartInProgress = true
        session.restartAttempts++
        scope.launch {
            try {
                val peer = session.peer ?: throw IllegalStateException("Transfer peer is unavailable")
                session.remoteDescriptionSet = false
                session.signalingReady = false
                val offer = createDescription(peer, offer = true, iceRestart = true)
                val manifest = session.manifest
                val auth = NativeP2pTransferProtocol.authenticateManifest(manifest, session.secret)
                val payload = mapOf(
                    "signal_type" to "file_transfer_offer",
                    "call_id" to session.id,
                    "transfer_id" to session.id,
                    "sdp" to offer.description,
                    "manifest" to mapOf(
                        "transfer_id" to manifest.transferId, "name" to manifest.displayName,
                        "mime" to manifest.mimeType, "size" to manifest.sizeBytes,
                        "chunk" to manifest.chunkBytes, "sha256" to manifest.sha256Hex,
                        "nonce" to b64(manifest.noncePrefix), "expires" to manifest.expiresAtEpochSeconds,
                    ),
                    "manifest_auth" to b64(auth),
                )
                sendSignalScoped(session, payload)
                session.signalingReady = true
                flushLocalCandidates(session)
                publish(session, "Reconnecting directly…")
            } catch (error: Throwable) {
                if (session.restartAttempts >= MAX_ICE_RESTARTS) fail(session, error.message ?: "Unable to reconnect")
            } finally {
                session.restartInProgress = false
            }
        }
    }

    private fun flushCandidates(session: Session) {
        val peer = session.peer ?: return
        session.queuedCandidates.toList().filter { !session.directOriginal || !ChatyDirectProtocol.isRelayCandidate(it.sdp) }
            .forEach(peer::addIceCandidate)
        session.queuedCandidates.clear()
    }

    private fun flushLocalCandidates(session: Session) {
        val pending = session.pendingLocalCandidates.toList()
        session.pendingLocalCandidates.clear()
        pending.forEach { sendCandidate(session, it) }
    }

    private fun sendSignalScoped(session: Session, data: Map<String, Any?>): Boolean {
        if (currentUserId() != session.localUserId) return false
        return sendSignal(session.peerUserId, data)
    }

    /** Pick a smaller authenticated frame on weak uplinks. The manifest locks this
     *  value for the life of a transfer, so reconnects cannot change chunk geometry. */
    private fun preferredChunkBytes(): Int {
        val upstream = networkStatus()?.upstreamKbps ?: 0
        return when {
            upstream in 1..128 -> 16 * 1024
            upstream in 129..400 -> 24 * 1024
            upstream in 401..900 -> 32 * 1024
            else -> NativeP2pTransferProtocol.DEFAULT_CHUNK_BYTES
        }
    }

    /** Small request windows prevent a weak receiver from building a large queue. */
    private fun maxRequestChunks(): Int {
        val downstream = networkStatus()?.downstreamKbps ?: 0
        return when {
            downstream in 1..128 -> 1
            downstream in 129..300 -> 2
            downstream in 301..700 -> 4
            downstream in 701..1_500 -> 8
            else -> MAX_REQUEST_CHUNKS
        }
    }

    /** Bound DataChannel buffering to roughly one/two chunks on weak cellular links. */
    private fun maxBufferedBytes(): Long {
        val upstream = networkStatus()?.upstreamKbps ?: 0
        return when {
            upstream in 1..128 -> 64L * 1024L
            upstream in 129..300 -> 128L * 1024L
            upstream in 301..700 -> 384L * 1024L
            upstream in 701..1_500 -> 768L * 1024L
            else -> MAX_BUFFERED_BYTES
        }
    }

    private fun sendCandidate(session: Session, candidate: IceCandidate) {
        sendSignalScoped(session, mapOf("signal_type" to "file_transfer_ice", "call_id" to session.id, "transfer_id" to session.id, "candidate" to candidate.sdp, "sdp_mid" to candidate.sdpMid, "sdp_mline" to candidate.sdpMLineIndex))
    }

    private fun fail(session: Session, reason: String) {
        session.monitorId?.let { transferMonitor.finish(it, TransferStatus.failed) }
        publish(session, if (reason == "Cancelled") "Cancelled" else "Failed", error = reason)
        session.channel?.close()
        session.peer?.close()
        runCatching { (session.sourceOwner ?: session.sourceChannel)?.close() }
        session.secret.fill(0)
        sessions.remove(session.id)
    }

    private fun publish(session: Session, status: String, path: String? = null, error: String? = null) {
        val bytes = if (session.direction == TransferDirection.upload) session.sentBytes else session.receivedBytes
        val item = TransferState(
            session.id, session.peerUserId, session.manifest.displayName, session.manifest.sizeBytes,
            bytes, session.direction, status, error, path,
        )
        _transfers.value = (_transfers.value.filterNot { it.transferId == session.id } + item).takeLast(40)
    }

    private fun senderResumeKey(transferId: String) = "sender_" + transferId

    /** Secrets and the persisted source URI are stored only in Keystore-backed preferences. */
    private fun persistSenderResume(session: Session) {
        val sourceUri = session.sourceUri ?: return
        if (session.direction != TransferDirection.upload) return
        val manifest = session.manifest
        val record = JSONObject()
            .put("transfer_id", session.id)
            .put("local_user_id", session.localUserId)
            .put("peer_user_id", session.peerUserId)
            .put("source_uri", sourceUri)
            .put("direct_original_v2", session.directOriginal)
            .put("display_name", manifest.displayName)
            .put("mime_type", manifest.mimeType)
            .put("size_bytes", manifest.sizeBytes)
            .put("chunk_bytes", manifest.chunkBytes)
            .put("sha256", manifest.sha256Hex)
            .put("nonce_prefix", b64(manifest.noncePrefix))
            .put("expires_at", manifest.expiresAtEpochSeconds)
            .put("secret", b64(session.secret))
        check(resumePrefs.edit().putString(senderResumeKey(session.id), record.toString()).commit()) {
            "Unable to save encrypted transfer resume state."
        }
    }

    private fun loadSenderResumeRecord(transferId: String): JSONObject? {
        val canonical = runCatching { java.util.UUID.fromString(transferId).toString() }.getOrNull()
        if (canonical != transferId) return null
        val raw = resumePrefs.getString(senderResumeKey(transferId), null) ?: return null
        val record = runCatching { JSONObject(raw) }.getOrNull() ?: run {
            clearSenderResume(transferId)
            return null
        }
        if (record.optString("transfer_id") != transferId ||
            record.optString("source_uri").isBlank() || record.optString("source_uri").length > 4096 ||
            record.optString("local_user_id").isBlank() || record.optString("local_user_id").length > 256 ||
            record.optString("peer_user_id").isBlank() || record.optString("peer_user_id").length > 256 ||
            record.optString("display_name").length > 180 ||
            record.optString("mime_type").length > 120 ||
            record.optString("secret").length !in 40..64
        ) {
            clearSenderResume(transferId)
            return null
        }
        return record
    }

    private fun clearSenderResume(transferId: String) {
        runCatching { resumePrefs.edit().remove(senderResumeKey(transferId)).commit() }
    }

    private fun restorePersistedUploadSnapshots() {
        val activeUser = currentUserId() ?: return
        val snapshots = runCatching {
            resumePrefs.all.entries.mapNotNull { (_, value) ->
                if (value !is String) return@mapNotNull null
                val record = runCatching { JSONObject(value) }.getOrNull() ?: return@mapNotNull null
                if (record.optString("local_user_id") != activeUser) return@mapNotNull null
                val id = record.optString("transfer_id")
                val size = record.optLong("size_bytes", 0L)
                if (id.isBlank() || size <= 0L) return@mapNotNull null
                TransferState(
                    transferId = id,
                    peerUserId = record.optString("peer_user_id"),
                    fileName = record.optString("display_name", "Large file"),
                    totalBytes = size,
                    transferredBytes = 0L,
                    direction = TransferDirection.upload,
                    status = "Paused — source ready to resume",
                    error = "Retry direct transfer to reconnect using the saved file permission.",
                )
            }
        }.getOrDefault(emptyList())
        if (snapshots.isNotEmpty()) {
            _transfers.value = (_transfers.value.filterNot { old -> snapshots.any { it.transferId == old.transferId } } + snapshots).takeLast(40)
        }
    }

    private suspend fun restoreSenderSession(transferId: String, expectedPeerId: String?): Boolean {
        if (sessions.containsKey(transferId)) return true
        if (!restoringUploads.add(transferId)) return false
        try {
            val record = withContext(Dispatchers.IO) { loadSenderResumeRecord(transferId) } ?: return false
            val localId = record.optString("local_user_id")
            val peerId = record.optString("peer_user_id")
            if (currentUserId() != localId || peerId.isBlank() ||
                (!expectedPeerId.isNullOrBlank() && peerId != expectedPeerId)
            ) throw SecurityException("Saved transfer belongs to a different account or recipient.")

            val secret = unb64(record.getString("secret"))
            if (secret.size != 32) {
                secret.fill(0)
                throw SecurityException("Saved transfer secret is invalid.")
            }
            try {
                val manifest = NativeP2pTransferProtocol.Manifest(
                    transferId = transferId,
                    displayName = record.getString("display_name"),
                    mimeType = record.getString("mime_type"),
                    sizeBytes = record.getLong("size_bytes"),
                    chunkBytes = record.getInt("chunk_bytes"),
                    sha256Hex = record.getString("sha256"),
                    noncePrefix = unb64(record.getString("nonce_prefix")),
                    expiresAtEpochSeconds = record.getLong("expires_at"),
                )
                if (manifest.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L) {
                    clearSenderResume(transferId)
                    throw SecurityException("This transfer link has expired.")
                }
                NativeP2pTransferProtocol.verifyManifest(
                    manifest,
                    NativeP2pTransferProtocol.authenticateManifest(manifest, secret),
                    secret,
                )
                val sourceUriText = record.getString("source_uri")
                val (channel, owner) = withContext(Dispatchers.IO) { openPersistedSource(sourceUriText) }
                val invite = NativeP2pTransferProtocol.Invite(transferId, secret)
                val verified = try {
                    withContext(Dispatchers.IO) {
                        NativeP2pTransferProtocol.createManifest(
                            invite, channel, manifest.displayName, manifest.sizeBytes, manifest.mimeType,
                            manifest.expiresAtEpochSeconds, manifest.chunkBytes,
                        )
                    }
                } catch (error: Throwable) {
                    runCatching { owner.close() }
                    throw error
                }
                if (verified.sha256Hex != manifest.sha256Hex) {
                    runCatching { owner.close() }
                    throw SecurityException("The saved source file has changed; select the original file again.")
                }
                val session = Session(
                    transferId, peerId, localId, null, manifest, secret.copyOf(),
                    TransferDirection.upload, directOriginal = record.optBoolean("direct_original_v2", false), sourceChannel = channel, sourceOwner = owner, sourceUri = sourceUriText,
                )
                val previous = sessions.putIfAbsent(transferId, session)
                if (previous != null) {
                    runCatching { owner.close() }
                    session.secret.fill(0)
                    return true
                }
                val kind = when {
                    manifest.mimeType.startsWith("video/") -> TransferKind.video
                    manifest.mimeType.startsWith("image/") -> TransferKind.image
                    manifest.mimeType.startsWith("audio/") -> TransferKind.audio
                    else -> TransferKind.document
                }
                session.monitorId = transferMonitor.begin(
                    TransferDirection.upload, kind, manifest.sizeBytes, manifest.displayName,
                )
                publish(session, "Reconnecting sender")
                try {
                    startRestoredSenderOffer(session)
                } catch (error: Throwable) {
                    fail(session, error.message ?: "Could not restore sender transfer")
                    throw error
                }
                return true
            } finally {
                secret.fill(0)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            android.util.Log.w("ChatyP2P", "Unable to restore sender transfer $transferId", error)
            _transfers.value = _transfers.value.map { item ->
                if (item.transferId == transferId) item.copy(
                    status = "Resume unavailable",
                    error = error.message ?: "Could not restore the original file source.",
                ) else item
            }
            return false
        } finally {
            restoringUploads.remove(transferId)
        }
    }

    private fun openPersistedSource(sourceUriText: String): Pair<FileChannel, java.io.Closeable> {
        val uri = Uri.parse(sourceUriText)
        return when (uri.scheme) {
            "content" -> {
                val descriptor = appContext.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw SecurityException("Saved file permission is no longer available; select the file again.")
                val owner = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                owner.channel to owner
            }
            "file" -> {
                val file = File(uri.path ?: throw SecurityException("Saved source path is invalid."))
                if (!file.isFile || !file.canRead()) throw SecurityException("Saved source file is no longer readable.")
                val owner = file.inputStream()
                owner.channel to owner
            }
            else -> throw SecurityException("Saved transfer source is not a supported file URI.")
        }
    }

    private suspend fun startRestoredSenderOffer(session: Session) {
        val peer = createPeer(session)
        val channel = peer.createDataChannel(if (session.directOriginal) ChatyDirectProtocol.CHANNEL else "chaty-file-v1", DataChannel.Init().apply { ordered = true })
            ?: throw IllegalStateException("Could not recreate transfer channel")
        session.channel = channel
        observeChannel(session, channel, isSender = true)
        val offer = createDescription(peer, offer = true)
        val manifest = session.manifest
        val payload = mapOf(
            "signal_type" to "file_transfer_offer",
            "call_id" to session.id,
            "transfer_id" to session.id,
            "sdp" to offer.description,
            "manifest" to mapOf(
                "transfer_id" to manifest.transferId, "name" to manifest.displayName,
                "mime" to manifest.mimeType, "size" to manifest.sizeBytes,
                "chunk" to manifest.chunkBytes, "sha256" to manifest.sha256Hex,
                "nonce" to b64(manifest.noncePrefix), "expires" to manifest.expiresAtEpochSeconds,
            ),
            "manifest_auth" to b64(NativeP2pTransferProtocol.authenticateManifest(manifest, session.secret)),
        )
        if (!sendSignalScoped(session, payload)) throw IllegalStateException("Chaty signaling is offline; retry when connected")
        session.signalingReady = true
        flushLocalCandidates(session)
        publish(session, "Waiting for recipient — resuming")
    }

    private fun senderIdInvalid(value: String?): Boolean = value.isNullOrBlank() || value.length > 256

    private fun refreshPending() {
        _pendingOffers.value = incoming.values.map { it.first }.sortedByDescending { it.expiresAtEpochSeconds }
    }

    companion object {
        private const val MAX_BUFFERED_BYTES = 2L * 1024L * 1024L
        private const val MAX_CONTROL_FRAME_BYTES = 16 * 1024
        private const val MAX_PENDING_OFFERS = 20
        private const val MAX_REQUEST_CHUNKS = 16
        private const val SLOW_LINK_BATCH_WAIT_ITERATIONS = 4_800
        private const val MAX_ICE_RESTARTS = 5
        private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        private fun unb64(value: String) = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        fun parseIceServers(raw: List<Map<String, Any?>>): List<PeerConnection.IceServer> {
            return raw.mapNotNull { entry ->
                val urls = when (val value = entry["urls"]) {
                    is String -> listOf(value)
                    is List<*> -> value.mapNotNull { it?.toString() }
                    else -> emptyList()
                }
                if (urls.isEmpty()) return@mapNotNull null
                val builder = PeerConnection.IceServer.builder(urls)
                val username = entry["username"]?.toString()
                val credential = entry["credential"]?.toString()
                if (!username.isNullOrBlank() && credential != null) {
                    builder.setUsername(username)
                    builder.setPassword(credential)
                }
                builder.createIceServer()
            }
        }

        fun defaultIceServers(): List<PeerConnection.IceServer> = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
    }
}
