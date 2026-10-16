import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import '../models/media_file_descriptor.dart';
import 'file_type_detector.dart';

class MediaLibraryService extends ChangeNotifier {
  MediaLibraryService();

  final List<MediaFileDescriptor> _files = <MediaFileDescriptor>[];
  bool _isLoading = false;
  String _searchQuery = '';
  MediaCategory? _activeCategoryFilter;

  List<MediaFileDescriptor> get files => List<MediaFileDescriptor>.unmodifiable(_files);
  bool get isLoading => _isLoading;
  String get searchQuery => _searchQuery;
  MediaCategory? get activeCategoryFilter => _activeCategoryFilter;

  List<MediaFileDescriptor> get filteredFiles {
    return _files.where((f) {
      if (_activeCategoryFilter != null && f.category != _activeCategoryFilter) {
        return false;
      }
      if (_searchQuery.isNotEmpty && !f.name.toLowerCase().contains(_searchQuery.toLowerCase())) {
        return false;
      }
      return true;
    }).toList();
  }

  void setFilter(MediaCategory? category) {
    _activeCategoryFilter = category;
    notifyListeners();
  }

  void setSearchQuery(String query) {
    _searchQuery = query;
    notifyListeners();
  }

  Future<void> refreshLibrary() async {
    _isLoading = true;
    notifyListeners();

    _files.clear();
    try {
      final appDocDir = await getApplicationDocumentsDirectory();
      final tempDir = await getTemporaryDirectory();

      final directories = <Directory>[appDocDir, tempDir];

      for (final dir in directories) {
        if (!await dir.exists()) continue;
        await for (final entity in dir.list(recursive: false)) {
          if (entity is File) {
            final name = entity.path.split(Platform.pathSeparator).last;
            if (name.startsWith('.')) continue; // skip hidden
            final stat = await entity.stat();
            final classification = await FileTypeDetector.classifyFile(entity.path);

            _files.add(MediaFileDescriptor(
              id: entity.path,
              name: name,
              path: entity.path,
              sizeBytes: stat.size,
              mimeType: classification.mimeType,
              category: classification.category,
              modifiedAt: stat.modified,
            ));
          }
        }
      }

      // If no local files exist on this device/emulator, provide pre-populated starter demo assets
      if (_files.isEmpty) {
        final now = DateTime.now();
        _files.addAll(<MediaFileDescriptor>[
          MediaFileDescriptor(
            id: 'demo_doc_spec',
            name: 'Project_Architecture_Spec.pdf',
            path: 'demo/Project_Architecture_Spec.pdf',
            sizeBytes: 2569011,
            mimeType: 'application/pdf',
            category: MediaCategory.document,
            modifiedAt: now.subtract(const Duration(hours: 2)),
            pageCount: 14,
          ),
          MediaFileDescriptor(
            id: 'demo_doc_sheet',
            name: 'Q4_Financial_Roadmap.xlsx',
            path: 'demo/Q4_Financial_Roadmap.xlsx',
            sizeBytes: 1145728,
            mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            category: MediaCategory.document,
            modifiedAt: now.subtract(const Duration(hours: 5)),
            pageCount: 3,
          ),
          MediaFileDescriptor(
            id: 'demo_audio_anirudh',
            name: 'Hangova - Anirudh.mp3',
            path: 'demo/Hangova - Anirudh.mp3',
            sizeBytes: 5033164,
            mimeType: 'audio/mpeg',
            category: MediaCategory.audio,
            modifiedAt: now.subtract(const Duration(hours: 8)),
            durationSeconds: 225,
          ),
          MediaFileDescriptor(
            id: 'demo_audio_meeting',
            name: 'Design_Review_Audio.m4a',
            path: 'demo/Design_Review_Audio.m4a',
            sizeBytes: 1843200,
            mimeType: 'audio/mp4',
            category: MediaCategory.audio,
            modifiedAt: now.subtract(const Duration(days: 1)),
            durationSeconds: 98,
          ),
        ]);
      }

      // Sort recent first
      _files.sort((a, b) => b.modifiedAt.compareTo(a.modifiedAt));
    } catch (_) {
      // Handled gracefully
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Map<MediaCategory, int> getStorageBreakdown() {
    final map = <MediaCategory, int>{};
    for (final f in _files) {
      map[f.category] = (map[f.category] ?? 0) + f.sizeBytes;
    }
    return map;
  }
}
