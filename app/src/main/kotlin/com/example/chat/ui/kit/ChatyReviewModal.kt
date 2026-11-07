package com.example.chat.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat.ui.theme.LocalAppColors

/** Approved changelog-style chrome. The body scrolls independently of the fixed footer. */
@Composable
fun ChatyReviewModal(title: String, subtitle: String, onDismiss: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp).fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8f).dp)
            .shadow(12.dp, ChatyModalShape).clip(ChatyModalShape).background(colors.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(title, color = colors.foreground, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(subtitle, color = colors.foregroundSecondary, fontSize = 14.sp)
            }
            HorizontalDivider(color = colors.borderSubtle)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), content = body)
            HorizontalDivider(color = colors.borderSubtle)
            Button(onClick = onDismiss, modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 48.dp),
                shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    }
}
