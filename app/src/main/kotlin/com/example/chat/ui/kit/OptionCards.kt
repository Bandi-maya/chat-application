package com.example.chat.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/** Port of lib/ui/core/design_system/components/option_cards.dart
 *  (ChatyOptionCard + ChatyOptionGallery). UNCOMPILED, no pixel check. */
@Composable
fun ChatyOptionCard(
    label: String,
    selected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 104.dp,
    previewHeight: Dp = 56.dp,
    preview: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val shape = RoundedCornerShape(14.dp)
    val bg by animateColorAsState(
        if (selected) colors.primary.copy(alpha = 0.12f) else colors.surfaceSecondary,
        tween(160), label = "optBg",
    )
    val borderColor by animateColorAsState(
        if (selected) colors.primary else colors.borderSubtle, tween(160), label = "optBorder",
    )
    Column(
        modifier
            .width(width)
            .semantics { role = Role.Button; this.selected = selected; contentDescription = label }
            .clip(shape)
            .clickable {
                ChatyHapticsPort.selection(view)
                onTap()
            }
            .drawBehind { drawRect(bg) }
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), shape)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(previewHeight)) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) { preview() }
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle, null, tint = colors.primary,
                    modifier = Modifier.align(Alignment.TopEnd).width(18.dp).height(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
            color = if (selected) colors.primary else colors.foreground,
        )
    }
}


@Composable
fun <T> ChatyOptionGallery(
    title: String,
    values: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    previewOf: @Composable (T) -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    Column(modifier) {
        Text(
            title, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 2.dp),
            fontSize = 15.sp, fontWeight = FontWeight.W700, color = colors.foreground,
        )
        if (subtitle != null) {
            Text(
                subtitle, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp),
                fontSize = 12.sp, color = colors.foregroundSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        // Dart: height = textScaler.scale(118)
        val h = (118f * density.fontScale).dp
        LazyRow(
            Modifier.fillMaxWidth().height(h),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(values) { _, v ->
                ChatyOptionCard(
                    label = labelOf(v), selected = v == selected, onTap = { onSelected(v) },
                ) { previewOf(v) }
            }
        }
    }
}

