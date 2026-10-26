package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chat.ui.kit.PushedPageTouchGuard

/** Debug-only synthetic host: a full-screen clickable "Home" layer with a pushed page on top.
 *  No ServiceLocator, session, network or account. EXTRA_GUARD=false omits the guard and is the
 *  control that proves the test can detect a fall-through. */
class PushedPageFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val guard = intent.getBooleanExtra(EXTRA_GUARD, true)
        setContent {
            var homeTaps by remember { mutableIntStateOf(0) }
            var actionTaps by remember { mutableIntStateOf(0) }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.LightGray).clickable { homeTaps++ })
                val page: @Composable () -> Unit = {
                    Column(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                        Text("Header")
                        Text("Plain description text")
                        Text("home=$homeTaps")
                        Text("action=$actionTaps")
                        Button(onClick = { actionTaps++ }) { Text("Explicit action") }
                    }
                }
                if (guard) PushedPageTouchGuard { page() } else page()
            }
        }
    }

    companion object { const val EXTRA_GUARD = "guard" }
}
