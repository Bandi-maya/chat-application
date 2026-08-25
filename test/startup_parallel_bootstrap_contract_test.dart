import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('independent local appearance bootstrap runs concurrently', () {
    final source = File('lib/main.dart').readAsStringSync();
    final mainBody = source.substring(
      source.indexOf('Future<void> main()'),
      source.indexOf('class ChatyApp'),
    );

    expect(mainBody, contains('Future.wait<void>'));
    expect(mainBody, contains('locator<ThemeController>().init()'));
    expect(mainBody, contains('locator<TemplateController>().init('));
    expect(
      mainBody.indexOf('Future.wait<void>'),
      lessThan(mainBody.indexOf('runApp')),
    );
  });
}
