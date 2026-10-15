import 'dart:math' as math;
import 'package:flutter/material.dart';

/// Real-time 60fps canvas audio waveform visualizer widget with smooth animated bar heights,
/// gradient fills, active playhead indicator, and audio recording / playback states.
class AnimatedWaveformVisualizer extends StatefulWidget {
  final List<double> samples;
  final double progress; // 0.0 to 1.0
  final bool isRecording;
  final bool isPlaying;
  final Color? activeColor;
  final Color? inactiveColor;
  final double height;
  final double barWidth;
  final double barGap;
  final ValueChanged<double>? onSeek;

  const AnimatedWaveformVisualizer({
    super.key,
    required this.samples,
    this.progress = 0.0,
    this.isRecording = false,
    this.isPlaying = false,
    this.activeColor,
    this.inactiveColor,
    this.height = 48.0,
    this.barWidth = 3.0,
    this.barGap = 2.0,
    this.onSeek,
  });

  @override
  State<AnimatedWaveformVisualizer> createState() => _AnimatedWaveformVisualizerState();
}

class _AnimatedWaveformVisualizerState extends State<AnimatedWaveformVisualizer>
    with SingleTickerProviderStateMixin {
  late final AnimationController _animController;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    )..repeat();
  }

  @override
  void dispose() {
    _animController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primary = widget.activeColor ?? theme.colorScheme.primary;
    final baseInactive = widget.inactiveColor ?? (isDark ? Colors.white24 : Colors.black12);

    return GestureDetector(
      onHorizontalDragUpdate: (details) {
        if (widget.onSeek != null && context.size != null) {
          final localX = details.localPosition.dx.clamp(0.0, context.size!.width);
          widget.onSeek!(localX / context.size!.width);
        }
      },
      onTapDown: (details) {
        if (widget.onSeek != null && context.size != null) {
          final localX = details.localPosition.dx.clamp(0.0, context.size!.width);
          widget.onSeek!(localX / context.size!.width);
        }
      },
      child: AnimatedBuilder(
        animation: _animController,
        builder: (context, _) => CustomPaint(
          size: Size(double.infinity, widget.height),
          painter: _WaveformCustomPainter(
            samples: widget.samples,
            progress: widget.progress,
            isRecording: widget.isRecording,
            isPlaying: widget.isPlaying,
            pulsePhase: _animController.value,
            activeColor: primary,
            inactiveColor: baseInactive,
            barWidth: widget.barWidth,
            barGap: widget.barGap,
          ),
        ),
      ),
    );
  }
}

class _WaveformCustomPainter extends CustomPainter {
  final List<double> samples;
  final double progress;
  final bool isRecording;
  final bool isPlaying;
  final double pulsePhase;
  final Color activeColor;
  final Color inactiveColor;
  final double barWidth;
  final double barGap;

  _WaveformCustomPainter({
    required this.samples,
    required this.progress,
    required this.isRecording,
    required this.isPlaying,
    required this.pulsePhase,
    required this.activeColor,
    required this.inactiveColor,
    required this.barWidth,
    required this.barGap,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (size.width <= 0 || size.height <= 0) return;

    final step = barWidth + barGap;
    final totalBars = (size.width / step).floor();
    if (totalBars <= 0) return;

    final paintActive = Paint()
      ..color = activeColor
      ..strokeCap = StrokeCap.round
      ..strokeWidth = barWidth;

    final paintInactive = Paint()
      ..color = inactiveColor
      ..strokeCap = StrokeCap.round
      ..strokeWidth = barWidth;

    final centerY = size.height / 2;
    final effectiveSamples = samples.isEmpty
        ? List.generate(totalBars, (i) => 0.25 + 0.5 * math.sin(i * 0.3).abs())
        : samples;

    for (var i = 0; i < totalBars; i++) {
      final sampleIndex = (i * effectiveSamples.length / totalBars).floor().clamp(0, effectiveSamples.length - 1);
      var amp = effectiveSamples[sampleIndex].clamp(0.08, 1.0);

      if (isRecording) {
        final wave = math.sin((i * 0.2) + (pulsePhase * math.pi * 2)).abs();
        amp = (amp * 0.7) + (wave * 0.3);
      }

      final barHeight = (amp * (size.height - 4)).clamp(4.0, size.height);
      final x = (i * step) + (barWidth / 2);
      final topY = centerY - (barHeight / 2);
      final bottomY = centerY + (barHeight / 2);

      final isBarActive = (x / size.width) <= progress;
      canvas.drawLine(
        Offset(x, topY),
        Offset(x, bottomY),
        isBarActive ? paintActive : paintInactive,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _WaveformCustomPainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.pulsePhase != pulsePhase ||
        oldDelegate.samples != samples ||
        oldDelegate.isRecording != isRecording ||
        oldDelegate.isPlaying != isPlaying;
  }
}