package com.example.chat.ui.settings.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.chat.ui.onboarding.ChatyPermissions
import com.example.chat.ui.settings.ChatyPreviewCard
import com.example.chat.ui.settings.ChatySettingsPage
import com.example.chat.ui.settings.ChatySettingsSection
import com.example.chat.ui.settings.ChatySettingsTile
import com.example.chat.ui.settings.ChatySwitchTile
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** permission_handler's PermissionStatus reduced to what the Dart screen
 *  uses: null = "not read yet (checking)", granted covers isGranted +
 *  isLimited (limited is iOS-only; Android reports granted). */
private enum class PermStatus { Checking, Granted, Denied, PermanentlyDenied }

private data class TrackedPermission(
    val id: String,
    /** Runtime permissions to request (permission_handler may fan one
     *  logical permission out to several Android permissions). */
    val androidPermissions: List<String>,
)

private val TRACKED = listOf(
    TrackedPermission("notification", listOfNotNull(ChatyPermissions.notification)),
    TrackedPermission("photos", listOf(ChatyPermissions.photos)),
    TrackedPermission("location",
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
    TrackedPermission("camera", listOf(Manifest.permission.CAMERA)),
    TrackedPermission("microphone", listOf(Manifest.permission.RECORD_AUDIO)),
    TrackedPermission("contacts", listOf(Manifest.permission.READ_CONTACTS)),
)

private fun statusOf(context: Context, activity: Activity?, tracked: TrackedPermission): PermStatus {
    if (tracked.androidPermissions.isEmpty()) return PermStatus.Granted // auto-granted (pre-33 notifications)
    val granted = tracked.androidPermissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    if (granted) return PermStatus.Granted
    // permission_handler's Android permanentlyDenied: denied + no rationale.
    val rationale = activity != null && tracked.androidPermissions.any {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
    return if (rationale) PermStatus.Denied else PermStatus.PermanentlyDenied
}

/** Port of lib/features/settings/permissions/system_permissions_screen.dart.
 *
 *  [triggerTestNotification], [canUseBiometrics] and [authenticateBiometric]
 *  are injected; the host wires ChatyNotificationService.triggerEventNotification
 *  (title "Chaty test notification", body "This is a sample notification from
 *  Chaty.", notifications icon, theme primary color) and LocalLockService
 *  (canUseBiometrics = canCheckBiometrics && availableBiometrics.isNotEmpty,
 *  authenticateBiometric = biometric-only prompt), matching the Dart service. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemPermissionsScreen(
    onBack: () -> Unit,
    triggerTestNotification: () -> Unit,
    canUseBiometrics: suspend () -> Boolean,
    authenticateBiometric: suspend (reason: String) -> Boolean,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var statuses by remember { mutableStateOf(TRACKED.associate { it.id to PermStatus.Checking }) }
    var biometricsAvailable by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var pendingAction by remember { mutableStateOf("none") } // "none" | "requestAll" | "locationFetch"
    var locationFix by remember { mutableStateOf<Location?>(null) }

    fun snack(message: String, color: Color? = null) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    suspend fun refreshAll() {
        val map = TRACKED.associate { it.id to statusOf(context, activity, it) }
        var bio = false
        try {
            bio = canUseBiometrics()
        } catch (_: Exception) {
        }
        statuses = map
        biometricsAvailable = bio
        loading = false
    }

    fun refreshOne(id: String) {
        val tracked = TRACKED.first { it.id == id }
        statuses = statuses + (id to statusOf(context, activity, tracked))
    }

    LaunchedEffect(Unit) { refreshAll() }

    // Dart didChangeAppLifecycleState: re-read after returning from settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { refreshAll() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Single/multi permission requests. Dart _toggle / _requestAll always
    // re-read the status afterwards.
    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        when (pendingAction) {
            "locationFetch" -> {
                val granted = results.values.any { it }
                if (!granted) {
                    snack("Location permission was denied.", colors.error)
                } else {
                    scope.launch {
                        try {
                            locationFix = currentLocation(context)
                        } catch (e: Exception) {
                            snack("Could not read location: $e", colors.error)
                        }
                    }
                }
            }
            "requestAll" -> {
                // Dart _requestAll: count granted (or limited) results.
                val grantedCount = results.values.count { it }
                snack(
                    "$grantedCount of ${results.size} permissions granted.",
                    if (grantedCount == results.size) colors.success else null,
                )
            }
        }
        pendingAction = "none"
        scope.launch { refreshAll() }
    }

    fun request(tracked: TrackedPermission) {
        if (tracked.androidPermissions.isEmpty()) {
            refreshOne(tracked.id)
        } else {
            requestLauncher.launch(tracked.androidPermissions.toTypedArray())
        }
    }

    // Dart _toggle: granted -> honest deep-link; blocked -> settings; else ask.
    fun toggle(tracked: TrackedPermission) {
        try {
            when (statuses[tracked.id]) {
                PermStatus.Granted -> {
                    snack("Turn this off from system settings.")
                    ChatyPermissions.openAppSettings(context)
                }
                PermStatus.PermanentlyDenied -> {
                    snack("This permission is blocked. Enable it in system settings.")
                    ChatyPermissions.openAppSettings(context)
                }
                else -> request(tracked)
            }
        } catch (_: Exception) {
            snack("Could not update this permission.", colors.error)
        } finally {
            refreshOne(tracked.id)
        }
    }

    // Dart _fetchLocation.
    fun fetchLocation() {
        scope.launch {
            try {
                val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val serviceEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                if (!serviceEnabled) {
                    snack("Location services are turned off on this device.", colors.error)
                    return@launch
                }
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    pendingAction = "locationFetch"
                    requestLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ))
                    return@launch
                }
                locationFix = currentLocation(context)
            } catch (e: Exception) {
                snack("Could not read location: $e", colors.error)
            } finally {
                refreshOne("location")
            }
        }
    }

    // Dart _requestAll.
    fun requestAll() {
        try {
            val all = TRACKED.flatMap { it.androidPermissions }.toTypedArray()
            if (all.isEmpty()) {
                snack("6 of 6 permissions granted.", colors.success)
                scope.launch { refreshAll() }
                return
            }
            pendingAction = "requestAll"
            requestLauncher.launch(all)
        } catch (_: Exception) {
            snack("Could not request permissions.", colors.error)
        }
    }

    locationFix?.let { fix ->
        AlertDialog(
            onDismissRequest = { locationFix = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = colors.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Current GPS location")
                }
            },
            text = {
                Column {
                    Text("Latitude: ${"%.5f".format(fix.latitude)}", fontWeight = FontWeight.Bold)
                    Text("Longitude: ${"%.5f".format(fix.longitude)}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Accuracy: ${"%.0f".format(fix.accuracy)} m",
                        fontSize = 12.sp,
                        color = colors.foregroundSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { locationFix = null }) { Text("Close") }
            },
        )
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ChatySettingsPage(
                title = "App Permissions & Hardware",
                subtitle = "Notifications, media, location, camera, mic & more",
                onBack = onBack,
                trailingHeaderWidget = {
                    IconButton(onClick = { scope.launch { refreshAll() } }, enabled = !loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Re-check permissions")
                    }
                },
            ) {
                // Live status center.
                ChatyPreviewCard(title = "Permission Status Center") {
                    Column {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusChip("Notifications", if (loading) null else statuses["notification"] == PermStatus.Granted, Icons.Rounded.NotificationsActive)
                            StatusChip("Media & Photos", if (loading) null else statuses["photos"] == PermStatus.Granted, Icons.Rounded.PermMedia)
                            StatusChip("Location", if (loading) null else statuses["location"] == PermStatus.Granted, Icons.Rounded.LocationOn)
                            StatusChip("Camera", if (loading) null else statuses["camera"] == PermStatus.Granted, Icons.Rounded.CameraAlt)
                            StatusChip("Microphone", if (loading) null else statuses["microphone"] == PermStatus.Granted, Icons.Rounded.Mic)
                            StatusChip("Contacts", if (loading) null else statuses["contacts"] == PermStatus.Granted, Icons.Rounded.Contacts)
                            StatusChip("Biometrics", if (loading) null else biometricsAvailable, Icons.Rounded.Fingerprint)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (loading) "Checking current permission status..."
                            else "Tap a permission to grant it, or manage it in system settings.",
                            fontSize = 11.5.sp,
                            color = colors.foregroundSecondary.copy(alpha = 0.6f),
                        )
                    }
                }

                // 1. Notifications
                ChatySettingsSection(
                    title = "Push Notifications & Alerts",
                    description = "Receive alerts for calls, mentions, and incoming messages.",
                ) {
                    PermissionSwitch(
                        icon = Icons.Rounded.NotificationsActive,
                        color = colors.primary,
                        title = "Notification Permission",
                        subtitle = "Allow Chaty to display alerts and heads-up notifications",
                        status = statuses["notification"],
                        onToggle = { toggle(TRACKED[0]) },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.Send,
                        iconColor = colors.info,
                        title = "Send a test notification",
                        subtitle = "Display a sample Chaty notification now",
                        onTap = {
                            triggerTestNotification()
                            snack("Test notification sent.")
                        },
                    )
                }

                // 2. Media, Photos & Storage
                ChatySettingsSection(
                    title = "Media, Photos & File System",
                    description = "Share images, videos, audio notes, and attachments in chats.",
                ) {
                    PermissionSwitch(
                        icon = Icons.Rounded.PermMedia,
                        color = colors.info,
                        title = "Media & Photo Library",
                        subtitle = "Access the gallery to send photos and video",
                        status = statuses["photos"],
                        onToggle = { toggle(TRACKED[1]) },
                    )
                }

                // 3. Location
                ChatySettingsSection(
                    title = "Location & Map Coordinates",
                    description = "Share your live location or drop a pin in a conversation.",
                ) {
                    PermissionSwitch(
                        icon = Icons.Rounded.LocationOn,
                        color = colors.warning,
                        title = "Location Permission",
                        subtitle = "Access device GPS to share your location in chats",
                        status = statuses["location"],
                        onToggle = { toggle(TRACKED[2]) },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.MyLocation,
                        iconColor = colors.warning,
                        title = "Fetch current location",
                        subtitle = "Read your real GPS coordinates now",
                        onTap = { fetchLocation() },
                    )
                }

                // 4. Hardware sensors
                ChatySettingsSection(
                    title = "Hardware & Sensors",
                    description = "Control camera, microphone, contacts, and biometric access.",
                ) {
                    PermissionSwitch(
                        icon = Icons.Rounded.CameraAlt,
                        color = colors.primary,
                        title = "Camera",
                        subtitle = "Take photos, record stories and start video calls",
                        status = statuses["camera"],
                        onToggle = { toggle(TRACKED[3]) },
                    )
                    PermissionSwitch(
                        icon = Icons.Rounded.Mic,
                        color = colors.error,
                        title = "Microphone",
                        subtitle = "Record voice messages and voice calls",
                        status = statuses["microphone"],
                        onToggle = { toggle(TRACKED[4]) },
                    )
                    PermissionSwitch(
                        icon = Icons.Rounded.Contacts,
                        color = colors.success,
                        title = "Contacts",
                        subtitle = "Discover which of your contacts are on Chaty",
                        status = statuses["contacts"],
                        onToggle = { toggle(TRACKED[5]) },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.Fingerprint,
                        iconColor = colors.primary,
                        title = "Biometrics & Fingerprint",
                        subtitle = if (biometricsAvailable) "Tap to test biometric unlock"
                            else "No enrolled biometrics detected on this device",
                        badgeText = if (loading) null else (if (biometricsAvailable) "Available" else "Unavailable"),
                        badgeColor = if (biometricsAvailable) colors.success else colors.disabled,
                        onTap = {
                            scope.launch {
                                if (!biometricsAvailable) {
                                    snack("No biometrics are enrolled on this device.", colors.error)
                                    return@launch
                                }
                                val ok = authenticateBiometric("Verify your identity for Chaty")
                                snack(
                                    if (ok) "Biometric verification succeeded."
                                    else "Biometric verification failed or cancelled.",
                                    if (ok) colors.success else colors.error,
                                )
                            }
                        },
                    )
                }

                // 5. Bulk actions
                ChatySettingsSection(title = "All System Permissions") {
                    ChatySettingsTile(
                        icon = Icons.Rounded.DoneAll,
                        iconColor = colors.success,
                        title = "Request all permissions",
                        subtitle = "Prompt for notifications, media, location, camera, mic & contacts",
                        onTap = { requestAll() },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.Settings,
                        iconColor = colors.foregroundSecondary,
                        title = "Open system settings",
                        subtitle = "Manage every Chaty permission directly in the OS",
                        onTap = { ChatyPermissions.openAppSettings(context) },
                    )
                }
            }
        }
    }
}

/** Dart _permissionSwitch: granted state + '(blocked in settings)' suffix. */
@Composable
private fun PermissionSwitch(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    status: PermStatus?,
    onToggle: () -> Unit,
) {
    val granted = status == PermStatus.Granted
    val blocked = status == PermStatus.PermanentlyDenied && !granted
    ChatySwitchTile(
        icon = icon,
        iconColor = color,
        title = title,
        subtitle = if (blocked) "$subtitle (blocked in settings)" else subtitle,
        value = granted,
        onChanged = { onToggle() },
    )
}

/** Dart _buildStatusChip. */
@Composable
private fun StatusChip(label: String, granted: Boolean?, icon: ImageVector) {
    val colors = LocalAppColors.current
    val color = when (granted) {
        null -> colors.foregroundSecondary
        true -> colors.success
        false -> colors.error
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .border(1.dp, color, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = color)
        Spacer(Modifier.width(4.dp))
        Icon(
            when (granted) {
                null -> Icons.Rounded.HourglassBottom
                true -> Icons.Rounded.Check
                false -> Icons.Rounded.Close
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** Geolocator.getCurrentPosition equivalent for minSdk 24:
 *  getCurrentLocation on API 30+, requestSingleUpdate below it. */
@SuppressLint("MissingPermission")
private suspend fun currentLocation(context: Context): Location = suspendCancellableCoroutine { cont ->
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = when {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> {
            cont.resumeWith(Result.failure(Exception("No location provider available")))
            return@suspendCancellableCoroutine
        }
    }
    if (Build.VERSION.SDK_INT >= 30) {
        val cancellation = android.os.CancellationSignal()
        cont.invokeOnCancellation { cancellation.cancel() }
        manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location ->
            if (location != null) cont.resumeWith(Result.success(location))
            else cont.resumeWith(Result.failure(Exception("Location unavailable")))
        }
    } else {
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                cont.resumeWith(Result.success(location))
            }
        }
        cont.invokeOnCancellation { manager.removeUpdates(listener) }
        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
    }
}


