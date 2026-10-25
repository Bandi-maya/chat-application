package com.example.chat.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Port of lib/features/auth/create_new_password_screen.dart (api @ 3bee780d).
 *
 *  Reached in the Dart app ONLY from the Supabase recovery path:
 *  main.dart _handleAuthUiEvent pushes it on AuthChangeEvent.passwordRecovery
 *  (the otp_verification_screen.dart route to it is dead code — nothing in the
 *  api branch navigates to OtpVerificationScreen). The custom backend's
 *  forgot-password endpoint only sends the email; it has no in-app reset
 *  surface in the Dart source either, so this screen is intentionally wired to
 *  the Supabase session, NOT ApiBackendService.
 *
 *  Wiring contract for the integration owner (AppRoot):
 *  - Route: add AuthRoute.CreateNewPassword and navigate to it when
 *    AuthSessionManager emits AuthChangeEvent.passwordRecovery (Dart pushes
 *    with pushAndRemoveUntil clearing the stack; a plain route swap matches).
 *  - submitNewPassword: mirror the Dart body exactly —
 *      if (ServiceLocator.sessionManager.currentAuthUser() == null)
 *          throw Exception(CreateNewPasswordLogic.NO_SESSION_MESSAGE)
 *      ServiceLocator.sessionManager.updateUserPassword(newPassword)
 *      ServiceLocator.sessionManager.signOut()
 *    (Dart: session null check, auth.updateUser(UserAttributes(password:)),
 *    then auth.signOut() so the user signs in fresh with the new password.)
 *  - On success this screen calls showSuccess(SUCCESS_MESSAGE) and then
 *    onPasswordUpdated(); Dart shows the SnackBar via the app-level
 *    ScaffoldMessenger and immediately replaces the stack with LoginScreen,
 *    so wire showSuccess to an app-level snackbar host and onPasswordUpdated
 *    to AuthRoute.Login.
 *
 *  [email] is part of the Dart widget contract (main.dart passes
 *  state.session?.user.email) but is never rendered in the source; kept
 *  unused for parity.
 */
@Composable
fun CreateNewPasswordScreen(
    email: String,
    onBack: () -> Unit,
    submitNewPassword: suspend (newPassword: String) -> Unit,
    showSuccess: (message: String) -> Unit,
    onPasswordUpdated: () -> Unit,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var obscurePassword by remember { mutableStateOf(true) }
    var obscureConfirm by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }

    fun handleResetPassword() {
        if (isLoading) return
        // Dart step 1: validate every field in one pass.
        val errors = CreateNewPasswordLogic.validateForm(password, confirmPassword)
        passwordError = errors.passwordError
        confirmError = errors.confirmError
        // Dart step 2: redundant top-level mismatch guard (kept for parity).
        if (errors.topLevelError != null) {
            errorMessage = errors.topLevelError
            return
        }
        if (!errors.isValid) return

        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                submitNewPassword(password)
                showSuccess(CreateNewPasswordLogic.SUCCESS_MESSAGE)
                onPasswordUpdated()
            } catch (e: Exception) {
                errorMessage = CreateNewPasswordLogic.displayError(e)
                isLoading = false
            }
        }
    }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                AuthBackButton(onClick = onBack)
                Spacer(Modifier.height(28.dp))
                Text(
                    "Create New Password",
                    color = Color(theme.primaryTextColor),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Use at least 12 characters with uppercase, lowercase, a number, and a symbol.",
                    color = Color(theme.secondaryTextColor),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(36.dp))
                AuthTextField(
                    label = "Password",
                    hintText = "Enter a strong password",
                    theme = theme,
                    value = password,
                    onValueChange = { password = it },
                    obscureText = obscurePassword,
                    error = passwordError,
                    suffixIcon = {
                        IconButton(onClick = { obscurePassword = !obscurePassword }) {
                            Icon(
                                // Dart: visibility_off_outlined while obscured,
                                // visibility_outlined while visible.
                                if (obscurePassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (obscurePassword) "Show password" else "Hide password",
                                tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(20.dp))
                AuthTextField(
                    label = "Confirm Password",
                    hintText = "Confirm password",
                    theme = theme,
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    obscureText = obscureConfirm,
                    error = confirmError,
                    suffixIcon = {
                        IconButton(onClick = { obscureConfirm = !obscureConfirm }) {
                            Icon(
                                if (obscureConfirm) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (obscureConfirm) "Show password" else "Hide password",
                                tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(24.dp))
                errorMessage?.let {
                    Text(
                        it,
                        color = Color(theme.dangerColor),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
                AuthPrimaryButton(
                    text = "Reset Password",
                    theme = theme,
                    onClick = { handleResetPassword() },
                    isLoading = isLoading,
                )
            }
        }
    }
}
