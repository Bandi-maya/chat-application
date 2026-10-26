package com.example.chat.ui.settings.security

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Pattern
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerifiedUser
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.services.LocalLockService
import com.example.chat.ui.settings.ChatyBadge
import com.example.chat.ui.settings.ChatyChoiceTile
import com.example.chat.ui.settings.ChatySettingsPage
import com.example.chat.ui.settings.ChatySettingsRowDivider
import com.example.chat.ui.settings.ChatySettingsSection
import com.example.chat.ui.settings.ChatySettingsTile
import com.example.chat.ui.settings.ChatySwitchTile
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

private val AutoLockOptions = listOf("Immediately", "15s", "30s", "1m", "5m", "15m")

private sealed interface Modal {
    class Verify(val result: CompletableDeferred<Boolean>) : Modal
    class Pick(val current: LockMethodType, val result: CompletableDeferred<LockMethodType?>) : Modal
    class Setup(val method: String, val pinLength: Int, val result: CompletableDeferred<Boolean>) : Modal
    class TestLock(val result: CompletableDeferred<Boolean>) : Modal
    object SafetyNumber : Modal
    class SecretPhrase(val result: CompletableDeferred<String?>) : Modal
}

/**
 * Port of SecurityCenterScreen (lib/features/settings/security/security_center_screen.dart).
 * Flow decisions live in [SecurityFlowRunner] (JVM-tested). UNCOMPILED (Compose).
 * [onOpenLockedChats] replaces LockedChatsScreen.open (screen owned by another worker).
 * The safety-number dialog is defined in Dart but not referenced from build(); it is kept for parity.
 */
@Composable
fun SecurityCenterScreen(
    preferences: ChatyPreferencesController,
    lockService: LocalLockService,
    onBack: () -> Unit,
    onOpenLockedChats: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    var pinLength by remember { mutableIntStateOf(4) }
    var biometricAvailable by remember { mutableStateOf(false) }
    var modal by remember { mutableStateOf<Modal?>(null) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { active = false } }

    suspend fun loadCapabilities() {
        val pl = lockService.getPinLength()
        val bio = lockService.canUseBiometrics()
        if (!active) return
        pinLength = pl
        biometricAvailable = bio
    }
    LaunchedEffect(Unit) { loadCapabilities() }

    fun toast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    val host = remember(preferences, lockService) {
        object : SecurityFlowHost {
            override fun currentLockMethod() = preferences.security.lockMethod
            override fun isAppLockEnabled() = preferences.security.isAppLockEnabled
            override suspend fun hasCredential(storageKey: String) = lockService.hasCredential(storageKey)
            override suspend fun authenticateBiometric(reason: String) = lockService.authenticateBiometric(reason)
            override suspend fun authenticateDeviceCredential(reason: String) = lockService.authenticateDeviceCredential(reason)
            override suspend fun showVerifyOverlay(): Boolean {
                val d = CompletableDeferred<Boolean>(); modal = Modal.Verify(d); return d.await().also { modal = null }
            }
            override suspend fun pickMethod(current: LockMethodType): LockMethodType? {
                val d = CompletableDeferred<LockMethodType?>(); modal = Modal.Pick(current, d); return d.await().also { modal = null }
            }
            override suspend fun setupCredential(storageKey: String, pinLength: Int): Boolean {
                val d = CompletableDeferred<Boolean>(); modal = Modal.Setup(storageKey, pinLength, d); return d.await().also { modal = null }
            }
            override suspend fun reloadCapabilities() = loadCapabilities()
            override fun toast(message: String) = toast(message)
            override fun isActive() = active
            override fun applySecurity(lockMethod: String?, appLockEnabled: Boolean?, logTitle: String) {
                val latest = preferences.security
                preferences.updateSecurity(
                    latest.copy(
                        lockMethod = lockMethod ?: latest.lockMethod,
                        isAppLockEnabled = appLockEnabled ?: latest.isAppLockEnabled,
                    ),
                    logTitle = logTitle,
                )
            }
        }
    }

    fun runFlow(intent: SecurityIntent, target: LockMethodType? = null, setupPinLength: Int? = null) {
        scope.launch { SecurityFlowRunner.run(host, intent, target, setupPinLength, pinLength) }
    }

    @Suppress("UNUSED_EXPRESSION") version
    val security = preferences.security
    val lockedChats = security.lockedConversationIds.size

    Box(Modifier.fillMaxSize()) {
        ChatySettingsPage(
            title = "Security & App Lock",
            subtitle = "Encryption Status, Authentication & Chaty Lock",
            onBack = onBack,
        ) {
            ChatySettingsSection(
                title = "Chaty App Lock",
                description = "Require a local credential or operating-system authentication before Chaty can be used.",
            ) {
                val rows = mutableListOf<@Composable () -> Unit>()
                rows += {
                    ChatySwitchTile(
                        icon = Icons.Rounded.Lock,
                        iconColor = colors.warning,
                        title = "Enable Chaty Lock",
                        subtitle = if (security.isAppLockEnabled) "App lock active (${security.lockMethod})"
                        else "Protect the application with biometrics, PIN, pattern, password, or device lock",
                        value = security.isAppLockEnabled,
                        onChanged = { v -> runFlow(if (v) SecurityIntent.enableLock else SecurityIntent.disableLock) },
                    )
                }
                if (security.isAppLockEnabled) {
                    rows += {
                        ChatySettingsTile(
                            icon = Icons.Rounded.Style,
                            title = "Lock type",
                            subtitle = "${security.lockMethod} \u2022 tap to verify and change type",
                            trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.foregroundTertiary, modifier = Modifier.size(20.dp)) },
                            onTap = { runFlow(SecurityIntent.changeMethod, null) },
                        )
                    }
                    if (security.lockMethod == "Biometric") rows += {
                        ChatySettingsTile(
                            icon = Icons.Rounded.Fingerprint,
                            title = "Biometric authentication",
                            subtitle = if (biometricAvailable) "Uses enrolled fingerprint / face authentication from this device"
                            else "No enrolled biometric is currently available",
                            badgeText = if (biometricAvailable) "READY" else "UNAVAILABLE",
                            badgeColor = if (biometricAvailable) colors.success else colors.warning,
                            onTap = {
                                scope.launch {
                                    val ok = lockService.authenticateBiometric("Test biometric unlock for Chaty")
                                    if (active) toast(if (ok) "Biometric unlock works." else "Biometric failed or was cancelled.")
                                }
                            },
                        )
                    }
                    if (security.lockMethod == "Device Credential") rows += {
                        ChatySettingsTile(
                            icon = Icons.Rounded.PhonelinkLock,
                            title = "Device screen lock",
                            subtitle = "Use the PIN, pattern, password, or biometric managed by Android/iOS",
                            onTap = {
                                scope.launch {
                                    val ok = lockService.authenticateDeviceCredential("Test your device screen lock for Chaty")
                                    if (active) toast(if (ok) "Device lock works." else "Device authentication failed or was cancelled.")
                                }
                            },
                        )
                    }
                    if (security.lockMethod == "PIN") {
                        rows += {
                            ChatyChoiceTile(
                                title = "PIN Length",
                                options = listOf("4 digits", "6 digits"),
                                selectedOption = if (pinLength == 6) "6 digits" else "4 digits",
                                optionLabel = { it },
                                onSelected = { v -> runFlow(SecurityIntent.changeCredential, LockMethodType.pin, if (v == "6 digits") 6 else 4) },
                            )
                        }
                        rows += {
                            ChatySettingsTile(
                                icon = Icons.Rounded.Pin,
                                title = "Change PIN Code",
                                subtitle = "$pinLength-digit PIN stored securely on this device",
                                onTap = { runFlow(SecurityIntent.changeCredential, LockMethodType.pin, pinLength) },
                            )
                        }
                    }
                    if (security.lockMethod == "Password") rows += {
                        ChatySettingsTile(
                            icon = Icons.Rounded.Password,
                            title = "Change Lock Password",
                            subtitle = "Password is hashed and stored in secure device storage",
                            onTap = { runFlow(SecurityIntent.changeCredential, LockMethodType.password) },
                        )
                    }
                    if (security.lockMethod == "Pattern") {
                        rows += {
                            ChatySettingsTile(
                                icon = Icons.Rounded.Pattern,
                                title = "Change Unlock Pattern",
                                subtitle = "Draw and confirm a 3\u00D73 gesture pattern",
                                onTap = { runFlow(SecurityIntent.changeCredential, LockMethodType.pattern) },
                            )
                        }
                        rows += {
                            ChatySwitchTile(
                                title = "Make Pattern Invisible",
                                subtitle = "Hide pattern lines while drawing",
                                value = security.makePatternInvisible,
                                onChanged = { v -> preferences.updateSecurity(security.copy(makePatternInvisible = v), logTitle = "Pattern visibility") },
                            )
                        }
                        rows += {
                            ChatySwitchTile(
                                title = "Disable Pattern Vibration",
                                subtitle = "Turn off haptic feedback while drawing the pattern",
                                value = security.disablePatternVibration,
                                onChanged = { v -> preferences.updateSecurity(security.copy(disablePatternVibration = v), logTitle = "Pattern vibration") },
                            )
                        }
                    }
                    rows += {
                        ChatyChoiceTile(
                            title = "Auto Lock Timeout",
                            options = AutoLockOptions,
                            selectedOption = security.autoLockTimeout,
                            optionLabel = { it },
                            onSelected = { v -> preferences.updateSecurity(security.copy(autoLockTimeout = v), logTitle = "Auto Lock Timeout") },
                        )
                    }
                    rows += {
                        ChatySwitchTile(
                            icon = Icons.Rounded.NotificationsPaused,
                            title = "Hide Notification Content when Locked",
                            subtitle = "Conceal message body text while the application is locked",
                            value = security.hideLockNotificationContent,
                            onChanged = { v -> preferences.updateSecurity(security.copy(hideLockNotificationContent = v), logTitle = "Lock notification privacy") },
                        )
                    }
                    rows += {
                        ChatySettingsTile(
                            icon = Icons.Rounded.LockOpen,
                            iconColor = colors.error,
                            title = "Test Lock Screen",
                            subtitle = "Run the same authentication gate used by the application",
                            onTap = { modal = Modal.TestLock(CompletableDeferred()) },
                        )
                    }
                }
                rows.forEachIndexed { i, row ->
                    row()
                    if (i < rows.lastIndex) ChatySettingsRowDivider()
                }
            }
            ChatySettingsSection(
                title = "Chat Lock & Hidden Chats",
                description = "Locked and hidden conversations require verified local authentication. Hidden chats do not appear in normal chat lists.",
            ) {
                ChatySettingsTile(
                    icon = Icons.Outlined.Lock,
                    iconColor = colors.accent,
                    title = "Open Locked & Hidden Chats",
                    subtitle = if (lockedChats == 0) "No chats protected \u2022 Tap to manage vault"
                    else "$lockedChats conversation${if (lockedChats == 1) "" else "s"} in secure vault",
                    badgeText = "$lockedChats",
                    badgeColor = if (lockedChats > 0) colors.accent else colors.foregroundSecondary,
                    onTap = onOpenLockedChats,
                )
                ChatySettingsRowDivider()
                ChatySwitchTile(
                    icon = Icons.Rounded.TouchApp,
                    title = "Unlock via Chaty Title Tap",
                    subtitle = "Tap the main screen Chaty header to authenticate and open locked chats",
                    value = security.entryByAppTitle,
                    onChanged = { v -> preferences.updateSecurity(security.copy(entryByAppTitle = v), logTitle = "Title tap vault entry") },
                )
                ChatySettingsRowDivider()
                ChatySwitchTile(
                    icon = Icons.Rounded.Key,
                    title = "Secret Code Search Discovery",
                    subtitle = "Reveal hidden locked chats only when typing your secret word or emoji in search",
                    value = security.entryBySecretPhrase,
                    onChanged = { v ->
                        scope.launch {
                            if (v) {
                                val d = CompletableDeferred<String?>()
                                modal = Modal.SecretPhrase(d)
                                val phrase = d.await().also { modal = null }
                                if (phrase.isNullOrEmpty() || !active) return@launch
                                lockService.setSecretPhrase(phrase)
                                preferences.updateSecurity(security.copy(entryBySecretPhrase = true), logTitle = "Secret search phrase entry")
                            } else {
                                preferences.updateSecurity(security.copy(entryBySecretPhrase = false), logTitle = "Secret search phrase disabled")
                            }
                        }
                    },
                )
                ChatySettingsRowDivider()
                ChatySwitchTile(
                    icon = Icons.Rounded.Screenshot,
                    title = "Protect Screen & App Previews",
                    subtitle = "Blank out app thumbnail in multi-tasking view and block screenshots on supported devices",
                    value = security.protectFromScreenshots,
                    onChanged = { v -> preferences.updateSecurity(security.copy(protectFromScreenshots = v), logTitle = "Screen & preview security") },
                )
                ChatySettingsRowDivider()
                ChatySettingsTile(
                    icon = Icons.Outlined.VerifiedUser,
                    title = "Lock Authentication Method",
                    subtitle = "Uses ${security.lockMethod}. Chat Lock protects specific chats even if full App Lock is off.",
                    onTap = { runFlow(SecurityIntent.changeCredential, SecurityFlowRunner.methodTypeOf(security.lockMethod)) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    when (val m = modal) {
        is Modal.Verify -> LockOverlayDialog(preferences, lockService, "Confirm it's you", "Verify your current lock to continue", m.result)
        is Modal.TestLock -> LockOverlayDialog(preferences, lockService, "Test Chaty Lock", "Authenticate to verify your Chaty Lock configuration", m.result) { modal = null }
        is Modal.Pick -> LockMethodSelectorSheet(LockSetupServiceAdapter(lockService), m.current) { m.result.complete(it) }
        is Modal.Setup -> LockCredentialSetupSheet(m.method, m.pinLength, LockSetupServiceAdapter(lockService), replaceExisting = true) { m.result.complete(it) }
        is Modal.SecretPhrase -> SecretPhraseDialog(m.result)
        Modal.SafetyNumber -> SafetyNumberDialog { modal = null }
        null -> {}
    }
}

@Composable
private fun LockOverlayDialog(
    preferences: ChatyPreferencesController,
    lockService: LocalLockService,
    title: String,
    reason: String,
    result: CompletableDeferred<Boolean>,
    onClosed: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = { result.complete(false); onClosed() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            AppLockOverlay(
                preferences = preferences,
                lockService = lockService,
                onUnlocked = { result.complete(true); onClosed() },
                title = title,
                reason = reason,
            )
        }
    }
}

@Composable
private fun SecretPhraseDialog(result: CompletableDeferred<String?>) {
    val colors = LocalAppColors.current
    var text by remember { mutableStateOf("") }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = { result.complete(null) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, null, tint = colors.accent)
                Spacer(Modifier.width(8.dp))
                Text("Set Secret Search Word")
            }
        },
        text = {
            Column {
                Text(
                    "Enter a secret word, phrase, or emoji. Typing this in the search bar will reveal your hidden locked chats.",
                    fontSize = 13.sp, color = colors.foregroundSecondary,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Secret Word or Emoji") },
                    placeholder = { Text("e.g. sesame, vault, \uD83D\uDD12") },
                    singleLine = true,
                )
            }
        },
        dismissButton = { TextButton(onClick = { result.complete(null) }) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = { val t = text.trim(); if (t.isNotEmpty()) result.complete(t) }) { Text("Save Word") }
        },
    )
}

@Composable
private fun SafetyNumberDialog(onClose: () -> Unit) {
    val colors = LocalAppColors.current
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = colors.success)
                Spacer(Modifier.width(8.dp))
                Text("Safety Number Verification")
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.background(colors.surfaceSecondary, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text(
                        "05423 89104 33812 77192\n44901 88321 00192 44381",
                        textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Demo Security Model \u2022 End-to-end encryption state verified with prekey identity.",
                    textAlign = TextAlign.Center, fontSize = 12.sp, color = colors.foregroundSecondary,
                )
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("Verified") } },
    )
}
