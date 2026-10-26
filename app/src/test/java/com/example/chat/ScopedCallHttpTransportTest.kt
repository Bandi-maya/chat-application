package com.example.chat
import com.example.chat.data.api.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*
class ScopedCallHttpTransportTest {
 @Test fun actualCallCanceledOnScopeTransitionAndLateRegisterRejected() {
  val server=MockWebServer();server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));server.start()
  try {
   val lock=Any();val expected=ApiAccountScope("a","fake","t",server.url("/").toString(),"",1);var current=expected
   val registry=CallRequestRegistry()
   val transport=ScopedCallHttpTransport(expected,{e,b->synchronized(lock){if(current !== e)false else {b();true}}},registry)
   val ended=CountDownLatch(1)
   val thread=Thread{try{transport.execute("GET",server.url("/test").toString(),emptyMap(),null);fail("Canceled request should not succeed")}catch(_:java.io.IOException){}finally{ended.countDown()}}
   thread.start();assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))
   synchronized(lock){current=ApiAccountScope("b","fake","t",expected.baseUrl,"",2);registry.cancelAll()}
   assertTrue("Real OkHttp cancellation finishes",ended.await(2,TimeUnit.SECONDS));thread.join(2000)
   try{transport.execute("GET",server.url("/late").toString(),emptyMap(),null);fail("Late registration rejected")}catch(_:java.io.IOException){}
   assertEquals(1,server.requestCount)
  }finally{server.shutdown()}
 }
}
