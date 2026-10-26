package com.example.chat.ui.settings.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.example.chat.ui.chats.home.HomeFilterChips
import com.example.chat.ui.kit.ChatyPreviewRadioRow
import com.example.chat.ui.kit.ChatySelectionModal
import com.example.chat.ui.navigation.pagerTransition
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * One grouped selector for the two related header options (tab bubble style and 3D pager effect).
 * Rows are preview left, name next to it, radio on the right. Nothing is saved until Apply.
 */
@Composable
fun HeaderTabStyleSheet(
    tabStyle: String,
    pagerStyle: String,
    onDismiss: () -> Unit,
    onApply: (tabStyle: String, pagerStyle: String) -> Unit,
) {
    var tabDraft by remember { mutableStateOf(tabStyle) }
    var pagerDraft by remember { mutableStateOf(pagerStyle) }
    val changed = tabDraft != tabStyle || pagerDraft != pagerStyle
    ChatySelectionModal(
        title = "Header tab style", onDismiss = onDismiss,
        onApply = { onApply(tabDraft, pagerDraft) }, applyEnabled = changed,
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            item { SectionLabel("Tab bubble style") }
            item {
                androidx.compose.foundation.layout.Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeUiStyles.tabBubbleStyles.forEach { s ->
                        ChatyPreviewRadioRow(s, s == tabDraft, { tabDraft = s }, preview = { TabChipPreview(s) })
                    }
                }
            }
            item { SectionLabel("3D pager / slider effect") }
            item {
                androidx.compose.foundation.layout.Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeUiStyles.pager3dStyles.forEach { s ->
                        ChatyPreviewRadioRow(s, s == pagerDraft, { pagerDraft = s }, preview = { PagerPreview(s) })
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = LocalAppColors.current.foregroundSecondary, fontSize = 12.sp, fontWeight = FontWeight.W700,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 8.dp),
    )
}

/**
 * The ACTUAL HomeFilterChips (same component, same tabBubbleStyle handling as the home screen), laid out in a
 * fixed viewport and scaled uniformly into the row's preview slot, so text and chips are never clipped and the
 * motif (radius, fill, outline, glow, segmenting) is exactly what the home screen draws.
 */
@Composable
private fun TabChipPreview(style: String) {
    val vw = 150.dp
    val vh = 36.dp
    androidx.compose.foundation.layout.BoxWithConstraints {
        val avail = if (constraints.hasBoundedWidth) maxWidth else vw
        val scale = (avail.value / vw.value).coerceAtMost(1f)
        Box(Modifier.size(vw * scale, vh * scale)) {
            Box(
                Modifier.requiredSize(vw, vh).graphicsLayer {
                    scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
                },
            ) {
                HomeFilterChips(LocalThemeConfig.current, style, listOf("All", "Unread"), "All") {}
            }
        }
    }
}

@Composable
private fun PagerPreview(style: String) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(5.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 30.dp, height = 40.dp).clip(shape).background(colors.primary.copy(alpha = 0.85f)))
        // Same transform the live pager applies to the neighbouring page, at half swipe.
        Box(
            Modifier.size(width = 30.dp, height = 40.dp).pagerTransition(style, 0.5f)
                .clip(shape).background(colors.surfaceSecondary).border(1.dp, colors.border, shape),
        )
    }
}
