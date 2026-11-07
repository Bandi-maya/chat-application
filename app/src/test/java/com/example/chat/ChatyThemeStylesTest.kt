package com.example.chat

import com.example.chat.ui.icons.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatyThemeStylesTest {
    private class Rec : IconCanvas {
        var draws = 0; var saves = 0; var restores = 0
        override fun drawPath(path: Path, paint: Paint) { draws++ }
        override fun drawLine(p1: Offset, p2: Offset, paint: Paint) { draws++ }
        override fun drawRect(rect: Rect, paint: Paint) { draws++ }
        override fun drawRRect(rrect: RRect, paint: Paint) { draws++ }
        override fun drawCircle(center: Offset, radius: Number, paint: Paint) { draws++ }
        override fun drawOval(rect: Rect, paint: Paint) { draws++ }
        override fun drawArc(rect: Rect, startAngle: Number, sweepAngle: Number, useCenter: Boolean, paint: Paint) { draws++ }
        override fun save() { saves++ }
        override fun restore() { restores++ }
        override fun translate(dx: Number, dy: Number) {}
        override fun scale(sx: Number, sy: Number) {}
        override fun rotate(radians: Number) {}
    }

    @Test fun `overrides name only real icon ids and every override draws with balanced canvas state`() {
        val names = ChatyIconId.entries.map { it.name }.toSet()
        var pairs = 0
        for ((theme, list) in ChatyThemeStyles.overrides) for (n in list.split(' ')) {
            assertTrue("$theme/$n is not an icon id", n in names)
            val c = Rec()
            assertTrue("$theme/$n", ChatyThemeStyles.paint(c, 24.0, ChatyIconId.valueOf(n), theme, Colors.white))
            assertTrue("$theme/$n draws", c.draws > 0)
            assertEquals("$theme/$n save/restore", c.saves, c.restores)
            pairs++
        }
        assertTrue("expected the generated override set", pairs > 400)
    }

    @Test fun `every icon id has base outlines and hand art keys are valid overridden pairs`() {
        assertEquals(ChatyIconId.entries.size, ChatyBaseOutlines.shapes.size)
        for (id in ChatyIconId.entries) assertTrue("$id", ChatyBaseOutlines.shapes.containsKey(id))
        for (key in ChatyThemeStyles.handArt.keys) {
            val (theme, icon) = key.split(':')
            assertTrue("$key must be an overridden pair", ChatyThemeStyles.applies(ChatyIconThemeId.valueOf(theme), ChatyIconId.valueOf(icon)))
        }
    }

    @Test fun `chatySigil and non-listed pairs keep the original painter`() {
        assertFalse(ChatyThemeStyles.paint(Rec(), 24.0, ChatyIconId.chats, ChatyIconThemeId.chatySigil, Colors.white))
        assertFalse(ChatyThemeStyles.applies(ChatyIconThemeId.chatySigil, ChatyIconId.lock))
    }
    @Test fun `bounded hand art slice adds exactly fifty two reachable designs`() {
        // This contract is for the nine original procedural styles, not bundled asset families.
        // Independent names keep removal/renaming of any original style a test failure.
        val proceduralNames = listOf(
            "precisionContour", "monolithicCutout", "origamiGeometry", "organicMotion",
            "pixelPrecision", "technicalBlueprint", "sculptedVolume", "expressiveInk", "signalLanguage",
        )
        val themes = proceduralNames.map(ChatyIconThemeId::valueOf)
        assertEquals(proceduralNames.toSet(), ChatyThemeStyles.overrides.keys.map { it.name }.toSet())
        val ids = listOf(ChatyIconId.chats, ChatyIconId.checkSingle, ChatyIconId.checkDouble, ChatyIconId.videoCam, ChatyIconId.voiceCall, ChatyIconId.mute)
        val expected = themes.flatMap { t -> ids.filter { ChatyThemeStyles.applies(t, it) }.map { "${t.name}:${it.name}" } }.toSet()
        assertEquals(52, expected.size)
        val existing = themes.flatMap { t -> listOf("lock", "delete", "share").map { "${t.name}:$it" } }.toSet()
        assertEquals(27, existing.size)
        assertEquals(79, ChatyThemeStyles.handArt.size)
        assertEquals(existing + expected, ChatyThemeStyles.handArt.keys)
        val signatures = expected.map { key -> ChatyThemeStyles.handArt.getValue(key).joinToString("|") { "${it.role}:${it.d}" } }
        assertEquals("every new drawing has its own geometry", 52, signatures.toSet().size)
        for (t in listOf(ChatyIconThemeId.monolithicCutout, ChatyIconThemeId.pixelPrecision)) {
            assertFalse(ChatyThemeStyles.applies(t, ChatyIconId.chats))
            assertFalse(ChatyThemeStyles.handArt.containsKey("${t.name}:chats"))
        }
        for (key in expected) {
            val (theme, name) = key.split(':')
            val c = Rec()
            assertTrue(key, ChatyThemeStyles.paint(c, 24.0, ChatyIconId.valueOf(name), ChatyIconThemeId.valueOf(theme), Colors.white))
            assertTrue("$key draws", c.draws > 0)
            assertEquals("$key balanced", c.saves, c.restores)
        }
    }
    @Test fun `bundled asset families never enter the procedural hand art path`() {
        val assetNames = listOf(
            "hugeiconsFree", "majesticonsLine", "majesticonsSolid",
            "pikaiconsStroke", "pikaiconsSolid", "phosphorRegular",
        )
        val assets = assetNames.map(ChatyIconThemeId::valueOf)
        assertEquals(assetNames.toSet(), ChatyIconThemeId.entries.filter(ChatyIconPackCatalog::isAssetPack).map { it.name }.toSet())
        assertEquals(6, assets.size)
        // Original82 IDs stay byte-for-byte in order; new roles append only.
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
        for (theme in assets) {
            assertFalse(theme.name, ChatyThemeStyles.handArt.keys.any { it.startsWith("${theme.name}:") })
            for (icon in ChatyIconId.entries) {
                val label = "${theme.name}:${icon.name}"
                assertFalse(label, ChatyThemeStyles.applies(theme, icon))
                val c = Rec()
                assertFalse(label, ChatyThemeStyles.paint(c, 24.0, icon, theme, Colors.white))
                assertEquals("$label style draws", 0, c.draws)
                assertEquals("$label style balanced", c.saves, c.restores)
                ChatyIconPainter.paint(c, 24.0, icon, theme, Colors.white)
                assertEquals("$label procedural painter draws", 0, c.draws)
                assertEquals("$label painter balanced", c.saves, c.restores)
            }
        }
    }


    @Test fun `five new semantic ids have base outlines and draw in every procedural theme`() {
        val newIds = listOf(ChatyIconId.mail, ChatyIconId.eye, ChatyIconId.eyeOff, ChatyIconId.workspace, ChatyIconId.info)
        val procedural = ChatyIconThemeId.entries.filterNot(ChatyIconPackCatalog::isAssetPack)
        assertEquals(10, procedural.size)
        for (id in newIds) {
            assertTrue("$id missing base outline", ChatyBaseOutlines.shapes.containsKey(id))
            val base = Rec()
            assertTrue("$id base outline paints", ChatyBaseOutlines.paint(base, 24.0, id, Colors.white))
            assertTrue("$id base outline draws", base.draws > 0)
            for (theme in procedural) {
                val c = Rec()
                ChatyIconPainter.paint(c, 24.0, id, theme, Colors.white)
                assertTrue("${theme.name}:$id painter draws", c.draws > 0)
                assertEquals("${theme.name}:$id balanced", c.saves, c.restores)
            }
        }
    }

    @Test fun `painter covers every icon in every procedural theme and no asset theme`() {
        for (id in ChatyIconId.entries) {
            for (theme in ChatyIconThemeId.entries) {
                val c = Rec()
                ChatyIconPainter.paint(c, 24.0, id, theme, Colors.white)
                assertEquals("${theme.name}:${id.name} balanced", c.saves, c.restores)
                if (ChatyIconPackCatalog.isAssetPack(theme)) {
                    assertEquals("${theme.name}:${id.name} asset theme must not draw procedurally", 0, c.draws)
                } else {
                    assertTrue("${theme.name}:${id.name} procedural theme must draw", c.draws > 0)
                }
            }
        }
    }

    @Test fun `eyeOff is eye plus slash in base outlines`() {
        val eye = ChatyBaseOutlines.shapes.getValue(ChatyIconId.eye)
        val eyeOff = ChatyBaseOutlines.shapes.getValue(ChatyIconId.eyeOff)
        assertEquals(eyeOff.take(eye.size), eye)
        assertEquals(1, eyeOff.size - eye.size)
        assertEquals("M4 4 L20 20", eyeOff.last().d)
    }
}
