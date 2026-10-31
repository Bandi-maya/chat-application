package com.example.chat.data.network

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import kotlin.math.max

enum class TransferDirection { upload, download }
enum class TransferKind { message, image, video, audio, document, call, other }
enum class TransferStatus { queued, active, paused, completed, failed, cancelled }

data class TransferItem(
    val id: String,
    val name: String,
    val direction: TransferDirection,
    val kind: TransferKind,
    val totalBytes: Long?,
    val transferredBytes: Long,
    val bytesPerSecond: Long,
    val remainingBytes: Long?,
    val etaSeconds: Long?,
    val status: TransferStatus,
    val updatedAtElapsedMs: Long,
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let {
            (transferredBytes.toDouble() / it).toFloat().coerceIn(0f, 1f)
        }
}

data class DataUsageTotals(
    val uploadedBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val messageBytes: Long = 0,
    val imageBytes: Long = 0,
    val videoBytes: Long = 0,
    val audioBytes: Long = 0,
    val documentBytes: Long = 0,
    val callBytes: Long = 0,
    val otherBytes: Long = 0,
)

/**
 * Process-wide transfer ledger. Progress is measured from actual request/response
 * body bytes; totals are persisted locally and are not inferred from attachment size.
 * It intentionally never stores URLs, message bodies, filenames from remote URLs,
 * auth tokens, or other message content.
 */
class TransferMonitor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chaty_transfer_usage_v1", Context.MODE_PRIVATE)
    private val lock = Any()
    private val active = LinkedHashMap<String, TransferItem>()
    private val _items = MutableStateFlow<List<TransferItem>>(emptyList())
    val items: StateFlow<List<TransferItem>> = _items
    private val _totals = MutableStateFlow(readTotals())
    val totals: StateFlow<DataUsageTotals> = _totals

    fun begin(
        direction: TransferDirection,
        kind: TransferKind,
        totalBytes: Long?,
        name: String = kind.name,
    ): String = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        active[id] = TransferItem(
            id = id,
            name = name.take(80),
            direction = direction,
            kind = kind,
            totalBytes = totalBytes?.takeIf { it >= 0 },
            transferredBytes = 0,
            bytesPerSecond = 0,
            remainingBytes = totalBytes?.takeIf { it >= 0 },
            etaSeconds = null,
            status = TransferStatus.active,
            updatedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
        publish()
        id
    }

    fun update(id: String, transferredBytes: Long, totalBytes: Long? = null) = synchronized(lock) {
        val old = active[id] ?: return
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - old.updatedAtElapsedMs).coerceAtLeast(1L)
        val delta = (transferredBytes - old.transferredBytes).coerceAtLeast(0L)
        val instantRate = delta * 1000L / elapsed
        val rate = if (old.bytesPerSecond == 0L) instantRate else (old.bytesPerSecond * 3L + instantRate) / 4L
        val total = totalBytes ?: old.totalBytes
        val sent = transferredBytes.coerceAtLeast(old.transferredBytes)
        val remaining = total?.let { (it - sent).coerceAtLeast(0L) }
        active[id] = old.copy(
            totalBytes = total,
            transferredBytes = sent,
            bytesPerSecond = rate,
            remainingBytes = remaining,
            etaSeconds = if (remaining != null && rate > 0) remaining / rate else null,
            updatedAtElapsedMs = now,
        )
        publish()
    }

    fun setStatus(id: String, status: TransferStatus) = synchronized(lock) {
        val old = active[id] ?: return
        active[id] = old.copy(status = status, updatedAtElapsedMs = SystemClock.elapsedRealtime())
        publish()
    }

    fun finish(id: String, status: TransferStatus = TransferStatus.completed) = synchronized(lock) {
        val old = active.remove(id) ?: return
        val actual = old.transferredBytes.coerceAtLeast(0L)
        if (status == TransferStatus.completed) {
            val t = _totals.value
            val updated = when (old.direction) {
                TransferDirection.upload -> t.copy(uploadedBytes = t.uploadedBytes + actual)
                TransferDirection.download -> t.copy(downloadedBytes = t.downloadedBytes + actual)
            }.addKind(old.kind, actual)
            _totals.value = updated
            persistTotals(updated)
        }
        // Keep terminal rows briefly in the observable list so the UI can show completion.
        completedRows.add(0, old.copy(
            transferredBytes = if (status == TransferStatus.completed) old.totalBytes ?: actual else actual,
            remainingBytes = if (status == TransferStatus.completed) 0 else old.remainingBytes,
            etaSeconds = if (status == TransferStatus.completed) 0 else old.etaSeconds,
            status = status,
            updatedAtElapsedMs = SystemClock.elapsedRealtime(),
        ))
        while (completedRows.size > 20) completedRows.removeLast()
        publish()
    }

    fun resetTotals() = synchronized(lock) {
        val cleared = DataUsageTotals()
        _totals.value = cleared
        persistTotals(cleared)
    }

    private val completedRows = ArrayList<TransferItem>()

    private fun publish() {
        _items.value = (active.values.toList() + completedRows.toList()).sortedByDescending { it.updatedAtElapsedMs }
    }

    private fun DataUsageTotals.addKind(kind: TransferKind, bytes: Long): DataUsageTotals = when (kind) {
        TransferKind.message -> copy(messageBytes = messageBytes + bytes)
        TransferKind.image -> copy(imageBytes = imageBytes + bytes)
        TransferKind.video -> copy(videoBytes = videoBytes + bytes)
        TransferKind.audio -> copy(audioBytes = audioBytes + bytes)
        TransferKind.document -> copy(documentBytes = documentBytes + bytes)
        TransferKind.call -> copy(callBytes = callBytes + bytes)
        TransferKind.other -> copy(otherBytes = otherBytes + bytes)
    }

    private fun readTotals() = DataUsageTotals(
        uploadedBytes = prefs.getLong("uploaded", 0),
        downloadedBytes = prefs.getLong("downloaded", 0),
        messageBytes = prefs.getLong("message", 0),
        imageBytes = prefs.getLong("image", 0),
        videoBytes = prefs.getLong("video", 0),
        audioBytes = prefs.getLong("audio", 0),
        documentBytes = prefs.getLong("document", 0),
        callBytes = prefs.getLong("call", 0),
        otherBytes = prefs.getLong("other", 0),
    )

    private fun persistTotals(value: DataUsageTotals) {
        prefs.edit()
            .putLong("uploaded", value.uploadedBytes)
            .putLong("downloaded", value.downloadedBytes)
            .putLong("message", value.messageBytes)
            .putLong("image", value.imageBytes)
            .putLong("video", value.videoBytes)
            .putLong("audio", value.audioBytes)
            .putLong("document", value.documentBytes)
            .putLong("call", value.callBytes)
            .putLong("other", value.otherBytes)
            .apply()
    }
}
