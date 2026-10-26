package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.chat.ui.chats.home.HomeSwipeAction
import com.example.chat.ui.chats.home.HomeSwipeActions

/** Synthetic callbacks only. No account, network, service locator, or live chat action. */
class SwipeRowFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dark = intent.getBooleanExtra("dark", false)
        val bg = if (dark) Color(0xFF171B21) else Color.White
        val fg = if (dark) Color.White else Color.Black
        setContent {
            var events by remember { mutableStateOf("none") }
            var count by remember { mutableIntStateOf(0) }
            var rowTaps by remember { mutableIntStateOf(0) }
            var swipeEnabled by remember { mutableStateOf(true) }
            Column(Modifier.fillMaxSize().background(bg).safeDrawingPadding()) {
                Text("Events: $events/$count; row: $rowTaps", color = fg)
                Button(onClick = { swipeEnabled = !swipeEnabled }) {
                    Text(if (swipeEnabled) "Enter selection" else "Exit selection")
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(2) { index ->
                        HomeSwipeActions(
                            enabled = swipeEnabled,
                            backgroundColor = bg,
                            leadingActions = listOf("Call", "Video").map { name ->
                                HomeSwipeAction(Icons.Rounded.Call, name, Color(0xFF316ACA)) { events = name; count++ }
                            },
                            actions = listOf("Pin", "Mute", "Archive").map { name ->
                                HomeSwipeAction(Icons.Rounded.PushPin, name,
                                    when (name) { "Pin" -> Color(0xFF008A74); "Mute" -> Color(0xFFF59E0B); else -> Color(0xFF516570) }
                                ) { events = name; count++ }
                            },
                        ) {
                            Row(Modifier.fillMaxWidth().height(if (index == 0) 86.dp else 112.dp)
                                .semantics { contentDescription = "Synthetic row $index" }
                                .clickable { rowTaps++ }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("AB", color = fg)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Synthetic chat $index", color = fg, maxLines = 1)
                                    Text("Last message preview", color = fg, maxLines = 1)
                                }
                                Text("8:59 PM", color = fg)
                            }
                        }
                    }
                }
            }
        }
    }
}
