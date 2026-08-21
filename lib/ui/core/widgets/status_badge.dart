import 'package:flutter/material.dart';
import '../../../domain/models/chat_task.dart';

class StatusBadge extends StatelessWidget {
  final TaskStatus status;
  final TaskPriority? priority;

  const StatusBadge({super.key, required this.status, this.priority});

  @override
  Widget build(BuildContext context) {
    Color bg;
    Color fg;
    String label;

    switch (status) {
      case TaskStatus.inbox:
        bg = Colors.blueGrey.withValues(alpha: 0.15);
        fg = Colors.blueGrey.shade300;
        label = 'Inbox';
        break;
      case TaskStatus.assigned:
        bg = Colors.blue.withValues(alpha: 0.15);
        fg = Colors.blue.shade300;
        label = 'Assigned';
        break;
      case TaskStatus.inProgress:
        bg = const Color(0xFFF59E0B).withValues(alpha: 0.15);
        fg = const Color(0xFFFBBF24);
        label = 'In Progress';
        break;
      case TaskStatus.blocked:
        bg = const Color(0xFFEF4444).withValues(alpha: 0.15);
        fg = const Color(0xFFF87171);
        label = 'Blocked';
        break;
      case TaskStatus.completed:
        bg = const Color(0xFF10B981).withValues(alpha: 0.15);
        fg = const Color(0xFF34D399);
        label = 'Done';
        break;
      case TaskStatus.archived:
        bg = Colors.grey.withValues(alpha: 0.15);
        fg = Colors.grey.shade400;
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
    Color col;
    String text;

    switch (priority) {
      case TaskPriority.low:
        col = Colors.blueGrey;
        text = 'Low';
        break;
      case TaskPriority.medium:
        col = Colors.amber;
        text = 'Med';
        break;
      case TaskPriority.high:
        col = Colors.orange;
        text = 'High';
        break;
      case TaskPriority.urgent:
        col = Colors.redAccent;
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
