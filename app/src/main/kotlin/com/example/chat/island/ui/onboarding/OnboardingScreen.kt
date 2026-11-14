package com.example.chat.island.ui.onboarding

import com.example.chat.island.ui.components.IslandIcon as Icon
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.chat.R
import com.example.chat.island.engine.DynamicIslandUi
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.engine.IslandMode
import com.example.chat.island.engine.IslandLevel
import com.example.chat.island.core.Perms
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(tick: Int) {
    val context = LocalContext.current
    var disclosure by remember { mutableStateOf<com.example.chat.island.core.IslandPermissionAccess?>(null) }
    disclosure?.let { access -> com.example.chat.island.core.IslandPermissionDisclosure(access,
        onContinue = { disclosure = null; access.open(context) }, onDismiss = { disclosure = null }) }
    val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    // Permission state, recomputed whenever the host's resume tick changes.
    val accessibility = remember(tick) { Perms.accessibility(context) }
    val overlay = remember(tick) { Perms.overlay(context) }
    val listener = remember(tick) { Perms.listener(context) }
    val post = remember(tick) { Perms.post(context) }
    val bluetooth = remember(tick) { Perms.bluetooth(context) }
    val battery = remember(tick) { Perms.battery(context) }

    val coreGranted = (accessibility || overlay) && listener && post
    val coreSteps = 3
    val coreDone = (if (accessibility || overlay) 1 else 0) +
        (if (listener) 1 else 0) +
        (if (post) 1 else 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        HeroCard()
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.onboarding_progress, coreDone, coreSteps),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            LinearProgressIndicator(
                progress = { coreDone / coreSteps.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---- Core steps -------------------------------------------------
        PermissionStepCard(
            title = stringResource(R.string.onboarding_accessibility_title),
            why = stringResource(R.string.onboarding_accessibility_why),
            granted = accessibility,
            satisfied = accessibility || overlay,
            required = true,
            deferNote = if (!accessibility && overlay) {
                stringResource(R.string.onboarding_accessibility_deferred)
            } else {
                null
            },
            actionLabel = stringResource(R.string.home_open_accessibility_settings),
            onAction = { disclosure = com.example.chat.island.core.IslandPermissionAccess.Accessibility },
            extraActionLabel = stringResource(R.string.home_app_info),
            onExtraAction = { openAppInfoSettings(context) },
        )

        if (!accessibility) {
            PermissionStepCard(
                title = stringResource(R.string.onboarding_overlay_title),
                why = stringResource(R.string.onboarding_overlay_why),
                granted = overlay,
                satisfied = overlay,
                required = false,
                deferNote = null,
                actionLabel = stringResource(R.string.onboarding_overlay_action),
                onAction = { disclosure = com.example.chat.island.core.IslandPermissionAccess.Overlay },
                extraActionLabel = null,
                onExtraAction = null,
            )
        }

        PermissionStepCard(
            title = stringResource(R.string.onboarding_listener_title),
            why = stringResource(R.string.onboarding_listener_why),
            granted = listener,
            satisfied = listener,
            required = true,
            deferNote = null,
            actionLabel = stringResource(R.string.onboarding_listener_action),
            onAction = { disclosure = com.example.chat.island.core.IslandPermissionAccess.Notifications },
            extraActionLabel = stringResource(R.string.home_app_info),
            onExtraAction = { openAppInfoSettings(context) },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val postLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            PermissionStepCard(
                title = stringResource(R.string.onboarding_post_title),
                why = stringResource(R.string.onboarding_post_why),
                granted = post,
                satisfied = post,
                required = true,
                deferNote = null,
                actionLabel = stringResource(R.string.onboarding_post_action),
                onAction = { postLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                extraActionLabel = null,
                onExtraAction = null,
            )
        }

        // ---- Optional steps ----------------------------------------------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            OptionalStepCard(
                title = stringResource(R.string.onboarding_bluetooth_title),
                why = stringResource(R.string.onboarding_bluetooth_why),
                granted = bluetooth,
                actionLabel = stringResource(R.string.home_grant_bluetooth),
                onAction = { bluetoothLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT) },
            )
        }

        OptionalStepCard(
            title = stringResource(R.string.onboarding_battery_title),
            why = stringResource(R.string.onboarding_battery_why),
            granted = battery,
            actionLabel = stringResource(R.string.home_battery_fix),
            onAction = { com.example.chat.island.core.Perms.openBatterySettings(context) },
        )

        Button(
            onClick = {
                if (!coreGranted) com.example.chat.island.core.IslandControl.stop(context)
                IslandSettings.onboarded.value = true
                IslandSettings.save()
            },
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_finish))
        }
        if (!coreGranted) {
            Text(
                stringResource(R.string.onboarding_finish_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Slowly cycling island preview so the hero feels alive. */
@Composable
private fun HeroCard() {
    var stage by remember { mutableIntStateOf(0) }
    val resumed = com.example.chat.ui.island.rememberIslandHostResumed()
    val reducedMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    LaunchedEffect(resumed, reducedMotion) {
        if (!resumed || reducedMotion) return@LaunchedEffect
        while (true) {
            delay(2600)
            stage = (stage + 1) % 3
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboardingHero",
            ) { s ->
                DynamicIslandUi(
                    mode = when (s) {
                        0 -> IslandMode.Idle
                        1 -> IslandMode.Media("Chaty", "Preview", true)
                        else -> IslandMode.Notification("Chaty", "Preview", "Your notifications and activities appear here")
                    },
                    level = IslandLevel.Compact,
                    scale = 1f,
                    cornerFactor = 1f,
                    animSpeed = 1f,
                    glow = true,
                    haptics = false,
                    onClick = {},
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.onboarding_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionStepCard(
    title: String,
    why: String,
    granted: Boolean,
    satisfied: Boolean,
    required: Boolean,
    deferNote: String?,
    actionLabel: String,
    onAction: () -> Unit,
    extraActionLabel: String?,
    onExtraAction: (() -> Unit)?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (satisfied) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = if (satisfied) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.padding(start = 8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (required) {
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(
                        stringResource(R.string.onboarding_required_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(why, style = MaterialTheme.typography.bodyMedium)
            if (deferNote != null) {
                Text(
                    deferNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                    if (extraActionLabel != null && onExtraAction != null) {
                        TextButton(onClick = onExtraAction) { Text(extraActionLabel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionalStepCard(
    title: String,
    why: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    // Optional steps are deferrable: "Later" simply collapses the step for
    // this run; onboarding completes without them.
    var deferred by rememberSaveable(title) { mutableStateOf(false) }
    if (granted || deferred) {
        if (granted) {
            PermissionStepCard(
                title = title,
                why = why,
                granted = true,
                satisfied = true,
                required = false,
                deferNote = null,
                actionLabel = actionLabel,
                onAction = onAction,
                extraActionLabel = null,
                onExtraAction = null,
            )
        }
        return
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(start = 8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.padding(start = 8.dp))
                Text(
                    stringResource(R.string.onboarding_optional_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(why, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
                TextButton(onClick = { deferred = true }) {
                    Text(stringResource(R.string.onboarding_later))
                }
            }
        }
    }
}

private fun openAppInfoSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}
