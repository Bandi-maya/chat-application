import 'dart:io';
import 'dart:ui' as ui;
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../ui/core/design_system/design_system.dart';
import '../../models/media_file_descriptor.dart';
import '../../services/file_type_detector.dart';
import '../../services/media_library_service.dart';
import '../icons/chaty_media_icons.dart';
import '../document/document_reader_screen.dart';

class CustomFilePickerScreen extends StatefulWidget {
  final bool allowMultiple;
  final MediaCategory? initialCategory;
  final String title;
  final bool isWorkspaceMode;

  const CustomFilePickerScreen({
    super.key,
    this.allowMultiple = true,
    this.initialCategory,
    this.title = 'Files & Media',
    this.isWorkspaceMode = false,
  });

  @override
  State<CustomFilePickerScreen> createState() => _CustomFilePickerScreenState();
}

class _CustomFilePickerScreenState extends State<CustomFilePickerScreen> {
  final MediaLibraryService _libraryService = MediaLibraryService();
  final TextEditingController _searchController = TextEditingController();
  final Set<String> _selectedPaths = <String>{};
  bool _isGridView = true;
  bool _isSelectionMode = false;

  @override
  void initState() {
    super.initState();
    if (widget.initialCategory != null) {
      _libraryService.setFilter(widget.initialCategory);
    }
    _libraryService.refreshLibrary();
  }

  @override
  void dispose() {
    _searchController.dispose();
    _libraryService.dispose();
    super.dispose();
  }

  void _toggleSelect(MediaFileDescriptor item) {
    HapticFeedback.selectionClick();
    setState(() {
      if (_selectedPaths.contains(item.path)) {
        _selectedPaths.remove(item.path);
        if (_selectedPaths.isEmpty && !widget.isWorkspaceMode) {
          _isSelectionMode = false;
        }
      } else {
        if (!widget.allowMultiple) {
          _selectedPaths.clear();
        }
        _selectedPaths.add(item.path);
        _isSelectionMode = true;
      }
    });
  }

  Future<void> _pickFromDeviceStorage() async {
    HapticFeedback.lightImpact();
    try {
      final picked = await FilePicker.pickFiles(
        allowMultiple: widget.allowMultiple,
      );
      if (picked.isNotEmpty) {
        final newPaths = picked
            .map((f) => f.path)
            .whereType<String>()
            .where((p) => p.isNotEmpty)
            .toList();

        if (newPaths.isNotEmpty) {
          if (widget.isWorkspaceMode) {
            await _libraryService.refreshLibrary();
          } else {
            final selectedDescriptors = <MediaFileDescriptor>[];
            for (final p in newPaths) {
              final classification = await FileTypeDetector.classifyFile(p);
              final f = File(p);
              final stat = await f.stat();
              selectedDescriptors.add(MediaFileDescriptor(
                id: p,
                name: p.split(Platform.pathSeparator).last,
                path: p,
                sizeBytes: stat.size,
                mimeType: classification.mimeType,
                category: classification.category,
                modifiedAt: stat.modified,
              ));
            }
            if (!mounted) return;
            _confirmAndReturn(selectedDescriptors);
          }
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to access device files: $e')),
        );
      }
    }
  }

  void _confirmAndReturn(List<MediaFileDescriptor> files) {
    if (files.isEmpty) return;
    _showAttachmentSummaryDialog(files);
  }

  Future<void> _showAttachmentSummaryDialog(List<MediaFileDescriptor> files) async {
    final captionController = TextEditingController();
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final result = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => Container(
        padding: EdgeInsets.fromLTRB(20, 16, 20, 20 + MediaQuery.viewInsetsOf(sheetContext).bottom),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF141923) : Colors.white,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
          border: Border(
            top: BorderSide(
              color: isDark ? Colors.white.withValues(alpha: 0.12) : Colors.black.withValues(alpha: 0.08),
              width: 1,
            ),
          ),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey.withValues(alpha: 0.4),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Text(
                  'Send ${files.length} ${files.length == 1 ? 'Attachment' : 'Attachments'}',
                  style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                ),
                const Spacer(),
                Text(
                  '${(files.fold<int>(0, (sum, f) => sum + f.sizeBytes) / (1024 * 1024)).toStringAsFixed(2)} MB total',
                  style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                ),
              ],
            ),
            const SizedBox(height: 14),
            SizedBox(
              height: 80,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: files.length,
                separatorBuilder: (_, __) => const SizedBox(width: 10),
                itemBuilder: (_, index) {
                  final f = files[index];
                  return Container(
                    width: 76,
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF1E2433) : const Color(0xFFF1F5F9),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: isDark ? Colors.white.withValues(alpha: 0.08) : Colors.black.withValues(alpha: 0.06),
                      ),
                    ),
                    padding: const EdgeInsets.all(8),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        _buildCategoryIcon(f.category, size: 24),
                        const SizedBox(height: 4),
                        Text(
                          f.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w600),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 14),
            TextField(
              controller: captionController,
              decoration: InputDecoration(
                hintText: 'Add a message or caption...',
                filled: true,
                fillColor: isDark ? const Color(0xFF1C2230) : const Color(0xFFF8FAFC),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none,
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.pop(sheetContext, false),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    ),
                    child: const Text('Cancel'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: () => Navigator.pop(sheetContext, true),
                    style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      backgroundColor: theme.colorScheme.primary,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    ),
                    child: const Text('Send Now'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );

    if (result == true && mounted) {
      Navigator.pop(context, files);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0F131A) : const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: isDark ? const Color(0xFF131822).withValues(alpha: 0.85) : Colors.white.withValues(alpha: 0.85),
        elevation: 0,
        surfaceTintColor: Colors.transparent,
        systemOverlayStyle: isDark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark,
        title: Text(
          widget.title,
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.w700,
            letterSpacing: -0.2,
          ),
        ),
        actions: [
          IconButton(
            icon: Icon(_isGridView ? Icons.view_list_rounded : Icons.grid_view_rounded),
            tooltip: _isGridView ? 'List view' : 'Grid view',
            onPressed: () => setState(() => _isGridView = !_isGridView),
          ),
          IconButton(
            icon: const Icon(Icons.add_to_photos_rounded),
            tooltip: 'Browse device files',
            onPressed: _pickFromDeviceStorage,
          ),
          const SizedBox(width: 6),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(104),
          child: Column(
            children: [
              // Search input
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: SizedBox(
                  height: 42,
                  child: TextField(
                    controller: _searchController,
                    onChanged: (val) => _libraryService.setSearchQuery(val),
                    decoration: InputDecoration(
                      hintText: 'Search files, documents, photos...',
                      prefixIcon: const Icon(Icons.search_rounded, size: 20),
                      suffixIcon: _searchController.text.isNotEmpty
                          ? IconButton(
                              icon: const Icon(Icons.clear_rounded, size: 18),
                              onPressed: () {
                                _searchController.clear();
                                _libraryService.setSearchQuery('');
                              },
                            )
                          : null,
                      filled: true,
                      fillColor: isDark ? const Color(0xFF1A212E) : const Color(0xFFEDF2F7),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: EdgeInsets.zero,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 8),
              // Category Filter Pills
              _buildFilterChips(isDark),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
      body: ListenableBuilder(
        listenable: _libraryService,
        builder: (context, _) {
          if (_libraryService.isLoading) {
            return const Center(child: CircularProgressIndicator.adaptive());
          }

          final items = _libraryService.filteredFiles;
          if (items.isEmpty) {
            return _buildEmptyState(theme, isDark);
          }

          return Stack(
            children: [
              _isGridView
                  ? _buildGridView(items, isDark)
                  : _buildListView(items, isDark),
              if (_selectedPaths.isNotEmpty)
                _buildFloatingActionDock(items, theme, isDark),
            ],
          );
        },
      ),
    );
  }

  Widget _buildFilterChips(bool isDark) {
    final categories = <(MediaCategory?, String)>[
      (null, 'All'),
      (MediaCategory.image, 'Images'),
      (MediaCategory.video, 'Videos'),
      (MediaCategory.audio, 'Audio'),
      (MediaCategory.pdf, 'PDFs'),
      (MediaCategory.document, 'Docs'),
      (MediaCategory.archive, 'Archives'),
    ];

    return SizedBox(
      height: 36,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        scrollDirection: Axis.horizontal,
        itemCount: categories.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (context, idx) {
          final cat = categories[idx].$1;
          final label = categories[idx].$2;
          final isSelected = _libraryService.activeCategoryFilter == cat;

          return SpringInteractiveScale(
            scaleDownFactor: 0.96,
            onTap: () {
              HapticFeedback.selectionClick();
              _libraryService.setFilter(cat);
            },
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
              decoration: BoxDecoration(
                color: isSelected
                    ? Theme.of(context).colorScheme.primary
                    : (isDark ? const Color(0xFF1A212E) : Colors.white),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: isSelected
                      ? Colors.transparent
                      : (isDark ? Colors.white.withValues(alpha: 0.1) : Colors.black.withValues(alpha: 0.08)),
                ),
              ),
              child: Center(
                child: Text(
                  label,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                    color: isSelected
                        ? Colors.white
                        : (isDark ? const Color(0xFFCBD5E1) : const Color(0xFF334155)),
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildGridView(List<MediaFileDescriptor> items, bool isDark) {
    return GridView.builder(
      padding: EdgeInsets.fromLTRB(16, 12, 16, _selectedPaths.isNotEmpty ? 90 : 20),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        crossAxisSpacing: 10,
        mainAxisSpacing: 10,
        childAspectRatio: 0.85,
      ),
      itemCount: items.length,
      itemBuilder: (context, index) {
        final item = items[index];
        final isSelected = _selectedPaths.contains(item.path);

        return SpringInteractiveScale(
          scaleDownFactor: 0.97,
          onTap: () {
            if (_isSelectionMode || !widget.isWorkspaceMode) {
              _toggleSelect(item);
            } else {
              _openFile(item);
            }
          },
          onLongPress: () => _toggleSelect(item),
          child: Container(
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF161C26) : Colors.white,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: isSelected
                    ? Theme.of(context).colorScheme.primary
                    : (isDark ? Colors.white.withValues(alpha: 0.08) : Colors.black.withValues(alpha: 0.06)),
                width: isSelected ? 2 : 1,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: isDark ? 0.2 : 0.04),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: Stack(
              children: [
                Positioned.fill(
                  child: Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Expanded(
                          child: Center(child: _buildItemPreview(item)),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          item.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: isDark ? Colors.white : const Color(0xFF1E293B),
                          ),
                        ),
                        Text(
                          item.humanReadableSize,
                          style: TextStyle(
                            fontSize: 9,
                            color: isDark ? Colors.white54 : Colors.black54,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                if (_isSelectionMode || isSelected)
                  Positioned(
                    top: 6,
                    right: 6,
                    child: Container(
                      width: 22,
                      height: 22,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: isSelected
                            ? Theme.of(context).colorScheme.primary
                            : Colors.black.withValues(alpha: 0.4),
                        border: Border.all(color: Colors.white, width: 1.5),
                      ),
                      child: isSelected
                          ? const Icon(Icons.check_rounded, size: 14, color: Colors.white)
                          : null,
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildListView(List<MediaFileDescriptor> items, bool isDark) {
    return ListView.separated(
      padding: EdgeInsets.fromLTRB(16, 12, 16, _selectedPaths.isNotEmpty ? 90 : 20),
      itemCount: items.length,
      separatorBuilder: (_, __) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final item = items[index];
        final isSelected = _selectedPaths.contains(item.path);

        return SpringInteractiveScale(
          scaleDownFactor: 0.98,
          onTap: () {
            if (_isSelectionMode || !widget.isWorkspaceMode) {
              _toggleSelect(item);
            } else {
              _openFile(item);
            }
          },
          onLongPress: () => _toggleSelect(item),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF161C26) : Colors.white,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: isSelected
                    ? Theme.of(context).colorScheme.primary
                    : (isDark ? Colors.white.withValues(alpha: 0.08) : Colors.black.withValues(alpha: 0.06)),
                width: isSelected ? 2 : 1,
              ),
            ),
            child: Row(
              children: [
                SizedBox(
                  width: 44,
                  height: 44,
                  child: Center(child: _buildItemPreview(item)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        item.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${item.humanReadableSize} • ${item.extension.toUpperCase()}',
                        style: TextStyle(
                          fontSize: 11,
                          color: isDark ? Colors.white54 : Colors.black54,
                        ),
                      ),
                    ],
                  ),
                ),
                if (_isSelectionMode || isSelected)
                  Container(
                    width: 22,
                    height: 22,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: isSelected
                          ? Theme.of(context).colorScheme.primary
                          : Colors.transparent,
                      border: Border.all(
                        color: isSelected
                            ? Theme.of(context).colorScheme.primary
                            : (isDark ? Colors.white38 : Colors.black38),
                        width: 1.5,
                      ),
                    ),
                    child: isSelected
                        ? const Icon(Icons.check_rounded, size: 14, color: Colors.white)
                        : null,
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildItemPreview(MediaFileDescriptor item) {
    if (item.category == MediaCategory.image && item.exists) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Image.file(
          File(item.path),
          fit: BoxFit.cover,
          width: 48,
          height: 48,
          errorBuilder: (_, __, ___) => _buildCategoryIcon(item.category),
        ),
      );
    }
    return _buildCategoryIcon(item.category);
  }

  Widget _buildCategoryIcon(MediaCategory category, {double size = 32}) {
    switch (category) {
      case MediaCategory.image:
        return Icon(Icons.image_rounded, size: size, color: const Color(0xFF38BDF8));
      case MediaCategory.video:
        return ChatyMediaIcon(icon: ChatyIconType.video, size: size, color: const Color(0xFFA855F7));
      case MediaCategory.audio:
        return ChatyMediaIcon(icon: ChatyIconType.audio, size: size, color: const Color(0xFF10B981));
      case MediaCategory.pdf:
        return ChatyMediaIcon(icon: ChatyIconType.pdf, size: size, color: const Color(0xFFEF4444));
      case MediaCategory.document:
        return ChatyMediaIcon(icon: ChatyIconType.document, size: size, color: const Color(0xFF3B82F6));
      case MediaCategory.archive:
        return ChatyMediaIcon(icon: ChatyIconType.archive, size: size, color: const Color(0xFFF59E0B));
      case MediaCategory.other:
        return Icon(Icons.insert_drive_file_rounded, size: size, color: Colors.grey);
    }
  }

  Widget _buildFloatingActionDock(List<MediaFileDescriptor> items, ThemeData theme, bool isDark) {
    final selectedItems = items.where((i) => _selectedPaths.contains(i.path)).toList();

    return Positioned(
      bottom: 20,
      left: 20,
      right: 20,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(22),
        child: BackdropFilter(
          filter: ui.ImageFilter.blur(sigmaX: 20, sigmaY: 20),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1A2230).withValues(alpha: 0.9) : Colors.white.withValues(alpha: 0.9),
              borderRadius: BorderRadius.circular(22),
              border: Border.all(
                color: isDark ? Colors.white.withValues(alpha: 0.15) : Colors.black.withValues(alpha: 0.1),
                width: 1,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.25),
                  blurRadius: 16,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Row(
              children: [
                Text(
                  '${_selectedPaths.length} selected',
                  style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                ),
                const Spacer(),
                TextButton(
                  onPressed: () => setState(() => _selectedPaths.clear()),
                  child: const Text('Deselect'),
                ),
                const SizedBox(width: 8),
                FilledButton.icon(
                  onPressed: () => _confirmAndReturn(selectedItems),
                  icon: const Icon(Icons.arrow_upward_rounded, size: 18),
                  label: Text(widget.isWorkspaceMode ? 'Share / Open' : 'Attach'),
                  style: FilledButton.styleFrom(
                    backgroundColor: theme.colorScheme.primary,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyState(ThemeData theme, bool isDark) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: isDark ? const Color(0xFF1E2638) : const Color(0xFFEDF2F7),
              ),
              child: const Icon(Icons.folder_open_rounded, size: 40, color: Colors.grey),
            ),
            const SizedBox(height: 16),
            Text(
              'No Files Found',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 6),
            Text(
              'Select "Browse device files" to import photos, videos, or documents from your phone.',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              onPressed: _pickFromDeviceStorage,
              icon: const Icon(Icons.file_open_rounded),
              label: const Text('Browse Files'),
              style: OutlinedButton.styleFrom(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _openFile(MediaFileDescriptor item) {
    if (item.path.startsWith('demo/') || !item.exists) {
      _toggleSelect(item);
      return;
    }
    Navigator.push(
      context,
      MaterialPageRoute<void>(
        builder: (_) => DocumentReaderScreen(file: item),
      ),
    );
  }
}
