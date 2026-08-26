import 'package:chat/data/services/native_call_pip_service.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('chaty/call_pip');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() {
    debugDefaultTargetPlatformOverride = null;
    messenger.setMockMethodCallHandler(channel, null);
  });

  test(
    'reports unsupported without invoking native code off Android',
    () async {
      var invoked = false;
      messenger.setMockMethodCallHandler(channel, (call) async {
        invoked = true;
        return true;
      });
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;

      expect(await NativeCallPipService().isSupported, isFalse);
      expect(invoked, isFalse);
    },
  );

  test('enters Android PiP with a safe video aspect ratio', () async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return true;
    });
    debugDefaultTargetPlatformOverride = TargetPlatform.android;

    expect(await NativeCallPipService().enter(), isTrue);
    expect(calls.map((call) => call.method), <String>['isSupported', 'enter']);
    expect(calls.last.arguments, <String, int>{'width': 9, 'height': 16});
  });
}
