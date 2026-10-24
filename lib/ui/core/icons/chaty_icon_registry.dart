import 'dart:math' as math;
import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../controllers/preferences_controller.dart';
import 'chaty_icon_theme.dart';

/// Canonical semantic identifiers for all Chaty system icons.
enum ChatyIconId {
  // Navigation & Tabs
  chats,
  updates,
  calls,
  profile,
  tasks,

  // Overflow & General Controls
  more,
  moreOptions,
  search,
  settings,
  close,
  check,
  tune,
  chevronRight,
  back,

  // Messaging Actions
  send,
  emoji,
  attachment,
  attachmentPlus,
  microphone,
  microphoneOff,
  pin,
  unpin,
  archive,
  lock,
  unlock,
  mute,
  unmute,
  favourite,
  favouriteFilled,
  delete,
  reply,
  forward,
  edit,
  copy,
  share,

  // Camera Controls
  camera,
  cameraFlip,
  cameraShutter,
  cameraFlashOn,
  cameraFlashOff,
  gallery,

  // Call Controls
  callAudio,
  callVideo,
  callVideoOff,
  callEnd,
  cameraSwitch,
  screenShare,
  screenShareStop,
  speaker,
  earpiece,

  // Status & Message Receipts
  signal,
  messageCheckSingle,
  messageCheckDouble,
  messageClock,

  // Additional UI Controls
  pictureInPicture,
  autoAwesome,
  fullscreen,
  fullscreenExit,
  openInNew,
  clear,
  addTask,
  editOutline,
  videoCam,
  voiceCall,
  logout,
  deleteOutline,
  schedule,
  scheduleSend,
  sync,
  checkSingle,
  checkDouble,
  errorOutline,
  pause,
  playArrow,
  musicNote,
}

/// Centralized, original vector icon system for Chaty.
/// Pure vector rendering with 0 external libraries, optimized for 60/120fps.
class ChatyIcon extends StatelessWidget {
  final ChatyIconId icon;
  final double size;
  final Color? color;
  final ChatyIconThemeId? themeOverride;
  final String? semanticLabel;

  const ChatyIcon(
    this.icon, {
    super.key,
    this.size = 24.0,
    this.color,
    this.themeOverride,
    this.semanticLabel,
  });

  @override
  Widget build(BuildContext context) {
    ChatyIconThemeId activeTheme = themeOverride ?? ChatyIconThemeId.chatySigil;
    if (themeOverride == null && locator.isRegistered<ChatyPreferencesController>()) {
      try {
        final prefs = locator<ChatyPreferencesController>();
        activeTheme = ChatyIconThemeId.fromString(prefs.universal.iconTheme);
      } catch (_) {}
    }

    final resolvedColor = color ?? IconTheme.of(context).color ?? Colors.white;

    Widget widget = SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: _ChatyIconVectorPainter(
          icon: icon,
          theme: activeTheme,
          color: resolvedColor,
        ),
      ),
    );

    if (semanticLabel != null && semanticLabel!.isNotEmpty) {
      widget = Semantics(
        label: semanticLabel,
        child: widget,
      );
    }

    return RepaintBoundary(child: widget);
  }
}

/// Interactive animated icon with Emil Kowalski tactile micro-interaction (scale 0.93 on press, 160ms).
class ChatyAnimatedIcon extends StatefulWidget {
  final ChatyIconId icon;
  final double size;
  final Color? color;
  final ChatyIconThemeId? themeOverride;
  final VoidCallback? onTap;
  final String? tooltip;

  const ChatyAnimatedIcon(
    this.icon, {
    super.key,
    this.size = 24.0,
    this.color,
    this.themeOverride,
    this.onTap,
    this.tooltip,
  });

  @override
  State<ChatyAnimatedIcon> createState() => _ChatyAnimatedIconState();
}

class _ChatyAnimatedIconState extends State<ChatyAnimatedIcon>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _scaleAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 160),
    );
    _scaleAnimation = Tween<double>(begin: 1.0, end: 0.93).animate(
      CurvedAnimation(parent: _controller, curve: const Cubic(0.23, 1.0, 0.32, 1.0)),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onTapDown(TapDownDetails details) {
    _controller.forward();
  }

  void _onTapUp(TapUpDetails details) {
    _controller.reverse();
    widget.onTap?.call();
  }

  void _onTapCancel() {
    _controller.reverse();
  }

  @override
  Widget build(BuildContext context) {
    Widget icon = ScaleTransition(
      scale: _scaleAnimation,
      child: ChatyIcon(
        widget.icon,
        size: widget.size,
        color: widget.color,
        themeOverride: widget.themeOverride,
        semanticLabel: widget.tooltip,
      ),
    );

    if (widget.tooltip != null) {
      icon = Tooltip(message: widget.tooltip!, child: icon);
    }

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: widget.onTap != null ? _onTapDown : null,
      onTapUp: widget.onTap != null ? _onTapUp : null,
      onTapCancel: widget.onTap != null ? _onTapCancel : null,
      child: icon,
    );
  }
}

class _ChatyIconVectorPainter extends CustomPainter {
  final ChatyIconId icon;
  final ChatyIconThemeId theme;
  final Color color;

  const _ChatyIconVectorPainter({
    required this.icon,
    required this.theme,
    required this.color,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.width;
    final scale = s / 24.0;
    canvas.save();
    canvas.scale(scale, scale);

    final strokeCap = switch (theme) {
      ChatyIconThemeId.precisionContour || ChatyIconThemeId.pixelPrecision || ChatyIconThemeId.technicalBlueprint => StrokeCap.square,
      _ => StrokeCap.round,
    };

    final strokeJoin = switch (theme) {
      ChatyIconThemeId.precisionContour || ChatyIconThemeId.pixelPrecision || ChatyIconThemeId.origamiGeometry || ChatyIconThemeId.technicalBlueprint => StrokeJoin.miter,
      _ => StrokeJoin.round,
    };

    final strokePaint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = switch (theme) {
        ChatyIconThemeId.precisionContour => 1.2,
        ChatyIconThemeId.technicalBlueprint => 1.3,
        ChatyIconThemeId.pixelPrecision => 1.8,
        ChatyIconThemeId.expressiveInk => 2.2,
        _ => 1.85,
      }
      ..strokeCap = strokeCap
      ..strokeJoin = strokeJoin
      ..isAntiAlias = true;

    final fillPaint = Paint()
      ..color = color
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;

    final secondaryFillPaint = Paint()
      ..color = color.withValues(alpha: theme == ChatyIconThemeId.sculptedVolume ? 0.32 : 0.16)
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;

    switch (icon) {
      case ChatyIconId.more:
      case ChatyIconId.moreOptions:
        _paintMore(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.chats:
        _paintChats(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.updates:
        _paintUpdates(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.calls:
        _paintCalls(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.profile:
        _paintProfile(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.tasks:
        _paintTasks(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.camera:
        _paintCamera(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.cameraFlip:
      case ChatyIconId.cameraSwitch:
        _paintCameraFlip(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.cameraShutter:
        _paintCameraShutter(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.cameraFlashOn:
        _paintFlash(canvas, strokePaint, fillPaint, on: true);
        break;
      case ChatyIconId.cameraFlashOff:
        _paintFlash(canvas, strokePaint, fillPaint, on: false);
        break;
      case ChatyIconId.gallery:
        _paintGallery(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.callAudio:
        _paintCalls(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.callVideo:
        _paintVideo(canvas, strokePaint, fillPaint, secondaryFillPaint, off: false);
        break;
      case ChatyIconId.callVideoOff:
        _paintVideo(canvas, strokePaint, fillPaint, secondaryFillPaint, off: true);
        break;
      case ChatyIconId.callEnd:
        _paintCallEnd(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.screenShare:
        _paintScreenShare(canvas, strokePaint, fillPaint, stop: false);
        break;
      case ChatyIconId.screenShareStop:
        _paintScreenShare(canvas, strokePaint, fillPaint, stop: true);
        break;
      case ChatyIconId.speaker:
        _paintSpeaker(canvas, strokePaint, fillPaint, isSpeaker: true);
        break;
      case ChatyIconId.earpiece:
        _paintSpeaker(canvas, strokePaint, fillPaint, isSpeaker: false);
        break;
      case ChatyIconId.send:
        _paintSend(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.emoji:
        _paintEmoji(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.attachment:
        _paintAttachment(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.attachmentPlus:
        _paintAttachmentPlus(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.microphone:
        _paintMic(canvas, strokePaint, fillPaint, secondaryFillPaint, off: false);
        break;
      case ChatyIconId.microphoneOff:
        _paintMic(canvas, strokePaint, fillPaint, secondaryFillPaint, off: true);
        break;
      case ChatyIconId.search:
        _paintSearch(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.pin:
        _paintPin(canvas, strokePaint, fillPaint, unpin: false);
        break;
      case ChatyIconId.unpin:
        _paintPin(canvas, strokePaint, fillPaint, unpin: true);
        break;
      case ChatyIconId.archive:
        _paintArchive(canvas, strokePaint, secondaryFillPaint);
        break;
      case ChatyIconId.lock:
        _paintLock(canvas, strokePaint, fillPaint, locked: true);
        break;
      case ChatyIconId.unlock:
        _paintLock(canvas, strokePaint, fillPaint, locked: false);
        break;
      case ChatyIconId.mute:
        _paintSpeaker(canvas, strokePaint, fillPaint, isMute: true);
        break;
      case ChatyIconId.unmute:
        _paintSpeaker(canvas, strokePaint, fillPaint, isMute: false);
        break;
      case ChatyIconId.favourite:
        _paintHeart(canvas, strokePaint, fillPaint, fillHeart: false);
        break;
      case ChatyIconId.favouriteFilled:
        _paintHeart(canvas, strokePaint, fillPaint, fillHeart: true);
        break;
      case ChatyIconId.delete:
        _paintDelete(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.settings:
        _paintSettings(canvas, strokePaint, fillPaint, secondaryFillPaint);
        break;
      case ChatyIconId.close:
        _paintClose(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.check:
        _paintCheck(canvas, strokePaint);
        break;
      case ChatyIconId.tune:
        _paintTune(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.chevronRight:
        _paintChevronRight(canvas, strokePaint);
        break;
      case ChatyIconId.back:
        _paintBack(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.reply:
        _paintReply(canvas, strokePaint);
        break;
      case ChatyIconId.forward:
        _paintForward(canvas, strokePaint);
        break;
      case ChatyIconId.edit:
        _paintEdit(canvas, strokePaint);
        break;
      case ChatyIconId.copy:
        _paintCopy(canvas, strokePaint);
        break;
      case ChatyIconId.share:
        _paintShare(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.signal:
        _paintSignal(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.messageCheckSingle:
        _paintTicks(canvas, strokePaint, fillPaint, doubleTicks: false);
        break;
      case ChatyIconId.messageCheckDouble:
        _paintTicks(canvas, strokePaint, fillPaint, doubleTicks: true);
        break;
      case ChatyIconId.messageClock:
        _paintClock(canvas, strokePaint);
        break;
      case ChatyIconId.pictureInPicture:
        _paintPictureInPicture(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.autoAwesome:
        _paintAutoAwesome(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.fullscreen:
        _paintFullscreen(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.fullscreenExit:
        _paintFullscreenExit(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.openInNew:
        _paintOpenInNew(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.clear:
        _paintClear(canvas, strokePaint);
        break;
      case ChatyIconId.addTask:
        _paintAddTask(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.editOutline:
        _paintEditOutline(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.videoCam:
        _paintVideoCam(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.voiceCall:
        _paintVoiceCall(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.logout:
        _paintLogout(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.deleteOutline:
        _paintDeleteOutline(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.schedule:
        _paintSchedule(canvas, strokePaint);
        break;
      case ChatyIconId.scheduleSend:
        _paintScheduleSend(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.sync:
        _paintSync(canvas, strokePaint);
        break;
      case ChatyIconId.checkSingle:
        _paintCheckSingle(canvas, strokePaint);
        break;
      case ChatyIconId.checkDouble:
        _paintCheckDouble(canvas, strokePaint);
        break;
      case ChatyIconId.errorOutline:
        _paintErrorOutline(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.pause:
        _paintPause(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.playArrow:
        _paintPlayArrow(canvas, strokePaint, fillPaint);
        break;
      case ChatyIconId.musicNote:
        _paintMusicNote(canvas, strokePaint, fillPaint);
        break;
    }

    canvas.restore();
  }

  // =========================================================================
  // 10 DISTINCT DESIGN GRAMMARS IMPLEMENTATION
  // =========================================================================

  // 1. MORE / CANONICAL THREE-DOT OVERFLOW
  void _paintMore(Canvas canvas, Paint stroke, Paint fill, Paint secondary) {
    const center = Offset(12, 12);
    const top = Offset(12, 5.2);
    const bottom = Offset(12, 18.8);

    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Orbital signature: connected spine with orbital halo on center dot
        final spine = Paint()
          ..color = stroke.color.withValues(alpha: 0.28)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.0;
        canvas.drawLine(const Offset(12, 7.5), const Offset(12, 16.5), spine);
        canvas.drawCircle(center, 2.1, fill);
        canvas.drawCircle(top, 1.85, fill);
        canvas.drawCircle(bottom, 1.85, fill);
        canvas.drawCircle(center, 4.0, Paint()
          ..color = stroke.color.withValues(alpha: 0.35)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 0.9);
        break;

      case ChatyIconThemeId.precisionContour:
        // Hairline architectural wireframe: 3 hollow square rings with alignment gaps
        void drawHollowSquare(Offset c) {
          canvas.drawRect(Rect.fromCenter(center: c, width: 3.8, height: 3.8), stroke..strokeWidth = 1.1);
        }
        drawHollowSquare(top);
        drawHollowSquare(center);
        drawHollowSquare(bottom);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // 100% solid filled monolith blocks with stenciled white cross incision
        void drawMonolithBlock(Offset c) {
          canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromCenter(center: c, width: 4.8, height: 4.8), const Radius.circular(1.0)), fill);
          // Stenciled white slit
          final slit = Paint()..color = Colors.white..strokeWidth = 0.9..style = PaintingStyle.stroke;
          canvas.drawLine(Offset(c.dx - 1.6, c.dy), Offset(c.dx + 1.6, c.dy), slit);
        }
        drawMonolithBlock(top);
        drawMonolithBlock(center);
        drawMonolithBlock(bottom);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Faceted diamond paper points with diagonal crease
        void drawOrigamiDiamond(Offset c) {
          final p = Path()
            ..moveTo(c.dx, c.dy - 2.5)
            ..lineTo(c.dx + 2.5, c.dy)
            ..lineTo(c.dx, c.dy + 2.5)
            ..lineTo(c.dx - 2.5, c.dy)
            ..close();
          canvas.drawPath(p, fill);
          final crease = Paint()..color = Colors.white38..strokeWidth = 0.7..style = PaintingStyle.stroke;
          canvas.drawLine(Offset(c.dx, c.dy - 2.5), Offset(c.dx, c.dy + 2.5), crease);
        }
        drawOrigamiDiamond(top);
        drawOrigamiDiamond(center);
        drawOrigamiDiamond(bottom);
        break;

      case ChatyIconThemeId.organicMotion:
        // Fluid asymmetrical pebble droplets
        canvas.drawOval(Rect.fromCenter(center: top, width: 3.6, height: 4.6), fill);
        canvas.drawOval(Rect.fromCenter(center: center, width: 4.6, height: 3.6), fill);
        canvas.drawOval(Rect.fromCenter(center: bottom, width: 3.8, height: 4.8), fill);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit stepped pixel square blocks (3x3 pixels)
        canvas.drawRect(Rect.fromLTWH(10.5, 4, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(10.5, 10.5, 3, 3), fill);
        canvas.drawRect(Rect.fromLTWH(10.5, 17, 3, 3), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD crosshair alignment targets (circular node with drafting ticks)
        void drawCADTarget(Offset c) {
          canvas.drawCircle(c, 1.4, fill);
          final tick = Paint()..color = stroke.color..strokeWidth = 0.85;
          canvas.drawLine(Offset(c.dx - 2.6, c.dy), Offset(c.dx + 2.6, c.dy), tick);
          canvas.drawLine(Offset(c.dx, c.dy - 2.6), Offset(c.dx, c.dy + 2.6), tick);
        }
        drawCADTarget(top);
        drawCADTarget(center);
        drawCADTarget(bottom);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Dimensional beveled pill facets with dual-tone depth
        void drawSculptedPill(Offset c) {
          canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromCenter(center: c, width: 4.5, height: 4.5), const Radius.circular(2.25)), secondary);
          canvas.drawCircle(Offset(c.dx - 0.6, c.dy - 0.6), 1.6, fill);
        }
        drawSculptedPill(top);
        drawSculptedPill(center);
        drawSculptedPill(bottom);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Dynamic calligraphic sumi-e ink droplets with pressure flares
        final inkPath = Path()
          ..moveTo(12, 3.5)
          ..quadraticBezierTo(13.6, 5.2, 12, 6.8)
          ..quadraticBezierTo(10.4, 5.2, 12, 3.5);
        canvas.drawPath(inkPath, fill);
        final inkCenter = Path()
          ..moveTo(12, 10.2)
          ..quadraticBezierTo(14.2, 12.0, 12, 13.8)
          ..quadraticBezierTo(9.8, 12.0, 12, 10.2);
        canvas.drawPath(inkCenter, fill);
        final inkBottom = Path()
          ..moveTo(12, 17.2)
          ..quadraticBezierTo(13.6, 18.8, 12, 20.5)
          ..quadraticBezierTo(10.4, 18.8, 12, 17.2);
        canvas.drawPath(inkBottom, fill);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Radiating telecommunication beacon pulses with wave segments
        canvas.drawCircle(center, 2.0, fill);
        final wave = Paint()..color = stroke.color..style = PaintingStyle.stroke..strokeWidth = 1.1;
        canvas.drawArc(const Rect.fromLTWH(8, 2.5, 8, 6), math.pi, math.pi, false, wave);
        canvas.drawArc(const Rect.fromLTWH(8, 15.5, 8, 6), 0, math.pi, false, wave);
        break;
    }
  }

  // 2. SEARCH
  void _paintSearch(Canvas canvas, Paint stroke, Paint fill) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Orbital signature: lens with intersecting orbital halo ring
        canvas.drawCircle(const Offset(10.5, 10.5), 6.0, stroke);
        canvas.drawCircle(const Offset(10.5, 10.5), 2.2, Paint()..color = stroke.color.withValues(alpha: 0.3)..style = PaintingStyle.stroke..strokeWidth = 0.9);
        canvas.drawLine(const Offset(15.0, 15.0), const Offset(20.5, 20.5), stroke);
        break;

      case ChatyIconThemeId.precisionContour:
        // Chamfered octagonal wire lens with detached hairline handle
        final oct = Path()
          ..moveTo(8.0, 4.5)..lineTo(13.0, 4.5)..lineTo(16.5, 8.0)
          ..lineTo(16.5, 13.0)..lineTo(13.0, 16.5)..lineTo(8.0, 16.5)
          ..lineTo(4.5, 13.0)..lineTo(4.5, 8.0)..close();
        canvas.drawPath(oct, stroke..strokeWidth = 1.2);
        canvas.drawLine(const Offset(16.0, 16.0), const Offset(21.0, 21.0), stroke);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Solid black disk with stenciled negative-space aperture and wedge handle
        final disk = Path()..addOval(const Rect.fromLTWH(4.0, 4.0, 13.0, 13.0));
        final handle = Path()
          ..moveTo(14.5, 14.5)..lineTo(20.5, 20.5)..lineTo(19.0, 22.0)..lineTo(13.0, 16.0)..close();
        canvas.drawPath(disk, fill);
        canvas.drawPath(handle, fill);
        // White negative space cutout
        canvas.drawCircle(const Offset(10.5, 10.5), 4.2, Paint()..color = Colors.white..style = PaintingStyle.fill);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Hexagonal faceted paper-folded lens with sharp diagonal crease
        final p = Path()
          ..moveTo(10.5, 4.0)..lineTo(16.0, 7.5)..lineTo(16.0, 13.5)
          ..lineTo(10.5, 17.0)..lineTo(5.0, 13.5)..lineTo(5.0, 7.5)..close();
        canvas.drawPath(p, stroke);
        canvas.drawLine(const Offset(10.5, 4.0), const Offset(10.5, 17.0), Paint()..color = stroke.color.withValues(alpha: 0.4)..strokeWidth = 0.9);
        canvas.drawLine(const Offset(14.5, 14.5), const Offset(21.0, 21.0), stroke..strokeWidth = 2.0);
        break;

      case ChatyIconThemeId.organicMotion:
        // Asymmetric liquid droplet lens with curved fluid handle
        final drop = Path()
          ..moveTo(10.5, 4.0)
          ..cubicTo(16.5, 4.2, 17.5, 11.0, 14.5, 15.5)
          ..cubicTo(11.5, 17.8, 5.5, 16.0, 4.8, 10.5)
          ..cubicTo(4.2, 6.2, 7.5, 3.8, 10.5, 4.0);
        canvas.drawPath(drop, stroke);
        final h = Path()
          ..moveTo(14.2, 14.8)
          ..quadraticBezierTo(16.0, 18.5, 21.0, 20.5);
        canvas.drawPath(h, stroke);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit stepped pixel magnifying glass
        // Stepped pixel ring: top 4px, sides 4px, bottom 4px, diagonal handle
        canvas.drawRect(const Rect.fromLTWH(8, 4, 6, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 16, 6, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(4, 8, 2, 6), fill);
        canvas.drawRect(const Rect.fromLTWH(16, 8, 2, 6), fill);
        canvas.drawRect(const Rect.fromLTWH(6, 6, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(14, 6, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(6, 14, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(14, 14, 2, 2), fill);
        // Stepped handle
        canvas.drawRect(const Rect.fromLTWH(16, 16, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(18, 18, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(20, 20, 2, 2), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD crosshair drafted lens with extended datum axes
        canvas.drawCircle(const Offset(10.5, 10.5), 6.0, stroke..strokeWidth = 1.2);
        // Extended crosshair axes
        final cad = Paint()..color = stroke.color.withValues(alpha: 0.65)..strokeWidth = 0.8;
        canvas.drawLine(const Offset(3.0, 10.5), const Offset(18.0, 10.5), cad);
        canvas.drawLine(const Offset(10.5, 3.0), const Offset(10.5, 18.0), cad);
        canvas.drawLine(const Offset(15.0, 15.0), const Offset(21.5, 21.5), stroke..strokeWidth = 1.5);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Dimensional beveled multi-plate lens
        canvas.drawCircle(const Offset(10.5, 10.5), 6.5, Paint()..color = color.withValues(alpha: 0.35)..style = PaintingStyle.fill);
        canvas.drawCircle(const Offset(10.0, 10.0), 5.5, stroke..strokeWidth = 1.8);
        canvas.drawLine(const Offset(14.5, 14.5), const Offset(20.5, 20.5), stroke..strokeWidth = 2.4);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Bold brush-pen calligraphic loop with tapered handle
        final ink = Path()
          ..moveTo(6.0, 10.5)
          ..cubicTo(6.0, 5.5, 15.5, 5.5, 15.5, 10.5)
          ..cubicTo(15.5, 15.5, 6.0, 15.5, 6.0, 10.5);
        canvas.drawPath(ink, stroke..strokeWidth = 2.4);
        final tail = Path()
          ..moveTo(14.0, 14.0)
          ..quadraticBezierTo(16.5, 17.5, 21.5, 21.0);
        canvas.drawPath(tail, stroke..strokeWidth = 2.8);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Radar sweep transmitter node with concentric pulse arcs
        canvas.drawCircle(const Offset(10.5, 10.5), 2.2, fill);
        canvas.drawArc(const Rect.fromLTWH(5.5, 5.5, 10, 10), -0.6, 2.4, false, stroke);
        canvas.drawArc(const Rect.fromLTWH(3.0, 3.0, 15, 15), -0.8, 2.8, false, stroke);
        canvas.drawLine(const Offset(15.5, 15.5), const Offset(21.0, 21.0), stroke..strokeWidth = 2.0);
        break;
    }
  }

  // 3. BACK NAVIGATION
  void _paintBack(Canvas canvas, Paint stroke, Paint fill) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Orbital smooth fillet arrow with circular terminal apex
        final p = Path()
          ..moveTo(14.8, 6.2)
          ..lineTo(9.0, 12.0)
          ..lineTo(14.8, 17.8);
        canvas.drawPath(p, stroke);
        canvas.drawCircle(const Offset(19.0, 12.0), 1.5, fill);
        canvas.drawLine(const Offset(9.5, 12.0), const Offset(17.5, 12.0), stroke);
        break;

      case ChatyIconThemeId.precisionContour:
        // Razor-sharp 90° hairline chevron with detached parallel guide
        final p = Path()
          ..moveTo(15.5, 5.5)
          ..lineTo(9.0, 12.0)
          ..lineTo(15.5, 18.5);
        canvas.drawPath(p, stroke..strokeWidth = 1.2);
        // Parallel drafting wire
        final p2 = Path()
          ..moveTo(18.0, 7.5)
          ..lineTo(13.5, 12.0)
          ..lineTo(18.0, 16.5);
        canvas.drawPath(p2, stroke..strokeWidth = 0.85);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Solid filled arrow wedge with stark white negative-space chevron cutout
        final wedge = Path()
          ..moveTo(18.5, 5.0)
          ..lineTo(7.5, 12.0)
          ..lineTo(18.5, 19.0)
          ..close();
        canvas.drawPath(wedge, fill);
        final slit = Paint()..color = Colors.white..strokeWidth = 1.6..style = PaintingStyle.stroke;
        canvas.drawLine(const Offset(16.0, 8.5), const Offset(11.0, 12.0), slit);
        canvas.drawLine(const Offset(11.0, 12.0), const Offset(16.0, 15.5), slit);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Folded paper triangular arrow with diagonal crease
        final p = Path()
          ..moveTo(17.5, 5.0)
          ..lineTo(7.5, 12.0)
          ..lineTo(17.5, 19.0)
          ..lineTo(14.5, 12.0)
          ..close();
        canvas.drawPath(p, fill);
        canvas.drawLine(const Offset(7.5, 12.0), const Offset(14.5, 12.0), Paint()..color = Colors.white60..strokeWidth = 0.9);
        break;

      case ChatyIconThemeId.organicMotion:
        // Fluid river-swoosh arrow with bulbous teardrop tip
        final p = Path()
          ..moveTo(16.5, 6.0)
          ..quadraticBezierTo(10.0, 10.0, 7.5, 12.0)
          ..quadraticBezierTo(10.0, 14.0, 16.5, 18.0);
        canvas.drawPath(p, stroke);
        canvas.drawCircle(const Offset(7.5, 12.0), 2.2, fill);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit stepped pixel arrow
        canvas.drawRect(const Rect.fromLTWH(6, 11, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 9, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 13, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(10, 7, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(10, 15, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(12, 5, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(12, 17, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 11, 10, 2), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD dimension leader arrow with vertex overshoot and datum tick
        canvas.drawLine(const Offset(15.5, 5.0), const Offset(8.0, 12.0), stroke..strokeWidth = 1.3);
        canvas.drawLine(const Offset(8.0, 12.0), const Offset(15.5, 19.0), stroke);
        canvas.drawLine(const Offset(8.0, 12.0), const Offset(20.0, 12.0), stroke);
        // Datum end ticks
        canvas.drawLine(const Offset(20.0, 9.5), const Offset(20.0, 14.5), stroke..strokeWidth = 0.9);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Embossed relief chevron with dual-plane light/shadow halves
        final topHalf = Path()
          ..moveTo(16.0, 6.0)..lineTo(8.5, 12.0)..lineTo(16.0, 12.0)..close();
        final btmHalf = Path()
          ..moveTo(16.0, 12.0)..lineTo(8.5, 12.0)..lineTo(16.0, 18.0)..close();
        canvas.drawPath(topHalf, fill);
        canvas.drawPath(btmHalf, Paint()..color = color.withValues(alpha: 0.35)..style = PaintingStyle.fill);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Dynamic calligraphic brush-stroke arrow
        final p = Path()
          ..moveTo(16.0, 5.0)
          ..quadraticBezierTo(11.0, 9.0, 7.5, 12.0)
          ..quadraticBezierTo(11.0, 15.0, 16.0, 19.0);
        canvas.drawPath(p, stroke..strokeWidth = 2.6);
        final stem = Path()
          ..moveTo(8.5, 12.0)
          ..quadraticBezierTo(14.0, 11.5, 19.5, 12.0);
        canvas.drawPath(stem, stroke..strokeWidth = 2.4);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Left-pointing directional vector beam with trailing pulse wave arcs
        canvas.drawLine(const Offset(14.0, 7.0), const Offset(8.0, 12.0), stroke..strokeWidth = 1.8);
        canvas.drawLine(const Offset(8.0, 12.0), const Offset(14.0, 17.0), stroke..strokeWidth = 1.8);
        canvas.drawArc(const Rect.fromLTWH(14, 8, 4, 8), -math.pi / 2, math.pi, false, stroke..strokeWidth = 1.1);
        canvas.drawArc(const Rect.fromLTWH(17, 6, 4, 12), -math.pi / 2, math.pi, false, stroke..strokeWidth = 1.1);
        break;
    }
  }

  // 4. CAMERA
  void _paintEmoji(Canvas canvas, Paint stroke, Paint fill, Paint secondary) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Signature smooth face with orbital halo dot
        canvas.drawCircle(const Offset(12, 12), 8.0, stroke);
        canvas.drawCircle(const Offset(9.2, 10.2), 1.1, fill);
        canvas.drawCircle(const Offset(14.8, 10.2), 1.1, fill);
        canvas.drawArc(const Rect.fromLTWH(7.6, 10.6, 8.8, 6.4), 0.35, 2.45, false, stroke);
        canvas.drawCircle(const Offset(19.0, 5.4), 1.0, fill);
        break;

      case ChatyIconThemeId.precisionContour:
        // Hairline open-contour face
        canvas.drawArc(const Rect.fromLTWH(4.0, 4.0, 16.0, 16.0), 0.5, 5.3, false, stroke..strokeWidth = 1.2);
        canvas.drawCircle(const Offset(9.2, 10.2), 0.9, stroke..strokeWidth = 1.0);
        canvas.drawCircle(const Offset(14.8, 10.2), 0.9, stroke..strokeWidth = 1.0);
        canvas.drawArc(const Rect.fromLTWH(7.8, 10.8, 8.4, 6.0), 0.4, 2.3, false, stroke..strokeWidth = 1.0);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Solid disc with negative-space eyes and smile
        canvas.drawCircle(const Offset(12, 12), 8.2, fill);
        final cut = Paint()..color = Colors.white..style = PaintingStyle.fill;
        canvas.drawCircle(const Offset(9.2, 10.0), 1.2, cut);
        canvas.drawCircle(const Offset(14.8, 10.0), 1.2, cut);
        final smile = Paint()..color = Colors.white..style = PaintingStyle.stroke..strokeWidth = 1.6..strokeCap = StrokeCap.round;
        canvas.drawArc(const Rect.fromLTWH(7.6, 10.4, 8.8, 6.6), 0.35, 2.45, false, smile);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Faceted hexagonal face with diamond eyes
        final face = Path()
          ..moveTo(12, 3.6)..lineTo(19.2, 7.8)..lineTo(19.2, 16.2)
          ..lineTo(12, 20.4)..lineTo(4.8, 16.2)..lineTo(4.8, 7.8)..close();
        canvas.drawPath(face, stroke);
        final eyeL = Path()
          ..moveTo(9.2, 9.0)..lineTo(10.2, 10.2)..lineTo(9.2, 11.4)..lineTo(8.2, 10.2)..close();
        final eyeR = Path()
          ..moveTo(14.8, 9.0)..lineTo(15.8, 10.2)..lineTo(14.8, 11.4)..lineTo(13.8, 10.2)..close();
        canvas.drawPath(eyeL, stroke);
        canvas.drawPath(eyeR, stroke);
        final smile = Path()
          ..moveTo(8.6, 14.2)..lineTo(12, 16.2)..lineTo(15.4, 14.2);
        canvas.drawPath(smile, stroke);
        break;

      case ChatyIconThemeId.organicMotion:
        // Pebble blob face with soft oval eyes
        final blob = Path()
          ..moveTo(12, 4.0)
          ..cubicTo(18.5, 3.5, 20.4, 9.0, 19.6, 13.0)
          ..cubicTo(18.8, 17.5, 14.5, 20.6, 10.2, 19.8)
          ..cubicTo(5.6, 19.0, 3.6, 14.8, 4.4, 10.4)
          ..cubicTo(5.2, 6.2, 8.0, 4.3, 12, 4.0);
        canvas.drawPath(blob, stroke);
        canvas.drawOval(const Rect.fromLTWH(8.0, 9.2, 2.2, 2.8), fill);
        canvas.drawOval(const Rect.fromLTWH(13.6, 9.2, 2.2, 2.8), fill);
        canvas.drawArc(const Rect.fromLTWH(7.8, 11.2, 8.4, 6.2), 0.4, 2.35, false, stroke);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // 8-bit stepped face
        canvas.drawRect(const Rect.fromLTWH(5, 5, 14, 14), stroke..strokeWidth = 1.8);
        canvas.drawRect(const Rect.fromLTWH(8, 9, 2.4, 2.4), fill);
        canvas.drawRect(const Rect.fromLTWH(13.6, 9, 2.4, 2.4), fill);
        canvas.drawRect(const Rect.fromLTWH(8.6, 14.6, 6.8, 1.8), fill);
        canvas.drawRect(const Rect.fromLTWH(7.4, 13.4, 1.4, 1.4), fill);
        canvas.drawRect(const Rect.fromLTWH(15.2, 13.4, 1.4, 1.4), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD face with datum crosshair and corner ticks
        canvas.drawCircle(const Offset(12, 12), 8.0, stroke..strokeWidth = 1.2);
        final tick = Paint()..color = stroke.color..strokeWidth = 0.8;
        canvas.drawLine(const Offset(12, 1.2), const Offset(12, 4.4), tick);
        canvas.drawLine(const Offset(12, 19.6), const Offset(12, 22.8), tick);
        canvas.drawLine(const Offset(1.2, 12), const Offset(4.4, 12), tick);
        canvas.drawLine(const Offset(19.6, 12), const Offset(22.8, 12), tick);
        canvas.drawCircle(const Offset(9.2, 10.2), 1.0, stroke..strokeWidth = 1.0);
        canvas.drawCircle(const Offset(14.8, 10.2), 1.0, stroke..strokeWidth = 1.0);
        canvas.drawArc(const Rect.fromLTWH(7.8, 10.6, 8.4, 6.2), 0.4, 2.35, false, stroke..strokeWidth = 1.0);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Layered dimensional face
        canvas.drawCircle(const Offset(12, 12), 8.2, fill);
        canvas.drawCircle(const Offset(10.6, 10.6), 6.4, secondary);
        final cut = Paint()..color = Colors.white..style = PaintingStyle.fill;
        canvas.drawCircle(const Offset(9.4, 9.8), 1.2, cut);
        canvas.drawCircle(const Offset(14.6, 9.8), 1.2, cut);
        final smile = Paint()..color = Colors.white..style = PaintingStyle.stroke..strokeWidth = 1.5..strokeCap = StrokeCap.round;
        canvas.drawArc(const Rect.fromLTWH(7.8, 10.4, 8.4, 6.4), 0.4, 2.35, false, smile);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Bold open brushstroke face
        canvas.drawArc(const Rect.fromLTWH(3.8, 4.2, 16.4, 16.0), 0.7, 5.0, false, stroke..strokeWidth = 2.6);
        canvas.drawCircle(const Offset(9.0, 10.0), 1.5, fill);
        canvas.drawCircle(const Offset(15.0, 9.6), 1.3, fill);
        canvas.drawArc(const Rect.fromLTWH(7.4, 10.8, 9.2, 6.6), 0.3, 2.6, false, stroke..strokeWidth = 2.2);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Face with radiating signal waves
        canvas.drawCircle(const Offset(12, 12), 7.4, stroke);
        canvas.drawCircle(const Offset(9.4, 10.4), 1.1, fill);
        canvas.drawCircle(const Offset(14.6, 10.4), 1.1, fill);
        canvas.drawArc(const Rect.fromLTWH(8.0, 10.8, 8.0, 5.8), 0.4, 2.35, false, stroke..strokeWidth = 1.1);
        canvas.drawArc(const Rect.fromLTWH(16.8, 1.6, 5.2, 5.2), -0.6, 2.2, false, stroke..strokeWidth = 1.0);
        canvas.drawArc(const Rect.fromLTWH(15.2, 3.4, 3.4, 3.4), -0.6, 2.2, false, stroke..strokeWidth = 1.0);
        break;
    }
  }

  void _paintCamera(Canvas canvas, Paint stroke, Paint fill, Paint secondary) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Smooth rounded body with orbital aperture lens ring
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(3.5, 6.5, 17.0, 12.5), const Radius.circular(3.5)), stroke);
        canvas.drawCircle(const Offset(12.0, 12.8), 3.5, stroke);
        canvas.drawCircle(const Offset(12.0, 12.8), 1.2, fill);
        canvas.drawCircle(const Offset(17.5, 9.5), 1.0, fill);
        break;

      case ChatyIconThemeId.precisionContour:
        // Hairline drafting camera body with open 45° chamfered corners
        final cam = Path()
          ..moveTo(5.5, 7.0)..lineTo(9.0, 7.0)..lineTo(10.5, 5.0)..lineTo(13.5, 5.0)
          ..lineTo(15.0, 7.0)..lineTo(18.5, 7.0)..lineTo(20.5, 9.0)..lineTo(20.5, 17.0)
          ..lineTo(18.5, 19.0)..lineTo(5.5, 19.0)..lineTo(3.5, 17.0)..lineTo(3.5, 9.0)..close();
        canvas.drawPath(cam, stroke..strokeWidth = 1.2);
        canvas.drawCircle(const Offset(12.0, 13.0), 3.4, stroke..strokeWidth = 1.0);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // 100% solid filled camera block silhouette with stark white negative-space lens
        final solid = Path()
          ..moveTo(4.0, 8.0)..lineTo(8.5, 8.0)..lineTo(10.0, 5.5)..lineTo(14.0, 5.5)
          ..lineTo(15.5, 8.0)..lineTo(20.0, 8.0)..lineTo(20.0, 19.0)..lineTo(4.0, 19.0)..close();
        canvas.drawPath(solid, fill);
        canvas.drawCircle(const Offset(12.0, 13.5), 3.6, Paint()..color = Colors.white..style = PaintingStyle.fill);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Faceted polygonal folded camera body
        final p = Path()
          ..moveTo(4.0, 8.0)..lineTo(9.0, 8.0)..lineTo(10.5, 5.0)..lineTo(13.5, 5.0)
          ..lineTo(15.0, 8.0)..lineTo(20.0, 8.0)..lineTo(18.5, 18.5)..lineTo(5.5, 18.5)..close();
        canvas.drawPath(p, stroke);
        // Diamond faceted lens
        final lens = Path()
          ..moveTo(12.0, 10.0)..lineTo(15.0, 13.0)..lineTo(12.0, 16.0)..lineTo(9.0, 13.0)..close();
        canvas.drawPath(lens, stroke);
        break;

      case ChatyIconThemeId.organicMotion:
        // Fluid asymmetric pebble camera body
        final dropCam = Path()
          ..moveTo(4.5, 9.0)
          ..cubicTo(5.0, 5.5, 19.0, 5.0, 19.5, 9.0)
          ..cubicTo(20.5, 14.0, 19.0, 19.5, 12.0, 19.0)
          ..cubicTo(4.5, 19.0, 4.0, 14.0, 4.5, 9.0);
        canvas.drawPath(dropCam, stroke);
        canvas.drawOval(const Rect.fromLTWH(8.5, 9.5, 7.0, 6.0), stroke);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit stepped pixel camera
        canvas.drawRect(const Rect.fromLTWH(4, 7, 16, 12), stroke..strokeWidth = 1.8);
        canvas.drawRect(const Rect.fromLTWH(9, 5, 6, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(9, 10, 6, 6), stroke..strokeWidth = 1.6);
        canvas.drawRect(const Rect.fromLTWH(16, 8, 2, 2), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD camera with corner alignment ticks and optical crosshairs
        canvas.drawRect(const Rect.fromLTWH(4.0, 7.0, 16.0, 12.0), stroke..strokeWidth = 1.2);
        // Alignment ticks
        final tick = Paint()..color = stroke.color..strokeWidth = 0.8;
        canvas.drawLine(const Offset(1.5, 7.0), const Offset(6.5, 7.0), tick);
        canvas.drawLine(const Offset(4.0, 4.5), const Offset(4.0, 9.5), tick);
        canvas.drawCircle(const Offset(12.0, 13.0), 3.5, stroke..strokeWidth = 1.0);
        canvas.drawLine(const Offset(7.5, 13.0), const Offset(16.5, 13.0), tick);
        canvas.drawLine(const Offset(12.0, 8.5), const Offset(12.0, 17.5), tick);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Dimensional layered camera body with raised top deck
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(4.0, 7.0, 16.0, 12.0), const Radius.circular(2.5)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(9.0, 4.5, 6.0, 3.5), const Radius.circular(1.0)), secondary);
        canvas.drawCircle(const Offset(12.0, 13.0), 3.8, secondary);
        canvas.drawCircle(const Offset(12.0, 13.0), 2.2, Paint()..color = Colors.white..style = PaintingStyle.fill);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Dynamic calligraphic brush-ink camera
        final base = Path()
          ..moveTo(4.0, 18.0)
          ..quadraticBezierTo(12.0, 17.0, 20.0, 18.0);
        canvas.drawPath(base, stroke..strokeWidth = 2.8);
        final body = Path()
          ..moveTo(4.5, 18.0)..lineTo(4.5, 8.5)
          ..quadraticBezierTo(12.0, 7.0, 19.5, 8.5)..lineTo(19.5, 18.0);
        canvas.drawPath(body, stroke..strokeWidth = 2.0);
        canvas.drawCircle(const Offset(12.0, 13.0), 3.5, stroke..strokeWidth = 2.2);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Telemetry camera sensor with radiating optical wave arcs
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(4.0, 7.0, 16.0, 12.0), const Radius.circular(2.0)), stroke);
        canvas.drawCircle(const Offset(12.0, 13.0), 2.0, fill);
        canvas.drawArc(const Rect.fromLTWH(7.0, 8.0, 10, 10), -1.2, 2.4, false, stroke..strokeWidth = 1.1);
        canvas.drawArc(const Rect.fromLTWH(17.5, 6.0, 4, 4), -0.5, 2.0, false, stroke..strokeWidth = 1.0);
        break;
    }
  }

  // 5. CAMERA FLIP / SWITCH
  void _paintCameraFlip(Canvas canvas, Paint stroke, Paint fill) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Dual orbital curved arrows revolving around a central core
        final rect = const Rect.fromLTWH(4.0, 4.0, 16.0, 16.0);
        canvas.drawArc(rect, 0.4, 2.2, false, stroke);
        canvas.drawArc(rect, 3.5, 2.2, false, stroke);
        // Arrowheads
        canvas.drawCircle(const Offset(12.0, 12.0), 1.5, fill);
        final a1 = Path()..moveTo(18.5, 14.5)..lineTo(20.5, 11.5)..lineTo(16.5, 11.5)..close();
        final a2 = Path()..moveTo(5.5, 9.5)..lineTo(3.5, 12.5)..lineTo(7.5, 12.5)..close();
        canvas.drawPath(a1, fill);
        canvas.drawPath(a2, fill);
        break;

      case ChatyIconThemeId.precisionContour:
        // Sharp right-angled counter-rotating wire arrows with gap breaks
        final p1 = Path()..moveTo(12.0, 4.0)..lineTo(19.0, 4.0)..lineTo(19.0, 11.0);
        final p2 = Path()..moveTo(12.0, 20.0)..lineTo(5.0, 20.0)..lineTo(5.0, 13.0);
        canvas.drawPath(p1, stroke..strokeWidth = 1.2);
        canvas.drawPath(p2, stroke..strokeWidth = 1.2);
        canvas.drawLine(const Offset(19.0, 11.0), const Offset(16.5, 8.5), stroke);
        canvas.drawLine(const Offset(5.0, 13.0), const Offset(7.5, 15.5), stroke);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Solid rotating circular shield with stenciled negative-space arrows
        canvas.drawCircle(const Offset(12.0, 12.0), 8.5, fill);
        final slit = Paint()..color = Colors.white..strokeWidth = 1.6..style = PaintingStyle.stroke;
        canvas.drawArc(const Rect.fromLTWH(6.5, 6.5, 11, 11), 0.5, 2.0, false, slit);
        canvas.drawArc(const Rect.fromLTWH(6.5, 6.5, 11, 11), 3.6, 2.0, false, slit);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Angular folded geometric arrows with sharp triangular heads
        final p = Path()
          ..moveTo(12.0, 4.5)..lineTo(18.0, 6.5)..lineTo(16.5, 11.0)
          ..moveTo(12.0, 19.5)..lineTo(6.0, 17.5)..lineTo(7.5, 13.0);
        canvas.drawPath(p, stroke);
        break;

      case ChatyIconThemeId.organicMotion:
        // Dual fluid liquid waves looping in smooth laminar flow
        final p = Path()
          ..moveTo(6.0, 10.0)
          ..cubicTo(7.0, 4.0, 17.0, 4.5, 18.0, 10.0)
          ..moveTo(18.0, 14.0)
          ..cubicTo(17.0, 20.0, 7.0, 19.5, 6.0, 14.0);
        canvas.drawPath(p, stroke);
        canvas.drawCircle(const Offset(18.0, 10.0), 2.0, fill);
        canvas.drawCircle(const Offset(6.0, 14.0), 2.0, fill);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit stepped pixel cycle arrows
        canvas.drawRect(const Rect.fromLTWH(8, 4, 8, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(16, 6, 2, 6), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 18, 8, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(6, 12, 2, 6), fill);
        // Pixel arrowheads
        canvas.drawRect(const Rect.fromLTWH(18, 10, 2, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(4, 12, 2, 2), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // Dual CAD rotation arcs with tangent dimension leader arrows
        canvas.drawArc(const Rect.fromLTWH(5, 5, 14, 14), 0.3, 2.3, false, stroke..strokeWidth = 1.2);
        canvas.drawArc(const Rect.fromLTWH(5, 5, 14, 14), 3.4, 2.3, false, stroke..strokeWidth = 1.2);
        canvas.drawCircle(const Offset(12.0, 12.0), 1.2, fill);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Overlapping dual-plane ribbon arrows with 3D crossover depth
        final r1 = const Rect.fromLTWH(4.5, 4.5, 15, 15);
        canvas.drawArc(r1, 0.4, 2.2, false, stroke..strokeWidth = 2.6);
        canvas.drawArc(r1, 3.5, 2.2, false, Paint()..color = color.withValues(alpha: 0.45)..style = PaintingStyle.stroke..strokeWidth = 2.6);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Dynamic two-stroke calligraphic brush swirl
        final s1 = Path()
          ..moveTo(6.0, 8.0)
          ..quadraticBezierTo(12.0, 3.5, 18.5, 8.5);
        final s2 = Path()
          ..moveTo(18.0, 16.0)
          ..quadraticBezierTo(12.0, 20.5, 5.5, 15.5);
        canvas.drawPath(s1, stroke..strokeWidth = 2.8);
        canvas.drawPath(s2, stroke..strokeWidth = 2.8);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Bidirectional transceiver arcs with propagating signal pulse packets
        canvas.drawArc(const Rect.fromLTWH(5.0, 5.0, 14, 14), 0.2, 2.5, false, stroke..strokeWidth = 1.2);
        canvas.drawArc(const Rect.fromLTWH(5.0, 5.0, 14, 14), 3.3, 2.5, false, stroke..strokeWidth = 1.2);
        canvas.drawCircle(const Offset(18.0, 14.0), 1.8, fill);
        canvas.drawCircle(const Offset(6.0, 10.0), 1.8, fill);
        break;
    }
  }

  // 6. CAMERA SHUTTER
  void _paintCameraShutter(Canvas canvas, Paint stroke, Paint fill) {
    const center = Offset(12, 12);
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Outer orbital guide circle with floating inner orbital ring & 4 aperture nodes
        canvas.drawCircle(center, 9.0, stroke);
        canvas.drawCircle(center, 5.5, Paint()..color = stroke.color.withValues(alpha: 0.35)..style = PaintingStyle.stroke..strokeWidth = 1.0);
        canvas.drawCircle(const Offset(12.0, 6.5), 1.2, fill);
        canvas.drawCircle(const Offset(12.0, 17.5), 1.2, fill);
        canvas.drawCircle(const Offset(6.5, 12.0), 1.2, fill);
        canvas.drawCircle(const Offset(17.5, 12.0), 1.2, fill);
        break;

      case ChatyIconThemeId.precisionContour:
        // Ultra-fine dual concentric drafting circles with 4 precision gap ticks
        canvas.drawCircle(center, 8.8, stroke..strokeWidth = 1.1);
        canvas.drawCircle(center, 5.2, stroke..strokeWidth = 0.9);
        final tick = Paint()..color = stroke.color..strokeWidth = 1.0;
        canvas.drawLine(const Offset(12, 2.0), const Offset(12, 4.0), tick);
        canvas.drawLine(const Offset(12, 20.0), const Offset(12, 22.0), tick);
        canvas.drawLine(const Offset(2.0, 12), const Offset(4.0, 12), tick);
        canvas.drawLine(const Offset(20.0, 12), const Offset(22.0, 12), tick);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Heavy solid puck with crisp white negative-space ring cut
        canvas.drawCircle(center, 9.2, fill);
        canvas.drawCircle(center, 5.6, Paint()..color = Colors.white..style = PaintingStyle.stroke..strokeWidth = 1.8);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // 12-sided faceted origami polygon with radiating diagonal creases
        final poly = Path();
        for (int i = 0; i < 12; i++) {
          final a = i * (math.pi / 6);
          final x = 12 + 8.5 * math.cos(a);
          final y = 12 + 8.5 * math.sin(a);
          if (i == 0) poly.moveTo(x, y); else poly.lineTo(x, y);
        }
        poly.close();
        canvas.drawPath(poly, stroke);
        canvas.drawCircle(center, 4.0, stroke);
        break;

      case ChatyIconThemeId.organicMotion:
        // Fluid liquid droplet ring with undulating wave contour
        final wave = Path();
        for (int i = 0; i < 36; i++) {
          final a = i * (math.pi / 18);
          final r = 8.5 + 0.9 * math.sin(a * 4);
          final x = 12 + r * math.cos(a);
          final y = 12 + r * math.sin(a);
          if (i == 0) wave.moveTo(x, y); else wave.lineTo(x, y);
        }
        wave.close();
        canvas.drawPath(wave, stroke);
        canvas.drawCircle(center, 3.8, fill);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Stepped 8-bit pixel ring with square center block
        canvas.drawRect(const Rect.fromLTWH(8, 3, 8, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 19, 8, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(3, 8, 2, 8), fill);
        canvas.drawRect(const Rect.fromLTWH(19, 8, 2, 8), fill);
        canvas.drawRect(const Rect.fromLTWH(5, 5, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(16, 5, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(5, 16, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(16, 16, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(10, 10, 4, 4), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // Technical CAD aperture dial with radial measurement ticks
        canvas.drawCircle(center, 8.8, stroke..strokeWidth = 1.2);
        for (int i = 0; i < 8; i++) {
          final a = i * (math.pi / 4);
          final p1 = Offset(12 + 6.5 * math.cos(a), 12 + 6.5 * math.sin(a));
          final p2 = Offset(12 + 8.8 * math.cos(a), 12 + 8.8 * math.sin(a));
          canvas.drawLine(p1, p2, stroke..strokeWidth = 0.9);
        }
        canvas.drawCircle(center, 3.5, stroke..strokeWidth = 1.0);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Multi-tier dimensional stepped relief rings
        canvas.drawCircle(center, 9.2, Paint()..color = color.withValues(alpha: 0.25)..style = PaintingStyle.fill);
        canvas.drawCircle(center, 6.8, stroke..strokeWidth = 2.2);
        canvas.drawCircle(center, 4.0, fill);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Calligraphic zen enso circle with dynamic thick-to-thin brush stroke
        final enso = Path()
          ..addArc(const Rect.fromLTWH(3.5, 3.5, 17, 17), 0.3, 5.8);
        canvas.drawPath(enso, stroke..strokeWidth = 2.8);
        canvas.drawCircle(center, 3.2, fill);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Radar beacon emitter with 3 concentric radiating sonar wave rings
        canvas.drawCircle(center, 2.2, fill);
        canvas.drawCircle(center, 5.2, stroke..strokeWidth = 1.1);
        canvas.drawCircle(center, 8.8, stroke..strokeWidth = 1.4);
        break;
    }
  }

  // 7. CALLS / AUDIO CALL
  void _paintCalls(Canvas canvas, Paint stroke, Paint fill, Paint secondary) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Curved handset with orbital signal halo
        final p = Path()
          ..moveTo(6.5, 4.5)..lineTo(9.5, 4.5)..lineTo(10.8, 8.0)..lineTo(8.8, 10.0)
          ..quadraticBezierTo(10.2, 13.8, 14.0, 15.2)..lineTo(16.0, 13.2)
          ..lineTo(19.5, 14.5)..lineTo(19.5, 17.5)..quadraticBezierTo(19.5, 19.5, 17.2, 19.5)
          ..cubicTo(10.5, 19.5, 4.5, 13.5, 4.5, 6.8)..quadraticBezierTo(4.5, 4.5, 6.5, 4.5);
        canvas.drawPath(p, stroke);
        // Orbital sound halo
        canvas.drawArc(const Rect.fromLTWH(14, 2.5, 7, 7), -1.2, 1.4, false, stroke..strokeWidth = 1.1);
        break;

      case ChatyIconThemeId.precisionContour:
        // Architectural wireframe handset with 45° chamfers
        final p = Path()
          ..moveTo(6.5, 4.0)..lineTo(9.5, 4.0)..lineTo(11.0, 7.5)..lineTo(9.0, 9.5)
          ..lineTo(14.5, 15.0)..lineTo(16.5, 13.0)..lineTo(20.0, 14.5)..lineTo(20.0, 17.5)
          ..lineTo(17.5, 20.0)..lineTo(14.0, 20.0)..lineTo(4.0, 10.0)..lineTo(4.0, 6.5)..close();
        canvas.drawPath(p, stroke..strokeWidth = 1.2);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // 100% solid filled handset silhouette with stenciled grip cut
        final p = Path()
          ..moveTo(6.5, 4.5)..lineTo(9.5, 4.5)..lineTo(10.8, 8.0)..lineTo(8.8, 10.0)
          ..quadraticBezierTo(10.2, 13.8, 14.0, 15.2)..lineTo(16.0, 13.2)
          ..lineTo(19.5, 14.5)..lineTo(19.5, 17.5)..quadraticBezierTo(19.5, 19.5, 17.2, 19.5)
          ..cubicTo(10.5, 19.5, 4.5, 13.5, 4.5, 6.8)..close();
        canvas.drawPath(p, fill);
        final grip = Paint()..color = Colors.white..strokeWidth = 1.4..style = PaintingStyle.stroke;
        canvas.drawLine(const Offset(9.5, 11.5), const Offset(12.5, 14.5), grip);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Faceted angular origami handset folded from geometric planes
        final p = Path()
          ..moveTo(7.0, 4.5)..lineTo(10.5, 4.5)..lineTo(12.0, 8.0)..lineTo(9.5, 10.5)
          ..lineTo(13.5, 14.5)..lineTo(16.0, 12.0)..lineTo(19.5, 13.5)..lineTo(19.5, 17.0)
          ..lineTo(17.0, 19.5)..lineTo(13.0, 18.0)..lineTo(6.0, 11.0)..lineTo(4.5, 7.0)..close();
        canvas.drawPath(p, stroke);
        break;

      case ChatyIconThemeId.organicMotion:
        // Smooth asymmetrical fluid kidney-bean handset
        final p = Path()
          ..moveTo(7.0, 4.5)
          ..cubicTo(10.0, 4.0, 11.0, 7.5, 9.5, 9.5)
          ..cubicTo(10.5, 13.5, 13.5, 14.5, 16.0, 13.0)
          ..cubicTo(18.0, 11.5, 20.0, 14.0, 19.5, 17.0)
          ..cubicTo(19.0, 20.0, 16.0, 20.0, 14.0, 19.0)
          ..cubicTo(8.0, 16.0, 4.5, 12.0, 4.5, 7.5)
          ..cubicTo(4.5, 5.0, 5.5, 4.5, 7.0, 4.5);
        canvas.drawPath(p, stroke);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit retro phone handset composed of pixel blocks
        canvas.drawRect(const Rect.fromLTWH(5, 4, 5, 4), fill);
        canvas.drawRect(const Rect.fromLTWH(7, 8, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(9, 11, 4, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(13, 14, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(15, 15, 5, 4), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD schematic handset with transmitter/receiver datum marks
        final p = Path()
          ..moveTo(6.5, 4.5)..lineTo(9.5, 4.5)..lineTo(10.8, 8.0)..lineTo(8.8, 10.0)
          ..quadraticBezierTo(10.2, 13.8, 14.0, 15.2)..lineTo(16.0, 13.2)
          ..lineTo(19.5, 14.5)..lineTo(19.5, 17.5)..quadraticBezierTo(19.5, 19.5, 17.2, 19.5)
          ..cubicTo(10.5, 19.5, 4.5, 13.5, 4.5, 6.8)..close();
        canvas.drawPath(p, stroke..strokeWidth = 1.2);
        canvas.drawCircle(const Offset(8.0, 6.2), 1.0, fill);
        canvas.drawCircle(const Offset(17.8, 16.0), 1.0, fill);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Embossed multi-plane handset with relief shell
        final p = Path()
          ..moveTo(6.5, 4.5)..lineTo(9.5, 4.5)..lineTo(10.8, 8.0)..lineTo(8.8, 10.0)
          ..quadraticBezierTo(10.2, 13.8, 14.0, 15.2)..lineTo(16.0, 13.2)
          ..lineTo(19.5, 14.5)..lineTo(19.5, 17.5)..quadraticBezierTo(19.5, 19.5, 17.2, 19.5)
          ..cubicTo(10.5, 19.5, 4.5, 13.5, 4.5, 6.8)..close();
        canvas.drawPath(p, fill);
        canvas.drawCircle(const Offset(8.0, 6.0), 2.0, secondary);
        canvas.drawCircle(const Offset(18.0, 16.0), 2.0, secondary);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Swift confident calligraphic brush sweep
        final p = Path()
          ..moveTo(6.0, 4.5)
          ..quadraticBezierTo(10.5, 6.0, 9.0, 9.0)
          ..quadraticBezierTo(11.0, 13.5, 14.5, 14.5)
          ..quadraticBezierTo(18.5, 13.5, 19.5, 17.5)
          ..cubicTo(18.0, 20.0, 12.0, 19.5, 5.0, 10.0)
          ..quadraticBezierTo(4.0, 5.5, 6.0, 4.5);
        canvas.drawPath(p, stroke..strokeWidth = 2.6);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Telecommunications antenna handset with 3 broadcast wave arcs
        final p = Path()
          ..moveTo(6.5, 4.5)..lineTo(9.5, 4.5)..lineTo(10.8, 8.0)..lineTo(8.8, 10.0)
          ..quadraticBezierTo(10.2, 13.8, 14.0, 15.2)..lineTo(16.0, 13.2)
          ..lineTo(19.5, 14.5)..lineTo(19.5, 17.5)..quadraticBezierTo(19.5, 19.5, 17.2, 19.5)
          ..cubicTo(10.5, 19.5, 4.5, 13.5, 4.5, 6.8)..close();
        canvas.drawPath(p, stroke);
        canvas.drawArc(const Rect.fromLTWH(13, 2, 8, 8), -1.4, 1.2, false, stroke..strokeWidth = 1.0);
        canvas.drawArc(const Rect.fromLTWH(11, 0, 12, 12), -1.4, 1.2, false, stroke..strokeWidth = 1.0);
        break;
    }
  }

  // 8. VIDEO CALL
  void _paintVideo(Canvas canvas, Paint stroke, Paint fill, Paint secondary, {required bool off}) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Sleek curved video camera with orbital lens cone
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(3.5, 6.5, 11.5, 11.0), const Radius.circular(3.0)), stroke);
        final cone = Path()..moveTo(15.0, 10.0)..lineTo(20.5, 6.8)..lineTo(20.5, 17.2)..lineTo(15.0, 14.0)..close();
        canvas.drawPath(cone, stroke);
        canvas.drawCircle(const Offset(9.2, 12.0), 2.0, fill);
        break;

      case ChatyIconThemeId.precisionContour:
        // Open-cornered rectangular wireframe camera with detached trapezoidal lens
        canvas.drawRect(const Rect.fromLTWH(3.5, 6.5, 11.0, 11.0), stroke..strokeWidth = 1.2);
        final cone = Path()..moveTo(15.5, 9.5)..lineTo(20.5, 6.5)..lineTo(20.5, 17.5)..lineTo(15.5, 14.5)..close();
        canvas.drawPath(cone, stroke..strokeWidth = 1.2);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Solid camera wedge silhouette with negative-space lens pupil
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(3.5, 6.5, 11.5, 11.0), const Radius.circular(1.5)), fill);
        final cone = Path()..moveTo(15.0, 9.5)..lineTo(20.5, 6.5)..lineTo(20.5, 17.5)..lineTo(15.0, 14.5)..close();
        canvas.drawPath(cone, fill);
        canvas.drawCircle(const Offset(9.2, 12.0), 2.2, Paint()..color = Colors.white..style = PaintingStyle.fill);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Faceted polygonal video body with sharp 60° triangular projection cone
        final b = Path()..moveTo(3.5, 7.0)..lineTo(15.0, 6.0)..lineTo(15.0, 18.0)..lineTo(3.5, 17.0)..close();
        canvas.drawPath(b, stroke);
        final c = Path()..moveTo(15.0, 9.0)..lineTo(20.5, 6.0)..lineTo(20.5, 18.0)..lineTo(15.0, 15.0)..close();
        canvas.drawPath(c, fill);
        break;

      case ChatyIconThemeId.organicMotion:
        // Fluid asymmetric teardrop camera housing with smooth bulbous lens
        final b = Path()
          ..moveTo(4.5, 7.5)
          ..cubicTo(14.5, 5.5, 15.5, 18.5, 4.5, 16.5)
          ..close();
        canvas.drawPath(b, stroke);
        final c = Path()
          ..moveTo(14.5, 10.0)
          ..quadraticBezierTo(20.5, 7.5, 20.5, 12.0)
          ..quadraticBezierTo(20.5, 16.5, 14.5, 14.0)..close();
        canvas.drawPath(c, stroke);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Stepped 8-bit pixel video camera with stepped cone
        canvas.drawRect(const Rect.fromLTWH(4, 7, 10, 10), stroke..strokeWidth = 1.8);
        canvas.drawRect(const Rect.fromLTWH(15, 10, 2, 4), fill);
        canvas.drawRect(const Rect.fromLTWH(17, 8, 2, 8), fill);
        canvas.drawRect(const Rect.fromLTWH(19, 6, 2, 12), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD video schematic with optical focal guide lines
        canvas.drawRect(const Rect.fromLTWH(3.5, 7.0, 11.0, 10.0), stroke..strokeWidth = 1.2);
        final cone = Path()..moveTo(14.5, 9.5)..lineTo(20.5, 6.5)..lineTo(20.5, 17.5)..lineTo(14.5, 14.5)..close();
        canvas.drawPath(cone, stroke..strokeWidth = 1.2);
        canvas.drawLine(const Offset(9.0, 5.0), const Offset(9.0, 19.0), Paint()..color = stroke.color.withValues(alpha: 0.5)..strokeWidth = 0.8);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Multi-tier dimensional camera housing with raised side panel
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(3.5, 6.5, 11.5, 11.0), const Radius.circular(2.0)), fill);
        final cone = Path()..moveTo(15.0, 9.5)..lineTo(20.5, 6.5)..lineTo(20.5, 17.5)..lineTo(15.0, 14.5)..close();
        canvas.drawPath(cone, secondary);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Bold gestural brush-painted camera housing
        final b = Path()..moveTo(4.0, 7.0)..lineTo(15.0, 7.0)..lineTo(15.0, 17.0)..lineTo(4.0, 17.0)..close();
        canvas.drawPath(b, stroke..strokeWidth = 2.4);
        final c = Path()..moveTo(15.0, 9.5)..lineTo(20.5, 6.5)..lineTo(20.5, 17.5)..lineTo(15.0, 14.5);
        canvas.drawPath(c, stroke..strokeWidth = 2.4);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Video broadcast transmitter camera with radiating video beams
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(3.5, 6.5, 11.0, 11.0), const Radius.circular(2.0)), stroke);
        final cone = Path()..moveTo(14.5, 9.5)..lineTo(19.0, 7.0)..lineTo(19.0, 17.0)..lineTo(14.5, 14.5)..close();
        canvas.drawPath(cone, stroke);
        canvas.drawArc(const Rect.fromLTWH(17, 8, 4, 8), -math.pi / 2, math.pi, false, stroke..strokeWidth = 1.0);
        break;
    }

    if (off) {
      canvas.drawLine(const Offset(3.5, 4.0), const Offset(20.5, 20.0), stroke..color = Colors.redAccent..strokeWidth = 2.0);
    }
  }

  // 9. SEND ACTION
  void _paintSend(Canvas canvas, Paint stroke, Paint fill) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Aerodynamic orbital glider dart with central orbital negative-space slit
        final dart = Path()
          ..moveTo(3.5, 3.5)
          ..lineTo(21.0, 12.0)
          ..lineTo(3.5, 20.5)
          ..lineTo(7.5, 12.0)
          ..close();
        canvas.drawPath(dart, stroke);
        canvas.drawLine(const Offset(7.5, 12.0), const Offset(15.0, 12.0), stroke);
        canvas.drawCircle(const Offset(15.0, 12.0), 1.5, fill);
        break;

      case ChatyIconThemeId.precisionContour:
        // Razor-sharp dual-wire dart with parallel wing contours
        final dart = Path()
          ..moveTo(3.5, 3.0)..lineTo(21.5, 12.0)..lineTo(3.5, 21.0)..lineTo(8.0, 12.0)..close();
        canvas.drawPath(dart, stroke..strokeWidth = 1.2);
        // Parallel inner drafting wire
        final inner = Path()
          ..moveTo(6.5, 6.5)..lineTo(16.5, 12.0)..lineTo(6.5, 17.5)..lineTo(9.5, 12.0)..close();
        canvas.drawPath(inner, stroke..strokeWidth = 0.8);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Solid filled heavy arrowhead silhouette with stark negative-space keel slot
        final dart = Path()
          ..moveTo(3.5, 3.5)..lineTo(21.0, 12.0)..lineTo(3.5, 20.5)..lineTo(7.5, 12.0)..close();
        canvas.drawPath(dart, fill);
        final slit = Paint()..color = Colors.white..strokeWidth = 1.8..style = PaintingStyle.stroke;
        canvas.drawLine(const Offset(6.5, 12.0), const Offset(16.5, 12.0), slit);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Classic folded origami paper airplane with sharp angular creases
        final topWing = Path()..moveTo(3.0, 3.0)..lineTo(21.0, 12.0)..lineTo(8.0, 12.0)..close();
        final btmWing = Path()..moveTo(3.0, 21.0)..lineTo(21.0, 12.0)..lineTo(8.0, 12.0)..close();
        canvas.drawPath(topWing, fill);
        canvas.drawPath(btmWing, stroke);
        canvas.drawLine(const Offset(8.0, 12.0), const Offset(21.0, 12.0), Paint()..color = Colors.white70..strokeWidth = 1.0);
        break;

      case ChatyIconThemeId.organicMotion:
        // Fluid curved manta-ray / droplet arrow gliding dynamically
        final ray = Path()
          ..moveTo(3.5, 4.5)
          ..quadraticBezierTo(10.0, 9.0, 21.0, 12.0)
          ..quadraticBezierTo(10.0, 15.0, 3.5, 19.5)
          ..quadraticBezierTo(7.5, 12.0, 3.5, 4.5);
        canvas.drawPath(ray, stroke);
        canvas.drawCircle(const Offset(11.5, 12.0), 2.2, fill);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit pixel arrow dart
        canvas.drawRect(const Rect.fromLTWH(4, 4, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(7, 6, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(10, 8, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(13, 10, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(16, 11, 4, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(13, 11, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(10, 13, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(7, 15, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(4, 17, 3, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 11, 6, 2), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // Supersonic technical vector schematic with coordinate flight axis
        final dart = Path()..moveTo(3.5, 3.0)..lineTo(21.5, 12.0)..lineTo(3.5, 21.0)..lineTo(7.5, 12.0)..close();
        canvas.drawPath(dart, stroke..strokeWidth = 1.2);
        // Guide lines
        final cad = Paint()..color = stroke.color.withValues(alpha: 0.5)..strokeWidth = 0.8;
        canvas.drawLine(const Offset(1.0, 12.0), const Offset(23.0, 12.0), cad);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Stealth-fighter faceted dart with raised central spine ridge
        final topFacet = Path()..moveTo(3.5, 4.0)..lineTo(21.0, 12.0)..lineTo(7.5, 12.0)..close();
        final btmFacet = Path()..moveTo(3.5, 20.0)..lineTo(21.0, 12.0)..lineTo(7.5, 12.0)..close();
        canvas.drawPath(topFacet, fill);
        canvas.drawPath(btmFacet, Paint()..color = color.withValues(alpha: 0.35)..style = PaintingStyle.fill);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Dynamic Japanese calligraphic brush stroke arrow
        final dart = Path()
          ..moveTo(4.0, 3.5)
          ..quadraticBezierTo(12.0, 8.5, 21.5, 12.0)
          ..quadraticBezierTo(12.0, 15.5, 4.0, 20.5)
          ..quadraticBezierTo(8.0, 12.0, 4.0, 3.5);
        canvas.drawPath(dart, stroke..strokeWidth = 2.6);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Directional telemetry beam dart with trailing signal impulse rings
        final dart = Path()..moveTo(7.0, 6.0)..lineTo(20.5, 12.0)..lineTo(7.0, 18.0)..lineTo(10.0, 12.0)..close();
        canvas.drawPath(dart, stroke..strokeWidth = 1.8);
        canvas.drawArc(const Rect.fromLTWH(2, 7, 5, 10), -math.pi / 2, math.pi, false, stroke..strokeWidth = 1.1);
        canvas.drawArc(const Rect.fromLTWH(0, 5, 5, 14), -math.pi / 2, math.pi, false, stroke..strokeWidth = 1.1);
        break;
    }
  }

  // 10. ATTACHMENT / PAPERCLIP
  void _paintAttachment(Canvas canvas, Paint stroke, Paint fill) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Continuous orbital paperclip loop with smooth concentric fillets
        final p = Path()
          ..moveTo(16.5, 8.5)
          ..lineTo(9.5, 15.5)
          ..arcToPoint(const Offset(6.5, 12.5), radius: const Radius.circular(2.1), clockwise: true)
          ..lineTo(14.0, 5.0)
          ..arcToPoint(const Offset(18.5, 9.5), radius: const Radius.circular(3.2), clockwise: true)
          ..lineTo(10.5, 17.5)
          ..arcToPoint(const Offset(6.0, 13.0), radius: const Radius.circular(4.2), clockwise: true);
        canvas.drawPath(p, stroke);
        break;

      case ChatyIconThemeId.precisionContour:
        // Open architectural paperclip with sharp 90° mitered turns
        final p = Path()
          ..moveTo(16.0, 8.0)..lineTo(10.0, 14.0)..lineTo(7.5, 11.5)..lineTo(13.5, 5.5)
          ..lineTo(18.0, 10.0)..lineTo(11.0, 17.0)..lineTo(6.0, 12.0);
        canvas.drawPath(p, stroke..strokeWidth = 1.2);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Solid capsule silhouette with stenciled white spiral slit
        canvas.save();
        canvas.translate(12, 12);
        canvas.rotate(math.pi / 4);
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(-4.5, -8.0, 9.0, 16.0), const Radius.circular(4.5)), fill);
        final slit = Paint()..color = Colors.white..strokeWidth = 1.6..style = PaintingStyle.stroke;
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(-2.0, -5.5, 4.0, 11.0), const Radius.circular(2.0)), slit);
        canvas.restore();
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Angular folded ribbon paperclip with 45°/135° diagonal corners
        final p = Path()
          ..moveTo(16.5, 7.5)..lineTo(10.5, 13.5)..lineTo(8.5, 11.5)..lineTo(13.5, 6.5)
          ..lineTo(17.5, 10.5)..lineTo(11.5, 16.5)..lineTo(6.5, 11.5);
        canvas.drawPath(p, stroke);
        break;

      case ChatyIconThemeId.organicMotion:
        // Organic looping droplet coil resembling a natural water swirl
        final p = Path()
          ..moveTo(16.0, 8.0)
          ..quadraticBezierTo(9.0, 15.0, 7.0, 13.0)
          ..quadraticBezierTo(5.0, 11.0, 12.0, 4.0)
          ..quadraticBezierTo(19.0, 7.0, 11.0, 17.0);
        canvas.drawPath(p, stroke);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Pixelated paperclip with 90° modular block turns
        canvas.drawRect(const Rect.fromLTWH(14, 6, 4, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(10, 10, 2, 6), fill);
        canvas.drawRect(const Rect.fromLTWH(6, 12, 2, 6), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 18, 6, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(14, 12, 2, 6), fill);
        canvas.drawRect(const Rect.fromLTWH(16, 8, 2, 6), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // Precision wire gauge blueprint with bend radius callouts
        final p = Path()
          ..moveTo(16.5, 8.5)..lineTo(9.5, 15.5)
          ..arcToPoint(const Offset(6.5, 12.5), radius: const Radius.circular(2.1), clockwise: true)
          ..lineTo(14.0, 5.0)
          ..arcToPoint(const Offset(18.5, 9.5), radius: const Radius.circular(3.2), clockwise: true)
          ..lineTo(10.5, 17.5);
        canvas.drawPath(p, stroke..strokeWidth = 1.2);
        canvas.drawCircle(const Offset(16.5, 8.5), 1.2, fill);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Overlapping multi-tier interlocking clip bands
        final p = Path()
          ..moveTo(16.5, 8.5)..lineTo(9.5, 15.5)
          ..arcToPoint(const Offset(6.5, 12.5), radius: const Radius.circular(2.1), clockwise: true)
          ..lineTo(14.0, 5.0);
        canvas.drawPath(p, stroke..strokeWidth = 2.8);
        final p2 = Path()
          ..arcToPoint(const Offset(18.5, 9.5), radius: const Radius.circular(3.2), clockwise: true)
          ..lineTo(10.5, 17.5);
        canvas.drawPath(p2, Paint()..color = color.withValues(alpha: 0.4)..style = PaintingStyle.stroke..strokeWidth = 2.8);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Sweeping continuous calligraphic brush ribbon loop
        final p = Path()
          ..moveTo(16.0, 8.0)
          ..cubicTo(10.0, 14.0, 6.0, 12.0, 13.0, 5.0)
          ..cubicTo(19.0, 9.0, 12.0, 18.0, 6.0, 12.0);
        canvas.drawPath(p, stroke..strokeWidth = 2.6);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Interlinked transmission frequency loops
        canvas.drawArc(const Rect.fromLTWH(6, 6, 12, 12), -0.6, 3.8, false, stroke..strokeWidth = 1.8);
        canvas.drawArc(const Rect.fromLTWH(8, 8, 8, 8), 1.2, 3.8, false, stroke..strokeWidth = 1.2);
        break;
    }
  }

  // 11. MICROPHONE
  void _paintMic(Canvas canvas, Paint stroke, Paint fill, Paint secondary, {required bool off}) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Rounded capsule mic inside orbital suspension arc
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(8.5, 4.0, 7.0, 11.0), const Radius.circular(3.5)), stroke);
        canvas.drawArc(const Rect.fromLTWH(5.5, 6.5, 13.0, 10.0), 0, math.pi, false, stroke);
        canvas.drawLine(const Offset(12.0, 16.5), const Offset(12.0, 20.0), stroke);
        canvas.drawLine(const Offset(8.0, 20.0), const Offset(16.0, 20.0), stroke);
        canvas.drawCircle(const Offset(12.0, 8.0), 1.2, fill);
        break;

      case ChatyIconThemeId.precisionContour:
        // Rectangular wireframe capsule with open bottom arc & crosshair stand
        canvas.drawRect(const Rect.fromLTWH(8.5, 4.0, 7.0, 10.0), stroke..strokeWidth = 1.2);
        canvas.drawArc(const Rect.fromLTWH(5.5, 6.5, 13.0, 9.0), 0, math.pi, false, stroke..strokeWidth = 1.2);
        canvas.drawLine(const Offset(12.0, 15.5), const Offset(12.0, 20.0), stroke..strokeWidth = 1.2);
        canvas.drawLine(const Offset(7.5, 20.0), const Offset(16.5, 20.0), stroke..strokeWidth = 1.2);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Solid capsule mic with stenciled sound grille slots
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(8.0, 3.5, 8.0, 12.0), const Radius.circular(4.0)), fill);
        final slit = Paint()..color = Colors.white..strokeWidth = 1.2..style = PaintingStyle.stroke;
        canvas.drawLine(const Offset(9.5, 7.0), const Offset(14.5, 7.0), slit);
        canvas.drawLine(const Offset(9.5, 10.0), const Offset(14.5, 10.0), slit);
        canvas.drawArc(const Rect.fromLTWH(5.0, 6.0, 14.0, 11.0), 0, math.pi, false, stroke..strokeWidth = 2.0);
        canvas.drawLine(const Offset(12.0, 17.0), const Offset(12.0, 20.5), stroke..strokeWidth = 2.0);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Hexagonal origami prism microphone
        final hex = Path()
          ..moveTo(12.0, 3.5)..lineTo(15.5, 6.0)..lineTo(15.5, 12.0)
          ..lineTo(12.0, 14.5)..lineTo(8.5, 12.0)..lineTo(8.5, 6.0)..close();
        canvas.drawPath(hex, stroke);
        canvas.drawArc(const Rect.fromLTWH(5.5, 6.5, 13.0, 9.5), 0, math.pi, false, stroke);
        canvas.drawLine(const Offset(12.0, 16.0), const Offset(12.0, 20.0), stroke);
        break;

      case ChatyIconThemeId.organicMotion:
        // Smooth fluid droplet capsule on curving stalk
        final drop = Path()
          ..moveTo(12.0, 3.5)
          ..cubicTo(16.0, 3.5, 16.5, 12.0, 12.0, 14.0)
          ..cubicTo(7.5, 12.0, 8.0, 3.5, 12.0, 3.5);
        canvas.drawPath(drop, stroke);
        canvas.drawArc(const Rect.fromLTWH(5.5, 6.0, 13.0, 10.0), 0.1, 2.9, false, stroke);
        final stalk = Path()..moveTo(12.0, 16.0)..quadraticBezierTo(13.0, 18.0, 12.0, 20.5);
        canvas.drawPath(stalk, stroke);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit stepped pixel microphone
        canvas.drawRect(const Rect.fromLTWH(9, 4, 6, 9), fill);
        canvas.drawRect(const Rect.fromLTWH(6, 8, 2, 5), fill);
        canvas.drawRect(const Rect.fromLTWH(16, 8, 2, 5), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 13, 8, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(11, 15, 2, 4), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 19, 8, 2), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD acoustic pickup schematic with reception radius ticks
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(8.5, 4.0, 7.0, 10.5), const Radius.circular(3.5)), stroke..strokeWidth = 1.2);
        canvas.drawArc(const Rect.fromLTWH(5.5, 6.5, 13.0, 9.5), 0, math.pi, false, stroke..strokeWidth = 1.0);
        canvas.drawLine(const Offset(12.0, 16.0), const Offset(12.0, 20.5), stroke..strokeWidth = 1.0);
        // Datum ticks
        canvas.drawLine(const Offset(10.0, 9.0), const Offset(14.0, 9.0), stroke..strokeWidth = 0.8);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Sculpted capsule with raised central mesh band
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(8.5, 3.5, 7.0, 11.0), const Radius.circular(3.5)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(7.5, 7.5, 9.0, 3.5), const Radius.circular(1.0)), secondary);
        canvas.drawArc(const Rect.fromLTWH(5.0, 6.0, 14.0, 10.0), 0, math.pi, false, stroke..strokeWidth = 2.2);
        canvas.drawLine(const Offset(12.0, 16.0), const Offset(12.0, 20.0), stroke..strokeWidth = 2.2);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Bold expressive brush capsule
        final p = Path()..moveTo(8.5, 4.0)..lineTo(15.5, 4.0)..lineTo(15.5, 12.0)..lineTo(8.5, 12.0)..close();
        canvas.drawPath(p, stroke..strokeWidth = 2.4);
        canvas.drawArc(const Rect.fromLTWH(5.0, 6.0, 14.0, 10.0), 0, math.pi, false, stroke..strokeWidth = 2.6);
        canvas.drawLine(const Offset(12.0, 16.0), const Offset(12.0, 20.0), stroke..strokeWidth = 2.4);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Voice broadcast transmitter with radiating audio wave arcs
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(9.0, 5.0, 6.0, 10.0), const Radius.circular(3.0)), stroke);
        canvas.drawArc(const Rect.fromLTWH(5.0, 6.0, 14.0, 9.0), 0, math.pi, false, stroke..strokeWidth = 1.2);
        canvas.drawLine(const Offset(12.0, 15.0), const Offset(12.0, 20.0), stroke);
        // Radiating audio wave arcs
        canvas.drawArc(const Rect.fromLTWH(3.0, 5.0, 4.0, 8.0), -1.2, 2.4, false, stroke..strokeWidth = 1.0);
        canvas.drawArc(const Rect.fromLTWH(17.0, 5.0, 4.0, 8.0), 1.9, 2.4, false, stroke..strokeWidth = 1.0);
        break;
    }

    if (off) {
      canvas.drawLine(const Offset(4.0, 4.0), const Offset(20.0, 20.0), stroke..color = Colors.redAccent..strokeWidth = 2.0);
    }
  }

  // 12. HEART / FAVOURITE / EMOJI
  void _paintHeart(Canvas canvas, Paint stroke, Paint fill, {required bool fillHeart}) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Orbital heart with orbital center aperture
        final p = Path()
          ..moveTo(12.0, 20.0)
          ..cubicTo(4.0, 14.5, 3.5, 8.5, 7.5, 5.5)
          ..cubicTo(10.5, 3.5, 12.0, 6.5, 12.0, 6.5)
          ..cubicTo(12.0, 6.5, 13.5, 3.5, 16.5, 5.5)
          ..cubicTo(20.5, 8.5, 20.0, 14.5, 12.0, 20.0);
        canvas.drawPath(p, fillHeart ? fill : stroke);
        canvas.drawCircle(const Offset(12.0, 10.5), 1.5, fillHeart ? (Paint()..color = Colors.white..style = PaintingStyle.fill) : stroke);
        break;

      case ChatyIconThemeId.precisionContour:
        // Geometric angular faceted wire heart
        final p = Path()
          ..moveTo(12.0, 19.5)..lineTo(4.5, 12.5)..lineTo(4.5, 7.5)..lineTo(8.5, 4.5)
          ..lineTo(12.0, 8.0)..lineTo(15.5, 4.5)..lineTo(19.5, 7.5)..lineTo(19.5, 12.5)..close();
        canvas.drawPath(p, stroke..strokeWidth = 1.2);
        break;

      case ChatyIconThemeId.monolithicCutout:
        // 100% solid filled heart silhouette with internal negative-space core slit
        final p = Path()
          ..moveTo(12.0, 20.0)
          ..cubicTo(4.0, 14.5, 3.5, 8.0, 7.5, 5.0)
          ..cubicTo(10.5, 3.0, 12.0, 6.5, 12.0, 6.5)
          ..cubicTo(12.0, 6.5, 13.5, 3.0, 16.5, 5.0)
          ..cubicTo(20.5, 8.0, 20.0, 14.5, 12.0, 20.0);
        canvas.drawPath(p, fill);
        final slit = Paint()..color = Colors.white..strokeWidth = 1.4..style = PaintingStyle.stroke;
        canvas.drawLine(const Offset(12.0, 8.5), const Offset(12.0, 14.5), slit);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Origami paper-folded heart with sharp angular bottom & diagonal crease
        final p = Path()
          ..moveTo(12.0, 20.0)..lineTo(4.0, 12.0)..lineTo(7.5, 5.0)
          ..lineTo(12.0, 8.5)..lineTo(16.5, 5.0)..lineTo(20.0, 12.0)..close();
        canvas.drawPath(p, fillHeart ? fill : stroke);
        canvas.drawLine(const Offset(12.0, 8.5), const Offset(12.0, 20.0), Paint()..color = Colors.white60..strokeWidth = 0.9);
        break;

      case ChatyIconThemeId.organicMotion:
        // Asymmetrical fluid droplet heart
        final p = Path()
          ..moveTo(12.0, 19.5)
          ..cubicTo(3.5, 14.0, 4.0, 7.0, 8.0, 5.0)
          ..cubicTo(11.0, 3.5, 12.0, 7.0, 12.0, 7.0)
          ..cubicTo(12.0, 7.0, 13.0, 4.0, 16.5, 6.0)
          ..cubicTo(21.0, 9.0, 19.5, 15.0, 12.0, 19.5);
        canvas.drawPath(p, fillHeart ? fill : stroke);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Authentic arcade 8-bit stepped pixel heart
        canvas.drawRect(const Rect.fromLTWH(6, 6, 4, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(14, 6, 4, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(4, 9, 16, 4), fill);
        canvas.drawRect(const Rect.fromLTWH(6, 13, 12, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 16, 8, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(10, 19, 4, 2), fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD parametric heart with central datum axis
        final p = Path()
          ..moveTo(12.0, 20.0)
          ..cubicTo(4.0, 14.5, 3.5, 8.0, 7.5, 5.0)
          ..cubicTo(10.5, 3.0, 12.0, 6.5, 12.0, 6.5)
          ..cubicTo(12.0, 6.5, 13.5, 3.0, 16.5, 5.0)
          ..cubicTo(20.5, 8.0, 20.0, 14.5, 12.0, 20.0);
        canvas.drawPath(p, stroke..strokeWidth = 1.2);
        canvas.drawLine(const Offset(12.0, 3.0), const Offset(12.0, 21.5), Paint()..color = stroke.color.withValues(alpha: 0.5)..strokeWidth = 0.8);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Embossed sculptural heart with split dimensional facet planes
        final leftHalf = Path()
          ..moveTo(12.0, 20.0)
          ..cubicTo(4.0, 14.5, 3.5, 8.0, 7.5, 5.0)
          ..cubicTo(10.5, 3.0, 12.0, 6.5, 12.0, 6.5)..close();
        final rightHalf = Path()
          ..moveTo(12.0, 6.5)
          ..cubicTo(12.0, 6.5, 13.5, 3.0, 16.5, 5.0)
          ..cubicTo(20.5, 8.0, 20.0, 14.5, 12.0, 20.0)..close();
        canvas.drawPath(leftHalf, fill);
        canvas.drawPath(rightHalf, Paint()..color = color.withValues(alpha: 0.4)..style = PaintingStyle.fill);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Passionate brush-stroke heart with dynamic pressure flares
        final p = Path()
          ..moveTo(12.0, 19.5)
          ..cubicTo(3.0, 13.5, 4.0, 6.5, 8.5, 5.0)
          ..quadraticBezierTo(11.0, 4.5, 12.0, 7.5)
          ..quadraticBezierTo(13.0, 4.5, 15.5, 5.0)
          ..cubicTo(20.0, 6.5, 21.0, 13.5, 12.0, 19.5);
        canvas.drawPath(p, stroke..strokeWidth = 2.6);
        break;

      case ChatyIconThemeId.signalLanguage:
        // Pulsating signal heart with concentric broadcast echo rings
        canvas.drawCircle(const Offset(12.0, 12.0), 3.0, fill);
        canvas.drawArc(const Rect.fromLTWH(5, 5, 14, 14), 0.3, 2.5, false, stroke..strokeWidth = 1.2);
        canvas.drawArc(const Rect.fromLTWH(5, 5, 14, 14), 3.4, 2.5, false, stroke..strokeWidth = 1.2);
        break;
    }
  }

  // 13. SETTINGS
  void _paintSettings(Canvas canvas, Paint stroke, Paint fill, Paint secondary) {
    const center = Offset(12, 12);
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Cog with 6 orbital rounded lobes and an open orbital hub
        final cog = Path();
        for (int i = 0; i < 6; i++) {
          final a = i * (math.pi / 3);
          final r1 = 8.8;
          final r2 = 6.8;
          final p1 = Offset(12 + r1 * math.cos(a - 0.25), 12 + r1 * math.sin(a - 0.25));
          final p2 = Offset(12 + r1 * math.cos(a + 0.25), 12 + r1 * math.sin(a + 0.25));
          final p3 = Offset(12 + r2 * math.cos(a + 0.35), 12 + r2 * math.sin(a + 0.35));
          if (i == 0) cog.moveTo(p1.dx, p1.dy); else cog.lineTo(p1.dx, p1.dy);
          cog.lineTo(p2.dx, p2.dy);
          cog.lineTo(p3.dx, p3.dy);
        }
        cog.close();
        canvas.drawPath(cog, stroke);
        canvas.drawCircle(center, 2.8, stroke);
        break;

      case ChatyIconThemeId.precisionContour:
        // Square-toothed technical gear with crosshair drafting axes
        canvas.drawCircle(center, 7.5, stroke..strokeWidth = 1.1);
        canvas.drawCircle(center, 3.0, stroke..strokeWidth = 1.0);
        for (int i = 0; i < 4; i++) {
          final a = i * (math.pi / 2);
          final x = 12 + 8.5 * math.cos(a);
          final y = 12 + 8.5 * math.sin(a);
          canvas.drawRect(Rect.fromCenter(center: Offset(x, y), width: 2.5, height: 2.5), stroke..strokeWidth = 1.0);
        }
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Solid industrial gear disk with stenciled negative-space radial spokes
        canvas.drawCircle(center, 8.8, fill);
        canvas.drawCircle(center, 3.2, Paint()..color = Colors.white..style = PaintingStyle.fill);
        final slit = Paint()..color = Colors.white..strokeWidth = 1.2..style = PaintingStyle.stroke;
        canvas.drawLine(const Offset(4, 12), const Offset(20, 12), slit);
        canvas.drawLine(const Offset(12, 4), const Offset(12, 20), slit);
        break;

      case ChatyIconThemeId.origamiGeometry:
        // 8-pointed origami faceted star wheel
        final star = Path();
        for (int i = 0; i < 8; i++) {
          final a = i * (math.pi / 4);
          final r = i.isEven ? 9.0 : 5.5;
          final x = 12 + r * math.cos(a);
          final y = 12 + r * math.sin(a);
          if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
        }
        star.close();
        canvas.drawPath(star, stroke);
        canvas.drawCircle(center, 2.8, stroke);
        break;

      case ChatyIconThemeId.organicMotion:
        // Organic anemone disk with 6 rounded petal lobes
        final petal = Path();
        for (int i = 0; i < 36; i++) {
          final a = i * (math.pi / 18);
          final r = 7.5 + 1.5 * math.sin(a * 6);
          final x = 12 + r * math.cos(a);
          final y = 12 + r * math.sin(a);
          if (i == 0) petal.moveTo(x, y); else petal.lineTo(x, y);
        }
        petal.close();
        canvas.drawPath(petal, stroke);
        canvas.drawCircle(center, 2.5, fill);
        break;

      case ChatyIconThemeId.pixelPrecision:
        // 8-bit retro cog with square pixel teeth
        canvas.drawRect(const Rect.fromLTWH(6, 6, 12, 12), fill);
        canvas.drawRect(const Rect.fromLTWH(10, 3, 4, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(10, 18, 4, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(3, 10, 3, 4), fill);
        canvas.drawRect(const Rect.fromLTWH(18, 10, 3, 4), fill);
        canvas.drawRect(const Rect.fromLTWH(9, 9, 6, 6), Paint()..color = Colors.white..style = PaintingStyle.fill);
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD gear profile with pitch circle and addendum lines
        canvas.drawCircle(center, 8.5, stroke..strokeWidth = 1.2);
        canvas.drawCircle(center, 5.5, Paint()..color = stroke.color.withValues(alpha: 0.5)..style = PaintingStyle.stroke..strokeWidth = 0.8);
        canvas.drawCircle(center, 2.5, stroke..strokeWidth = 1.0);
        canvas.drawLine(const Offset(2.0, 12.0), const Offset(22.0, 12.0), Paint()..color = stroke.color.withValues(alpha: 0.4)..strokeWidth = 0.8);
        canvas.drawLine(const Offset(12.0, 2.0), const Offset(12.0, 22.0), Paint()..color = stroke.color.withValues(alpha: 0.4)..strokeWidth = 0.8);
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Machined gear disc with recessed central hub
        canvas.drawCircle(center, 8.8, fill);
        canvas.drawCircle(center, 4.8, secondary);
        canvas.drawCircle(center, 2.5, Paint()..color = Colors.white..style = PaintingStyle.fill);
        break;

      case ChatyIconThemeId.expressiveInk:
        // Expressive calligraphic sun wheel with brush-flick teeth
        canvas.drawCircle(center, 5.0, stroke..strokeWidth = 2.4);
        for (int i = 0; i < 8; i++) {
          final a = i * (math.pi / 4);
          final p1 = Offset(12 + 6.0 * math.cos(a), 12 + 6.0 * math.sin(a));
          final p2 = Offset(12 + 9.5 * math.cos(a), 12 + 9.5 * math.sin(a));
          canvas.drawLine(p1, p2, stroke..strokeWidth = 2.0);
        }
        break;

      case ChatyIconThemeId.signalLanguage:
        // Telemetry radar array with 4 transmission dishes radiating outward
        canvas.drawCircle(center, 2.5, fill);
        canvas.drawArc(const Rect.fromLTWH(4, 4, 16, 16), 0.2, 1.2, false, stroke..strokeWidth = 1.4);
        canvas.drawArc(const Rect.fromLTWH(4, 4, 16, 16), 1.8, 1.2, false, stroke..strokeWidth = 1.4);
        canvas.drawArc(const Rect.fromLTWH(4, 4, 16, 16), 3.4, 1.2, false, stroke..strokeWidth = 1.4);
        canvas.drawArc(const Rect.fromLTWH(4, 4, 16, 16), 5.0, 1.2, false, stroke..strokeWidth = 1.4);
        break;
    }
  }

  // 14. MESSAGE STATUS (TICKS)
  void _paintTicks(Canvas canvas, Paint stroke, Paint fill, {required bool doubleTicks}) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Orbital curved checkmark with smooth terminal node
        final p = Path()..moveTo(doubleTicks ? 4.0 : 6.0, 12.0)..lineTo(9.5, 16.5)..lineTo(18.5, 7.5);
        canvas.drawPath(p, stroke);
        if (doubleTicks) {
          final p2 = Path()..moveTo(9.0, 12.0)..lineTo(13.5, 16.5)..lineTo(22.0, 8.0);
          canvas.drawPath(p2, stroke);
        }
        canvas.drawCircle(const Offset(18.5, 7.5), 1.2, fill);
        break;

      case ChatyIconThemeId.precisionContour:
        // Sharp angled hairline check with corner tick
        final p = Path()..moveTo(doubleTicks ? 4.0 : 6.0, 12.0)..lineTo(9.5, 17.0)..lineTo(18.5, 7.0);
        canvas.drawPath(p, stroke..strokeWidth = 1.2);
        if (doubleTicks) {
          final p2 = Path()..moveTo(8.5, 12.0)..lineTo(13.5, 17.0)..lineTo(21.5, 8.0);
          canvas.drawPath(p2, stroke..strokeWidth = 1.2);
        }
        break;

      case ChatyIconThemeId.monolithicCutout:
        // Bold solid heavy wedge checkmark
        final p = Path()
          ..moveTo(doubleTicks ? 4.0 : 6.0, 12.0)..lineTo(9.5, 17.5)..lineTo(18.5, 7.0)
          ..lineTo(17.0, 6.0)..lineTo(9.5, 14.5)..lineTo(5.5, 10.5)..close();
        canvas.drawPath(p, fill);
        if (doubleTicks) {
          final p2 = Path()
            ..moveTo(8.5, 12.0)..lineTo(13.5, 17.5)..lineTo(22.0, 7.5)
            ..lineTo(20.5, 6.5)..lineTo(13.5, 14.5)..lineTo(10.0, 10.5)..close();
          canvas.drawPath(p2, fill);
        }
        break;

      case ChatyIconThemeId.origamiGeometry:
        // Sharp diagonal origami check creases
        final p = Path()..moveTo(doubleTicks ? 4.0 : 6.0, 12.0)..lineTo(9.0, 17.0)..lineTo(18.0, 7.0);
        canvas.drawPath(p, stroke);
        if (doubleTicks) {
          final p2 = Path()..moveTo(9.0, 12.0)..lineTo(13.5, 17.0)..lineTo(21.5, 8.0);
          canvas.drawPath(p2, stroke);
        }
        break;

      case ChatyIconThemeId.organicMotion:
        // Smooth fluid droplet checkmark
        final p = Path()
          ..moveTo(doubleTicks ? 4.5 : 6.5, 12.0)
          ..quadraticBezierTo(8.0, 14.5, 9.5, 16.5)
          ..quadraticBezierTo(14.0, 12.0, 18.5, 7.5);
        canvas.drawPath(p, stroke);
        if (doubleTicks) {
          final p2 = Path()
            ..moveTo(9.0, 12.0)
            ..quadraticBezierTo(12.0, 14.5, 13.5, 16.5)
            ..quadraticBezierTo(17.5, 12.0, 21.5, 8.0);
          canvas.drawPath(p2, stroke);
        }
        break;

      case ChatyIconThemeId.pixelPrecision:
        // Stepped 8-bit pixel checkmark (2x2 pixel blocks)
        canvas.drawRect(Rect.fromLTWH(doubleTicks ? 4 : 6, 12, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(doubleTicks ? 6 : 8, 14, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(doubleTicks ? 8 : 10, 16, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(doubleTicks ? 10 : 12, 14, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(doubleTicks ? 12 : 14, 12, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(doubleTicks ? 14 : 16, 10, 2, 2), fill);
        canvas.drawRect(Rect.fromLTWH(doubleTicks ? 16 : 18, 8, 2, 2), fill);
        if (doubleTicks) {
          canvas.drawRect(const Rect.fromLTWH(12, 16, 2, 2), fill);
          canvas.drawRect(const Rect.fromLTWH(14, 14, 2, 2), fill);
          canvas.drawRect(const Rect.fromLTWH(16, 12, 2, 2), fill);
          canvas.drawRect(const Rect.fromLTWH(18, 10, 2, 2), fill);
          canvas.drawRect(const Rect.fromLTWH(20, 8, 2, 2), fill);
        }
        break;

      case ChatyIconThemeId.technicalBlueprint:
        // CAD drafting verification tick with blueprint end cross
        final p = Path()..moveTo(doubleTicks ? 4.0 : 6.0, 12.0)..lineTo(9.5, 17.0)..lineTo(18.5, 7.0);
        canvas.drawPath(p, stroke..strokeWidth = 1.3);
        canvas.drawLine(const Offset(17.0, 8.5), const Offset(20.0, 5.5), stroke..strokeWidth = 0.9);
        if (doubleTicks) {
          final p2 = Path()..moveTo(8.5, 12.0)..lineTo(13.5, 17.0)..lineTo(22.0, 7.5);
          canvas.drawPath(p2, stroke..strokeWidth = 1.3);
        }
        break;

      case ChatyIconThemeId.sculptedVolume:
        // Dimensional heavy chiseled checkmark
        final p = Path()..moveTo(doubleTicks ? 4.0 : 6.0, 12.0)..lineTo(9.5, 17.0)..lineTo(18.5, 7.0);
        canvas.drawPath(p, stroke..strokeWidth = 2.4);
        if (doubleTicks) {
          final p2 = Path()..moveTo(8.5, 12.0)..lineTo(13.5, 17.0)..lineTo(22.0, 7.5);
          canvas.drawPath(p2, Paint()..color = color.withValues(alpha: 0.4)..style = PaintingStyle.stroke..strokeWidth = 2.4);
        }
        break;

      case ChatyIconThemeId.expressiveInk:
        // Swift confident calligraphic brush-pen checkmark
        final p = Path()
          ..moveTo(doubleTicks ? 4.0 : 6.0, 11.0)
          ..lineTo(9.5, 17.0)
          ..quadraticBezierTo(14.0, 11.0, 19.0, 6.5);
        canvas.drawPath(p, stroke..strokeWidth = 2.6);
        if (doubleTicks) {
          final p2 = Path()
            ..moveTo(8.5, 11.0)
            ..lineTo(13.5, 17.0)
            ..quadraticBezierTo(17.5, 11.5, 22.0, 7.0);
          canvas.drawPath(p2, stroke..strokeWidth = 2.6);
        }
        break;

      case ChatyIconThemeId.signalLanguage:
        // Telemetry verified receipt pulse waves
        canvas.drawArc(Rect.fromLTWH(doubleTicks ? 4 : 6, 8, 12, 10), -0.5, 2.2, false, stroke..strokeWidth = 1.8);
        canvas.drawCircle(Offset(doubleTicks ? 14 : 16, 10), 1.6, fill);
        if (doubleTicks) {
          canvas.drawArc(const Rect.fromLTWH(8, 6, 12, 10), -0.5, 2.2, false, stroke..strokeWidth = 1.4);
        }
        break;
    }
  }

  // NAVIGATION & TAB ICONS
  void _paintChats(Canvas canvas, Paint stroke, Paint fill, Paint secondary) {
    switch (theme) {
      case ChatyIconThemeId.monolithicCutout:
        final b = Path()
          ..moveTo(4.0, 5.0)..lineTo(20.0, 5.0)..lineTo(20.0, 16.0)..lineTo(9.0, 16.0)..lineTo(5.0, 20.0)..close();
        canvas.drawPath(b, fill);
        final slit = Paint()..color = Colors.white..strokeWidth = 1.4..style = PaintingStyle.stroke;
        canvas.drawLine(const Offset(7.0, 9.0), const Offset(17.0, 9.0), slit);
        canvas.drawLine(const Offset(7.0, 12.5), const Offset(14.0, 12.5), slit);
        break;
      case ChatyIconThemeId.pixelPrecision:
        canvas.drawRect(const Rect.fromLTWH(4, 5, 16, 11), fill);
        canvas.drawRect(const Rect.fromLTWH(6, 16, 4, 3), fill);
        canvas.drawRect(const Rect.fromLTWH(4, 19, 3, 2), fill);
        canvas.drawRect(const Rect.fromLTWH(7, 8, 10, 2), Paint()..color = Colors.white..style = PaintingStyle.fill);
        canvas.drawRect(const Rect.fromLTWH(7, 12, 6, 2), Paint()..color = Colors.white..style = PaintingStyle.fill);
        break;
      default:
        final p = Path()
          ..addRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(3.5, 5.0, 17.0, 12.0), const Radius.circular(3.5)));
        p.moveTo(7.5, 17.0);
        p.lineTo(6.0, 20.0);
        p.lineTo(11.5, 17.0);
        canvas.drawPath(p, stroke);
        canvas.drawLine(const Offset(7.5, 9.5), const Offset(16.5, 9.5), stroke..strokeWidth = stroke.strokeWidth * 0.85);
        canvas.drawLine(const Offset(7.5, 13.0), const Offset(13.5, 13.0), stroke);
        break;
    }
  }

  void _paintUpdates(Canvas canvas, Paint stroke, Paint fill, Paint secondary) {
    final rect = const Rect.fromLTWH(3.8, 3.8, 16.4, 16.4);
    canvas.drawArc(rect, -0.6, 2.2, false, stroke);
    canvas.drawArc(rect, 1.9, 1.8, false, stroke);
    canvas.drawArc(rect, 4.0, 1.4, false, stroke);
    canvas.drawCircle(const Offset(12, 12), 2.2, fill);
  }

  void _paintProfile(Canvas canvas, Paint stroke, Paint fill, Paint secondary) {
    if (theme == ChatyIconThemeId.monolithicCutout) {
      canvas.drawCircle(const Offset(12, 8.0), 4.0, fill);
      final body = Path()..moveTo(4.5, 19.5)..quadraticBezierTo(4.5, 14.0, 12.0, 14.0)..quadraticBezierTo(19.5, 14.0, 19.5, 19.5)..close();
      canvas.drawPath(body, fill);
      return;
    }
    canvas.drawCircle(const Offset(12, 8.2), 3.8, stroke);
    final p = Path();
    p.moveTo(4.8, 19.5);
    p.quadraticBezierTo(4.8, 14.5, 12.0, 14.5);
    p.quadraticBezierTo(19.2, 14.5, 19.2, 19.5);
    canvas.drawPath(p, stroke);
  }

  void _paintTasks(Canvas canvas, Paint stroke, Paint fill, Paint secondary) {
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(4.5, 3.8, 15.0, 16.4), const Radius.circular(2.5)), stroke);
    canvas.drawLine(const Offset(8.0, 8.5), const Offset(16.0, 8.5), stroke);
    canvas.drawLine(const Offset(8.0, 12.0), const Offset(16.0, 12.0), stroke);
    canvas.drawLine(const Offset(8.0, 15.5), const Offset(13.0, 15.5), stroke);
  }

  void _paintFlash(Canvas canvas, Paint stroke, Paint fill, {required bool on}) {
    final p = Path()
      ..moveTo(13.0, 3.5)
      ..lineTo(6.5, 12.5)
      ..lineTo(12.0, 12.5)
      ..lineTo(11.0, 20.5)
      ..lineTo(17.5, 11.0)
      ..lineTo(12.5, 11.0)
      ..close();
    if (on) {
      canvas.drawPath(p, fill);
    } else {
      canvas.drawPath(p, stroke);
      canvas.drawLine(const Offset(4.0, 4.0), const Offset(20.0, 20.0), stroke..color = Colors.redAccent..strokeWidth = 2.0);
    }
  }

  void _paintGallery(Canvas canvas, Paint stroke, Paint fill, Paint secondary) {
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(3.5, 4.5, 17.0, 15.0), const Radius.circular(3.0)), stroke);
    canvas.drawCircle(const Offset(8.5, 9.0), 1.8, fill);
    final mountains = Path()
      ..moveTo(4.5, 17.5)
      ..lineTo(9.5, 12.5)
      ..lineTo(13.0, 15.5)
      ..lineTo(16.5, 11.5)
      ..lineTo(19.5, 15.5)
      ..lineTo(19.5, 17.5)
      ..close();
    canvas.drawPath(mountains, secondary);
    canvas.drawPath(mountains, stroke);
  }

  void _paintCallEnd(Canvas canvas, Paint stroke, Paint fill) {
    final endColor = Colors.redAccent;
    final endPaint = Paint()..color = endColor..style = PaintingStyle.fill..isAntiAlias = true;
    canvas.save();
    canvas.translate(12, 12);
    canvas.rotate(math.pi * 0.75);
    final p = Path()
      ..moveTo(-5.5, -7.5)..lineTo(-2.5, -7.5)..lineTo(-1.2, -4.0)..lineTo(-3.2, -2.0)
      ..quadraticBezierTo(-1.8, 1.8, 2.0, 3.2)..lineTo(4.0, 1.2)..lineTo(7.5, 2.5)
      ..lineTo(7.5, 5.5)..quadraticBezierTo(7.5, 7.5, 5.2, 7.5)..cubicTo(-1.5, 7.5, -7.5, 1.5, -7.5, -5.2)..close();
    canvas.drawPath(p, endPaint);
    canvas.restore();
  }

  void _paintScreenShare(Canvas canvas, Paint stroke, Paint fill, {required bool stop}) {
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(3.5, 4.5, 17.0, 12.0), const Radius.circular(2.5)), stroke);
    canvas.drawLine(const Offset(8.0, 19.5), const Offset(16.0, 19.5), stroke);
    canvas.drawLine(const Offset(12.0, 16.5), const Offset(12.0, 19.5), stroke);
    final arrow = Path()..moveTo(12.0, 8.0)..lineTo(9.0, 11.0)..lineTo(15.0, 11.0)..close();
    canvas.drawPath(arrow, fill);
    if (stop) {
      canvas.drawLine(const Offset(4.0, 4.0), const Offset(20.0, 20.0), stroke..color = Colors.redAccent..strokeWidth = 2.0);
    }
  }

  void _paintSpeaker(Canvas canvas, Paint stroke, Paint fill, {bool isSpeaker = true, bool isMute = false}) {
    final cone = Path()
      ..moveTo(4.0, 9.0)..lineTo(8.0, 9.0)..lineTo(13.0, 5.0)..lineTo(13.0, 19.0)..lineTo(8.0, 15.0)..lineTo(4.0, 15.0)..close();
    canvas.drawPath(cone, fill);
    if (!isMute) {
      canvas.drawArc(const Rect.fromLTWH(13, 8, 5, 8), -math.pi / 2, math.pi, false, stroke);
      if (isSpeaker) {
        canvas.drawArc(const Rect.fromLTWH(15, 5, 6, 14), -math.pi / 2, math.pi, false, stroke);
      }
    } else {
      canvas.drawLine(const Offset(16.0, 9.5), const Offset(21.0, 14.5), stroke..color = Colors.redAccent);
      canvas.drawLine(const Offset(21.0, 9.5), const Offset(16.0, 14.5), stroke..color = Colors.redAccent);
    }
  }

  void _paintAttachmentPlus(Canvas canvas, Paint stroke, Paint fill) {
    _paintAttachment(canvas, stroke, fill);
    canvas.drawCircle(const Offset(18.5, 18.5), 4.2, fill);
    final p = Paint()..color = Colors.white..strokeWidth = 1.3..style = PaintingStyle.stroke;
    canvas.drawLine(const Offset(16.5, 18.5), const Offset(20.5, 18.5), p);
    canvas.drawLine(const Offset(18.5, 16.5), const Offset(18.5, 20.5), p);
  }

  void _paintPin(Canvas canvas, Paint stroke, Paint fill, {required bool unpin}) {
    canvas.save();
    canvas.translate(12, 12);
    canvas.rotate(math.pi / 4);
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(-4.0, -9.0, 8.0, 4.0), const Radius.circular(1.0)), fill);
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(-2.5, -5.0, 5.0, 7.0), const Radius.circular(1.0)), fill);
    canvas.drawLine(const Offset(0, 2.0), const Offset(0, 8.0), stroke..strokeWidth = 2.0);
    canvas.restore();
    if (unpin) {
      canvas.drawLine(const Offset(4.0, 4.0), const Offset(20.0, 20.0), stroke..color = Colors.redAccent..strokeWidth = 2.0);
    }
  }

  void _paintArchive(Canvas canvas, Paint stroke, Paint secondary) {
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(3.5, 4.5, 17.0, 4.0), const Radius.circular(1.5)), stroke);
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(5.0, 8.5, 14.0, 11.5), const Radius.circular(1.5)), stroke);
    final arrow = Path()..moveTo(12.0, 16.0)..lineTo(9.5, 13.0)..lineTo(14.5, 13.0)..close();
    canvas.drawPath(arrow, stroke);
  }

  void _paintLock(Canvas canvas, Paint stroke, Paint fill, {required bool locked}) {
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(4.5, 9.5, 15.0, 11.0), const Radius.circular(3.0)), stroke);
    final shackle = Path();
    if (locked) {
      shackle.moveTo(7.5, 10.0);
      shackle.lineTo(7.5, 6.5);
      shackle.arcToPoint(const Offset(16.5, 6.5), radius: const Radius.circular(4.5));
      shackle.lineTo(16.5, 10.0);
    } else {
      shackle.moveTo(7.5, 10.0);
      shackle.lineTo(7.5, 6.5);
      shackle.arcToPoint(const Offset(16.5, 6.5), radius: const Radius.circular(4.5));
      shackle.lineTo(16.5, 4.0);
    }
    canvas.drawPath(shackle, stroke);
    canvas.drawCircle(const Offset(12.0, 14.5), 1.8, fill);
    canvas.drawLine(const Offset(12.0, 14.5), const Offset(12.0, 17.5), stroke);
  }

  void _paintDelete(Canvas canvas, Paint stroke, Paint fill) {
    canvas.drawLine(const Offset(4.0, 6.0), const Offset(20.0, 6.0), stroke);
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(9.0, 3.5, 6.0, 2.5), const Radius.circular(1.0)), stroke);
    final bin = Path()..moveTo(6.0, 6.0)..lineTo(7.2, 19.5)..lineTo(16.8, 19.5)..lineTo(18.0, 6.0);
    canvas.drawPath(bin, stroke);
    canvas.drawLine(const Offset(10.0, 9.5), const Offset(10.0, 16.0), stroke);
    canvas.drawLine(const Offset(14.0, 9.5), const Offset(14.0, 16.0), stroke);
  }

  void _paintClose(Canvas canvas, Paint stroke, Paint fill) {
    canvas.drawLine(const Offset(5.5, 5.5), const Offset(18.5, 18.5), stroke);
    canvas.drawLine(const Offset(18.5, 5.5), const Offset(5.5, 18.5), stroke);
  }

  void _paintCheck(Canvas canvas, Paint stroke) {
    final p = Path()..moveTo(5.0, 12.0)..lineTo(9.5, 16.5)..lineTo(19.0, 7.0);
    canvas.drawPath(p, stroke);
  }

  void _paintTune(Canvas canvas, Paint stroke, Paint fill) {
    canvas.drawLine(const Offset(4.0, 7.0), const Offset(20.0, 7.0), stroke);
    canvas.drawLine(const Offset(4.0, 12.0), const Offset(20.0, 12.0), stroke);
    canvas.drawLine(const Offset(4.0, 17.0), const Offset(20.0, 17.0), stroke);
    canvas.drawCircle(const Offset(9.0, 7.0), 2.4, fill);
    canvas.drawCircle(const Offset(15.0, 12.0), 2.4, fill);
    canvas.drawCircle(const Offset(11.0, 17.0), 2.4, fill);
  }

  void _paintChevronRight(Canvas canvas, Paint stroke) {
    final p = Path()..moveTo(9.5, 6.0)..lineTo(15.5, 12.0)..lineTo(9.5, 18.0);
    canvas.drawPath(p, stroke);
  }

  void _paintReply(Canvas canvas, Paint stroke) {
    final p = Path()
      ..moveTo(9.5, 7.5)..lineTo(4.0, 12.0)..lineTo(9.5, 16.5);
    canvas.drawPath(p, stroke);
    final arc = Path()..moveTo(5.0, 12.0)..quadraticBezierTo(14.0, 11.5, 19.0, 17.5);
    canvas.drawPath(arc, stroke);
  }

  void _paintForward(Canvas canvas, Paint stroke) {
    final p = Path()
      ..moveTo(14.5, 7.5)..lineTo(20.0, 12.0)..lineTo(14.5, 16.5);
    canvas.drawPath(p, stroke);
    final arc = Path()..moveTo(19.0, 12.0)..quadraticBezierTo(10.0, 11.5, 5.0, 17.5);
    canvas.drawPath(arc, stroke);
  }

  void _paintEdit(Canvas canvas, Paint stroke) {
    final p = Path()
      ..moveTo(15.5, 4.5)..lineTo(19.5, 8.5)..lineTo(9.0, 19.0)..lineTo(5.0, 19.5)..lineTo(5.5, 15.5)..close();
    canvas.drawPath(p, stroke);
    canvas.drawLine(const Offset(13.5, 6.5), const Offset(17.5, 10.5), stroke);
  }

  void _paintCopy(Canvas canvas, Paint stroke) {
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(8.0, 4.0, 12.0, 13.0), const Radius.circular(2.0)), stroke);
    final back = Path()
      ..moveTo(5.0, 7.5)..lineTo(5.0, 19.5)..lineTo(16.5, 19.5);
    canvas.drawPath(back, stroke);
  }

  void _paintShare(Canvas canvas, Paint stroke, Paint fill) {
    const n1 = Offset(17.5, 6.5);
    const n2 = Offset(6.5, 12.0);
    const n3 = Offset(17.5, 17.5);
    canvas.drawLine(n2, n1, stroke);
    canvas.drawLine(n2, n3, stroke);
    canvas.drawCircle(n1, 2.5, fill);
    canvas.drawCircle(n2, 2.5, fill);
    canvas.drawCircle(n3, 2.5, fill);
  }

  void _paintSignal(Canvas canvas, Paint stroke, Paint fill) {
    for (int i = 0; i < 4; i++) {
      final h = 4.0 + i * 3.8;
      canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(4.5 + i * 4.2, 19.0 - h, 2.8, h), const Radius.circular(1.0)), fill);
    }
  }

  void _paintClock(Canvas canvas, Paint stroke) {
    canvas.drawCircle(const Offset(12.0, 12.0), 8.5, stroke);
    canvas.drawLine(const Offset(12.0, 7.5), const Offset(12.0, 12.0), stroke);
    canvas.drawLine(const Offset(12.0, 12.0), const Offset(15.5, 12.0), stroke);
  }

  void _paintPictureInPicture(Canvas canvas, Paint stroke, Paint fill) {
    // PiP: large rect with smaller rect in corner
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(3.0, 3.0, 18.0, 18.0), const Radius.circular(2.0)), stroke);
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(14.0, 3.5, 5.5, 5.5), const Radius.circular(1.0)), fill);
  }

  void _paintAutoAwesome(Canvas canvas, Paint stroke, Paint fill) {
    // Sparkle/magic wand icon
    canvas.drawLine(const Offset(12.0, 3.0), const Offset(12.0, 6.0), stroke);
    canvas.drawLine(const Offset(12.0, 18.0), const Offset(12.0, 21.0), stroke);
    canvas.drawLine(const Offset(3.0, 12.0), const Offset(6.0, 12.0), stroke);
    canvas.drawLine(const Offset(18.0, 12.0), const Offset(21.0, 12.0), stroke);
    canvas.drawCircle(const Offset(8.0, 8.0), 1.8, fill);
    canvas.drawCircle(const Offset(16.0, 8.0), 1.8, fill);
    canvas.drawCircle(const Offset(8.0, 16.0), 1.8, fill);
    canvas.drawCircle(const Offset(16.0, 16.0), 1.8, fill);
  }

  void _paintFullscreen(Canvas canvas, Paint stroke, Paint fill) {
    // Expand arrows - corners pointing out
    final corners = [
      const Offset(5.0, 5.0), const Offset(19.0, 5.0),
      const Offset(5.0, 19.0), const Offset(19.0, 19.0),
    ];
    for (final c in corners) {
      final dx = c.dx < 12 ? 4.0 : -4.0;
      final dy = c.dy < 12 ? 4.0 : -4.0;
      canvas.drawPath(Path()..moveTo(c.dx, c.dy)..lineTo(c.dx + dx, c.dy)..lineTo(c.dx, c.dy + dy), stroke);
    }
  }

  void _paintFullscreenExit(Canvas canvas, Paint stroke, Paint fill) {
    // Contract arrows - corners pointing in
    final corners = [
      const Offset(7.0, 7.0), const Offset(17.0, 7.0),
      const Offset(7.0, 17.0), const Offset(17.0, 17.0),
    ];
    for (final c in corners) {
      final dx = c.dx < 12 ? -4.0 : 4.0;
      final dy = c.dy < 12 ? -4.0 : 4.0;
      canvas.drawPath(Path()..moveTo(c.dx, c.dy)..lineTo(c.dx + dx, c.dy)..lineTo(c.dx, c.dy + dy), stroke);
    }
  }

  void _paintOpenInNew(Canvas canvas, Paint stroke, Paint fill) {
    // Box with arrow pointing out
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(5.0, 5.0, 10.0, 10.0), const Radius.circular(2.0)), stroke);
    canvas.drawLine(const Offset(15.0, 12.0), const Offset(19.0, 12.0), stroke);
    canvas.drawLine(const Offset(15.0, 12.0), const Offset(17.0, 10.0), stroke);
    canvas.drawLine(const Offset(15.0, 12.0), const Offset(17.0, 14.0), stroke);
    canvas.drawLine(const Offset(12.0, 15.0), const Offset(12.0, 19.0), stroke);
    canvas.drawLine(const Offset(10.0, 17.0), const Offset(12.0, 19.0), stroke);
    canvas.drawLine(const Offset(14.0, 17.0), const Offset(12.0, 19.0), stroke);
  }

  void _paintClear(Canvas canvas, Paint stroke) {
    // X icon
    canvas.drawLine(const Offset(6.0, 6.0), const Offset(18.0, 18.0), stroke);
    canvas.drawLine(const Offset(18.0, 6.0), const Offset(6.0, 18.0), stroke);
  }

  void _paintAddTask(Canvas canvas, Paint stroke, Paint fill) {
    // Plus with clipboard/list
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(4.0, 4.0, 16.0, 16.0), const Radius.circular(2.0)), stroke);
    canvas.drawLine(const Offset(8.0, 12.0), const Offset(16.0, 12.0), stroke);
    canvas.drawLine(const Offset(12.0, 8.0), const Offset(12.0, 16.0), stroke);
  }

  void _paintEditOutline(Canvas canvas, Paint stroke, Paint fill) {
    // Pencil outline
    final p = Path()
      ..moveTo(16.5, 4.5)..lineTo(19.5, 7.5)..lineTo(8.5, 18.5)..lineTo(4.5, 19.0)..lineTo(5.0, 15.0)..close();
    canvas.drawPath(p, stroke);
  }

  void _paintVideoCam(Canvas canvas, Paint stroke, Paint fill) {
    // Video camera icon
    final p = Path()
      ..moveTo(5.0, 5.0)
      ..lineTo(18.0, 9.0)
      ..lineTo(5.0, 13.0)
      ..close();
    canvas.drawPath(p, fill);
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(5.0, 5.0, 11.0, 8.0), const Radius.circular(1.0)), stroke);
  }

  void _paintVoiceCall(Canvas canvas, Paint stroke, Paint fill) {
    // Phone handset icon
    final p = Path()
      ..moveTo(6.0, 5.0)
      ..quadraticBezierTo(14.0, 5.0, 14.0, 12.0)
      ..lineTo(17.0, 12.0)
      ..quadraticBezierTo(17.0, 5.0, 22.0, 5.0)
      ..lineTo(22.0, 19.0)
      ..quadraticBezierTo(17.0, 19.0, 17.0, 12.0)
      ..lineTo(14.0, 12.0)
      ..quadraticBezierTo(6.0, 12.0, 6.0, 5.0)
      ..close();
    canvas.drawPath(p, stroke);
  }

  void _paintLogout(Canvas canvas, Paint stroke, Paint fill) {
    // Logout: arrow pointing right from a door-like shape
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(5.0, 5.0, 12.0, 14.0), const Radius.circular(2.0)), stroke);
    canvas.drawLine(const Offset(10.0, 12.0), const Offset(18.0, 12.0), stroke);
    canvas.drawLine(const Offset(15.0, 9.0), const Offset(18.0, 12.0), stroke);
    canvas.drawLine(const Offset(15.0, 15.0), const Offset(18.0, 12.0), stroke);
  }

  void _paintDeleteOutline(Canvas canvas, Paint stroke, Paint fill) {
    // Delete outline: trash can outline
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(5.0, 4.0, 14.0, 4.0), const Radius.circular(1.0)), stroke);
    canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(6.0, 8.0, 12.0, 12.0), const Radius.circular(1.0)), stroke);
    canvas.drawLine(const Offset(8.0, 12.0), const Offset(8.0, 17.0), stroke);
    canvas.drawLine(const Offset(12.0, 12.0), const Offset(12.0, 17.0), stroke);
    canvas.drawLine(const Offset(16.0, 12.0), const Offset(16.0, 17.0), stroke);
  }

  void _paintSchedule(Canvas canvas, Paint stroke) {
    // Clock outline
    canvas.drawCircle(const Offset(12.0, 12.0), 9.0, stroke);
    canvas.drawLine(const Offset(12.0, 6.0), const Offset(12.0, 12.0), stroke);
    canvas.drawLine(const Offset(12.0, 12.0), const Offset(15.0, 14.0), stroke);
  }

  void _paintScheduleSend(Canvas canvas, Paint stroke, Paint fill) {
    // Clock with arrow
    canvas.drawCircle(const Offset(12.0, 12.0), 8.0, stroke);
    canvas.drawLine(const Offset(12.0, 7.0), const Offset(12.0, 11.0), stroke);
    canvas.drawLine(const Offset(12.0, 11.0), const Offset(14.0, 13.0), stroke);
    canvas.drawLine(const Offset(12.0, 16.0), const Offset(16.0, 12.0), stroke);
    canvas.drawLine(const Offset(14.0, 10.0), const Offset(16.0, 12.0), stroke);
    canvas.drawLine(const Offset(14.0, 14.0), const Offset(16.0, 12.0), stroke);
  }

  void _paintSync(Canvas canvas, Paint stroke) {
    // Sync/circular arrows
    canvas.drawArc(Rect.fromCenter(center: const Offset(12.0, 12.0), width: 16.0, height: 16.0), 0.5, 5.0, false, stroke);
    canvas.drawLine(const Offset(20.0, 12.0), const Offset(17.0, 9.0), stroke);
    canvas.drawLine(const Offset(20.0, 12.0), const Offset(17.0, 15.0), stroke);
    canvas.drawArc(Rect.fromCenter(center: const Offset(12.0, 12.0), width: 16.0, height: 16.0), 3.5, 5.0, false, stroke);
    canvas.drawLine(const Offset(4.0, 12.0), const Offset(7.0, 9.0), stroke);
    canvas.drawLine(const Offset(4.0, 12.0), const Offset(7.0, 15.0), stroke);
  }

  void _paintCheckSingle(Canvas canvas, Paint stroke) {
    final p = Path()..moveTo(6.0, 12.0)..lineTo(10.0, 16.0)..lineTo(18.0, 8.0);
    canvas.drawPath(p, stroke);
  }

  void _paintCheckDouble(Canvas canvas, Paint stroke) {
    // Two overlapping checks
    final p1 = Path()..moveTo(4.0, 12.0)..lineTo(8.0, 16.0)..lineTo(16.0, 8.0);
    final p2 = Path()..moveTo(8.0, 12.0)..lineTo(12.0, 16.0)..lineTo(20.0, 8.0);
    canvas.drawPath(p1, stroke);
    canvas.drawPath(p2, stroke);
  }

  void _paintErrorOutline(Canvas canvas, Paint stroke, Paint fill) {
    // Error: exclamation in circle
    canvas.drawCircle(const Offset(12.0, 12.0), 9.0, stroke);
    canvas.drawLine(const Offset(12.0, 6.0), const Offset(12.0, 15.0), stroke);
    canvas.drawCircle(const Offset(12.0, 17.0), 1.5, fill);
  }

  void _paintPause(Canvas canvas, Paint stroke, Paint fill) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Orbital dual bars with smooth rounded caps
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(5.5, 5.5, 4.5, 13.0), const Radius.circular(2.25)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(14.0, 5.5, 4.5, 13.0), const Radius.circular(2.25)), fill);
        break;
      case ChatyIconThemeId.precisionContour:
        // Hairline dual bars with sharp corners
        canvas.drawRect(const Rect.fromLTWH(6.0, 5.0, 4.0, 14.0), stroke..strokeWidth = 1.1);
        canvas.drawRect(const Rect.fromLTWH(14.0, 5.0, 4.0, 14.0), stroke..strokeWidth = 1.1);
        break;
      case ChatyIconThemeId.monolithicCutout:
        // Solid monolith blocks with stenciled gap
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(5.5, 5.0, 4.5, 14.0), const Radius.circular(1.0)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(14.0, 5.0, 4.5, 14.0), const Radius.circular(1.0)), fill);
        break;
      case ChatyIconThemeId.origamiGeometry:
        // Faceted angular pause bars
        final p1 = Path()..moveTo(5.5, 5.0)..lineTo(9.5, 5.0)..lineTo(10.5, 6.0)..lineTo(6.5, 18.0)..lineTo(5.5, 19.0)..close();
        final p2 = Path()..moveTo(14.5, 5.0)..lineTo(18.5, 5.0)..lineTo(17.5, 6.0)..lineTo(13.5, 18.0)..lineTo(14.5, 19.0)..close();
        canvas.drawPath(p1, fill);
        canvas.drawPath(p2, fill);
        break;
      case ChatyIconThemeId.organicMotion:
        // Fluid organic pause bars with natural curves
        final p1o = Path()..moveTo(5.5, 5.5)..quadraticBezierTo(6.0, 12.0, 5.5, 18.5)..quadraticBezierTo(6.0, 19.5, 9.5, 18.5)..quadraticBezierTo(10.0, 12.0, 9.5, 5.5)..close();
        final p2o = Path()..moveTo(14.5, 5.5)..quadraticBezierTo(15.0, 12.0, 14.5, 18.5)..quadraticBezierTo(15.0, 19.5, 18.5, 18.5)..quadraticBezierTo(18.0, 12.0, 18.5, 5.5)..close();
        canvas.drawPath(p1o, fill);
        canvas.drawPath(p2o, fill);
        break;
      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit stepped pixel bars
        canvas.drawRect(const Rect.fromLTWH(6, 5, 4, 14), fill);
        canvas.drawRect(const Rect.fromLTWH(14, 5, 4, 14), fill);
        break;
      case ChatyIconThemeId.technicalBlueprint:
        // CAD dimension bars with datum lines
        canvas.drawRect(const Rect.fromLTWH(5.5, 5.0, 4.5, 14.0), stroke..strokeWidth = 1.2);
        canvas.drawRect(const Rect.fromLTWH(14.0, 5.0, 4.5, 14.0), stroke..strokeWidth = 1.2);
        canvas.drawLine(const Offset(3.5, 12.0), const Offset(20.5, 12.0), Paint()..color = stroke.color.withValues(alpha: 0.4)..strokeWidth = 0.8);
        break;
      case ChatyIconThemeId.sculptedVolume:
        // Embossed dimensional bars with light/shadow planes
        final left = Path()..moveTo(5.5, 5.0)..lineTo(10.0, 5.0)..lineTo(10.0, 19.0)..lineTo(5.5, 19.0)..close();
        final right = Path()..moveTo(14.0, 5.0)..lineTo(18.5, 5.0)..lineTo(18.5, 19.0)..lineTo(14.0, 19.0)..close();
        canvas.drawPath(left, fill);
        canvas.drawPath(right, Paint()..color = color.withValues(alpha: 0.4)..style = PaintingStyle.fill);
        break;
      case ChatyIconThemeId.expressiveInk:
        // Calligraphic brush-stroke pause bars
        canvas.drawRect(const Rect.fromLTWH(5.5, 5.0, 4.5, 14.0), stroke..strokeWidth = 2.4);
        canvas.drawRect(const Rect.fromLTWH(14.0, 5.0, 4.5, 14.0), stroke..strokeWidth = 2.4);
        break;
      case ChatyIconThemeId.signalLanguage:
        // Telemetry pause signal with pulse indicators
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(5.5, 6.0, 4.5, 12.0), const Radius.circular(2.0)), fill);
        canvas.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTWH(14.0, 6.0, 4.5, 12.0), const Radius.circular(2.0)), fill);
        canvas.drawCircle(const Offset(12.0, 12.0), 1.5, fill);
        break;
    }
  }

  void _paintPlayArrow(Canvas canvas, Paint stroke, Paint fill) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Orbital play triangle with smooth vertices
        final p = Path()..moveTo(6.5, 5.5)..lineTo(19.5, 12.0)..lineTo(6.5, 18.5)..close();
        canvas.drawPath(p, fill);
        break;
      case ChatyIconThemeId.precisionContour:
        // Hairline play triangle
        final p2 = Path()..moveTo(7.0, 5.0)..lineTo(19.0, 12.0)..lineTo(7.0, 19.0)..close();
        canvas.drawPath(p2, stroke..strokeWidth = 1.2);
        break;
      case ChatyIconThemeId.monolithicCutout:
        // Solid play triangle
        final p3 = Path()..moveTo(6.0, 5.0)..lineTo(20.0, 12.0)..lineTo(6.0, 19.0)..close();
        canvas.drawPath(p3, fill);
        break;
      case ChatyIconThemeId.origamiGeometry:
        // Faceted origami play triangle
        final p4 = Path()..moveTo(6.5, 5.5)..lineTo(19.5, 12.0)..lineTo(6.5, 18.5)..close();
        canvas.drawPath(p4, stroke);
        break;
      case ChatyIconThemeId.organicMotion:
        // Fluid play triangle with curved edges
        final p5 = Path()
          ..moveTo(7.0, 5.5)
          ..quadraticBezierTo(15.0, 10.0, 19.0, 12.0)
          ..quadraticBezierTo(15.0, 14.0, 7.0, 18.5)
          ..close();
        canvas.drawPath(p5, fill);
        break;
      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit stepped pixel play triangle
        canvas.drawRect(const Rect.fromLTWH(7, 5, 1, 14), fill);
        canvas.drawRect(const Rect.fromLTWH(8, 6, 2, 12), fill);
        canvas.drawRect(const Rect.fromLTWH(10, 7, 3, 10), fill);
        canvas.drawRect(const Rect.fromLTWH(13, 8, 3, 8), fill);
        canvas.drawRect(const Rect.fromLTWH(16, 9, 2, 6), fill);
        canvas.drawRect(const Rect.fromLTWH(18, 10, 1, 4), fill);
        break;
      case ChatyIconThemeId.technicalBlueprint:
        // CAD play triangle with construction lines
        final p6 = Path()..moveTo(6.5, 5.5)..lineTo(19.5, 12.0)..lineTo(6.5, 18.5)..close();
        canvas.drawPath(p6, stroke..strokeWidth = 1.2);
        canvas.drawLine(const Offset(12.0, 3.0), const Offset(12.0, 21.0), Paint()..color = stroke.color.withValues(alpha: 0.4)..strokeWidth = 0.8);
        break;
      case ChatyIconThemeId.sculptedVolume:
        // Dimensional play triangle with dual-plane depth
        final leftPlay = Path()..moveTo(6.5, 5.5)..lineTo(12.5, 12.0)..lineTo(6.5, 18.5)..close();
        final rightPlay = Path()..moveTo(12.5, 12.0)..lineTo(19.5, 12.0)..lineTo(12.5, 12.0)..close(); // degenerate for shadow
        canvas.drawPath(leftPlay, fill);
        canvas.drawPath(rightPlay, Paint()..color = color.withValues(alpha: 0.35)..style = PaintingStyle.fill);
        break;
      case ChatyIconThemeId.expressiveInk:
        // Expressive brush-stroke play triangle
        final p7 = Path()
          ..moveTo(6.5, 5.5)
          ..quadraticBezierTo(11.0, 8.0, 19.5, 12.0)
          ..quadraticBezierTo(11.0, 16.0, 6.5, 18.5)
          ..close();
        canvas.drawPath(p7, stroke..strokeWidth = 2.6);
        break;
      case ChatyIconThemeId.signalLanguage:
        // Telemetry play signal with broadcast pulse
        final p8 = Path()..moveTo(7.0, 5.0)..lineTo(19.0, 12.0)..lineTo(7.0, 19.0)..close();
        canvas.drawPath(p8, fill);
        canvas.drawArc(const Rect.fromLTWH(19, 8, 4, 8), -math.pi / 2, math.pi, false, stroke..strokeWidth = 1.2);
        break;
    }
  }

  void _paintMusicNote(Canvas canvas, Paint stroke, Paint fill) {
    switch (theme) {
      case ChatyIconThemeId.chatySigil:
        // Orbital music note with smooth curves
        final path = Path()
          ..moveTo(6.0, 19.0)
          ..lineTo(6.0, 7.0)
          ..quadraticBezierTo(6.0, 4.5, 8.5, 4.5)
          ..quadraticBezierTo(11.0, 4.5, 11.0, 7.0)
          ..lineTo(11.0, 10.5)
          ..quadraticBezierTo(11.0, 13.0, 13.5, 13.0)
          ..quadraticBezierTo(16.0, 13.0, 16.0, 10.5)
          ..lineTo(16.0, 7.0)
          ..quadraticBezierTo(16.0, 4.5, 18.5, 4.5)
          ..quadraticBezierTo(21.0, 4.5, 21.0, 7.0)
          ..lineTo(21.0, 19.0)
          ..close();
        canvas.drawPath(path, fill);
        break;
      case ChatyIconThemeId.precisionContour:
        // Hairline music note
        final path2 = Path()
          ..moveTo(6.0, 19.0)
          ..lineTo(6.0, 7.0)
          ..quadraticBezierTo(6.0, 4.5, 8.5, 4.5)
          ..quadraticBezierTo(11.0, 4.5, 11.0, 7.0)
          ..lineTo(11.0, 10.5)
          ..quadraticBezierTo(11.0, 13.0, 13.5, 13.0)
          ..quadraticBezierTo(16.0, 13.0, 16.0, 10.5)
          ..lineTo(16.0, 7.0)
          ..quadraticBezierTo(16.0, 4.5, 18.5, 4.5)
          ..quadraticBezierTo(21.0, 4.5, 21.0, 7.0)
          ..lineTo(21.0, 19.0)
          ..close();
        canvas.drawPath(path2, stroke..strokeWidth = 1.2);
        break;
      case ChatyIconThemeId.monolithicCutout:
        // Solid music note
        final path3 = Path()
          ..moveTo(6.0, 19.0)
          ..lineTo(6.0, 7.0)
          ..quadraticBezierTo(6.0, 4.5, 8.5, 4.5)
          ..quadraticBezierTo(11.0, 4.5, 11.0, 7.0)
          ..lineTo(11.0, 10.5)
          ..quadraticBezierTo(11.0, 13.0, 13.5, 13.0)
          ..quadraticBezierTo(16.0, 13.0, 16.0, 10.5)
          ..lineTo(16.0, 7.0)
          ..quadraticBezierTo(16.0, 4.5, 18.5, 4.5)
          ..quadraticBezierTo(21.0, 4.5, 21.0, 7.0)
          ..lineTo(21.0, 19.0)
          ..close();
        canvas.drawPath(path3, fill);
        break;
      case ChatyIconThemeId.origamiGeometry:
        // Faceted origami music note
        final path4 = Path()
          ..moveTo(6.0, 19.0)
          ..lineTo(6.0, 7.0)
          ..quadraticBezierTo(6.0, 4.5, 8.5, 4.5)
          ..quadraticBezierTo(11.0, 4.5, 11.0, 7.0)
          ..lineTo(11.0, 10.5)
          ..quadraticBezierTo(11.0, 13.0, 13.5, 13.0)
          ..quadraticBezierTo(16.0, 13.0, 16.0, 10.5)
          ..lineTo(16.0, 7.0)
          ..quadraticBezierTo(16.0, 4.5, 18.5, 4.5)
          ..quadraticBezierTo(21.0, 4.5, 21.0, 7.0)
          ..lineTo(21.0, 19.0)
          ..close();
        canvas.drawPath(path4, stroke);
        break;
      case ChatyIconThemeId.organicMotion:
        // Fluid music note with curved edges
        final path5 = Path()
          ..moveTo(6.0, 19.0)
          ..lineTo(6.0, 7.5)
          ..quadraticBezierTo(6.0, 5.0, 8.5, 5.0)
          ..quadraticBezierTo(11.0, 5.0, 11.0, 7.5)
          ..lineTo(11.0, 10.5)
          ..quadraticBezierTo(11.0, 13.5, 13.5, 13.5)
          ..quadraticBezierTo(16.0, 13.5, 16.0, 10.5)
          ..lineTo(16.0, 7.5)
          ..quadraticBezierTo(16.0, 5.0, 18.5, 5.0)
          ..quadraticBezierTo(21.0, 5.0, 21.0, 7.5)
          ..lineTo(21.0, 19.0)
          ..close();
        canvas.drawPath(path5, fill);
        break;
      case ChatyIconThemeId.pixelPrecision:
        // Authentic 8-bit stepped pixel music note
        canvas.drawRect(const Rect.fromLTWH(5, 7, 2, 12), fill);
        canvas.drawRect(const Rect.fromLTWH(7, 4, 4, 4), fill);
        canvas.drawRect(const Rect.fromLTWH(11, 7, 2, 4), fill);
        canvas.drawRect(const Rect.fromLTWH(13, 7, 4, 4), fill);
        canvas.drawRect(const Rect.fromLTWH(17, 4, 4, 4), fill);
        canvas.drawRect(const Rect.fromLTWH(19, 7, 2, 12), fill);
        break;
      case ChatyIconThemeId.technicalBlueprint:
        // CAD music note with construction lines
        final path6 = Path()
          ..moveTo(6.0, 19.0)
          ..lineTo(6.0, 7.0)
          ..quadraticBezierTo(6.0, 4.5, 8.5, 4.5)
          ..quadraticBezierTo(11.0, 4.5, 11.0, 7.0)
          ..lineTo(11.0, 10.5)
          ..quadraticBezierTo(11.0, 13.0, 13.5, 13.0)
          ..quadraticBezierTo(16.0, 13.0, 16.0, 10.5)
          ..lineTo(16.0, 7.0)
          ..quadraticBezierTo(16.0, 4.5, 18.5, 4.5)
          ..quadraticBezierTo(21.0, 4.5, 21.0, 7.0)
          ..lineTo(21.0, 19.0)
          ..close();
        canvas.drawPath(path6, stroke..strokeWidth = 1.2);
        canvas.drawLine(const Offset(12.0, 3.0), const Offset(12.0, 21.0), Paint()..color = stroke.color.withValues(alpha: 0.4)..strokeWidth = 0.8);
        break;
      case ChatyIconThemeId.sculptedVolume:
        // Dimensional music note with dual-plane depth
        final leftNote = Path()
          ..moveTo(6.0, 19.0)
          ..lineTo(6.0, 7.0)
          ..quadraticBezierTo(6.0, 4.5, 8.5, 4.5)
          ..quadraticBezierTo(11.0, 4.5, 11.0, 7.0)
          ..lineTo(11.0, 10.5)
          ..quadraticBezierTo(11.0, 13.0, 13.5, 13.0)
          ..quadraticBezierTo(16.0, 13.0, 16.0, 10.5)
          ..lineTo(16.0, 7.0)
          ..quadraticBezierTo(16.0, 4.5, 18.5, 4.5)
          ..quadraticBezierTo(21.0, 4.5, 21.0, 7.0)
          ..lineTo(21.0, 19.0)
          ..close();
        canvas.drawPath(leftNote, fill);
        canvas.drawPath(leftNote, Paint()..color = color.withValues(alpha: 0.35)..style = PaintingStyle.fill);
        break;
      case ChatyIconThemeId.expressiveInk:
        // Expressive brush-stroke music note
        final path7 = Path()
          ..moveTo(6.0, 19.0)
          ..lineTo(6.0, 7.0)
          ..quadraticBezierTo(6.0, 4.5, 8.5, 4.5)
          ..quadraticBezierTo(11.0, 4.5, 11.0, 7.0)
          ..lineTo(11.0, 10.5)
          ..quadraticBezierTo(11.0, 13.0, 13.5, 13.0)
          ..quadraticBezierTo(16.0, 13.0, 16.0, 10.5)
          ..lineTo(16.0, 7.0)
          ..quadraticBezierTo(16.0, 4.5, 18.5, 4.5)
          ..quadraticBezierTo(21.0, 4.5, 21.0, 7.0)
          ..lineTo(21.0, 19.0)
          ..close();
        canvas.drawPath(path7, stroke..strokeWidth = 2.6);
        break;
      case ChatyIconThemeId.signalLanguage:
        // Telemetry music signal
        final path8 = Path()
          ..moveTo(6.0, 19.0)
          ..lineTo(6.0, 7.0)
          ..quadraticBezierTo(6.0, 4.5, 8.5, 4.5)
          ..quadraticBezierTo(11.0, 4.5, 11.0, 7.0)
          ..lineTo(11.0, 10.5)
          ..quadraticBezierTo(11.0, 13.0, 13.5, 13.0)
          ..quadraticBezierTo(16.0, 13.0, 16.0, 10.5)
          ..lineTo(16.0, 7.0)
          ..quadraticBezierTo(16.0, 4.5, 18.5, 4.5)
          ..quadraticBezierTo(21.0, 4.5, 21.0, 7.0)
          ..lineTo(21.0, 19.0)
          ..close();
        canvas.drawPath(path8, fill);
        canvas.drawArc(const Rect.fromLTWH(19, 8, 4, 8), -math.pi / 2, math.pi, false, stroke..strokeWidth = 1.2);
        break;
    }
  }

  @override
  bool shouldRepaint(covariant _ChatyIconVectorPainter oldDelegate) {
    return oldDelegate.icon != icon ||
        oldDelegate.theme != theme ||
        oldDelegate.color != color;
  }
}
