package com.example.chat.ui.media

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLibraryServiceHardeningTest {

    @Test
    fun excludesHiddenAndTransientTransferFiles() = runBlocking {
        val root = Files.createTempDirectory("chaty-media-scan").toFile()
        try {
            File(root, "photo.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
            File(root, ".hidden.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
            File(root, "chaty-outgoing-123-photo.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
            File(root, "unfinished.jpg.part").writeText("partial")
            File(root, "cache.tmp").writeText("temporary")

            val service = MediaLibraryService(libraryDirectories = { listOf(root) })
            service.refreshLibrary()

            assertEquals(listOf("photo.jpg"), service.files.map { it.name })
            assertEquals(MediaCategory.image, service.files.single().category)
            assertTrue(!service.isLoading)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun olderConcurrentScanCannotReplaceNewerSnapshot() = runBlocking {
        val oldRoot = Files.createTempDirectory("chaty-old-scan").toFile()
        val newRoot = Files.createTempDirectory("chaty-new-scan").toFile()
        val firstScanEntered = CountDownLatch(1)
        val releaseFirstScan = CountDownLatch(1)
        val callCount = AtomicInteger(0)
        try {
            File(oldRoot, "old.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
            File(newRoot, "new.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))

            val service = MediaLibraryService(
                libraryDirectories = {
                    if (callCount.incrementAndGet() == 1) {
                        firstScanEntered.countDown()
                        check(releaseFirstScan.await(5, TimeUnit.SECONDS))
                        listOf(oldRoot)
                    } else {
                        listOf(newRoot)
                    }
                },
            )

            val older = async(Dispatchers.Default) { service.refreshLibrary() }
            assertTrue(firstScanEntered.await(5, TimeUnit.SECONDS))
            service.refreshLibrary()
            releaseFirstScan.countDown()
            older.await()

            assertEquals(listOf("new.jpg"), service.files.map { it.name })
            assertTrue(!service.isLoading)
        } finally {
            releaseFirstScan.countDown()
            oldRoot.deleteRecursively()
            newRoot.deleteRecursively()
        }
    }
}
