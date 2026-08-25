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
  }) {
    final actions = <MessageActionDescriptor>[];

    // Reply is available on all valid non-system messages
    if (message.type != MessageType.system && !message.isDeletedForEveryone) {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.reply,
          label: 'Reply',
          icon: Icons.reply_rounded,
        ),
      );
    }

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

    // Forward is available for all standard content (excluding system/deleted messages)
    if (message.type != MessageType.system && !message.isDeletedForEveryone) {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.forward,
          label: 'Forward',
          icon: Icons.shortcut_rounded,
        ),
      );
    }

    // Star (non-system messages)
    if (message.type != MessageType.system) {
      actions.add(
        MessageActionDescriptor(
          type: MessageActionType.star,
          label: message.isStarred ? 'Unstar' : 'Star',
          icon: message.isStarred
              ? Icons.star_rounded
              : Icons.star_border_rounded,
        ),
      );
    }

    // Edit (only for own text messages within allowed policy window)
    if (isMe &&
        message.type == MessageType.text &&
        !message.isDeletedForEveryone &&
        allowEditPolicy) {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.edit,
          label: 'Edit',
          icon: Icons.edit_rounded,
          isPrimary: false,
        ),
      );
    }

    // Pin (non-system messages)
    if (message.type != MessageType.system) {
      actions.add(
        MessageActionDescriptor(
          type: MessageActionType.pin,
          label: message.isPinned ? 'Unpin' : 'Pin',
          icon: Icons.push_pin_outlined,
          isPrimary: false,
        ),
      );
    }

    // Task creation from text message
    if (message.text.isNotEmpty && message.type == MessageType.text) {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.task,
          label: 'Create Task',
          icon: Icons.task_alt_rounded,
          isPrimary: false,
        ),
      );
    }

    // Message Info for outgoing messages
    if (isMe && message.type != MessageType.system) {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.info,
          label: 'Message info',
          icon: Icons.info_outline_rounded,
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

    if (isMe && !message.isDeletedForEveryone) {
      actions.add(
        const MessageActionDescriptor(
          type: MessageActionType.deleteForEveryone,
          label: 'Delete for everyone',
          icon: Icons.delete_forever_rounded,
          isDestructive: true,
          isPrimary: false,
        ),
      );
    }

    return List<MessageActionDescriptor>.unmodifiable(actions);
  }
}
