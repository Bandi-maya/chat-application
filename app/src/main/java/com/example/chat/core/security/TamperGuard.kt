package com.example.chat.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.example.chat.BuildConfig
import java.io.File
import java.security.MessageDigest

/**
 * Tamper detection for release builds (own implementation, patch 26).
 *
 * Collects independent signals - signing-certificate hash, attached debugger,
 * emulator heuristics, root/hook artifacts, installer source - and folds them
 * into one [Verdict]. The guard is deliberately report-only unless the build
 * was configured with -PCHATY_TAMPER_ENFORCE=true; the certificate check is
 * skipped unless -PCHATY_CERT_SHA256=<release hash> was provided, since the
 * real hash lives with the keystore owner (CI/vault), never in the repo.
 *
 * Fail-open by contract: every probe that throws yields a neutral signal, so
 * a broken device API can never lock a legitimate user out. Only a positive
 * mismatch/artifact can flag tampering.
 */
object TamperGuard {

    enum class SignatureState { UNCONFIGURED, UNAVAILABLE, MATCH, MISMATCH }

    /** Raw signals. All fields are plain values so [decide] stays JVM-testable. */
    data class Signals(
        val signature: SignatureState,
        val debuggerAttached: Boolean,
        val installerPackage: String?,
        val emulatorHits: List<String>,
        val hookArtifacts: List<String>,
    )

    data class Verdict(
        val signals: Signals,
        val tampered: Boolean,
        val enforced: Boolean,
    ) {
        fun summary(): String = buildString {
            append("tampered=").append(tampered)
            append(" enforced=").append(enforced)
            append(" signature=").append(signals.signature)
            append(" debugger=").append(signals.debuggerAttached)
            append(" installer=").append(signals.installerPackage ?: "unknown")
            if (signals.emulatorHits.isNotEmpty()) {
                append(" emulatorHits=").append(signals.emulatorHits.joinToString(",", "[", "]"))
            }
            if (signals.hookArtifacts.isNotEmpty()) {
                append(" hookArtifacts=").append(signals.hookArtifacts.joinToString(",", "[", "]"))
            }
        }
    }

    // ---- pure decision logic (no Android calls; unit-tested on the JVM) ----

    /** Normalizes a hex hash for comparison: strips separators and case. */
    fun normalizeHex(value: String): String =
        value.filter { it.isLetterOrDigit() }.lowercase()

    fun signatureState(expectedSha256: String, actualSha256: String?): SignatureState {
        val expected = normalizeHex(expectedSha256)
        if (!Regex("^[0-9a-f]{64}$").matches(expected)) return SignatureState.UNCONFIGURED
        val actual = actualSha256?.let(::normalizeHex).orEmpty()
        if (actual.isEmpty()) return SignatureState.UNAVAILABLE
        return if (actual == expected) SignatureState.MATCH else SignatureState.MISMATCH
    }

    /** Only an observed signer mismatch supports a modified-APK verdict.
     *  Root, emulator and debugger observations remain advisory diagnostics.
     *  Installer source is informational; sideloading is legitimate. */
    fun decide(signals: Signals, enforceEnabled: Boolean): Verdict {
        // Other observations are diagnostics, not proof the APK was modified.
        val tampered = signals.signature == SignatureState.MISMATCH
        return Verdict(signals, tampered, enforceEnabled && tampered)
    }

    // ---- Android signal collection ----

    fun evaluate(context: Context): Verdict {
        val signals = Signals(
            signature = signatureState(BuildConfig.CHATY_CERT_SHA256, currentSignatureSha256(context)),
            debuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger(),
            installerPackage = installerPackage(context),
            emulatorHits = emulatorHits(),
            hookArtifacts = hookArtifacts(context),
        )
        // Root/emulator/debugger observations are not proof the APK was modified.
        // Keep diagnostic probes separate from any future reviewed lockout policy.
        return decide(signals, com.example.chat.ReleaseFeatures.ENFORCE_TAMPER_GUARD &&
            !BuildConfig.DEBUG && BuildConfig.CHATY_TAMPER_ENFORCE &&
            signals.signature == SignatureState.MISMATCH)
    }

    private fun currentSignatureSha256(context: Context): String? {
        val pm = context.packageManager
        val certBytes = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                @Suppress("DEPRECATION")
                val signing = info.signingInfo ?: return null
                @Suppress("DEPRECATION")
                (signing.apkContentsSigners ?: signing.signingCertificateHistory)
                    ?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                    .signatures?.firstOrNull()?.toByteArray()
            }
        } catch (e: Exception) {
            null
        } ?: return null
        return sha256Hex(certBytes)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun installerPackage(context: Context): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    } catch (e: Exception) {
        null
    }

    private fun emulatorHits(): List<String> {
        val hits = mutableListOf<String>()
        fun check(field: String, value: String?, vararg needles: String) {
            if (value != null && needles.any { value.contains(it, ignoreCase = true) }) {
                hits += "$field=$value"
            }
        }
        check("fingerprint", Build.FINGERPRINT, "generic", "emulator", "vbox")
        check("model", Build.MODEL, "emulator", "android sdk built for", "genymotion")
        check("manufacturer", Build.MANUFACTURER, "genymotion")
        check("hardware", Build.HARDWARE, "goldfish", "ranchu", "vbox86")
        check("product", Build.PRODUCT, "sdk", "google_sdk", "sdk_x86", "vbox86p", "emulator")
        check("brand+device", Build.BRAND + Build.DEVICE, "generic")
        return hits
    }

    private val ROOT_PATHS = listOf(
        "/system/app/Superuser.apk",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/xbin/daemonsu",
        "/system/etc/init.d/99SuperSUDaemon",
        "/dev/com.koushikdutta.superuser.daemon/",
        "/system/bin/.ext/.su",
        "/data/local/tmp/frida-server",
        "/data/local/tmp/re.frida.server",
    )

    private val HOOK_PACKAGES = listOf(
        "de.robv.android.xposed.installer",
        "com.saurik.substrate",
        "com.topjohnwu.magisk",
        "com.zachspong.temprootremovejb",
        "com.amphoras.hidemyroot",
        "com.formyhm.hideroot",
    )

    private fun hookArtifacts(context: Context): List<String> {
        val found = mutableListOf<String>()
        for (path in ROOT_PATHS) {
            try {
                if (File(path).exists()) found += path
            } catch (e: Exception) {
                // Unreadable path is not evidence; fail open.
            }
        }
        val pm = context.packageManager
        for (pkg in HOOK_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                found += pkg
            } catch (e: PackageManager.NameNotFoundException) {
                // Absent: the common case.
            } catch (e: Exception) {
                // Fail open.
            }
        }
        return found
    }
}
