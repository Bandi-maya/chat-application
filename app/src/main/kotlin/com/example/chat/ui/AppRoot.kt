package com.example.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
private enum class AuthRoute { Splash, Welcome, Login, Register, ForgotPassword, RegisterOrganization, Home }

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
                    ServiceLocator.sessionManager.currentUserId() != null
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
                login = { identifier, password ->
                    val user = ServiceLocator.authRepository.login(identifier, password)
                    user.id
                },
            )
            AuthRoute.Register -> com.example.chat.ui.auth.RegisterScreen(
                onBack = { route = AuthRoute.Welcome },
                onRegistered = { userId, _ ->
                    ServiceLocator.appScope.launch {
                        ServiceLocator.preferencesStorage.setStoredUserId(userId)
                    }
                    route = AuthRoute.Home
                },
                onRegisterOrganization = { route = AuthRoute.RegisterOrganization },
                register = { email, username, password ->
                    // The Dart register screen collects email/username/password only;
                    // the public username doubles as the display identity.
                    val user = ServiceLocator.authRepository.registerUser(
                        displayName = username,
                        username = username,
                        password = password,
                        email = email,
                    )
                    user.id to user.displayName
                },
                // ApiBackendService.checkOrganization slice has not landed:
                // organization-mode probing surfaces the not-found banner until
                // it does. Default-mode registration is unaffected.
                checkOrganization = { workspace ->
                    throw Exception("tenant not found: $workspace")
                },
            )
            AuthRoute.ForgotPassword -> com.example.chat.ui.auth.ForgotPasswordScreen(
                onBack = { route = AuthRoute.Login },
                sendRecovery = { email -> ServiceLocator.authRepository.resetPassword(email) },
            )
            AuthRoute.RegisterOrganization -> AuthWipStub("Register an Organization", onBack = { route = AuthRoute.Login })
            AuthRoute.Home -> AuthWipStub("Chats home arrives in the next slice", onBack = { route = AuthRoute.Welcome })
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
