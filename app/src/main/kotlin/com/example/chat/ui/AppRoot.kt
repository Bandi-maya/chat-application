package com.example.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chat.ServiceLocator
import com.example.chat.core.theme.ThemeController
import com.example.chat.ui.auth.AuthBackButton
import com.example.chat.ui.auth.LoginScreen
import com.example.chat.ui.auth.SplashScreen
import com.example.chat.ui.auth.WelcomeScreen
import com.example.chat.ui.theme.ChatyTheme
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** App shell with the auth navigation graph (splash -> welcome -> login).
 *  Chats home, conversation, register, forgot-password and organization
 *  registration destinations land in their own parity slices; the register /
 *  forgot-password / org buttons route to honest WIP stubs until then. */
private enum class AuthRoute { Splash, Welcome, Login, Register, ForgotPassword, RegisterOrganization, SubscriptionPlan, Home, ChatDetail, Security }

@Composable
fun AppRoot() {
    val themeController = remember { ServiceLocator.themeController }
    var themeRevision by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(themeController) {
        val listener: () -> Unit = { themeRevision++ }
        themeController.addListener(listener)
        onDispose { themeController.removeListener(listener) }
    }
    // themeRevision re-reads the controller's current theme on every mutation.
    @Suppress("UNUSED_EXPRESSION") themeRevision
    ChatyTheme(config = themeController.globalTheme) {
        var route by remember { mutableStateOf(AuthRoute.Splash) }
        var selectedConversation by remember { mutableStateOf<com.example.chat.domain.models.Conversation?>(null) }
        val lockRequired by ServiceLocator.appLock.lockRequired.collectAsState()
        androidx.compose.runtime.LaunchedEffect(route) {
            // Dart _scheduleInitialAppLockIfNeeded on reaching the authenticated shell.
            if (route == AuthRoute.Home) ServiceLocator.appLock.scheduleInitialIfNeeded()
        }
        var promptPending by remember { mutableStateOf(false) }
        // Dart AuthChangeEvent.signedOut: reset lock/prompt state and replace the stack
        // with WelcomeScreen (pushAndRemoveUntil).
        androidx.compose.runtime.DisposableEffect(Unit) {
            val signOutListener: () -> Unit = {
                if (!ServiceLocator.apiBackend.isAuthenticated &&
                    (route == AuthRoute.Home || route == AuthRoute.ChatDetail || route == AuthRoute.Security)
                ) {
                    ServiceLocator.appLock.onSignedOut()
                    promptPending = false
                    selectedConversation = null
                    route = AuthRoute.Welcome
                }
            }
            ServiceLocator.apiBackend.addListener(signOutListener)
            onDispose { ServiceLocator.apiBackend.removeListener(signOutListener) }
        }
        var showLockPrompt by remember { mutableStateOf(false) }
        // Dart _handleSecurityPreferenceChanged: disabling app lock clears pending lock state.
        androidx.compose.runtime.DisposableEffect(Unit) {
            val prefListener: () -> Unit = {
                if (!ServiceLocator.preferencesController.security.isAppLockEnabled) {
                    ServiceLocator.appLock.onLockDisabled()
                }
            }
            ServiceLocator.preferencesController.addListener(prefListener)
            onDispose { ServiceLocator.preferencesController.removeListener(prefListener) }
        }
        // Dart _schedulePostLoginAppLockPrompt: 650ms after the shell appears post-login,
        // offer app-lock setup unless a usable lock method is already configured.
        androidx.compose.runtime.LaunchedEffect(route, promptPending) {
            if (promptPending && route == AuthRoute.Home) {
                kotlinx.coroutines.delay(650)
                promptPending = false
                val sec = ServiceLocator.preferencesController.security
                val ready = sec.isAppLockEnabled &&
                    com.example.chat.data.services.ProtectedResourceGate.isMethodConfigured(
                        ServiceLocator.localLockService, sec.lockMethod,
                    )
                if (!ready && ServiceLocator.apiBackend.isAuthenticated) showLockPrompt = true
            }
        }
        if (showLockPrompt) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showLockPrompt = false },
                title = { androidx.compose.material3.Text("Enable App Lock?") },
                text = {
                    androidx.compose.material3.Text(
                        "You can protect Chaty with biometrics, device lock, PIN, pattern, or password. Setup is optional and can be changed anytime in Settings.",
                    )
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showLockPrompt = false }) {
                        androidx.compose.material3.Text("Not now")
                    }
                },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = { showLockPrompt = false; route = AuthRoute.Security }) {
                        androidx.compose.material3.Text("Open settings")
                    }
                },
            )
        }
        Box(Modifier.fillMaxSize()) {
        when (route) {
            AuthRoute.Splash -> SplashScreen(
                routeToHome = {
                    // Mirrors splash_screen.dart: backend.initialize() best-effort,
                    // then route on authentication state.
                    try {
                        ServiceLocator.sessionManager.restore()
                    } catch (_: Exception) {
                        // Authentication remains the source of truth.
                    }
                    // Facade precedence (backend_service.dart): a live REST
                    // (ApiBackendService) session also counts as authenticated.
                    ServiceLocator.sessionManager.currentUserId() != null ||
                        ServiceLocator.apiBackend.isAuthenticated
                },
                onRoute = { isAuthed ->
                    // Dart pushes MainNavigationShell when authenticated; the
                    // chats-home slice replaces the Home stub destination.
                    route = if (isAuthed) AuthRoute.Home else AuthRoute.Welcome
                },
            )
            AuthRoute.Welcome -> WelcomeScreen(
                onLogin = { route = AuthRoute.Login },
                onRegister = { route = AuthRoute.Register },
            )
            AuthRoute.Login -> LoginScreen(
                onBack = { route = AuthRoute.Welcome },
                onLoggedIn = { userId ->
                    ServiceLocator.appLock.onSignedIn()
                    promptPending = true
                    // Mirrors login_screen.dart: setStoredUserId, then
                    // pushAndRemoveUntil(MainNavigationShell). The chats-home
                    // slice replaces the Home stub destination.
                    ServiceLocator.appScope.launch {
                        ServiceLocator.preferencesStorage.setStoredUserId(userId)
                    }
                    route = AuthRoute.Home
                },
                onForgotPassword = { route = AuthRoute.ForgotPassword },
                onRegister = { route = AuthRoute.Register },
                onRegisterOrganization = { route = AuthRoute.RegisterOrganization },
                loadSavedTenant = {
                    ServiceLocator.apiBackend.config.getSavedTenantId() to
                        ServiceLocator.apiBackend.config.isUsingDefaultTenant
                },
                login = { identifier, password, tenant, isDefaultTenant ->
                    val user = ServiceLocator.apiBackend.login(identifier, password, tenant, isDefaultTenant)
                    user.id
                },
            )
            AuthRoute.Register -> com.example.chat.ui.auth.RegisterScreen(
                onBack = { route = AuthRoute.Welcome },
                onRegistered = { userId, _ ->
                    ServiceLocator.appLock.onSignedIn()
                    promptPending = true
                    ServiceLocator.appScope.launch {
                        ServiceLocator.preferencesStorage.setStoredUserId(userId)
                    }
                    route = AuthRoute.Home
                },
                onRegisterOrganization = { route = AuthRoute.RegisterOrganization },
                loadSavedTenant = {
                    ServiceLocator.apiBackend.config.getSavedTenantId() to
                        ServiceLocator.apiBackend.config.isUsingDefaultTenant
                },
                register = { email, username, password, tenant, isDefaultTenant ->
                    // Dart _handleRegister: apply the tenant selection, then
                    // ApiBackendService.register sends only the three fields;
                    // the display name arrives in the server response.
                    if (isDefaultTenant) {
                        ServiceLocator.apiBackend.config.setTenantId(null, useDefault = true)
                    } else if (!tenant.isNullOrBlank()) {
                        ServiceLocator.apiBackend.config.setTenantId(tenant, useDefault = false)
                    }
                    val user = ServiceLocator.apiBackend.register(email, username, password)
                    user.id to user.displayName
                },
                checkOrganization = { workspace ->
                    val data = ServiceLocator.apiBackend.checkOrganization(workspace)
                    val (name, allow) = com.example.chat.data.api.ApiAuthClient.orgCheckToBanner(data, workspace)
                    com.example.chat.ui.auth.OrgCheckResult(name, allow)
                },
            )
            AuthRoute.ForgotPassword -> com.example.chat.ui.auth.ForgotPasswordScreen(
                onBack = { route = AuthRoute.Login },
                // Dart prefers ApiBackendService.forgotPassword when registered
                // (it is, in locator) over the Supabase path.
                sendRecovery = { email -> ServiceLocator.apiBackend.forgotPassword(email) },
            )
            AuthRoute.RegisterOrganization -> com.example.chat.ui.auth.RegisterOrganizationScreen(
                onBack = { route = AuthRoute.Login },
                onRegistered = { userId, _, _ ->
                    ServiceLocator.appLock.onSignedIn()
                    promptPending = true
                    ServiceLocator.appScope.launch {
                        ServiceLocator.preferencesStorage.setStoredUserId(userId)
                    }
                    route = AuthRoute.SubscriptionPlan
                },
                registerOrganization = { tenantId, orgName, adminUser, adminEmail, adminPass, adminFull, allowReg ->
                    val (user, orgId, orgNameResolved) = ServiceLocator.apiBackend.registerOrganization(
                        tenantId = tenantId,
                        organizationName = orgName,
                        adminUsername = adminUser,
                        adminEmail = adminEmail,
                        adminPassword = adminPass,
                        adminFullName = adminFull,
                        allowUserRegistration = allowReg,
                    )
                    Triple(user.id, orgId, orgNameResolved)
                },
            )
            AuthRoute.SubscriptionPlan -> AuthWipStub(
                "Plan selection arrives with the subscription slice",
                onBack = { route = AuthRoute.Home },
            )
            AuthRoute.Home -> run {
                // Observe ApiBackendService chat state (listener -> recompose).
                var chatRevision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                androidx.compose.runtime.DisposableEffect(Unit) {
                    val listener: () -> Unit = { chatRevision++ }
                    ServiceLocator.apiBackend.addListener(listener)
                    onDispose { ServiceLocator.apiBackend.removeListener(listener) }
                }
                @Suppress("UNUSED_EXPRESSION") chatRevision
                com.example.chat.ui.chats.ChatsHomeScreen(
                    conversations = ServiceLocator.apiBackend.conversations,
                    requiresSubscription = ServiceLocator.apiBackend.requiresSubscription,
                    subscriptionMessage = ServiceLocator.apiBackend.subscriptionErrorMessage,
                    onOpenConversation = { conversation ->
                        ServiceLocator.apiBackend.setActiveConversation(conversation.id)
                        selectedConversation = conversation
                        route = AuthRoute.ChatDetail
                    },
                )
            }
            AuthRoute.Security -> com.example.chat.ui.settings.security.SecurityCenterScreen(
                preferences = ServiceLocator.preferencesController,
                lockService = ServiceLocator.localLockService,
                onBack = { route = AuthRoute.Home },
                onOpenLockedChats = { },
            )
            AuthRoute.ChatDetail -> run {
                val conversation = selectedConversation
                if (conversation == null) {
                    route = AuthRoute.Home
                } else {
                    var chatRevision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        val listener: () -> Unit = { chatRevision++ }
                        ServiceLocator.apiBackend.addListener(listener)
                        onDispose { ServiceLocator.apiBackend.removeListener(listener) }
                    }
                    @Suppress("UNUSED_EXPRESSION") chatRevision
                    com.example.chat.ui.chats.ChatDetailScreen(
                        conversation = conversation,
                        messages = ServiceLocator.apiBackend.getMessages(conversation.id),
                        currentUserId = ServiceLocator.apiBackend.currentUser?.id ?: "",
                        onBack = {
                            ServiceLocator.apiBackend.setActiveConversation(null)
                            selectedConversation = null
                            route = AuthRoute.Home
                        },
                        onSend = { text ->
                            ServiceLocator.apiBackend.sendMessage(conversation.id, text)
                        },
                    )
                }
            }
        }
        // Dart shouldShowLock: authenticated && lock enabled && _appLockRequired.
        if (lockRequired && ServiceLocator.apiBackend.isAuthenticated &&
            ServiceLocator.preferencesController.security.isAppLockEnabled
        ) {
            com.example.chat.ui.settings.security.AppLockOverlay(
                preferences = ServiceLocator.preferencesController,
                lockService = ServiceLocator.localLockService,
                onUnlocked = { ServiceLocator.appLock.onUnlocked() },
            )
        }
        }
    }
}

/** Honest work-in-progress destination for not-yet-ported auth screens. */
@Composable
private fun AuthWipStub(title: String, onBack: () -> Unit) {
    val theme = LocalThemeConfig.current
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            AuthBackButton(onClick = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(title, color = Color(theme.primaryTextColor))
            }
        }
    }
}
