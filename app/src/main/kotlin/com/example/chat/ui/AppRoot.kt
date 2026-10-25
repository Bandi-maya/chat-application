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
    @Suppress("UNUSED_EXPRESSION") themeRevision
    ChatyTheme(config = themeController.globalTheme) {
        var route by remember { mutableStateOf(AuthRoute.Splash) }
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
                val shellColors = remember(themeController.globalTheme) {
                    com.example.chat.ui.core.theme.ChatyColors(themeController.globalTheme)
                }
                var settingsNotice by remember { mutableStateOf<String?>(null) }
                var settingsSub by remember { mutableStateOf<com.example.chat.ui.settings.SettingsDestination?>(null) }
                var pushedSettings by remember { mutableStateOf(false) }
                var pushedProfile by remember { mutableStateOf(false) }
                val rootPhoto = rememberRootProfilePhoto()
                var pushedEditProfile by remember { mutableStateOf(false) }
                var pushedMessageManagement by remember { mutableStateOf(false) }
                var pushedAccount by remember { mutableStateOf(false) }
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
                com.example.chat.ui.navigation.MainNavigationShell(
                    colors = shellColors,
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
                                com.example.chat.ui.calls.CallsScreen(
                                    records = history.records,
                                    historyUnavailable = history.lastError != null,
                                    currentUserId = ServiceLocator.apiBackend.currentUser?.id ?: "",
                                    contactFor = { null },
                                    onRefresh = { callScope.launch { history.refresh() } },
                                    // Add-call contact picker host is not wired yet.
                                    onOpenAddCall = { settingsNotice = "New call" },
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
                                        colors = com.example.chat.ui.tasks.TaskColors(themeController.globalTheme),
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
                                        colors = com.example.chat.ui.tasks.TaskColors(themeController.globalTheme),
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
                            // Dart: def.icon ?? def.category.icon (per-setting icon names are not resolved yet).
                            icon = { def ->
                                when (def.category) {
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
                                    // customization, app_icon, universal_appearance: no host yet.
                                    else -> settingsNotice = def.canonicalRoute
                                }
                            },
                            onBack = { pushedSettingsSearch = false },
                        )
                    }
                }
                if (pushedMessageManagement) {
                    // Originating settings route stays under this overlay (Dart Navigator.push).
                    androidx.activity.compose.BackHandler { pushedMessageManagement = false }
                    Box(Modifier.fillMaxSize()) { MessageManagementHost(onBack = { pushedMessageManagement = false }) }
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
                        com.example.chat.ui.settings.SettingsSubHost(sub, onBack = { settingsSub = null })
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
                    colors = com.example.chat.ui.core.theme.ChatyColors(themeController.globalTheme),
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
                        initialMessageId = selectedInitialMessageId,
                        currentUserId = ServiceLocator.apiBackend.currentUser?.id ?: "",
                        onBack = {
                            ServiceLocator.apiBackend.setActiveConversation(null)
                            selectedConversation = null
                            selectedInitialMessageId = null
                            route = AuthRoute.Home
                        },
                        onSend = { text ->
                            ServiceLocator.apiBackend.sendMessage(conversation.id, text)
                        },
                    )
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
