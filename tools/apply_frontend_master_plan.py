#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f'Frontend master-plan marker missing: {label}')
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# PHASE 0 — root lifecycle stability + first-frame startup.
# ---------------------------------------------------------------------------
path = 'lib/main.dart'
text = read(path)

old = '''final GlobalKey<NavigatorState> _rootNavigatorKey = GlobalKey<NavigatorState>();

Future<void> main() async {
'''
new = '''final GlobalKey<NavigatorState> _rootNavigatorKey = GlobalKey<NavigatorState>();

Future<void> _initializeDeferredPlatformServices() async {
  try {
    await locator<AppIconController>().initialize();
  } catch (error, stackTrace) {
    debugPrint('Deferred app-icon initialization failed: $error\\n$stackTrace');
  }
  try {
    await locator<NotificationChannelManager>().initialize();
  } catch (error, stackTrace) {
    debugPrint('Deferred notification-channel initialization failed: $error\\n$stackTrace');
  }
  try {
    await locator<PushTokenService>().initialize();
  } catch (error, stackTrace) {
    debugPrint('Deferred push initialization failed: $error\\n$stackTrace');
  }
}

Future<void> main() async {
'''
text = replace_once(text, old, new, 'main deferred initializer')

old = '''  await locator<ThemeController>().init();
  await locator<AppIconController>().initialize();
  await locator<NotificationChannelManager>().initialize();
  await locator<PushTokenService>().initialize();
  runApp(const ChatyApp());
}'''
new = '''  await locator<ThemeController>().init();
  runApp(const ChatyApp());
  // First frame is not blocked by launcher-icon, channel, or push housekeeping.
  // Cached/local application state can paint while platform services finish.
  unawaited(_initializeDeferredPlatformServices());
}'''
text = replace_once(text, old, new, 'main first frame')

old = '''  late final StreamSubscription<AuthState> _authUiSubscription;
  bool _recoveryRouteOpen = false;'''
new = '''  late final StreamSubscription<AuthState> _authUiSubscription;
  late final Listenable _rootSignals;
  bool _recoveryRouteOpen = false;'''
text = replace_once(text, old, new, 'root signal field')

old = '''    _richRealtime = locator<RichChatRealtimeService>();
    _preferencesController.addListener(_handleSecurityPreferenceChanged);'''
new = '''    _richRealtime = locator<RichChatRealtimeService>();
    // Construct once. Recreating Listenable.merge during build can detach an
    // inherited dependency while descendants still depend on it and was one
    // source of the device-only `_dependents.isEmpty` assertion.
    _rootSignals = Listenable.merge(<Listenable>[
      _themeController,
      _preferencesController,
      _appearanceController,
      _backend,
      _richRealtime,
      _callService,
    ]);
    _preferencesController.addListener(_handleSecurityPreferenceChanged);'''
text = replace_once(text, old, new, 'root signals init')

old = '''    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        _themeController,
        _preferencesController,
        _appearanceController,
        _backend,
        _richRealtime,
        _callService,
      ]),'''
new = '''    return ListenableBuilder(
      listenable: _rootSignals,'''
text = replace_once(text, old, new, 'root stable builder')
write(path, text)


# ---------------------------------------------------------------------------
# PHASE 0/3 — navigation lifecycle stability and remove product-copy wording.
# ---------------------------------------------------------------------------
path = 'lib/features/chats/main_navigation_shell.dart'
text = read(path)
old = '''  late final PageController _pageController;

  @override
  void initState() {
    super.initState();
    _pageController = PageController(initialPage: _currentIndex);
  }'''
new = '''  late final PageController _pageController;
  late final Listenable _navigationSignals;

  @override
  void initState() {
    super.initState();
    _pageController = PageController(initialPage: _currentIndex);
    _navigationSignals = Listenable.merge(<Listenable>[
      locator<ThemeController>(),
      locator<ChatyPreferencesController>(),
      locator<AppearanceVariantController>(),
      locator<ChatyDataStore>(),
    ]);
  }'''
text = replace_once(text, old, new, 'navigation signal init')
old = '''    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        themeController,
        preferencesController,
        appearanceController,
        dataStore,
      ]),'''
new = '''    return ListenableBuilder(
      listenable: _navigationSignals,'''
text = replace_once(text, old, new, 'navigation stable builder')
text = text.replace('TOP WHATSAPP-STYLE GREEN TAB BAR', 'LEGACY TOP TAB BAR')
text = text.replace('Top WhatsApp Style Tab Bar', 'Legacy Top Tab Bar')
text = text.replace('WA-iOS', 'legacy compact')
write(path, text)


# ---------------------------------------------------------------------------
# PHASE 3 — Home: stable signals, one title, Quick Peek, canonical tile.
# ---------------------------------------------------------------------------
path = 'lib/features/chats/chats_home_screen.dart'
text = read(path)
if "components/messaging_components.dart" not in text:
    text = text.replace(
        "import '../../ui/core/design_system/components/app_components.dart';",
        "import '../../ui/core/design_system/components/app_components.dart';\n"
        "import '../../ui/core/design_system/components/messaging_components.dart';\n"
        "import '../../ui/core/design_system/components/signature_components.dart';\n"
        "import '../../ui/core/design_system/component_state.dart';",
        1,
    )
old = '''  late final ContactRelationshipService _relationships;
  String _selectedFilter = 'All';'''
new = '''  late final ContactRelationshipService _relationships;
  late final Listenable _homeSignals;
  String _selectedFilter = 'All';'''
text = replace_once(text, old, new, 'home signal field')
old = '''    _realtime = locator<RichChatRealtimeService>();
    _relationships = locator<ContactRelationshipService>();
    WidgetsBinding.instance.addPostFrameCallback((_) {'''
new = '''    _realtime = locator<RichChatRealtimeService>();
    _relationships = locator<ContactRelationshipService>();
    _homeSignals = Listenable.merge(<Listenable>[
      widget.dataStore,
      _realtime,
      widget.preferencesController,
    ]);
    WidgetsBinding.instance.addPostFrameCallback((_) {'''
text = replace_once(text, old, new, 'home signal init')
old = '''    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        widget.dataStore,
        _realtime,
        widget.preferencesController,
      ]),'''
new = '''    return ListenableBuilder(
      listenable: _homeSignals,'''
text = replace_once(text, old, new, 'home stable builder')

old = '''  void _handleConversationLongPress(Conversation conversation) {
    HapticFeedback.mediumImpact();
    _toggleSelection(conversation.id);
  }'''
new = '''  void _handleConversationLongPress(Conversation conversation) {
    HapticFeedback.mediumImpact();
    ChatyGlassSheet.show<void>(
      context,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 10, 18, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              width: 38,
              height: 4,
              margin: const EdgeInsets.only(bottom: 14),
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.outlineVariant,
                borderRadius: BorderRadius.circular(999),
              ),
            ),
            Row(
              children: [
                ChatyAvatar(
                  initials: conversation.avatarInitials ??
                      conversation.title.characters.take(2).toString().toUpperCase(),
                  color: conversation.avatarColorHex == null
                      ? widget.themeController.globalTheme.accentColor
                      : Color(int.parse(conversation.avatarColorHex!)),
                  size: 48,
                  shape: widget.preferencesController.home.avatarShape,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        conversation.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        conversation.lastMessageText.isEmpty
                            ? 'No messages yet'
                            : conversation.lastMessageText,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 18),
            Center(
              child: ChatyActionDock(
                actions: [
                  ChatyDockAction(
                    icon: conversation.isPinned
                        ? Icons.push_pin_rounded
                        : Icons.push_pin_outlined,
                    label: conversation.isPinned ? 'Unpin' : 'Pin',
                    onPressed: () {
                      widget.dataStore.togglePinConversation(conversation.id);
                      Navigator.of(context).pop();
                    },
                  ),
                  ChatyDockAction(
                    icon: conversation.isMuted
                        ? Icons.notifications_active_rounded
                        : Icons.notifications_off_rounded,
                    label: conversation.isMuted ? 'Unmute' : 'Mute',
                    onPressed: () {
                      widget.dataStore.toggleMuteConversation(conversation.id);
                      Navigator.of(context).pop();
                    },
                  ),
                  ChatyDockAction(
                    icon: Icons.archive_rounded,
                    label: conversation.isArchived ? 'Unarchive' : 'Archive',
                    onPressed: () {
                      widget.dataStore.toggleArchiveConversation(conversation.id);
                      Navigator.of(context).pop();
                    },
                  ),
                  ChatyDockAction(
                    icon: widget.preferencesController.isConversationLocked(conversation.id)
                        ? Icons.lock_open_rounded
                        : Icons.lock_rounded,
                    label: widget.preferencesController.isConversationLocked(conversation.id)
                        ? 'Unlock'
                        : 'Lock',
                    onPressed: () {
                      widget.preferencesController.toggleLockConversation(
                        conversation.id,
                        lock: !widget.preferencesController
                            .isConversationLocked(conversation.id),
                      );
                      Navigator.of(context).pop();
                    },
                  ),
                  ChatyDockAction(
                    icon: Icons.checklist_rounded,
                    label: 'Select',
                    onPressed: () {
                      Navigator.of(context).pop();
                      _toggleSelection(conversation.id);
                    },
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: () {
                Navigator.of(context).pop();
                _handleConversationTap(conversation);
              },
              icon: const Icon(Icons.open_in_new_rounded),
              label: const Text('Open conversation'),
            ),
          ],
        ),
      ),
    );
  }'''
text = replace_once(text, old, new, 'home quick peek')

# The compact app bar already renders pageTitle. Do not render a second large
# copy below it. An expressive large title is reserved for the username view.
text = text.replace(
    "if (!_isSelectionMode && widget.pageTitle != null)",
    "if (!_isSelectionMode && widget.pageTitle == null && homeStyle == 'Expressive')",
    1,
)
text = text.replace(
    "                                  widget.pageTitle!,",
    "                                  widget.dataStore.currentUser.displayName.isEmpty\n"
    "                                      ? 'Chats'\n"
    "                                      : widget.dataStore.currentUser.displayName,",
    1,
)
text = text.replace('P4: WhatsApp-iOS swipe actions', 'Canonical swipe actions')
text = text.replace('WA-iOS presence dot sits bottom-right.', 'Presence indicator sits bottom-right.')
text = text.replace('WA-iOS row metrics: 16pt name.', 'Compact row metric: 16pt name.')
text = text.replace('WA-iOS: 13.5pt gray preview.', 'Compact row preview: 13.5pt.')
write(path, text)


# ---------------------------------------------------------------------------
# PHASE 1/2 — Debug-only UI Lab entry. No mock data enters production flows.
# ---------------------------------------------------------------------------
path = 'lib/features/settings/settings_root_screen.dart'
text = read(path)
if "package:flutter/foundation.dart" not in text:
    text = text.replace(
        "import 'package:flutter/material.dart';",
        "import 'package:flutter/foundation.dart';\nimport 'package:flutter/material.dart';",
        1,
    )
if "../ui_lab/ui_lab_screen.dart" not in text:
    text = text.replace(
        "import '../profile/profile_actions.dart';",
        "import '../profile/profile_actions.dart';\nimport '../ui_lab/ui_lab_screen.dart';",
        1,
    )
marker = '''    return <SettingsSearchResult>[
      SettingsSearchResult('''
if "title: 'UI Lab'" not in text:
    replacement = '''    return <SettingsSearchResult>[
      if (kDebugMode)
        const SettingsSearchResult(
          title: 'UI Lab',
          category: 'Developer Preview',
          description: 'Preview new Chaty components with deterministic mock states',
          icon: Icons.science_rounded,
          destination: UiLabScreen(),
          keywords: <String>['ui lab', 'mock', 'preview', 'components', 'states'],
        ),
      SettingsSearchResult('''
    if marker not in text:
        raise SystemExit('Settings search index marker missing')
    text = text.replace(marker, replacement, 1)
write(path, text)


# Build-time invariants for the master-plan pass.
checks = {
    'lib/main.dart': [
        'late final Listenable _rootSignals;',
        'listenable: _rootSignals',
        '_initializeDeferredPlatformServices',
    ],
    'lib/features/chats/main_navigation_shell.dart': [
        'late final Listenable _navigationSignals;',
        'listenable: _navigationSignals',
    ],
    'lib/features/chats/chats_home_screen.dart': [
        'late final Listenable _homeSignals;',
        'ChatyGlassSheet.show<void>',
        'homeStyle == \'Expressive\'',
    ],
    'lib/features/settings/settings_root_screen.dart': [
        "title: 'UI Lab'",
        'if (kDebugMode)',
    ],
}
for file_path, needles in checks.items():
    value = read(file_path)
    for needle in needles:
        if needle not in value:
            raise SystemExit(f'Frontend master-plan invariant missing: {file_path}: {needle}')

print('Chaty frontend master-plan integration applied.')
