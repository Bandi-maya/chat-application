import 'dart:async';
import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

import '../../domain/models/chat_message.dart';
import '../repositories/chaty_data_store.dart';
import 'chat_media_service.dart';

class VoiceNoteService {
  VoiceNoteService({required this.conversationId, required this.dataStore});

  final String conversationId;
  final ChatyDataStore dataStore;
  final AudioRecorder _recorder = AudioRecorder();
  final ChatMediaService _media = ChatMediaService();
  DateTime? _startedAt;
  String? _path;
  bool _recording = false;
  bool _busy = false;
  bool _disposed = false;
  Completer<void>? _busyCompleter;
  Future<void>? _disposeFuture;

  Completer<void> _enterBusy() {
    _busy = true;
    final completer = Completer<void>();
    _busyCompleter = completer;
    return completer;
  }

  void _leaveBusy(Completer<void> operation) {
    _busy = false;
    if (identical(_busyCompleter, operation)) {
      _busyCompleter = null;
      operation.complete();
    }
  }

  bool get isRecording => _recording;
  Duration get elapsed => _startedAt == null
      ? Duration.zero
      : DateTime.now().difference(_startedAt!);

  /// Live microphone input level in dBFS (roughly -60…0) for the recording
  /// UI's level meter. Strictly read-only: it never touches the capture
  /// pipeline. Returns the floor when idle or if the probe fails, so the UI
  /// can always render something stable.
  Future<double> currentLevel() async {
    if (!_recording) return -60;
    try {
      final amplitude = await _recorder.getAmplitude();
      return amplitude.current;
    } catch (_) {
      return -60;
    }
  }

  Future<void> start() async {
    if (_disposed) throw StateError('Voice-note service has been disposed.');
    if (_recording || _busy) return;
    final operation = _enterBusy();
    String? stagingPath;
    try {
      if (!await _recorder.hasPermission()) {
        throw Exception('Microphone permission is required.');
      }
      final directory = await getTemporaryDirectory();
      stagingPath =
          '${directory.path}/chaty_voice_${DateTime.now().millisecondsSinceEpoch}.m4a';
      await _recorder.start(
        const RecordConfig(
          encoder: AudioEncoder.aacLc,
          bitRate: 96000,
          sampleRate: 44100,
          numChannels: 1,
          autoGain: true,
          echoCancel: true,
          noiseSuppress: true,
        ),
        path: stagingPath,
      );
      _path = stagingPath;
      _startedAt = DateTime.now();
      _recording = true;
    } catch (_) {
      if (stagingPath != null) await _deleteQuietly(stagingPath);
      rethrow;
    } finally {
      _leaveBusy(operation);
    }
  }

  Future<bool> stopAndSend() async {
    if (_disposed || !_recording || _busy) return false;
    final operation = _enterBusy();
    final started = _startedAt;
    final stagedPath = _path;
    String? recordedPath;
    try {
      recordedPath = await _recorder.stop() ?? stagedPath;
      if (recordedPath == null || recordedPath.isEmpty || started == null) {
        return false;
      }
      final seconds = DateTime.now().difference(started).inSeconds;
      if (seconds < 1) return false;

      final attachment = await _media.uploadFile(
        conversationId: conversationId,
        type: 'audio',
        sourcePath: recordedPath,
        displayName: 'voice_note_${DateTime.now().millisecondsSinceEpoch}.m4a',
        durationSeconds: seconds,
      );
      await dataStore.sendMessage(
        conversationId: conversationId,
        text: '',
        type: MessageType.audio,
        attachment: attachment,
      );
      return true;
    } finally {
      _recording = false;
      _startedAt = null;
      _path = null;
      final cleanupPath = recordedPath ?? stagedPath;
      if (cleanupPath != null) await _deleteQuietly(cleanupPath);
      _leaveBusy(operation);
    }
  }

  Future<void> cancel() async {
    if (_busy) return;
    if (!_recording && _path == null) return;
    final operation = _enterBusy();
    var path = _path;
    try {
      if (_recording) path = await _recorder.stop() ?? path;
    } finally {
      _recording = false;
      _startedAt = null;
      _path = null;
      if (path != null) await _deleteQuietly(path);
      _leaveBusy(operation);
    }
  }

  Future<void> dispose() => _disposeFuture ??= _dispose();

  Future<void> _dispose() async {
    _disposed = true;
    // Wait for native start/stop to finish before disposing the recorder.
    final pendingOperation = _busyCompleter;
    if (pendingOperation != null) await pendingOperation.future;
    if (_recording || _path != null) await cancel();
    await _recorder.dispose();
  }

  static Future<void> _deleteQuietly(String path) async {
    try {
      final file = File(path);
      if (await file.exists()) await file.delete();
    } catch (_) {}
  }
}
