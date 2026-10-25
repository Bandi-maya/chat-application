package com.example.chat.core.validators

/**
 * Port of lib/ui/core/validators/input_validators.dart (api @ 3bee780d).
 * Error strings match the Flutter source exactly.
 */
object ChatyValidators {
    const val MIN_PASSWORD_LENGTH = 12
    const val MAX_PASSWORD_LENGTH = 128

    val reservedUsernames = setOf(
        "admin", "administrator", "root", "support", "security", "chaty",
        "official", "moderator", "system", "help", "bot", "service",
        "guest", "null", "undefined", "anonymous", "channel", "community",
        "group", "saved", "updates",
    )

    fun normalizeUsername(username: String): String =
        username.trim().lowercase().replace(Regex("^@+"), "")

    fun validateUsername(value: String?, forRegistration: Boolean = true): String? {
        if (value == null || value.trim().isEmpty()) return "Username is required"
        val normalized = normalizeUsername(value)
        if (normalized.length < 3) return "Username must be at least 3 characters"
        if (normalized.length > 24) return "Username must not exceed 24 characters"
        if (!Regex("^[a-z0-9_]+$").matches(normalized)) {
            return "Only letters, numbers, and underscores are allowed"
        }
        if (forRegistration && reservedUsernames.contains(normalized)) {
            return "This username is reserved and cannot be registered"
        }
        return null
    }

    fun validateEmail(value: String?): String? {
        if (value == null || value.trim().isEmpty()) return "Email address is required"
        val email = value.trim()
        val emailPattern = Regex(
            "^[a-zA-Z0-9.!#\$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+\$",
        )
        if (!emailPattern.matches(email)) return "Please enter a valid email address"
        return null
    }

    fun validatePassword(value: String?): String? {
        if (value == null || value.isEmpty()) return "Password is required"
        if (value.length < MIN_PASSWORD_LENGTH) {
            return "Password must be at least $MIN_PASSWORD_LENGTH characters"
        }
        if (value.length > MAX_PASSWORD_LENGTH) {
            return "Password cannot exceed $MAX_PASSWORD_LENGTH characters"
        }
        if (!Regex("[a-z]").containsMatchIn(value)) return "Password must include a lowercase letter"
        if (!Regex("[A-Z]").containsMatchIn(value)) return "Password must include an uppercase letter"
        if (!Regex("[0-9]").containsMatchIn(value)) return "Password must include a number"
        if (!Regex("[!@#\$%^&*()_+\\-=\\[\\]{};'\\\\:\"|<>?,./`~]").containsMatchIn(value)) {
            return "Password must include a symbol"
        }
        return null
    }

    fun validatePhone(value: String?): String? {
        if (value == null || value.trim().isEmpty()) return "Phone number is required"
        val cleaned = value.replace(Regex("[\\s\\-\\(\\)\\.]"), "")
        if (!Regex("^\\+?[0-9]{7,15}\$").matches(cleaned)) {
            return "Please enter a valid international phone number"
        }
        return null
    }

    fun validateDisplayName(value: String?): String? {
        if (value == null || value.trim().isEmpty()) return "Display name is required"
        if (value.trim().length > 50) return "Display name must not exceed 50 characters"
        return null
    }

    fun validateGroupTitle(value: String?): String? {
        if (value == null || value.trim().isEmpty()) return "Title is required"
        if (value.trim().length > 64) return "Title must not exceed 64 characters"
        return null
    }

    fun validateBio(value: String?): String? {
        if (value != null && value.length > 256) return "Bio must not exceed 256 characters"
        return null
    }
}
