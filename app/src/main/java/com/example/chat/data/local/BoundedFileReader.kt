package com.example.chat.data.local

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Reads a small file only when the caller has an explicit in-memory upload
 * route. Checks the on-disk size first and the actual stream size while
 * reading, so a provider/file change cannot bypass the memory ceiling.
 */
object BoundedFileReader {
    const val MAX_IN_MEMORY_UPLOAD_BYTES: Long = 16L * 1024L * 1024L
    private const val BUFFER_BYTES = 64 * 1024

    fun read(file: File, maxBytes: Long = MAX_IN_MEMORY_UPLOAD_BYTES): ByteArray {
        require(maxBytes > 0L) { "Upload memory limit must be positive." }
        require(file.isFile && file.canRead()) { "Selected file is not readable." }
        if (file.length() > maxBytes) {
            throw IllegalStateException("Selected file exceeds the in-memory upload limit.")
        }

        val initialCapacity = minOf(file.length(), maxBytes, BUFFER_BYTES.toLong()).toInt()
        val output = ByteArrayOutputStream(initialCapacity)
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        file.inputStream().buffered(BUFFER_BYTES).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count.toLong()
                if (total > maxBytes) {
                    throw IllegalStateException("Selected file grew beyond the in-memory upload limit.")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }
}
