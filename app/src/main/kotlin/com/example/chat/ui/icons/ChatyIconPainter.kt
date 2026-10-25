package com.example.chat.ui.icons

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Port of lib/ui/core/icons/chaty_icon_theme.dart. */
enum class ChatyIconThemeId(val title: String, val description: String) {
    chatySigil("Chaty Sigil", "Signature Chaty visual identity with balanced geometry and orbital counter-spaces."),
    precisionContour("Precision Contour", "Hairline architectural contour drafting with precision open terminals."),
    monolithicCutout("Monolithic Cutout", "Bold solid silhouettes with stenciled negative-space internal cuts."),
    origamiGeometry("Origami Geometry", "Faceted paper-fold geometry with 45°/60° planar angular creases."),
    organicMotion("Organic Motion", "Fluid droplet contours and asymmetrical pebble-smooth organic curves."),
    pixelPrecision("Pixel Precision", "Authentic 8-bit stepped pixel grid with modular block construction."),
    technicalBlueprint("Technical Blueprint", "Engineered CAD schematic linework with corner ticks and datum crosshairs."),
    sculptedVolume("Sculpted Volume", "Multi-planar dimensional facets implying depth via overlapping planes."),
    expressiveInk("Expressive Ink", "Dynamic brush-pen calligraphy with high-contrast pressure stroke rhythm."),
    signalLanguage("Signal Language", "Telecommunication radar motifs with concentric broadcast pulse waves.");

    companion object {
        private val strip = Regex("[\\s_\\-]+")
        private fun norm(s: String) = s.trim().lowercase().replace(strip, "")

        /** Resolves stored preference strings, including legacy aliases. */
        fun fromString(value: String?): ChatyIconThemeId {
            if (value == null) return chatySigil
            val normalized = norm(value)
            when (normalized) {
                "chatyorbit", "orbit" -> return chatySigil
                "precisionline" -> return precisionContour
                "softgeometry" -> return monolithicCutout
                "liquidmotion" -> return organicMotion
                "sculptedduotone" -> return sculptedVolume
                "signalpulse" -> return signalLanguage
                "neominimal" -> return technicalBlueprint
                "inkflow" -> return expressiveInk
                "prismedge" -> return origamiGeometry
            }
            for (theme in values()) {
                if (normalized == norm(theme.name) || normalized == norm(theme.title)) return theme
            }
            return chatySigil
        }
    }
}

/** Canonical semantic identifiers for all Chaty system icons (chaty_icon_registry.dart). */
enum class ChatyIconId {
    chats, updates, calls, profile, tasks, groups, desktop, more, moreOptions, search, settings, close, check, tune, chevronRight, back, send, emoji, attachment, attachmentPlus, microphone, microphoneOff, pin, unpin, archive, lock, unlock, mute, unmute, favourite, favouriteFilled, delete, reply, forward, edit, copy, share, camera, cameraFlip, cameraShutter, cameraFlashOn, cameraFlashOff, gallery, callAudio, callVideo, callVideoOff, callEnd, cameraSwitch, screenShare, screenShareStop, speaker, earpiece, signal, messageCheckSingle, messageCheckDouble, messageClock, pictureInPicture, autoAwesome, fullscreen, fullscreenExit, openInNew, clear, addTask, editOutline, videoCam, voiceCall, logout, deleteOutline, schedule, scheduleSend, sync, checkSingle, checkDouble, errorOutline, pause, playArrow, musicNote
}

/** Port of _ChatyIconVectorPainter. Pure drawing against IconCanvas so it is JVM-testable. */
object ChatyIconPainter {
    fun paint(canvas: IconCanvas, sizeWidth: Double, icon: ChatyIconId, theme: ChatyIconThemeId, iconColor: Color) {
        val s = sizeWidth
        val scale = s / 24.0
        canvas.save()
        canvas.scale(scale, scale)
        // Optical padding: artwork lives in the central 80% of the 24-unit box.
        canvas.translate(2.4, 2.4)
        canvas.scale(0.8, 0.8)

        val strokeCap = when (theme) {
            ChatyIconThemeId.precisionContour, ChatyIconThemeId.pixelPrecision, ChatyIconThemeId.technicalBlueprint -> StrokeCap.square
            else -> StrokeCap.round
        }
        val strokeJoin = when (theme) {
            ChatyIconThemeId.precisionContour, ChatyIconThemeId.pixelPrecision, ChatyIconThemeId.origamiGeometry, ChatyIconThemeId.technicalBlueprint -> StrokeJoin.miter
            else -> StrokeJoin.round
        }
        val strokePaint = Paint().apply {
            color = iconColor
            style = PaintingStyle.stroke
            strokeWidth = when (theme) {
                ChatyIconThemeId.precisionContour -> 1.2
                ChatyIconThemeId.technicalBlueprint -> 1.3
                ChatyIconThemeId.pixelPrecision -> 1.8
                ChatyIconThemeId.expressiveInk -> 2.2
                else -> 1.85
            }
            this.strokeCap = strokeCap
            this.strokeJoin = strokeJoin
            isAntiAlias = true
        }
        val fillPaint = Paint().apply {
            color = iconColor
            style = PaintingStyle.fill
            isAntiAlias = true
        }
        val secondaryFillPaint = Paint().apply {
            color = iconColor.withValues(alpha = if (theme == ChatyIconThemeId.sculptedVolume) 0.32 else 0.16)
            style = PaintingStyle.fill
            isAntiAlias = true
        }
        val p = Painter(theme)
        run {
            when (icon) {
            ChatyIconId.more, ChatyIconId.moreOptions -> p._paintMore(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.chats -> p._paintChats(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.updates -> p._paintUpdates(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.calls -> p._paintCalls(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.profile -> p._paintProfile(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.tasks -> p._paintTasks(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.camera -> p._paintCamera(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.cameraFlip, ChatyIconId.cameraSwitch -> p._paintCameraFlip(canvas, strokePaint, fillPaint)
            ChatyIconId.cameraShutter -> p._paintCameraShutter(canvas, strokePaint, fillPaint)
            ChatyIconId.cameraFlashOn -> p._paintFlash(canvas, strokePaint, fillPaint, on = true)
            ChatyIconId.cameraFlashOff -> p._paintFlash(canvas, strokePaint, fillPaint, on = false)
            ChatyIconId.gallery -> p._paintGallery(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.callAudio -> p._paintCalls(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.callVideo -> p._paintVideo(canvas, strokePaint, fillPaint, secondaryFillPaint, off = false)
            ChatyIconId.callVideoOff -> p._paintVideo(canvas, strokePaint, fillPaint, secondaryFillPaint, off = true)
            ChatyIconId.callEnd -> p._paintCallEnd(canvas, strokePaint, fillPaint)
            ChatyIconId.screenShare -> p._paintScreenShare(canvas, strokePaint, fillPaint, stop = false)
            ChatyIconId.screenShareStop -> p._paintScreenShare(canvas, strokePaint, fillPaint, stop = true)
            ChatyIconId.speaker -> p._paintSpeaker(canvas, strokePaint, fillPaint, isSpeaker = true)
            ChatyIconId.earpiece -> p._paintSpeaker(canvas, strokePaint, fillPaint, isSpeaker = false)
            ChatyIconId.send -> p._paintSend(canvas, strokePaint, fillPaint)
            ChatyIconId.emoji -> p._paintEmoji(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.groups -> p._paintGroups(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.desktop -> p._paintDesktop(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.attachment -> p._paintAttachment(canvas, strokePaint, fillPaint)
            ChatyIconId.attachmentPlus -> p._paintAttachmentPlus(canvas, strokePaint, fillPaint)
            ChatyIconId.microphone -> p._paintMic(canvas, strokePaint, fillPaint, secondaryFillPaint, off = false)
            ChatyIconId.microphoneOff -> p._paintMic(canvas, strokePaint, fillPaint, secondaryFillPaint, off = true)
            ChatyIconId.search -> p._paintSearch(canvas, strokePaint, fillPaint)
            ChatyIconId.pin -> p._paintPin(canvas, strokePaint, fillPaint, unpin = false)
            ChatyIconId.unpin -> p._paintPin(canvas, strokePaint, fillPaint, unpin = true)
            ChatyIconId.archive -> p._paintArchive(canvas, strokePaint, secondaryFillPaint)
            ChatyIconId.lock -> p._paintLock(canvas, strokePaint, fillPaint, locked = true)
            ChatyIconId.unlock -> p._paintLock(canvas, strokePaint, fillPaint, locked = false)
            ChatyIconId.mute -> p._paintSpeaker(canvas, strokePaint, fillPaint, isMute = true)
            ChatyIconId.unmute -> p._paintSpeaker(canvas, strokePaint, fillPaint, isMute = false)
            ChatyIconId.favourite -> p._paintHeart(canvas, strokePaint, fillPaint, fillHeart = false)
            ChatyIconId.favouriteFilled -> p._paintHeart(canvas, strokePaint, fillPaint, fillHeart = true)
            ChatyIconId.delete -> p._paintDelete(canvas, strokePaint, fillPaint)
            ChatyIconId.settings -> p._paintSettings(canvas, strokePaint, fillPaint, secondaryFillPaint)
            ChatyIconId.close -> p._paintClose(canvas, strokePaint, fillPaint)
            ChatyIconId.check -> p._paintCheck(canvas, strokePaint)
            ChatyIconId.tune -> p._paintTune(canvas, strokePaint, fillPaint)
            ChatyIconId.chevronRight -> p._paintChevronRight(canvas, strokePaint)
            ChatyIconId.back -> p._paintBack(canvas, strokePaint, fillPaint)
            ChatyIconId.reply -> p._paintReply(canvas, strokePaint)
            ChatyIconId.forward -> p._paintForward(canvas, strokePaint)
            ChatyIconId.edit -> p._paintEdit(canvas, strokePaint)
            ChatyIconId.copy -> p._paintCopy(canvas, strokePaint)
            ChatyIconId.share -> p._paintShare(canvas, strokePaint, fillPaint)
            ChatyIconId.signal -> p._paintSignal(canvas, strokePaint, fillPaint)
            ChatyIconId.messageCheckSingle -> p._paintTicks(canvas, strokePaint, fillPaint, doubleTicks = false)
            ChatyIconId.messageCheckDouble -> p._paintTicks(canvas, strokePaint, fillPaint, doubleTicks = true)
            ChatyIconId.messageClock -> p._paintClock(canvas, strokePaint)
            ChatyIconId.pictureInPicture -> p._paintPictureInPicture(canvas, strokePaint, fillPaint)
            ChatyIconId.autoAwesome -> p._paintAutoAwesome(canvas, strokePaint, fillPaint)
            ChatyIconId.fullscreen -> p._paintFullscreen(canvas, strokePaint, fillPaint)
            ChatyIconId.fullscreenExit -> p._paintFullscreenExit(canvas, strokePaint, fillPaint)
            ChatyIconId.openInNew -> p._paintOpenInNew(canvas, strokePaint, fillPaint)
            ChatyIconId.clear -> p._paintClear(canvas, strokePaint)
            ChatyIconId.addTask -> p._paintAddTask(canvas, strokePaint, fillPaint)
            ChatyIconId.editOutline -> p._paintEditOutline(canvas, strokePaint, fillPaint)
            ChatyIconId.videoCam -> p._paintVideoCam(canvas, strokePaint, fillPaint)
            ChatyIconId.voiceCall -> p._paintVoiceCall(canvas, strokePaint, fillPaint)
            ChatyIconId.logout -> p._paintLogout(canvas, strokePaint, fillPaint)
            ChatyIconId.deleteOutline -> p._paintDeleteOutline(canvas, strokePaint, fillPaint)
            ChatyIconId.schedule -> p._paintSchedule(canvas, strokePaint)
            ChatyIconId.scheduleSend -> p._paintScheduleSend(canvas, strokePaint, fillPaint)
            ChatyIconId.sync -> p._paintSync(canvas, strokePaint)
            ChatyIconId.checkSingle -> p._paintCheckSingle(canvas, strokePaint)
            ChatyIconId.checkDouble -> p._paintCheckDouble(canvas, strokePaint)
            ChatyIconId.errorOutline -> p._paintErrorOutline(canvas, strokePaint, fillPaint)
            ChatyIconId.pause -> p._paintPause(canvas, strokePaint, fillPaint)
            ChatyIconId.playArrow -> p._paintPlayArrow(canvas, strokePaint, fillPaint)
            ChatyIconId.musicNote -> p._paintMusicNote(canvas, strokePaint, fillPaint)
                else -> {}
            }
        }
        canvas.restore()
    }

    private class Painter(val theme: ChatyIconThemeId) {
  fun _paintMore(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    val center = Offset(12, 12);
    val top = Offset(12, 5.2);
    val bottom = Offset(12, 18.8);

    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Orbital signature: connected spine with orbital halo on center dot
        val spine = Paint().apply { color = stroke.color.withValues(alpha = 0.28); style = PaintingStyle.stroke; strokeWidth = 1.0 };
        canvas.drawLine(Offset(12, 7.5), Offset(12, 16.5), spine);
        canvas.drawCircle(center, 2.1, fill);
        canvas.drawCircle(top, 1.85, fill);
        canvas.drawCircle(bottom, 1.85, fill);
        canvas.drawCircle(center, 4.0, Paint().apply { color = stroke.color.withValues(alpha = 0.35); style = PaintingStyle.stroke; strokeWidth = 0.9 });
        }

      ChatyIconThemeId.precisionContour -> {
        // Hairline architectural wireframe: 3 hollow square rings with alignment gaps
        fun drawHollowSquare(c: Offset) {
          canvas.drawRect(Rect.fromCenter(center = c, width = 3.8, height = 3.8), stroke.apply { strokeWidth = 1.1 });
        }
        drawHollowSquare(top);
        drawHollowSquare(center);
        drawHollowSquare(bottom);
        }

      ChatyIconThemeId.monolithicCutout -> {
        // 100% solid filled monolith blocks with stenciled white cross incision
        fun drawMonolithBlock(c: Offset) {
          canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromCenter(center = c, width = 4.8, height = 4.8), Radius.circular(1.0)), fill);
          // Stenciled white slit
          val slit = Paint().apply { color = Colors.white; strokeWidth = 0.9; style = PaintingStyle.stroke };
          canvas.drawLine(Offset(c.dx - 1.6, c.dy), Offset(c.dx + 1.6, c.dy), slit);
        }
        drawMonolithBlock(top);
        drawMonolithBlock(center);
        drawMonolithBlock(bottom);
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Faceted diamond paper points with diagonal crease
        fun drawOrigamiDiamond(c: Offset) {
          val p = Path().apply { moveTo(c.dx, c.dy - 2.5); lineTo(c.dx + 2.5, c.dy); lineTo(c.dx, c.dy + 2.5); lineTo(c.dx - 2.5, c.dy); close() };
          canvas.drawPath(p, fill);
          val crease = Paint().apply { color = Colors.white38; strokeWidth = 0.7; style = PaintingStyle.stroke };
          canvas.drawLine(Offset(c.dx, c.dy - 2.5), Offset(c.dx, c.dy + 2.5), crease);
        }
        drawOrigamiDiamond(top);
        drawOrigamiDiamond(center);
        drawOrigamiDiamond(bottom);
        }

      ChatyIconThemeId.organicMotion -> {
        // Fluid asymmetrical pebble droplets
        canvas.drawOval(Rect.fromCenter(center = top, width = 3.6, height = 4.6), fill);
        canvas.drawOval(Rect.fromCenter(center = center, width = 4.6, height = 3.6), fill);
        canvas.drawOval(Rect.fromCenter(center = bottom, width = 3.8, height = 4.8), fill);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit stepped pixel square blocks (3x3 pixels)
        canvas.drawRect(Rect.fromLTWH(10.5, 4, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(10.5, 10.5, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(10.5, 17, 3, 3), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD crosshair alignment targets (circular node with drafting ticks)
        fun drawCADTarget(c: Offset) {
          canvas.drawCircle(c, 1.4, fill);
          val tick = Paint().apply { color = stroke.color; strokeWidth = 0.85 };
          canvas.drawLine(Offset(c.dx - 2.6, c.dy), Offset(c.dx + 2.6, c.dy), tick);
          canvas.drawLine(Offset(c.dx, c.dy - 2.6), Offset(c.dx, c.dy + 2.6), tick);
        }
        drawCADTarget(top);
        drawCADTarget(center);
        drawCADTarget(bottom);
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Dimensional beveled pill facets with dual-tone depth
        fun drawSculptedPill(c: Offset) {
          canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromCenter(center = c, width = 4.5, height = 4.5), Radius.circular(2.25)), secondary);
          canvas.drawCircle(Offset(c.dx - 0.6, c.dy - 0.6), 1.6, fill);
        }
        drawSculptedPill(top);
        drawSculptedPill(center);
        drawSculptedPill(bottom);
        }

      ChatyIconThemeId.expressiveInk -> {
        // Dynamic calligraphic sumi-e ink droplets with pressure flares
        val inkPath = Path().apply { moveTo(12, 3.5); quadraticBezierTo(13.6, 5.2, 12, 6.8); quadraticBezierTo(10.4, 5.2, 12, 3.5) };
        canvas.drawPath(inkPath, fill);
        val inkCenter = Path().apply { moveTo(12, 10.2); quadraticBezierTo(14.2, 12.0, 12, 13.8); quadraticBezierTo(9.8, 12.0, 12, 10.2) };
        canvas.drawPath(inkCenter, fill);
        val inkBottom = Path().apply { moveTo(12, 17.2); quadraticBezierTo(13.6, 18.8, 12, 20.5); quadraticBezierTo(10.4, 18.8, 12, 17.2) };
        canvas.drawPath(inkBottom, fill);
        }

      ChatyIconThemeId.signalLanguage -> {
        // Radiating telecommunication beacon pulses with wave segments
        canvas.drawCircle(center, 2.0, fill);
        val wave = Paint().apply { color = stroke.color; style = PaintingStyle.stroke; strokeWidth = 1.1 };
        canvas.drawArc(Rect.fromLTWH(8, 2.5, 8, 6), PI, PI, false, wave);
        canvas.drawArc(Rect.fromLTWH(8, 15.5, 8, 6), 0, PI, false, wave);
        }
        else -> {}
    }
  }

  // 2. SEARCH
  fun _paintSearch(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Orbital signature: lens with intersecting orbital halo ring
        canvas.drawCircle(Offset(10.5, 10.5), 6.0, stroke);
        canvas.drawCircle(Offset(10.5, 10.5), 2.2, Paint().apply { color = stroke.color.withValues(alpha = 0.3); style = PaintingStyle.stroke; strokeWidth = 0.9 });
        canvas.drawLine(Offset(15.0, 15.0), Offset(20.5, 20.5), stroke);
        }

      ChatyIconThemeId.precisionContour -> {
        // Chamfered octagonal wire lens with detached hairline handle
        val oct = Path().apply { moveTo(8.0, 4.5); lineTo(13.0, 4.5); lineTo(16.5, 8.0); lineTo(16.5, 13.0); lineTo(13.0, 16.5); lineTo(8.0, 16.5); lineTo(4.5, 13.0); lineTo(4.5, 8.0); close() };
        canvas.drawPath(oct, stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(16.0, 16.0), Offset(21.0, 21.0), stroke);
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Solid black disk with stenciled negative-space aperture and wedge handle
        val disk = Path().apply { addOval(Rect.fromLTWH(4.0, 4.0, 13.0, 13.0)) };
        val handle = Path().apply { moveTo(14.5, 14.5); lineTo(20.5, 20.5); lineTo(19.0, 22.0); lineTo(13.0, 16.0); close() };
        canvas.drawPath(disk, fill);
        canvas.drawPath(handle, fill);
        // White negative space cutout
        canvas.drawCircle(Offset(10.5, 10.5), 4.2, Paint().apply { color = Colors.white; style = PaintingStyle.fill });
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Hexagonal faceted paper-folded lens with sharp diagonal crease
        val p = Path().apply { moveTo(10.5, 4.0); lineTo(16.0, 7.5); lineTo(16.0, 13.5); lineTo(10.5, 17.0); lineTo(5.0, 13.5); lineTo(5.0, 7.5); close() };
        canvas.drawPath(p, stroke);
        canvas.drawLine(Offset(10.5, 4.0), Offset(10.5, 17.0), Paint().apply { color = stroke.color.withValues(alpha = 0.4); strokeWidth = 0.9 });
        canvas.drawLine(Offset(14.5, 14.5), Offset(21.0, 21.0), stroke.apply { strokeWidth = 2.0 });
        }

      ChatyIconThemeId.organicMotion -> {
        // Asymmetric liquid droplet lens with curved fluid handle
        val drop = Path().apply { moveTo(10.5, 4.0); cubicTo(16.5, 4.2, 17.5, 11.0, 14.5, 15.5); cubicTo(11.5, 17.8, 5.5, 16.0, 4.8, 10.5); cubicTo(4.2, 6.2, 7.5, 3.8, 10.5, 4.0) };
        canvas.drawPath(drop, stroke);
        val h = Path().apply { moveTo(14.2, 14.8); quadraticBezierTo(16.0, 18.5, 21.0, 20.5) };
        canvas.drawPath(h, stroke);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit stepped pixel magnifying glass
        // Stepped pixel ring: top 4px, sides 4px, bottom 4px, diagonal handle
        canvas.drawRect(Rect.fromLTWH(8, 4, 6, 2), fill);
        canvas.drawRect(Rect.fromLTWH(8, 16, 6, 2), fill);
        canvas.drawRect(Rect.fromLTWH(4, 8, 2, 6), fill);
        canvas.drawRect(Rect.fromLTWH(16, 8, 2, 6), fill);
        canvas.drawRect(Rect.fromLTWH(6, 6, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(14, 6, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(6, 14, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(14, 14, 2, 2), fill);
        // Stepped handle
        canvas.drawRect(Rect.fromLTWH(16, 16, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(18, 18, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(20, 20, 2, 2), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD crosshair drafted lens with extended datum axes
        canvas.drawCircle(Offset(10.5, 10.5), 6.0, stroke.apply { strokeWidth = 1.2 });
        // Extended crosshair axes
        val cad = Paint().apply { color = stroke.color.withValues(alpha = 0.65); strokeWidth = 0.8 };
        canvas.drawLine(Offset(3.0, 10.5), Offset(18.0, 10.5), cad);
        canvas.drawLine(Offset(10.5, 3.0), Offset(10.5, 18.0), cad);
        canvas.drawLine(Offset(15.0, 15.0), Offset(21.5, 21.5), stroke.apply { strokeWidth = 1.5 });
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Dimensional beveled multi-plate lens
        canvas.drawCircle(Offset(10.5, 10.5), 6.5, Paint().apply { color = color.withValues(alpha = 0.35); style = PaintingStyle.fill });
        canvas.drawCircle(Offset(10.0, 10.0), 5.5, stroke.apply { strokeWidth = 1.8 });
        canvas.drawLine(Offset(14.5, 14.5), Offset(20.5, 20.5), stroke.apply { strokeWidth = 2.4 });
        }

      ChatyIconThemeId.expressiveInk -> {
        // Bold brush-pen calligraphic loop with tapered handle
        val ink = Path().apply { moveTo(6.0, 10.5); cubicTo(6.0, 5.5, 15.5, 5.5, 15.5, 10.5); cubicTo(15.5, 15.5, 6.0, 15.5, 6.0, 10.5) };
        canvas.drawPath(ink, stroke.apply { strokeWidth = 2.4 });
        val tail = Path().apply { moveTo(14.0, 14.0); quadraticBezierTo(16.5, 17.5, 21.5, 21.0) };
        canvas.drawPath(tail, stroke.apply { strokeWidth = 2.8 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Radar sweep transmitter node with concentric pulse arcs
        canvas.drawCircle(Offset(10.5, 10.5), 2.2, fill);
        canvas.drawArc(Rect.fromLTWH(5.5, 5.5, 10, 10), -0.6, 2.4, false, stroke);
        canvas.drawArc(Rect.fromLTWH(3.0, 3.0, 15, 15), -0.8, 2.8, false, stroke);
        canvas.drawLine(Offset(15.5, 15.5), Offset(21.0, 21.0), stroke.apply { strokeWidth = 2.0 });
        }
        else -> {}
    }
  }

  // 3. BACK NAVIGATION
  fun _paintBack(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Orbital smooth fillet arrow with circular terminal apex
        val p = Path().apply { moveTo(14.8, 6.2); lineTo(9.0, 12.0); lineTo(14.8, 17.8) };
        canvas.drawPath(p, stroke);
        canvas.drawCircle(Offset(19.0, 12.0), 1.5, fill);
        canvas.drawLine(Offset(9.5, 12.0), Offset(17.5, 12.0), stroke);
        }

      ChatyIconThemeId.precisionContour -> {
        // Razor-sharp 90° hairline chevron with detached parallel guide
        val p = Path().apply { moveTo(15.5, 5.5); lineTo(9.0, 12.0); lineTo(15.5, 18.5) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 1.2 });
        // Parallel drafting wire
        val p2 = Path().apply { moveTo(18.0, 7.5); lineTo(13.5, 12.0); lineTo(18.0, 16.5) };
        canvas.drawPath(p2, stroke.apply { strokeWidth = 0.85 });
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Solid filled arrow wedge with stark white negative-space chevron cutout
        val wedge = Path().apply { moveTo(18.5, 5.0); lineTo(7.5, 12.0); lineTo(18.5, 19.0); close() };
        canvas.drawPath(wedge, fill);
        val slit = Paint().apply { color = Colors.white; strokeWidth = 1.6; style = PaintingStyle.stroke };
        canvas.drawLine(Offset(16.0, 8.5), Offset(11.0, 12.0), slit);
        canvas.drawLine(Offset(11.0, 12.0), Offset(16.0, 15.5), slit);
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Folded paper triangular arrow with diagonal crease
        val p = Path().apply { moveTo(17.5, 5.0); lineTo(7.5, 12.0); lineTo(17.5, 19.0); lineTo(14.5, 12.0); close() };
        canvas.drawPath(p, fill);
        canvas.drawLine(Offset(7.5, 12.0), Offset(14.5, 12.0), Paint().apply { color = Colors.white60; strokeWidth = 0.9 });
        }

      ChatyIconThemeId.organicMotion -> {
        // Fluid river-swoosh arrow with bulbous teardrop tip
        val p = Path().apply { moveTo(16.5, 6.0); quadraticBezierTo(10.0, 10.0, 7.5, 12.0); quadraticBezierTo(10.0, 14.0, 16.5, 18.0) };
        canvas.drawPath(p, stroke);
        canvas.drawCircle(Offset(7.5, 12.0), 2.2, fill);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit stepped pixel arrow
        canvas.drawRect(Rect.fromLTWH(6, 11, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(8, 9, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(8, 13, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(10, 7, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(10, 15, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(12, 5, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(12, 17, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(8, 11, 10, 2), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD dimension leader arrow with vertex overshoot and datum tick
        canvas.drawLine(Offset(15.5, 5.0), Offset(8.0, 12.0), stroke.apply { strokeWidth = 1.3 });
        canvas.drawLine(Offset(8.0, 12.0), Offset(15.5, 19.0), stroke);
        canvas.drawLine(Offset(8.0, 12.0), Offset(20.0, 12.0), stroke);
        // Datum end ticks
        canvas.drawLine(Offset(20.0, 9.5), Offset(20.0, 14.5), stroke.apply { strokeWidth = 0.9 });
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Embossed relief chevron with dual-plane light/shadow halves
        val topHalf = Path().apply { moveTo(16.0, 6.0); lineTo(8.5, 12.0); lineTo(16.0, 12.0); close() };
        val btmHalf = Path().apply { moveTo(16.0, 12.0); lineTo(8.5, 12.0); lineTo(16.0, 18.0); close() };
        canvas.drawPath(topHalf, fill);
        canvas.drawPath(btmHalf, Paint().apply { color = color.withValues(alpha = 0.35); style = PaintingStyle.fill });
        }

      ChatyIconThemeId.expressiveInk -> {
        // Dynamic calligraphic brush-stroke arrow
        val p = Path().apply { moveTo(16.0, 5.0); quadraticBezierTo(11.0, 9.0, 7.5, 12.0); quadraticBezierTo(11.0, 15.0, 16.0, 19.0) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 2.6 });
        val stem = Path().apply { moveTo(8.5, 12.0); quadraticBezierTo(14.0, 11.5, 19.5, 12.0) };
        canvas.drawPath(stem, stroke.apply { strokeWidth = 2.4 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Left-pointing directional vector beam with trailing pulse wave arcs
        canvas.drawLine(Offset(14.0, 7.0), Offset(8.0, 12.0), stroke.apply { strokeWidth = 1.8 });
        canvas.drawLine(Offset(8.0, 12.0), Offset(14.0, 17.0), stroke.apply { strokeWidth = 1.8 });
        canvas.drawArc(Rect.fromLTWH(14, 8, 4, 8), -PI / 2, PI, false, stroke.apply { strokeWidth = 1.1 });
        canvas.drawArc(Rect.fromLTWH(17, 6, 4, 12), -PI / 2, PI, false, stroke.apply { strokeWidth = 1.1 });
        }
        else -> {}
    }
  }

  // 4. CAMERA
  fun _paintEmoji(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Signature smooth face with orbital halo dot
        canvas.drawCircle(Offset(12, 12), 8.0, stroke);
        canvas.drawCircle(Offset(9.2, 10.2), 1.1, fill);
        canvas.drawCircle(Offset(14.8, 10.2), 1.1, fill);
        canvas.drawArc(Rect.fromLTWH(7.6, 10.6, 8.8, 6.4), 0.35, 2.45, false, stroke);
        canvas.drawCircle(Offset(19.0, 5.4), 1.0, fill);
        }

      ChatyIconThemeId.precisionContour -> {
        // Hairline open-contour face
        canvas.drawArc(Rect.fromLTWH(4.0, 4.0, 16.0, 16.0), 0.5, 5.3, false, stroke.apply { strokeWidth = 1.2 });
        canvas.drawCircle(Offset(9.2, 10.2), 0.9, stroke.apply { strokeWidth = 1.0 });
        canvas.drawCircle(Offset(14.8, 10.2), 0.9, stroke.apply { strokeWidth = 1.0 });
        canvas.drawArc(Rect.fromLTWH(7.8, 10.8, 8.4, 6.0), 0.4, 2.3, false, stroke.apply { strokeWidth = 1.0 });
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Solid disc with negative-space eyes and smile
        canvas.drawCircle(Offset(12, 12), 8.2, fill);
        val cut = Paint().apply { color = Colors.white; style = PaintingStyle.fill };
        canvas.drawCircle(Offset(9.2, 10.0), 1.2, cut);
        canvas.drawCircle(Offset(14.8, 10.0), 1.2, cut);
        val smile = Paint().apply { color = Colors.white; style = PaintingStyle.stroke; strokeWidth = 1.6; strokeCap = StrokeCap.round };
        canvas.drawArc(Rect.fromLTWH(7.6, 10.4, 8.8, 6.6), 0.35, 2.45, false, smile);
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Faceted hexagonal face with diamond eyes
        val face = Path().apply { moveTo(12, 3.6); lineTo(19.2, 7.8); lineTo(19.2, 16.2); lineTo(12, 20.4); lineTo(4.8, 16.2); lineTo(4.8, 7.8); close() };
        canvas.drawPath(face, stroke);
        val eyeL = Path().apply { moveTo(9.2, 9.0); lineTo(10.2, 10.2); lineTo(9.2, 11.4); lineTo(8.2, 10.2); close() };
        val eyeR = Path().apply { moveTo(14.8, 9.0); lineTo(15.8, 10.2); lineTo(14.8, 11.4); lineTo(13.8, 10.2); close() };
        canvas.drawPath(eyeL, stroke);
        canvas.drawPath(eyeR, stroke);
        val smile = Path().apply { moveTo(8.6, 14.2); lineTo(12, 16.2); lineTo(15.4, 14.2) };
        canvas.drawPath(smile, stroke);
        }

      ChatyIconThemeId.organicMotion -> {
        // Pebble blob face with soft oval eyes
        val blob = Path().apply { moveTo(12, 4.0); cubicTo(18.5, 3.5, 20.4, 9.0, 19.6, 13.0); cubicTo(18.8, 17.5, 14.5, 20.6, 10.2, 19.8); cubicTo(5.6, 19.0, 3.6, 14.8, 4.4, 10.4); cubicTo(5.2, 6.2, 8.0, 4.3, 12, 4.0) };
        canvas.drawPath(blob, stroke);
        canvas.drawOval(Rect.fromLTWH(8.0, 9.2, 2.2, 2.8), fill);
        canvas.drawOval(Rect.fromLTWH(13.6, 9.2, 2.2, 2.8), fill);
        canvas.drawArc(Rect.fromLTWH(7.8, 11.2, 8.4, 6.2), 0.4, 2.35, false, stroke);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // 8-bit stepped face
        canvas.drawRect(Rect.fromLTWH(5, 5, 14, 14), stroke.apply { strokeWidth = 1.8 });
        canvas.drawRect(Rect.fromLTWH(8, 9, 2.4, 2.4), fill);
        canvas.drawRect(Rect.fromLTWH(13.6, 9, 2.4, 2.4), fill);
        canvas.drawRect(Rect.fromLTWH(8.6, 14.6, 6.8, 1.8), fill);
        canvas.drawRect(Rect.fromLTWH(7.4, 13.4, 1.4, 1.4), fill);
        canvas.drawRect(Rect.fromLTWH(15.2, 13.4, 1.4, 1.4), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD face with datum crosshair and corner ticks
        canvas.drawCircle(Offset(12, 12), 8.0, stroke.apply { strokeWidth = 1.2 });
        val tick = Paint().apply { color = stroke.color; strokeWidth = 0.8 };
        canvas.drawLine(Offset(12, 1.2), Offset(12, 4.4), tick);
        canvas.drawLine(Offset(12, 19.6), Offset(12, 22.8), tick);
        canvas.drawLine(Offset(1.2, 12), Offset(4.4, 12), tick);
        canvas.drawLine(Offset(19.6, 12), Offset(22.8, 12), tick);
        canvas.drawCircle(Offset(9.2, 10.2), 1.0, stroke.apply { strokeWidth = 1.0 });
        canvas.drawCircle(Offset(14.8, 10.2), 1.0, stroke.apply { strokeWidth = 1.0 });
        canvas.drawArc(Rect.fromLTWH(7.8, 10.6, 8.4, 6.2), 0.4, 2.35, false, stroke.apply { strokeWidth = 1.0 });
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Layered dimensional face
        canvas.drawCircle(Offset(12, 12), 8.2, fill);
        canvas.drawCircle(Offset(10.6, 10.6), 6.4, secondary);
        val cut = Paint().apply { color = Colors.white; style = PaintingStyle.fill };
        canvas.drawCircle(Offset(9.4, 9.8), 1.2, cut);
        canvas.drawCircle(Offset(14.6, 9.8), 1.2, cut);
        val smile = Paint().apply { color = Colors.white; style = PaintingStyle.stroke; strokeWidth = 1.5; strokeCap = StrokeCap.round };
        canvas.drawArc(Rect.fromLTWH(7.8, 10.4, 8.4, 6.4), 0.4, 2.35, false, smile);
        }

      ChatyIconThemeId.expressiveInk -> {
        // Bold open brushstroke face
        canvas.drawArc(Rect.fromLTWH(3.8, 4.2, 16.4, 16.0), 0.7, 5.0, false, stroke.apply { strokeWidth = 2.6 });
        canvas.drawCircle(Offset(9.0, 10.0), 1.5, fill);
        canvas.drawCircle(Offset(15.0, 9.6), 1.3, fill);
        canvas.drawArc(Rect.fromLTWH(7.4, 10.8, 9.2, 6.6), 0.3, 2.6, false, stroke.apply { strokeWidth = 2.2 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Face with radiating signal waves
        canvas.drawCircle(Offset(12, 12), 7.4, stroke);
        canvas.drawCircle(Offset(9.4, 10.4), 1.1, fill);
        canvas.drawCircle(Offset(14.6, 10.4), 1.1, fill);
        canvas.drawArc(Rect.fromLTWH(8.0, 10.8, 8.0, 5.8), 0.4, 2.35, false, stroke.apply { strokeWidth = 1.1 });
        canvas.drawArc(Rect.fromLTWH(16.8, 1.6, 5.2, 5.2), -0.6, 2.2, false, stroke.apply { strokeWidth = 1.0 });
        canvas.drawArc(Rect.fromLTWH(15.2, 3.4, 3.4, 3.4), -0.6, 2.2, false, stroke.apply { strokeWidth = 1.0 });
        }
        else -> {}
    }
  }

  fun _paintGroups(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    // Two-person group mark, drawn per theme grammar.
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        canvas.drawCircle(Offset(9.2, 9.0), 3.0, stroke);
        canvas.drawCircle(Offset(15.4, 9.6), 2.4, stroke);
        canvas.drawArc(Rect.fromLTWH(3.6, 12.6, 11.2, 8.6), 3.4, 2.9, false, stroke);
        canvas.drawArc(Rect.fromLTWH(11.6, 13.4, 8.6, 6.8), 3.5, 2.7, false, stroke);
        canvas.drawCircle(Offset(19.4, 5.2), 0.9, fill);
        }
      ChatyIconThemeId.precisionContour -> {
        canvas.drawCircle(Offset(9.2, 9.0), 3.0, stroke.apply { strokeWidth = 1.2 });
        canvas.drawCircle(Offset(15.4, 9.6), 2.4, stroke.apply { strokeWidth = 1.2 });
        canvas.drawArc(Rect.fromLTWH(3.6, 12.6, 11.2, 8.6), 3.4, 2.9, false, stroke.apply { strokeWidth = 1.1 });
        canvas.drawArc(Rect.fromLTWH(11.6, 13.4, 8.6, 6.8), 3.5, 2.7, false, stroke.apply { strokeWidth = 1.1 });
        }
      ChatyIconThemeId.monolithicCutout -> {
        canvas.drawCircle(Offset(9.2, 8.8), 3.2, fill);
        canvas.drawCircle(Offset(15.8, 9.6), 2.5, fill);
        val body = Path().apply { moveTo(3.2, 20.0); cubicTo(3.2, 14.6, 15.2, 14.6, 15.2, 20.0); close() };
        canvas.drawPath(body, fill);
        val body2 = Path().apply { moveTo(14.4, 20.0); cubicTo(14.8, 16.0, 21.0, 16.2, 21.0, 20.0); close() };
        canvas.drawPath(body2, fill);
        }
      ChatyIconThemeId.origamiGeometry -> {
        val h1 = Path().apply { moveTo(9.2, 5.6); lineTo(12.0, 9.0); lineTo(9.2, 12.4); lineTo(6.4, 9.0); close() };
        val h2 = Path().apply { moveTo(15.6, 7.0); lineTo(17.8, 9.8); lineTo(15.6, 12.6); lineTo(13.4, 9.8); close() };
        canvas.drawPath(h1, stroke);
        canvas.drawPath(h2, stroke);
        val b1 = Path().apply { moveTo(4.0, 19.6); lineTo(9.2, 13.8); lineTo(14.4, 19.6) };
        val b2 = Path().apply { moveTo(12.0, 19.6); lineTo(15.6, 14.8); lineTo(19.6, 19.6) };
        canvas.drawPath(b1, stroke);
        canvas.drawPath(b2, stroke);
        }
      ChatyIconThemeId.organicMotion -> {
        canvas.drawOval(Rect.fromLTWH(6.0, 5.6, 6.4, 6.8), stroke);
        canvas.drawOval(Rect.fromLTWH(13.2, 7.0, 5.0, 5.4), stroke);
        val b = Path().apply { moveTo(3.4, 19.6); cubicTo(3.8, 14.2, 14.8, 14.0, 15.0, 19.6) };
        canvas.drawPath(b, stroke);
        val b2 = Path().apply { moveTo(13.6, 19.6); cubicTo(14.2, 15.8, 20.6, 16.0, 20.8, 19.6) };
        canvas.drawPath(b2, stroke);
        }
      ChatyIconThemeId.pixelPrecision -> {
        canvas.drawRect(Rect.fromLTWH(6.6, 5.6, 5.2, 5.2), stroke.apply { strokeWidth = 1.7 });
        canvas.drawRect(Rect.fromLTWH(13.6, 7.2, 4.0, 4.0), stroke.apply { strokeWidth = 1.6 });
        canvas.drawRect(Rect.fromLTWH(4.0, 14.0, 10.4, 6.0), stroke.apply { strokeWidth = 1.7 });
        canvas.drawRect(Rect.fromLTWH(13.0, 15.2, 7.4, 4.8), stroke.apply { strokeWidth = 1.6 });
        }
      ChatyIconThemeId.technicalBlueprint -> {
        canvas.drawCircle(Offset(9.2, 9.0), 3.0, stroke.apply { strokeWidth = 1.2 });
        canvas.drawCircle(Offset(15.4, 9.6), 2.4, stroke.apply { strokeWidth = 1.2 });
        val tick = Paint().apply { color = stroke.color; strokeWidth = 0.8 };
        canvas.drawLine(Offset(9.2, 4.4), Offset(9.2, 6.0), tick);
        canvas.drawLine(Offset(4.6, 9.0), Offset(6.2, 9.0), tick);
        canvas.drawArc(Rect.fromLTWH(3.6, 12.6, 11.2, 8.6), 3.4, 2.9, false, stroke.apply { strokeWidth = 1.1 });
        canvas.drawArc(Rect.fromLTWH(11.6, 13.4, 8.6, 6.8), 3.5, 2.7, false, stroke.apply { strokeWidth = 1.1 });
        }
      ChatyIconThemeId.sculptedVolume -> {
        canvas.drawCircle(Offset(9.2, 9.0), 3.2, fill);
        canvas.drawCircle(Offset(9.2, 9.0), 2.0, secondary);
        canvas.drawCircle(Offset(15.6, 9.8), 2.5, fill);
        val body = Path().apply { moveTo(3.4, 20.0); cubicTo(3.4, 14.8, 15.0, 14.8, 15.0, 20.0); close() };
        canvas.drawPath(body, fill);
        val body2 = Path().apply { moveTo(14.2, 20.0); cubicTo(14.6, 16.2, 20.8, 16.4, 20.8, 20.0); close() };
        canvas.drawPath(body2, secondary);
        }
      ChatyIconThemeId.expressiveInk -> {
        canvas.drawArc(Rect.fromLTWH(6.0, 5.4, 6.6, 6.8), -0.4, 5.6, false, stroke.apply { strokeWidth = 2.4 });
        canvas.drawArc(Rect.fromLTWH(13.2, 6.8, 5.2, 5.4), -0.4, 5.6, false, stroke.apply { strokeWidth = 2.2 });
        val b = Path().apply { moveTo(3.2, 19.8); quadraticBezierTo(9.0, 13.2, 15.2, 19.4) };
        canvas.drawPath(b, stroke.apply { strokeWidth = 2.6 });
        val b2 = Path().apply { moveTo(13.8, 19.8); quadraticBezierTo(17.4, 15.4, 21.0, 19.4) };
        canvas.drawPath(b2, stroke.apply { strokeWidth = 2.2 });
        }
      ChatyIconThemeId.signalLanguage -> {
        canvas.drawCircle(Offset(9.2, 9.0), 3.0, stroke);
        canvas.drawCircle(Offset(15.4, 9.6), 2.4, stroke);
        canvas.drawArc(Rect.fromLTWH(3.6, 12.6, 11.2, 8.6), 3.4, 2.9, false, stroke.apply { strokeWidth = 1.1 });
        canvas.drawArc(Rect.fromLTWH(11.6, 13.4, 8.6, 6.8), 3.5, 2.7, false, stroke.apply { strokeWidth = 1.1 });
        canvas.drawArc(Rect.fromLTWH(15.6, 2.4, 6.0, 6.0), -0.6, 2.2, false, stroke.apply { strokeWidth = 1.0 });
        }
        else -> {}
    }
  }

  fun _paintDesktop(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    // Desktop monitor with stand, drawn per theme grammar.
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.4, 4.6, 17.2, 11.6), Radius.circular(2.4)), stroke);
        canvas.drawLine(Offset(12, 16.2), Offset(12, 19.0), stroke);
        canvas.drawLine(Offset(8.0, 19.4), Offset(16.0, 19.4), stroke);
        canvas.drawCircle(Offset(17.6, 7.4), 0.9, fill);
        }
      ChatyIconThemeId.precisionContour -> {
        val m = Path().apply { moveTo(4.6, 4.6); lineTo(19.4, 4.6); lineTo(20.6, 5.8); lineTo(20.6, 15.0); lineTo(19.4, 16.2); lineTo(4.6, 16.2); lineTo(3.4, 15.0); lineTo(3.4, 5.8); close() };
        canvas.drawPath(m, stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(12, 16.2), Offset(12, 19.0), stroke.apply { strokeWidth = 1.1 });
        canvas.drawLine(Offset(8.0, 19.4), Offset(16.0, 19.4), stroke.apply { strokeWidth = 1.1 });
        }
      ChatyIconThemeId.monolithicCutout -> {
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.2, 4.4, 17.6, 12.0), Radius.circular(1.6)), fill);
        canvas.drawRect(Rect.fromLTWH(5.0, 6.2, 14.0, 8.4), Paint().apply { color = Colors.white; style = PaintingStyle.fill });
        canvas.drawRect(Rect.fromLTWH(11.0, 16.4, 2.0, 2.6), fill);
        canvas.drawRect(Rect.fromLTWH(7.6, 19.0, 8.8, 1.4), fill);
        }
      ChatyIconThemeId.origamiGeometry -> {
        val m = Path().apply { moveTo(3.6, 4.6); lineTo(20.4, 4.6); lineTo(19.2, 16.2); lineTo(4.8, 16.2); close() };
        canvas.drawPath(m, stroke);
        canvas.drawLine(Offset(4.4, 13.2), Offset(19.8, 13.2), stroke);
        canvas.drawLine(Offset(12, 16.2), Offset(12, 19.0), stroke);
        val stand = Path().apply { moveTo(8.2, 19.4); lineTo(12, 16.8); lineTo(15.8, 19.4) };
        canvas.drawPath(stand, stroke);
        }
      ChatyIconThemeId.organicMotion -> {
        val m = Path().apply { moveTo(4.0, 5.2); cubicTo(6.0, 3.8, 18.4, 3.8, 20.2, 5.4); cubicTo(21.0, 9.0, 20.8, 13.4, 19.6, 15.6); cubicTo(15.2, 17.0, 8.4, 17.0, 4.4, 15.4); cubicTo(3.2, 12.0, 3.2, 8.2, 4.0, 5.2) };
        canvas.drawPath(m, stroke);
        canvas.drawLine(Offset(12, 16.6), Offset(12, 19.2), stroke);
        canvas.drawOval(Rect.fromLTWH(8.0, 18.6, 8.0, 1.8), stroke);
        }
      ChatyIconThemeId.pixelPrecision -> {
        canvas.drawRect(Rect.fromLTWH(3.6, 4.6, 16.8, 11.6), stroke.apply { strokeWidth = 1.8 });
        canvas.drawRect(Rect.fromLTWH(6.0, 7.0, 12.0, 6.8), stroke.apply { strokeWidth = 1.4 });
        canvas.drawRect(Rect.fromLTWH(10.8, 16.2, 2.4, 2.4), fill);
        canvas.drawRect(Rect.fromLTWH(7.6, 18.6, 8.8, 1.6), fill);
        }
      ChatyIconThemeId.technicalBlueprint -> {
        canvas.drawRect(Rect.fromLTWH(3.4, 4.6, 17.2, 11.6), stroke.apply { strokeWidth = 1.2 });
        val tick = Paint().apply { color = stroke.color; strokeWidth = 0.8 };
        canvas.drawLine(Offset(1.6, 4.6), Offset(5.0, 4.6), tick);
        canvas.drawLine(Offset(3.4, 2.8), Offset(3.4, 6.2), tick);
        canvas.drawLine(Offset(3.4, 10.4), Offset(20.6, 10.4), tick);
        canvas.drawLine(Offset(12, 16.2), Offset(12, 19.0), stroke.apply { strokeWidth = 1.1 });
        canvas.drawLine(Offset(8.0, 19.4), Offset(16.0, 19.4), stroke.apply { strokeWidth = 1.1 });
        }
      ChatyIconThemeId.sculptedVolume -> {
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.4, 4.6, 17.2, 11.6), Radius.circular(2.0)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(5.0, 6.2, 14.4, 8.4), Radius.circular(1.2)), secondary);
        canvas.drawRect(Rect.fromLTWH(11.0, 16.2, 2.0, 2.8), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(7.6, 19.0, 8.8, 1.4), Radius.circular(0.7)), fill);
        }
      ChatyIconThemeId.expressiveInk -> {
        val m = Path().apply { moveTo(3.6, 15.6); lineTo(3.9, 5.2); quadraticBezierTo(12.0, 3.8, 20.4, 5.4); lineTo(20.2, 15.8); quadraticBezierTo(12.0, 17.0, 3.6, 15.6) };
        canvas.drawPath(m, stroke.apply { strokeWidth = 2.2 });
        val st = Path().apply { moveTo(12, 16.4); lineTo(11.6, 19.2) };
        canvas.drawPath(st, stroke.apply { strokeWidth = 2.2 });
        canvas.drawArc(Rect.fromLTWH(7.4, 18.2, 9.2, 2.6), 3.3, 2.9, false, stroke.apply { strokeWidth = 2.2 });
        }
      ChatyIconThemeId.signalLanguage -> {
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.4, 4.6, 17.2, 11.6), Radius.circular(2.0)), stroke);
        canvas.drawLine(Offset(12, 16.2), Offset(12, 19.0), stroke);
        canvas.drawLine(Offset(8.0, 19.4), Offset(16.0, 19.4), stroke);
        canvas.drawArc(Rect.fromLTWH(8.0, 6.6, 8.0, 8.0), -2.4, 1.6, false, stroke.apply { strokeWidth = 1.0 });
        canvas.drawArc(Rect.fromLTWH(10.4, 8.4, 3.2, 3.2), -2.4, 1.6, false, stroke.apply { strokeWidth = 1.0 });
        }
        else -> {}
    }
  }

  fun _paintCamera(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Smooth rounded body with orbital aperture lens ring
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.5, 6.5, 17.0, 12.5), Radius.circular(3.5)), stroke);
        canvas.drawCircle(Offset(12.0, 12.8), 3.5, stroke);
        canvas.drawCircle(Offset(12.0, 12.8), 1.2, fill);
        canvas.drawCircle(Offset(17.5, 9.5), 1.0, fill);
        }

      ChatyIconThemeId.precisionContour -> {
        // Hairline drafting camera body with open 45° chamfered corners
        val cam = Path().apply { moveTo(5.5, 7.0); lineTo(9.0, 7.0); lineTo(10.5, 5.0); lineTo(13.5, 5.0); lineTo(15.0, 7.0); lineTo(18.5, 7.0); lineTo(20.5, 9.0); lineTo(20.5, 17.0); lineTo(18.5, 19.0); lineTo(5.5, 19.0); lineTo(3.5, 17.0); lineTo(3.5, 9.0); close() };
        canvas.drawPath(cam, stroke.apply { strokeWidth = 1.2 });
        canvas.drawCircle(Offset(12.0, 13.0), 3.4, stroke.apply { strokeWidth = 1.0 });
        }

      ChatyIconThemeId.monolithicCutout -> {
        // 100% solid filled camera block silhouette with stark white negative-space lens
        val solid = Path().apply { moveTo(4.0, 8.0); lineTo(8.5, 8.0); lineTo(10.0, 5.5); lineTo(14.0, 5.5); lineTo(15.5, 8.0); lineTo(20.0, 8.0); lineTo(20.0, 19.0); lineTo(4.0, 19.0); close() };
        canvas.drawPath(solid, fill);
        canvas.drawCircle(Offset(12.0, 13.5), 3.6, Paint().apply { color = Colors.white; style = PaintingStyle.fill });
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Faceted polygonal folded camera body
        val p = Path().apply { moveTo(4.0, 8.0); lineTo(9.0, 8.0); lineTo(10.5, 5.0); lineTo(13.5, 5.0); lineTo(15.0, 8.0); lineTo(20.0, 8.0); lineTo(18.5, 18.5); lineTo(5.5, 18.5); close() };
        canvas.drawPath(p, stroke);
        // Diamond faceted lens
        val lens = Path().apply { moveTo(12.0, 10.0); lineTo(15.0, 13.0); lineTo(12.0, 16.0); lineTo(9.0, 13.0); close() };
        canvas.drawPath(lens, stroke);
        }

      ChatyIconThemeId.organicMotion -> {
        // Fluid asymmetric pebble camera body
        val dropCam = Path().apply { moveTo(4.5, 9.0); cubicTo(5.0, 5.5, 19.0, 5.0, 19.5, 9.0); cubicTo(20.5, 14.0, 19.0, 19.5, 12.0, 19.0); cubicTo(4.5, 19.0, 4.0, 14.0, 4.5, 9.0) };
        canvas.drawPath(dropCam, stroke);
        canvas.drawOval(Rect.fromLTWH(8.5, 9.5, 7.0, 6.0), stroke);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit stepped pixel camera
        canvas.drawRect(Rect.fromLTWH(4, 7, 16, 12), stroke.apply { strokeWidth = 1.8 });
        canvas.drawRect(Rect.fromLTWH(9, 5, 6, 2), fill);
        canvas.drawRect(Rect.fromLTWH(9, 10, 6, 6), stroke.apply { strokeWidth = 1.6 });
        canvas.drawRect(Rect.fromLTWH(16, 8, 2, 2), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD camera with corner alignment ticks and optical crosshairs
        canvas.drawRect(Rect.fromLTWH(4.0, 7.0, 16.0, 12.0), stroke.apply { strokeWidth = 1.2 });
        // Alignment ticks
        val tick = Paint().apply { color = stroke.color; strokeWidth = 0.8 };
        canvas.drawLine(Offset(1.5, 7.0), Offset(6.5, 7.0), tick);
        canvas.drawLine(Offset(4.0, 4.5), Offset(4.0, 9.5), tick);
        canvas.drawCircle(Offset(12.0, 13.0), 3.5, stroke.apply { strokeWidth = 1.0 });
        canvas.drawLine(Offset(7.5, 13.0), Offset(16.5, 13.0), tick);
        canvas.drawLine(Offset(12.0, 8.5), Offset(12.0, 17.5), tick);
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Dimensional layered camera body with raised top deck
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(4.0, 7.0, 16.0, 12.0), Radius.circular(2.5)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(9.0, 4.5, 6.0, 3.5), Radius.circular(1.0)), secondary);
        canvas.drawCircle(Offset(12.0, 13.0), 3.8, secondary);
        canvas.drawCircle(Offset(12.0, 13.0), 2.2, Paint().apply { color = Colors.white; style = PaintingStyle.fill });
        }

      ChatyIconThemeId.expressiveInk -> {
        // Dynamic calligraphic brush-ink camera
        val base = Path().apply { moveTo(4.0, 18.0); quadraticBezierTo(12.0, 17.0, 20.0, 18.0) };
        canvas.drawPath(base, stroke.apply { strokeWidth = 2.8 });
        val body = Path().apply { moveTo(4.5, 18.0); lineTo(4.5, 8.5); quadraticBezierTo(12.0, 7.0, 19.5, 8.5); lineTo(19.5, 18.0) };
        canvas.drawPath(body, stroke.apply { strokeWidth = 2.0 });
        canvas.drawCircle(Offset(12.0, 13.0), 3.5, stroke.apply { strokeWidth = 2.2 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Telemetry camera sensor with radiating optical wave arcs
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(4.0, 7.0, 16.0, 12.0), Radius.circular(2.0)), stroke);
        canvas.drawCircle(Offset(12.0, 13.0), 2.0, fill);
        canvas.drawArc(Rect.fromLTWH(7.0, 8.0, 10, 10), -1.2, 2.4, false, stroke.apply { strokeWidth = 1.1 });
        canvas.drawArc(Rect.fromLTWH(17.5, 6.0, 4, 4), -0.5, 2.0, false, stroke.apply { strokeWidth = 1.0 });
        }
        else -> {}
    }
  }

  // 5. CAMERA FLIP / SWITCH
  fun _paintCameraFlip(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Dual orbital curved arrows revolving around a central core
        val rect = Rect.fromLTWH(4.0, 4.0, 16.0, 16.0);
        canvas.drawArc(rect, 0.4, 2.2, false, stroke);
        canvas.drawArc(rect, 3.5, 2.2, false, stroke);
        // Arrowheads
        canvas.drawCircle(Offset(12.0, 12.0), 1.5, fill);
        val a1 = Path().apply { moveTo(18.5, 14.5); lineTo(20.5, 11.5); lineTo(16.5, 11.5); close() };
        val a2 = Path().apply { moveTo(5.5, 9.5); lineTo(3.5, 12.5); lineTo(7.5, 12.5); close() };
        canvas.drawPath(a1, fill);
        canvas.drawPath(a2, fill);
        }

      ChatyIconThemeId.precisionContour -> {
        // Sharp right-angled counter-rotating wire arrows with gap breaks
        val p1 = Path().apply { moveTo(12.0, 4.0); lineTo(19.0, 4.0); lineTo(19.0, 11.0) };
        val p2 = Path().apply { moveTo(12.0, 20.0); lineTo(5.0, 20.0); lineTo(5.0, 13.0) };
        canvas.drawPath(p1, stroke.apply { strokeWidth = 1.2 });
        canvas.drawPath(p2, stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(19.0, 11.0), Offset(16.5, 8.5), stroke);
        canvas.drawLine(Offset(5.0, 13.0), Offset(7.5, 15.5), stroke);
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Solid rotating circular shield with stenciled negative-space arrows
        canvas.drawCircle(Offset(12.0, 12.0), 8.5, fill);
        val slit = Paint().apply { color = Colors.white; strokeWidth = 1.6; style = PaintingStyle.stroke };
        canvas.drawArc(Rect.fromLTWH(6.5, 6.5, 11, 11), 0.5, 2.0, false, slit);
        canvas.drawArc(Rect.fromLTWH(6.5, 6.5, 11, 11), 3.6, 2.0, false, slit);
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Angular folded geometric arrows with sharp triangular heads
        val p = Path().apply { moveTo(12.0, 4.5); lineTo(18.0, 6.5); lineTo(16.5, 11.0); moveTo(12.0, 19.5); lineTo(6.0, 17.5); lineTo(7.5, 13.0) };
        canvas.drawPath(p, stroke);
        }

      ChatyIconThemeId.organicMotion -> {
        // Dual fluid liquid waves looping in smooth laminar flow
        val p = Path().apply { moveTo(6.0, 10.0); cubicTo(7.0, 4.0, 17.0, 4.5, 18.0, 10.0); moveTo(18.0, 14.0); cubicTo(17.0, 20.0, 7.0, 19.5, 6.0, 14.0) };
        canvas.drawPath(p, stroke);
        canvas.drawCircle(Offset(18.0, 10.0), 2.0, fill);
        canvas.drawCircle(Offset(6.0, 14.0), 2.0, fill);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit stepped pixel cycle arrows
        canvas.drawRect(Rect.fromLTWH(8, 4, 8, 2), fill);
        canvas.drawRect(Rect.fromLTWH(16, 6, 2, 6), fill);
        canvas.drawRect(Rect.fromLTWH(8, 18, 8, 2), fill);
        canvas.drawRect(Rect.fromLTWH(6, 12, 2, 6), fill);
        // Pixel arrowheads
        canvas.drawRect(Rect.fromLTWH(18, 10, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(4, 12, 2, 2), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // Dual CAD rotation arcs with tangent dimension leader arrows
        canvas.drawArc(Rect.fromLTWH(5, 5, 14, 14), 0.3, 2.3, false, stroke.apply { strokeWidth = 1.2 });
        canvas.drawArc(Rect.fromLTWH(5, 5, 14, 14), 3.4, 2.3, false, stroke.apply { strokeWidth = 1.2 });
        canvas.drawCircle(Offset(12.0, 12.0), 1.2, fill);
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Overlapping dual-plane ribbon arrows with 3D crossover depth
        val r1 = Rect.fromLTWH(4.5, 4.5, 15, 15);
        canvas.drawArc(r1, 0.4, 2.2, false, stroke.apply { strokeWidth = 2.6 });
        canvas.drawArc(r1, 3.5, 2.2, false, Paint().apply { color = color.withValues(alpha = 0.45); style = PaintingStyle.stroke; strokeWidth = 2.6 });
        }

      ChatyIconThemeId.expressiveInk -> {
        // Dynamic two-stroke calligraphic brush swirl
        val s1 = Path().apply { moveTo(6.0, 8.0); quadraticBezierTo(12.0, 3.5, 18.5, 8.5) };
        val s2 = Path().apply { moveTo(18.0, 16.0); quadraticBezierTo(12.0, 20.5, 5.5, 15.5) };
        canvas.drawPath(s1, stroke.apply { strokeWidth = 2.8 });
        canvas.drawPath(s2, stroke.apply { strokeWidth = 2.8 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Bidirectional transceiver arcs with propagating signal pulse packets
        canvas.drawArc(Rect.fromLTWH(5.0, 5.0, 14, 14), 0.2, 2.5, false, stroke.apply { strokeWidth = 1.2 });
        canvas.drawArc(Rect.fromLTWH(5.0, 5.0, 14, 14), 3.3, 2.5, false, stroke.apply { strokeWidth = 1.2 });
        canvas.drawCircle(Offset(18.0, 14.0), 1.8, fill);
        canvas.drawCircle(Offset(6.0, 10.0), 1.8, fill);
        }
        else -> {}
    }
  }

  // 6. CAMERA SHUTTER
  fun _paintCameraShutter(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    val center = Offset(12, 12);
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Outer orbital guide circle with floating inner orbital ring & 4 aperture nodes
        canvas.drawCircle(center, 9.0, stroke);
        canvas.drawCircle(center, 5.5, Paint().apply { color = stroke.color.withValues(alpha = 0.35); style = PaintingStyle.stroke; strokeWidth = 1.0 });
        canvas.drawCircle(Offset(12.0, 6.5), 1.2, fill);
        canvas.drawCircle(Offset(12.0, 17.5), 1.2, fill);
        canvas.drawCircle(Offset(6.5, 12.0), 1.2, fill);
        canvas.drawCircle(Offset(17.5, 12.0), 1.2, fill);
        }

      ChatyIconThemeId.precisionContour -> {
        // Ultra-fine dual concentric drafting circles with 4 precision gap ticks
        canvas.drawCircle(center, 8.8, stroke.apply { strokeWidth = 1.1 });
        canvas.drawCircle(center, 5.2, stroke.apply { strokeWidth = 0.9 });
        val tick = Paint().apply { color = stroke.color; strokeWidth = 1.0 };
        canvas.drawLine(Offset(12, 2.0), Offset(12, 4.0), tick);
        canvas.drawLine(Offset(12, 20.0), Offset(12, 22.0), tick);
        canvas.drawLine(Offset(2.0, 12), Offset(4.0, 12), tick);
        canvas.drawLine(Offset(20.0, 12), Offset(22.0, 12), tick);
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Heavy solid puck with crisp white negative-space ring cut
        canvas.drawCircle(center, 9.2, fill);
        canvas.drawCircle(center, 5.6, Paint().apply { color = Colors.white; style = PaintingStyle.stroke; strokeWidth = 1.8 });
        }

      ChatyIconThemeId.origamiGeometry -> {
        // 12-sided faceted origami polygon with radiating diagonal creases
        val poly = Path();
        for (i in 0 until 12) {
          val a = i * (PI / 6);
          val x = 12 + 8.5 * cos(a);
          val y = 12 + 8.5 * sin(a);
          if (i == 0) poly.moveTo(x, y) else poly.lineTo(x, y);
        }
        poly.close();
        canvas.drawPath(poly, stroke);
        canvas.drawCircle(center, 4.0, stroke);
        }

      ChatyIconThemeId.organicMotion -> {
        // Fluid liquid droplet ring with undulating wave contour
        val wave = Path();
        for (i in 0 until 36) {
          val a = i * (PI / 18);
          val r = 8.5 + 0.9 * sin(a * 4);
          val x = 12 + r * cos(a);
          val y = 12 + r * sin(a);
          if (i == 0) wave.moveTo(x, y) else wave.lineTo(x, y);
        }
        wave.close();
        canvas.drawPath(wave, stroke);
        canvas.drawCircle(center, 3.8, fill);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Stepped 8-bit pixel ring with square center block
        canvas.drawRect(Rect.fromLTWH(8, 3, 8, 2), fill);
        canvas.drawRect(Rect.fromLTWH(8, 19, 8, 2), fill);
        canvas.drawRect(Rect.fromLTWH(3, 8, 2, 8), fill);
        canvas.drawRect(Rect.fromLTWH(19, 8, 2, 8), fill);
        canvas.drawRect(Rect.fromLTWH(5, 5, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(16, 5, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(5, 16, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(16, 16, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(10, 10, 4, 4), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // Technical CAD aperture dial with radial measurement ticks
        canvas.drawCircle(center, 8.8, stroke.apply { strokeWidth = 1.2 });
        for (i in 0 until 8) {
          val a = i * (PI / 4);
          val p1 = Offset(12 + 6.5 * cos(a), 12 + 6.5 * sin(a));
          val p2 = Offset(12 + 8.8 * cos(a), 12 + 8.8 * sin(a));
          canvas.drawLine(p1, p2, stroke.apply { strokeWidth = 0.9 });
        }
        canvas.drawCircle(center, 3.5, stroke.apply { strokeWidth = 1.0 });
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Multi-tier dimensional stepped relief rings
        canvas.drawCircle(center, 9.2, Paint().apply { color = color.withValues(alpha = 0.25); style = PaintingStyle.fill });
        canvas.drawCircle(center, 6.8, stroke.apply { strokeWidth = 2.2 });
        canvas.drawCircle(center, 4.0, fill);
        }

      ChatyIconThemeId.expressiveInk -> {
        // Calligraphic zen enso circle with dynamic thick-to-thin brush stroke
        val enso = Path().apply { addArc(Rect.fromLTWH(3.5, 3.5, 17, 17), 0.3, 5.8) };
        canvas.drawPath(enso, stroke.apply { strokeWidth = 2.8 });
        canvas.drawCircle(center, 3.2, fill);
        }

      ChatyIconThemeId.signalLanguage -> {
        // Radar beacon emitter with 3 concentric radiating sonar wave rings
        canvas.drawCircle(center, 2.2, fill);
        canvas.drawCircle(center, 5.2, stroke.apply { strokeWidth = 1.1 });
        canvas.drawCircle(center, 8.8, stroke.apply { strokeWidth = 1.4 });
        }
        else -> {}
    }
  }

  // 7. CALLS / AUDIO CALL
  fun _paintCalls(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Curved handset with orbital signal halo
        val p = Path().apply { moveTo(6.5, 4.5); lineTo(9.5, 4.5); lineTo(10.8, 8.0); lineTo(8.8, 10.0); quadraticBezierTo(10.2, 13.8, 14.0, 15.2); lineTo(16.0, 13.2); lineTo(19.5, 14.5); lineTo(19.5, 17.5); quadraticBezierTo(19.5, 19.5, 17.2, 19.5); cubicTo(10.5, 19.5, 4.5, 13.5, 4.5, 6.8); quadraticBezierTo(4.5, 4.5, 6.5, 4.5) };
        canvas.drawPath(p, stroke);
        // Orbital sound halo
        canvas.drawArc(Rect.fromLTWH(14, 2.5, 7, 7), -1.2, 1.4, false, stroke.apply { strokeWidth = 1.1 });
        }

      ChatyIconThemeId.precisionContour -> {
        // Architectural wireframe handset with 45° chamfers
        val p = Path().apply { moveTo(6.5, 4.0); lineTo(9.5, 4.0); lineTo(11.0, 7.5); lineTo(9.0, 9.5); lineTo(14.5, 15.0); lineTo(16.5, 13.0); lineTo(20.0, 14.5); lineTo(20.0, 17.5); lineTo(17.5, 20.0); lineTo(14.0, 20.0); lineTo(4.0, 10.0); lineTo(4.0, 6.5); close() };
        canvas.drawPath(p, stroke.apply { strokeWidth = 1.2 });
        }

      ChatyIconThemeId.monolithicCutout -> {
        // 100% solid filled handset silhouette with stenciled grip cut
        val p = Path().apply { moveTo(6.5, 4.5); lineTo(9.5, 4.5); lineTo(10.8, 8.0); lineTo(8.8, 10.0); quadraticBezierTo(10.2, 13.8, 14.0, 15.2); lineTo(16.0, 13.2); lineTo(19.5, 14.5); lineTo(19.5, 17.5); quadraticBezierTo(19.5, 19.5, 17.2, 19.5); cubicTo(10.5, 19.5, 4.5, 13.5, 4.5, 6.8); close() };
        canvas.drawPath(p, fill);
        val grip = Paint().apply { color = Colors.white; strokeWidth = 1.4; style = PaintingStyle.stroke };
        canvas.drawLine(Offset(9.5, 11.5), Offset(12.5, 14.5), grip);
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Faceted angular origami handset folded from geometric planes
        val p = Path().apply { moveTo(7.0, 4.5); lineTo(10.5, 4.5); lineTo(12.0, 8.0); lineTo(9.5, 10.5); lineTo(13.5, 14.5); lineTo(16.0, 12.0); lineTo(19.5, 13.5); lineTo(19.5, 17.0); lineTo(17.0, 19.5); lineTo(13.0, 18.0); lineTo(6.0, 11.0); lineTo(4.5, 7.0); close() };
        canvas.drawPath(p, stroke);
        }

      ChatyIconThemeId.organicMotion -> {
        // Smooth asymmetrical fluid kidney-bean handset
        val p = Path().apply { moveTo(7.0, 4.5); cubicTo(10.0, 4.0, 11.0, 7.5, 9.5, 9.5); cubicTo(10.5, 13.5, 13.5, 14.5, 16.0, 13.0); cubicTo(18.0, 11.5, 20.0, 14.0, 19.5, 17.0); cubicTo(19.0, 20.0, 16.0, 20.0, 14.0, 19.0); cubicTo(8.0, 16.0, 4.5, 12.0, 4.5, 7.5); cubicTo(4.5, 5.0, 5.5, 4.5, 7.0, 4.5) };
        canvas.drawPath(p, stroke);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit retro phone handset composed of pixel blocks
        canvas.drawRect(Rect.fromLTWH(5, 4, 5, 4), fill);
        canvas.drawRect(Rect.fromLTWH(7, 8, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(9, 11, 4, 3), fill);
        canvas.drawRect(Rect.fromLTWH(13, 14, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(15, 15, 5, 4), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD schematic handset with transmitter/receiver datum marks
        val p = Path().apply { moveTo(6.5, 4.5); lineTo(9.5, 4.5); lineTo(10.8, 8.0); lineTo(8.8, 10.0); quadraticBezierTo(10.2, 13.8, 14.0, 15.2); lineTo(16.0, 13.2); lineTo(19.5, 14.5); lineTo(19.5, 17.5); quadraticBezierTo(19.5, 19.5, 17.2, 19.5); cubicTo(10.5, 19.5, 4.5, 13.5, 4.5, 6.8); close() };
        canvas.drawPath(p, stroke.apply { strokeWidth = 1.2 });
        canvas.drawCircle(Offset(8.0, 6.2), 1.0, fill);
        canvas.drawCircle(Offset(17.8, 16.0), 1.0, fill);
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Embossed multi-plane handset with relief shell
        val p = Path().apply { moveTo(6.5, 4.5); lineTo(9.5, 4.5); lineTo(10.8, 8.0); lineTo(8.8, 10.0); quadraticBezierTo(10.2, 13.8, 14.0, 15.2); lineTo(16.0, 13.2); lineTo(19.5, 14.5); lineTo(19.5, 17.5); quadraticBezierTo(19.5, 19.5, 17.2, 19.5); cubicTo(10.5, 19.5, 4.5, 13.5, 4.5, 6.8); close() };
        canvas.drawPath(p, fill);
        canvas.drawCircle(Offset(8.0, 6.0), 2.0, secondary);
        canvas.drawCircle(Offset(18.0, 16.0), 2.0, secondary);
        }

      ChatyIconThemeId.expressiveInk -> {
        // Swift confident calligraphic brush sweep
        val p = Path().apply { moveTo(6.0, 4.5); quadraticBezierTo(10.5, 6.0, 9.0, 9.0); quadraticBezierTo(11.0, 13.5, 14.5, 14.5); quadraticBezierTo(18.5, 13.5, 19.5, 17.5); cubicTo(18.0, 20.0, 12.0, 19.5, 5.0, 10.0); quadraticBezierTo(4.0, 5.5, 6.0, 4.5) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 2.6 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Telecommunications antenna handset with 3 broadcast wave arcs
        val p = Path().apply { moveTo(6.5, 4.5); lineTo(9.5, 4.5); lineTo(10.8, 8.0); lineTo(8.8, 10.0); quadraticBezierTo(10.2, 13.8, 14.0, 15.2); lineTo(16.0, 13.2); lineTo(19.5, 14.5); lineTo(19.5, 17.5); quadraticBezierTo(19.5, 19.5, 17.2, 19.5); cubicTo(10.5, 19.5, 4.5, 13.5, 4.5, 6.8); close() };
        canvas.drawPath(p, stroke);
        canvas.drawArc(Rect.fromLTWH(13, 2, 8, 8), -1.4, 1.2, false, stroke.apply { strokeWidth = 1.0 });
        canvas.drawArc(Rect.fromLTWH(11, 0, 12, 12), -1.4, 1.2, false, stroke.apply { strokeWidth = 1.0 });
        }
        else -> {}
    }
  }

  // 8. VIDEO CALL
  fun _paintVideo(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint, off: Boolean) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Sleek curved video camera with orbital lens cone
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.5, 6.5, 11.5, 11.0), Radius.circular(3.0)), stroke);
        val cone = Path().apply { moveTo(15.0, 10.0); lineTo(20.5, 6.8); lineTo(20.5, 17.2); lineTo(15.0, 14.0); close() };
        canvas.drawPath(cone, stroke);
        canvas.drawCircle(Offset(9.2, 12.0), 2.0, fill);
        }

      ChatyIconThemeId.precisionContour -> {
        // Open-cornered rectangular wireframe camera with detached trapezoidal lens
        canvas.drawRect(Rect.fromLTWH(3.5, 6.5, 11.0, 11.0), stroke.apply { strokeWidth = 1.2 });
        val cone = Path().apply { moveTo(15.5, 9.5); lineTo(20.5, 6.5); lineTo(20.5, 17.5); lineTo(15.5, 14.5); close() };
        canvas.drawPath(cone, stroke.apply { strokeWidth = 1.2 });
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Solid camera wedge silhouette with negative-space lens pupil
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.5, 6.5, 11.5, 11.0), Radius.circular(1.5)), fill);
        val cone = Path().apply { moveTo(15.0, 9.5); lineTo(20.5, 6.5); lineTo(20.5, 17.5); lineTo(15.0, 14.5); close() };
        canvas.drawPath(cone, fill);
        canvas.drawCircle(Offset(9.2, 12.0), 2.2, Paint().apply { color = Colors.white; style = PaintingStyle.fill });
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Faceted polygonal video body with sharp 60° triangular projection cone
        val b = Path().apply { moveTo(3.5, 7.0); lineTo(15.0, 6.0); lineTo(15.0, 18.0); lineTo(3.5, 17.0); close() };
        canvas.drawPath(b, stroke);
        val c = Path().apply { moveTo(15.0, 9.0); lineTo(20.5, 6.0); lineTo(20.5, 18.0); lineTo(15.0, 15.0); close() };
        canvas.drawPath(c, fill);
        }

      ChatyIconThemeId.organicMotion -> {
        // Fluid asymmetric teardrop camera housing with smooth bulbous lens
        val b = Path().apply { moveTo(4.5, 7.5); cubicTo(14.5, 5.5, 15.5, 18.5, 4.5, 16.5); close() };
        canvas.drawPath(b, stroke);
        val c = Path().apply { moveTo(14.5, 10.0); quadraticBezierTo(20.5, 7.5, 20.5, 12.0); quadraticBezierTo(20.5, 16.5, 14.5, 14.0); close() };
        canvas.drawPath(c, stroke);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Stepped 8-bit pixel video camera with stepped cone
        canvas.drawRect(Rect.fromLTWH(4, 7, 10, 10), stroke.apply { strokeWidth = 1.8 });
        canvas.drawRect(Rect.fromLTWH(15, 10, 2, 4), fill);
        canvas.drawRect(Rect.fromLTWH(17, 8, 2, 8), fill);
        canvas.drawRect(Rect.fromLTWH(19, 6, 2, 12), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD video schematic with optical focal guide lines
        canvas.drawRect(Rect.fromLTWH(3.5, 7.0, 11.0, 10.0), stroke.apply { strokeWidth = 1.2 });
        val cone = Path().apply { moveTo(14.5, 9.5); lineTo(20.5, 6.5); lineTo(20.5, 17.5); lineTo(14.5, 14.5); close() };
        canvas.drawPath(cone, stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(9.0, 5.0), Offset(9.0, 19.0), Paint().apply { color = stroke.color.withValues(alpha = 0.5); strokeWidth = 0.8 });
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Multi-tier dimensional camera housing with raised side panel
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.5, 6.5, 11.5, 11.0), Radius.circular(2.0)), fill);
        val cone = Path().apply { moveTo(15.0, 9.5); lineTo(20.5, 6.5); lineTo(20.5, 17.5); lineTo(15.0, 14.5); close() };
        canvas.drawPath(cone, secondary);
        }

      ChatyIconThemeId.expressiveInk -> {
        // Bold gestural brush-painted camera housing
        val b = Path().apply { moveTo(4.0, 7.0); lineTo(15.0, 7.0); lineTo(15.0, 17.0); lineTo(4.0, 17.0); close() };
        canvas.drawPath(b, stroke.apply { strokeWidth = 2.4 });
        val c = Path().apply { moveTo(15.0, 9.5); lineTo(20.5, 6.5); lineTo(20.5, 17.5); lineTo(15.0, 14.5) };
        canvas.drawPath(c, stroke.apply { strokeWidth = 2.4 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Video broadcast transmitter camera with radiating video beams
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.5, 6.5, 11.0, 11.0), Radius.circular(2.0)), stroke);
        val cone = Path().apply { moveTo(14.5, 9.5); lineTo(19.0, 7.0); lineTo(19.0, 17.0); lineTo(14.5, 14.5); close() };
        canvas.drawPath(cone, stroke);
        canvas.drawArc(Rect.fromLTWH(17, 8, 4, 8), -PI / 2, PI, false, stroke.apply { strokeWidth = 1.0 });
        }
        else -> {}
    }

    if (off) {
      canvas.drawLine(Offset(3.5, 4.0), Offset(20.5, 20.0), stroke.apply { color = Colors.redAccent; strokeWidth = 2.0 });
    }
  }

  // 9. SEND ACTION
  fun _paintSend(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Aerodynamic orbital glider dart with central orbital negative-space slit
        val dart = Path().apply { moveTo(3.5, 3.5); lineTo(21.0, 12.0); lineTo(3.5, 20.5); lineTo(7.5, 12.0); close() };
        canvas.drawPath(dart, stroke);
        canvas.drawLine(Offset(7.5, 12.0), Offset(15.0, 12.0), stroke);
        canvas.drawCircle(Offset(15.0, 12.0), 1.5, fill);
        }

      ChatyIconThemeId.precisionContour -> {
        // Razor-sharp dual-wire dart with parallel wing contours
        val dart = Path().apply { moveTo(3.5, 3.0); lineTo(21.5, 12.0); lineTo(3.5, 21.0); lineTo(8.0, 12.0); close() };
        canvas.drawPath(dart, stroke.apply { strokeWidth = 1.2 });
        // Parallel inner drafting wire
        val inner = Path().apply { moveTo(6.5, 6.5); lineTo(16.5, 12.0); lineTo(6.5, 17.5); lineTo(9.5, 12.0); close() };
        canvas.drawPath(inner, stroke.apply { strokeWidth = 0.8 });
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Solid filled heavy arrowhead silhouette with stark negative-space keel slot
        val dart = Path().apply { moveTo(3.5, 3.5); lineTo(21.0, 12.0); lineTo(3.5, 20.5); lineTo(7.5, 12.0); close() };
        canvas.drawPath(dart, fill);
        val slit = Paint().apply { color = Colors.white; strokeWidth = 1.8; style = PaintingStyle.stroke };
        canvas.drawLine(Offset(6.5, 12.0), Offset(16.5, 12.0), slit);
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Classic folded origami paper airplane with sharp angular creases
        val topWing = Path().apply { moveTo(3.0, 3.0); lineTo(21.0, 12.0); lineTo(8.0, 12.0); close() };
        val btmWing = Path().apply { moveTo(3.0, 21.0); lineTo(21.0, 12.0); lineTo(8.0, 12.0); close() };
        canvas.drawPath(topWing, fill);
        canvas.drawPath(btmWing, stroke);
        canvas.drawLine(Offset(8.0, 12.0), Offset(21.0, 12.0), Paint().apply { color = Colors.white70; strokeWidth = 1.0 });
        }

      ChatyIconThemeId.organicMotion -> {
        // Fluid curved manta-ray / droplet arrow gliding dynamically
        val ray = Path().apply { moveTo(3.5, 4.5); quadraticBezierTo(10.0, 9.0, 21.0, 12.0); quadraticBezierTo(10.0, 15.0, 3.5, 19.5); quadraticBezierTo(7.5, 12.0, 3.5, 4.5) };
        canvas.drawPath(ray, stroke);
        canvas.drawCircle(Offset(11.5, 12.0), 2.2, fill);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit pixel arrow dart
        canvas.drawRect(Rect.fromLTWH(4, 4, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(7, 6, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(10, 8, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(13, 10, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(16, 11, 4, 2), fill);
        canvas.drawRect(Rect.fromLTWH(13, 11, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(10, 13, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(7, 15, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(4, 17, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(8, 11, 6, 2), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // Supersonic technical vector schematic with coordinate flight axis
        val dart = Path().apply { moveTo(3.5, 3.0); lineTo(21.5, 12.0); lineTo(3.5, 21.0); lineTo(7.5, 12.0); close() };
        canvas.drawPath(dart, stroke.apply { strokeWidth = 1.2 });
        // Guide lines
        val cad = Paint().apply { color = stroke.color.withValues(alpha = 0.5); strokeWidth = 0.8 };
        canvas.drawLine(Offset(1.0, 12.0), Offset(23.0, 12.0), cad);
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Stealth-fighter faceted dart with raised central spine ridge
        val topFacet = Path().apply { moveTo(3.5, 4.0); lineTo(21.0, 12.0); lineTo(7.5, 12.0); close() };
        val btmFacet = Path().apply { moveTo(3.5, 20.0); lineTo(21.0, 12.0); lineTo(7.5, 12.0); close() };
        canvas.drawPath(topFacet, fill);
        canvas.drawPath(btmFacet, Paint().apply { color = color.withValues(alpha = 0.35); style = PaintingStyle.fill });
        }

      ChatyIconThemeId.expressiveInk -> {
        // Dynamic Japanese calligraphic brush stroke arrow
        val dart = Path().apply { moveTo(4.0, 3.5); quadraticBezierTo(12.0, 8.5, 21.5, 12.0); quadraticBezierTo(12.0, 15.5, 4.0, 20.5); quadraticBezierTo(8.0, 12.0, 4.0, 3.5) };
        canvas.drawPath(dart, stroke.apply { strokeWidth = 2.6 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Directional telemetry beam dart with trailing signal impulse rings
        val dart = Path().apply { moveTo(7.0, 6.0); lineTo(20.5, 12.0); lineTo(7.0, 18.0); lineTo(10.0, 12.0); close() };
        canvas.drawPath(dart, stroke.apply { strokeWidth = 1.8 });
        canvas.drawArc(Rect.fromLTWH(2, 7, 5, 10), -PI / 2, PI, false, stroke.apply { strokeWidth = 1.1 });
        canvas.drawArc(Rect.fromLTWH(0, 5, 5, 14), -PI / 2, PI, false, stroke.apply { strokeWidth = 1.1 });
        }
        else -> {}
    }
  }

  // 10. ATTACHMENT / PAPERCLIP
  fun _paintAttachment(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Continuous orbital paperclip loop with smooth concentric fillets
        val p = Path().apply { moveTo(16.5, 8.5); lineTo(9.5, 15.5); arcToPoint(Offset(6.5, 12.5), radius = Radius.circular(2.1), clockwise = true); lineTo(14.0, 5.0); arcToPoint(Offset(18.5, 9.5), radius = Radius.circular(3.2), clockwise = true); lineTo(10.5, 17.5); arcToPoint(Offset(6.0, 13.0), radius = Radius.circular(4.2), clockwise = true) };
        canvas.drawPath(p, stroke);
        }

      ChatyIconThemeId.precisionContour -> {
        // Open architectural paperclip with sharp 90° mitered turns
        val p = Path().apply { moveTo(16.0, 8.0); lineTo(10.0, 14.0); lineTo(7.5, 11.5); lineTo(13.5, 5.5); lineTo(18.0, 10.0); lineTo(11.0, 17.0); lineTo(6.0, 12.0) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 1.2 });
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Solid capsule silhouette with stenciled white spiral slit
        canvas.save();
        canvas.translate(12, 12);
        canvas.rotate(PI / 4);
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(-4.5, -8.0, 9.0, 16.0), Radius.circular(4.5)), fill);
        val slit = Paint().apply { color = Colors.white; strokeWidth = 1.6; style = PaintingStyle.stroke };
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(-2.0, -5.5, 4.0, 11.0), Radius.circular(2.0)), slit);
        canvas.restore();
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Angular folded ribbon paperclip with 45°/135° diagonal corners
        val p = Path().apply { moveTo(16.5, 7.5); lineTo(10.5, 13.5); lineTo(8.5, 11.5); lineTo(13.5, 6.5); lineTo(17.5, 10.5); lineTo(11.5, 16.5); lineTo(6.5, 11.5) };
        canvas.drawPath(p, stroke);
        }

      ChatyIconThemeId.organicMotion -> {
        // Organic looping droplet coil resembling a natural water swirl
        val p = Path().apply { moveTo(16.0, 8.0); quadraticBezierTo(9.0, 15.0, 7.0, 13.0); quadraticBezierTo(5.0, 11.0, 12.0, 4.0); quadraticBezierTo(19.0, 7.0, 11.0, 17.0) };
        canvas.drawPath(p, stroke);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Pixelated paperclip with 90° modular block turns
        canvas.drawRect(Rect.fromLTWH(14, 6, 4, 2), fill);
        canvas.drawRect(Rect.fromLTWH(10, 10, 2, 6), fill);
        canvas.drawRect(Rect.fromLTWH(6, 12, 2, 6), fill);
        canvas.drawRect(Rect.fromLTWH(8, 18, 6, 2), fill);
        canvas.drawRect(Rect.fromLTWH(14, 12, 2, 6), fill);
        canvas.drawRect(Rect.fromLTWH(16, 8, 2, 6), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // Precision wire gauge blueprint with bend radius callouts
        val p = Path().apply { moveTo(16.5, 8.5); lineTo(9.5, 15.5); arcToPoint(Offset(6.5, 12.5), radius = Radius.circular(2.1), clockwise = true); lineTo(14.0, 5.0); arcToPoint(Offset(18.5, 9.5), radius = Radius.circular(3.2), clockwise = true); lineTo(10.5, 17.5) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 1.2 });
        canvas.drawCircle(Offset(16.5, 8.5), 1.2, fill);
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Overlapping multi-tier interlocking clip bands
        val p = Path().apply { moveTo(16.5, 8.5); lineTo(9.5, 15.5); arcToPoint(Offset(6.5, 12.5), radius = Radius.circular(2.1), clockwise = true); lineTo(14.0, 5.0) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 2.8 });
        val p2 = Path().apply { arcToPoint(Offset(18.5, 9.5), radius = Radius.circular(3.2), clockwise = true); lineTo(10.5, 17.5) };
        canvas.drawPath(p2, Paint().apply { color = color.withValues(alpha = 0.4); style = PaintingStyle.stroke; strokeWidth = 2.8 });
        }

      ChatyIconThemeId.expressiveInk -> {
        // Sweeping continuous calligraphic brush ribbon loop
        val p = Path().apply { moveTo(16.0, 8.0); cubicTo(10.0, 14.0, 6.0, 12.0, 13.0, 5.0); cubicTo(19.0, 9.0, 12.0, 18.0, 6.0, 12.0) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 2.6 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Interlinked transmission frequency loops
        canvas.drawArc(Rect.fromLTWH(6, 6, 12, 12), -0.6, 3.8, false, stroke.apply { strokeWidth = 1.8 });
        canvas.drawArc(Rect.fromLTWH(8, 8, 8, 8), 1.2, 3.8, false, stroke.apply { strokeWidth = 1.2 });
        }
        else -> {}
    }
  }

  // 11. MICROPHONE
  fun _paintMic(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint, off: Boolean) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Rounded capsule mic inside orbital suspension arc
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(8.5, 4.0, 7.0, 11.0), Radius.circular(3.5)), stroke);
        canvas.drawArc(Rect.fromLTWH(5.5, 6.5, 13.0, 10.0), 0, PI, false, stroke);
        canvas.drawLine(Offset(12.0, 16.5), Offset(12.0, 20.0), stroke);
        canvas.drawLine(Offset(8.0, 20.0), Offset(16.0, 20.0), stroke);
        canvas.drawCircle(Offset(12.0, 8.0), 1.2, fill);
        }

      ChatyIconThemeId.precisionContour -> {
        // Rectangular wireframe capsule with open bottom arc & crosshair stand
        canvas.drawRect(Rect.fromLTWH(8.5, 4.0, 7.0, 10.0), stroke.apply { strokeWidth = 1.2 });
        canvas.drawArc(Rect.fromLTWH(5.5, 6.5, 13.0, 9.0), 0, PI, false, stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(12.0, 15.5), Offset(12.0, 20.0), stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(7.5, 20.0), Offset(16.5, 20.0), stroke.apply { strokeWidth = 1.2 });
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Solid capsule mic with stenciled sound grille slots
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(8.0, 3.5, 8.0, 12.0), Radius.circular(4.0)), fill);
        val slit = Paint().apply { color = Colors.white; strokeWidth = 1.2; style = PaintingStyle.stroke };
        canvas.drawLine(Offset(9.5, 7.0), Offset(14.5, 7.0), slit);
        canvas.drawLine(Offset(9.5, 10.0), Offset(14.5, 10.0), slit);
        canvas.drawArc(Rect.fromLTWH(5.0, 6.0, 14.0, 11.0), 0, PI, false, stroke.apply { strokeWidth = 2.0 });
        canvas.drawLine(Offset(12.0, 17.0), Offset(12.0, 20.5), stroke.apply { strokeWidth = 2.0 });
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Hexagonal origami prism microphone
        val hex = Path().apply { moveTo(12.0, 3.5); lineTo(15.5, 6.0); lineTo(15.5, 12.0); lineTo(12.0, 14.5); lineTo(8.5, 12.0); lineTo(8.5, 6.0); close() };
        canvas.drawPath(hex, stroke);
        canvas.drawArc(Rect.fromLTWH(5.5, 6.5, 13.0, 9.5), 0, PI, false, stroke);
        canvas.drawLine(Offset(12.0, 16.0), Offset(12.0, 20.0), stroke);
        }

      ChatyIconThemeId.organicMotion -> {
        // Smooth fluid droplet capsule on curving stalk
        val drop = Path().apply { moveTo(12.0, 3.5); cubicTo(16.0, 3.5, 16.5, 12.0, 12.0, 14.0); cubicTo(7.5, 12.0, 8.0, 3.5, 12.0, 3.5) };
        canvas.drawPath(drop, stroke);
        canvas.drawArc(Rect.fromLTWH(5.5, 6.0, 13.0, 10.0), 0.1, 2.9, false, stroke);
        val stalk = Path().apply { moveTo(12.0, 16.0); quadraticBezierTo(13.0, 18.0, 12.0, 20.5) };
        canvas.drawPath(stalk, stroke);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit stepped pixel microphone
        canvas.drawRect(Rect.fromLTWH(9, 4, 6, 9), fill);
        canvas.drawRect(Rect.fromLTWH(6, 8, 2, 5), fill);
        canvas.drawRect(Rect.fromLTWH(16, 8, 2, 5), fill);
        canvas.drawRect(Rect.fromLTWH(8, 13, 8, 2), fill);
        canvas.drawRect(Rect.fromLTWH(11, 15, 2, 4), fill);
        canvas.drawRect(Rect.fromLTWH(8, 19, 8, 2), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD acoustic pickup schematic with reception radius ticks
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(8.5, 4.0, 7.0, 10.5), Radius.circular(3.5)), stroke.apply { strokeWidth = 1.2 });
        canvas.drawArc(Rect.fromLTWH(5.5, 6.5, 13.0, 9.5), 0, PI, false, stroke.apply { strokeWidth = 1.0 });
        canvas.drawLine(Offset(12.0, 16.0), Offset(12.0, 20.5), stroke.apply { strokeWidth = 1.0 });
        // Datum ticks
        canvas.drawLine(Offset(10.0, 9.0), Offset(14.0, 9.0), stroke.apply { strokeWidth = 0.8 });
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Sculpted capsule with raised central mesh band
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(8.5, 3.5, 7.0, 11.0), Radius.circular(3.5)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(7.5, 7.5, 9.0, 3.5), Radius.circular(1.0)), secondary);
        canvas.drawArc(Rect.fromLTWH(5.0, 6.0, 14.0, 10.0), 0, PI, false, stroke.apply { strokeWidth = 2.2 });
        canvas.drawLine(Offset(12.0, 16.0), Offset(12.0, 20.0), stroke.apply { strokeWidth = 2.2 });
        }

      ChatyIconThemeId.expressiveInk -> {
        // Bold expressive brush capsule
        val p = Path().apply { moveTo(8.5, 4.0); lineTo(15.5, 4.0); lineTo(15.5, 12.0); lineTo(8.5, 12.0); close() };
        canvas.drawPath(p, stroke.apply { strokeWidth = 2.4 });
        canvas.drawArc(Rect.fromLTWH(5.0, 6.0, 14.0, 10.0), 0, PI, false, stroke.apply { strokeWidth = 2.6 });
        canvas.drawLine(Offset(12.0, 16.0), Offset(12.0, 20.0), stroke.apply { strokeWidth = 2.4 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Voice broadcast transmitter with radiating audio wave arcs
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(9.0, 5.0, 6.0, 10.0), Radius.circular(3.0)), stroke);
        canvas.drawArc(Rect.fromLTWH(5.0, 6.0, 14.0, 9.0), 0, PI, false, stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(12.0, 15.0), Offset(12.0, 20.0), stroke);
        // Radiating audio wave arcs
        canvas.drawArc(Rect.fromLTWH(3.0, 5.0, 4.0, 8.0), -1.2, 2.4, false, stroke.apply { strokeWidth = 1.0 });
        canvas.drawArc(Rect.fromLTWH(17.0, 5.0, 4.0, 8.0), 1.9, 2.4, false, stroke.apply { strokeWidth = 1.0 });
        }
        else -> {}
    }

    if (off) {
      canvas.drawLine(Offset(4.0, 4.0), Offset(20.0, 20.0), stroke.apply { color = Colors.redAccent; strokeWidth = 2.0 });
    }
  }

  // 12. HEART / FAVOURITE / EMOJI
  fun _paintHeart(canvas: IconCanvas, stroke: Paint, fill: Paint, fillHeart: Boolean) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Orbital heart with orbital center aperture
        val p = Path().apply { moveTo(12.0, 20.0); cubicTo(4.0, 14.5, 3.5, 8.5, 7.5, 5.5); cubicTo(10.5, 3.5, 12.0, 6.5, 12.0, 6.5); cubicTo(12.0, 6.5, 13.5, 3.5, 16.5, 5.5); cubicTo(20.5, 8.5, 20.0, 14.5, 12.0, 20.0) };
        canvas.drawPath(p,(if (fillHeart) fill else stroke));
        canvas.drawCircle(Offset(12.0, 10.5), 1.5,(if (fillHeart) (Paint().apply { color = Colors.white; style = PaintingStyle.fill }) else stroke));
        }

      ChatyIconThemeId.precisionContour -> {
        // Geometric angular faceted wire heart
        val p = Path().apply { moveTo(12.0, 19.5); lineTo(4.5, 12.5); lineTo(4.5, 7.5); lineTo(8.5, 4.5); lineTo(12.0, 8.0); lineTo(15.5, 4.5); lineTo(19.5, 7.5); lineTo(19.5, 12.5); close() };
        canvas.drawPath(p, stroke.apply { strokeWidth = 1.2 });
        }

      ChatyIconThemeId.monolithicCutout -> {
        // 100% solid filled heart silhouette with internal negative-space core slit
        val p = Path().apply { moveTo(12.0, 20.0); cubicTo(4.0, 14.5, 3.5, 8.0, 7.5, 5.0); cubicTo(10.5, 3.0, 12.0, 6.5, 12.0, 6.5); cubicTo(12.0, 6.5, 13.5, 3.0, 16.5, 5.0); cubicTo(20.5, 8.0, 20.0, 14.5, 12.0, 20.0) };
        canvas.drawPath(p, fill);
        val slit = Paint().apply { color = Colors.white; strokeWidth = 1.4; style = PaintingStyle.stroke };
        canvas.drawLine(Offset(12.0, 8.5), Offset(12.0, 14.5), slit);
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Origami paper-folded heart with sharp angular bottom & diagonal crease
        val p = Path().apply { moveTo(12.0, 20.0); lineTo(4.0, 12.0); lineTo(7.5, 5.0); lineTo(12.0, 8.5); lineTo(16.5, 5.0); lineTo(20.0, 12.0); close() };
        canvas.drawPath(p,(if (fillHeart) fill else stroke));
        canvas.drawLine(Offset(12.0, 8.5), Offset(12.0, 20.0), Paint().apply { color = Colors.white60; strokeWidth = 0.9 });
        }

      ChatyIconThemeId.organicMotion -> {
        // Asymmetrical fluid droplet heart
        val p = Path().apply { moveTo(12.0, 19.5); cubicTo(3.5, 14.0, 4.0, 7.0, 8.0, 5.0); cubicTo(11.0, 3.5, 12.0, 7.0, 12.0, 7.0); cubicTo(12.0, 7.0, 13.0, 4.0, 16.5, 6.0); cubicTo(21.0, 9.0, 19.5, 15.0, 12.0, 19.5) };
        canvas.drawPath(p,(if (fillHeart) fill else stroke));
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Authentic arcade 8-bit stepped pixel heart
        canvas.drawRect(Rect.fromLTWH(6, 6, 4, 3), fill);
        canvas.drawRect(Rect.fromLTWH(14, 6, 4, 3), fill);
        canvas.drawRect(Rect.fromLTWH(4, 9, 16, 4), fill);
        canvas.drawRect(Rect.fromLTWH(6, 13, 12, 3), fill);
        canvas.drawRect(Rect.fromLTWH(8, 16, 8, 3), fill);
        canvas.drawRect(Rect.fromLTWH(10, 19, 4, 2), fill);
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD parametric heart with central datum axis
        val p = Path().apply { moveTo(12.0, 20.0); cubicTo(4.0, 14.5, 3.5, 8.0, 7.5, 5.0); cubicTo(10.5, 3.0, 12.0, 6.5, 12.0, 6.5); cubicTo(12.0, 6.5, 13.5, 3.0, 16.5, 5.0); cubicTo(20.5, 8.0, 20.0, 14.5, 12.0, 20.0) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(12.0, 3.0), Offset(12.0, 21.5), Paint().apply { color = stroke.color.withValues(alpha = 0.5); strokeWidth = 0.8 });
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Embossed sculptural heart with split dimensional facet planes
        val leftHalf = Path().apply { moveTo(12.0, 20.0); cubicTo(4.0, 14.5, 3.5, 8.0, 7.5, 5.0); cubicTo(10.5, 3.0, 12.0, 6.5, 12.0, 6.5); close() };
        val rightHalf = Path().apply { moveTo(12.0, 6.5); cubicTo(12.0, 6.5, 13.5, 3.0, 16.5, 5.0); cubicTo(20.5, 8.0, 20.0, 14.5, 12.0, 20.0); close() };
        canvas.drawPath(leftHalf, fill);
        canvas.drawPath(rightHalf, Paint().apply { color = color.withValues(alpha = 0.4); style = PaintingStyle.fill });
        }

      ChatyIconThemeId.expressiveInk -> {
        // Passionate brush-stroke heart with dynamic pressure flares
        val p = Path().apply { moveTo(12.0, 19.5); cubicTo(3.0, 13.5, 4.0, 6.5, 8.5, 5.0); quadraticBezierTo(11.0, 4.5, 12.0, 7.5); quadraticBezierTo(13.0, 4.5, 15.5, 5.0); cubicTo(20.0, 6.5, 21.0, 13.5, 12.0, 19.5) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 2.6 });
        }

      ChatyIconThemeId.signalLanguage -> {
        // Pulsating signal heart with concentric broadcast echo rings
        canvas.drawCircle(Offset(12.0, 12.0), 3.0, fill);
        canvas.drawArc(Rect.fromLTWH(5, 5, 14, 14), 0.3, 2.5, false, stroke.apply { strokeWidth = 1.2 });
        canvas.drawArc(Rect.fromLTWH(5, 5, 14, 14), 3.4, 2.5, false, stroke.apply { strokeWidth = 1.2 });
        }
        else -> {}
    }
  }

  // 13. SETTINGS
  fun _paintSettings(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    val center = Offset(12, 12);
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Cog with 6 orbital rounded lobes and an open orbital hub
        val cog = Path();
        for (i in 0 until 6) {
          val a = i * (PI / 3);
          val r1 = 8.8;
          val r2 = 6.8;
          val p1 = Offset(12 + r1 * cos(a - 0.25), 12 + r1 * sin(a - 0.25));
          val p2 = Offset(12 + r1 * cos(a + 0.25), 12 + r1 * sin(a + 0.25));
          val p3 = Offset(12 + r2 * cos(a + 0.35), 12 + r2 * sin(a + 0.35));
          if (i == 0) cog.moveTo(p1.dx, p1.dy) else cog.lineTo(p1.dx, p1.dy);
          cog.lineTo(p2.dx, p2.dy);
          cog.lineTo(p3.dx, p3.dy);
        }
        cog.close();
        canvas.drawPath(cog, stroke);
        canvas.drawCircle(center, 2.8, stroke);
        }

      ChatyIconThemeId.precisionContour -> {
        // Square-toothed technical gear with crosshair drafting axes
        canvas.drawCircle(center, 7.5, stroke.apply { strokeWidth = 1.1 });
        canvas.drawCircle(center, 3.0, stroke.apply { strokeWidth = 1.0 });
        for (i in 0 until 4) {
          val a = i * (PI / 2);
          val x = 12 + 8.5 * cos(a);
          val y = 12 + 8.5 * sin(a);
          canvas.drawRect(Rect.fromCenter(center = Offset(x, y), width = 2.5, height = 2.5), stroke.apply { strokeWidth = 1.0 });
        }
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Solid industrial gear disk with stenciled negative-space radial spokes
        canvas.drawCircle(center, 8.8, fill);
        canvas.drawCircle(center, 3.2, Paint().apply { color = Colors.white; style = PaintingStyle.fill });
        val slit = Paint().apply { color = Colors.white; strokeWidth = 1.2; style = PaintingStyle.stroke };
        canvas.drawLine(Offset(4, 12), Offset(20, 12), slit);
        canvas.drawLine(Offset(12, 4), Offset(12, 20), slit);
        }

      ChatyIconThemeId.origamiGeometry -> {
        // 8-pointed origami faceted star wheel
        val star = Path();
        for (i in 0 until 8) {
          val a = i * (PI / 4);
          val r =(if ((i % 2 == 0)) 9.0 else 5.5);
          val x = 12 + r * cos(a);
          val y = 12 + r * sin(a);
          if (i == 0) star.moveTo(x, y) else star.lineTo(x, y);
        }
        star.close();
        canvas.drawPath(star, stroke);
        canvas.drawCircle(center, 2.8, stroke);
        }

      ChatyIconThemeId.organicMotion -> {
        // Organic anemone disk with 6 rounded petal lobes
        val petal = Path();
        for (i in 0 until 36) {
          val a = i * (PI / 18);
          val r = 7.5 + 1.5 * sin(a * 6);
          val x = 12 + r * cos(a);
          val y = 12 + r * sin(a);
          if (i == 0) petal.moveTo(x, y) else petal.lineTo(x, y);
        }
        petal.close();
        canvas.drawPath(petal, stroke);
        canvas.drawCircle(center, 2.5, fill);
        }

      ChatyIconThemeId.pixelPrecision -> {
        // 8-bit retro cog with square pixel teeth
        canvas.drawRect(Rect.fromLTWH(6, 6, 12, 12), fill);
        canvas.drawRect(Rect.fromLTWH(10, 3, 4, 3), fill);
        canvas.drawRect(Rect.fromLTWH(10, 18, 4, 3), fill);
        canvas.drawRect(Rect.fromLTWH(3, 10, 3, 4), fill);
        canvas.drawRect(Rect.fromLTWH(18, 10, 3, 4), fill);
        canvas.drawRect(Rect.fromLTWH(9, 9, 6, 6), Paint().apply { color = Colors.white; style = PaintingStyle.fill });
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD gear profile with pitch circle and addendum lines
        canvas.drawCircle(center, 8.5, stroke.apply { strokeWidth = 1.2 });
        canvas.drawCircle(center, 5.5, Paint().apply { color = stroke.color.withValues(alpha = 0.5); style = PaintingStyle.stroke; strokeWidth = 0.8 });
        canvas.drawCircle(center, 2.5, stroke.apply { strokeWidth = 1.0 });
        canvas.drawLine(Offset(2.0, 12.0), Offset(22.0, 12.0), Paint().apply { color = stroke.color.withValues(alpha = 0.4); strokeWidth = 0.8 });
        canvas.drawLine(Offset(12.0, 2.0), Offset(12.0, 22.0), Paint().apply { color = stroke.color.withValues(alpha = 0.4); strokeWidth = 0.8 });
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Machined gear disc with recessed central hub
        canvas.drawCircle(center, 8.8, fill);
        canvas.drawCircle(center, 4.8, secondary);
        canvas.drawCircle(center, 2.5, Paint().apply { color = Colors.white; style = PaintingStyle.fill });
        }

      ChatyIconThemeId.expressiveInk -> {
        // Expressive calligraphic sun wheel with brush-flick teeth
        canvas.drawCircle(center, 5.0, stroke.apply { strokeWidth = 2.4 });
        for (i in 0 until 8) {
          val a = i * (PI / 4);
          val p1 = Offset(12 + 6.0 * cos(a), 12 + 6.0 * sin(a));
          val p2 = Offset(12 + 9.5 * cos(a), 12 + 9.5 * sin(a));
          canvas.drawLine(p1, p2, stroke.apply { strokeWidth = 2.0 });
        }
        }

      ChatyIconThemeId.signalLanguage -> {
        // Telemetry radar array with 4 transmission dishes radiating outward
        canvas.drawCircle(center, 2.5, fill);
        canvas.drawArc(Rect.fromLTWH(4, 4, 16, 16), 0.2, 1.2, false, stroke.apply { strokeWidth = 1.4 });
        canvas.drawArc(Rect.fromLTWH(4, 4, 16, 16), 1.8, 1.2, false, stroke.apply { strokeWidth = 1.4 });
        canvas.drawArc(Rect.fromLTWH(4, 4, 16, 16), 3.4, 1.2, false, stroke.apply { strokeWidth = 1.4 });
        canvas.drawArc(Rect.fromLTWH(4, 4, 16, 16), 5.0, 1.2, false, stroke.apply { strokeWidth = 1.4 });
        }
        else -> {}
    }
  }

  // 14. MESSAGE STATUS (TICKS)
  fun _paintTicks(canvas: IconCanvas, stroke: Paint, fill: Paint, doubleTicks: Boolean) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Orbital curved checkmark with smooth terminal node
        val p = Path().apply { moveTo((if (doubleTicks) 4.0 else 6.0), 12.0); lineTo(9.5, 16.5); lineTo(18.5, 7.5) };
        canvas.drawPath(p, stroke);
        if (doubleTicks) {
          val p2 = Path().apply { moveTo(9.0, 12.0); lineTo(13.5, 16.5); lineTo(22.0, 8.0) };
          canvas.drawPath(p2, stroke);
        }
        canvas.drawCircle(Offset(18.5, 7.5), 1.2, fill);
        }

      ChatyIconThemeId.precisionContour -> {
        // Sharp angled hairline check with corner tick
        val p = Path().apply { moveTo((if (doubleTicks) 4.0 else 6.0), 12.0); lineTo(9.5, 17.0); lineTo(18.5, 7.0) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 1.2 });
        if (doubleTicks) {
          val p2 = Path().apply { moveTo(8.5, 12.0); lineTo(13.5, 17.0); lineTo(21.5, 8.0) };
          canvas.drawPath(p2, stroke.apply { strokeWidth = 1.2 });
        }
        }

      ChatyIconThemeId.monolithicCutout -> {
        // Bold solid heavy wedge checkmark
        val p = Path().apply { moveTo((if (doubleTicks) 4.0 else 6.0), 12.0); lineTo(9.5, 17.5); lineTo(18.5, 7.0); lineTo(17.0, 6.0); lineTo(9.5, 14.5); lineTo(5.5, 10.5); close() };
        canvas.drawPath(p, fill);
        if (doubleTicks) {
          val p2 = Path().apply { moveTo(8.5, 12.0); lineTo(13.5, 17.5); lineTo(22.0, 7.5); lineTo(20.5, 6.5); lineTo(13.5, 14.5); lineTo(10.0, 10.5); close() };
          canvas.drawPath(p2, fill);
        }
        }

      ChatyIconThemeId.origamiGeometry -> {
        // Sharp diagonal origami check creases
        val p = Path().apply { moveTo((if (doubleTicks) 4.0 else 6.0), 12.0); lineTo(9.0, 17.0); lineTo(18.0, 7.0) };
        canvas.drawPath(p, stroke);
        if (doubleTicks) {
          val p2 = Path().apply { moveTo(9.0, 12.0); lineTo(13.5, 17.0); lineTo(21.5, 8.0) };
          canvas.drawPath(p2, stroke);
        }
        }

      ChatyIconThemeId.organicMotion -> {
        // Smooth fluid droplet checkmark
        val p = Path().apply { moveTo((if (doubleTicks) 4.5 else 6.5), 12.0); quadraticBezierTo(8.0, 14.5, 9.5, 16.5); quadraticBezierTo(14.0, 12.0, 18.5, 7.5) };
        canvas.drawPath(p, stroke);
        if (doubleTicks) {
          val p2 = Path().apply { moveTo(9.0, 12.0); quadraticBezierTo(12.0, 14.5, 13.5, 16.5); quadraticBezierTo(17.5, 12.0, 21.5, 8.0) };
          canvas.drawPath(p2, stroke);
        }
        }

      ChatyIconThemeId.pixelPrecision -> {
        // Stepped 8-bit pixel checkmark (2x2 pixel blocks)
        canvas.drawRect(Rect.fromLTWH((if (doubleTicks) 4 else 6), 12, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH((if (doubleTicks) 6 else 8), 14, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH((if (doubleTicks) 8 else 10), 16, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH((if (doubleTicks) 10 else 12), 14, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH((if (doubleTicks) 12 else 14), 12, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH((if (doubleTicks) 14 else 16), 10, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH((if (doubleTicks) 16 else 18), 8, 2, 2), fill);
        if (doubleTicks) {
          canvas.drawRect(Rect.fromLTWH(12, 16, 2, 2), fill);
          canvas.drawRect(Rect.fromLTWH(14, 14, 2, 2), fill);
          canvas.drawRect(Rect.fromLTWH(16, 12, 2, 2), fill);
          canvas.drawRect(Rect.fromLTWH(18, 10, 2, 2), fill);
          canvas.drawRect(Rect.fromLTWH(20, 8, 2, 2), fill);
        }
        }

      ChatyIconThemeId.technicalBlueprint -> {
        // CAD drafting verification tick with blueprint end cross
        val p = Path().apply { moveTo((if (doubleTicks) 4.0 else 6.0), 12.0); lineTo(9.5, 17.0); lineTo(18.5, 7.0) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 1.3 });
        canvas.drawLine(Offset(17.0, 8.5), Offset(20.0, 5.5), stroke.apply { strokeWidth = 0.9 });
        if (doubleTicks) {
          val p2 = Path().apply { moveTo(8.5, 12.0); lineTo(13.5, 17.0); lineTo(22.0, 7.5) };
          canvas.drawPath(p2, stroke.apply { strokeWidth = 1.3 });
        }
        }

      ChatyIconThemeId.sculptedVolume -> {
        // Dimensional heavy chiseled checkmark
        val p = Path().apply { moveTo((if (doubleTicks) 4.0 else 6.0), 12.0); lineTo(9.5, 17.0); lineTo(18.5, 7.0) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 2.4 });
        if (doubleTicks) {
          val p2 = Path().apply { moveTo(8.5, 12.0); lineTo(13.5, 17.0); lineTo(22.0, 7.5) };
          canvas.drawPath(p2, Paint().apply { color = color.withValues(alpha = 0.4); style = PaintingStyle.stroke; strokeWidth = 2.4 });
        }
        }

      ChatyIconThemeId.expressiveInk -> {
        // Swift confident calligraphic brush-pen checkmark
        val p = Path().apply { moveTo((if (doubleTicks) 4.0 else 6.0), 11.0); lineTo(9.5, 17.0); quadraticBezierTo(14.0, 11.0, 19.0, 6.5) };
        canvas.drawPath(p, stroke.apply { strokeWidth = 2.6 });
        if (doubleTicks) {
          val p2 = Path().apply { moveTo(8.5, 11.0); lineTo(13.5, 17.0); quadraticBezierTo(17.5, 11.5, 22.0, 7.0) };
          canvas.drawPath(p2, stroke.apply { strokeWidth = 2.6 });
        }
        }

      ChatyIconThemeId.signalLanguage -> {
        // Telemetry verified receipt pulse waves
        canvas.drawArc(Rect.fromLTWH((if (doubleTicks) 4 else 6), 8, 12, 10), -0.5, 2.2, false, stroke.apply { strokeWidth = 1.8 });
        canvas.drawCircle(Offset((if (doubleTicks) 14 else 16), 10), 1.6, fill);
        if (doubleTicks) {
          canvas.drawArc(Rect.fromLTWH(8, 6, 12, 10), -0.5, 2.2, false, stroke.apply { strokeWidth = 1.4 });
        }
        }
        else -> {}
    }
  }

  // NAVIGATION & TAB ICONS
  fun _paintChats(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    when (theme) {
      ChatyIconThemeId.monolithicCutout -> {
        val b = Path().apply { moveTo(4.0, 5.0); lineTo(20.0, 5.0); lineTo(20.0, 16.0); lineTo(9.0, 16.0); lineTo(5.0, 20.0); close() };
        canvas.drawPath(b, fill);
        val slit = Paint().apply { color = Colors.white; strokeWidth = 1.4; style = PaintingStyle.stroke };
        canvas.drawLine(Offset(7.0, 9.0), Offset(17.0, 9.0), slit);
        canvas.drawLine(Offset(7.0, 12.5), Offset(14.0, 12.5), slit);
        }
      ChatyIconThemeId.pixelPrecision -> {
        canvas.drawRect(Rect.fromLTWH(4, 5, 16, 11), fill);
        canvas.drawRect(Rect.fromLTWH(6, 16, 4, 3), fill);
        canvas.drawRect(Rect.fromLTWH(4, 19, 3, 2), fill);
        canvas.drawRect(Rect.fromLTWH(7, 8, 10, 2), Paint().apply { color = Colors.white; style = PaintingStyle.fill });
        canvas.drawRect(Rect.fromLTWH(7, 12, 6, 2), Paint().apply { color = Colors.white; style = PaintingStyle.fill });
        }
      else -> {
        val p = Path().apply { addRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.5, 5.0, 17.0, 12.0), Radius.circular(3.5))) };
        p.moveTo(7.5, 17.0);
        p.lineTo(6.0, 20.0);
        p.lineTo(11.5, 17.0);
        canvas.drawPath(p, stroke);
        canvas.drawLine(Offset(7.5, 9.5), Offset(16.5, 9.5), stroke.apply { strokeWidth = stroke.strokeWidth * 0.85 });
        canvas.drawLine(Offset(7.5, 13.0), Offset(13.5, 13.0), stroke);
        }
    }
  }

  fun _paintUpdates(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    val rect = Rect.fromLTWH(3.8, 3.8, 16.4, 16.4);
    canvas.drawArc(rect, -0.6, 2.2, false, stroke);
    canvas.drawArc(rect, 1.9, 1.8, false, stroke);
    canvas.drawArc(rect, 4.0, 1.4, false, stroke);
    canvas.drawCircle(Offset(12, 12), 2.2, fill);
  }

  fun _paintProfile(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    if (theme == ChatyIconThemeId.monolithicCutout) {
      canvas.drawCircle(Offset(12, 8.0), 4.0, fill);
      val body = Path().apply { moveTo(4.5, 19.5); quadraticBezierTo(4.5, 14.0, 12.0, 14.0); quadraticBezierTo(19.5, 14.0, 19.5, 19.5); close() };
      canvas.drawPath(body, fill);
      return;
    }
    canvas.drawCircle(Offset(12, 8.2), 3.8, stroke);
    val p = Path();
    p.moveTo(4.8, 19.5);
    p.quadraticBezierTo(4.8, 14.5, 12.0, 14.5);
    p.quadraticBezierTo(19.2, 14.5, 19.2, 19.5);
    canvas.drawPath(p, stroke);
  }

  fun _paintTasks(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(4.5, 3.8, 15.0, 16.4), Radius.circular(2.5)), stroke);
    canvas.drawLine(Offset(8.0, 8.5), Offset(16.0, 8.5), stroke);
    canvas.drawLine(Offset(8.0, 12.0), Offset(16.0, 12.0), stroke);
    canvas.drawLine(Offset(8.0, 15.5), Offset(13.0, 15.5), stroke);
  }

  fun _paintFlash(canvas: IconCanvas, stroke: Paint, fill: Paint, on: Boolean) {
    val p = Path().apply { moveTo(13.0, 3.5); lineTo(6.5, 12.5); lineTo(12.0, 12.5); lineTo(11.0, 20.5); lineTo(17.5, 11.0); lineTo(12.5, 11.0); close() };
    if (on) {
      canvas.drawPath(p, fill);
    } else {
      canvas.drawPath(p, stroke);
      canvas.drawLine(Offset(4.0, 4.0), Offset(20.0, 20.0), stroke.apply { color = Colors.redAccent; strokeWidth = 2.0 });
    }
  }

  fun _paintGallery(canvas: IconCanvas, stroke: Paint, fill: Paint, secondary: Paint) {
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.5, 4.5, 17.0, 15.0), Radius.circular(3.0)), stroke);
    canvas.drawCircle(Offset(8.5, 9.0), 1.8, fill);
    val mountains = Path().apply { moveTo(4.5, 17.5); lineTo(9.5, 12.5); lineTo(13.0, 15.5); lineTo(16.5, 11.5); lineTo(19.5, 15.5); lineTo(19.5, 17.5); close() };
    canvas.drawPath(mountains, secondary);
    canvas.drawPath(mountains, stroke);
  }

  fun _paintCallEnd(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Honor the caller's color like every other glyph: the end-call button
    // paints this white-on-error-red, and a hardcoded redAccent glyph was
    // invisible on that red fill.
    val endPaint = Paint().apply { color = fill.color; style = PaintingStyle.fill; isAntiAlias = true };
    canvas.save();
    canvas.translate(12, 12);
    canvas.rotate(PI * 0.75);
    val p = Path().apply { moveTo(-5.5, -7.5); lineTo(-2.5, -7.5); lineTo(-1.2, -4.0); lineTo(-3.2, -2.0); quadraticBezierTo(-1.8, 1.8, 2.0, 3.2); lineTo(4.0, 1.2); lineTo(7.5, 2.5); lineTo(7.5, 5.5); quadraticBezierTo(7.5, 7.5, 5.2, 7.5); cubicTo(-1.5, 7.5, -7.5, 1.5, -7.5, -5.2); close() };
    canvas.drawPath(p, endPaint);
    canvas.restore();
  }

  fun _paintScreenShare(canvas: IconCanvas, stroke: Paint, fill: Paint, stop: Boolean) {
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.5, 4.5, 17.0, 12.0), Radius.circular(2.5)), stroke);
    canvas.drawLine(Offset(8.0, 19.5), Offset(16.0, 19.5), stroke);
    canvas.drawLine(Offset(12.0, 16.5), Offset(12.0, 19.5), stroke);
    val arrow = Path().apply { moveTo(12.0, 8.0); lineTo(9.0, 11.0); lineTo(15.0, 11.0); close() };
    canvas.drawPath(arrow, fill);
    if (stop) {
      canvas.drawLine(Offset(4.0, 4.0), Offset(20.0, 20.0), stroke.apply { color = Colors.redAccent; strokeWidth = 2.0 });
    }
  }

  fun _paintSpeaker(canvas: IconCanvas, stroke: Paint, fill: Paint, isSpeaker: Boolean = true, isMute: Boolean = false) {
    val cone = Path().apply { moveTo(4.0, 9.0); lineTo(8.0, 9.0); lineTo(13.0, 5.0); lineTo(13.0, 19.0); lineTo(8.0, 15.0); lineTo(4.0, 15.0); close() };
    canvas.drawPath(cone, fill);
    if (!isMute) {
      canvas.drawArc(Rect.fromLTWH(13, 8, 5, 8), -PI / 2, PI, false, stroke);
      if (isSpeaker) {
        canvas.drawArc(Rect.fromLTWH(15, 5, 6, 14), -PI / 2, PI, false, stroke);
      }
    } else {
      canvas.drawLine(Offset(16.0, 9.5), Offset(21.0, 14.5), stroke.apply { color = Colors.redAccent });
      canvas.drawLine(Offset(21.0, 9.5), Offset(16.0, 14.5), stroke.apply { color = Colors.redAccent });
    }
  }

  fun _paintAttachmentPlus(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    _paintAttachment(canvas, stroke, fill);
    canvas.drawCircle(Offset(18.5, 18.5), 4.2, fill);
    val p = Paint().apply { color = Colors.white; strokeWidth = 1.3; style = PaintingStyle.stroke };
    canvas.drawLine(Offset(16.5, 18.5), Offset(20.5, 18.5), p);
    canvas.drawLine(Offset(18.5, 16.5), Offset(18.5, 20.5), p);
  }

  fun _paintPin(canvas: IconCanvas, stroke: Paint, fill: Paint, unpin: Boolean) {
    canvas.save();
    canvas.translate(12, 12);
    canvas.rotate(PI / 4);
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(-4.0, -9.0, 8.0, 4.0), Radius.circular(1.0)), fill);
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(-2.5, -5.0, 5.0, 7.0), Radius.circular(1.0)), fill);
    canvas.drawLine(Offset(0, 2.0), Offset(0, 8.0), stroke.apply { strokeWidth = 2.0 });
    canvas.restore();
    if (unpin) {
      canvas.drawLine(Offset(4.0, 4.0), Offset(20.0, 20.0), stroke.apply { color = Colors.redAccent; strokeWidth = 2.0 });
    }
  }

  fun _paintArchive(canvas: IconCanvas, stroke: Paint, secondary: Paint) {
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.5, 4.5, 17.0, 4.0), Radius.circular(1.5)), stroke);
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(5.0, 8.5, 14.0, 11.5), Radius.circular(1.5)), stroke);
    val arrow = Path().apply { moveTo(12.0, 16.0); lineTo(9.5, 13.0); lineTo(14.5, 13.0); close() };
    canvas.drawPath(arrow, stroke);
  }

  fun _paintLock(canvas: IconCanvas, stroke: Paint, fill: Paint, locked: Boolean) {
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(4.5, 9.5, 15.0, 11.0), Radius.circular(3.0)), stroke);
    val shackle = Path();
    if (locked) {
      shackle.moveTo(7.5, 10.0);
      shackle.lineTo(7.5, 6.5);
      shackle.arcToPoint(Offset(16.5, 6.5), radius = Radius.circular(4.5));
      shackle.lineTo(16.5, 10.0);
    } else {
      shackle.moveTo(7.5, 10.0);
      shackle.lineTo(7.5, 6.5);
      shackle.arcToPoint(Offset(16.5, 6.5), radius = Radius.circular(4.5));
      shackle.lineTo(16.5, 4.0);
    }
    canvas.drawPath(shackle, stroke);
    canvas.drawCircle(Offset(12.0, 14.5), 1.8, fill);
    canvas.drawLine(Offset(12.0, 14.5), Offset(12.0, 17.5), stroke);
  }

  fun _paintDelete(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    canvas.drawLine(Offset(4.0, 6.0), Offset(20.0, 6.0), stroke);
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(9.0, 3.5, 6.0, 2.5), Radius.circular(1.0)), stroke);
    val bin = Path().apply { moveTo(6.0, 6.0); lineTo(7.2, 19.5); lineTo(16.8, 19.5); lineTo(18.0, 6.0) };
    canvas.drawPath(bin, stroke);
    canvas.drawLine(Offset(10.0, 9.5), Offset(10.0, 16.0), stroke);
    canvas.drawLine(Offset(14.0, 9.5), Offset(14.0, 16.0), stroke);
  }

  fun _paintClose(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    canvas.drawLine(Offset(5.5, 5.5), Offset(18.5, 18.5), stroke);
    canvas.drawLine(Offset(18.5, 5.5), Offset(5.5, 18.5), stroke);
  }

  fun _paintCheck(canvas: IconCanvas, stroke: Paint) {
    val p = Path().apply { moveTo(5.0, 12.0); lineTo(9.5, 16.5); lineTo(19.0, 7.0) };
    canvas.drawPath(p, stroke);
  }

  fun _paintTune(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    canvas.drawLine(Offset(4.0, 7.0), Offset(20.0, 7.0), stroke);
    canvas.drawLine(Offset(4.0, 12.0), Offset(20.0, 12.0), stroke);
    canvas.drawLine(Offset(4.0, 17.0), Offset(20.0, 17.0), stroke);
    canvas.drawCircle(Offset(9.0, 7.0), 2.4, fill);
    canvas.drawCircle(Offset(15.0, 12.0), 2.4, fill);
    canvas.drawCircle(Offset(11.0, 17.0), 2.4, fill);
  }

  fun _paintChevronRight(canvas: IconCanvas, stroke: Paint) {
    val p = Path().apply { moveTo(9.5, 6.0); lineTo(15.5, 12.0); lineTo(9.5, 18.0) };
    canvas.drawPath(p, stroke);
  }

  fun _paintReply(canvas: IconCanvas, stroke: Paint) {
    val p = Path().apply { moveTo(9.5, 7.5); lineTo(4.0, 12.0); lineTo(9.5, 16.5) };
    canvas.drawPath(p, stroke);
    val arc = Path().apply { moveTo(5.0, 12.0); quadraticBezierTo(14.0, 11.5, 19.0, 17.5) };
    canvas.drawPath(arc, stroke);
  }

  fun _paintForward(canvas: IconCanvas, stroke: Paint) {
    val p = Path().apply { moveTo(14.5, 7.5); lineTo(20.0, 12.0); lineTo(14.5, 16.5) };
    canvas.drawPath(p, stroke);
    val arc = Path().apply { moveTo(19.0, 12.0); quadraticBezierTo(10.0, 11.5, 5.0, 17.5) };
    canvas.drawPath(arc, stroke);
  }

  fun _paintEdit(canvas: IconCanvas, stroke: Paint) {
    val p = Path().apply { moveTo(15.5, 4.5); lineTo(19.5, 8.5); lineTo(9.0, 19.0); lineTo(5.0, 19.5); lineTo(5.5, 15.5); close() };
    canvas.drawPath(p, stroke);
    canvas.drawLine(Offset(13.5, 6.5), Offset(17.5, 10.5), stroke);
  }

  fun _paintCopy(canvas: IconCanvas, stroke: Paint) {
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(8.0, 4.0, 12.0, 13.0), Radius.circular(2.0)), stroke);
    val back = Path().apply { moveTo(5.0, 7.5); lineTo(5.0, 19.5); lineTo(16.5, 19.5) };
    canvas.drawPath(back, stroke);
  }

  fun _paintShare(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    val n1 = Offset(17.5, 6.5);
    val n2 = Offset(6.5, 12.0);
    val n3 = Offset(17.5, 17.5);
    canvas.drawLine(n2, n1, stroke);
    canvas.drawLine(n2, n3, stroke);
    canvas.drawCircle(n1, 2.5, fill);
    canvas.drawCircle(n2, 2.5, fill);
    canvas.drawCircle(n3, 2.5, fill);
  }

  fun _paintSignal(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    for (i in 0 until 4) {
      val h = 4.0 + i * 3.8;
      canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(4.5 + i * 4.2, 19.0 - h, 2.8, h), Radius.circular(1.0)), fill);
    }
  }

  fun _paintClock(canvas: IconCanvas, stroke: Paint) {
    canvas.drawCircle(Offset(12.0, 12.0), 8.5, stroke);
    canvas.drawLine(Offset(12.0, 7.5), Offset(12.0, 12.0), stroke);
    canvas.drawLine(Offset(12.0, 12.0), Offset(15.5, 12.0), stroke);
  }

  fun _paintPictureInPicture(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // PiP: large rect with smaller rect in corner
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(3.0, 3.0, 18.0, 18.0), Radius.circular(2.0)), stroke);
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(14.0, 3.5, 5.5, 5.5), Radius.circular(1.0)), fill);
  }

  fun _paintAutoAwesome(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Sparkle/magic wand icon
    canvas.drawLine(Offset(12.0, 3.0), Offset(12.0, 6.0), stroke);
    canvas.drawLine(Offset(12.0, 18.0), Offset(12.0, 21.0), stroke);
    canvas.drawLine(Offset(3.0, 12.0), Offset(6.0, 12.0), stroke);
    canvas.drawLine(Offset(18.0, 12.0), Offset(21.0, 12.0), stroke);
    canvas.drawCircle(Offset(8.0, 8.0), 1.8, fill);
    canvas.drawCircle(Offset(16.0, 8.0), 1.8, fill);
    canvas.drawCircle(Offset(8.0, 16.0), 1.8, fill);
    canvas.drawCircle(Offset(16.0, 16.0), 1.8, fill);
  }

  fun _paintFullscreen(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Expand arrows - corners pointing out
    val corners = listOf(
      Offset(5.0, 5.0), Offset(19.0, 5.0),
      Offset(5.0, 19.0), Offset(19.0, 19.0),
    )
    for (c in corners) {
      val dx =(if (c.dx < 12) 4.0 else -4.0);
      val dy =(if (c.dy < 12) 4.0 else -4.0);
      canvas.drawPath(Path().apply { moveTo(c.dx, c.dy); lineTo(c.dx + dx, c.dy); lineTo(c.dx, c.dy + dy) }, stroke);
    }
  }

  fun _paintFullscreenExit(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Contract arrows - corners pointing in
    val corners = listOf(
      Offset(7.0, 7.0), Offset(17.0, 7.0),
      Offset(7.0, 17.0), Offset(17.0, 17.0),
    )
    for (c in corners) {
      val dx =(if (c.dx < 12) -4.0 else 4.0);
      val dy =(if (c.dy < 12) -4.0 else 4.0);
      canvas.drawPath(Path().apply { moveTo(c.dx, c.dy); lineTo(c.dx + dx, c.dy); lineTo(c.dx, c.dy + dy) }, stroke);
    }
  }

  fun _paintOpenInNew(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Box with arrow pointing out
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(5.0, 5.0, 10.0, 10.0), Radius.circular(2.0)), stroke);
    canvas.drawLine(Offset(15.0, 12.0), Offset(19.0, 12.0), stroke);
    canvas.drawLine(Offset(15.0, 12.0), Offset(17.0, 10.0), stroke);
    canvas.drawLine(Offset(15.0, 12.0), Offset(17.0, 14.0), stroke);
    canvas.drawLine(Offset(12.0, 15.0), Offset(12.0, 19.0), stroke);
    canvas.drawLine(Offset(10.0, 17.0), Offset(12.0, 19.0), stroke);
    canvas.drawLine(Offset(14.0, 17.0), Offset(12.0, 19.0), stroke);
  }

  fun _paintClear(canvas: IconCanvas, stroke: Paint) {
    // X icon
    canvas.drawLine(Offset(6.0, 6.0), Offset(18.0, 18.0), stroke);
    canvas.drawLine(Offset(18.0, 6.0), Offset(6.0, 18.0), stroke);
  }

  fun _paintAddTask(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Plus with clipboard/list
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(4.0, 4.0, 16.0, 16.0), Radius.circular(2.0)), stroke);
    canvas.drawLine(Offset(8.0, 12.0), Offset(16.0, 12.0), stroke);
    canvas.drawLine(Offset(12.0, 8.0), Offset(12.0, 16.0), stroke);
  }

  fun _paintEditOutline(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Pencil outline
    val p = Path().apply { moveTo(16.5, 4.5); lineTo(19.5, 7.5); lineTo(8.5, 18.5); lineTo(4.5, 19.0); lineTo(5.0, 15.0); close() };
    canvas.drawPath(p, stroke);
  }

  fun _paintVideoCam(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Video camera icon
    val p = Path().apply { moveTo(5.0, 5.0); lineTo(18.0, 9.0); lineTo(5.0, 13.0); close() };
    canvas.drawPath(p, fill);
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(5.0, 5.0, 11.0, 8.0), Radius.circular(1.0)), stroke);
  }

  fun _paintVoiceCall(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Phone handset icon
    val p = Path().apply { moveTo(6.0, 5.0); quadraticBezierTo(14.0, 5.0, 14.0, 12.0); lineTo(17.0, 12.0); quadraticBezierTo(17.0, 5.0, 22.0, 5.0); lineTo(22.0, 19.0); quadraticBezierTo(17.0, 19.0, 17.0, 12.0); lineTo(14.0, 12.0); quadraticBezierTo(6.0, 12.0, 6.0, 5.0); close() };
    canvas.drawPath(p, stroke);
  }

  fun _paintLogout(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Logout: arrow pointing right from a door-like shape
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(5.0, 5.0, 12.0, 14.0), Radius.circular(2.0)), stroke);
    canvas.drawLine(Offset(10.0, 12.0), Offset(18.0, 12.0), stroke);
    canvas.drawLine(Offset(15.0, 9.0), Offset(18.0, 12.0), stroke);
    canvas.drawLine(Offset(15.0, 15.0), Offset(18.0, 12.0), stroke);
  }

  fun _paintDeleteOutline(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Delete outline: trash can outline
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(5.0, 4.0, 14.0, 4.0), Radius.circular(1.0)), stroke);
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(6.0, 8.0, 12.0, 12.0), Radius.circular(1.0)), stroke);
    canvas.drawLine(Offset(8.0, 12.0), Offset(8.0, 17.0), stroke);
    canvas.drawLine(Offset(12.0, 12.0), Offset(12.0, 17.0), stroke);
    canvas.drawLine(Offset(16.0, 12.0), Offset(16.0, 17.0), stroke);
  }

  fun _paintSchedule(canvas: IconCanvas, stroke: Paint) {
    // Clock outline
    canvas.drawCircle(Offset(12.0, 12.0), 9.0, stroke);
    canvas.drawLine(Offset(12.0, 6.0), Offset(12.0, 12.0), stroke);
    canvas.drawLine(Offset(12.0, 12.0), Offset(15.0, 14.0), stroke);
  }

  fun _paintScheduleSend(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Clock with arrow
    canvas.drawCircle(Offset(12.0, 12.0), 8.0, stroke);
    canvas.drawLine(Offset(12.0, 7.0), Offset(12.0, 11.0), stroke);
    canvas.drawLine(Offset(12.0, 11.0), Offset(14.0, 13.0), stroke);
    canvas.drawLine(Offset(12.0, 16.0), Offset(16.0, 12.0), stroke);
    canvas.drawLine(Offset(14.0, 10.0), Offset(16.0, 12.0), stroke);
    canvas.drawLine(Offset(14.0, 14.0), Offset(16.0, 12.0), stroke);
  }

  fun _paintSync(canvas: IconCanvas, stroke: Paint) {
    // Sync/circular arrows
    canvas.drawArc(Rect.fromCenter(center = Offset(12.0, 12.0), width = 16.0, height = 16.0), 0.5, 5.0, false, stroke);
    canvas.drawLine(Offset(20.0, 12.0), Offset(17.0, 9.0), stroke);
    canvas.drawLine(Offset(20.0, 12.0), Offset(17.0, 15.0), stroke);
    canvas.drawArc(Rect.fromCenter(center = Offset(12.0, 12.0), width = 16.0, height = 16.0), 3.5, 5.0, false, stroke);
    canvas.drawLine(Offset(4.0, 12.0), Offset(7.0, 9.0), stroke);
    canvas.drawLine(Offset(4.0, 12.0), Offset(7.0, 15.0), stroke);
  }

  fun _paintCheckSingle(canvas: IconCanvas, stroke: Paint) {
    val p = Path().apply { moveTo(6.0, 12.0); lineTo(10.0, 16.0); lineTo(18.0, 8.0) };
    canvas.drawPath(p, stroke);
  }

  fun _paintCheckDouble(canvas: IconCanvas, stroke: Paint) {
    // Two overlapping checks
    val p1 = Path().apply { moveTo(4.0, 12.0); lineTo(8.0, 16.0); lineTo(16.0, 8.0) };
    val p2 = Path().apply { moveTo(8.0, 12.0); lineTo(12.0, 16.0); lineTo(20.0, 8.0) };
    canvas.drawPath(p1, stroke);
    canvas.drawPath(p2, stroke);
  }

  fun _paintErrorOutline(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    // Error: exclamation in circle
    canvas.drawCircle(Offset(12.0, 12.0), 9.0, stroke);
    canvas.drawLine(Offset(12.0, 6.0), Offset(12.0, 15.0), stroke);
    canvas.drawCircle(Offset(12.0, 17.0), 1.5, fill);
  }

  fun _paintPause(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Orbital dual bars with smooth rounded caps
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(5.5, 5.5, 4.5, 13.0), Radius.circular(2.25)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(14.0, 5.5, 4.5, 13.0), Radius.circular(2.25)), fill);
        }
      ChatyIconThemeId.precisionContour -> {
        // Hairline dual bars with sharp corners
        canvas.drawRect(Rect.fromLTWH(6.0, 5.0, 4.0, 14.0), stroke.apply { strokeWidth = 1.1 });
        canvas.drawRect(Rect.fromLTWH(14.0, 5.0, 4.0, 14.0), stroke.apply { strokeWidth = 1.1 });
        }
      ChatyIconThemeId.monolithicCutout -> {
        // Solid monolith blocks with stenciled gap
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(5.5, 5.0, 4.5, 14.0), Radius.circular(1.0)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(14.0, 5.0, 4.5, 14.0), Radius.circular(1.0)), fill);
        }
      ChatyIconThemeId.origamiGeometry -> {
        // Faceted angular pause bars
        val p1 = Path().apply { moveTo(5.5, 5.0); lineTo(9.5, 5.0); lineTo(10.5, 6.0); lineTo(6.5, 18.0); lineTo(5.5, 19.0); close() };
        val p2 = Path().apply { moveTo(14.5, 5.0); lineTo(18.5, 5.0); lineTo(17.5, 6.0); lineTo(13.5, 18.0); lineTo(14.5, 19.0); close() };
        canvas.drawPath(p1, fill);
        canvas.drawPath(p2, fill);
        }
      ChatyIconThemeId.organicMotion -> {
        // Fluid organic pause bars with natural curves
        val p1o = Path().apply { moveTo(5.5, 5.5); quadraticBezierTo(6.0, 12.0, 5.5, 18.5); quadraticBezierTo(6.0, 19.5, 9.5, 18.5); quadraticBezierTo(10.0, 12.0, 9.5, 5.5); close() };
        val p2o = Path().apply { moveTo(14.5, 5.5); quadraticBezierTo(15.0, 12.0, 14.5, 18.5); quadraticBezierTo(15.0, 19.5, 18.5, 18.5); quadraticBezierTo(18.0, 12.0, 18.5, 5.5); close() };
        canvas.drawPath(p1o, fill);
        canvas.drawPath(p2o, fill);
        }
      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit stepped pixel bars
        canvas.drawRect(Rect.fromLTWH(6, 5, 4, 14), fill);
        canvas.drawRect(Rect.fromLTWH(14, 5, 4, 14), fill);
        }
      ChatyIconThemeId.technicalBlueprint -> {
        // CAD dimension bars with datum lines
        canvas.drawRect(Rect.fromLTWH(5.5, 5.0, 4.5, 14.0), stroke.apply { strokeWidth = 1.2 });
        canvas.drawRect(Rect.fromLTWH(14.0, 5.0, 4.5, 14.0), stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(3.5, 12.0), Offset(20.5, 12.0), Paint().apply { color = stroke.color.withValues(alpha = 0.4); strokeWidth = 0.8 });
        }
      ChatyIconThemeId.sculptedVolume -> {
        // Embossed dimensional bars with light/shadow planes
        val left = Path().apply { moveTo(5.5, 5.0); lineTo(10.0, 5.0); lineTo(10.0, 19.0); lineTo(5.5, 19.0); close() };
        val right = Path().apply { moveTo(14.0, 5.0); lineTo(18.5, 5.0); lineTo(18.5, 19.0); lineTo(14.0, 19.0); close() };
        canvas.drawPath(left, fill);
        canvas.drawPath(right, Paint().apply { color = color.withValues(alpha = 0.4); style = PaintingStyle.fill });
        }
      ChatyIconThemeId.expressiveInk -> {
        // Calligraphic brush-stroke pause bars
        canvas.drawRect(Rect.fromLTWH(5.5, 5.0, 4.5, 14.0), stroke.apply { strokeWidth = 2.4 });
        canvas.drawRect(Rect.fromLTWH(14.0, 5.0, 4.5, 14.0), stroke.apply { strokeWidth = 2.4 });
        }
      ChatyIconThemeId.signalLanguage -> {
        // Telemetry pause signal with pulse indicators
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(5.5, 6.0, 4.5, 12.0), Radius.circular(2.0)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(14.0, 6.0, 4.5, 12.0), Radius.circular(2.0)), fill);
        canvas.drawCircle(Offset(12.0, 12.0), 1.5, fill);
        }
        else -> {}
    }
  }

  fun _paintPlayArrow(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Orbital play triangle with smooth vertices
        val p = Path().apply { moveTo(6.5, 5.5); lineTo(19.5, 12.0); lineTo(6.5, 18.5); close() };
        canvas.drawPath(p, fill);
        }
      ChatyIconThemeId.precisionContour -> {
        // Hairline play triangle
        val p2 = Path().apply { moveTo(7.0, 5.0); lineTo(19.0, 12.0); lineTo(7.0, 19.0); close() };
        canvas.drawPath(p2, stroke.apply { strokeWidth = 1.2 });
        }
      ChatyIconThemeId.monolithicCutout -> {
        // Solid play triangle
        val p3 = Path().apply { moveTo(6.0, 5.0); lineTo(20.0, 12.0); lineTo(6.0, 19.0); close() };
        canvas.drawPath(p3, fill);
        }
      ChatyIconThemeId.origamiGeometry -> {
        // Faceted origami play triangle
        val p4 = Path().apply { moveTo(6.5, 5.5); lineTo(19.5, 12.0); lineTo(6.5, 18.5); close() };
        canvas.drawPath(p4, stroke);
        }
      ChatyIconThemeId.organicMotion -> {
        // Fluid play triangle with curved edges
        val p5 = Path().apply { moveTo(7.0, 5.5); quadraticBezierTo(15.0, 10.0, 19.0, 12.0); quadraticBezierTo(15.0, 14.0, 7.0, 18.5); close() };
        canvas.drawPath(p5, fill);
        }
      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit stepped pixel play triangle
        canvas.drawRect(Rect.fromLTWH(7, 5, 1, 14), fill);
        canvas.drawRect(Rect.fromLTWH(8, 6, 2, 12), fill);
        canvas.drawRect(Rect.fromLTWH(10, 7, 3, 10), fill);
        canvas.drawRect(Rect.fromLTWH(13, 8, 3, 8), fill);
        canvas.drawRect(Rect.fromLTWH(16, 9, 2, 6), fill);
        canvas.drawRect(Rect.fromLTWH(18, 10, 1, 4), fill);
        }
      ChatyIconThemeId.technicalBlueprint -> {
        // CAD play triangle with construction lines
        val p6 = Path().apply { moveTo(6.5, 5.5); lineTo(19.5, 12.0); lineTo(6.5, 18.5); close() };
        canvas.drawPath(p6, stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(12.0, 3.0), Offset(12.0, 21.0), Paint().apply { color = stroke.color.withValues(alpha = 0.4); strokeWidth = 0.8 });
        }
      ChatyIconThemeId.sculptedVolume -> {
        // Dimensional play triangle with dual-plane depth
        val leftPlay = Path().apply { moveTo(6.5, 5.5); lineTo(12.5, 12.0); lineTo(6.5, 18.5); close() };
        val rightPlay = Path().apply { moveTo(12.5, 12.0); lineTo(19.5, 12.0); lineTo(12.5, 12.0); close() }; // degenerate for shadow
        canvas.drawPath(leftPlay, fill);
        canvas.drawPath(rightPlay, Paint().apply { color = color.withValues(alpha = 0.35); style = PaintingStyle.fill });
        }
      ChatyIconThemeId.expressiveInk -> {
        // Expressive brush-stroke play triangle
        val p7 = Path().apply { moveTo(6.5, 5.5); quadraticBezierTo(11.0, 8.0, 19.5, 12.0); quadraticBezierTo(11.0, 16.0, 6.5, 18.5); close() };
        canvas.drawPath(p7, stroke.apply { strokeWidth = 2.6 });
        }
      ChatyIconThemeId.signalLanguage -> {
        // Telemetry play signal with broadcast pulse
        val p8 = Path().apply { moveTo(7.0, 5.0); lineTo(19.0, 12.0); lineTo(7.0, 19.0); close() };
        canvas.drawPath(p8, fill);
        canvas.drawArc(Rect.fromLTWH(19, 8, 4, 8), -PI / 2, PI, false, stroke.apply { strokeWidth = 1.2 });
        }
        else -> {}
    }
  }

  fun _paintMusicNote(canvas: IconCanvas, stroke: Paint, fill: Paint) {
    when (theme) {
      ChatyIconThemeId.chatySigil -> {
        // Orbital music note with smooth curves
        val path = Path().apply { moveTo(6.0, 19.0); lineTo(6.0, 7.0); quadraticBezierTo(6.0, 4.5, 8.5, 4.5); quadraticBezierTo(11.0, 4.5, 11.0, 7.0); lineTo(11.0, 10.5); quadraticBezierTo(11.0, 13.0, 13.5, 13.0); quadraticBezierTo(16.0, 13.0, 16.0, 10.5); lineTo(16.0, 7.0); quadraticBezierTo(16.0, 4.5, 18.5, 4.5); quadraticBezierTo(21.0, 4.5, 21.0, 7.0); lineTo(21.0, 19.0); close() };
        canvas.drawPath(path, fill);
        }
      ChatyIconThemeId.precisionContour -> {
        // Hairline music note
        val path2 = Path().apply { moveTo(6.0, 19.0); lineTo(6.0, 7.0); quadraticBezierTo(6.0, 4.5, 8.5, 4.5); quadraticBezierTo(11.0, 4.5, 11.0, 7.0); lineTo(11.0, 10.5); quadraticBezierTo(11.0, 13.0, 13.5, 13.0); quadraticBezierTo(16.0, 13.0, 16.0, 10.5); lineTo(16.0, 7.0); quadraticBezierTo(16.0, 4.5, 18.5, 4.5); quadraticBezierTo(21.0, 4.5, 21.0, 7.0); lineTo(21.0, 19.0); close() };
        canvas.drawPath(path2, stroke.apply { strokeWidth = 1.2 });
        }
      ChatyIconThemeId.monolithicCutout -> {
        // Solid music note
        val path3 = Path().apply { moveTo(6.0, 19.0); lineTo(6.0, 7.0); quadraticBezierTo(6.0, 4.5, 8.5, 4.5); quadraticBezierTo(11.0, 4.5, 11.0, 7.0); lineTo(11.0, 10.5); quadraticBezierTo(11.0, 13.0, 13.5, 13.0); quadraticBezierTo(16.0, 13.0, 16.0, 10.5); lineTo(16.0, 7.0); quadraticBezierTo(16.0, 4.5, 18.5, 4.5); quadraticBezierTo(21.0, 4.5, 21.0, 7.0); lineTo(21.0, 19.0); close() };
        canvas.drawPath(path3, fill);
        }
      ChatyIconThemeId.origamiGeometry -> {
        // Faceted origami music note
        val path4 = Path().apply { moveTo(6.0, 19.0); lineTo(6.0, 7.0); quadraticBezierTo(6.0, 4.5, 8.5, 4.5); quadraticBezierTo(11.0, 4.5, 11.0, 7.0); lineTo(11.0, 10.5); quadraticBezierTo(11.0, 13.0, 13.5, 13.0); quadraticBezierTo(16.0, 13.0, 16.0, 10.5); lineTo(16.0, 7.0); quadraticBezierTo(16.0, 4.5, 18.5, 4.5); quadraticBezierTo(21.0, 4.5, 21.0, 7.0); lineTo(21.0, 19.0); close() };
        canvas.drawPath(path4, stroke);
        }
      ChatyIconThemeId.organicMotion -> {
        // Fluid music note with curved edges
        val path5 = Path().apply { moveTo(6.0, 19.0); lineTo(6.0, 7.5); quadraticBezierTo(6.0, 5.0, 8.5, 5.0); quadraticBezierTo(11.0, 5.0, 11.0, 7.5); lineTo(11.0, 10.5); quadraticBezierTo(11.0, 13.5, 13.5, 13.5); quadraticBezierTo(16.0, 13.5, 16.0, 10.5); lineTo(16.0, 7.5); quadraticBezierTo(16.0, 5.0, 18.5, 5.0); quadraticBezierTo(21.0, 5.0, 21.0, 7.5); lineTo(21.0, 19.0); close() };
        canvas.drawPath(path5, fill);
        }
      ChatyIconThemeId.pixelPrecision -> {
        // Authentic 8-bit stepped pixel music note
        canvas.drawRect(Rect.fromLTWH(5, 7, 2, 12), fill);
        canvas.drawRect(Rect.fromLTWH(7, 4, 4, 4), fill);
        canvas.drawRect(Rect.fromLTWH(11, 7, 2, 4), fill);
        canvas.drawRect(Rect.fromLTWH(13, 7, 4, 4), fill);
        canvas.drawRect(Rect.fromLTWH(17, 4, 4, 4), fill);
        canvas.drawRect(Rect.fromLTWH(19, 7, 2, 12), fill);
        }
      ChatyIconThemeId.technicalBlueprint -> {
        // CAD music note with construction lines
        val path6 = Path().apply { moveTo(6.0, 19.0); lineTo(6.0, 7.0); quadraticBezierTo(6.0, 4.5, 8.5, 4.5); quadraticBezierTo(11.0, 4.5, 11.0, 7.0); lineTo(11.0, 10.5); quadraticBezierTo(11.0, 13.0, 13.5, 13.0); quadraticBezierTo(16.0, 13.0, 16.0, 10.5); lineTo(16.0, 7.0); quadraticBezierTo(16.0, 4.5, 18.5, 4.5); quadraticBezierTo(21.0, 4.5, 21.0, 7.0); lineTo(21.0, 19.0); close() };
        canvas.drawPath(path6, stroke.apply { strokeWidth = 1.2 });
        canvas.drawLine(Offset(12.0, 3.0), Offset(12.0, 21.0), Paint().apply { color = stroke.color.withValues(alpha = 0.4); strokeWidth = 0.8 });
        }
      ChatyIconThemeId.sculptedVolume -> {
        // Dimensional music note with dual-plane depth
        val leftNote = Path().apply { moveTo(6.0, 19.0); lineTo(6.0, 7.0); quadraticBezierTo(6.0, 4.5, 8.5, 4.5); quadraticBezierTo(11.0, 4.5, 11.0, 7.0); lineTo(11.0, 10.5); quadraticBezierTo(11.0, 13.0, 13.5, 13.0); quadraticBezierTo(16.0, 13.0, 16.0, 10.5); lineTo(16.0, 7.0); quadraticBezierTo(16.0, 4.5, 18.5, 4.5); quadraticBezierTo(21.0, 4.5, 21.0, 7.0); lineTo(21.0, 19.0); close() };
        canvas.drawPath(leftNote, fill);
        canvas.drawPath(leftNote, Paint().apply { color = color.withValues(alpha = 0.35); style = PaintingStyle.fill });
        }
      ChatyIconThemeId.expressiveInk -> {
        // Expressive brush-stroke music note
        val path7 = Path().apply { moveTo(6.0, 19.0); lineTo(6.0, 7.0); quadraticBezierTo(6.0, 4.5, 8.5, 4.5); quadraticBezierTo(11.0, 4.5, 11.0, 7.0); lineTo(11.0, 10.5); quadraticBezierTo(11.0, 13.0, 13.5, 13.0); quadraticBezierTo(16.0, 13.0, 16.0, 10.5); lineTo(16.0, 7.0); quadraticBezierTo(16.0, 4.5, 18.5, 4.5); quadraticBezierTo(21.0, 4.5, 21.0, 7.0); lineTo(21.0, 19.0); close() };
        canvas.drawPath(path7, stroke.apply { strokeWidth = 2.6 });
        }
      ChatyIconThemeId.signalLanguage -> {
        // Telemetry music signal
        val path8 = Path().apply { moveTo(6.0, 19.0); lineTo(6.0, 7.0); quadraticBezierTo(6.0, 4.5, 8.5, 4.5); quadraticBezierTo(11.0, 4.5, 11.0, 7.0); lineTo(11.0, 10.5); quadraticBezierTo(11.0, 13.0, 13.5, 13.0); quadraticBezierTo(16.0, 13.0, 16.0, 10.5); lineTo(16.0, 7.0); quadraticBezierTo(16.0, 4.5, 18.5, 4.5); quadraticBezierTo(21.0, 4.5, 21.0, 7.0); lineTo(21.0, 19.0); close() };
        canvas.drawPath(path8, fill);
        canvas.drawArc(Rect.fromLTWH(19, 8, 4, 8), -PI / 2, PI, false, stroke.apply { strokeWidth = 1.2 });
        }
        else -> {}
    }
  }
    }
}
