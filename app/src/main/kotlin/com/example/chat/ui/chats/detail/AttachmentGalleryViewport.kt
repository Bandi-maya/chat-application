package com.example.chat.ui.chats.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Stable full-height sheet anchors with content laid out only in its visible viewport.
 * The modal translates its whole sheet at the partial anchor; its measured bottom
 * is offscreen. Reserve header/footer space within the visible part, not that bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentGalleryViewport(sheetState: SheetState, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
        val density = LocalDensity.current
        val offsetPx = runCatching { sheetState.requireOffset() }.getOrNull()
        val offset = if (offsetPx != null && offsetPx.isFinite()) with(density) { offsetPx.toDp() }
            else maxHeight / 2
        // Material's offset is relative to the dialog viewport. Any top inset is
        // conservatively reserved too, keeping the footer above system navigation.
        val visible = (maxHeight - offset).coerceIn(0.dp, maxHeight)
        Box(Modifier.fillMaxWidth().height(visible)) { content() }
    }
}
