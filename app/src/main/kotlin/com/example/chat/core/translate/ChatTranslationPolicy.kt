package com.example.chat.core.translate

/** Online per-message translation following the GTranslate approach the user
 *  approved (same free Google endpoint family the GTranslate widget uses):
 *  client-side GET, no Chaty backend, no Firebase, no key. Bundled target-language catalogue
 *  follows the free Google translation widget. Search is entirely on-device.
 *  Pure policy + parser: JVM-testable, no Android imports. */
object ChatTranslationPolicy {

    data class Language(val code: String, val label: String, val nativeLabel: String = label)

    val languages: List<Language> = TranslationLanguages.all

    fun searchLanguages(query: String): List<Language> {
        val needle = query.trim()
        if (needle.isEmpty()) return languages
        return languages.filter {
            it.label.contains(needle, ignoreCase = true) ||
                it.nativeLabel.contains(needle, ignoreCase = true) ||
                it.code.contains(needle, ignoreCase = true)
        }
    }

    const val DEFAULT_TARGET = "en"

    /** Free-tier safety bound; longer messages are rejected, never truncated silently. */
    const val MAX_CHARS = 4500

    fun supported(code: String): Boolean = languages.any { it.code == code }

    fun normalizeTarget(code: String?): String =
        code?.takeIf { supported(it) } ?: DEFAULT_TARGET

    fun endpoint(text: String, target: String): String {
        require(text.isNotBlank()) { "Nothing to translate" }
        require(text.length <= MAX_CHARS) { "Message is too long to translate online" }
        require(supported(target)) { "Unsupported target language: $target" }
        val q = java.net.URLEncoder.encode(text, "UTF-8")
        return "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&q=$q"
    }

    /** Response shape: [[["translated","source",...],...],...]; join segments. */
    fun parseResponse(body: String): String? {
        return try {
            val outer = org.json.JSONArray(body)
            val segments = outer.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONArray(i) ?: continue
                val piece = seg.optString(0, "")
                if (piece.isNotEmpty()) sb.append(piece)
            }
            sb.toString().ifBlank { null }
        } catch (e: org.json.JSONException) {
            null
        }
    }
}
