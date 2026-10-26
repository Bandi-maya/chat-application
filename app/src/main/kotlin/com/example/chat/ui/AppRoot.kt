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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Animation

/** App shell with the auth navigation graph (splash -> welcome -> login).
 *  Chats home, conversation, register, forgot-password and organization
 *  registration destinations land in their own parity slices; the register /
 *  forgot-password / org buttons route to honest WIP stubs until then. */
private enum class AuthRoute { Splash, Welcome, Login, Register, ForgotPassword, RegisterOrganization, SubscriptionPlan, Home, ChatDetail, Security, LockedChats, HiddenVault, MessageManagement, LinkedDevices, Starred, NewChat, GlobalSearch }

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
    androidx.compose.runtime.DisposableEffect(Unit) {
        val prefsListener: () -> Unit = { themeRevision++ }
        ServiceLocator.preferencesController.addListener(prefsListener)
        onDispose { ServiceLocator.preferencesController.removeListener(prefsListener) }
    }
    @Suppress("UNUSED_EXPRESSION") themeRevision
    // Dart main.dart:725 GbThemeOverrides.resolve(themeController.globalTheme, prefs).
    val resolvedTheme = com.example.chat.ui.theme.GbThemeOverrides.resolve(
        themeController.globalTheme, ServiceLocator.preferencesController,
    )
    ChatyTheme(config = resolvedTheme) {
    GlobalTypographyScale {
        var route by remember { mutableStateOf(AuthRoute.Splash) }
        // Dart pre-auth Navigator stack: Register is pushed from Welcome or Login, RegisterOrganization from
        // Login or Register; pop returns to whichever pushed it.
        var registerOrigin by remember { mutableStateOf(AuthRoute.Welcome) }
        var orgOrigin by remember { mutableStateOf(AuthRoute.Login) }
        var selectedConversation by remember { mutableStateOf<com.example.chat.domain.models.Conversation?>(null) }
        var planOrgId by remember { mutableStateOf("") }
        var planOrgName by remember { mutableStateOf("") }
        var planIsNew by remember { mutableStateOf(false) }
        var selectedInitialMessageId by remember { mutableStateOf<String?>(null) }
        val lockRequired by ServiceLocator.appLock.lockRequired.collectAsState()
        androidx.compose.runtime.LaunchedEffect(route) {
            // Dart _scheduleInitialAppLockIfNeeded on reaching the authenticated shell.
            if (route == AuthRoute.Home) ServiceLocator.appLock.scheduleInitialIfNeeded()
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            // Dart _AppState.initState: locator<MessageAutomationService>() (30s account sync, gated on a session).
            ServiceLocator.messageAutomation
        }
        var previousLockRequired by remember { mutableStateOf(lockRequired) }
        androidx.compose.runtime.LaunchedEffect(lockRequired) {
            // local_auth stopAuthentication(): a pending prompt never resumes once the lock requirement drops (unlocked
            // another way, or lock disabled). Route changes do not stop it: Security/LockedChats/HiddenVault start
            // their own prompts. Sign-out stops it in the signed-out listener; activity detach in LockPromptHost.
            if (com.example.chat.platform.LockBiometricPolicy.stopOnLockTransition(previousLockRequired, lockRequired)) {
                ServiceLocator.lockBiometrics.stopAuthentication()
            }
            previousLockRequired = lockRequired
        }
        var promptPending by remember { mutableStateOf(false) }
        val gate = remember { GatePrompts() }
        var newChatGroup by remember { mutableStateOf(false) }
        // 0 = QR only (Dart _openQrScreen initialIndex 1, qrOnly), 1 = devices only.
        var linkedDevicesOnly by remember { mutableStateOf(false) }
        var newChatBroadcast by remember { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        // Dart AuthChangeEvent.signedOut: reset lock/prompt state and replace the stack
        // with WelcomeScreen (pushAndRemoveUntil).
        androidx.compose.runtime.DisposableEffect(Unit) {
            val signOutListener: () -> Unit = {
                if (!ServiceLocator.apiBackend.isAuthenticated &&
                    (route == AuthRoute.Home || route == AuthRoute.ChatDetail || route == AuthRoute.Security ||
                        route == AuthRoute.LockedChats ||
                        route == AuthRoute.HiddenVault || route == AuthRoute.MessageManagement || route == AuthRoute.LinkedDevices ||
                        route == AuthRoute.NewChat || route == AuthRoute.GlobalSearch)
                ) {
                    ServiceLocator.appLock.onSignedOut()
                    ServiceLocator.lockBiometrics.stopAuthentication()
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
        // Dart Navigator.pop for the pre-auth stack (Welcome > Login/Register > Forgot/RegisterOrganization);
        // system Back must follow the same targets as each screen's on-screen back arrow.
        androidx.activity.compose.BackHandler(
            enabled = route == AuthRoute.Login || route == AuthRoute.Register ||
                route == AuthRoute.ForgotPassword || route == AuthRoute.RegisterOrganization,
        ) {
            route = when (route) {
                AuthRoute.ForgotPassword -> AuthRoute.Login
                AuthRoute.RegisterOrganization -> orgOrigin
                AuthRoute.Register -> registerOrigin
                else -> AuthRoute.Welcome
            }
        }
        val loginScreen: @Composable () -> Unit = {
            LoginScreen(
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
                onRegister = { registerOrigin = AuthRoute.Login; route = AuthRoute.Register },
                onRegisterOrganization = { orgOrigin = AuthRoute.Login; route = AuthRoute.RegisterOrganization },
                loadSavedTenant = {
                    ServiceLocator.apiBackend.config.getSavedTenantId() to
                        ServiceLocator.apiBackend.config.isUsingDefaultTenant
                },
                login = { identifier, password, tenant, isDefaultTenant ->
                    val user = ServiceLocator.apiBackend.login(identifier, password, tenant, isDefaultTenant)
                    user.id
                },
            )
        }
        val registerScreen: @Composable () -> Unit = {
            com.example.chat.ui.auth.RegisterScreen(
                onBack = { route = registerOrigin },
                onRegistered = { userId, _ ->
                    ServiceLocator.appLock.onSignedIn()
                    promptPending = true
                    ServiceLocator.appScope.launch {
                        ServiceLocator.preferencesStorage.setStoredUserId(userId)
                    }
                    route = AuthRoute.Home
                },
                onRegisterOrganization = { orgOrigin = AuthRoute.Register; route = AuthRoute.RegisterOrganization },
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
        }
        val forgotScreen: @Composable () -> Unit = {
            com.example.chat.ui.auth.ForgotPasswordScreen(
                onBack = { route = AuthRoute.Login },
                // Dart prefers ApiBackendService.forgotPassword when registered
                // (it is, in locator) over the Supabase path.
                sendRecovery = { email -> ServiceLocator.apiBackend.forgotPassword(email) },
            )
        }
        val orgScreen: @Composable () -> Unit = {
            com.example.chat.ui.auth.RegisterOrganizationScreen(
                onBack = { route = orgOrigin },
                onRegistered = { userId, regOrgId, regOrgName ->
                    planOrgId = regOrgId; planOrgName = regOrgName; planIsNew = true
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
        }
        GlobalRouteLayers {
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
                onRegister = { registerOrigin = AuthRoute.Welcome; route = AuthRoute.Register },
            )
            AuthRoute.Login, AuthRoute.Register, AuthRoute.ForgotPassword, AuthRoute.RegisterOrganization -> {
                // Dart keeps each pushed-under screen (and its form state) alive: Login under Register/Forgot/
                // RegisterOrganization, Register under RegisterOrganization. State is in-memory only (no saved
                // instance state), so passwords are never persisted.
                val showLogin = route == AuthRoute.Login || route == AuthRoute.ForgotPassword ||
                    (route == AuthRoute.Register && registerOrigin == AuthRoute.Login) ||
                    (route == AuthRoute.RegisterOrganization &&
                        (orgOrigin == AuthRoute.Login || (orgOrigin == AuthRoute.Register && registerOrigin == AuthRoute.Login)))
                val showRegister = route == AuthRoute.Register ||
                    (route == AuthRoute.RegisterOrganization && orgOrigin == AuthRoute.Register)
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    if (showLogin) PreAuthLayer(route == AuthRoute.Login, loginScreen)
                    if (showRegister) PreAuthLayer(route == AuthRoute.Register, registerScreen)
                    if (route == AuthRoute.ForgotPassword) PreAuthLayer(true, forgotScreen)
                    if (route == AuthRoute.RegisterOrganization) PreAuthLayer(true, orgScreen)
                }
            }
            AuthRoute.SubscriptionPlan -> com.example.chat.ui.subscription.PlanSubscriptionScreen(
                organizationId = planOrgId,
                organizationName = planOrgName,
                isNewRegistration = planIsNew,
                onBack = { route = AuthRoute.Home },
                // Dart pushAndRemoveUntil(MainNavigationShell).
                onSubscribed = { route = AuthRoute.Home },
                loadPlans = { id -> ServiceLocator.apiBackend.getAvailablePlans(id) },
                subscribe = { id, planId, months ->
                    if (!ServiceLocator.apiBackend.subscribeToPlan(id, planId, months)) {
                        throw Exception("Subscription failed")
                    }
                },
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
                // chats destination content
                // (ChatsHomeScreen below)
                val shellColors = remember(resolvedTheme) {
                    com.example.chat.ui.core.theme.ChatyColors(resolvedTheme)
                }
                var settingsNotice by remember { mutableStateOf<String?>(null) }
                var showCallPicker by remember { mutableStateOf(false) }
                var storyCapture by remember {
                    mutableStateOf<kotlinx.coroutines.CompletableDeferred<com.example.chat.ui.updates.CapturedStoryMedia?>?>(null)
                }
                var showCenterEffects by remember { mutableStateOf(false) }
                var createTaskTick by remember { mutableIntStateOf(0) }
                var shellRev by remember { mutableIntStateOf(0) }
                val shellCtx = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.runtime.DisposableEffect(Unit) {
                    val l: () -> Unit = { shellRev++ }
                    ServiceLocator.preferencesController.addListener(l)
                    ServiceLocator.templateController.addListener(l)
                    ServiceLocator.appearanceVariantController.addListener(l)
                    onDispose {
                        ServiceLocator.preferencesController.removeListener(l)
                        ServiceLocator.templateController.removeListener(l)
                        ServiceLocator.appearanceVariantController.removeListener(l)
                    }
                }
                @Suppress("UNUSED_EXPRESSION") shellRev
                val shellToast: (String) -> Unit = {
                    android.widget.Toast.makeText(shellCtx, it, android.widget.Toast.LENGTH_SHORT).show()
                }
                // ChatyCameraCaptureScreen.open(story): a suspend seam completed by the camera dialog below.
                val captureStory: suspend () -> com.example.chat.ui.updates.CapturedStoryMedia? = {
                    val d = kotlinx.coroutines.CompletableDeferred<com.example.chat.ui.updates.CapturedStoryMedia?>()
                    storyCapture = d
                    d.await()
                }
                val publishStory: suspend (com.example.chat.ui.updates.CapturedStoryMedia) -> Unit = { m ->
                    ServiceLocator.statusService.publishMediaBytes(
                        bytes = m.bytes, displayName = "chaty_story.jpg", mediaType = "image",
                        text = m.caption, mimeType = "image/jpeg",
                    )
                }
                var settingsSub by remember { mutableStateOf<com.example.chat.ui.settings.SettingsDestination?>(null) }
                var pushedSettings by remember { mutableStateOf(false) }
                var pushedProfile by remember { mutableStateOf(false) }
                val rootPhoto = rememberRootProfilePhoto()
                var pushedEditProfile by remember { mutableStateOf(false) }
                var pushedMessageManagement by remember { mutableStateOf(false) }
                var pushedScheduled by remember { mutableStateOf(false) }
                var pushedAutoReply by remember { mutableStateOf(false) }
                var pushedAccount by remember { mutableStateOf(false) }
                var pushedUniversalAppearance by remember { mutableStateOf(false) }
                var pushedComponentStyles by remember { mutableStateOf(false) }
                var pushedThemeStudio by remember { mutableStateOf(false) }
                var pushedAppIcon by remember { mutableStateOf(false) }
                var pushedSettingsSearch by remember { mutableStateOf(false) }
                val settingsNavigate: (com.example.chat.ui.settings.SettingsDestination) -> Unit = { dest ->
                    if (dest == com.example.chat.ui.settings.SettingsDestination.Security) {
                        route = AuthRoute.Security
                    } else if (dest == com.example.chat.ui.settings.SettingsDestination.SearchSettings) {
                        // Dart: showSearch(SettingsSearchDelegate(allSettings: _searchIndex())).
                        pushedSettingsSearch = true
                    } else if (dest == com.example.chat.ui.settings.SettingsDestination.Automation) {
                        // Dart settings_root_screen: 'Message Automation & Scheduler' -> MessageManagementPage.
                        pushedMessageManagement = true
                    } else if (dest == com.example.chat.ui.settings.SettingsDestination.EditProfile) {
                        // Dart: ProfileEditScreen.open(context, dataStore).
                        pushedEditProfile = true
                    } else if (dest == com.example.chat.ui.settings.SettingsDestination.LinkedDevices ||
                        dest == com.example.chat.ui.settings.SettingsDestination.QrCode) {
                        linkedDevicesOnly = dest == com.example.chat.ui.settings.SettingsDestination.LinkedDevices
                        route = AuthRoute.LinkedDevices
                    } else if (dest in com.example.chat.ui.settings.hostedSettingsDestinations) {
                        settingsSub = dest
                    } else {
                        settingsNotice = dest.name
                    }
                }
                val shellHome = ServiceLocator.preferencesController.home
                val shellPrefsCtl = ServiceLocator.preferencesController
                val shellPrivacy = shellPrefsCtl.privacy
                com.example.chat.ui.navigation.MainNavigationShell(
                    colors = shellColors,
                    template = ServiceLocator.templateController.navigation,
                    navigationMode = themeController.navigationMode,
                    layoutMode = themeController.layoutMode,
                    prefs = com.example.chat.ui.navigation.NavigationShellPrefs(
                        separateChatsAndGroups = shellHome.separateChatsAndGroups,
                        hasCustomNavigationDestinations = ServiceLocator.templateController.hasCustomNavigationDestinations,
                        hideTasks = shellPrefsCtl.gbBool("nav_hide_tasks"),
                        hideUpdates = shellPrefsCtl.gbBool("nav_hide_updates"),
                        pagerTransition3d = shellHome.pagerTransition3d,
                        bottomBarStyle = ServiceLocator.appearanceVariantController.bottomBarStyle,
                        navigationStyleName = ServiceLocator.appearanceVariantController.navigationStyle,
                        homeStyle = shellHome.homeStyle,
                        animationsDisabled = android.provider.Settings.Global.getFloat(
                            shellCtx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
                        ) == 0f,
                    ),
                    myNameOverride = { shellHome.myNameOverride },
                    onNotify = shellToast,
                    onExitApp = { (shellCtx as? android.app.Activity)?.finish() },
                    onCenterAction = {
                        // Dart _handleNavigationCenterAction.
                        if (ServiceLocator.templateController.navigation.centerActionId != "camera") {
                            showCenterEffects = true
                        } else {
                            scope.launch {
                                val r = captureStory()
                                if (r != null) {
                                    try {
                                        publishStory(r)
                                        shellToast("Status posted.")
                                    } catch (e: Exception) {
                                        shellToast((e.message ?: e.toString()).replaceFirst("Exception: ", ""))
                                    }
                                }
                            }
                        }
                    },
                    fab = { activeId ->
                        com.example.chat.ui.navigation.ContextualNavigationFab(
                            activeDestinationId = activeId,
                            colors = shellColors,
                            prefs = com.example.chat.ui.navigation.NavigationFabPrefs(
                                hideFab = shellHome.hideFab,
                                showMetaAiIcon = shellHome.showMetaAiIcon,
                                fabNormalColor = shellHome.fabNormalColor,
                                fabPressedColor = shellHome.fabPressedColor,
                                fabIconsColor = shellHome.fabIconsColor,
                                hideNewMessageFab = shellHome.hideNewMessageFab,
                                hideLastSeenFab = shellHome.hideLastSeenFab,
                                hideCutEditFab = shellHome.hideCutEditFab,
                                hidePluginsList = shellHome.hidePluginsList,
                                hideGbwaSettingsFab = shellHome.hideGbwaSettingsFab,
                                freezeLastSeen = shellPrivacy.freezeLastSeen,
                            ),
                            actions = com.example.chat.ui.navigation.NavigationFabActions(
                                onNewChat = { g -> newChatGroup = g; newChatBroadcast = false; route = AuthRoute.NewChat },
                                onCreateTask = { createTaskTick++ },
                                onNewCall = { showCallPicker = true },
                                onScanQr = { linkedDevicesOnly = false; route = AuthRoute.LinkedDevices },
                                onOpenSettings = { pushedSettings = true },
                                onOpenThemeStudio = { pushedThemeStudio = true },
                                onOpenMessageScheduler = { pushedScheduled = true },
                                onOpenAutoReplyRules = { pushedAutoReply = true },
                                onToggleFreezeLastSeen = { frozen ->
                                    shellPrefsCtl.updatePrivacy(shellPrefsCtl.privacy.copy(freezeLastSeen = frozen))
                                },
                                onRestartApp = {
                                    val launch = shellCtx.packageManager.getLaunchIntentForPackage(shellCtx.packageName)
                                    if (launch != null) {
                                        launch.addFlags(
                                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
                                        )
                                        shellCtx.startActivity(launch)
                                        Runtime.getRuntime().exit(0)
                                    }
                                },
                                captureStory = captureStory,
                                publishStory = publishStory,
                                openDirectConversation = { target ->
                                    val api = ServiceLocator.apiBackend
                                    val number = target.trim()
                                    val existing = api.users.firstOrNull { it.phone.trim() == number || it.username.trim() == number }
                                    val digits = number.filter { it.isDigit() }
                                    val user = existing ?: com.example.chat.domain.models.UserProfile(
                                        id = if (digits.isNotEmpty()) "user_$digits" else number,
                                        displayName = number, username = number,
                                        avatarInitials = number.take(1).uppercase(), avatarColorHex = "#25D366",
                                        about = "Chaty User", lastSeenAt = java.time.Instant.now(), phone = number,
                                    )
                                    ServiceLocator.conversationsRepository.getOrCreateDirectConversationAsync(user).id
                                },
                                onOpenConversation = { id ->
                                    ServiceLocator.apiBackend.conversations.firstOrNull { it.id == id }?.let { c ->
                                        ServiceLocator.apiBackend.setActiveConversation(c.id)
                                        selectedConversation = c
                                        route = AuthRoute.ChatDetail
                                    }
                                },
                            ),
                            onNotify = shellToast,
                        )
                    },
                    currentUser = ServiceLocator.apiBackend.currentUser?.let { u ->
                        com.example.chat.domain.models.UserProfile(
                            id = u.id, displayName = u.displayName, username = u.username,
                            avatarInitials = u.avatarInitials, avatarColorHex = u.avatarColorHex,
                            about = u.about, lastSeenAt = java.time.Instant.now(),
                            email = u.email, phone = u.phone,
                        )
                    },
                    screenContent = { destinationId ->
                        when (destinationId) {
                            com.example.chat.core.templates.ChatyNavigationDestinationIds.settings -> {
                                val apiUser = ServiceLocator.apiBackend.currentUser
                                if (apiUser != null) {
                                    com.example.chat.ui.settings.SettingsRootScreen(
                                        preferences = ServiceLocator.preferencesController,
                                        themeController = themeController,
                                        user = com.example.chat.domain.models.UserProfile(
                                            id = apiUser.id, displayName = apiUser.displayName,
                                            username = apiUser.username, avatarInitials = apiUser.avatarInitials,
                                            avatarColorHex = apiUser.avatarColorHex, about = apiUser.about,
                                            lastSeenAt = java.time.Instant.now(),
                                            email = apiUser.email, phone = apiUser.phone,
                                        ),
                                        canGoBack = false,
                                        onBack = { },
                                        onNavigate = settingsNavigate,
                                        // Avatar/banner upload hosts are not wired yet.
                                        onPickAvatar = { source -> rootPhoto.avatar(source) },
                                        onPickBanner = { source -> rootPhoto.banner(source) },
                                        // Dart logout: ApiBackendService.signOut; the signed-out
                                        // listener above routes to Welcome.
                                        onLogout = { ServiceLocator.apiBackend.signOut() },
                                    )
                                }
                            }
                            com.example.chat.core.templates.ChatyNavigationDestinationIds.chats -> {
                com.example.chat.ui.ChatsHomeHost(
                    forcedType = null, pageTitle = null, gate = gate,
                    onOpenConversation = { conversation ->
                        ServiceLocator.apiBackend.setActiveConversation(conversation.id)
                        selectedConversation = conversation
                        route = AuthRoute.ChatDetail
                    },
                    onOpenSecurity = { route = AuthRoute.Security },
                    onOpenLockedChats = { route = AuthRoute.HiddenVault },
                    onOpenSettings = { pushedSettings = true },
                    onOpenNewChat = { g, b -> newChatGroup = g; newChatBroadcast = b; route = AuthRoute.NewChat },
                    onOpenGlobalSearch = { route = AuthRoute.GlobalSearch },
                    onOpenMessageManagement = { route = AuthRoute.MessageManagement },
                    onOpenStarredMessages = { route = AuthRoute.Starred },
                    // Dart chats_home banner: org uid ?: tenantId ?: config tenant; isNewRegistration=false.
                    onSelectPlan = {
                        val org = ServiceLocator.apiBackend.currentOrganization
                        planOrgId = org?.get("uid")?.toString() ?: org?.get("tenantId")?.toString() ?: ServiceLocator.apiBackend.config.tenantId
                        planOrgName = org?.get("name")?.toString() ?: ServiceLocator.apiBackend.config.tenantId
                        planIsNew = false
                        route = AuthRoute.SubscriptionPlan
                    },
                    onOpenProfile = { pushedProfile = true },
                    onOpenQr = { linkedDevicesOnly = false; route = AuthRoute.LinkedDevices },
                    onOpenLinkedDevices = { linkedDevicesOnly = true; route = AuthRoute.LinkedDevices },
                )
                            }
                            com.example.chat.core.templates.ChatyNavigationDestinationIds.groups ->
                                com.example.chat.ui.ChatsHomeHost(
                                    forcedType = com.example.chat.domain.models.ConversationType.group,
                                    pageTitle = "Groups", gate = gate,
                                    onOpenConversation = { conversation ->
                                        ServiceLocator.apiBackend.setActiveConversation(conversation.id)
                                        selectedConversation = conversation
                                        route = AuthRoute.ChatDetail
                                    },
                                    onOpenSecurity = { route = AuthRoute.Security },
                                    onOpenLockedChats = { route = AuthRoute.HiddenVault },
                                    onOpenSettings = { pushedSettings = true },
                    onOpenNewChat = { g, b -> newChatGroup = g; newChatBroadcast = b; route = AuthRoute.NewChat },
                    onOpenGlobalSearch = { route = AuthRoute.GlobalSearch },
                    onOpenMessageManagement = { route = AuthRoute.MessageManagement },
                    onOpenStarredMessages = { route = AuthRoute.Starred },
                    // Dart chats_home banner: org uid ?: tenantId ?: config tenant; isNewRegistration=false.
                    onSelectPlan = {
                        val org = ServiceLocator.apiBackend.currentOrganization
                        planOrgId = org?.get("uid")?.toString() ?: org?.get("tenantId")?.toString() ?: ServiceLocator.apiBackend.config.tenantId
                        planOrgName = org?.get("name")?.toString() ?: ServiceLocator.apiBackend.config.tenantId
                        planIsNew = false
                        route = AuthRoute.SubscriptionPlan
                    },
                    onOpenProfile = { pushedProfile = true },
                    onOpenQr = { linkedDevicesOnly = false; route = AuthRoute.LinkedDevices },
                    onOpenLinkedDevices = { linkedDevicesOnly = true; route = AuthRoute.LinkedDevices },
                                )
                            com.example.chat.core.templates.ChatyNavigationDestinationIds.calls -> {
                                val history = ServiceLocator.callHistory
                                val callScope = androidx.compose.runtime.rememberCoroutineScope()
                                androidx.compose.runtime.LaunchedEffect(Unit) { history.start() }
                                if (showCallPicker) {
                                    androidx.compose.ui.window.Dialog(
                                        onDismissRequest = { showCallPicker = false },
                                        properties = androidx.compose.ui.window.DialogProperties(
                                            usePlatformDefaultWidth = false,
                                            decorFitsSystemWindows = false,
                                        ),
                                    ) {
                                        val ctx = androidx.compose.ui.platform.LocalContext.current
                                        com.example.chat.ui.calls.CallContactPickerScreen(
                                            contacts = ServiceLocator.apiBackend.users,
                                            currentUserId = ServiceLocator.apiBackend.currentUser?.id ?: "",
                                            initialMode = com.example.chat.ui.calls.CallContactPickerMode.dialpad,
                                            onBack = { showCallPicker = false },
                                            onStartCall = { contact, isVideo ->
                                                val name = contact.displayName.trim()
                                                ServiceLocator.callPresentationController.showFullScreen()
                                                callScope.launch {
                                                    try {
                                                        ServiceLocator.callSignaling.initiateCall(
                                                            contact.id,
                                                            if (name.isEmpty()) "Chaty contact" else name,
                                                            if (name.isEmpty()) "?" else name.substring(0, 1).uppercase(),
                                                            contact.avatarColorHex,
                                                            isVideo,
                                                        )
                                                        showCallPicker = false
                                                    } catch (e: Exception) {
                                                        android.widget.Toast.makeText(ctx, (e.message ?: e.toString()).replace("Bad state: ", ""), android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            showToast = { m, _ -> android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_SHORT).show() },
                                        )
                                    }
                                }
                                com.example.chat.ui.calls.CallsScreen(
                                    records = history.records,
                                    historyUnavailable = history.lastError != null,
                                    currentUserId = ServiceLocator.apiBackend.currentUser?.id ?: "",
                                    contactFor = { id -> ServiceLocator.apiBackend.users.firstOrNull { it.id == id } },
                                    onRefresh = { callScope.launch { history.refresh() } },
                                    // Dart calls_screen FAB: push CallContactPickerScreen(dialpad).
                                    onOpenAddCall = { showCallPicker = true },
                                    onRedial = { remoteUserId, displayName, initials, colorHex, isVideo ->
                                        ServiceLocator.callPresentationController.showFullScreen()
                                        callScope.launch {
                                            ServiceLocator.callSignaling.initiateCall(
                                                remoteUserId, displayName, initials, colorHex, isVideo,
                                            )
                                        }
                                    },
                                )
                            }
                            com.example.chat.core.templates.ChatyNavigationDestinationIds.tasks -> {
                                val controller = ServiceLocator.tasksController
                                var tasksRevision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                                var detailTask by remember { mutableStateOf<com.example.chat.domain.models.ChatTask?>(null) }
                                var editorOpen by remember { mutableStateOf(false) }
                                var editorTask by remember { mutableStateOf<com.example.chat.domain.models.ChatTask?>(null) }
                                // Contextual FAB 'Create task' (hoisted tick from the shell FAB).
                                androidx.compose.runtime.LaunchedEffect(createTaskTick) {
                                    if (createTaskTick > 0) { editorTask = null; editorOpen = true }
                                }
                                androidx.compose.runtime.DisposableEffect(controller) {
                                    val l: () -> Unit = { tasksRevision++ }
                                    controller.addListener(l)
                                    onDispose { controller.removeListener(l) }
                                }
                                androidx.compose.runtime.LaunchedEffect(Unit) { controller.loadTasks() }
                                val taskUser = ServiceLocator.apiBackend.currentUser?.let { toDomainUser(it) }
                                val lookup: (String) -> com.example.chat.domain.models.UserProfile? =
                                    { id -> ServiceLocator.apiBackend.users.firstOrNull { it.id == id } }
                                val detail = detailTask
                                if (taskUser != null && detail != null) {
                                    com.example.chat.ui.tasks.TaskDetailScreen(
                                        task = detail,
                                        controller = controller,
                                        colors = com.example.chat.ui.tasks.TaskColors(resolvedTheme),
                                        currentUser = taskUser,
                                        getUser = lookup,
                                        onBack = { detailTask = null },
                                        onEditTask = { editorTask = it; editorOpen = true },
                                    )
                                } else {
                                    com.example.chat.ui.tasks.TasksScreen(
                                        controller = controller,
                                        tasksRevision = tasksRevision,
                                        conversations = ServiceLocator.apiBackend.conversations,
                                        getUser = lookup,
                                        onOpenDetail = { detailTask = it },
                                        onEditTask = { editorTask = it; editorOpen = true },
                                        onCreateTask = { editorTask = null; editorOpen = true },
                                    )
                                }
                                if (editorOpen && taskUser != null) {
                                    com.example.chat.ui.tasks.TaskCreateEditModal(
                                        controller = controller,
                                        colors = com.example.chat.ui.tasks.TaskColors(resolvedTheme),
                                        conversations = ServiceLocator.apiBackend.conversations,
                                        currentUser = taskUser,
                                        getUser = lookup,
                                        sourceConversationId = "",
                                        existingTask = editorTask,
                                        onDismiss = { editorOpen = false },
                                        onSaved = { msg -> editorOpen = false; settingsNotice = msg },
                                        onError = { msg -> settingsNotice = msg },
                                    )
                                }
                            }
                            com.example.chat.core.templates.ChatyNavigationDestinationIds.updates -> {
                                val updUser = ServiceLocator.apiBackend.currentUser?.let { toDomainUser(it) }
                                if (updUser != null) {
                                    val statusService = ServiceLocator.statusService
                                    val statusFlow = remember(statusService) {
                                        statusService.watchActiveStatuses(
                                            keepDeleted = { ServiceLocator.preferencesController.privacy.antiDeleteStatus },
                                        )
                                    }
                                    val toastContext = androidx.compose.ui.platform.LocalContext.current
                                    com.example.chat.ui.updates.UpdatesScreen(
                                        statusService = statusService,
                                        colors = shellColors,
                                        currentUser = updUser,
                                        getUser = { id -> ServiceLocator.apiBackend.users.firstOrNull { it.id == id } },
                                        statuses = statusFlow,
                                        captureStory = captureStory,
                                        pickStatusMedia = com.example.chat.ui.updates.rememberStatusMediaPicker(),
                                        onNotify = { msg ->
                                            android.widget.Toast.makeText(toastContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                }
                            }
                            else -> androidx.compose.material3.Text(
                                "Destination \"$destinationId\" is not hosted in this build yet.",
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    },
                )
                if (showCenterEffects) com.example.chat.ui.camera.EffectPickerHost(onDismiss = { showCenterEffects = false })
                storyCapture?.let { pending ->
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { pending.complete(null); storyCapture = null },
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
                        ),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            com.example.chat.ui.camera.ChatyCameraCaptureScreen(
                                mode = com.example.chat.ui.camera.ChatyCaptureMode.story,
                                onResult = { r ->
                                    scope.launch {
                                        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            java.io.File(r.path).readBytes()
                                        }
                                        pending.complete(com.example.chat.ui.updates.CapturedStoryMedia(bytes, r.caption))
                                        storyCapture = null
                                    }
                                },
                                onDismiss = { pending.complete(null); storyCapture = null },
                            )
                        }
                    }
                }
                if (pushedProfile) {
                    androidx.activity.compose.BackHandler(enabled = settingsSub == null && !pushedSettings) { pushedProfile = false }
                    val profUser = ServiceLocator.apiBackend.currentUser?.let { toDomainUser(it) }
                    if (profUser != null) {
                        val convs = ServiceLocator.apiBackend.conversations
                        val profTemplate = ServiceLocator.templateController.profile
                        Box(Modifier.fillMaxSize()) {
                            com.example.chat.ui.profile.ProfileScreen(
                                user = profUser,
                                // Dart: dataStore.conversations.length / group count / contacts (users except me).
                                chatsCount = convs.size,
                                groupsCount = convs.count { it.type == com.example.chat.domain.models.ConversationType.group },
                                contactsCount = ServiceLocator.apiBackend.users.count { it.id != profUser.id },
                                displayNameOverride = ServiceLocator.preferencesController.home.myNameOverride,
                                headerStyle = when (profTemplate.headerStyle) {
                                    com.example.chat.core.templates.ProfileHeaderStyle.compactHeader -> com.example.chat.ui.profile.ProfileHeaderStyle.compactHeader
                                    com.example.chat.core.templates.ProfileHeaderStyle.centeredIdentity -> com.example.chat.ui.profile.ProfileHeaderStyle.centeredIdentity
                                    com.example.chat.core.templates.ProfileHeaderStyle.bannerWithAvatar -> com.example.chat.ui.profile.ProfileHeaderStyle.bannerWithAvatar
                                },
                                showStatsGrid = profTemplate.showStatsGrid,
                                avatarShape = profTemplate.avatarShape,
                                onEditProfile = { pushedEditProfile = true },
                                onOpenSettings = { pushedSettings = true },
                                onOpenPrivacyCenter = { settingsNavigate(com.example.chat.ui.settings.SettingsDestination.Privacy) },
                                onOpenNotifications = { settingsNavigate(com.example.chat.ui.settings.SettingsDestination.Notifications) },
                                onLogout = { ServiceLocator.apiBackend.signOut() },
                            )
                        }
                    }
                }
                if (pushedSettings) {
                    androidx.activity.compose.BackHandler(enabled = settingsSub == null) { pushedSettings = false }
                    val pushedUser = ServiceLocator.apiBackend.currentUser?.let { toDomainUser(it) }
                    if (pushedUser != null) {
                        Box(Modifier.fillMaxSize()) {
                            com.example.chat.ui.settings.SettingsRootScreen(
                                preferences = ServiceLocator.preferencesController,
                                themeController = themeController,
                                user = pushedUser,
                                canGoBack = true,
                                onBack = { pushedSettings = false },
                                onNavigate = settingsNavigate,
                                onPickAvatar = { source -> rootPhoto.avatar(source) },
                                onPickBanner = { source -> rootPhoto.banner(source) },
                                onLogout = { ServiceLocator.apiBackend.signOut() },
                            )
                        }
                    }
                }
                if (pushedSettingsSearch) {
                    androidx.activity.compose.BackHandler { pushedSettingsSearch = false }
                    Box(Modifier.fillMaxSize()) {
                        com.example.chat.ui.settings.navigation.SettingsSearchScreen(
                            allSettings = com.example.chat.ui.settings.registry.SettingsRegistry.allSettings,
                            title = { it.title },
                            category = { it.category.title },
                            description = { it.description },
                            keywords = { it.searchKeywords },
                            // Dart: def.icon ?? def.category.icon (def.iconName resolved by SettingsIcons).
                            icon = { def ->
                                com.example.chat.ui.settings.navigation.SettingsIcons.resolve(def.iconName) ?: when (def.category) {
                                    com.example.chat.ui.settings.registry.SettingsCategory.account -> androidx.compose.material.icons.Icons.Outlined.Person
                                    com.example.chat.ui.settings.registry.SettingsCategory.privacy -> androidx.compose.material.icons.Icons.Outlined.VisibilityOff
                                    com.example.chat.ui.settings.registry.SettingsCategory.security -> androidx.compose.material.icons.Icons.Outlined.Lock
                                    com.example.chat.ui.settings.registry.SettingsCategory.chats -> androidx.compose.material.icons.Icons.Outlined.ChatBubbleOutline
                                    com.example.chat.ui.settings.registry.SettingsCategory.appearance -> androidx.compose.material.icons.Icons.Outlined.Palette
                                    com.example.chat.ui.settings.registry.SettingsCategory.homeAndNavigation -> androidx.compose.material.icons.Icons.Outlined.Home
                                    com.example.chat.ui.settings.registry.SettingsCategory.notifications -> androidx.compose.material.icons.Icons.Outlined.Notifications
                                    com.example.chat.ui.settings.registry.SettingsCategory.calls -> androidx.compose.material.icons.Icons.Outlined.Call
                                    com.example.chat.ui.settings.registry.SettingsCategory.storageAndMedia -> androidx.compose.material.icons.Icons.Rounded.Storage
                                    com.example.chat.ui.settings.registry.SettingsCategory.devices -> androidx.compose.material.icons.Icons.Rounded.Devices
                                    com.example.chat.ui.settings.registry.SettingsCategory.effects -> androidx.compose.material.icons.Icons.Rounded.Animation
                                    com.example.chat.ui.settings.registry.SettingsCategory.permissions -> androidx.compose.material.icons.Icons.Outlined.AdminPanelSettings
                                    com.example.chat.ui.settings.registry.SettingsCategory.about -> androidx.compose.material.icons.Icons.Outlined.Info
                                }
                            },
                            onSelect = { def ->
                                // Dart _destinationForRoute; closing the search precedes navigation.
                                pushedSettingsSearch = false
                                when (def.canonicalRoute) {
                                    "/settings/privacy" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.Privacy)
                                    "/settings/security" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.Security)
                                    "/settings/conversation" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.Conversation)
                                    "/settings/message_management" -> pushedMessageManagement = true
                                    "/settings/themes" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.ThemeEngine)
                                    "/settings/templates" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.TemplateStudio)
                                    "/settings/notifications" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.Notifications)
                                    "/settings/calls" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.Calls)
                                    "/settings/storage" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.StorageMedia)
                                    "/settings/effects" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.Effects)
                                    "/settings/permissions" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.Permissions)
                                    "/settings/home" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.HomeScreen)
                                    "/settings/devices" -> settingsNavigate(com.example.chat.ui.settings.SettingsDestination.LinkedDevices)
                                    "/settings/account" -> pushedAccount = true
                                    "/settings/universal_appearance" -> pushedUniversalAppearance = true
                                    "/settings/customization" -> pushedComponentStyles = true
                                    "/settings/app_icon" -> pushedAppIcon = true
                
                                    else -> settingsNotice = def.canonicalRoute
                                }
                            },
                            onBack = { pushedSettingsSearch = false },
                        )
                    }
                }
                if (pushedScheduled) {
                    androidx.activity.compose.BackHandler { pushedScheduled = false }
                    Box(Modifier.fillMaxSize()) {
                        com.example.chat.ui.automation.ScheduledMessagesScreen(
                            service = ServiceLocator.scheduledMessageService,
                            conversationsProvider = { ServiceLocator.apiBackend.conversations },
                            onBack = { pushedScheduled = false },
                        )
                    }
                }
                if (pushedAutoReply) {
                    androidx.activity.compose.BackHandler { pushedAutoReply = false }
                    Box(Modifier.fillMaxSize()) {
                        com.example.chat.ui.automation.AutoReplyRulesScreen(
                            engine = ServiceLocator.autoReplyRuleEngine,
                            onBack = { pushedAutoReply = false },
                        )
                    }
                }
                if (pushedMessageManagement) {
                    // Originating settings route stays under this overlay (Dart Navigator.push).
                    androidx.activity.compose.BackHandler { pushedMessageManagement = false }
                    Box(Modifier.fillMaxSize()) { MessageManagementHost(onBack = { pushedMessageManagement = false }) }
                }
                if (pushedAppIcon) {
                    androidx.activity.compose.BackHandler { pushedAppIcon = false }
                    val iconController = ServiceLocator.appIconController
                    androidx.compose.runtime.LaunchedEffect(iconController) { iconController.initialize() }
                    Box(Modifier.fillMaxSize()) {
                        com.example.chat.ui.settings.appicon.AppIconSettingsScreen(
                            controller = iconController,
                            onBack = { pushedAppIcon = false },
                            showToast = { msg -> settingsNotice = msg },
                        )
                    }
                }
                if (pushedThemeStudio) {
                    // Dart main_navigation_shell Quick Actions "Theme Studio" -> ThemeStudioScreen(prefs, ThemeController).
                    androidx.activity.compose.BackHandler { pushedThemeStudio = false }
                    val tsContext = androidx.compose.ui.platform.LocalContext.current
                    var tsJsonWaiter by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<String?>?>(null) }
                    val tsJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        val text = try {
                            uri?.let { tsContext.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }
                        } catch (_: Exception) { null }
                        tsJsonWaiter?.complete(text); tsJsonWaiter = null
                    }
                    Box(Modifier.fillMaxSize()) {
                        com.example.chat.ui.settings.studio.ThemeStudioScreen(
                            themeController = ServiceLocator.themeController,
                            preferencesController = ServiceLocator.preferencesController,
                            onBack = { pushedThemeStudio = false },
                            onOpenComponentStyles = { pushedThemeStudio = false; pushedComponentStyles = true },
                            pickThemeJson = {
                                val d = kotlinx.coroutines.CompletableDeferred<String?>()
                                tsJsonWaiter = d
                                tsJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                d.await()
                            },
                            saveThemeJson = { json ->
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val cfg = com.example.chat.core.theme.ChatyThemeManager.validateAndImportTheme(json)
                                    com.example.chat.core.theme.ChatyThemeManager.saveThemeToFile(
                                        tsContext.getExternalFilesDir(null) ?: tsContext.filesDir, cfg,
                                    ).name
                                }
                            },
                            templateController = ServiceLocator.templateController,
                            appearanceVariantController = ServiceLocator.appearanceVariantController,
                        )
                    }
                }
                if (pushedComponentStyles) {
                    androidx.activity.compose.BackHandler { pushedComponentStyles = false }
                    val customization = com.example.chat.ui.core.customization.rememberLoadedCustomizationController()
                    val overlayController = com.example.chat.ui.core.components.overlays.rememberChatyOverlayController()
                    Box(Modifier.fillMaxSize()) {
                        com.example.chat.ui.settings.appearance.ComponentStylesScreen(
                            controller = customization,
                            overlayController = overlayController,
                            onBack = { pushedComponentStyles = false },
                        )
                    }
                }
                if (pushedUniversalAppearance) {
                    androidx.activity.compose.BackHandler { pushedUniversalAppearance = false }
                    Box(Modifier.fillMaxSize()) {
                        com.example.chat.ui.settings.appearance.UniversalAppearanceScreen(
                            controller = ServiceLocator.appearanceVariantController,
                            onBack = { pushedUniversalAppearance = false },
                            onMessage = { msg -> settingsNotice = msg },
                        )
                    }
                }
                if (pushedAccount) {
                    val acct = ServiceLocator.apiBackend.currentUser
                    androidx.activity.compose.BackHandler { pushedAccount = false }
                    if (acct != null) Box(Modifier.fillMaxSize()) {
                        com.example.chat.ui.settings.account.AccountSettingsScreen(
                            user = com.example.chat.ui.settings.account.AccountUserInfo(
                                id = acct.id, displayName = acct.displayName, username = acct.username,
                                about = acct.about, avatarInitials = acct.avatarInitials, avatarUrl = acct.avatarUrl,
                            ),
                            // Dart: org?['uid'] ?: org?['tenantId'] ?: ApiBackendConfig.tenantId; name ?: tenantId.
                            organization = {
                                val org = ServiceLocator.apiBackend.currentOrganization
                                (org?.get("uid")?.toString() ?: org?.get("tenantId")?.toString() ?: ServiceLocator.apiBackend.config.tenantId) to
                                    (org?.get("name")?.toString() ?: ServiceLocator.apiBackend.config.tenantId)
                            },
                            onBack = { pushedAccount = false },
                            onOpenSubscription = { orgId, orgName ->
                                planOrgId = orgId; planOrgName = orgName; planIsNew = false
                                pushedAccount = false
                                route = AuthRoute.SubscriptionPlan
                            },
                            checkOrganization = { ws -> ServiceLocator.apiBackend.checkOrganization(ws) },
                            updateRegistrationSupport = { id, allow -> ServiceLocator.apiBackend.updateOrganizationRegistrationSupport(id, allow) },
                            updateProfile = { name, about, username, avatarUrl ->
                                ServiceLocator.apiBackend.updateProfile(displayName = name, about = about, username = username, avatarUrl = avatarUrl)
                            },
                            uploadProfilePhoto = { filename, bytes -> ServiceLocator.apiBackend.uploadProfilePhoto(filename, bytes) },
                            // Dart: api.deleteUser(id), then purgeLocalDataForDeletedAccount(id), then logout.
                            deleteAccountData = { id ->
                                ServiceLocator.apiBackend.deleteUser(id)
                                ServiceLocator.purgeLocalDataForDeletedAccount(id)
                            },
                            logout = { ServiceLocator.apiBackend.signOut() },
                        )
                    }
                }
                if (pushedEditProfile) {
                    val editUser = ServiceLocator.apiBackend.currentUser?.let { toDomainUser(it) }
                    if (editUser != null) {
                        val editContext = androidx.compose.ui.platform.LocalContext.current
                        var photoWaiter by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<ByteArray?>?>(null) }
                        val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                        ) { uri ->
                            val bytes = try {
                                uri?.let { editContext.contentResolver.openInputStream(it)?.use { i -> i.readBytes() } }
                            } catch (_: Exception) { null }
                            photoWaiter?.complete(bytes); photoWaiter = null
                        }
                        val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview(),
                        ) { bmp ->
                            val bytes = bmp?.let {
                                val o = java.io.ByteArrayOutputStream()
                                it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, o)
                                o.toByteArray()
                            }
                            photoWaiter?.complete(bytes); photoWaiter = null
                        }
                        suspend fun pickBytes(source: com.example.chat.data.services.ProfileMediaSource): ByteArray? {
                            val d = kotlinx.coroutines.CompletableDeferred<ByteArray?>()
                            photoWaiter = d
                            if (source == com.example.chat.data.services.ProfileMediaSource.camera) cameraLauncher.launch(null)
                            else galleryLauncher.launch("image/*")
                            return d.await()
                        }
                        fun profileMedia() = com.example.chat.data.services.ProfileMediaService(
                            storage = ServiceLocator.storageClient,
                            currentUserId = { ServiceLocator.sessionManager.currentUserId() },
                        )
                        Box(Modifier.fillMaxSize()) {
                            com.example.chat.ui.profile.ProfileEditScreen(
                                user = editUser,
                                onBack = { pushedEditProfile = false },
                                // Dart ProfileMediaService.uploadAvatar/uploadBanner (Supabase storage 'profile-media').
                                onPickAvatar = { source ->
                                    val bytes = pickBytes(source)
                                    if (bytes == null) null else profileMedia().uploadAvatar(bytes)
                                },
                                onPickBanner = { source ->
                                    val bytes = pickBytes(source)
                                    if (bytes == null) null else profileMedia().uploadBanner(bytes)
                                },
                                onSave = { name, username, about, avatarUrl, _ ->
                                    // Dart updateUser -> api.updateProfile(name, about, username, updated.avatarUrl);
                                    // bannerUrl is not sent by the api-branch backend either.
                                    ServiceLocator.apiBackend.updateProfile(
                                        displayName = name, about = about, username = username,
                                        avatarUrl = avatarUrl ?: editUser.avatarUrl,
                                    )
                                },
                            )
                        }
                    }
                }
                val sub = settingsSub
                if (sub != null) {
                    androidx.activity.compose.BackHandler { settingsSub = null }
                    Box(Modifier.fillMaxSize()) {
                        com.example.chat.ui.settings.SettingsSubHost(sub, onBack = { settingsSub = null }, onSwitch = { settingsSub = it })
                    }
                }
                val notice = settingsNotice
                if (notice != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { settingsNotice = null },
                        title = { androidx.compose.material3.Text(notice) },
                        text = { androidx.compose.material3.Text("This settings page is not hosted in this build yet.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { settingsNotice = null }) {
                                androidx.compose.material3.Text("OK")
                            }
                        },
                    )
                }
            }
            AuthRoute.Security -> com.example.chat.ui.settings.security.SecurityCenterScreen(
                preferences = ServiceLocator.preferencesController,
                lockService = ServiceLocator.localLockService,
                onBack = { route = AuthRoute.Home },
                onOpenLockedChats = {
                    scope.launch {
                        val ok = com.example.chat.data.services.ProtectedResourceGate.authorizeGeneralAction(
                            lockMethod = ServiceLocator.preferencesController.security.lockMethod,
                            lockService = ServiceLocator.localLockService,
                            lockPrompt = gate.lockPrompt,
                            title = "Locked Chats", reason = "Authenticate to open Locked Chats",
                        )
                        if (ok) route = AuthRoute.LockedChats
                    }
                },
            )
            AuthRoute.NewChat -> {
                val repoN = ServiceLocator.conversationsRepository
                com.example.chat.ui.chats.NewChatScreen(
                    contacts = ServiceLocator.apiBackend.users,
                    startInGroupMode = newChatGroup,
                    startInBroadcastMode = newChatBroadcast,
                    searchUsersRemote = { q -> repoN.searchUsersRemote(q) },
                    openDirectConversation = { u -> repoN.getOrCreateDirectConversationAsync(u) },
                    createGroup = { title, ids -> repoN.createGroup(title, ids) },
                    deleteUser = { id -> ServiceLocator.apiBackend.deleteUser(id) },
                    onOpenConversation = { conversation ->
                        ServiceLocator.apiBackend.setActiveConversation(conversation.id)
                        selectedConversation = conversation
                        route = AuthRoute.ChatDetail
                    },
                    onBack = { route = AuthRoute.Home },
                )
            }
            AuthRoute.GlobalSearch -> {
                val repoS = ServiceLocator.conversationsRepository
                val prefsS = ServiceLocator.preferencesController
                com.example.chat.ui.search.GlobalSearchScreen(
                    colors = com.example.chat.ui.core.theme.ChatyColors(resolvedTheme),
                    conversations = ServiceLocator.apiBackend.conversations,
                    searchUsers = { q -> repoS.searchUsersRemote(q) },
                    openDirectConversation = { u -> repoS.getOrCreateDirectConversationAsync(u) },
                    verifySecretPhrase = { phrase -> ServiceLocator.localLockService.verifySecretPhrase(phrase) },
                    isConversationHidden = { id -> prefsS.isConversationHidden(id) },
                    onBack = { route = AuthRoute.Home },
                    onOpenLockedChats = { route = AuthRoute.HiddenVault },
                    onOpenConversation = { id ->
                        ServiceLocator.apiBackend.conversations.firstOrNull { it.id == id }?.let { c ->
                            ServiceLocator.apiBackend.setActiveConversation(c.id)
                            selectedConversation = c
                            route = AuthRoute.ChatDetail
                        }
                    },
                    onOpenConversationReplace = { id ->
                        ServiceLocator.apiBackend.conversations.firstOrNull { it.id == id }?.let { c ->
                            ServiceLocator.apiBackend.setActiveConversation(c.id)
                            selectedConversation = c
                            route = AuthRoute.ChatDetail
                        }
                    },
                )
            }
            AuthRoute.LockedChats -> {
                val prefsL = ServiceLocator.preferencesController
                val lockSvc = ServiceLocator.localLockService
                var lockedRev by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                androidx.compose.runtime.DisposableEffect(prefsL) {
                    val l: () -> Unit = { lockedRev++ }
                    prefsL.addListener(l)
                    onDispose { prefsL.removeListener(l) }
                }
                @Suppress("UNUSED_EXPRESSION") lockedRev
                val lockedIds = prefsL.security.lockedConversationIds
                com.example.chat.ui.chats.locked_chats.LockedChatsScreen(
                    lockedConversations = ServiceLocator.apiBackend.conversations.filter { it.id in lockedIds },
                    lockMethod = prefsL.security.lockMethod,
                    isConversationHidden = { id -> prefsL.isConversationHidden(id) },
                    hasSecretPhrase = { lockSvc.hasSecretPhrase() },
                    setSecretPhrase = { lockSvc.setSecretPhrase(it) },
                    clearSecretPhrase = { lockSvc.clearSecretPhrase() },
                    onSetEntryBySecretPhrase = { on ->
                        prefsL.updateSecurity(prefsL.security.copy(entryBySecretPhrase = on))
                    },
                    onToggleHide = { id, hide -> prefsL.toggleHideConversation(id, hide) },
                    onUnlockCompletely = { id -> prefsL.toggleLockConversation(id, false) },
                    onOpenChat = { conversation ->
                        scope.launch {
                            val sec = prefsL.security
                            val ok = com.example.chat.data.services.ProtectedResourceGate.authorizeConversation(
                                conversationId = conversation.id,
                                isProtected = prefsL.isConversationLocked(conversation.id),
                                autoLockTimeout = sec.autoLockTimeout,
                                lockMethod = sec.lockMethod,
                                lockService = lockSvc,
                                setupPrompt = gate.setupPrompt,
                                lockPrompt = gate.lockPrompt,
                            )
                            if (ok) {
                                ServiceLocator.apiBackend.setActiveConversation(conversation.id)
                                selectedConversation = conversation
                                route = AuthRoute.ChatDetail
                            }
                        }
                    },
                    onOpenSecuritySettings = { route = AuthRoute.Security },
                    onBack = { route = AuthRoute.Home },
                )
            }
            AuthRoute.HiddenVault -> {
                // Dart _openLockedChatsVault: push HiddenVaultScreen(dataStore).
                androidx.compose.runtime.LaunchedEffect(Unit) { ServiceLocator.hiddenVaultService.loadVaultRegistry() }
                var vaultConvRev by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                androidx.compose.runtime.DisposableEffect(Unit) {
                    val l: () -> Unit = { vaultConvRev++ }
                    ServiceLocator.apiBackend.addListener(l)
                    onDispose { ServiceLocator.apiBackend.removeListener(l) }
                }
                @Suppress("UNUSED_EXPRESSION") vaultConvRev
                com.example.chat.ui.vault.HiddenVaultScreen(
                    controller = ServiceLocator.hiddenVaultController,
                    conversationsProvider = { ServiceLocator.apiBackend.conversations },
                    onOpenConversation = { id ->
                        ServiceLocator.apiBackend.conversations.firstOrNull { it.id == id }?.let { c ->
                            ServiceLocator.apiBackend.setActiveConversation(c.id)
                            selectedConversation = c
                            route = AuthRoute.ChatDetail
                        }
                    },
                    onBack = { route = AuthRoute.Home },
                )
            }
            AuthRoute.MessageManagement -> MessageManagementHost(onBack = { route = AuthRoute.Home })
            AuthRoute.LinkedDevices -> {
                val repoL = ServiceLocator.conversationsRepository
                val rel = ServiceLocator.contactRelationships
                val meL = ServiceLocator.apiBackend.currentUser
                var devices by remember { mutableStateOf<List<com.example.chat.data.services.LinkedDevice>>(emptyList()) }
                var loadingDevices by remember { mutableStateOf(false) }
                var devicesError by remember { mutableStateOf<String?>(null) }
                suspend fun reloadDevices() {
                    loadingDevices = true
                    try {
                        devices = rel.linkedDevices(); devicesError = null
                    } catch (e: Exception) {
                        devicesError = e.message ?: e.toString()
                    }
                    loadingDevices = false
                }
                androidx.compose.runtime.LaunchedEffect(Unit) { reloadDevices() }
                com.example.chat.ui.chats.linked_devices.LinkedDevicesQrScreen(
                    currentUserDisplayName = meL?.displayName ?: "",
                    currentUserUsername = meL?.username ?: "",
                    devices = devices,
                    loadingDevices = loadingDevices,
                    devicesError = devicesError,
                    initialIndex = if (linkedDevicesOnly) 0 else 1,
                    qrOnly = !linkedDevicesOnly,
                    devicesOnly = linkedDevicesOnly,
                    onRefreshDevices = { reloadDevices() },
                    onRevokeDevice = { d -> rel.revokeDevice(d.id); reloadDevices() },
                    searchUsersRemote = { q -> repoL.searchUsersRemote(q) },
                    openDirectConversation = { u -> repoL.getOrCreateDirectConversationAsync(u) },
                    onOpenConversation = { conversation ->
                        ServiceLocator.apiBackend.setActiveConversation(conversation.id)
                        selectedConversation = conversation
                        route = AuthRoute.ChatDetail
                    },
                    onBack = { route = AuthRoute.Home },
                )
            }
            AuthRoute.Starred -> {
                var starRevision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                val starScope = androidx.compose.runtime.rememberCoroutineScope()
                androidx.compose.runtime.DisposableEffect(Unit) {
                    val listener: () -> Unit = { starRevision++ }
                    ServiceLocator.apiBackend.addListener(listener)
                    onDispose { ServiceLocator.apiBackend.removeListener(listener) }
                }
                @Suppress("UNUSED_EXPRESSION") starRevision
                androidx.activity.compose.BackHandler { route = AuthRoute.Home }
                com.example.chat.ui.chats.starred.StarredMessagesScreen(
                    conversations = ServiceLocator.apiBackend.conversations,
                    messagesFor = { id -> ServiceLocator.apiBackend.getMessages(id) },
                    // Dart _loadConversations: ensureConversationLoaded for every conversation (Future.wait).
                    onRefresh = {
                        var ok = true
                        for (c in ServiceLocator.apiBackend.conversations) {
                            try { ServiceLocator.apiBackend.loadMessages(c.id) } catch (_: Exception) { ok = false }
                        }
                        ok
                    },
                    // Dart toggleStarMessage -> _backend.setMessageState('starred', !isStarred): the
                    // ChatyBackendService (Supabase RPC set_message_user_state) rail. A custom-backend
                    // session has no Supabase auth, so this may fail at runtime; the api branch has the same rail.
                    onToggleStar = { cid, mid ->
                        val m = ServiceLocator.apiBackend.getMessages(cid).firstOrNull { it.id == mid }
                        if (m != null) starScope.launch {
                            try {
                                ServiceLocator.conversationsRepository.setMessageStateAsync(cid, mid, "starred", !m.isStarred)
                            } catch (_: Exception) { }
                        }
                    },
                    onOpenConversation = { cid, mid ->
                        ServiceLocator.apiBackend.conversations.firstOrNull { it.id == cid }?.let { c ->
                            ServiceLocator.apiBackend.setActiveConversation(c.id)
                            selectedConversation = c
                            selectedInitialMessageId = mid
                            route = AuthRoute.ChatDetail
                        }
                    },
                    onBack = { route = AuthRoute.Home },
                )
            }
            AuthRoute.ChatDetail -> run {
                val conversation = selectedConversation
                if (conversation == null) {
                    route = AuthRoute.Home
                } else {
                    ChatDetailHost(
                        conversation = conversation,
                        initialMessageId = selectedInitialMessageId,
                        onBack = {
                            ServiceLocator.apiBackend.setActiveConversation(null)
                            selectedConversation = null
                            selectedInitialMessageId = null
                            route = AuthRoute.Home
                        },
                    )
                }
            }
        }
        }
        gate.Host()
        com.example.chat.ui.calls.GlobalCallHost(isAuthenticated = ServiceLocator.apiBackend.isAuthenticated)
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
        val toastPrefs = ServiceLocator.preferencesController
        ChatyEventToastOverlay(
            notificationService = ServiceLocator.notifications,
            preferences = toastPrefs,
            concealWhileLocked = lockRequired && ServiceLocator.apiBackend.isAuthenticated &&
                toastPrefs.security.isAppLockEnabled && toastPrefs.security.hideLockNotificationContent,
            content = { },
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

/** ApiBackendService's auth profile -> domain UserProfile (presence/lastSeen are
 *  not carried by the auth payload; defaults are offline / now). */
private fun toDomainUser(u: com.example.chat.data.api.ApiUserProfile): com.example.chat.domain.models.UserProfile =
    com.example.chat.domain.models.UserProfile(
        id = u.id, displayName = u.displayName, username = u.username,
        avatarInitials = u.avatarInitials, avatarColorHex = u.avatarColorHex, about = u.about,
        lastSeenAt = java.time.Instant.now(), email = u.email, phone = u.phone, avatarUrl = u.avatarUrl,
    )

/** settings_root_screen.dart _editAccountAvatar/_editAccountBanner: pick (camera/gallery), upload through
 *  ProfileMediaService, then dataStore.updateUser(currentUser.copyWith(...)) which the api branch sends as
 *  api.updateProfile(displayName, about, username, avatarUrl) - the banner URL is uploaded but not sent. A
 *  cancelled pick throws a "cancelled" message, which the root screen swallows like Dart. */
internal class RootProfilePhoto(
    val avatar: suspend (com.example.chat.ui.settings.ProfileMediaSource) -> Unit,
    val banner: suspend (com.example.chat.ui.settings.ProfileMediaSource) -> Unit,
)

@androidx.compose.runtime.Composable
private fun rememberRootProfilePhoto(): RootProfilePhoto {
    val context = androidx.compose.ui.platform.LocalContext.current
    var waiter by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<ByteArray?>?>(null) }
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        val bytes = try { uri?.let { context.contentResolver.openInputStream(it)?.use { i -> i.readBytes() } } } catch (_: Exception) { null }
        waiter?.complete(bytes); waiter = null
    }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview(),
    ) { bmp ->
        val bytes = bmp?.let {
            val o = java.io.ByteArrayOutputStream()
            it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, o)
            o.toByteArray()
        }
        waiter?.complete(bytes); waiter = null
    }
    suspend fun pick(source: com.example.chat.ui.settings.ProfileMediaSource): ByteArray {
        val d = kotlinx.coroutines.CompletableDeferred<ByteArray?>()
        waiter = d
        if (source == com.example.chat.ui.settings.ProfileMediaSource.camera) camera.launch(null) else gallery.launch("image/*")
        return d.await() ?: throw com.example.chat.data.services.ProfileMediaCancelledException()
    }
    fun media() = com.example.chat.data.services.ProfileMediaService(
        storage = ServiceLocator.storageClient,
        currentUserId = { ServiceLocator.sessionManager.currentUserId() },
    )
    suspend fun saveCurrent() {
        val me = ServiceLocator.apiBackend.currentUser ?: throw Exception("Not authenticated")
        ServiceLocator.apiBackend.updateProfile(
            displayName = me.displayName, about = me.about, username = me.username, avatarUrl = me.avatarUrl,
        )
    }
    return RootProfilePhoto(
        avatar = { source ->
            val url = media().uploadAvatar(pick(source))
            val me = ServiceLocator.apiBackend.currentUser ?: throw Exception("Not authenticated")
            ServiceLocator.apiBackend.updateProfile(
                displayName = me.displayName, about = me.about, username = me.username, avatarUrl = url,
            )
        },
        banner = { source ->
            media().uploadBanner(pick(source))
            saveCurrent()
        },
    )
}

@Composable
private fun MessageManagementHost(onBack: () -> Unit) {
                val mmToast = androidx.compose.ui.platform.LocalContext.current
                com.example.chat.ui.settings.messagemanagement.MessageManagementPage(
                    preferencesController = ServiceLocator.preferencesController,
                    server = ServiceLocator.gbFeatures,
                    conversationSource = object : com.example.chat.ui.settings.messagemanagement.MessageManagementConversationSource {
                        override val conversations
                            get() = ServiceLocator.apiBackend.conversations.map {
                                com.example.chat.ui.settings.messagemanagement.MessageManagementConversation(it.id, it.title)
                            }
                    },
                    onBack = onBack,
                    showToast = { android.widget.Toast.makeText(mmToast, it, android.widget.Toast.LENGTH_SHORT).show() },
                )
}


/** One pre-auth screen layer. A covered layer stays composed (form state kept) but is measured, placed and
 *  clipped at zero size so it neither draws, takes touches, nor appears in the accessibility tree; the
 *  visible layer blocks touches from reaching anything under it. */
@Composable
private fun PreAuthLayer(visible: Boolean, content: @Composable () -> Unit) {
    val mod = if (visible) {
        Modifier.fillMaxSize().pointerInput(Unit) {}
    } else {
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(0, 0) { placeable.place(0, 0) }
            }
            .clipToBounds()
    }
    androidx.compose.foundation.layout.Box(mod) { content() }
}
