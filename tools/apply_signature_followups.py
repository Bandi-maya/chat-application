#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


def write(path, text):
    (ROOT / path).write_text(text, encoding='utf-8')

# Chat composer needs the signature component import.
path = 'lib/features/chats/chat_detail_screen.dart'
text = read(path)
if "../../ui/core/design_system/components/signature_components.dart" not in text:
    text = text.replace(
        "import '../../ui/core/design_system/components/app_components.dart';",
        "import '../../ui/core/design_system/components/app_components.dart';\n"
        "import '../../ui/core/design_system/components/signature_components.dart';",
        1,
    )
# Source-comment brand cleanup that does not touch persisted identifiers.
text = text.replace('matching WhatsApp semantics', 'matching Chaty view-once semantics')
text = text.replace('WhatsApp/Telegram/Instagram scroll logic', 'Chaty scroll and unread-position logic')
write(path, text)

# Real fingerprint is runtime data, so the Text cannot be const.
path = 'lib/features/settings/security/security_center_screen.dart'
text = read(path)
text = text.replace(
    "child: const Text(\n                _deviceFingerprint ?? 'Security keys are initializing…',",
    "child: Text(\n                _deviceFingerprint ?? 'Security keys are initializing…',",
)
write(path, text)

# Existing installations may have the old green preset persisted. Migrate only
# the former default ids; explicit non-default custom themes remain untouched.
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

# Replace the search bar with the signature universal search component.
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

# Reaction bar: use the signature component in the message action surface.
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
# remove now-unused normal quickEmojis declaration (iOS menu has its own const)
text = text.replace("    final quickEmojis = <String>['👍', '❤️', '🔥', '🎉', '👀', '🚀'];\n", '')
write(path, text)

# View-once choice gets the brand glass-sheet surface.
path = 'lib/features/messages/chat_attachment_actions.dart'
text = read(path)
text = text.replace(
    "final result = await showModalBottomSheet<bool>(\n      context: context,\n      backgroundColor: Colors.transparent,\n      builder: (sheetContext) => SafeArea(",
    "final result = await ChatyGlassSheet.show<bool>(\n      context,\n      child: SafeArea(",
    1,
)
# ChatyGlassSheet has no builder scope; rename the old builder context to context
# only inside the view-once block by using Navigator.of(context).
segment_start = text.find('Future<bool> _confirmViewOnce')
segment_end = text.find('  Future<void> shareMedia', segment_start)
if segment_start >= 0 and segment_end > segment_start:
    segment = text[segment_start:segment_end].replace('sheetContext', 'context')
    text = text[:segment_start] + segment + text[segment_end:]
write(path, text)

print('Chaty signature follow-up wiring applied.')
