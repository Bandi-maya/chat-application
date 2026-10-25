package com.example.chat.ui.settings.security

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.services.LocalLockService
import kotlinx.coroutines.launch

/**
 * Port of AppLockOverlayModal (app_lock_overlay.dart). Full-screen, back is
 * swallowed (PopScope canPop:false). [onUnlocked] is invoked only by
 * [AppLockController] after a verified credential or native auth success.
 * [onSetupMissingCredential] hosts the LockCredentialSetupModal flow (returns
 * true when a credential was configured, then capabilities reload).
 * Pending visual check: key press scale animation, haptics, Material icons.
 */
@Composable
fun AppLockOverlay(
    preferences: ChatyPreferencesController,
    lockService: LocalLockService,
    onUnlocked: () -> Unit,
    title: String = "Chaty Lock",
    reason: String = "Authenticate to unlock Chaty",
    onSetupMissingCredential: suspend (method: String, pinLength: Int) -> Boolean = { _, _ -> false },
) {
    val scope = rememberCoroutineScope()
    val controller = remember(lockService) {
        AppLockController(lockService, { preferences.security.lockMethod }, reason, onUnlocked)
    }
    val ui by controller.state.collectAsState()
    var password by remember { mutableStateOf("") }
    val security = preferences.security
    val method = security.lockMethod
    val cs = MaterialTheme.colorScheme
    val locked = ui.busy || ui.cooldownSeconds > 0

    BackHandler(enabled = true) { /* canPop: false */ }
    LaunchedEffect(controller) {
        controller.loadCapabilities()
        controller.startNativeMethodIfNeeded()
    }
    LaunchedEffect(ui.errorMessage) { if (ui.errorMessage.isNotEmpty() && method == "Password") password = "" }

    Box(Modifier.fillMaxSize().background(cs.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 430.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(74.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text(
                    when (method) { "Biometric" -> "\u261D"; "Pattern" -> "\u2234"; "Device Credential" -> "\u25A3"; else -> "\u25A0" },
                    color = cs.primary, fontSize = 32.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(title, color = cs.onBackground, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (method == "Device Credential") "Use your device screen lock to continue" else "Use $method to continue",
                color = cs.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            if (ui.errorMessage.isNotEmpty()) {
                Text(
                    ui.errorMessage, color = cs.onErrorContainer, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.errorContainer).padding(12.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
            val credentialMethod = method == "PIN" || method == "Pattern" || method == "Password"
            if (!ui.hasConfiguredCredential && credentialMethod) {
                Text("This lock method has not been configured securely on this device yet.", color = cs.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    scope.launch { if (onSetupMissingCredential(method, ui.pinLength)) controller.loadCapabilities() }
                }) { Text("Set up $method") }
            } else when (method) {
                "PIN" -> {
                    PinDots(ui.pinLength, ui.enteredPin.length)
                    Spacer(Modifier.height(24.dp))
                    PinKeypad(
                        enabled = !locked,
                        biometricEnabled = !ui.busy && ui.biometricAvailable,
                        deleteEnabled = !ui.busy && ui.enteredPin.isNotEmpty(),
                        onDigit = { d -> scope.launch { controller.verifyPinDigit(d) } },
                        onBiometric = { scope.launch { controller.runNativeAuthentication("Biometric") } },
                        onDelete = { controller.deleteDigit() },
                    )
                    Spacer(Modifier.height(12.dp))
                    NativeFallbacks(ui.biometricAvailable, ui.busy, controller, scope, "Use fingerprint / Face unlock")
                }
                "Pattern" -> {
                    PatternLockPad(
                        hideTrace = security.makePatternInvisible,
                        enableHaptics = !security.disablePatternVibration,
                        onPatternComplete = { p -> scope.launch { controller.verifySecret("Pattern", p) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    NativeFallbacks(ui.biometricAvailable, ui.busy, controller, scope, "Use fingerprint / Face unlock")
                }
                "Password" -> {
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        enabled = !locked, singleLine = true, label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { scope.launch { controller.verifySecret("Password", password) } }),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { scope.launch { controller.verifySecret("Password", password) } },
                        enabled = !locked, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (ui.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Unlock")
                    }
                    Spacer(Modifier.height(10.dp))
                    NativeFallbacks(ui.biometricAvailable, ui.busy, controller, scope, "Use biometric instead")
                }
                else -> {
                    Spacer(Modifier.height(8.dp))
                    if (ui.busy) CircularProgressIndicator() else Button(onClick = { scope.launch { controller.runNativeAuthentication(method) } }) {
                        Text(if (method == "Device Credential") "Authenticate with device lock" else "Scan biometric")
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeFallbacks(
    biometricAvailable: Boolean,
    busy: Boolean,
    controller: AppLockController,
    scope: kotlinx.coroutines.CoroutineScope,
    biometricLabel: String,
) {
    val cs = MaterialTheme.colorScheme
    if (biometricAvailable) {
        TextButton(onClick = { scope.launch { controller.runNativeAuthentication("Biometric") } }, enabled = !busy) {
            Text(biometricLabel, color = cs.primary)
        }
    }
    TextButton(onClick = { scope.launch { controller.runNativeAuthentication("Device Credential") } }, enabled = !busy) {
        Text("Can't remember? Use device screen lock", fontSize = 12.5.sp, color = cs.onSurfaceVariant.copy(alpha = 0.8f))
    }
}

@Composable
private fun PinDots(length: Int, filledCount: Int) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(length) { i ->
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .background(if (i < filledCount) cs.primary else Color.Transparent)
                    .border(2.dp, cs.primary, CircleShape),
            )
        }
    }
}

private val keySub = mapOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+")

@Composable
private fun PinKeypad(
    enabled: Boolean,
    biometricEnabled: Boolean,
    deleteEnabled: Boolean,
    onDigit: (String) -> Unit,
    onBiometric: () -> Unit,
    onDelete: () -> Unit,
) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                r.forEach { d -> Box(Modifier.weight(1f)) { NumberKey(d, keySub[d] ?: "", enabled) { onDigit(d) } } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { ActionKey("\u261D", biometricEnabled, onBiometric) }
            Box(Modifier.weight(1f)) { NumberKey("0", "+", enabled) { onDigit("0") } }
            Box(Modifier.weight(1f)) { ActionKey("\u232B", deleteEnabled, onDelete) }
        }
    }
}

@Composable
private fun NumberKey(label: String, sub: String, enabled: Boolean, onTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.55f))
            .clickable(enabled = enabled, onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = cs.onSurface, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        if (sub.isNotEmpty()) Text(sub, color = cs.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun ActionKey(glyph: String, enabled: Boolean, onTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = 22.sp, color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.3f)) }
}
