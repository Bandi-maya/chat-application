package com.example.chat.ui.camera

import java.io.File

/** Only paths explicitly created by this capture entry may be registered. Never register an imported source. */
class CameraOwnedFiles : AutoCloseable {
    private val files = mutableSetOf<File>()
    private var closed = false
    @Synchronized fun own(file: File): Boolean {
        if (closed) { file.delete(); return false }
        files.add(file); return true
    }
    @Synchronized fun isOpen(): Boolean = !closed
    @Synchronized fun discard() { files.forEach { it.delete() }; files.clear() }
    /** Transfer only the confirmed final file. The receiver must retain until its retry/durable policy allows release. */
    @Synchronized fun transfer(path: String): CameraOwnedResult? {
        val file = File(path)
        if (closed || !files.remove(file) || !file.isFile) return null
        discard()
        return CameraOwnedResult(file)
    }
    @Synchronized override fun close() { closed = true; discard() }
}

/** Ownership, not delivery confirmation. close only after bytes are no longer needed by send/retry. */
class CameraOwnedResult internal constructor(val file: File) : AutoCloseable {
    @Synchronized override fun close() { file.delete() }
}

/** Story consumes detached bytes, never the local file. Whether accepted or stale,
 * this handoff owns cleanup; cancellation must remain cancellation. */
suspend fun handoffCameraPhotoBytes(
    result: CameraOwnedResult?,
    readBytes: suspend () -> ByteArray,
    accept: (ByteArray) -> Boolean,
): Boolean = try {
    val bytes = readBytes()
    kotlin.coroutines.coroutineContext.ensureActiveForCameraHandoff()
    accept(bytes)
} finally {
    result?.close()
}

private fun kotlin.coroutines.CoroutineContext.ensureActiveForCameraHandoff() {
    this[kotlinx.coroutines.Job]?.let { if (!it.isActive) throw kotlinx.coroutines.CancellationException("Story capture cancelled") }
}
