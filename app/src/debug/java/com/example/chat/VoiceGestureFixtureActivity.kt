package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.core.templates.ComposerTemplate
import com.example.chat.core.templates.ComposerActionPlacement
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.ui.chats.detail.*
import com.example.chat.ui.theme.ChatyTheme

/** DEBUG ONLY: actual composer gesture + actual session/controller, fake hardware and transport. */
class VoiceGestureFixtureActivity : ComponentActivity() {
    companion object { var advanceSyntheticAudio: (() -> Unit)? = null }
    override fun onDestroy() { advanceSyntheticAudio = null; super.onDestroy() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme = remember { ThemePresets.getSystemDefaultTheme(platformIsLight = true) }
            ChatyTheme(config = theme) {
                val scope = rememberCoroutineScope()
                var state by remember { mutableStateOf(VoiceUiState()) }
                var uploads by remember { mutableStateOf(0) }
                var sends by remember { mutableStateOf(0) }
                var deletes by remember { mutableStateOf(0) }
                var now by remember { mutableStateOf(0L) }
                var text by remember { mutableStateOf("") }
                SideEffect { advanceSyntheticAudio = { now = 2000 } }
                val voice = remember {
                    VoiceNoteController("synthetic", object : VoiceRecorderPort {
                        override suspend fun hasPermission() = true
                        override suspend fun start(config: VoiceRecordConfig, path: String) { now = 0 }
                        override suspend fun stop(): String? = "synthetic.m4a"
                        override suspend fun currentAmplitudeDbfs() = -20.0
override suspend fun dispose() {}
                    }, object : VoiceStagingPort {
                        override fun newStagingPath(epochMs: Long) = "synthetic.m4a"
                        override suspend fun deleteQuietly(path: String) { deletes++ }
                    }, uploadAudio = { _, _, _ -> uploads++; MessageAttachment("fixture", "audio", "fixture", "1 KB", "offline") },
                        sendAudioMessage = { sends++ }, now = { now })
                }
                val session = remember { VoiceSessionController(voice, scope, onState = { state = it }) }
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OFFLINE SYNTHETIC VOICE GESTURE FIXTURE")
                    Text("recording=${state.recording};locked=${state.locked};busy=${state.busy};uploads=$uploads;sends=$sends;deletes=$deletes")
                    TextButton(onClick = { now = 2000 }) { Text("Advance synthetic audio 2s") }
                    TextButton(onClick = session::cancelVoice) { Text("Delete synthetic recording") }
                    TextButton(onClick = session::finishVoice) { Text("Send synthetic recording") }
                    Spacer(Modifier.weight(1f))
                    // Keep room on the left for a physical cancel drag. The actual button still owns dispatch.
                    Box(Modifier.fillMaxWidth()) {
                        ChatEntryBar(entryStyle = if (intent.getBooleanExtra("style2", false)) SECOND_REFERENCE_ENTRY_STYLE else STOCK_ENTRY_STYLE,
                            colors = ComposerColors.resolve(theme), template = ComposerTemplate(ComposerActionPlacement.integrated),
                            theme = theme, text = text, onTextChange = { text = it }, onCamera = {},
                            onVoice = { session.beginVoice(true) }, onVoiceHoldStart = { session.beginVoice(false) },
                            onVoiceMove = { session.onDrag(it.x, it.y) }, onVoiceHoldEnd = session::onRelease)
                    }
                }
            }
        }
    }
}
