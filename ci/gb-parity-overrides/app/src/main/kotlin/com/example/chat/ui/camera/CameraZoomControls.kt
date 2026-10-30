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

/** Preset zoom circles + always-visible arc dial (user redesign, 2026-10-30):
 *  1x/2x/4x/6x circles sit above the arc, the live value rides the arc's fixed
 *  top marker, and horizontal drags zoom continuously. No expand/collapse toggle:
 *  the arc stays visible while the camera is live. */
@Composable
fun CameraZoomControls(current:Float,min:Float,max:Float,enabled:Boolean,onZoom:(Float)->Unit) {
    var proposed by remember(current,min,max) { mutableStateOf(clampCameraZoom(current,min,max)) }
    val presets = remember(min,max) { (listOf(min,1f,2f,4f,6f,max).filter{it in min..max}).distinct().sorted() }
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        // Preset circles (user design): yellow when active, always above the arc.
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
            verticalAlignment=Alignment.CenterVertically) {
            presets.forEach { ratio ->
                val active=abs(current-ratio)<.05f
                Box(Modifier.sizeIn(minWidth=56.dp,minHeight=56.dp).clip(CircleShape).background(if(active)Color(0xFFFFD54F) else Color.Transparent,CircleShape)
                    .clickable(enabled=enabled){proposed=ratio;onZoom(ratio)}
                    .semantics{contentDescription=String.format(java.util.Locale.ROOT,if(ratio%1f==0f)"Zoom %.0fx" else "Zoom %.1fx",ratio);selected=active;role=Role.Button},contentAlignment=Alignment.Center) {
                    Text(String.format(java.util.Locale.ROOT,if(ratio%1f==0f)"%.0fx" else "%.1fx",ratio),color=if(active)Color.Black else Color.White,maxLines=1)
                }
            }
        }
        if(max>min) CircularZoomDial(
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
                    val major = (listOf(min,1f,2f,4f,6f,max).any { abs(zTick-it)<.001f })
                    drawLine(
                        if(major) Color.White else Color.White.copy(alpha=.55f),
                        start = pointFor(f, radius), end = pointFor(f, radius - if(major) 18f else 10f),
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
        // Live value pill riding the arc's fixed top marker (user design).
        Text(
            String.format(java.util.Locale.ROOT,if(current%1f==0f)"%.0fx" else "%.1fx",current),
            color = Color(0xFFFFD54F),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .semantics{contentDescription=String.format(java.util.Locale.ROOT,"Zoom value %.1fx",current)},
        )
    }
}
