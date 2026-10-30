package com.example.chat.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Reference zoom control (user video 2026-10-30, user-confirmed replacement of
 *  the arc design): a compact dark pill of preset values above the shutter -
 *  tapping a preset jumps, tapping the active value (or the pill) opens the
 *  horizontal slider (live value above, "-" / "+" ends, yellow fill + marker,
 *  drag the track). While a video records the slider stays open, because the
 *  reference zooms during recording. */
@Composable
fun CameraZoomControls(current:Float,min:Float,max:Float,enabled:Boolean,forceSlider:Boolean=false,onZoom:(Float)->Unit) {
    var proposed by remember(current,min,max) { mutableStateOf(clampCameraZoom(current,min,max)) }
    var expanded by remember { mutableStateOf(false) }
    val presets = remember(min,max) { (listOf(min,1f,2f,4f,6f,max).filter{it in min..max}).distinct().sorted() }
    val showSlider = (expanded || forceSlider) && max > min
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        if(!showSlider) {
            // Compact pill (reference idle): preset values in a dark pill, active yellow.
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x99141414))
                    .clickable(enabled=enabled){expanded=true}
                    .padding(horizontal=8.dp,vertical=2.dp)
                    .semantics{contentDescription="Zoom presets"},
                verticalAlignment=Alignment.CenterVertically,
            ) {
                presets.forEach { ratio ->
                    val active=abs(current-ratio)<.05f
                    Text(
                        String.format(java.util.Locale.ROOT,if(ratio%1f==0f)"%.0fx" else "%.1fx",ratio),
                        color = if(active) Color(0xFFFFD54F) else Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled=enabled){ if(active) expanded=true else {proposed=ratio;onZoom(ratio)} }
                            .padding(horizontal=10.dp,vertical=6.dp)
                            .semantics{contentDescription=String.format(java.util.Locale.ROOT,if(ratio%1f==0f)"Zoom %.0fx" else "%.1fx",ratio);selected=active;role=Role.Button},
                    )
                }
            }
        } else {
            // Horizontal slider (reference): live value above the track.
            Text(
                String.format(java.util.Locale.ROOT,if(proposed%1f==0f)"%.0fx" else "%.1fx",proposed),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable(enabled=enabled && !forceSlider){expanded=false}
                    .semantics{contentDescription=String.format(java.util.Locale.ROOT,"Zoom value %.1fx",proposed)},
            )
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x99141414))
                    .semantics{contentDescription="Zoom slider, drag to zoom";role=Role.Button}
                    .pointerInput(min,max,enabled) {
                        if(!enabled) return@pointerInput
                        var value = current
                        detectHorizontalDragGestures(
                            onDragStart = { value = current },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val widthPx = size.width.coerceAtLeast(1)
                                val f0 = ((value-min)/(max-min)).coerceIn(0f,1f)
                                val f1 = (f0 + dragAmount/widthPx).coerceIn(0f,1f)
                                val next = min + f1*(max-min)
                                if(abs(next-value)>0.005f) { value = next; proposed = clampCameraZoom(next,min,max); onZoom(proposed) }
                            },
                        )
                    },
            ) {
                val trackPad = 30.dp
                val f = ((proposed-min)/(max-min)).coerceIn(0f,1f)
                Text("-",color=Color.White,fontSize=14.sp,modifier=Modifier.align(Alignment.CenterStart).padding(start=12.dp))
                Text("+",color=Color.White,fontSize=14.sp,modifier=Modifier.align(Alignment.CenterEnd).padding(end=12.dp))
                Box(Modifier.align(Alignment.Center).padding(horizontal=trackPad).fillMaxWidth().height(2.dp).background(Color(0x44FFFFFF)))
                Box(Modifier.align(Alignment.CenterStart).padding(start=trackPad).fillMaxWidth(f).height(2.dp).background(Color(0xFFFFD54F)))
                Box(Modifier.align(Alignment.CenterStart).padding(start=trackPad+(maxWidth-trackPad*2-14.dp)*f).size(14.dp).clip(CircleShape).background(Color(0xFFFFD54F)))
            }
        }
        if(current>=4f) Text("High zoom loses detail; hold steady.",color=Color.White)
    }
}
