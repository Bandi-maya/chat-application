import 'package:flutter/material.dart';
import '../../domain/models/chat_message.dart';

/// Available message actions in Chaty.
enum MessageActionType {
  react,
  reply,
  copy,
  forward,
  edit,
  pin,
  star,
  translate,
  select,
  info,
  task,
  more,
  deleteForMe,
  deleteForEveryone,
  report,
}

/// Metadata and policy for a single message action.
class MessageActionDescriptor {
  final MessageActionType type;
  final String label;
  final IconData icon;
  final bool isDestructive;
  final bool isPrimary;

  const MessageActionDescriptor({
    required this.type,
    required this.label,
    required this.icon,
    this.isDestructive = false,
    this.isPrimary = true,
  });
}

/// Central registry computing available message actions based on ownership,
/// message type, editing policies, and conversation capabilities.
class MessageActionRegistry {
  const MessageActionRegistry._();

  static List<MessageActionDescriptor> getAvailableActions({
    required ChatMessage message,
    required bool isMe,
    bool allowEditPolicy = true,
    bool isTranslationAvailable = true,
  }) {
    final actions = <MessageActionDescriptor>[];

    // Reply is available on all valid messages
    actions.add(
      const MessageActionDescriptor(
        type: MessageActionType.reply,
        label: 'Reply',
        icon: Icons.reply_rounded,
      ),
    );

    // Copy is available for text messages
    if (message.text.isNotEmpty && message.type == MessageType.text) {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.copy,
          label: 'Copy',
          icon: Icons.copy_rounded,
        ),
      );
    }

    // Forward is available for all standard content
    actions.add(
      const MessageActionDescriptor(
        type: MessageActionType.forward,
        label: 'Forward',
        icon: Icons.shortcut_rounded,
      ),
    );

    // Star
    actions.add(
      MessageActionDescriptor(
        type: MessageActionType.star,
        label: message.isStarred ? 'Unstar' : 'Star',
        icon: message.isStarred ? Icons.star_rounded : Icons.star_border_rounded,
      ),
    );

    // Edit (only for own text messages within allowed policy window)
    if (isMe && message.type == MessageType.text && allowEditPolicy) {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.edit,
          label: 'Edit',
          icon: Icons.edit_rounded,
          isPrimary: false,
        ),
      );
    }

    // Pin
    actions.add(
      MessageActionDescriptor(
        type: MessageActionType.pin,
        label: message.isPinned ? 'Unpin' : 'Pin',
        icon: Icons.push_pin_outlined,
        isPrimary: false,
      ),
    );

    // Task creation from message
    actions.add(
      const MessageActionDescriptor(
        type: MessageActionType.task,
        label: 'Create Task',
        icon: Icons.task_alt_rounded,
        isPrimary: false,
      ),
    );

    // Translate (for non-empty text from other users when supported)
    if (!isMe && message.text.isNotEmpty && isTranslationAvailable) {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.translate,
          label: 'Translate',
          icon: Icons.translate_rounded,
          isPrimary: false,
        ),
      );
    }

    // Multi-select mode
    actions.add(
      const MessageActionDescriptor(
        type: MessageActionType.select,
        label: 'Select',
        icon: Icons.checklist_rounded,
        isPrimary: false,
      ),
    );

    // Delete options
    actions.add(
      const MessageActionDescriptor(
        type: MessageActionType.deleteForMe,
        label: 'Delete for me',
        icon: Icons.delete_outline_rounded,
        isDestructive: true,
        isPrimary: false,
      ),
    );

    if (isMe) {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.deleteForEveryone,
          label: 'Delete for everyone',
          icon: Icons.delete_forever_rounded,
          isDestructive: true,
          isPrimary: false,
        ),
      );
    } else {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.report,
          label: 'Report',
          icon: Icons.report_problem_outlined,
          isDestructive: true,
          isPrimary: false,
        ),
      );
    }

    return List<MessageActionDescriptor>.unmodifiable(actions);
  }
}
