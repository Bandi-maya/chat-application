enum TaskStatus { inbox, assigned, inProgress, blocked, completed, archived }

enum TaskPriority { low, medium, high, urgent }

class TaskChecklistItem {
  final String id;
  final String title;
  final bool isCompleted;

  const TaskChecklistItem({
    required this.id,
    required this.title,
    this.isCompleted = false,
  });

  TaskChecklistItem copyWith({String? id, String? title, bool? isCompleted}) {
    return TaskChecklistItem(
      id: id ?? this.id,
      title: title ?? this.title,
      isCompleted: isCompleted ?? this.isCompleted,
    );
  }

  Map<String, dynamic> toMap() => {
    'id': id,
    'title': title,
    'is_completed': isCompleted,
  };

  factory TaskChecklistItem.fromMap(Map<String, dynamic> map) {
    return TaskChecklistItem(
      id: map['id']?.toString() ?? '',
      title: map['title']?.toString() ?? '',
      isCompleted: map['is_completed'] == true,
    );
  }
}

class TaskActivity {
  final String id;
  final String userId;
  final String text;
  final DateTime timestamp;

  const TaskActivity({
    required this.id,
    required this.userId,
    required this.text,
    required this.timestamp,
  });
}

class ChatTask {
  final String id;
  final String sourceConversationId;
  final String? sourceMessageId;
  final String title;
  final String description;
  final String creatorId;
  final List<String> assigneeIds;
  final TaskStatus status;
  final TaskPriority priority;
  final DateTime dueAt;
  final DateTime? reminderAt;
  final List<String> labels;
  final DateTime createdAt;
  final DateTime updatedAt;
  final List<TaskChecklistItem> checklistItems;
  final List<TaskActivity> activities;

  const ChatTask({
    required this.id,
    required this.sourceConversationId,
    this.sourceMessageId,
    required this.title,
    required this.description,
    required this.creatorId,
    required this.assigneeIds,
    this.status = TaskStatus.inbox,
    this.priority = TaskPriority.medium,
    required this.dueAt,
    this.reminderAt,
    this.labels = const [],
    required this.createdAt,
    required this.updatedAt,
    this.checklistItems = const [],
    this.activities = const [],
  });

  bool get isOverdue {
    return status != TaskStatus.completed &&
        status != TaskStatus.archived &&
        dueAt.isBefore(DateTime.now());
  }

  int get completedChecklistCount =>
      checklistItems.where((item) => item.isCompleted).length;

  String get dueRelativeText {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final dueDay = DateTime(dueAt.year, dueAt.month, dueAt.day);
    final diffDays = dueDay.difference(today).inDays;

    final hour = dueAt.hour.toString().padLeft(2, '0');
    final min = dueAt.minute.toString().padLeft(2, '0');
    final timeStr = '$hour:$min';

    if (isOverdue) {
      if (diffDays < 0) {
        return '${-diffDays}d overdue';
      }
      return 'Overdue ($timeStr)';
    }

    if (diffDays == 0) {
      return 'Today, $timeStr';
    } else if (diffDays == 1) {
      return 'Tomorrow, $timeStr';
    } else if (diffDays > 1 && diffDays <= 7) {
      const weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
      return '${weekdays[dueAt.weekday - 1]}, $timeStr';
    } else {
      return '${dueAt.day}/${dueAt.month} $timeStr';
    }
  }

  ChatTask copyWith({
    String? id,
    String? sourceConversationId,
    String? sourceMessageId,
    String? title,
    String? description,
    String? creatorId,
    List<String>? assigneeIds,
    TaskStatus? status,
    TaskPriority? priority,
    DateTime? dueAt,
    DateTime? reminderAt,
    List<String>? labels,
    DateTime? createdAt,
    DateTime? updatedAt,
    List<TaskChecklistItem>? checklistItems,
    List<TaskActivity>? activities,
  }) {
    return ChatTask(
      id: id ?? this.id,
      sourceConversationId: sourceConversationId ?? this.sourceConversationId,
      sourceMessageId: sourceMessageId ?? this.sourceMessageId,
      title: title ?? this.title,
      description: description ?? this.description,
      creatorId: creatorId ?? this.creatorId,
      assigneeIds: assigneeIds ?? this.assigneeIds,
      status: status ?? this.status,
      priority: priority ?? this.priority,
      dueAt: dueAt ?? this.dueAt,
      reminderAt: reminderAt ?? this.reminderAt,
      labels: labels ?? this.labels,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      checklistItems: checklistItems ?? this.checklistItems,
      activities: activities ?? this.activities,
    );
  }
}
