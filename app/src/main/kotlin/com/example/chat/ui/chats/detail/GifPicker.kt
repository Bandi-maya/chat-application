package com.example.chat.ui.chats.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.example.chat.core.gif.GifResult
import com.example.chat.core.theme.ThemeConfig

/** Own-server GIF setup draft. No provider requests or sends are enabled. */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun GifPicker(
    theme: ThemeConfig,
    apiKey: String = "",
    onConfirmSend: ((GifResult) -> Unit)?,
    modifier: Modifier = Modifier,
    unavailableReason: String? = null,
) {
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text("GIF library is not connected. Your server integration is pending.", color = Color(theme.primaryTextColor))
        unavailableReason?.let { Text(it, color = Color(theme.secondaryTextColor)) }
    }
}
