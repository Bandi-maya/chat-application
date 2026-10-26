package com.example.chat.ui.chats.location

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Debug-only entry PROPOSAL. Not exported or registered until owner reviews debug manifest. */
class GeoLibreSyntheticMapActivity : Activity() {
    private var enabledAt = 0L
    var fakeSends = 0
        private set
    var mapReady = false
        private set
    @Volatile var blockTiles = false
        private set
    @Volatile var allowedTileRequests = 0
        private set
    @Volatile var blockedTileRequests = 0
        private set
    private var selected: LocationCoordinate? = null
    private var map: GeoLibreLocationMapView? = null
    private var mapContainer: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val status = TextView(this).apply { text = "Synthetic fixture only. No GPS, permissions, accounts or messages. Online maps contact OpenFreeMap only after Load map." }
        content.addView(status)
        content.addView(Button(this).apply {
            text = "Load synthetic map"
            setOnClickListener {
                if (map == null) {
                    mapReady = false
                    enabledAt = SystemClock.elapsedRealtime()
                    map = GeoLibreLocationMapView(this@GeoLibreSyntheticMapActivity,
                        onPinSelected = { point -> selected = point; status.text = "Synthetic selected: ${point.latitude}, ${point.longitude}. No location sent." },
                        onUnavailable = { status.text = it },
                        onAttributionLink = { status.text = "Attribution link selected: ${it.host}. No external browser opened by fixture." },
                        onMapReady = { mapReady = true },
                        tileRequestsAllowed = { !blockTiles },
                        onTileRequestDecision = { allowed -> if (allowed) allowedTileRequests++ else blockedTileRequests++ },
                    ).also { view ->
                        mapContainer?.addView(view, LinearLayout.LayoutParams(-1, -1))
                        view.showPin(LocationCoordinate(17.385, 78.4867))
                    }
                }
            }
        })
        content.addView(Button(this).apply {
            text = "Close map"
            setOnClickListener { closeMap(); status.text = "Map disposed. No GPS or message action exists in this fixture." }
        })
        content.addView(Button(this).apply {
            text = "Fake approximate permission/fix"
            setOnClickListener { selected = LocationCoordinate(17.385, 78.4867); map?.showPin(selected!!); status.text = "FAKE approximate grant, synthetic fix, accuracy 800 m. No real permission requested." }
        })
        content.addView(Button(this).apply {
            text = "Fake denied permission"
            setOnClickListener { status.text = "FAKE denied. Manual synthetic selection remains available. No permission requested." }
        })
        content.addView(Button(this).apply {
            text = "Use manual synthetic coordinates"
            setOnClickListener { selected = LocationCoordinate(12.0, 78.0); map?.showPin(selected!!); status.text = "Manual synthetic coordinates selected without GPS." }
        })
        content.addView(Button(this).apply {
            text = "Confirm to FAKE sink"
            setOnClickListener { if (selected != null) { fakeSends++; selected = null; status.text = "FAKE sink count $fakeSends. No backend or account exists in fixture." } }
        })
        content.addView(Button(this).apply {
            text = "Block tile requests before load"
            setOnClickListener { blockTiles = true; closeMap(); status.text = "Tile HTTPS requests will now be denied in WebView request interception. Manual coordinates still work." }
        })
        content.addView(Button(this).apply {
            text = "Record timing and PSS"
            setOnClickListener { val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info); status.text = "Fixture measurement: PSS ${info.totalPss} KB, elapsed since enable ${if(enabledAt == 0L) 0 else SystemClock.elapsedRealtime()-enabledAt} ms. Not benchmark acceptance." }
        })
        mapContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; contentDescription = "synthetic-map-container" }
        content.addView(mapContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(content)
    }

    private fun closeMap() {
        val previous = map
        map = null
        mapReady = false
        mapContainer?.removeAllViews()
        previous?.disposeMap()
    }
    override fun onPause() { map?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); map?.onResume() }
    override fun onDestroy() { closeMap(); super.onDestroy() }
}
