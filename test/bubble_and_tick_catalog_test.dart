import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/bubbles/bubble_style_id.dart';
import 'package:chat/ui/core/bubbles/bubble_style_registry.dart';
import 'package:chat/ui/core/ticks/delivery_icon_style.dart';
import 'package:chat/ui/core/theme/theme_config.dart';
import 'package:chat/ui/core/theme/chaty_theme_manager.dart';
import 'package:chat/domain/models/preferences.dart';

void main() {
  group('Bubble & Tick Catalog Verification', () {
    test('All 48 BubbleStyleId values generate valid vector paths', () {
      expect(BubbleStyleId.values.length, 48);

      const testRect = Rect.fromLTWH(0, 0, 200, 60);

      for (final styleId in BubbleStyleId.values) {
        final geometry = BubbleStyleRegistry.getGeometry(styleId);
        expect(geometry.styleId, styleId);

        // Compute path for outgoing and incoming
        final outPath = geometry.getBubblePath(testRect, isMe: true);
        final inPath = geometry.getBubblePath(testRect, isMe: false);

        expect(outPath, isNotNull);
        expect(inPath, isNotNull);
        expect(outPath.getBounds().isEmpty, isFalse);
        expect(inPath.getBounds().isEmpty, isFalse);
      }
    });

    test('All 16 DeliveryIconStyle values have valid descriptors', () {
      expect(DeliveryIconStyle.values.length, 24);

      for (final style in DeliveryIconStyle.values) {
        expect(style.displayName.isNotEmpty, isTrue);
        // Test deserialization roundtrip
        final parsed = DeliveryIconStyleExtension.fromString(style.displayName);
        expect(parsed, style);
      }
    });

    test('Conversation bubble picker labels select distinct real renderers', () {
      final labels = <String, BubbleStyleId>{
        'WhatsApp Default': BubbleStyleId.stock,
        'Rounded Pill': BubbleStyleId.facebookMessenger,
        'Chaty Modern': BubbleStyleId.rounded,
        'Squircle Soft': BubbleStyleId.gabiSqua,
        'Paper Chat': BubbleStyleId.waPaperRedesigned,
        'Glassmorphic': BubbleStyleId.transparent,
        'iOS Flat': BubbleStyleId.ios,
        'Telegram Arc': BubbleStyleId.telegram,
        '3D Shadow': BubbleStyleId.threeD,
        'Minimal Border': BubbleStyleId.gabiOutline,
        'Retro Bubble': BubbleStyleId.oldHangouts,
        'Futuristic Clip': BubbleStyleId.foldV2,
      };

      for (final entry in labels.entries) {
        expect(
          BubbleStyleIdExtension.fromString(entry.key),
          entry.value,
          reason: '${entry.key} must render its selected bubble geometry.',
        );
      }
      expect(labels.values.toSet().length, labels.length);
    });

    test('Conversation tick picker labels select distinct real renderers', () {
      final labels = <String, DeliveryIconStyle>{
        'Default': DeliveryIconStyle.rcIos11,
        'iOS Ticks': DeliveryIconStyle.ios2,
        'Wings': DeliveryIconStyle.wings,
        'Traffic Lights': DeliveryIconStyle.trafficLights,
        'Circles': DeliveryIconStyle.cirCheck,
        'Stars': DeliveryIconStyle.stars,
        'Hearts': DeliveryIconStyle.hearts,
        'Batman': DeliveryIconStyle.batman,
        'Double Sword': DeliveryIconStyle.doubleSword,
        'Glowing Dots': DeliveryIconStyle.glowingDots,
        'Material 3 Rounded': DeliveryIconStyle.material3Rounded,
      };

      for (final entry in labels.entries) {
        expect(
          DeliveryIconStyleExtension.fromString(entry.key),
          entry.value,
          reason: '${entry.key} must render its selected tick design.',
        );
      }
      expect(labels.values.toSet().length, labels.length);
    });

    test(
      'ConversationPreferences correctly deserializes legacy bubble and tick styles',
      () {
        final legacyMap = <String, dynamic>{
          'bubbleShape': 'Squircle',
          'tickStyle': 'iOS Style',
          'bubbleRadius': 20.0,
        };

        final prefs = ConversationPreferences.fromMap(legacyMap);
        expect(prefs.bubbleStyle, 'Squircle');
        expect(prefs.tickStyle, 'iOS Style');

        final bubbleId = BubbleStyleIdExtension.fromString(prefs.bubbleStyle);
        expect(bubbleId, BubbleStyleId.gabiSqua);

        final tickStyle = DeliveryIconStyleExtension.fromString(
          prefs.tickStyle,
        );
        expect(tickStyle, DeliveryIconStyle.rcIos11);
      },
    );

    test('ChatyThemeManager exports and validates ThemeConfig JSON', () {
      const theme = ThemeConfig(
        id: 'test_custom',
        name: 'Test Custom Theme',
        brightness: Brightness.dark,
        accentColor: Color(0xFF6366F1),
        backgroundColor: Color(0xFF0F172A),
        surfaceColor: Color(0xFF1E293B),
        cardColor: Color(0xFF334155),
        primaryTextColor: Color(0xFFF8FAFC),
        secondaryTextColor: Color(0xFF94A3B8),
        outgoingBubbleColor: Color(0xFF6366F1),
        incomingBubbleColor: Color(0xFF1E293B),
        outgoingTextColor: Color(0xFFFFFFFF),
        incomingTextColor: Color(0xFFF8FAFC),
        linkColor: Color(0xFF60A5FA),
        dangerColor: Color(0xFFEF4444),
        successColor: Color(0xFF10B981),
        bubbleStyle: BubbleStyleId.rcIos11,
        deliveryTickStyle: DeliveryIconStyle.greenTick,
      );

      final jsonStr = ChatyThemeManager.exportTheme(theme);
      expect(jsonStr.contains('"schemaVersion": 1'), isTrue);

      final imported = ChatyThemeManager.validateAndImportTheme(jsonStr);
      expect(imported.name, 'Test Custom Theme');
      expect(imported.bubbleStyle, BubbleStyleId.rcIos11);
      expect(imported.deliveryTickStyle, DeliveryIconStyle.greenTick);
    });
  });
}
