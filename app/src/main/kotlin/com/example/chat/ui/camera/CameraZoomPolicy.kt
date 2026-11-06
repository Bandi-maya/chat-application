package com.example.chat.ui.camera

internal fun clampCameraZoom(requested:Float,min:Float,max:Float):Float =
    if (!requested.isFinite() || !min.isFinite() || !max.isFinite() || min<=0 || max<min) 1f else requested.coerceIn(min,max)
internal fun shouldAnalyzeCameraFrame(enabled:Boolean,now:Long,last:Long):Boolean = enabled && now-last>=66_000_000L
