import 'package:flutter/widgets.dart';

import '../../data/services/call_signaling_service.dart';

/// Authoritative modes for rendering active call state in Chaty.
enum CallPresentationMode {
  /// No call presentation active or call is ended.
  none,

  /// Full-screen OngoingCallScreen is active; in-app floating PiP and Call Island are strictly hidden.
  fullScreen,

  /// Compact iOS-Dynamic-Island-inspired call indicator at top-center.
  inAppIsland,

  /// In-app floating draggable video PiP window.
  inAppVideoPip,

  /// Android OS native Picture-in-Picture window owns the presentation.
  systemPip,

  /// Minimized to Android OS foreground service call notification.
  backgroundNotification,
}

/// Single authoritative manager for the active call UI presentation state machine.
class CallPresentationController extends ChangeNotifier with WidgetsBindingObserver {
  final CallSignalingService _callService;

  CallPresentationMode _mode = CallPresentationMode.none;
  int _fullScreenPresentations = 0;
  bool _isBackgrounded = false;

  CallPresentationController({required CallSignalingService callService})
      // ignore: prefer_initializing_formals
      : _callService = callService {
    WidgetsBinding.instance.addObserver(this);
    _callService.addListener(_handleCallStateChanged);
    _handleCallStateChanged();
  }

  CallPresentationMode get mode => _mode;

  bool get isFullScreen => _mode == CallPresentationMode.fullScreen;
  bool get isInAppVideoPip => _mode == CallPresentationMode.inAppVideoPip;
  bool get isInAppIsland => _mode == CallPresentationMode.inAppIsland;
  bool get isSystemPip => _mode == CallPresentationMode.systemPip;
  bool get isBackgroundNotification => _mode == CallPresentationMode.backgroundNotification;
  bool get isNone => _mode == CallPresentationMode.none;

  /// Invoked when [OngoingCallScreen] is mounted or pushed to full-screen.
  void showFullScreen() {
    _fullScreenPresentations = 1;
    _mode = CallPresentationMode.fullScreen;
    notifyListeners();
  }

  /// Invoked when [OngoingCallScreen] is unmounted or user navigates back into Chaty.
  void minimizeToPipOrIsland() {
    _fullScreenPresentations = 0;
    final session = _callService.currentSession;
    if (session == null || !session.isActive) {
      _mode = CallPresentationMode.none;
    } else if (session.isVideo) {
      _mode = CallPresentationMode.inAppVideoPip;
    } else {
      _mode = CallPresentationMode.inAppIsland;
    }
    notifyListeners();
  }

  /// Transition from Video PiP to Call Island.
  void collapseToIsland() {
    if (_mode == CallPresentationMode.inAppVideoPip) {
      _mode = CallPresentationMode.inAppIsland;
      notifyListeners();
    }
  }

  /// Transition from Call Island back to Floating PiP (if video call) or full screen.
  void expandFromIsland() {
    final session = _callService.currentSession;
    if (session == null || !session.isActive) {
      _mode = CallPresentationMode.none;
    } else if (session.isVideo) {
      _mode = CallPresentationMode.inAppVideoPip;
    } else {
      _mode = CallPresentationMode.fullScreen;
    }
    notifyListeners();
  }

  /// Expand floating Video PiP to full screen.
  void expandToFullScreen() {
    _mode = CallPresentationMode.fullScreen;
    notifyListeners();
  }

  /// Force a specific presentation mode if allowable.
  void setMode(CallPresentationMode nextMode) {
    if (_mode == nextMode) return;
    _mode = nextMode;
    notifyListeners();
  }

  void _handleCallStateChanged() {
    _reconcilePresentationMode();
  }

  void _reconcilePresentationMode() {
    final session = _callService.currentSession;
    final isActive = session != null && session.isActive;

    if (!isActive) {
      if (_mode != CallPresentationMode.none) {
        _mode = CallPresentationMode.none;
        _fullScreenPresentations = 0;
        notifyListeners();
      }
      return;
    }

    if (_isBackgrounded) {
      final targetBg = session.isVideo
          ? CallPresentationMode.systemPip
          : CallPresentationMode.backgroundNotification;
      if (_mode != targetBg) {
        _mode = targetBg;
        notifyListeners();
      }
      return;
    }

    if (_fullScreenPresentations > 0) {
      if (_mode != CallPresentationMode.fullScreen) {
        _mode = CallPresentationMode.fullScreen;
        notifyListeners();
      }
      return;
    }

    // Call is active in foreground:
    if (_mode == CallPresentationMode.inAppIsland) {
      return; // Preserve user's minimized choice
    }

    final targetMode = session.isVideo
        ? CallPresentationMode.inAppVideoPip
        : CallPresentationMode.inAppIsland;

    if (_mode != targetMode) {
      _mode = targetMode;
      notifyListeners();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _isBackgrounded = state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden ||
        state == AppLifecycleState.detached;
    _reconcilePresentationMode();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _callService.removeListener(_handleCallStateChanged);
    super.dispose();
  }
}
