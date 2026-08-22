import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import '../../domain/models/call_state.dart';
import '../../domain/models/other_models.dart';
import '../repositories/mock_data_store.dart';
import 'backend_service.dart';

/// Realtime Call Signaling & Session Service for Chaty.
///
/// Features:
/// - Explicit typed call state machine (`CallSessionState`)
/// - Direct peer-to-peer signaling via Supabase Realtime broadcast channels (`chaty_calls_v1_{userId}`)
/// - Media device routing (speaker, earpiece, bluetooth)
/// - Timeout handling for missed calls
/// - Call history persistence into backend data store
class CallSignalingService extends ChangeNotifier {
  final SupabaseClient _client;
  final MockDataStore dataStore;
  final ChatyBackendService backend;

  ChatyCallSession? _currentSession;
  Timer? _ringTimeoutTimer;
  Timer? _durationTimer;
  int _callDurationSeconds = 0;

  RealtimeChannel? _signalingChannel;

  CallSignalingService({
    SupabaseClient? client,
    required this.dataStore,
    required this.backend,
  }) : _client = client ?? Supabase.instance.client;

  ChatyCallSession? get currentSession => _currentSession;
  int get callDurationSeconds => _callDurationSeconds;
  bool get isInActiveCall =>
      _currentSession != null &&
      (_currentSession!.state == CallSessionState.connecting ||
          _currentSession!.state == CallSessionState.connected ||
          _currentSession!.state == CallSessionState.reconnecting);

  /// Starts an isolated deterministic mock call session strictly for QA / UI testing.
  /// This preview session is completely client-side and does not send signals to real users.
  void startMockCallForQA({
    String remoteDisplayName = 'Alex Rivera (QA Participant)',
    String remoteAvatarInitials = 'AR',
    String remoteAvatarColorHex = '0xFF6366F1',
    bool isVideo = true,
  }) {
    if (!kDebugMode) return; // Isolated to debug / development only
    _durationTimer?.cancel();
    _currentSession = ChatyCallSession(
      callId: 'qa_mock_call_${DateTime.now().millisecondsSinceEpoch}',
      remoteUserId: 'qa_mock_user_1',
      remoteDisplayName: remoteDisplayName,
      remoteAvatarInitials: remoteAvatarInitials,
      remoteAvatarColorHex: remoteAvatarColorHex,
      isVideo: isVideo,
      isOutgoing: false,
      state: CallSessionState.connected,
      startedAt: DateTime.now(),
      connectedAt: DateTime.now(),
      audioRoute: isVideo ? AudioRouteType.speaker : AudioRouteType.earpiece,
    );
    _startDurationTimer();
    notifyListeners();
  }

  /// Outgoing call initiator
  Future<void> initiateCall({
    required String remoteUserId,
    required String remoteDisplayName,
    String? remoteAvatarInitials,
    String? remoteAvatarColorHex,
    required bool isVideo,
  }) async {
    final myId = _client.auth.currentUser?.id;
    if (myId == null) return;

    final callId = 'call_${DateTime.now().millisecondsSinceEpoch}';

    _currentSession = ChatyCallSession(
      callId: callId,
      remoteUserId: remoteUserId,
      remoteDisplayName: remoteDisplayName,
      remoteAvatarInitials: remoteAvatarInitials,
      remoteAvatarColorHex: remoteAvatarColorHex,
      isVideo: isVideo,
      isOutgoing: true,
      state: CallSessionState.initiating,
      startedAt: DateTime.now(),
      audioRoute: isVideo ? AudioRouteType.speaker : AudioRouteType.earpiece,
    );
    notifyListeners();

    // Subscribe to signaling channel
    _subscribeToChannel(myId);

    // Send call invite to remote user channel
    await _sendSignal(remoteUserId, {
      'call_id': callId,
      'from': myId,
      'from_name': backend.currentUser?.displayName ?? 'Chaty User',
      'is_video': isVideo,
      'type': 'invite',
    });

    _currentSession = _currentSession?.copyWith(
      state: CallSessionState.ringing,
    );
    notifyListeners();

    // Ring timeout: 40 seconds
    _ringTimeoutTimer?.cancel();
    _ringTimeoutTimer = Timer(const Duration(seconds: 40), () {
      if (_currentSession?.state == CallSessionState.ringing ||
          _currentSession?.state == CallSessionState.initiating) {
        endCall(reason: 'no_answer');
      }
    });
  }

  /// Incoming call handler (called when an invite broadcast arrives)
  void handleIncomingInvite({
    required String callId,
    required String fromUserId,
    required String fromDisplayName,
    String? fromAvatarInitials,
    String? fromAvatarColorHex,
    required bool isVideo,
  }) {
    // If already in a call, respond with busy
    if (_currentSession != null &&
        _currentSession!.state != CallSessionState.ended &&
        _currentSession!.state != CallSessionState.declined) {
      _sendSignal(fromUserId, {
        'call_id': callId,
        'response': 'busy',
        'type': 'response',
      });
      return;
    }

    _currentSession = ChatyCallSession(
      callId: callId,
      remoteUserId: fromUserId,
      remoteDisplayName: fromDisplayName,
      remoteAvatarInitials: fromAvatarInitials,
      remoteAvatarColorHex: fromAvatarColorHex,
      isVideo: isVideo,
      isOutgoing: false,
      state: CallSessionState.incoming,
      startedAt: DateTime.now(),
      audioRoute: isVideo ? AudioRouteType.speaker : AudioRouteType.earpiece,
    );
    notifyListeners();

    final myId = _client.auth.currentUser?.id;
    if (myId != null) _subscribeToChannel(myId);

    _ringTimeoutTimer?.cancel();
    _ringTimeoutTimer = Timer(const Duration(seconds: 40), () {
      if (_currentSession?.state == CallSessionState.incoming) {
        endCall(reason: 'no_answer');
      }
    });
  }

  /// Accept incoming call
  Future<void> acceptCall() async {
    final session = _currentSession;
    if (session == null || session.state != CallSessionState.incoming) return;

    _ringTimeoutTimer?.cancel();

    await _sendSignal(session.remoteUserId, {
      'call_id': session.callId,
      'response': 'accepted',
      'type': 'response',
    });

    _currentSession = session.copyWith(
      state: CallSessionState.connected,
      connectedAt: DateTime.now(),
    );
    _startDurationTimer();
    notifyListeners();
  }

  /// Decline incoming call
  Future<void> declineCall() async {
    final session = _currentSession;
    if (session == null || session.state != CallSessionState.incoming) return;

    _ringTimeoutTimer?.cancel();

    await _sendSignal(session.remoteUserId, {
      'call_id': session.callId,
      'response': 'declined',
      'type': 'response',
    });

    _logCallRecord(CallDirection.missed, 0);

    _currentSession = session.copyWith(
      state: CallSessionState.declined,
      endedAt: DateTime.now(),
    );
    notifyListeners();
  }

  /// End ongoing or pending call
  Future<void> endCall({String reason = 'ended'}) async {
    final session = _currentSession;
    if (session == null) return;

    _ringTimeoutTimer?.cancel();
    _stopDurationTimer();

    await _sendSignal(session.remoteUserId, {
      'call_id': session.callId,
      'response': reason == 'no_answer' ? 'cancelled' : 'ended',
      'type': 'response',
    });

    final duration = _callDurationSeconds;
    final direction = session.isOutgoing
        ? CallDirection.outgoing
        : (duration > 0 ? CallDirection.incoming : CallDirection.missed);
    _logCallRecord(direction, duration);

    _currentSession = session.copyWith(
      state: CallSessionState.ended,
      endedAt: DateTime.now(),
    );
    notifyListeners();
  }

  /// Send in-call quick reaction burst
  Future<void> sendCallReaction(String emoji) async {
    final session = _currentSession;
    if (session == null || session.state != CallSessionState.connected) return;

    await _sendSignal(session.remoteUserId, {
      'call_id': session.callId,
      'reaction': emoji,
      'type': 'reaction',
    });
  }

  /// Toggle microphone mute
  void toggleMute() {
    if (_currentSession == null) return;
    _currentSession = _currentSession!.copyWith(
      isMuted: !_currentSession!.isMuted,
    );
    notifyListeners();
  }

  /// Toggle camera on/off
  void toggleCamera() {
    if (_currentSession == null) return;
    _currentSession = _currentSession!.copyWith(
      isCameraOff: !_currentSession!.isCameraOff,
    );
    notifyListeners();
  }

  /// Switch front / back camera
  void switchCamera() {
    if (_currentSession == null) return;
    _currentSession = _currentSession!.copyWith(
      isFrontCamera: !_currentSession!.isFrontCamera,
    );
    notifyListeners();
  }

  /// Set audio output route
  void setAudioRoute(AudioRouteType route) {
    if (_currentSession == null) return;
    _currentSession = _currentSession!.copyWith(audioRoute: route);
    notifyListeners();
  }

  void _startDurationTimer() {
    _callDurationSeconds = 0;
    _durationTimer?.cancel();
    _durationTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      _callDurationSeconds++;
      notifyListeners();
    });
  }

  void _stopDurationTimer() {
    _durationTimer?.cancel();
    _durationTimer = null;
  }

  void _logCallRecord(CallDirection direction, int durationSec) {
    final session = _currentSession;
    if (session == null) return;

    final myId = _client.auth.currentUser?.id ?? '';
    final record = CallRecord(
      id: session.callId,
      callerId: session.isOutgoing ? myId : session.remoteUserId,
      participantIds: [myId, session.remoteUserId],
      type: session.isVideo ? CallType.video : CallType.voice,
      direction: direction,
      durationSeconds: durationSec,
      timestamp: session.startedAt,
    );

    // Save into repository
    dataStore.addCallRecord(record);
  }

  void _subscribeToChannel(String userId) {
    if (_signalingChannel != null) return;
    final channel = _client.channel('chaty_calls_v1_$userId');
    channel
        .onBroadcast(
          event: 'chaty_call_signal',
          callback: (payload) =>
              _handleSignalPayload(Map<String, dynamic>.from(payload)),
        )
        .subscribe();
    _signalingChannel = channel;
  }

  void _handleSignalPayload(Map<String, dynamic> payload) {
    final callId = payload['call_id']?.toString() ?? '';
    final type = payload['type']?.toString() ?? '';
    final response = payload['response']?.toString() ?? '';

    if (type == 'response' && _currentSession?.callId == callId) {
      if (response == 'accepted') {
        _ringTimeoutTimer?.cancel();
        _currentSession = _currentSession?.copyWith(
          state: CallSessionState.connected,
          connectedAt: DateTime.now(),
        );
        _startDurationTimer();
        notifyListeners();
      } else if (response == 'declined') {
        _ringTimeoutTimer?.cancel();
        _currentSession = _currentSession?.copyWith(
          state: CallSessionState.declined,
        );
        notifyListeners();
      } else if (response == 'busy') {
        _ringTimeoutTimer?.cancel();
        _currentSession = _currentSession?.copyWith(
          state: CallSessionState.busy,
        );
        notifyListeners();
      } else if (response == 'ended' || response == 'cancelled') {
        endCall(reason: response);
      }
    }
  }

  Future<void> _sendSignal(
    String toUserId,
    Map<String, dynamic> payload,
  ) async {
    final completer = Completer<void>();
    final channel = _client.channel('chaty_calls_v1_$toUserId');
    try {
      channel.subscribe((status, error) {
        if (completer.isCompleted) return;
        if (status == RealtimeSubscribeStatus.subscribed) {
          completer.complete();
        } else if (status == RealtimeSubscribeStatus.channelError ||
            status == RealtimeSubscribeStatus.timedOut) {
          completer.completeError(error ?? StateError('channel $status'));
        }
      });
      await completer.future.timeout(const Duration(seconds: 5));
      await channel.sendBroadcastMessage(
        event: 'chaty_call_signal',
        payload: payload,
      );
    } catch (_) {
      // Graceful fallback on signal delivery
    } finally {
      try {
        await _client.removeChannel(channel);
      } catch (_) {}
    }
  }

  @override
  void dispose() {
    _ringTimeoutTimer?.cancel();
    _stopDurationTimer();
    final ch = _signalingChannel;
    if (ch != null) _client.removeChannel(ch);
    super.dispose();
  }
}
