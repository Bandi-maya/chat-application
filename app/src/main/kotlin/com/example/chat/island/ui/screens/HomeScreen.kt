package com.example.chat.island.ui.screens

import com.example.chat.island.ui.components.IslandIcon as Icon
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.chat.R
import com.example.chat.island.engine.DynamicIslandUi
import com.example.chat.island.core.IslandControl
import com.example.chat.island.engine.IslandController
import com.example.chat.island.engine.IslandService
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.engine.IslandMode
import com.example.chat.island.engine.IslandLevel
import com.example.chat.island.core.Perms

@Composable
fun HomeScreen(tick: Int) {
    val context = LocalContext.current
    val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        StatusCard(context)
        PreviewCard()
        AccessibilityWarningCard(context, tick)
        BluetoothCard(context, tick)
        BatteryCard(context, tick)
        DemoCard(
            titleRes = R.string.test_basic_demos,
            demos = listOf(
                R.string.demo_notification to { IslandController.demoNotification() },
                R.string.demo_charging to { IslandController.demoCharging() },
                R.string.demo_timer to { IslandController.demoTimer(60) },
                R.string.demo_media to { IslandController.demoMedia() },
                R.string.demo_incoming_call to { IslandController.demoIncomingCall() },
                R.string.demo_end_call to { IslandController.demoEndCall() },
                R.string.demo_earbuds to { IslandController.demoEarbudsConnected() },
                R.string.demo_low_battery to { IslandController.demoLowBattery() },
                R.string.demo_silent to { IslandController.demoSilentMode() },
                R.string.demo_flashlight to { IslandController.demoFlashlight() },
            ),
        )
        DemoCard(
            titleRes = R.string.test_live_demos,
            demos = listOf(
                R.string.demo_navigation to { IslandController.demoNavigation() },
                R.string.demo_tracking to { IslandController.demoOrderTracking() },
                R.string.demo_download to { IslandController.demoDownload() },
                R.string.demo_clear_live to { IslandController.clearLiveActivities() },
            ),
        )
        DemoCard(
            titleRes = R.string.test_combo_demos,
            demos = listOf(
                R.string.demo_queued_notifications to { IslandController.demoQueuedNotifications(3) },
                R.string.demo_navigation_music to { IslandController.demoNavigationWithMusic() },
                R.string.demo_clear_music to { IslandController.clearMusic() },
            ),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusCard(context: Context) {
    val running by IslandService.running.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_status_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        if (running) R.string.home_status_running else R.string.home_status_stopped,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (running) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(
                checked = running,
                onCheckedChange = { want ->
                    if (want) IslandControl.start(context) else IslandControl.stop(context)
                },
            )
        }
    }
}

/** Live island preview reflecting the current shape/animation settings. */
@Composable
private fun PreviewCard() {
    val scale by IslandSettings.scale.collectAsState()
    val widthScale by IslandSettings.widthScale.collectAsState()
    val heightScale by IslandSettings.heightScale.collectAsState()
    val corner by IslandSettings.corner.collectAsState()
    val animSpeed by IslandSettings.animSpeed.collectAsState()
    val glow by IslandSettings.glow.collectAsState()
    val haptics by IslandSettings.haptics.collectAsState()

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            DynamicIslandUi(
                mode = IslandMode.Media("Chaty", "Preview", true),
                level = IslandLevel.Compact,
                scale = scale,
                cornerFactor = corner,
                animSpeed = animSpeed,
                glow = glow,
                haptics = haptics,
                onClick = {},
                modifier = Modifier.graphicsLayer {
                    scaleX = widthScale
                    scaleY = heightScale
                },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AccessibilityWarningCard(context: Context, tick: Int) {
    val granted = remember(tick) { Perms.accessibility(context) }
    if (granted) return

    WarningCard(
        icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
        title = stringResource(R.string.home_accessibility_warning_title),
        body = stringResource(R.string.home_accessibility_warning_body),
    ) {
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) {
            Text(stringResource(R.string.home_open_accessibility_settings))
        }
        OutlinedButton(onClick = { openAppInfo(context) }) {
            Text(stringResource(R.string.home_app_info))
        }
    }
}

@Composable
private fun BluetoothCard(context: Context, tick: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val granted = remember(tick) { Perms.bluetooth(context) }
    if (granted) return

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    WarningCard(
        icon = { Icon(Icons.Outlined.BluetoothDisabled, contentDescription = null) },
        title = stringResource(R.string.home_bluetooth_title),
        body = stringResource(R.string.home_bluetooth_body),
    ) {
        Button(onClick = {
            launcher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        }) {
            Text(stringResource(R.string.home_grant_bluetooth))
        }
    }
}

@Composable
private fun BatteryCard(context: Context, tick: Int) {
    val exempt = remember(tick) { Perms.battery(context) }
    if (exempt) return

    WarningCard(
        icon = { Icon(Icons.Outlined.BatteryAlert, contentDescription = null) },
        title = stringResource(R.string.home_battery_title),
        body = stringResource(R.string.home_battery_body),
    ) {
        Button(onClick = {
            val request = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            )
            try {
                context.startActivity(request)
            } catch (e: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }) {
            Text(stringResource(R.string.home_battery_fix))
        }
    }
}

@Composable
private fun WarningCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    actions: @Composable () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.padding(start = 8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoCard(titleRes: Int, demos: List<Pair<Int, () -> Unit>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                demos.forEach { (labelRes, run) ->
                    OutlinedButton(onClick = run) {
                        Text(stringResource(labelRes))
                    }
                }
            }
        }
    }
}

internal fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}
