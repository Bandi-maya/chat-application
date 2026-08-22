import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

import '../../domain/models/call_state.dart';

@pragma('vm:entry-point')
void chatyCallForegroundCallback() {
  FlutterForegroundTask.setTaskHandler(_ChatyCallTaskHandler());
}

class _ChatyCallTaskHandler extends TaskHandler {
  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {}

  @override
  void onRepeatEvent(DateTime timestamp) {}

  @override
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {}
}

/// Android foreground-execution wrapper for an already-authorized WebRTC call.
///
/// This service is deliberately started only after local media has been opened:
/// outgoing calls reach `ringing` after getUserMedia succeeds; incoming calls
/// reach `connecting` after the user accepts and getUserMedia succeeds. That
/// ordering is required by Android 14+ while-in-use camera/microphone rules.
class ChatyCallForegroundService {
  bool _initialized = false;
  bool _startInFlight = false;

  @visibleForTesting
  static bool shouldRunFor(ChatyCallSession? session) {
    if (session == null) return false;
    switch (session.state) {
      case CallSessionState.ringing:
        return session.isOutgoing;
      case CallSessionState.connecting:
      case CallSessionState.connected:
      case CallSessionState.reconnecting:
        return true;
      case CallSessionState.idle:
      case CallSessionState.initiating:
      case CallSessionState.incoming:
      case CallSessionState.declined:
      case CallSessionState.busy:
      case CallSessionState.missed:
      case CallSessionState.ended:
      case CallSessionState.failed:
        return false;
    }
  }

  Future<void> initialize() async {
    if (_initialized || !Platform.isAndroid) return;
    FlutterForegroundTask.initCommunicationPort();
    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: 'chaty_active_call',
        channelName: 'Active calls',
        channelDescription:
            'Keeps an active Chaty voice or video call running while the app is in the background.',
        onlyAlertOnce: true,
      ),
      iosNotificationOptions: const IOSNotificationOptions(
        showNotification: false,
        playSound: false,
      ),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.nothing(),
        autoRunOnBoot: false,
        autoRunOnMyPackageReplaced: false,
        allowWakeLock: true,
        allowWifiLock: true,
        allowAutoRestart: false,
        stopWithTask: true,
      ),
    );
    _initialized = true;
  }

  Future<void> sync(ChatyCallSession? session) async {
    if (!Platform.isAndroid) return;
    await initialize();

    if (!shouldRunFor(session)) {
      await stop();
      return;
    }

    final activeSession = session!;
    final title = activeSession.isVideo
        ? 'Chaty video call'
        : 'Chaty voice call';
    final text = _notificationText(activeSession);

    try {
      if (await FlutterForegroundTask.isRunningService) {
        await FlutterForegroundTask.updateService(
          notificationTitle: title,
          notificationText: text,
        );
        return;
      }
      if (_startInFlight) return;
      _startInFlight = true;

      final notificationPermission =
          await FlutterForegroundTask.checkNotificationPermission();
      if (notificationPermission != NotificationPermission.granted) {
        final requested =
            await FlutterForegroundTask.requestNotificationPermission();
        if (requested != NotificationPermission.granted) {
          debugPrint(
            'Chaty active-call notification permission was not granted; '
            'background call visibility/reliability is not guaranteed.',
          );
        }
      }

      await FlutterForegroundTask.startService(
        serviceId: 7401,
        serviceTypes: <ForegroundServiceTypes>[
          ForegroundServiceTypes.microphone,
          if (activeSession.isVideo) ForegroundServiceTypes.camera,
        ],
        notificationTitle: title,
        notificationText: text,
        notificationInitialRoute: '/',
        callback: chatyCallForegroundCallback,
      );
    } catch (error, stackTrace) {
      // WebRTC remains the media source of truth. A foreground-service failure
      // must be visible in diagnostics but must never be presented as a
      // successful background-call guarantee.
      debugPrint(
        'Chaty foreground call service failed: $error\n$stackTrace',
      );
    } finally {
      _startInFlight = false;
    }
  }

  Future<void> stop() async {
    if (!Platform.isAndroid || !_initialized) return;
    try {
      if (await FlutterForegroundTask.isRunningService) {
        await FlutterForegroundTask.stopService();
      }
    } catch (error, stackTrace) {
      debugPrint('Chaty foreground call stop failed: $error\n$stackTrace');
    }
  }

  String _notificationText(ChatyCallSession session) {
    final name = session.remoteDisplayName.trim().isEmpty
        ? 'Chaty contact'
        : session.remoteDisplayName.trim();
    switch (session.state) {
      case CallSessionState.ringing:
        return 'Calling $name…';
      case CallSessionState.connecting:
        return 'Connecting with $name…';
      case CallSessionState.reconnecting:
        return 'Reconnecting with $name…';
      case CallSessionState.connected:
        return 'In call with $name';
      default:
        return 'Call with $name';
    }
  }
}
