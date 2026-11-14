package com.example.chat.data.api

/** Contact claim only, never ownership proof. No implicit country or SIM trust. */
object RegistrationPhonePolicy {
    fun normalize(value: String): String? {
        if (value.length !in 8..32) return null
        val number = value.replace(Regex("[ ()-]"), "")
        return number.takeIf { Regex("\\+[1-9][0-9]{7,14}").matches(it) }
    }
}
