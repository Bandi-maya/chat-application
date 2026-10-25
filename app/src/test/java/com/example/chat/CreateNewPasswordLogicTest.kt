package com.example.chat

import com.example.chat.ui.auth.CreateNewPasswordLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parity tests for CreateNewPasswordLogic against
 *  lib/features/auth/create_new_password_screen.dart (api @ 3bee780d),
 *  exercising the real ChatyValidators port for the password field. */
class CreateNewPasswordLogicTest {

    private val strong = "Str0ng!Passw0rd"

    @Test
    fun `valid strong password with matching confirm passes`() {
        val errors = CreateNewPasswordLogic.validateForm(strong, strong)
        assertNull(errors.passwordError)
        assertNull(errors.confirmError)
        assertNull(errors.topLevelError)
        assertTrue(errors.isValid)
    }

    @Test
    fun `empty password fails with Dart validator text`() {
        val errors = CreateNewPasswordLogic.validateForm("", "")
        assertEquals("Password is required", errors.passwordError)
        // Flutter form.validate() evaluates every field in the same pass.
        assertEquals("Please confirm your password", errors.confirmError)
        assertFalse(errors.isValid)
    }

    @Test
    fun `password rules match Dart validator messages`() {
        assertEquals(
            "Password must be at least 12 characters",
            CreateNewPasswordLogic.validateForm("Aa1!aaaaaaa", "Aa1!aaaaaaa").passwordError,
        )
        assertEquals(
            "Password must include an uppercase letter",
            CreateNewPasswordLogic.validateForm("aa1!aaaaaaaa", "aa1!aaaaaaaa").passwordError,
        )
        assertEquals(
            "Password must include a lowercase letter",
            CreateNewPasswordLogic.validateForm("AA1!AAAAAAAA", "AA1!AAAAAAAA").passwordError,
        )
        assertEquals(
            "Password must include a number",
            CreateNewPasswordLogic.validateForm("Aaa!aaaaaaaa", "Aaa!aaaaaaaa").passwordError,
        )
        assertEquals(
            "Password must include a symbol",
            CreateNewPasswordLogic.validateForm("Aa12aaaaaaaa", "Aa12aaaaaaaa").passwordError,
        )
    }

    @Test
    fun `empty confirm fails with Dart inline validator text`() {
        val errors = CreateNewPasswordLogic.validateForm(strong, "")
        assertNull(errors.passwordError)
        assertEquals("Please confirm your password", errors.confirmError)
        assertFalse(errors.isValid)
    }

    @Test
    fun `mismatched confirm fails on the field, not the top level`() {
        val errors = CreateNewPasswordLogic.validateForm(strong, "Different1!aaaa")
        assertNull(errors.passwordError)
        assertEquals("Passwords do not match", errors.confirmError)
        // The Dart top-level mismatch guard is unreachable once the confirm
        // validator has reported the mismatch.
        assertNull(errors.topLevelError)
        assertFalse(errors.isValid)
    }

    @Test
    fun `error display strips the Exception prefix like Dart`() {
        assertEquals(
            "Open the newest password-recovery email link first.",
            CreateNewPasswordLogic.displayError(
                Exception("Open the newest password-recovery email link first."),
            ),
        )
        assertEquals("boom", CreateNewPasswordLogic.displayError(Exception("boom")))
    }

    @Test
    fun `user-facing constants match the Dart source verbatim`() {
        assertEquals(
            "Password updated. Sign in with your new password.",
            CreateNewPasswordLogic.SUCCESS_MESSAGE,
        )
        assertEquals(
            "Open the newest password-recovery email link first.",
            CreateNewPasswordLogic.NO_SESSION_MESSAGE,
        )
    }
}
