import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'theme_config.dart';

/// Manages Chaty Themes: safe JSON import/export, local custom themes, and resets.
class ChatyThemeManager {
  ChatyThemeManager._();

  static const int schemaVersion = 1;
  static const int maxImportSizeBytes = 1024 * 1024; // 1 MB limit

  /// Safely exports a ThemeConfig to a structured JSON string.
  static String exportTheme(ThemeConfig theme) {
    final payload = <String, dynamic>{
      'schemaVersion': schemaVersion,
      'exportedAt': DateTime.now().toIso8601String(),
      'theme': theme.toMap(),
    };
    return const JsonEncoder.withIndent('  ').convert(payload);
  }

  /// Safely validates and parses an imported JSON string into a ThemeConfig.
  /// Rejects oversized, malicious, invalid, or unsupported payload shapes.
  static ThemeConfig validateAndImportTheme(String jsonString) {
    if (jsonString.isEmpty) {
      throw const FormatException('Empty theme file provided.');
    }
    if (jsonString.length > maxImportSizeBytes) {
      throw const FormatException('Theme file exceeds maximum allowed size (1 MB).');
    }

    dynamic decoded;
    try {
      decoded = jsonDecode(jsonString);
    } catch (_) {
      throw const FormatException('Invalid JSON payload.');
    }

    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Expected a JSON object at root.');
    }

    final ver = decoded['schemaVersion'];
    if (ver == null || (ver is int && ver > schemaVersion)) {
      throw const FormatException(
        'Unsupported or missing theme schema version.',
      );
    }

    final rawTheme = decoded['theme'];
    if (rawTheme is! Map<String, dynamic>) {
      throw const FormatException('Missing theme object in import payload.');
    }

    final theme = ThemeConfig.fromMap(rawTheme);

    // Validate contrast and key token validity
    if (theme.primaryTextColor.toARGB32() == theme.backgroundColor.toARGB32() ||
        theme.hasContrastIssue) {
      throw const FormatException(
        'Imported theme fails readability or accessibility contrast validation.',
      );
    }

    return theme;
  }

  /// Saves a theme file to the user's exported themes directory.
  static Future<File> saveThemeToFile(ThemeConfig theme) async {
    final dir = await getApplicationDocumentsDirectory();
    final safeName = theme.name
        .replaceAll(RegExp(r'[^a-zA-Z0-9_]'), '_')
        .toLowerCase();
    final file = File(
      '${dir.path}/chaty_theme_${safeName}_${DateTime.now().millisecondsSinceEpoch}.json',
    );
    final jsonStr = exportTheme(theme);
    return file.writeAsString(jsonStr);
  }
}
