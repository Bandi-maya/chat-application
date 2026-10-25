package com.example.chat.ui.media

/** Exact port of FileClassification from
 *  lib/features/media/services/file_type_detector.dart. */
data class FileClassification(
    val mimeType: String,
    val category: MediaCategory,
    val extension: String,
    val isSafe: Boolean,
)

/** Exact port of lib/features/media/services/file_type_detector.dart.
 *
 *  classifyBytes is the full magic-byte + extension fallback chain, preserved
 *  branch-for-branch. classifyFile's file read is supplied by the caller as
 *  [headerBytesOf] so the detector stays context-free; on Android pass a
 *  reader over java.io.File (32 bytes, same as Dart).
 *
 *  lookupMimeType ports the extension table of the Dart `mime` package for
 *  the types Chaty media handles; unknown extensions return null exactly
 *  like the package, and the fallback path substitutes
 *  'application/octet-stream' the same way.
 */
object FileTypeDetector {
    const val HEADER_BYTES_TO_READ = 32

    fun classifyFile(headerBytesOf: () -> ByteArray?, filePath: String): FileClassification {
        val bytes = headerBytesOf()
            ?: return FileClassification(
                mimeType = "application/octet-stream",
                category = MediaCategory.other,
                extension = getExtension(filePath),
                isSafe = false,
            )
        return classifyBytes(bytes, fileName = filePath)
    }

    fun classifyBytes(bytes: ByteArray, fileName: String? = null): FileClassification {
        val ext = if (fileName != null) getExtension(fileName) else ""

        // Check Magic Bytes
        if (bytes.size >= 4) {
            // PDF: %PDF- (0x25 0x50 0x44 0x46 0x2D)
            if (bytes.u(0) == 0x25 && bytes.u(1) == 0x50 && bytes.u(2) == 0x44 && bytes.u(3) == 0x46) {
                return FileClassification("application/pdf", MediaCategory.pdf, "pdf", true)
            }

            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if (bytes.u(0) == 0x89 && bytes.u(1) == 0x50 && bytes.u(2) == 0x4E && bytes.u(3) == 0x47) {
                return FileClassification("image/png", MediaCategory.image, "png", true)
            }

            // JPEG: FF D8 FF
            if (bytes.u(0) == 0xFF && bytes.u(1) == 0xD8 && bytes.u(2) == 0xFF) {
                return FileClassification("image/jpeg", MediaCategory.image, "jpg", true)
            }

            // GIF: GIF87a or GIF89a
            if (bytes.u(0) == 0x47 && bytes.u(1) == 0x49 && bytes.u(2) == 0x46 && bytes.u(3) == 0x38) {
                return FileClassification("image/gif", MediaCategory.image, "gif", true)
            }

            // ZIP / DOCX / XLSX / APK: PK  (0x50 0x4B 0x03 0x04)
            if (bytes.u(0) == 0x50 && bytes.u(1) == 0x4B && bytes.u(2) == 0x03 && bytes.u(3) == 0x04) {
                if (ext == "docx" || ext == "xlsx" || ext == "pptx") {
                    return FileClassification(
                        mimeType = lookupMimeType(fileName ?: "")
                            ?: "application/vnd.openxmlformats-officedocument",
                        category = MediaCategory.document,
                        extension = ext,
                        isSafe = true,
                    )
                }
                return FileClassification("application/zip", MediaCategory.archive, "zip", true)
            }

            // MP3: ID3 (0x49 0x44 0x33)
            if (bytes.u(0) == 0x49 && bytes.u(1) == 0x44 && bytes.u(2) == 0x33) {
                return FileClassification("audio/mpeg", MediaCategory.audio, "mp3", true)
            }
        }

        // MP4 / MOV: ftyp at offset 4
        if (bytes.size >= 12 &&
            bytes.u(4) == 0x66 && bytes.u(5) == 0x74 && bytes.u(6) == 0x79 && bytes.u(7) == 0x70
        ) {
            return FileClassification("video/mp4", MediaCategory.video, "mp4", true)
        }

        // WebP: RIFF ... WEBP
        if (bytes.size >= 12 &&
            bytes.u(0) == 0x52 && bytes.u(1) == 0x49 && bytes.u(2) == 0x46 && bytes.u(3) == 0x46 &&
            bytes.u(8) == 0x57 && bytes.u(9) == 0x45 && bytes.u(10) == 0x42 && bytes.u(11) == 0x50
        ) {
            return FileClassification("image/webp", MediaCategory.image, "webp", true)
        }

        // Text and Document extensions fallback
        val mime = (if (fileName != null) lookupMimeType(fileName) else null)
            ?: "application/octet-stream"
        var cat = MediaCategory.other

        if (mime.startsWith("image/")) {
            cat = MediaCategory.image
        } else if (mime.startsWith("video/")) {
            cat = MediaCategory.video
        } else if (mime.startsWith("audio/")) {
            cat = MediaCategory.audio
        } else if (ext == "pdf" || mime == "application/pdf") {
            cat = MediaCategory.pdf
        } else if (ext == "txt" || ext == "md" || ext == "json" || ext == "csv" ||
            ext == "doc" || ext == "docx" || ext == "xls" || ext == "xlsx" ||
            ext == "ppt" || ext == "pptx" || ext == "rtf" || ext == "xml" ||
            mime.startsWith("text/")
        ) {
            cat = MediaCategory.document
        } else if (ext == "zip" || ext == "tar" || ext == "gz" || ext == "rar" || ext == "7z") {
            cat = MediaCategory.archive
        }

        return FileClassification(mime, cat, ext, true)
    }

    fun getExtension(path: String): String {
        val idx = path.lastIndexOf('.')
        if (idx == -1 || idx == path.length - 1) return ""
        return path.substring(idx + 1).lowercase()
    }

    private fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF

    /** Extension subset of the Dart `mime` package's default table covering
     *  every type the fallback chain can reach plus common Chaty media. */
    private val MIME_BY_EXTENSION = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "mp4" to "video/mp4",
        "mov" to "video/quicktime",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "avi" to "video/x-msvideo",
        "3gp" to "video/3gpp",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "wav" to "audio/x-wav",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "opus" to "audio/opus",
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "md" to "text/markdown",
        "json" to "application/json",
        "csv" to "text/csv",
        "xml" to "text/xml",
        "rtf" to "application/rtf",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "zip" to "application/zip",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed",
        "apk" to "application/vnd.android.package-archive",
        "html" to "text/html",
        "htm" to "text/html",
    )

    fun lookupMimeType(path: String): String? = MIME_BY_EXTENSION[getExtension(path)]
}
