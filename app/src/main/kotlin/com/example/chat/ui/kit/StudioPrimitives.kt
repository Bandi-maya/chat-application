package com.example.chat.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig


/** morphing_segmented_control.dart: sliding-pill segmented control. */
data class MorphingSegmentItem<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val badgeCount: Int? = null,
)

@Composable
fun <T> MorphingSegmentedControl(
    items: List<MorphingSegmentItem<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    height: Float = 42f,
    activePillColor: Color? = null,
    activeTextColor: Color? = null,
    inactiveTextColor: Color? = null,
) {
    val theme = LocalThemeConfig.current
    val appColors = LocalAppColors.current
    val pillColor = activePillColor ?: Color(theme.accentColor)
    val textActive = activeTextColor ?: Color(theme.onAccentColor)
    val textInactive = inactiveTextColor ?: Color(theme.secondaryTextColor)

    Row(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(Color(theme.cardColor), RoundedCornerShape((height / 2).dp))
            .border(1.dp, appColors.borderSubtle, RoundedCornerShape((height / 2).dp))
            .padding(4.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        for (item in items) {
            val isSelected = item.value == selectedValue
            val pillBg by animateColorAsState(
                if (isSelected) pillColor else Color.Transparent,
                animationSpec = tween(220),
                label = "segmentPill",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .springInteractiveScale()
                    .clip(RoundedCornerShape((height / 2).dp))
                    .background(pillBg)
                    .clickable { if (!isSelected) onSelected(item.value) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                if (item.icon != null) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (isSelected) textActive else textInactive,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    item.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) textActive else textInactive,
                )
                if (item.badgeCount != null && item.badgeCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .background(
                                if (isSelected) textActive.copy(alpha = 0.25f) else pillColor,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${item.badgeCount}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) textActive else Color(theme.onAccentColor),
                        )
                    }
                }
            }
        }
    }
}

/** bento_primitives.dart BentoCard (the variant Theme Studio uses:
 *  title/subtitle/headerLeading header row + child body). */
@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    headerLeading: (@Composable () -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderRadius: Float = 16f,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val theme = LocalThemeConfig.current
    val isDark = theme.isDark
    val defaultBg = if (isDark) Color(0xFF161A22) else Color(0xFFF6F8FA)
    val defaultBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val shadowColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.05f)

    Column(
        modifier
            .shadow(10.dp, RoundedCornerShape(borderRadius.dp), ambientColor = shadowColor, spotColor = shadowColor)
            .clip(RoundedCornerShape(borderRadius.dp))
            .background(backgroundColor ?: defaultBg)
            .border(1.dp, borderColor ?: defaultBorder, RoundedCornerShape(borderRadius.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) {
        if (title != null || headerLeading != null || headerTrailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (headerLeading != null) {
                    headerLeading()
                    Spacer(Modifier.width(8.dp))
                }
                if (title != null) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(theme.primaryTextColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(theme.secondaryTextColor),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (headerTrailing != null) headerTrailing()
            }
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}
