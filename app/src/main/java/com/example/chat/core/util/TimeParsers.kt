package com.example.chat.core.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Mirrors Dart DateTime.tryParse semantics used across the Flutter models:
 *  accepts ISO-8601 with or without an offset; returns null on failure. */
fun parseIsoToInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }.getOrNull()
}

/** Dart: DateTime.tryParse(x) ?? DateTime.now() */
fun parseIsoOrNow(raw: String?): Instant = parseIsoToInstant(raw) ?: Instant.now()
