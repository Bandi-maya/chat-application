import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('global call capsule never requires a Tooltip overlay', () {
    final source = File(
      'lib/ui/core/design_system/components/call_activity_capsule.dart',
    ).readAsStringSync();
    expect(source, isNot(contains('tooltip:')));
    expect(source, contains('Semantics('));
    expect(source, contains('width: 48'));
    expect(source, contains('height: 48'));
  });

  test('frontend master patch owns stable listenable groups', () {
    final patch = File(
      'tools/apply_frontend_master_plan.py',
    ).readAsStringSync();
    expect(patch, contains('late final Listenable _rootSignals;'));
    expect(patch, contains('late final Listenable _navigationSignals;'));
    expect(patch, contains('late final Listenable _homeSignals;'));
    expect(patch, contains('listenable: _rootSignals'));
    expect(patch, contains('listenable: _navigationSignals'));
    expect(patch, contains('listenable: _homeSignals'));
  });

  test('UI Lab is deterministic and isolated from production repositories', () {
    final screen = File(
      'lib/features/ui_lab/ui_lab_screen.dart',
    ).readAsStringSync();
    final repository = File(
      'lib/features/ui_lab/ui_lab_repository.dart',
    ).readAsStringSync();
    expect(screen, contains("assert(kDebugMode"));
    expect(repository, contains('Deterministic UI-only fixtures'));
    expect(repository, isNot(contains('Supabase')));
    expect(repository, isNot(contains('ChatyBackendService')));
  });

  test(
    'canonical design system exports the master-plan component families',
    () {
      final source = File(
        'lib/ui/core/design_system/design_system.dart',
      ).readAsStringSync();
      for (final export in <String>[
        'chaty_motion.dart',
        'chaty_glyph.dart',
        'chaty_haptics.dart',
        'chaty_adaptive.dart',
        'component_state.dart',
        'messaging_components.dart',
        'social_components.dart',
        'settings_components.dart',
      ]) {
        expect(
          source,
          contains(export),
          reason: 'Missing design-system export: $export',
        );
      }
    },
  );

  test('home overflow uses adaptive presentation and a shared action handler', () {
    final source = File(
      'lib/features/chats/chats_home_screen.dart',
    ).readAsStringSync();

    expect(source, contains('final useOverflowSheet = availableWidth < 600;'));
    expect(source, contains('onSelected: _handleHomeOverflowAction'));
    expect(source, contains('if (useOverflowSheet)'));
    expect(source, contains('_handleHomeOverflowAction(value);'));
    for (final route in <String>[
      "case 'effects':",
      "case 'qr':",
      "case 'linked':",
      "case 'themes':",
      "case 'templates':",
      "case 'starred':",
      "case 'home':",
      "case 'navigation':",
      "case 'settings':",
    ]) {
      expect(source, contains(route), reason: 'Missing overflow action $route');
    }
  });

  test('template studio searches presets and independent component overrides', () {
    final source = File(
      'lib/features/settings/templates/templates_settings_screen.dart',
    ).readAsStringSync();
    expect(source, contains('Search templates and components'));
    expect(source, contains('...matchingTemplates.map'));
    expect(source, contains('...matchingComponents.map'));
    expect(source, contains('No template or component found'));
  });

  test('starred messages screen reads saved state and offers open/unstar actions', () {
    final screen = File(
      'lib/features/messages/starred_messages_screen.dart',
    ).readAsStringSync();
    final home = File(
      'lib/features/chats/chats_home_screen.dart',
    ).readAsStringSync();

    expect(screen, contains('widget.dataStore.conversations'));
    expect(screen, contains('message.isStarred'));
    expect(screen, contains('ensureConversationLoaded'));
    expect(screen, contains("value: 'open'"));
    expect(screen, contains("value: 'unstar'"));
    expect(screen, contains('toggleStarMessage'));
    expect(home, contains("case 'starred':"));
    expect(home, contains("title: Text('Starred messages')"));
  });

  test('component template previews do not display placeholder copy', () {
    final source = File(
      'lib/features/settings/templates/component_override_screen.dart',
    ).readAsStringSync();
    expect(source, isNot(contains("' configuration ()'")));
    expect(source, isNot(contains("'Message ()...'")));
    expect(source, contains('case TemplateComponentType.navigation:'));
    expect(source, contains('case TemplateComponentType.calls:'));
  });
  test('starred messages open the exact message and reveal its timeline item', () {
    final starred = File(
      'lib/features/messages/starred_messages_screen.dart',
    ).readAsStringSync();
    final chat = File(
      'lib/features/chats/chat_detail_screen.dart',
    ).readAsStringSync();

    expect(starred, contains('initialMessageId: messageId'));
    expect(starred, contains('_openConversation(conversation, messageId: message.id)'));
    expect(chat, contains('final String? initialMessageId;'));
    expect(chat, contains('void _jumpToMessage(String messageId'));
    expect(chat, contains('Scrollable.ensureVisible('));
    expect(chat, contains('_messageItemKeys'));
    expect(chat, contains('_highlightedSearchMessageId = messageId'));
  });

  test('voice-note cancellation always restores chat UI state after errors', () {
    final source = File(
      'lib/features/chats/chat_detail_screen.dart',
    ).readAsStringSync();
    final start = source.indexOf('Future<void> _cancelVoice() async {');
    final end = source.indexOf('void _handleVoiceDrag(', start);
    expect(start, greaterThanOrEqualTo(0));
    expect(end, greaterThan(start));
    final cancelMethod = source.substring(start, end);

    expect(cancelMethod, contains('try {'));
    expect(cancelMethod, contains('await _voice.cancel();'));
    expect(cancelMethod, contains('finally {'));
    expect(cancelMethod, contains('await _realtime.setRecording(widget.conversationId, false);'));
    expect(cancelMethod, contains('_recording = false;'));
    expect(cancelMethod, contains('_voiceBusy = false;'));
  });

  test('voice-note capture serializes actions and cleans up failed recordings', () {
    final source = File(
      'lib/data/services/voice_note_service.dart',
    ).readAsStringSync();

    expect(source, contains('bool _busy = false;'));
    expect(source, contains('if (_recording || _busy) return;'));
    expect(source, contains('if (!_recording || _busy) return false;'));
    expect(source, contains('final cleanupPath = recordedPath ?? stagedPath;'));
    expect(source, contains('if (_busy) return;'));
    expect(source, contains('Future<void> _dispose() async'));
    expect(source, contains('final pendingOperation = _busyCompleter;'));
  });

  test('ongoing call controls respect system reduced-motion settings', () {
    final source = File(
      'lib/features/calls/ongoing_call_screen.dart',
    ).readAsStringSync();

    expect(source, contains('final reduceMotion = MediaQuery.disableAnimationsOf(context);'));
    expect(source, contains('duration: reduceMotion'));
    expect(source, contains('? Duration.zero'));
  });

  test('call signaling bounds stalled connections and records remote terminal calls once', () {
    final source = File(
      'lib/data/services/call_signaling_service.dart',
    ).readAsStringSync();

    expect(source, contains('void _startConnectionTimeout(String callId)'));
    expect(source, contains('final callId = _uuid.v4();\n    _callDurationSeconds = 0;'));
    expect(source, contains('_callDurationSeconds = 0;\n      _currentSession = ChatyCallSession('));
    expect(source, contains("endCall(reason: 'connection_timeout')"));
    expect(source, contains('_connectionTimeoutTimer?.cancel();'));
    expect(source, contains('void _logCallRecordOnce('));
    expect(source, contains('if (!_loggedCallIds.add(session.callId)) return;'));
    expect(source, contains("if (status == 'ended' || status == 'failed')"));
    expect(source, contains('_logCallRecordOnce(\n        terminalSession'));
  });

  test('template settings wait for persistence before showing success', () {
    final component = File(
      'lib/features/settings/templates/component_override_screen.dart',
    ).readAsStringSync();
    final templates = File(
      'lib/features/settings/templates/templates_settings_screen.dart',
    ).readAsStringSync();
    final navigation = File(
      'lib/features/settings/templates/navigation_destinations_settings_screen.dart',
    ).readAsStringSync();

    expect(component, contains('await templateController.applyComponent('));
    expect(component, contains('await templateController.removeComponentOverride('));
    expect(component, contains('catch (error)'));
    expect(templates, contains('await controller.applyFullTemplate('));
    expect(templates, contains('await controller.resetToDefaults('));
    expect(templates, contains('catch (error)'));
    expect(navigation, contains('await _controller.resetNavigationDestinations('));
    expect(navigation, contains('catch (error)'));
  });

}
