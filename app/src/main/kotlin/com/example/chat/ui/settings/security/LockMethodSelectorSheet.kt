package com.example.chat.ui.settings.security

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Pattern
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/** Biometric availability seam (LocalLockService.canUseBiometrics). */
fun interface BiometricAvailability { suspend fun canUseBiometrics(): Boolean }

/** Port of LockMethodSelectorSheet. [onResult] gets the chosen method or null when dismissed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockMethodSelectorSheet(
    availability: BiometricAvailability,
    currentMethod: LockMethodType?,
    onResult: (LockMethodType?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onResult(null) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LockMethodSelectorContent(availability, currentMethod, onResult)
    }
}

@Composable
fun LockMethodSelectorContent(
    availability: BiometricAvailability,
    currentMethod: LockMethodType?,
    onResult: (LockMethodType?) -> Unit,
) {
    val colors = LocalAppColors.current
    var biometricAvailable by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { biometricAvailable = availability.canUseBiometrics() }

    Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
        Text("Choose a lock type", color = colors.foreground, fontSize = 20.sp, fontWeight = FontWeight.W800, letterSpacing = (-0.3).sp)
        Spacer(Modifier.height(4.dp))
        Text("You will set it up in the next step. You can change it later.", color = colors.foregroundSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        MethodCard(Icons.Rounded.Pin, "PIN", "A 4- or 6-digit numeric code", currentMethod == LockMethodType.pin) { onResult(LockMethodType.pin) }
        Spacer(Modifier.height(10.dp))
        MethodCard(Icons.Rounded.Pattern, "Pattern", "Draw a gesture on a 3\u00d73 grid", currentMethod == LockMethodType.pattern) { onResult(LockMethodType.pattern) }
        Spacer(Modifier.height(10.dp))
        MethodCard(Icons.Rounded.Password, "Password", "At least 6 characters, letters and symbols allowed", currentMethod == LockMethodType.password) { onResult(LockMethodType.password) }
        Spacer(Modifier.height(10.dp))
        OsAuthCard(
            Icons.Rounded.Fingerprint, "Biometric",
            when (biometricAvailable) {
                null -> "Checking device support\u2026"
                true -> "Fingerprint / face enrolled on this device"
                false -> "No biometric is enrolled on this device"
            },
            enabled = biometricAvailable == true,
            selected = currentMethod == LockMethodType.biometric,
        ) { onResult(LockMethodType.biometric) }
        Spacer(Modifier.height(10.dp))
        OsAuthCard(
            Icons.Rounded.PhonelinkLock, "Device lock",
            "The PIN / pattern / password managed by Android or iOS",
            enabled = true,
            selected = currentMethod == LockMethodType.deviceCredential,
        ) { onResult(LockMethodType.deviceCredential) }
    }
}

@Composable
private fun CardBody(icon: ImageVector, title: String, subtitle: String, selected: Boolean, enabled: Boolean, trailing: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .border(BorderStroke(if (selected) 1.6.dp else 1.dp, if (selected) colors.primary else colors.borderSubtle), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .background(if (enabled) colors.primary.copy(alpha = 0.12f) else colors.surfaceSecondary),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(21.dp), tint = if (enabled) colors.primary else colors.disabled) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) colors.foreground else colors.foregroundTertiary, fontSize = 15.sp, fontWeight = FontWeight.W700)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.5.sp)
        }
        trailing()
    }
}

@Composable
private fun RadioIcon(selected: Boolean) {
    val colors = LocalAppColors.current
    Icon(
        if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
        null, Modifier.size(20.dp), tint = if (selected) colors.primary else colors.foregroundTertiary,
    )
}

/** _MethodCard: press scale 0.98 over 120ms easeOutCubic. */
@Composable
private fun MethodCard(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)), label = "methodScale")
    Box(
        Modifier.scale(scale)
            .semantics { role = Role.Button; this.selected = selected; contentDescription = if (selected) "$title, selected" else title }
            .pointerInput(onTap) {
                detectTapGestures(onPress = {
                    pressed = true
                    val ok = tryAwaitRelease()
                    pressed = false
                    if (ok) onTap()
                })
            },
    ) { CardBody(icon, title, subtitle, selected, true) { RadioIcon(selected) } }
}

/** _OsAuthCard: honest UNAVAILABLE state; only tappable when enabled. */
@Composable
private fun OsAuthCard(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, selected: Boolean, onEnabledTap: () -> Unit) {
    val colors = LocalAppColors.current
    val body: @Composable () -> Unit = {
        CardBody(icon, title, subtitle, selected, enabled) {
            if (!enabled) {
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(colors.warning.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("UNAVAILABLE", color = colors.warning, fontSize = 10.sp, fontWeight = FontWeight.W800, letterSpacing = 0.4.sp)
                }
            } else RadioIcon(selected)
        }
    }
    if (!enabled) body() else Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onEnabledTap)) { body() }
}
