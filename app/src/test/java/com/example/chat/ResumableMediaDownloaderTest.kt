package com.example.chat

import com.example.chat.data.media.ResumableMediaDownloader
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Real HTTP against MockWebServer (plain http via the internal test constructor): resume only with proven bytes. */
class ResumableMediaDownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private lateinit var part: File
    private val dl get() = ResumableMediaDownloader(OkHttpClient(), 10_000_000, { emptyMap() }, false)
    private val E1 = "\"e1\""

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        dir = File.createTempFile("dltest", "d").apply { delete(); mkdirs() }
        part = File(dir, "x.part")
    }
    @After fun tearDown() { server.shutdown(); dir.deleteRecursively() }

    private fun get() = runBlocking { dl.download(server.url("/f").toString(), part, { true }) { _, _, _ -> } }
    private fun seed(bytes: String, etag: String?) { part.writeText(bytes); etag?.let { File(part.path + ".etag").writeText(it) } }

    @Test fun `resumes with Range and If-Range only when a strong etag was saved and the reply matches`() {
        seed("abc", E1)
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setHeader("ETag", E1).setBody("def"))
        val r = get()
        assertEquals("abcdef", part.readText()); assertEquals(6L, r.totalBytes)
        val req = server.takeRequest()
        assertEquals("bytes=3-", req.getHeader("Range")); assertEquals(E1, req.getHeader("If-Range"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `part without a saved validator is discarded and no Range is sent`() {
        seed("abc", null)
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", E1).setBody("abcdef"))
        get()
        assertNull(server.takeRequest().getHeader("Range"))
        assertEquals("abcdef", part.readText())
    }

    @Test fun `206 with a different etag throws the part away and re-requests from zero`() {
        seed("abc", E1)
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setHeader("ETag", "\"e2\"").setBody("XYZ"))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"e2\"").setBody("newnew"))
        get()
        assertEquals("newnew", part.readText())
        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test fun `206 without any ETag is never appended`() {
        seed("abc", E1)
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setBody("def"))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", E1).setBody("abcdef"))
        get()
        assertEquals("abcdef", part.readText()); assertEquals(2, server.requestCount)
        assertNull(run { server.takeRequest(); server.takeRequest().getHeader("Range") })
    }

    @Test fun `weak etag is never persisted so a later attempt cannot resume on it`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "W/\"w\"").setBody("abcdef"))
        get()
        assertFalse(File(part.path + ".etag").exists())
    }

    @Test fun `server ignores If-Range and answers 200 so the part is replaced`() {
        seed("abc", E1)
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"e9\"").setBody("whole!"))
        get()
        assertEquals("whole!", part.readText()); assertEquals("\"e9\"", File(part.path + ".etag").readText())
    }

    @Test fun `complete part is proven by 416 plus equal etag`() {
        seed("abcdef", E1)
        server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */6").setHeader("ETag", E1))
        val r = get()
        assertEquals("abcdef", part.readText()); assertEquals(6L, r.totalBytes)
    }
}
