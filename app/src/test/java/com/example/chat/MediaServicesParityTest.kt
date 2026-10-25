package com.example.chat

import com.example.chat.ui.media.FileSanitizationService
import com.example.chat.ui.media.FileTypeDetector
import com.example.chat.ui.media.MediaCategory
import com.example.chat.ui.media.MediaFileDescriptor
import com.example.chat.ui.media.MediaLibraryService
import com.example.chat.ui.media.MediaPlaybackState
import com.example.chat.ui.media.PlaybackStatus
import com.example.chat.ui.media.ThumbnailCacheService
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests written from the Dart sources' observable behavior in
 *  the media services and models under lib/features/media/. */
class MediaServicesParityTest {

    private fun bytes(vararg values: Int) =
        values.map { it.toByte() }.toByteArray()

    // ── FileTypeDetector magic bytes ───────────────────────────────────────

    @Test fun `pdf magic bytes`() {
        val c = FileTypeDetector.classifyBytes(bytes(0x25, 0x50, 0x44, 0x46, 0x2D), fileName = "x.bin")
        assertEquals("application/pdf", c.mimeType)
        assertEquals(MediaCategory.pdf, c.category)
        assertEquals("pdf", c.extension)
        assertTrue(c.isSafe)
    }

    @Test fun `png magic bytes`() {
        val c = FileTypeDetector.classifyBytes(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0xA), fileName = "x.bin")
        assertEquals("image/png", c.mimeType)
        assertEquals(MediaCategory.image, c.category)
    }

    @Test fun `jpeg magic bytes`() {
        val c = FileTypeDetector.classifyBytes(bytes(0xFF, 0xD8, 0xFF, 0xE0), fileName = "x.bin")
        assertEquals("image/jpeg", c.mimeType)
        assertEquals("jpg", c.extension)
    }

    @Test fun `gif magic bytes`() {
        val c = FileTypeDetector.classifyBytes(bytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61), fileName = "x.bin")
        assertEquals("image/gif", c.mimeType)
    }

    @Test fun `zip magic for zip extension`() {
        val c = FileTypeDetector.classifyBytes(bytes(0x50, 0x4B, 0x03, 0x04), fileName = "x.zip")
        assertEquals("application/zip", c.mimeType)
        assertEquals(MediaCategory.archive, c.category)
    }

    @Test fun `zip magic for docx routes to document`() {
        val c = FileTypeDetector.classifyBytes(bytes(0x50, 0x4B, 0x03, 0x04), fileName = "report.docx")
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", c.mimeType)
        assertEquals(MediaCategory.document, c.category)
        assertEquals("docx", c.extension)
    }

    @Test fun `mp3 id3 magic`() {
        val c = FileTypeDetector.classifyBytes(bytes(0x49, 0x44, 0x33, 0x04), fileName = "song.bin")
        assertEquals("audio/mpeg", c.mimeType)
        assertEquals(MediaCategory.audio, c.category)
    }

    @Test fun `mp4 ftyp at offset 4`() {
        val b = ByteArray(12)
        b[4] = 0x66; b[5] = 0x74; b[6] = 0x79; b[7] = 0x70
        val c = FileTypeDetector.classifyBytes(b, fileName = "vid.bin")
        assertEquals("video/mp4", c.mimeType)
        assertEquals(MediaCategory.video, c.category)
    }

    @Test fun `webp riff container`() {
        val b = ByteArray(12)
        b[0] = 0x52; b[1] = 0x49; b[2] = 0x46; b[3] = 0x46
        b[8] = 0x57; b[9] = 0x45; b[10] = 0x42; b[11] = 0x50
        val c = FileTypeDetector.classifyBytes(b, fileName = "img.bin")
        assertEquals("image/webp", c.mimeType)
        assertEquals(MediaCategory.image, c.category)
    }

    @Test fun `unknown bytes fall back to extension`() {
        val c = FileTypeDetector.classifyBytes(bytes(0x01, 0x02), fileName = "notes.txt")
        assertEquals("text/plain", c.mimeType)
        assertEquals(MediaCategory.document, c.category)
    }

    @Test fun `unknown everything is octet-stream other`() {
        val c = FileTypeDetector.classifyBytes(bytes(0x01, 0x02), fileName = "blob")
        assertEquals("application/octet-stream", c.mimeType)
        assertEquals(MediaCategory.other, c.category)
    }

    @Test fun `missing file is unsafe`() {
        val c = FileTypeDetector.classifyFile(headerBytesOf = { null }, filePath = "gone.pdf")
        assertFalse(c.isSafe)
        assertEquals(MediaCategory.other, c.category)
        assertEquals("pdf", c.extension)
    }

    @Test fun `extension helper edge cases`() {
        assertEquals("", FileTypeDetector.getExtension("noext"))
        assertEquals("", FileTypeDetector.getExtension("trailing."))
        assertEquals("pdf", FileTypeDetector.getExtension("A.PDF"))
    }

    // ── MediaFileDescriptor ────────────────────────────────────────────────

    private fun descriptor(size: Long, name: String = "f.bin") = MediaFileDescriptor(
        id = name, name = name, path = "/tmp/$name", sizeBytes = size,
        mimeType = "application/octet-stream", category = MediaCategory.other,
        modifiedAt = Instant.ofEpochMilli(0),
    )

    @Test fun `human readable size matches dart thresholds`() {
        assertEquals("512 B", descriptor(512).humanReadableSize)
        assertEquals("1.5 KB", descriptor(1536).humanReadableSize)
        assertEquals("2.0 MB", descriptor(2 * 1024 * 1024).humanReadableSize)
        assertEquals("1.50 GB", descriptor(1610612736L).humanReadableSize)
    }

    @Test fun `descriptor extension`() {
        assertEquals("pdf", descriptor(1, "Report.PDF").extension)
        assertEquals("", descriptor(1, "noext").extension)
        assertEquals("", descriptor(1, "trailing.").extension)
    }

    // ── MediaPlaybackState ─────────────────────────────────────────────────

    @Test fun `playback derived properties`() {
        val item = descriptor(1)
        val s = MediaPlaybackState(
            status = PlaybackStatus.playing,
            position = 30.seconds,
            duration = 120.seconds,
            queue = listOf(item, item, item),
            queueIndex = 1,
        )
        assertTrue(s.isPlaying)
        assertTrue(s.hasActiveItem == false) // no activeItem set
        assertTrue(s.hasNext)
        assertTrue(s.hasPrevious)
        assertEquals(0.25, s.progress, 1e-9)
        assertEquals(0.0, s.copy(duration = 0.seconds).progress, 0.0)
    }

    // ── FileSanitizationService ────────────────────────────────────────────

    private val sanitizer = FileSanitizationService { File(System.getProperty("java.io.tmpdir")) }

    @Test fun `sanitizeFileName replaces illegal chars and trims`() {
        assertEquals("a_b_c", sanitizer.sanitizeFileName("a/b\\c"))
        assertEquals("unnamed_file", sanitizer.sanitizeFileName("   "))
        assertEquals(120, sanitizer.sanitizeFileName("x".repeat(200)).length)
        assertEquals("a_b", sanitizer.sanitizeFileName("a:b"))
        assertEquals("a_b", sanitizer.sanitizeFileName("a|b"))
    }

    // ── ThumbnailCacheService ──────────────────────────────────────────────

    @Test fun `thumbnail cache is 120-entry fifo`() {
        val cache = ThumbnailCacheService()
        for (i in 0 until 130) {
            cache.putThumbnailBytes("f$i", byteArrayOf(i.toByte()))
        }
        assertFalse(cache.hasThumbnail("f0"))
        assertTrue(cache.hasThumbnail("f129"))
        assertEquals(120, (0 until 130).count { cache.hasThumbnail("f$it") })
    }

    // ── MediaLibraryService ────────────────────────────────────────────────

    @Test fun `empty library gets dart demo fallback sorted recent first`() {
        val service = MediaLibraryService(libraryDirectories = { emptyList() })
        service.refreshLibrary()
        assertEquals(4, service.files.size)
        assertEquals("Project_Architecture_Spec.pdf", service.files[0].name)
        assertEquals("Design_Review_Audio.m4a", service.files[3].name)
    }

    @Test fun `filter and search compose like dart`() {
        val service = MediaLibraryService(libraryDirectories = { emptyList() })
        service.refreshLibrary()
        service.setFilter(MediaCategory.audio)
        assertEquals(2, service.filteredFiles.size)
        service.setSearchQuery("hangova")
        assertEquals(1, service.filteredFiles.size)
        service.setSearchQuery("HANGOVA") // case-insensitive
        assertEquals(1, service.filteredFiles.size)
        service.setFilter(null)
        service.setSearchQuery("")
        assertEquals(4, service.filteredFiles.size)
    }

    @Test fun `storage breakdown sums by category`() {
        val service = MediaLibraryService(libraryDirectories = { emptyList() })
        service.refreshLibrary()
        val breakdown = service.getStorageBreakdown()
        assertEquals(2569011L + 1145728L, breakdown[MediaCategory.document])
        assertEquals(5033164L + 1843200L, breakdown[MediaCategory.audio])
    }

    @Test fun `refresh scans directories skipping hidden files`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "chaty-test-${System.nanoTime()}")
        dir.mkdirs()
        try {
            File(dir, "photo.png").writeBytes(bytes(0x89, 0x50, 0x4E, 0x47))
            File(dir, ".hidden").writeBytes(bytes(0x01))
            val service = MediaLibraryService(libraryDirectories = { listOf(dir) })
            service.refreshLibrary()
            assertEquals(1, service.files.size)
            assertEquals("photo.png", service.files[0].name)
            assertEquals(MediaCategory.image, service.files[0].category)
            assertEquals("image/png", service.files[0].mimeType)
        } finally {
            dir.deleteRecursively()
        }
    }
}
