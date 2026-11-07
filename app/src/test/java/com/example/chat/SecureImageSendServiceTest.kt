package com.example.chat

import com.example.chat.data.media.ImageSendPolicy
import com.example.chat.data.media.ImageSendProcessor
import com.example.chat.data.media.SecureImageSendService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Media matrix send pipeline: fresh key per image, ciphertext-only upload,
 *  key + blur placeholder travel inside the MLS envelope. */
class SecureImageSendServiceTest {

    private class Harness {
        val uploads = mutableListOf<Pair<String, ByteArray>>()
        val messages = mutableListOf<Pair<String, Map<String, Any?>>>()
        val key = ByteArray(32) { it.toByte() }
        val service = SecureImageSendService(
            encrypt = { plain, k ->
                assertTrue(k.contentEquals(key))
                plain.reversedArray() // stand-in ciphertext
            },
            upload = { cid, ct -> uploads.add(cid to ct); "att-1" },
            sendMessage = { cid, payload -> messages.add(cid to payload) },
            randomKey = { key },
        )
    }

    private fun prepared(blur: String? = "blur64") = ImageSendProcessor.Prepared(
        bytes = byteArrayOf(1, 2, 3, 4), width = 1600, height = 1200,
        mimeType = "image/jpeg", blurPlaceholder = blur,
    )

    @Test fun `auto tier send encrypts uploads and sends key in envelope`() = runTest {
        val h = Harness()
        val outcome = h.service.send("room-1", prepared(), ImageSendPolicy.tier(ImageSendPolicy.SendMode.AUTO, 500), "hello")
        assertEquals("att-1", outcome.attachmentId)
        assertEquals(4L, outcome.sizeBytes)
        assertEquals("room-1" to byteArrayOf(4, 3, 2, 1).toList(), h.uploads.single().first to h.uploads.single().second.toList())
        val env = h.messages.single().second
        assertEquals("attachment", env["type"])
        assertEquals("att-1", env["attachment_id"])
        assertEquals(java.util.Base64.getEncoder().encodeToString(h.key), env["key_b64"])
        val meta = env["metadata"] as Map<*, *>
        assertEquals("image", meta["kind"])
        assertEquals("auto", meta["send_mode"])
        assertEquals(true, meta["exif_stripped"])
        assertEquals("blur64", meta["blur_placeholder"])
        assertEquals("hello", meta["caption"])
    }

    @Test fun `file mode keeps exif flag and drops blur placeholder`() = runTest {
        val h = Harness()
        h.service.send("room-1", prepared(blur = null), ImageSendPolicy.tier(ImageSendPolicy.SendMode.FILE), null)
        val meta = h.messages.single().second["metadata"] as Map<*, *>
        assertEquals("file", meta["send_mode"])
        assertEquals(false, meta["exif_stripped"])
        assertFalse(meta.containsKey("blur_placeholder"))
        assertFalse(meta.containsKey("caption"))
    }

    @Test fun `hd mode is marked hd`() = runTest {
        val h = Harness()
        h.service.send("room-1", prepared(), ImageSendPolicy.tier(ImageSendPolicy.SendMode.HD), null)
        val meta = h.messages.single().second["metadata"] as Map<*, *>
        assertEquals("hd", meta["send_mode"])
    }
}
