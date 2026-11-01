package com.example.chat

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Synthetic, debug-only fixture for the lock-badge motion instrumentation proof. */
class LockBadgeProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initiallyLocked = intent.getBooleanExtra(EXTRA_INITIALLY_LOCKED, false)
        val calibration = intent.getBooleanExtra(EXTRA_CALIB_BEACON, false)
        setContent { LockBadgeProofContent(initiallyLocked, calibration) }
    }

    companion object {
        const val EXTRA_INITIALLY_LOCKED = "lock_badge.initially_locked"
        const val EXTRA_CALIB_BEACON = "lock_badge.calibration_beacon"
        const val TARGET_TITLE = "Lock badge proof target"
    }
}

@Composable
private fun LockBadgeProofContent(initiallyLocked: Boolean, calibration: Boolean) {
    var locked by remember { mutableStateOf(initiallyLocked) }
    var flips by remember { mutableIntStateOf(0) }
    val offsetY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val context = LocalContext.current
    val durationScale = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f)
    }

    LaunchedEffect(locked, flips) {
        if (!locked || durationScale <= 0f) {
            offsetY.snapTo(0f)
            rotation.snapTo(0f)
        } else {
            offsetY.snapTo(-7f)
            rotation.snapTo(-13f)
            launch { offsetY.animateTo(0f, tween(360, easing = LinearEasing)) }
            launch { rotation.animateTo(0f, tween(360, easing = LinearEasing)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("locked0=$locked flips=$flips", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "toggle0",
                modifier = Modifier.clickable {
                    locked = !locked
                    flips += 1
                }.padding(vertical = 12.dp, horizontal = 8.dp),
                fontSize = 16.sp,
            )
            if (calibration) CalibrationBeacon()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (locked) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "Locked badge",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer {
                                translationY = offsetY.value * density
                                rotationZ = rotation.value
                            },
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(TARGET_TITLE, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(900.dp))
        }
    }
}

@Composable
private fun CalibrationBeacon() {
    val transition = rememberInfiniteTransition(label = "lock-badge-calibration")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "calibration-rotation",
    )
    Icon(
        Icons.Rounded.Lock,
        contentDescription = "calib-loop",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = rotation },
    )
}
