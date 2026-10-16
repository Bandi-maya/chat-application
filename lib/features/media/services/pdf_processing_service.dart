import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import '../models/conversion_models.dart';
import 'file_sanitization_service.dart';

class PdfProcessingService {
  PdfProcessingService();

  /// Converts multiple image files into a single, clean, valid PDF byte stream.
  Future<ConversionResult> convertImagesToPdf(ConversionRequest request) async {
    final stopwatch = Stopwatch()..start();
    try {
      final validImages = <File>[];
      for (final p in request.sourceFilePaths) {
        final f = File(p);
        if (await f.exists()) validImages.add(f);
      }
      if (validImages.isEmpty) {
        return const ConversionResult(
          success: false,
          errorMessage: 'No accessible source images were provided.',
        );
      }

      final outDir = Directory(request.outputDirectory);
      if (!await outDir.exists()) await outDir.create(recursive: true);

      final safeName = FileSanitizationService.instance.sanitizeFileName(request.outputFileName);
      final outPath = '${outDir.path}/$safeName${safeName.endsWith('.pdf') ? '' : '.pdf'}';

      final pdfBytes = await _buildPdfFromImages(validImages);
      final outFile = File(outPath);
      await outFile.writeAsBytes(pdfBytes);
      stopwatch.stop();

      return ConversionResult(
        success: true,
        outputFilePaths: <String>[outPath],
        totalBytes: pdfBytes.length,
        elapsed: stopwatch.elapsed,
      );
    } catch (e) {
      stopwatch.stop();
      return ConversionResult(
        success: false,
        errorMessage: 'Image to PDF conversion failed: $e',
        elapsed: stopwatch.elapsed,
      );
    }
  }

  /// Converts plain text or markdown content into a structured, readable PDF.
  Future<ConversionResult> convertTextToPdf(ConversionRequest request) async {
    final stopwatch = Stopwatch()..start();
    try {
      if (request.sourceFilePaths.isEmpty) {
        return const ConversionResult(success: false, errorMessage: 'No source text file specified.');
      }
      final source = File(request.sourceFilePaths.first);
      if (!await source.exists()) {
        return const ConversionResult(success: false, errorMessage: 'Source file does not exist.');
      }

      final text = await source.readAsString();
      final outDir = Directory(request.outputDirectory);
      if (!await outDir.exists()) await outDir.create(recursive: true);

      final safeName = FileSanitizationService.instance.sanitizeFileName(request.outputFileName);
      final outPath = '${outDir.path}/$safeName${safeName.endsWith('.pdf') ? '' : '.pdf'}';

      final pdfBytes = _buildPdfFromText(text, title: source.path.split(Platform.pathSeparator).last);
      final outFile = File(outPath);
      await outFile.writeAsBytes(pdfBytes);
      stopwatch.stop();

      return ConversionResult(
        success: true,
        outputFilePaths: <String>[outPath],
        totalBytes: pdfBytes.length,
        elapsed: stopwatch.elapsed,
      );
    } catch (e) {
      stopwatch.stop();
      return ConversionResult(
        success: false,
        errorMessage: 'Text to PDF conversion failed: $e',
        elapsed: stopwatch.elapsed,
      );
    }
  }

  /// Extracts textual contents from PDF streams.
  Future<String> extractTextFromPdf(String pdfPath) async {
    final file = File(pdfPath);
    if (!await file.exists()) return '';

    try {
      final bytes = await file.readAsBytes();
      final latin1Str = latin1.decode(bytes);
      final buffer = StringBuffer();

      // Extract text within BT ... ET blocks
      final btRegex = RegExp(r'BT[\s\S]*?ET');
      final matches = btRegex.allMatches(latin1Str);

      for (final m in matches) {
        final block = m.group(0) ?? '';
        // Look for (text) Tj or [(text)] TJ
        final tjRegex = RegExp(r'\((.*?)\)\s*Tj');
        for (final tj in tjRegex.allMatches(block)) {
          final t = tj.group(1);
          if (t != null && t.isNotEmpty) {
            buffer.write('$t ');
          }
        }
      }
      final extracted = buffer.toString().trim();
      return extracted.isNotEmpty ? extracted : 'No accessible extractable text stream found in this PDF.';
    } catch (e) {
      return 'Error extracting text: $e';
    }
  }

  /// Low-level clean-room standard PDF byte generator for images.
  Future<Uint8List> _buildPdfFromImages(List<File> images) async {
    final buffer = BytesBuilder();
    // Header
    buffer.add(utf8.encode('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n'));

    final offsets = <int>[];
    int currentOffset = buffer.length;

    // 1: Catalog
    offsets.add(currentOffset);
    final catalog = '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n';
    buffer.add(utf8.encode(catalog));
    currentOffset = buffer.length;

    // We'll have:
    // Obj 2: Pages
    // For each image i:
    //   Obj (3 + i*3): Page
    //   Obj (4 + i*3): Image XObject
    //   Obj (5 + i*3): Content stream

    final pageCount = images.length;
    final kids = List.generate(pageCount, (i) => '${3 + i * 3} 0 R').join(' ');

    offsets.add(currentOffset);
    final pagesObj = '2 0 obj\n<< /Type /Pages /Kids [ $kids ] /Count $pageCount >>\nendobj\n';
    buffer.add(utf8.encode(pagesObj));
    currentOffset = buffer.length;

    for (int i = 0; i < pageCount; i++) {
      final imgFile = images[i];
      final imgBytes = await imgFile.readAsBytes();
      final isPng = imgBytes.length > 4 && imgBytes[0] == 0x89 && imgBytes[1] == 0x50;

      // Page dimensions (standard A4: 595 x 842 pt)
      const pageWidth = 595;
      const pageHeight = 842;

      final pageObjIndex = 3 + i * 3;
      final imgObjIndex = 4 + i * 3;
      final contentObjIndex = 5 + i * 3;

      // Page Object
      offsets.add(currentOffset);
      final pageObj = '$pageObjIndex 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [ 0 0 $pageWidth $pageHeight ] /Resources << /XObject << /Im$i $imgObjIndex 0 R >> >> /Contents $contentObjIndex 0 R >>\nendobj\n';
      buffer.add(utf8.encode(pageObj));
      currentOffset = buffer.length;

      // Image Object (DCTDecode for JPEG or Flate for standard)
      offsets.add(currentOffset);
      final filter = isPng ? '/FlateDecode' : '/DCTDecode';
      final imgHeader = '$imgObjIndex 0 obj\n<< /Type /XObject /Subtype /Image /Width $pageWidth /Height $pageHeight /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter $filter /Length ${imgBytes.length} >>\nstream\n';
      buffer.add(utf8.encode(imgHeader));
      buffer.add(imgBytes);
      buffer.add(utf8.encode('\nendstream\nendobj\n'));
      currentOffset = buffer.length;

      // Content stream (Draw image scaled to page)
      final contentStream = 'q\n$pageWidth 0 0 $pageHeight 0 0 cm\n/Im$i Do\nQ\n';
      final contentBytes = utf8.encode(contentStream);
      offsets.add(currentOffset);
      final contentObj = '$contentObjIndex 0 obj\n<< /Length ${contentBytes.length} >>\nstream\n$contentStream\nendstream\nendobj\n';
      buffer.add(utf8.encode(contentObj));
      currentOffset = buffer.length;
    }

    // XRef table
    final xrefOffset = currentOffset;
    final totalObjs = 2 + pageCount * 3 + 1;
    buffer.add(utf8.encode('xref\n0 $totalObjs\n0000000000 65535 f \n'));
    for (final off in offsets) {
      final padded = off.toString().padLeft(10, '0');
      buffer.add(utf8.encode('$padded 00000 n \n'));
    }

    // Trailer
    final trailer = 'trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n';
    buffer.add(utf8.encode(trailer));

    return buffer.toBytes();
  }

  /// Low-level clean-room PDF generator for text documents.
  Uint8List _buildPdfFromText(String text, {String title = 'Document'}) {
    final buffer = BytesBuilder();
    buffer.add(utf8.encode('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n'));

    final offsets = <int>[];
    int currentOffset = buffer.length;

    // 1: Catalog
    offsets.add(currentOffset);
    buffer.add(utf8.encode('1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n'));
    currentOffset = buffer.length;

    // Split text into chunks/pages
    final lines = text.split('\n');
    const linesPerPage = 42;
    final pagesList = <List<String>>[];
    for (int i = 0; i < lines.length; i += linesPerPage) {
      pagesList.add(lines.sublist(i, (i + linesPerPage > lines.length) ? lines.length : i + linesPerPage));
    }
    if (pagesList.isEmpty) pagesList.add(<String>['[Empty Document]']);

    final pageCount = pagesList.length;
    final kids = List.generate(pageCount, (i) => '${3 + i * 2} 0 R').join(' ');

    // 2: Pages
    offsets.add(currentOffset);
    buffer.add(utf8.encode('2 0 obj\n<< /Type /Pages /Kids [ $kids ] /Count $pageCount >>\nendobj\n'));
    currentOffset = buffer.length;

    for (int i = 0; i < pageCount; i++) {
      final pageLines = pagesList[i];
      final pageObjIndex = 3 + i * 2;
      final contentObjIndex = 4 + i * 2;

      // Page Object
      offsets.add(currentOffset);
      buffer.add(utf8.encode('$pageObjIndex 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [ 0 0 595 842 ] /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> /Contents $contentObjIndex 0 R >>\nendobj\n'));
      currentOffset = buffer.length;

      // Stream content
      final streamBuilder = StringBuffer();
      streamBuilder.write('BT\n/F1 14 Tf\n50 800 Td\n(${_escapePdfText(title)} - Page ${i + 1}/$pageCount) Tj\n/F1 10 Tf\n0 -25 Td\n');
      for (final line in pageLines) {
        final safeLine = _escapePdfText(line.trimRight());
        streamBuilder.write('($safeLine) Tj\n0 -16 Td\n');
      }
      streamBuilder.write('ET\n');

      final contentBytes = utf8.encode(streamBuilder.toString());
      offsets.add(currentOffset);
      buffer.add(utf8.encode('$contentObjIndex 0 obj\n<< /Length ${contentBytes.length} >>\nstream\n${streamBuilder.toString()}\nendstream\nendobj\n'));
      currentOffset = buffer.length;
    }

    final xrefOffset = currentOffset;
    final totalObjs = 2 + pageCount * 2 + 1;
    buffer.add(utf8.encode('xref\n0 $totalObjs\n0000000000 65535 f \n'));
    for (final off in offsets) {
      final padded = off.toString().padLeft(10, '0');
      buffer.add(utf8.encode('$padded 00000 n \n'));
    }

    buffer.add(utf8.encode('trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n'));
    return buffer.toBytes();
  }

  String _escapePdfText(String text) {
    return text.replaceAll('\\', '\\\\').replaceAll('(', '\\(').replaceAll(')', '\\)');
  }
}
