import 'package:flutter/material.dart';

import '../../../ui/core/theme/theme_config.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../domain/models/chat_task.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';

class TaskCreateEditModal extends StatefulWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;
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
    _selectedAssigneeIds =
        task?.assigneeIds.toList() ?? <String>[widget.dataStore.currentUser.id];
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
          backgroundColor: const Color(0xFF10B981),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _isSaving = false;
        _errorMessage = error.toString().replaceFirst('Exception: ', '');
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
    final theme = widget.theme;
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

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      decoration: BoxDecoration(
        color: theme.surfaceColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
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
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                    color: theme.secondaryTextColor.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    widget.existingTask != null
                        ? 'Edit Task'
                        : 'Create Task (/task)',
                    style: TextStyle(
                      color: theme.primaryTextColor,
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close_rounded),
                    onPressed: _isSaving ? null : () => Navigator.pop(context),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _titleCtrl,
                enabled: !_isSaving,
                maxLength: 140,
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 14.5,
                ),
                decoration: InputDecoration(
                  labelText: 'Task Title',
                  counterText: '',
                  labelStyle: TextStyle(color: theme.secondaryTextColor),
                  filled: true,
                  fillColor: theme.cardColor,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(theme.cornerRadius),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: _descCtrl,
                enabled: !_isSaving,
                maxLines: 3,
                maxLength: 2000,
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 13.5,
                ),
                decoration: InputDecoration(
                  labelText: 'Description / Instructions',
                  labelStyle: TextStyle(color: theme.secondaryTextColor),
                  filled: true,
                  fillColor: theme.cardColor,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(theme.cornerRadius),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'Assign To Participants',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  FilterChip(
                    label: Text(
                      'You (${currentUser.displayName.split(' ').first})',
                    ),
                    selected: _selectedAssigneeIds.contains(currentUser.id),
                    selectedColor: theme.accentColor.withValues(alpha: 0.25),
                    onSelected: _isSaving
                        ? null
                        : (selected) => _setAssignee(
                              currentUser.id,
                              selected,
                            ),
                  ),
                  ...candidateAssignees.map((candidate) {
                    final selected = _selectedAssigneeIds.contains(candidate.id);
                    return FilterChip(
                      label: Text(candidate.displayName.split(' ').first),
                      selected: selected,
                      selectedColor:
                          theme.accentColor.withValues(alpha: 0.25),
                      onSelected: _isSaving
                          ? null
                          : (value) => _setAssignee(candidate.id, value),
                    );
                  }),
                ],
              ),
              const SizedBox(height: 16),
              Text(
                'Priority',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
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
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: _isSaving ? null : _pickDueDate,
                icon: const Icon(Icons.event_rounded),
                label: Text(
                  'Due ${_dueDate.day.toString().padLeft(2, '0')}/'
                  '${_dueDate.month.toString().padLeft(2, '0')}/${_dueDate.year}',
                ),
              ),
              if (_errorMessage != null) ...[
                const SizedBox(height: 14),
                Text(
                  _errorMessage!,
                  style: TextStyle(
                    color: theme.dangerColor,
                    fontSize: 13,
                  ),
                ),
              ],
              const SizedBox(height: 20),
              ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: theme.accentColor,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(theme.cornerRadius),
                  ),
                ),
                onPressed: _isSaving ? null : _saveTask,
                icon: _isSaving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Icon(Icons.check_rounded, size: 18),
                label: Text(
                  _isSaving ? 'Saving…' : 'Save and Share in Chat',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
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
