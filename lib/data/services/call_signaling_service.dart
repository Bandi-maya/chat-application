import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../domain/models/call_state.dart';
import '../../domain/models/other_models.dart';
import '../repositories/chaty_data_store.dart';
import 'backend_service.dart';

/// Production call-session controller.
///
/// Supabase Postgres + RLS is the signaling source of truth. WebRTC is the
/// media source of truth. A session is never marked connected merely because
/// an invite/answer signal was exchanged: only RTCPeerConnection reaching the
/// connected state starts the call timer and exposes a connected UI state.
class CallSignalingService extends ChangeNotifier {
  final SupabaseClient _client;
  final ChatyDataStore dataStore;
  final ChatyBackendService backend;
  final Uuid _uuid = const Uuid();

  ChatyCallSession? _currentSession;
  Timer? _ringTimeoutTimer;
  Timer? _durationTimer;
  int _callDurationSeconds = 0;

  RTCPeerConnection? _peerConnection;
  MediaStream? _localStream;
  MediaStream? _remoteStream;
  final List<RTCIceCandidate> _queuedRemoteCandidates = <RTCIceCandidate>[];
  bool _remoteDescriptionReady = false;

  RealtimeChannel? _sessionChannel;
  RealtimeChannel? _candidateChannel;
  StreamSubscription<AuthState>? _authSubscription;
  bool _initialized = false;
  Future<void>? _initializeFuture;

  CallSignalingService({
    SupabaseClient? client,
    required this.dataStore,
    required this.backend,
  }) : _client = client ?? Supabase.instance.client {
    unawaited(initialize());
  }

  ChatyCallSession? get currentSession => _currentSession;
  int get callDurationSeconds => _callDurationSeconds;
  MediaStream? get localStream => _localStream;
  MediaStream? get remoteStream => _remoteStream;
  bool get hasLocalMedia => _localStream != null;
  bool get hasRemoteMedia => _remoteStream != null;

  bool get isInActiveCall =>
      _currentSession != null &&
      (_currentSession!.state == CallSessionState.connecting ||
          _currentSession!.state == CallSessionState.connected ||
          _currentSession!.state == CallSessionState.reconnecting);

  Future<void> initialize() {
    if (_initialized) return Future<void>.value();
    return _initializeFuture ??= _initialize();
  }

  Future<void> _initialize() async {
    _authSubscription ??= _client.auth.onAuthStateChange.listen((state) {
      if (state.session == null) {
        unawaited(_resetForSignedOutSession());
      } else {
        unawaited(_subscribeDatabaseSignaling());
        unawaited(_hydrateLatestIncomingCall());
      }
    });

    if (_client.auth.currentSession != null) {
      await _subscribeDatabaseSignaling();
      await _hydrateLatestIncomingCall();
    }
    _initialized = true;
  }

  /// Starts a real outgoing WebRTC call. The direct conversation and call row
  /// are authorized by server-side RPC/RLS before the remote peer can ring.
  Future<void> initiateCall({
    required String remoteUserId,
    required String remoteDisplayName,
    String? remoteAvatarInitials,
    String? remoteAvatarColorHex,
    required bool isVideo,
  }) async {
    await initialize();
    final myId = _client.auth.currentUser?.id;
    if (myId == null) throw StateError('Authentication required.');
    if (remoteUserId.isEmpty || remoteUserId == myId) {
      throw ArgumentError('A different call participant is required.');
    }
    if (_currentSession != null &&
        _currentSession!.state != CallSessionState.ended &&
        _currentSession!.state != CallSessionState.declined &&
        _currentSession!.state != CallSessionState.failed) {
      throw StateError('Another call is already active.');
    }

    await _disposeMediaTransport();
    final conversationRaw = await _client.rpc(
      'create_direct_conversation',
      params: <String, dynamic>{'p_other_user_id': remoteUserId},
    );
    final conversationId = conversationRaw?.toString() ?? '';
    if (conversationId.isEmpty) {
      throw StateError('Unable to establish an authorized conversation.');
    }

    final callId = _uuid.v4();
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

    try {
      await _createMediaTransport(isVideo: isVideo);
      final offer = await _peerConnection!.createOffer(<String, dynamic>{
        'offerToReceiveAudio': true,
        'offerToReceiveVideo': isVideo,
      });
      await _peerConnection!.setLocalDescription(offer);
      final offerSdp = offer.sdp;
      if (offerSdp == null || offerSdp.isEmpty) {
        throw StateError('WebRTC did not produce an SDP offer.');
      }

      await _client.from('call_sessions').insert(<String, dynamic>{
        'id': callId,
        'conversation_id': conversationId,
        'caller_id': myId,
        'callee_id': remoteUserId,
        'kind': isVideo ? 'video' : 'audio',
        'status': 'ringing',
        'offer_sdp': offerSdp,
      });

      _currentSession = _currentSession?.copyWith(
        state: CallSessionState.ringing,
      );
      notifyListeners();

      _ringTimeoutTimer?.cancel();
      _ringTimeoutTimer = Timer(const Duration(seconds: 40), () {
        final state = _currentSession?.state;
        if (state == CallSessionState.ringing ||
            state == CallSessionState.initiating ||
            state == CallSessionState.connecting) {
          unawaited(endCall(reason: 'no_answer'));
        }
      });
    } catch (error) {
      await _failCurrentCall(error);
      rethrow;
    }
  }

  /// Accepts an incoming call by building a real peer connection, applying the
  /// stored SDP offer, creating an answer and persisting it through the guarded
  /// server RPC. The state remains `connecting` until WebRTC itself connects.
  Future<void> acceptCall() async {
    await initialize();
    var session = _currentSession;
    if (session == null || session.state != CallSessionState.incoming) {
      await _hydrateLatestIncomingCall();
      session = _currentSession;
    }
    if (session == null || session.state != CallSessionState.incoming) {
      throw StateError('No authorized incoming call is available.');
    }

    _ringTimeoutTimer?.cancel();
    try {
      final row = await _client
          .from('call_sessions')
          .select()
          .eq('id', session.callId)
          .single();
      final offerSdp = row['offer_sdp']?.toString() ?? '';
      if (offerSdp.isEmpty) throw StateError('Incoming call has no SDP offer.');

      await _createMediaTransport(isVideo: session.isVideo);
      await _peerConnection!.setRemoteDescription(
        RTCSessionDescription(offerSdp, 'offer'),
      );
      _remoteDescriptionReady = true;
      await _flushRemoteCandidates();

      final answer = await _peerConnection!.createAnswer(<String, dynamic>{
        'offerToReceiveAudio': true,
        'offerToReceiveVideo': session.isVideo,
      });
      await _peerConnection!.setLocalDescription(answer);
      final answerSdp = answer.sdp;
      if (answerSdp == null || answerSdp.isEmpty) {
        throw StateError('WebRTC did not produce an SDP answer.');
      }

      await _client.rpc(
        'accept_call_session',
        params: <String, dynamic>{
          'p_call_id': session.callId,
          'p_answer_sdp': answerSdp,
        },
      );
      _currentSession = session.copyWith(state: CallSessionState.connecting);
      notifyListeners();
    } catch (error) {
      await _failCurrentCall(error);
      rethrow;
    }
  }

  Future<void> declineCall() async {
    await initialize();
    final session = _currentSession;
    if (session == null || session.state != CallSessionState.incoming) return;

    _ringTimeoutTimer?.cancel();
    try {
      await _client.rpc(
        'decline_call_session',
        params: <String, dynamic>{'p_call_id': session.callId},
      );
    } finally {
      _logCallRecord(CallDirection.missed, 0);
      _currentSession = session.copyWith(
        state: CallSessionState.declined,
        endedAt: DateTime.now(),
      );
      await _disposeMediaTransport();
      notifyListeners();
    }
  }

  Future<void> endCall({String reason = 'ended'}) async {
    final session = _currentSession;
    if (session == null) return;

    _ringTimeoutTimer?.cancel();
    _stopDurationTimer();
    final duration = _callDurationSeconds;
    try {
      await _client.rpc(
        'end_call_session',
        params: <String, dynamic>{'p_call_id': session.callId},
      );
    } catch (error, stackTrace) {
      debugPrint('Chaty call end persistence failed: $error\n$stackTrace');
    } finally {
      final direction = session.isOutgoing
          ? CallDirection.outgoing
          : (duration > 0 ? CallDirection.incoming : CallDirection.missed);
      _logCallRecord(direction, duration);
      _currentSession = session.copyWith(
        state: CallSessionState.ended,
        endedAt: DateTime.now(),
      );
      await _disposeMediaTransport();
      notifyListeners();
    }
  }

  Future<void> sendCallReaction(String emoji) async {
    throw UnsupportedError('Call reactions are not available in production yet.');
  }

  void toggleMute() {
    final session = _currentSession;
    final stream = _localStream;
    if (session == null || stream == null) return;
    final nextMuted = !session.isMuted;
    for (final track in stream.getAudioTracks()) {
      track.enabled = !nextMuted;
    }
    _currentSession = session.copyWith(isMuted: nextMuted);
    notifyListeners();
  }

  void toggleCamera() {
    final session = _currentSession;
    final stream = _localStream;
    if (session == null || stream == null || !session.isVideo) return;
    final nextCameraOff = !session.isCameraOff;
    for (final track in stream.getVideoTracks()) {
      track.enabled = !nextCameraOff;
    }
    _currentSession = session.copyWith(isCameraOff: nextCameraOff);
    notifyListeners();
  }

  MediaStream? _screenStream;

  bool get isSharingScreen => _currentSession?.isSharingScreen ?? false;

  Future<void> startScreenShare() async {
    final session = _currentSession;
    final peer = _peerConnection;
    if (session == null || peer == null || !session.isVideo) return;
    if (session.isSharingScreen) return;

    try {
      final screenStream = await navigator.mediaDevices.getDisplayMedia(<String, dynamic>{
        'video': true,
        'audio': false,
      });
      final screenTracks = screenStream.getVideoTracks();
      if (screenTracks.isEmpty) return;

      final senders = await peer.getSenders();
      final videoSender = senders.where((s) => s.track?.kind == 'video').firstOrNull;
      if (videoSender != null) {
        await videoSender.replaceTrack(screenTracks.first);
      }

      _screenStream = screenStream;
      screenTracks.first.onEnded = () {
        unawaited(stopScreenShare());
      };

      _currentSession = session.copyWith(isSharingScreen: true);
      notifyListeners();
    } catch (error, stackTrace) {
      debugPrint('Chaty screen sharing failed: $error\n$stackTrace');
      rethrow;
    }
  }

  Future<void> stopScreenShare() async {
    final session = _currentSession;
    final peer = _peerConnection;
    if (session == null || !session.isSharingScreen) return;

    final screenStream = _screenStream;
    _screenStream = null;
    if (screenStream != null) {
      for (final track in screenStream.getTracks()) {
        await track.stop();
      }
      await screenStream.dispose();
    }

    final localVideoTrack = _localStream?.getVideoTracks().firstOrNull;
    if (peer != null && localVideoTrack != null) {
      try {
        final senders = await peer.getSenders();
        final videoSender = senders.where((s) => s.track?.kind == 'video').firstOrNull;
        if (videoSender != null) {
          await videoSender.replaceTrack(localVideoTrack);
        }
      } catch (error) {
        debugPrint('Chaty screen share restore video track failed: $error');
      }
    }

    _currentSession = session.copyWith(isSharingScreen: false);
    notifyListeners();
  }

  Future<void> toggleScreenShare() async {
    if (isSharingScreen) {
      await stopScreenShare();
    } else {
      await startScreenShare();
    }
  }

  Future<void> switchCamera() async {
    final session = _currentSession;
    final stream = _localStream;
    if (session == null || stream == null || !session.isVideo) return;
    if (session.isSharingScreen) return;
    final tracks = stream.getVideoTracks();
    if (tracks.isEmpty) return;
    final switched = await Helper.switchCamera(tracks.first);
    if (!switched) return;
    _currentSession = session.copyWith(
      isFrontCamera: !session.isFrontCamera,
    );
    notifyListeners();
  }

  Future<void> setAudioRoute(AudioRouteType route) async {
    final session = _currentSession;
    if (session == null) return;
    if (route != AudioRouteType.speaker && route != AudioRouteType.earpiece) {
      throw UnsupportedError('Select Bluetooth/headset through the system audio route.');
    }
    await Helper.setSpeakerphoneOn(route == AudioRouteType.speaker);
    _currentSession = session.copyWith(audioRoute: route);
    notifyListeners();
  }

  Future<void> _createMediaTransport({required bool isVideo}) async {
    await _disposeMediaTransport();
    final iceServers = await _loadIceServers();
    final peer = await createPeerConnection(<String, dynamic>{
      'iceServers': iceServers,
      'sdpSemantics': 'unified-plan',
    });
    _peerConnection = peer;

    peer.onIceCandidate = (candidate) {
      if (candidate.candidate == null || candidate.candidate!.isEmpty) return;
      unawaited(_persistLocalCandidate(candidate));
    };
    peer.onTrack = (event) {
      if (event.streams.isNotEmpty) {
        _remoteStream = event.streams.first;
        notifyListeners();
      }
    };
    peer.onConnectionState = _handlePeerConnectionState;
    peer.onIceConnectionState = (state) {
      if (state == RTCIceConnectionState.RTCIceConnectionStateFailed) {
        _markTransportFailed('ICE negotiation failed.');
      }
    };

    await _openLocalMedia(isVideo: isVideo);
    final stream = _localStream!;
    for (final track in stream.getTracks()) {
      await peer.addTrack(track, stream);
    }
    if (isVideo) {
      await Helper.setSpeakerphoneOn(true);
    }
  }

  Future<void> _openLocalMedia({required bool isVideo}) async {
    if (_localStream != null) return;
    _localStream = await navigator.mediaDevices.getUserMedia(<String, dynamic>{
      'audio': true,
      'video': isVideo
          ? <String, dynamic>{
              'facingMode': 'user',
              'width': <String, dynamic>{'ideal': 1280},
              'height': <String, dynamic>{'ideal': 720},
              'frameRate': <String, dynamic>{'ideal': 30},
            }
          : false,
    });
    notifyListeners();
  }

  Future<List<Map<String, dynamic>>> _loadIceServers() async {
    final response = await _client.functions.invoke(
      'turn-credentials',
      body: const <String, dynamic>{},
    );
    if (response.status != 200) {
      throw StateError(
        'Secure call relay is not configured. TURN credentials are required before calls can start.',
      );
    }
    final data = Map<String, dynamic>.from(response.data as Map);
    final rawServers = data['ice_servers'];
    if (rawServers is! List || rawServers.isEmpty) {
      throw StateError('The call relay returned no ICE servers.');
    }
    return rawServers
        .whereType<Map>()
        .map((server) => Map<String, dynamic>.from(server))
        .toList(growable: false);
  }

  Future<void> _subscribeDatabaseSignaling() async {
    final userId = _client.auth.currentUser?.id;
    if (userId == null) return;
    await _removeDatabaseChannels();

    final sessionChannel = _client.channel('chaty-call-sessions-$userId');
    sessionChannel.onPostgresChanges(
      event: PostgresChangeEvent.all,
      schema: 'public',
      table: 'call_sessions',
      callback: (payload) {
        final row = Map<String, dynamic>.from(payload.newRecord);
        if (row.isNotEmpty) unawaited(_handleCallSessionRow(row));
      },
    ).subscribe();
    _sessionChannel = sessionChannel;

    final candidateChannel = _client.channel('chaty-call-candidates-$userId');
    candidateChannel.onPostgresChanges(
      event: PostgresChangeEvent.insert,
      schema: 'public',
      table: 'call_ice_candidates',
      callback: (payload) {
        final row = Map<String, dynamic>.from(payload.newRecord);
        if (row.isNotEmpty) unawaited(_handleRemoteCandidateRow(row));
      },
    ).subscribe();
    _candidateChannel = candidateChannel;
  }

  Future<void> _hydrateLatestIncomingCall() async {
    final userId = _client.auth.currentUser?.id;
    if (userId == null || isInActiveCall) return;
    try {
      final rows = await _client
          .from('call_sessions')
          .select()
          .eq('callee_id', userId)
          .eq('status', 'ringing')
          .order('started_at', ascending: false)
          .limit(1);
      if (rows.isNotEmpty) {
        await _handleCallSessionRow(Map<String, dynamic>.from(rows.first));
      }
    } catch (error, stackTrace) {
      debugPrint('Chaty incoming-call hydration failed: $error\n$stackTrace');
    }
  }

  Future<void> _handleCallSessionRow(Map<String, dynamic> row) async {
    final myId = _client.auth.currentUser?.id;
    final callId = row['id']?.toString() ?? '';
    final callerId = row['caller_id']?.toString() ?? '';
    final calleeId = row['callee_id']?.toString() ?? '';
    final status = row['status']?.toString() ?? '';
    if (myId == null ||
        callId.isEmpty ||
        (callerId != myId && calleeId != myId)) {
      return;
    }

    final active = _currentSession;
    if (active != null && active.callId != callId && isInActiveCall) return;

    if (status == 'ringing' &&
        calleeId == myId &&
        (_currentSession == null || _currentSession!.callId != callId)) {
      final remote = await _resolveParticipantPresentation(
        callerId,
        row['conversation_id']?.toString() ?? '',
      );
      _currentSession = ChatyCallSession(
        callId: callId,
        remoteUserId: callerId,
        remoteDisplayName: remote['name'] ?? 'Chaty contact',
        remoteAvatarInitials: remote['initials'],
        remoteAvatarColorHex: remote['color'],
        isVideo: row['kind']?.toString() == 'video',
        isOutgoing: false,
        state: CallSessionState.incoming,
        startedAt:
            DateTime.tryParse(row['started_at']?.toString() ?? '')?.toLocal() ??
            DateTime.now(),
        audioRoute: row['kind']?.toString() == 'video'
            ? AudioRouteType.speaker
            : AudioRouteType.earpiece,
      );
      notifyListeners();
      return;
    }

    if (_currentSession?.callId != callId) return;

    if (status == 'accepted' && _currentSession!.isOutgoing) {
      final answerSdp = row['answer_sdp']?.toString() ?? '';
      if (answerSdp.isNotEmpty &&
          !_remoteDescriptionReady &&
          _peerConnection != null) {
        _ringTimeoutTimer?.cancel();
        await _peerConnection!.setRemoteDescription(
          RTCSessionDescription(answerSdp, 'answer'),
        );
        _remoteDescriptionReady = true;
        _currentSession = _currentSession?.copyWith(
          state: CallSessionState.connecting,
        );
        await _flushRemoteCandidates();
        notifyListeners();
      }
      return;
    }

    if (status == 'declined') {
      _ringTimeoutTimer?.cancel();
      _stopDurationTimer();
      _currentSession = _currentSession?.copyWith(
        state: CallSessionState.declined,
        endedAt: DateTime.now(),
      );
      await _disposeMediaTransport();
      notifyListeners();
      return;
    }

    if (status == 'ended' || status == 'failed') {
      _ringTimeoutTimer?.cancel();
      _stopDurationTimer();
      _currentSession = _currentSession?.copyWith(
        state: status == 'failed'
            ? CallSessionState.failed
            : CallSessionState.ended,
        endedAt: DateTime.now(),
      );
      await _disposeMediaTransport();
      notifyListeners();
    }
  }

  Future<Map<String, String?>> _resolveParticipantPresentation(
    String userId,
    String conversationId,
  ) async {
    final cached = backend.getUserById(userId);
    if (cached != null) {
      return <String, String?>{
        'name': cached.displayName,
        'initials': cached.avatarInitials,
        'color': cached.avatarColorHex,
      };
    }
    if (conversationId.isEmpty) return const <String, String?>{};
    try {
      final raw = await _client.rpc(
        'get_conversation_members',
        params: <String, dynamic>{'p_conversation_id': conversationId},
      );
      if (raw is List) {
        for (final item in raw.whereType<Map>()) {
          final row = Map<String, dynamic>.from(item);
          final id =
              row['user_id']?.toString() ?? row['id']?.toString() ?? '';
          if (id != userId) continue;
          final name =
              row['display_name']?.toString() ?? row['username']?.toString();
          final initials = row['avatar_initials']?.toString();
          return <String, String?>{
            'name': name,
            'initials': initials,
            'color': row['avatar_color_hex']?.toString(),
          };
        }
      }
    } catch (error, stackTrace) {
      debugPrint('Chaty call participant lookup failed: $error\n$stackTrace');
    }
    return const <String, String?>{};
  }

  Future<void> _persistLocalCandidate(RTCIceCandidate candidate) async {
    final session = _currentSession;
    final myId = _client.auth.currentUser?.id;
    if (session == null || myId == null) return;
    try {
      await _client.from('call_ice_candidates').insert(<String, dynamic>{
        'call_id': session.callId,
        'sender_id': myId,
        'candidate': candidate.candidate,
        'sdp_mid': candidate.sdpMid,
        'sdp_mline_index': candidate.sdpMLineIndex,
      });
    } catch (error, stackTrace) {
      debugPrint('Chaty ICE candidate persistence failed: $error\n$stackTrace');
    }
  }

  Future<void> _handleRemoteCandidateRow(Map<String, dynamic> row) async {
    final session = _currentSession;
    final myId = _client.auth.currentUser?.id;
    if (session == null || myId == null || _peerConnection == null) return;
    if (row['call_id']?.toString() != session.callId) return;
    if (row['sender_id']?.toString() == myId) return;

    final candidateText = row['candidate']?.toString();
    if (candidateText == null || candidateText.isEmpty) return;
    final indexRaw = row['sdp_mline_index'];
    final index = indexRaw is int
        ? indexRaw
        : int.tryParse(indexRaw?.toString() ?? '');
    final candidate = RTCIceCandidate(
      candidateText,
      row['sdp_mid']?.toString(),
      index,
    );
    if (!_remoteDescriptionReady) {
      _queuedRemoteCandidates.add(candidate);
      return;
    }
    await _peerConnection!.addCandidate(candidate);
  }

  Future<void> _flushRemoteCandidates() async {
    final peer = _peerConnection;
    if (peer == null || !_remoteDescriptionReady) return;
    final pending = List<RTCIceCandidate>.from(_queuedRemoteCandidates);
    _queuedRemoteCandidates.clear();
    for (final candidate in pending) {
      await peer.addCandidate(candidate);
    }
  }

  void _handlePeerConnectionState(RTCPeerConnectionState state) {
    final session = _currentSession;
    if (session == null) return;
    switch (state) {
      case RTCPeerConnectionState.RTCPeerConnectionStateConnected:
        _ringTimeoutTimer?.cancel();
        _currentSession = session.copyWith(
          state: CallSessionState.connected,
          connectedAt: DateTime.now(),
        );
        _startDurationTimer();
        notifyListeners();
        break;
      case RTCPeerConnectionState.RTCPeerConnectionStateDisconnected:
        if (session.state == CallSessionState.connected) {
          _currentSession = session.copyWith(
            state: CallSessionState.reconnecting,
          );
          notifyListeners();
        }
        break;
      case RTCPeerConnectionState.RTCPeerConnectionStateFailed:
        _markTransportFailed('WebRTC peer connection failed.');
        break;
      case RTCPeerConnectionState.RTCPeerConnectionStateClosed:
        _stopDurationTimer();
        break;
      default:
        break;
    }
  }

  void _markTransportFailed(String message) {
    final session = _currentSession;
    if (session == null || session.state == CallSessionState.failed) return;
    _ringTimeoutTimer?.cancel();
    _stopDurationTimer();
    _currentSession = session.copyWith(
      state: CallSessionState.failed,
      endedAt: DateTime.now(),
    );
    debugPrint('Chaty call transport failed: $message');
    notifyListeners();
    unawaited(_markServerCallFailed(session.callId));
    unawaited(_disposeMediaTransport());
  }

  Future<void> _markServerCallFailed(String callId) async {
    try {
      await _client
          .from('call_sessions')
          .update(<String, dynamic>{
            'status': 'failed',
            'ended_at': DateTime.now().toUtc().toIso8601String(),
            'ended_by': _client.auth.currentUser?.id,
          })
          .eq('id', callId);
    } catch (error, stackTrace) {
      debugPrint('Chaty failed-call persistence failed: $error\n$stackTrace');
    }
  }

  Future<void> _failCurrentCall(Object error) async {
    debugPrint('Chaty call setup failed: $error');
    _markTransportFailed(error.toString());
  }

  void _startDurationTimer() {
    if (_durationTimer != null) return;
    _callDurationSeconds = 0;
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
    dataStore.addCallRecord(
      CallRecord(
        id: session.callId,
        callerId: session.isOutgoing ? myId : session.remoteUserId,
        participantIds: <String>[myId, session.remoteUserId],
        type: session.isVideo ? CallType.video : CallType.voice,
        direction: direction,
        durationSeconds: durationSec,
        timestamp: session.startedAt,
      ),
    );
  }

  Future<void> _disposeMediaTransport() async {
    _remoteDescriptionReady = false;
    _queuedRemoteCandidates.clear();
    final local = _localStream;
    final remote = _remoteStream;
    final screen = _screenStream;
    final peer = _peerConnection;
    _localStream = null;
    _remoteStream = null;
    _screenStream = null;
    _peerConnection = null;

    if (screen != null) {
      for (final track in screen.getTracks()) {
        await track.stop();
      }
      await screen.dispose();
    }
    if (local != null) {
      for (final track in local.getTracks()) {
        await track.stop();
      }
      await local.dispose();
    }
    if (remote != null && !identical(remote, local)) {
      for (final track in remote.getTracks()) {
        await track.stop();
      }
      await remote.dispose();
    }
    if (peer != null) await peer.close();
  }

  Future<void> _removeDatabaseChannels() async {
    final sessions = _sessionChannel;
    final candidates = _candidateChannel;
    _sessionChannel = null;
    _candidateChannel = null;
    if (sessions != null) await _client.removeChannel(sessions);
    if (candidates != null) await _client.removeChannel(candidates);
  }

  Future<void> _resetForSignedOutSession() async {
    _ringTimeoutTimer?.cancel();
    _stopDurationTimer();
    await _disposeMediaTransport();
    await _removeDatabaseChannels();
    _currentSession = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _ringTimeoutTimer?.cancel();
    _stopDurationTimer();
    unawaited(_authSubscription?.cancel());
    unawaited(_disposeMediaTransport());
    unawaited(_removeDatabaseChannels());
    super.dispose();
  }
}
