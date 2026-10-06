import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

/// Production-ready application sharing service adhering to Rule 22.
///
/// Features:
/// - Environment-configurable Play Store URL via [CHATY_PLAY_STORE_URL]
/// - Native platform share sheet invocation via `share_plus`
/// - Defensive error handling for canceled shares, unsupported surfaces, and errors
/// - Production-ready text payload with Chaty branding and store link
class ChatyShareService {
  ChatyShareService._();

  /// Default Play Store link, configurable at compile/build time via:
  /// `--dart-define=CHATY_PLAY_STORE_URL=https://...`
  static const String playStoreUrl = String.fromEnvironment(
    'CHATY_PLAY_STORE_URL',
    defaultValue: 'https://play.google.com/store/apps/details?id=com.example.chat',
  );

  static const String appName = 'Chaty';

  /// Standard sharing text payload with download URL and fallback description.
  static String get shareMessage =>
      'Connect with me on $appName — private, fast, and customizable messaging!\n\nDownload $appName here: $playStoreUrl';

  /// Invokes the native platform share sheet.
  ///
  /// Returns `true` if the share sheet was invoked successfully or completed.
  static Future<bool> shareApp(
    BuildContext context, {
    Rect? sharePositionOrigin,
  }) async {
    try {
      final box = context.findRenderObject() as RenderBox?;
      final origin = sharePositionOrigin ??
          (box != null && box.hasSize
              ? box.localToGlobal(Offset.zero) & box.size
              : null);

      final result = await Share.share(
        shareMessage,
        subject: 'Join me on $appName',
        sharePositionOrigin: origin,
      );

      if (kDebugMode) {
        debugPrint('ChatyShareService: share completed with status ${result.status}');
      }
      return true;
    } catch (e, stack) {
      if (kDebugMode) {
        debugPrint('ChatyShareService error: $e\n$stack');
      }
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Could not open share menu: $e'),
            duration: const Duration(seconds: 3),
          ),
        );
      }
      return false;
    }
  }
}
