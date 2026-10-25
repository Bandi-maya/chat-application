package com.example.chat.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.DomainAdd
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.chat.core.auth.friendlyLoginError
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.core.validators.ChatyValidators
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Port of lib/features/auth/login_screen.dart. The login call is injected so
 *  the screen never touches the service locator directly (testable seam);
 *  AppRoot wires ServiceLocator.authRepository.login plus
 *  LocalPreferencesStorage.setStoredUserId, mirroring the Dart call sequence. */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: (userId: String) -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    onRegisterOrganization: () -> Unit,
    login: suspend (identifier: String, password: String) -> String,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()

    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var workspace by remember { mutableStateOf("") }
    var obscurePassword by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var useOrganization by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var identifierError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var workspaceError by remember { mutableStateOf<String?>(null) }

    // Dart _validateIdentifier
    fun validateIdentifier(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return "Enter your email or username"
        if (value.contains("@")) return ChatyValidators.validateEmail(value)
        return ChatyValidators.validateUsername(value, forRegistration = false)
    }

    fun handleLogin() {
        identifierError = validateIdentifier(identifier)
        passwordError = when {
            password.isEmpty() -> "Please enter your password"
            password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        workspaceError =
            if (useOrganization && workspace.trim().isEmpty()) "Please enter your Workspace ID" else null
        if (identifierError != null || passwordError != null || isLoading) return
        if (useOrganization && workspace.trim().isEmpty()) {
            errorMessage = "Please enter your Workspace / Organization ID."
            return
        }

        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                // Tenant routing lands with the organization-backend slice; the
                // ported AuthRepository performs the default-tenant sign-in.
                val userId = login(identifier.trim(), password)
                onLoggedIn(userId)
            } catch (e: Exception) {
                errorMessage = friendlyLoginError(e)
                isLoading = false
            }
        }
    }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                AuthBackButton(onClick = onBack)
                Spacer(Modifier.height(24.dp))
                Text(
                    "Welcome Back!",
                    color = Color(theme.primaryTextColor),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sign in with your email address or Chaty username.",
                    color = Color(theme.secondaryTextColor),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(24.dp))

                WorkspaceModeSelector(
                    theme = theme,
                    useOrganization = useOrganization,
                    onSelect = { useOrganization = it; errorMessage = null },
                )
                Spacer(Modifier.height(18.dp))

                if (useOrganization) {
                    AuthTextField(
                        label = "Organization / Workspace ID",
                        hintText = "e.g. acme-corp or school_123",
                        theme = theme,
                        value = workspace,
                        onValueChange = { workspace = it },
                        error = workspaceError,
                        prefixIcon = {
                            Icon(
                                Icons.Rounded.Apartment,
                                contentDescription = null,
                                tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.7)),
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                    Spacer(Modifier.height(18.dp))
                }

                AuthTextField(
                    label = "Email or username",
                    hintText = "name@example.com or @username",
                    theme = theme,
                    value = identifier,
                    onValueChange = { identifier = it },
                    keyboardType = KeyboardType.Email,
                    error = identifierError,
                )
                Spacer(Modifier.height(18.dp))
                AuthTextField(
                    label = "Password",
                    hintText = "Enter password",
                    theme = theme,
                    value = password,
                    onValueChange = { password = it },
                    obscureText = obscurePassword,
                    error = passwordError,
                    suffixIcon = {
                        IconButton(onClick = { obscurePassword = !obscurePassword }) {
                            Icon(
                                if (obscurePassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (obscurePassword) "Show password" else "Hide password",
                                tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(22.dp))

                errorMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxWidth()
                            .background(
                                Color(argbWithAlpha(theme.dangerColor, 0.1)),
                                RoundedCornerShape(12.dp),
                            )
                            .border(
                                1.dp,
                                Color(argbWithAlpha(theme.dangerColor, 0.35)),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = Color(theme.dangerColor),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            msg,
                            color = Color(theme.dangerColor),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                AuthPrimaryButton(text = "Log In", theme = theme, onClick = { handleLogin() }, isLoading = isLoading)
                Spacer(Modifier.height(14.dp))

                RegisterOrgCard(theme = theme, onClick = onRegisterOrganization)

                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onForgotPassword) {
                        Text(
                            "Forgot Password?",
                            color = Color(theme.secondaryTextColor),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Text(
                        "Don't have an account? ",
                        color = Color(theme.secondaryTextColor),
                        fontSize = 13.5.sp,
                    )
                    Text(
                        "Register",
                        color = Color(theme.accentColor),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onRegister),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WorkspaceModeSelector(
    theme: ThemeConfig,
    useOrganization: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(argbWithAlpha(theme.surfaceColor, 0.6)),
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                // Dart: dividerColor (secondaryText at 0.2) then withValues(alpha: 0.25) replaces the alpha
                Color(argbWithAlpha(theme.secondaryTextColor, 0.25)),
                RoundedCornerShape(14.dp),
            )
            .padding(4.dp),
    ) {
        ModeTab(
            theme = theme,
            label = "Normal / Default",
            selected = !useOrganization,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(false) },
        )
        ModeTab(
            theme = theme,
            label = "Organization",
            selected = useOrganization,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(true) },
        )
    }
}

@Composable
private fun ModeTab(
    theme: ThemeConfig,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) Color(theme.onAccentColor) else Color(theme.secondaryTextColor)
    Box(
        modifier = modifier
            .background(
                if (selected) Color(theme.accentColor) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = contentColor,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun RegisterOrgCard(theme: ThemeConfig, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(argbWithAlpha(theme.accentColor, 0.06)), RoundedCornerShape(14.dp))
            .border(1.dp, Color(argbWithAlpha(theme.accentColor, 0.2)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(Color(argbWithAlpha(theme.accentColor, 0.15)), RoundedCornerShape(10.dp))
                .padding(7.dp),
        ) {
            Icon(
                Icons.Rounded.DomainAdd,
                contentDescription = null,
                tint = Color(theme.accentColor),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Register an Organization",
                color = Color(theme.primaryTextColor),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                "Set up a custom workspace & team space",
                color = Color(theme.secondaryTextColor),
                fontSize = 11.5.sp,
            )
        }
        Icon(
            Icons.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = Color(theme.accentColor),
            modifier = Modifier.size(14.dp),
        )
    }
}
