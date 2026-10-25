package com.example.chat.core.profile

/**
 * Context-free port of the display-name fallback chain in
 * lib/features/profile/profile_screen.dart (api @ 3bee780d), used by the
 * Profile root header:
 *
 *   final override = preferencesController.home.myNameOverride.trim();
 *   final effectiveOverride = (override.isNotEmpty && override != 'Alex Rivera' && override != 'Bandi Maya') ? override : '';
 *   final rawName = user.displayName.trim();
 *   final displayName = effectiveOverride.isNotEmpty
 *       ? effectiveOverride
 *       : (rawName.isNotEmpty && rawName != 'Bandi Maya' && rawName != 'Chaty User'
 *           ? rawName
 *           : (user.username.isNotEmpty ? user.username : 'User'));
 */
object ProfileDisplayName {
    fun effectiveDisplayName(
        myNameOverride: String,
        displayName: String,
        username: String,
    ): String {
        val override = myNameOverride.trim()
        val effectiveOverride =
            if (override.isNotEmpty() && override != "Alex Rivera" && override != "Bandi Maya") override else ""
        val rawName = displayName.trim()
        return if (effectiveOverride.isNotEmpty()) {
            effectiveOverride
        } else if (rawName.isNotEmpty() && rawName != "Bandi Maya" && rawName != "Chaty User") {
            rawName
        } else {
            username.ifEmpty { "User" }
        }
    }
}
