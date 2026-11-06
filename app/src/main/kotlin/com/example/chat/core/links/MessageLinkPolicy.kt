package com.example.chat.core.links

/** Chaty #34: URL extraction for bubble link previews. Pure Kotlin: JVM-testable. */
object MessageLinkPolicy {

    private val urlRegex = Regex("""https?://[^\s<>"'()\[\]{}]+""")

    /** Ordered, de-duplicated http(s) URLs, trailing punctuation stripped. */
    fun extractUrls(text: String, limit: Int = 3): List<String> {
        if (text.isBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        for (match in urlRegex.findAll(text)) {
            val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')
            if (url.length > 12) out += url
            if (out.size >= limit) break
        }
        return out.toList()
    }


    data class LinkSpan(val start: Int, val end: Int, val url: String)

    /** Character ranges of every http(s) URL in the text, for in-text
     *  underlined/tappable rendering. Previews stay capped separately;
     *  in-text linking covers all links (render safety cap [limit]). */
    fun linkSpans(text: String, limit: Int = 20): List<LinkSpan> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<LinkSpan>()
        for (match in urlRegex.findAll(text)) {
            val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')
            if (url.length <= 12) continue
            out += LinkSpan(match.range.first, match.range.first + url.length, url)
            if (out.size >= limit) break
        }
        return out
    }

    fun displayHost(url: String): String =
        url.removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore(':')
}
