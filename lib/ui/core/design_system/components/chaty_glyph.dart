import 'package:flutter/material.dart';

/// Chaty's original glyph inventory. Each definition below is SVG markup owned
/// by this project and rendered by the tiny path/shape renderer in this file.
/// This avoids icon fonts, external icon packages, network assets, and startup
/// asset loading. Controls that use these glyphs should still supply their own
/// tooltip/semantic label.
enum ChatyGlyph {
  camera,
  qrScan,
  devices,
  palette,
  templates,
  homeLayout,
  navigation,
  settings,
  chevronRight,
  more,
  tune,
  search,
  close,
  exportProfile,
  importProfile,
  reset,
  chatBubble,
  person,
  list,
  updates,
  calls,
  groups,
  tasks,
}

const Map<ChatyGlyph, String> _chatyGlyphSvg = <ChatyGlyph, String>{
  ChatyGlyph.camera: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <rect x="3.5" y="7.5" width="17" height="12" rx="2.6" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 8 7.5 L 9.7 4.8 L 14.5 4.8 L 16.2 7.5" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <circle cx="12" cy="13.5" r="3.1" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <circle cx="17.6" cy="10" r=".8" fill="currentColor" stroke="none"/>
</svg>''',
  ChatyGlyph.qrScan: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <path d="M 9 4 L 4 4 L 4 9 M 15 4 L 20 4 L 20 9 M 20 15 L 20 20 L 15 20 M 9 20 L 4 20 L 4 15" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <rect x="7" y="7" width="4" height="4" fill="none" stroke="currentColor" stroke-width="1.6"/>
  <rect x="13" y="7" width="4" height="4" fill="none" stroke="currentColor" stroke-width="1.6"/>
  <rect x="7" y="13" width="4" height="4" fill="none" stroke="currentColor" stroke-width="1.6"/>
  <path d="M 13 13 L 15 13 L 15 15 L 13 15 Z M 17 16 L 18.8 16 L 18.8 18.8 L 16 18.8 L 16 17" fill="currentColor" stroke="none"/>
</svg>''',
  ChatyGlyph.devices: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <rect x="7" y="2.7" width="10.2" height="18.6" rx="2.1" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 9.5 5.5 L 14.7 5.5" fill="none" stroke="currentColor" stroke-width="1.5"/>
  <circle cx="12.1" cy="18.5" r=".75" fill="currentColor" stroke="none"/>
  <rect x="2.4" y="8" width="3.8" height="8.5" rx="1" fill="none" stroke="currentColor" stroke-width="1.6"/>
  <path d="M 3.5 10 L 5.1 10" fill="none" stroke="currentColor" stroke-width="1.4"/>
</svg>''',
  ChatyGlyph.palette: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <path d="M 12 3.2 C 6.9 3.2 3.1 6.8 3.1 11.4 C 3.1 16.3 7.1 20.8 12.1 20.8 C 13.8 20.8 14.9 19.9 14.9 18.5 C 14.9 17.5 14.3 16.9 14.3 16 C 14.3 14.8 15.2 14 16.5 14 L 18.4 14 C 20.1 14 20.9 12.6 20.9 10.8 C 20.9 6.3 16.8 3.2 12 3.2 Z" fill="none" stroke="currentColor" stroke-width="1.7"/>
  <circle cx="7.5" cy="10" r="1.05" fill="currentColor" stroke="none"/>
  <circle cx="11.5" cy="7.4" r="1.05" fill="currentColor" stroke="none"/>
  <circle cx="16" cy="8.3" r="1.05" fill="currentColor" stroke="none"/>
  <circle cx="8.8" cy="14.2" r="1.05" fill="currentColor" stroke="none"/>
</svg>''',
  ChatyGlyph.templates: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <rect x="3.2" y="3.2" width="17.6" height="17.6" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 3.8 9 L 20.2 9 M 9 9.5 L 9 20.2 M 14.8 9.5 L 14.8 20.2 M 5.5 6.1 L 7 6.1 M 10.8 6.1 L 12.3 6.1" fill="none" stroke="currentColor" stroke-width="1.6"/>
</svg>''',
  ChatyGlyph.homeLayout: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <rect x="3.1" y="3.5" width="17.8" height="17" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 8.6 3.8 L 8.6 20.2 M 9.2 9 L 20.4 9 M 9.2 14 L 20.4 14" fill="none" stroke="currentColor" stroke-width="1.6"/>
  <circle cx="5.9" cy="7" r=".75" fill="currentColor" stroke="none"/>
</svg>''',
  ChatyGlyph.navigation: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <circle cx="5" cy="6" r="1.1" fill="currentColor" stroke="none"/>
  <circle cx="5" cy="12" r="1.1" fill="currentColor" stroke="none"/>
  <circle cx="5" cy="18" r="1.1" fill="currentColor" stroke="none"/>
  <path d="M 9 6 L 20 6 M 9 12 L 20 12 M 9 18 L 20 18" fill="none" stroke="currentColor" stroke-width="1.8"/>
</svg>''',
  ChatyGlyph.settings: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <path d="M 4 7 L 20 7 M 4 12 L 20 12 M 4 17 L 20 17" fill="none" stroke="currentColor" stroke-width="1.7"/>
  <circle cx="9" cy="7" r="2" fill="currentColor" stroke="none"/>
  <circle cx="15" cy="12" r="2" fill="currentColor" stroke="none"/>
  <circle cx="8" cy="17" r="2" fill="currentColor" stroke="none"/>
  <circle cx="9" cy="7" r=".7" fill="#000000" fill-opacity=".2" stroke="none"/>
  <circle cx="15" cy="12" r=".7" fill="#000000" fill-opacity=".2" stroke="none"/>
  <circle cx="8" cy="17" r=".7" fill="#000000" fill-opacity=".2" stroke="none"/>
</svg>''',
  ChatyGlyph.chevronRight: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <path d="M 9 4 L 17 12 L 9 20" fill="none" stroke="currentColor" stroke-width="1.9"/>
</svg>''',
  ChatyGlyph.more: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <circle cx="5.2" cy="12" r="1.45" fill="currentColor" stroke="none"/>
  <circle cx="12" cy="12" r="1.45" fill="currentColor" stroke="none"/>
  <circle cx="18.8" cy="12" r="1.45" fill="currentColor" stroke="none"/>
</svg>''',
  ChatyGlyph.tune: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <path d="M 3 6 L 21 6 M 3 12 L 21 12 M 3 18 L 21 18" fill="none" stroke="currentColor" stroke-width="1.7"/>
  <circle cx="8" cy="6" r="1.8" fill="currentColor" stroke="none"/>
  <circle cx="15" cy="12" r="1.8" fill="currentColor" stroke="none"/>
  <circle cx="10.5" cy="18" r="1.8" fill="currentColor" stroke="none"/>
</svg>''',
  ChatyGlyph.search: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <circle cx="10.5" cy="10.5" r="6.2" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 15 15 L 20.5 20.5" fill="none" stroke="currentColor" stroke-width="1.9"/>
</svg>''',
  ChatyGlyph.close: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <path d="M 6 6 L 18 18 M 18 6 L 6 18" fill="none" stroke="currentColor" stroke-width="1.9"/>
</svg>''',
  ChatyGlyph.exportProfile: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <rect x="4" y="10" width="16" height="11" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 12 16 L 12 3 M 7.5 7.5 L 12 3 L 16.5 7.5" fill="none" stroke="currentColor" stroke-width="1.8"/>
</svg>''',
  ChatyGlyph.importProfile: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <rect x="4" y="10" width="16" height="11" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 12 3 L 12 16 M 7.5 11.5 L 12 16 L 16.5 11.5" fill="none" stroke="currentColor" stroke-width="1.8"/>
</svg>''',
  ChatyGlyph.reset: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <path d="M 19 8 C 16.2 3.5 9.9 2.7 6 6.5 C 2.2 10.2 3.2 16.7 7.5 19.2 C 11.1 21.4 16.1 20 18.2 17" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 18.5 4 L 19 8 L 15 8" fill="none" stroke="currentColor" stroke-width="1.8"/>
</svg>''',
  ChatyGlyph.chatBubble: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <rect x="3" y="3.5" width="18" height="13.5" rx="4" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 7 17 L 6 21 L 12 17 M 7 8 L 17 8 M 7 12 L 14 12" fill="none" stroke="currentColor" stroke-width="1.6"/>
</svg>''',
  ChatyGlyph.person: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <circle cx="12" cy="7.5" r="3.5" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 4 20 C 4.7 15.8 8 13.8 12 13.8 C 16 13.8 19.3 15.8 20 20" fill="none" stroke="currentColor" stroke-width="1.8"/>
</svg>''',
  ChatyGlyph.list: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <circle cx="4.5" cy="6" r="1.1" fill="currentColor" stroke="none"/>
  <circle cx="4.5" cy="12" r="1.1" fill="currentColor" stroke="none"/>
  <circle cx="4.5" cy="18" r="1.1" fill="currentColor" stroke="none"/>
  <path d="M 8 6 L 20 6 M 8 12 L 20 12 M 8 18 L 20 18" fill="none" stroke="currentColor" stroke-width="1.8"/>
</svg>''',
  ChatyGlyph.updates: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <circle cx="12" cy="12" r="8.3" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <circle cx="12" cy="12" r="5.8" fill="none" stroke="currentColor" stroke-width="1.5"/>
  <circle cx="18" cy="6" r="2" fill="currentColor" stroke="none"/>
</svg>''',
  ChatyGlyph.calls: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <path d="M 7 3.5 L 4.8 5.7 C 4.2 6.4 5.2 10.8 9.2 14.8 C 13.2 18.8 17.6 19.8 18.3 19.2 L 20.5 17 L 16.2 13.8 L 13.9 15.5 C 11.8 14.6 9.4 12.2 8.5 10.1 L 10.2 7.8 Z" fill="none" stroke="currentColor" stroke-width="1.8"/>
</svg>''',
  ChatyGlyph.groups: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <circle cx="9" cy="8" r="3.1" fill="none" stroke="currentColor" stroke-width="1.7"/>
  <circle cx="17" cy="9" r="2.4" fill="none" stroke="currentColor" stroke-width="1.7"/>
  <path d="M 3.5 20 C 4.2 15.8 6.4 13.7 9.2 13.7 C 12.2 13.7 14.1 15.8 14.8 20 M 14 15 C 16.9 14.2 19.6 16 20.4 19" fill="none" stroke="currentColor" stroke-width="1.7"/>
</svg>''',
  ChatyGlyph.tasks: r'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <rect x="4" y="3" width="16" height="18" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <path d="M 6 7 L 7 8 L 8.7 6 M 11 7 L 17.5 7 M 6 12 L 7 13 L 8.7 11 M 11 12 L 17.5 12 M 6 17 L 7 18 L 8.7 16 M 11 17 L 17.5 17" fill="none" stroke="currentColor" stroke-width="1.6"/>
</svg>''',
};

class ChatyGlyphIcon extends StatelessWidget {
  const ChatyGlyphIcon({
    super.key,
    required this.glyph,
    this.size = 20,
    this.color,
    this.strokeWidth = 1.8,
  });

  final ChatyGlyph glyph;
  final double size;
  final Color? color;
  final double strokeWidth;

  @override
  Widget build(BuildContext context) {
    final resolvedColor = color ?? IconTheme.of(context).color ?? Colors.white;
    return SizedBox.square(
      dimension: size,
      child: CustomPaint(
        painter: _ChatySvgGlyphPainter(
          glyph: glyph,
          color: resolvedColor,
          defaultStrokeWidth: strokeWidth,
        ),
      ),
    );
  }
}

class _SvgVectorElement {
  const _SvgVectorElement({
    required this.path,
    required this.fill,
    required this.stroke,
    required this.strokeWidth,
  });

  final Path path;
  final bool fill;
  final bool stroke;
  final double strokeWidth;
}

class _ChatySvgGlyphPainter extends CustomPainter {
  const _ChatySvgGlyphPainter({
    required this.glyph,
    required this.color,
    required this.defaultStrokeWidth,
  });

  final ChatyGlyph glyph;
  final Color color;
  final double defaultStrokeWidth;

  static final Map<ChatyGlyph, List<_SvgVectorElement>> _cache =
      <ChatyGlyph, List<_SvgVectorElement>>{};

  static final RegExp _shapeRegex = RegExp(
    r'<(path|circle|rect)\b([^>]*)/?>',
    caseSensitive: false,
  );
  static final RegExp _attributeRegex = RegExp(
    r'([a-zA-Z-]+)="([^"]*)"',
  );
  static final RegExp _tokenRegex = RegExp(
    r'[A-Za-z]|[-+]?(?:\d*\.)?\d+(?:[eE][-+]?\d+)?',
  );

  List<_SvgVectorElement> _elementsFor(ChatyGlyph value) =>
      _cache.putIfAbsent(value, () => _parseSvg(_chatyGlyphSvg[value]!));

  List<_SvgVectorElement> _parseSvg(String svg) {
    final elements = <_SvgVectorElement>[];
    for (final match in _shapeRegex.allMatches(svg)) {
      final tag = match.group(1)!.toLowerCase();
      final rawAttributes = match.group(2)!;
      final attributes = <String, String>{
        for (final attribute in _attributeRegex.allMatches(rawAttributes))
          attribute.group(1)!: attribute.group(2)!,
      };
      final path = switch (tag) {
        'path' => _parsePath(attributes['d'] ?? ''),
        'circle' => _circlePath(attributes),
        'rect' => _rectPath(attributes),
        _ => Path(),
      };
      if (path.getBounds().isEmpty) continue;
      final fill = (attributes['fill'] ?? 'currentColor') != 'none';
      final stroke = (attributes['stroke'] ?? 'none') != 'none';
      final strokeWidth =
          double.tryParse(attributes['stroke-width'] ?? '') ??
          defaultStrokeWidth;
      elements.add(
        _SvgVectorElement(
          path: path,
          fill: fill,
          stroke: stroke,
          strokeWidth: strokeWidth,
        ),
      );
    }
    return elements;
  }

  Path _circlePath(Map<String, String> attributes) {
    final cx = double.tryParse(attributes['cx'] ?? '') ?? 0;
    final cy = double.tryParse(attributes['cy'] ?? '') ?? 0;
    final radius = double.tryParse(attributes['r'] ?? '') ?? 0;
    return Path()..addOval(
      Rect.fromCircle(center: Offset(cx, cy), radius: radius),
    );
  }

  Path _rectPath(Map<String, String> attributes) {
    final x = double.tryParse(attributes['x'] ?? '') ?? 0;
    final y = double.tryParse(attributes['y'] ?? '') ?? 0;
    final width = double.tryParse(attributes['width'] ?? '') ?? 0;
    final height = double.tryParse(attributes['height'] ?? '') ?? 0;
    final radius = double.tryParse(attributes['rx'] ?? '') ?? 0;
    final rect = Rect.fromLTWH(x, y, width, height);
    if (radius <= 0) return Path()..addRect(rect);
    return Path()
      ..addRRect(RRect.fromRectAndRadius(rect, Radius.circular(radius)));
  }

  Path _parsePath(String data) {
    final tokens = _tokenRegex
        .allMatches(data)
        .map((match) => match.group(0)!)
        .toList(growable: false);
    final path = Path();
    var index = 0;
    var command = '';
    var current = Offset.zero;
    var subpathStart = Offset.zero;

    double nextNumber() {
      if (index >= tokens.length) {
        throw const FormatException('SVG path ended before a coordinate pair.');
      }
      final value = double.tryParse(tokens[index]);
      if (value == null) {
        throw FormatException('Expected SVG coordinate, got ${tokens[index]}');
      }
      index++;
      return value;
    }

    while (index < tokens.length) {
      final token = tokens[index];
      if (RegExp(r'^[A-Za-z]$').hasMatch(token)) {
        command = token;
        index++;
        if (command == 'Z' || command == 'z') {
          path.close();
          current = subpathStart;
          command = '';
          continue;
        }
      }
      if (command.isEmpty) {
        throw const FormatException('SVG path data has no command.');
      }
      switch (command) {
        case 'M':
          current = Offset(nextNumber(), nextNumber());
          path.moveTo(current.dx, current.dy);
          subpathStart = current;
          command = 'L';
          break;
        case 'L':
          current = Offset(nextNumber(), nextNumber());
          path.lineTo(current.dx, current.dy);
          break;
        case 'C':
          final control1 = Offset(nextNumber(), nextNumber());
          final control2 = Offset(nextNumber(), nextNumber());
          final end = Offset(nextNumber(), nextNumber());
          path.cubicTo(
            control1.dx,
            control1.dy,
            control2.dx,
            control2.dy,
            end.dx,
            end.dy,
          );
          current = end;
          break;
        default:
          throw FormatException('Unsupported SVG path command: $command');
      }
    }
    return path;
  }

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final sx = size.width / 24;
    final sy = size.height / 24;
    canvas.save();
    canvas.scale(sx, sy);
    for (final element in _elementsFor(glyph)) {
      if (element.fill) {
        canvas.drawPath(
          element.path,
          Paint()
            ..color = color
            ..style = PaintingStyle.fill
            ..isAntiAlias = true,
            );
      }
      if (element.stroke) {
        canvas.drawPath(
          element.path,
          Paint()
            ..color = color
            ..style = PaintingStyle.stroke
            ..strokeWidth = element.strokeWidth == 0
                ? defaultStrokeWidth
                : element.strokeWidth
            ..strokeCap = StrokeCap.round
            ..strokeJoin = StrokeJoin.round
            ..isAntiAlias = true,
        );
      }
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _ChatySvgGlyphPainter oldDelegate) =>
      oldDelegate.glyph != glyph ||
      oldDelegate.color != color ||
      oldDelegate.defaultStrokeWidth != defaultStrokeWidth;
}
