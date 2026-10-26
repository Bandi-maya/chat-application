package com.example.chat.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** A visible pushed page owns every touch inside its bounds. Without a pointer-input node the
 *  events that nothing on the page consumes (plain text, empty background) fall through to
 *  whatever clickable is composed underneath, e.g. the Home header. */
@Composable
fun PushedPageTouchGuard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().pointerInput(Unit) {}) { content() }
}
