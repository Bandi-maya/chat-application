package com.example.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig

/**
 * Offline demo marker (NOT in Flutter): slim in-flow banner so the demo
 * context stays visible wherever sample data is shown.
 */
@Composable
fun DemoModeBanner(theme: ThemeConfig, onExit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(theme.cardColor))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Demo mode - offline sample data, nothing is sent",
            color = Color(theme.secondaryTextColor),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Exit demo",
            color = Color(theme.accentColor),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onExit),
        )
    }
}
