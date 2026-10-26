package com.example.chat.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/** Chaty field chrome, with the editable text semantics and IME supplied by BasicTextField. */
@Composable
fun ChatyTaskField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val c = LocalAppColors.current
    BasicTextField(
        value = value, onValueChange = onValueChange, enabled = enabled,
        singleLine = singleLine, minLines = if (singleLine) 1 else 3,
        textStyle = TextStyle(color = c.foreground, fontSize = 15.sp, lineHeight = 22.sp),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        decorationBox = { input ->
            Row(
                Modifier.clip(ChatyModalShape).background(c.inputFill)
                    .border(1.dp, c.inputBorder, ChatyModalShape)
                    .heightIn(min = if (singleLine) 54.dp else 104.dp).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.let {
                    CompositionLocalProvider(LocalContentColor provides c.foregroundSecondary) { it() }
                    Spacer(Modifier.width(12.dp))
                }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = c.foregroundSecondary, fontSize = 15.sp)
                    input()
                }
            }
        },
    )
}

/** App-token choice/action tile. Selection is exposed to accessibility, not drawn as a stock chip. */
@Composable
fun ChatyChoiceTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    prominent: Boolean = false,
    role: Role = Role.Button,
    compact: Boolean = false,
) {
    val c = LocalAppColors.current
    val filled = prominent || selected
    Row(
        modifier.clip(ChatyModalShape)
            .background(if (prominent && !enabled) c.disabled else if (prominent) c.accent else if (selected) c.accent.copy(alpha = 0.12f) else c.surface)
            .border(1.dp, if (prominent && !enabled) c.border else if (filled) c.accent.copy(alpha = 0.6f) else c.border, ChatyModalShape)
            .then(if (role == Role.Checkbox) Modifier.toggleable(value = selected, enabled = enabled, role = role, onValueChange = { onClick() })
                else Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick))
            .heightIn(min = 48.dp).padding(horizontal = if (compact) 2.dp else 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = if (!enabled) (if (prominent) c.foregroundSecondary else c.disabledForeground) else if (prominent) c.onAccent else if (selected) c.accent else c.foreground,
            fontSize = 13.sp, fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium)
    }
}
