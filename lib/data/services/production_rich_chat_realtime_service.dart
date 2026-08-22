import 'package:supabase_flutter/supabase_flutter.dart';

import '../../ui/core/controllers/preferences_controller.dart';
import 'backend_service.dart';
import 'notification_service.dart';
import 'rich_chat_realtime_service.dart';

/// Compatibility wrapper for rich message/presence realtime behavior.
///
/// Call signaling is intentionally absent. Production calls are owned only by
/// CallSignalingService using the RLS-protected call_sessions and
/// call_ice_candidates tables plus WebRTC.
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
}
