import 'package:flutter/material.dart';
import '../../data/repositories/chaty_data_store.dart';
import '../../domain/models/conversation.dart';
import '../../injection/locator.dart';
import 'scheduled_message_service.dart';

class ScheduledMessagesScreen extends StatefulWidget {
  const ScheduledMessagesScreen({super.key});

  @override
  State<ScheduledMessagesScreen> createState() => _ScheduledMessagesScreenState();
}

class _ScheduledMessagesScreenState extends State<ScheduledMessagesScreen> {
  late final ScheduledMessageService _service;
  late final ChatyDataStore _dataStore;

  @override
  void initState() {
    super.initState();
    _service = locator<ScheduledMessageService>();
    _dataStore = locator<ChatyDataStore>();
    _service.refresh();
  }

  Future<void> _openScheduleModal() async {
    final conversations = _dataStore.conversations;
    if (conversations.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No active conversations found to schedule for.')),
      );
      return;
    }

    Conversation selectedConversation = conversations.first;
    final messageCtrl = TextEditingController();
    DateTime selectedDateTime = DateTime.now().add(const Duration(hours: 1));

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setModalState) {
          final theme = Theme.of(context);
          return Container(
            padding: EdgeInsets.only(
              left: 20,
              right: 20,
              top: 24,
              bottom: MediaQuery.of(context).viewInsets.bottom + 24,
            ),
            decoration: BoxDecoration(
              color: theme.scaffoldBackgroundColor,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Icon(Icons.schedule_send_rounded, color: theme.colorScheme.primary),
                    const SizedBox(width: 10),
                    Text(
                      'Schedule Message',
                      style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                    ),
                    const Spacer(),
                    IconButton(
                      icon: const Icon(Icons.close_rounded),
                      onPressed: () => Navigator.pop(ctx),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                DropdownButtonFormField<Conversation>(
                  value: selectedConversation,
                  decoration: const InputDecoration(
                    labelText: 'Recipient / Chat',
                    border: OutlineInputBorder(),
                  ),
                  items: conversations.map((c) {
                    final title = c.type == ConversationType.direct
                        ? (c.title.isNotEmpty ? c.title : 'Direct Chat')
                        : (c.title.isNotEmpty ? c.title : 'Group Chat');
                    return DropdownMenuItem(
                      value: c,
                      child: Text(title, overflow: TextOverflow.ellipsis),
                    );
                  }).toList(),
                  onChanged: (val) {
                    if (val != null) setModalState(() => selectedConversation = val);
                  },
                ),
                const SizedBox(height: 14),
                TextField(
                  controller: messageCtrl,
                  maxLines: 3,
                  decoration: const InputDecoration(
                    labelText: 'Message Body',
                    hintText: 'Enter scheduled message...',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 14),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.calendar_today_rounded),
                  title: const Text('Schedule Date & Time'),
                  subtitle: Text(
                    '${selectedDateTime.year}-${selectedDateTime.month.toString().padLeft(2, '0')}-${selectedDateTime.day.toString().padLeft(2, '0')} at ${selectedDateTime.hour.toString().padLeft(2, '0')}:${selectedDateTime.minute.toString().padLeft(2, '0')}',
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () async {
                    final pickedDate = await showDatePicker(
                      context: context,
                      initialDate: selectedDateTime,
                      firstDate: DateTime.now(),
                      lastDate: DateTime.now().add(const Duration(days: 365)),
                    );
                    if (pickedDate == null || !mounted) return;

                    final pickedTime = await showTimePicker(
                      context: context,
                      initialTime: TimeOfDay.fromDateTime(selectedDateTime),
                    );
                    if (pickedTime == null || !mounted) return;

                    setModalState(() {
                      selectedDateTime = DateTime(
                        pickedDate.year,
                        pickedDate.month,
                        pickedDate.day,
                        pickedTime.hour,
                        pickedTime.minute,
                      );
                    });
                  },
                ),
                const SizedBox(height: 20),
                FilledButton.icon(
                  icon: const Icon(Icons.schedule_send_rounded),
                  label: const Text('Confirm Schedule'),
                  onPressed: () async {
                    if (messageCtrl.text.trim().isEmpty) return;
                    Navigator.pop(ctx);
                    try {
                      await _service.scheduleMessage(
                        conversationId: selectedConversation.id,
                        body: messageCtrl.text.trim(),
                        scheduledAt: selectedDateTime,
                      );
                      if (mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Message scheduled successfully!')),
                        );
                      }
                    } catch (e) {
                      if (mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text('Failed to schedule: $e')),
                        );
                      }
                    }
                  },
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Message Scheduler'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () => _service.refresh(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        icon: const Icon(Icons.add_rounded),
        label: const Text('Schedule'),
        onPressed: _openScheduleModal,
      ),
      body: AnimatedBuilder(
        animation: _service,
        builder: (context, _) {
          if (_service.isLoading && _service.messages.isEmpty) {
            return const Center(child: CircularProgressIndicator());
          }

          if (_service.messages.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.schedule_rounded, size: 64, color: theme.disabledColor),
                  const SizedBox(height: 16),
                  Text(
                    'No scheduled messages',
                    style: theme.textTheme.titleMedium?.copyWith(color: theme.disabledColor),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Tap + Schedule to queue messages for automatic delivery',
                    style: theme.textTheme.bodySmall?.copyWith(color: theme.disabledColor),
                  ),
                ],
              ),
            );
          }

          return ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
            itemCount: _service.messages.length,
            separatorBuilder: (_, __) => const SizedBox(height: 10),
            itemBuilder: (context, index) {
              final msg = _service.messages[index];
              final conversation = _dataStore.conversations
                  .where((c) => c.id == msg.conversationId)
                  .firstOrNull;
              final convTitle = conversation != null
                  ? (conversation.title.isNotEmpty
                      ? conversation.title
                      : 'Conversation')
                  : 'Chat (${msg.conversationId.substring(0, msg.conversationId.length > 8 ? 8 : msg.conversationId.length)})';

              final isPending = msg.state == 'pending';

              return Card(
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                  side: BorderSide(color: theme.dividerColor.withValues(alpha: 0.2)),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.chat_bubble_outline_rounded, size: 18, color: theme.colorScheme.primary),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              convTitle,
                              style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: isPending
                                  ? Colors.amber.withValues(alpha: 0.15)
                                  : msg.state == 'completed'
                                      ? Colors.green.withValues(alpha: 0.15)
                                      : Colors.red.withValues(alpha: 0.15),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              msg.state.toUpperCase(),
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: isPending
                                    ? Colors.amber.shade800
                                    : msg.state == 'completed'
                                        ? Colors.green.shade800
                                        : Colors.red.shade800,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        msg.body,
                        style: theme.textTheme.bodyMedium,
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Icon(Icons.access_time_rounded, size: 14, color: theme.hintColor),
                          const SizedBox(width: 6),
                          Text(
                            'Delivery: ${msg.scheduledAt.toLocal().toString().substring(0, 16)}',
                            style: theme.textTheme.bodySmall?.copyWith(color: theme.hintColor),
                          ),
                          const Spacer(),
                          if (isPending)
                            TextButton.icon(
                              icon: const Icon(Icons.cancel_outlined, size: 16),
                              label: const Text('Cancel'),
                              style: TextButton.styleFrom(
                                foregroundColor: theme.colorScheme.error,
                                visualDensity: VisualDensity.compact,
                              ),
                              onPressed: () => _service.cancelScheduledMessage(msg.id),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
