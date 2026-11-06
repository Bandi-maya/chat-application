package com.example.chat

import com.example.chat.data.links.LinkPreviewService
import com.example.chat.data.links.PreviewImagePolicy
import com.example.chat.data.links.PublicUrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

class LinkPreviewGuardTest {

    // --- Panorama / dimension handling (was the r6 hole) ---

    @Test fun panoramaDecodesWithinBudget() {
        // 12000x1000 passes the source cap but would decode to 48 MB unsampled.
        assertTrue(PreviewImagePolicy.accepts(12000, 1000))
        val sample = PreviewImagePolicy.sampleSizeFor(12000, 1000)
        val w = 12000 / sample
        val h = 1000 / sample
        assertTrue(w.toLong() * h <= PreviewImagePolicy.DECODED_PIXEL_BUDGET)
        assertTrue(maxOf(w, h) <= PreviewImagePolicy.MAX_DECODED_DIMENSION)
    }

    @Test fun tallStripDecodesWithinBudget() {
        val sample = PreviewImagePolicy.sampleSizeFor(900, 11000)
        val w = 900 / sample
        val h = 11000 / sample
        assertTrue(w.toLong() * h <= PreviewImagePolicy.DECODED_PIXEL_BUDGET)
        assertTrue(maxOf(w, h) <= PreviewImagePolicy.MAX_DECODED_DIMENSION)
    }

    @Test fun smallImageNeedsNoSampling() {
        assertEquals(1, PreviewImagePolicy.sampleSizeFor(200, 100))
    }

    // --- Negative cases ---

    @Test fun oversizeSourceImageRejected() {
        assertFalse(PreviewImagePolicy.accepts(13000, 1000)) // 13 MP > 12 MP cap
        assertFalse(PreviewImagePolicy.accepts(5000, 5000))
        assertFalse(PreviewImagePolicy.accepts(0, 100))
        assertFalse(PreviewImagePolicy.accepts(-5, 100))
    }

    @Test fun redirectToPrivateTargetRejected() {
        // The hop validator is requirePublicHttpUrl; every redirect hop calls it.
        assertThrows(IOException::class.java) {
            PublicUrlPolicy.requirePublicHttpUrl("http://169.254.169.254/latest/meta-data")
        }
        assertThrows(IOException::class.java) {
            PublicUrlPolicy.requirePublicHttpUrl("http://192.168.1.1/admin")
        }
        // A redirect may also change scheme - non-http(s) is refused.
        assertThrows(IOException::class.java) {
            PublicUrlPolicy.requirePublicHttpUrl("file:///etc/hosts")
        }
    }

    @Test fun dnsAnswerPrivateRejectedAtConnectTime() {
        val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val dns = LinkPreviewService.PublicDns { listOf(loopback) }
        assertThrows(UnknownHostException::class.java) {
            dns.lookup("attacker-controlled.example")
        }
    }

    @Test fun dnsMixedAnswerRejectedAtConnectTime() {
        // One public + one private answer: the whole lookup must fail.
        val public = InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        val cgnat = InetAddress.getByAddress(byteArrayOf(100, 64, 1, 1))
        val dns = LinkPreviewService.PublicDns { listOf(public, cgnat) }
        assertThrows(UnknownHostException::class.java) {
            dns.lookup("round-robin.example")
        }
    }

    @Test fun dnsPublicAnswerAccepted() {
        val public = InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        val dns = LinkPreviewService.PublicDns { listOf(public) }
        assertEquals(listOf(public), dns.lookup("good.example"))
    }

    @Test fun dnsEmptyAnswerRejected() {
        val dns = LinkPreviewService.PublicDns { emptyList() }
        assertThrows(UnknownHostException::class.java) {
            dns.lookup("empty.example")
        }
    }
}
