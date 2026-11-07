package com.example.chat.ui.icons

/**
 * Bundled icon family availability and adaptation provenance.
 *
 * Fail-closed availability registry for icon themes. A theme is selectable only
 * when [PackInfo.available] is true; there is no partial or generic fallback art.
 *
 * Availability requires complete semantic mappings and genuine or explicitly adapted
 * bundled vectors. Runtime/visual acceptance is tracked separately from source coverage.
 */
object ChatyIconPackCatalog {

    class PackInfo(
        val theme: ChatyIconThemeId?,
        val title: String,
        val available: Boolean,
        val reason: String?,
        val missingIcons: List<String> = emptyList(),
    )

    private val assetPacks: Map<ChatyIconThemeId, PackInfo> = mapOf(
        ChatyIconThemeId.hugeiconsFree to PackInfo(
            ChatyIconThemeId.hugeiconsFree, "Hugeicons Free",
            available = true,
            reason = "Includes adapted status-ring center dot, forward, fullscreen and filled-heart icons, plus a scheduled-send composition. Also includes original supplemental action/selection/crop art; see original-supplemental-provenance.json. Other artwork is genuine Hugeicons Free. MIT; not Hugeicons Pro.",
            missingIcons = emptyList(),
        ),
        ChatyIconThemeId.majesticonsLine to PackInfo(
            ChatyIconThemeId.majesticonsLine, "Majesticons Line", available = true,
            reason = "Includes adapted family-style additions for missing actions, status ring and upright earpiece. Includes original supplemental action/selection/crop art; genuine family artwork is retained where available. MIT.",
            missingIcons = emptyList(),
        ),
        ChatyIconThemeId.majesticonsSolid to PackInfo(
            ChatyIconThemeId.majesticonsSolid, "Majesticons Solid", available = true,
            reason = "Includes adapted family-style additions for missing actions, status ring and upright earpiece. Includes original supplemental action/selection/crop art; genuine family artwork is retained where available. MIT.",
            missingIcons = emptyList(),
        ),
        ChatyIconThemeId.pikaiconsStroke to PackInfo(
            ChatyIconThemeId.pikaiconsStroke, "Pikaicons Stroke", available = true,
            reason = "Includes adapted family-style additions for missing actions, status ring and state variants. Includes original supplemental action/selection/crop art; genuine family artwork is retained where available. MIT.",
            missingIcons = emptyList(),
        ),
        ChatyIconThemeId.pikaiconsSolid to PackInfo(
            ChatyIconThemeId.pikaiconsSolid, "Pikaicons Solid", available = true,
            reason = "Includes adapted family-style additions for missing actions, status ring and state variants. Includes original supplemental action/selection/crop art; genuine family artwork is retained where available. MIT.",
            missingIcons = emptyList(),
        ),
        ChatyIconThemeId.phosphorRegular to PackInfo(
            ChatyIconThemeId.phosphorRegular, "Phosphor Regular", available = true,
            reason = "Includes adapted scheduled-send and stop-screen-sharing compositions, and a mobile-device earpiece icon. Also includes original supplemental action/selection/crop art. Other artwork is genuine Phosphor. MIT.",
            missingIcons = emptyList(),
        ),
    )

    /** Requested packs with no code support today. Shown as honest unavailable rows. */
    val blockedPacks: List<PackInfo> = listOf(
        PackInfo(null, "Hugeicons Pro", false, "Paid license required. The free set is offered separately as Hugeicons Free; no silent substitution."),
        PackInfo(null, "Iconly Pro", false, "License scope unresolved; no verified distributable assets."),
        PackInfo(null, "Nucleo", false, "Selectable icon distribution needs an appropriate license; not cleared."),
        PackInfo(null, "Anron", false, "Paid license required (anron.pro/license)."),
        PackInfo(null, "Streamline", false, "Free license prohibits making assets selectable by users."),
        PackInfo(null, "Untitled UI", false, "Custom restrictive package license (not MIT despite package.json label); interpretation pending."),
        PackInfo(null, "Icons8", false, "Requires account and attribution per icons8.com/license; decision pending."),
        PackInfo(null, "Lordicon", false, "Free license does not permit this icon redistribution use."),
    )

    /** The ten original parametric themes are always selectable; asset themes only when flipped. */
    fun isSelectable(theme: ChatyIconThemeId): Boolean =
        assetPacks[theme]?.available ?: true

    fun isAssetPack(theme: ChatyIconThemeId): Boolean = theme in assetPacks

    fun info(theme: ChatyIconThemeId): PackInfo? = assetPacks[theme]
}
