package com.example.chat.island.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.chat.R
import com.example.chat.island.engine.DynamicIslandUi
import com.example.chat.island.engine.IslandService
import com.example.chat.island.engine.IslandMode
import com.example.chat.island.engine.IslandLevel
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.core.Perms
import java.util.Locale

@Composable
fun AboutScreen(tick: Int) {
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
        HeroCard(context)
        DeviceInfoCard()
        StatusCard()
        PermissionChecklistCard(context, tick)
        GestureGuideCard()
        AccessibilityHelpCard(context, tick)
        BackgroundTroubleshootingCard(context)
        Spacer(Modifier.height(16.dp))
    }
}

private data class VersionInfo(val name: String, val code: Long)

private fun readVersion(context: Context): VersionInfo = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    VersionInfo(
        name = info.versionName ?: "?",
        code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        },
    )
}.getOrDefault(VersionInfo("?", 0L))

@Composable
private fun HeroCard(context: Context) {
    val version = remember { readVersion(context) }
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val scale by IslandSettings.scale.collectAsState()
            val corner by IslandSettings.corner.collectAsState()
            val animSpeed by IslandSettings.animSpeed.collectAsState()
            val glow by IslandSettings.glow.collectAsState()
            DynamicIslandUi(
                mode = IslandMode.Media("Chaty", "Preview", true),
                level = IslandLevel.Compact,
                scale = scale,
                cornerFactor = corner,
                animSpeed = animSpeed,
                glow = glow,
                haptics = false,
                onClick = {},
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.about_app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.about_version, version.name, version.code),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceInfoCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.about_device_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    R.string.about_device_line,
                    Build.MANUFACTURER.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                    },
                    Build.MODEL,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.about_android_line,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard() {
    val running by IslandService.running.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.about_status_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(
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
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.padding(start = 8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (!granted && actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun PermissionChecklistCard(context: Context, tick: Int) {
    val accessibility = remember(tick) { Perms.accessibility(context) }
    val listener = remember(tick) { Perms.listener(context) }
    val post = remember(tick) { Perms.post(context) }
    val bluetooth = remember(tick) { Perms.bluetooth(context) }
    val battery = remember(tick) { Perms.battery(context) }

    val postLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.about_permissions_title),
                style = MaterialTheme.typography.titleMedium,
            )
            PermissionRow(
                label = stringResource(R.string.about_perm_accessibility),
                granted = accessibility,
                actionLabel = stringResource(R.string.about_fix),
                onAction = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
            PermissionRow(
                label = stringResource(R.string.about_perm_listener),
                granted = listener,
                actionLabel = stringResource(R.string.about_fix),
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    label = stringResource(R.string.about_perm_post),
                    granted = post,
                    actionLabel = stringResource(R.string.about_fix),
                    onAction = { postLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionRow(
                    label = stringResource(R.string.about_perm_bluetooth),
                    granted = bluetooth,
                    actionLabel = stringResource(R.string.about_fix),
                    onAction = { bluetoothLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT) },
                )
            }
            PermissionRow(
                label = stringResource(R.string.about_perm_battery),
                granted = battery,
                actionLabel = stringResource(R.string.about_fix),
                onAction = { openBatteryExemption(context) },
            )
        }
    }
}

@Composable
private fun GestureGuideCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.about_gestures_title),
                style = MaterialTheme.typography.titleMedium,
            )
            listOf(
                R.string.about_gesture_tap,
                R.string.about_gesture_long_press,
                R.string.about_gesture_swipe_horizontal,
                R.string.about_gesture_swipe_up,
            ).forEach { lineRes ->
                Text(
                    stringResource(lineRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Extra help shown only while the accessibility service is not yet enabled. */
@Composable
private fun AccessibilityHelpCard(context: Context, tick: Int) {
    val accessibility = remember(tick) { Perms.accessibility(context) }
    if (accessibility) return

    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.about_accessibility_help_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.about_accessibility_help_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text(stringResource(R.string.home_open_accessibility_settings)) }
                OutlinedButton(onClick = { openAppInfo(context) }) {
                    Text(stringResource(R.string.home_app_info))
                }
            }
        }
    }
}

private val STRICT_KILL_MANUFACTURERS = listOf(
    "xiaomi", "redmi", "poco", "oppo", "vivo", "realme", "oneplus", "samsung", "huawei", "honor",
)

@Composable
private fun BackgroundTroubleshootingCard(context: Context) {
    val strict = remember {
        Build.MANUFACTURER.lowercase(Locale.US) in STRICT_KILL_MANUFACTURERS
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.about_background_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (strict) {
                Text(
                    stringResource(R.string.about_background_strict_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringResource(R.string.about_background_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openBatteryExemption(context) }) {
                    Text(stringResource(R.string.home_battery_fix))
                }
                OutlinedButton(onClick = { openAppInfo(context) }) {
                    Text(stringResource(R.string.home_app_info))
                }
            }
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com")),
                )
            }) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                Spacer(Modifier.padding(start = 4.dp))
                Text(stringResource(R.string.about_dontkillmyapp))
            }
        }
    }
}

private fun openBatteryExemption(context: Context) {
    val request = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    try {
        context.startActivity(request)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
