import com.example.chat.ui.settings.security.*
import kotlinx.coroutines.runBlocking

class FakeLock(var has: Boolean = false, var secret: String = "") : LockSetupService {
    var saved: Triple<String, String, Int?>? = null
    var bio = true
    var failSave: String? = null
    override suspend fun hasCredential(method: String) = has
    override suspend fun verifyCredential(method: String, secret: String) = secret == this.secret
    override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {
        failSave?.let { throw IllegalArgumentException(it) }
        saved = Triple(method, secret, pinLength)
    }
    override suspend fun authenticateBiometric(reason: String) = bio
    override suspend fun authenticateDeviceCredential(reason: String) = bio
}

fun main() = runBlocking {
    // PIN: new flow
    val l = FakeLock(); val f = LockCredentialSetupFlow("PIN", 4, l); f.init()
    check(f.step == SetupStep.enterNew && f.stepTitle() == "Create PIN" && f.stepSubtitle() == "Choose a 4-digit PIN code.")
    check(f.handleContinue("12") == SetupOutcome.none && f.error == "Enter all 4 digits.")
    f.handleContinue("0000"); check(f.error == "Weak PIN. Avoid repeating digits like 0000.")
    check(f.handleContinue("1357") == SetupOutcome.clearPin && f.step == SetupStep.confirmNew)
    check(f.handleContinue("1111") == SetupOutcome.clearPin && f.error == "PINs do not match. Try entering again.")
    check(f.handleContinue("1357") == SetupOutcome.saved && l.saved == Triple("PIN", "1357", 4))
    // PIN: existing credential requires current
    val l2 = FakeLock(true, "2468"); val f2 = LockCredentialSetupFlow("PIN", 4, l2); f2.init()
    check(f2.step == SetupStep.enterCurrent && f2.stepTitle() == "Enter Current PIN")
    f2.handleContinue("0000"); check(f2.error == "Current PIN is incorrect.")
    check(f2.handleContinue("2468") == SetupOutcome.clearPin && f2.step == SetupStep.enterNew && f2.stepTitle() == "Enter New PIN")
    // replaceExisting skips current
    val f3 = LockCredentialSetupFlow("PIN", 4, l2, replaceExisting = true); f3.init(); check(f3.step == SetupStep.enterNew && !f3.hasExistingSecret)
    // Password
    val l4 = FakeLock(); val p = LockCredentialSetupFlow("Password", 4, l4); p.init()
    p.handleContinue("   "); check(p.error == "Please enter a password.")
    p.handleContinue("abc"); check(p.error == "Password must contain at least 6 characters.")
    check(p.handleContinue(" secret1 ") == SetupOutcome.clearText && p.step == SetupStep.confirmNew)
    p.handleContinue("secret2"); check(p.error == "Passwords do not match. Try again.")
    check(p.handleContinue("secret1") == SetupOutcome.saved && l4.saved == Triple("Password", "secret1", null))
    // save failure message cleanup
    val l5 = FakeLock().apply { failSave = "Invalid argument(s): PIN too short" }; val f5 = LockCredentialSetupFlow("PIN", 4, l5); f5.init()
    f5.handleContinue("1357"); f5.handleContinue("1357"); check(f5.error == "PIN too short" && !f5.busy)
    // Pattern
    val l6 = FakeLock(); val pt = LockCredentialSetupFlow("Pattern", 4, l6); pt.init()
    check(!pt.canContinuePattern); pt.handlePatternContinue(); check(pt.error == "Draw your pattern first.")
    pt.onPatternComplete("0-1"); check(pt.error == "Connect at least 4 dots.")
    pt.onPatternComplete("0-1-2-5"); check(pt.canContinuePattern && pt.error == "")
    check(pt.handlePatternContinue() == SetupOutcome.resetPad && pt.step == SetupStep.confirmNew && pt.firstPatternDrawn == null)
    pt.handlePatternContinue(); check(pt.error == "Draw the confirmation pattern first.")
    pt.onPatternComplete("0-1-2-4"); check(pt.handlePatternContinue() == SetupOutcome.resetPad && pt.error == "Patterns do not match. Draw again to confirm.")
    pt.onPatternComplete("0-1-2-5"); check(pt.handlePatternContinue() == SetupOutcome.saved && l6.saved == Triple("Pattern", "0-1-2-5", null))
    // Native
    val l7 = FakeLock().apply { bio = false }; val b = LockCredentialSetupFlow("Biometric", 4, l7); b.init()
    check(b.verifyNativeMethod() == SetupOutcome.none && b.error == "Biometric verification was cancelled or unavailable." && !b.busy)
    l7.bio = true; check(b.verifyNativeMethod() == SetupOutcome.saved)
    val d = LockCredentialSetupFlow("Device Credential", 4, FakeLock().apply { bio = false }); d.init(); d.verifyNativeMethod()
    check(d.error == "Device credential verification was cancelled or unavailable.")
    println("OK")
}
