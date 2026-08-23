#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'lib/main.dart'
text = path.read_text(encoding='utf-8')

# Older offline-first compatibility work deferred these services directly.
# Normalize only this overlap so the frontend master patch becomes the single
# owner of startup/lifecycle behavior. Cache/realtime changes are untouched.
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

# The same compatibility patch cached a merged listenable under an older name.
# Restore its source shape so the master patch can install its one stable owner.
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
print('Frontend master input normalized.')
