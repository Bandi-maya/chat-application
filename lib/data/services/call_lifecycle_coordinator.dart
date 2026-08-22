import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../../domain/models/call_state.dart';
import 'call_signaling_service.dart';

/// Owns process/lifecycle policy around the WebRTC call controller.
///
/// Signaling and media remain the responsibility of [CallSignalingService].
/// This coordinator only decides what to do when the transport temporarily
/// disconnects or the Flutter process is being detached.
///
/// A paused/hidden app does NOT end an active call here. Reliable long-running
/// Android background calls require the dedicated foreground-service phase;
/// until that is installed we keep the WebRTC transport alive and fail closed
/// only when Flutter is actually detached.
class CallLifecycleCoordinator with WidgetsBindingObserver {
  CallLifecycleCoordinator({
    required CallSignalingService callService,
    this.reconnectGracePeriod = const Duration(seconds: 15),
  }) : _callService = callService;

  final CallSignalingService _callService;
  final Duration reconnectGracePeriod;

  Timer? _reconnectTimer;
  bool _started = false;
  bool _disposed = false;

  bool get isStarted => _started && !_disposed;

  void start() {
    if (_started || _disposed) return;
    _started = true;
    WidgetsBinding.instance.addObserver(this);
    _callService.addListener(_handleCallStateChanged);
    _handleCallStateChanged();
  }

  void _handleCallStateChanged() {
    if (_disposed) return;
    final session = _callService.currentSession;
    if (session?.state == CallSessionState.reconnecting) {
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
      if (session?.state != CallSessionState.reconnecting) return;
      debugPrint(
        'Chaty call reconnect grace period expired for ${session?.callId}.',
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
      // app was backgrounded. initialize() is idempotent.
      unawaited(_callService.initialize());
      _handleCallStateChanged();
      return;
    }

    if (state != AppLifecycleState.detached) return;

    _cancelReconnectDeadline();
    final session = _callService.currentSession;
    if (session == null || _isTerminal(session.state)) return;

    // There is no UI/process left to own camera, microphone or the peer
    // connection. Persist the terminal call state instead of leaving a
    // ringing/connected row stranded on the server.
    unawaited(_callService.endCall(reason: 'app_detached'));
  }

  bool _isTerminal(CallSessionState state) {
    return state == CallSessionState.ended ||
        state == CallSessionState.declined ||
        state == CallSessionState.failed ||
        state == CallSessionState.missed ||
        state == CallSessionState.busy ||
        state == CallSessionState.idle;
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _cancelReconnectDeadline();
    if (_started) {
      WidgetsBinding.instance.removeObserver(this);
      _callService.removeListener(_handleCallStateChanged);
    }
    _started = false;
  }
}
