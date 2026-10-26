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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat.ui.theme.LocalAppColors

/** Custom Chaty confirmation chrome. Existing form fields/actions remain caller-owned. */
@Composable
fun ChatyConfirmationDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = ChatyModalShape,
    containerColor: Color = LocalAppColors.current.surfaceElevated,
    properties: DialogProperties = DialogProperties(),
) {
    val colors = LocalAppColors.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Column(
            modifier.widthIn(max = 480.dp).fillMaxWidth().imePadding()
                .shadow(20.dp, shape).clip(shape).background(containerColor)
                .border(1.dp, colors.border, shape)
                .heightIn(max = maxHeight)
                .semantics { paneTitle = "Chaty dialog" },
        ) {
            // App-accent edge and compact header, not the stock centered Material dialog layout.
            Spacer(Modifier.fillMaxWidth().height(3.dp).background(colors.accent))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp)) {
                icon?.let {
                    CompositionLocalProvider(LocalContentColor provides colors.accent) { it() }
                    Spacer(Modifier.height(12.dp))
                }
                title?.let {
                    CompositionLocalProvider(LocalContentColor provides colors.foreground) {
                        ProvideTextStyle(TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)) { it() }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                text?.let {
                    CompositionLocalProvider(LocalContentColor provides colors.foregroundSecondary) {
                        ProvideTextStyle(TextStyle(fontSize = 14.sp, lineHeight = 21.sp)) { it() }
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
            Row(
                Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/** Single default sheet grabber. Sheets with a functional/custom grabber keep their own owner. */
@Composable
fun ChatySheetGrabber() {
    val colors = LocalAppColors.current
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Spacer(Modifier.widthIn(min = 36.dp, max = 36.dp).height(4.dp).clip(ChatyModalShape)
            .background(colors.foregroundTertiary.copy(alpha = 0.45f)))
    }
}
