package com.example.chat.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.DomainAdd
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.chat.core.auth.friendlyOrgRegisterError
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.core.validators.ChatyValidators
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Port of lib/features/auth/register_organization_screen.dart. The backend
 *  call is injected; AppRoot wires ApiBackendService.registerOrganization
 *  (REST) plus the stored-user-id persistence. [onRegistered] receives
 *  (userId, organizationId, organizationName) and routes to the subscription
 *  plan screen destination. */
@Composable
fun RegisterOrganizationScreen(
    onBack: () -> Unit,
    onRegistered: (userId: String, organizationId: String, organizationName: String) -> Unit,
    registerOrganization: suspend (
        tenantId: String,
        organizationName: String,
        adminUsername: String,
        adminEmail: String,
        adminPassword: String,
        adminFullName: String,
        allowUserRegistration: Boolean,
    ) -> Triple<String, String, String>,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()

    var orgName by remember { mutableStateOf("") }
    var tenantId by remember { mutableStateOf("") }
    var adminFullName by remember { mutableStateOf("") }
    var adminUsername by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var obscurePassword by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var hasManuallyEditedSlug by remember { mutableStateOf(false) }
    var allowUserRegistration by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var orgNameError by remember { mutableStateOf<String?>(null) }
    var tenantIdError by remember { mutableStateOf<String?>(null) }
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    fun onOrgNameChanged(value: String) {
        orgName = value
        if (!hasManuallyEditedSlug) {
            tenantId = value.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .replace(Regex("^-+|-+$"), "")
        }
    }

    fun validateTenantId(raw: String): String? {
        val clean = raw.trim().lowercase()
        if (clean.isEmpty()) return "Workspace ID is required"
        if (clean.length < 3) return "Workspace ID must be at least 3 characters"
        if (!Regex("^[a-z0-9_-]+$").matches(clean)) return "Use lowercase letters, numbers, and hyphens only"
        return null
    }

    fun handleRegister() {
        orgNameError = when {
            orgName.trim().isEmpty() -> "Please enter your organization name"
            orgName.trim().length < 2 -> "Name must be at least 2 characters"
            else -> null
        }
        tenantIdError = validateTenantId(tenantId)
        fullNameError = if (adminFullName.trim().isEmpty()) "Please enter administrator full name" else null
        usernameError = ChatyValidators.validateUsername(adminUsername.trim(), forRegistration = true)
        emailError = ChatyValidators.validateEmail(adminEmail.trim())
        passwordError = when {
            adminPassword.isEmpty() -> "Please enter a password"
            adminPassword.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        if (listOf(orgNameError, tenantIdError, fullNameError, usernameError, emailError, passwordError)
                .any { it != null } || isLoading
        ) return

        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val (userId, orgId, resolvedName) = registerOrganization(
                    tenantId.trim().lowercase(),
                    orgName.trim(),
                    adminUsername.trim(),
                    adminEmail.trim(),
                    adminPassword,
                    adminFullName.trim(),
                    allowUserRegistration,
                )
                onRegistered(userId, orgId, resolvedName)
            } catch (e: Exception) {
                errorMessage = friendlyOrgRegisterError(e)
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
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                AuthBackButton(onClick = onBack)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(argbWithAlpha(theme.accentColor, 0.12)), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                    ) {
                        Icon(Icons.Rounded.DomainAdd, contentDescription = null, tint = Color(theme.accentColor), modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Register Organization",
                            color = Color(theme.primaryTextColor),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Create a dedicated workspace for your team",
                            color = Color(theme.secondaryTextColor),
                            fontSize = 13.5.sp,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))

                SectionHeader(theme, "1. Organization Details") {
                    Icon(Icons.Rounded.Apartment, contentDescription = null, tint = Color(theme.accentColor), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(14.dp))
                AuthTextField(
                    label = "Organization Name",
                    hintText = "e.g. Acme Corporation",
                    theme = theme,
                    value = orgName,
                    onValueChange = { onOrgNameChanged(it) },
                    error = orgNameError,
                    prefixIcon = { PrefixIcon(theme) { Icon(Icons.Rounded.Business, null, tint = it, modifier = Modifier.size(20.dp)) } },
                )
                Spacer(Modifier.height(16.dp))
                AuthTextField(
                    label = "Workspace / Tenant ID",
                    hintText = "e.g. acme-corp",
                    theme = theme,
                    value = tenantId,
                    onValueChange = { tenantId = it; hasManuallyEditedSlug = true },
                    error = tenantIdError,
                    prefixIcon = { PrefixIcon(theme) { Icon(Icons.Rounded.Link, null, tint = it, modifier = Modifier.size(20.dp)) } },
                )
                Text(
                    "Your team members will use this Workspace ID to sign into your organization.",
                    color = Color(argbWithAlpha(theme.secondaryTextColor, 0.8)),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
                Spacer(Modifier.height(28.dp))

                SectionHeader(theme, "2. Admin Account") {
                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = Color(theme.accentColor), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(14.dp))
                AuthTextField(
                    label = "Admin Full Name",
                    hintText = "e.g. Alex Rivera",
                    theme = theme,
                    value = adminFullName,
                    onValueChange = { adminFullName = it },
                    error = fullNameError,
                    prefixIcon = { PrefixIcon(theme) { Icon(Icons.Rounded.Badge, null, tint = it, modifier = Modifier.size(20.dp)) } },
                )
                Spacer(Modifier.height(16.dp))
                AuthTextField(
                    label = "Admin Username",
                    hintText = "e.g. alex_admin",
                    theme = theme,
                    value = adminUsername,
                    onValueChange = { adminUsername = it },
                    error = usernameError,
                    prefixIcon = { PrefixIcon(theme) { Icon(Icons.Rounded.AlternateEmail, null, tint = it, modifier = Modifier.size(20.dp)) } },
                )
                Spacer(Modifier.height(16.dp))
                AuthTextField(
                    label = "Admin Email",
                    hintText = "e.g. alex@acme.com",
                    theme = theme,
                    value = adminEmail,
                    onValueChange = { adminEmail = it },
                    keyboardType = KeyboardType.Email,
                    error = emailError,
                    prefixIcon = { PrefixIcon(theme) { Icon(Icons.Rounded.MailOutline, null, tint = it, modifier = Modifier.size(20.dp)) } },
                )
                Spacer(Modifier.height(16.dp))
                AuthTextField(
                    label = "Admin Password",
                    hintText = "Minimum 6 characters",
                    theme = theme,
                    value = adminPassword,
                    onValueChange = { adminPassword = it },
                    obscureText = obscurePassword,
                    error = passwordError,
                    prefixIcon = { PrefixIcon(theme) { Icon(Icons.Rounded.Lock, null, tint = it, modifier = Modifier.size(20.dp)) } },
                    suffixIcon = {
                        IconButton(onClick = { obscurePassword = !obscurePassword }) {
                            Icon(
                                if (obscurePassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(18.dp))

                AllowRegistrationCard(
                    theme = theme,
                    allow = allowUserRegistration,
                    onChange = { allowUserRegistration = it },
                )
                Spacer(Modifier.height(24.dp))

                errorMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = 18.dp)
                            .fillMaxWidth()
                            .background(Color(argbWithAlpha(theme.dangerColor, 0.12)), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(argbWithAlpha(theme.dangerColor, 0.4)), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(theme.dangerColor), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(msg, color = Color(theme.dangerColor), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                AuthPrimaryButton(
                    text = "Create Organization",
                    theme = theme,
                    onClick = { handleRegister() },
                    isLoading = isLoading,
                )
                Spacer(Modifier.height(20.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = null,
                            tint = Color(theme.secondaryTextColor),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Already have an organization? Log in",
                            color = Color(theme.secondaryTextColor),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefixIcon(theme: ThemeConfig, icon: @Composable (Color) -> Unit) {
    icon(Color(argbWithAlpha(theme.secondaryTextColor, 0.7)))
}

@Composable
private fun SectionHeader(theme: ThemeConfig, title: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = Color(theme.primaryTextColor),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.sp,
        )
    }
}

@Composable
private fun AllowRegistrationCard(theme: ThemeConfig, allow: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(argbWithAlpha(theme.surfaceColor, 0.6)), RoundedCornerShape(16.dp))
            .border(1.dp, Color(argbWithAlpha(theme.secondaryTextColor, 0.25)), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val activeColor = if (allow) theme.accentColor else theme.secondaryTextColor
        Box(
            modifier = Modifier
                .background(Color(argbWithAlpha(activeColor, 0.12)), RoundedCornerShape(10.dp))
                .padding(8.dp),
        ) {
            Icon(Icons.Rounded.GroupAdd, contentDescription = null, tint = Color(activeColor), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Allow Member Self-Registration",
                color = Color(theme.primaryTextColor),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (allow) "Team members can join this workspace with the Workspace ID."
                else "Self-registration disabled (only admins can invite members).",
                color = Color(argbWithAlpha(theme.secondaryTextColor, 0.8)),
                fontSize = 11.5.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = allow,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(theme.accentColor)),
        )
    }
}
