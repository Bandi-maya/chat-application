#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Chaty Tidal identity — Deep Ocean + Seafoam + Warm Coral.
# Explicitly removes the previous indigo/violet-heavy default while retaining
# stable theme IDs so existing installations migrate safely.
# ---------------------------------------------------------------------------
path = 'lib/ui/core/theme/theme_presets.dart'
text = read(path)
replacements = {
    "name: 'Chaty Aurora',": "name: 'Chaty Tidal',",
    "name: 'Chaty Aurora Dark',": "name: 'Chaty Tidal Dark',",
    'Color(0xFF5B5FF2)': 'Color(0xFF087F8C)',
    'Color(0xFFF8F9FF)': 'Color(0xFFF7FAF9)',
    'Color(0xFFF0F1FB)': 'Color(0xFFEAF3F1)',
    'Color(0xFF15162A)': 'Color(0xFF12201E)',
    'Color(0xFF686B86)': 'Color(0xFF61736F)',
    'Color(0xFFDFE1FF)': 'Color(0xFFD8F0EC)',
    'Color(0xFF171831)': 'Color(0xFF12201E)',
    'Color(0xFF0A8FBF)': 'Color(0xFF147D96)',
    'Color(0xFFD9365C)': 'Color(0xFFE25555)',
    'Color(0xFF14A27A)': 'Color(0xFF0B8F78)',
    'Color(0xFF8A8DFF)': 'Color(0xFF5ED6C4)',
    'Color(0xFF0A0B16)': 'Color(0xFF071513)',
    'Color(0xFF111323)': 'Color(0xFF0D211E)',
    'Color(0xFF1A1D31)': 'Color(0xFF15302C)',
    'Color(0xFFF4F4FF)': 'Color(0xFFF1FAF7)',
    'Color(0xFFA7AAC8)': 'Color(0xFF9DB7B1)',
    'Color(0xFF343A82)': 'Color(0xFF164C46)',
    'Color(0xFF1B1E31)': 'Color(0xFF112824)',
    'Color(0xFFF8F8FF)': 'Color(0xFFF5FCFA)',
    'Color(0xFF62D6FF)': 'Color(0xFF66CDE1)',
    'Color(0xFFFF6A89)': 'Color(0xFFFF746A)',
    'Color(0xFF33D6A2)': 'Color(0xFF52D5B8)',
}
for old, new in replacements.items():
    text = text.replace(old, new)
write(path, text)

# Replace the common purple fallback avatar only; user-selected/custom avatar
# colors remain untouched.
for dart_path in (ROOT / 'lib').rglob('*.dart'):
    original = dart_path.read_text(encoding='utf-8')
    updated = original.replace("'0xFF6366F1'", "'0xFF087F8C'")
    if updated != original:
        dart_path.write_text(updated, encoding='utf-8')

# ---------------------------------------------------------------------------
# Global ongoing-call capsule from every screen.
# ---------------------------------------------------------------------------
path = 'lib/main.dart'
text = read(path)
if "package:chat/ui/core/design_system/components/call_activity_capsule.dart" not in text:
    text = text.replace(
        "import 'package:chat/ui/core/controllers/app_icon_controller.dart';",
        "import 'package:chat/ui/core/controllers/app_icon_controller.dart';\n"
        "import 'package:chat/ui/core/design_system/components/call_activity_capsule.dart';",
        1,
    )

# Add compact status helper to the state.
if 'String _callCapsuleStatus(ChatyCallSession session)' not in text:
    marker = '  @override\n  Widget build(BuildContext context) {'
    helper = r'''  String _callCapsuleStatus(ChatyCallSession session) {
    switch (session.state) {
      case CallSessionState.ringing:
        return session.isOutgoing ? 'Ringing…' : 'Incoming call';
      case CallSessionState.connecting:
        return 'Connecting…';
      case CallSessionState.connected:
        final seconds = _callService.callDurationSeconds;
        final minutes = (seconds ~/ 60).toString().padLeft(2, '0');
        final rest = (seconds % 60).toString().padLeft(2, '0');
        return 'Connected · $minutes:$rest';
      case CallSessionState.reconnecting:
        return 'Reconnecting…';
      default:
        return session.isVideo ? 'Video call' : 'Voice call';
    }
  }

  bool _showGlobalCallCapsule(ChatyCallSession? session) {
    if (session == null) return false;
    return session.state == CallSessionState.ringing ||
        session.state == CallSessionState.connecting ||
        session.state == CallSessionState.connected ||
        session.state == CallSessionState.reconnecting;
  }

'''
    if marker not in text:
        raise SystemExit('main build marker missing')
    text = text.replace(marker, helper + marker, 1)

# Root app builder previously returns appContent directly when there is no lock
# or incoming call. Wrap it whenever an ongoing call exists.
old = '''            final showIncoming =
                incomingCall != null && _backend.isAuthenticated;
            if (!shouldShowLock && !showIncoming) return appContent;
            return Stack(
              fit: StackFit.expand,
              children: [
                appContent,
                if (showIncoming)
'''
new = '''            final showIncoming =
                incomingCall != null && _backend.isAuthenticated;
            final showCallCapsule =
                _backend.isAuthenticated &&
                !showIncoming &&
                _showGlobalCallCapsule(callSession);
            if (!shouldShowLock && !showIncoming && !showCallCapsule) {
              return appContent;
            }
            return Stack(
              fit: StackFit.expand,
              children: [
                appContent,
                if (showCallCapsule && callSession != null)
                  Align(
                    alignment: Alignment.topCenter,
                    child: ChatyCallActivityCapsule(
                      contactName: callSession.remoteDisplayName,
                      status: _callCapsuleStatus(callSession),
                      isVideo: callSession.isVideo,
                      isSpeaker:
                          callSession.audioRoute == AudioRouteType.speaker,
                      onOpen: () {
                        _rootNavigatorKey.currentState?.push(
                          MaterialPageRoute(
                            builder: (_) => OngoingCallScreen(
                              theme: currentTheme,
                            ),
                          ),
                        );
                      },
                      onToggleSpeaker: () {
                        _callService.setAudioRoute(
                          callSession.audioRoute == AudioRouteType.speaker
                              ? AudioRouteType.earpiece
                              : AudioRouteType.speaker,
                        );
                      },
                      onHangUp: () => unawaited(_callService.endCall()),
                    ),
                  ),
                if (showIncoming)
'''
if old in text:
    text = text.replace(old, new, 1)
write(path, text)

# Export the global call capsule through the canonical design-system barrel.
path = 'lib/ui/core/design_system/design_system.dart'
text = read(path)
export = "export 'components/call_activity_capsule.dart';"
if export not in text:
    text += '\n' + export + '\n'
write(path, text)

# ---------------------------------------------------------------------------
# Updates: replace technical publish errors with the same activity language.
# ---------------------------------------------------------------------------
path = 'lib/features/updates/updates_screen.dart'
text = read(path)
raw = '''                  ScaffoldMessenger.of(sheetContext).showSnackBar(
                    SnackBar(
                      content: Text(
                        error.toString().replaceFirst('Exception: ', ''),
                      ),
                    ),
                  );'''
friendly = '''                  debugPrint('Chaty update publish failed: $error');
                  ChatyActivityIsland.show(
                    sheetContext,
                    icon: Icons.cloud_off_rounded,
                    title: 'Couldn’t publish the update',
                    subtitle: 'Your content is still here. Check the connection and retry.',
                  );'''
text = text.replace(raw, friendly)
text = text.replace("'New Update'", "'Create update'")
text = text.replace(
    "'Updates expire automatically after 24 hours.'",
    "'Share a moment, voice clip, file, or note. Updates expire after 24 hours.'",
)
write(path, text)

# Ensure the attachment surface describes actions rather than resembling a
# generic icon grid.
path = 'lib/features/messages/attachment_sheet.dart'
text = read(path)
text = text.replace("'Send something'", "'Create or share'")
text = text.replace(
    "'Pick an action — Chaty secures media before upload.'",
    "'Choose what belongs in this conversation.'",
)
write(path, text)

# Build-time invariants for this round.
for file_path, needle in (
    ('lib/ui/core/theme/theme_presets.dart', "name: 'Chaty Tidal'"),
    ('lib/main.dart', 'ChatyCallActivityCapsule('),
    ('lib/features/messages/attachment_sheet.dart', "'Create or share'"),
):
    if needle not in read(file_path):
        raise SystemExit(f'UX round-3 invariant missing: {file_path}: {needle}')

print('Research-driven Chaty UX round 3 applied.')
