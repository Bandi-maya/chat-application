import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../domain/models/chat_task.dart';
import '../../domain/models/task_workflow.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../ui/core/widgets/status_badge.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/design_system/design_system.dart';
import 'task_create_edit_modal.dart';
import 'task_detail_screen.dart';

class TasksScreen extends StatefulWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;

  const TasksScreen({super.key, required this.theme, required this.dataStore});

  @override
  State<TasksScreen> createState() => _TasksScreenState();
}

class _TasksScreenState extends State<TasksScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabCtrl;
  String _selectedPriorityFilter = 'All';

  // P4 Kanban workflow — stage order, labels and legal transitions live in
  // the typed [TaskWorkflow] model; the chevron AND drag & drop both go
  // through the same validated transition below.
  static const List<TaskStatus> _workflow = TaskWorkflow.stages;

  static String _stageLabel(TaskStatus status) => TaskWorkflow.label(status);

  // --- Task history ("task tree"): every stage transition is recorded
  // per task and viewable from the card's history button.
  static const String _historyKey = 'chaty_task_history_v1';
  static const int _historyCapPerTask = 30;

  Future<Map<String, List<String>>> _loadHistory() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_historyKey);
      if (raw == null || raw.isEmpty) return <String, List<String>>{};
      final decoded = jsonDecode(raw) as Map<String, dynamic>;
      return decoded.map(
        (key, value) => MapEntry(
          key,
          (value as List).map((item) => item.toString()).toList(),
        ),
      );
    } catch (_) {
      return <String, List<String>>{};
    }
  }

  Future<void> _recordMove(
    ChatTask task,
    TaskStatus from,
    TaskStatus to,
  ) async {
    final history = await _loadHistory();
    final entries = history.putIfAbsent(task.id, () => <String>[]);
    final now = DateTime.now();
    final stamp =
        '${now.day}/${now.month} '
        '${now.hour.toString().padLeft(2, '0')}:'
        '${now.minute.toString().padLeft(2, '0')}';
    entries.insert(0, '${_stageLabel(from)} → ${_stageLabel(to)} • $stamp');
    while (entries.length > _historyCapPerTask) {
      entries.removeLast();
    }
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(
        _historyKey,
        jsonEncode(history.map((k, v) => MapEntry(k, v))),
      );
    } catch (_) {}
  }

  Future<void> _showHistory(ChatTask task) async {
    final history = await _loadHistory();
    if (!mounted) return;
    final entries = history[task.id] ?? const <String>[];
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('History — ${task.title}'),
        content: SizedBox(
          width: double.maxFinite,
          child: entries.isEmpty
              ? const Padding(
                  padding: EdgeInsets.symmetric(vertical: 14),
                  child: Text('No stage changes recorded yet.'),
                )
              : ListView(
                  shrinkWrap: true,
                  children: [
                    for (final entry in entries)
                      ListTile(
                        dense: true,
                        leading: const Icon(Icons.history_rounded, size: 18),
                        title: Text(
                          entry,
                          style: const TextStyle(fontSize: 13),
                        ),
                      ),
                  ],
                ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabCtrl.dispose();
    super.dispose();
  }

  Future<void> _openTask(ChatTask task, ThemeConfig theme) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => TaskDetailScreen(
          task: task,
          theme: theme,
          dataStore: widget.dataStore,
        ),
      ),
    );
  }

  void _createTask(ThemeConfig theme) {
    final conversations = widget.dataStore.conversations;
    if (conversations.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Start a conversation before creating an action item.'),
        ),
      );
      return;
    }
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => TaskCreateEditModal(
        theme: theme,
        dataStore: widget.dataStore,
        sourceConversationId: conversations.first.id,
      ),
    );
  }

  /// The SINGLE transition use-case. Both the card chevrons and the kanban
  /// drag & drop funnel through here:
  /// workflow validation → backend persistence → authoritative reload.
  /// The UI only moves a task after the RPC succeeds; failures surface the
  /// real error and the board stays on the persisted state (no fake moves).
  Future<void> _moveTask(ChatTask task, TaskStatus target) async {
    final Transition? move;
    try {
      move = TaskWorkflow.validate(task.status, target);
    } on TaskTransitionError catch (error) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(error.reason)));
      return;
    }
    if (move == null) return;
    try {
      await widget.dataStore.updateTaskStatus(task.id, target);
      await _recordMove(task, move.from, move.to);
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(
              'Could not move "${task.title}" to '
              '${TaskWorkflow.label(target)}: $error',
            ),
          ),
        );
    }
  }

  TaskStatus? _previous(TaskStatus status) => TaskWorkflow.previous(status);

  TaskStatus? _next(TaskStatus status) => TaskWorkflow.next(status);

  @override
  Widget build(BuildContext context) {
    final themeData = Theme.of(context);
    final colors = context.colors;
    final dataStore = widget.dataStore;
    final allTasks = dataStore.tasks;

    return ChatyScaffold(
      safeAreaTop: true,
      safeAreaBottom: false,
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(
              ChatySpacing.base,
              ChatySpacing.md,
              ChatySpacing.base,
              ChatySpacing.xs,
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    'Action Items',
                    style: ChatyTypography.headline(colors.foreground),
                  ),
                ),
                ChatyIconButton(
                  icon: Icons.add_task_rounded,
                  tooltip: 'Create task',
                  backgroundColor: colors.surfaceSecondary,
                  color: colors.primary,
                  onPressed: () => _createTask(widget.theme),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: ChatySpacing.base),
            child: TabBar(
              controller: _tabCtrl,
              labelColor: colors.primary,
              unselectedLabelColor: colors.foregroundSecondary,
              indicatorColor: colors.primary,
              indicatorWeight: 2.5,
              tabs: const [
                Tab(
                  icon: Icon(Icons.list_alt_rounded, size: 18),
                  text: 'Task List',
                ),
                Tab(
                  icon: Icon(Icons.view_kanban_outlined, size: 18),
                  text: 'Kanban Board',
                ),
              ],
            ),
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.fromLTRB(
                ChatySpacing.base,
                ChatySpacing.sm,
                ChatySpacing.base,
                ChatySpacing.xs,
              ),
              child: Row(
                children: ['All', 'Urgent', 'High', 'Med', 'Low']
                    .map((p) {
                      final isSelected = _selectedPriorityFilter == p;
                      return Padding(
                        padding: const EdgeInsets.only(right: ChatySpacing.sm),
                        child: ChoiceChip(
                          label: Text(p),
                          selected: isSelected,
                          selectedColor: colors.primary.withValues(alpha: 0.15),
                          backgroundColor: colors.surfaceSecondary,
                          labelStyle: TextStyle(
                            color: isSelected
                                ? colors.primary
                                : colors.foregroundSecondary,
                            fontWeight: isSelected
                                ? FontWeight.w700
                                : FontWeight.w500,
                            fontSize: 12,
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(
                              ChatyRadius.full,
                            ),
                          ),
                          side: BorderSide(
                            color: isSelected ? colors.primary : colors.border,
                          ),
                          onSelected: (value) {
                            if (value) {
                              setState(() => _selectedPriorityFilter = p);
                            }
                          },
                        ),
                      );
                    })
                    .toList(growable: false),
              ),
            ),
          ),

          Expanded(
            child: TabBarView(
              controller: _tabCtrl,
              children: [
                _buildListView(allTasks, widget.theme, dataStore, themeData),
                _buildKanbanView(allTasks, widget.theme, dataStore, themeData),
              ],
            ),
          ),
        ],
      ),
    );
  }

  List<ChatTask> _filtered(List<ChatTask> tasks) {
    return tasks
        .where((task) {
          if (_selectedPriorityFilter == 'Urgent') {
            return task.priority == TaskPriority.urgent;
          }
          if (_selectedPriorityFilter == 'High') {
            return task.priority == TaskPriority.high;
          }
          if (_selectedPriorityFilter == 'Med') {
            return task.priority == TaskPriority.medium;
          }
          if (_selectedPriorityFilter == 'Low') {
            return task.priority == TaskPriority.low;
          }
          return true;
        })
        .toList(growable: false);
  }

  Widget _buildListView(
    List<ChatTask> tasks,
    ThemeConfig theme,
    MockDataStore dataStore,
    ThemeData themeData,
  ) {
    final filtered = _filtered(tasks);
    if (filtered.isEmpty) return _emptyState(themeData);

    return ListView.separated(
      padding: const EdgeInsets.symmetric(
        horizontal: ChatySpacing.base,
        vertical: ChatySpacing.sm,
      ),
      itemCount: filtered.length,
      separatorBuilder: (_, __) => const SizedBox(height: ChatySpacing.sm),
      itemBuilder: (context, index) {
        final task = filtered[index];
        final isOverdue = task.isOverdue;

        return ChatyCard(
          padding: const EdgeInsets.all(ChatySpacing.base),
          borderColor: isOverdue
              ? context.colors.error.withValues(alpha: 0.4)
              : null,
          onTap: () => _openTask(task, theme),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  StatusBadge(status: task.status),
                  const SizedBox(width: 8),
                  PriorityBadge(priority: task.priority),
                  const Spacer(),
                  if (isOverdue)
                    Text(
                      'OVERDUE',
                      style: TextStyle(
                        color: context.colors.error,
                        fontSize: 10.5,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.5,
                      ),
                    ),
                  const SizedBox(width: 4),
                  Icon(
                    Icons.chevron_right_rounded,
                    color: context.colors.foregroundSecondary,
                  ),
                ],
              ),
              const SizedBox(height: ChatySpacing.sm),
              Text(
                task.title,
                style: TextStyle(
                  color: context.colors.foreground,
                  fontSize: 15.5,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.2,
                ),
              ),
              if (task.description.isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  task.description,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: ChatyTypography.caption(
                    context.colors.foregroundSecondary,
                  ),
                ),
              ],
              const SizedBox(height: ChatySpacing.md),
              Row(
                children: [
                  ...task.assigneeIds.take(4).map((id) {
                    final contact = dataStore.getUser(id);
                    return Padding(
                      padding: const EdgeInsets.only(right: 5),
                      child: AppAvatar(
                        initials: contact?.avatarInitials ?? 'U',
                        colorHex: contact?.avatarColorHex,
                        size: 24,
                      ),
                    );
                  }),
                  const Spacer(),
                  Text(
                    'Due ${task.dueAt.day}/${task.dueAt.month}',
                    style: TextStyle(
                      color: isOverdue
                          ? context.colors.error
                          : context.colors.foregroundSecondary,
                      fontSize: 12,
                      fontWeight: isOverdue ? FontWeight.w700 : FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _emptyState(ThemeData themeData) {
    if (_selectedPriorityFilter != 'All') {
      return ChatyNoResultsState(
        query: '$_selectedPriorityFilter Priority',
        message: 'No tasks found matching the selected priority filter.',
        clearLabel: 'Show all tasks',
        onClear: () => setState(() => _selectedPriorityFilter = 'All'),
      );
    }
    return ChatyEmptyState(
      icon: Icons.task_alt_rounded,
      title: 'No action items yet',
      message:
          'Create an action item from any message or using the create task button.',
      iconColor: context.colors.primary,
      titleColor: context.colors.foreground,
      messageColor: context.colors.foregroundSecondary,
      actionLabel: 'Create task',
      onAction: () => _createTask(widget.theme),
    );
  }

  Widget _buildKanbanView(
    List<ChatTask> tasks,
    ThemeConfig theme,
    MockDataStore dataStore,
    ThemeData themeData,
  ) {
    final filtered = _filtered(tasks);
    // P4: the five user-facing Kanban stages.
    final columns = <(TaskStatus, String)>[
      for (final status in _workflow) (status, _stageLabel(status)),
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        // Responsive: on narrow screens columns take 82% width and scroll
        // horizontally; on wide screens the width shrinks just enough for
        // ALL five stages to fit on screen at once (no horizontal scroll).
        final outerPadding = ChatySpacing.base * 2;
        final innerMargins = ChatySpacing.md * columns.length;
        final fitAllWidth =
            (constraints.maxWidth - outerPadding - innerMargins) /
            columns.length;
        final columnWidth = constraints.maxWidth < 500
            ? constraints.maxWidth * 0.82
            : fitAllWidth < 240
            ? 280.0
            : fitAllWidth.clamp(240.0, 460.0);
        return SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.all(ChatySpacing.base),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: columns
                .map((column) {
                  final status = column.$1;
                  final title = column.$2;
                  final columnTasks = filtered
                      .where((task) => task.status == status)
                      .toList(growable: false);
                  return DragTarget<ChatTask>(
                    // Same workflow validation as the chevron: same-stage
                    // drops are silent no-ops, off-board moves reject.
                    onWillAcceptWithDetails: (details) =>
                        TaskWorkflow.canMove(details.data.status, status),
                    onAcceptWithDetails: (details) =>
                        _moveTask(details.data, status),
                    builder: (context, candidates, rejected) {
                      final highlighted = candidates.isNotEmpty;
                      return AnimatedContainer(
                        duration: ChatyMotion.standard,
                        curve: ChatyMotion.standardEasing,
                        width: columnWidth,
                        constraints: BoxConstraints(
                          minHeight: constraints.maxHeight - 24,
                        ),
                        margin: const EdgeInsets.only(right: ChatySpacing.md),
                        padding: const EdgeInsets.all(ChatySpacing.md),
                        decoration: BoxDecoration(
                          color: highlighted
                              ? context.colors.primary.withValues(alpha: 0.1)
                              : context.colors.surfaceSecondary,
                          borderRadius: BorderRadius.circular(ChatyRadius.card),
                          border: Border.all(
                            color: highlighted
                                ? context.colors.primary
                                : context.colors.border,
                            width: highlighted ? 1.5 : 1.0,
                          ),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    title,
                                    style: TextStyle(
                                      color: context.colors.foreground,
                                      fontWeight: FontWeight.w700,
                                      fontSize: 14,
                                    ),
                                  ),
                                ),
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 8,
                                    vertical: 2,
                                  ),
                                  decoration: BoxDecoration(
                                    color: context.colors.surfaceSecondary,
                                    borderRadius: BorderRadius.circular(
                                      ChatyRadius.full,
                                    ),
                                  ),
                                  child: Text(
                                    '${columnTasks.length}',
                                    style: TextStyle(
                                      color: context.colors.foreground,
                                      fontSize: 11,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: ChatySpacing.md),
                            if (columnTasks.isEmpty)
                              Padding(
                                padding: const EdgeInsets.symmetric(
                                  vertical: ChatySpacing.xl,
                                ),
                                child: Center(
                                  child: Text(
                                    'Drop tasks here',
                                    style: ChatyTypography.caption(
                                      context.colors.foregroundTertiary,
                                    ),
                                  ),
                                ),
                              ),
                            ...columnTasks.map(
                              (task) => _kanbanCard(task, theme, themeData),
                            ),
                          ],
                        ),
                      );
                    },
                  );
                })
                .toList(growable: false),
          ),
        );
      },
    );
  }

  Widget _kanbanCard(ChatTask task, ThemeConfig theme, ThemeData themeData) {
    final previous = _previous(task.status);
    final next = _next(task.status);

    final card = Container(
      decoration: BoxDecoration(
        color: context.colors.surface,
        borderRadius: BorderRadius.circular(ChatyRadius.md),
        border: Border.all(color: context.colors.border, width: 1.0),
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(ChatyRadius.md),
        child: InkWell(
          borderRadius: BorderRadius.circular(ChatyRadius.md),
          onTap: () => _openTask(task, theme),
          child: Padding(
            padding: const EdgeInsets.all(ChatySpacing.md),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  task.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: context.colors.foreground,
                    fontWeight: FontWeight.w600,
                    fontSize: 13.5,
                  ),
                ),
                const SizedBox(height: ChatySpacing.sm),
                Row(
                  children: [
                    PriorityBadge(priority: task.priority),
                    const Spacer(),
                    // Task tree: every stage change is recorded and
                    // viewable here.
                    IconButton(
                      tooltip: 'History',
                      visualDensity: VisualDensity.compact,
                      onPressed: () => _showHistory(task),
                      icon: Icon(
                        Icons.history_rounded,
                        size: 18,
                        color: context.colors.primary,
                      ),
                    ),
                    if (previous != null)
                      ChatyIconButton(
                        size: 28,
                        iconSize: 18,
                        tooltip: 'Move back',
                        onPressed: () => _moveTask(task, previous),
                        icon: Icons.chevron_left_rounded,
                        color: context.colors.primary,
                      ),
                    if (next != null)
                      ChatyIconButton(
                        size: 28,
                        iconSize: 18,
                        tooltip: 'Move forward',
                        onPressed: () => _moveTask(task, next),
                        icon: Icons.chevron_right_rounded,
                        color: context.colors.primary,
                      ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );

    return Padding(
      padding: const EdgeInsets.only(bottom: ChatySpacing.sm),
      child: LongPressDraggable<ChatTask>(
        data: task,
        feedback: Material(
          color: Colors.transparent,
          child: SizedBox(
            width: 240,
            child: Opacity(opacity: 0.92, child: card),
          ),
        ),
        childWhenDragging: Opacity(opacity: 0.35, child: card),
        child: card,
      ),
    );
  }
}
