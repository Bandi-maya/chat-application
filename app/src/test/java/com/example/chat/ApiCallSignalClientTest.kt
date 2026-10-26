package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test

class ApiCallSignalClientTest {
    private fun scope() = CallSignalScope("me", "https://example.invalid", "fake-token", 1)
    @Test fun post404RetriesOnlySameHostVersionedPath() {
        val s=scope(); val urls=mutableListOf<String>()
        val client=ApiCallSignalClient({s}, ApiHttpTransport { _,url,_,_ -> urls.add(url); if(urls.size==1) 404 to "" else 202 to "" })
        assertEquals(ApiCallSignalClient.SendResult.ACCEPTED, client.send("peer", mapOf("call_id" to "c", "signal_type" to "offer")))
        assertEquals(listOf("https://example.invalid/chat/call/signal", "https://example.invalid/api/v1/chat/call/signal"), urls)
    }
    @Test fun unauthorizedDoesNotRetry() {
        val s=scope(); var n=0
        val client=ApiCallSignalClient({s}, ApiHttpTransport { _,_,_,_ -> n++; 401 to "" })
        assertEquals(ApiCallSignalClient.PollResult.Unauthorized,client.poll()); assertEquals(1,n)
    }
    @Test fun staleAccountResultRejectedAndNoFallbackAfterSwitch() {
        var s:CallSignalScope?=scope(); var n=0
        val client=ApiCallSignalClient({s}, ApiHttpTransport { _,_,_,_ -> n++; s=null; 404 to "" })
        assertEquals(ApiCallSignalClient.PollResult.Stale,client.poll()); assertEquals(1,n)
    }
    @Test fun unsupportedPollingStopsForSameScope() {
        val s=scope(); var n=0
        val client=ApiCallSignalClient({s}, ApiHttpTransport { _,_,_,_ -> n++; 404 to "" })
        assertEquals(ApiCallSignalClient.PollResult.Unavailable,client.poll())
        assertEquals(ApiCallSignalClient.PollResult.Unavailable,client.poll()); assertEquals(2,n)
    }
    @Test fun parseQueuedSignalsAndRejectConflictingSender() {
        val s=scope()
        val body="""{"data":[{"sender_id":"peer","data":{"call_id":"c","signal_type":"offer","sdp":{"sdp":"raw"}}},{"sender_id":"other","data":{"call_id":"x","sender_id":"forged"}}]}"""
        val client=ApiCallSignalClient({s},ApiHttpTransport { _,_,_,_ -> 200 to body })
        val result=client.poll() as ApiCallSignalClient.PollResult.Signals
        assertEquals(1,result.signals.size);assertEquals("peer",result.signals.single()["sender_id"])
    }
    @Test fun concurrentReentrantPollHasOneFlight() {
        val s=scope(); lateinit var client:ApiCallSignalClient
        client=ApiCallSignalClient({s},ApiHttpTransport { _,_,_,_ -> assertEquals(ApiCallSignalClient.PollResult.InFlight,client.poll());200 to "{\"data\":[]}" })
        assertTrue(client.poll() is ApiCallSignalClient.PollResult.Signals)
    }
    @Test fun explicitOldScopeNeverUsesReplacementCredentialsEvenWithSameToken() {
        val old=scope(); var current=old; var requests=0
        val client=ApiCallSignalClient({current},ApiHttpTransport { _,_,_,_ -> requests++;200 to "{}" })
        current=CallSignalScope(old.accountId,old.baseUrl,old.token,2)
        assertEquals(ApiCallSignalClient.SendResult.STALE,client.send(old,"peer",mapOf("call_id" to "old","signal_type" to "offer")))
        assertEquals(ApiCallSignalClient.PollResult.Stale,client.poll(old));assertEquals(0,requests)
    }
    @Test fun httpUrlNeverReceivesCredentials() {
        val s=CallSignalScope("me","http://example.invalid","fake",1)
        val client=ApiCallSignalClient({s}, ApiHttpTransport { _,_,_,_ -> fail("No HTTP request allowed");500 to "" })
        assertEquals(ApiCallSignalClient.PollResult.Stale,client.poll())
    }
}
