package com.example.chat.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.theme.LocalThemeConfig

/** Port of lib/features/auth/welcome_screen.dart. */
@Composable
fun WelcomeScreen(onLogin: () -> Unit, onRegister: () -> Unit) {
    val theme = LocalThemeConfig.current
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                AuthIllustration(type = "welcome", theme = theme, height = 220)
                Spacer(Modifier.height(22.dp))
                Text(
                    "Welcome to Chaty",
                    textAlign = TextAlign.Center,
                    color = Color(theme.primaryTextColor),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Private, customizable, and lightning-fast messaging.",
                    textAlign = TextAlign.Center,
                    color = Color(theme.secondaryTextColor),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.weight(1f))
            }
            AuthPrimaryButton(text = "Log In", theme = theme, onClick = onLogin)
            Spacer(Modifier.height(14.dp))
            AuthSecondaryButton(text = "Register", theme = theme, onClick = onRegister)
            Spacer(Modifier.height(24.dp))
        }
    }
}
