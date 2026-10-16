import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../injection/locator.dart';
import '../../../../ui/core/design_system/design_system.dart' hide ChatyMiniPlayer;
import '../../models/media_file_descriptor.dart';
import '../../services/file_sanitization_service.dart';
import '../../services/media_library_service.dart';
import '../../services/media_player_coordinator.dart';
import '../audio/chaty_audio_player_screen.dart';
import '../audio/chaty_mini_player.dart';
import '../document/document_reader_screen.dart';
import '../icons/chaty_media_icons.dart';
import '../pdf/pdf_workspace_screen.dart';
import '../picker/custom_file_picker_screen.dart';

class MediaWorkspaceScreen extends StatefulWidget {
  const MediaWorkspaceScreen({super.key});

  @override
  State<MediaWorkspaceScreen> createState() => _MediaWorkspaceScreenState();
}

class _MediaWorkspaceScreenState extends State<MediaWorkspaceScreen> {
  final MediaLibraryService _library = MediaLibraryService();
  late final MediaPlayerCoordinator _playerCoordinator;

  @override
  void initState() {
    super.initState();
    _playerCoordinator = locator.isRegistered<MediaPlayerCoordinator>()
        ? locator<MediaPlayerCoordinator>()
        : MediaPlayerCoordinator();
    _library.refreshLibrary();
  }

  @override
  void dispose() {
    _library.dispose();
    super.dispose();
  }

  Future<void> _cleanTempCache() async {
    HapticFeedback.lightImpact();
    await FileSanitizationService.instance.cleanExpiredTempFiles(maxAge: Duration.zero);
    await _library.refreshLibrary();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Temporary media cache cleaned successfully.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0F131A) : const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: isDark ? const Color(0xFF131822) : Colors.white,
        elevation: 0,
        title: const Text(
          'Media Studio & Files',
          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.cleaning_services_rounded),
            tooltip: 'Clean temporary cache',
            onPressed: _cleanTempCache,
          ),
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: 'Refresh library',
            onPressed: () => _library.refreshLibrary(),
          ),
          const SizedBox(width: 6),
        ],
      ),
      body: ListenableBuilder(
        listenable: _library,
        builder: (context, _) {
          final totalBytes = _library.files.fold<int>(0, (sum, f) => sum + f.sizeBytes);
          final breakdown = _library.getStorageBreakdown();

          return Stack(
            children: [
              ListView(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 90),
                children: [
                  // Storage overview card
                  _buildStorageOverviewCard(theme, isDark, totalBytes, breakdown),
                  const SizedBox(height: 20),

                  // Quick Action Grid
                  Text(
                    'STUDIO WORKSPACES',
                    style: theme.textTheme.labelMedium?.copyWith(
                      letterSpacing: 1.1,
                      fontWeight: FontWeight.w800,
                      color: Colors.grey,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: _actionCard(
                          icon: ChatyIconType.folder,
                          title: 'File Explorer',
                          subtitle: 'Browse all media',
                          color: const Color(0xFF38BDF8),
                          isDark: isDark,
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute<void>(
                                builder: (_) => const CustomFilePickerScreen(
                                  isWorkspaceMode: true,
                                  title: 'All Media & Files',
                                ),
                              ),
                            ).then((_) => _library.refreshLibrary());
                          },
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _actionCard(
                          icon: ChatyIconType.pdf,
                          title: 'PDF Studio',
                          subtitle: 'Convert & manage',
                          color: const Color(0xFFEF4444),
                          isDark: isDark,
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute<void>(
                                builder: (_) => const PdfWorkspaceScreen(),
                              ),
                            ).then((_) => _library.refreshLibrary());
                          },
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),

                  // Recent Files in Chaty
                  Text(
                    'RECENT ATTACHMENTS & FILES',
                    style: theme.textTheme.labelMedium?.copyWith(
                      letterSpacing: 1.1,
                      fontWeight: FontWeight.w800,
                      color: Colors.grey,
                    ),
                  ),
                  const SizedBox(height: 12),

                  if (_library.files.isEmpty)
                    Container(
                      padding: const EdgeInsets.all(28),
                      decoration: BoxDecoration(
                        color: isDark ? const Color(0xFF161C26) : Colors.white,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: const Center(
                        child: Text('No files or media found in Chaty storage.'),
                      ),
                    )
                  else
                    ListView.separated(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: _library.files.take(15).length,
                      separatorBuilder: (_, __) => const SizedBox(height: 8),
                      itemBuilder: (context, idx) {
                        final file = _library.files[idx];
                        return SpringInteractiveScale(
                          scaleDownFactor: 0.98,
                          onTap: () {
                            if (file.category == MediaCategory.audio) {
                              _playerCoordinator.playTrack(file, queue: _library.files.where((f) => f.category == MediaCategory.audio).toList());
                            } else {
                              Navigator.push(
                                context,
                                MaterialPageRoute<void>(
                                  builder: (_) => DocumentReaderScreen(file: file),
                                ),
                              );
                            }
                          },
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                            decoration: BoxDecoration(
                              color: isDark ? const Color(0xFF161C26) : Colors.white,
                              borderRadius: BorderRadius.circular(14),
                              border: Border.all(
                                color: isDark ? Colors.white.withValues(alpha: 0.08) : Colors.black.withValues(alpha: 0.06),
                              ),
                            ),
                            child: Row(
                              children: [
                                _buildFileThumb(file),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        file.name,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
                                      ),
                                      Text(
                                        '${file.humanReadableSize} • ${file.category.name.toUpperCase()}',
                                        style: TextStyle(
                                          fontSize: 11,
                                          color: isDark ? Colors.white54 : Colors.black54,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const Icon(Icons.chevron_right_rounded, color: Colors.grey),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                ],
              ),

              // Mini Player pinned at bottom if active
              Positioned(
                bottom: 0,
                left: 0,
                right: 0,
                child: ChatyMiniPlayer(coordinator: _playerCoordinator),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildStorageOverviewCard(ThemeData theme, bool isDark, int totalBytes, Map<MediaCategory, int> breakdown) {
    final mb = totalBytes / (1024 * 1024);

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF161C26) : Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isDark ? Colors.white.withValues(alpha: 0.1) : Colors.black.withValues(alpha: 0.08),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.25 : 0.04),
            blurRadius: 14,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                'Chaty Media Storage',
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
              ),
              const Spacer(),
              Text(
                '${mb.toStringAsFixed(1)} MB Used',
                style: TextStyle(
                  fontWeight: FontWeight.w700,
                  color: theme.colorScheme.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          // Multi-segmented bar
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: SizedBox(
              height: 10,
              child: Row(
                children: [
                  _storageSegment(breakdown[MediaCategory.image] ?? 0, totalBytes, const Color(0xFF38BDF8)),
                  _storageSegment(breakdown[MediaCategory.video] ?? 0, totalBytes, const Color(0xFFA855F7)),
                  _storageSegment(breakdown[MediaCategory.audio] ?? 0, totalBytes, const Color(0xFF10B981)),
                  _storageSegment(breakdown[MediaCategory.pdf] ?? 0, totalBytes, const Color(0xFFEF4444)),
                  _storageSegment(breakdown[MediaCategory.document] ?? 0, totalBytes, const Color(0xFFF59E0B)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 14),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _legendDot('Photos', const Color(0xFF38BDF8)),
              _legendDot('Videos', const Color(0xFFA855F7)),
              _legendDot('Audio', const Color(0xFF10B981)),
              _legendDot('PDFs', const Color(0xFFEF4444)),
              _legendDot('Docs', const Color(0xFFF59E0B)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _storageSegment(int bytes, int total, Color color) {
    if (total == 0 || bytes == 0) return const SizedBox.shrink();
    return Expanded(
      flex: bytes,
      child: Container(color: color),
    );
  }

  Widget _legendDot(String label, Color color) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 8, height: 8, decoration: BoxDecoration(shape: BoxShape.circle, color: color)),
        const SizedBox(width: 4),
        Text(label, style: const TextStyle(fontSize: 10, color: Colors.grey)),
      ],
    );
  }

  Widget _actionCard({
    required ChatyIconType icon,
    required String title,
    required String subtitle,
    required Color color,
    required bool isDark,
    required VoidCallback onTap,
  }) {
    return SpringInteractiveScale(
      scaleDownFactor: 0.96,
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF161C26) : Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isDark ? Colors.white.withValues(alpha: 0.08) : Colors.black.withValues(alpha: 0.06),
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: isDark ? 0.2 : 0.04),
              blurRadius: 10,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: color.withValues(alpha: 0.15),
              ),
              child: Center(child: ChatyMediaIcon(icon: icon, size: 20, color: color)),
            ),
            const SizedBox(height: 12),
            Text(title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
            const SizedBox(height: 2),
            Text(subtitle, style: const TextStyle(fontSize: 11, color: Colors.grey)),
          ],
        ),
      ),
    );
  }

  Widget _buildFileThumb(MediaFileDescriptor file) {
    if (file.category == MediaCategory.image && file.exists) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Image.file(
          File(file.path),
          width: 38,
          height: 38,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => const Icon(Icons.image_rounded),
        ),
      );
    }
    return Container(
      width: 38,
      height: 38,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: Colors.grey.withValues(alpha: 0.15),
      ),
      child: Center(
        child: ChatyMediaIcon(
          icon: file.category == MediaCategory.audio
              ? ChatyIconType.audio
              : file.category == MediaCategory.pdf
                  ? ChatyIconType.pdf
                  : ChatyIconType.document,
          size: 18,
          color: Colors.blueAccent,
        ),
      ),
    );
  }
}
