import 'dart:async';

import 'package:supabase_flutter/supabase_flutter.dart';

import '../../ui/core/controllers/preferences_controller.dart';
import 'backend_service.dart';
import 'notification_service.dart';
import 'rich_chat_realtime_service.dart';

/// Production compatibility wrapper for the rich message/presence realtime
/// service.
///
/// Call signaling is intentionally disabled here. Calls are owned exclusively
/// by [CallSignalingService] using the RLS-protected `call_sessions` and
/// `call_ice_candidates` tables plus WebRTC. Keeping these overrides while old
/// call-site references are removed prevents any legacy broadcast invite from
/// becoming a second source of call state.
class ProductionRichChatRealtimeService extends RichChatRealtimeService {
  ProductionRichChatRealtimeService({
    required ChatyPreferencesController preferencesController,
    required ChatyNotificationService notificationService,
    required ChatyBackendService backendService,
    SupabaseClient? client,
  }) : super(
         preferencesController: preferencesController,
         notificationService: notificationService,
         backendService: backendService,
         client: client,
       );

  @override
  IncomingCall? get incomingCall => null;

  @override
  Stream<CallResponseEvent> get callResponses =>
      const Stream<CallResponseEvent>.empty();

  @override
  void respondToIncomingCall(bool accept) {
    // No-op by design. Production call responses use CallSignalingService.
  }

  @override
  Future<void> placeCall({
    required String calleeId,
    required String callId,
    required bool isVideo,
  }) async {
    // No-op by design. Production call creation uses CallSignalingService.
  }

  @override
  Future<void> cancelCall(String callId) async {
    // No-op by design. Production call cancellation uses CallSignalingService.
  }
}
