import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../../../ui/core/design_system/design_system.dart';
import '../../models/conversion_models.dart';
import '../../models/media_file_descriptor.dart';
import '../../services/file_type_detector.dart';
import '../../services/media_library_service.dart';
import '../../services/pdf_processing_service.dart';
import '../document/document_reader_screen.dart';
import '../icons/chaty_media_icons.dart';

class PdfWorkspaceScreen extends StatefulWidget {
  const PdfWorkspaceScreen({super.key});

  @override
  State<PdfWorkspaceScreen> createState() => _PdfWorkspaceScreenState();
}

class _PdfWorkspaceScreenState extends State<PdfWorkspaceScreen> {
  final MediaLibraryService _library = MediaLibraryService();
  final PdfProcessingService _pdfService = PdfProcessingService();
  bool _isProcessing = false;
  String _processingStatus = '';

  @override
  void initState() {
    super.initState();
    _library.setFilter(MediaCategory.pdf);
    _library.refreshLibrary();
  }

  @override
  void dispose() {
    _library.dispose();
    super.dispose();
  }

  Future<void> _convertImagesToPdf() async {
    HapticFeedback.lightImpact();
    try {
      final picked = await FilePicker.pickFiles(
        type: FileType.image,
        allowMultiple: true,
      );
      if (picked.isEmpty) return;

      final paths = picked.map((f) => f.path).whereType<String>().toList();
      if (paths.isEmpty) return;

      setState(() {
        _isProcessing = true;
        _processingStatus = 'Converting ${paths.length} images to PDF...';
      });

      final docDir = await getApplicationDocumentsDirectory();
      final request = ConversionRequest(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        type: ConversionType.imagesToPdf,
        sourceFilePaths: paths,
        outputDirectory: docDir.path,
        outputFileName: 'Chaty_Images_${DateTime.now().millisecondsSinceEpoch}.pdf',
      );

      final result = await _pdfService.convertImagesToPdf(request);
      if (!mounted) return;
      setState(() => _isProcessing = false);

      if (result.success && result.outputFilePaths.isNotEmpty) {
        HapticFeedback.mediumImpact();
        await _library.refreshLibrary();
        _showSuccessSheet('Image to PDF Complete', result.outputFilePaths.first);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(result.errorMessage ?? 'Conversion failed.')),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isProcessing = false);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    }
  }

  Future<void> _convertTextToPdf() async {
    HapticFeedback.lightImpact();
    try {
      final picked = await FilePicker.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['txt', 'md', 'json', 'csv'],
      );
      if (picked.isEmpty || picked.first.path == null) return;

      final path = picked.first.path!;
      setState(() {
        _isProcessing = true;
        _processingStatus = 'Converting text to PDF...';
      });

      final docDir = await getApplicationDocumentsDirectory();
      final request = ConversionRequest(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        type: ConversionType.textToPdf,
        sourceFilePaths: [path],
        outputDirectory: docDir.path,
        outputFileName: 'Chaty_Document_${DateTime.now().millisecondsSinceEpoch}.pdf',
      );

      final result = await _pdfService.convertTextToPdf(request);
      if (!mounted) return;
      setState(() => _isProcessing = false);

      if (result.success && result.outputFilePaths.isNotEmpty) {
        HapticFeedback.mediumImpact();
        await _library.refreshLibrary();
        _showSuccessSheet('Text to PDF Complete', result.outputFilePaths.first);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(result.errorMessage ?? 'Conversion failed.')),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isProcessing = false);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    }
  }

  void _showSuccessSheet(String title, String outputPath) {
    final file = File(outputPath);
    final sizeStr = file.existsSync()
        ? '${(file.lengthSync() / 1024).toStringAsFixed(1)} KB'
        : '';
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
          children: [
            Container(
              width: 54,
              height: 54,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.green.withValues(alpha: 0.15),
              ),
              child: const Icon(Icons.check_circle_rounded, color: Colors.green, size: 32),
            ),
            const SizedBox(height: 12),
            Text(
              title,
              style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17),
            ),
            const SizedBox(height: 4),
            Text(
              '${outputPath.split(Platform.pathSeparator).last} • $sizeStr',
              style: const TextStyle(color: Colors.grey, fontSize: 12),
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () {
                      Navigator.pop(sheetCtx);
                      SharePlus.instance.share(ShareParams(files: [XFile(outputPath)]));
                    },
                    icon: const Icon(Icons.share_rounded),
                    label: const Text('Share'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: () async {
                      Navigator.pop(sheetCtx);
                      final stat = await file.stat();
                      final descriptor = MediaFileDescriptor(
                        id: outputPath,
                        name: outputPath.split(Platform.pathSeparator).last,
                        path: outputPath,
                        sizeBytes: stat.size,
                        mimeType: 'application/pdf',
                        category: MediaCategory.pdf,
                        modifiedAt: stat.modified,
                      );
                      if (!mounted) return;
                      Navigator.push(
                        context,
                        MaterialPageRoute<void>(
                          builder: (_) => DocumentReaderScreen(file: descriptor),
                        ),
                      );
                    },
                    icon: const Icon(Icons.remove_red_eye_rounded),
                    label: const Text('Open PDF'),
                  ),
                ),
              ],
            ),
          ],
        ),
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
        title: const Text('PDF Workspace & Tools', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
      ),
      body: Stack(
        children: [
          ListView(
            padding: const EdgeInsets.all(18),
            children: [
              // Fast Conversion Tools Grid
              Text('CONVERSION UTILITIES', style: theme.textTheme.labelMedium?.copyWith(letterSpacing: 1.1, fontWeight: FontWeight.w800, color: Colors.grey)),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: _toolCard(
                      icon: ChatyIconType.convert,
                      title: 'Images to PDF',
                      subtitle: 'Merge photos into document',
                      color: const Color(0xFF38BDF8),
                      isDark: isDark,
                      onTap: _convertImagesToPdf,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _toolCard(
                      icon: ChatyIconType.document,
                      title: 'Text to PDF',
                      subtitle: 'Format txt/md as PDF',
                      color: const Color(0xFF10B981),
                      isDark: isDark,
                      onTap: _convertTextToPdf,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Recent PDFs in App
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('YOUR PDF DOCUMENTS', style: theme.textTheme.labelMedium?.copyWith(letterSpacing: 1.1, fontWeight: FontWeight.w800, color: Colors.grey)),
                  IconButton(
                    icon: const Icon(Icons.refresh_rounded, size: 18),
                    onPressed: () => _library.refreshLibrary(),
                  ),
                ],
              ),
              const SizedBox(height: 8),

              ListenableBuilder(
                listenable: _library,
                builder: (context, _) {
                  final pdfs = _library.files.where((f) => f.category == MediaCategory.pdf).toList();
                  if (pdfs.isEmpty) {
                    return Container(
                      padding: const EdgeInsets.all(28),
                      decoration: BoxDecoration(
                        color: isDark ? const Color(0xFF161C26) : Colors.white,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: const Center(
                        child: Text('No PDFs stored in Chaty yet. Convert images or import one above!'),
                      ),
                    );
                  }

                  return ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: pdfs.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (context, idx) {
                      final item = pdfs[idx];
                      return SpringInteractiveScale(
                        scaleDownFactor: 0.98,
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute<void>(
                              builder: (_) => DocumentReaderScreen(file: item),
                            ),
                          );
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
                              Container(
                                width: 40,
                                height: 40,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: const Color(0xFFEF4444).withValues(alpha: 0.15),
                                ),
                                child: const Center(
                                  child: ChatyMediaIcon(
                                    icon: ChatyIconType.pdf,
                                    size: 20,
                                    color: Color(0xFFEF4444),
                                  ),
                                ),
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
                                      style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
                                    ),
                                    Text(
                                      '${item.humanReadableSize} • ${item.modifiedAt.toLocal().toString().split('.')[0]}',
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
                  );
                },
              ),
            ],
          ),

          // Processing progress overlay
          if (_isProcessing)
            Container(
              color: Colors.black54,
              child: Center(
                child: Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF1E2638) : Colors.white,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const CircularProgressIndicator.adaptive(),
                      const SizedBox(height: 16),
                      Text(_processingStatus, style: const TextStyle(fontWeight: FontWeight.w700)),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _toolCard({
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
}
