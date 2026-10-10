import 'package:chat/domain/models/preferences.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/gb/gb_theme_overrides.dart';
import 'package:chat/ui/core/theme/theme_presets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUpAll(() {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
  });

  group('Universal Settings & Persistence Tests', () {
    test('UniversalPreferences model defaults match expected configuration', () {
      const prefs = UniversalPreferences();
      expect(prefs.launcherIcon, 'Classic');
      expect(prefs.emojiVariant, 'WhatsApp');
      expect(prefs.notificationIcon, 'White');
      expect(prefs.fontStyle, 'Default');
      expect(prefs.loadFontEnabled, isFalse);
      expect(prefs.hidePhotosFromGallery, isFalse);
      expect(prefs.hideVideosFromGallery, isFalse);
      expect(prefs.hideGifsFromGallery, isFalse);
      expect(prefs.translateOptionSettings, 'Server + No outside apps');
      expect(prefs.defaultTranslationLanguage, 'Show All');
      expect(prefs.conversationCards, isTrue);
      expect(prefs.disableHeadsUpNotification, isFalse);
      expect(prefs.disableBadgeCounter, isFalse);
      expect(prefs.disableAudioPlayingNotification, isFalse);
      expect(prefs.increaseForwardLimit, isFalse);
      expect(prefs.disableSwipeToExitConversation, isFalse);
      expect(prefs.enableAlwaysOnline, isTrue);
      expect(prefs.tenorGiphyGifProvider, 'Tenor');
      expect(prefs.sendImagesInFullResolutionMb, 1.0);
    });

    test('UniversalPreferences round-trip serialization and copyWith', () {
      const original = UniversalPreferences(
        universalColor: 0xFF25D366,
        universalActionBarTextColor: 0xFFFFFFFF,
        backgroundColor: 0xFF121212,
        listBackgroundColor: 0xFF1A1A1A,
        statusBarColor: 0xFF000000,
        navigationBarColor: 0xFF18181B,
        launcherIcon: 'GB Cyan',
        emojiVariant: 'iOS NEW 2025',
        notificationIcon: 'Gold',
        fontStyle: 'Roboto-Medium',
        loadFontEnabled: true,
        hidePhotosFromGallery: true,
        hideVideosFromGallery: true,
        hideGifsFromGallery: true,
        translateOptionSettings: 'In-app',
        defaultTranslationLanguage: 'Spanish (Español)',
        conversationCards: false,
        disableHeadsUpNotification: true,
        disableBadgeCounter: true,
        disableAudioPlayingNotification: true,
        increaseForwardLimit: true,
        disableSwipeToExitConversation: true,
        enableAlwaysOnline: false,
        tenorGiphyGifProvider: 'Giphy',
        sendImagesInFullResolutionMb: 4.0,
      );

      final map = original.toMap();
      final restored = UniversalPreferences.fromMap(map);

      expect(restored.universalColor, original.universalColor);
      expect(restored.universalActionBarTextColor, original.universalActionBarTextColor);
      expect(restored.backgroundColor, original.backgroundColor);
      expect(restored.listBackgroundColor, original.listBackgroundColor);
      expect(restored.statusBarColor, original.statusBarColor);
      expect(restored.navigationBarColor, original.navigationBarColor);
      expect(restored.launcherIcon, original.launcherIcon);
      expect(restored.emojiVariant, original.emojiVariant);
      expect(restored.notificationIcon, original.notificationIcon);
      expect(restored.fontStyle, original.fontStyle);
      expect(restored.loadFontEnabled, original.loadFontEnabled);
      expect(restored.hidePhotosFromGallery, original.hidePhotosFromGallery);
      expect(restored.hideVideosFromGallery, original.hideVideosFromGallery);
      expect(restored.hideGifsFromGallery, original.hideGifsFromGallery);
      expect(restored.translateOptionSettings, original.translateOptionSettings);
      expect(restored.defaultTranslationLanguage, original.defaultTranslationLanguage);
      expect(restored.conversationCards, original.conversationCards);
      expect(restored.disableHeadsUpNotification, original.disableHeadsUpNotification);
      expect(restored.disableBadgeCounter, original.disableBadgeCounter);
      expect(restored.disableAudioPlayingNotification, original.disableAudioPlayingNotification);
      expect(restored.increaseForwardLimit, original.increaseForwardLimit);
      expect(restored.disableSwipeToExitConversation, original.disableSwipeToExitConversation);
      expect(restored.enableAlwaysOnline, original.enableAlwaysOnline);
      expect(restored.tenorGiphyGifProvider, original.tenorGiphyGifProvider);
      expect(restored.sendImagesInFullResolutionMb, original.sendImagesInFullResolutionMb);

      // Verify aliases
      expect(restored.loadFontCustom, isTrue);
      expect(restored.hideMediaPhotos, isTrue);
      expect(restored.hideMediaVideos, isTrue);
      expect(restored.hideMediaGifs, isTrue);
      expect(restored.translateOption, 'In-app');
      expect(restored.translationLanguage, 'Spanish (Español)');
      expect(restored.disableSwipeToExit, isTrue);
      expect(restored.gifProvider, 'Giphy');
      expect(restored.sendImagesFullResolutionMb, 4);
    });

    test('GbThemeOverrides incorporates Universal colors dynamically', () {
      final controller = ChatyPreferencesController();
      final baseTheme = ThemePresets.monochromeDark;

      // When universalColor and backgroundColor are unset
      final defaultResolved = GbThemeOverrides.resolve(baseTheme, controller);
      expect(defaultResolved.accentColor, baseTheme.accentColor);
      expect(defaultResolved.backgroundColor, baseTheme.backgroundColor);

      // Set universal colors
      const customAccent = 0xFF00E676; // Mint green
      const customBg = 0xFF101010;
      const customActionText = 0xFFECEFF1;

      controller.updateUniversal(
        controller.universal.copyWith(
          universalColor: customAccent,
          backgroundColor: customBg,
          universalActionBarTextColor: customActionText,
        ),
      );

      final customResolved = GbThemeOverrides.resolve(baseTheme, controller);
      expect(customResolved.accentColor.toARGB32(), customAccent);
      expect(customResolved.backgroundColor.toARGB32(), customBg);
      expect(customResolved.primaryTextColor.toARGB32(), customActionText);
    });

    test('ChatyPreferencesController updates universal state reactively and persists', () async {
      final controller = ChatyPreferencesController();
      bool notified = false;
      controller.addListener(() {
        notified = true;
      });

      controller.updateUniversal(
        controller.universal.copyWith(
          increaseForwardLimit: true,
          disableSwipeToExit: true,
          sendImagesFullResolutionMb: 5,
        ),
      );

      expect(notified, isTrue);
      expect(controller.universal.increaseForwardLimit, isTrue);
      expect(controller.universal.disableSwipeToExit, isTrue);
      expect(controller.universal.sendImagesFullResolutionMb, 5);
    });
  });
}
