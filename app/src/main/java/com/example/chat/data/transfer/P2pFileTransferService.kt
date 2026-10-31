package com.example.chat.data.transfer

import android.content.Context
import android.util.Base64
import com.example.chat.data.network.TransferDirection
import com.example.chat.data.network.TransferKind
import com.example.chat.data.network.TransferMonitor
import com.example.chat.data.network.TransferStatus
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
                return WireManifest(
                    j["transfer_id"]?.toString() ?: throw SecurityException("Transfer id missing"),
                    j["name"]?.toString() ?: "video",
                    j["mime"]?.toString() ?: "application/octet-stream",
                    (j["size"] as? Number)?.toLong() ?: j["size"]?.toString()?.toLongOrNull() ?: 0L,
                    (j["chunk"] as? Number)?.toInt() ?: j["chunk"]?.toString()?.toIntOrNull() ?: 0,
                    j["sha256"]?.toString() ?: "",
                    unb64(j["nonce"]?.toString() ?: ""),
                    (j["expires"] as? Number)?.toLong() ?: j["expires"]?.toString()?.toLongOrNull() ?: 0L,
                )
            }
        }
    }

    private data class Session(
        val id: String,
        val peerUserId: String,
        val file: File?,
        val manifest: NativeP2pTransferProtocol.Manifest,
        val secret: ByteArray,
        val direction: TransferDirection,
        var sourceChannel: FileChannel? = null,
        var sourceOwner: java.io.Closeable? = null,
        var peer: PeerConnection? = null,
        var channel: DataChannel? = null,
        var ledger: ChunkReceiptLedger? = null,
        var destination: File? = null,
        var finalDestination: File? = null,
        var monitorId: String? = null,
        val sentChunkIndices: java.util.BitSet = java.util.BitSet(),
        var sentBytes: Long = 0,
        var receivedBytes: Long = 0,
        var receiverFinalized: Boolean = false,
        var restartAttempts: Int = 0,
        var restartInProgress: Boolean = false,
        var remoteDescriptionSet: Boolean = false,
        var signalingReady: Boolean = false,
        val queuedCandidates: MutableList<IceCandidate> = mutableListOf(),
        val pendingLocalCandidates: MutableList<IceCandidate> = mutableListOf(),
    )

    private val appContext = context.applicationContext
    private val factory: PeerConnectionFactory
    private val sessions = ConcurrentHashMap<String, Session>()
    private val incoming = ConcurrentHashMap<String, Pair<PendingOffer, Map<String, Any?>>>()
    private val incomingCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val _pendingOffers = MutableStateFlow<List<PendingOffer>>(emptyList())
    val pendingOffers: StateFlow<List<PendingOffer>> = _pendingOffers.asStateFlow()
    private val _transfers = MutableStateFlow<List<TransferState>>(emptyList())
    val transfers: StateFlow<List<TransferState>> = _transfers.asStateFlow()

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).setEnableInternalTracer(false).createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    /** Creates the P2P offer and returns a link whose fragment is secret material. */
    suspend fun offerFile(
        targetUserId: String,
        file: File,
        mimeType: String,
        linkExpirySeconds: Long = 7L * 24L * 60L * 60L,
    ): String = withContext(Dispatchers.IO) {
        require(targetUserId.isNotBlank() && targetUserId != currentUserId()) { "Choose a valid recipient" }
        val invite = NativeP2pTransferProtocol.createInvite()
        val manifest = NativeP2pTransferProtocol.createManifest(
            invite, file, mimeType, System.currentTimeMillis() / 1000L + linkExpirySeconds,
        )
        val auth = NativeP2pTransferProtocol.authenticateManifest(manifest, invite.secret)
        val wireManifest = WireManifest(manifest.transferId, manifest.displayName, manifest.mimeType, manifest.sizeBytes, manifest.chunkBytes, manifest.sha256Hex, manifest.noncePrefix, manifest.expiresAtEpochSeconds)
        val session = Session(invite.transferId, targetUserId, file, manifest, invite.secret.copyOf(),
            TransferDirection.upload)
        sessions[invite.transferId] = session
        val monitorId = transferMonitor.begin(
            TransferDirection.upload, TransferKind.video, manifest.sizeBytes, manifest.displayName,
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
            if (!sendSignal(targetUserId, mapOf(
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
    ): String = withContext(Dispatchers.IO) {
        require(targetUserId.isNotBlank() && targetUserId != currentUserId()) { "Choose a valid recipient" }
        val invite = NativeP2pTransferProtocol.createInvite()
        val manifest = NativeP2pTransferProtocol.createManifest(
            invite, source, displayName, sizeBytes, mimeType,
            System.currentTimeMillis() / 1000L + linkExpirySeconds,
        )
        val auth = NativeP2pTransferProtocol.authenticateManifest(manifest, invite.secret)
        val wireManifest = WireManifest(manifest.transferId, manifest.displayName, manifest.mimeType, manifest.sizeBytes,
            manifest.chunkBytes, manifest.sha256Hex, manifest.noncePrefix, manifest.expiresAtEpochSeconds)
        val session = Session(invite.transferId, targetUserId, null, manifest, invite.secret.copyOf(),
            TransferDirection.upload, sourceChannel = source, sourceOwner = sourceOwner)
        sessions[invite.transferId] = session
        val kind = if (mimeType.startsWith("video/")) TransferKind.video else TransferKind.document
        session.monitorId = transferMonitor.begin(TransferDirection.upload, kind, manifest.sizeBytes, manifest.displayName)
        publish(session, "Waiting for recipient")
        try {
            val peer = createPeer(session)
            val channel = peer.createDataChannel("chaty-file-v1", DataChannel.Init().apply { ordered = true })
                ?: throw IllegalStateException("Could not create transfer channel")
            session.channel = channel
            observeChannel(session, channel, isSender = true)
            val offer = createDescription(peer, offer = true)
            if (!sendSignal(targetUserId, mapOf(
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
            invite.toUri()
        } catch (error: Throwable) {
            fail(session, error.message ?: "Unable to start transfer")
            throw error
        }
    }

    /** Called for scoped call_signal events. Unknown call events are ignored. */
    suspend fun handleRemoteSignal(signal: Map<String, Any?>) {
        val kind = signal["signal_type"]?.toString() ?: return
        if (!kind.startsWith("file_transfer_")) return
        val id = signal["transfer_id"]?.toString() ?: return
        val sender = (signal["sender_id"] ?: signal["caller_id"])?.toString() ?: return
        if (sender.isBlank() || sender == currentUserId()) return
        when (kind) {
            "file_transfer_offer" -> {
                val manifest = WireManifest.parse(signal["manifest"])
                if (manifest.transferId != id || manifest.sizeBytes !in 1..NativeP2pTransferProtocol.MAX_FILE_BYTES) return
                if (signal["sdp"]?.toString().orEmpty().length !in 1..256_000) return
                if (manifest.displayName.length > 180 || manifest.mimeType.length > 120) return
                val offer = PendingOffer(id, sender, manifest.displayName, manifest.sizeBytes, manifest.mimeType, manifest.expiresAtEpochSeconds)
                val activeReceiver = sessions[id]?.takeIf { it.peerUserId == sender && it.direction == TransferDirection.download }
                if (activeReceiver != null) {
                    val peer = activeReceiver.peer ?: return
                    NativeP2pTransferProtocol.verifyManifest(
                        activeReceiver.manifest,
                        unb64(signal["manifest_auth"]?.toString() ?: ""),
                        activeReceiver.secret,
                    )
                    activeReceiver.remoteDescriptionSet = false
                    setRemoteDescription(peer, SessionDescription.Type.OFFER, signal["sdp"]?.toString() ?: return)
                    activeReceiver.remoteDescriptionSet = true
                    flushCandidates(activeReceiver)
                    val answer = createDescription(peer, offer = false)
                    if (sendSignal(sender, mapOf("signal_type" to "file_transfer_answer", "call_id" to id, "transfer_id" to id, "sdp" to answer.description))) {
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
                if (sdp.isNotBlank()) {
                    setRemoteDescription(session.peer ?: return, SessionDescription.Type.ANSWER, sdp)
                    session.remoteDescriptionSet = true
                }
                flushCandidates(session)
                session.restartAttempts = 0
                publish(session, "Connecting")
            }
            "file_transfer_ice" -> {
                val candidate = signal["candidate"]?.toString().orEmpty()
                if (candidate.isBlank()) return
                val ice = IceCandidate(signal["sdp_mid"]?.toString(), (signal["sdp_mline"] as? Number)?.toInt() ?: 0, candidate)
                val session = sessions[id]?.takeIf { it.peerUserId == sender }
                if (session == null) {
                    val queue = incomingCandidates.getOrPut(id) { java.util.Collections.synchronizedList(mutableListOf()) }
                    if (queue.size < 256) queue.add(ice)
                } else if (!session.remoteDescriptionSet) session.queuedCandidates.add(ice)
                else session.peer?.addIceCandidate(ice)
            }
            "file_transfer_restart_request" -> {
                sessions[id]?.takeIf { it.peerUserId == sender && it.direction == TransferDirection.upload }?.let { restartIce(it) }
            }
            "file_transfer_cancel" -> {
                sessions[id]?.takeIf { it.peerUserId == sender }?.let { fail(it, "Sender cancelled this transfer") }
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
    suspend fun acceptInvite(link: String, destination: File): Boolean {
        val invite = NativeP2pTransferProtocol.parseInvite(link)
        var pending = incoming[invite.transferId]
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
            incoming.remove(invite.transferId); refreshPending()
            throw SecurityException("This transfer link has expired")
        }
        val wire = WireManifest.parse(signal["manifest"])
        val manifest = wire.protocol()
        try {
            NativeP2pTransferProtocol.verifyManifest(
                manifest, unb64(signal["manifest_auth"]?.toString() ?: ""), invite.secret,
            )
        } catch (error: Throwable) {
            invite.secret.fill(0)
            throw error
        }
        destination.parentFile?.mkdirs()
        val availableBytes = android.os.StatFs((destination.parentFile ?: appContext.filesDir).absolutePath).availableBytes
        if (availableBytes < manifest.sizeBytes) {
            invite.secret.fill(0)
            throw IllegalStateException("Not enough free storage to receive this file.")
        }
        val session = Session(invite.transferId, offer.senderId, null, manifest, invite.secret.copyOf(),
            TransferDirection.download, destination = null, finalDestination = destination)
        val partFile = File(destination.parentFile ?: appContext.filesDir, ".${invite.transferId}.part")
        session.destination = partFile
        invite.secret.fill(0)
        session.ledger = ChunkReceiptLedger(File(appContext.filesDir, "p2p-ledger/${invite.transferId}.state"), manifest.chunkCount)
        incomingCandidates.remove(invite.transferId)?.let { session.queuedCandidates.addAll(it) }
        session.monitorId = transferMonitor.begin(
            TransferDirection.download, TransferKind.video, manifest.sizeBytes, manifest.displayName,
        )
        sessions[invite.transferId] = session
        try {
            val peer = createPeer(session)
            setRemoteDescription(peer, SessionDescription.Type.OFFER, signal["sdp"]?.toString() ?: throw SecurityException("Offer missing"))
            session.remoteDescriptionSet = true
            flushCandidates(session)
            val answer = createDescription(peer, offer = false)
            if (!sendSignal(offer.senderId, mapOf(
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

    fun cancel(transferId: String) {
        val session = sessions[transferId] ?: return
        sendSignal(session.peerUserId, mapOf("signal_type" to "file_transfer_cancel", "call_id" to transferId, "transfer_id" to transferId))
        session.file?.let { runCatching { it.delete() } }
        fail(session, "Cancelled")
    }

    private suspend fun createPeer(session: Session): PeerConnection {
        val config = PeerConnection.RTCConfiguration(iceServers().ifEmpty { defaultIceServers() }).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        val peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) sendSignal(session.peerUserId, mapOf(
                    "signal_type" to "file_transfer_ice", "call_id" to session.id, "transfer_id" to session.id,
                    "candidate" to candidate.sdp, "sdp_mid" to candidate.sdpMid,
                    "sdp_mline" to candidate.sdpMLineIndex,
                ))
            }
            override fun onDataChannel(channel: DataChannel?) {
                if (channel != null && session.direction == TransferDirection.download) {
                    session.channel = channel
                    observeChannel(session, channel, isSender = false)
                }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        session.restartAttempts = 0
                        publish(session, "Secure channel connected")
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
        session.peer = peer
        return peer
    }

    private fun observeChannel(session: Session, channel: DataChannel, isSender: Boolean) {
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
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    if (!isSender) scope.launch { receiveChunk(session, bytes) }
                    return
                }
                val text = runCatching { Charsets.UTF_8.decode(buffer.data).toString() }.getOrDefault("")
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (message.optString("kind")) {
                    "request" -> if (isSender) scope.launch { sendRequested(session, message.optJSONArray("indices")) }
                    "complete" -> if (isSender) completeSender(session)
                    "pause" -> publish(session, "Peer paused")
                }
            }
        })
    }

    private suspend fun requestMissing(session: Session) {
        val channel = session.channel ?: return
        val ledger = session.ledger ?: return
        while (channel.state() == DataChannel.State.OPEN && !ledger.isComplete()) {
            val indices = ledger.missingIndices(128)
            if (indices.isEmpty()) break
            val json = JSONObject().put("kind", "request").put("indices", JSONArray().apply { indices.forEach { put(it) } })
            if (!channel.send(DataChannel.Buffer(ByteBuffer.wrap(json.toString().toByteArray()), false))) {
                kotlinx.coroutines.delay(150)
                continue
            }
            // Sender responds in ordered batches. Wait for a batch before requesting more.
            val before = ledger.receivedCount()
            val expectedAfterBatch = before + indices.size
            var waitCount = 0
            while (ledger.receivedCount() < expectedAfterBatch && !ledger.isComplete() && channel.state() == DataChannel.State.OPEN && waitCount < 40) {
                kotlinx.coroutines.delay(250)
                waitCount++
            }
            if (ledger.receivedCount() < expectedAfterBatch) {
                publish(session, "Waiting for sender / network")
                kotlinx.coroutines.delay(1_000)
            }
        }
        if (ledger.isComplete()) finalizeReceiver(session)
    }

    private suspend fun sendRequested(session: Session, array: JSONArray?) {
        val file = session.file
        val sourceChannel = session.sourceChannel
        if (file == null && sourceChannel == null) return
        val channel = session.channel ?: return
        if (channel.state() != DataChannel.State.OPEN) return
        val count = minOf(array?.length() ?: 0, 128)
        for (i in 0 until count) {
            if (channel.state() != DataChannel.State.OPEN) break
            while (channel.bufferedAmount() > MAX_BUFFERED_BYTES && channel.state() == DataChannel.State.OPEN) {
                kotlinx.coroutines.delay(40)
            }
            val index = array?.optLong(i, -1L) ?: -1L
            if (index !in 0 until session.manifest.chunkCount) continue
            val encrypted = withContext(Dispatchers.IO) {
                if (file != null) NativeP2pTransferProtocol.encryptChunk(file, session.manifest, session.secret, index)
                else NativeP2pTransferProtocol.encryptChunk(sourceChannel!!, session.manifest, session.secret, index)
            }
            val packet = ByteBuffer.allocate(8 + encrypted.ciphertextAndTag.size).order(ByteOrder.BIG_ENDIAN)
            packet.putLong(index)
            packet.put(encrypted.ciphertextAndTag)
            packet.flip()
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
    }

    private suspend fun receiveChunk(session: Session, bytes: ByteArray) {
        if (bytes.size < 8 + 16) return
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val index = buffer.long
            if (index !in 0 until session.manifest.chunkCount) throw SecurityException("Invalid chunk index")
            val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
            NativeP2pTransferProtocol.writeChunk(
                session.destination ?: throw IllegalStateException("Destination missing"),
                session.manifest, session.secret,
                NativeP2pTransferProtocol.EncryptedChunk(index, encrypted),
            )
            session.ledger?.markReceived(index)
            session.receivedBytes = (session.ledger?.receivedCount() ?: 0L) * session.manifest.chunkBytes
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
        if (!NativeP2pTransferProtocol.verifyCompletedFile(part, session.manifest)) {
            fail(session, "File integrity check failed; received file was discarded")
            part.delete()
            return
        }
        if (target.exists()) throw IllegalStateException("A file with this name already exists")
        target.parentFile?.mkdirs()
        if (!part.renameTo(target)) throw IllegalStateException("Cannot finalize received file")
        session.ledger?.flush()
        session.channel?.send(DataChannel.Buffer(ByteBuffer.wrap(JSONObject().put("kind", "complete").toString().toByteArray()), false))
        session.monitorId?.let { transferMonitor.finish(it) }
        publish(session, "Received and verified", target.absolutePath)
        session.secret.fill(0)
        session.channel?.close()
        session.peer?.close()
        sessions.remove(session.id)
    }

    private fun completeSender(session: Session) {
        session.monitorId?.let { transferMonitor.finish(it) }
        publish(session, "Sent")
        session.channel?.close()
        session.peer?.close()
        session.file?.let { runCatching { it.delete() } }
        runCatching { (session.sourceOwner ?: session.sourceChannel)?.close() }
        session.secret.fill(0)
        sessions.remove(session.id)
    }

    fun disposeAll() {
        sessions.values.toList().forEach { session ->
            session.file?.let { runCatching { it.delete() } }
            fail(session, "Account signed out")
        }
        incoming.clear()
        incomingCandidates.clear()
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
            sendSignal(session.peerUserId, mapOf(
                "signal_type" to "file_transfer_restart_request",
                "call_id" to session.id,
                "transfer_id" to session.id,
            ))
            publish(session, "Waiting for sender to reconnect")
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
                sendSignal(session.peerUserId, payload)
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
        session.queuedCandidates.toList().forEach(peer::addIceCandidate)
        session.queuedCandidates.clear()
    }

    private fun flushLocalCandidates(session: Session) {
        val pending = session.pendingLocalCandidates.toList()
        session.pendingLocalCandidates.clear()
        pending.forEach { sendCandidate(session, it) }
    }

    private fun sendCandidate(session: Session, candidate: IceCandidate) {
        sendSignal(session.peerUserId, mapOf("signal_type" to "file_transfer_ice", "call_id" to session.id, "transfer_id" to session.id, "candidate" to candidate.sdp, "sdp_mid" to candidate.sdpMid, "sdp_mline" to candidate.sdpMLineIndex))
    }

    private fun fail(session: Session, reason: String) {
        session.monitorId?.let { transferMonitor.finish(it, TransferStatus.failed) }
        publish(session, "Failed", error = reason)
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

    private fun refreshPending() {
        _pendingOffers.value = incoming.values.map { it.first }.sortedByDescending { it.expiresAtEpochSeconds }
    }

    companion object {
        private const val MAX_BUFFERED_BYTES = 2L * 1024L * 1024L
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
