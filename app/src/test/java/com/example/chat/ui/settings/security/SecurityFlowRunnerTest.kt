import com.example.chat.ui.settings.security.*
import kotlinx.coroutines.runBlocking

class FakeHost(
    var method: String = "PIN", var enabled: Boolean = false,
    val creds: MutableSet<String> = mutableSetOf(),
    var verify: Boolean = true, var pick: LockMethodType? = null,
    var setup: Boolean = true, var bio: Boolean = true, var dev: Boolean = true,
) : SecurityFlowHost {
    val log = mutableListOf<String>()
    override fun currentLockMethod() = method
    override fun isAppLockEnabled() = enabled
    override suspend fun hasCredential(storageKey: String) = storageKey in creds
    override suspend fun authenticateBiometric(reason: String): Boolean { log += "bio:$reason"; return bio }
    override suspend fun authenticateDeviceCredential(reason: String): Boolean { log += "dev:$reason"; return dev }
    override suspend fun showVerifyOverlay(): Boolean { log += "overlay"; return verify }
    override suspend fun pickMethod(current: LockMethodType): LockMethodType? { log += "pick"; return pick }
    override suspend fun setupCredential(storageKey: String, pinLength: Int): Boolean { log += "setup:$storageKey:$pinLength"; return setup }
    override suspend fun reloadCapabilities() { log += "reload" }
    override fun toast(message: String) { log += "toast:$message" }
    override fun isActive() = true
    override fun applySecurity(lockMethod: String?, appLockEnabled: Boolean?, logTitle: String) { log += "apply:$lockMethod:$appLockEnabled:$logTitle" }
}

fun check(c: Boolean, m: String) { if (!c) throw AssertionError(m) }

fun main() = runBlocking {
    // enable with PIN: pick -> setup -> apply, no verification (lock disabled)
    var h = FakeHost(pick = LockMethodType.pin)
    check(SecurityFlowRunner.run(h, SecurityIntent.enableLock), "enable")
    check(h.log == listOf("pick", "setup:PIN:4", "reload", "apply:PIN:true:Enable Chaty Lock (PIN)"), h.log.toString())

    // enable biometric: preflight, no setup
    h = FakeHost(pick = LockMethodType.biometric)
    check(SecurityFlowRunner.run(h, SecurityIntent.enableLock), "enable bio")
    check(h.log == listOf("pick", "bio:Confirm biometric unlock works for Chaty", "apply:Biometric:true:Enable Chaty Lock (Biometric)"), h.log.toString())

    // biometric preflight failure
    h = FakeHost(pick = LockMethodType.biometric, bio = false)
    check(!SecurityFlowRunner.run(h, SecurityIntent.enableLock), "bio fail")
    check(h.log.last().startsWith("toast:Biometric unavailable"), h.log.toString())
    check(h.log.none { it.startsWith("apply") }, "no apply")

    // disable with PIN set: verify overlay then apply
    h = FakeHost(method = "PIN", enabled = true, creds = mutableSetOf("PIN"))
    check(SecurityFlowRunner.run(h, SecurityIntent.disableLock), "disable")
    check(h.log == listOf("overlay", "apply:null:false:Disable App Lock"), h.log.toString())

    // disable, verification cancelled
    h = FakeHost(method = "PIN", enabled = true, creds = mutableSetOf("PIN"), verify = false)
    check(!SecurityFlowRunner.run(h, SecurityIntent.disableLock), "disable cancelled")
    check(h.log == listOf("overlay", "toast:Verification cancelled."), h.log.toString())

    // PIN set enabled but no stored credential: no proof required
    h = FakeHost(method = "PIN", enabled = true)
    check(SecurityFlowRunner.run(h, SecurityIntent.disableLock), "disable nocred")
    check(h.log == listOf("apply:null:false:Disable App Lock"), h.log.toString())

    // change method to pattern: verify -> pick -> setup -> apply
    h = FakeHost(method = "PIN", enabled = true, creds = mutableSetOf("PIN"), pick = LockMethodType.pattern)
    check(SecurityFlowRunner.run(h, SecurityIntent.changeMethod), "change method")
    check(h.log == listOf("overlay", "pick", "setup:Pattern:4", "reload", "apply:Pattern:true:Lock Method"), h.log.toString())

    // change credential same method, 6 digit
    h = FakeHost(method = "PIN", enabled = true, creds = mutableSetOf("PIN"))
    check(SecurityFlowRunner.run(h, SecurityIntent.changeCredential, LockMethodType.pin, 6), "change cred")
    check(h.log == listOf("overlay", "setup:PIN:6", "reload"), h.log.toString())

    // change method on OS-based current: authenticates via device credential
    h = FakeHost(method = "Device Credential", enabled = true, pick = LockMethodType.biometric)
    check(SecurityFlowRunner.run(h, SecurityIntent.changeMethod), "os change")
    check(h.log.first() == "dev:Confirm it is you to continue", h.log.toString())

    // unknown stored method falls back to PIN
    check(SecurityFlowRunner.methodTypeOf("???") == LockMethodType.pin, "fallback")
    println("SecurityFlowRunnerTest OK")
}
