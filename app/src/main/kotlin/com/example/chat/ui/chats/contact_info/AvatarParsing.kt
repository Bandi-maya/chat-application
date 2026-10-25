package com.example.chat.ui.chats.contact_info

/** Avatar color parsing, two exact Dart variants (api branch anchor 3bee780).
 *  ARGB longs keep this JVM-testable; call sites wrap in Color(...). */

/** AppAvatar._parseColor (app_avatar.dart): strips ALL '#' and '0x'
 *  occurrences, pads 6-digit hex with FF alpha, radix-16 parse; failure ->
 *  caller's fallback (theme primary). */
fun parseAvatarColorArgb(hex: String?, fallbackArgb: Long): Long {
    if (hex.isNullOrEmpty()) return fallbackArgb
    return try {
        val clean = hex.replace("#", "").replace("0x", "")
        (if (clean.length == 6) "FF$clean" else clean).toLong(16)
    } catch (_: Exception) {
        fallbackArgb
    }
}

/** ChatyAvatarCore.parseColorHex (chaty_kit.dart): replaceFirst for the 0x/#
 *  prefix, tryParse, 6-digit-or-less values OR-ed with FF alpha; the Dart
 *  default fallback is indigo 0xFF6366F1. Contact Info's header avatar uses
 *  this variant (not the AppAvatar one). */
fun parseAvatarColorArgbKit(hex: String?, fallbackArgb: Long = 0xFF6366F1): Long {
    if (hex.isNullOrEmpty()) return fallbackArgb
    val normalized = hex.replaceFirst("0x", "").replaceFirst("#", "")
    val value = normalized.toLongOrNull(16) ?: return fallbackArgb
    return if (normalized.length <= 6) 0xFF000000L or value else value
}
