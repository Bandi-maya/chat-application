package com.example.chat

import com.example.chat.data.local.BoundedFileReader
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedFileReaderTest {
    @Test
    fun readsFilesWithinTheConfiguredMemoryBudget() {
        val file = Files.createTempFile("chaty-buffered-upload", ".bin").toFile()
        try {
            val expected = ByteArray(96 * 1024) { (it % 251).toByte() }
            file.writeBytes(expected)
            assertArrayEquals(expected, BoundedFileReader.read(file, 128 * 1024L))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsFilesAboveTheMemoryBudgetBeforeReadingThem() {
        val file = Files.createTempFile("chaty-buffered-upload-large", ".bin").toFile()
        try {
            file.writeBytes(ByteArray(4096))
            var rejected = false
            try {
                BoundedFileReader.read(file, 1024L)
            } catch (_: IllegalStateException) {
                rejected = true
            }
            assertTrue("oversized files must not be materialized into a ByteArray", rejected)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsNonFilesAndZeroBudgets() {
        val directory = Files.createTempDirectory("chaty-buffered-upload-dir").toFile()
        try {
            var rejected = false
            try { BoundedFileReader.read(directory, 1024L) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            val file = Files.createTempFile(directory.toPath(), "empty", ".bin").toFile()
            rejected = false
            try { BoundedFileReader.read(file, 0L) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        } finally {
            directory.deleteRecursively()
        }
    }
}
