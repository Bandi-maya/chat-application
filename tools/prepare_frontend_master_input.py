#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# ---------------------------------------------------------------------------
# Root lifecycle/startup overlap.
# ---------------------------------------------------------------------------
path = ROOT / 'lib/main.dart'
text = path.read_text(encoding='utf-8')

offline_startup = '''  setupLocator();
  await locator<ThemeController>().init();
  runApp(const ChatyApp());

  // Do not block first paint on launcher-icon/channel/push housekeeping.
  unawaited(locator<AppIconController>().initialize());
  unawaited(locator<NotificationChannelManager>().initialize());
  unawaited(locator<PushTokenService>().initialize());
}'''
canonical_input = '''  setupLocator();
  await locator<ThemeController>().init();
  await locator<AppIconController>().initialize();
  await locator<NotificationChannelManager>().initialize();
  await locator<PushTokenService>().initialize();
  runApp(const ChatyApp());
}'''
if offline_startup in text:
    text = text.replace(offline_startup, canonical_input, 1)

if '  late final Listenable _rootListenable;\n' in text:
    text = text.replace('  late final Listenable _rootListenable;\n', '', 1)

legacy_init = '''    _statusService = StatusService();
    _rootListenable = Listenable.merge(<Listenable>[
      _themeController,
      _preferencesController,
      _appearanceController,
      _backend,
      _richRealtime,
      _callService,
    ]);'''
if legacy_init in text:
    text = text.replace(legacy_init, '    _statusService = StatusService();', 1)

legacy_builder = '      listenable: _rootListenable,'
canonical_builder = '''      listenable: Listenable.merge(<Listenable>[
        _themeController,
        _preferencesController,
        _appearanceController,
        _backend,
        _richRealtime,
        _callService,
      ]),'''
if legacy_builder in text:
    text = text.replace(legacy_builder, canonical_builder, 1)

path.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Home Quick Peek overlap.
# Earlier signature rounds may already have replaced the long-press handler.
# Normalize only that function body so the master plan can install its final
# action contract deterministically without touching any surrounding logic.
# ---------------------------------------------------------------------------
home_path = ROOT / 'lib/features/chats/chats_home_screen.dart'
home = home_path.read_text(encoding='utf-8')
start_marker = '  void _handleConversationLongPress(Conversation conversation) {'
end_marker = '\n  void _togglePinSelected() {'
start = home.find(start_marker)
end = home.find(end_marker, start)
if start >= 0 and end > start:
    canonical_long_press_input = '''  void _handleConversationLongPress(Conversation conversation) {
    HapticFeedback.mediumImpact();
    _toggleSelection(conversation.id);
  }
'''
    home = home[:start] + canonical_long_press_input + home[end:]
    home_path.write_text(home, encoding='utf-8')

print('Frontend master input normalized.')
