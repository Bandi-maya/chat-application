package com.example.chat.ui.settings.templates

import com.example.chat.core.templates.ChatyNavigationDestinationIds

/** Pure text/logic helpers ported from component_override_screen.dart so they
 *  run under the JVM harness without Compose. */

/** Dart: error.toString().replaceFirst('Bad state: ', ''). Dart's StateError
 *  toString() carries the "Bad state: " prefix; Kotlin's
 *  IllegalStateException/IllegalArgumentException messages are already clean,
 *  so the prefix strip is kept only for parity safety. */
fun overrideErrorText(t: Throwable): String =
    (t.message ?: t.toString()).removePrefix("Bad state: ")

/** Dart: isFromBase ? 'Base Template' : 'Active Override' (only when used). */
fun overrideBadge(isCurrentlyUsed: Boolean, isFromBase: Boolean): String? = when {
    !isCurrentlyUsed -> null
    isFromBase -> "Base Template"
    else -> "Active Override"
}

fun overrideApplySuccess(templateName: String, componentTitle: String): String =
    "Applied $templateName to $componentTitle only."

fun overrideResetSuccess(componentTitle: String): String =
    "Reset $componentTitle to the base template."

/** Dart destinationLabel switch over ChatyNavigationDestinationIds. */
fun navigationDestinationLabel(id: String): String = when (id) {
    ChatyNavigationDestinationIds.chats -> "Chats"
    ChatyNavigationDestinationIds.groups -> "Groups"
    ChatyNavigationDestinationIds.updates -> "Updates"
    ChatyNavigationDestinationIds.tasks -> "Tasks"
    ChatyNavigationDestinationIds.calls -> "Calls"
    ChatyNavigationDestinationIds.settings -> "Settings"
    ChatyNavigationDestinationIds.desktop -> "Devices"
    else -> id
}

/** Dart: '${tmpl.navigation.layout.name} · ${tmpl.navigation.height.toInt()} dp'. */
fun navigationSummary(layoutName: String, height: Double): String =
    "$layoutName · ${height.toInt()} dp"

/** Dart: centerActionId == 'camera' ? 'Center camera action' : 'Center quick action'. */
fun centerActionLabel(centerActionId: String): String =
    if (centerActionId == "camera") "Center camera action" else "Center quick action"

/** Dart: 'Bubble: ${bubbleStyle} · Ticks: ${tickStyle}'. */
fun conversationSummary(bubbleStyle: String, tickStyle: String): String =
    "Bubble: $bubbleStyle · Ticks: $tickStyle"

/** Dart: '${density.name} · ${itemHeight.toInt()} dp rows'. */
fun chatListSummary(densityName: String, itemHeight: Double): String =
    "$densityName · ${itemHeight.toInt()} dp rows"

/** Dart: '${layoutMode.name} · audio ${enableStatusAudio ? "on" : "off"}'. */
fun updatesSummary(layoutName: String, statusAudioEnabled: Boolean): String =
    "$layoutName · audio ${if (statusAudioEnabled) "on" else "off"}"

/** Dart: enableFloatingIsland ? 'Island on' : 'Island off'. */
fun callsIslandLabel(floatingIslandEnabled: Boolean): String =
    if (floatingIslandEnabled) "Island on" else "Island off"

/** Dart chatList preview row vertical padding: compact 3, comfortable 8, else 5. */
fun chatListRowPaddingDp(densityName: String): Int = when (densityName) {
    "compact" -> 3
    "comfortable" -> 8
    else -> 5
}

/** Dart chatList avatar radius: 'squircle' 8, 'roundedSquare' 5, else 99. */
fun chatListAvatarRadiusDp(avatarShape: String): Int = when (avatarShape) {
    "squircle" -> 8
    "roundedSquare" -> 5
    else -> 99
}

/** Dart home preview row height: homeStylePreset == 'Compact' ? 20 : 27. */
fun homeRowHeightDp(homeStylePreset: String): Int = if (homeStylePreset == "Compact") 20 else 27

/** Dart profile banner height: compactHeader 26 else 44. */
fun profileBannerHeightDp(headerStyleName: String): Int =
    if (headerStyleName == "compactHeader") 26 else 44

/** Universal appearance overview chips: 'Type: X' / 'In: Y' / 'Out: Z'. */
fun appearanceOverviewChips(typography: String, entry: String, exit: String): List<String> =
    listOf("Type: $typography", "In: $entry", "Out: $exit")

/** Dart universal_appearance_screen save-failure snackbar text. */
const val APPEARANCE_SAVE_ERROR =
    "Could not save this appearance setting. Your previous choice was kept."
