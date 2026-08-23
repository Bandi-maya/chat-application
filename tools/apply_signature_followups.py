#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


def write(path, text):
    (ROOT / path).write_text(text, encoding='utf-8')

# Chat composer: signature shell import + discoverable command palette.
path = 'lib/features/chats/chat_detail_screen.dart'
text = read(path)
if "../../ui/core/design_system/components/signature_components.dart" not in text:
    text = text.replace(
        "import '../../ui/core/design_system/components/app_components.dart';",
        "import '../../ui/core/design_system/components/app_components.dart';\n"
        "import '../../ui/core/design_system/components/signature_components.dart';",
        1,
    )
text = text.replace('matching WhatsApp semantics', 'matching Chaty view-once semantics')
text = text.replace('WhatsApp/Telegram/Instagram scroll logic', 'Chaty scroll and unread-position logic')
text = text.replace(
    '            onChanged: widget.onChanged,',
    """            onChanged: (value) {
              widget.onChanged(value);
              if (value.trim() == '/') {
                Future<void>.microtask(() async {
                  final command = await ChatyCommandPalette.show(context);
                  if (!mounted || command == null) return;
                  widget.controller
                    ..text = '$command '
                    ..selection = TextSelection.collapsed(
                      offset: command.length + 1,
                    );
                  widget.onChanged(widget.controller.text);
                });
              }
            },""",
    1,
)
# Never expose Supabase/native exception strings in the chat UI.
old_call_error = """    } catch (error) {
      if (!mounted) return;
      final reason = error
          .toString()
          .replaceFirst('StateError: ', '')
          .replaceFirst('Exception: ', '');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Unable to start ${isVideo ? 'video' : 'voice'} call: $reason',
          ),
        ),
      );
    }
"""
new_call_error = """    } catch (error, stackTrace) {
      debugPrint('Chaty call start failed: $error\\n$stackTrace');
      if (!mounted) return;
      ChatyActivityIsland.show(
        context,
        icon: Icons.call_end_rounded,
        title: 'Couldn’t start the ${isVideo ? 'video' : 'voice'} call',
        subtitle: 'Check your connection and try again.',
      );
    }
"""
if old_call_error in text:
    text = text.replace(old_call_error, new_call_error, 1)
# In-chat avatar now uses the same live presence language as home.
old_header_avatar = """              if (showHeaderAvatar)
                Stack(
                  clipBehavior: Clip.none,
                  children: [
                    AppAvatar(
                      initials:
                          conversation.avatarInitials ??
                          conversation.title.characters
                              .take(2)
                              .toString()
                              .toUpperCase(),
                      colorHex: conversation.avatarColorHex ?? '0xFF6366F1',
                      size: 38,
                    ),
                    if (conversation.type == ConversationType.direct &&
                        isOnline)
                      Positioned(
                        right: -1,
                        bottom: -1,
                        child: ChatyOnlineDot(
                          active: true,
                          avatarSize: 38,
                          color: theme.successColor,
                          ringColor: theme.surfaceColor,
                        ),
                      ),
                  ],
                ),
"""
new_header_avatar = """              if (showHeaderAvatar)
                ChatyPresenceAvatar(
                  size: 40,
                  online: conversation.type == ConversationType.direct && isOnline,
                  typing: remoteTyping,
                  recording: remoteRecording,
                  child: AppAvatar(
                    initials:
                        conversation.avatarInitials ??
                        conversation.title.characters
                            .take(2)
                            .toString()
                            .toUpperCase(),
                    colorHex: conversation.avatarColorHex ?? '0xFF6366F1',
                    size: 38,
                  ),
                ),
"""
if old_header_avatar in text:
    text = text.replace(old_header_avatar, new_header_avatar, 1)
write(path, text)

# Runtime device fingerprint cannot be rendered in a const Text.
path = 'lib/features/settings/security/security_center_screen.dart'
text = read(path)
text = text.replace(
    "child: const Text(\n                _deviceFingerprint ?? 'Security keys are initializing…',",
    "child: Text(\n                _deviceFingerprint ?? 'Security keys are initializing…',",
)
write(path, text)

# Existing installations migrate only the former default green presets.
path = 'lib/ui/core/theme/theme_controller.dart'
text = read(path)
marker = """    _layoutMode = _globalTheme.layoutMode;
    _navigationMode = _globalTheme.navigationMode;"""
if 'legacyDefaultIds' not in text:
    replacement = """    const legacyDefaultIds = <String>{
      'whatsapp_ios_light',
      'whatsapp_ios_dark',
    };
    if (legacyDefaultIds.contains(_globalTheme.id)) {
      _globalTheme = _globalTheme.brightness == Brightness.dark
          ? ThemePresets.chatyAuroraDark
          : ThemePresets.chatyAuroraLight;
    }

    _layoutMode = _globalTheme.layoutMode;
    _navigationMode = _globalTheme.navigationMode;"""
    if marker not in text:
        raise SystemExit('theme migration marker missing')
    text = text.replace(marker, replacement, 1)
write(path, text)

# Universal smart search.
path = 'lib/features/search/global_search_screen.dart'
text = read(path)
old = """        titleWidget: Container(
          height: 40,
          decoration: BoxDecoration(
            color: context.colors.inputFill,
            borderRadius: BorderRadius.circular(ChatyRadius.full),
          ),
          child: TextField(
            controller: _searchController,
            autofocus: true,
            style: TextStyle(color: context.colors.foreground, fontSize: 15),
            decoration: InputDecoration(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: ChatySpacing.base,
                vertical: 10,
              ),
              hintText: 'Search @username, people, groups...',
              hintStyle: ChatyTypography.caption(
                themeData.colorScheme.onSurface.withValues(alpha: 0.5),
              ),
              border: InputBorder.none,
              prefixIcon: Icon(
                Icons.search_rounded,
                size: 20,
                color: themeData.colorScheme.onSurface.withValues(alpha: 0.5),
              ),
            ),
          ),
        ),"""
new = """        titleWidget: SizedBox(
          height: 44,
          child: ChatySmartSearchField(
            controller: _searchController,
            onChanged: (_) {},
          ),
        ),"""
if old in text:
    text = text.replace(old, new, 1)
write(path, text)

# Signature reaction bar.
path = 'lib/features/messages/message_action_sheet.dart'
text = read(path)
start = """              Container(
                padding: const EdgeInsets.symmetric(
                  vertical: ChatySpacing.xs,
                  horizontal: ChatySpacing.sm,
                ),
                decoration: BoxDecoration(
                  color: colors.surfaceSecondary,
                  borderRadius: BorderRadius.circular(ChatyRadius.full),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: ["""
end = """                  ],
                ),
              ),
              const SizedBox(height: ChatySpacing.base),"""
if start in text:
    s = text.index(start)
    e = text.find(end, s)
    if e < 0:
        raise SystemExit('reaction bar close marker missing')
    e += len(end)
    replacement = """              Row(
                children: [
                  Expanded(
                    child: SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: ChatyReactionBar(onReaction: onReact),
                    ),
                  ),
                  const SizedBox(width: 6),
                  ChatyIconButton(
                    icon: Icons.add_reaction_outlined,
                    tooltip: 'More reactions',
                    onPressed: () => _openAllReactions(context),
                  ),
                ],
              ),
              const SizedBox(height: ChatySpacing.base),"""
    text = text[:s] + replacement + text[e:]
text = text.replace("    final quickEmojis = <String>['👍', '❤️', '🔥', '🎉', '👀', '🚀'];\n", '')
write(path, text)

# View-once confirmation uses Chaty's glass-sheet surface.
path = 'lib/features/messages/chat_attachment_actions.dart'
text = read(path)
text = text.replace(
    "final result = await showModalBottomSheet<bool>(\n      context: context,\n      backgroundColor: Colors.transparent,\n      builder: (sheetContext) => SafeArea(",
    "final result = await ChatyGlassSheet.show<bool>(\n      context,\n      child: SafeArea(",
    1,
)
segment_start = text.find('Future<bool> _confirmViewOnce')
segment_end = text.find('  Future<void> shareMedia', segment_start)
if segment_start >= 0 and segment_end > segment_start:
    segment = text[segment_start:segment_end].replace('sheetContext', 'context')
    text = text[:segment_start] + segment + text[segment_end:]
write(path, text)

# Home: presence avatar + Quick Peek and contextual action dock.
path = 'lib/features/chats/chats_home_screen.dart'
text = read(path)
if "../../ui/core/design_system/components/signature_components.dart" not in text:
    text = text.replace(
        "import '../../ui/core/design_system/components/app_components.dart';",
        "import '../../ui/core/design_system/components/app_components.dart';\n"
        "import '../../ui/core/design_system/components/signature_components.dart';",
        1,
    )
old_long = """  void _handleConversationLongPress(Conversation conversation) {
    HapticFeedback.mediumImpact();
    _toggleSelection(conversation.id);
  }
"""
new_long = """  void _handleConversationLongPress(Conversation conversation) {
    HapticFeedback.mediumImpact();
    final locked = widget.preferencesController.isConversationLocked(
      conversation.id,
    );
    ChatyQuickPeek.show(
      context,
      title: conversation.title,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            conversation.lastMessageText.isEmpty
                ? 'No message preview available.'
                : conversation.lastMessageText,
            maxLines: 4,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 16),
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
                      ? Icons.volume_up_rounded
                      : Icons.volume_off_rounded,
                  label: conversation.isMuted ? 'Unmute' : 'Mute',
                  onPressed: () {
                    widget.dataStore.toggleMuteConversation(conversation.id);
                    Navigator.of(context).pop();
                  },
                ),
                ChatyDockAction(
                  icon: Icons.archive_outlined,
                  label: 'Archive',
                  onPressed: () {
                    widget.dataStore.toggleArchiveConversation(conversation.id);
                    Navigator.of(context).pop();
                  },
                ),
                ChatyDockAction(
                  icon: locked ? Icons.lock_open_rounded : Icons.lock_rounded,
                  label: locked ? 'Unlock' : 'Lock',
                  onPressed: () {
                    widget.preferencesController.toggleLockConversation(
                      conversation.id,
                      lock: !locked,
                    );
                    Navigator.of(context).pop();
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: () {
              Navigator.of(context).pop();
              _handleConversationTap(conversation);
            },
            child: const Text('Open conversation'),
          ),
        ],
      ),
    );
  }
"""
if old_long in text:
    text = text.replace(old_long, new_long, 1)
old_avatar = """                child: ChatyNetworkAvatar(
                  initials: widget.dataStore.currentUser.avatarInitials,
                  colorHex: widget.dataStore.currentUser.avatarColorHex,
                  url: widget.dataStore.currentUser.avatarUrl,
                  size: 34,
                ),"""
new_avatar = """                child: ChatyPresenceAvatar(
                  size: 36,
                  online: true,
                  child: ChatyNetworkAvatar(
                    initials: widget.dataStore.currentUser.avatarInitials,
                    colorHex: widget.dataStore.currentUser.avatarColorHex,
                    url: widget.dataStore.currentUser.avatarUrl,
                    size: 34,
                  ),
                ),"""
if old_avatar in text:
    text = text.replace(old_avatar, new_avatar, 1)
text = text.replace('WA-iOS presence dot sits bottom-right.', 'Chaty presence dot sits bottom-right.')
text = text.replace('WA-iOS row metrics: 16pt name.', 'Chaty row metrics: 16pt name.')
write(path, text)

print('Chaty signature follow-up wiring applied.')
