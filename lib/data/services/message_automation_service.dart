import 'dart:async';

import 'package:supabase_flutter/supabase_flutter.dart';

import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../repositories/mock_data_store.dart';
import 'gb_feature_backend_service.dart';

/// Keeps the account-side automation configuration synchronized with Supabase.
/// Scheduled execution and auto-replies are performed server-side so they keep
/// working when the mobile process is backgrounded or terminated.
class MessageAutomationService {
  final ChatyPreferencesController preferencesController;
  final MockDataStore dataStore;
  final GbFeatureBackendService _backend = GbFeatureBackendService();
  Timer? _syncTimer;
  bool _syncing = false;

  MessageAutomationService({
    required this.preferencesController,
    required this.dataStore,
  }) {
    preferencesController.addListener(_onPreferencesChanged);
    _syncTimer = Timer.periodic(const Duration(seconds: 30), (_) => unawaited(_sync()));
    unawaited(_sync());
  }

  void _onPreferencesChanged() => unawaited(_sync());

  Future<void> _sync() async {
    if (_syncing || Supabase.instance.client.auth.currentUser == null) return;
    _syncing = true;
    try {
      await _backend.synchronizeAutomation(preferencesController.automation);
    } catch (_) {
      // Account sync retries on the next preference change / periodic pass.
    } finally {
      _syncing = false;
    }
  }

  /// Kept for presentation compatibility. Incoming auto-reply execution now
  /// happens in the database trigger after the incoming message is committed.
  void handleIncomingMessageAutoReply(String conversationId, String text) {
    unawaited(_sync());
  }

  void dispose() {
    preferencesController.removeListener(_onPreferencesChanged);
    _syncTimer?.cancel();
  }
}
