package com.example.chat.data.transfer

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateReceiveDestinationTest {
    @Test
    fun onlyAllowsFilesDirectlyInsidePrivateReceiveDirectory() {
        val root = Files.createTempDirectory("chaty-private-receive").toFile()
        try {
            val expected = File(File(root, "p2p-received").apply { mkdirs() }, "video.mp4")
            val actual = validatePrivateReceiveDestination(root, expected)
            assertEquals(expected.absoluteFile, actual)
            assertTrue(File(root, "p2p-received").isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsExternalDirectoriesAndTraversalNames() {
        val root = Files.createTempDirectory("chaty-private-receive-outside").toFile()
        val outside = Files.createTempDirectory("chaty-outside").toFile()
        try {
            val invalid = listOf(
                File(outside, "outside.mp4"),
                File(File(root, "p2p-received"), "../escape.mp4"),
                File(File(root, "p2p-received"), "."),
                File(File(root, "p2p-received"), "bad\nname.mp4"),
            )
            invalid.forEach { file ->
                var rejected = false
                try { validatePrivateReceiveDestination(root, file) } catch (_: SecurityException) { rejected = true }
                assertTrue("destination must be rejected: $file", rejected)
            }
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun rejectsExistingDestination() {
        val root = Files.createTempDirectory("chaty-private-receive-existing").toFile()
        try {
            val directory = File(root, "p2p-received").apply { mkdirs() }
            val existing = File(directory, "already.mp4").apply { writeBytes(byteArrayOf(1)) }
            var rejected = false
            try { validatePrivateReceiveDestination(root, existing) } catch (_: SecurityException) { rejected = true }
            assertTrue(rejected)
        } finally {
            root.deleteRecursively()
        }
    }
}
