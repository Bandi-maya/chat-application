package com.example.chat.ui.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** In-place camera content, not an Activity/navigation destination. */
@Composable
fun InlineDocumentReview(path:String,onRetake:()->Unit,onCropped:(String)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var bitmap by remember(path){mutableStateOf<Bitmap?>(null)}
    var quad by remember(path){mutableStateOf(PaperQuad.inset)}
    var note by remember(path){mutableStateOf("Finding paper edges…")}
    var busy by remember(path){mutableStateOf(false)}
    val ownedPreview = remember(path) { java.util.concurrent.atomic.AtomicReference<Bitmap?>(null) }
    DisposableEffect(path) { onDispose { ownedPreview.getAndSet(null)?.recycle() } }
    LaunchedEffect(path) {
        var pending: Bitmap? = null
        try {
            val result = withContext(Dispatchers.IO) {
                val image = InlineDocumentProcessor.loadUpright(path,true)
                pending = image
                image to InlineDocumentProcessor.propose(image)
            }
            ownedPreview.set(result.first)
            bitmap=result.first
            pending=null
            quad=result.second?:PaperQuad.inset
            note=if(result.second==null) "Paper edges not found. Drag all four corners around the paper." else "Suggested paper edges. Check and adjust all four corners."
        } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(e:Exception) { note=e.message?:"Could not read image. Original kept." }
        catch(e:OutOfMemoryError) { note="Not enough memory to open this image. Original kept." }
        finally { pending?.recycle() }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        Text("Document · adjust paper corners",color=Color.White)
        Text(note,color=Color.LightGray)
        val image=bitmap
        if(image!=null) Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "Adjust paper corners" }.pointerInput(image){
            var selected=-1
            fun frame():FloatArray {val scale=minOf(size.width.toFloat()/image.width,size.height.toFloat()/image.height);val w=image.width*scale;val h=image.height*scale;return floatArrayOf((size.width-w)/2,(size.height-h)/2,w,h)}
            detectDragGestures(onDragStart={point->val f=frame();selected=quad.points.indices.minBy{ i->val p=quad.points[i];(Offset(f[0]+p.x*f[2],f[1]+p.y*f[3])-point).getDistance()}},onDragEnd={selected=-1},onDragCancel={selected=-1}){change,_->
                if(selected>=0){val f=frame();quad=quad.moved(selected,PaperPoint((change.position.x-f[0])/f[2],(change.position.y-f[1])/f[3]));change.consume()}
            }
        }) {
            val scale=minOf(size.width/image.width,size.height/image.height);val w=image.width*scale;val h=image.height*scale;val left=(size.width-w)/2;val top=(size.height-h)/2
            drawImage(image.asImageBitmap(),dstOffset=IntOffset(left.toInt(),top.toInt()),dstSize=IntSize(w.toInt(),h.toInt()))
            val pts=quad.points.map{Offset(left+it.x*w,top+it.y*h)}
            val color=if(quad.isValid())Color.Yellow else Color.Red
            for(i in 0..3){drawLine(color,pts[i],pts[(i+1)%4],3.dp.toPx());drawCircle(color,9.dp.toPx(),pts[i]);drawCircle(Color.Black,9.dp.toPx(),pts[i],style=Stroke(1.dp.toPx()))}
        } else Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onRetake,enabled=!busy){Text("Retake")}
            Button(shape = com.example.chat.ui.kit.ChatyActionShape, enabled=image!=null&&quad.isValid()&&!busy,onClick={busy=true;scope.launch{
                var produced:String?=null
                try{val result=withContext(Dispatchers.IO){InlineDocumentProcessor.crop(path,quad,File(context.cacheDir,"paper_crops")).also{produced=it}};onCropped(result);produced=null}
                catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                catch(e:Exception){note=e.message?:"Crop failed. Original kept."}
                catch(e:OutOfMemoryError){note="Not enough memory to crop this full-resolution image. Original kept."}finally{produced?.let{File(it).delete()};busy=false}
            }}){Text(if(busy)"Cropping…" else "Crop paper")}
        }
    }
}
