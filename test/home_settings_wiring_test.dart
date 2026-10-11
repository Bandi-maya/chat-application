import 'package:flutter_test/flutter_test.dart';
import 'package:chat/domain/models/preferences.dart';
import 'package:chat/ui/core/controllers/home_preset_normalizer.dart';
import 'package:chat/ui/core/formatting/chat_formatters.dart';

void main() {
  group('HomePresetNormalizer Tests', () {
    test('canonicalizes legacy home styles to valid preset layouts', () {
      expect(HomePresetNormalizer.homeStyle('ONE UI'), 'Cards');
      expect(HomePresetNormalizer.homeStyle('one ui'), 'Cards');
      expect(HomePresetNormalizer.homeStyle('IOS STYLE'), 'Stories First');
      expect(HomePresetNormalizer.homeStyle('WhatsApp UI Stock'), 'Chaty Default');
      expect(HomePresetNormalizer.homeStyle('BUBBLES TAB STYLE'), 'Compact');
      expect(HomePresetNormalizer.homeStyle('BASIC TAB STYLE'), 'Productivity');
      expect(HomePresetNormalizer.homeStyle('WhatsApp OLD UI'), 'Classic');
      expect(
        HomePresetNormalizer.homeStyle('WhatsApp-style top bar'),
        'Classic',
      );
      expect(HomePresetNormalizer.homeStyle('Floating Rail'), 'Stories First');
      expect(
        HomePresetNormalizer.homeStyle('3D Perspective Drawer'),
        'Expressive',
      );
      expect(HomePresetNormalizer.homeStyle('Modern Side Menu'), 'Cards');
      expect(
        HomePresetNormalizer.homeStyle('Curved Radial Menu'),
        'Expressive',
      );
      expect(HomePresetNormalizer.homeStyle('Classic'), 'Classic');
      expect(HomePresetNormalizer.homeStyle('Tablet Split View'), 'Tablet Split View');
      expect(HomePresetNormalizer.homeStyle('Unknown Value'), 'Chaty Default');
    });

    test('canonicalizes story styles', () {
      expect(HomePresetNormalizer.storiesStyle('Instagram'), 'Circular');
      expect(HomePresetNormalizer.storiesStyle('Facebook'), 'Circular');
      expect(HomePresetNormalizer.storiesStyle('Stock'), 'Circular');
      expect(HomePresetNormalizer.storiesStyle('Card'), 'Card');
      expect(HomePresetNormalizer.storiesStyle('Squircle'), 'Squircle');
      expect(HomePresetNormalizer.storiesStyle('Compact'), 'Compact');
      expect(HomePresetNormalizer.storiesStyle('Minimal'), 'Minimal');
    });
  });

  group('ChatFormatters ElapsedTime Tests', () {
    final now = DateTime(2026, 10, 10, 12, 0, 0);

    test('formats immediate timestamps as just now', () {
      final recent = now.subtract(const Duration(seconds: 20));
      expect(formatElapsedTime(recent, now: now), 'just now');
    });

    test('formats minutes elapsed', () {
      final fiveMinAgo = now.subtract(const Duration(minutes: 5));
      expect(formatElapsedTime(fiveMinAgo, now: now), '5m ago');
    });

    test('formats hours elapsed', () {
      final twoHoursAgo = now.subtract(const Duration(hours: 2));
      expect(formatElapsedTime(twoHoursAgo, now: now), '2h ago');
    });

    test('formats days elapsed', () {
      final threeDaysAgo = now.subtract(const Duration(days: 3));
      expect(formatElapsedTime(threeDaysAgo, now: now), '3d ago');
    });

    test('formats weeks elapsed', () {
      final twoWeeksAgo = now.subtract(const Duration(days: 14));
      expect(formatElapsedTime(twoWeeksAgo, now: now), '2w ago');
    });

    test('formats months elapsed', () {
      final twoMonthsAgo = now.subtract(const Duration(days: 65));
      expect(formatElapsedTime(twoMonthsAgo, now: now), '2mo ago');
    });

    test('formats years elapsed', () {
      final twoYearsAgo = now.subtract(const Duration(days: 750));
      expect(formatElapsedTime(twoYearsAgo, now: now), '2y ago');
    });
  });

  group('HomePreferences Serialization and Defaults', () {
    test('roundtrips all 43 fields through toMap and fromMap', () {
      const original = HomePreferences(
        homeStyle: 'Cards',
        enableStoriesStrip: true,
        storiesStyle: 'Squircle',
        separateChatsAndGroups: true,
        myNameOverride: 'Bandi Maya',
        avatarShape: 'squircle',
        ghostMode: true,
        airplaneModeSimulator: true,
        showSearchBar: true,
        showCameraIcon: true,
        showDesktopIcon: false,
        carouselView: true,
        setMyName: false,
        disableStatusUnderName: true,
        hideChatSortList: true,
        disableSearchBar: true,
        themesEnabled: false,
        showIconAddAccount: true,
        hideChatsDivider: true,
        hideUnsavedNumbers: true,
        hideFrequentlyContacted: true,
        hideOtherContacts: true,
        hideRecentChats: true,
        screenTextSize: 19.5,
        rowTextColor: 0xFF112233,
        rowContactNameColor: 0xFF223344,
        unreadCounterColor: 0xFF334455,
        unreadCounterTextColor: 0xFF445566,
        contactOnlineColor: 0xFF556677,
        lastSeenColor: 0xFF667788,
        mentionIndicatorBgColor: 0xFF778899,
        mentionIconColor: 0xFF8899AA,
        hideArchivedChats: true,
        archiveChatsOnTop: false,
        disableContactOnlineLastSeen: true,
        disableOnlineDot: true,
        onlineDotColor: 0xFF99AABB,
        elapsedTime: true,
        hideFab: true,
        fabNormalColor: 0xFFAABBCC,
        fabPressedColor: 0xFFBBCCDD,
        fabIconsColor: 0xFFCCDDEE,
        showMetaAiIcon: true,
        hideNewMessageFab: true,
        hideLastSeenFab: true,
        hideCutEditFab: true,
        hidePluginsList: true,
        hideGbwaSettingsFab: true,
        pagerTransition3d: 'Card Flip',
        tabBubbleStyle: 'Glassmorphism Glow',
      );

      final map = original.toMap();
      final restored = HomePreferences.fromMap(map);

      expect(restored.homeStyle, original.homeStyle);
      expect(restored.enableStoriesStrip, original.enableStoriesStrip);
      expect(restored.storiesStyle, original.storiesStyle);
      expect(restored.separateChatsAndGroups, original.separateChatsAndGroups);
      expect(restored.myNameOverride, original.myNameOverride);
      expect(restored.setMyName, original.setMyName);
      expect(restored.disableStatusUnderName, original.disableStatusUnderName);
      expect(restored.hideChatSortList, original.hideChatSortList);
      expect(restored.disableSearchBar, original.disableSearchBar);
      expect(restored.hideChatsDivider, original.hideChatsDivider);
      expect(restored.hideArchivedChats, original.hideArchivedChats);
      expect(restored.archiveChatsOnTop, original.archiveChatsOnTop);
      expect(restored.screenTextSize, original.screenTextSize);
      expect(restored.rowTextColor, original.rowTextColor);
      expect(restored.rowContactNameColor, original.rowContactNameColor);
      expect(restored.unreadCounterColor, original.unreadCounterColor);
      expect(restored.unreadCounterTextColor, original.unreadCounterTextColor);
      expect(restored.contactOnlineColor, original.contactOnlineColor);
      expect(restored.lastSeenColor, original.lastSeenColor);
      expect(restored.disableContactOnlineLastSeen, original.disableContactOnlineLastSeen);
      expect(restored.disableOnlineDot, original.disableOnlineDot);
      expect(restored.onlineDotColor, original.onlineDotColor);
      expect(restored.elapsedTime, original.elapsedTime);
      expect(restored.hideFab, original.hideFab);
      expect(restored.fabNormalColor, original.fabNormalColor);
      expect(restored.fabPressedColor, original.fabPressedColor);
      expect(restored.fabIconsColor, original.fabIconsColor);
      expect(restored.showMetaAiIcon, original.showMetaAiIcon);
      expect(restored.hideNewMessageFab, original.hideNewMessageFab);
      expect(restored.hideLastSeenFab, original.hideLastSeenFab);
      expect(restored.hideCutEditFab, original.hideCutEditFab);
      expect(restored.hidePluginsList, original.hidePluginsList);
      expect(restored.hideGbwaSettingsFab, original.hideGbwaSettingsFab);
      expect(restored.tabBubbleStyle, original.tabBubbleStyle);
      expect(restored.pagerTransition3d, original.pagerTransition3d);
    });
  });

  group('StatusPreferences Serialization and Defaults', () {
    test('roundtrips StatusPreferences through toMap and fromMap', () {
      const original = StatusPreferences(
        enableInstagramStories: true,
        carouselView: true,
        storiesStyle: 'Instagram',
        activateNewStatusStyle: 'New Status with Thumbnail',
        statusReactionEmoji: '🔥',
        recentUpdatesBarColor: 0xFF123456,
        recentUpdatesTextColor: 0xFF654321,
        contactNameColor: 0xFFABCDEF,
        statusSeenColor: 0xFF888888,
        statusUnSeenColor: 0xFF25D366,
        counterBackgroundColor: 0xFF111111,
        counterTextColor: 0xFFFFFFFF,
        statusAroundProfile: true,
        saveAndMarkSeenOptions: true,
        changePhotoProfileStatusPreview: true,
        startStoriesDirectlyWithSound: true,
        confirmBeforeSendingStatus: false,
        fiveMinuteStatus: true,
      );

      final map = original.toMap();
      final restored = StatusPreferences.fromMap(map);

      expect(restored.enableInstagramStories, original.enableInstagramStories);
      expect(restored.carouselView, original.carouselView);
      expect(restored.storiesStyle, original.storiesStyle);
      expect(restored.recentUpdatesBarColor, original.recentUpdatesBarColor);
      expect(restored.recentUpdatesTextColor, original.recentUpdatesTextColor);
      expect(restored.contactNameColor, original.contactNameColor);
      expect(restored.statusSeenColor, original.statusSeenColor);
      expect(restored.statusUnSeenColor, original.statusUnSeenColor);
      expect(restored.confirmBeforeSendingStatus, original.confirmBeforeSendingStatus);
      expect(restored.fiveMinuteStatus, original.fiveMinuteStatus);
    });
  });
}
