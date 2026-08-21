import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/theme/theme_config.dart';
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
    // Real consumers for the 'Calls appearance' GB keys.
    final prefs = locator<ChatyPreferencesController>();
    final callsBackground =
        prefs.gbColor('ModCallsBackground') ?? const Color(0xFF09090B);
    final callsText = prefs.gbColor('ModCallsTextColor');
    final callsIcon = prefs.gbColor('ModCallsIconColors');

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
                color: Colors.white.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(ChatyRadius.full),
                border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.lock_rounded,
                    color: callsIcon ?? const Color(0xFF10B981),
                    size: 14,
                  ),
                  const SizedBox(width: 6),
                  Text(
                    'Direct E2E Encrypted Call',
                    style: TextStyle(
                      color: callsText ?? Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: ChatySpacing.xl),

            // Caller Info
            Text(
              widget.title,
              style: TextStyle(
                color: callsText ?? Colors.white,
                fontSize: 24,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: ChatySpacing.xs),
            Text(
              _formatDuration(_secondsElapsed),
              style: TextStyle(
                color: (callsText ?? Colors.white).withValues(alpha: 0.65),
                fontSize: 15,
                fontWeight: FontWeight.w500,
              ),
            ),

            const Spacer(),

            // Video Preview or Avatar
            if (_isVideoOn)
              Container(
                margin: const EdgeInsets.symmetric(
                  horizontal: ChatySpacing.lg,
                  vertical: ChatySpacing.base,
                ),
                height: 280,
                width: double.infinity,
                decoration: BoxDecoration(
                  color: const Color(0xFF18181B),
                  borderRadius: BorderRadius.circular(ChatyRadius.xl),
                  border: Border.all(
                    color: Colors.white.withValues(alpha: 0.12),
                  ),
                ),
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    const Icon(
                      Icons.videocam_rounded,
                      size: 64,
                      color: Colors.white24,
                    ),
                    Positioned(
                      bottom: 12,
                      left: 12,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.65),
                          borderRadius: BorderRadius.circular(ChatyRadius.sm),
                        ),
                        child: Text(
                          widget.title,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              )
            else
              Container(
                width: 110,
                height: 110,
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                  border: Border.all(color: theme.accentColor, width: 2),
                ),
                child: Center(
                  child: Text(
                    widget.title.isNotEmpty
                        ? widget.title.substring(0, 2).toUpperCase()
                        : 'CALL',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 32,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ),

            const Spacer(),

            // Call Controls Toolbar
            Container(
              padding: const EdgeInsets.symmetric(
                horizontal: ChatySpacing.xl,
                vertical: ChatySpacing.lg,
              ),
              decoration: BoxDecoration(
                color: const Color(0xFF18181B),
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(ChatyRadius.xl),
                ),
                border: Border(
                  top: BorderSide(color: Colors.white.withValues(alpha: 0.08)),
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  _buildCallBtn(
                    icon: _isMuted ? Icons.mic_off_rounded : Icons.mic_rounded,
                    isActive: _isMuted,
                    onTap: () => setState(() => _isMuted = !_isMuted),
                  ),
                  _buildCallBtn(
                    icon: _isVideoOn
                        ? Icons.videocam_rounded
                        : Icons.videocam_off_rounded,
                    isActive: _isVideoOn,
                    onTap: () => setState(() => _isVideoOn = !_isVideoOn),
                  ),
                  _buildCallBtn(
                    icon: _isSpeakerOn
                        ? Icons.volume_up_rounded
                        : Icons.volume_down_rounded,
                    isActive: _isSpeakerOn,
                    onTap: () => setState(() => _isSpeakerOn = !_isSpeakerOn),
                  ),
                  // End Call
                  GestureDetector(
                    onTap: () => Navigator.pop(context),
                    child: Container(
                      padding: const EdgeInsets.all(ChatySpacing.md),
                      decoration: const BoxDecoration(
                        color: Color(0xFFEF4444),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.call_end_rounded,
                        color: Colors.white,
                        size: 26,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// Real consumer: `ModCallsIconColors` tints the in-call control icons.
  Color get _callsIconColor =>
      locator<ChatyPreferencesController>().gbColor('ModCallsIconColors') ??
      Colors.white;

  Widget _buildCallBtn({
    required IconData icon,
    required bool isActive,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(ChatySpacing.md),
        decoration: BoxDecoration(
          color: isActive
              ? Colors.white.withValues(alpha: 0.22)
              : Colors.white.withValues(alpha: 0.08),
          shape: BoxShape.circle,
        ),
        child: Icon(icon, color: _callsIconColor, size: 22),
      ),
    );
  }
}

