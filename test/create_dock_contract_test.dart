import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('create dock groups actions by user intent', () {
    final source = File(
      'lib/features/messages/attachment_sheet.dart',
    ).readAsStringSync();

    for (final section in <String>[
      'Media',
      'Files',
      'People & places',
      'Create',
    ]) {
      expect(source, contains("_OrbitGroup('$section'"));
    }
    for (final action in <String>[
      'Gallery',
      'Video',
      'Document',
      'Audio',
      'Location',
      'Contact',
      'Poll',
      'Task',
    ]) {
      expect(source, contains("'$action'"));
    }
    expect(
      source,
      contains('Attachments are encrypted on this device before upload.'),
    );
    expect(source, contains('Semantics('));
  });
}
