import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../data/services/native_call_pip_service.dart';
import '../../data/services/call_signaling_service.dart';
import '../settings/calls/call_presentation_preferences.dart';

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
class CallPresentationController extends ChangeNotifier
    with WidgetsBindingObserver {
  /// Process-wide presentation signal used by globally hosted call surfaces.
  ///
  /// The app shell builds the generic activity capsule independently from the
  /// feature call overlays. Publishing the authoritative mode here lets the
  /// capsule suppress itself whenever an island or PiP surface owns the call,
  /// preventing two minimized controls from being shown at once.
  static final ValueNotifier<CallPresentationMode> presentationModeSignal =
      ValueNotifier<CallPresentationMode>(CallPresentationMode.none);

  final CallSignalingService _callService;
  final CallPresentationPreferencesStore _preferences;

  CallPresentationMode _mode = CallPresentationMode.none;
  int _fullScreenPresentations = 0;
  bool _isBackgrounded = false;
  StreamSubscription<bool>? _nativePipSubscription;

  CallPresentationController({
    required CallSignalingService callService,
    CallPresentationPreferencesStore? preferences,
  })
    // ignore: prefer_initializing_formals
    : _callService = callService,
       _preferences = preferences ?? CallPresentationPreferencesStore.instance {
    WidgetsBinding.instance.addObserver(this);
    _callService.addListener(_handleCallStateChanged);
    _preferences.addListener(_reconcilePresentationMode);
    unawaited(
      _preferences.initialize().then((_) => _reconcilePresentationMode()),
    );
    _nativePipSubscription = NativeCallPipService().modeChanges.listen(
      reportNativePipMode,
    );
    _handleCallStateChanged();
  }

  CallPresentationMode get mode => _mode;

  bool get isFullScreen => _mode == CallPresentationMode.fullScreen;
  bool get isInAppVideoPip => _mode == CallPresentationMode.inAppVideoPip;
  bool get isInAppIsland => _mode == CallPresentationMode.inAppIsland;
  bool get isSystemPip => _mode == CallPresentationMode.systemPip;
  bool get isBackgroundNotification =>
      _mode == CallPresentationMode.backgroundNotification;
  bool get isNone => _mode == CallPresentationMode.none;

  /// Invoked when [OngoingCallScreen] is mounted or pushed to full-screen.
  void showFullScreen() {
    _fullScreenPresentations = 1;
    _mode = CallPresentationMode.fullScreen;
    _publishMode();
    notifyListeners();
  }

  /// Invoked when [OngoingCallScreen] is unmounted or user navigates back into Chaty.
  void minimizeToPipOrIsland() {
    _fullScreenPresentations = 0;
    final session = _callService.currentSession;
    if (session == null || !session.isActive) {
      _mode = CallPresentationMode.none;
    } else if (session.isVideo && _preferences.value.pictureInPictureEnabled) {
      _mode = CallPresentationMode.inAppVideoPip;
    } else if (_preferences.value.dynamicIslandEnabled) {
      _mode = CallPresentationMode.inAppIsland;
    } else {
      _mode = CallPresentationMode.none;
    }
    _publishAndNotify();
  }

  /// Transition from Video PiP to Call Island.
  void collapseToIsland() {
    if (_mode == CallPresentationMode.inAppVideoPip) {
      _mode = _preferences.value.dynamicIslandEnabled
          ? CallPresentationMode.inAppIsland
          : CallPresentationMode.none;
      _publishAndNotify();
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
    _publishAndNotify();
  }

  /// Expand floating Video PiP to full screen.
  void expandToFullScreen() {
    final session = _callService.currentSession;
    if (session == null || !session.isActive || _isBackgrounded) return;
    _mode = CallPresentationMode.fullScreen;
    _fullScreenPresentations = 1;
    _publishAndNotify();
  }

  /// Force a specific presentation mode if allowable.
  void setMode(CallPresentationMode nextMode) {
    final session = _callService.currentSession;
    if (nextMode != CallPresentationMode.none &&
        (session == null || !session.isActive)) {
      return;
    }
    if (nextMode == CallPresentationMode.inAppVideoPip &&
        session?.isVideo != true) {
      return;
    }
    if (nextMode == CallPresentationMode.systemPip) {
      // Native PiP must be confirmed through reportNativePipMode(). Merely
      // backgrounding the app is not proof that Android entered PiP.
      return;
    }
    if (_mode == nextMode) return;
    _mode = nextMode;
    _publishAndNotify();
  }

  /// Synchronizes presentation with the native Android PiP callback.
  void reportNativePipMode(bool active) {
    final session = _callService.currentSession;
    if (active) {
      if (!_preferences.value.pictureInPictureEnabled) return;
      if (session == null || !session.isActive || !session.isVideo) return;
      _mode = CallPresentationMode.systemPip;
      _publishAndNotify();
      return;
    }
    if (_mode != CallPresentationMode.systemPip) return;
    _mode = _isBackgrounded
        ? CallPresentationMode.backgroundNotification
        : CallPresentationMode.none;
    _reconcilePresentationMode();
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
        _publishAndNotify();
      }
      return;
    }

    if (_isBackgrounded) {
      // Android PiP is entered explicitly and confirmed through
      // reportNativePipMode(). Until then the foreground notification is the
      // truthful system-level presentation for both audio and video.
      final targetBg = _mode == CallPresentationMode.systemPip
          ? CallPresentationMode.systemPip
          : CallPresentationMode.backgroundNotification;
      if (_mode != targetBg) {
        _mode = targetBg;
        _publishAndNotify();
      }
      return;
    }

    if (_fullScreenPresentations > 0) {
      if (_mode != CallPresentationMode.fullScreen) {
        _mode = CallPresentationMode.fullScreen;
        _publishAndNotify();
      }
      return;
    }

    // Call is active in foreground:
    if (_mode == CallPresentationMode.inAppIsland &&
        _preferences.value.dynamicIslandEnabled) {
      return; // Preserve user's minimized choice
    }

    final targetMode =
        session.isVideo && _preferences.value.pictureInPictureEnabled
        ? CallPresentationMode.inAppVideoPip
        : _preferences.value.dynamicIslandEnabled
        ? CallPresentationMode.inAppIsland
        : CallPresentationMode.none;

    if (_mode != targetMode) {
      _mode = targetMode;
      _publishAndNotify();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _isBackgrounded =
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden ||
        state == AppLifecycleState.detached;
    _reconcilePresentationMode();
  }

  void _publishMode() {
    if (presentationModeSignal.value != _mode) {
      presentationModeSignal.value = _mode;
    }
  }

  void _publishAndNotify() {
    _publishMode();
    notifyListeners();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _callService.removeListener(_handleCallStateChanged);
    _preferences.removeListener(_reconcilePresentationMode);
    unawaited(_nativePipSubscription?.cancel());
    if (presentationModeSignal.value == _mode) {
      presentationModeSignal.value = CallPresentationMode.none;
    }
    super.dispose();
  }
}
