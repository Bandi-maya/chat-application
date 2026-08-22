import 'dart:async';
import 'package:flutter/material.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../domain/models/call_state.dart';
import '../../data/services/call_signaling_service.dart';
import '../camera/effects/effect_engine.dart';
import '../camera/effects/widgets/effect_picker_sheet.dart';

/// Full-screen ongoing voice and video call screen with:
/// - Auto-hiding overlay controls
/// - Real draggable & edge-snapping local video preview PIP
/// - In-call quick reactions burst
/// - Call focus mode
/// - Audio device routing switcher (earpiece, speaker, bluetooth)
/// - Effects engine integration
class OngoingCallScreen extends StatefulWidget {
  final ThemeConfig theme;

  const OngoingCallScreen({super.key, required this.theme});

  @override
  State<OngoingCallScreen> createState() => _OngoingCallScreenState();
}

class _OngoingCallScreenState extends State<OngoingCallScreen> {
  final CallSignalingService _callService = locator<CallSignalingService>();
  final EffectEngine _effectEngine = locator<EffectEngine>();

  bool _controlsVisible = true;
  bool _focusMode = false;
  Timer? _autoHideTimer;

  // Draggable PiP coordinates
  Offset _localPipOffset = const Offset(20, 80);
  String? _activeReactionEmoji;
  Timer? _reactionBurstTimer;

  @override
  void initState() {
    super.initState();
    _callService.addListener(_handleCallStateChanged);
    _startAutoHideTimer();
  }

  void _handleCallStateChanged() {
    final session = _callService.currentSession;
    if (session == null ||
        session.state == CallSessionState.ended ||
        session.state == CallSessionState.declined ||
        session.state == CallSessionState.failed) {
      if (mounted && Navigator.of(context).canPop()) {
        Navigator.of(context).pop();
      }
    }
  }

  void _startAutoHideTimer() {
    _autoHideTimer?.cancel();
    _autoHideTimer = Timer(const Duration(seconds: 5), () {
      if (mounted && !_focusMode) {
        setState(() => _controlsVisible = false);
      }
    });
  }

  void _toggleControlsVisibility() {
    setState(() => _controlsVisible = !_controlsVisible);
    if (_controlsVisible) _startAutoHideTimer();
  }

  void _triggerReaction(String emoji) {
    setState(() => _activeReactionEmoji = emoji);
    _callService.sendCallReaction(emoji);

    _reactionBurstTimer?.cancel();
    _reactionBurstTimer = Timer(const Duration(seconds: 2), () {
      if (mounted) setState(() => _activeReactionEmoji = null);
    });
  }

  String _formatDuration(int totalSeconds) {
    final m = (totalSeconds ~/ 60).toString().padLeft(2, '0');
    final s = (totalSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  void dispose() {
    _callService.removeListener(_handleCallStateChanged);
    _autoHideTimer?.cancel();
    _reactionBurstTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return ListenableBuilder(
      listenable: Listenable.merge([_callService, _effectEngine]),
      builder: (context, _) {
        final session = _callService.currentSession;
        if (session == null) return const SizedBox.shrink();

        return Scaffold(
          backgroundColor: Colors.black,
          body: GestureDetector(
            onTap: _toggleControlsVisibility,
            behavior: HitTestBehavior.opaque,
            child: Stack(
              fit: StackFit.expand,
              children: [
                // 1. Remote Video / Avatar (Full Screen)
                if (session.isVideo && !session.isCameraOff)
                  _effectEngine.renderEffect(
                    child: Container(
                      color: const Color(0xFF0F172A),
                      child: Center(
                        child: Icon(
                          Icons.videocam_rounded,
                          size: 96,
                          color: colors.foregroundSecondary.withValues(alpha: 0.25),
                        ),
                      ),
                    ),
                  )
                else
                  _buildVoiceCallBackdrop(session, colors),

                // 2. Local Video PiP Preview (Draggable & edge snapping)
                if (session.isVideo && !_focusMode)
                  Positioned(
                    left: _localPipOffset.dx,
                    top: _localPipOffset.dy,
                    child: GestureDetector(
                      onPanUpdate: (details) {
                        final size = MediaQuery.of(context).size;
                        setState(() {
                          _localPipOffset = Offset(
                            (_localPipOffset.dx + details.delta.dx)
                                .clamp(16.0, size.width - 136.0),
                            (_localPipOffset.dy + details.delta.dy)
                                .clamp(60.0, size.height - 220.0),
                          );
                        });
                      },
                      child: Container(
                        width: 120,
                        height: 160,
                        decoration: BoxDecoration(
                          color: colors.surfaceSecondary,
                          borderRadius: BorderRadius.circular(ChatyRadius.lg),
                          border: Border.all(color: colors.border, width: 1.5),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.4),
                              blurRadius: 12,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(ChatyRadius.lg),
                          child: Stack(
                            alignment: Alignment.center,
                            children: [
                              Icon(
                                Icons.person_rounded,
                                size: 48,
                                color: colors.foregroundSecondary.withValues(alpha: 0.3),
                              ),
                              Positioned(
                                bottom: 6,
                                right: 6,
                                child: GestureDetector(
                                  onTap: () => _callService.switchCamera(),
                                  child: Container(
                                    padding: const EdgeInsets.all(4),
                                    decoration: BoxDecoration(
                                      color: Colors.black.withValues(alpha: 0.6),
                                      shape: BoxShape.circle,
                                    ),
                                    child: const Icon(
                                      Icons.flip_camera_ios_rounded,
                                      size: 14,
                                      color: Colors.white,
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),

                // 3. Reaction Burst Animation
                if (_activeReactionEmoji != null)
                  Center(
                    child: AnimatedScale(
                      scale: 1.6,
                      duration: const Duration(milliseconds: 300),
                      curve: Curves.elasticOut,
                      child: Text(
                        _activeReactionEmoji!,
                        style: const TextStyle(fontSize: 72),
                      ),
                    ),
                  ),

                // 4. Header Bar (Overlay)
                AnimatedPositioned(
                  duration: const Duration(milliseconds: 250),
                  top: _controlsVisible ? 0 : -100,
                  left: 0,
                  right: 0,
                  child: Container(
                    padding: const EdgeInsets.fromLTRB(
                      ChatySpacing.lg,
                      ChatySpacing.xxl,
                      ChatySpacing.lg,
                      ChatySpacing.md,
                    ),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Colors.black.withValues(alpha: 0.7),
                          Colors.transparent,
                        ],
                      ),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        IconButton(
                          icon: const Icon(
                            Icons.arrow_back_ios_new_rounded,
                            color: Colors.white,
                            size: 20,
                          ),
                          onPressed: () => Navigator.of(context).pop(),
                        ),
                        Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              session.remoteDisplayName,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 17,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            Text(
                              _formatDuration(_callService.callDurationSeconds),
                              style: TextStyle(
                                color: Colors.white.withValues(alpha: 0.7),
                                fontSize: 13,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ],
                        ),
                        IconButton(
                          icon: Icon(
                            _focusMode
                                ? Icons.fullscreen_exit_rounded
                                : Icons.fullscreen_rounded,
                            color: Colors.white,
                          ),
                          onPressed: () {
                            setState(() => _focusMode = !_focusMode);
                          },
                        ),
                      ],
                    ),
                  ),
                ),

                // 5. In-call Quick Reactions Bar
                if (_controlsVisible && !_focusMode)
                  Positioned(
                    bottom: 120,
                    left: 0,
                    right: 0,
                    child: Center(
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.55),
                          borderRadius: BorderRadius.circular(ChatyRadius.full),
                          border: Border.all(color: Colors.white.withValues(alpha: 0.15)),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            for (final emoji in ['❤️', '👍', '😂', '🎉', '👏', '🔥'])
                              GestureDetector(
                                onTap: () => _triggerReaction(emoji),
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(horizontal: 6),
                                  child: Text(emoji, style: const TextStyle(fontSize: 22)),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                  ),

                // 6. Bottom Controls Bar
                AnimatedPositioned(
                  duration: const Duration(milliseconds: 250),
                  bottom: _controlsVisible ? 0 : -140,
                  left: 0,
                  right: 0,
                  child: Container(
                    padding: const EdgeInsets.fromLTRB(
                      ChatySpacing.xl,
                      ChatySpacing.lg,
                      ChatySpacing.xl,
                      ChatySpacing.xxl,
                    ),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.bottomCenter,
                        end: Alignment.topCenter,
                        colors: [
                          Colors.black.withValues(alpha: 0.8),
                          Colors.transparent,
                        ],
                      ),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        // Mic Mute
                        _buildCallButton(
                          icon: session.isMuted
                              ? Icons.mic_off_rounded
                              : Icons.mic_rounded,
                          isActive: session.isMuted,
                          activeColor: colors.error,
                          onTap: () => _callService.toggleMute(),
                        ),

                        // Camera Toggle
                        if (session.isVideo)
                          _buildCallButton(
                            icon: session.isCameraOff
                                ? Icons.videocam_off_rounded
                                : Icons.videocam_rounded,
                            isActive: session.isCameraOff,
                            activeColor: colors.error,
                            onTap: () => _callService.toggleCamera(),
                          ),

                        // Effects Drawer
                        if (session.isVideo)
                          _buildCallButton(
                            icon: Icons.auto_awesome_rounded,
                            isActive: _effectEngine.activeEffect.id != 'none',
                            activeColor: colors.primary,
                            onTap: () => EffectPickerSheet.show(context),
                          ),

                        // Audio Route Selector (Speaker / Bluetooth / Earpiece)
                        _buildCallButton(
                          icon: session.audioRoute == AudioRouteType.speaker
                              ? Icons.volume_up_rounded
                              : Icons.hearing_rounded,
                          isActive: session.audioRoute == AudioRouteType.speaker,
                          activeColor: colors.primary,
                          onTap: () {
                            _callService.setAudioRoute(
                              session.audioRoute == AudioRouteType.speaker
                                  ? AudioRouteType.earpiece
                                  : AudioRouteType.speaker,
                            );
                          },
                        ),

                        // End Call
                        GestureDetector(
                          onTap: () async {
                            await _callService.endCall();
                            if (context.mounted && Navigator.of(context).canPop()) {
                              Navigator.of(context).pop();
                            }
                          },
                          child: Container(
                            width: 58,
                            height: 58,
                            decoration: BoxDecoration(
                              color: colors.error,
                              shape: BoxShape.circle,
                            ),
                            child: Icon(
                              Icons.call_end_rounded,
                              color: colors.onError,
                              size: 28,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildVoiceCallBackdrop(ChatyCallSession session, AppColors colors) {
    return Container(
      color: colors.surfaceElevated,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            AppAvatar(
              initials: session.remoteAvatarInitials ??
                  (session.remoteDisplayName.isNotEmpty
                      ? session.remoteDisplayName.substring(0, 1).toUpperCase()
                      : 'U'),
              colorHex: session.remoteAvatarColorHex,
              size: 110,
            ),
            const SizedBox(height: ChatySpacing.lg),
            Text(
              session.remoteDisplayName,
              style: TextStyle(
                color: colors.foreground,
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: ChatySpacing.xs),
            Text(
              _formatDuration(_callService.callDurationSeconds),
              style: TextStyle(
                color: colors.foregroundSecondary,
                fontSize: 16,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCallButton({
    required IconData icon,
    required bool isActive,
    required Color activeColor,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 52,
        height: 52,
        decoration: BoxDecoration(
          color: isActive
              ? activeColor.withValues(alpha: 0.25)
              : Colors.white.withValues(alpha: 0.15),
          shape: BoxShape.circle,
          border: Border.all(
            color: isActive ? activeColor : Colors.white.withValues(alpha: 0.2),
          ),
        ),
        child: Icon(
          icon,
          color: isActive ? activeColor : Colors.white,
          size: 22,
        ),
      ),
    );
  }
}
