#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


# ---------------------------------------------------------------------------
# P8 — Global call UX: never expose raw exception text from the root call host.
# The actual active-call capsule is installed by the preceding UX round and is
# available from every screen; system foreground controls remain authoritative
# while backgrounded.
# ---------------------------------------------------------------------------
path = 'lib/main.dart'
text = read(path)
if "components/signature_components.dart" not in text:
    marker = "import 'package:chat/ui/core/design_system/components/call_activity_capsule.dart';"
    if marker in text:
        text = text.replace(
            marker,
            marker + "\nimport 'package:chat/ui/core/design_system/components/signature_components.dart';",
            1,
        )

old = """                          final callContext = _rootNavigatorKey.currentContext;
                          if (callContext != null) {
                            ScaffoldMessenger.of(callContext).showSnackBar(
                              SnackBar(
                                content: Text(
                                  'Unable to answer call: ${error.toString().replaceFirst('Exception: ', '')}',
                                ),
                              ),
                            );
                          }
"""
new = """                          debugPrint('Unable to answer call: $error');
                          final callContext = _rootNavigatorKey.currentContext;
                          if (callContext != null) {
                            ChatyActivityIsland.show(
                              callContext,
                              icon: Icons.call_end_rounded,
                              title: 'Couldn’t answer the call',
                              subtitle: 'The call may have ended or the connection changed.',
                            );
                          }
"""
if old in text:
    text = text.replace(old, new, 1)
write(path, text)


# ---------------------------------------------------------------------------
# P9 — Updates becomes Moments + Recent activity. The rail is backed by actual
# StatusService data; no production mock data is introduced.
# ---------------------------------------------------------------------------
path = 'lib/features/updates/updates_screen.dart'
text = read(path)

# The previous compatibility pass will already sanitize publish errors. Keep a
# direct fallback replacement for trees where it has not yet run.
text = text.replace("'New Update'", "'Create update'")
text = text.replace("'My Status'", "'Your moment'")
text = text.replace("'RECENT UPDATES'", "'RECENT ACTIVITY'")
text = text.replace("'No recent updates'", "'No recent activity'")

moment_marker = """          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                ChatySpacing.base + 4,
                ChatySpacing.lg,
                ChatySpacing.base,
                ChatySpacing.xs,
              ),
              child: Text(
                'RECENT ACTIVITY',
"""
if "'MOMENTS'" not in text and moment_marker in text:
    moment_section = r'''          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                ChatySpacing.base + 4,
                ChatySpacing.lg,
                ChatySpacing.base,
                ChatySpacing.xs,
              ),
              child: Text(
                'MOMENTS',
                style: TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w800,
                  color: colors.primary,
                  letterSpacing: 0.9,
                ),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: SizedBox(
              height: 94,
              child: StreamBuilder<List<StatusRecord>>(
                stream: _statusService.watchActiveStatuses(),
                builder: (context, snapshot) {
                  final statuses = snapshot.data ?? const <StatusRecord>[];
                  final latestByUser = <String, StatusRecord>{};
                  for (final status in statuses) {
                    latestByUser.putIfAbsent(status.userId, () => status);
                  }
                  final items = latestByUser.values.toList(growable: false);
                  return ListView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(
                      horizontal: ChatySpacing.base,
                    ),
                    children: [
                      ChatyMomentRing(
                        avatar: AppAvatar(
                          initials: widget.dataStore.currentUser.avatarInitials,
                          colorHex: widget.dataStore.currentUser.avatarColorHex,
                          size: 54,
                        ),
                        label: 'You',
                        isMine: true,
                        onTap: _openComposer,
                      ),
                      for (final status in items)
                        if (status.userId != widget.dataStore.currentUser.id)
                          ChatyMomentRing(
                            avatar: AppAvatar(
                              initials: widget.dataStore
                                      .getUser(status.userId)
                                      ?.avatarInitials ??
                                  'U',
                              colorHex: widget.dataStore
                                      .getUser(status.userId)
                                      ?.avatarColorHex ??
                                  '0xFF087F8C',
                              size: 54,
                            ),
                            label: widget.dataStore
                                    .getUser(status.userId)
                                    ?.displayName ??
                                'Contact',
                            onTap: () => _openStatus(status),
                          ),
                    ],
                  );
                },
              ),
            ),
          ),
'''
    text = text.replace(moment_marker, moment_section + moment_marker, 1)
write(path, text)


# ---------------------------------------------------------------------------
# P10 — Settings/profile language normalization. Keep the existing mature
# production primitives and cached profile media, while removing status-era
# wording from the primary profile/update surfaces.
# ---------------------------------------------------------------------------
path = 'lib/features/profile/profile_screen.dart'
text = read(path)
text = text.replace("'My Status'", "'My moment'")
text = text.replace("'Status'", "'Moment'")
write(path, text)

path = 'lib/features/settings/settings_root_screen.dart'
text = read(path)
text = text.replace("'Status'", "'Moments'")
write(path, text)


# Build-time invariants.
checks = {
    'lib/main.dart': [
        'ChatyCallActivityCapsule(',
        "title: 'Couldn’t answer the call'",
    ],
    'lib/features/updates/updates_screen.dart': [
        "'MOMENTS'",
        'ChatyMomentRing(',
        "'RECENT ACTIVITY'",
    ],
}
for file_path, needles in checks.items():
    value = read(file_path)
    for needle in needles:
        if needle not in value:
            raise SystemExit(f'P8-P10 invariant missing: {file_path}: {needle}')

print('Frontend P8-P10 integration applied.')
