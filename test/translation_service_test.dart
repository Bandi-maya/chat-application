import 'package:flutter_test/flutter_test.dart';
import 'package:chat/core/services/translation_service.dart';

void main() {
  group('TranslationService (SEC-02 Compliance & Script Heuristics)', () {
    late TranslationService service;

    setUp(() {
      service = TranslationService();
    });

    test('Supported languages catalog contains standard global languages', () {
      final langs = service.supportedLanguages;
      expect(langs.length, greaterThanOrEqualTo(10));
      final codes = langs.map((l) => l.code).toSet();
      expect(codes, containsAll(['en', 'es', 'fr', 'de', 'ar', 'zh', 'hi', 'pt', 'ru', 'ja', 'it']));
    });

    test('Script detection heuristics accurately classify non-Latin scripts', () {
      expect(service.detectLanguage('مرحبا كيف حالك'), equals('ar')); // Arabic
      expect(service.detectLanguage('नमस्ते आप कैसे हैं'), equals('hi')); // Devanagari/Hindi
      expect(service.detectLanguage('你好世界'), equals('zh')); // CJK/Chinese
      expect(service.detectLanguage('こんにちは'), equals('ja')); // Japanese Hiragana/Katakana
      expect(service.detectLanguage('Привет мир'), equals('ru')); // Cyrillic/Russian
    });

    test('Offline dictionary fallback translates common greetings without network exposure', () async {
      // Testing on-device offline translation preserves SEC-02
      final resultEs = await service.translate(
        text: 'hello',
        targetLanguageCode: 'es',
      );
      expect(resultEs.translatedText.toLowerCase(), contains('hola'));
      expect(resultEs.targetLanguage, equals('es'));

      final resultFr = await service.translate(
        text: 'thank you',
        targetLanguageCode: 'fr',
      );
      expect(resultFr.translatedText.toLowerCase(), contains('merci'));

      final resultDe = await service.translate(
        text: 'good morning',
        targetLanguageCode: 'de',
      );
      expect(resultDe.translatedText.toLowerCase(), contains('guten morgen'));
    });

    test('Translation result caching prevents redundant translations', () async {
      final res1 = await service.translate(
        text: 'hello',
        targetLanguageCode: 'es',
      );
      expect(res1.isFromCache, isFalse);

      final res2 = await service.translate(
        text: 'hello',
        targetLanguageCode: 'es',
      );
      expect(res2.isFromCache, isTrue);
      expect(res2.translatedText, equals(res1.translatedText));
    });

    test('Empty text returns immediately without processing', () async {
      final result = await service.translate(
        text: '   ',
        targetLanguageCode: 'es',
      );
      expect(result.translatedText.trim(), isEmpty);
    });
  });
}
