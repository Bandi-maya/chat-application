package com.example.chat.ui.core.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.templates.PreviewLayout
import com.example.chat.core.theme.ThemeConfig

/** Port of lib/ui/core/theme/theme_preview_card.dart. Uncompiled; no pixel check. */
@Composable
fun ThemePreviewCard(
    themeConfig: ThemeConfig,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    layout: PreviewLayout? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val t = themeConfig
    Box(
        modifier
            // BoxShadow(blur 14/8) approximated by elevation shadow; exact blur/offset pending pixel comparison.
            .shadow(if (isSelected) 8.dp else 4.dp, shape, clip = false,
                ambientColor = if (isSelected) tc(t.accentColor).alpha(0.28f) else Color.Black.alpha(0.08f),
                spotColor = if (isSelected) tc(t.accentColor).alpha(0.28f) else Color.Black.alpha(0.08f))
            .background(tc(t.backgroundColor), shape)
            .border(
                BorderStroke(
                    if (isSelected) 2.2.dp else 1.1.dp,
                    if (isSelected) tc(t.accentColor) else tc(t.secondaryTextColor).alpha(0.25f),
                ),
                shape,
            )
            .clip(shape)
            .clickable(onClick = onTap),
    ) {
        Column(Modifier.padding(10.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(18.dp)
                        .background(if (isSelected) tc(t.accentColor) else Color.Transparent, CircleShape)
                        .border(2.dp, if (isSelected) tc(t.accentColor) else tc(t.secondaryTextColor).alpha(0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Box(Modifier.size(6.dp).background(if (t.isDark) Color.Black else Color.White, CircleShape))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    t.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = tc(t.primaryTextColor), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp,
                )
                Box(
                    Modifier
                        .background(tc(t.surfaceColor), RoundedCornerShape(6.dp))
                        .border(1.dp, tc(t.secondaryTextColor).alpha(0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.5.dp),
                ) {
                    Text(if (t.isDark) "DARK" else "LIGHT", color = tc(t.secondaryTextColor), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.size(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ThemePairPreview(t, layout = layout) }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(tc(t.backgroundColor)); Spacer(Modifier.width(4.dp))
                ColorDot(tc(t.surfaceColor)); Spacer(Modifier.width(4.dp))
                ColorDot(tc(t.accentColor)); Spacer(Modifier.width(4.dp))
                ColorDot(tc(t.outgoingBubbleColor))
                Spacer(Modifier.weight(1f))
                Text(
                    if (isSelected) "\u25CF Active" else "Apply",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) tc(t.accentColor) else tc(t.secondaryTextColor),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/** Dart Tooltip (Bg/Surface/Accent/Bubble) has no touch-equivalent here; omitted (long-press tooltip only). */
@Composable
private fun ColorDot(color: Color) {
    Box(Modifier.size(14.dp).background(color, CircleShape).border(1.dp, Color.Black.alpha(0.15f), CircleShape))
}
