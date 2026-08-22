import 'chat_task.dart';

/// Typed kanban workflow — the SINGLE authoritative source for stage order,
/// labels and legal transitions.
///
/// Both the card chevron and the drag & drop target MUST go through
/// [TaskWorkflow.validate] so there is exactly one transition use-case; the
/// UI never moves a task that the workflow rejects and persistence is the
/// only thing that makes a move visible (realtime/refresh confirms it).
class TaskWorkflow {
  const TaskWorkflow._();

  /// User-facing stages in board order. Enum values reuse the existing
  /// server mapping (`inbox, assigned, in_progress, blocked, completed`).
  static const List<TaskStatus> stages = <TaskStatus>[
    TaskStatus.inbox,
    TaskStatus.inProgress,
    TaskStatus.assigned,
    TaskStatus.blocked,
    TaskStatus.completed,
  ];

  static const Map<TaskStatus, String> _labels = <TaskStatus, String>{
    TaskStatus.inbox: 'Todo',
    TaskStatus.inProgress: 'In Process',
    TaskStatus.assigned: 'In Review',
    TaskStatus.blocked: 'Testing',
    TaskStatus.completed: 'Done',
    TaskStatus.archived: 'Archived',
  };

  static String label(TaskStatus status) => _labels[status] ?? status.name;

  static int _indexOf(TaskStatus status) => stages.indexOf(status);

  /// The previous board stage, or null when already at the first stage
  /// (or outside the board, e.g. archived).
  static TaskStatus? previous(TaskStatus status) {
    final index = _indexOf(status);
    if (index <= 0) return null;
    return stages[index - 1];
  }

  /// The next board stage, or null when no forward transition exists.
  static TaskStatus? next(TaskStatus status) {
    final index = _indexOf(status);
    if (index < 0 || index >= stages.length - 1) return null;
    return stages[index + 1];
  }

  /// Whether [current] → [target] is a legal move on the board. Same-stage
  /// drops are no-ops (accepted silently), off-board statuses are rejected.
  static bool canMove(TaskStatus current, TaskStatus target) =>
      _indexOf(current) >= 0 && _indexOf(target) >= 0;

  /// Validate + describe a requested move. Returns null when the move is a
  /// no-op (same stage); throws [TaskTransitionError] when illegal so the
  /// UI can reject a drop cleanly instead of silently doing nothing.
  static Transition? validate(TaskStatus current, TaskStatus target) {
    if (current == target) return null;
    if (!canMove(current, target)) {
      throw TaskTransitionError(
        from: current,
        to: target,
        reason:
            '${label(current)} → ${label(target)} is not a valid workflow '
            'move.',
      );
    }
    return Transition(from: current, to: target);
  }
}

class Transition {
  final TaskStatus from;
  final TaskStatus to;

  const Transition({required this.from, required this.to});
}

class TaskTransitionError implements Exception {
  final TaskStatus from;
  final TaskStatus to;
  final String reason;

  const TaskTransitionError({
    required this.from,
    required this.to,
    required this.reason,
  });

  @override
  String toString() => reason;
}
