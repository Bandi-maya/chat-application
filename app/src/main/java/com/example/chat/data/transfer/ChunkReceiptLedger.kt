package com.example.chat.data.transfer

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.util.BitSet

/**
 * Crash-safe receive bitmap for the native chunk protocol.
 *
 * Store one ledger per transfer in Chaty's private files directory. The part file
 * and this ledger must remain private to the app; never place them in shared storage
 * until the final SHA-256 verification succeeds.
 */
class ChunkReceiptLedger(
    stateFile: File,
    private val chunkCount: Long,
) {
    private val atomic = AtomicFile(stateFile)
    private val lock = Any()
    private var received = load()

    init {
        require(chunkCount in 1..Int.MAX_VALUE.toLong()) { "Unsupported chunk count" }
    }

    fun markReceived(index: Long) = synchronized(lock) {
        require(index in 0 until chunkCount)
        if (!received[index.toInt()]) {
            received.set(index.toInt())
            persist()
        }
    }

    fun hasReceived(index: Long): Boolean = synchronized(lock) {
        index in 0 until chunkCount && received[index.toInt()]
    }

    fun missingIndices(maxItems: Int = 4096): LongArray = synchronized(lock) {
        require(maxItems > 0)
        val result = ArrayList<Long>(minOf(maxItems, 1024))
        var next = received.nextClearBit(0)
        while (next.toLong() < chunkCount && result.size < maxItems) {
            result.add(next.toLong())
            next = received.nextClearBit(next + 1)
        }
        result.toLongArray()
    }

    fun isComplete(): Boolean = synchronized(lock) {
        received.nextClearBit(0).toLong() >= chunkCount
    }

    fun receivedCount(): Long = synchronized(lock) { received.cardinality().toLong() }

    fun clear() = synchronized(lock) {
        received.clear()
        runCatching { atomic.delete() }
    }

    private fun load(): BitSet {
        if (!stateFileExists()) return BitSet()
        return try {
            val bytes = atomic.openRead().use { it.readBytes() }
            BitSet.valueOf(bytes).also {
                if (it.length().toLong() > chunkCount) throw IOException("Receipt bitmap exceeds transfer geometry")
            }
        } catch (_: Exception) {
            runCatching { atomic.delete() }
            BitSet()
        }
    }

    private fun stateFileExists(): Boolean = runCatching {
        atomic.baseFile.exists() || atomic.baseFileBak.exists() || atomic.baseFileNew.exists()
    }.getOrDefault(false)

    private fun persist() {
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(received.toByteArray())
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) atomic.failWrite(stream)
            throw IOException("Unable to persist transfer resume state", error)
        }
    }
}
