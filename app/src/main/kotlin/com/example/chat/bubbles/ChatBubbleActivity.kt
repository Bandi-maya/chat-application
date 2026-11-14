package com.example.chat.bubbles

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.chat.ServiceLocator
import com.example.chat.ui.theme.ChatyTheme
import com.example.chat.ui.theme.GbThemeOverrides
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The OS embeds this activity over other apps. It never hosts AppRoot or bypasses its account gates. */
class ChatBubbleActivity : FragmentActivity() {
    private var ticket: ChatBubbleController.Ticket? = null
    private var visible by mutableStateOf(false)
    private var validation: Job? = null
    private val invalidation: () -> Unit = {
        runOnUiThread {
            if (ChatBubbleController.resolve(ticket?.id) == null) dismissUnsafe()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        ChatBubbleController.install(this)
        ticket = ChatBubbleController.resolve(intent.getStringExtra(ChatBubbleController.EXTRA_TICKET))
        if (ticket == null) { dismissUnsafe(); return }
        ChatBubbleController.addObserver(invalidation)
        ServiceLocator.apiBackend.addListener(invalidation)
        ServiceLocator.preferencesController.addListener(invalidation)
        ServiceLocator.hiddenVaultController.addListener(invalidation)
        setContent {
            val selected = ticket
            // Recheck on every composition before ever mounting a composer or message loader.
            if (visible && selected != null && ChatBubbleController.resolve(selected.id) != null) {
                if (ChatBubbleController.resolve(selected.id) != null) {
                    ChatyTheme(GbThemeOverrides.resolve(ServiceLocator.themeController.globalTheme, ServiceLocator.preferencesController)) {
                        com.example.chat.ui.icons.ProvideChatyIconTheme(ServiceLocator.preferencesController) {
                            com.example.chat.ui.GlobalTypographyScale {
                            Surface {
                                Column(Modifier.fillMaxSize().imePadding()) {
                                    Row(Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { visible = false; finish() }) { Text("Collapse") }
                                        TextButton(onClick = { ChatBubbleController.close(selected.id); visible = false; finish() }) { Text("Close bubble") }
                                    }
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        BubbleConversationSurface(selected) { openFullApp() }
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openFullApp() {
        if (ChatBubbleController.resolve(ticket?.id) == null) { dismissUnsafe(); return }
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
        visible = false
        finish()
    }

    override fun onStart() {
        super.onStart()
        visible = false
        validation?.cancel()
        validation = lifecycleScope.launch {
            // Re-enter the same auto-lock policy as the normal app, before mounting any chat UI.
            ServiceLocator.appLock.onResumed()
            ServiceLocator.appLock.awaitPendingCheck()
            ServiceLocator.appLock.scheduleInitialIfNeeded()
            ServiceLocator.appLock.awaitPendingCheck()
            if (ChatBubbleController.resolve(ticket?.id) == null) dismissUnsafe() else visible = true
        }
    }

    override fun onStop() {
        visible = false
        validation?.cancel()
        super.onStop()
    }

    private fun dismissUnsafe() {
        visible = false
        ticket?.id?.let(ChatBubbleController::close)
        if (!isFinishing) {
            Toast.makeText(this, "Open and unlock Chaty, then choose the bubble again.", Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        validation?.cancel()
        ChatBubbleController.removeObserver(invalidation)
        ServiceLocator.apiBackend.removeListener(invalidation)
        ServiceLocator.preferencesController.removeListener(invalidation)
        ServiceLocator.hiddenVaultController.removeListener(invalidation)
        super.onDestroy()
    }
}
