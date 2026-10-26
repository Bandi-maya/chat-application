package com.example.chat.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * Selection row for settings choosers: real preview on the left, name (and optional description)
 * beside it, radio on the right. The whole row is the radio target; the radio only mirrors state.
 * Colors come from theme tokens, so it follows light and dark. Previews are not clipped: the slot
 * only caps their width at 42% of the row so the name keeps room (previews that size themselves
 * smaller keep their own size).
 */
@Composable
fun ChatyPreviewRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    preview: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val dark = LocalThemeConfig.current.isDark
    val shape = RoundedCornerShape(14.dp)
    val shadowColor = Color.Black.copy(alpha = if (dark) 0.55f else 0.20f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val previewMax = maxWidth * 0.42f
        Row(
            Modifier.fillMaxWidth()
                .shadow(if (selected) 6.dp else 3.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
                .clip(shape)
                .background(if (selected) colors.primary.copy(alpha = 0.12f).compositeOver(colors.surface) else colors.surface)
                .border(if (selected) 1.5.dp else 1.dp, if (selected) colors.primary else colors.border, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (preview != null) {
                Box(Modifier.widthIn(max = previewMax), contentAlignment = Alignment.Center) { preview() }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
                Text(label, color = colors.foreground, fontSize = 14.sp, fontWeight = if (selected) FontWeight.W700 else FontWeight.W500)
                if (!description.isNullOrEmpty()) Text(description, color = colors.foregroundSecondary, fontSize = 12.sp)
            }
            RadioButton(
                selected = selected, onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.foregroundSecondary),
            )
        }
    }
}
