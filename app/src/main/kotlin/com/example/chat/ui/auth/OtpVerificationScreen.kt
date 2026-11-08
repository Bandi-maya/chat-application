package com.example.chat.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Email OTP entry, reached after registration (VerifyEmail) or after the
 *  forgot-password request (ResetPassword).
 *
 *  Behavior follows the auth-flow research report:
 *  - Digits shown as typed, numeric keyboard, paste accepted (4a.1).
 *  - Destination shown masked so a typo is visible without the full address.
 *  - Resend sits behind a countdown; the server's escalating cooldown and
 *    429 message still apply and are shown inline (4a.2).
 *  - Wrong/expired codes show the server's detail inline, including the
 *    attempts remaining (4a.3).
 *
 *  For ResetPassword there is no server "check code" endpoint: the code is
 *  only validated by auth/reset-password at the final step, so Continue just
 *  carries it forward and a wrong code reports on the new-password screen.
 */
@Composable
fun OtpVerificationScreen(
    email: String,
    purpose: OtpPurpose,
    onBack: () -> Unit,
    /** VerifyEmail only: confirm the code with the server. Not called for
     *  ResetPassword (the code is carried to the new-password screen). */
    verify: suspend (code: String) -> Unit,
    resend: suspend () -> String,
    onVerified: (code: String) -> Unit,
    /** VerifyEmail only: verification is a nudge, not a gate — skipping lands
     *  on Home, where the banner offers to verify later. */
    onSkip: (() -> Unit)? = null,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resendMessage by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableIntStateOf(OtpVerificationLogic.RESEND_COUNTDOWN_SECONDS) }

    LaunchedEffect(resendIn) {
        if (resendIn > 0) {
            delay(1000)
            resendIn -= 1
        }
    }

    fun handleContinue() {
        if (isLoading || !OtpVerificationLogic.isComplete(code)) return
        if (purpose == OtpPurpose.ResetPassword) {
            onVerified(code)
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                verify(code)
                isLoading = false
                onVerified(code)
            } catch (e: Exception) {
                isLoading = false
                errorMessage = OtpVerificationLogic.displayError(e)
            }
        }
    }

    fun handleResend() {
        if (isLoading || resendIn > 0) return
        isLoading = true
        errorMessage = null
        resendMessage = null
        scope.launch {
            try {
                resendMessage = resend()
                code = ""
                resendIn = OtpVerificationLogic.RESEND_COUNTDOWN_SECONDS
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
                errorMessage = OtpVerificationLogic.displayError(e)
            }
        }
    }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
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
                    AuthIllustration(type = "otp", theme = theme, height = 190)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (purpose == OtpPurpose.VerifyEmail) "Verify Your Email" else "Enter Reset Code",
                        color = Color(theme.primaryTextColor),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter the 6-digit code we sent to ${OtpVerificationLogic.maskEmail(email)}.",
                        color = Color(theme.secondaryTextColor),
                        fontSize = 14.sp,
                        lineHeight = 19.6.sp,
                    )
                    Spacer(Modifier.height(28.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            code = OtpVerificationLogic.sanitizeCode(it)
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color(theme.primaryTextColor),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 12.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    errorMessage?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            it,
                            color = Color(theme.dangerColor),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    resendMessage?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            it,
                            color = Color(theme.secondaryTextColor),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    AuthPrimaryButton(
                        text = if (purpose == OtpPurpose.VerifyEmail) "Verify" else "Continue",
                        theme = theme,
                        onClick = { handleContinue() },
                        isLoading = isLoading,
                        capsule = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { handleResend() },
                        enabled = !isLoading && resendIn <= 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (resendIn > 0) "Resend code in ${resendIn}s" else "Resend code",
                            color = Color(
                                if (resendIn > 0) theme.secondaryTextColor else theme.accentColor,
                            ),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    if (onSkip != null) {
                        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Skip for now",
                                color = Color(theme.secondaryTextColor),
                                fontSize = 13.5.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
