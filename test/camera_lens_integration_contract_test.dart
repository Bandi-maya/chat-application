import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('camera entry screen wires live face lenses to capture', () {
    final source = File(
      'lib/features/camera/camera_capture_screen.dart',
    ).readAsStringSync();

    expect(source, contains('startImageStream'));
    expect(source, contains('CameraFaceDetectionBridge'));
    expect(source, contains('LensCarousel(manager: _lensManager)'));
    expect(source, contains('LensOverlay('));
    expect(source, contains('composeLensIntoImage('));
    expect(source, contains('ImageFormatGroup.yuv420'));
  });

  test('Photo and Document modes update preview and capture output', () {
    final source = File(
      'lib/features/camera/camera_capture_screen.dart',
    ).readAsStringSync();

    expect(source, contains('_CaptureViewMode.photo'));
    expect(source, contains('_CaptureViewMode.document'));
    expect(source, contains('_DocumentGuidePainter'));
    expect(source, contains('bakeEffectIntoFile('));
    expect(source, contains('Find a face'));
    expect(source, contains('Fit the page inside the frame'));
  });

  test('native face detection is on-device on iOS and Android', () {
    final bridge = File(
      'lib/features/camera/lens/camera_face_detection_bridge.dart',
    ).readAsStringSync();
    final ios = File('ios/Runner/AppDelegate.swift').readAsStringSync();
    final android = File(
      'android/app/src/main/kotlin/com/example/chat/MainActivity.kt',
    ).readAsStringSync();

    expect(bridge, contains('chaty/camera_face_tracking'));
    expect(bridge, contains('_longSide = 160'));
    expect(ios, contains('VNDetectFaceLandmarksRequest'));
    expect(android, contains('detectFacesOnDevice'));
    expect(android, contains('android.media.FaceDetector'));
  });

  test('camera feature adds no external Flutter package', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    expect(pubspec, contains('camera:'));
    expect(pubspec, isNot(contains('google_mlkit_face_detection:')));
    expect(pubspec, isNot(contains('google_mlkit_document_scanner:')));
  });
}
