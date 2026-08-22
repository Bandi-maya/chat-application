import 'package:flutter/material.dart';
import '../../../domain/models/chat_task.dart';
import '../design_system/design_system.dart';

class StatusBadge extends StatelessWidget {
  final TaskStatus status;
  final TaskPriority? priority;

  const StatusBadge({super.key, required this.status, this.priority});

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    Color bg;
    Color fg;
    String label;

    switch (status) {
      case TaskStatus.inbox:
        bg = colors.foregroundSecondary.withValues(alpha: 0.15);
        fg = colors.foregroundSecondary;
        label = 'Inbox';
        break;
      case TaskStatus.assigned:
        bg = colors.info.withValues(alpha: 0.15);
        fg = colors.info;
        label = 'Assigned';
        break;
      case TaskStatus.inProgress:
        bg = colors.warning.withValues(alpha: 0.15);
        fg = colors.warning;
        label = 'In Progress';
        break;
      case TaskStatus.blocked:
        bg = colors.error.withValues(alpha: 0.15);
        fg = colors.error;
        label = 'Blocked';
        break;
      case TaskStatus.completed:
        bg = colors.success.withValues(alpha: 0.15);
        fg = colors.success;
        label = 'Done';
        break;
      case TaskStatus.archived:
        bg = colors.foregroundTertiary.withValues(alpha: 0.15);
        fg = colors.foregroundTertiary;
        label = 'Archived';
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: fg.withValues(alpha: 0.3), width: 0.8),
      ),
      child: Text(
        label,
        style: TextStyle(color: fg, fontSize: 11, fontWeight: FontWeight.w600),
      ),
    );
  }
}

class PriorityBadge extends StatelessWidget {
  final TaskPriority priority;

  const PriorityBadge({super.key, required this.priority});

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    Color col;
    String text;

    switch (priority) {
      case TaskPriority.low:
        col = colors.foregroundSecondary;
        text = 'Low';
        break;
      case TaskPriority.medium:
        col = colors.info;
        text = 'Med';
        break;
      case TaskPriority.high:
        col = colors.warning;
        text = 'High';
        break;
      case TaskPriority.urgent:
        col = colors.error;
        text = 'Urgent';
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: col.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.flag_rounded, size: 11, color: col),
          const SizedBox(width: 3),
          Text(
            text,
            style: TextStyle(
              color: col,
              fontSize: 10.5,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
