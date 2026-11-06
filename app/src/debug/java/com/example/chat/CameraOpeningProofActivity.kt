package com.example.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.chat.ui.camera.ChatyCameraCaptureScreen
import com.example.chat.ui.camera.ChatyCaptureMode

/** Isolated camera opening fixture: callbacks never capture/send/upload. */
class CameraOpeningProofActivity : ComponentActivity() {
    var testAuth: SyntheticCameraAuthPort? = null
    var retakeCallbacks = 0
        private set
    var cropCallbacks = 0
        private set
    var croppedPath: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (intent.getBooleanExtra("landscape",false)) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, intent.getFloatExtra("fontScale", 1f)),
            ) {
            val syntheticAuth = androidx.compose.runtime.remember { SyntheticCameraAuthPort().also { testAuth = it } }
            val documentPath = intent.getStringExtra("syntheticDocumentPath")
            val review = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(documentPath != null) }
            val result = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Document fixture") }
            if (documentPath != null) {
                if (review.value) com.example.chat.ui.camera.InlineDocumentReview(documentPath,
                    onRetake = { retakeCallbacks++; result.value = "Retake kept original"; review.value = false },
                    onCropped = { cropCallbacks++; croppedPath = it; result.value = "Crop ready: " + it; review.value = false },
                ) else androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing).padding(24.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) { androidx.compose.material3.Text(result.value,color=androidx.compose.ui.graphics.Color.White) }
            } else ChatyCameraCaptureScreen(
                mode = ChatyCaptureMode.chat,
                videoAuthPort = syntheticAuth,
                entryPreset = if(intent.getBooleanExtra("composerFrontVideo",false)) com.example.chat.ui.camera.CameraEntryPreset.composerFrontVideo else com.example.chat.ui.camera.CameraEntryPreset.photoBack,
                onResult = {},
                onDismiss = { finish() },
            )
            }
        }
    }
}

/** Debug-only opaque identities, no real account or token. */
class SyntheticCameraAuthPort : com.example.chat.ui.camera.VideoAuthPort {
    private var current: Any = Any()
    private val listeners = mutableSetOf<(Any) -> Unit>()
    override fun snapshot(): Any = synchronized(this) { current }
    override fun isSignedIn(snapshot: Any): Boolean = true
    override fun withScope(expected: Any, block: () -> Unit): Boolean = synchronized(this) {
        if (expected !== current) false else { block(); expected === current }
    }
    override fun addListener(listener: (Any) -> Unit) = synchronized(this) { listeners.add(listener); listener(current) }
    override fun removeListener(listener: (Any) -> Unit) { synchronized(this) { listeners.remove(listener) } }
    fun invalidate() = synchronized(this) { current = Any(); listeners.toList().forEach { it(current) } }
}
