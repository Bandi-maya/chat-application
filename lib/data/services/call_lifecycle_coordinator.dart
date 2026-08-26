import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../domain/models/call_state.dart';
import 'call_foreground_service.dart';
import 'call_signaling_service.dart';

/// Owns process/lifecycle policy around the WebRTC call controller.
///
/// Signaling and media remain the responsibility of [CallSignalingService].
/// This coordinator decides how long reconnecting transports may survive,
/// synchronizes Android foreground execution with the authoritative call
/// state, and terminates stranded server/media state when Flutter detaches.
class CallLifecycleCoordinator with WidgetsBindingObserver {
  CallLifecycleCoordinator({
    required this._callService,
    required this._foregroundService,
    this.reconnectGracePeriod = const Duration(seconds: 15),
  });

  final CallSignalingService _callService;
  final ChatyCallForegroundService _foregroundService;
  final Duration reconnectGracePeriod;

  Timer? _reconnectTimer;
  bool _started = false;
  bool _disposed = false;

  bool get isStarted => _started && !_disposed;

  @visibleForTesting
  static bool needsReconnectDeadline(CallSessionState state) =>
      state == CallSessionState.reconnecting;

  @visibleForTesting
  static bool isTerminalState(CallSessionState state) => state.isTerminal;

  void start() {
    if (_started || _disposed) return;
    _started = true;
    WidgetsBinding.instance.addObserver(this);
    _callService.addListener(_handleCallStateChanged);
    unawaited(_foregroundService.initialize());
    _handleCallStateChanged();
  }

  void _handleCallStateChanged() {
    if (_disposed) return;
    final session = _callService.currentSession;
    unawaited(_foregroundService.sync(session));

    if (session != null && needsReconnectDeadline(session.state)) {
      _ensureReconnectDeadline();
      return;
    }
    _cancelReconnectDeadline();
  }

  void _ensureReconnectDeadline() {
    if (_reconnectTimer != null || _disposed) return;
    _reconnectTimer = Timer(reconnectGracePeriod, () {
      _reconnectTimer = null;
      if (_disposed) return;
      final session = _callService.currentSession;
      if (session == null || !needsReconnectDeadline(session.state)) return;
      debugPrint(
        'Chaty call reconnect grace period expired for ${session.callId}.',
      );
      unawaited(_callService.endCall(reason: 'reconnect_timeout'));
    });
  }

  void _cancelReconnectDeadline() {
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (_disposed) return;

    if (state == AppLifecycleState.resumed) {
      // Re-establish database signaling if the OS suspended sockets while the
      // app was backgrounded. This intentionally bypasses initialize()'s
      // one-time guard and also reconciles events missed while suspended.
      unawaited(_callService.reconnectSignaling());
      _handleCallStateChanged();
      return;
    }

    if (state != AppLifecycleState.detached) return;

    _cancelReconnectDeadline();
    final session = _callService.currentSession;
    if (session == null || isTerminalState(session.state)) {
      unawaited(_foregroundService.stop());
      return;
    }

    // There is no UI/main isolate left to own the WebRTC peer connection.
    // Persist the terminal call state instead of leaving a ringing/connected
    // row stranded. The foreground service is configured stopWithTask=true,
    // so task removal cannot masquerade as a surviving call.
    unawaited(_callService.endCall(reason: 'app_detached'));
    unawaited(_foregroundService.stop());
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _cancelReconnectDeadline();
    if (_started) {
      WidgetsBinding.instance.removeObserver(this);
      _callService.removeListener(_handleCallStateChanged);
    }
    unawaited(_foregroundService.stop());
    _started = false;
  }
}
