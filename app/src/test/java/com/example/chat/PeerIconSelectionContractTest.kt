package com.example.chat

import com.example.chat.ui.icons.ChatyIconPackCatalog
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.icons.ChatyIconThemeId
import com.example.chat.ui.settings.universal.UniversalIconsLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Peer Cluster A contract tests: icon identity and selection.
 *
 * Scope notes (source-verified at baseline 744ba4c0, overlay 320/320 hash-verified):
 * - The single resolver is ChatyIconThemeId.fromString (ChatyIconPainter.kt, read-only).
 *   Both the settings chooser (UniversalChatyIconsScreen.kt: `ChatyIconThemeId.fromString(prefs.iconTheme)`)
 *   and the consumer provider (ChatyIcon.kt: `ProvideChatyIconTheme` -> `ChatyIconThemeId.fromString(
 *   preferences.universal.iconTheme)`) call that one function, so chooser and consumer cannot diverge
 *   in resolution. Compose wiring itself is not exercised here (no runtime claim).
 * - Apply persists the existing display-title string: UniversalChatyIconsScreen.kt writes
 *   `preferences.universal.copy(iconTheme = theme.title)`, matching Dart `_applyTheme`
 *   (`iconTheme: theme.title`). No new ID format is introduced.
 * - Only JVM-pure files are referenced (ChatyIconPainter.kt / IconPrimitives.kt /
 *   UniversalIconsLogic.kt have no androidx imports), so these tests run on the plain
 *   JVM unit-test path without Compose or an Android runtime.
 */
class PeerIconSelectionContractTest {

    private val expectedNamesInOrder = listOf(
        "chatySigil", "precisionContour", "monolithicCutout", "origamiGeometry",
        "organicMotion", "pixelPrecision", "technicalBlueprint", "sculptedVolume",
        "expressiveInk", "signalLanguage",
    )

    private val expectedTitlesInOrder = listOf(
        "Chaty Sigil", "Precision Contour", "Monolithic Cutout", "Origami Geometry",
        "Organic Motion", "Pixel Precision", "Technical Blueprint", "Sculpted Volume",
        "Expressive Ink", "Signal Language",
    )

    /** Check 1: all ten canonical IDs stay identical and in the same order,
     *  including the persisted display-title strings. */
    @Test
    fun tenCanonicalIdsIdenticalAndOrdered() {
        assertTrue(ChatyIconThemeId.entries.take(10).all(ChatyIconPackCatalog::isSelectable))
        assertEquals(expectedNamesInOrder, ChatyIconThemeId.entries.take(10).map { it.name })
        assertEquals(expectedTitlesInOrder, ChatyIconThemeId.entries.take(10).map { it.title })
        // Default theme is the first canonical entry.
        assertEquals(ChatyIconThemeId.chatySigil, ChatyIconThemeId.entries.first())
    }

    /** Check 2: every enum name resolves to that same entry. */
    @Test
    fun everyEnumNameResolvesToSameEntry() {
        for (theme in ChatyIconThemeId.entries.filter(ChatyIconPackCatalog::isSelectable)) {
            assertEquals(theme, ChatyIconThemeId.fromString(theme.name))
        }
    }

    /** Check 3: every persisted display title resolves to that same entry. */
    @Test
    fun everyPersistedDisplayTitleResolvesToSameEntry() {
        for (theme in ChatyIconThemeId.entries.filter(ChatyIconPackCatalog::isSelectable)) {
            assertEquals(theme, ChatyIconThemeId.fromString(theme.title))
        }
    }

    /** Check 4: existing supported aliases retain their exact mapping. */
    @Test
    fun legacyAliasesRetainExactMapping() {
        val aliases = mapOf(
            "chatyOrbit" to ChatyIconThemeId.chatySigil,
            "chatyorbit" to ChatyIconThemeId.chatySigil,
            "orbit" to ChatyIconThemeId.chatySigil,
            "precisionLine" to ChatyIconThemeId.precisionContour,
            "softGeometry" to ChatyIconThemeId.monolithicCutout,
            "liquidMotion" to ChatyIconThemeId.organicMotion,
            "sculptedDuotone" to ChatyIconThemeId.sculptedVolume,
            "signalPulse" to ChatyIconThemeId.signalLanguage,
            "neoMinimal" to ChatyIconThemeId.technicalBlueprint,
            "inkFlow" to ChatyIconThemeId.expressiveInk,
            "prismEdge" to ChatyIconThemeId.origamiGeometry,
        )
        for ((alias, expected) in aliases) {
            assertEquals("alias '$alias'", expected, ChatyIconThemeId.fromString(alias))
        }
    }

    /** Check 5: whitespace/underscore/hyphen and case normalization remains deterministic. */
    @Test
    fun normalizationIsWhitespaceSeparatorAndCaseInsensitive() {
        assertEquals(ChatyIconThemeId.chatySigil, ChatyIconThemeId.fromString("  Chaty Sigil  "))
        assertEquals(ChatyIconThemeId.chatySigil, ChatyIconThemeId.fromString("chaty_sigil"))
        assertEquals(ChatyIconThemeId.chatySigil, ChatyIconThemeId.fromString("chaty-sigil"))
        assertEquals(ChatyIconThemeId.chatySigil, ChatyIconThemeId.fromString("CHATYSIGIL"))
        assertEquals(ChatyIconThemeId.pixelPrecision, ChatyIconThemeId.fromString(" Pixel_Precision "))
        assertEquals(ChatyIconThemeId.pixelPrecision, ChatyIconThemeId.fromString("pixel-precision"))
        assertEquals(ChatyIconThemeId.pixelPrecision, ChatyIconThemeId.fromString("PIXELPRECISION"))
        assertEquals(ChatyIconThemeId.technicalBlueprint, ChatyIconThemeId.fromString("technical___blueprint"))
        assertEquals(ChatyIconThemeId.organicMotion, ChatyIconThemeId.fromString("Organic  Motion"))
        assertEquals(ChatyIconThemeId.signalLanguage, ChatyIconThemeId.fromString("SIGNAL-LANGUAGE"))
        // Determinism: identical input resolves identically on repeated calls.
        assertEquals(
            ChatyIconThemeId.fromString("Precision Contour"),
            ChatyIconThemeId.fromString("Precision Contour"),
        )
    }

    /** Check 6: null/unknown/empty input retains the current fallback default. */
    @Test
    fun nullUnknownAndEmptyInputsFallBackToDefault() {
        assertEquals(ChatyIconThemeId.hugeiconsFree, ChatyIconThemeId.fromString(null))
        assertEquals(ChatyIconThemeId.chatySigil, ChatyIconThemeId.fromString(""))
        assertEquals(ChatyIconThemeId.chatySigil, ChatyIconThemeId.fromString("   "))
        assertEquals(ChatyIconThemeId.chatySigil, ChatyIconThemeId.fromString("___"))
        assertEquals(ChatyIconThemeId.chatySigil, ChatyIconThemeId.fromString("no-such-theme"))
        assertEquals(ChatyIconThemeId.chatySigil, ChatyIconThemeId.fromString("sigil-2"))
    }

    /** Checks 8+9: the string apply persists (theme.title) re-resolves to the selected
     *  entry for every theme, with no cross-theme collision. Because the alias branch
     *  runs before the name/title scan, these assertions also prove no alias shadows
     *  another theme's name or title. */
    @Test
    fun appliedTitlesRoundTripWithoutCrossThemeCollision() {
        val strip = Regex("[\\s_\\-]+")
        fun norm(s: String) = s.trim().lowercase().replace(strip, "")

        for (theme in ChatyIconThemeId.entries.filter(ChatyIconPackCatalog::isSelectable)) {
            // Re-resolving the exact persisted string returns the same selection.
            assertEquals(theme, ChatyIconThemeId.fromString(theme.title))
        }
        val nameNorms = ChatyIconThemeId.entries.map { norm(it.name) }
        val titleNorms = ChatyIconThemeId.entries.map { norm(it.title) }
        assertEquals("normalized names must be unique", ChatyIconThemeId.entries.size, nameNorms.toSet().size)
        assertEquals("normalized titles must be unique", ChatyIconThemeId.entries.size, titleNorms.toSet().size)
        // No name normalizes to a different theme's title (and vice versa).
        for (i in ChatyIconThemeId.entries.indices) {
            for (j in ChatyIconThemeId.entries.indices) {
                if (i != j) {
                    assertTrue(
                        "name ${nameNorms[i]} collides with title ${titleNorms[j]}",
                        nameNorms[i] != titleNorms[j],
                    )
                }
            }
        }
        // Old and new alternatives remain simultaneously available and distinct.
        assertNotSame(
            ChatyIconThemeId.fromString("chatyOrbit"),
            ChatyIconThemeId.fromString("precisionContour"),
        )
    }

    /** Check 10: real catalog/filter/preview IDs remain mapped to canonical semantic IDs,
     *  and the catalog order stays exactly the Dart port's. */
    @Test
    fun catalogFilterAndPreviewUseCanonicalIds() {
        // Categories, exact order (Dart _categories).
        assertEquals(
            listOf("All", "Navigation", "Messaging", "Calls", "Camera", "Status", "Settings"),
            UniversalIconsLogic.categories,
        )
        // Core contact strip: 14 canonical IDs, exact order (Dart _coreContactIcons).
        assertEquals(14, UniversalIconsLogic.coreContactIcons.size)
        assertEquals(ChatyIconId.search, UniversalIconsLogic.coreContactIcons.first())
        assertEquals(ChatyIconId.messageCheckDouble, UniversalIconsLogic.coreContactIcons.last())
        // Every catalog entry is a canonical ChatyIconId (compile-time typed, verified here too).
        for (id in UniversalIconsLogic.coreContactIcons) {
            assertTrue(ChatyIconId.entries.contains(id))
        }
        assertEquals(6, UniversalIconsLogic.categorizedIcons.size)
        for ((category, ids) in UniversalIconsLogic.categorizedIcons) {
            assertTrue("category '$category' must be listed in categories", category in UniversalIconsLogic.categories)
            assertTrue(ids.isNotEmpty())
            for (id in ids) assertTrue(ChatyIconId.entries.contains(id))
        }
        // 'All' and unknown categories expose every canonical ID.
        assertEquals(ChatyIconId.entries, UniversalIconsLogic.filteredIcons("All", ""))
        assertEquals(ChatyIconId.entries, UniversalIconsLogic.filteredIcons("Unknown Category", ""))
        // Category-scoped filtering returns only canonical members of that category.
        val camera = UniversalIconsLogic.filteredIcons("Camera", "camera")
        assertTrue(camera.isNotEmpty())
        assertTrue(UniversalIconsLogic.categorizedIcons["Camera"]!!.containsAll(camera))
        // Preview sizes remain the Dart 20/26 pair.
        assertEquals(20f, UniversalIconsLogic.previewSize(true), 0.0f)
        assertEquals(26f, UniversalIconsLogic.previewSize(false), 0.0f)
        // Display names remain the camelCase-split, capitalized Dart format.
        assertEquals("Chats", UniversalIconsLogic.iconDisplayName(ChatyIconId.chats))
        assertEquals("Message Check Double", UniversalIconsLogic.iconDisplayName(ChatyIconId.messageCheckDouble))
    }

    /** New icon ids append after musicNote without disturbing existing ids, and every
     *  saved pack preference (persisted title string) still resolves to a selectable pack. */
    @Test
    fun newIdsAppendInOrderAndSavedPacksStaySelectable() {
        assertEquals(
            listOf("mail", "eye", "eyeOff", "workspace", "info"),
            ChatyIconId.entries.take(82).takeLast(5).map { it.name },
        )
        assertEquals(266, ChatyIconId.entries.size)
        assertEquals(listOf("chats", "updates", "calls", "profile", "tasks", "groups", "desktop", "more", "moreOptions", "search", "settings", "close", "check", "tune", "chevronRight", "back", "send", "emoji", "attachment", "attachmentPlus", "microphone", "microphoneOff", "pin", "unpin", "archive", "lock", "unlock", "mute", "unmute", "favourite", "favouriteFilled", "delete", "reply", "forward", "edit", "copy", "share", "camera", "cameraFlip", "cameraShutter", "cameraFlashOn", "cameraFlashOff", "gallery", "callAudio", "callVideo", "callVideoOff", "callEnd", "cameraSwitch", "screenShare", "screenShareStop", "speaker", "earpiece", "signal", "messageCheckSingle", "messageCheckDouble", "messageClock", "pictureInPicture", "autoAwesome", "fullscreen", "fullscreenExit", "openInNew", "clear", "addTask", "editOutline", "videoCam", "voiceCall", "logout", "deleteOutline", "schedule", "scheduleSend", "sync", "checkSingle", "checkDouble", "errorOutline", "pause", "playArrow", "musicNote", "mail", "eye", "eyeOff", "workspace", "info"), ChatyIconId.entries.take(82).map { it.name })
        assertEquals(listOf("star", "starFilled", "report", "block", "selectAll", "rotateRight", "rotateLeft", "reset", "add", "wallpaper", "palette", "markRead", "markUnread", "unarchive", "ghost", "translate"), ChatyIconId.entries.drop(82).take(16).map { it.name })
        assertEquals(listOf("home", "shield", "fingerprint", "atSign", "chevronDown", "chevronUp", "textFormat", "paint", "dialpad", "personAdd", "layout", "storage", "wifi", "permissions", "backup", "clean", "widgets", "touch", "qrCode", "link", "history", "calendar", "folder", "document"), ChatyIconId.entries.drop(98).take(24).map { it.name })
        assertEquals(listOf("notifications", "notificationsOff", "checkCircle", "addCircle", "minusCircle", "groupAdd", "replyAll", "key", "menu", "grid", "list", "location", "download", "upload", "quote", "poll", "refresh", "notificationDot", "personSearch", "contacts", "clipboard", "crop", "filter", "speed"), ChatyIconId.entries.drop(122).take(24).map { it.name })
        assertEquals(listOf("stories", "waveform", "layers", "alignTop", "freeze", "style", "deleteSweep", "checklist", "searchOff", "mediaError", "badge", "deviceLock", "keyboard", "timer", "personOff", "playCircle", "sidebar", "pattern", "password", "paintFill", "cloudOff", "cast", "sun", "moon"), ChatyIconId.entries.drop(146).take(24).map { it.name })
        assertEquals(listOf("arrowUp", "arrowRight", "minus", "locate", "restore", "undo", "help", "warning", "cut", "pinCode", "organization", "organizationAdd", "personRemove", "keyOff", "syncProblem", "mediaLibrary", "devices", "cloudDownload", "entry", "profileCard", "contactLabel", "forwardInbox", "dock", "bubbleChart"), ChatyIconId.entries.drop(170).take(24).map { it.name })
        assertEquals(listOf("premium", "bolt", "backspace", "campaign", "speakerGroup", "ringVolume", "robot", "mosaic", "gif", "carousel", "hd", "subtitles", "toggle", "radial", "extension", "screenshot", "cube", "brain", "corner", "online", "layersOff", "community", "science", "mapPin"), ChatyIconId.entries.drop(194).take(24).map { it.name })
        assertEquals(listOf("shuffle", "repeat", "repeatOne", "replayTen", "forwardTen", "fit", "aspectRatio", "videoLibrary", "photoAdd", "fileOpen", "fileUpload", "headphones", "headset", "bluetooth", "usb", "ethernet", "wifiOff", "cloudCheck", "cloudSync", "language", "swipe", "callIncoming", "callOutgoing", "callMissed"), ChatyIconId.entries.drop(218).take(24).map { it.name })
        assertEquals(listOf("shieldMoon", "diamond", "animation", "numberOne", "verified", "medal", "lightbulb", "angleRoute", "airplane", "inbox", "calendarOff", "manageSearch", "moveDown", "alignBottom", "addBox", "alarm", "alarmAdd", "fileRules", "kanban", "note", "lockReset", "phone", "tv", "navigation"), ChatyIconId.entries.drop(242).map { it.name })
        for (theme in ChatyIconThemeId.entries.filter(ChatyIconPackCatalog::isAssetPack)) {
            assertTrue("${theme.title} must stay selectable", ChatyIconPackCatalog.isSelectable(theme))
            assertEquals(theme, ChatyIconThemeId.fromString(theme.title))
            assertEquals(theme, ChatyIconThemeId.fromString(theme.name))
        }
    }
}
