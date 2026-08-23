#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

path = ROOT / 'lib/data/services/call_foreground_service.dart'
text = path.read_text(encoding='utf-8')

old_handler = '''class _ChatyCallTaskHandler extends TaskHandler {
  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {}

  @override
  void onRepeatEvent(DateTime timestamp) {}

  @override
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {}
}
'''
new_handler = '''class _ChatyCallTaskHandler extends TaskHandler {
  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {}

  @override
  void onRepeatEvent(DateTime timestamp) {}

  @override
  void onNotificationButtonPressed(String id) {
    FlutterForegroundTask.sendDataToMain(<String, dynamic>{
      'type': 'chaty_call_action',
      'action': id,
    });
  }

  @override
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {}
}
'''
if old_handler in text:
    text = text.replace(old_handler, new_handler, 1)

if 'ValueChanged<String>? _actionHandler;' not in text:
    text = text.replace(
        '  bool _startInFlight = false;',
        '  bool _startInFlight = false;\n  ValueChanged<String>? _actionHandler;\n  bool _taskDataCallbackBound = false;',
        1,
    )

if 'void bindActionHandler(' not in text:
    marker = '  @visibleForTesting\n  static bool shouldRunFor'
    methods = '''  void bindActionHandler(ValueChanged<String> handler) {
    _actionHandler = handler;
    if (_taskDataCallbackBound) return;
    FlutterForegroundTask.addTaskDataCallback(_handleTaskData);
    _taskDataCallbackBound = true;
  }

  void unbindActionHandler() {
    _actionHandler = null;
    if (!_taskDataCallbackBound) return;
    FlutterForegroundTask.removeTaskDataCallback(_handleTaskData);
    _taskDataCallbackBound = false;
  }

  void _handleTaskData(Object data) {
    if (data is! Map) return;
    if (data['type']?.toString() != 'chaty_call_action') return;
    final action = data['action']?.toString() ?? '';
    if (action.isNotEmpty) _actionHandler?.call(action);
  }

'''
    if marker not in text:
        raise SystemExit('call service insertion marker missing')
    text = text.replace(marker, methods + marker, 1)

# Ongoing notification controls are intentionally minimal: speaker and hangup.
text = text.replace(
    '''        await FlutterForegroundTask.updateService(
          notificationTitle: title,
          notificationText: text,
        );''',
    '''        await FlutterForegroundTask.updateService(
          notificationTitle: title,
          notificationText: text,
          notificationButtons: const <NotificationButton>[
            NotificationButton(id: 'speaker', text: 'Speaker'),
            NotificationButton(id: 'hangup', text: 'Hang up'),
          ],
        );''',
    1,
)
text = text.replace(
    '''        notificationText: text,
        notificationInitialRoute: '/',
        callback: chatyCallForegroundCallback,''',
    '''        notificationText: text,
        notificationButtons: const <NotificationButton>[
          NotificationButton(id: 'speaker', text: 'Speaker'),
          NotificationButton(id: 'hangup', text: 'Hang up'),
        ],
        notificationInitialRoute: '/',
        callback: chatyCallForegroundCallback,''',
    1,
)
path.write_text(text, encoding='utf-8')

# Main isolate executes the actual WebRTC actions when a foreground-notification
# button is pressed. The task isolate never touches the peer connection.
path = ROOT / 'lib/main.dart'
text = path.read_text(encoding='utf-8')
if "package:chat/data/services/call_foreground_service.dart" not in text:
    text = text.replace(
        "import 'package:chat/data/services/call_signaling_service.dart';",
        "import 'package:chat/data/services/call_signaling_service.dart';\n"
        "import 'package:chat/data/services/call_foreground_service.dart';",
        1,
    )
if 'late final ChatyCallForegroundService _callForegroundService;' not in text:
    text = text.replace(
        '  late final CallSignalingService _callService;',
        '  late final CallSignalingService _callService;\n'
        '  late final ChatyCallForegroundService _callForegroundService;',
        1,
    )

assignment = '''    _callService = locator<CallSignalingService>();
    unawaited(
      _callService.initialize().catchError((Object error, StackTrace stackTrace) {'''
if assignment in text and '_callForegroundService = locator<ChatyCallForegroundService>();' not in text:
    text = text.replace(
        assignment,
        '''    _callService = locator<CallSignalingService>();
    _callForegroundService = locator<ChatyCallForegroundService>();
    _callForegroundService.bindActionHandler(_handleCallNotificationAction);
    unawaited(
      _callService.initialize().catchError((Object error, StackTrace stackTrace) {''',
        1,
    )

if 'void _handleCallNotificationAction(String action)' not in text:
    marker = '  Future<void> _registerCurrentDevice() async {'
    method = '''  void _handleCallNotificationAction(String action) {
    final session = _callService.currentSession;
    if (session == null) return;
    switch (action) {
      case 'hangup':
        unawaited(_callService.endCall());
        break;
      case 'speaker':
        _callService.setAudioRoute(
          session.audioRoute == AudioRouteType.speaker
              ? AudioRouteType.earpiece
              : AudioRouteType.speaker,
        );
        break;
    }
  }

'''
    if marker not in text:
        raise SystemExit('main call action insertion marker missing')
    text = text.replace(marker, method + marker, 1)

text = text.replace(
    '''    _statusService.dispose();
    super.dispose();''',
    '''    _statusService.dispose();
    _callForegroundService.unbindActionHandler();
    super.dispose();''',
    1,
)
path.write_text(text, encoding='utf-8')

for file_path, needle in (
    ('lib/data/services/call_foreground_service.dart', "NotificationButton(id: 'hangup'"),
    ('lib/main.dart', '_handleCallNotificationAction(String action)'),
):
    if needle not in (ROOT / file_path).read_text(encoding='utf-8'):
        raise SystemExit(f'call notification invariant missing: {file_path}')

print('Call notification actions applied.')
