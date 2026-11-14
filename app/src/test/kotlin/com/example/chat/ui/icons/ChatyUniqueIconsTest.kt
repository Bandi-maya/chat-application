package com.example.chat.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the unique-glyph icon set: the 15 new supplemental drawables keep the default-pack
 * geometry rules (24dp viewport, 1.5 stroke, rounded caps/joins, black carrier color only),
 * the materializer covers every audited glyph, and the role bridge resolves all 39 stock names.
 */
class ChatyUniqueIconsTest {

    private val newGlyphFiles = listOf(
        "ic_hgf_role_reactionadd", "ic_hgf_role_presencedot", "ic_hgf_role_rupee",
        "ic_hgf_role_symbols", "ic_hgf_role_hourglass", "ic_hgf_role_paw",
        "ic_hgf_role_food", "ic_hgf_role_radioon", "ic_hgf_role_radiooff",
        "ic_hgf_role_sportsball", "ic_hgf_role_calldatasaver", "ic_hgf_role_channeloff",
        "ic_hgf_role_voicemail", "ic_hgf_role_onlinering", "ic_hgf_role_fabchat",
    )

    private val auditedIds = listOf(
        ChatyIconId.reactionAdd, ChatyIconId.moveDown, ChatyIconId.autoAwesome,
        ChatyIconId.cameraSwitch, ChatyIconId.checklist, ChatyIconId.presenceDot,
        ChatyIconId.updates, ChatyIconId.rupee, ChatyIconId.symbols, ChatyIconId.layers,
        ChatyIconId.hourglass, ChatyIconId.community, ChatyIconId.entry,
        ChatyIconId.contactLabel, ChatyIconId.gallery, ChatyIconId.fabChat,
        ChatyIconId.animation, ChatyIconId.eyeOff, ChatyIconId.paw, ChatyIconId.earpiece,
        ChatyIconId.aspectRatio, ChatyIconId.radioOn, ChatyIconId.radioOff, ChatyIconId.food,
        ChatyIconId.tune, ChatyIconId.toggle, ChatyIconId.personOff,
        ChatyIconId.callDataSaver, ChatyIconId.sportsBall, ChatyIconId.atSign,
        ChatyIconId.timer, ChatyIconId.channelOff, ChatyIconId.list, ChatyIconId.alignTop,
        ChatyIconId.mosaic, ChatyIconId.layout, ChatyIconId.voicemail, ChatyIconId.dock,
        ChatyIconId.onlineRing,
    )

    private val bridgedStockNames = listOf(
        "AddReaction", "ArrowDownward", "AutoFixHigh", "CameraFront", "CheckBox", "Circle",
        "CircleOutlined", "CurrencyRupee", "EmojiSymbols", "FilterNone", "HourglassBottom",
        "Hub", "Input", "Label", "Landscape", "Lens", "MotionPhotosOn", "NotificationsPaused",
        "Pets", "PhoneInTalk", "PhotoSizeSelectSmall", "RadioButtonChecked",
        "RadioButtonUnchecked", "Restaurant", "SettingsSuggest", "SmartButton",
        "SpeakerNotesOff", "SpeakerPhone", "SportsSoccer", "Tag", "Timelapse", "TvOff",
        "ViewAgenda", "ViewHeadline", "ViewQuilt", "ViewWeek", "Voicemail", "Web",
        "WifiTethering",
    )

    @Test
    fun newGlyphDrawablesFollowDefaultPackGeometry() {
        val factory = DocumentBuilderFactory.newInstance()
        for (name in newGlyphFiles) {
            val file = File("app/src/main/res/drawable/$name.xml")
            assertTrue("missing drawable $name", file.isFile)
            val doc = factory.newDocumentBuilder().parse(file)
            val root = doc.documentElement
            assertEquals("vector", root.tagName)
            assertEquals("24", root.getAttribute("android:viewportWidth"))
            assertEquals("24", root.getAttribute("android:viewportHeight"))
            val paths = root.getElementsByTagName("path")
            assertTrue("$name has no paths", paths.length > 0)
            for (i in 0 until paths.length) {
                val node = paths.item(i) as org.w3c.dom.Element
                val data = node.getAttribute("android:pathData")
                assertTrue("$name pathData must not use arc commands", !data.contains(Regex("[Aa]")))
                if (node.hasAttribute("android:strokeColor")) {
                    assertEquals("#FF000000", node.getAttribute("android:strokeColor"))
                    assertEquals("1.5", node.getAttribute("android:strokeWidth"))
                    assertEquals("round", node.getAttribute("android:strokeLineCap"))
                    assertEquals("round", node.getAttribute("android:strokeLineJoin"))
                }
                if (node.hasAttribute("android:fillColor")) {
                    val fill = node.getAttribute("android:fillColor")
                    assertTrue("$name fill must be opaque black carrier", fill == "#FF000000")
                }
            }
        }
    }

    @Test
    fun materializerCoversEveryAuditedGlyph() {
        for (id in auditedIds) {
            val vector = ChatyGlyphVectors.of(id)
            assertEquals(24f, vector.viewportWidth)
            assertEquals(24f, vector.viewportHeight)
        }
    }

    @Test
    fun roleBridgeResolvesAllAuditedStockNames() {
        for (name in bridgedStockNames) {
            assertNotNull("role bridge missing $name", ChatyMaterialIconRoles.byName[name])
        }
        assertEquals(auditedIds.toSet(), bridgedStockNames.map { ChatyMaterialIconRoles.byName.getValue(it) }.toSet())
    }

    @Test
    fun supplementalArtRegisteredForEveryTheme() {
        val newIds = listOf(
            ChatyIconId.reactionAdd, ChatyIconId.presenceDot, ChatyIconId.rupee,
            ChatyIconId.symbols, ChatyIconId.hourglass, ChatyIconId.paw, ChatyIconId.food,
            ChatyIconId.radioOn, ChatyIconId.radioOff, ChatyIconId.sportsBall,
            ChatyIconId.callDataSaver, ChatyIconId.channelOff, ChatyIconId.voicemail,
            ChatyIconId.onlineRing, ChatyIconId.fabChat,
        )
        for (theme in ChatyIconThemeId.entries) {
            for (id in newIds) {
                assertNotNull("supplemental art missing for $theme/$id", ChatySupplementalIcons.drawable(theme, id))
            }
        }
    }
}
