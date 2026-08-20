import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:path_provider/path_provider.dart';

class CustomAppIconProcessor {
  const CustomAppIconProcessor._();

  static const int outputSize = 512;
  static const double adaptiveSafeZoneFraction = 0.82;

  static Future<File> persistSquarePng(
    Uint8List sourcePng, {
    String? presetId,
  }) async {
    if (sourcePng.isEmpty) {
      throw ArgumentError.value(
        sourcePng,
        'sourcePng',
        'Custom icon data is empty.',
      );
    }

    final codec = await ui.instantiateImageCodec(sourcePng);
    ui.Image? sourceImage;
    ui.Image? launcherImage;
    try {
      final frame = await codec.getNextFrame();
      sourceImage = frame.image;

      final recorder = ui.PictureRecorder();
      final canvas = ui.Canvas(recorder);
      final paint = ui.Paint()
        ..isAntiAlias = true
        ..filterQuality = ui.FilterQuality.high;

      final sourceWidth = sourceImage.width.toDouble();
      final sourceHeight = sourceImage.height.toDouble();
      final squareSide = sourceWidth < sourceHeight ? sourceWidth : sourceHeight;
      final sourceRect = ui.Rect.fromLTWH(
        (sourceWidth - squareSide) / 2,
        (sourceHeight - squareSide) / 2,
        squareSide,
        squareSide,
      );

      final safeSize = outputSize * adaptiveSafeZoneFraction;
      final safeOffset = (outputSize - safeSize) / 2;
      final destinationRect = ui.Rect.fromLTWH(
        safeOffset,
        safeOffset,
        safeSize,
        safeSize,
      );

      canvas.drawColor(const ui.Color(0x00000000), ui.BlendMode.src);
      canvas.drawImageRect(sourceImage, sourceRect, destinationRect, paint);

      final picture = recorder.endRecording();
      launcherImage = await picture.toImage(outputSize, outputSize);
      picture.dispose();

      final byteData = await launcherImage.toByteData(format: ui.ImageByteFormat.png);
      final normalized = byteData?.buffer.asUint8List();
      if (normalized == null || normalized.isEmpty) {
        throw StateError('Unable to encode the processed adaptive launcher icon.');
      }

      final root = await getApplicationSupportDirectory();
      final directory = Directory(
        '${root.path}${Platform.pathSeparator}branding${Platform.pathSeparator}custom_icons',
      );
      if (!await directory.exists()) {
        await directory.create(recursive: true);
      }

      final safeId = (presetId ?? 'custom_${DateTime.now().microsecondsSinceEpoch}')
          .replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_');
      final target = File(
        '${directory.path}${Platform.pathSeparator}$safeId.launcher.png',
      );
      final temporary = File('${target.path}.tmp');

      await temporary.writeAsBytes(normalized, flush: true);
      if (await target.exists()) {
        await target.delete();
      }
      return await temporary.rename(target.path);
    } finally {
      launcherImage?.dispose();
      sourceImage?.dispose();
      codec.dispose();
    }
  }
}
