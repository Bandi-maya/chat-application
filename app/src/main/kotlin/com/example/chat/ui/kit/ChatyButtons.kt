package com.example.chat.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/**
 * Chaty text action: capsule hit area, accent label, optional leading icon,
 * 48dp minimum touch height. Replaces stock Material TextButton in Chaty chrome.
 */
@Composable
fun ChatyTextButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color? = null,
    enabled: Boolean = true,
    fontWeight: FontWeight = FontWeight.W600,
) {
    val colors = LocalAppColors.current
    val contentColor = when {
        !enabled -> colors.disabledForeground
        color != null -> color
        else -> colors.accent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(ChatyActionShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = contentColor, fontWeight = fontWeight, fontSize = 14.sp)
    }
}

/**
 * Chaty filled action: capsule container in the theme accent, optional leading
 * icon, 48dp minimum touch height. Caller content is allowed for busy spinners
 * and multi-part labels. Replaces stock Material Button in Chaty chrome.
 */
@Composable
fun ChatyActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    enabled: Boolean = true,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val container = when {
        !enabled -> colors.disabled
        containerColor != null -> containerColor
        else -> colors.accent
    }
    val foreground = when {
        !enabled -> colors.disabledForeground
        contentColor != null -> contentColor
        else -> colors.onAccent
    }
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = modifier
                .heightIn(min = 48.dp)
                .clip(ChatyActionShape)
                .background(container)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (content != null) {
                content()
            } else {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                if (label != null) {
                    Text(label, fontWeight = FontWeight.W700, fontSize = 14.sp)
                }
            }
        }
    }
}
