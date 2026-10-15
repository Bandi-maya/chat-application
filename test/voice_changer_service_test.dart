import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/core/media/voice_changer_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('VoiceChangerPreset & VoiceChangerService Tests', () {
    test('VoiceChangerPreset catalog contains all 7 reference presets', () {
      final presets = VoiceChangerPreset.allPresets;
      expect(presets.length, equals(7));

      final ids = presets.map((p) => p.id).toSet();
      expect(ids, containsAll(['normal', 'robot', 'deep', 'fast', 'slow', 'chipmunk', 'echo']));
    });

    test('Preset lookup by id maps correctly and safely falls back to normal', () {
      expect(VoiceChangerPreset.fromId('robot'), equals(VoiceChangerPreset.robot));
      expect(VoiceChangerPreset.fromId('DEEP'), equals(VoiceChangerPreset.deep));
      expect(VoiceChangerPreset.fromId('chipmunk'), equals(VoiceChangerPreset.chipmunk));
      expect(VoiceChangerPreset.fromId('unknown_preset_id'), equals(VoiceChangerPreset.normal));
      expect(VoiceChangerPreset.fromId(null), equals(VoiceChangerPreset.normal));
    });

    test('VoiceChangerService preset switching notifies listeners', () {
      final service = VoiceChangerService();
      expect(service.currentPreset, equals(VoiceChangerPreset.normal));

      int notifyCount = 0;
      service.addListener(() {
        notifyCount++;
      });

      service.setPreset(VoiceChangerPreset.robot);
      expect(service.currentPreset, equals(VoiceChangerPreset.robot));
      expect(notifyCount, equals(1));

      // Same preset does not fire duplicate notification
      service.setPreset(VoiceChangerPreset.robot);
      expect(notifyCount, equals(1));

      service.dispose();
    });

    test('generateWaveformPreview produces bounded values in range [0.0, 1.0]', () {
      final service = VoiceChangerService();

      for (final preset in VoiceChangerPreset.allPresets) {
        final waveform = service.generateWaveformPreview(preset: preset, sampleCount: 40);
        expect(waveform.length, equals(40));

        for (final sample in waveform) {
          expect(sample, greaterThanOrEqualTo(0.0));
          expect(sample, lessThanOrEqualTo(1.0));
        }
      }

      service.dispose();
    });

    test('Audio file processing with normal preset returns input file immediately', () async {
      final service = VoiceChangerService();
      final tempDir = Directory.systemTemp.createTempSync('voice_changer_test');
      final inputFile = File('${tempDir.path}/test_voice.m4a');
      await inputFile.writeAsString('mock audio data');

      final result = await service.processAudioFile(
        inputFile: inputFile,
        preset: VoiceChangerPreset.normal,
      );

      expect(result, equals(inputFile.path));

      // Clean up
      tempDir.deleteSync(recursive: true);
      service.dispose();
    });
  });
}
