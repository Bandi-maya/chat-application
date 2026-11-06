package com.example.chat

import com.example.chat.core.links.MessageLinkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageLinkPolicyTest {

    @Test fun extractsOrderedDedupedUrls() {
        val text = "see https://a.example/x, then https://b.example/y. again https://a.example/x"
        assertEquals(listOf("https://a.example/x", "https://b.example/y"), MessageLinkPolicy.extractUrls(text))
    }

    @Test fun stripsTrailingPunctuation() {
        assertEquals(listOf("https://a.example/page?x=1"), MessageLinkPolicy.extractUrls("open https://a.example/page?x=1."))
    }

    @Test fun respectsLimit() {
        val text = (1..6).joinToString(" ") { "https://h$it.example/" }
        assertEquals(3, MessageLinkPolicy.extractUrls(text).size)
    }

    @Test fun ignoresNonHttpAndBlank() {
        assertEquals(emptyList<String>(), MessageLinkPolicy.extractUrls("ftp://x.example no links"))
        assertEquals(emptyList<String>(), MessageLinkPolicy.extractUrls("   "))
    }

    @Test fun displayHost() {
        assertEquals("a.example", MessageLinkPolicy.displayHost("https://a.example:8443/path"))
    }

    @Test fun linkSpansCoverAllLinksWithExactRanges() {
        val text = "a https://a.example/x, b https://b.example/y."
        val spans = MessageLinkPolicy.linkSpans(text)
        assertEquals(2, spans.size)
        assertEquals("https://a.example/x", text.substring(spans[0].start, spans[0].end))
        assertEquals("https://b.example/y", text.substring(spans[1].start, spans[1].end))
        assertEquals(spans[0].url, text.substring(spans[0].start, spans[0].end))
    }

    @Test fun linkSpansIgnoreNonHttpAndBlank() {
        assertEquals(emptyList<MessageLinkPolicy.LinkSpan>(), MessageLinkPolicy.linkSpans("ftp://x.example none"))
        assertEquals(emptyList<MessageLinkPolicy.LinkSpan>(), MessageLinkPolicy.linkSpans(""))
    }
}
