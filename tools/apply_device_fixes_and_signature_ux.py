#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


def write(path, text):
    (ROOT / path).write_text(text, encoding='utf-8')


def replace_required(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing marker for {label}')
    return text.replace(old, new, 1)

# 1) Android MLS compatibility. openmls 2.x uses std::fs file locking that is
# unsupported on some Android/F2FS builds. Pin the last lock-free wrapper and
# serialize all app-facing MLS work through one process-wide gate.
pub = read('pubspec.yaml')
pub = re.sub(r'  openmls: \^2\.0\.1', '  openmls: 1.4.2', pub)
write('pubspec.yaml', pub)

mls_path = 'lib/data/services/mls_e2ee_service.dart'
mls = read(mls_path)
if 'final _ConversationGate _engineGate = _ConversationGate();' not in mls:
    mls = replace_required(
        mls,
        "  final Map<String, _ConversationGate> _conversationGates =\n      <String, _ConversationGate>{};\n",
        "  final Map<String, _ConversationGate> _conversationGates =\n      <String, _ConversationGate>{};\n  final _ConversationGate _engineGate = _ConversationGate();\n",
        'MLS global gate field',
    )

mls = mls.replace(
    "    return _gate(conversationId).run<MlsConversationState>(\n      () => _ensureConversationReadyLocked(conversationId),\n    );",
    "    return _engineGate.run<MlsConversationState>(\n      () => _gate(conversationId).run<MlsConversationState>(\n        () => _ensureConversationReadyLocked(conversationId),\n      ),\n    );",
)
mls = mls.replace(
    "    return _gate(conversationId).run<MlsEncryptedPayload>(() async {",
    "    return _engineGate.run<MlsEncryptedPayload>(() =>\n      _gate(conversationId).run<MlsEncryptedPayload>(() async {",
    1,
)
# close first encrypt wrapper before method end
needle = "      return MlsEncryptedPayload(\n        groupId: group.groupId,\n        epoch: localEpoch.toInt(),\n        ciphertext: base64Encode(encrypted.ciphertext),\n      );\n    });\n  }"
if needle in mls:
    mls = mls.replace(needle, needle.replace("    });\n  }", "    }));\n  });\n  }"), 1)

# Decrypt wrapper
mls = mls.replace(
    "    return _gate(conversationId).run<Map<String, dynamic>>(() async {",
    "    return _engineGate.run<Map<String, dynamic>>(() =>\n      _gate(conversationId).run<Map<String, dynamic>>(() async {",
    1,
)
needle = "      return Map<String, dynamic>.from(envelope['payload'] as Map);\n    });\n  }"
if needle in mls:
    mls = mls.replace(needle, "      return Map<String, dynamic>.from(envelope['payload'] as Map);\n    }));\n  });\n  }", 1)

# Exporter wrapper
mls = mls.replace(
    "    return _gate(conversationId).run<Uint8List>(() async {",
    "    return _engineGate.run<Uint8List>(() =>\n      _gate(conversationId).run<Uint8List>(() async {",
    1,
)
needle = "      return _requireEngine().exportSecret(\n        groupIdBytes: base64Decode(group.groupId),\n        label: 'chaty-attachment-v1',\n        context: utf8.encode('$conversationId:$attachmentId'),\n        keyLength: 32,\n      );\n    });\n  }"
if needle in mls:
    mls = mls.replace(needle, needle.replace("    });\n  }", "    }));\n  });\n  }"), 1)

# Real device fingerprint for the Security Center.
if 'Future<String> deviceFingerprint() async' not in mls:
    insert = """
  Future<String> deviceFingerprint() async {
    await initializeForCurrentSession();
    final key = _requireSignerPublicKey();
    final digest = await Sha256().hash(key);
    final hex = digest.bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return List<String>.generate(
      (hex.length / 4).ceil(),
      (index) => hex.substring(index * 4, (index * 4 + 4).clamp(0, hex.length)),
    ).join(' ');
  }

"""
    mls = mls.replace('  Future<void> close() async {', insert + '  Future<void> close() async {', 1)
    if "package:cryptography/cryptography.dart" not in mls:
        mls = mls.replace("import 'package:flutter/foundation.dart';", "import 'package:cryptography/cryptography.dart';\nimport 'package:flutter/foundation.dart';", 1)
write(mls_path, mls)

# 2) Calls: TURN is preferred, not a hard precondition. Use STUN-only direct
# ICE when the relay function is not configured so testing and permissive
# networks work immediately. If ICE later fails, UI reports a concise message.
call_path = 'lib/data/services/call_signaling_service.dart'
call = read(call_path)
old = """  Future<List<Map<String, dynamic>>> _loadIceServers() async {
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
"""
new = """  Future<List<Map<String, dynamic>>> _loadIceServers() async {
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
          .toList(growable: true);
      return <Map<String, dynamic>>[...directServers, ...relays];
    } catch (error) {
      debugPrint('Chaty TURN lookup failed; using direct ICE/STUN: $error');
      return directServers;
    }
  }
"""
call = replace_required(call, old, new, 'TURN fallback')
call = call.replace("_markTransportFailed('ICE negotiation failed.');", "_markTransportFailed('Call could not connect on this network.');")
write(call_path, call)

# 3) Security Center: remove simulated/demo claims and use the actual MLS
# engine fingerprint. Do not leave Biometric selected when the device cannot
# perform biometric auth; fall back to device credential while keeping lock on.
sec_path = 'lib/features/settings/security/security_center_screen.dart'
sec = read(sec_path)
if "import '../../../data/services/mls_e2ee_service.dart';" not in sec:
    sec = sec.replace("import '../../../data/services/local_lock_service.dart';", "import '../../../data/services/local_lock_service.dart';\nimport '../../../data/services/mls_e2ee_service.dart';", 1)
sec = sec.replace('  bool _biometricAvailable = false;', '  bool _biometricAvailable = false;\n  String? _deviceFingerprint;')
old_load = """  Future<void> _loadCapabilities() async {
    final pinLength = await _lockService.getPinLength();
    final biometricAvailable = await _lockService.canUseBiometrics();
    if (!mounted) return;
    setState(() {
      _pinLength = pinLength;
      _biometricAvailable = biometricAvailable;
    });
  }
"""
new_load = """  Future<void> _loadCapabilities() async {
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
"""
sec = replace_required(sec, old_load, new_load, 'security capability load')
# Replace static safety number dialog payload/copy.
sec = sec.replace("'05423 89104 33812 77192\\n44901 88321 00192 44381'", "_deviceFingerprint ?? 'Security keys are initializing…'")
sec = sec.replace("'Demo Security Model • End-to-end encryption state verified with prekey identity.'", "'This is this device’s MLS identity fingerprint. Compare it with your contact through a trusted channel before marking the device verified.'")
sec = sec.replace("'All conversations in Chaty utilize local end-to-end encryption simulation.'", "'Chaty messages use RFC 9420 MLS encryption. Device keys stay on this device and conversation membership changes advance the MLS epoch.'")
sec = sec.replace("title: 'Demo Security Model',", "title: 'MLS Device Security',")
sec = sec.replace("subtitle: 'Double-ratchet session status: Active & Verified',", "subtitle: _deviceFingerprint == null\n                  ? 'Initializing encrypted device identity…'\n                  : 'RFC 9420 MLS device identity is active',")
sec = sec.replace("subtitle: 'Simulate key fingerprints for security auditing',", "subtitle: 'Compare the real device fingerprint before trusting a device',")
sec = sec.replace("const Text('Safety Number Verification')", "const Text('Device Fingerprint Verification')")
write(sec_path, sec)

# 4) Unique default Chaty theme. Keep legacy ids readable for migration but
# stop using/advertising WhatsApp as the app identity.
theme_path = 'lib/ui/core/theme/theme_presets.dart'
theme = read(theme_path)
if 'static const ThemeConfig chatyAuroraLight' not in theme:
    marker = "  /// WhatsApp iOS (light) — the app's default identity. Authentic palette:"
    if marker not in theme:
        raise SystemExit('theme insertion marker missing')
    aurora = """  /// Chaty's signature palette: indigo-violet base with cyan highlights.
  /// The colors are intentionally independent from other messaging brands.
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
    deliveryTickStyle: DeliveryIconStyle.material,
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
    deliveryTickStyle: DeliveryIconStyle.material,
    wallpaperId: 'none',
  );

"""
    theme = theme.replace(marker, aurora + "  /// Legacy green light palette retained only for persisted-theme migration.", 1)
# remove WhatsApp labels from visible preset names/comments and set defaults
theme = theme.replace("name: 'WhatsApp iOS'", "name: 'Legacy Green Light'")
theme = theme.replace("name: 'WhatsApp iOS Dark'", "name: 'Legacy Green Dark'")
theme = theme.replace('    whatsappIosLight,\n    whatsappIosDark,', '    chatyAuroraLight,\n    chatyAuroraDark,\n    whatsappIosLight,\n    whatsappIosDark,')
theme = theme.replace('return brightness == Brightness.light ? whatsappIosLight : whatsappIosDark;', 'return brightness == Brightness.light ? chatyAuroraLight : chatyAuroraDark;')
write(theme_path, theme)

# 5) Rename user-visible WhatsApp labels in lib/ without touching enum/property
# identifiers required by persistence.
for path in (ROOT / 'lib').rglob('*.dart'):
    text = path.read_text(encoding='utf-8')
    updated = text
    # Visible/common labels and comments. Identifiers like topWhatsAppBar and
    # whatsappLb deliberately remain for backwards-compatible persistence.
    updated = updated.replace("'WhatsApp Style'", "'Chaty Classic'")
    updated = updated.replace("'WhatsApp iOS'", "'Chaty Classic'")
    updated = updated.replace("'WhatsApp iOS Dark'", "'Chaty Classic Dark'")
    updated = updated.replace("'Whatsapp LB'", "'Chaty Soft'")
    updated = updated.replace('WHATSAPP-STYLE', 'CHATY CLASSIC')
    updated = updated.replace('WhatsApp-style', 'Chaty classic')
    updated = updated.replace('WhatsApp iOS', 'Chaty Classic')
    updated = updated.replace('WhatsApp-iOS', 'Chaty Classic')
    if updated != text:
        path.write_text(updated, encoding='utf-8')

# 6) Export signature components.
design_path = 'lib/ui/core/design_system/design_system.dart'
design = read(design_path)
if "export 'components/signature_components.dart';" not in design:
    design = design.replace("export 'components/chaty_kit.dart';", "export 'components/chaty_kit.dart';\nexport 'components/signature_components.dart';")
write(design_path, design)

# 7) Wire signature task card and composer treatment into the main chat.
bubble_path = 'lib/features/messages/message_bubble.dart'
bubble = read(bubble_path)
if "../core/design_system/design_system.dart" not in bubble and "design_system/design_system.dart" not in bubble:
    # determine existing import area conservatively
    bubble = bubble.replace("import 'package:flutter/material.dart';", "import 'package:flutter/material.dart';\n\nimport '../../ui/core/design_system/design_system.dart';", 1)
old_task = re.compile(r"                        if \(message\.type == MessageType\.taskCard\)\n                          InkWell\(.*?\n                          \),\n                        // Real consumer", re.S)
m = old_task.search(bubble)
if m:
    replacement = """                        if (message.type == MessageType.taskCard)
                          ChatyTaskCard(
                            title: message.text,
                            status: message.metadata['status']?.toString() ?? 'Open',
                            assignee: message.metadata['assignee_name']?.toString(),
                            onTap: onTaskTap,
                          ),
                        // Real consumer"""
    bubble = bubble[:m.start()] + replacement + bubble[m.end():]
write(bubble_path, bubble)

chat_path = 'lib/features/chats/chat_detail_screen.dart'
chat = read(chat_path)
# Transform composer background and hint into signature mode, without changing
# the proven send/record behavior.
chat = chat.replace("hintText: 'Message…  /task or #reply',", "hintText: 'Message…  type / for commands',")
chat = chat.replace(
    "        Expanded(\n          child: TextField(",
    "        Expanded(\n          child: ChatyComposerShell(\n            commandMode: widget.controller.text.trimLeft().startsWith('/'),\n            child: TextField(",
    1,
)
# close shell immediately after TextField in this specific composer region
needle = "            ),\n          ),\n        ),\n        const SizedBox(width: 6),\n        ValueListenableBuilder<TextEditingValue>("
if needle in chat:
    chat = chat.replace(needle, "            ),\n          ),\n        ),\n        ),\n        const SizedBox(width: 6),\n        ValueListenableBuilder<TextEditingValue>(", 1)
write(chat_path, chat)

# 8) Never show raw internal exception paths to users for attachment failures.
attach_path = 'lib/features/messages/chat_attachment_actions.dart'
attach = read(attach_path)
attach = attach.replace("_toast(context, 'Unable to send $type: $error');", "debugPrint('Chaty media send failed: $error');\n      _toast(context, 'Couldn’t send this item. Check the connection and try again.');")
attach = attach.replace("_toast(context, 'Unable to send voice note: $error');", "debugPrint('Chaty voice send failed: $error');\n      _toast(context, 'Couldn’t send the voice note. Please try again.');")
attach = attach.replace("_toast(context, 'Unable to share location: $error');", "debugPrint('Chaty location share failed: $error');\n      _toast(context, 'Couldn’t share the location. Check location permission and try again.');")
attach = attach.replace("_toast(context, 'Unable to share contact: $error');", "debugPrint('Chaty contact share failed: $error');\n      _toast(context, 'Couldn’t share the contact. Please try again.');")
attach = attach.replace("_toast(context, 'Unable to create poll: $error');", "debugPrint('Chaty poll creation failed: $error');\n      _toast(context, 'Couldn’t create the poll. Please try again.');")
if "../../ui/core/design_system/design_system.dart" not in attach:
    attach = attach.replace("import '../../ui/core/controllers/preferences_controller.dart';", "import '../../ui/core/controllers/preferences_controller.dart';\nimport '../../ui/core/design_system/design_system.dart';", 1)
old_toast = """  static void _toast(BuildContext context, String message) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
"""
new_toast = """  static void _toast(BuildContext context, String message) {
    if (!context.mounted) return;
    ChatyActivityIsland.show(
      context,
      icon: message.toLowerCase().contains('sent') ? Icons.done_rounded : Icons.info_outline_rounded,
      title: message,
    );
  }
"""
if old_toast in attach:
    attach = attach.replace(old_toast, new_toast, 1)
write(attach_path, attach)

# 9) Brand the main navigation comments/labels and make overflow/activity
# styling derive from the new theme automatically.
nav_path = 'lib/features/chats/main_navigation_shell.dart'
nav = read(nav_path)
nav = nav.replace('// 1. TOP WHATSAPP-STYLE GREEN TAB BAR (Image 1)', '// 1. TOP CHATY CLASSIC TAB BAR')
write(nav_path, nav)

# Contract check: no user-visible demo security or hard TURN startup error.
checks = {
    sec_path: ['MLS Device Security', 'RFC 9420 MLS device identity is active'],
    call_path: ['using direct ICE/STUN fallback', 'stun.cloudflare.com:3478'],
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
