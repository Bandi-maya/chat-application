package com.example.chat.ui.icons

/**
 * Base outlines: an original rounded-outline icon pack on a 24-unit grid (1.8 stroke, round caps/joins),
 * drawn from scratch for this app. No third-party path data. Data-only; drawing reuses [IconCanvas].
 *
 * Shape grammar (absolute coordinates): path commands M L H V Q Z; `O cx cy r` circle; `R x y w h r` rounded rect.
 * Roles: s = stroke, f = fill, t = tint fill at 20 percent of the icon colour.
 */
internal data class S(val role: Char, val d: String)

internal object ChatyBaseOutlines {
    const val GRID = 24.0
    const val STROKE = 1.8

    /** Every [ChatyIconId] has an entry (asserted by ChatyThemeStylesTest); no fallback to another theme. */
    internal val shapes: Map<ChatyIconId, List<S>> = mapOf(
        ChatyIconId.chats to listOf(S('s', "M5 4 H19 Q21 4 21 6 V15 Q21 17 19 17 H11 L7 20.5 V17 H5 Q3 17 3 15 V6 Q3 4 5 4 Z")),
        ChatyIconId.updates to listOf(S('s', "O 12 12 8.5"), S('f', "O 12 12 3")),
        ChatyIconId.calls to listOf(S('s', "M6.8 3.6 L9.3 3.3 L10.6 7 L8.9 8.6 Q10.5 12 14 15.1 L15.8 13.5 L19.6 14.9 L19.4 17.4 Q19.2 20 16.4 19.8 Q9 19 4.7 11 Q3.6 5.8 6.8 3.6 Z")),
        ChatyIconId.profile to listOf(S('s', "O 12 8 3.8"), S('s', "M4.5 20 Q4.5 14.5 12 14.5 Q19.5 14.5 19.5 20")),
        ChatyIconId.tasks to listOf(S('s', "R 5 4.5 14 16.5 2.5"), S('s', "R 9 2.5 6 3.5 1.2"), S('s', "M8.8 13 L11 15.2 L15.4 10.6")),
        ChatyIconId.groups to listOf(S('s', "O 9 8.5 3"), S('s', "M3.5 19 Q3.5 14 9 14 Q14.5 14 14.5 19"), S('s', "O 17 9.5 2.4"), S('s', "M16.2 14.2 Q20.5 14.2 20.5 18.5")),
        ChatyIconId.desktop to listOf(S('s', "R 3 4.5 18 12 2"), S('s', "M9 20 H15"), S('s', "M12 16.5 V20")),
        ChatyIconId.more to listOf(S('f', "O 12 5 1.7"), S('f', "O 12 12 1.7"), S('f', "O 12 19 1.7")),
        ChatyIconId.moreOptions to listOf(S('f', "O 12 5 1.7"), S('f', "O 12 12 1.7"), S('f', "O 12 19 1.7")),
        ChatyIconId.search to listOf(S('s', "O 10.5 10.5 6.5"), S('s', "M15.5 15.5 L20 20")),
        ChatyIconId.settings to listOf(S('s', "M18.86 10.61 L21.13 10.90 L21.13 13.10 L18.86 13.39 L17.83 15.87 L19.24 17.68 L17.68 19.24 L15.87 17.83 L13.39 18.86 L13.10 21.13 L10.90 21.13 L10.61 18.86 L8.13 17.83 L6.32 19.24 L4.76 17.68 L6.17 15.87 L5.14 13.39 L2.87 13.10 L2.87 10.90 L5.14 10.61 L6.17 8.13 L4.76 6.32 L6.32 4.76 L8.13 6.17 L10.61 5.14 L10.90 2.87 L13.10 2.87 L13.39 5.14 L15.87 6.17 L17.68 4.76 L19.24 6.32 L17.83 8.13 Z"), S('s', "O 12 12 2.8")),
        ChatyIconId.close to listOf(S('s', "M6 6 L18 18"), S('s', "M18 6 L6 18")),
        ChatyIconId.check to listOf(S('s', "M5 12.5 L10 17.5 L19 7")),
        ChatyIconId.tune to listOf(S('s', "M4 8 H6.6"), S('s', "M11.4 8 H20"), S('s', "M4 16 H12.6"), S('s', "M17.4 16 H20"), S('s', "O 9 8 2.4"), S('s', "O 15 16 2.4")),
        ChatyIconId.chevronRight to listOf(S('s', "M9.5 5.5 L16 12 L9.5 18.5")),
        ChatyIconId.back to listOf(S('s', "M19.5 12 H5"), S('s', "M11.5 5.5 L5 12 L11.5 18.5")),
        ChatyIconId.send to listOf(S('s', "M20.5 3.5 L3.5 10.5 L10.5 13.5 L13.5 20.5 Z"), S('s', "M10.5 13.5 L20.5 3.5")),
        ChatyIconId.emoji to listOf(S('s', "O 12 12 8.5"), S('s', "M8.3 14 Q12 17.8 15.7 14"), S('f', "O 9 10 1.1"), S('f', "O 15 10 1.1")),
        ChatyIconId.attachment to listOf(S('s', "M17.5 11 L11 17.5 Q8.5 19.8 6.4 17.6 Q4.3 15.4 6.6 13.1 L13.2 6.5 Q14.9 4.8 16.7 6.6 Q18.4 8.3 16.7 10 L10.3 16.4")),
        ChatyIconId.attachmentPlus to listOf(S('s', "O 12 12 8.5"), S('s', "M12 8 V16"), S('s', "M8 12 H16")),
        ChatyIconId.microphone to listOf(S('s', "R 9 3 6 11 3"), S('s', "M5.5 11.5 Q5.5 17.5 12 17.5 Q18.5 17.5 18.5 11.5"), S('s', "M12 17.5 V21")),
        ChatyIconId.microphoneOff to listOf(S('s', "R 9 3 6 11 3"), S('s', "M5.5 11.5 Q5.5 17.5 12 17.5 Q18.5 17.5 18.5 11.5"), S('s', "M12 17.5 V21"), S('s', "M4 4 L20 20")),
        ChatyIconId.pin to listOf(S('s', "M9 3.5 H15 L14 9 L17 12.5 H7 L10 9 Z"), S('s', "M12 12.5 V20.5")),
        ChatyIconId.unpin to listOf(S('s', "M9 3.5 H15 L14 9 L17 12.5 H7 L10 9 Z"), S('s', "M12 12.5 V20.5"), S('s', "M4 4 L20 20")),
        ChatyIconId.archive to listOf(S('s', "R 3.5 4 17 4.5 1.2"), S('s', "M5 8.5 V17 Q5 19 7 19 H17 Q19 19 19 17 V8.5"), S('s', "M10 12.5 H14")),
        ChatyIconId.lock to listOf(S('s', "R 5 10.5 14 10 2.5"), S('s', "M8 10.5 V8 Q8 4 12 4 Q16 4 16 8 V10.5"), S('f', "O 12 15.5 1.4")),
        ChatyIconId.unlock to listOf(S('s', "R 5 10.5 14 10 2.5"), S('s', "M8 10.5 V8 Q8 4 12 4 Q15 4 15.8 6.5"), S('f', "O 12 15.5 1.4")),
        ChatyIconId.mute to listOf(S('s', "M4.5 9.5 H8 L12.5 5.5 V18.5 L8 14.5 H4.5 Z"), S('s', "M16 9.5 L21 14.5"), S('s', "M21 9.5 L16 14.5")),
        ChatyIconId.unmute to listOf(S('s', "M4.5 9.5 H8 L12.5 5.5 V18.5 L8 14.5 H4.5 Z"), S('s', "M16 9 Q18 12 16 15"), S('s', "M18.8 6.5 Q22.3 12 18.8 17.5")),
        ChatyIconId.speaker to listOf(S('s', "M4.5 9.5 H8 L12.5 5.5 V18.5 L8 14.5 H4.5 Z"), S('s', "M16 9 Q18 12 16 15"), S('s', "M18.8 6.5 Q22.3 12 18.8 17.5")),
        ChatyIconId.favourite to listOf(S('s', "M12 20 Q3.5 14 3.5 8.6 Q3.5 4.6 7.3 4.6 Q10.2 4.6 12 7.4 Q13.8 4.6 16.7 4.6 Q20.5 4.6 20.5 8.6 Q20.5 14 12 20 Z")),
        ChatyIconId.favouriteFilled to listOf(S('f', "M12 20 Q3.5 14 3.5 8.6 Q3.5 4.6 7.3 4.6 Q10.2 4.6 12 7.4 Q13.8 4.6 16.7 4.6 Q20.5 4.6 20.5 8.6 Q20.5 14 12 20 Z"), S('s', "M12 20 Q3.5 14 3.5 8.6 Q3.5 4.6 7.3 4.6 Q10.2 4.6 12 7.4 Q13.8 4.6 16.7 4.6 Q20.5 4.6 20.5 8.6 Q20.5 14 12 20 Z")),
        ChatyIconId.delete to listOf(S('s', "M4.5 6.5 H19.5 M9 6.5 V4.5 H15 V6.5"), S('s', "M6.5 6.5 L7.5 19 Q7.6 20.5 9 20.5 H15 Q16.4 20.5 16.5 19 L17.5 6.5"), S('s', "M10 10.5 V16.5"), S('s', "M14 10.5 V16.5")),
        ChatyIconId.deleteOutline to listOf(S('s', "M4.5 6.5 H19.5 M9 6.5 V4.5 H15 V6.5"), S('s', "M6.5 6.5 L7.5 19 Q7.6 20.5 9 20.5 H15 Q16.4 20.5 16.5 19 L17.5 6.5")),
        ChatyIconId.reply to listOf(S('s', "M10 6 L4 11.5 L10 17"), S('s', "M4 11.5 H14 Q20 11.5 20 17.5 V19")),
        ChatyIconId.forward to listOf(S('s', "M14 6 L20 11.5 L14 17"), S('s', "M20 11.5 H10 Q4 11.5 4 17.5 V19")),
        ChatyIconId.edit to listOf(S('s', "M4 20 L4.8 15.8 L16 4.6 Q17 3.6 18.2 4.6 L19.4 5.8 Q20.4 7 19.4 8 L8.2 19.2 Z"), S('s', "M13.5 7 L17 10.5")),
        ChatyIconId.copy to listOf(S('s', "R 8.5 8.5 11.5 11.5 2.2"), S('s', "M15.5 8.5 V6.5 Q15.5 4.5 13.5 4.5 H6.5 Q4.5 4.5 4.5 6.5 V13.5 Q4.5 15.5 6.5 15.5 H8.5")),
        ChatyIconId.share to listOf(S('s', "O 18 5.5 2.5"), S('s', "O 6 12 2.5"), S('s', "O 18 18.5 2.5"), S('s', "M8.2 10.8 L15.8 6.7"), S('s', "M8.2 13.2 L15.8 17.3")),
        ChatyIconId.camera to listOf(S('s', "M4 8 Q4 6.5 5.5 6.5 H8 L9.5 4.5 H14.5 L16 6.5 H18.5 Q20 6.5 20 8 V17 Q20 18.5 18.5 18.5 H5.5 Q4 18.5 4 17 Z"), S('s', "O 12 12.5 3.6")),
        ChatyIconId.cameraFlip to listOf(S('s', "M5 11 Q5.5 6 11 5.5 L14 5.5"), S('s', "M12 3.2 L14.4 5.5 L12 7.8"), S('s', "M19 13 Q18.5 18 13 18.5 L10 18.5"), S('s', "M12 16.2 L9.6 18.5 L12 20.8")),
        ChatyIconId.cameraShutter to listOf(S('s', "O 12 12 8.8"), S('f', "O 12 12 5")),
        ChatyIconId.cameraFlashOn to listOf(S('s', "M13.6 3 L5.6 13.5 H11 L10 21 L18.4 10 H13 Z")),
        ChatyIconId.cameraFlashOff to listOf(S('s', "M13.6 3 L5.6 13.5 H11 L10 21 L18.4 10 H13 Z"), S('s', "M4 4 L20 20")),
        ChatyIconId.gallery to listOf(S('s', "R 3.5 4.5 17 15 2.5"), S('s', "M4 17.5 L9.5 12.5 L13 15.5 L16 12.5 L20 16.5"), S('f', "O 9 9.5 1.8")),
        ChatyIconId.callAudio to listOf(S('s', "M6.8 3.6 L9.3 3.3 L10.6 7 L8.9 8.6 Q10.5 12 14 15.1 L15.8 13.5 L19.6 14.9 L19.4 17.4 Q19.2 20 16.4 19.8 Q9 19 4.7 11 Q3.6 5.8 6.8 3.6 Z")),
        ChatyIconId.callVideo to listOf(S('s', "R 3.5 6.5 12 11 2.5"), S('s', "M15.5 10 L20.5 7 V17 L15.5 14")),
        ChatyIconId.videoCam to listOf(S('s', "R 3.5 6.5 12 11 2.5"), S('s', "M15.5 10 L20.5 7 V17 L15.5 14")),
        ChatyIconId.callVideoOff to listOf(S('s', "R 3.5 6.5 12 11 2.5"), S('s', "M15.5 10 L20.5 7 V17 L15.5 14"), S('s', "M4 4 L20 20")),
        ChatyIconId.callEnd to listOf(S('s', "M3 14 Q12 8.5 21 14 L20 17 L16.5 16 V13.8 Q12 12.2 7.5 13.8 V16 L4 17 Z")),
        ChatyIconId.cameraSwitch to listOf(S('s', "M6 9 H8 L9 7.5 H15 L16 9 H18 Q19.5 9 19.5 10.5 V16 Q19.5 17.5 18 17.5 H6 Q4.5 17.5 4.5 16 V10.5 Q4.5 9 6 9 Z"), S('s', "M9.6 13.6 Q10 11.6 12 11.6 Q13.2 11.6 14 12.4"), S('s', "M14.4 12.8 Q14 14.8 12 14.8 Q10.8 14.8 10 14")),
        ChatyIconId.screenShare to listOf(S('s', "R 3 5 18 11.5 2"), S('s', "M9 20 H15"), S('s', "M12 16.5 V20"), S('s', "M12 13 V8.5"), S('s', "M9.8 10.6 L12 8.4 L14.2 10.6")),
        ChatyIconId.screenShareStop to listOf(S('s', "R 3 5 18 11.5 2"), S('s', "M9 20 H15"), S('s', "M12 16.5 V20"), S('s', "M9.5 8 L14.5 13"), S('s', "M14.5 8 L9.5 13")),
        ChatyIconId.earpiece to listOf(S('s', "R 7.5 2.5 9 19 2.5"), S('s', "M10.5 6 H13.5"), S('f', "O 12 18 1.2")),
        ChatyIconId.signal to listOf(S('s', "M5 19 V15"), S('s', "M10 19 V11"), S('s', "M15 19 V7"), S('s', "M20 19 V3.5")),
        ChatyIconId.messageCheckSingle to listOf(S('s', "M5.5 12.5 L10 17 L18.5 7.5")),
        ChatyIconId.messageCheckDouble to listOf(S('s', "M2.5 12.5 L7 17 L15 8"), S('s', "M9 15.8 L10.2 17 L20 7")),
        ChatyIconId.checkDouble to listOf(S('s', "M2.5 12.5 L7 17 L15 8"), S('s', "M9 15.8 L10.2 17 L20 7")),
        ChatyIconId.checkSingle to listOf(S('s', "M5 12.5 L10 17.5 L19 7")),
        ChatyIconId.messageClock to listOf(S('s', "O 12 12 8.5"), S('s', "M12 7.5 V12 L15 14")),
        ChatyIconId.schedule to listOf(S('s', "O 12 12 8.5"), S('s', "M12 7 V12 L8.8 14.4")),
        ChatyIconId.pictureInPicture to listOf(S('s', "R 3 5 18 14 2.5"), S('t', "R 11.5 11.5 7 5.5 1.2"), S('s', "R 11.5 11.5 7 5.5 1.2")),
        ChatyIconId.autoAwesome to listOf(S('s', "M10 4 Q10.6 9.4 16 10 Q10.6 10.6 10 16 Q9.4 10.6 4 10 Q9.4 9.4 10 4 Z"), S('s', "M17.5 14.5 Q17.8 17.2 20.5 17.5 Q17.8 17.8 17.5 20.5 Q17.2 17.8 14.5 17.5 Q17.2 17.2 17.5 14.5 Z")),
        ChatyIconId.fullscreen to listOf(S('s', "M4 9 V5.5 Q4 4 5.5 4 H9"), S('s', "M15 4 H18.5 Q20 4 20 5.5 V9"), S('s', "M20 15 V18.5 Q20 20 18.5 20 H15"), S('s', "M9 20 H5.5 Q4 20 4 18.5 V15")),
        ChatyIconId.fullscreenExit to listOf(S('s', "M9 4 V7.5 Q9 9 7.5 9 H4"), S('s', "M20 9 H16.5 Q15 9 15 7.5 V4"), S('s', "M15 20 V16.5 Q15 15 16.5 15 H20"), S('s', "M4 15 H7.5 Q9 15 9 16.5 V20")),
        ChatyIconId.openInNew to listOf(S('s', "M13 4 H20 V11"), S('s', "M20 4 L11 13"), S('s', "M18 14 V18 Q18 20 16 20 H6 Q4 20 4 18 V8 Q4 6 6 6 H10")),
        ChatyIconId.clear to listOf(S('s', "O 12 12 8.5"), S('s', "M9 9 L15 15"), S('s', "M15 9 L9 15")),
        ChatyIconId.addTask to listOf(S('s', "R 5 4.5 14 16.5 2.5"), S('s', "R 9 2.5 6 3.5 1.2"), S('s', "M12 10.5 V16.5"), S('s', "M9 13.5 H15")),
        ChatyIconId.editOutline to listOf(S('s', "M11 5 H6.5 Q4.5 5 4.5 7 V17.5 Q4.5 19.5 6.5 19.5 H17 Q19 19.5 19 17.5 V13"), S('s', "M16.5 4.5 L19.5 7.5 L11.5 15.5 L8 16 L8.5 12.5 Z")),
        ChatyIconId.voiceCall to listOf(S('s', "M6.8 3.6 L9.3 3.3 L10.6 7 L8.9 8.6 Q10.5 12 14 15.1 L15.8 13.5 L19.6 14.9 L19.4 17.4 Q19.2 20 16.4 19.8 Q9 19 4.7 11 Q3.6 5.8 6.8 3.6 Z"), S('s', "M14.5 5 Q18.5 5.5 19 9.5"), S('s', "M14.5 8 Q16.5 8.3 16.8 10.3")),
        ChatyIconId.logout to listOf(S('s', "M10 4.5 H6.5 Q4.5 4.5 4.5 6.5 V17.5 Q4.5 19.5 6.5 19.5 H10"), S('s', "M15 8 L19.5 12 L15 16"), S('s', "M19.5 12 H9.5")),
        ChatyIconId.scheduleSend to listOf(S('s', "M19.5 4.5 L4 10.5 L9 12.5 L11.2 17.5 Z"), S('s', "M9 12.5 L19.5 4.5"), S('s', "O 17 17 4"), S('s', "M17 15 V17 L18.4 17.9")),
        ChatyIconId.sync to listOf(S('s', "M19 11 Q18 5.5 12 5.5 Q8 5.5 5.5 8.5"), S('s', "M5.5 4.5 V8.5 H9.5"), S('s', "M5 13 Q6 18.5 12 18.5 Q16 18.5 18.5 15.5"), S('s', "M18.5 19.5 V15.5 H14.5")),
        ChatyIconId.errorOutline to listOf(S('s', "O 12 12 8.5"), S('s', "M12 7.5 V13"), S('f', "O 12 16.6 1.1")),
        ChatyIconId.pause to listOf(S('f', "R 6.5 5 3.8 14 1.4"), S('f', "R 13.7 5 3.8 14 1.4")),
        ChatyIconId.playArrow to listOf(S('f', "M8 5.5 Q8 4.2 9.2 4.9 L19 11 Q20 12 19 13 L9.2 19.1 Q8 19.8 8 18.5 Z")),
        ChatyIconId.musicNote to listOf(S('s', "M9 18 V6 L19 4 V16"), S('s', "O 6.5 18 2.5"), S('s', "O 16.5 16 2.5")),
    )

    private val tokens = Regex("[MLHVQZ]|-?\\d+\\.?\\d*")

    internal fun parsePath(d: String): Path {
        val p = Path()
        val t = tokens.findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = ' '
        var cx = 0.0
        var cy = 0.0
        fun n() = t[i++].toDouble()
        while (i < t.size) {
            if (t[i][0].isLetter()) cmd = t[i++][0]
            when (cmd) {
                'M' -> { cx = n(); cy = n(); p.moveTo(cx, cy); cmd = 'L' }
                'L' -> { cx = n(); cy = n(); p.lineTo(cx, cy) }
                'H' -> { cx = n(); p.lineTo(cx, cy) }
                'V' -> { cy = n(); p.lineTo(cx, cy) }
                'Q' -> { val x1 = n(); val y1 = n(); cx = n(); cy = n(); p.quadraticBezierTo(x1, y1, cx, cy) }
                'Z' -> p.close()
                else -> error("bad path command in '$d'")
            }
        }
        return p
    }

    /** Returns false when the pack has no shapes for [icon] (never expected). */
    internal fun paint(canvas: IconCanvas, sizeWidth: Double, icon: ChatyIconId, color: Color): Boolean {
        val list = shapes[icon] ?: return false
        canvas.save()
        canvas.scale(sizeWidth / GRID, sizeWidth / GRID)
        val stroke = Paint().apply {
            this.color = color; style = PaintingStyle.stroke; strokeWidth = STROKE
            strokeCap = StrokeCap.round; strokeJoin = StrokeJoin.round; isAntiAlias = true
        }
        val fill = Paint().apply { this.color = color; style = PaintingStyle.fill; isAntiAlias = true }
        val tint = Paint().apply { this.color = color.withValues(color.a * 0.2); style = PaintingStyle.fill; isAntiAlias = true }
        for (s in list) {
            val paint = when (s.role) { 's' -> stroke; 'f' -> fill; else -> tint }
            val parts = s.d.split(' ')
            when (parts[0]) {
                "O" -> canvas.drawCircle(Offset(parts[1].toDouble(), parts[2].toDouble()), parts[3].toDouble(), paint)
                "R" -> canvas.drawRRect(
                    RRect.fromRectAndRadius(
                        Rect.fromLTWH(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble(), parts[4].toDouble()),
                        Radius.circular(parts[5].toDouble()),
                    ),
                    paint,
                )
                else -> canvas.drawPath(parsePath(s.d), paint)
            }
        }
        canvas.restore()
        return true
    }
}
