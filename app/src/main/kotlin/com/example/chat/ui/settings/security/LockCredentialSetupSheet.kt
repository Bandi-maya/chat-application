package com.example.chat.ui.settings.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Pattern
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Port of LockCredentialSetupModal.show: modal bottom sheet, 28dp top corners, returns true when saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockCredentialSetupSheet(
    method: String,
    pinLength: Int,
    service: LockSetupService,
    replaceExisting: Boolean = false,
    onResult: (Boolean) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onResult(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        LockCredentialSetupContent(method, pinLength, service, replaceExisting, onResult)
    }
}

@Composable
fun LockCredentialSetupContent(
    method: String,
    pinLength: Int,
    service: LockSetupService,
    replaceExisting: Boolean,
    onResult: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val flow = remember(method, pinLength, replaceExisting) { LockCredentialSetupFlow(method, pinLength, service, replaceExisting) }
    var tick by remember(flow) { mutableIntStateOf(0) }
    DisposableEffect(flow) {
        val l: () -> Unit = { tick += 1 }
        flow.addListener(l)
        onDispose { flow.removeListener(l) }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val scope = rememberCoroutineScope()
    LaunchedEffect(flow) { flow.init() }

    val pins = remember(pinLength) { mutableStateListOf<String>().apply { repeat(pinLength) { add("") } } }
    val focusRequesters = remember(pinLength) { List(pinLength) { FocusRequester() } }
    var focusedPin by remember { mutableIntStateOf(-1) }
    var text by remember { mutableStateOf("") }
    var obscure by remember { mutableStateOf(true) }
    val padState = remember { PatternLockPadState() }

    fun clearPin() { for (i in pins.indices) pins[i] = ""; runCatching { focusRequesters.first().requestFocus() } }
    fun apply(o: SetupOutcome) {
        when (o) {
            SetupOutcome.clearPin -> clearPin()
            SetupOutcome.clearText -> text = ""
            SetupOutcome.resetPad -> padState.reset()
            SetupOutcome.saved -> onResult(true)
            SetupOutcome.none -> {}
        }
    }
    fun doContinue() {
        scope.launch {
            val input = if (flow.isPin) pins.joinToString("") else text
            apply(flow.handleContinue(input))
        }
    }

    val icon: ImageVector = when {
        flow.isPattern -> Icons.Rounded.Pattern
        flow.isBiometric -> Icons.Rounded.Fingerprint
        flow.isDeviceCredential -> Icons.Rounded.PhonelinkLock
        flow.isPassword -> Icons.Rounded.Password
        else -> Icons.Rounded.Pin
    }

    Column(
        Modifier.fillMaxWidth().imePadding().padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(cs.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = cs.primary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(flow.stepTitle(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700))
                Spacer(Modifier.height(3.dp))
                Text(flow.stepSubtitle(), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (flow.error.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.errorContainer).padding(12.dp)) {
                Text(flow.error, color = cs.onErrorContainer)
            }
            Spacer(Modifier.height(16.dp))
        }
        when {
            flow.isPattern -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Dart IgnorePointer(ignoring: busy)
                    Box {
                        PatternLockPad(
                            onPatternComplete = { flow.onPatternComplete(it) },
                            onPatternReset = { flow.onPatternReset() },
                            hideTrace = false,
                            enableHaptics = true,
                            clearOnFinish = false,
                            state = padState,
                        )
                        if (flow.busy) {
                            Box(
                                Modifier.matchParentSize().pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { padState.reset() }, enabled = !flow.busy, modifier = Modifier.weight(1f)) { Text("Clear") }
                    Button(
                        onClick = { scope.launch { apply(flow.handlePatternContinue()) } },
                        enabled = flow.canContinuePattern,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (flow.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (flow.step == SetupStep.confirmNew) "Confirm & Save" else "Continue")
                    }
                }
            }
            flow.isBiometric || flow.isDeviceCredential -> {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (flow.isBiometric) Icons.Rounded.Fingerprint else Icons.Rounded.PhonelinkLock,
                        null, Modifier.size(86.dp), tint = cs.primary,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { scope.launch { apply(flow.verifyNativeMethod()) } },
                    enabled = !flow.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (flow.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(if (flow.isBiometric) Icons.Rounded.Fingerprint else Icons.Rounded.VerifiedUser, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (flow.busy) "Verifying\u2026" else if (flow.isBiometric) "Verify biometric" else "Verify device lock")
                }
            }
            flow.isPin -> {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    for (index in 0 until pinLength) {
                        val focused = focusedPin == index
                        Box(
                            Modifier.padding(horizontal = 5.dp).width(48.dp).height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cs.surfaceContainerHighest.copy(alpha = 0.5f))
                                .border(
                                    BorderStroke(if (focused) 2.dp else 1.dp, if (focused) cs.primary else cs.outline.copy(alpha = 0.3f)),
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicTextField(
                                value = pins[index],
                                onValueChange = { raw ->
                                    val v = raw.filter { it.isDigit() }.takeLast(1)
                                    pins[index] = v
                                    if (v.isNotEmpty()) {
                                        if (index < pinLength - 1) focusRequesters[index + 1].requestFocus()
                                        else doContinue()
                                    } else if (index > 0) focusRequesters[index - 1].requestFocus()
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface, textAlign = TextAlign.Center),
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequesters[index])
                                    .onFocusChanged { if (it.isFocused) focusedPin = index else if (focusedPin == index) focusedPin = -1 },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = { doContinue() }, enabled = !flow.busy, modifier = Modifier.fillMaxWidth()) {
                    if (flow.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (flow.step == SetupStep.confirmNew) "Confirm PIN" else "Continue")
                }
            }
            else -> {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = !flow.busy,
                    singleLine = true,
                    visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { doContinue() }),
                    textStyle = TextStyle(fontSize = 16.sp, letterSpacing = if (obscure) 2.2.sp else 0.sp, color = cs.onSurface),
                    label = {
                        Text(
                            when (flow.step) {
                                SetupStep.enterCurrent -> "Current Password"
                                SetupStep.enterNew -> "New Password"
                                SetupStep.confirmNew -> "Confirm Password"
                            },
                        )
                    },
                    placeholder = { Text("At least 6 characters") },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = cs.surfaceContainerHighest.copy(alpha = 0.58f),
                        unfocusedContainerColor = cs.surfaceContainerHighest.copy(alpha = 0.58f),
                        disabledContainerColor = cs.surfaceContainerHighest.copy(alpha = 0.58f),
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.outline.copy(alpha = 0.22f),
                    ),
                    trailingIcon = {
                        IconButton(onClick = { obscure = !obscure }) {
                            Icon(if (obscure) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (obscure) "Show password" else "Hide password")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { doContinue() }, enabled = !flow.busy, modifier = Modifier.fillMaxWidth()) {
                    if (flow.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (flow.step == SetupStep.confirmNew) "Save Password" else "Continue")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Credentials are encrypted with salted PBKDF2 hashes in hardware-backed secure storage.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurfaceVariant, fontSize = 11.5.sp),
        )
    }
}
