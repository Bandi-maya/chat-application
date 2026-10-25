package com.example.chat.ui.auth

import com.example.chat.core.validators.ChatyValidators

/** Pure form logic of lib/features/auth/create_new_password_screen.dart
 *  (api @ 3bee780d), split out so the behavior is JVM-testable.
 *
 *  Dart submit order (_handleResetPassword):
 *  1. form.validate() — password runs ChatyValidators.validatePassword, confirm
 *     runs the inline validator (empty -> 'Please confirm your password',
 *     mismatch -> 'Passwords do not match'). Any error aborts.
 *  2. Redundant top-level mismatch check -> 'Passwords do not match'
 *     (unreachable in practice: step 1's confirm validator catches mismatch
 *     first; kept for exactness).
 *  3. On failure of the auth call, error.toString() with the 'Exception: '
 *     prefix stripped is shown above the button.
 */
object CreateNewPasswordLogic {

    /** SnackBar text shown after a successful reset (Dart ScaffoldMessenger). */
    const val SUCCESS_MESSAGE = "Password updated. Sign in with your new password."

    /** Dart: thrown when the recovery session is gone before updateUser. */
    const val NO_SESSION_MESSAGE = "Open the newest password-recovery email link first."

    /** Inline Dart confirm-field validator. */
    fun confirmPasswordError(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Please confirm your password"
        confirm != password -> "Passwords do not match"
        else -> null
    }

    data class FormErrors(
        val passwordError: String?,
        val confirmError: String?,
        val topLevelError: String?,
    ) {
        val isValid: Boolean
            get() = passwordError == null && confirmError == null && topLevelError == null
    }

    /** Dart step 1 + 2: both field validators, then the top-level mismatch
     *  guard. Validators run even when the password field already failed —
     *  Flutter's form.validate() evaluates every field, so both error texts
     *  become visible in one pass. */
    fun validateForm(password: String, confirm: String): FormErrors {
        val passwordError = ChatyValidators.validatePassword(password)
        val confirmError = confirmPasswordError(password, confirm)
        val topLevelError =
            if (passwordError == null && confirmError == null && password != confirm) {
                "Passwords do not match"
            } else {
                null
            }
        return FormErrors(passwordError, confirmError, topLevelError)
    }

    /** Dart catch block: error.toString().replaceFirst('Exception: ', '').
     *  Kotlin exceptions carry the message separately, so mirror the sibling
     *  screens: message (fallback toString) with the prefix stripped. */
    fun displayError(e: Exception): String =
        (e.message ?: e.toString()).replaceFirst("Exception: ", "")
}
