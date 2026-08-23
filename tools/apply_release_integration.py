#!/usr/bin/env python3
from __future__ import annotations

import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def git_show(ref: str, path: str) -> str:
    return subprocess.check_output(
        ["git", "show", f"origin/{ref}:{path}"],
        cwd=ROOT,
        text=True,
    )


def copy_from(ref: str, path: str, target: str | None = None) -> None:
    destination = ROOT / (target or path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(git_show(ref, path), encoding="utf-8")


def production_store_rename(path: str) -> None:
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    text = text.replace("mock_data_store.dart", "chaty_data_store.dart")
    text = text.replace("MockDataStore", "ChatyDataStore")
    file.write_text(text, encoding="utf-8")


def require(text: str, marker: str, label: str) -> None:
    if marker not in text:
        raise SystemExit(f"Integration marker missing for {label}: {marker}")


# ---------------------------------------------------------------------------
# Phase 2: encrypted attachment bytes + authenticated local rendering.
# This branch also carries the verified failure-atomic MLS membership fix, so
# its MLS implementation is the canonical integration copy.
# ---------------------------------------------------------------------------
phase2 = "phase-2-encrypted-attachments"
for path in [
    "lib/data/services/encrypted_attachment_codec.dart",
    "lib/data/services/uploaded_attachment_guard.dart",
    "lib/data/services/chat_media_service.dart",
    "lib/data/services/mls_e2ee_service.dart",
    "lib/data/services/voice_note_service.dart",
    "lib/domain/models/chat_message.dart",
    "lib/features/chats/contact_info_screen.dart",
    "lib/features/messages/chat_attachment_actions.dart",
    "lib/features/messages/media_viewer_screen.dart",
    "supabase/migrations/20260823025304_allow_encrypted_chat_media_blobs.sql",
    "test/encrypted_attachment_codec_test.dart",
    "test/uploaded_attachment_guard_test.dart",
]:
    copy_from(phase2, path)

for path in [
    "lib/data/services/voice_note_service.dart",
    "lib/features/chats/contact_info_screen.dart",
    "lib/features/messages/chat_attachment_actions.dart",
]:
    production_store_rename(path)

# ---------------------------------------------------------------------------
# Phase 4: bounded pagination, targeted realtime reconciliation, encrypted
# offline outbox. Phase 4 chat detail becomes the structural base because it
# owns scroll/pagination semantics. Phase 2/5 semantic deltas are applied below.
# ---------------------------------------------------------------------------
phase4 = "phase-4-realtime-offline"
for path in [
    "lib/data/services/backend_service.dart",
    "lib/data/services/encrypted_message_outbox.dart",
    "lib/features/chats/chat_detail_screen.dart",
    "test/encrypted_message_outbox_test.dart",
]:
    copy_from(phase4, path)
production_store_rename("lib/features/chats/chat_detail_screen.dart")

# Preserve Phase 8 production facade while exposing Phase 4 pagination API.
store_path = ROOT / "lib/data/repositories/chaty_data_store.dart"
store = store_path.read_text(encoding="utf-8")
if "bool hasOlderMessages(String conversationId)" not in store:
    marker = (
        "  Future<void> ensureConversationLoaded(String conversationId) =>\n"
        "      _backend.ensureConversationLoaded(conversationId);\n"
    )
    require(store, marker, "ChatyDataStore pagination insertion")
    store = store.replace(
        marker,
        marker
        + "\n  bool hasOlderMessages(String conversationId) =>\n"
        + "      _backend.hasOlderMessages(conversationId);\n\n"
        + "  Future<bool> loadOlderMessages(String conversationId) =>\n"
        + "      _backend.loadOlderMessages(conversationId);\n",
        1,
    )
store_path.write_text(store, encoding="utf-8")

# Phase 2 MediaViewer contract on the Phase 4 chat-detail structural base.
chat_path = ROOT / "lib/features/chats/chat_detail_screen.dart"
chat = chat_path.read_text(encoding="utf-8")
chat = re.sub(
    r"MediaViewerScreen\(\s*title: attachment\.name,\s*type: attachment\.type,\s*size: attachment\.size,\s*storagePath: attachment\.url,\s*theme: theme,\s*\)",
    "MediaViewerScreen(\n          theme: theme,\n          conversationId: message.conversationId,\n          attachment: attachment,\n        )",
    chat,
    count=1,
)
chat = re.sub(
    r"MediaViewerScreen\(\s*title: message\.attachment!\.name,\s*type: message\.attachment!\.type,\s*size: message\.attachment!\.size,\s*storagePath: message\.attachment!\.url,\s*theme: theme,\s*\)",
    "MediaViewerScreen(\n                        theme: theme,\n                        conversationId: message.conversationId,\n                        attachment: message.attachment!,\n                      )",
    chat,
    count=1,
)
require(chat, "conversationId: message.conversationId", "encrypted MediaViewer wiring")

# ---------------------------------------------------------------------------
# Phase 5: runtime settings consumers and navigation policy.
# ---------------------------------------------------------------------------
phase5 = "phase-5-feature-correctness"
for path in [
    "lib/features/chats/main_navigation_shell.dart",
    "lib/features/chats/root_navigation_policy.dart",
    "lib/features/messages/message_bubble.dart",
    "test/phase5_feature_contracts_test.dart",
    "test/root_navigation_policy_test.dart",
    "tools/audit_gb_runtime_consumers.py",
]:
    copy_from(phase5, path)
production_store_rename("lib/features/chats/main_navigation_shell.dart")

settings_marker = (
    "    final retainViewOnce =\n"
    "        widget.preferencesController.privacy.antiViewOnce ||\n"
    "        widget.preferencesController.gbBool('anti_vw_once');\n"
)
if "final messageTextSize = widget.preferencesController.gbDouble(" not in chat:
    require(chat, settings_marker, "message appearance settings")
    chat = chat.replace(
        settings_marker,
        settings_marker
        + "    final messageTextSize = widget.preferencesController.gbDouble(\n"
        + "      'text_size_pick',\n"
        + "      fallback: 15,\n"
        + "    );\n"
        + "    final outgoingBubbleTextColor = widget.preferencesController.gbColor(\n"
        + "      'ModChatBubbleText',\n"
        + "    );\n"
        + "    final incomingBubbleTextColor = widget.preferencesController.gbColor(\n"
        + "      'ModChatBubbleTextLeft',\n"
        + "    );\n"
        + "    final outgoingTimestampColor = widget.preferencesController.gbColor(\n"
        + "      'date_right_color',\n"
        + "    );\n"
        + "    final incomingTimestampColor = widget.preferencesController.gbColor(\n"
        + "      'date_left_color',\n"
        + "    );\n",
        1,
    )

bubble_marker = "            showDeletedContent: showDeleted,\n"
if "messageTextSize: messageTextSize" not in chat:
    require(chat, bubble_marker, "MessageBubble runtime consumer wiring")
    chat = chat.replace(
        bubble_marker,
        bubble_marker
        + "            messageTextSize: messageTextSize,\n"
        + "            bubbleTextColor: isMine\n"
        + "                ? outgoingBubbleTextColor\n"
        + "                : incomingBubbleTextColor,\n"
        + "            timestampColor: isMine\n"
        + "                ? outgoingTimestampColor\n"
        + "                : incomingTimestampColor,\n",
        1,
    )
chat_path.write_text(chat, encoding="utf-8")

# ---------------------------------------------------------------------------
# Phase 6: shared state views/design-system consistency.
# ---------------------------------------------------------------------------
phase6 = "phase-6-ui-ux-consistency"
for path in [
    "lib/ui/core/design_system/components/state_views.dart",
    "lib/ui/core/design_system/design_system.dart",
    "test/phase6_state_views_test.dart",
]:
    copy_from(phase6, path)

# ---------------------------------------------------------------------------
# Phase 7: account lifecycle / platform security. Keep Phase 8's architecture
# and convert any old facade naming after copying the isolated source changes.
# ---------------------------------------------------------------------------
phase7 = "phase-7-platform-security"
for path in [
    "lib/data/services/push_token_service.dart",
    "lib/features/profile/profile_actions.dart",
    "lib/features/settings/settings_root_screen.dart",
    "supabase/functions/delete-account/index.ts",
]:
    copy_from(phase7, path)
for path in [
    "lib/features/profile/profile_actions.dart",
    "lib/features/settings/settings_root_screen.dart",
]:
    production_store_rename(path)

# Phase 3 server-backed call history is integrated manually in the release
# branch so that Phase 8's no-QA/no-mock call surface is preserved.
# Guard the intended call-history source file and strict production paths.
if not (ROOT / "lib/data/services/call_history_service.dart").exists():
    copy_from("phase-3-calls-transport", "lib/data/services/call_history_service.dart")

# Release graph must contain no old production facade naming.
offenders: list[str] = []
for path in (ROOT / "lib").rglob("*.dart"):
    text = path.read_text(encoding="utf-8")
    if "MockDataStore" in text or "mock_data_store.dart" in text:
        offenders.append(str(path.relative_to(ROOT)))
if offenders:
    raise SystemExit("Production mock facade references remain: " + ", ".join(offenders))

# Critical security invariants must be visible in the final integrated source.
mls = (ROOT / "lib/data/services/mls_e2ee_service.dart").read_text(encoding="utf-8")
publish = mls.find("'publish_mls_membership_update_v1'")
merge = mls.find("mergePendingCommit(groupIdBytes: groupIdBytes)", publish)
if publish < 0 or merge < 0 or merge < publish:
    raise SystemExit("MLS membership commit is not failure-atomic after integration")
for marker in [
    "clearPendingCommit(groupIdBytes: groupIdBytes)",
    "final newEpoch = group.epoch + 1;",
]:
    require(mls, marker, "MLS failure-atomic invariant")

media = (ROOT / "lib/data/services/chat_media_service.dart").read_text(encoding="utf-8")
for marker in [
    "EncryptedAttachmentCodec",
    "application/octet-stream",
    "resolveToLocalFile",
]:
    require(media, marker, "encrypted attachment invariant")

backend = (ROOT / "lib/data/services/backend_service.dart").read_text(encoding="utf-8")
for marker in [
    "EncryptedMessageOutbox",
    "_messagePageSize",
    "loadOlderMessages",
]:
    require(backend, marker, "realtime/offline invariant")

print("Release integration source patch applied and invariants verified.")
