package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.chat.ui.updates.StatusUnavailablePane

/** Debug-only synthetic host for the status-unavailable pane. No ServiceLocator, session,
 *  network or account. */
class StatusUnavailableFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StatusUnavailablePane() }
    }
}
