package com.example.chat.ui.updates

/** A notes revision identifies changed notes even while development APKs share a version. */
data class ReleaseHistoryEntry(
    val versionName: String,
    val versionCode: Long,
    val revision: String,
    val changes: List<String>,
) {
    val identity: String get() = ReleaseNotesPolicy.identity(versionName, versionCode, revision)
}

object ReleaseNotesPolicy {
    const val CURRENT_REVISION = "message-actions-emoji-2026-11-03"

    fun identity(versionName: String, versionCode: Long, revision: String): String =
        "${versionName.length}:$versionName:$versionCode:${revision.length}:$revision"

    fun shouldShow(lastSeenIdentity: String?, current: ReleaseHistoryEntry): Boolean =
        lastSeenIdentity != current.identity

    /** Keep every observed release. Never invent versions that were not recorded. */
    fun history(current: ReleaseHistoryEntry, stored: List<ReleaseHistoryEntry>): List<ReleaseHistoryEntry> =
        (listOf(current) + stored).distinctBy { it.identity }

    fun current(versionName: String, versionCode: Long) = ReleaseHistoryEntry(
        versionName.ifBlank { "Current build" }, versionCode, CURRENT_REVISION,
        listOf(
            "Updated Android bytecode tooling to address the conversation-screen verification crash.",
            "Message reloads preserve supported media album and protection metadata. Unsupported metadata keeps media grouping restricted.",
            "The gallery review action now shows the selected item count.",
            "Default and ONE UI Home layouts now use the swapped presentation. Header settings preview the actual Home layout without changing it.",
            "Call media cleanup now keeps track ownership consistent, including rollback after partial setup failure.",
            "Added separate supplied emoji choices: 50 iOS animations, 19 Telegram-style source variants and 11 TheSub Lottie animations. Pack coverage is partial; the yawning face is an enlarged GIF.",
            "Tap opens more message options while long-press keeps message selection. Local-only text modifications do not change the sender's message; copy, share and quote images use the original text.",
            "Whole original text-message quote images can be previewed and shared explicitly, with the sender photo or initials. Added copy selection, starred-message navigation and the default double-tap emoji choice; protected and revoked messages remain restricted.",
            "Reactions use existing local-first storage with best-effort backend sync. Cross-device delivery depends on backend support. Offline translation and read-notification actions are not included in this build.",
            "This changelog keeps the versions and changes recorded on this device. Reinstalling the same version and notes does not reopen it.",
        ),
    )
}
