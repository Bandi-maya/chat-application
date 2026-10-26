package com.example.chat.ui.chats.location

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

/** Exercises the actual production composable and assets, no account/backend/GPS fixture APIs. */
class GeoLibreProductionRouteProofActivity : ComponentActivity() {
    @Volatile var mapReady = false
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MapViewerScreen(
                    context = this,
                    accountId = "synthetic-map-owner",
                    conversationId = "synthetic-map-room",
                    currentScope = { "synthetic-map-owner" to "synthetic-map-room" },
                    onDismiss = { finish() },
                    onAttributionLink = {},
                    initialCoordinate = LocationCoordinate(17.385, 78.4867),
                    currentLocationEnabled = false,
                    onMapReady = { mapReady = true },
                )
            }
        }
    }
}
