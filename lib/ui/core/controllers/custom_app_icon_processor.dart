import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:path_provider/path_provider.dart';

class CustomAppIconProcessor {
  const CustomAppIconProcessor._();

  static const int outputSize = 512;

  static Future<File> persistSquarePng(Uint8List sourcePng) async {
    if (sourcePng.isEmpty) {
      throw ArgumentError.value(sourcePng, 'sourcePng', 'Custom icon data is empty.');
    }

    final codec = await ui.instantiateImageCodec(
      sourcePng,
      targetWidth: outputSize,
      targetHeight: outputSize,
      allowUpscaling: true,
    );
    try {
      final frame = await codec.getNextFrame();
      final byteData = await frame.image.toByteData(format: ui.ImageByteFormat.png);
      frame.image.dispose();
      final normalized = byteData?.buffer.asUint8List();
      if (normalized == null || normalized.isEmpty) {
        throw StateError('Unable to encode the processed custom app icon.');
      }

      final root = await getApplicationSupportDirectory();
      final directory = Directory('${root.path}${Platform.pathSeparator}branding');
      if (!await directory.exists()) {
        await directory.create(recursive: true);
      }

      final target = File('${directory.path}${Platform.pathSeparator}custom_brand_icon.png');
      final temporary = File('${target.path}.tmp');
      await temporary.writeAsBytes(normalized, flush: true);

      if (await target.exists()) {
        await target.delete();
      }
      return temporary.rename(target.path);
    } finally {
      codec.dispose();
    }
  }
}
