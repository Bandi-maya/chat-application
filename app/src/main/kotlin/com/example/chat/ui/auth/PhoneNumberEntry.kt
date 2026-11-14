package com.example.chat.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity

/** Formatting is not ownership. A selected SIM hint must still be verified by a server OTP challenge. */
internal object PhoneNumberEntryPolicy {
    fun normalize(value: String): String? {
        return com.example.chat.data.api.RegistrationPhonePolicy.normalize(value)
    }
}

private fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeIf { it !== this }?.hostActivity()
    else -> null
}

@Composable
internal fun PhoneNumberEntry(value: String, theme: com.example.chat.core.theme.ThemeConfig, enabled: Boolean = true, onValueChange: (String) -> Unit, onNormalized: (String?) -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.hostActivity() }
    val changed by rememberUpdatedState(onValueChange)
    val normalizedCallback by rememberUpdatedState(onNormalized)
    var hintBusy by remember { mutableStateOf(false) }
    var hintStatus by remember { mutableStateOf<String?>(null) }
    var mounted by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { mounted = false } }
    fun emitValue(next: String) {
        changed(next)
        normalizedCallback(PhoneNumberEntryPolicy.normalize(next))
    }
    val normalized = remember(value) { PhoneNumberEntryPolicy.normalize(value) }
    LaunchedEffect(normalized) { normalizedCallback(normalized) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        hintBusy = false
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            val number = runCatching { Identity.getSignInClient(activity).getPhoneNumberFromIntent(result.data) }.getOrNull()
            if (!number.isNullOrBlank()) { emitValue(number); hintStatus = "Number selected. Unverified." }
            else hintStatus = "No number was supplied. Enter it manually."
        } else hintStatus = "You can enter your number manually."
    }
    Column(Modifier.fillMaxWidth()) {
        AuthTextField(value = value, onValueChange = { if (enabled) { emitValue(it.take(32)); hintStatus = null } },
            label = "Contact number", hintText = "+country code and number", theme = theme,
            capsule = true, keyboardType = KeyboardType.Phone,
            error = if (value.isNotBlank() && normalized == null) "Include +country code" else null)
        Spacer(Modifier.height(8.dp))
        TextButton(enabled = enabled && activity != null && !hintBusy,
            modifier = Modifier.align(androidx.compose.ui.Alignment.End).heightIn(min = 48.dp), onClick = {
            val host = activity ?: return@TextButton
            hintBusy = true; hintStatus = null
            try { Identity.getSignInClient(host).getPhoneNumberHintIntent(GetPhoneNumberHintIntentRequest.builder().build())
                .addOnSuccessListener { pending ->
                    if (mounted) {
                        try { launcher.launch(IntentSenderRequest.Builder(pending).build()) }
                        catch (_: Exception) { hintBusy = false; hintStatus = "Number chooser is unavailable. Enter the number manually." }
                    }
                }
                .addOnFailureListener { if (mounted) { hintBusy = false; hintStatus = "No number hint is available. Enter the number manually." } }
            } catch (_: Exception) {
                hintBusy = false; hintStatus = "Number chooser is unavailable. Enter the number manually."
            }
        }) { Text(if (hintBusy) "Opening number chooser..." else "Use device number") }
        hintStatus?.let { Text(it) }
    }
}
