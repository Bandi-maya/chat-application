import 'package:chat/features/settings/calls/call_presentation_preferences.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{});
  });

  test('uses safe defaults on a new installation', () async {
    final store = CallPresentationPreferencesStore();

    await store.initialize();

    expect(store.value.dynamicIslandEnabled, isTrue);
    expect(store.value.pictureInPictureEnabled, isTrue);
    expect(store.value.lowDataUsageEnabled, isFalse);
  });

  test('persists each call presentation choice', () async {
    final store = CallPresentationPreferencesStore();
    await store.initialize();

    await store.setDynamicIslandEnabled(false);
    await store.setPictureInPictureEnabled(false);
    await store.setLowDataUsageEnabled(true);

    final restored = CallPresentationPreferencesStore();
    await restored.initialize();

    expect(restored.value.dynamicIslandEnabled, isFalse);
    expect(restored.value.pictureInPictureEnabled, isFalse);
    expect(restored.value.lowDataUsageEnabled, isTrue);
  });

  test('notifies listeners only when a value changes', () async {
    final store = CallPresentationPreferencesStore();
    await store.initialize();
    var notifications = 0;
    store.addListener(() => notifications++);

    await store.setDynamicIslandEnabled(true);
    expect(notifications, 0);

    await store.setDynamicIslandEnabled(false);
    expect(notifications, 1);
  });
}
