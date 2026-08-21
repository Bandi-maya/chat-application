import 'package:flutter/material.dart';

import '../../../ui/core/theme/theme_config.dart';
import '../../domain/models/chat_task.dart';
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
    final themeData = Theme.of(context);
    final isDark = themeData.brightness == Brightness.dark;
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
                    style: ChatyTypography.headline(
                      themeData.colorScheme.onSurface,
                    ),
                  ),
                ),
                ChatyIconButton(
                  icon: Icons.add_task_rounded,
                  tooltip: 'Create task',
                  backgroundColor:
                      isDark
                          ? const Color(0xFF27272A)
                          : const Color(0xFFF4F4F5),
                  color: themeData.colorScheme.primary,
                  onPressed: () => _createTask(widget.theme),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: ChatySpacing.base),
            child: TabBar(
              controller: _tabCtrl,
              labelColor: themeData.colorScheme.primary,
              unselectedLabelColor: themeData.colorScheme.onSurface.withValues(
                alpha: 0.55,
              ),
              indicatorColor: themeData.colorScheme.primary,
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
                          selectedColor: themeData.colorScheme.primary.withValues(
                            alpha: 0.15,
                          ),
                          backgroundColor:
                              isDark
                                  ? const Color(0xFF18181B)
                                  : const Color(0xFFF4F4F5),
                          labelStyle: TextStyle(
                            color: isSelected
                                ? themeData.colorScheme.primary
                                : themeData.colorScheme.onSurface.withValues(
                                    alpha: 0.65,
                                  ),
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
                            color: isSelected
                                ? themeData.colorScheme.primary
                                : (isDark
                                      ? const Color(0xFF27272A)
                                      : const Color(0xFFE4E4E7)),
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
          borderColor: isOverdue ? const Color(0xFFEF4444).withValues(alpha: 0.4) : null,
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
                    const Text(
                      'OVERDUE',
                      style: TextStyle(
                        color: Color(0xFFEF4444),
                        fontSize: 10.5,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.5,
                      ),
                    ),
                  const SizedBox(width: 4),
                  Icon(
                    Icons.chevron_right_rounded,
                    color: themeData.colorScheme.onSurface.withValues(alpha: 0.35),
                  ),
                ],
              ),
              const SizedBox(height: ChatySpacing.sm),
              Text(
                task.title,
                style: TextStyle(
                  color: themeData.colorScheme.onSurface,
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
                    themeData.colorScheme.onSurface.withValues(alpha: 0.65),
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
                        colorHex: contact?.avatarColorHex ?? '0xFF6366F1',
                        size: 24,
                      ),
                    );
                  }),
                  const Spacer(),
                  Text(
                    'Due ${task.dueAt.day}/${task.dueAt.month}',
                    style: TextStyle(
                      color: isOverdue
                          ? const Color(0xFFEF4444)
                          : themeData.colorScheme.onSurface.withValues(alpha: 0.55),
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
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(ChatySpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.task_alt_rounded,
              size: 48,
              color: themeData.colorScheme.primary,
            ),
            const SizedBox(height: ChatySpacing.base),
            Text(
              'No action items yet',
              style: ChatyTypography.title(themeData.colorScheme.onSurface),
            ),
            const SizedBox(height: ChatySpacing.xs),
            Text(
              'Create an action item from any message or using the add button.',
              textAlign: TextAlign.center,
              style: ChatyTypography.caption(
                themeData.colorScheme.onSurface.withValues(alpha: 0.6),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildKanbanView(
    List<ChatTask> tasks,
    ThemeConfig theme,
    MockDataStore dataStore,
    ThemeData themeData,
  ) {
    final filtered = _filtered(tasks);
    final isDark = themeData.brightness == Brightness.dark;
    final columns = <(TaskStatus, String)>[
      (TaskStatus.inbox, 'Inbox'),
      (TaskStatus.assigned, 'Assigned'),
      (TaskStatus.inProgress, 'In Progress'),
      (TaskStatus.completed, 'Completed'),
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        final columnWidth = constraints.maxWidth < 500
            ? constraints.maxWidth * 0.82
            : 280.0;
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
                    onWillAcceptWithDetails: (details) =>
                        details.data.status != status,
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
                              ? themeData.colorScheme.primary.withValues(alpha: 0.1)
                              : (isDark
                                    ? const Color(0xFF18181B)
                                    : const Color(0xFFF4F4F5)),
                          borderRadius: BorderRadius.circular(ChatyRadius.card),
                          border: Border.all(
                            color: highlighted
                                ? themeData.colorScheme.primary
                                : (isDark
                                      ? const Color(0xFF27272A)
                                      : const Color(0xFFE4E4E7)),
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
                                      color: themeData.colorScheme.onSurface,
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
                                    color: isDark
                                        ? const Color(0xFF27272A)
                                        : const Color(0xFFE4E4E7),
                                    borderRadius: BorderRadius.circular(
                                      ChatyRadius.full,
                                    ),
                                  ),
                                  child: Text(
                                    '${columnTasks.length}',
                                    style: TextStyle(
                                      color: themeData.colorScheme.onSurface,
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
                                      themeData.colorScheme.onSurface
                                          .withValues(alpha: 0.45),
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
    final isDark = themeData.brightness == Brightness.dark;

    final card = Container(
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF27272A) : Colors.white,
        borderRadius: BorderRadius.circular(ChatyRadius.md),
        border: Border.all(
          color: isDark ? const Color(0xFF3F3F46) : const Color(0xFFE4E4E7),
          width: 1.0,
        ),
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
                    color: themeData.colorScheme.onSurface,
                    fontWeight: FontWeight.w600,
                    fontSize: 13.5,
                  ),
                ),
                const SizedBox(height: ChatySpacing.sm),
                Row(
                  children: [
                    PriorityBadge(priority: task.priority),
                    const Spacer(),
                    if (previous != null)
                      ChatyIconButton(
                        size: 28,
                        iconSize: 18,
                        tooltip: 'Move back',
                        onPressed: () => _moveTask(task, previous),
                        icon: Icons.chevron_left_rounded,
                        color: themeData.colorScheme.primary,
                      ),
                    if (next != null)
                      ChatyIconButton(
                        size: 28,
                        iconSize: 18,
                        tooltip: 'Move forward',
                        onPressed: () => _moveTask(task, next),
                        icon: Icons.chevron_right_rounded,
                        color: themeData.colorScheme.primary,
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

