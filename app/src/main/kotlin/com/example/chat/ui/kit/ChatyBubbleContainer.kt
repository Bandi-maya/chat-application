package com.example.chat.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.ui.chats.detail.BubbleStyleRegistry
import com.example.chat.ui.chats.detail.drawBubble

/** Port of bubble_style_preview.dart ChatyBubbleContainer. Reuses W2's
 *  BubbleStyleRegistry/drawBubble (ui/chats/detail); no duplicate painter.
 *  UNCOMPILED. contentPadding is InsetsD (l,t,r,b order assumed from W2's
 *  constructor; verify field names at integration). */
@Composable
fun ChatyBubbleContainer(
    styleId: BubbleStyleId,
    isMe: Boolean,
    fillColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    strokeColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val g = BubbleStyleRegistry.getGeometry(styleId)
    val stroke = strokeColor ?: accentColor.copy(alpha = 0.4f)
    val p = g.contentPadding
    Box(
        modifier
            .drawBehind { drawBubble(styleId, isMe, fillColor, stroke, accentColor) }
            .padding(p.left.dp, p.top.dp, p.right.dp, p.bottom.dp),
    ) { content() }
}
