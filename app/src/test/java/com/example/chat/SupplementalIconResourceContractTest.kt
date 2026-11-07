package com.example.chat

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SupplementalIconResourceContractTest {
    private fun root(): File = listOf(File("src/main"), File("app/src/main"))
        .first { File(it, "res/drawable").isDirectory }

    @Test fun `every supplemental mapping names nonempty original vector art`() {
        val main = root()
        val source = File(main, "kotlin/com/example/chat/ui/icons/ChatySupplementalIcons.kt").readText()
        val names = Regex("R\\.drawable\\.(\\w+)").findAll(source).map { it.groupValues[1] }.toList()
        assertEquals(16, Regex("ChatyIconThemeId\\.\\w+ to mapOf").findAll(source).count())
        val expectedRoles = listOf("star", "starFilled", "report", "block", "selectAll", "rotateRight", "rotateLeft", "reset", "add", "wallpaper", "palette", "markRead", "markUnread", "unarchive", "ghost", "translate", "home", "shield", "fingerprint", "atSign", "chevronDown", "chevronUp", "textFormat", "paint", "dialpad", "personAdd", "layout", "storage", "wifi", "permissions", "backup", "clean", "widgets", "touch", "qrCode", "link", "history", "calendar", "folder", "document", "notifications", "notificationsOff", "checkCircle", "addCircle", "minusCircle", "groupAdd", "replyAll", "key", "menu", "grid", "list", "location", "download", "upload", "quote", "poll", "refresh", "notificationDot", "personSearch", "contacts", "clipboard", "crop", "filter", "speed", "stories", "waveform", "layers", "alignTop", "freeze", "style", "deleteSweep", "checklist", "searchOff", "mediaError", "badge", "deviceLock", "keyboard", "timer", "personOff", "playCircle", "sidebar", "pattern", "password", "paintFill", "cloudOff", "cast", "sun", "moon", "arrowUp", "arrowRight", "minus", "locate", "restore", "undo", "help", "warning", "cut", "pinCode", "organization", "organizationAdd", "personRemove", "keyOff", "syncProblem", "mediaLibrary", "devices", "cloudDownload", "entry", "profileCard", "contactLabel", "forwardInbox", "dock", "bubbleChart", "premium", "bolt", "backspace", "campaign", "speakerGroup", "ringVolume", "robot", "mosaic", "gif", "carousel", "hd", "subtitles", "toggle", "radial", "extension", "screenshot", "cube", "brain", "corner", "online", "layersOff", "community", "science", "mapPin", "shuffle", "repeat", "repeatOne", "replayTen", "forwardTen", "fit", "aspectRatio", "videoLibrary", "photoAdd", "fileOpen", "fileUpload", "headphones", "headset", "bluetooth", "usb", "ethernet", "wifiOff", "cloudCheck", "cloudSync", "language", "swipe", "callIncoming", "callOutgoing", "callMissed", "shieldMoon", "diamond", "animation", "numberOne", "verified", "medal", "lightbulb", "angleRoute", "airplane", "inbox", "calendarOff", "manageSearch", "moveDown", "alignBottom", "addBox", "alarm", "alarmAdd", "fileRules", "kanban", "note", "lockReset", "phone", "tv", "navigation")
        for (role in expectedRoles) {
            assertEquals("16 packs for $role", 16, Regex("ChatyIconId\\.$role to R").findAll(source).count())
        }
        assertEquals(2944, names.size)
        assertEquals(2944, names.toSet().size)
        for (name in names) {
            val file = File(main, "res/drawable/$name.xml")
            assertTrue("$name exists", file.isFile)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            assertEquals("vector", doc.documentElement.tagName)
            val paths = doc.getElementsByTagName("path")
            assertTrue("$name has artwork", paths.length > 0)
            for (index in 0 until paths.length) {
                assertTrue("$name path data", paths.item(index).attributes.getNamedItem("android:pathData").nodeValue.isNotBlank())
            }
        }
        assertTrue(File(main, "assets/icon-packs/original-supplemental-provenance.json").readText().contains("original family-style adaptations"))
    }
}
