enum ConversionType {
  imagesToPdf,
  pdfToImages,
  textToPdf,
  pdfToText,
  pdfMerge,
  pdfSplit,
  pdfRotate,
  pdfExtractPages,
}

enum ConversionStatus {
  idle,
  processing,
  completed,
  cancelled,
  failed,
}

class ConversionRequest {
  final String id;
  final ConversionType type;
  final List<String> sourceFilePaths;
  final String outputDirectory;
  final String outputFileName;
  final Map<String, dynamic> options;

  const ConversionRequest({
    required this.id,
    required this.type,
    required this.sourceFilePaths,
    required this.outputDirectory,
    required this.outputFileName,
    this.options = const <String, dynamic>{},
  });
}

class ConversionProgress {
  final double fraction;
  final String currentOperation;
  final int completedItems;
  final int totalItems;

  const ConversionProgress({
    required this.fraction,
    required this.currentOperation,
    this.completedItems = 0,
    this.totalItems = 0,
  });
}

class ConversionResult {
  final bool success;
  final List<String> outputFilePaths;
  final int totalBytes;
  final Duration elapsed;
  final String? errorMessage;

  const ConversionResult({
    required this.success,
    this.outputFilePaths = const <String>[],
    this.totalBytes = 0,
    this.elapsed = Duration.zero,
    this.errorMessage,
  });
}
