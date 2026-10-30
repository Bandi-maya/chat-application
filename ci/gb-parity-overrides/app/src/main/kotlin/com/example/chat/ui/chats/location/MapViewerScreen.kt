package com.example.chat.ui.chats.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.ChatyDropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Dedicated GeoLibre viewer. Explicitly opening this screen starts map tile requests, not GPS.
 * Provider attribution and the online-service notice remain in the map. No Send path exists. */
@Composable
fun MapViewerScreen(
    context: Context,
    // Capture stable identifiers; currentScope must read the live owner, not a stale closure.
    accountId: String,
    conversationId: String,
    currentScope: () -> Pair<String?, String?>,
    onDismiss: () -> Unit,

    onAttributionLink: (Uri) -> Unit,
    initialCoordinate: LocationCoordinate? = null,
    currentLocationEnabled: Boolean = true,
    onMapReady: () -> Unit = {},
) {
    // Landscape: keep the Locate button left of the 56dp-wide map controls column so they never overlap.
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val systemDark = (LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val mapColors = if (systemDark) darkColorScheme() else lightColorScheme()
    val controlColors = IconButtonDefaults.filledIconButtonColors(containerColor = mapColors.secondaryContainer, contentColor = mapColors.onSecondaryContainer, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant)
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var mapActive by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var mapView by remember { mutableStateOf<GeoLibreLocationMapView?>(null) }
    var requestedBefore by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf<LocationDraft?>(initialCoordinate?.let { LocationDraft(it, LocationSource.manualPin) }) }
    var status by remember { mutableStateOf("Loading map...") }
    var locating by remember { mutableStateOf(false) }
    val capturedScope = remember { accountId to conversationId }
    val liveScope by rememberUpdatedState(currentScope)

    var buildingsEnabled by remember { mutableStateOf(false) }
    val buildingsStateDescription = if (buildingsEnabled) "3D buildings enabled" else "2D map"
    var rendererReady by remember { mutableStateOf(false) }
    var layersOpen by remember { mutableStateOf(false) }
    var selectedStyle by remember { mutableStateOf(if (systemDark) "dark" else "positron") }
    LaunchedEffect(systemDark) {
        selectedStyle = if (systemDark) "dark" else "positron"
        buildingsEnabled = false
        mapView?.setTheme(systemDark)
        mapView?.selectStyle(selectedStyle)
    }
    var rationale by remember { mutableStateOf(false) }
    var locateJob by remember { mutableStateOf<Job?>(null) }
    var friendsMapOpen by remember { mutableStateOf(false) }
    val gate = remember { LocationSendGate() }

    val activity = remember(context) { context.findMapActivity() }
    val manager = remember(context) { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun select(value: LocationDraft) { gate.select(value); draft = value }
    fun stopLocate() { locateJob?.cancel(); locateJob = null; locating = false }
    fun acquireCurrentFix() {
        stopLocate()
        locating = true
        status = "Getting current location for this map. Nothing is sent."
        locateJob = scope.launch {
            try {
                // Recheck immediately before the API call; never use a cached last-known fix.
                val precise = granted(Manifest.permission.ACCESS_FINE_LOCATION)
                check(precise || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) { "Location permission is not available." }
                check(LocationManagerCompat.isLocationEnabled(manager)) { "Location services are off." }
                val provider = when {
                    !precise && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> error("No location provider is available.")
                }
                val fix = withTimeout(20_000) { mapCurrentLocationFix(context, manager, provider) }
                    ?: error("No current location was found. Choose a pin or try again.")
                val ageMs = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
                check(ageMs in 0..15_000) { "Location is too old. Choose a pin or try again." }
                check(fix.hasAccuracy()) { "Location accuracy is unavailable." }
                val value = LocationDraft(LocationCoordinate(fix.latitude, fix.longitude), LocationSource.currentFix,
                    fix.accuracy.toDouble(), if (precise) LocationPrecision.precise else LocationPrecision.approximate)
                check(liveScope() == capturedScope) { "Account or conversation changed." }
                select(value)
                mapView?.showPin(value.coordinate)
                status = if (precise) "Current location shown on the map. Nothing is sent." else "Approximate location shown. You can move the pin. Nothing is sent."
            } catch (timeout: TimeoutCancellationException) {
                status = "Location timed out. Choose a pin or try again."
            } catch (cancelled: CancellationException) {
                status = "Location request stopped. Choose a pin or try again."
                throw cancelled
            } catch (error: Exception) {
                status = error.message ?: "Location is unavailable. Choose a pin instead."
            } finally { locating = false }
        }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) acquireCurrentFix()
        } else status = "Location permission was denied. You can still choose a pin without it."
    }
    fun requestPermission() {
        requestedBefore = true
        permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    fun locate() {
        val access = ForegroundLocationAccess(
            fineGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            coarseGranted = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            requestedBefore = requestedBefore,
            shouldShowRationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION) || ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) } == true,
            locationServicesEnabled = LocationManagerCompat.isLocationEnabled(manager),
        )
        when (nextLocateAction(access)) {
            LocateAction.usePreciseFix, LocateAction.useApproximateFix -> acquireCurrentFix()
            LocateAction.requestForeground -> requestPermission()
            LocateAction.explainRationale -> rationale = true
            LocateAction.offerAppSettings -> {
                status = "Permission is unavailable. You can allow location in app settings, or choose a pin."
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }
            LocateAction.offerLocationSettings -> {
                status = "Location services are off. Turn them on, then tap Current location again, or choose a pin."
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { rendererReady = false; layersOpen = false; stopLocate(); mapActive = false; mapView?.disposeMap(); mapView = null }
                Lifecycle.Event.ON_RESUME -> { mapActive = true }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stopLocate(); mapView?.disposeMap(); mapView = null; gate.cancel() }
    }
    LaunchedEffect(accountId, conversationId) {
        if (capturedScope != (accountId to conversationId)) { stopLocate(); gate.cancel(); onDismiss() }
    }
    androidx.activity.compose.BackHandler { stopLocate(); gate.cancel(); onDismiss() }
    MaterialTheme(colorScheme = mapColors) {
    Dialog(onDismissRequest = { stopLocate(); gate.cancel(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(modifier = Modifier.fillMaxSize(), color = mapColors.background, contentColor = mapColors.onBackground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { stopLocate(); gate.cancel(); onDismiss() }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Back" }) { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) }
                Text("Map", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { friendsMapOpen = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Friends map" }) { Icon(Icons.Rounded.Groups, contentDescription = null) }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
            if (mapActive) {
                AndroidView(
                    factory = { viewContext -> GeoLibreLocationMapView(viewContext,
                        onPinSelected = { point -> stopLocate(); select(LocationDraft(point, LocationSource.manualPin)); status = "Pin selected locally. Nothing is sent." },
                        onUnavailable = { status = it }, onAttributionLink = onAttributionLink,
                        onMapReady = { rendererReady = true; mapView?.selectStyle(selectedStyle); if (buildingsEnabled) mapView?.control("toggleBuildings"); status = ""; onMapReady() }, nativeLayers = true,
                        systemDark = systemDark, onStyleChanged = { selectedStyle = it }, onViewChanged = { buildingsEnabled = it },
                    ).also { mapView = it; draft?.let { selected -> it.showPin(selected.coordinate) } } },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.setTheme(systemDark) },
                )
            }
            Column(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledIconButton(onClick = { mapView?.control("zoomIn") }, enabled = rendererReady, colors = controlColors, modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom in" }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.Add, contentDescription = null) }
                FilledIconButton(onClick = { mapView?.control("zoomOut") }, enabled = rendererReady, colors = controlColors, modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom out" }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.Remove, contentDescription = null) }
                FilledIconButton(onClick = { mapView?.control("toggleBuildings") }, enabled = rendererReady, colors = controlColors,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Toggle 3D buildings"; stateDescription = buildingsStateDescription }, shape = androidx.compose.foundation.shape.CircleShape) { BuildingsToggleLabel(if (buildingsEnabled) "2D" else "3D") }
                Box {
                FilledIconButton(onClick = { layersOpen = true }, enabled = rendererReady, colors = controlColors, modifier = Modifier.size(48.dp).semantics { contentDescription = "Map layers" }, shape = androidx.compose.foundation.shape.CircleShape) {
                    Icon(Icons.Rounded.Layers, contentDescription = null)
                }
                ChatyDropdownMenu(expanded = layersOpen, onDismissRequest = { layersOpen = false }, containerColor = mapColors.surfaceContainerHigh) {
                    listOf("liberty" to "Streets", "positron" to "Light", "dark" to "Dark", "bright" to "Bright", "fiord" to "Fiord").forEach { (style, label) ->
                        ChatyDropdownMenuItem(
                            text = { Text(label, color = mapColors.onSurface, fontSize = 15.5.sp, fontWeight = FontWeight.W600) },
                            trailingIcon = { if (selectedStyle == style) Icon(Icons.Rounded.Check, contentDescription = "Selected") },
                            onClick = { layersOpen = false; mapView?.selectStyle(style) },
                        )
                    }
                }
                }
            }
            FilledIconButton(
                onClick = { locate() }, enabled = currentLocationEnabled && !locating,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = if (isLandscape) 72.dp else 16.dp, bottom = 112.dp).size(56.dp).semantics { contentDescription = "Locate current location" },
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = mapColors.primary, contentColor = mapColors.onPrimary, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant),
            ) {
                if (locating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = mapColors.onPrimary)
                else Icon(Icons.Rounded.MyLocation, contentDescription = null)
            }
            }
            if (status.isNotBlank()) Text(status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    }
    if (friendsMapOpen) {
        // Global friends map (frontend-only): honest not-connected preview, no friend
        // data and no sharing. Opened from this map's toolbar; per-chat send flow is untouched.
        FriendMapScreen(
            context = context,
            onDismiss = { friendsMapOpen = false },
            onAttributionLink = onAttributionLink,
            currentLocationEnabled = currentLocationEnabled,
        )
    }
    if (rationale) AlertDialog(onDismissRequest = { rationale = false }, title = { Text("Use your current location?") },
        text = { Text("Chaty needs foreground location access only to find a point you request. Approximate access is accepted. You can choose a map pin without granting location access.") },
        confirmButton = { TextButton(onClick = { rationale = false; requestPermission() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { rationale = false }) { Text("Choose a pin instead") } })
    }
}

private tailrec fun Context.findMapActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findMapActivity()
    else -> null
}

@android.annotation.SuppressLint("MissingPermission")
private suspend fun mapCurrentLocationFix(context: Context, manager: LocationManager, provider: String): Location? =
    suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }

/**
 * Draws the 2D/3D label without creating a semantics node, so the clickable button stays a childless node
 * and exposes its own contentDescription.
 *
 * Intended to use the same inputs as material3 Text (ambient LocalTextStyle, color precedence of style color
 * then LocalContentColor, Clip overflow, softWrap, unlimited lines, the incoming constraints and density).
 * Pixel equivalence with Text is NOT guaranteed and needs runtime validation (font scale 1.0 and 2.0, both
 * orientations). Known risks: TextMeasurer and Text use separate paragraph layout paths; drawText cannot
 * relayout when async font loading resolves; the layout result holder is a plain array written during
 * layout and read during draw, not snapshot state (draw invalidation relies on the draw lambda capturing text and
 * color, assumed, not runtime-verified).
 */
@Composable
private fun BuildingsToggleLabel(text: String) {
    val style = LocalTextStyle.current
    val textColor = style.color.takeOrElse { LocalContentColor.current }
    val mergedStyle = style.merge(color = textColor)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val holder = remember { arrayOfNulls<androidx.compose.ui.text.TextLayoutResult>(1) }
    Spacer(
        Modifier
            // Draw is the OUTER modifier: it wraps the node sized by the layout modifier below. A draw modifier placed
            // inside a layout modifier that never measures its child is never measured, placed or drawn.
            .drawBehind {
                val result = holder[0]
                // text and textColor are read here so this lambda changes with them and draw is re-invoked on 2D/3D
                // and on enabled/disabled color changes even when the measured size is unchanged.
                if (result != null && result.layoutInput.text.text == text) drawText(result, color = textColor)
            }
            .layout { _, constraints ->
                val result = measurer.measure(
                    text = text,
                    style = mergedStyle,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                    constraints = constraints,
                    layoutDirection = layoutDirection,
                    density = this
                )
                holder[0] = result
                layout(result.size.width, result.size.height) {}
            }
    )
}
