package com.example.chat.data.api

/**
 * Validates opaque backend IDs before using them as a single URL path segment.
 * This intentionally rejects encoded separators as well as raw separators;
 * identifiers in Chaty APIs are UUIDs or restricted opaque ASCII keys.
 */
internal object ApiPathSegments {
    fun safe(value: String, maxLength: Int = 128): String? =
        value.takeIf { candidate ->
            candidate.length in 1..maxLength &&
                candidate != "." && candidate != ".." &&
                candidate.all { ch ->
                    ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                        ch == '-' || ch == '_' || ch == '.' || ch == '~'
                }
        }
}
