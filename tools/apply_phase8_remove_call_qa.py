from pathlib import Path
import re

service_path = Path('lib/data/services/call_signaling_service.dart')
service = service_path.read_text(encoding='utf-8')

# Remove the entire debug-only call preview API. Production source should not
# carry a parallel fake call state machine, even behind kDebugMode.
service, count = re.subn(
    r"\n  /// Debug-only isolated media preview\.[\s\S]*?\n  /// Starts a real outgoing WebRTC call\.",
    "\n  /// Starts a real outgoing WebRTC call.",
    service,
    count=1,
)
if count != 1 and 'startMockCallForQA' in service:
    raise SystemExit('Unable to remove startMockCallForQA deterministically')

# QA IDs must not alter persistence/teardown semantics. Once the preview API is
# removed every call is authoritative, so collapse the old debug branches.
service = service.replace(
    "      if (!session.callId.startsWith('qa_')) {\n"
    "        await _client.rpc(\n"
    "          'end_call_session',\n"
    "          params: <String, dynamic>{'p_call_id': session.callId},\n"
    "        );\n"
    "      }",
    "      await _client.rpc(\n"
    "        'end_call_session',\n"
    "        params: <String, dynamic>{'p_call_id': session.callId},\n"
    "      );",
)
service = service.replace(
    "      if (!session.callId.startsWith('qa_')) {\n"
    "        final direction = session.isOutgoing\n"
    "            ? CallDirection.outgoing\n"
    "            : (duration > 0 ? CallDirection.incoming : CallDirection.missed);\n"
    "        _logCallRecord(direction, duration);\n"
    "      }",
    "      final direction = session.isOutgoing\n"
    "          ? CallDirection.outgoing\n"
    "          : (duration > 0 ? CallDirection.incoming : CallDirection.missed);\n"
    "      _logCallRecord(direction, duration);",
)
service = service.replace(
    "    if (session == null ||\n"
    "        myId == null ||\n"
    "        session.callId.startsWith('qa_')) {\n"
    "      return;\n"
    "    }",
    "    if (session == null || myId == null) return;",
)
service = service.replace(
    "    if (!session.callId.startsWith('qa_')) {\n"
    "      unawaited(_markServerCallFailed(session.callId));\n"
    "    }",
    "    unawaited(_markServerCallFailed(session.callId));",
)

for forbidden in ('startMockCallForQA', 'qa_local_preview', "startsWith('qa_')"):
    if forbidden in service:
        raise SystemExit(f'Production call service still contains {forbidden}')
service_path.write_text(service, encoding='utf-8')

screen_path = Path('lib/features/calls/calls_screen.dart')
screen = screen_path.read_text(encoding='utf-8')
screen = screen.replace("import 'package:flutter/foundation.dart';\n", '')

screen, count = re.subn(
    r"\n  Future<void> _openDebugPreview\(BuildContext context\) async \{[\s\S]*?\n  \}\n\n  @override",
    "\n\n  @override",
    screen,
    count=1,
)
if count != 1 and '_openDebugPreview' in screen:
    raise SystemExit('Unable to remove debug call preview UI deterministically')

screen = re.sub(
    r"\n\s*if \(kDebugMode\)\n\s*Semantics\([\s\S]*?\n\s*\),\n\s*\],",
    "\n                ],",
    screen,
    count=1,
)
screen = screen.replace(
    "                actionLabel: kDebugMode ? 'Open local media QA' : null,\n"
    "                onAction: kDebugMode ? () => _openDebugPreview(context) : null,\n",
    '',
)

for forbidden in ('kDebugMode', '_openDebugPreview', 'Local media QA', 'startMockCallForQA'):
    if forbidden in screen:
        raise SystemExit(f'Production calls screen still contains {forbidden}')
screen_path.write_text(screen, encoding='utf-8')
