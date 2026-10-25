package com.example.chat.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Port of lib/features/auth/forgot_password_screen.dart. The recovery call is
 *  injected; AppRoot wires AuthRepository.resetPassword (the ChatyBackendService
 *  path — the ApiBackendService variant lands with the organization slice). */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    sendRecovery: suspend (email: String) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var emailSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }

    fun validateEmail(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return "Please enter your email"
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+\$").matches(value)) return "Please enter a valid email"
        return null
    }

    fun handleSend() {
        emailError = validateEmail(email)
        if (emailError != null || isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                sendRecovery(email.trim())
                isLoading = false
                emailSent = true
            } catch (e: Exception) {
                isLoading = false
                errorMessage = (e.message ?: e.toString()).replaceFirst("Exception: ", "")
            }
        }
    }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
                AuthBackButton(onClick = onBack)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                    AuthIllustration(type = "forgot_password", theme = theme, height = 190)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Forgot Password?",
                        color = Color(theme.primaryTextColor),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (emailSent)
                            "We sent a secure recovery link. Open the newest email on this device; Chaty will reopen through the reset-password callback."
                        else
                            "Enter the email registered with your Chaty account.",
                        color = Color(theme.secondaryTextColor),
                        fontSize = 14.sp,
                        lineHeight = 19.6.sp,
                    )
                    Spacer(Modifier.height(28.dp))
                    AuthTextField(
                        label = "Email",
                        hintText = "Enter email",
                        theme = theme,
                        value = email,
                        onValueChange = { email = it },
                        keyboardType = KeyboardType.Email,
                        error = emailError,
                    )
                    Spacer(Modifier.height(20.dp))
                    errorMessage?.let {
                        Text(
                            it,
                            color = Color(theme.dangerColor),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    AuthPrimaryButton(
                        text = if (emailSent) "Resend Recovery Email" else "Send Recovery Email",
                        theme = theme,
                        onClick = { handleSend() },
                        isLoading = isLoading,
                    )
                    if (emailSent) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color(argbWithAlpha(theme.accentColor, 0.5)),
                            ),
                        ) {
                            Text(
                                "Back to Sign In",
                                color = Color(theme.primaryTextColor),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
