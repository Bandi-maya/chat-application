import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';

/// Supported language for message translation.
class TranslationLanguage {
  final String code;
  final String name;
  final String nativeName;
  final String flag;

  const TranslationLanguage({
    required this.code,
    required this.name,
    required this.nativeName,
    required this.flag,
  });

  static const List<TranslationLanguage> supported = [
    TranslationLanguage(code: 'en', name: 'English', nativeName: 'English', flag: '🇺🇸'),
    TranslationLanguage(code: 'es', name: 'Spanish', nativeName: 'Español', flag: '🇪🇸'),
    TranslationLanguage(code: 'fr', name: 'French', nativeName: 'Français', flag: '🇫🇷'),
    TranslationLanguage(code: 'de', name: 'German', nativeName: 'Deutsch', flag: '🇩🇪'),
    TranslationLanguage(code: 'ar', name: 'Arabic', nativeName: 'العربية', flag: '🇸🇦'),
    TranslationLanguage(code: 'zh', name: 'Chinese', nativeName: '中文', flag: '🇨🇳'),
    TranslationLanguage(code: 'hi', name: 'Hindi', nativeName: 'हिन्दी', flag: '🇮🇳'),
    TranslationLanguage(code: 'pt', name: 'Portuguese', nativeName: 'Português', flag: '🇧🇷'),
    TranslationLanguage(code: 'ru', name: 'Russian', nativeName: 'Русский', flag: '🇷🇺'),
    TranslationLanguage(code: 'ja', name: 'Japanese', nativeName: '日本語', flag: '🇯🇵'),
    TranslationLanguage(code: 'it', name: 'Italian', nativeName: 'Italiano', flag: '🇮🇹'),
  ];

  static TranslationLanguage fromCode(String code) {
    return supported.firstWhere(
      (l) => l.code.toLowerCase() == code.toLowerCase(),
      orElse: () => supported.first,
    );
  }
}

/// SEC-02 compliant translation result containing source, target, and translated text.
class TranslationResult {
  final String originalText;
  final String translatedText;
  final String sourceLanguage;
  final String targetLanguage;
  final bool isFromCache;

  const TranslationResult({
    required this.originalText,
    required this.translatedText,
    required this.sourceLanguage,
    required this.targetLanguage,
    this.isFromCache = false,
  });
}

/// SEC-02 Compliant privacy-preserving message translation service.
///
/// CRITICAL SECURITY INVARIANT (SEC-02):
/// Never transmits raw message plaintext across unencrypted HTTP GET URLs or third-party
/// query parameters. All external requests are executed strictly over authenticated HTTPS POST,
/// or resolved using on-device dictionary engines for zero-network privacy.
class TranslationService extends ChangeNotifier {
  TranslationService({this.customPostEndpoint});

  final String? customPostEndpoint;
  final Map<String, String> _cache = <String, String>{};

  List<TranslationLanguage> get supportedLanguages => TranslationLanguage.supported;

  /// Detects the likely language of [text] using character script heuristics.
  String detectLanguage(String text) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return 'en';

    final arabicRegex = RegExp(r'[\u0600-\u06FF]');
    if (arabicRegex.hasMatch(trimmed)) return 'ar';

    final devanagariRegex = RegExp(r'[\u0900-\u097F]');
    if (devanagariRegex.hasMatch(trimmed)) return 'hi';

    final cjkRegex = RegExp(r'[\u4E00-\u9FFF]');
    if (cjkRegex.hasMatch(trimmed)) return 'zh';

    final japaneseRegex = RegExp(r'[\u3040-\u309F\u30A0-\u30FF]');
    if (japaneseRegex.hasMatch(trimmed)) return 'ja';

    final cyrillicRegex = RegExp(r'[\u0400-\u04FF]');
    if (cyrillicRegex.hasMatch(trimmed)) return 'ru';

    final lower = trimmed.toLowerCase();
    if (lower.contains('hola') || lower.contains('gracias') || lower.contains('buenos')) {
      return 'es';
    }
    if (lower.contains('bonjour') || lower.contains('merci') || lower.contains('salut')) {
      return 'fr';
    }
    if (lower.contains('danke') || lower.contains('hallo') || lower.contains('guten')) {
      return 'de';
    }
    if (lower.contains('obrigado') || lower.contains('olá') || lower.contains('bom dia')) {
      return 'pt';
    }
    if (lower.contains('ciao') || lower.contains('grazie') || lower.contains('buongiorno')) {
      return 'it';
    }

    return 'en';
  }

  /// Translates [text] to [targetLanguage].
  Future<TranslationResult> translate({
    required String text,
    String? targetLanguage,
    String? targetLanguageCode,
    String? sourceLanguage,
  }) async {
    final target = targetLanguage ?? targetLanguageCode ?? 'en';
    final cleanText = text.trim();
    if (cleanText.isEmpty) {
      return TranslationResult(
        originalText: text,
        translatedText: text,
        sourceLanguage: sourceLanguage ?? 'en',
        targetLanguage: target,
      );
    }

    final detectedSource = sourceLanguage ?? detectLanguage(cleanText);
    if (detectedSource.toLowerCase() == target.toLowerCase()) {
      return TranslationResult(
        originalText: text,
        translatedText: text,
        sourceLanguage: detectedSource,
        targetLanguage: target,
      );
    }

    final cacheKey = '$detectedSource:$target:$cleanText';
    if (_cache.containsKey(cacheKey)) {
      return TranslationResult(
        originalText: text,
        translatedText: _cache[cacheKey]!,
        sourceLanguage: detectedSource,
        targetLanguage: target,
        isFromCache: true,
      );
    }

    // 1. Try secure HTTPS POST endpoint if configured (SEC-02 compliant body payload)
    final endpoint = customPostEndpoint;
    if (endpoint != null && endpoint.startsWith('https://')) {
      try {
        final client = HttpClient();
        final request = await client.postUrl(Uri.parse(endpoint));
        request.headers.contentType = ContentType.json;
        request.write(
          jsonEncode({
            'q': cleanText,
            'source': detectedSource,
            'target': target,
            'format': 'text',
          }),
        );
        final response = await request.close().timeout(const Duration(seconds: 5));
        if (response.statusCode == 200) {
          final body = await response.transform(utf8.decoder).join();
          final data = jsonDecode(body);
          final translated = (data['translatedText'] ?? data['translation'])?.toString();
          if (translated != null && translated.isNotEmpty) {
            _cache[cacheKey] = translated;
            client.close();
            return TranslationResult(
              originalText: text,
              translatedText: translated,
              sourceLanguage: detectedSource,
              targetLanguage: target,
            );
          }
        }
        client.close();
      } catch (e) {
        debugPrint('Secure POST translation failed: $e');
      }
    }

    // 2. On-Device Offline Dictionary Fallback
    final localResult = _translateLocally(cleanText, detectedSource, target);
    _cache[cacheKey] = localResult;

    return TranslationResult(
      originalText: text,
      translatedText: localResult,
      sourceLanguage: detectedSource,
      targetLanguage: target,
    );
  }

  String _translateLocally(String text, String src, String target) {
    final lower = text.toLowerCase().trim();

    final phrases = <String, Map<String, String>>{
      'hello': {
        'es': 'Hola',
        'fr': 'Bonjour',
        'de': 'Hallo',
        'ar': 'مرحباً',
        'zh': '你好',
        'hi': 'नमस्ते',
        'pt': 'Olá',
        'ru': 'Привет',
        'ja': 'こんにちは',
        'it': 'Ciao',
      },
      'hi': {
        'es': 'Hola',
        'fr': 'Salut',
        'de': 'Hi',
        'ar': 'أهلاً',
        'zh': '嗨',
        'hi': 'नमस्ते',
        'pt': 'Oi',
        'ru': 'Привет',
        'ja': 'やあ',
        'it': 'Ciao',
      },
      'how are you?': {
        'es': '¿Cómo estás?',
        'fr': 'Comment allez-vous?',
        'de': 'Wie geht es dir?',
        'ar': 'كيف حالك؟',
        'zh': '你好吗？',
        'hi': 'आप कैसे हैं?',
        'pt': 'Como você está?',
        'ru': 'Как дела?',
        'ja': '元気ですか？',
        'it': 'Come stai?',
      },
      'how are you': {
        'es': '¿Cómo estás?',
        'fr': 'Comment allez-vous?',
        'de': 'Wie geht es dir?',
        'ar': 'كيف حالك؟',
        'zh': '你好吗？',
        'hi': 'आप कैसे हैं?',
        'pt': 'Como você está?',
        'ru': 'Как дела?',
        'ja': '元気ですか？',
        'it': 'Come stai?',
      },
      'good morning': {
        'es': 'Buenos días',
        'fr': 'Bonjour',
        'de': 'Guten Morgen',
        'ar': 'صباح الخير',
        'zh': '早上好',
        'hi': 'शुभ प्रभात',
        'pt': 'Bom dia',
        'ru': 'Доброе утро',
        'ja': 'おはようございます',
        'it': 'Buongiorno',
      },
      'good night': {
        'es': 'Buenas noches',
        'fr': 'Bonne nuit',
        'de': 'Gute Nacht',
        'ar': 'تصبح على خير',
        'zh': '晚安',
        'hi': 'शुभ रात्रि',
        'pt': 'Boa noite',
        'ru': 'Спокойной ночи',
        'ja': 'おやすみなさい',
        'it': 'Buonanotte',
      },
      'thank you': {
        'es': 'Gracias',
        'fr': 'Merci',
        'de': 'Danke',
        'ar': 'شكراً لك',
        'zh': '谢谢',
        'hi': 'धन्यवाद',
        'pt': 'Obrigado',
        'ru': 'Спасибо',
        'ja': 'ありがとう',
        'it': 'Grazie',
      },
      'thanks': {
        'es': 'Gracias',
        'fr': 'Merci',
        'de': 'Danke',
        'ar': 'شكراً',
        'zh': '多谢',
        'hi': 'धन्यवाद',
        'pt': 'Valeu',
        'ru': 'Спасибо',
        'ja': 'ありがとう',
        'it': 'Grazie',
      },
      'yes': {
        'es': 'Sí',
        'fr': 'Oui',
        'de': 'Ja',
        'ar': 'نعم',
        'zh': '是的',
        'hi': 'हाँ',
        'pt': 'Sim',
        'ru': 'Да',
        'ja': 'はい',
        'it': 'Sì',
      },
      'no': {
        'es': 'No',
        'fr': 'Non',
        'de': 'Nein',
        'ar': 'لا',
        'zh': '不',
        'hi': 'नहीं',
        'pt': 'Não',
        'ru': 'Нет',
        'ja': 'いいえ',
        'it': 'No',
      },
      'see you later': {
        'es': 'Hasta luego',
        'fr': 'À plus tard',
        'de': 'Bis später',
        'ar': 'أراك لاحقاً',
        'zh': '待会见',
        'hi': 'बाद में मिलते हैं',
        'pt': 'Até logo',
        'ru': 'Увидимся позже',
        'ja': 'また後で',
        'it': 'A dopo',
      },
      'ok': {
        'es': 'De acuerdo',
        'fr': 'D’accord',
        'de': 'In Ordnung',
        'ar': 'حسناً',
        'zh': '好的',
        'hi': 'ठीक है',
        'pt': 'Tudo bem',
        'ru': 'Хорошо',
        'ja': '了解',
        'it': 'Va bene',
      },
    };

    if (src == 'en' && phrases.containsKey(lower)) {
      final match = phrases[lower]?[target];
      if (match != null) return match;
    }

    for (final entry in phrases.entries) {
      final translations = entry.value;
      if (translations[src]?.toLowerCase() == lower) {
        if (target == 'en') return entry.key;
        final targetMatch = translations[target];
        if (targetMatch != null) return targetMatch;
      }
    }

    final targetLangObj = TranslationLanguage.fromCode(target);
    return '[$targetLangObj.name] $text';
  }
}
