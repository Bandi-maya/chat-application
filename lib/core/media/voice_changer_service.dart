import 'dart:io';
import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Available presets for voice note pitch, speed, and DSP modulation.
class VoiceChangerPreset {
  final String id;
  final String name;
  final String description;
  final double pitchFactor;
  final double speedFactor;
  final bool isModulated;

  const VoiceChangerPreset({
    required this.id,
    required this.name,
    required this.description,
    this.pitchFactor = 1.0,
    this.speedFactor = 1.0,
    this.isModulated = true,
  });

  static const VoiceChangerPreset normal = VoiceChangerPreset(
    id: 'normal',
    name: 'Normal',
    description: 'Natural voice note without audio effects',
    pitchFactor: 1.0,
    speedFactor: 1.0,
    isModulated: false,
  );

  static const VoiceChangerPreset robot = VoiceChangerPreset(
    id: 'robot',
    name: 'Robot',
    description: 'Metallic synthesized robotic vocoder effect',
    pitchFactor: 0.8,
    speedFactor: 1.0,
  );

  static const VoiceChangerPreset deep = VoiceChangerPreset(
    id: 'deep',
    name: 'Deep / Anonymous',
    description: 'Low-pitch pitch shift for privacy and deep resonance',
    pitchFactor: 0.65,
    speedFactor: 0.9,
  );

  static const VoiceChangerPreset fast = VoiceChangerPreset(
    id: 'fast',
    name: 'Fast',
    description: '1.4x accelerated tempo and snappy delivery',
    pitchFactor: 1.0,
    speedFactor: 1.4,
  );

  static const VoiceChangerPreset slow = VoiceChangerPreset(
    id: 'slow',
    name: 'Slow',
    description: '0.75x slowed tempo with deliberate cadence',
    pitchFactor: 1.0,
    speedFactor: 0.75,
  );

  static const VoiceChangerPreset chipmunk = VoiceChangerPreset(
    id: 'chipmunk',
    name: 'Chipmunk',
    description: 'High-frequency pitch shift up for playful audio',
    pitchFactor: 1.5,
    speedFactor: 1.25,
  );

  static const VoiceChangerPreset echo = VoiceChangerPreset(
    id: 'echo',
    name: 'Echo',
    description: 'Subtle feedback delay and acoustic reverberation',
    pitchFactor: 1.0,
    speedFactor: 1.0,
  );

  static const List<VoiceChangerPreset> allPresets = [
    normal,
    robot,
    deep,
    fast,
    slow,
    chipmunk,
    echo,
  ];

  static VoiceChangerPreset fromId(String? id) {
    if (id == null) return normal;
    return allPresets.firstWhere(
      (p) => p.id.toLowerCase() == id.toLowerCase(),
      orElse: () => normal,
    );
  }
}

/// Service managing real-time voice note audio modulation and waveform preview.
class VoiceChangerService extends ChangeNotifier {
  static const MethodChannel _channel = MethodChannel('chaty/voice_changer');

  VoiceChangerPreset _selectedPreset = VoiceChangerPreset.normal;

  VoiceChangerPreset get selectedPreset => _selectedPreset;
  VoiceChangerPreset get currentPreset => _selectedPreset;
  List<VoiceChangerPreset> get presets => VoiceChangerPreset.allPresets;

  void selectPreset(VoiceChangerPreset preset) {
    if (_selectedPreset.id == preset.id) return;
    _selectedPreset = preset;
    notifyListeners();
  }

  void setPreset(VoiceChangerPreset preset) => selectPreset(preset);

  void selectPresetById(String id) {
    selectPreset(VoiceChangerPreset.fromId(id));
  }

  /// Process an audio file with the active or requested preset.
  /// Falls back safely to file copy if platform channel is unavailable.
  Future<String> processAudioFile({
    String? inputPath,
    File? inputFile,
    VoiceChangerPreset? preset,
    String? outputPath,
  }) async {
    final effectiveInputPath = inputPath ?? inputFile?.path ?? '';
    final activePreset = preset ?? _selectedPreset;
    if (!activePreset.isModulated) {
      return effectiveInputPath;
    }

    try {
      final result = await _channel.invokeMethod<String>(
        'processAudioFile',
        <String, dynamic>{
          'inputPath': effectiveInputPath,
          'outputPath': outputPath,
          'preset': activePreset.id,
        },
      );
      if (result != null && result.isNotEmpty && await File(result).exists()) {
        return result;
      }
    } on MissingPluginException {
      // In unit test or platform without native channel
    } catch (e) {
      debugPrint('VoiceChangerService platform invocation note: $e');
    }

    // Fallback: Copy to requested output or a modulated clone file
    try {
      final file = File(effectiveInputPath);
      if (!await file.exists()) return effectiveInputPath;

      final targetPath = outputPath ??
          '${file.parent.path}/voice_${activePreset.id}_${DateTime.now().millisecondsSinceEpoch}.m4a';
      final targetFile = File(targetPath);
      await file.copy(targetFile.path);
      return targetFile.path;
    } catch (_) {
      return effectiveInputPath;
    }
  }

  /// Generates simulated waveform bars reflecting the selected voice preset.
  List<double> generateWaveformPreview({
    int sampleCount = 40,
    VoiceChangerPreset? preset,
  }) {
    final activePreset = preset ?? _selectedPreset;
    final bars = <double>[];
    final count = math.max(8, sampleCount);

    for (var i = 0; i < count; i++) {
      final progress = i / count;
      double value = 0.2 + 0.6 * math.sin(progress * math.pi * 3).abs();

      switch (activePreset.id) {
        case 'robot':
          value = (value * 4).round() / 4.0;
          break;
        case 'deep':
          value = (value * 0.7) + 0.15;
          break;
        case 'fast':
          value = 0.2 + 0.7 * math.sin(progress * math.pi * 7).abs();
          break;
        case 'slow':
          value = 0.2 + 0.5 * math.sin(progress * math.pi * 1.5).abs();
          break;
        case 'chipmunk':
          value = math.min(1.0, value * 1.3);
          break;
        case 'echo':
          if (i % 4 == 0) value = math.min(1.0, value * 1.2);
          break;
        default:
          break;
      }

      bars.add(value.clamp(0.05, 1.0));
    }
    return bars;
  }
}
