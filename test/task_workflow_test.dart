import 'package:flutter_test/flutter_test.dart';
import 'package:chat/data/services/backend_service.dart';
import 'package:chat/domain/models/chat_task.dart';
import 'package:chat/domain/models/task_workflow.dart';

void main() {
  group('TaskWorkflow stage order', () {
    test('board order is Todo → In Process → In Review → Testing → Done', () {
      expect(TaskWorkflow.stages, const [
        TaskStatus.inbox,
        TaskStatus.inProgress,
        TaskStatus.assigned,
        TaskStatus.blocked,
        TaskStatus.completed,
      ]);
    });

    test('next() advances every stage and stops at Done', () {
      expect(TaskWorkflow.next(TaskStatus.inbox), TaskStatus.inProgress);
      // THE reported bug: In Process must advance to In Review.
      expect(TaskWorkflow.next(TaskStatus.inProgress), TaskStatus.assigned);
      expect(TaskWorkflow.next(TaskStatus.assigned), TaskStatus.blocked);
      expect(TaskWorkflow.next(TaskStatus.blocked), TaskStatus.completed);
      expect(TaskWorkflow.next(TaskStatus.completed), isNull);
      expect(TaskWorkflow.next(TaskStatus.archived), isNull);
    });

    test('previous() mirrors next()', () {
      expect(TaskWorkflow.previous(TaskStatus.inbox), isNull);
      expect(TaskWorkflow.previous(TaskStatus.inProgress), TaskStatus.inbox);
      expect(TaskWorkflow.previous(TaskStatus.assigned), TaskStatus.inProgress);
      expect(TaskWorkflow.previous(TaskStatus.completed), TaskStatus.blocked);
      expect(TaskWorkflow.previous(TaskStatus.archived), isNull);
    });

    test('labels are user-facing', () {
      expect(TaskWorkflow.label(TaskStatus.inProgress), 'In Process');
      expect(TaskWorkflow.label(TaskStatus.assigned), 'In Review');
    });
  });

  group('TaskWorkflow.validate (the single transition use-case)', () {
    test('allows the In Process → In Review advance', () {
      final move = TaskWorkflow.validate(
        TaskStatus.inProgress,
        TaskStatus.assigned,
      );
      expect(move, isNotNull);
      expect(move!.from, TaskStatus.inProgress);
      expect(move.to, TaskStatus.assigned);
    });

    test('treats same-stage drops as a silent no-op', () {
      expect(
        TaskWorkflow.validate(TaskStatus.inProgress, TaskStatus.inProgress),
        isNull,
      );
    });

    test('rejects off-board targets (e.g. archived) with a real error', () {
      expect(
        () => TaskWorkflow.validate(TaskStatus.inProgress, TaskStatus.archived),
        throwsA(isA<TaskTransitionError>()),
      );
    });

    test('canMove accepts every adjacent pair on the board', () {
      for (var i = 0; i < TaskWorkflow.stages.length - 1; i++) {
        expect(
          TaskWorkflow.canMove(
            TaskWorkflow.stages[i],
            TaskWorkflow.stages[i + 1],
          ),
          isTrue,
          reason:
              '${TaskWorkflow.label(TaskWorkflow.stages[i])} → '
              '${TaskWorkflow.label(TaskWorkflow.stages[i + 1])} must be legal',
        );
      }
    });
  });

  group('Database status mapping (bijective — the root-cause fix)', () {
    test('every workflow status round-trips through the database value', () {
      for (final status in TaskWorkflow.stages) {
        final dbValue = ChatyBackendService.taskStatusToDatabase(status);
        expect(
          ChatyBackendService.taskStatusFromDatabase(dbValue),
          status,
          reason: '$status → "$dbValue" must read back as $status',
        );
      }
    });

    test('In Process no longer collides with Testing in the database', () {
      expect(
        ChatyBackendService.taskStatusToDatabase(TaskStatus.inProgress),
        'in_progress',
      );
      expect(
        ChatyBackendService.taskStatusToDatabase(TaskStatus.blocked),
        'blocked',
      );
    });

    test('statuses never map to the forbidden legacy "todo" value', () {
      for (final status in TaskStatus.values) {
        expect(
          ChatyBackendService.taskStatusToDatabase(status),
          isNot('todo'),
          reason: '"todo" violates the tasks.status DB constraint (23514)',
        );
      }
    });
  });
}
