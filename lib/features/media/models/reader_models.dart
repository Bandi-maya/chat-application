class DocumentOutlineNode {
  final String title;
  final int pageNumber;
  final int depth;
  final List<DocumentOutlineNode> children;

  const DocumentOutlineNode({
    required this.title,
    required this.pageNumber,
    this.depth = 0,
    this.children = const <DocumentOutlineNode>[],
  });
}

class DocumentBookmark {
  final String id;
  final int pageNumber;
  final String label;
  final DateTime createdAt;

  const DocumentBookmark({
    required this.id,
    required this.pageNumber,
    required this.label,
    required this.createdAt,
  });
}

class DocumentSearchMatch {
  final int pageNumber;
  final int matchIndex;
  final String snippet;
  final int startOffset;
  final int endOffset;

  const DocumentSearchMatch({
    required this.pageNumber,
    required this.matchIndex,
    required this.snippet,
    required this.startOffset,
    required this.endOffset,
  });
}

enum ReaderZoomMode {
  fitWidth,
  fitPage,
  manual,
}
