package com.example.chat.ui.auth

/** Pure form logic of the email OTP verification screen, split out so the
 *  behavior is JVM-testable (same pattern as CreateNewPasswordLogic).
 *
 *  Choices follow the auth-flow research report:
 *  - 6 digits, shown as typed (no masking), numeric keyboard, paste accepted.
 *  - Destination shown masked (b****@gmail.com) so the user can spot a typo
 *    without the full address on screen.
 *  - Wrong/expired codes surface the server's own detail text inline
 *    ("Incorrect code. N attempts remaining."); the client never invents
 *    attempt counts.
 */
object OtpVerificationLogic {

    const val CODE_LENGTH = 6

    /** Resend countdown seeded when the screen opens (a code was just sent)
     *  and after every successful resend. The backend's escalating cooldown
     *  (60s, 2min, 5min) still applies server-side; a premature request gets
     *  the server's 429 message inline. */
    const val RESEND_COUNTDOWN_SECONDS = 60

    /** Digits only, capped at CODE_LENGTH — paste of "123 456" or a longer
     *  string still lands as a usable code. */
    fun sanitizeCode(raw: String): String = raw.filter { it.isDigit() }.take(CODE_LENGTH)

    fun isComplete(code: String): Boolean =
        code.length == CODE_LENGTH && code.all { it.isDigit() }

    /** b****@gmail.com-style masking for the destination line. Keeps the first
     *  local-part character and the full domain; anything without a usable
     *  local part passes through unchanged. */
    fun maskEmail(email: String): String {
        val at = email.lastIndexOf('@')
        if (at <= 0) return email
        return email.substring(0, 1) + "****" + email.substring(at)
    }

    fun displayError(e: Exception): String =
        (e.message ?: e.toString()).replaceFirst("Exception: ", "")
}

/** Why the OTP screen is on screen: verifying a fresh registration's email, or
 *  confirming ownership before a password reset. */
enum class OtpPurpose { VerifyEmail, ResetPassword, SignIn }
