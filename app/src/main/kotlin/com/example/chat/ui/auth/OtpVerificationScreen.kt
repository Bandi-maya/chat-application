package com.example.chat.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.chat.ui.kit.ChatyAdaptiveLogic

/** Email OTP entry, reached after registration (VerifyEmail) or after the
 *  forgot-password request (ResetPassword). Layout follows the approved
 *  preview 7: six code cells with dash placeholders, "Code sent to" masked
 *  destination, "Verify and continue" primary action, and a
 *  resend-countdown / change-email row.
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
 *
 *  Debug builds only: the approved offline-demo code (000000) short-circuits
 *  to onVerified through DebugOtpGate before any backend call - no request,
 *  no token. Release builds never contain the gate. */
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
    deliveryMessage: String? = null,
    initialResendSeconds: Int = OtpVerificationLogic.RESEND_COUNTDOWN_SECONDS,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resendMessage by remember(email, deliveryMessage) { mutableStateOf(deliveryMessage) }
    var resendIn by remember { mutableIntStateOf(initialResendSeconds.coerceIn(1, 3600)) }

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
        // Debug-only offline demo gate (approved preview 7): in any debug build, as explicitly requested13:51
        // the demo code skips the server entirely - no production request, no
        // token. Release builds never contain the gate, so this is always
        // false there.
        if (purpose == OtpPurpose.VerifyEmail && DebugOtpGate.acceptsDebugCode(code)) {
            errorMessage = null
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
                Column(modifier = Modifier.widthIn(max = ChatyAdaptiveLogic.AUTH_COLUMN_MAX_DP.dp).fillMaxWidth()) {
                    AuthIllustration(type = "otp", theme = theme, height = 190)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when (purpose) { OtpPurpose.VerifyEmail -> "Verify Your Email"; OtpPurpose.SignIn -> "Sign in with email code"; OtpPurpose.ResetPassword -> "Enter Reset Code" },
                        color = Color(theme.primaryTextColor),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Code sent to ${OtpVerificationLogic.maskEmail(email)}.",
                        color = Color(theme.secondaryTextColor),
                        fontSize = 14.sp,
                        lineHeight = 19.6.sp,
                    )
                    Spacer(Modifier.height(28.dp))
                    OtpCodeCells(
                        code = code,
                        onCodeChange = {
                            code = OtpVerificationLogic.sanitizeCode(it)
                            errorMessage = null
                        },
                        theme = theme,
                    )
                    errorMessage?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            it,
                            color = Color(theme.dangerColor),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
                        text = when (purpose) { OtpPurpose.VerifyEmail -> "Verify and continue"; OtpPurpose.SignIn -> "Verify and sign in"; OtpPurpose.ResetPassword -> "Continue" },
                        theme = theme,
                        onClick = { handleContinue() },
                        isLoading = isLoading,
                        capsule = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { handleResend() },
                            enabled = !isLoading && resendIn <= 0,
                            modifier = Modifier.heightIn(min = AuthCapsuleTokens.tapTarget),
                        ) {
                            Text(
                                if (resendIn > 0) "Resend code in ${resendIn}s" else "Resend code",
                                color = Color(
                                    if (resendIn > 0) theme.secondaryTextColor else theme.accentColor,
                                ),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                            )
                        }
                        Text(
                            "•",
                            color = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                            fontSize = 13.5.sp,
                        )
                        TextButton(
                            onClick = onBack,
                            enabled = !isLoading,
                            modifier = Modifier.heightIn(min = AuthCapsuleTokens.tapTarget),
                        ) {
                            Text(
                                "Change email",
                                color = Color(theme.accentColor),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                            )
                        }
                    }
                    if (onSkip != null && purpose == OtpPurpose.VerifyEmail) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth().heightIn(min = AuthCapsuleTokens.tapTarget),
                        ) {
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

/** The approved preview 7 code entry: CODE_LENGTH cells with dash
 *  placeholders over a single transparent input, so typing, paste, autofill
 *  and the numeric keyboard all behave like one field while the cells give
 *  the per-digit readout. Cells are decorative for accessibility; the hidden
 *  field carries the label and the entered-count state. Cell tint animation
 *  honors reduced motion. */
@Composable
private fun OtpCodeCells(
    code: String,
    onCodeChange: (String) -> Unit,
    theme: ThemeConfig,
) {
    val colors = LocalAppColors.current
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusRequester.requestFocus() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(OtpVerificationLogic.CODE_LENGTH) { index ->
                val digit = code.getOrNull(index)
                val cellTint by animateColorAsState(
                    targetValue = if (digit != null || index == code.length) {
                        Color(theme.accentColor)
                    } else {
                        colors.border
                    },
                    animationSpec = tween(
                        durationMillis = if (reduced) 0 else com.example.chat.ui.kit.ChatyMotion.fastMs,
                    ),
                    label = "otp-cell-tint-$index",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .background(colors.inputFill, RoundedCornerShape(12.dp))
                        .border(1.dp, cellTint, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        digit?.toString() ?: "-",
                        color = if (digit != null) {
                            Color(theme.primaryTextColor)
                        } else {
                            Color(argbWithAlpha(theme.secondaryTextColor, 0.5))
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .focusRequester(focusRequester)
                .semantics {
                    contentDescription = "One-time code, ${OtpVerificationLogic.CODE_LENGTH} digits"
                    stateDescription = "${code.length} of ${OtpVerificationLogic.CODE_LENGTH} digits entered"
                },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
        )
    }
}
