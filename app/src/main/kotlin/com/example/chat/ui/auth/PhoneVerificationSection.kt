package com.example.chat.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.chat.ServiceLocator
import com.example.chat.data.api.PhoneVerificationChallenge
import com.example.chat.data.api.PhoneVerificationFailure
import com.example.chat.data.api.PhoneVerificationProof
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Optional verification, not login or account creation. No automatic SMS request or READ_SMS access. */
@Composable
internal fun PhoneVerificationSection(number: String?, tenant: String?, useDefault: Boolean, enabled: Boolean,
    onProof: (PhoneVerificationProof?) -> Unit) {
    key(number, tenant, useDefault) {
        val api = ServiceLocator.apiBackend
        val owner = remember { api.authSnapshot() }
        val latestProof by rememberUpdatedState(onProof)
        val scope = rememberCoroutineScope()
        var challenge by remember { mutableStateOf<PhoneVerificationChallenge?>(null) }
        var proof by remember { mutableStateOf<PhoneVerificationProof?>(null) }
        var code by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf<String?>(null) }
        var retryAt by remember { mutableStateOf<Instant?>(null) }
        var now by remember { mutableStateOf(Instant.now()) }
        LaunchedEffect(Unit) { latestProof(null) }
        LaunchedEffect(retryAt, challenge, proof) {
            while (true) {
                now = Instant.now()
                if (proof?.expiresAt?.isAfter(now) == false) { val expired = proof; proof = null; latestProof(expired); status = "Phone proof expired. Verify again or continue with email only." }
                if (challenge?.expiresAt?.isAfter(now) == false) { challenge = null; code = ""; status = "Code expired. Request a new code." }
                if (retryAt?.isAfter(now) != true && challenge == null && proof == null) break
                delay(1_000)
            }
        }
        fun failure(error: Exception) {
            status = error.message ?: "Verification failed. Continue with email only or try again."
            (error as? PhoneVerificationFailure)?.retryAfterSeconds?.let { retryAt = Instant.now().plusSeconds(it.toLong()) }
        }
        Column(Modifier.fillMaxWidth()) {
            Text("Phone verification is optional. Email-only registration stays available.")
            TextButton(enabled = enabled && number != null && !busy && proof == null && retryAt?.isAfter(now) != true,
                onClick = {
                    val target = number ?: return@TextButton
                    if (busy || api.authSnapshot() !== owner) return@TextButton
                    busy = true; status = null; code = ""
                    scope.launch {
                        try {
                            val result = api.requestPhoneVerification(target, tenant, useDefault, owner)
                            if (api.authSnapshot() === owner) { challenge = result; retryAt = result.resendAt; status = "Code requested. Provider acceptance does not confirm delivery." }
                        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                        catch (error: Exception) { failure(error) }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "Working..." else if (retryAt?.isAfter(now) == true) "Resend available shortly" else if (challenge == null) "Verify number by SMS" else "Resend code") }
            challenge?.let { pending ->
                if (proof == null) {
                    OutlinedTextField(value = code, onValueChange = { code = it.filter { ch -> ch in '0'..'9' }.take(6) },
                        label = { Text("Six-digit SMS code") }, singleLine = true, enabled = enabled && !busy,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = enabled && !busy && code.length == 6, onClick = {
                        if (busy || api.authSnapshot() !== owner) return@TextButton
                        busy = true; status = null
                        val captured = code
                        scope.launch {
                            try {
                                val result = api.verifyPhoneNumber(pending, captured, owner)
                                if (api.authSnapshot() === owner) { proof = result; latestProof(result); code = ""; status = "Number verified for registration. Your account is not created yet." }
                            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                            catch (error: Exception) { failure(error) }
                            finally { busy = false }
                        }
                    }) { Text("Confirm code") }
                }
            }
            status?.let { Text(it) }
            if (proof != null) TextButton(onClick = { proof = null; challenge = null; code = ""; latestProof(null); status = "Phone will not be attached. Continue with email only." }, enabled = !busy) { Text("Use email only instead") }
            Text("SMS availability depends on the service. If SMS is unavailable, no code is sent and the number is not marked verified.")
        }
    }
}
