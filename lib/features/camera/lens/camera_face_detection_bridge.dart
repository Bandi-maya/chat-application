import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' show Offset, Rect;

import 'package:camera/camera.dart';
import 'package:flutter/services.dart';

import 'face_tracker.dart';

/// Low-bandwidth bridge to on-device platform face detection.
///
/// Only a tiny RGB thumbnail is sent to the native detector. Full camera
/// frames never leave the device and no network service is involved. Detection
/// is called only while a face-anchored lens is selected.
class CameraFaceDetectionBridge {
  static const MethodChannel _channel =
      MethodChannel('chaty/camera_face_tracking');
  static const int _longSide = 160;

  Future<CameraFaceDetectionResult> detect(
    CameraImage image, {
    required int sensorOrientation,
  }) async {
    final frame = _toRgbThumbnail(image, sensorOrientation);
    try {
      final result = await _channel.invokeListMethod<Object?>(
        'detectFaces',
        <String, Object>{
          'width': frame.width,
          'height': frame.height,
          'rgbBytes': frame.bytes,
        },
      );
      final faces = <int, RawFace>{};
      for (final row in result ?? const <Object?>[]) {
        if (row is! Map) continue;
        final data = Map<Object?, Object?>.from(row);
        num number(String key, [num fallback = 0]) =>
            data[key] is num ? data[key]! as num : fallback;
        final id = number('trackingId', faces.length + 1).toInt();
        final left = number('headLeft').toDouble();
        final top = number('headTop').toDouble();
        final right = number('headRight').toDouble();
        final bottom = number('headBottom').toDouble();
        faces[id] = RawFace(
          trackingId: id,
          headBounds: Rect.fromLTRB(left, top, right, bottom),
          leftEye: Offset(
            number('leftEyeX').toDouble(),
            number('leftEyeY').toDouble(),
          ),
          rightEye: Offset(
            number('rightEyeX').toDouble(),
            number('rightEyeY').toDouble(),
          ),
          noseBase: Offset(
            number('noseX').toDouble(),
            number('noseY').toDouble(),
          ),
          mouthCenter: Offset(
            number('mouthX').toDouble(),
            number('mouthY').toDouble(),
          ),
          rollRadians: number('rollRadians').toDouble(),
          yawRadians: number('yawRadians').toDouble(),
          pitchRadians: number('pitchRadians').toDouble(),
          confidence: number('confidence', 0.5).toDouble(),
        );
      }
      return CameraFaceDetectionResult(
        imageWidth: frame.width,
        imageHeight: frame.height,
        faces: faces,
      );
    } on MissingPluginException {
      // Desktop/test targets can still use the camera's normal capture UI.
      return CameraFaceDetectionResult(
        imageWidth: frame.width,
        imageHeight: frame.height,
        faces: const <int, RawFace>{},
      );
    } on PlatformException {
      return CameraFaceDetectionResult(
        imageWidth: frame.width,
        imageHeight: frame.height,
        faces: const <int, RawFace>{},
      );
    }
  }

  _RgbFrame _toRgbThumbnail(CameraImage image, int sensorOrientation) {
    final rotate = sensorOrientation % 180 != 0;
    final orientedWidth = rotate ? image.height : image.width;
    final orientedHeight = rotate ? image.width : image.height;
    final scale = math.min(1.0, _longSide / math.max(orientedWidth, orientedHeight));
    final width = math.max(1, (orientedWidth * scale).round());
    final height = math.max(1, (orientedHeight * scale).round());
    final pixels = Uint8List(width * height * 3);
    final group = image.format.group;

    if (group != ImageFormatGroup.yuv420 &&
        group != ImageFormatGroup.bgra8888) {
      throw UnsupportedError('Unsupported camera frame format: $group');
    }
    if (image.planes.isEmpty) {
      throw StateError('Camera frame has no image planes.');
    }

    final yPlane = image.planes.first;
    final uvPlane = image.planes.length > 1 ? image.planes[1] : null;
    final vPlane = image.planes.length > 2 ? image.planes[2] : null;

    for (var y = 0; y < height; y++) {
      final orientedY = math.min(
        orientedHeight - 1,
        ((y + 0.5) * orientedHeight / height).floor(),
      );
      for (var x = 0; x < width; x++) {
        final orientedX = math.min(
          orientedWidth - 1,
          ((x + 0.5) * orientedWidth / width).floor(),
        );
        final source = _unrotate(
          orientedX,
          orientedY,
          image.width,
          image.height,
          sensorOrientation,
        );
        final rgb = group == ImageFormatGroup.bgra8888
            ? _readBgra(yPlane, source.$1, source.$2)
            : _readYuv(
                yPlane,
                uvPlane,
                vPlane,
                source.$1,
                source.$2,
              );
        final index = (y * width + x) * 3;
        pixels[index] = rgb.$1;
        pixels[index + 1] = rgb.$2;
        pixels[index + 2] = rgb.$3;
      }
    }

    return _RgbFrame(width: width, height: height, bytes: pixels);
  }

  (int, int) _unrotate(
    int x,
    int y,
    int width,
    int height,
    int sensorOrientation,
  ) {
    switch ((sensorOrientation % 360 + 360) % 360) {
      case 90:
        return (y.clamp(0, width - 1).toInt(), (height - 1 - x).clamp(0, height - 1).toInt());
      case 180:
        return (
          (width - 1 - x).clamp(0, width - 1).toInt(),
          (height - 1 - y).clamp(0, height - 1).toInt(),
        );
      case 270:
        return ((width - 1 - y).clamp(0, width - 1).toInt(), x.clamp(0, height - 1).toInt());
      default:
        return (x.clamp(0, width - 1).toInt(), y.clamp(0, height - 1).toInt());
    }
  }

  (int, int, int) _readBgra(Plane plane, int x, int y) {
    final stride = plane.bytesPerPixel ?? 4;
    final index = y * plane.bytesPerRow + x * stride;
    final bytes = plane.bytes;
    return (
      bytes[(index + 2).clamp(0, bytes.length - 1)],
      bytes[(index + 1).clamp(0, bytes.length - 1)],
      bytes[index.clamp(0, bytes.length - 1)],
    );
  }

  (int, int, int) _readYuv(
    CameraImage image,
    CameraImagePlane yPlane,
    CameraImagePlane? uvPlane,
    CameraImagePlane? vPlane,
    int x,
    int y,
  ) {
    final yIndex =
        y * yPlane.bytesPerRow + x * (yPlane.bytesPerPixel ?? 1);
    final luminance = yPlane.bytes[yIndex].toDouble();
    var u = 128.0;
    var v = 128.0;

    if (uvPlane != null && vPlane != null) {
      final uIndex =
          (y >> 1) * uvPlane.bytesPerRow +
          (x >> 1) * (uvPlane.bytesPerPixel ?? 1);
      final vIndex =
          (y >> 1) * vPlane.bytesPerRow +
          (x >> 1) * (vPlane.bytesPerPixel ?? 1);
      u = uvPlane.bytes[uIndex].toDouble();
      v = vPlane.bytes[vIndex].toDouble();
    } else if (uvPlane != null) {
      final uvIndex =
          (y >> 1) * uvPlane.bytesPerRow +
          (x >> 1) * (uvPlane.bytesPerPixel ?? 2);
      if (uvIndex + 1 < uvPlane.bytes.length) {
        // Two-plane camera buffers use interleaved U/V (NV12) samples.
        u = uvPlane.bytes[uvIndex].toDouble();
        v = uvPlane.bytes[uvIndex + 1].toDouble();
      }
    }

    final red = (luminance + 1.402 * (v - 128)).round().clamp(0, 255);
    final green =
        (luminance - 0.344136 * (u - 128) - 0.714136 * (v - 128))
            .round()
            .clamp(0, 255);
    final blue = (luminance + 1.772 * (u - 128)).round().clamp(0, 255);
    return (red, green, blue);
  }
}

class CameraFaceDetectionResult {
  const CameraFaceDetectionResult({
    required this.imageWidth,
    required this.imageHeight,
    required this.faces,
  });

  final int imageWidth;
  final int imageHeight;
  final Map<int, RawFace> faces;
}

class _RgbFrame {
  const _RgbFrame({
    required this.width,
    required this.height,
    required this.bytes,
  });

  final int width;
  final int height;
  final Uint8List bytes;
}
