package com.example.chat.ui.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.abs

@Composable
fun CameraOptionsPanel(
    options: CameraCaptureOptions,
    stabilizationSupported: Boolean,
    onChange: (CameraCaptureOptions) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Color(0xEE101010), RoundedCornerShape(10.dp))
        .heightIn(max = 350.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Camera options   Close", color = Color.White, modifier = Modifier.align(Alignment.End).clickable(onClick = onClose).padding(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CaptureAspect.entries.forEach { aspect ->
                OptionChip(aspect.label, options.aspect == aspect, { onChange(options.copy(aspect = aspect)) }, Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 3, 5, 10).forEach { seconds ->
                OptionChip(if (seconds == 0) "Off" else "${seconds}s", options.timerSeconds == seconds,
                    { onChange(options.copy(timerSeconds = seconds)) }, Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionChip("Grid lines", options.grid, { onChange(options.copy(grid = !options.grid)) }, Modifier.weight(1f))
            OptionChip("Level", options.level, { onChange(options.copy(level = !options.level)) }, Modifier.weight(1f))
        }
        OptionChip(if (stabilizationSupported) "Preview stabilization" else "Stabilization unavailable on this camera",
            options.stabilization && stabilizationSupported, { if (stabilizationSupported) onChange(options.copy(stabilization = !options.stabilization)) }, Modifier.fillMaxWidth(), stabilizationSupported)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionChip("Watermark", options.watermark, { onChange(options.copy(watermark = !options.watermark)) }, Modifier.weight(1f))
            OptionChip("Clarity", options.clarity, { onChange(options.copy(clarity = !options.clarity)) }, Modifier.weight(1f))
        }
        Text("Clarity adjusts photo contrast only. Stabilization affects preview where supported; it does not restore blurred detail. Document scanning has its own crop review.", color = Color.LightGray)
    }
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    Box(modifier.background(if (selected) Color(0xFFFFD747) else Color(0xFF292929), RoundedCornerShape(10.dp))
        .clickable(enabled = enabled, onClick = onClick).padding(10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) Color.Black else if (enabled) Color.White else Color.Gray)
    }
}

@Composable
fun CameraGridAndLevel(grid: Boolean, level: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var roll by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(level) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size >= 3) {
                    // Display rotation determines which sensor axes are horizontal.
                    @Suppress("DEPRECATION")
                    val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay?.rotation ?: 0
                    val x = event.values[0].toDouble()
                    val y = event.values[1].toDouble()
                    val angle = when (rotation) {
                        android.view.Surface.ROTATION_90 -> atan2(y, x)
                        android.view.Surface.ROTATION_180 -> atan2(x, -y)
                        android.view.Surface.ROTATION_270 -> atan2(-y, -x)
                        else -> atan2(-x, y)
                    }
                    roll = if (kotlin.math.hypot(x,y) < 1.0) null else Math.toDegrees(angle).toFloat()
                }
            }
        }
        if (level && sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager?.unregisterListener(listener) }
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            if (grid) for (n in 1..2) {
                drawLine(Color.White.copy(alpha = .4f), Offset(size.width * n / 3, 0f), Offset(size.width * n / 3, size.height), 1f)
                drawLine(Color.White.copy(alpha = .4f), Offset(0f, size.height * n / 3), Offset(size.width, size.height * n / 3), 1f)
            }
        }
        if (level) Text(roll?.let { "Level ${it.toInt()}°" } ?: "Level sensor unavailable", color = if (roll != null && abs(roll!!) < 2) Color.Green else Color.White,
            modifier = Modifier.align(Alignment.Center).background(Color(0x88000000), RoundedCornerShape(10.dp)).padding(8.dp))
    }
}
