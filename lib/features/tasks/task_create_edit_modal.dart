import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../domain/models/chat_task.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/design_system/design_system.dart';

class TaskCreateEditModal extends StatefulWidget {
  final ThemeConfig theme;
  final ChatyDataStore dataStore;
  final String sourceConversationId;
  final String? initialTitle;
  final String? sourceMessageId;
  final ChatTask? existingTask;

  const TaskCreateEditModal({
    super.key,
    required this.theme,
    required this.dataStore,
    required this.sourceConversationId,
    this.initialTitle,
    this.sourceMessageId,
    this.existingTask,
  });

  @override
  State<TaskCreateEditModal> createState() => _TaskCreateEditModalState();
}

class _TaskCreateEditModalState extends State<TaskCreateEditModal> {
  late final TextEditingController _titleCtrl;
  late final TextEditingController _descCtrl;
  late List<String> _selectedAssigneeIds;
  late TaskPriority _priority;
  late DateTime _dueDate;
  bool _isSaving = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    final task = widget.existingTask;
    _titleCtrl = TextEditingController(
      text: task?.title ?? widget.initialTitle ?? '',
    );
    _descCtrl = TextEditingController(text: task?.description ?? '');

    if (task != null) {
      _selectedAssigneeIds = task.assigneeIds.toList();
    } else {
      final me = widget.dataStore.currentUser.id;
      final conversation = widget.dataStore.conversations
          .where((item) => item.id == widget.sourceConversationId)
          .firstOrNull;
      final otherParticipant = conversation?.participantIds.firstWhere(
        (id) => id != me,
        orElse: () => '',
      );
      _selectedAssigneeIds = <String>[
        if (otherParticipant != null && otherParticipant.isNotEmpty)
          otherParticipant
        else
          me,
      ];
    }

    _priority = task?.priority ?? TaskPriority.medium;
    _dueDate = task?.dueAt ?? DateTime.now().add(const Duration(days: 3));
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _descCtrl.dispose();
    super.dispose();
  }

  Future<void> _saveTask() async {
    if (_isSaving) return;
    final title = _titleCtrl.text.trim();
    if (title.isEmpty) {
      setState(() => _errorMessage = 'Task title is required.');
      return;
    }
    if (_selectedAssigneeIds.isEmpty) {
      setState(() => _errorMessage = 'Select at least one assignee.');
      return;
    }

    setState(() {
      _isSaving = true;
      _errorMessage = null;
    });

    try {
      if (widget.existingTask != null) {
        await widget.dataStore.updateTaskAsync(
          taskId: widget.existingTask!.id,
          title: title,
          description: _descCtrl.text.trim(),
          assigneeIds: _selectedAssigneeIds,
          priority: _priority,
          dueAt: _dueDate,
          labels: widget.existingTask!.labels,
        );
      } else {
        await widget.dataStore.createTaskAsync(
          sourceConversationId: widget.sourceConversationId,
          sourceMessageId: widget.sourceMessageId,
          title: title,
          description: _descCtrl.text.trim(),
          assigneeIds: _selectedAssigneeIds,
          priority: _priority,
          dueAt: _dueDate,
          labels: const <String>['In-Chat', 'Workflow'],
        );
      }

      if (!mounted) return;
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            widget.existingTask == null
                ? 'Task "$title" created and shared in chat.'
                : 'Task "$title" updated.',
          ),
          backgroundColor: context.colors.success,
        ),
      );
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _isSaving = false;
        final message = error.toString().replaceFirst('Exception: ', '');
        _errorMessage = message.contains('23514')
            ? 'Task status could not be saved. Please retry after refreshing the chat.'
            : message;
      });
    }
  }

  Future<void> _pickDueDate() async {
    final selected = await showDatePicker(
      context: context,
      initialDate: _dueDate,
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 3650)),
    );
    if (selected == null || !mounted) return;
    setState(() {
      _dueDate = DateTime(
        selected.year,
        selected.month,
        selected.day,
        _dueDate.hour,
        _dueDate.minute,
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    final currentUser = widget.dataStore.currentUser;
    final conv = widget.dataStore.conversations.firstWhere(
      (conversation) => conversation.id == widget.sourceConversationId,
      orElse: () => Conversation(
        id: widget.sourceConversationId,
        type: ConversationType.direct,
        title: 'Chat',
        participantIds: <String>[currentUser.id],
        lastMessageText: '',
        lastMessageTime: DateTime.now(),
        lastMessageSenderId: currentUser.id,
      ),
    );

    final candidateAssignees = conv.participantIds
        .where((id) => id != currentUser.id)
        .map(widget.dataStore.getUser)
        .whereType<UserProfile>()
        .toList();

    final colors = context.colors;

    return Container(
      padding: EdgeInsets.only(
        left: ChatySpacing.lg,
        right: ChatySpacing.lg,
        top: ChatySpacing.md,
        bottom: MediaQuery.of(context).viewInsets.bottom + ChatySpacing.lg,
      ),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(ChatyRadius.xl),
        ),
      ),
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Center(
                child: Container(
                  width: 38,
                  height: 4.5,
                  decoration: BoxDecoration(
                    color: colors.foregroundTertiary,
                    borderRadius: BorderRadius.circular(ChatyRadius.full),
                  ),
                ),
              ),
              const SizedBox(height: ChatySpacing.base),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    widget.existingTask != null ? 'Edit Task' : 'New Task',
                    style: ChatyTypography.headline(colors.foreground),
                  ),
                  ChatyIconButton(
                    icon: Icons.close_rounded,
                    tooltip: 'Close',
                    onPressed: _isSaving ? null : () => Navigator.pop(context),
                  ),
                ],
              ),
              const SizedBox(height: ChatySpacing.base),
              ChatyInput(
                controller: _titleCtrl,
                enabled: !_isSaving,
                hintText: 'Task Title',
                prefixIcon: const Icon(Icons.check_box_outlined, size: 20),
              ),
              const SizedBox(height: ChatySpacing.md),
              ChatyInput(
                controller: _descCtrl,
                enabled: !_isSaving,
                hintText: 'Description & instructions (optional)',
                maxLines: 3,
                prefixIcon: const Icon(Icons.notes_rounded, size: 20),
              ),
              const SizedBox(height: ChatySpacing.base),
              Text(
                'Assign To',
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'Select who is responsible for this task:',
                style: ChatyTypography.caption(colors.foregroundSecondary),
              ),
              const SizedBox(height: ChatySpacing.sm),
              Wrap(
                spacing: ChatySpacing.sm,
                runSpacing: ChatySpacing.sm,
                children: [
                  ...candidateAssignees.map((candidate) {
                    final selected = _selectedAssigneeIds.contains(
                      candidate.id,
                    );
                    return FilterChip(
                      avatar: Icon(
                        selected
                            ? Icons.check_circle_rounded
                            : Icons.person_outline_rounded,
                        size: 16,
                      ),
                      label: Text(candidate.displayName.split(' ').first),
                      selected: selected,
                      selectedColor: colors.primary.withValues(alpha: 0.2),
                      onSelected: _isSaving
                          ? null
                          : (value) => _setAssignee(candidate.id, value),
                    );
                  }),
                  FilterChip(
                    avatar: const Icon(Icons.person_rounded, size: 16),
                    label: Text(
                      'Me (${currentUser.displayName.split(' ').first})',
                    ),
                    selected: _selectedAssigneeIds.contains(currentUser.id),
                    selectedColor: colors.primary.withValues(alpha: 0.2),
                    onSelected: _isSaving
                        ? null
                        : (selected) => _setAssignee(currentUser.id, selected),
                  ),
                ],
              ),
              const SizedBox(height: ChatySpacing.base),
              Text(
                'Priority',
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: ChatySpacing.sm),
              SegmentedButton<TaskPriority>(
                segments: const <ButtonSegment<TaskPriority>>[
                  ButtonSegment<TaskPriority>(
                    value: TaskPriority.low,
                    label: Text('Low'),
                  ),
                  ButtonSegment<TaskPriority>(
                    value: TaskPriority.medium,
                    label: Text('Med'),
                  ),
                  ButtonSegment<TaskPriority>(
                    value: TaskPriority.high,
                    label: Text('High'),
                  ),
                  ButtonSegment<TaskPriority>(
                    value: TaskPriority.urgent,
                    label: Text('Urgent'),
                  ),
                ],
                selected: <TaskPriority>{_priority},
                onSelectionChanged: _isSaving
                    ? null
                    : (value) => setState(() => _priority = value.first),
              ),
              const SizedBox(height: ChatySpacing.base),
              Text(
                'Due Date',
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: ChatySpacing.xs),
              Wrap(
                spacing: ChatySpacing.xs,
                children: [
                  ActionChip(
                    label: const Text('Today 6 PM'),
                    onPressed: _isSaving
                        ? null
                        : () {
                            final now = DateTime.now();
                            setState(() {
                              _dueDate = DateTime(now.year, now.month, now.day, 18, 0);
                            });
                          },
                  ),
                  ActionChip(
                    label: const Text('Tomorrow'),
                    onPressed: _isSaving
                        ? null
                        : () {
                            final tomorrow = DateTime.now().add(const Duration(days: 1));
                            setState(() {
                              _dueDate = DateTime(tomorrow.year, tomorrow.month, tomorrow.day, 18, 0);
                            });
                          },
                  ),
                  ActionChip(
                    label: const Text('Next Week'),
                    onPressed: _isSaving
                        ? null
                        : () {
                            final nextWeek = DateTime.now().add(const Duration(days: 7));
                            setState(() {
                              _dueDate = DateTime(nextWeek.year, nextWeek.month, nextWeek.day, 18, 0);
                            });
                          },
                  ),
                ],
              ),
              const SizedBox(height: ChatySpacing.xs),
              ChatySecondaryButton(
                text:
                    'Due ${_dueDate.day.toString().padLeft(2, '0')}/${_dueDate.month.toString().padLeft(2, '0')}/${_dueDate.year} at ${_dueDate.hour.toString().padLeft(2, '0')}:${_dueDate.minute.toString().padLeft(2, '0')}',
                icon: Icons.event_rounded,
                onPressed: _isSaving ? null : _pickDueDate,
              ),
              if (_errorMessage != null) ...[
                const SizedBox(height: ChatySpacing.sm),
                Text(
                  _errorMessage!,
                  style: TextStyle(color: colors.error, fontSize: 13),
                ),
              ],
              const SizedBox(height: ChatySpacing.lg),
              ChatyPrimaryButton(
                text: widget.existingTask == null
                    ? 'Create & Share in Chat'
                    : 'Save Changes',
                isLoading: _isSaving,
                onPressed: _saveTask,
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _setAssignee(String userId, bool selected) {
    setState(() {
      if (selected) {
        if (!_selectedAssigneeIds.contains(userId)) {
          _selectedAssigneeIds.add(userId);
        }
      } else {
        _selectedAssigneeIds.remove(userId);
      }
    });
  }
}
