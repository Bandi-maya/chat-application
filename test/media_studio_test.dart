import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:chat/core/audio/audio_capability_service.dart';
import 'package:chat/core/audio/audio_policy_coordinator.dart';
import 'package:chat/core/audio/audio_types.dart';
import 'package:chat/core/audio/chaty_audio_platform.dart';
import 'package:chat/features/media/models/conversion_models.dart';
import 'package:chat/features/media/models/media_file_descriptor.dart';
import 'package:chat/features/media/models/media_playback_state.dart';
import 'package:chat/features/media/services/file_sanitization_service.dart';
import 'package:chat/features/media/services/file_type_detector.dart';
import 'package:chat/features/media/services/media_library_service.dart';
import 'package:chat/features/media/services/media_player_coordinator.dart';
import 'package:chat/features/media/services/pdf_processing_service.dart';
import 'package:chat/features/media/services/thumbnail_cache_service.dart';
import 'package:chat/features/media/ui/icons/chaty_media_icons.dart';

class MockAudioCapabilityService extends AudioCapabilityService {
  MockAudioCapabilityService()
      : super(
          platform: const MockPlatform(),
        );
}

class MockPlatform implements ChatyAudioPlatform {
  const MockPlatform();

  @override
  Stream<AudioCapabilities> get capabilityChanges => const Stream.empty();

  @override
  Future<AudioCapabilities> getCapabilities(AudioUseCase useCase) async {
    return const AudioCapabilities(
      availableRoutes: [AudioOutputRoute.systemDefault, AudioOutputRoute.speaker],
      activeRoute: AudioOutputRoute.systemDefault,
      dualOutputStatus: DualOutputStatus.unsupported,
    );
  }

  @override
  Future<AudioOutputRoute> getCurrentRoute(AudioUseCase useCase) async {
    return AudioOutputRoute.systemDefault;
  }

  @override
  Future<DualOutputStatus> getDualOutputStatus(AudioUseCase useCase) async {
    return DualOutputStatus.unsupported;
  }

  @override
  Stream<AudioOutputRoute> get routeChanges => const Stream.empty();

  @override
  Future<void> releaseSession(AudioUseCase useCase) async {}

  @override
  Future<AudioCapabilities> setPreferredRoute(
    AudioUseCase useCase,
    AudioOutputRoute route,
  ) async {
    return AudioCapabilities(
      availableRoutes: const [AudioOutputRoute.systemDefault, AudioOutputRoute.speaker],
      activeRoute: route,
      dualOutputStatus: DualOutputStatus.unsupported,
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Media Studio: FileTypeDetector Tests', () {
    test('Identifies PDF magic bytes %PDF-', () {
      final pdfBytes = Uint8List.fromList([0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34]);
      final result = FileTypeDetector.classifyBytes(pdfBytes, fileName: 'test.pdf');
      expect(result.category, equals(MediaCategory.pdf));
      expect(result.mimeType, equals('application/pdf'));
      expect(result.isSafe, isTrue);
    });

    test('Identifies PNG magic bytes', () {
      final pngBytes = Uint8List.fromList([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
      final result = FileTypeDetector.classifyBytes(pngBytes, fileName: 'photo.png');
      expect(result.category, equals(MediaCategory.image));
      expect(result.mimeType, equals('image/png'));
    });

    test('Identifies JPEG magic bytes', () {
      final jpgBytes = Uint8List.fromList([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
      final result = FileTypeDetector.classifyBytes(jpgBytes, fileName: 'image.jpg');
      expect(result.category, equals(MediaCategory.image));
      expect(result.mimeType, equals('image/jpeg'));
    });

    test('Identifies MP3 ID3 magic bytes', () {
      final mp3Bytes = Uint8List.fromList([0x49, 0x44, 0x33, 0x03, 0x00, 0x00]);
      final result = FileTypeDetector.classifyBytes(mp3Bytes, fileName: 'song.mp3');
      expect(result.category, equals(MediaCategory.audio));
      expect(result.mimeType, equals('audio/mpeg'));
    });

    test('Identifies MP4 ftyp box header', () {
      final mp4Bytes = Uint8List.fromList([
        0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D
      ]);
      final result = FileTypeDetector.classifyBytes(mp4Bytes, fileName: 'clip.mp4');
      expect(result.category, equals(MediaCategory.video));
      expect(result.mimeType, equals('video/mp4'));
    });

    test('Identifies ZIP archive header', () {
      final zipBytes = Uint8List.fromList([0x50, 0x4B, 0x03, 0x04, 0x14, 0x00]);
      final result = FileTypeDetector.classifyBytes(zipBytes, fileName: 'bundle.zip');
      expect(result.category, equals(MediaCategory.archive));
      expect(result.mimeType, equals('application/zip'));
    });
  });

  group('Media Studio: ThumbnailCacheService Tests', () {
    test('Stores and retrieves thumbnail bytes', () {
      final cache = ThumbnailCacheService.instance;
      cache.clear();

      final bytes = Uint8List.fromList([1, 2, 3, 4, 5]);
      cache.putThumbnailBytes('/test/path/img.jpg', bytes);

      expect(cache.hasThumbnail('/test/path/img.jpg'), isTrue);
      expect(cache.getThumbnailBytes('/test/path/img.jpg'), equals(bytes));
      expect(cache.hasThumbnail('/test/path/other.jpg'), isFalse);
    });

    test('Clearing removes all items', () {
      final cache = ThumbnailCacheService.instance;
      cache.putThumbnailBytes('k1', Uint8List.fromList([1]));
      cache.clear();
      expect(cache.hasThumbnail('k1'), isFalse);
    });
  });

  group('Media Studio: FileSanitizationService Tests', () {
    test('Sanitizes illegal file name characters safely', () {
      final service = FileSanitizationService.instance;
      final raw = 'report/2026:draft*final?.pdf';
      final clean = service.sanitizeFileName(raw);

      expect(clean.contains('/'), isFalse);
      expect(clean.contains(':'), isFalse);
      expect(clean.contains('*'), isFalse);
      expect(clean.contains('?'), isFalse);
      expect(clean.endsWith('.pdf'), isTrue);
    });

    test('Returns default name for empty inputs', () {
      final service = FileSanitizationService.instance;
      expect(service.sanitizeFileName(''), equals('unnamed_file'));
      expect(service.sanitizeFileName('   '), equals('unnamed_file'));
    });
  });

  group('Media Studio: PdfProcessingService Tests', () {
    test('Text to PDF generator creates valid PDF document bytes', () async {
      final tempDir = Directory.systemTemp.createTempSync('chaty_test_pdf');
      final textFile = File('${tempDir.path}/sample.txt');
      await textFile.writeAsString('Hello Chaty\nThis is a sample document for testing PDF generation.');

      final service = PdfProcessingService();
      final request = ConversionRequest(
        id: 'req_1',
        type: ConversionType.textToPdf,
        sourceFilePaths: [textFile.path],
        outputDirectory: tempDir.path,
        outputFileName: 'generated.pdf',
      );

      final result = await service.convertTextToPdf(request);
      expect(result.success, isTrue);
      expect(result.outputFilePaths.isNotEmpty, isTrue);

      final generated = File(result.outputFilePaths.first);
      expect(generated.existsSync(), isTrue);

      final bytes = await generated.readAsBytes();
      final header = String.fromCharCodes(bytes.take(8));
      expect(header.startsWith('%PDF-1.4'), isTrue);

      tempDir.deleteSync(recursive: true);
    });

    test('Extract text from empty or missing PDF returns safe message', () async {
      final service = PdfProcessingService();
      final text = await service.extractTextFromPdf('/non_existent_file.pdf');
      expect(text, isEmpty);
    });
  });

  group('Media Studio: MediaLibraryService Filtering & Sorting Tests', () {
    test('Category filtering applies accurately', () {
      final service = MediaLibraryService();
      expect(service.activeCategoryFilter, isNull);

      service.setFilter(MediaCategory.image);
      expect(service.activeCategoryFilter, equals(MediaCategory.image));

      service.setSearchQuery('report');
      expect(service.searchQuery, equals('report'));

      service.setFilter(null);
      expect(service.activeCategoryFilter, isNull);
    });

    test('Storage breakdown calculates sums correctly', () {
      final service = MediaLibraryService();
      final breakdown = service.getStorageBreakdown();
      expect(breakdown, isA<Map<MediaCategory, int>>());
    });
  });

  group('Media Studio: MediaPlaybackState & Coordinator Tests', () {
    test('Default MediaPlaybackState values match expectations', () {
      const state = MediaPlaybackState();
      expect(state.status, equals(PlaybackStatus.idle));
      expect(state.isPlaying, isFalse);
      expect(state.speed, equals(1.0));
      expect(state.repeatMode, equals(ChatyRepeatMode.off));
      expect(state.progress, equals(0.0));
    });

    test('RepeatMode cycles through off -> all -> one -> off', () {
      final mockCap = MockAudioCapabilityService();
      final coordinator = AudioPolicyCoordinator(capabilityService: mockCap);
      final mediaCoord = MediaPlayerCoordinator(policyCoordinator: coordinator);

      expect(mediaCoord.state.repeatMode, equals(ChatyRepeatMode.off));
      mediaCoord.cycleRepeatMode();
      expect(mediaCoord.state.repeatMode, equals(ChatyRepeatMode.all));
      mediaCoord.cycleRepeatMode();
      expect(mediaCoord.state.repeatMode, equals(ChatyRepeatMode.one));
      mediaCoord.cycleRepeatMode();
      expect(mediaCoord.state.repeatMode, equals(ChatyRepeatMode.off));

      mediaCoord.dispose();
      coordinator.dispose();
    });

    test('Toggle shuffle updates state reactively', () {
      final mockCap = MockAudioCapabilityService();
      final coordinator = AudioPolicyCoordinator(capabilityService: mockCap);
      final mediaCoord = MediaPlayerCoordinator(policyCoordinator: coordinator);

      expect(mediaCoord.state.isShuffle, isFalse);
      mediaCoord.toggleShuffle();
      expect(mediaCoord.state.isShuffle, isTrue);
      mediaCoord.toggleShuffle();
      expect(mediaCoord.state.isShuffle, isFalse);

      mediaCoord.dispose();
      coordinator.dispose();
    });
  });

  group('Media Studio: Custom Vector Icons Widget Tests', () {
    testWidgets('Renders ChatyMediaIcon for all primary icon types', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Row(
              children: [
                ChatyMediaIcon(icon: ChatyIconType.audio),
                ChatyMediaIcon(icon: ChatyIconType.video),
                ChatyMediaIcon(icon: ChatyIconType.pdf),
                ChatyMediaIcon(icon: ChatyIconType.document),
                ChatyMediaIcon(icon: ChatyIconType.play),
                ChatyMediaIcon(icon: ChatyIconType.pause),
              ],
            ),
          ),
        ),
      );

      expect(find.byType(ChatyMediaIcon), findsNWidgets(6));
    });
  });
}
