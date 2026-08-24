import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/menu/context_surface_engine.dart';
import 'package:chat/features/messages/message_action_registry.dart';
import 'package:chat/domain/models/chat_message.dart';

void main() {
  group('ContextSurfaceResolver Tests', () {
    test('resolves below anchor when sufficient space exists', () {
      final resolution = ContextSurfaceResolver.resolve(
        const ContextSurfaceRequest(
          anchorRect: Rect.fromLTWH(100, 100, 50, 40),
          preferredSize: Size(180, 200),
          screenSize: Size(400, 800),
          safeInsets: EdgeInsets.zero,
          keyboardInsets: EdgeInsets.zero,
        ),
      );

      expect(
        resolution.placement == ContextSurfacePlacement.belowLeft ||
            resolution.placement == ContextSurfacePlacement.belowRight,
        isTrue,
      );
      expect(resolution.targetRect.top, greaterThanOrEqualTo(140));
    });

    test('flips above anchor when bottom viewport overflows', () {
      final resolution = ContextSurfaceResolver.resolve(
        const ContextSurfaceRequest(
          anchorRect: Rect.fromLTWH(100, 700, 50, 40),
          preferredSize: Size(180, 200),
          screenSize: Size(400, 800),
          safeInsets: EdgeInsets.zero,
          keyboardInsets: EdgeInsets.zero,
        ),
      );

      expect(
        resolution.placement == ContextSurfacePlacement.aboveLeft ||
            resolution.placement == ContextSurfacePlacement.aboveRight,
        isTrue,
      );
      expect(resolution.targetRect.bottom, lessThanOrEqualTo(700));
    });
  });

  group('MessageActionRegistry Tests', () {
    test('returns correct action list for own text message', () {
      final msg = ChatMessage(
        id: 'msg_1',
        conversationId: 'conv_1',
        senderId: 'user_1',
        text: 'Hello world',
        createdAt: DateTime.now(),
        type: MessageType.text,
      );

      final actions = MessageActionRegistry.getAvailableActions(
        message: msg,
        isMe: true,
      );

      final types = actions.map((a) => a.type).toSet();
      expect(types.contains(MessageActionType.reply), isTrue);
      expect(types.contains(MessageActionType.copy), isTrue);
      expect(types.contains(MessageActionType.edit), isTrue);
      expect(types.contains(MessageActionType.deleteForEveryone), isTrue);
      expect(types.contains(MessageActionType.deleteForMe), isTrue);
    });

    test('restricts edit and deleteForEveryone for received message', () {
      final msg = ChatMessage(
        id: 'msg_2',
        conversationId: 'conv_1',
        senderId: 'user_2',
        text: 'Hi there',
        createdAt: DateTime.now(),
        type: MessageType.text,
      );

      final actions = MessageActionRegistry.getAvailableActions(
        message: msg,
        isMe: false,
      );

      final types = actions.map((a) => a.type).toSet();
      expect(types.contains(MessageActionType.reply), isTrue);
      expect(types.contains(MessageActionType.copy), isTrue);
      expect(types.contains(MessageActionType.edit), isFalse);
      expect(types.contains(MessageActionType.deleteForEveryone), isFalse);
      expect(types.contains(MessageActionType.deleteForMe), isTrue);
    });
  });
}
