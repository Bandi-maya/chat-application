package com.example.chat.ui.chats.linked_devices

import java.net.URLDecoder
import java.time.Instant

/** Pure QR payload + device-label rules ported from
 *  linked_devices_qr_screen.dart (api branch anchor 3bee780). */
object QrPayloads {

    /** _myQrPayload: `chaty://contact/<username>` (raw username, unencoded). */
    fun myQrPayload(username: String): String = "chaty://contact/$username"

    sealed interface ScanResult {
        /** Valid Chaty contact QR; [username] decoded, leading '@' stripped,
         *  trimmed. */
        data class Contact(val username: String) : ScanResult
        /** Not a chaty://contact/<username> code. */
        data object NotChatyQr : ScanResult
        /** Valid Chaty URI whose decoded username is empty; Dart returns
         *  silently (`if (username.isEmpty) return;`). */
        data object EmptyUsername : ScanResult
    }

    /** _handleBarcode URI validation: scheme chaty, host contact, at least
     *  one path segment; segment is URI-decoded, leading '@' stripped,
     *  trimmed. */
    fun parseScanned(raw: String?): ScanResult {
        if (raw.isNullOrEmpty()) return ScanResult.NotChatyQr
        val uri = try {
            java.net.URI(raw)
        } catch (_: Exception) {
            return ScanResult.NotChatyQr
        }
        if ((uri.scheme ?: "").lowercase() != "chaty") return ScanResult.NotChatyQr
        if ((uri.host ?: "").lowercase() != "contact") return ScanResult.NotChatyQr
        val path = uri.rawPath ?: ""
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return ScanResult.NotChatyQr
        val username = try {
            URLDecoder.decode(segments.first(), "UTF-8")
        } catch (_: Exception) {
            segments.first()
        }.removePrefix("@").trim()
        if (username.isEmpty()) return ScanResult.EmptyUsername
        return ScanResult.Contact(username)
    }

    /** Dart self-check: scanned username equals the current account's
     *  username, case-insensitive. */
    fun isSelfUsername(scanned: String, currentUsername: String): Boolean =
        scanned.lowercase() == currentUsername.lowercase()

    /** _lastActive: 'Active now' under a minute, 'Active Nm ago' under an
     *  hour, 'Active Nh ago' under a day, else 'Active d/M/yyyy'. */
    fun lastActiveLabel(lastActiveAt: Instant, now: Instant = Instant.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val value = lastActiveAt.atZone(zone)
        val diff = java.time.Duration.between(lastActiveAt, now)
        if (diff.toMinutes() < 1) return "Active now"
        if (diff.toMinutes() < 60) return "Active ${diff.toMinutes()}m ago"
        if (diff.toHours() < 24) return "Active ${diff.toHours()}h ago"
        return "Active ${value.dayOfMonth}/${value.monthValue}/${value.year}"
    }
}
