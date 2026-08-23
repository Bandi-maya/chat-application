#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


def replace_required(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing marker for {label}')
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1. Android MLS compatibility.
# ---------------------------------------------------------------------------
# openmls 2.x introduced an advisory <db>.lock file. On the user's Android
# filesystem std::fs::File::try_lock returns "not supported", preventing every
# encrypted attachment operation. 1.4.2 predates that wrapper-level file lock
# while retaining the RFC 9420 engine/API used by Chaty. Keep the existing
# per-conversation serialization until an Android-safe 2.x lock implementation
# is available upstream.
pub = read('pubspec.yaml')
pub = re.sub(r'  openmls:\s*\^?2\.0\.1', '  openmls: 1.4.2', pub)
write('pubspec.yaml', pub)

mls_path = 'lib/data/services/mls_e2ee_service.dart'
mls = read(mls_path)
if 'Future<String> deviceFingerprint() async' not in mls:
    if "package:cryptography/cryptography.dart" not in mls:
        mls = mls.replace(
            "import 'package:flutter/foundation.dart';",
            "import 'package:cryptography/cryptography.dart';\nimport 'package:flutter/foundation.dart';",
            1,
        )
    method = r'''
  Future<String> deviceFingerprint() async {
    await initializeForCurrentSession();
    final key = _requireSignerPublicKey();
    final digest = await Sha256().hash(key);
    final hex = digest.bytes
        .map((byte) => byte.toRadixString(16).padLeft(2, '0'))
        .join();
    final groups = <String>[];
    for (var offset = 0; offset < hex.length; offset += 4) {
      groups.add(hex.substring(offset, (offset + 4).clamp(0, hex.length)));
    }
    return groups.join(' ');
  }

'''
    mls = replace_required(
        mls,
        '  Future<void> close() async {',
        method + '  Future<void> close() async {',
        'MLS close method',
    )
write(mls_path, mls)


# ---------------------------------------------------------------------------
# 2. Calls: TURN is preferred, but it must not block direct WebRTC testing.
# ---------------------------------------------------------------------------
call_path = 'lib/data/services/call_signaling_service.dart'
call = read(call_path)
old_turn = r'''  Future<List<Map<String, dynamic>>> _loadIceServers() async {
    final response = await _client.functions.invoke(
      'turn-credentials',
      body: const <String, dynamic>{},
    );
    if (response.status != 200) {
      throw StateError(
        'Secure call relay is not configured. TURN credentials are required before calls can start.',
      );
    }
    final data = Map<String, dynamic>.from(response.data as Map);
    final rawServers = data['ice_servers'];
    if (rawServers is! List || rawServers.isEmpty) {
      throw StateError('The call relay returned no ICE servers.');
    }
    return rawServers
        .whereType<Map>()
        .map((server) => Map<String, dynamic>.from(server))
        .toList(growable: false);
  }
'''
new_turn = r'''  Future<List<Map<String, dynamic>>> _loadIceServers() async {
    final directServers = <Map<String, dynamic>>[
      <String, dynamic>{
        'urls': <String>[
          'stun:stun.l.google.com:19302',
          'stun:stun1.l.google.com:19302',
          'stun:stun.cloudflare.com:3478',
        ],
      },
    ];
    try {
      final response = await _client.functions.invoke(
        'turn-credentials',
        body: const <String, dynamic>{},
      );
      if (response.status != 200 || response.data is! Map) {
        debugPrint('Chaty TURN unavailable; using direct ICE/STUN fallback.');
        return directServers;
      }
      final data = Map<String, dynamic>.from(response.data as Map);
      final rawServers = data['ice_servers'];
      if (rawServers is! List || rawServers.isEmpty) return directServers;
      final relays = rawServers
          .whereType<Map>()
          .map((server) => Map<String, dynamic>.from(server))
          .toList(growable: false);
      return <Map<String, dynamic>>[...directServers, ...relays];
    } catch (error) {
      debugPrint('Chaty TURN lookup failed; using direct ICE/STUN: $error');
      return directServers;
    }
  }
'''
if old_turn in call:
    call = call.replace(old_turn, new_turn, 1)
elif 'stun:stun.cloudflare.com:3478' not in call:
    raise SystemExit('TURN loader marker not found')
call = call.replace(
    "_markTransportFailed('ICE negotiation failed.');",
    "_markTransportFailed('Call could not connect on this network.');",
)
write(call_path, call)


# ---------------------------------------------------------------------------
# 3. Security Center: real MLS status/fingerprint + safe biometric fallback.
# ---------------------------------------------------------------------------
sec_path = 'lib/features/settings/security/security_center_screen.dart'
sec = read(sec_path)
if "import '../../../data/services/mls_e2ee_service.dart';" not in sec:
    sec = sec.replace(
        "import '../../../data/services/local_lock_service.dart';",
        "import '../../../data/services/local_lock_service.dart';\n"
        "import '../../../data/services/mls_e2ee_service.dart';",
        1,
    )
if 'String? _deviceFingerprint;' not in sec:
    sec = sec.replace(
        '  bool _biometricAvailable = false;',
        '  bool _biometricAvailable = false;\n  String? _deviceFingerprint;',
        1,
    )
old_load = r'''  Future<void> _loadCapabilities() async {
    final pinLength = await _lockService.getPinLength();
    final biometricAvailable = await _lockService.canUseBiometrics();
    if (!mounted) return;
    setState(() {
      _pinLength = pinLength;
      _biometricAvailable = biometricAvailable;
    });
  }
'''
new_load = r'''  Future<void> _loadCapabilities() async {
    final pinLength = await _lockService.getPinLength();
    final biometricAvailable = await _lockService.canUseBiometrics();
    String? fingerprint;
    try {
      fingerprint = await locator<MlsE2eeService>().deviceFingerprint();
    } catch (_) {}
    if (!mounted) return;
    final security = widget.preferencesController.security;
    if (security.isAppLockEnabled &&
        security.lockMethod == 'Biometric' &&
        !biometricAvailable) {
      widget.preferencesController.updateSecurity(
        security.copyWith(lockMethod: 'Device Credential'),
        logTitle: 'Biometric unavailable: use device credential',
      );
    }
    setState(() {
      _pinLength = pinLength;
      _biometricAvailable = biometricAvailable;
      _deviceFingerprint = fingerprint;
    });
  }
'''
if old_load in sec:
    sec = sec.replace(old_load, new_load, 1)
sec = sec.replace(
    "'05423 89104 33812 77192\\n44901 88321 00192 44381'",
    "_deviceFingerprint ?? 'Security keys are initializing…'",
)
sec = sec.replace(
    "'Demo Security Model • End-to-end encryption state verified with prekey identity.'",
    "'This is this device’s MLS identity fingerprint. Compare it through a trusted channel before trusting the device.'",
)
sec = sec.replace(
    "'All conversations in Chaty utilize local end-to-end encryption simulation.'",
    "'Chaty messages use RFC 9420 MLS encryption. Device keys stay on this device and membership changes advance the MLS epoch.'",
)
sec = sec.replace("title: 'Demo Security Model',", "title: 'MLS Device Security',")
sec = sec.replace(
    "subtitle: 'Double-ratchet session status: Active & Verified',",
    "subtitle: _deviceFingerprint == null\n"
    "                  ? 'Initializing encrypted device identity…'\n"
    "                  : 'RFC 9420 MLS device identity is active',",
)
sec = sec.replace(
    "subtitle: 'Simulate key fingerprints for security auditing',",
    "subtitle: 'Compare the real device fingerprint before trusting a device',",
)
sec = sec.replace(
    "const Text('Safety Number Verification')",
    "const Text('Device Fingerprint Verification')",
)
write(sec_path, sec)


# ---------------------------------------------------------------------------
# 4. Chaty's own default visual identity.
# ---------------------------------------------------------------------------
theme_path = 'lib/ui/core/theme/theme_presets.dart'
theme = read(theme_path)
if 'static const ThemeConfig chatyAuroraLight' not in theme:
    marker = "  /// WhatsApp iOS (light) — the app's default identity. Authentic palette:"
    if marker not in theme:
        raise SystemExit('theme insertion marker missing')
    aurora = r'''  /// Chaty's signature palette: indigo-violet with cyan highlights.
  static const ThemeConfig chatyAuroraLight = ThemeConfig(
    id: 'chaty_aurora_light',
    name: 'Chaty Aurora',
    brightness: Brightness.light,
    accentColor: Color(0xFF5B5FF2),
    backgroundColor: Color(0xFFF8F9FF),
    surfaceColor: Color(0xFFFFFFFF),
    cardColor: Color(0xFFF0F1FB),
    primaryTextColor: Color(0xFF15162A),
    secondaryTextColor: Color(0xFF686B86),
    outgoingBubbleColor: Color(0xFFDFE1FF),
    incomingBubbleColor: Color(0xFFFFFFFF),
    outgoingTextColor: Color(0xFF171831),
    incomingTextColor: Color(0xFF171831),
    linkColor: Color(0xFF0A8FBF),
    dangerColor: Color(0xFFD9365C),
    successColor: Color(0xFF14A27A),
    cornerRadius: 19.0,
    density: 1.0,
    fontScale: 1.0,
    bubbleStyle: BubbleStyleId.rounded,
    deliveryTickStyle: DeliveryIconStyle.cirCheck,
    wallpaperId: 'none',
  );

  static const ThemeConfig chatyAuroraDark = ThemeConfig(
    id: 'chaty_aurora_dark',
    name: 'Chaty Aurora Dark',
    brightness: Brightness.dark,
    accentColor: Color(0xFF8A8DFF),
    backgroundColor: Color(0xFF0A0B16),
    surfaceColor: Color(0xFF111323),
    cardColor: Color(0xFF1A1D31),
    primaryTextColor: Color(0xFFF4F4FF),
    secondaryTextColor: Color(0xFFA7AAC8),
    outgoingBubbleColor: Color(0xFF343A82),
    incomingBubbleColor: Color(0xFF1B1E31),
    outgoingTextColor: Color(0xFFF8F8FF),
    incomingTextColor: Color(0xFFF4F4FF),
    linkColor: Color(0xFF62D6FF),
    dangerColor: Color(0xFFFF6A89),
    successColor: Color(0xFF33D6A2),
    cornerRadius: 19.0,
    density: 1.0,
    fontScale: 1.0,
    bubbleStyle: BubbleStyleId.rounded,
    deliveryTickStyle: DeliveryIconStyle.cirCheck,
    wallpaperId: 'none',
  );

'''
    theme = theme.replace(marker, aurora + '  /// Legacy green light palette retained for persisted-theme migration.\n', 1)
if '    chatyAuroraLight,' not in theme:
    theme = theme.replace(
        '  static const List<ThemeConfig> all = <ThemeConfig>[\n',
        '  static const List<ThemeConfig> all = <ThemeConfig>[\n'
        '    chatyAuroraLight,\n    chatyAuroraDark,\n',
        1,
    )
theme = theme.replace("name: 'WhatsApp iOS',", "name: 'Legacy Green Light',")
theme = theme.replace("name: 'WhatsApp iOS Dark',", "name: 'Legacy Green Dark',")
theme = theme.replace(
    'return brightness == Brightness.light ? whatsappIosLight : whatsappIosDark;',
    'return brightness == Brightness.light ? chatyAuroraLight : chatyAuroraDark;',
)
write(theme_path, theme)


# ---------------------------------------------------------------------------
# 5. Remove third-party brand names from user-visible Chaty copy while keeping
# persisted enum/setting identifiers unchanged for compatibility.
# ---------------------------------------------------------------------------
for dart_path in (ROOT / 'lib').rglob('*.dart'):
    original = dart_path.read_text(encoding='utf-8')
    updated = original
    replacements = {
        "'WhatsApp Style'": "'Chaty Classic'",
        "'WhatsApp iOS'": "'Chaty Classic'",
        "'WhatsApp iOS Dark'": "'Chaty Classic Dark'",
        "'Whatsapp LB'": "'Chaty Soft'",
        'WHATSAPP-STYLE': 'CHATY CLASSIC',
        'WhatsApp-style': 'Chaty classic',
        'WhatsApp iOS': 'Chaty Classic',
        'WhatsApp-iOS': 'Chaty Classic',
    }
    for old, new in replacements.items():
        updated = updated.replace(old, new)
    if updated != original:
        dart_path.write_text(updated, encoding='utf-8')


# ---------------------------------------------------------------------------
# 6. Export and wire signature components.
# ---------------------------------------------------------------------------
design_path = 'lib/ui/core/design_system/design_system.dart'
design = read(design_path)
if "export 'components/signature_components.dart';" not in design:
    design = design.replace(
        "export 'components/chaty_kit.dart';",
        "export 'components/chaty_kit.dart';\nexport 'components/signature_components.dart';",
        1,
    )
write(design_path, design)

bubble_path = 'lib/features/messages/message_bubble.dart'
bubble = read(bubble_path)
if "../../ui/core/design_system/design_system.dart" not in bubble:
    bubble = bubble.replace(
        "import 'package:flutter/material.dart';",
        "import 'package:flutter/material.dart';\n\n"
        "import '../../ui/core/design_system/design_system.dart';",
        1,
    )
pattern = re.compile(
    r"                        if \(message\.type == MessageType\.taskCard\)\n"
    r"                          InkWell\(.*?\n"
    r"                          \),\n"
    r"                        // Real consumer",
    re.S,
)
match = pattern.search(bubble)
if match:
    replacement = r'''                        if (message.type == MessageType.taskCard)
                          ChatyTaskCard(
                            title: message.text,
                            status: message.metadata['status']?.toString() ?? 'Open',
                            assignee: message.metadata['assignee_name']?.toString(),
                            onTap: onTaskTap,
                          ),
                        // Real consumer'''
    bubble = bubble[:match.start()] + replacement + bubble[match.end():]
write(bubble_path, bubble)

chat_path = 'lib/features/chats/chat_detail_screen.dart'
chat = read(chat_path)
chat = chat.replace(
    "hintText: 'Message…  /task or #reply',",
    "hintText: 'Message…  type / for commands',",
)
# Use a subtle Chaty shell without touching recording/send state machinery.
composer_marker = """        Expanded(
          child: TextField(
            controller: widget.controller,"""
composer_replacement = """        Expanded(
          child: ChatyComposerShell(
            commandMode: widget.controller.text.trimLeft().startsWith('/'),
            child: TextField(
              controller: widget.controller,"""
if composer_marker in chat:
    chat = chat.replace(composer_marker, composer_replacement, 1)
    close_marker = """            ),
          ),
        ),
        const SizedBox(width: 6),
        ValueListenableBuilder<TextEditingValue>("""
    if close_marker not in chat:
        raise SystemExit('composer close marker missing')
    chat = chat.replace(
        close_marker,
        """            ),
            ),
          ),
        ),
        const SizedBox(width: 6),
        ValueListenableBuilder<TextEditingValue>(""",
        1,
    )
write(chat_path, chat)


# ---------------------------------------------------------------------------
# 7. Replace raw internal errors with the Chaty Activity Island.
# ---------------------------------------------------------------------------
attach_path = 'lib/features/messages/chat_attachment_actions.dart'
attach = read(attach_path)
if "../../ui/core/design_system/design_system.dart" not in attach:
    attach = attach.replace(
        "import '../../ui/core/controllers/preferences_controller.dart';",
        "import '../../ui/core/controllers/preferences_controller.dart';\n"
        "import '../../ui/core/design_system/design_system.dart';",
        1,
    )
replacements = {
    "_toast(context, 'Unable to send $type: $error');":
        "debugPrint('Chaty media send failed: $error');\n"
        "      _toast(context, 'Couldn’t send this item. Check the connection and try again.');",
    "_toast(context, 'Unable to send voice note: $error');":
        "debugPrint('Chaty voice send failed: $error');\n"
        "      _toast(context, 'Couldn’t send the voice note. Please try again.');",
    "_toast(context, 'Unable to share location: $error');":
        "debugPrint('Chaty location share failed: $error');\n"
        "      _toast(context, 'Couldn’t share the location. Check location permission and try again.');",
    "_toast(context, 'Unable to share contact: $error');":
        "debugPrint('Chaty contact share failed: $error');\n"
        "      _toast(context, 'Couldn’t share the contact. Please try again.');",
    "_toast(context, 'Unable to create poll: $error');":
        "debugPrint('Chaty poll creation failed: $error');\n"
        "      _toast(context, 'Couldn’t create the poll. Please try again.');",
}
for old, new in replacements.items():
    attach = attach.replace(old, new)
old_toast = r'''  static void _toast(BuildContext context, String message) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
'''
new_toast = r'''  static void _toast(BuildContext context, String message) {
    if (!context.mounted) return;
    ChatyActivityIsland.show(
      context,
      icon: message.toLowerCase().contains('sent')
          ? Icons.done_rounded
          : Icons.info_outline_rounded,
      title: message,
    );
  }
'''
if old_toast in attach:
    attach = attach.replace(old_toast, new_toast, 1)
write(attach_path, attach)


# ---------------------------------------------------------------------------
# 8. Brand cleanup in root navigation source comments.
# ---------------------------------------------------------------------------
nav_path = 'lib/features/chats/main_navigation_shell.dart'
nav = read(nav_path)
nav = nav.replace(
    '// 1. TOP WHATSAPP-STYLE GREEN TAB BAR (Image 1)',
    '// 1. TOP CHATY CLASSIC TAB BAR',
)
write(nav_path, nav)


# Hard invariants.
checks = {
    mls_path: ['deviceFingerprint'],
    call_path: ['using direct ICE/STUN fallback', 'stun.cloudflare.com:3478'],
    sec_path: ['MLS Device Security', 'RFC 9420 MLS device identity is active'],
    theme_path: ['chatyAuroraLight', 'chatyAuroraDark'],
    design_path: ['signature_components.dart'],
    attach_path: ['Couldn’t send this item'],
}
for path, markers in checks.items():
    text = read(path)
    for marker in markers:
        if marker not in text:
            raise SystemExit(f'final invariant missing in {path}: {marker}')

print('Device runtime fixes + Chaty signature UX applied.')
