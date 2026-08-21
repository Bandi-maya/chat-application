import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../../ui/core/theme/theme_config.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../domain/models/chat_task.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/widgets/status_badge.dart';
import '../../ui/core/design_system/design_system.dart';

class TaskDetailScreen extends StatefulWidget {
  final ChatTask task;
  final ThemeConfig theme;
  final MockDataStore dataStore;

  const TaskDetailScreen({
    super.key,
    required this.task,
    required this.theme,
    required this.dataStore,
  });

  @override
  State<TaskDetailScreen> createState() => _TaskDetailScreenState();
}

class _TaskDetailScreenState extends State<TaskDetailScreen> {
  late Future<List<Map<String, dynamic>>> _activityFuture;

  @override
  void initState() {
    super.initState();
    _activityFuture = _loadActivity();
  }

  Future<List<Map<String, dynamic>>> _loadActivity() async {
    final rows = await Supabase.instance.client
        .from('task_activity')
        .select('id,user_id,action,created_at')
        .eq('task_id', widget.task.id)
        .order('created_at', ascending: false);
    return (rows as List)
        .whereType<Map>()
        .map((row) => Map<String, dynamic>.from(row))
        .toList(growable: false);
  }

  UserProfile? _user(String id) {
    if (id == widget.dataStore.currentUser.id) {
      return widget.dataStore.currentUser;
    }
    return widget.dataStore.getUser(id);
  }

  String _formatDate(DateTime date) {
    final d = date.day.toString().padLeft(2, '0');
    final m = date.month.toString().padLeft(2, '0');
    final h = date.hour.toString().padLeft(2, '0');
    final min = date.minute.toString().padLeft(2, '0');
    return '$d/$m/${date.year} $h:$min';
  }

  @override
  Widget build(BuildContext context) {
    final task = widget.task;
    final creator = _user(task.creatorId);
    final themeData = Theme.of(context);

    return ChatyScaffold(
      appBar: const ChatyAppBar(
        title: 'Task Details',
        leading: ChatyBackButton(),
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.symmetric(
            horizontal: ChatySpacing.base,
            vertical: ChatySpacing.md,
          ),
          children: [
            ChatyCard(
              padding: const EdgeInsets.all(ChatySpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Wrap(
                    spacing: ChatySpacing.sm,
                    runSpacing: ChatySpacing.sm,
                    children: [
                      StatusBadge(status: task.status),
                      PriorityBadge(priority: task.priority),
                    ],
                  ),
                  const SizedBox(height: ChatySpacing.md),
                  Text(
                    task.title,
                    style: ChatyTypography.headline(
                      themeData.colorScheme.onSurface,
                    ),
                  ),
                  if (task.description.isNotEmpty) ...[
                    const SizedBox(height: ChatySpacing.sm),
                    Text(
                      task.description,
                      style: TextStyle(
                        color: themeData.colorScheme.onSurface.withValues(
                          alpha: 0.75,
                        ),
                        fontSize: 14.5,
                        height: 1.45,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: ChatySpacing.base),
            ChatyGroupedSection(
              title: 'Task Properties',
              children: [
                ChatyListTile(
                  leading: Icon(
                    Icons.fingerprint_rounded,
                    color: themeData.colorScheme.primary,
                    size: 20,
                  ),
                  title: Text(
                    'Task ID',
                    style: TextStyle(
                      color: themeData.colorScheme.onSurface,
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                  ),
                  subtitle: Text(
                    task.id,
                    style: TextStyle(
                      color: themeData.colorScheme.onSurface.withValues(
                        alpha: 0.6,
                      ),
                      fontFamily: 'monospace',
                      fontSize: 12,
                    ),
                  ),
                ),
                ChatyListTile(
                  leading: Icon(
                    Icons.person_outline_rounded,
                    color: themeData.colorScheme.primary,
                    size: 20,
                  ),
                  title: Text(
                    'Created By',
                    style: TextStyle(
                      color: themeData.colorScheme.onSurface,
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                  ),
                  subtitle: Text(
                    creator?.displayName ?? task.creatorId,
                    style: ChatyTypography.caption(
                      themeData.colorScheme.onSurface.withValues(alpha: 0.6),
                    ),
                  ),
                ),
                ChatyListTile(
                  leading: Icon(
                    Icons.calendar_today_outlined,
                    color: themeData.colorScheme.primary,
                    size: 20,
                  ),
                  title: Text(
                    'Created At',
                    style: TextStyle(
                      color: themeData.colorScheme.onSurface,
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                  ),
                  subtitle: Text(
                    _formatDate(task.createdAt),
                    style: ChatyTypography.caption(
                      themeData.colorScheme.onSurface.withValues(alpha: 0.6),
                    ),
                  ),
                ),
                ChatyListTile(
                  leading: const Icon(
                    Icons.alarm_rounded,
                    color: Color(0xFFF59E0B),
                    size: 20,
                  ),
                  title: Text(
                    'Due Date',
                    style: TextStyle(
                      color: themeData.colorScheme.onSurface,
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                  ),
                  subtitle: Text(
                    _formatDate(task.dueAt),
                    style: ChatyTypography.caption(
                      themeData.colorScheme.onSurface.withValues(alpha: 0.6),
                    ),
                  ),
                ),
                if (task.assigneeIds.isNotEmpty)
                  ChatyListTile(
                    leading: Icon(
                      Icons.group_outlined,
                      color: themeData.colorScheme.primary,
                      size: 20,
                    ),
                    title: Text(
                      'Assignees (${task.assigneeIds.length})',
                      style: TextStyle(
                        color: themeData.colorScheme.onSurface,
                        fontWeight: FontWeight.w600,
                        fontSize: 14,
                      ),
                    ),
                    subtitle: Padding(
                      padding: const EdgeInsets.only(top: ChatySpacing.xs),
                      child: Wrap(
                        spacing: ChatySpacing.xs,
                        children: task.assigneeIds.map((id) {
                          final user = _user(id);
                          return Chip(
                            avatar: AppAvatar(
                              initials: user?.avatarInitials ?? 'U',
                              colorHex: user?.avatarColorHex ?? '0xFF6366F1',
                              size: 18,
                            ),
                            label: Text(
                              user?.displayName ?? id,
                              style: const TextStyle(fontSize: 11.5),
                            ),
                            padding: EdgeInsets.zero,
                          );
                        }).toList(),
                      ),
                    ),
                  ),
                if (task.labels.isNotEmpty)
                  ChatyListTile(
                    leading: Icon(
                      Icons.label_outline_rounded,
                      color: themeData.colorScheme.primary,
                      size: 20,
                    ),
                    title: Text(
                      'Labels',
                      style: TextStyle(
                        color: themeData.colorScheme.onSurface,
                        fontWeight: FontWeight.w600,
                        fontSize: 14,
                      ),
                    ),
                    subtitle: Padding(
                      padding: const EdgeInsets.only(top: ChatySpacing.xs),
                      child: Wrap(
                        spacing: ChatySpacing.xs,
                        children: task.labels
                            .map(
                              (l) => Chip(
                                label: Text(
                                  l,
                                  style: const TextStyle(fontSize: 11.5),
                                ),
                              ),
                            )
                            .toList(),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: ChatySpacing.base),
            ChatyGroupedSection(
              title: 'Activity Timeline',
              children: [
                FutureBuilder<List<Map<String, dynamic>>>(
                  future: _activityFuture,
                  builder: (context, snapshot) {
                    if (snapshot.connectionState == ConnectionState.waiting) {
                      return const Padding(
                        padding: EdgeInsets.all(ChatySpacing.base),
                        child: Center(
                          child: CircularProgressIndicator(strokeWidth: 2.2),
                        ),
                      );
                    }
                    if (snapshot.hasError) {
                      return Padding(
                        padding: const EdgeInsets.all(ChatySpacing.base),
                        child: Text(
                          'Unable to load activity: ${snapshot.error}',
                          style: TextStyle(
                            color: themeData.colorScheme.error,
                            fontSize: 12.5,
                          ),
                        ),
                      );
                    }
                    final rows =
                        snapshot.data ?? const <Map<String, dynamic>>[];
                    if (rows.isEmpty) {
                      return Padding(
                        padding: const EdgeInsets.all(ChatySpacing.base),
                        child: Center(
                          child: Text(
                            'No changes recorded yet.',
                            style: ChatyTypography.caption(
                              themeData.colorScheme.onSurface.withValues(
                                alpha: 0.5,
                              ),
                            ),
                          ),
                        ),
                      );
                    }
                    return Column(
                      children: rows
                          .map((row) {
                            final userId = row['user_id']?.toString() ?? '';
                            final user = _user(userId);
                            final time = DateTime.tryParse(
                              row['created_at']?.toString() ?? '',
                            )?.toLocal();
                            return ChatyListTile(
                              leading: Container(
                                padding: const EdgeInsets.all(6),
                                decoration: BoxDecoration(
                                  color: themeData.colorScheme.primary
                                      .withValues(alpha: 0.12),
                                  shape: BoxShape.circle,
                                ),
                                child: Icon(
                                  Icons.history_rounded,
                                  color: themeData.colorScheme.primary,
                                  size: 16,
                                ),
                              ),
                              title: Text(
                                user?.displayName ?? userId,
                                style: TextStyle(
                                  color: themeData.colorScheme.onSurface,
                                  fontWeight: FontWeight.w600,
                                  fontSize: 13.5,
                                ),
                              ),
                              subtitle: Text(
                                '${row['action'] ?? 'updated task'}${time != null ? ' • ${_formatDate(time)}' : ''}',
                                style: ChatyTypography.caption(
                                  themeData.colorScheme.onSurface.withValues(
                                    alpha: 0.6,
                                  ),
                                ),
                              ),
                            );
                          })
                          .toList(growable: false),
                    );
                  },
                ),
              ],
            ),
            const SizedBox(height: ChatySpacing.xl),
          ],
        ),
      ),
    );
  }
}

