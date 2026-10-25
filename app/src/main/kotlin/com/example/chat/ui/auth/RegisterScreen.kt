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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.chat.core.auth.friendlyRegisterError
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.core.validators.ChatyValidators
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Result of the workspace availability/self-registration probe
 *  (ApiBackendService.checkOrganization on the Dart side). */
data class OrgCheckResult(val name: String, val allowUserRegistration: Boolean)

/** Port of lib/features/auth/register_screen.dart.
 *
 *  Injected seams (wired by AppRoot):
 *  - [register]: default-tenant sign-up (AuthRepository.registerUser).
 *  - [checkOrganization]: workspace probe. The ApiBackendService slice has not
 *    landed yet; AppRoot wires a seam that surfaces the workspace-not-found
 *    banner for organization mode until then. Default-mode registration is
 *    fully functional against the real backend. */
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onRegistered: (userId: String, displayName: String) -> Unit,
    onRegisterOrganization: () -> Unit,
    register: suspend (email: String, username: String, password: String) -> Pair<String, String>,
    checkOrganization: suspend (workspace: String) -> OrgCheckResult,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var workspace by remember { mutableStateOf("") }
    var obscurePassword by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var useOrganization by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var emailError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var workspaceError by remember { mutableStateOf<String?>(null) }

    var orgCheckJob by remember { mutableStateOf<Job?>(null) }
    var isCheckingOrg by remember { mutableStateOf(false) }
    var orgFound by remember { mutableStateOf<Boolean?>(null) }
    var orgName by remember { mutableStateOf<String?>(null) }
    var allowUserRegistration by remember { mutableStateOf(true) }
    var orgCheckError by remember { mutableStateOf<String?>(null) }

    fun checkOrg(ws: String) {
        scope.launch {
            try {
                val data = checkOrganization(ws)
                isCheckingOrg = false
                orgFound = true
                orgName = data.name
                allowUserRegistration = data.allowUserRegistration
                orgCheckError = null
                if (!data.allowUserRegistration) errorMessage = null
            } catch (e: Exception) {
                isCheckingOrg = false
                orgFound = false
                orgName = null
                allowUserRegistration = false
                orgCheckError = (e.message ?: e.toString()).replaceFirst("Exception: ", "")
            }
        }
    }

    fun onWorkspaceChanged(value: String) {
        workspace = value
        orgCheckJob?.cancel()
        val clean = value.trim()
        if (clean.isEmpty()) {
            isCheckingOrg = false
            orgFound = null
            orgName = null
            allowUserRegistration = true
            orgCheckError = null
            return
        }
        isCheckingOrg = true
        orgCheckError = null
        orgCheckJob = scope.launch {
            delay(400) // Dart: 400ms debounce timer
            checkOrg(clean)
        }
    }

    fun handleRegister() {
        if (isLoading) return
        emailError = ChatyValidators.validateEmail(email.trim())
        usernameError = ChatyValidators.validateUsername(username.trim(), forRegistration = true)
        passwordError = when {
            password.isEmpty() -> "Please enter a password"
            password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        workspaceError =
            if (useOrganization && workspace.trim().isEmpty()) "Please enter your Workspace ID" else null
        if (emailError != null || usernameError != null || passwordError != null || workspaceError != null) return

        if (useOrganization) {
            if (isCheckingOrg) {
                errorMessage = "Checking workspace settings, please wait..."
                return
            }
            if (!allowUserRegistration) {
                errorMessage =
                    "User self-registration is disabled for \"${orgName ?: workspace.trim()}\". Contact your administrator."
                return
            }
        }

        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val (userId, displayName) = register(email.trim(), username.trim(), password)
                onRegistered(userId, displayName)
            } catch (e: Exception) {
                errorMessage = friendlyRegisterError(e)
                isLoading = false
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
                    Text(
                        "Everything You Need!",
                        color = Color(theme.primaryTextColor),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Create your Chaty account with a unique public username.",
                        color = Color(theme.secondaryTextColor),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(20.dp))

                    WorkspaceModeSelector(
                        theme = theme,
                        useOrganization = useOrganization,
                        onSelect = { org ->
                            useOrganization = org
                            errorMessage = null
                            if (org && workspace.trim().isNotEmpty()) checkOrg(workspace.trim())
                        },
                    )
                    Spacer(Modifier.height(18.dp))

                    if (useOrganization) {
                        AuthTextField(
                            label = "Organization / Workspace ID",
                            hintText = "e.g. acme-corp or school_123",
                            theme = theme,
                            value = workspace,
                            onValueChange = { onWorkspaceChanged(it) },
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
                        OrgStatusBanner(
                            theme = theme,
                            workspace = workspace.trim(),
                            isChecking = isCheckingOrg,
                            orgFound = orgFound,
                            orgName = orgName,
                            allowUserRegistration = allowUserRegistration,
                            orgCheckError = orgCheckError,
                            onDefaultMode = { useOrganization = false; errorMessage = null },
                            onNewOrg = onRegisterOrganization,
                        )
                        Spacer(Modifier.height(18.dp))
                    }

                    AuthTextField(
                        label = "Email",
                        hintText = "Enter email",
                        theme = theme,
                        value = email,
                        onValueChange = { email = it },
                        keyboardType = KeyboardType.Email,
                        error = emailError,
                    )
                    Spacer(Modifier.height(18.dp))
                    AuthTextField(
                        label = "Username",
                        hintText = "Choose a unique username",
                        theme = theme,
                        value = username,
                        onValueChange = { username = it },
                        error = usernameError,
                        prefixIcon = {
                            Icon(
                                Icons.Rounded.AlternateEmail,
                                contentDescription = null,
                                tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.7)),
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                    Spacer(Modifier.height(18.dp))
                    AuthTextField(
                        label = "Password",
                        hintText = "Minimum 6 characters",
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
                    Spacer(Modifier.height(24.dp))

                    errorMessage?.let { msg ->
                        Row(
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .fillMaxWidth()
                                .background(Color(argbWithAlpha(theme.dangerColor, 0.1)), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(argbWithAlpha(theme.dangerColor, 0.35)), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = Color(theme.dangerColor), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(msg, color = Color(theme.dangerColor), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    val registrationBlocked = useOrganization && !allowUserRegistration
                    AuthPrimaryButton(
                        text = if (registrationBlocked) "Registration Disabled" else "Register",
                        theme = theme,
                        onClick = { if (!registrationBlocked) handleRegister() },
                        isLoading = isLoading,
                    )
                    Spacer(Modifier.height(14.dp))

                    RegisterOrgCard(theme = theme, onClick = onRegisterOrganization)

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text("Already have an account? ", color = Color(theme.secondaryTextColor), fontSize = 13.5.sp)
                        Text(
                            "Log In",
                            color = Color(theme.accentColor),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onBack),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun OrgStatusBanner(
    theme: ThemeConfig,
    workspace: String,
    isChecking: Boolean,
    orgFound: Boolean?,
    orgName: String?,
    allowUserRegistration: Boolean,
    orgCheckError: String?,
    onDefaultMode: () -> Unit,
    onNewOrg: () -> Unit,
) {
    if (workspace.isEmpty()) return

    if (isChecking) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 4.dp)
                .fillMaxWidth()
                .background(Color(argbWithAlpha(theme.surfaceColor, 0.5)), RoundedCornerShape(12.dp))
                .border(1.dp, Color(argbWithAlpha(theme.secondaryTextColor, 0.2)), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(theme.accentColor))
            Spacer(Modifier.width(10.dp))
            Text(
                "Checking workspace registration support...",
                color = Color(theme.secondaryTextColor),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        return
    }

    if (orgFound == true) {
        if (allowUserRegistration) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp).padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (!orgName.isNullOrEmpty()) "Organization: $orgName" else "Valid Organization",
                        color = Color(0xFF047857),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "User self-registration is enabled for this workspace.",
                        color = Color(0xFF047857).copy(alpha = 0.9f),
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .background(Color(0xFFF59E0B).copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Row {
                    Box(
                        modifier = Modifier.background(Color(0xFFF59E0B).copy(alpha = 0.2f), CircleShape).padding(6.dp),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Self-Registration Disabled", color = Color(0xFFB45309), fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Organization \"${orgName ?: workspace}\" has restricted user self-registration. Only administrators can add or invite members to this workspace.",
                            color = Color(0xFF92400E),
                            fontSize = 12.sp,
                            lineHeight = 16.2.sp,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    OutlinedButton(
                        onClick = onDefaultMode,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB45309).copy(alpha = 0.4f)),
                    ) {
                        Text("Default Mode", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onNewOrg,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309), contentColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        Text("New Org", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    if (orgFound == false && orgCheckError != null) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 4.dp)
                .fillMaxWidth()
                .background(Color(argbWithAlpha(theme.dangerColor, 0.08)), RoundedCornerShape(12.dp))
                .border(1.dp, Color(argbWithAlpha(theme.dangerColor, 0.3)), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(theme.dangerColor), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(orgCheckError, color = Color(theme.dangerColor), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
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
        Box(modifier = Modifier.background(Color(argbWithAlpha(theme.accentColor, 0.15)), RoundedCornerShape(10.dp)).padding(7.dp)) {
            Icon(
                androidx.compose.material.icons.Icons.Rounded.DomainAdd,
                contentDescription = null,
                tint = Color(theme.accentColor),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // Register screen's card title differs from the login screen's.
            Text("Register a New Organization", color = Color(theme.primaryTextColor), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(1.dp))
            Text("Set up a custom workspace & team space", color = Color(theme.secondaryTextColor), fontSize = 11.5.sp)
        }
        Icon(
            androidx.compose.material.icons.Icons.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = Color(theme.accentColor),
            modifier = Modifier.size(14.dp),
        )
    }
}
