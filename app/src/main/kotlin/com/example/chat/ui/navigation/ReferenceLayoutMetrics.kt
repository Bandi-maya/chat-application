package com.example.chat.ui.navigation

/** Shared geometry from the supplied Default references and inspected GB home resource.
 * Geometry does not own data, destination inventory, colors, or the persisted style ID.
 */
object ReferenceLayoutMetrics {
    const val actionBarHeightDp = 56
    const val compactHeaderHeightDp = 160
    const val compactIdentityHeightDp = compactHeaderHeightDp - actionBarHeightDp
    const val compactIdentityTextSizeSp = 26
    const val expandedHeaderHeightDp = 250
    const val expandedIdentityHeightDp = expandedHeaderHeightDp - actionBarHeightDp
    // GB's toolbar overlays the expanded container, not a second block below it.
    // In a split Column, offset the identity by half the overlaid toolbar height.
    const val identityCenterOffsetDp = actionBarHeightDp / 2
    const val identityIconSizeDp = 38
    const val identityTextSizeSp = 30
}
