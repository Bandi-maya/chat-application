import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' show Offset, Rect;

import 'package:camera/camera.dart';
import 'package:flutter/services.dart';
import 'package:mediapipe_face_mesh/mediapipe_face_mesh.dart';

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

  FaceMeshIsolatePipeline? _meshPipeline;
  Future<FaceMeshIsolatePipeline>? _meshPipelineCreation;

  /// Uses the open-source 478-landmark MediaPipe pipeline first. If model
  /// loading or native inference fails, it falls back to the platform detector
  /// already wired into the app. Camera frames remain on-device.
  Future<CameraFaceDetectionResult> detect(
    CameraImage image, {
    required int sensorOrientation,
  }) async {
    try {
      final frame = _toFaceMeshFrame(image);
      if (frame != null) {
        final pipeline = await _ensureMeshPipeline();
        if (!pipeline.isBusy && !pipeline.isClosed) {
          final result = await pipeline.processMultiFace(
            frame,
            maxMeshFaces: 3,
            rotationDegrees: sensorOrientation,
          );
          return _fromMeshResult(result);
        }
      }
    } catch (_) {
      // Gracefully fall back to Vision / Android FaceDetector if the optional
      // model can't initialize or this device can't run the native runtime.
    }

    return _detectWithNativeChannel(
      image,
      sensorOrientation: sensorOrientation,
    );
  }

  Future<FaceMeshIsolatePipeline> _ensureMeshPipeline() async {
    final current = _meshPipeline;
    if (current != null && !current.isClosed) return current;

    final pending = _meshPipelineCreation;
    if (pending != null) return pending;

    final creation = FaceMeshIsolatePipeline.spawn(
      _createChatyFaceMeshPipeline,
      FaceMeshModel.v2,
      debugName: 'ChatyFaceLensMesh',
    );
    _meshPipelineCreation = creation;
    try {
      final pipeline = await creation;
      _meshPipeline = pipeline;
      return pipeline;
    } finally {
      if (identical(_meshPipelineCreation, creation)) {
        _meshPipelineCreation = null;
      }
    }
  }

  Future<void> dispose() async {
    final pipeline = _meshPipeline;
    _meshPipeline = null;
    if (pipeline != null) {
      await pipeline.close();
    }
  }

  FaceMeshFrame? _toFaceMeshFrame(CameraImage image) {
    final planes = image.planes;
    if (planes.isEmpty) return null;

    if (image.format.group == ImageFormatGroup.bgra8888) {
      final plane = planes.first;
      return FaceMeshImage(
        pixels: plane.bytes,
        width: image.width,
        height: image.height,
        bytesPerRow: plane.bytesPerRow,
        pixelFormat: FaceMeshPixelFormat.bgra,
      );
    }

    final y = planes.first;
    if (image.format.group == ImageFormatGroup.nv21 && planes.length == 1) {
      return FaceMeshNv21Image.tryFromSinglePlane(
        bytes: y.bytes,
        width: image.width,
        height: image.height,
        bytesPerRow: y.bytesPerRow,
      );
    }

    if (planes.length == 2) {
      return FaceMeshNv21Image.tryFromYAndInterleavedVuPlanes(
        width: image.width,
        height: image.height,
        yPlane: FaceMeshImagePlane(
          bytes: planes[0].bytes,
          bytesPerRow: planes[0].bytesPerRow,
          bytesPerPixel: planes[0].bytesPerPixel,
        ),
        vuPlane: FaceMeshImagePlane(
          bytes: planes[1].bytes,
          bytesPerRow: planes[1].bytesPerRow,
          bytesPerPixel: planes[1].bytesPerPixel,
        ),
      );
    }

    if (planes.length >= 3) {
      return FaceMeshNv21Image.tryFromYuv420Planes(
        width: image.width,
        height: image.height,
        yPlane: FaceMeshImagePlane(
          bytes: planes[0].bytes,
          bytesPerRow: planes[0].bytesPerRow,
          bytesPerPixel: planes[0].bytesPerPixel,
        ),
        uPlane: FaceMeshImagePlane(
          bytes: planes[1].bytes,
          bytesPerRow: planes[1].bytesPerRow,
          bytesPerPixel: planes[1].bytesPerPixel,
        ),
        vPlane: FaceMeshImagePlane(
          bytes: planes[2].bytes,
          bytesPerRow: planes[2].bytesPerRow,
          bytesPerPixel: planes[2].bytesPerPixel,
        ),
      );
    }
    return null;
  }

  CameraFaceDetectionResult _fromMeshResult(
    FaceMeshMultiInferenceResult result,
  ) {
    final faces = <int, RawFace>{};
    var width = 1;
    var height = 1;

    for (final tracked in result.faces) {
      final mesh = tracked.mesh;
      width = mesh.imageWidth;
      height = mesh.imageHeight;
      final points = mesh.landmarks;
      if (points.length < 468) continue;

      Offset point(int index) => Offset(
        points[index].x * mesh.imageWidth,
        points[index].y * mesh.imageHeight,
      );

      final leftEye = point(points.length >= 478 ? 468 : 33);
      final rightEye = point(points.length >= 478 ? 473 : 263);
      final nose = point(2);
      final mouth = point(13);
      final pixelPoints = points
          .map((landmark) => Offset(
                landmark.x * mesh.imageWidth,
                landmark.y * mesh.imageHeight,
              ))
          .toList(growable: false);
      final left = pixelPoints.map((p) => p.dx).reduce((a, b) => a < b ? a : b);
      final top = pixelPoints.map((p) => p.dy).reduce((a, b) => a < b ? a : b);
      final right = pixelPoints.map((p) => p.dx).reduce((a, b) => a > b ? a : b);
      final bottom = pixelPoints.map((p) => p.dy).reduce((a, b) => a > b ? a : b);
      final roll = math.atan2(
        rightEye.dy - leftEye.dy,
        rightEye.dx - leftEye.dx,
      );

      faces[tracked.trackId] = RawFace(
        trackingId: tracked.trackId,
        headBounds: Rect.fromLTRB(left, top, right, bottom),
        leftEye: leftEye,
        rightEye: rightEye,
        noseBase: nose,
        mouthCenter: mouth,
        rollRadians: roll,
        confidence: mesh.score,
      );
    }

    return CameraFaceDetectionResult(
      imageWidth: width,
      imageHeight: height,
      faces: faces,
    );
  }

  Future<CameraFaceDetectionResult> _detectWithNativeChannel(
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
    } on UnsupportedError {
      return CameraFaceDetectionResult(
        imageWidth: image.width,
        imageHeight: image.height,
        faces: const <int, RawFace>{},
      );
    }
  }

  _RgbFrame _toRgbThumbnail(CameraImage image, int sensorOrientation) {
    final rotate = sensorOrientation % 180 != 0;
    final orientedWidth = rotate ? image.height : image.width;
    final orientedHeight = rotate ? image.width : image.height;
    final scale = math.min(1.0, _longSide / math.max(orientedWidth, orientedHeight));
    final width = math.max(2, (orientedWidth * scale).round() & ~1);
    final height = math.max(1, (orientedHeight * scale).round());
    final pixels = Uint8List(width * height * 3);
    final group = image.format.group;

    if (group != ImageFormatGroup.yuv420 &&
        group != ImageFormatGroup.nv21 &&
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
        final rgb = switch (group) {
          ImageFormatGroup.bgra8888 =>
            _readBgra(yPlane, source.$1, source.$2),
          ImageFormatGroup.nv21 =>
            _readNv21(yPlane, image.width, image.height, source.$1, source.$2),
          _ => _readYuv(
              yPlane,
              uvPlane,
              vPlane,
              source.$1,
              source.$2,
            ),
        };
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

  (int, int, int) _readNv21(
    Plane plane,
    int width,
    int height,
    int x,
    int y,
  ) {
    final bytes = plane.bytes;
    final yIndex = y * plane.bytesPerRow + x;
    final uvBase = plane.bytesPerRow * height;
    final uvIndex =
        uvBase + (y >> 1) * plane.bytesPerRow + (x & ~1);
    final luminance = bytes[yIndex.clamp(0, bytes.length - 1).toInt()].toDouble();
    var v = 128.0;
    var u = 128.0;
    if (uvIndex + 1 < bytes.length) {
      v = bytes[uvIndex].toDouble();
      u = bytes[uvIndex + 1].toDouble();
    }
    final red = (luminance + 1.402 * (v - 128)).round().clamp(0, 255).toInt();
    final green =
        (luminance - 0.344136 * (u - 128) - 0.714136 * (v - 128))
            .round()
            .clamp(0, 255)
            .toInt();
    final blue =
        (luminance + 1.772 * (u - 128)).round().clamp(0, 255).toInt();
    return (red, green, blue);
  }

  (int, int, int) _readBgra(Plane plane, int x, int y) {
    final stride = plane.bytesPerPixel ?? 4;
    final index = y * plane.bytesPerRow + x * stride;
    final bytes = plane.bytes;
    return (
      bytes[(index + 2).clamp(0, bytes.length - 1).toInt()],
      bytes[(index + 1).clamp(0, bytes.length - 1).toInt()],
      bytes[index.clamp(0, bytes.length - 1).toInt()],
    );
  }

  (int, int, int) _readYuv(
    Plane yPlane,
    Plane? uvPlane,
    Plane? vPlane,
    int x,
    int y,
  ) {
    final yIndex =
        y * yPlane.bytesPerRow + x * (yPlane.bytesPerPixel ?? 1);
    final luminance = yPlane.bytes[yIndex.clamp(0, yPlane.bytes.length - 1).toInt()].toDouble();
    var u = 128.0;
    var v = 128.0;

    if (uvPlane != null && vPlane != null) {
      final uIndex =
          (y >> 1) * uvPlane.bytesPerRow +
          (x >> 1) * (uvPlane.bytesPerPixel ?? 1);
      final vIndex =
          (y >> 1) * vPlane.bytesPerRow +
          (x >> 1) * (vPlane.bytesPerPixel ?? 1);
      u = uvPlane.bytes[uIndex.clamp(0, uvPlane.bytes.length - 1).toInt()].toDouble();
      v = vPlane.bytes[vIndex.clamp(0, vPlane.bytes.length - 1).toInt()].toDouble();
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

    final red = (luminance + 1.402 * (v - 128)).round().clamp(0, 255).toInt();
    final green =
        (luminance - 0.344136 * (u - 128) - 0.714136 * (v - 128))
            .round()
            .clamp(0, 255)
            .toInt();
    final blue = (luminance + 1.772 * (u - 128)).round().clamp(0, 255).toInt();
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


Future<FaceMeshInferencePipeline> _createChatyFaceMeshPipeline(
  FaceMeshModel model,
) async {
  final detector = await FaceDetectorProcessor.create(
    maxResults: 3,
    delegate: FaceMeshDelegate.xnnpack,
  );
  final mesh = await FaceMeshProcessor.create(
    model: model,
    delegate: FaceMeshDelegate.xnnpack,
  );
  return FaceMeshInferencePipeline(detector: detector, mesh: mesh);
}

