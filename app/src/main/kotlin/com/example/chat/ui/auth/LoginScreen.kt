package com.example.chat.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.auth.friendlyLoginError
import com.example.chat.data.api.ApiSessionStorageException
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.core.validators.ChatyValidators
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch
import com.example.chat.ui.kit.ChatyAdaptiveLogic

/** Label for the secure-storage-failure support action. A share chooser is
 *  not customer care, so this is "Share error report" until a verified support
 *  address exists (publisher review 2026-11-04; Bandi asked, pending) - then
 *  flip this single constant to "Contact customer care". */
private const val STORAGE_FAILURE_ACTION_LABEL = "Share error report"

/** Port of lib/features/auth/login_screen.dart. The login call is injected so
 *  the screen never touches the service locator directly (testable seam);
 *  AppRoot wires ServiceLocator.apiBackend login plus
 *  LocalPreferencesStorage.setStoredUserId, mirroring the Dart call sequence. */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: (userId: String) -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    onRegisterOrganization: () -> Unit,
    login: suspend (identifier: String, password: String, tenant: String?, isDefaultTenant: Boolean) -> String,
    demoLogin: (suspend () -> String)? = null,
    /** Dart initState: getSavedTenantId + isUsingDefaultTenant. */
    loadSavedTenant: suspend () -> Pair<String?, Boolean>,
    initialIdentifier: String = "",
    initialWorkspace: String? = null,
    requestEmailCode: (suspend (email: String, tenant: String?, isDefaultTenant: Boolean) -> com.example.chat.data.api.ApiBackendService.EmailCodeAttempt)? = null,
    verifyEmailCode: (suspend (com.example.chat.data.api.ApiBackendService.EmailCodeAttempt, String) -> String)? = null,
) {
    // Keep the deployed password route as the default until code-login backend is live.
    var useEmailCode by remember { mutableStateOf(false) }
    var identifier by remember(initialIdentifier) { mutableStateOf(initialIdentifier) }
    var workspace by remember(initialWorkspace) { mutableStateOf(initialWorkspace.orEmpty()) }
    var useOrganization by remember(initialWorkspace) { mutableStateOf(!initialWorkspace.isNullOrBlank()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (initialWorkspace == null) {
            val (saved, usingDefault) = loadSavedTenant()
            useOrganization = !usingDefault && !saved.isNullOrBlank() &&
                !saved.equals(com.example.chat.data.api.BackendRoutingPolicy.DEFAULT_TENANT_ID, ignoreCase = true)
            workspace = if (useOrganization) saved.orEmpty() else ""
        }
    }
    if (useEmailCode && requestEmailCode != null && verifyEmailCode != null) {
        EmailCodeLoginForm(onBack, onLoggedIn, onRegister, onRegisterOrganization,
            { workspace.takeIf { useOrganization } to !useOrganization }, identifier,
            workspace.takeIf { useOrganization }, requestEmailCode, verifyEmailCode, demoLogin,
            onUsePassword = { email, selectedWorkspace, organization ->
                identifier = email
                workspace = selectedWorkspace
                useOrganization = organization
                useEmailCode = false
            })
        return
    }
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var obscurePassword by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var identifierError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var workspaceError by remember { mutableStateOf<String?>(null) }
    // Non-null only when sign-in was stopped by a secure-storage failure
    // (fail-closed); drives the Contact-customer-care action (Bandi 2026-11-04).
    var storageFailure by remember { mutableStateOf<ApiSessionStorageException?>(null) }
    val context = LocalContext.current


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
        storageFailure = null
        scope.launch {
            try {
                val userId = login(
                    identifier.trim(),
                    password,
                    if (useOrganization) workspace.trim() else null,
                    !useOrganization,
                )
                onLoggedIn(userId)
            } catch (e: Exception) {
                storageFailure = e as? ApiSessionStorageException
                errorMessage = friendlyLoginError(e)
                isLoading = false
            }
        }
    }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ChatyAdaptiveLogic.AUTH_COLUMN_MAX_DP.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = AuthCapsuleTokens.tapTarget)) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, size = 20.dp, color = Color(theme.accentColor))
                    Spacer(Modifier.width(12.dp))
                    Text("Back", color = Color(theme.secondaryTextColor))
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Welcome back",
                    color = Color(theme.primaryTextColor),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sign in to Chaty.",
                    color = Color(theme.secondaryTextColor),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(24.dp))

                AuthCapsuleModeSelector(
                    theme = theme,
                    useOrganization = useOrganization,
                    onSelect = { useOrganization = it; errorMessage = null },
                )
                Spacer(Modifier.height(18.dp))

                if (useOrganization) {
                    AuthTextField(
                        label = "Workspace ID",
                        hintText = "e.g. acme-corp",
                        capsule = true,
                        theme = theme,
                        value = workspace,
                        onValueChange = { workspace = it },
                        error = workspaceError,
                        prefixIcon = {
                            AuthRoleIcon("workspace", theme)
                        },
                    )
                    Spacer(Modifier.height(18.dp))
                }

                AuthTextField(
                    label = "Email or username",
                    hintText = "Email or @username",
                    capsule = true,
                    prefixIcon = { AuthRoleIcon("mail", theme) },
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
                    capsule = true,
                    prefixIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock, size = 20.dp, color = Color(theme.secondaryTextColor)) },
                    theme = theme,
                    value = password,
                    onValueChange = { password = it },
                    obscureText = obscurePassword,
                    error = passwordError,
                    suffixIcon = {
                        IconButton(onClick = { obscurePassword = !obscurePassword }) {
                            AuthRoleIcon(if (obscurePassword) "eyeOff" else "eye", theme,
                                if (obscurePassword) "Show password" else "Hide password")
                        }
                    },
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onForgotPassword, modifier = Modifier.heightIn(min = AuthCapsuleTokens.tapTarget)) {
                        Text(
                            "Forgot Password?",
                            color = Color(theme.accentColor),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
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
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.info,
                            semanticLabel = null,
                            color = Color(theme.dangerColor),
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

                storageFailure?.let { failure ->
                    // A share chooser is not "customer care": this stays
                    // STORAGE_FAILURE_ACTION_LABEL ("Share error report") until
                    // a verified support address exists (asked Bandi, pending),
                    // then the single constant flips to "Contact customer care"
                    // and the intent becomes a direct mailto. The exported
                    // report contains only the allowlisted failure copy - never
                    // the raw store exception, identifier, password, or tokens.
                    TextButton(
                        onClick = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Chaty sign-in problem: secure storage failure")
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "Customer care,\n\nChaty could not sign me in because the app could not save the session securely on this device.\n\nDetails shown by the app:\n" +
                                        (failure.message ?: "unknown reason") +
                                        "\n\nPlease advise how to fix this.",
                                )
                            }
                            runCatching {
                                context.startActivity(
                                    android.content.Intent.createChooser(send, "Contact customer care"),
                                )
                            }
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        Text(
                            STORAGE_FAILURE_ACTION_LABEL,
                            color = Color(theme.linkColor),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                AuthPrimaryButton(text = "Log In", theme = theme, onClick = { handleLogin() }, isLoading = isLoading, capsule = true)
                if (requestEmailCode != null && verifyEmailCode != null) {
                    TextButton(onClick = { useEmailCode = true }, enabled = !isLoading,
                        modifier = Modifier.heightIn(min = AuthCapsuleTokens.tapTarget)) {
                        Text("Use email code instead", color = Color(theme.accentColor))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "New to Chaty? ",
                        color = Color(theme.accentColor),
                        fontSize = 13.5.sp,
                    )
                    Text(
                        "Register",
                        color = Color(theme.accentColor),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.heightIn(min = AuthCapsuleTokens.tapTarget).clickable(onClick = onRegister).padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.HorizontalDivider(color = Color(argbWithAlpha(theme.secondaryTextColor, 0.2)))
                AuthTextLink("Register a New Organization", theme, onRegisterOrganization)
                if (demoLogin != null) {
                    AuthTextLink(text = "Try demo", theme = theme, enabled = !isLoading, onClick = {
                        errorMessage = null
                        storageFailure = null
                        isLoading = true
                        scope.launch {
                            try {
                                val userId = demoLogin()
                                onLoggedIn(userId)
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Demo login failed. Please try again."
                                isLoading = false
                            }
                        }
                    })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Offline demo entry (NOT in Flutter): clearly labelled sample session. */
@Composable
private fun DemoLoginCard(theme: ThemeConfig, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(argbWithAlpha(theme.accentColor, 0.06)), RoundedCornerShape(14.dp))
            .border(1.dp, Color(argbWithAlpha(theme.accentColor, 0.2)), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(Color(argbWithAlpha(theme.accentColor, 0.15)), RoundedCornerShape(10.dp))
                .padding(7.dp),
        ) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.info,
                semanticLabel = null,
                color = Color(theme.accentColor),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Try the demo",
                color = Color(theme.primaryTextColor),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                "Explore with offline sample data - no account or server",
                color = Color(theme.secondaryTextColor),
                fontSize = 11.5.sp,
            )
        }
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
            semanticLabel = null,
            color = Color(theme.accentColor),
            modifier = Modifier.size(14.dp),
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
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
            semanticLabel = null,
            color = Color(theme.accentColor),
            modifier = Modifier.size(14.dp),
        )
    }
}


/** Existing Login route's approved email-code mode. No local success bypass. */
@Composable
private fun EmailCodeLoginForm(
    onBack: () -> Unit, onLoggedIn: (String) -> Unit,
    onRegister: () -> Unit, onRegisterOrganization: () -> Unit,
    loadSavedTenant: suspend () -> Pair<String?, Boolean>,
    initialEmail: String, initialWorkspace: String?,
    requestCode: suspend (String, String?, Boolean) -> com.example.chat.data.api.ApiBackendService.EmailCodeAttempt,
    verifyCode: suspend (com.example.chat.data.api.ApiBackendService.EmailCodeAttempt, String) -> String,
    demoLogin: (suspend () -> String)?,
    onUsePassword: (String, String, Boolean) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail.takeIf { it.contains('@') }.orEmpty()) }
    var workspace by remember { mutableStateOf(initialWorkspace.orEmpty()) }
    var organization by remember { mutableStateOf(!initialWorkspace.isNullOrBlank()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf<com.example.chat.data.api.ApiBackendService.EmailCodeAttempt?>(null) }
    var signedInUser by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (initialWorkspace == null) {
            val (saved, usingDefault) = loadSavedTenant()
            organization = !usingDefault && !saved.isNullOrBlank()
            workspace = if (organization) saved.orEmpty() else ""
        }
    }
    val active = attempt
    if (active != null) {
        androidx.compose.runtime.key(active.challenge.id) {
            OtpVerificationScreen(
                email = active.challenge.email, purpose = OtpPurpose.SignIn,
                onBack = { attempt = null; error = null },
                verify = { code -> signedInUser = verifyCode(active, code) },
                resend = {
                    attempt = requestCode(active.challenge.email,
                        active.targetTenant.takeUnless { active.isDefaultTenant }, active.isDefaultTenant)
                    "If an account exists, a new sign-in code has been sent."
                },
                onVerified = { signedInUser?.let(onLoggedIn) ?: throw IllegalStateException("Server session was not established.") },
                deliveryMessage = "If an account exists for this email, a sign-in code has been sent.",
                initialResendSeconds = active.challenge.resendAfterSeconds,
            )
        }
        return
    }
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = ChatyAdaptiveLogic.AUTH_COLUMN_MAX_DP.dp).fillMaxWidth().padding(24.dp)) {
                AuthBackButton(onClick = onBack)
                Spacer(Modifier.height(24.dp))
                Text("Welcome back", color = Color(theme.primaryTextColor), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Sign in with an email code.", color = Color(theme.secondaryTextColor))
                Spacer(Modifier.height(24.dp))
                AuthCapsuleModeSelector(theme, organization) { organization = it; error = null }
                if (organization) {
                    Spacer(Modifier.height(18.dp))
                    AuthTextField(label = "Workspace ID", hintText = "e.g. acme-corp", capsule = true,
                        theme = theme, value = workspace, onValueChange = { workspace = it })
                }
                Spacer(Modifier.height(18.dp))
                AuthTextField(label = "Email", hintText = "you@example.com", capsule = true,
                    theme = theme, value = email, onValueChange = { email = it }, keyboardType = KeyboardType.Email)
                Spacer(Modifier.height(18.dp))
                error?.let { Text(it, color = Color(theme.dangerColor)); Spacer(Modifier.height(12.dp)) }
                AuthPrimaryButton(text = "Send code", isLoading = loading, capsule = true, theme = theme, onClick = {
                    error = ChatyValidators.validateEmail(email)
                    if (organization && workspace.isBlank()) error = "Enter a workspace ID."
                    if (error == null && !loading) {
                        loading = true
                        val selectedEmail = email.trim()
                        val selectedWorkspace = workspace.trim().takeIf { organization }
                        val selectedDefault = !organization
                        scope.launch {
                            try { attempt = requestCode(selectedEmail, selectedWorkspace, selectedDefault) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (e: Exception) { error = e.message ?: "Could not request a code." }
                            finally { loading = false }
                        }
                    }
                })
                TextButton(onClick = { onUsePassword(email, workspace, organization) }, enabled = !loading,
                    modifier = Modifier.heightIn(min = AuthCapsuleTokens.tapTarget)) {
                    Text("Use password instead", color = Color(theme.accentColor))
                }
                TextButton(onClick = onRegister, enabled = !loading) { Text("Create account", color = Color(theme.accentColor)) }
                TextButton(onClick = onRegisterOrganization, enabled = !loading) { Text("Create a workspace", color = Color(theme.secondaryTextColor)) }
                if (demoLogin != null) {
                    TextButton(enabled = !loading, onClick = {
                        loading = true
                        scope.launch {
                            try { onLoggedIn(demoLogin()) }
                            catch (e: Exception) { error = e.message ?: "Demo unavailable." }
                            finally { loading = false }
                        }
                    }) { Text("Offline demo", color = Color(theme.secondaryTextColor)) }
                }
            }
        }
    }
}
