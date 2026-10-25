package com.example.chat

import com.example.chat.data.api.ApiMediaUploadClient
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Parity tests for ApiMediaUploadClient against
 *  lib/data/services/api_backend_service.dart:1569-1606. */
class ApiMediaUploadClientParityTest {

    private class FakeTransport(vararg val responses: ApiMediaUploadClient.Response) :
        ApiMediaUploadClient.Transport {
        val calls = mutableListOf<String>()
        var index = 0
        override fun upload(
            url: String,
            authorization: String,
            tenantId: String,
            filename: String,
            fileBytes: ByteArray,
        ): ApiMediaUploadClient.Response {
            calls.add("$url|$authorization|$tenantId|$filename|${fileBytes.size}")
            return responses[minOf(index++, responses.size - 1)]
        }
    }

    private fun client(t: ApiMediaUploadClient.Transport, token: String? = "tok") =
        ApiMediaUploadClient({ "https://api.example.com/" }, { "tenant-1" }, { token }, t)

    private fun tempFile(): File {
        val f = File.createTempFile("upload", ".bin")
        f.writeBytes(byteArrayOf(1, 2, 3, 4))
        f.deleteOnExit()
        return f
    }

    @Test
    fun successReturnsDataMapAndSendsExactHeaders() {
        val t = FakeTransport(ApiMediaUploadClient.Response(200, """{"data":{"url":"https://cdn/x.png","size":4}}"""))
        val out = client(t).uploadMedia(tempFile())
        assertEquals("https://cdn/x.png", out["url"])
        assertEquals(4, out["size"])
        assertEquals(
            "https://api.example.com/chat/upload|Bearer tok|tenant-1|${t.calls[0].split("|")[3]}|4",
            t.calls[0],
        )
    }

    @Test
    fun bareBodyWithoutDataKeyIsAccepted() {
        val t = FakeTransport(ApiMediaUploadClient.Response(200, """{"url":"https://cdn/y.png"}"""))
        assertEquals("https://cdn/y.png", client(t).uploadMedia(tempFile())["url"])
    }

    @Test
    fun notFoundFallsBackToApiV1Path() {
        val t = FakeTransport(
            ApiMediaUploadClient.Response(404, "{}"),
            ApiMediaUploadClient.Response(200, """{"data":{"url":"u"}}"""),
        )
        client(t).uploadMedia(tempFile())
        assertTrue(t.calls[0].endsWith("|4"))
        assertTrue(t.calls[1].startsWith("https://api.example.com/api/v1/chat/upload|"))
    }

    @Test
    fun nonSuccessSurfacesServerDetail() {
        val t = FakeTransport(ApiMediaUploadClient.Response(413, """{"detail":"too large"}"""))
        val e = assertThrows(ApiMediaUploadClient.UploadException::class.java) {
            client(t).uploadMedia(tempFile())
        }
        assertEquals("too large", e.message)
    }

    @Test
    fun nonJsonErrorBodyFallsBackToStatusDetail() {
        val t = FakeTransport(ApiMediaUploadClient.Response(500, "oops"))
        val e = assertThrows(ApiMediaUploadClient.UploadException::class.java) {
            client(t).uploadMedia(tempFile())
        }
        assertEquals("Failed to upload media (500)", e.message)
    }

    @Test
    fun missingTokenThrowsNotAuthenticated() {
        val t = FakeTransport(ApiMediaUploadClient.Response(200, "{}"))
        val e = assertThrows(ApiMediaUploadClient.UploadException::class.java) {
            client(t, token = null).uploadMedia(tempFile())
        }
        assertEquals("Not authenticated", e.message)
        assertTrue(t.calls.isEmpty())
    }
}
