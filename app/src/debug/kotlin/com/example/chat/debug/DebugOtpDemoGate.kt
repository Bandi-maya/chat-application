package com.example.chat.debug

/** DEBUG-BUILD-ONLY verification navigation shortcut. The user's explicit
 *  13:51 policy permits code 000000 for any email during non-production UI
 *  testing. This supersedes the earlier offline-demo-only preview restriction.
 *  Main code additionally checks BuildConfig.DEBUG and invokes reflectively,
 *  so release builds have neither the accepting source nor a working bypass.
 *  This only calls the existing onVerified callback. It issues no token,
 *  changes no server email verification flag and does not bypass password reset. */
object DebugOtpDemoGate {

    /** Debug UI-navigation code, not a server authentication credential. */
    const val OFFLINE_DEMO_CODE = "000000"

    /** Called reflectively by com.example.chat.ui.auth.DebugOtpGate. */
    @JvmStatic
    fun accepts(code: String): Boolean = code == OFFLINE_DEMO_CODE
}
