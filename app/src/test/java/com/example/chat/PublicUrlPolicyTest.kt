package com.example.chat

import com.example.chat.data.links.PublicUrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class PublicUrlPolicyTest {

    @Test fun blocksPrivateLoopbackAndSpecialUseTargets() {
        val blocked = listOf(
            "http://127.0.0.1/",
            "http://127.53.0.1/",
            "http://10.0.0.8/",
            "http://172.16.5.4/",
            "http://172.31.255.255/",
            "http://192.168.1.20/",
            "http://169.254.169.254/latest/meta-data",
            "http://0.0.0.0/",
            "http://100.64.1.1/",
            "http://100.127.9.9/",
            "http://198.18.0.1/",
            "http://198.19.255.2/",
            "http://240.1.2.3/",
            "http://[::1]/",
        )
        for (url in blocked) {
            assertThrows("expected block: $url", IOException::class.java) {
                PublicUrlPolicy.requirePublicHttpUrl(url)
            }
        }
    }

    @Test fun allowsPublicLiteralIps() {
        assertEquals("http://1.1.1.1/", PublicUrlPolicy.requirePublicHttpUrl("http://1.1.1.1/"))
        assertEquals("https://8.8.8.8/x", PublicUrlPolicy.requirePublicHttpUrl("https://8.8.8.8/x"))
    }

    @Test fun blocksNonHttpSchemes() {
        for (url in listOf("ftp://1.1.1.1/", "file:///etc/hosts", "content://media/x", "gopher://1.1.1.1/")) {
            assertThrows("expected block: $url", IOException::class.java) {
                PublicUrlPolicy.requirePublicHttpUrl(url)
            }
        }
    }

    @Test fun blocksLocalhostName() {
        assertThrows(IOException::class.java) {
            PublicUrlPolicy.requirePublicHttpUrl("http://localhost:8080/admin")
        }
    }
}
