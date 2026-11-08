package com.example.chat.ui.settings.privacy

/** Mappings between the server's privacy enums (research 5.2 / 5.3) and the
 *  WhatsApp-shaped labels the privacy center shows. Unknown server values
 *  map to null so callers fail closed instead of showing a wrong policy. */
object PrivacyOptionsLogic {

    /** everyone | contacts | contacts_except | nobody -> label. */
    fun audienceLabel(policy: String?): String? = when (policy) {
        "everyone" -> "Everyone"
        "contacts" -> "My Contacts"
        "contacts_except" -> "My Contacts Except…"
        "nobody" -> "Nobody"
        else -> null
    }

    /** Label -> server enum for the four-option audiences. */
    fun audiencePolicy(label: String): String? = when (label) {
        "Everyone" -> "everyone"
        "My Contacts" -> "contacts"
        "My Contacts Except…" -> "contacts_except"
        "Nobody" -> "nobody"
        else -> null
    }

    val audienceLabels: List<String> = listOf("Everyone", "My Contacts", "My Contacts Except…", "Nobody")

    /** online_policy: everyone | same_as_last_seen (report 5.2). */
    fun onlineLabel(policy: String?): String? = when (policy) {
        "everyone" -> "Everyone"
        "same_as_last_seen" -> "Same as last seen"
        else -> null
    }

    fun onlinePolicy(label: String): String? = when (label) {
        "Everyone" -> "everyone"
        "Same as last seen" -> "same_as_last_seen"
        else -> null
    }

    val onlineLabels: List<String> = listOf("Everyone", "Same as last seen")
}
