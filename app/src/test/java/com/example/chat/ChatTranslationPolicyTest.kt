package com.example.chat

import com.example.chat.core.translate.ChatTranslationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTranslationPolicyTest {

    @Test fun languageSetIncludesWidgetTargetsWithoutDuplicateCodes() {
        assertEquals(249, ChatTranslationPolicy.languages.size)
        assertEquals(249, ChatTranslationPolicy.languages.map { it.code }.toSet().size)
        assertTrue(ChatTranslationPolicy.supported("te"))
        assertTrue(ChatTranslationPolicy.supported("de"))
    }

    @Test fun unknownTargetFallsBackToEnglish() {
        assertEquals("en", ChatTranslationPolicy.normalizeTarget("not-a-language"))
        assertEquals("en", ChatTranslationPolicy.normalizeTarget(null))
        assertEquals("hi", ChatTranslationPolicy.normalizeTarget("hi"))
    }

    @Test fun languageSearchMatchesEnglishNativeNameAndCode() {
        for (query in listOf("Telugu", "TELUGU", "తెలుగు", " te ")) {
            assertTrue(ChatTranslationPolicy.searchLanguages(query).any { it.code == "te" })
        }
        assertEquals(ChatTranslationPolicy.languages, ChatTranslationPolicy.searchLanguages("  "))
        assertTrue(ChatTranslationPolicy.searchLanguages("no-such-language-123").isEmpty())
        assertEquals("te", ChatTranslationPolicy.normalizeTarget("te"))
    }

    @Test fun endpointEncodesTextAndTarget() {
        val url = ChatTranslationPolicy.endpoint("hello world & को", "hi")
        assertTrue(url.startsWith("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=hi&dt=t&q="))
        assertTrue(url.contains("hello+world+%26+%E0%A4%95%E0%A5%8B"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun endpointRejectsUnsupportedTarget() {
        ChatTranslationPolicy.endpoint("hello", "not-a-language")
    }

    @Test(expected = IllegalArgumentException::class)
    fun endpointRejectsOversizedText() {
        ChatTranslationPolicy.endpoint("x".repeat(ChatTranslationPolicy.MAX_CHARS + 1), "en")
    }

    @Test fun parseResponseJoinsSegments() {
        val body = "[[[\"नमस्ते\",\"hello\",null,null,10],[\" दुनिया\",\" world\",null,null,1]],null,\"en\"]"
        assertEquals("नमस्ते दुनिया", ChatTranslationPolicy.parseResponse(body))
    }

    @Test fun parseResponseRejectsMalformed() {
        assertNull(ChatTranslationPolicy.parseResponse("not json"))
        assertNull(ChatTranslationPolicy.parseResponse("[]"))
        assertNull(ChatTranslationPolicy.parseResponse("[[[]]]"))
    }
}
