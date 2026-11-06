package com.example.chat.ui.camera

import android.graphics.*
import android.media.ExifInterface
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Local-only paper crop. The original is read-only and remains available for retake. */
object InlineDocumentProcessor {
    fun loadUpright(path: String, preview: Boolean): Bitmap {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(path,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0) { "Cannot read the captured image" }
        var sample=1
        if(preview) while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1400)sample*=2
        if(!preview) {
            val runtime=Runtime.getRuntime()
            val available=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())
            check(bounds.outWidth.toLong()*bounds.outHeight*12L<available*3/4) { "Not enough memory to crop this full-resolution image. Original kept." }
        }
        val raw=BitmapFactory.decodeFile(path,BitmapFactory.Options().apply { inSampleSize=sample }) ?: error("Cannot decode image")
        try {
            val orientation=ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
            val m=Matrix().apply { when(orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f,1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f,-1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {postScale(-1f,1f);postRotate(90f)}
                ExifInterface.ORIENTATION_TRANSVERSE -> {postScale(-1f,1f);postRotate(270f)}
            } }
            val upright=Bitmap.createBitmap(raw,0,0,raw.width,raw.height,m,true)
            if(upright!==raw)raw.recycle()
            return upright
        }catch(t:Throwable){if(!raw.isRecycled)raw.recycle();throw t}
    }
    fun propose(bitmap: Bitmap): PaperQuad? {
        val small=Bitmap.createScaledBitmap(bitmap,160,160,true)
        try {
            val pixels=IntArray(160*160);small.getPixels(pixels,0,160,0,0,160,160)
            val luma=IntArray(pixels.size){i->val c=pixels[i];(Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000}
            return detectPaperQuad(luma,160,160)
        }finally{if(small!==bitmap)small.recycle()}
    }
    fun crop(source: String,quad: PaperQuad,folder: File): String {
        require(quad.isValid()) { "Move the four corners around the paper without crossing them." }
        val bitmap=loadUpright(source,false)
        var output:Bitmap?=null;var file:File?=null
        try {
            val p=quad.points.map{PaperPoint(it.x*(bitmap.width-1),it.y*(bitmap.height-1))}
            fun distance(a:PaperPoint,b:PaperPoint)=hypot((a.x-b.x).toDouble(),(a.y-b.y).toDouble())
            val w=maxOf(distance(p[0],p[1]),distance(p[3],p[2])).roundToInt().coerceAtLeast(1)
            val h=maxOf(distance(p[0],p[3]),distance(p[1],p[2])).roundToInt().coerceAtLeast(1)
            val rendered=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            output=rendered
            val transform=Matrix()
            check(transform.setPolyToPoly(p.flatMap{listOf(it.x,it.y)}.toFloatArray(),0,floatArrayOf(0f,0f,w.toFloat(),0f,w.toFloat(),h.toFloat(),0f,h.toFloat()),0,4))
            Canvas(rendered).apply{drawColor(Color.WHITE);drawBitmap(bitmap,transform,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))}
            folder.mkdirs();file=File.createTempFile("paper_",".jpg",folder)
            val destination = file
            destination.outputStream().use{check(rendered.compress(Bitmap.CompressFormat.JPEG,98,it))}
            return destination.absolutePath
        }catch(t:Throwable){file?.delete();throw t}finally{output?.recycle();bitmap.recycle()}
    }
}
