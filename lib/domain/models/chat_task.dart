enum TaskStatus { inbox, assigned, inProgress, blocked, completed, archived }

enum TaskPriority { low, medium, high, urgent }

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
    this.activities = const [],
  });

  bool get isOverdue {
    return status != TaskStatus.completed &&
        status != TaskStatus.archived &&
        dueAt.isBefore(DateTime.now());
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
      activities: activities ?? this.activities,
    );
  }
}
