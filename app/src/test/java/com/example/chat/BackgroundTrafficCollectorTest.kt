package com.example.chat
import java.io.File
import org.junit.Assert.*
import org.junit.Test
/** Source-contract checks, not an Android lifecycle runtime test. */
class BackgroundTrafficCollectorTest {
    @Test fun bothCounterSubscribersStopWithTheirScreens() {
        val root=if(File("app/src/main").exists()) File("app/src/main") else File("src/main")
        for(path in listOf("kotlin/com/example/chat/ui/ChatDetailHost.kt", "kotlin/com/example/chat/ui/settings/NetworkDataUsageScreen.kt")) {
            val s=File(root,path).readText()
            assertFalse(path,s.contains("appTrafficMonitor.state.collectAsState()"))
            assertTrue(path,s.contains("trafficOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED)"))
            assertTrue(path,s.contains("trafficState.collect { value = it }"))
        }
    }
}
