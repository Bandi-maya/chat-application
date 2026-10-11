import 'package:chat/domain/models/preferences.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/gb/gb_theme_overrides.dart';
import 'package:chat/ui/core/theme/theme_presets.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUpAll(() {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
  });

  group('ConversationPreferences Model & Constraints Tests', () {
    test('Default ConversationPreferences values conform to schema contracts', () {
      const prefs = ConversationPreferences();
      expect(prefs.bubbleStyle, 'Stock');
      expect(prefs.tickStyle, 'RC iOS 11');
      expect(prefs.enableQuickContactSidebar, isFalse);
      expect(prefs.sidebarPosition, 'Right');
      expect(prefs.sidebarOpacity, 0.9);
      expect(prefs.iosStylePopupMenu, isTrue);
      expect(prefs.doubleTapReactionEmoji, '❤️');
      expect(prefs.wallpaperType, 'Pattern');
      expect(prefs.voicePlaybackSpeed, 1.0);
      expect(prefs.wallpaperPath, '');
      expect(prefs.enableAnimatedEmojis, isTrue);
      expect(prefs.switchDirectContactLink, isFalse);
      expect(prefs.confirmBeforeSendingSticker, isTrue);
      expect(prefs.newAttachmentPickerUi, isFalse);
      expect(prefs.hideDateAndName, isTrue);
      expect(prefs.hideAdminNameIcon, isFalse);
      expect(prefs.groupAdminIcon, 'Default');
      expect(prefs.quickContactSidebarPosition, 'Top');
      expect(prefs.quickContactBgColor, 0xFF000000);
      expect(prefs.quickContactTextColor, 0xFF000000);
      expect(prefs.hideChatFab, isTrue);
      expect(prefs.disableMoreOptionsFromBubble, isFalse);
      expect(prefs.disableDoubleTapReaction, isFalse);
      expect(prefs.translateOptionSettings, 'Server');
      expect(prefs.hideMessageTranslationIcon, isFalse);
      expect(prefs.customWallpaperPerContact, isFalse);
      expect(prefs.profilePicWallpaper, isFalse);
      expect(prefs.enableProximitySensor, isTrue);
      expect(prefs.disableOutputSwitching, isFalse);
      expect(prefs.playVoiceNotes, isFalse);
      expect(prefs.forwardAsVoiceNote, isFalse);
      expect(prefs.incomingMessageRingtone, 'Default');
      expect(prefs.sendMessageRingtone, 'Default');

      expect(prefs.messageTextSize, 16.0);
      expect(prefs.makeTextSelectable, isTrue);
      expect(prefs.removeReadMore, isFalse);
    });

    test('Clamps numeric bounds for voicePlaybackSpeed, sidebarOpacity, messageTextSize', () {
      final clamped = const ConversationPreferences().copyWith(
        voicePlaybackSpeed: 10.0, // Should clamp to max 3.0
        sidebarOpacity: -0.5,    // Should clamp to min 0.1
        messageTextSize: 50.0,   // Should clamp to max 30.0
      );
      expect(clamped.voicePlaybackSpeed, 3.0);
      expect(clamped.sidebarOpacity, 0.1);
      expect(clamped.messageTextSize, 30.0);

      final clampedLow = const ConversationPreferences().copyWith(
        voicePlaybackSpeed: 0.1,  // Should clamp to min 0.5
        sidebarOpacity: 1.5,      // Should clamp to max 1.0
        messageTextSize: 5.0,     // Should clamp to min 10.0
      );
      expect(clampedLow.voicePlaybackSpeed, 0.5);
      expect(clampedLow.sidebarOpacity, 1.0);
      expect(clampedLow.messageTextSize, 10.0);
    });

    test('Round-trip serialization toMap and fromMap preserves all fields', () {
      final original = const ConversationPreferences(
        bubbleStyle: 'WhatsApp Rounded',
        tickStyle: 'Blue Double',
        enableQuickContactSidebar: true,
        sidebarPosition: 'Left',
        sidebarOpacity: 0.85,
        iosStylePopupMenu: true,
        doubleTapReactionEmoji: '🔥',
        wallpaperType: 'Custom Image',
        voicePlaybackSpeed: 1.5,
        wallpaperPath: '/path/to/wall.png',
        enableAnimatedEmojis: false,
        switchDirectContactLink: true,
        confirmBeforeSendingSticker: true,
        newAttachmentPickerUi: false,
        hideDateAndName: true,
        hideAdminNameIcon: true,
        groupAdminIcon: 'Crown',
        quickContactSidebarPosition: 'Bottom',
        quickContactBgColor: 0xFF123456,
        quickContactTextColor: 0xFF654321,
        hideChatFab: true,
        disableMoreOptionsFromBubble: true,
        disableDoubleTapReaction: true,
        translateOptionSettings: 'In-outside apps',
        hideMessageTranslationIcon: true,
        customWallpaperPerContact: false,
        profilePicWallpaper: true,
        enableProximitySensor: false,
        disableOutputSwitching: true,
        playVoiceNotes: false,
        forwardAsVoiceNote: true,
        incomingMessageRingtone: 'Soft Ping',
        sendMessageRingtone: 'Chime Bell',
        actionBarColor: 0xFF001122,
        hideProfilePicture: true,
        hideContactName: true,
        hideCallButton: true,
        disableContactStatus: true,
        contactStatusBgColor: 0xFF223344,
        contactStatusTextColor: 0xFF334455,
        messageTextSize: 18.0,
        convBackgroundColor: 0xFF101010,
        rightBubbleColor: 0xFF005500,
        rightChatBubbleTextColor: 0xFFFFFFFF,
        rightBubbleTimeColor: 0xFFAAAAAA,
        leftBubbleColor: 0xFF202020,
        leftChatBubbleTextColor: 0xFFEEEEEE,
        leftBubbleTimeColor: 0xFF888888,
        deletedMessageIconColor: 0xFFFF0000,
        quotedDividerColor: 0xFF00FF00,
        quotedNameColor: 0xFF0000FF,
        quotedMessageColor: 0xFFFFFF00,
        quotedBackgroundColor: 0xFF333333,
        makeTextSelectable: false,
        removeReadMore: true,
        entryStyle: 'Curved',
        uiEntryBackgroundColor: 0xFF222222,
        uiButtonsColor: 0xFF44AAFF,
        emojiButtonColor: 0xFFFFAA00,
        sendButtonColor: 0xFF00FF88,
        micSendBgCircleColor: 0xFF8800FF,
        textEntryBackgroundColor: 0xFF181818,
        textEntryColor: 0xFFFFFFFF,
        emojiHeaderColor: 0xFF282828,
        emojiHeaderIconsColor: 0xFFCCCCCC,
        emojiPickerBgColor: 0xFF1F1F1F,
        hyperlinksColor: 0xFF3399FF,
        infoBalloonsTextColor: 0xFFE0E0E0,
        infoBalloonsBgColor: 0xFF404040,
      );

      final map = original.toMap();
      final restored = ConversationPreferences.fromMap(map);

      expect(restored.bubbleStyle, original.bubbleStyle);
      expect(restored.tickStyle, original.tickStyle);
      expect(restored.enableQuickContactSidebar, original.enableQuickContactSidebar);
      expect(restored.sidebarPosition, original.sidebarPosition);
      expect(restored.sidebarOpacity, original.sidebarOpacity);
      expect(restored.iosStylePopupMenu, original.iosStylePopupMenu);
      expect(restored.doubleTapReactionEmoji, original.doubleTapReactionEmoji);
      expect(restored.wallpaperType, original.wallpaperType);
      expect(restored.voicePlaybackSpeed, original.voicePlaybackSpeed);
      expect(restored.wallpaperPath, original.wallpaperPath);
      expect(restored.enableAnimatedEmojis, original.enableAnimatedEmojis);
      expect(restored.switchDirectContactLink, original.switchDirectContactLink);
      expect(restored.confirmBeforeSendingSticker, original.confirmBeforeSendingSticker);
      expect(restored.newAttachmentPickerUi, original.newAttachmentPickerUi);
      expect(restored.hideDateAndName, original.hideDateAndName);
      expect(restored.hideAdminNameIcon, original.hideAdminNameIcon);
      expect(restored.groupAdminIcon, original.groupAdminIcon);
      expect(restored.quickContactSidebarPosition, original.quickContactSidebarPosition);
      expect(restored.quickContactBgColor, original.quickContactBgColor);
      expect(restored.quickContactTextColor, original.quickContactTextColor);
      expect(restored.hideChatFab, original.hideChatFab);
      expect(restored.disableMoreOptionsFromBubble, original.disableMoreOptionsFromBubble);
      expect(restored.disableDoubleTapReaction, original.disableDoubleTapReaction);
      expect(restored.translateOptionSettings, original.translateOptionSettings);
      expect(restored.hideMessageTranslationIcon, original.hideMessageTranslationIcon);
      expect(restored.customWallpaperPerContact, original.customWallpaperPerContact);
      expect(restored.profilePicWallpaper, original.profilePicWallpaper);
      expect(restored.enableProximitySensor, original.enableProximitySensor);
      expect(restored.disableOutputSwitching, original.disableOutputSwitching);
      expect(restored.playVoiceNotes, original.playVoiceNotes);
      expect(restored.forwardAsVoiceNote, original.forwardAsVoiceNote);
      expect(restored.incomingMessageRingtone, original.incomingMessageRingtone);
      expect(restored.sendMessageRingtone, original.sendMessageRingtone);
      expect(restored.actionBarColor, original.actionBarColor);
      expect(restored.hideProfilePicture, original.hideProfilePicture);
      expect(restored.hideContactName, original.hideContactName);
      expect(restored.hideCallButton, original.hideCallButton);
      expect(restored.disableContactStatus, original.disableContactStatus);
      expect(restored.contactStatusBgColor, original.contactStatusBgColor);
      expect(restored.contactStatusTextColor, original.contactStatusTextColor);
      expect(restored.messageTextSize, original.messageTextSize);
      expect(restored.convBackgroundColor, original.convBackgroundColor);
      expect(restored.rightBubbleColor, original.rightBubbleColor);
      expect(restored.rightChatBubbleTextColor, original.rightChatBubbleTextColor);
      expect(restored.rightBubbleTimeColor, original.rightBubbleTimeColor);
      expect(restored.leftBubbleColor, original.leftBubbleColor);
      expect(restored.leftChatBubbleTextColor, original.leftChatBubbleTextColor);
      expect(restored.leftBubbleTimeColor, original.leftBubbleTimeColor);
      expect(restored.deletedMessageIconColor, original.deletedMessageIconColor);
      expect(restored.quotedDividerColor, original.quotedDividerColor);
      expect(restored.quotedNameColor, original.quotedNameColor);
      expect(restored.quotedMessageColor, original.quotedMessageColor);
      expect(restored.quotedBackgroundColor, original.quotedBackgroundColor);
      expect(restored.makeTextSelectable, original.makeTextSelectable);
      expect(restored.removeReadMore, original.removeReadMore);
      expect(restored.entryStyle, original.entryStyle);
      expect(restored.uiEntryBackgroundColor, original.uiEntryBackgroundColor);
      expect(restored.uiButtonsColor, original.uiButtonsColor);
      expect(restored.emojiButtonColor, original.emojiButtonColor);
      expect(restored.sendButtonColor, original.sendButtonColor);
      expect(restored.micSendBgCircleColor, original.micSendBgCircleColor);
      expect(restored.textEntryBackgroundColor, original.textEntryBackgroundColor);
      expect(restored.textEntryColor, original.textEntryColor);
      expect(restored.emojiHeaderColor, original.emojiHeaderColor);
      expect(restored.emojiHeaderIconsColor, original.emojiHeaderIconsColor);
      expect(restored.emojiPickerBgColor, original.emojiPickerBgColor);
      expect(restored.hyperlinksColor, original.hyperlinksColor);
      expect(restored.infoBalloonsTextColor, original.infoBalloonsTextColor);
      expect(restored.infoBalloonsBgColor, original.infoBalloonsBgColor);
    });

    test('copyWith clear flags cleanly reset nullable color overrides to null', () {
      final withColors = const ConversationPreferences(
        actionBarColor: 0xFF111111,
        convBackgroundColor: 0xFF222222,
        rightBubbleColor: 0xFF333333,
        leftBubbleColor: 0xFF444444,
        uiEntryBackgroundColor: 0xFF555555,
      );

      final cleared = withColors.copyWith(
        clearActionBarColor: true,
        clearConvBackgroundColor: true,
        clearRightBubbleColor: true,
        clearLeftBubbleColor: true,
        clearUiEntryBackgroundColor: true,
      );

      expect(cleared.actionBarColor, isNull);
      expect(cleared.convBackgroundColor, isNull);
      expect(cleared.rightBubbleColor, isNull);
      expect(cleared.leftBubbleColor, isNull);
      expect(cleared.uiEntryBackgroundColor, isNull);
    });

    test('fromMap resolves legacy aliases correctly', () {
      final legacyMap = {
        'abu_saleh_quickcontact': 'Left',
        'abu_saleh_quickcontact_pos': 'Bottom',
        'abu_saleh_quickcontact_bg': 0xFF123123,
        'abu_saleh_quickcontact_txt': 0xFF456456,
        'hide_divider': true,
        'conversation_style_row': 'Telegram Flat',
      };
      final resolved = ConversationPreferences.fromMap(legacyMap);
      expect(resolved.enableQuickContactSidebar, isTrue);
      expect(resolved.sidebarPosition, 'Left');
      expect(resolved.quickContactSidebarPosition, 'Bottom');
      expect(resolved.quickContactBgColor, 0xFF123123);
      expect(resolved.quickContactTextColor, 0xFF456456);
      expect(resolved.bubbleStyle, 'Telegram Flat');
    });
  });

  group('GB Theme Overrides Integration Tests', () {
    test('Resolves conversation colors over global theme fallbacks', () {
      final prefs = ChatyPreferencesController();

      final convPrefs = const ConversationPreferences(
        rightBubbleColor: 0xFF25D366, // Light green bubble
        leftBubbleColor: 0xFF128C7E,  // Dark teal bubble
        rightChatBubbleTextColor: 0xFF000000, // Black text on green (high contrast)
        leftChatBubbleTextColor: 0xFFFFFFFF,  // White text on teal (high contrast)
        convBackgroundColor: 0xFF1E293B,
        hyperlinksColor: 0xFF38BDF8,
      );

      prefs.updateConversation(convPrefs);

      final baseTheme = ThemePresets.midnight;
      final resolved = GbThemeOverrides.resolve(baseTheme, prefs);

      expect(resolved.outgoingBubbleColor, const Color(0xFF25D366));
      expect(resolved.incomingBubbleColor, const Color(0xFF128C7E));
      expect(resolved.outgoingTextColor, const Color(0xFF000000));
      expect(resolved.incomingTextColor, const Color(0xFFFFFFFF));
      expect(resolved.backgroundColor, const Color(0xFF1E293B));
      expect(resolved.linkColor, const Color(0xFF38BDF8));
    });
  });
}
