package com.example.chat.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun CameraZoomControls(current:Float,min:Float,max:Float,enabled:Boolean,onZoom:(Float)->Unit) {
    var proposed by remember(current,min,max) { mutableStateOf(clampCameraZoom(current,min,max)) }
    var expanded by remember { mutableStateOf(false) }
    val presets = remember(min,max) { (listOf(min,1f,2f,5f,10f,max).filter{it in min..max}).distinct().sorted() }
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.background(Color(0xCC141414),RoundedCornerShape(28.dp)).horizontalScroll(rememberScrollState()).padding(4.dp),
            verticalAlignment=Alignment.CenterVertically) {
            presets.forEach { ratio ->
                val active=abs(current-ratio)<.05f
                Box(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).clip(com.example.chat.ui.kit.ChatyActionShape).background(if(active)androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.Transparent,com.example.chat.ui.kit.ChatyActionShape)
                    .clickable(enabled=enabled){proposed=ratio;onZoom(ratio)}
                    .semantics{contentDescription=String.format(java.util.Locale.ROOT,"Zoom %.1fx",ratio);selected=active;role=Role.Button},contentAlignment=Alignment.Center) {
                    Text(String.format(java.util.Locale.ROOT,if(ratio%1f==0f)"%.0fx" else "%.1fx",ratio),color=if(active)androidx.compose.material3.MaterialTheme.colorScheme.onPrimary else Color.White,maxLines=1)
                }
            }
            Box(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).clickable(enabled=enabled){expanded=!expanded}
                .semantics{contentDescription="Toggle precise zoom";stateDescription=if(expanded)"Expanded"else"Collapsed";role=Role.Button},contentAlignment=Alignment.Center) {
                Text(if(expanded)"−"else"+",color=Color.White)
            }
        }
        if(expanded && max>min) CircularZoomDial(
            current = proposed,
            min = min,
            max = max,
            enabled = enabled,
            onZoomLive = { next -> proposed = clampCameraZoom(next,min,max); onZoom(proposed) },
        )
        if(current>=4f) Text("High zoom loses detail; hold steady.",color=Color.White)
    }
}

/** OEM-style circular zoom dial (reference: stock camera arc): the arc spans the
 *  zoom range log-mapped like real cameras, the fixed top marker reads the live
 *  value, and horizontal drags scroll the arc to zoom continuously. */
@Composable
private fun CircularZoomDial(current:Float,min:Float,max:Float,enabled:Boolean,onZoomLive:(Float)->Unit) {
    val rangeFraction:(Float)->Float = { z ->
        kotlin.math.ln(z/min)/kotlin.math.ln(max/min)
    }
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE6141414))
            .semantics{contentDescription="Circular zoom dial, drag horizontally to zoom";role=Role.Button}
            .pointerInput(min,max,enabled) {
                if(!enabled) return@pointerInput
                var value = current
                detectHorizontalDragGestures(
                    onDragStart = { value = current },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val widthPx = size.width.coerceAtLeast(1)
                        // A full-width drag sweeps roughly half the zoom range.
                        val f0 = rangeFraction(value)
                        val f1 = (f0 + dragAmount/widthPx*0.5f).coerceIn(0f,1f)
                        val next = min*kotlin.math.exp(f1*kotlin.math.ln(max/min))
                        if(kotlin.math.abs(next-value)>0.005f) { value = next; onZoomLive(next) }
                    },
                )
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val center = androidx.compose.ui.geometry.Offset(w/2f, h*2.4f)
            val radius = h*2.1f
            val fCurrent = rangeFraction(current)
            fun pointFor(f:Float,r:Float):androidx.compose.ui.geometry.Offset {
                val angleDeg = 270.0 + (f - fCurrent)*140.0
                val rad = Math.toRadians(angleDeg)
                return androidx.compose.ui.geometry.Offset(
                    center.x + (r*kotlin.math.cos(rad)).toFloat(),
                    center.y + (r*kotlin.math.sin(rad)).toFloat(),
                )
            }
            var step = 0.5f
            if(max-min>6f) step = 1f
            var zTick = min
            while(zTick<=max+0.001f) {
                val f = rangeFraction(zTick)
                if(kotlin.math.abs(f-fCurrent)<0.38f) {
                    val major = (listOf(min,1f,2f,5f,10f,max).any{ kotlin.math.abs(zTick-it)<0.05f })
                    val a = pointFor(f, radius)
                    val b = pointFor(f, radius - if(major) 16f else 9f)
                    drawLine(
                        color = if(major) Color.White else Color.White.copy(alpha=.55f),
                        start = a, end = b,
                        strokeWidth = if(major) 3f else 2f,
                    )
                }
                zTick += step
            }
            // Fixed top-center marker showing the live value position.
            val tipY = 14f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w/2f-8f, tipY); lineTo(w/2f+8f, tipY); lineTo(w/2f, tipY+12f); close()
            }
            drawPath(path, Color.White)
        }
        // Live value + preset labels.
        Text(
            String.format(java.util.Locale.ROOT,"%.1fx",current),
            color = Color(0xFFFFD54F),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
        )
    }
}
