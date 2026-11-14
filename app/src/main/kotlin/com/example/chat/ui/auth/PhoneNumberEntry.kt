package com.example.chat.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.telephony.PhoneNumberUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import java.util.Locale

/** Formatting is not ownership. A selected SIM hint must still be verified by a server OTP challenge. */
internal object PhoneNumberEntryPolicy {
    fun normalize(value: String, region: String): String? {
        if (value.isBlank()) return null
        if (value.length > 40 || value.any { !it.isDigit() && it !in "+()- ." }) return null
        return PhoneNumberUtils.formatNumberToE164(value.trim(), region.uppercase(Locale.ROOT))
            ?.takeIf { Regex("\\+[1-9][0-9]{6,14}").matches(it) }
    }
}

private fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeIf { it !== this }?.hostActivity()
    else -> null
}

@Composable
internal fun PhoneNumberEntry(value: String, onValueChange: (String) -> Unit, onNormalized: (String?) -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.hostActivity() }
    val changed by rememberUpdatedState(onValueChange)
    val normalizedCallback by rememberUpdatedState(onNormalized)
    var region by remember { mutableStateOf(Locale.getDefault().country.takeIf { it in Locale.getISOCountries() } ?: "IN") }
    var countriesOpen by remember { mutableStateOf(false) }
    var hintBusy by remember { mutableStateOf(false) }
    var hintStatus by remember { mutableStateOf<String?>(null) }
    var mounted by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { mounted = false } }
    fun emitValue(next: String) {
        changed(next)
        normalizedCallback(PhoneNumberEntryPolicy.normalize(next, region))
    }
    val normalized = remember(value, region) { PhoneNumberEntryPolicy.normalize(value, region) }
    LaunchedEffect(normalized) { normalizedCallback(normalized) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        hintBusy = false
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            val number = runCatching { Identity.getSignInClient(activity).getPhoneNumberFromIntent(result.data) }.getOrNull()
            if (!number.isNullOrBlank()) { emitValue(number); hintStatus = "Number selected. Review it below. This is not verified." }
            else hintStatus = "No number was supplied. Enter it manually."
        } else hintStatus = "You can enter your number manually."
    }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { countriesOpen = true }, enabled = !hintBusy) {
            Text("Country: ${Locale("", region).displayCountry} ($region)")
        }
        OutlinedTextField(value = value, onValueChange = { emitValue(it.take(40)); hintStatus = null },
            label = { Text("Contact number (optional)") }, placeholder = { Text("International number, for example +91...") },
            singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = value.isNotBlank() && normalized == null, modifier = Modifier.fillMaxWidth())
        if (value.isNotBlank()) Text(normalized?.let { "Number format: $it · Not verified" }
            ?: "Check the number and selected country. A valid format does not verify ownership.")
        TextButton(enabled = activity != null && !hintBusy, onClick = {
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
        }) { Text(if (hintBusy) "Opening number chooser..." else "Use phone number from this device") }
        hintStatus?.let { Text(it) }
        Text("Optional field. A hint or valid format does not verify ownership. Only a successful SMS challenge can attach a verified number; email-only registration remains available.")
    }
    if (countriesOpen) {
        var query by remember { mutableStateOf("") }
        val countries = remember(query) { Locale.getISOCountries().filter {
            it.contains(query, true) || Locale("", it).displayCountry.contains(query, true)
        }.sortedBy { Locale("", it).displayCountry } }
        AlertDialog(onDismissRequest = { countriesOpen = false }, title = { Text("Choose country") },
            text = { Column { OutlinedTextField(query, { query = it.take(80) }, label = { Text("Search country") })
                LazyColumn(Modifier.heightIn(max = 320.dp)) { items(countries, key = { it }) { iso ->
                    TextButton(onClick = { region = iso; normalizedCallback(PhoneNumberEntryPolicy.normalize(value, iso)); countriesOpen = false }) { Text("${Locale("", iso).displayCountry} ($iso)") }
                } }
            } }, confirmButton = { TextButton(onClick = { countriesOpen = false }) { Text("Cancel") } })
    }
}
