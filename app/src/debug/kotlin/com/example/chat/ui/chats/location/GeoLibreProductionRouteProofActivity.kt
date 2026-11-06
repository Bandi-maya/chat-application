package com.example.chat.ui.chats.location

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Exercises the actual production composable and assets, no account/backend/GPS fixture APIs. */
class GeoLibreProductionRouteProofActivity : ComponentActivity() {
    @Volatile var mapReady = false
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, intent.getFloatExtra("syntheticFontScale", 1f))) {
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
}
