package com.example.chat.data.links

import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/** Chaty #34 hardening: SSRF guard for client-side link previews. Only http(s)
 *  URLs whose host resolves exclusively to public addresses may be fetched.
 *  Checked on the initial URL and again on every redirect hop, so a public
 *  page cannot bounce the fetcher at loopback, RFC1918, link-local (cloud
 *  metadata), CGNAT or otherwise non-routable targets. Pure Kotlin except for
 *  InetAddress: JVM-testable. */
object PublicUrlPolicy {

    /** Returns the URL unchanged when it is a fetchable public http(s) URL,
     *  otherwise throws IOException. */
    fun requirePublicHttpUrl(url: String): String {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw IOException("malformed URL", e)
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") throw IOException("unsupported scheme")
        val host = uri.host ?: throw IOException("missing host")
        requirePublicHost(host)
        return url
    }

    /** Resolves the host and requires every answer to be a public address.
     *  Rejecting when any answer is private blocks DNS-round-robin pinning. */
    fun requirePublicHost(host: String) {
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            throw IOException("host does not resolve", e)
        }
        if (addresses.isEmpty()) throw IOException("host does not resolve")
        for (address in addresses) {
            if (!isPublicAddress(address)) throw IOException("non-public address blocked")
        }
    }

    internal fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }
        return when (address) {
            is Inet4Address -> isPublicV4(address.address)
            is Inet6Address -> isPublicV6(address.address)
            else -> false
        }
    }

    private fun isPublicV4(b: ByteArray): Boolean {
        if (b.size != 4) return false
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        val b2 = b[2].toInt() and 0xFF
        if (b0 == 0) return false // 0.0.0.0/8 "this host"
        if (b0 == 100 && (b1 and 0xC0) == 0x40) return false // 100.64.0.0/10 CGNAT
        if (b0 == 192 && b1 == 0 && b2 == 0) return false // 192.0.0.0/24 protocol assignments
        if (b0 == 192 && b1 == 0 && b2 == 2) return false // documentation
        if (b0 == 198 && (b1 and 0xFE) == 18) return false // 198.18.0.0/15 benchmarking
        if (b0 == 198 && b1 == 51 && b2 == 100) return false // documentation
        if (b0 == 203 && b1 == 0 && b2 == 113) return false // documentation
        if ((b0 and 0xF0) == 0xF0) return false // 240.0.0.0/4 reserved
        return true
    }

    private fun isPublicV6(b: ByteArray): Boolean {
        if (b.size != 16) return false
        if ((b[0].toInt() and 0xFE) == 0xFC) return false // fc00::/7 unique local
        var mapped = true
        for (i in 0 until 10) if (b[i].toInt() != 0) { mapped = false; break }
        if (mapped && b[10].toInt() == 0xFF && b[11].toInt() == 0xFF) {
            // IPv4-mapped: judge the embedded v4 address.
            return isPublicV4(byteArrayOf(b[12], b[13], b[14], b[15]))
        }
        return true
    }
}
