import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Android 12 uses the manifest launch theme with a visible icon', () {
    final styles = File(
      'android/app/src/main/res/values-v31/styles.xml',
    ).readAsStringSync();

    expect(styles, contains('<style name="LaunchTheme"'));
    expect(styles, isNot(contains('LaunchThemeCustom')));
    expect(styles, isNot(contains('transparent_splash_icon')));
    expect(styles, contains('windowSplashScreenAnimatedIcon'));
  });

  test('default launch resources use the application launcher icon', () {
    final manifest = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    final background = File(
      'android/app/src/main/res/drawable/launch_background.xml',
    ).readAsStringSync();
    final styles = File(
      'android/app/src/main/res/values-v31/styles.xml',
    ).readAsStringSync();

    expect(manifest, contains('android:icon="@mipmap/ic_launcher"'));
    expect(background, contains('@mipmap/ic_launcher'));
    expect(
      styles,
      contains(
        '<item name="android:windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>',
      ),
    );
  });

  test('every launcher alias uses its matching launch theme', () {
    final manifest = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();

    for (final theme in <String>[
      'LaunchThemeWarm',
      'LaunchThemeOutline',
      'LaunchThemeObsidian',
      'LaunchThemeGlass',
      'LaunchThemeSignal',
      'LaunchThemeFold',
    ]) {
      expect(manifest, contains('android:theme="@style/$theme"'));
    }
  });

  test('Android 12 night mode defines the same visible splash themes', () {
    final styles = File(
      'android/app/src/main/res/values-night-v31/styles.xml',
    ).readAsStringSync();

    expect(styles, isNot(contains('transparent_splash_icon')));
    for (final theme in <String>[
      'LaunchTheme',
      'LaunchThemeWarm',
      'LaunchThemeOutline',
      'LaunchThemeObsidian',
      'LaunchThemeGlass',
      'LaunchThemeSignal',
      'LaunchThemeFold',
    ]) {
      expect(styles, contains('<style name="$theme"'));
    }
  });
}
