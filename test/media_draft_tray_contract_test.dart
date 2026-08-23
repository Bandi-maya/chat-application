import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('media draft tray keeps review/remove/send states explicit', () {
    final source = File(
      'lib/ui/core/design_system/components/media_draft_tray.dart',
    ).readAsStringSync();

    expect(source, contains('class ChatyMediaDraftTray'));
    expect(source, contains('onRemove'));
    expect(source, contains('onAddMore'));
    expect(source, contains('captionController'));
    expect(source, contains('Encrypted before upload'));
    expect(source, contains("label: Text(sending ? 'Sending' : 'Send')"));
  });
}
