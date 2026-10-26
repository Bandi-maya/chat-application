package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.chat.ui.camera.ChatyCameraCaptureScreen
import com.example.chat.ui.camera.ChatyCaptureMode

/** Isolated camera opening fixture: callbacks never capture/send/upload. */
class CameraOpeningProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatyCameraCaptureScreen(
                mode = ChatyCaptureMode.chat,
                onResult = {},
                onDismiss = { finish() },
            )
        }
    }
}
