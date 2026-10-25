package com.example.chat.ui.media

import java.io.File

/** Exact port of lib/features/media/services/file_sanitization_service.dart.
 *
 *  path_provider's getTemporaryDirectory is supplied as the [tempDirectory]
 *  seam (Android: context.cacheDir).
 */
class FileSanitizationService(
    private val tempDirectory: () -> File,
) {
    companion object {
        const val MAX_SAFE_FILE_SIZE = 100 * 1024 * 1024 // 100 MB
        const val MAX_ARCHIVE_ENTRIES = 5000
    }

    fun validateSafePath(targetDirectory: String, requestedFilePath: String): Boolean {
        val canonicalTarget = File(targetDirectory).canonicalPath
        val canonicalFile = File(requestedFilePath).canonicalPath
        return canonicalFile.startsWith(canonicalTarget)
    }

    fun sanitizeFileName(inputName: String): String {
        val sanitized = inputName
            .replace(Regex("[\\\\/:*?\"<>|\u0000-\u001F]"), "_")
            .trim()
        if (sanitized.isEmpty()) return "unnamed_file"
        return if (sanitized.length > 120) sanitized.substring(0, 120) else sanitized
    }

    suspend fun getScopedTempDirectory(): File {
        val mediaTemp = File(tempDirectory(), "chaty_media")
        if (!mediaTemp.exists()) {
            mediaTemp.mkdirs()
        }
        return mediaTemp
    }

    suspend fun cleanExpiredTempFiles(maxAgeMs: Long = 24 * 60 * 60 * 1000) {
        try {
            val dir = getScopedTempDirectory()
            val now = System.currentTimeMillis()
            dir.listFiles()?.forEach { entity ->
                if (entity.isFile) {
                    if (now - entity.lastModified() > maxAgeMs) {
                        entity.delete()
                    }
                }
            }
        } catch (_: Exception) {
            // Safe ignore
        }
    }
}
