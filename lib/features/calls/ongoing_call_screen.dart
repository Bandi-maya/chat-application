import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../../data/services/call_signaling_service.dart';
import '../../domain/models/call_state.dart';
import '../../features/camera/effects/effect_engine.dart';
import '../../features/camera/effects/effect_registry.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/widgets/app_avatar.dart';
import 'call_presentation_controller.dart';

/// Full-screen voice/video call UI backed by real WebRTC media streams.
///
/// No placeholder camera surface is rendered as a successful call. The call
/// timer only runs after RTCPeerConnection reports a connected transport.
class OngoingCallScreen extends StatefulWidget {
  final ThemeConfig theme;

  const OngoingCallScreen({super.key, required this.theme});

  /// Number of call-screen instances currently presented. The root activity
  /// layer listens to this so minimized-call controls only appear while the
  /// full call surface is not on screen.
  static final ValueNotifier<int> presentedInstances = ValueNotifier<int>(0);

  @override
  State<OngoingCallScreen> createState() => _OngoingCallScreenState();
}

class _OngoingCallScreenState extends State<OngoingCallScreen> {
  final CallSignalingService _callService = locator<CallSignalingService>();
  final RTCVideoRenderer _localRenderer = RTCVideoRenderer();
  final RTCVideoRenderer _remoteRenderer = RTCVideoRenderer();

  /// Personal viewing filter applied to the local preview and the remote
  /// video surface on THIS device. Synchronizing one shared lens across
  /// both peers requires frame-level signaling that is not available in the
  /// current transport; until then this is a viewer-side effect only.
  final EffectEngine _effectEngine = EffectEngine();

  bool _controlsVisible = true;
  bool _focusMode = false;
  bool _renderersReady = false;
  String? _setupError;
  Timer? _autoHideTimer;
  Offset _localPipOffset = const Offset(20, 80);

  @override
  void initState() {
    super.initState();
    OngoingCallScreen.presentedInstances.value++;
    locator<CallPresentationController>().showFullScreen();
    _callService.addListener(_handleCallStateChanged);
    unawaited(_initializeMediaUi());
    _startAutoHideTimer();
  }

  Future<void> _initializeMediaUi() async {
    try {
      await Future.wait<void>(<Future<void>>[
        _localRenderer.initialize(),
        _remoteRenderer.initialize(),
      ]);
      await _callService.initialize();
      if (!mounted) return;
      setState(() => _renderersReady = true);
      _syncRenderers();

      // The existing app-level ringing overlay opens this screen after the
      // user taps Accept. Hydrate the server-authorized call and answer it here.
      if (_callService.currentSession?.state == CallSessionState.incoming) {
        await _callService.acceptCall();
      }
    } catch (error) {
      if (!mounted) return;
      setState(() => _setupError = error.toString());
    }
  }

  void _syncRenderers() {
    if (!_renderersReady) return;
    final local = _callService.localStream;
    final remote = _callService.remoteStream;
    if (!identical(_localRenderer.srcObject, local)) {
      _localRenderer.srcObject = local;
    }
    if (!identical(_remoteRenderer.srcObject, remote)) {
      _remoteRenderer.srcObject = remote;
    }
  }

  void _handleCallStateChanged() {
    _syncRenderers();
    final session = _callService.currentSession;
    if (session == null ||
        session.state == CallSessionState.ended ||
        session.state == CallSessionState.declined) {
      if (mounted && Navigator.of(context).canPop()) {
        Navigator.of(context).pop();
      }
      return;
    }
    if (mounted) setState(() {});
  }

  void _startAutoHideTimer() {
    _autoHideTimer?.cancel();
    _autoHideTimer = Timer(const Duration(seconds: 5), () {
      if (mounted && !_focusMode) setState(() => _controlsVisible = false);
    });
  }

  void _toggleControlsVisibility() {
    setState(() => _controlsVisible = !_controlsVisible);
    if (_controlsVisible) _startAutoHideTimer();
  }

  String _formatDuration(int totalSeconds) {
    final m = (totalSeconds ~/ 60).toString().padLeft(2, '0');
    final s = (totalSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  String _statusLabel(ChatyCallSession session) {
    switch (session.state) {
      case CallSessionState.initiating:
        return 'Preparing secure media…';
      case CallSessionState.ringing:
        return 'Ringing…';
      case CallSessionState.incoming:
        return 'Incoming call';
      case CallSessionState.connecting:
        return 'Connecting media…';
      case CallSessionState.connected:
        return _formatDuration(_callService.callDurationSeconds);
      case CallSessionState.reconnecting:
        return 'Reconnecting…';
      case CallSessionState.busy:
        return 'Busy';
      case CallSessionState.missed:
        return 'Missed';
      case CallSessionState.failed:
        return 'Connection failed';
      case CallSessionState.declined:
        return 'Declined';
      case CallSessionState.ended:
        return 'Call ended';
      case CallSessionState.idle:
        return 'Preparing call…';
    }
  }

  @override
  void dispose() {
    OngoingCallScreen.presentedInstances.value--;
    locator<CallPresentationController>().minimizeToPipOrIsland();
    _callService.removeListener(_handleCallStateChanged);
    _effectEngine.dispose();
    _autoHideTimer?.cancel();
    _localRenderer.srcObject = null;
    _remoteRenderer.srcObject = null;
    unawaited(_localRenderer.dispose());
    unawaited(_remoteRenderer.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return ListenableBuilder(
      listenable: _callService,
      builder: (context, _) {
        _syncRenderers();
        final session = _callService.currentSession;
        if (session == null) {
          return Scaffold(
            backgroundColor: Colors.black,
            body: SafeArea(
              child: ChatyEmptyState(
                icon: Icons.call_end_rounded,
                title: 'Call unavailable',
                message:
                    _setupError ?? 'This call session is no longer available.',
                iconColor: colors.error,
                titleColor: Colors.white,
                messageColor: Colors.white70,
                actionLabel: 'Close',
                onAction: () => Navigator.of(context).maybePop(),
              ),
            ),
          );
        }

        return Scaffold(
          backgroundColor: Colors.black,
          body: GestureDetector(
            onTap: _toggleControlsVisibility,
            behavior: HitTestBehavior.opaque,
            child: Stack(
              fit: StackFit.expand,
              children: [
                if (session.isVideo)
                  _buildRemoteVideo(session, colors)
                else
                  _buildVoiceCallBackdrop(session, colors),

                if (session.isVideo &&
                    _renderersReady &&
                    _callService.localStream != null &&
                    !_focusMode)
                  Positioned(
                    left: _localPipOffset.dx,
                    top: _localPipOffset.dy,
                    child: GestureDetector(
                      onPanUpdate: (details) {
                        final size = MediaQuery.of(context).size;
                        setState(() {
                          _localPipOffset = Offset(
                            (_localPipOffset.dx + details.delta.dx).clamp(
                              16.0,
                              size.width - 136.0,
                            ),
                            (_localPipOffset.dy + details.delta.dy).clamp(
                              60.0,
                              size.height - 220.0,
                            ),
                          );
                        });
                      },
                      child: Container(
                        width: 120,
                        height: 160,
                        decoration: BoxDecoration(
                          color: Colors.black,
                          borderRadius: BorderRadius.circular(ChatyRadius.lg),
                          border: Border.all(
                            color: Colors.white.withValues(alpha: 0.22),
                            width: 1,
                          ),
                          boxShadow: <BoxShadow>[
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.45),
                              blurRadius: 14,
                              offset: const Offset(0, 5),
                            ),
                          ],
                        ),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(ChatyRadius.lg),
                          child: Stack(
                            fit: StackFit.expand,
                            children: [
                              if (!session.isCameraOff)
                                _effectEngine.renderEffect(
                                  child: RTCVideoView(
                                    _localRenderer,
                                    mirror: session.isFrontCamera,
                                    objectFit: RTCVideoViewObjectFit
                                        .RTCVideoViewObjectFitCover,
                                  ),
                                )
                              else
                                Center(
                                  child: Icon(
                                    Icons.videocam_off_rounded,
                                    color: Colors.white.withValues(alpha: 0.65),
                                  ),
                                ),
                              Positioned(
                                bottom: 6,
                                right: 6,
                                child: Semantics(
                                  button: true,
                                  label: 'Switch camera',
                                  child: GestureDetector(
                                    onTap: () =>
                                        unawaited(_callService.switchCamera()),
                                    child: Container(
                                      width: 32,
                                      height: 32,
                                      decoration: BoxDecoration(
                                        color: Colors.black.withValues(
                                          alpha: 0.62,
                                        ),
                                        shape: BoxShape.circle,
                                      ),
                                      alignment: Alignment.center,
                                      child: const Icon(
                                        Icons.flip_camera_ios_rounded,
                                        size: 16,
                                        color: Colors.white,
                                      ),
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

                if (_setupError != null ||
                    session.state == CallSessionState.failed)
                  Positioned(
                    left: 20,
                    right: 20,
                    top: MediaQuery.of(context).padding.top + 96,
                    child: Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: colors.error.withValues(alpha: 0.92),
                        borderRadius: BorderRadius.circular(ChatyRadius.md),
                      ),
                      child: Text(
                        _setupError ?? 'The media connection failed.',
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),

                AnimatedPositioned(
                  duration: const Duration(milliseconds: 220),
                  top: _controlsVisible ? 0 : -110,
                  left: 0,
                  right: 0,
                  child: Container(
                    padding: EdgeInsets.fromLTRB(
                      ChatySpacing.md,
                      MediaQuery.of(context).padding.top + ChatySpacing.sm,
                      ChatySpacing.md,
                      ChatySpacing.md,
                    ),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: <Color>[
                          Colors.black.withValues(alpha: 0.76),
                          Colors.transparent,
                        ],
                      ),
                    ),
                    child: Row(
                      children: [
                        ChatyBackButton(
                          color: Colors.white,
                          backgroundColor: Colors.black.withValues(alpha: 0.35),
                        ),
                        Expanded(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                session.remoteDisplayName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 17,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                _statusLabel(session),
                                style: TextStyle(
                                  color: Colors.white.withValues(alpha: 0.72),
                                  fontSize: 13,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ],
                          ),
                        ),
                        if (session.isVideo)
                          ChatyIconButton(
                            icon: Icons.auto_awesome_rounded,
                            tooltip: 'Call filter',
                            color: Colors.white,
                            backgroundColor:
                                _effectEngine.activeEffect.id != 'none'
                                ? Colors.white.withValues(alpha: 0.28)
                                : Colors.black.withValues(alpha: 0.35),
                            onPressed: _renderersReady
                                ? _showCallEffectSheet
                                : null,
                          ),
                        ChatyIconButton(
                          icon: _focusMode
                              ? Icons.fullscreen_exit_rounded
                              : Icons.fullscreen_rounded,
                          tooltip: _focusMode
                              ? 'Exit focus mode'
                              : 'Focus mode',
                          color: Colors.white,
                          backgroundColor: Colors.black.withValues(alpha: 0.35),
                          onPressed: () =>
                              setState(() => _focusMode = !_focusMode),
                        ),
                      ],
                    ),
                  ),
                ),

                AnimatedPositioned(
                  duration: const Duration(milliseconds: 220),
                  bottom: _controlsVisible ? 0 : -150,
                  left: 0,
                  right: 0,
                  child: Container(
                    padding: EdgeInsets.fromLTRB(
                      ChatySpacing.lg,
                      ChatySpacing.lg,
                      ChatySpacing.lg,
                      MediaQuery.of(context).padding.bottom + ChatySpacing.lg,
                    ),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.bottomCenter,
                        end: Alignment.topCenter,
                        colors: <Color>[
                          Colors.black.withValues(alpha: 0.84),
                          Colors.transparent,
                        ],
                      ),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        _buildCallButton(
                          label: session.isMuted ? 'Unmute' : 'Mute',
                          icon: session.isMuted
                              ? Icons.mic_off_rounded
                              : Icons.mic_rounded,
                          isActive: session.isMuted,
                          activeColor: colors.error,
                          onTap: _callService.hasLocalMedia
                              ? _callService.toggleMute
                              : null,
                        ),
                        if (session.isVideo) ...[
                          _buildCallButton(
                            label: session.isCameraOff
                                ? 'Camera on'
                                : 'Camera off',
                            icon: session.isCameraOff
                                ? Icons.videocam_off_rounded
                                : Icons.videocam_rounded,
                            isActive: session.isCameraOff,
                            activeColor: colors.error,
                            onTap: _callService.hasLocalMedia
                                ? _callService.toggleCamera
                                : null,
                          ),
                          _buildCallButton(
                            label: 'Switch camera',
                            icon: Icons.cameraswitch_rounded,
                            isActive: false,
                            activeColor: colors.primary,
                            onTap: _callService.hasLocalMedia && !session.isCameraOff && !session.isSharingScreen
                                ? () => unawaited(_callService.switchCamera())
                                : null,
                          ),
                          _buildCallButton(
                            label: session.isSharingScreen
                                ? 'Stop sharing'
                                : 'Share screen',
                            icon: session.isSharingScreen
                                ? Icons.stop_screen_share_rounded
                                : Icons.screen_share_rounded,
                            isActive: session.isSharingScreen,
                            activeColor: const Color(0xFF38BDF8),
                            onTap: _callService.hasLocalMedia
                                ? () => unawaited(_callService.toggleScreenShare())
                                : null,
                          ),
                        ],
                        _buildCallButton(
                          label: session.audioRoute == AudioRouteType.speaker
                              ? 'Earpiece'
                              : 'Speaker',
                          icon: session.audioRoute == AudioRouteType.speaker
                              ? Icons.volume_up_rounded
                              : Icons.hearing_rounded,
                          isActive:
                              session.audioRoute == AudioRouteType.speaker,
                          activeColor: colors.primary,
                          onTap: _callService.hasLocalMedia
                              ? () => unawaited(
                                  _callService.setAudioRoute(
                                    session.audioRoute == AudioRouteType.speaker
                                        ? AudioRouteType.earpiece
                                        : AudioRouteType.speaker,
                                  ),
                                )
                              : null,
                        ),
                        Semantics(
                          button: true,
                          label: 'End call',
                          child: GestureDetector(
                            onTap: () async {
                              await _callService.endCall();
                              if (context.mounted)
                                Navigator.of(context).maybePop();
                            },
                            child: Container(
                              width: 58,
                              height: 58,
                              decoration: BoxDecoration(
                                color: colors.error,
                                shape: BoxShape.circle,
                              ),
                              alignment: Alignment.center,
                              child: Icon(
                                Icons.call_end_rounded,
                                color: colors.onError,
                                size: 28,
                              ),
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

  Widget _buildRemoteVideo(ChatyCallSession session, AppColors colors) {
    if (!_renderersReady || _callService.remoteStream == null) {
      return Container(
        color: const Color(0xFF07090D),
        alignment: Alignment.center,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            AppAvatar(
              initials:
                  session.remoteAvatarInitials ??
                  (session.remoteDisplayName.isEmpty
                      ? 'U'
                      : session.remoteDisplayName
                            .substring(0, 1)
                            .toUpperCase()),
              colorHex: session.remoteAvatarColorHex,
              size: 108,
            ),
            const SizedBox(height: 18),
            Text(
              _statusLabel(session),
              style: const TextStyle(
                color: Colors.white70,
                fontSize: 15,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 16),
            const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                strokeWidth: 2.2,
                color: Colors.white70,
              ),
            ),
          ],
        ),
      );
    }
    return _effectEngine.renderEffect(
      child: RTCVideoView(
        _remoteRenderer,
        objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
      ),
    );
  }

  /// Live filter picker for video calls. The selection applies to this
  /// device's rendering of both video surfaces.
  Future<void> _showCallEffectSheet() async {
    ChatyHaptics.selection();
    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.black87,
      builder: (sheetContext) => SafeArea(
        child: ListenableBuilder(
          listenable: _effectEngine,
          builder: (context, _) => Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 14, 20, 4),
                child: Row(
                  children: [
                    const Text(
                      'Call filter',
                      style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                        fontSize: 16,
                      ),
                    ),
                    const Spacer(),
                    Text(
                      'Applies to your view',
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: .55),
                        fontSize: 11.5,
                      ),
                    ),
                  ],
                ),
              ),
              SizedBox(
                height: 116,
                child: ListView.builder(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  itemCount: EffectRegistry.allEffects.length,
                  itemBuilder: (context, index) {
                    final effect = EffectRegistry.allEffects[index];
                    final selected = _effectEngine.activeEffect.id == effect.id;
                    return Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 6),
                      child: Semantics(
                        button: true,
                        selected: selected,
                        label: 'Filter ${effect.name}',
                        child: InkWell(
                          borderRadius: BorderRadius.circular(24),
                          onTap: () => _effectEngine.selectEffect(effect),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              CircleAvatar(
                                radius: 22,
                                backgroundColor: effect.previewColor.withValues(
                                  alpha: selected ? .95 : .45,
                                ),
                                child: Icon(
                                  effect.icon,
                                  size: 20,
                                  color: selected ? Colors.black : Colors.white,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                effect.name,
                                style: TextStyle(
                                  color: selected
                                      ? Colors.white
                                      : Colors.white60,
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),
              if (_effectEngine.activeEffect.colorMatrix != null)
                Semantics(
                  label: 'Filter strength',
                  child: SliderTheme(
                    data: const SliderThemeData(
                      activeTrackColor: Colors.white70,
                      inactiveTrackColor: Colors.white24,
                      thumbColor: Colors.white,
                      trackHeight: 2,
                    ),
                    child: Slider(
                      value: _effectEngine.intensity,
                      onChanged: _effectEngine.setIntensity,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
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
              initials:
                  session.remoteAvatarInitials ??
                  (session.remoteDisplayName.isNotEmpty
                      ? session.remoteDisplayName.substring(0, 1).toUpperCase()
                      : 'U'),
              colorHex: session.remoteAvatarColorHex,
              size: 110,
            ),
            const SizedBox(height: ChatySpacing.lg),
            Text(
              session.remoteDisplayName,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: colors.foreground,
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: ChatySpacing.xs),
            Text(
              _statusLabel(session),
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
    required String label,
    required IconData icon,
    required bool isActive,
    required Color activeColor,
    required VoidCallback? onTap,
  }) {
    return Semantics(
      button: true,
      enabled: onTap != null,
      label: label,
      child: GestureDetector(
        onTap: onTap,
        child: Opacity(
          opacity: onTap == null ? 0.45 : 1,
          child: Container(
            width: 54,
            height: 54,
            decoration: BoxDecoration(
              color: isActive
                  ? activeColor.withValues(alpha: 0.28)
                  : Colors.white.withValues(alpha: 0.16),
              shape: BoxShape.circle,
              border: Border.all(
                color: isActive
                    ? activeColor
                    : Colors.white.withValues(alpha: 0.2),
              ),
            ),
            alignment: Alignment.center,
            child: Icon(
              icon,
              color: isActive ? activeColor : Colors.white,
              size: 23,
            ),
          ),
        ),
      ),
    );
  }
}
