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
    const val CURRENT_REVISION = "reference-layout-media-source-2026-11-03"

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
            "Refined Header options and shared Home navigation layouts using the supplied references, while preserving saved layout choices.",
            "Grouped image selection keeps its compact layout and selects the complete validated album; forwarding uses a centered chooser.",
            "Single-tap More options and long-press selection menus remain separate.",
            "Added bounded local voice waveform decoding and image text overlays; edited and trimmed copies use existing upload cleanup.",
            "Sticker display uses an animated WebP decoder on supported Android versions, with a reduced-motion static path.",
            "Updated Android bytecode tooling to address the conversation-screen verification crash.",
            "Message reloads preserve supported media album and protection metadata. Unsupported metadata keeps media grouping restricted.",
            "The gallery review action now shows the selected item count.",
            "Default and ONE UI Home layouts now use the swapped presentation. Header settings preview the actual Home layout without changing it.",
            "Call media cleanup now keeps track ownership consistent, including rollback after partial setup failure.",
            "Added separate supplied emoji choices: 50 iOS animations, 19 Telegram-style source variants and 11 TheSub Lottie animations. Pack coverage is partial; the yawning face is an enlarged GIF.",
            "Tap opens more message options while long-press keeps message selection. Local-only text modifications do not change the sender's message; copy, share and quote images use the original text.",
            "Whole original text-message quote images can be previewed and shared explicitly, with the sender photo or initials. Added copy selection, starred-message navigation and the default double-tap emoji choice; protected and revoked messages remain restricted.",
            "Reactions use existing local-first storage with best-effort backend sync. Cross-device delivery depends on backend support. Offline translation and read-notification actions are not included in this build.",
            "Added a separate Drive MOV animation pack covering all 132 files in the supplied folder, including gendered shrug, facepalm and no gestures. The first 50 source designs overlap the separate iOS ZIP. This is source-folder coverage, not every Unicode emoji; missing artwork uses normal glyphs.",
            "This changelog keeps the versions and changes recorded on this device. Reinstalling the same version and notes does not reopen it.",
        ),
    )
}
