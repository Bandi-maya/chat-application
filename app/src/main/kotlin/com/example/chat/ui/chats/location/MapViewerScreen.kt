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

    var rationale by remember { mutableStateOf(false) }
    var locateJob by remember { mutableStateOf<Job?>(null) }
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
                Lifecycle.Event.ON_PAUSE -> { stopLocate(); mapActive = false; mapView?.disposeMap(); mapView = null }
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
    Dialog(onDismissRequest = { stopLocate(); gate.cancel(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { stopLocate(); gate.cancel(); onDismiss() }) { Text("Back") }
                Text("Map", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { locate() }, enabled = currentLocationEnabled && !locating) { Text(if (locating) "Locating..." else "Locate") }
            }
            if (mapActive) {
                AndroidView(
                    factory = { viewContext -> GeoLibreLocationMapView(viewContext,
                        onPinSelected = { point -> stopLocate(); select(LocationDraft(point, LocationSource.manualPin)); status = "Pin selected locally. Nothing is sent." },
                        onUnavailable = { status = it }, onAttributionLink = onAttributionLink,
                        onMapReady = { status = ""; onMapReady() },
                    ).also { mapView = it; draft?.let { selected -> it.showPin(selected.coordinate) } } },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
            if (status.isNotBlank()) Text(status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    }
    if (rationale) AlertDialog(onDismissRequest = { rationale = false }, title = { Text("Use your current location?") },
        text = { Text("Chaty needs foreground location access only to find a point you request. Approximate access is accepted. You can choose a map pin without granting location access.") },
        confirmButton = { TextButton(onClick = { rationale = false; requestPermission() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { rationale = false }) { Text("Choose a pin instead") } })
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
