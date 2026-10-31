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
    private var dirtyCount = 0
    private val lock = Any()

    init {
        require(chunkCount in 1..Int.MAX_VALUE.toLong()) { "Unsupported chunk count" }
    }

    private var received = load()

    fun markReceived(index: Long) = synchronized(lock) {
        require(index in 0 until chunkCount)
        if (!received[index.toInt()]) {
            received.set(index.toInt())
            dirtyCount++
            if (dirtyCount >= CHECKPOINT_EVERY_CHUNKS || isCompleteLocked()) persist()
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

    /** Force a durable checkpoint when pausing or leaving the transfer screen. */
    fun flush() = synchronized(lock) { if (dirtyCount > 0) persist() }

    fun clear() = synchronized(lock) {
        received.clear()
        dirtyCount = 0
        runCatching { atomic.delete() }
    }

    private fun load(): BitSet {
        return try {
            // AtomicFile.openRead() restores its backup if the process died mid-commit.
            val bytes = atomic.openRead().use { it.readBytes() }
            val maxBytes = ((chunkCount + 7L) / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (bytes.size > maxBytes) throw IOException("Receipt bitmap is larger than expected")
            BitSet.valueOf(bytes).also {
                if (it.length().toLong() > chunkCount) throw IOException("Receipt bitmap exceeds transfer geometry")
            }
        } catch (_: java.io.FileNotFoundException) {
            BitSet()
        } catch (_: Exception) {
            runCatching { atomic.delete() }
            BitSet()
        }
    }

    private fun isCompleteLocked(): Boolean = received.nextClearBit(0).toLong() >= chunkCount

    private fun persist() {
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(received.toByteArray())
            stream.fd.sync()
            atomic.finishWrite(stream)
            dirtyCount = 0
        } catch (error: Exception) {
            if (stream != null) atomic.failWrite(stream)
            throw IOException("Unable to persist transfer resume state", error)
        }
    }

    private companion object { const val CHECKPOINT_EVERY_CHUNKS = 16 }
}
