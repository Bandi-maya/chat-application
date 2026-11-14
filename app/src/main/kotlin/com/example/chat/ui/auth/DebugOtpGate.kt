package com.example.chat.ui.auth

/** Debug verification-navigation shortcut. Explicit user policy13:51 permits
 *  the debug code for any email in a debug build. The accepting implementation
 *  exists only in src/debug; release always uses server verification.
 *  Acceptance calls the existing onVerified navigation callback only: no
 *  token issuance, server email-verified update, or password-reset bypass. */
internal object DebugOtpGate {

    private const val DEBUG_GATE_CLASS = "com.example.chat.debug.DebugOtpDemoGate"
    private const val DEBUG_GATE_METHOD = "accepts"

    fun acceptsDebugCode(code: String): Boolean {
        if (!com.example.chat.BuildConfig.DEBUG) return false
        return runCatching {
            val type = Class.forName(DEBUG_GATE_CLASS)
            val method = type.getDeclaredMethod(DEBUG_GATE_METHOD, String::class.java)
            (method.invoke(null, code) as? Boolean) == true
        }.getOrDefault(false)
    }
}
