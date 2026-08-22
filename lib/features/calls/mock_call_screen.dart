import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/design_system/design_system.dart';

class MockCallScreen extends StatefulWidget {
  final ThemeConfig theme;
  final String title;
  final bool isVideo;

  const MockCallScreen({
    super.key,
    required this.theme,
    required this.title,
    this.isVideo = false,
  });

  @override
  State<MockCallScreen> createState() => _MockCallScreenState();
}

class _MockCallScreenState extends State<MockCallScreen> {
  bool _isMuted = false;
  bool _isSpeakerOn = true;
  bool _isVideoOn = true;
  int _secondsElapsed = 0;
  late final Stream<int> _timerStream;

  @override
  void initState() {
    super.initState();
    _isVideoOn = widget.isVideo;
    _timerStream = Stream.periodic(const Duration(seconds: 1), (i) => i + 1);
    _timerStream.listen((sec) {
      if (mounted) {
        setState(() => _secondsElapsed = sec);
      }
    });
  }

  String _formatDuration(int totalSec) {
    final m = (totalSec ~/ 60).toString().padLeft(2, '0');
    final s = (totalSec % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final theme = widget.theme;
    final colors = context.colors;
    // Real consumers for the 'Calls appearance' GB keys.
    final prefs = locator<ChatyPreferencesController>();
    final callsBackground =
        prefs.gbColor('ModCallsBackground') ?? colors.surfaceElevated;
    final callsText = prefs.gbColor('ModCallsTextColor') ?? colors.foreground;
    final callsIcon = prefs.gbColor('ModCallsIconColors') ?? colors.success;

    return Scaffold(
      backgroundColor: callsBackground,
      body: SafeArea(
        child: Column(
          children: [
            const SizedBox(height: ChatySpacing.base),
            // Header Security Info
            Container(
              padding: const EdgeInsets.symmetric(
                horizontal: ChatySpacing.md,
                vertical: 6,
              ),
              decoration: BoxDecoration(
                color: colors.foreground.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(ChatyRadius.full),
                border: Border.all(color: colors.borderSubtle),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.lock_rounded, color: callsIcon, size: 14),
                  const SizedBox(width: 6),
                  Text(
                    'Direct E2E Encrypted Call',
                    style: TextStyle(
                      color: callsText,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: ChatySpacing.md),
            // Caller Info
            Text(
              widget.title,
              style: TextStyle(
                color: callsText,
                fontSize: 24,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: ChatySpacing.xs),
            Text(
              _formatDuration(_secondsElapsed),
              style: TextStyle(
                color: callsText.withValues(alpha: 0.65),
                fontSize: 15,
                fontWeight: FontWeight.w500,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),

            const Spacer(),

            // Primary content: remote video fills the stage; audio-only falls
            // back to the avatar monogram. Local preview is a small PiP.
            Expanded(
              child: Stack(
                children: [
                  Positioned.fill(
                    child: _isVideoOn
                        ? _RemoteVideoStage(colors: colors, title: widget.title)
                        : Center(
                            child: _AvatarMonogram(
                              title: widget.title,
                              theme: theme,
                              colors: colors,
                            ),
                          ),
                  ),
                  if (_isVideoOn)
                    Positioned(
                      top: ChatySpacing.sm,
                      right: ChatySpacing.md,
                      child: _LocalPip(colors: colors),
                    ),
                ],
              ),
            ),

            // Call Controls Toolbar
            Container(
              padding: const EdgeInsets.symmetric(
                horizontal: ChatySpacing.xl,
                vertical: ChatySpacing.lg,
              ),
              decoration: BoxDecoration(
                color: colors.surface,
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(ChatyRadius.xl),
                ),
                border: Border(top: BorderSide(color: colors.borderSubtle)),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  _CallControl(
                    icon: _isMuted ? Icons.mic_off_rounded : Icons.mic_rounded,
                    isOff: _isMuted,
                    onTap: () => setState(() => _isMuted = !_isMuted),
                    colors: colors,
                    callsIcon: callsIcon,
                  ),
                  _CallControl(
                    icon: _isVideoOn
                        ? Icons.videocam_rounded
                        : Icons.videocam_off_rounded,
                    isOff: !_isVideoOn,
                    onTap: () => setState(() => _isVideoOn = !_isVideoOn),
                    colors: colors,
                    callsIcon: callsIcon,
                  ),
                  _CallControl(
                    icon: _isSpeakerOn
                        ? Icons.volume_up_rounded
                        : Icons.volume_down_rounded,
                    isOff: !_isSpeakerOn,
                    onTap: () => setState(() => _isSpeakerOn = !_isSpeakerOn),
                    colors: colors,
                    callsIcon: callsIcon,
                  ),
                  // End call — always destructive.
                  _EndCallControl(onTap: () => Navigator.pop(context)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Full-stage remote video surface. The call engine renders real frames into
/// the app's existing transport; this placeholder keeps the stage geometry
/// stable when no frame surface is attached.
class _RemoteVideoStage extends StatelessWidget {
  final AppColors colors;
  final String title;

  const _RemoteVideoStage({required this.colors, required this.title});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: ChatySpacing.md),
      decoration: BoxDecoration(
        color: colors.surfaceSecondary,
        borderRadius: BorderRadius.circular(ChatyRadius.xl),
        border: Border.all(color: colors.border),
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          Icon(
            Icons.videocam_rounded,
            size: 64,
            color: colors.foregroundSecondary.withValues(alpha: 0.24),
          ),
          Positioned(
            bottom: 12,
            left: 12,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: colors.surface.withValues(alpha: 0.65),
                borderRadius: BorderRadius.circular(ChatyRadius.sm),
              ),
              child: Text(
                title,
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Audio-only fallback: avatar monogram with the accent ring.
class _AvatarMonogram extends StatelessWidget {
  final String title;
  final ThemeConfig theme;
  final AppColors colors;

  const _AvatarMonogram({
    required this.title,
    required this.theme,
    required this.colors,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 110,
      height: 110,
      decoration: BoxDecoration(
        color: theme.accentColor.withValues(alpha: 0.15),
        shape: BoxShape.circle,
        border: Border.all(color: theme.accentColor, width: 2),
      ),
      child: Center(
        child: Text(
          title.isNotEmpty ? title.substring(0, 2).toUpperCase() : 'CALL',
          style: TextStyle(
            color: colors.foreground,
            fontSize: 32,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}

/// Small self-view thumbnail (secondary surface, top-right).
class _LocalPip extends StatelessWidget {
  final AppColors colors;

  const _LocalPip({required this.colors});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 92,
      height: 124,
      decoration: BoxDecoration(
        color: colors.surfaceElevated,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.borderSubtle),
        boxShadow: [
          BoxShadow(
            color: colors.shadow,
            blurRadius: 12,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.person_rounded,
            size: 30,
            color: colors.foregroundSecondary.withValues(alpha: 0.6),
          ),
          const SizedBox(height: 4),
          Text(
            'You',
            style: TextStyle(
              color: colors.foregroundSecondary,
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

/// Uniform 54dp circular call control. "Off" states (muted, camera off,
/// speaker off) tint toward error so the state reads at a glance; "on"
/// states use the calls icon color on a subtle fill.
class _CallControl extends StatefulWidget {
  final IconData icon;
  final bool isOff;
  final VoidCallback onTap;
  final AppColors colors;
  final Color callsIcon;

  const _CallControl({
    required this.icon,
    required this.isOff,
    required this.onTap,
    required this.colors,
    required this.callsIcon,
  });

  @override
  State<_CallControl> createState() => _CallControlState();
}

class _CallControlState extends State<_CallControl> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final colors = widget.colors;
    return GestureDetector(
      onTap: widget.onTap,
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) => setState(() => _pressed = false),
      child: AnimatedScale(
        scale: _pressed ? 0.93 : 1.0,
        duration: const Duration(milliseconds: 120),
        curve: Curves.easeOutCubic,
        child: Container(
          width: 54,
          height: 54,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: widget.isOff
                ? colors.error.withValues(alpha: 0.16)
                : colors.foreground.withValues(alpha: 0.08),
            border: Border.all(
              color: widget.isOff
                  ? colors.error.withValues(alpha: 0.35)
                  : Colors.transparent,
            ),
          ),
          child: Icon(
            widget.icon,
            color: widget.isOff ? colors.error : widget.callsIcon,
            size: 22,
          ),
        ),
      ),
    );
  }
}

/// End-call control: larger, always destructive, same press-scale language.
class _EndCallControl extends StatefulWidget {
  final VoidCallback onTap;
  const _EndCallControl({required this.onTap});

  @override
  State<_EndCallControl> createState() => _EndCallControlState();
}

class _EndCallControlState extends State<_EndCallControl> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return GestureDetector(
      onTap: widget.onTap,
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) => setState(() => _pressed = false),
      child: AnimatedScale(
        scale: _pressed ? 0.93 : 1.0,
        duration: const Duration(milliseconds: 120),
        curve: Curves.easeOutCubic,
        child: Container(
          width: 62,
          height: 62,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: colors.error,
          ),
          child: Icon(Icons.call_end_rounded, color: colors.onError, size: 27),
        ),
      ),
    );
  }
}
