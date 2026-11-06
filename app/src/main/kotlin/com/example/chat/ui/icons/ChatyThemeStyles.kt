package com.example.chat.ui.icons

/**
 * Theme-specific geometry for icons that the original painter drew identically in several themes (found by comparing the
 * recorded draw ops of all ten themes). Only those (theme, icon) pairs are listed in [overrides]; every other pair keeps
 * its original painter art. The geometry comes from the original [ChatyBaseOutlines] outlines pushed through a per-theme
 * style (weight, corner treatment, facets, smoothing, grid snapping, plates), so the themes read as different systems.
 */
internal object ChatyThemeStyles {
    private class Style(
        val w: Double, val cap: StrokeCap, val join: StrokeJoin,
        val nQ: Int = 6, val nCirc: Int = 32, val nCorner: Int = 6,
        val chaikin: Int = 0, val snap: Double = 0.0,
        val fillClosed: Boolean = false, val plate: Double = 0.0, val dbl: Double = 0.0,
        val ticks: Boolean = false, val pulse: Boolean = false,
    )

    private val styles: Map<ChatyIconThemeId, Style> = mapOf(
        ChatyIconThemeId.precisionContour to Style(1.15, StrokeCap.square, StrokeJoin.miter),
        ChatyIconThemeId.monolithicCutout to Style(3.2, StrokeCap.round, StrokeJoin.round, fillClosed = true),
        ChatyIconThemeId.origamiGeometry to Style(1.9, StrokeCap.butt, StrokeJoin.miter, nQ = 2, nCirc = 8, nCorner = 1),
        ChatyIconThemeId.organicMotion to Style(2.1, StrokeCap.round, StrokeJoin.round, chaikin = 2),
        ChatyIconThemeId.pixelPrecision to Style(2.2, StrokeCap.square, StrokeJoin.miter, nQ = 3, nCirc = 12, nCorner = 2, snap = 1.5),
        ChatyIconThemeId.technicalBlueprint to Style(1.1, StrokeCap.square, StrokeJoin.miter, ticks = true),
        ChatyIconThemeId.sculptedVolume to Style(1.9, StrokeCap.round, StrokeJoin.round, plate = 1.4),
        ChatyIconThemeId.expressiveInk to Style(2.4, StrokeCap.round, StrokeJoin.round, dbl = 0.7),
        ChatyIconThemeId.signalLanguage to Style(1.8, StrokeCap.round, StrokeJoin.round, pulse = true),
    )

    /** Space separated icon names per theme (generated from the ten-theme op comparison). */
    internal val overrides: Map<ChatyIconThemeId, String> = mapOf(
        ChatyIconThemeId.precisionContour to "chats updates profile tasks close check tune chevronRight pin unpin archive lock unlock mute unmute delete reply forward edit copy share cameraFlashOn cameraFlashOff gallery callEnd screenShare screenShareStop speaker earpiece signal messageClock pictureInPicture autoAwesome fullscreen fullscreenExit openInNew clear addTask editOutline videoCam voiceCall logout deleteOutline schedule scheduleSend sync checkSingle checkDouble errorOutline",
        ChatyIconThemeId.monolithicCutout to "updates tasks close check tune chevronRight pin unpin archive lock unlock mute unmute delete reply forward edit copy share cameraFlashOn cameraFlashOff gallery callEnd screenShare screenShareStop speaker earpiece signal messageClock pictureInPicture autoAwesome fullscreen fullscreenExit openInNew clear addTask editOutline videoCam voiceCall logout deleteOutline schedule scheduleSend sync checkSingle checkDouble errorOutline musicNote",
        ChatyIconThemeId.origamiGeometry to "chats updates profile tasks close check tune chevronRight pin unpin archive lock unlock mute unmute delete reply forward edit copy share cameraFlashOn cameraFlashOff gallery callEnd screenShare screenShareStop speaker earpiece signal messageClock pictureInPicture autoAwesome fullscreen fullscreenExit openInNew clear addTask editOutline videoCam voiceCall logout deleteOutline schedule scheduleSend sync checkSingle checkDouble errorOutline musicNote",
        ChatyIconThemeId.organicMotion to "chats updates profile tasks close check tune chevronRight pin unpin archive lock unlock mute unmute delete reply forward edit copy share cameraFlashOn cameraFlashOff gallery callEnd screenShare screenShareStop speaker earpiece signal messageClock pictureInPicture autoAwesome fullscreen fullscreenExit openInNew clear addTask editOutline videoCam voiceCall logout deleteOutline schedule scheduleSend sync checkSingle checkDouble errorOutline",
        ChatyIconThemeId.pixelPrecision to "updates profile tasks close check tune chevronRight pin unpin archive lock unlock mute unmute delete reply forward edit copy share cameraFlashOn cameraFlashOff gallery callEnd screenShare screenShareStop speaker earpiece signal messageClock pictureInPicture autoAwesome fullscreen fullscreenExit openInNew clear addTask editOutline videoCam voiceCall logout deleteOutline schedule scheduleSend sync checkSingle checkDouble errorOutline",
        ChatyIconThemeId.technicalBlueprint to "chats updates profile tasks close check tune chevronRight pin unpin archive lock unlock mute unmute delete reply forward edit copy share cameraFlashOn cameraFlashOff gallery callEnd screenShare screenShareStop speaker earpiece signal messageClock pictureInPicture autoAwesome fullscreen fullscreenExit openInNew clear addTask editOutline videoCam voiceCall logout deleteOutline schedule scheduleSend sync checkSingle checkDouble errorOutline",
        ChatyIconThemeId.sculptedVolume to "chats updates profile tasks close check tune chevronRight pin unpin archive lock unlock mute unmute delete reply forward edit copy share cameraFlashOn cameraFlashOff callEnd screenShare screenShareStop speaker earpiece signal messageCheckSingle messageClock pictureInPicture autoAwesome fullscreen fullscreenExit openInNew clear addTask editOutline videoCam voiceCall logout deleteOutline schedule scheduleSend sync checkSingle checkDouble errorOutline",
        ChatyIconThemeId.expressiveInk to "chats updates profile tasks close check tune chevronRight pin unpin archive lock unlock mute unmute delete reply forward edit copy share cameraFlashOn cameraFlashOff gallery callEnd screenShare screenShareStop speaker earpiece signal messageClock pictureInPicture autoAwesome fullscreen fullscreenExit openInNew clear addTask editOutline videoCam voiceCall logout deleteOutline schedule scheduleSend sync checkSingle checkDouble errorOutline musicNote",
        ChatyIconThemeId.signalLanguage to "chats updates profile tasks close check tune chevronRight pin unpin archive lock unlock mute unmute delete reply forward edit copy share cameraFlashOn cameraFlashOff gallery callEnd screenShare screenShareStop speaker earpiece signal messageClock pictureInPicture autoAwesome fullscreen fullscreenExit openInNew clear addTask editOutline videoCam voiceCall logout deleteOutline schedule scheduleSend sync checkSingle checkDouble errorOutline",
    )

    /** Hand-drawn art for the most visible shared icons, keyed "theme:icon"; drawn directly with the theme's weight/caps. */
    internal val handArt: Map<String, List<S>> = mapOf(
        "precisionContour:lock" to listOf(S('s', "R 5 10.5 14 10 1"), S('s', "M8.5 10.5 V7.5 Q8.5 4 12 4 Q15.5 4 15.5 7.5 V10.5"), S('s', "M12 14 V16.5")),
        "monolithicCutout:lock" to listOf(S('f', "R 4.5 10 15 11 3.5"), S('s', "M8 10 V7.5 Q8 4 12 4 Q16 4 16 7.5 V10")),
        "origamiGeometry:lock" to listOf(S('s', "M5 10.5 H19 L17.5 20.5 H6.5 Z"), S('s', "M8.5 10.5 L9 6.5 L12 4 L15 6.5 L15.5 10.5"), S('s', "M12 14 L13.2 16 L12 18 L10.8 16 Z")),
        "organicMotion:lock" to listOf(S('s', "M5.5 14 Q5.5 10.5 12 10.5 Q18.5 10.5 18.5 14 Q18.5 20.5 12 20.5 Q5.5 20.5 5.5 14 Z"), S('s', "M8.5 10.5 Q8.5 4.5 12 4.5 Q15.5 4.5 15.5 10.5"), S('f', "O 12 15 1.3")),
        "pixelPrecision:lock" to listOf(S('s', "M5 10.5 H19 V20.5 H5 Z"), S('s', "M8 10.5 V6 H10 V4 H14 V6 H16 V10.5"), S('f', "R 11 14 2 3 0")),
        "technicalBlueprint:lock" to listOf(S('s', "R 5 10.5 14 10 1"), S('s', "M8 10.5 V8 Q8 4 12 4 Q16 4 16 8 V10.5"), S('s', "M3 15.5 H5"), S('s', "M19 15.5 H21"), S('s', "O 12 15.5 1.2")),
        "sculptedVolume:lock" to listOf(S('t', "R 6.5 11.5 14 10 3"), S('s', "R 5 10.5 14 10 3"), S('s', "M8 10.5 V8 Q8 4 12 4 Q16 4 16 8 V10.5"), S('f', "O 12 15.5 1.4")),
        "expressiveInk:lock" to listOf(S('s', "M8 10.5 Q7.5 3.5 12 4 Q16.5 4.5 16 10.5"), S('s', "M5 11 Q12 9.5 19 11 L18 20.5 Q12 21.5 6 20.5 Z"), S('f', "O 12 15.5 1.2")),
        "signalLanguage:lock" to listOf(S('s', "R 5 10.5 14 10 2.5"), S('s', "M8 10.5 V8 Q8 4 12 4 Q16 4 16 8 V10.5"), S('f', "O 12 15.5 1"), S('s', "M9.5 15.5 Q9.5 13 12 13 Q14.5 13 14.5 15.5")),
        "precisionContour:delete" to listOf(S('s', "M5 7 H19"), S('s', "M9.5 7 V5 H14.5 V7"), S('s', "M6.5 7 L7.5 20 H16.5 L17.5 7")),
        "monolithicCutout:delete" to listOf(S('f', "M6 8 H18 L17 20.5 H7 Z"), S('s', "M4.5 6 H19.5"), S('s', "M9.5 6 V4 H14.5 V6")),
        "origamiGeometry:delete" to listOf(S('s', "M4 7 L12 4 L20 7 Z"), S('s', "M6 7.5 L8 20 H16 L18 7.5"), S('s', "M12 10.5 V17")),
        "organicMotion:delete" to listOf(S('s', "M4.5 7 Q12 4.5 19.5 7"), S('s', "M6.5 8 Q6.5 20.5 12 20.5 Q17.5 20.5 17.5 8"), S('s', "M10 11 V16.5"), S('s', "M14 11 V16.5")),
        "pixelPrecision:delete" to listOf(S('s', "M3.5 7 H20.5"), S('s', "M9 7 V4 H15 V7"), S('s', "M6 7 V20 H18 V7"), S('s', "M10 10 V17"), S('s', "M14 10 V17")),
        "technicalBlueprint:delete" to listOf(S('s', "M5 7 H19"), S('s', "M9.5 7 V5 H14.5 V7"), S('s', "M6.5 7 L7.5 20 H16.5 L17.5 7"), S('s', "M12 9 V18"), S('s', "M3 20 H5")),
        "sculptedVolume:delete" to listOf(S('t', "M7 9 L8 21 H17 L18 9 Z"), S('s', "M4.5 7 H19.5"), S('s', "M9.5 7 V4.5 H14.5 V7"), S('s', "M6.5 7 L7.5 20 H16.5 L17.5 7")),
        "expressiveInk:delete" to listOf(S('s', "M4 7.2 Q12 5.8 20 7.2"), S('s', "M9 6.8 V4.5 H15 V6.8"), S('s', "M6.5 7.8 Q7 20.5 12 20.5 Q17 20.5 17.5 7.8"), S('s', "M10 11 L10.5 17"), S('s', "M14 11 L13.5 17")),
        "signalLanguage:delete" to listOf(S('s', "M5 7 H19"), S('s', "M9.5 7 V5 H14.5 V7"), S('s', "M6.5 7 L7.5 20 H16.5 L17.5 7"), S('s', "M10 11 V16"), S('s', "M14 11 V16"), S('s', "M20 4 Q22 4.2 22.2 6.2")),
        "precisionContour:share" to listOf(S('s', "O 18 5.5 2"), S('s', "O 6 12 2"), S('s', "O 18 18.5 2"), S('s', "M8 11 L16 6.5"), S('s', "M8 13 L16 17.5")),
        "monolithicCutout:share" to listOf(S('f', "O 18 5.5 3"), S('f', "O 6 12 3"), S('f', "O 18 18.5 3"), S('s', "M8 11 L16 6.5"), S('s', "M8 13 L16 17.5")),
        "origamiGeometry:share" to listOf(S('s', "M18 3 L20.5 5.5 L18 8 L15.5 5.5 Z"), S('s', "M6 9.5 L8.5 12 L6 14.5 L3.5 12 Z"), S('s', "M18 16 L20.5 18.5 L18 21 L15.5 18.5 Z"), S('s', "M8.5 11 L15.8 6.8"), S('s', "M8.5 13 L15.8 17.2")),
        "organicMotion:share" to listOf(S('s', "O 18 5.5 2.8"), S('s', "O 6 12 2.8"), S('s', "O 18 18.5 2.8"), S('s', "M8.6 11 Q12 7.5 15.2 6.3"), S('s', "M8.6 13 Q12 16.5 15.2 17.7")),
        "pixelPrecision:share" to listOf(S('s', "R 15.5 3 5 5 0"), S('s', "R 3.5 9.5 5 5 0"), S('s', "R 15.5 16 5 5 0"), S('s', "M8.5 11 L15.5 6.5"), S('s', "M8.5 13 L15.5 17.5")),
        "technicalBlueprint:share" to listOf(S('s', "O 18 5.5 2.2"), S('s', "O 6 12 2.2"), S('s', "O 18 18.5 2.2"), S('s', "M8.2 11 L15.8 6.6"), S('s', "M8.2 13 L15.8 17.4"), S('s', "M1.5 12 H3"), S('s', "M18 1.5 V3"), S('s', "M18 21 V22.5")),
        "sculptedVolume:share" to listOf(S('t', "O 19 6.5 2.8"), S('t', "O 7 13 2.8"), S('t', "O 19 19.5 2.8"), S('s', "O 18 5.5 2.6"), S('s', "O 6 12 2.6"), S('s', "O 18 18.5 2.6"), S('s', "M8.4 11 L15.6 6.8"), S('s', "M8.4 13 L15.6 17.2")),
        "expressiveInk:share" to listOf(S('s', "O 18 5.5 2.4"), S('s', "O 6 12 2.4"), S('s', "O 18 18.5 2.4"), S('s', "M8 11.3 Q12 8.2 16 6.6"), S('s', "M8 12.7 Q12 15.8 16 17.4")),
        "signalLanguage:share" to listOf(S('s', "O 6 12 2.4"), S('s', "O 18 5.5 2.4"), S('s', "O 18 18.5 2.4"), S('s', "M8.4 11 L15.6 6.8"), S('s', "M8.4 13 L15.6 17.2"), S('s', "M21 8.8 Q22.8 5.5 21 2.5")),
        "precisionContour:chats" to listOf(S('s', "M4 6 L6 4 H18 L20 6 V15 L18 17 H11 L6 21 V17 H4 Z"), S('s', "M7 9 H17"), S('s', "M7 12 H14")),
        "precisionContour:checkSingle" to listOf(S('s', "M4 12 L9 17 L20 6"), S('s', "M18 6 H20 V8")),
        "precisionContour:checkDouble" to listOf(S('s', "M2 12 L7 17 L17 6"), S('s', "M10 14 L13 17 L22 7"), S('s', "M20 7 H22 V9")),
        "precisionContour:videoCam" to listOf(S('s', "M3 7 L5 5 H13 L15 7 V17 H3 Z"), S('s', "M17 10 L21 7 V17 L17 14"), S('s', "M6 9 H10")),
        "precisionContour:voiceCall" to listOf(S('s', "M4 4 H9 L11 8 L8 11 L13 16 L16 13 L20 15 V19 L18 21 L14 20 L4 10 Z"), S('s', "M15 4 L20 9"), S('s', "M18 4 H20 V6")),
        "precisionContour:mute" to listOf(S('s', "M3 9 H7 L12 5 V19 L7 15 H3 Z"), S('s', "M16 9 L21 14"), S('s', "M21 9 L16 14")),
        "monolithicCutout:checkSingle" to listOf(S('f', "M3 12 L6 9 L10 13 L18 5 L21 8 L10 19 Z")),
        "monolithicCutout:checkDouble" to listOf(S('f', "M1 12 L4 9 L8 13 L16 5 L19 8 L8 19 Z"), S('f', "M11 14 L13 16 L21 8 L23 10 L13 20 L9 16 Z")),
        "monolithicCutout:videoCam" to listOf(S('f', "R 2 6 13 12 2"), S('f', "M17 9 L22 6 V18 L17 15 Z")),
        "monolithicCutout:voiceCall" to listOf(S('f', "M4 3 H8 L11 8 L8 11 Q10 14 14 16 L17 13 L21 16 V20 L18 21 Q8 20 3 9 Z"), S('f', "M14 3 H17 V5 H20 V8 H22 V11 H19 V9 H17 V7 H14 Z")),
        "monolithicCutout:mute" to listOf(S('f', "M2 9 H7 L13 4 V20 L7 15 H2 Z"), S('f', "M16 8 L19 11 L21 8 L23 10 L21 13 L23 15 L21 17 L19 15 L16 17 L14 15 L17 13 L14 10 Z")),
        "origamiGeometry:chats" to listOf(S('s', "M3 6 L18 3 L21 15 L12 17 L7 21 L7 18 L4 17 Z"), S('t', "M3 6 L12 11 L18 3 Z"), S('s', "M7 9 L12 11 L17 8"), S('s', "M8 14 L16 12")),
        "origamiGeometry:checkSingle" to listOf(S('f', "M3 11 H7 L10 14 L19 5 L21 8 L10 19 L8 15 Z"), S('t', "M10 14 L19 5 L10 19 Z")),
        "origamiGeometry:checkDouble" to listOf(S('s', "M2 11 L7 18 L17 6 L16 10"), S('s', "M10 13 L13 18 L22 7 L21 11"), S('t', "M2 11 L7 18 L5 13 Z")),
        "origamiGeometry:videoCam" to listOf(S('s', "M3 7 L14 5 L16 17 L5 19 Z"), S('t', "M3 7 L10 12 L14 5 Z"), S('f', "M16 10 L21 6 L22 18 L17 14 Z"), S('s', "M5 9 L10 12 L7 16")),
        "origamiGeometry:voiceCall" to listOf(S('s', "M4 4 L9 3 L12 7 L9 10 L14 15 L17 12 L21 15 L20 20 L16 21 L5 12 L3 7 Z"), S('t', "M4 4 L9 3 L9 10 Z"), S('t', "M14 15 L20 20 L16 21 Z"), S('s', "M15 4 L19 6 L20 10")),
        "origamiGeometry:mute" to listOf(S('s', "M3 9 L7 8 L12 4 L13 20 L7 16 L3 15 Z"), S('t', "M7 8 L12 4 L7 16 Z"), S('s', "M16 8 L22 16"), S('s', "M21 8 L16 16")),
        "organicMotion:chats" to listOf(S('s', "M4 7 Q5 3 12 4 Q21 3 20 12 Q21 18 12 17 Q9 20 5 20 Q7 18 6 17 Q2 16 4 7 Z"), S('s', "M7 9 Q12 7 17 9"), S('s', "M8 13 Q11 14 15 12")),
        "organicMotion:checkSingle" to listOf(S('s', "M4 12 Q7 13 9 17 Q14 11 19 6"), S('f', "O 19 6 1")),
        "organicMotion:checkDouble" to listOf(S('s', "M3 12 Q5 13 7 17 Q12 11 17 7"), S('s', "M10 13 Q12 14 13 17 Q17 11 21 8"), S('f', "O 21 8 1")),
        "organicMotion:videoCam" to listOf(S('s', "M4 7 Q9 4 14 7 Q16 12 14 17 Q8 20 4 17 Q2 12 4 7 Z"), S('s', "M17 10 Q21 6 21 12 Q21 18 17 14"), S('f', "O 7 10 1")),
        "organicMotion:voiceCall" to listOf(S('s', "M6 4 Q10 3 11 7 Q11 9 8 10 Q10 14 14 16 Q16 12 18 14 Q22 16 20 19 Q18 22 13 19 Q6 15 4 9 Q2 5 6 4 Z"), S('s', "M14 4 Q20 4 20 9"), S('s', "M14 7 Q17 7 17 10")),
        "organicMotion:mute" to listOf(S('s', "M3 10 Q3 8 7 9 Q9 5 12 5 Q14 12 12 19 Q9 18 7 15 Q3 16 3 13 Z"), S('s', "M16 9 Q18 11 21 15"), S('s', "M21 9 Q18 13 16 15")),
        "pixelPrecision:checkSingle" to listOf(S('f', "M3 10 H6 V13 H9 V16 H12 V13 H15 V10 H18 V7 H21 V10 H18 V13 H15 V16 H12 V19 H9 V16 H6 V13 H3 Z")),
        "pixelPrecision:checkDouble" to listOf(S('f', "M1 11 H4 V14 H7 V17 H10 V14 H13 V11 H16 V8 H19 V11 H16 V14 H13 V17 H10 V20 H7 V17 H4 V14 H1 Z"), S('f', "M12 14 H15 V17 H18 V14 H21 V11 H23 V14 H21 V17 H18 V20 H15 V17 H12 Z")),
        "pixelPrecision:videoCam" to listOf(S('f', "M3 6 H13 V8 H15 V16 H13 V18 H3 V16 H1 V8 H3 Z"), S('f', "M17 10 H19 V8 H21 V6 H23 V18 H21 V16 H19 V14 H17 Z")),
        "pixelPrecision:voiceCall" to listOf(S('f', "M3 3 H9 V6 H11 V9 H8 V11 H10 V13 H12 V15 H15 V13 H18 V15 H21 V21 H15 V19 H11 V17 H8 V14 H5 V10 H3 Z"), S('f', "M14 3 H18 V5 H20 V7 H22 V11 H20 V8 H18 V6 H14 Z")),
        "pixelPrecision:mute" to listOf(S('f', "M2 9 H6 V7 H8 V5 H10 V3 H12 V21 H10 V19 H8 V17 H6 V15 H2 Z"), S('f', "M15 8 H17 V10 H19 V8 H21 V10 H19 V12 H21 V14 H19 V16 H17 V14 H15 V12 H17 V10 H15 Z")),
        "technicalBlueprint:chats" to listOf(S('s', "M4 5 H19 V17 H11 L6 21 V17 H4 Z"), S('s', "M7 9 H16"), S('s', "M7 12 H14"), S('s', "M2 5 H6"), S('s', "M4 2 V8"), S('s', "M19 15 V20"), S('s', "M17 17 H22")),
        "technicalBlueprint:checkSingle" to listOf(S('s', "M4 12 L9 17 L19 7"), S('s', "M3 14 L6 10"), S('s', "M17 5 L21 9"), S('t', "O 9 17 2")),
        "technicalBlueprint:checkDouble" to listOf(S('s', "M2 12 L7 17 L17 7"), S('s', "M10 14 L13 17 L22 8"), S('s', "M15 5 L19 9"), S('s', "M20 6 L23 9"), S('s', "M7 19 V22")),
        "technicalBlueprint:videoCam" to listOf(S('s', "R 3 6 11 12 0"), S('s', "M16 10 L21 7 V17 L16 14 Z"), S('s', "M1 6 H6"), S('s', "M3 4 V9"), S('s', "M8 9 V15"), S('s', "M6 12 H11"), S('s', "M19 5 V19")),
        "technicalBlueprint:voiceCall" to listOf(S('s', "M4 4 H8 L10 8 L8 11 Q10 14 14 16 L16 13 L20 15 V19 Q18 22 14 20 Q6 16 3 8 Q2 5 4 4 Z"), S('s', "M14 4 Q19 5 20 10"), S('s', "M13 4 H16"), S('s', "M20 8 V12"), S('f', "O 6 6 0.7"), S('f', "O 18 17 0.7")),
        "technicalBlueprint:mute" to listOf(S('s', "M3 9 H7 L12 5 V19 L7 15 H3 Z"), S('s', "M16 9 L21 14"), S('s', "M21 9 L16 14"), S('s', "M2 7 H5"), S('s', "M2 17 H5"), S('s', "M15 12 H23")),
        "sculptedVolume:chats" to listOf(S('t', "R 5 6 16 12 3"), S('t', "M8 18 V22 L13 18 Z"), S('s', "M5 4 H17 Q19 4 19 7 V14 Q19 16 17 16 H11 L6 20 V16 H5 Q3 16 3 14 V7 Q3 4 5 4 Z"), S('s', "M7 9 H15"), S('s', "M7 12 H13")),
        "sculptedVolume:checkSingle" to listOf(S('t', "M4 13 L9 18 L20 7 L22 9 L9 22 L2 15 Z"), S('s', "M4 11 L9 16 L19 6"), S('s', "M9 16 V19")),
        "sculptedVolume:checkDouble" to listOf(S('t', "M3 13 L7 17 L16 8 L18 10 L7 21 L1 15 Z"), S('s', "M3 11 L7 16 L16 6"), S('s', "M10 13 L13 16 L21 8"), S('t', "M13 16 L21 8 L23 10 L13 20 L10 17 Z")),
        "sculptedVolume:videoCam" to listOf(S('t', "R 5 8 11 11 2"), S('t', "M18 11 L22 8 V19 L18 16 Z"), S('s', "R 3 6 11 11 2"), S('s', "M16 10 L20 7 V17 L16 14 Z"), S('s', "M5 8 H10")),
        "sculptedVolume:voiceCall" to listOf(S('t', "M6 5 H10 L12 9 L10 12 L15 17 L18 15 L22 18 L21 22 H17 L7 13 L5 8 Z"), S('s', "M4 3 H8 L10 7 L8 10 Q10 14 14 16 L16 13 L20 15 V19 Q18 21 15 20 Q6 16 3 8 Z"), S('s', "M15 4 Q20 4 20 9"), S('f', "O 6 6 1")),
        "sculptedVolume:mute" to listOf(S('t', "M5 11 H9 L14 7 V21 L9 17 H5 Z"), S('s', "M3 9 H7 L12 5 V19 L7 15 H3 Z"), S('t', "M18 10 L23 15 L21 17 L16 12 Z"), S('s', "M16 9 L21 14"), S('s', "M21 9 L16 14")),
        "expressiveInk:chats" to listOf(S('s', "M4 7 Q11 2 20 6 L19 16 Q15 18 11 16 L5 21 L6 16 Q2 13 4 7 Z"), S('s', "M6 9 Q12 8 17 9"), S('s', "M7 13 Q11 12 14 13"), S('s', "M4 6 Q10 3 17 5")),
        "expressiveInk:checkSingle" to listOf(S('s', "M3 11 Q6 12 9 17 Q13 11 20 5"), S('f', "M8 15 L10 17 L9 20 L6 15 Z"), S('s', "M11 14 Q15 9 19 7")),
        "expressiveInk:checkDouble" to listOf(S('s', "M2 11 Q5 12 7 17 Q11 11 17 5"), S('s', "M10 12 Q12 14 13 17 Q17 10 22 6"), S('s', "M3 12 L6 16"), S('s', "M15 14 L21 8")),
        "expressiveInk:videoCam" to listOf(S('s', "M3 7 Q8 5 14 7 L14 17 Q8 18 3 17 Z"), S('s', "M17 10 L21 6 Q20 12 21 18 L17 14"), S('s', "M4 8 Q8 7 12 8"), S('f', "M3 15 L6 16 L4 19 Z")),
        "expressiveInk:voiceCall" to listOf(S('s', "M5 4 Q10 3 10 8 L8 10 Q11 15 14 16 Q17 12 19 15 Q23 19 18 20 Q9 19 4 10 Q2 6 5 4 Z"), S('s', "M14 4 Q21 3 21 10"), S('s', "M14 7 Q18 7 18 10"), S('s', "M5 7 Q4 12 9 16")),
        "expressiveInk:mute" to listOf(S('s', "M3 9 Q5 8 7 9 L12 4 Q11 12 12 20 L7 15 Q4 16 3 14 Z"), S('s', "M16 8 Q18 12 22 16"), S('s', "M22 8 Q19 13 16 16"), S('s', "M17 9 L21 15")),
        "signalLanguage:chats" to listOf(S('s', "M4 7 Q4 5 6 5 H16 Q18 5 18 7 V15 Q18 17 16 17 H10 L6 21 V17 H4 Z"), S('f', "O 8 11 1"), S('f', "O 12 11 1"), S('s', "M20 5 Q23 7 21 11"), S('s', "M18 2 Q23 4 22 10")),
        "signalLanguage:checkSingle" to listOf(S('s', "M4 12 L9 17 L18 8"), S('f', "O 18 8 1.3"), S('s', "M18 4 Q22 5 22 9"), S('s', "M18 1.5 Q23 3 22 11")),
        "signalLanguage:checkDouble" to listOf(S('s', "M2 12 L7 17 L16 8"), S('s', "M10 14 L13 17 L21 9"), S('f', "O 16 8 1"), S('f', "O 21 9 1"), S('s', "M16 4 Q21 3 22 7")),
        "signalLanguage:videoCam" to listOf(S('s', "R 2.5 7 11 10 2"), S('s', "M15 10 L18 8 V16 L15 14 Z"), S('f', "O 7 12 1.5"), S('s', "M20 9 Q23 12 20 15"), S('s', "M20.5 6 Q24 12 20.5 18")),
        "signalLanguage:voiceCall" to listOf(S('s', "M4 4 H8 L10 8 L8 10 Q10 14 14 16 L16 14 L20 16 V19 Q18 21 15 20 Q6 16 3 8 Z"), S('f', "O 15 6 1"), S('s', "M15 3 Q21 3 21 9"), S('s', "M15 1.5 Q23 2 22 11")),
        "signalLanguage:mute" to listOf(S('s', "M3 9 H7 L12 5 V19 L7 15 H3 Z"), S('s', "M16 10 L20 14"), S('s', "M20 10 L16 14"), S('s', "M16 6 Q22 6 22 12"), S('s', "M16 3 Q23 3 22 15")),
    )

    private val sets: Map<ChatyIconThemeId, Set<String>> = overrides.mapValues { it.value.split(' ').toSet() }

    fun applies(theme: ChatyIconThemeId, icon: ChatyIconId) = sets[theme]?.contains(icon.name) == true

    private class Poly(val pts: List<DoubleArray>, val closed: Boolean)

    private val tokens = Regex("[MLHVQZ]|-?\\d+\\.?\\d*")

    private fun flatten(d: String, st: Style): List<Poly> {
        val parts = d.split(' ')
        when (parts[0]) {
            "O" -> {
                val cx = parts[1].toDouble(); val cy = parts[2].toDouble(); val r = parts[3].toDouble()
                val pts = (0 until st.nCirc).map { val a = 2 * Math.PI * it / st.nCirc + (if (st.nCirc <= 12) Math.PI / st.nCirc else 0.0); doubleArrayOf(cx + r * Math.cos(a), cy + r * Math.sin(a)) }
                return listOf(Poly(pts, true))
            }
            "R" -> {
                val x = parts[1].toDouble(); val y = parts[2].toDouble(); val w = parts[3].toDouble(); val h = parts[4].toDouble(); val r = parts[5].toDouble()
                val pts = ArrayList<DoubleArray>()
                val corners = listOf(doubleArrayOf(x + w - r, y + r, -90.0), doubleArrayOf(x + w - r, y + h - r, 0.0), doubleArrayOf(x + r, y + h - r, 90.0), doubleArrayOf(x + r, y + r, 180.0))
                for (c in corners) for (k in 0..st.nCorner) {
                    val a = Math.toRadians(c[2] + 90.0 * k / st.nCorner)
                    pts.add(doubleArrayOf(c[0] + r * Math.cos(a), c[1] + r * Math.sin(a)))
                }
                return listOf(Poly(pts, true))
            }
        }
        val out = ArrayList<Poly>()
        var cur = ArrayList<DoubleArray>()
        val t = tokens.findAll(d).map { it.value }.toList()
        var i = 0; var cmd = ' '; var cx = 0.0; var cy = 0.0
        fun n() = t[i++].toDouble()
        while (i < t.size) {
            if (t[i][0].isLetter()) cmd = t[i++][0]
            when (cmd) {
                'M' -> { if (cur.size > 1) out.add(Poly(cur, false)); cur = ArrayList(); cx = n(); cy = n(); cur.add(doubleArrayOf(cx, cy)); cmd = 'L' }
                'L' -> { cx = n(); cy = n(); cur.add(doubleArrayOf(cx, cy)) }
                'H' -> { cx = n(); cur.add(doubleArrayOf(cx, cy)) }
                'V' -> { cy = n(); cur.add(doubleArrayOf(cx, cy)) }
                'Q' -> {
                    val x1 = n(); val y1 = n(); val x2 = n(); val y2 = n()
                    for (k in 1..st.nQ) { val u = k.toDouble() / st.nQ; val v = 1 - u
                        cur.add(doubleArrayOf(v * v * cx + 2 * v * u * x1 + u * u * x2, v * v * cy + 2 * v * u * y1 + u * u * y2)) }
                    cx = x2; cy = y2
                }
                'Z' -> { if (cur.size > 1) out.add(Poly(cur, true)); cur = ArrayList() }
                else -> error("bad path command in '$d'")
            }
        }
        if (cur.size > 1) out.add(Poly(cur, false))
        return out
    }

    private fun chaikin(p: Poly, rounds: Int): Poly {
        var pts = p.pts
        repeat(rounds) {
            val n = ArrayList<DoubleArray>()
            val m = pts.size
            if (!p.closed) n.add(pts[0])
            val last = if (p.closed) m else m - 1
            for (i in 0 until last) {
                val a = pts[i]; val b = pts[(i + 1) % m]
                n.add(doubleArrayOf(0.75 * a[0] + 0.25 * b[0], 0.75 * a[1] + 0.25 * b[1]))
                n.add(doubleArrayOf(0.25 * a[0] + 0.75 * b[0], 0.25 * a[1] + 0.75 * b[1]))
            }
            if (!p.closed) n.add(pts[m - 1])
            pts = n
        }
        return Poly(pts, p.closed)
    }

    private fun toPath(p: Poly, dx: Double = 0.0, dy: Double = 0.0, snap: Double = 0.0): Path {
        val path = Path()
        p.pts.forEachIndexed { i, q ->
            var x = q[0]; var y = q[1]
            if (snap > 0) { x = Math.round(x / snap) * snap; y = Math.round(y / snap) * snap }
            if (i == 0) path.moveTo(x + dx, y + dy) else path.lineTo(x + dx, y + dy)
        }
        if (p.closed) path.close()
        return path
    }

    private fun drawShapes(canvas: IconCanvas, size: Double, list: List<S>, st: Style, color: Color) {
        canvas.save()
        canvas.scale(size / 24.0, size / 24.0)
        val stroke = Paint().apply { this.color = color; style = PaintingStyle.stroke; strokeWidth = st.w; strokeCap = st.cap; strokeJoin = st.join; isAntiAlias = true }
        val fill = Paint().apply { this.color = color; style = PaintingStyle.fill; isAntiAlias = true }
        val tint = Paint().apply { this.color = color.withValues(color.a * 0.2); style = PaintingStyle.fill; isAntiAlias = true }
        for (sh in list) {
            val paint = when (sh.role) { 's' -> stroke; 'f' -> fill; else -> tint }
            val parts = sh.d.split(' ')
            when (parts[0]) {
                "O" -> canvas.drawCircle(Offset(parts[1].toDouble(), parts[2].toDouble()), parts[3].toDouble(), paint)
                "R" -> canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble(), parts[4].toDouble()), Radius.circular(parts[5].toDouble())), paint)
                else -> canvas.drawPath(ChatyBaseOutlines.parsePath(sh.d), paint)
            }
        }
        canvas.restore()
    }

    fun paint(canvas: IconCanvas, sizeWidth: Double, icon: ChatyIconId, theme: ChatyIconThemeId, color: Color): Boolean {
        if (!applies(theme, icon)) return false
        val st = styles[theme] ?: return false
        handArt["${theme.name}:${icon.name}"]?.let { drawShapes(canvas, sizeWidth, it, st, color); return true }
        val shapes = ChatyBaseOutlines.shapes[icon] ?: return false
        canvas.save()
        canvas.scale(sizeWidth / 24.0, sizeWidth / 24.0)
        fun stroke(a: Double = color.a, w: Double = st.w) = Paint().apply {
            this.color = color.withValues(a); style = PaintingStyle.stroke; strokeWidth = w; strokeCap = st.cap; strokeJoin = st.join; isAntiAlias = true
        }
        fun fill(a: Double = color.a) = Paint().apply { this.color = color.withValues(a); style = PaintingStyle.fill; isAntiAlias = true }
        for (s in shapes) {
            val polys = flatten(s.d, st).map { if (st.chaikin > 0) chaikin(it, st.chaikin) else it }
            for (p in polys) {
                if (st.plate > 0 && p.closed) canvas.drawPath(toPath(p, st.plate, st.plate, st.snap), fill(color.a * 0.3))
                if (st.fillClosed && p.closed && s.role != 'f') canvas.drawPath(toPath(p, 0.0, 0.0, st.snap), fill(color.a * 0.18))
                if (s.role == 'f') canvas.drawPath(toPath(p, 0.0, 0.0, st.snap), fill())
                else {
                    if (st.dbl > 0) canvas.drawPath(toPath(p, st.dbl, -st.dbl * 0.6, st.snap), stroke(color.a * 0.55, st.w * 0.6))
                    canvas.drawPath(toPath(p, 0.0, 0.0, st.snap), stroke())
                }
            }
        }
        if (st.ticks) {
            val tp = Paint().apply { this.color = color.withValues(color.a * 0.6); style = PaintingStyle.stroke; strokeWidth = 0.7; strokeCap = StrokeCap.butt; isAntiAlias = true }
            for ((x, y) in listOf(1.0 to 1.0, 23.0 to 1.0, 1.0 to 23.0, 23.0 to 23.0)) {
                val sx = if (x < 12) 1.0 else -1.0; val sy = if (y < 12) 1.0 else -1.0
                canvas.drawLine(Offset(x, y), Offset(x + 2.2 * sx, y), tp); canvas.drawLine(Offset(x, y), Offset(x, y + 2.2 * sy), tp)
            }
        }
        if (st.pulse) {
            val pp = stroke(color.a * 0.6, 1.2)
            canvas.drawPath(Path().apply { moveTo(19.0, 2.2); quadraticBezierTo(22.0, 2.4, 22.2, 5.4) }, pp)
            canvas.drawPath(Path().apply { moveTo(17.0, 0.9); quadraticBezierTo(23.1, 1.0, 23.2, 7.2) }, pp)
        }
        canvas.restore()
        return true
    }
}
