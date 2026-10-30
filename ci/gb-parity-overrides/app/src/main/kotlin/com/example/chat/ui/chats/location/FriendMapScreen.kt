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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.chat.ui.kit.ChatyDropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Snap Map-style friends map, FRONTEND ONLY. The Chaty backend has no location-sharing
 * endpoints yet, so this screen is an honest preview: it shows the map, the layout and the
 * controls, and says clearly that friend locations are not connected. It renders NO friend
 * pins (there is no real friend data and none is invented), publishes NO location and keeps
 * every sharing control visibly disabled. The user's own pin is local-only.
 *
 * The existing per-chat location flow (MapViewerScreen / LocationSendGate) is untouched.
 * Opening this screen starts map tile requests, not GPS; GPS runs only on an explicit
 * locate tap. Provider attribution and the online-service notice remain in the map.
 */
@Composable
fun FriendMapScreen(
    context: Context,
    onDismiss: () -> Unit,
    onAttributionLink: (Uri) -> Unit,
    currentLocationEnabled: Boolean = true,
    onMapReady: () -> Unit = {},
) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val systemDark = (LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val mapColors = if (systemDark) darkColorScheme() else lightColorScheme()
    val controlColors = IconButtonDefaults.filledIconButtonColors(containerColor = mapColors.secondaryContainer, contentColor = mapColors.onSecondaryContainer, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant)
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var mapActive by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var mapView by remember { mutableStateOf<GeoLibreLocationMapView?>(null) }
    var requestedBefore by rememberSaveable { mutableStateOf(false) }
    var ownPin by remember { mutableStateOf<FriendMapOwnPin?>(null) }
    var status by remember { mutableStateOf("Loading map...") }
    var locating by remember { mutableStateOf(false) }

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

    // The only connection state that exists until the backend phase lands.
    val connection: FriendMapConnection = FriendMapConnection.NotConnected

    val activity = remember(context) { context.findFriendMapActivity() }
    val manager = remember(context) { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun stopLocate() { locateJob?.cancel(); locateJob = null; locating = false }
    fun acquireCurrentFix() {
        stopLocate()
        locating = true
        status = "Getting your location for this map only. Nothing is shared."
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
                val fix = withTimeout(20_000) { friendMapCurrentLocationFix(context, manager, provider) }
                    ?: error("No current location was found. Try again.")
                val ageMs = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
                check(ageMs in 0..15_000) { "Location is too old. Try again." }
                check(fix.hasAccuracy()) { "Location accuracy is unavailable." }
                val pin = FriendMapOwnPin(LocationCoordinate(fix.latitude, fix.longitude), precise)
                ownPin = pin
                mapView?.showPin(pin.coordinate)
                status = if (precise) "Your location is shown on this map only. Nothing is shared." else "Approximate location shown on this map only. Nothing is shared."
            } catch (timeout: TimeoutCancellationException) {
                status = "Location timed out. Try again."
            } catch (cancelled: CancellationException) {
                status = "Location request stopped."
                throw cancelled
            } catch (error: Exception) {
                status = error.message ?: "Location is unavailable."
            } finally { locating = false }
        }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) acquireCurrentFix()
        } else status = "Location permission was denied. The map still works without it."
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
                status = "Permission is unavailable. You can allow location in app settings."
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }
            LocateAction.offerLocationSettings -> {
                status = "Location services are off. Turn them on, then tap Current location again."
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
        onDispose { lifecycle.removeObserver(observer); stopLocate(); mapView?.disposeMap(); mapView = null }
    }
    androidx.activity.compose.BackHandler { stopLocate(); onDismiss() }
    MaterialTheme(colorScheme = mapColors) {
    Dialog(onDismissRequest = { stopLocate(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(modifier = Modifier.fillMaxSize(), color = mapColors.background, contentColor = mapColors.onBackground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { stopLocate(); onDismiss() }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Back" }) { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) }
                Text("Friends map", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(48.dp))
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
            if (mapActive) {
                AndroidView(
                    factory = { viewContext -> GeoLibreLocationMapView(viewContext,
                        // Friend pins are never fabricated; map taps place nothing in this preview.
                        onPinSelected = { },
                        onUnavailable = { status = it }, onAttributionLink = onAttributionLink,
                        onMapReady = { rendererReady = true; mapView?.selectStyle(selectedStyle); if (buildingsEnabled) mapView?.control("toggleBuildings"); status = ""; onMapReady() }, nativeLayers = true,
                        systemDark = systemDark, onStyleChanged = { selectedStyle = it }, onViewChanged = { buildingsEnabled = it },
                    ).also { mapView = it; ownPin?.let { pin -> it.showPin(pin.coordinate) } } },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.setTheme(systemDark) },
                )
            }
            // Honest top chip: friend sharing is not live.
            Surface(
                color = mapColors.surfaceContainerHigh,
                contentColor = mapColors.onSurface,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).semantics { contentDescription = FriendMapCopy.MAP_STATUS_CHIP },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(14.dp), tint = mapColors.onSurfaceVariant)
                    Spacer(Modifier.size(6.dp))
                    Text(FriendMapCopy.MAP_STATUS_CHIP, fontSize = 12.sp, fontWeight = FontWeight.W600, color = mapColors.onSurfaceVariant)
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledIconButton(onClick = { mapView?.control("zoomIn") }, enabled = rendererReady, colors = controlColors, modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom in" }, shape = CircleShape) { Icon(Icons.Rounded.Add, contentDescription = null) }
                FilledIconButton(onClick = { mapView?.control("zoomOut") }, enabled = rendererReady, colors = controlColors, modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom out" }, shape = CircleShape) { Icon(Icons.Rounded.Remove, contentDescription = null) }
                FilledIconButton(onClick = { mapView?.control("toggleBuildings") }, enabled = rendererReady, colors = controlColors,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Toggle 3D buildings"; stateDescription = buildingsStateDescription }, shape = CircleShape) { Icon(Icons.Rounded.ViewInAr, contentDescription = null) }
                Box {
                FilledIconButton(onClick = { layersOpen = true }, enabled = rendererReady, colors = controlColors, modifier = Modifier.size(48.dp).semantics { contentDescription = "Map layers" }, shape = CircleShape) {
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
            // Bottom panel: Snap Map-style friends tray, honest not-connected content.
            val panelMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
            Surface(
                color = mapColors.surfaceContainerHigh,
                contentColor = mapColors.onSurface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = panelMaxHeight),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).background(mapColors.onSurfaceVariant.copy(alpha = 0.4f), CircleShape))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Friends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.W700)
                            Text("0 sharing with you", fontSize = 12.5.sp, color = mapColors.onSurfaceVariant)
                        }
                        FilledIconButton(
                            onClick = { locate() }, enabled = currentLocationEnabled && !locating,
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Show my location on this map only" },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = mapColors.primary, contentColor = mapColors.onPrimary, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant),
                        ) {
                            if (locating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = mapColors.onPrimary)
                            else Icon(Icons.Rounded.MyLocation, contentDescription = null)
                        }
                    }
                    if (status.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(status, fontSize = 12.sp, color = mapColors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    when (connection) {
                        FriendMapConnection.NotConnected -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(36.dp), tint = mapColors.onSurfaceVariant)
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text(FriendMapCopy.NOT_CONNECTED_TITLE, fontWeight = FontWeight.W600, fontSize = 14.5.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(FriendMapCopy.NOT_CONNECTED_BODY, fontSize = 12.5.sp, color = mapColors.onSurfaceVariant, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = mapColors.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(20.dp), tint = mapColors.onSurfaceVariant)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your location: not shared", fontWeight = FontWeight.W600, fontSize = 14.sp)
                            Text(FriendMapCopy.OWN_LOCATION_CAPTION, fontSize = 12.sp, color = mapColors.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Who could see you (not active yet)", fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = mapColors.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    FriendMapVisibilityPreview.entries.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth().alpha(0.55f).semantics(mergeDescendants = true) { stateDescription = "Not available until the Chaty backend is connected" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = false, enabled = false, onClick = { })
                            Spacer(Modifier.size(4.dp))
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(choice.label, fontSize = 14.sp, fontWeight = FontWeight.W600)
                                Text(choice.description, fontSize = 12.sp, color = mapColors.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(FriendMapCopy.VISIBILITY_INACTIVE_CAPTION, fontSize = 12.sp, color = mapColors.onSurfaceVariant, lineHeight = 16.sp)
                    Spacer(Modifier.height(if (isLandscape) 16.dp else 8.dp))
                }
            }
            }
        }
    }
    }
    if (rationale) AlertDialog(onDismissRequest = { rationale = false }, title = { Text("Show your location on this map?") },
        text = { Text("Chaty needs foreground location access only to show your own position on this map. Approximate access is accepted. Nothing is shared with anyone.") },
        confirmButton = { TextButton(onClick = { rationale = false; requestPermission() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { rationale = false }) { Text("Not now") } })
    }
}

private tailrec fun Context.findFriendMapActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findFriendMapActivity()
    else -> null
}

@android.annotation.SuppressLint("MissingPermission")
private suspend fun friendMapCurrentLocationFix(context: Context, manager: LocationManager, provider: String): Location? =
    suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }
