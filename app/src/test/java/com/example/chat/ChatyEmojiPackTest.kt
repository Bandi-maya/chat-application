package com.example.chat.ui.chats.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatyEmojiPackTest {
    @Test fun defaultAndLegacyStylesAreChaty() {
        assertEquals("Chaty", ChatyEmojiPack.normalize(null))
        assertEquals("Chaty", ChatyEmojiPack.normalize("Chaty Motion"))
        assertEquals("Chaty", ChatyEmojiPack.normalize(""))
        assertEquals("Chaty", ChatyEmojiPack.normalize("Chaty"))
    }

    @Test fun iosIsSelectedOnlyByIos() {
        assertEquals("iOS", ChatyEmojiPack.normalize("iOS"))
        assertEquals("iOS", ChatyEmojiPack.normalize("ios"))
    }

    @Test fun fiftyEmojisMapToDistinctFiles() {
        assertEquals(50, ChatyEmojiPack.coverage)
        val cps = listOf(0x1f600, 0x1f601, 0x1f602, 0x1f923, 0x1f603, 0x1f604, 0x1f605, 0x1f606, 0x1f609, 0x1f60a,
            0x1f60b, 0x1f60e, 0x1f60d, 0x1f618, 0x1f617, 0x1f619, 0x1f61a, 0x263a, 0x1f642, 0x1f917, 0x1f929, 0x1f914,
            0x1f928, 0x1f610, 0x1f611, 0x1f636, 0x1f644, 0x1f60f, 0x1f623, 0x1f625, 0x1f62e, 0x1f910, 0x1f62f, 0x1f62a,
            0x1f62b, 0x1f634, 0x1f60c, 0x1f61b, 0x1f61c, 0x1f61d, 0x1f924, 0x1f612, 0x1f613, 0x1f614, 0x1f615, 0x1f643,
            0x1f911, 0x1f632, 0x2639, 0x1f641)
        val paths = cps.map { ChatyEmojiPack.iosAssetPath(String(Character.toChars(it))) }
        assertTrue(paths.all { it != null })
        assertEquals(50, paths.toSet().size)
        assertEquals("emoji/ios_animated/3_face_with_tears.webp", ChatyEmojiPack.iosAssetPath("\uD83D\uDE02"))
    }

    @Test fun variationSelectorIsIgnoredAndUncoveredIsNull() {
        assertEquals(ChatyEmojiPack.iosAssetPath("\u263A"), ChatyEmojiPack.iosAssetPath("\u263A\uFE0F"))
        assertNull(ChatyEmojiPack.iosAssetPath("\uD83D\uDC4D")) // thumbs up: Chaty style
        assertNull(ChatyEmojiPack.iosAssetPath("\uD83D\uDE02\uD83D\uDE02")) // two emojis
        assertNull(ChatyEmojiPack.iosAssetPath(""))
    }

    @Test fun animatedWebpNeedsApi28() {
        assertFalse(ChatyEmojiPack.iosSupported(27))
        assertTrue(ChatyEmojiPack.iosSupported(28))
    }

    @Test fun sevenActuallyBundledPackIdentitiesInPickerOrder() {
        assertEquals(listOf("Chaty", "iOS", "Microsoft", "OpenMoji", "Telegram", "Telegram Lottie", "Drive MOV animations"), ChatyEmojiPack.ORDER)
        assertEquals("Microsoft", ChatyEmojiPack.normalize("microsoft"))
        assertEquals("OpenMoji", ChatyEmojiPack.normalize("OPENMOJI"))
        assertEquals("Telegram", ChatyEmojiPack.normalize("Telegram"))
        assertEquals("Chaty", ChatyEmojiPack.normalize("Chaty Motion"))
    }

    @Test fun codePointPackPaths() {
        assertEquals("emoji/fluent_animated/1f602.webp", ChatyEmojiPack.assetPathFor("Microsoft", "\uD83D\uDE02"))
        assertEquals("emoji/openmoji/1F602.png", ChatyEmojiPack.assetPathFor("OpenMoji", "\uD83D\uDE02"))
        assertEquals("emoji/openmoji/263A.png", ChatyEmojiPack.assetPathFor("OpenMoji", "\u263A\uFE0F"))
        assertNull(ChatyEmojiPack.assetPathFor("Microsoft", "\uD83D\uDE02\uD83D\uDE02"))
    }

    @Test fun chatyAndUncoveredTelegramHaveNoPackArt() {
        assertNull(ChatyEmojiPack.assetPathFor("Chaty", "\uD83D\uDE02"))
        assertNull(ChatyEmojiPack.assetPathFor("Telegram", "\uD83D\uDE02"))
    }

    @Test fun telegramApprovedArtUsesCorrectUnicodeNotOriginalFilenames() {
        assertEquals(19, ChatyEmojiPack.telegramCodePoints.size)
        for (cp in ChatyEmojiPack.telegramCodePoints) assertEquals("emoji/telegram19/${Integer.toHexString(cp)}.webp", ChatyEmojiPack.assetPathFor("Telegram", String(Character.toChars(cp))))
        assertEquals("emoji/telegram19/1f62b.webp", ChatyEmojiPack.assetPathFor("Telegram", "😫"))
        assertEquals("emoji/telegram19/1f611.webp", ChatyEmojiPack.assetPathFor("Telegram", "😑"))
        assertNull(ChatyEmojiPack.assetPathFor("Telegram", "😱"))
        assertNull(ChatyEmojiPack.assetPathFor("Telegram", "😐"))
        assertNull(ChatyEmojiPack.assetPathFor("Telegram", "😫😫"))
    }
    @Test fun separateLottieSetHasElevenDistinctPaths() {
        val paths = ChatyEmojiPack.lottieCodePoints.map { ChatyEmojiPack.lottieAssetPath(String(Character.toChars(it))) }
        assertEquals(11, paths.size); assertEquals(11, paths.toSet().size)
        assertTrue(paths.all { it?.endsWith(".json") == true })
        assertNull(ChatyEmojiPack.lottieAssetPath("😫"))
        assertNull(ChatyEmojiPack.lottieAssetPath("😅😅"))
        assertEquals("Telegram Lottie", ChatyEmojiPack.normalize("telegram lottie"))
        assertEquals(ChatyEmojiPack.lottieAssetPath("🌤"), ChatyEmojiPack.lottieAssetPath("🌤️"))
    }
    @Test fun drivePackSupportsActualGenderedZwjSequencesWithoutChangingOtherStyles() {
        assertEquals(132, ChatyEmojiPack.driveKeys.size)
        assertEquals("emoji/drive_mov132/1f937-200d-2642.webp", ChatyEmojiPack.driveAssetPath("🤷‍♂️"))
        assertEquals(ChatyEmojiPack.driveAssetPath("🤷‍♂"), ChatyEmojiPack.driveAssetPath("🤷‍♂️"))
        assertEquals("emoji/drive_mov132/1f926-200d-2640.webp", ChatyEmojiPack.driveAssetPath("🤦‍♀️"))
        assertEquals("emoji/drive_mov132/1f92f.webp", ChatyEmojiPack.driveAssetPath("🤯"))
        assertEquals("emoji/drive_mov132/1f645-200d-2642.webp", ChatyEmojiPack.driveAssetPath("🙅‍♂️"))
        assertEquals("emoji/drive_mov132/1f645-200d-2640.webp", ChatyEmojiPack.driveAssetPath("🙅‍♀️"))
        assertNull(ChatyEmojiPack.driveAssetPath("🦀")) // not part of the source folder
        assertNull(ChatyEmojiPack.driveAssetPath("🤷‍♂️🔥"))
        assertNull(ChatyEmojiPack.iosAssetPath("🤷‍♂️"))
        assertTrue(ChatyEmojiPack.needsAnimatedDecoder(ChatyEmojiPack.DRIVE_MOV))
    }
    @Test fun onlyOpenMojiIsStatic() {
        assertTrue(ChatyEmojiPack.needsAnimatedDecoder("Telegram"))
        assertTrue(ChatyEmojiPack.needsAnimatedDecoder("Microsoft"))
        assertTrue(ChatyEmojiPack.needsAnimatedDecoder("iOS"))
        assertFalse(ChatyEmojiPack.needsAnimatedDecoder("OpenMoji"))
        assertFalse(ChatyEmojiPack.needsAnimatedDecoder("Chaty"))
    }
}
