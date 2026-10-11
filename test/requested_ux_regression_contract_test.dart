import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Calls uses one Add call action and a custom numeric dialpad', () {
    final calls = File('lib/features/calls/calls_screen.dart').readAsStringSync();
    final picker = File(
      'lib/features/calls/call_contact_picker_screen.dart',
    ).readAsStringSync();

    expect(calls, contains("label: 'Add call'"));
    expect(calls, contains('CallContactPickerMode.dialpad'));
    expect(calls, isNot(contains("tooltip: 'Open dialpad'")));
    expect(picker, contains('Widget _buildDialpad()'));
    expect(picker, contains('TextInputType.none'));
    expect(picker, contains('readOnly: _isDialpad'));
    expect(picker, contains('matches.length != 1'));
    expect(picker, contains('Enter a registered Chaty number'));
  });

  test('Mark All as Read awaits a real bulk data operation', () {
    final home = File('lib/features/chats/chats_home_screen.dart').readAsStringSync();
    final store = File('lib/data/repositories/chaty_data_store.dart').readAsStringSync();
    final backend = File('lib/data/services/backend_service.dart').readAsStringSync();

    expect(home, contains('await widget.dataStore.markAllAsRead()'));
    expect(home, contains('Marked all chats as read'));
    expect(store, contains('Future<void> markAllAsRead()'));
    expect(backend, contains('Future<void> markAllAsRead()'));
    expect(backend, contains("'mark_conversation_read'"));
    expect(backend, contains('_shouldSendReadReceipts'));
    expect(backend, contains('unreadCount: 0'));
  });

  test('changing lock type verifies once before selecting and replacing', () {
    final security = File(
      'lib/features/settings/security/security_center_screen.dart',
    ).readAsStringSync();
    final setup = File(
      'lib/features/settings/security/lock_credential_setup_modal.dart',
    ).readAsStringSync();
    final pattern = File(
      'lib/features/settings/security/pattern_lock_pad.dart',
    ).readAsStringSync();

    expect(security, contains('final requiresCurrentProof'));
    expect(security, contains('final picked = await LockMethodSelectorSheet.show'));
    expect(security, contains('replaceExisting: true'));
    expect(security, contains('latest.copyWith(isAppLockEnabled: false)'));
    expect(setup, contains('this.replaceExisting = false'));
    expect(setup, contains('_hasExistingSecret = hasCred && !widget.replaceExisting'));
    expect(pattern, contains('One crisp trace stroke'));
    expect(pattern, contains('canvas.drawPath(path, linePaint)'));
    expect(pattern, isNot(contains('glowPaint')));
  });

  test('emoji composer supports multi-pick, keyboard return and one-message send', () {
    final picker = File(
      'lib/core/emoji/widgets/chaty_emoji_picker.dart',
    ).readAsStringSync();
    final chat = File('lib/features/chats/chat_detail_screen.dart').readAsStringSync();
    final inline = File(
      'lib/core/emoji/widgets/animated_emoji_text.dart',
    ).readAsStringSync();
    final view = File(
      'lib/core/emoji/widgets/animated_emoji_view.dart',
    ).readAsStringSync();

    expect(picker, contains('Future<ChatyEmojiComposerResult?> showComposer'));
    expect(picker, contains('_insertSelectedEmoji(emoji.emoji)'));
    expect(picker, contains('ChatyEmojiComposerAction.keyboard'));
    expect(picker, contains('ChatyEmojiComposerAction.send'));
    expect(picker, contains('isDismissible: false'));
    expect(chat, contains('ChatyEmojiPicker.showComposer'));
    expect(chat, contains('await _sendMessage()'));
    expect(inline, contains('interactive: true'));
    expect(view, contains('repeat: widget.mode == EmojiDisplayMode.inline'));
    expect(view, contains('RepaintBoundary(child: body)'));
  });

  test('lenses use an open-source on-device mesh and preserve native fallback', () {
    final bridge = File(
      'lib/features/camera/lens/camera_face_detection_bridge.dart',
    ).readAsStringSync();
    final pubspec = File('pubspec.yaml').readAsStringSync();
    final docs = File(
      'docs/implementation/CHATY_CAMERA_LENSES_AND_DOCUMENT_MODE.md',
    ).readAsStringSync();

    expect(pubspec, contains('mediapipe_face_mesh: ^3.2.1'));
    expect(bridge, contains('FaceMeshIsolatePipeline.spawn'));
    expect(bridge, contains('FaceMeshModel.v2'));
    expect(bridge, contains('processMultiFace'));
    expect(bridge, contains('_detectWithNativeChannel'));
    expect(bridge, contains('point(33)'));
    expect(bridge, contains('point(263)'));
    expect(docs, contains('BSD-3-Clause'));
    expect(docs, contains('Apache-2.0'));
  });
}
