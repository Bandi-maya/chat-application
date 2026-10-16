import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../../ui/core/design_system/design_system.dart';
import '../../models/media_file_descriptor.dart';
import '../../models/reader_models.dart';
import '../../services/file_type_detector.dart';
import '../../services/pdf_processing_service.dart';
import '../icons/chaty_media_icons.dart';
import '../video/chaty_video_player_widget.dart';

class DocumentReaderScreen extends StatefulWidget {
  final MediaFileDescriptor file;

  const DocumentReaderScreen({
    super.key,
    required this.file,
  });

  @override
  State<DocumentReaderScreen> createState() => _DocumentReaderScreenState();
}

class _DocumentReaderScreenState extends State<DocumentReaderScreen> {
  final TextEditingController _searchController = TextEditingController();
  final PdfProcessingService _pdfService = PdfProcessingService();

  bool _isSearching = false;
  String _searchQuery = '';
  int _currentPage = 1;
  int _totalPages = 1;
  String _textContent = '';
  bool _isLoadingContent = true;
  String? _contentError;
  List<DocumentSearchMatch> _searchMatches = <DocumentSearchMatch>[];
  int _currentMatchIndex = 0;

  @override
  void initState() {
    super.initState();
    _loadDocumentContent();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadDocumentContent() async {
    setState(() => _isLoadingContent = true);
    final file = File(widget.file.path);
    if (!await file.exists()) {
      if (mounted) {
        setState(() {
          _isLoadingContent = false;
          _contentError = 'File does not exist on disk: ${widget.file.path}';
        });
      }
      return;
    }

    try {
      if (widget.file.category == MediaCategory.pdf) {
        final extracted = await _pdfService.extractTextFromPdf(widget.file.path);
        // Estimate pages based on size and lines
        final lines = extracted.split('\n').length;
        _totalPages = math.max(1, (lines / 35).ceil());
        _textContent = extracted;
      } else if (widget.file.category == MediaCategory.document ||
                 widget.file.extension == 'txt' ||
                 widget.file.extension == 'md' ||
                 widget.file.extension == 'json' ||
                 widget.file.extension == 'csv') {
        _textContent = await file.readAsString();
        final lines = _textContent.split('\n').length;
        _totalPages = math.max(1, (lines / 45).ceil());
      }
    } catch (e) {
      _contentError = 'Could not read file contents: $e';
    } finally {
      if (mounted) {
        setState(() => _isLoadingContent = false);
      }
    }
  }

  void _onSearchChanged(String query) {
    setState(() {
      _searchQuery = query;
      _searchMatches.clear();
      _currentMatchIndex = 0;

      if (query.isNotEmpty && _textContent.isNotEmpty) {
        final matches = RegExp(RegExp.escape(query), caseSensitive: false).allMatches(_textContent);
        int idx = 0;
        for (final m in matches) {
          final start = math.max(0, m.start - 20);
          final end = math.min(_textContent.length, m.end + 20);
          final snippet = _textContent.substring(start, end).replaceAll('\n', ' ');
          _searchMatches.add(DocumentSearchMatch(
            pageNumber: (m.start / math.max(1, _textContent.length / _totalPages)).ceil().clamp(1, _totalPages),
            matchIndex: idx++,
            snippet: snippet,
            startOffset: m.start,
            endOffset: m.end,
          ));
        }
      }
    });
  }

  void _nextMatch() {
    if (_searchMatches.isEmpty) return;
    setState(() {
      _currentMatchIndex = (_currentMatchIndex + 1) % _searchMatches.length;
      _currentPage = _searchMatches[_currentMatchIndex].pageNumber;
    });
  }

  void _prevMatch() {
    if (_searchMatches.isEmpty) return;
    setState(() {
      _currentMatchIndex = (_currentMatchIndex - 1 + _searchMatches.length) % _searchMatches.length;
      _currentPage = _searchMatches[_currentMatchIndex].pageNumber;
    });
  }

  Future<void> _shareFile() async {
    HapticFeedback.lightImpact();
    await SharePlus.instance.share(
      ShareParams(files: [XFile(widget.file.path)], subject: widget.file.name),
    );
  }

  Future<void> _openExternal() async {
    HapticFeedback.lightImpact();
    final uri = Uri.file(widget.file.path);
    final launched = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!launched && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No external application found to open this file.')),
      );
    }
  }

  void _showPageJumpDialog() {
    final controller = TextEditingController(text: '$_currentPage');
    showDialog<void>(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        title: const Text('Jump to Page'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          autofocus: true,
          decoration: InputDecoration(
            hintText: 'Enter page (1 - $_totalPages)',
            suffixText: 'of $_totalPages',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogCtx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              final target = int.tryParse(controller.text);
              if (target != null && target >= 1 && target <= _totalPages) {
                setState(() => _currentPage = target);
              }
              Navigator.pop(dialogCtx);
            },
            child: const Text('Jump'),
          ),
        ],
      ),
    );
  }

  void _showFileDetailsSheet() {
    final f = widget.file;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (sheetCtx) => Container(
        padding: const EdgeInsets.all(22),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF141923) : Colors.white,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey.withValues(alpha: 0.4),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'File Information',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 14),
            _infoRow('File Name', f.name),
            _infoRow('Size', f.humanReadableSize),
            _infoRow('MIME Type', f.mimeType),
            _infoRow('Category', f.category.name.toUpperCase()),
            _infoRow('Path', f.path),
            _infoRow('Modified', f.modifiedAt.toLocal().toString().split('.')[0]),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () {
                      Navigator.pop(sheetCtx);
                      _openExternal();
                    },
                    icon: const Icon(Icons.open_in_new_rounded),
                    label: const Text('Open External'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: () {
                      Navigator.pop(sheetCtx);
                      _shareFile();
                    },
                    icon: const Icon(Icons.share_rounded),
                    label: const Text('Share File'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _infoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 90,
            child: Text(label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12, color: Colors.grey)),
          ),
          Expanded(
            child: Text(value, style: const TextStyle(fontSize: 12)),
          ),
        ],
      ),
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
        surfaceTintColor: Colors.transparent,
        title: _isSearching
            ? TextField(
                controller: _searchController,
                autofocus: true,
                onChanged: _onSearchChanged,
                decoration: InputDecoration(
                  hintText: 'Search text in document...',
                  border: InputBorder.none,
                  suffixIcon: _searchMatches.isNotEmpty
                      ? Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              '${_currentMatchIndex + 1}/${_searchMatches.length}',
                              style: const TextStyle(fontSize: 12, color: Colors.grey),
                            ),
                            IconButton(
                              icon: const Icon(Icons.keyboard_arrow_up_rounded),
                              onPressed: _prevMatch,
                            ),
                            IconButton(
                              icon: const Icon(Icons.keyboard_arrow_down_rounded),
                              onPressed: _nextMatch,
                            ),
                          ],
                        )
                      : null,
                ),
              )
            : Text(
                widget.file.name,
                style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
              ),
        actions: [
          IconButton(
            icon: Icon(_isSearching ? Icons.close_rounded : Icons.search_rounded),
            tooltip: _isSearching ? 'Close search' : 'Search text',
            onPressed: () {
              setState(() {
                _isSearching = !_isSearching;
                if (!_isSearching) {
                  _searchController.clear();
                  _searchQuery = '';
                  _searchMatches.clear();
                }
              });
            },
          ),
          IconButton(
            icon: const Icon(Icons.share_rounded),
            tooltip: 'Share',
            onPressed: _shareFile,
          ),
          PopupMenuButton<String>(
            icon: const Icon(Icons.more_vert_rounded),
            onSelected: (val) {
              if (val == 'info') _showFileDetailsSheet();
              if (val == 'external') _openExternal();
              if (val == 'jump') _showPageJumpDialog();
            },
            itemBuilder: (_) => [
              if (_totalPages > 1)
                const PopupMenuItem(value: 'jump', child: Text('Jump to page...')),
              const PopupMenuItem(value: 'external', child: Text('Open in external app')),
              const PopupMenuItem(value: 'info', child: Text('File Details')),
            ],
          ),
        ],
      ),
      body: _buildContent(theme, isDark),
      bottomNavigationBar: _totalPages > 1
          ? Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                color: isDark ? const Color(0xFF131822) : Colors.white,
                border: Border(
                  top: BorderSide(
                    color: isDark ? Colors.white10 : Colors.black12,
                  ),
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  IconButton(
                    icon: const Icon(Icons.chevron_left_rounded),
                    onPressed: _currentPage > 1
                        ? () => setState(() => _currentPage--)
                        : null,
                  ),
                  SpringInteractiveScale(
                    scaleDownFactor: 0.95,
                    onTap: _showPageJumpDialog,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                      decoration: BoxDecoration(
                        color: isDark ? const Color(0xFF1A2230) : const Color(0xFFEDF2F7),
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Text(
                        'Page $_currentPage of $_totalPages',
                        style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.chevron_right_rounded),
                    onPressed: _currentPage < _totalPages
                        ? () => setState(() => _currentPage++)
                        : null,
                  ),
                ],
              ),
            )
          : null,
    );
  }

  Widget _buildContent(ThemeData theme, bool isDark) {
    if (_contentError != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.error_outline_rounded, color: Colors.redAccent, size: 44),
              const SizedBox(height: 12),
              Text(_contentError!, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: _openExternal,
                icon: const Icon(Icons.open_in_new_rounded),
                label: const Text('Try External App'),
              ),
            ],
          ),
        ),
      );
    }

    if (_isLoadingContent) {
      return const Center(child: CircularProgressIndicator.adaptive());
    }

    final cat = widget.file.category;

    if (cat == MediaCategory.image) {
      return Center(
        child: InteractiveViewer(
          minScale: 0.8,
          maxScale: 5.0,
          child: Image.file(File(widget.file.path)),
        ),
      );
    }

    if (cat == MediaCategory.video) {
      return Center(
        child: ChatyVideoPlayerWidget(
          videoPath: widget.file.path,
          title: widget.file.name,
          autoPlay: true,
        ),
      );
    }

    if (cat == MediaCategory.pdf || cat == MediaCategory.document) {
      return _buildTextOrPdfView(isDark);
    }

    // Office / Archive / Unsupported fallback
    return _buildFallbackView(theme, isDark);
  }

  Widget _buildTextOrPdfView(bool isDark) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Container(
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF151B26) : Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isDark ? Colors.white.withValues(alpha: 0.08) : Colors.black.withValues(alpha: 0.06),
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: isDark ? 0.3 : 0.04),
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        padding: const EdgeInsets.all(20),
        child: SelectableText(
          _textContent.isNotEmpty ? _textContent : 'Document is empty.',
          style: TextStyle(
            fontSize: 14,
            height: 1.6,
            fontFamily: widget.file.extension == 'json' || widget.file.extension == 'csv' ? 'monospace' : null,
            color: isDark ? const Color(0xFFE2E8F0) : const Color(0xFF1E293B),
          ),
        ),
      ),
    );
  }

  Widget _buildFallbackView(ThemeData theme, bool isDark) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
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
              child: Center(
                child: ChatyMediaIcon(
                  icon: widget.file.category == MediaCategory.archive ? ChatyIconType.archive : ChatyIconType.document,
                  size: 38,
                  color: theme.colorScheme.primary,
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              widget.file.name,
              textAlign: TextAlign.center,
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 6),
            Text(
              '${widget.file.humanReadableSize} • ${widget.file.mimeType}',
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
            const SizedBox(height: 24),
            Text(
              'This document format is best viewed with an external application or productivity suite.',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium?.copyWith(color: isDark ? Colors.white70 : Colors.black87),
            ),
            const SizedBox(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                OutlinedButton.icon(
                  onPressed: _openExternal,
                  icon: const Icon(Icons.open_in_new_rounded),
                  label: const Text('Open External'),
                ),
                const SizedBox(width: 14),
                FilledButton.icon(
                  onPressed: _shareFile,
                  icon: const Icon(Icons.share_rounded),
                  label: const Text('Share File'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
