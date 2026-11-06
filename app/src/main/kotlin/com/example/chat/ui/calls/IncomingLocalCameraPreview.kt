package com.example.chat.ui.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Owns only a local CameraX Preview. No recorder, microphone, WebRTC track or sender. */
class IncomingPreviewOwner {
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var generation = 0
    val isBound: Boolean get() = preview != null
    @androidx.annotation.MainThread
    fun close() { generation++; preview?.let { provider?.unbind(it) }; preview = null; provider = null }
    @androidx.annotation.MainThread
    internal fun bind(context: android.content.Context, owner: androidx.lifecycle.LifecycleOwner, view: PreviewView, ready: (Boolean) -> Unit) {
        close()
        val ticket = generation
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            (context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked) { ready(false); return }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (ticket != generation || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                (context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked) return@addListener
            try {
                val p = future.get()
                val useCase = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                provider = p; preview = useCase
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
                // Success binding is not proof that frames are arriving. PreviewView's
                // stream state is observed by the composable before displaying Ready.
            } catch (_: Exception) { close(); ready(false) }
        }, ContextCompat.getMainExecutor(context))
    }
}

@Composable
fun IncomingLocalCameraPreview(owner: IncomingPreviewOwner, enabled: Boolean, modifier: Modifier = Modifier, onReady: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var view by remember { mutableStateOf<PreviewView?>(null) }
    val ready by rememberUpdatedState(onReady)
    AndroidView(factory = { PreviewView(it).also { v -> v.scaleType = PreviewView.ScaleType.FILL_CENTER; view = v } }, modifier = modifier)
    DisposableEffect(owner, lifecycle, enabled, view) {
        fun bind() { val v = view; if (enabled && v != null) owner.bind(context, lifecycle, v, ready) else { owner.close(); ready(false) } }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) bind()
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) { owner.close(); ready(false) }
        }
        val streamObserver = androidx.lifecycle.Observer<PreviewView.StreamState> { state ->
            ready(owner.isBound && enabled && lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && state == PreviewView.StreamState.STREAMING)
        }
        val lockReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == android.content.Intent.ACTION_SCREEN_OFF) { owner.close(); ready(false) }
            }
        }
        ContextCompat.registerReceiver(context, lockReceiver, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        view?.previewStreamState?.observe(lifecycle, streamObserver)
        lifecycle.lifecycle.addObserver(observer); bind()
        onDispose { context.unregisterReceiver(lockReceiver); view?.previewStreamState?.removeObserver(streamObserver); lifecycle.lifecycle.removeObserver(observer); owner.close() }
    }
}
