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
    expect(source, contains('ImageFormatGroup.bgra8888'));
    expect(source, contains('_faceDetection.dispose()'));
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

  test('open-source MediaPipe face mesh is primary with native fallbacks', () {
    final bridge = File(
      'lib/features/camera/lens/camera_face_detection_bridge.dart',
    ).readAsStringSync();
    final ios = File('ios/Runner/AppDelegate.swift').readAsStringSync();
    final android = File(
      'android/app/src/main/kotlin/com/example/chat/MainActivity.kt',
    ).readAsStringSync();

    expect(bridge, contains('FaceMeshIsolatePipeline.spawn'));
    expect(bridge, contains('FaceMeshModel.v2'));
    expect(bridge, contains('processMultiFace'));
    expect(bridge, contains('point(33)'));
    expect(bridge, contains('point(263)'));
    expect(bridge, contains('chaty/camera_face_tracking'));
    expect(bridge, contains('_longSide = 160'));
    expect(ios, contains('VNDetectFaceLandmarksRequest'));
    expect(android, contains('detectFacesOnDevice'));
    expect(android, contains('android.media.FaceDetector'));
  });

  test('camera uses the vetted open-source face mesh instead of paid AR SDKs', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    expect(pubspec, contains('camera:'));
    expect(pubspec, contains('mediapipe_face_mesh: ^3.2.1'));
    expect(pubspec, isNot(contains('google_mlkit_face_detection:')));
    expect(pubspec, isNot(contains('google_mlkit_document_scanner:')));
    expect(pubspec, isNot(contains('deepar:')));
  });
}
