import 'package:flutter/material.dart';

import '../../../ui/core/theme/theme_config.dart';
import '../../domain/models/chat_task.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../ui/core/widgets/status_badge.dart';
import '../../ui/core/widgets/app_avatar.dart';
import 'task_create_edit_modal.dart';
import 'task_detail_screen.dart';
import '../../injection/locator.dart';
import '../../../ui/core/theme/theme_controller.dart';

class TasksScreen extends StatefulWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;

  const TasksScreen({
    super.key,
    required this.theme,
    required this.dataStore,
  });

  @override
  State<TasksScreen> createState() => _TasksScreenState();
}

class _TasksScreenState extends State<TasksScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabCtrl;
  String _selectedPriorityFilter = 'All';

  static const List<TaskStatus> _workflow = <TaskStatus>[
    TaskStatus.inbox,
    TaskStatus.assigned,
    TaskStatus.inProgress,
    TaskStatus.completed,
  ];

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
        const SnackBar(content: Text('Start a conversation before creating an action item.')),
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

  void _moveTask(ChatTask task, TaskStatus target) {
    if (task.status == target) return;
    widget.dataStore.updateTaskStatus(task.id, target);
  }

  TaskStatus? _previous(TaskStatus status) {
    final index = _workflow.indexOf(status);
    if (index <= 0) return null;
    return _workflow[index - 1];
  }

  TaskStatus? _next(TaskStatus status) {
    final index = _workflow.indexOf(status);
    if (index < 0 || index >= _workflow.length - 1) return null;
    return _workflow[index + 1];
  }

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;
    final dataStore = widget.dataStore;
    final allTasks = dataStore.tasks;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      'Tasks & Action Items',
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 22 * theme.fontScale,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.5,
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Create task',
                    icon: const Icon(Icons.add_task_rounded),
                    onPressed: () => _createTask(theme),
                  ),
                ],
              ),
            ),
            TabBar(
              controller: _tabCtrl,
              labelColor: theme.accentColor,
              unselectedLabelColor: theme.secondaryTextColor,
              indicatorColor: theme.accentColor,
              tabs: const [
                Tab(icon: Icon(Icons.list_alt_rounded, size: 18), text: 'Task List'),
                Tab(icon: Icon(Icons.view_kanban_outlined, size: 18), text: 'Kanban Board'),
              ],
            ),
            Align(
              alignment: Alignment.centerLeft,
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.fromLTRB(16, 6, 16, 8),
                child: Row(
                  children: ['All', 'Urgent', 'High', 'Med', 'Low'].map((p) {
                    final isSelected = _selectedPriorityFilter == p;
                    return Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(p),
                        selected: isSelected,
                        selectedColor: theme.accentColor.withValues(alpha: 0.25),
                        backgroundColor: theme.cardColor,
                        labelStyle: TextStyle(
                          color: isSelected ? theme.accentColor : theme.secondaryTextColor,
                          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                          fontSize: 12,
                        ),
                        onSelected: (value) {
                          if (value) setState(() => _selectedPriorityFilter = p);
                        },
                      ),
                    );
                  }).toList(growable: false),
                ),
              ),
            ),
            Expanded(
              child: TabBarView(
                controller: _tabCtrl,
                children: [
                  _buildListView(allTasks, theme, dataStore),
                  _buildKanbanView(allTasks, theme, dataStore),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<ChatTask> _filtered(List<ChatTask> tasks) {
    return tasks.where((task) {
      if (_selectedPriorityFilter == 'Urgent') return task.priority == TaskPriority.urgent;
      if (_selectedPriorityFilter == 'High') return task.priority == TaskPriority.high;
      if (_selectedPriorityFilter == 'Med') return task.priority == TaskPriority.medium;
      if (_selectedPriorityFilter == 'Low') return task.priority == TaskPriority.low;
      return true;
    }).toList(growable: false);
  }

  Widget _buildListView(List<ChatTask> tasks, ThemeConfig theme, MockDataStore dataStore) {
    final filtered = _filtered(tasks);
    if (filtered.isEmpty) return _emptyState(theme);

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: filtered.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final task = filtered[index];
        final isOverdue = task.isOverdue;
        return InkWell(
          borderRadius: BorderRadius.circular(theme.cornerRadius),
          onTap: () => _openTask(task, theme),
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: theme.cardColor,
              borderRadius: BorderRadius.circular(theme.cornerRadius),
              border: Border.all(
                color: isOverdue ? theme.dangerColor.withValues(alpha: 0.5) : theme.surfaceColor,
              ),
            ),
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
                        style: TextStyle(color: theme.dangerColor, fontSize: 10, fontWeight: FontWeight.w800),
                      ),
                    Icon(Icons.chevron_right_rounded, color: theme.secondaryTextColor),
                  ],
                ),
                const SizedBox(height: 10),
                Text(
                  task.title,
                  style: TextStyle(
                    color: theme.primaryTextColor,
                    fontSize: 15 * theme.fontScale,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                if (task.description.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    task.description,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(color: theme.secondaryTextColor, fontSize: 13 * theme.fontScale),
                  ),
                ],
                const SizedBox(height: 12),
                Row(
                  children: [
                    ...task.assigneeIds.take(4).map((id) {
                      final contact = dataStore.getUser(id);
                      return Padding(
                        padding: const EdgeInsets.only(right: 5),
                        child: AppAvatar(
                          initials: contact?.avatarInitials ?? 'CU',
                          colorHex: contact?.avatarColorHex ?? '0xFF6366F1',
                          size: 26,
                        ),
                      );
                    }),
                    const Spacer(),
                    Text(
                      'Due ${task.dueAt.day}/${task.dueAt.month}',
                      style: TextStyle(
                        color: isOverdue ? theme.dangerColor : theme.secondaryTextColor,
                        fontSize: 11.5,
                        fontWeight: isOverdue ? FontWeight.bold : FontWeight.normal,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _emptyState(ThemeConfig theme) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.task_alt_rounded, size: 54, color: theme.accentColor),
            const SizedBox(height: 18),
            Text('No action items yet', style: TextStyle(color: theme.primaryTextColor, fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Text(
              'Create a task from a chat with /task, from a message, or with the add button.',
              textAlign: TextAlign.center,
              style: TextStyle(color: theme.secondaryTextColor, height: 1.4),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildKanbanView(List<ChatTask> tasks, ThemeConfig theme, MockDataStore dataStore) {
    final filtered = _filtered(tasks);
    final columns = <(TaskStatus, String)>[
      (TaskStatus.inbox, 'Inbox'),
      (TaskStatus.assigned, 'Assigned'),
      (TaskStatus.inProgress, 'In Progress'),
      (TaskStatus.completed, 'Completed'),
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        final columnWidth = constraints.maxWidth < 500 ? constraints.maxWidth * 0.82 : 280.0;
        return SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.all(12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: columns.map((column) {
              final status = column.$1;
              final title = column.$2;
              final columnTasks = filtered.where((task) => task.status == status).toList(growable: false);
              return DragTarget<ChatTask>(
                onWillAcceptWithDetails: (details) => details.data.status != status,
                onAcceptWithDetails: (details) => _moveTask(details.data, status),
                builder: (context, candidates, rejected) {
                  final highlighted = candidates.isNotEmpty;
                  return AnimatedContainer(
                    duration: const Duration(milliseconds: 160),
                    width: columnWidth,
                    constraints: BoxConstraints(minHeight: constraints.maxHeight - 24),
                    margin: const EdgeInsets.only(right: 12),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: highlighted
                          ? theme.accentColor.withValues(alpha: 0.12)
                          : theme.cardColor.withValues(alpha: 0.65),
                      borderRadius: BorderRadius.circular(theme.cornerRadius),
                      border: Border.all(
                        color: highlighted ? theme.accentColor : theme.surfaceColor,
                        width: highlighted ? 1.5 : 1,
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(title, style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.bold, fontSize: 14)),
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                              decoration: BoxDecoration(color: theme.surfaceColor, borderRadius: BorderRadius.circular(10)),
                              child: Text('${columnTasks.length}', style: TextStyle(color: theme.secondaryTextColor, fontSize: 11, fontWeight: FontWeight.bold)),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        if (columnTasks.isEmpty)
                          Padding(
                            padding: const EdgeInsets.symmetric(vertical: 28),
                            child: Center(
                              child: Text('Drop tasks here', style: TextStyle(color: theme.secondaryTextColor, fontSize: 12)),
                            ),
                          ),
                        ...columnTasks.map((task) => _kanbanCard(task, theme)),
                      ],
                    ),
                  );
                },
              );
            }).toList(growable: false),
          ),
        );
      },
    );
  }

  Widget _kanbanCard(ChatTask task, ThemeConfig theme) {
    final previous = _previous(task.status);
    final next = _next(task.status);
    final card = Material(
      color: theme.cardColor,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => _openTask(task, theme),
        child: Padding(
          padding: const EdgeInsets.all(11),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                task.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w700, fontSize: 13 * theme.fontScale),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  PriorityBadge(priority: task.priority),
                  const Spacer(),
                  if (previous != null)
                    IconButton(
                      visualDensity: VisualDensity.compact,
                      tooltip: 'Move back',
                      onPressed: () => _moveTask(task, previous),
                      icon: Icon(Icons.chevron_left_rounded, size: 20, color: theme.accentColor),
                    ),
                  if (next != null)
                    IconButton(
                      visualDensity: VisualDensity.compact,
                      tooltip: 'Move forward',
                      onPressed: () => _moveTask(task, next),
                      icon: Icon(Icons.chevron_right_rounded, size: 20, color: theme.accentColor),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );

    return Padding(
      padding: const EdgeInsets.only(bottom: 9),
      child: LongPressDraggable<ChatTask>(
        data: task,
        feedback: Material(
          color: Colors.transparent,
          child: SizedBox(width: 240, child: Opacity(opacity: 0.92, child: card)),
        ),
        childWhenDragging: Opacity(opacity: 0.35, child: card),
        child: card,
      ),
    );
  }
}
