import 'package:flutter/material.dart';

import '../../../data/repositories/mock_data_store.dart';
import '../../../data/services/gb_feature_backend_service.dart';
import '../../../domain/models/preferences.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/settings_primitives.dart';
import '../../../ui/core/theme/app_theme.dart';

class MessageManagementPage extends StatefulWidget {
  final ChatyPreferencesController preferencesController;
  final MockDataStore dataStore;

  const MessageManagementPage({
    super.key,
    required this.preferencesController,
    required this.dataStore,
  });

  @override
  State<MessageManagementPage> createState() => _MessageManagementPageState();
}

class _MessageManagementPageState extends State<MessageManagementPage> {
  final GbFeatureBackendService _server = GbFeatureBackendService();
  late Future<List<ServerScheduledMessage>> _scheduledFuture;

  @override
  void initState() {
    super.initState();
    _scheduledFuture = _server.getScheduledMessages();
    _sync();
  }

  Future<void> _sync() async {
    try {
      await _server.synchronizeAutomation(
        widget.preferencesController.automation,
      );
      if (mounted) _refreshScheduled();
    } catch (_) {}
  }

  void _refreshScheduled() {
    setState(() => _scheduledFuture = _server.getScheduledMessages());
  }

  Future<void> _addAutoReplyRule() async {
    final keywordCtrl = TextEditingController();
    final messageCtrl = TextEditingController();
    String scope = 'all';
    final payload =
        await showDialog<({String keyword, String response, String scope})>(
          context: context,
          builder: (ctx) => StatefulBuilder(
            builder: (context, setDialogState) => AlertDialog(
              title: const Text('Add auto-reply rule'),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: keywordCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Keyword trigger',
                      hintText: 'busy, help, pricing',
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: messageCtrl,
                    maxLines: 3,
                    decoration: const InputDecoration(
                      labelText: 'Automatic response',
                    ),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    initialValue: scope,
                    decoration: const InputDecoration(labelText: 'Scope'),
                    items: const [
                      DropdownMenuItem(
                        value: 'all',
                        child: Text('All conversations'),
                      ),
                      DropdownMenuItem(
                        value: 'direct',
                        child: Text('Direct chats only'),
                      ),
                      DropdownMenuItem(
                        value: 'group',
                        child: Text('Groups only'),
                      ),
                    ],
                    onChanged: (value) =>
                        setDialogState(() => scope = value ?? 'all'),
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: () {
                    final keyword = keywordCtrl.text.trim();
                    final response = messageCtrl.text.trim();
                    if (keyword.isNotEmpty && response.isNotEmpty)
                      Navigator.pop(ctx, (
                        keyword: keyword,
                        response: response,
                        scope: scope,
                      ));
                  },
                  child: const Text('Add rule'),
                ),
              ],
            ),
          ),
        );
    keywordCtrl.dispose();
    messageCtrl.dispose();
    if (payload == null) return;
    try {
      final id = await _server.createAutoReplyRule(
        keyword: payload.keyword,
        response: payload.response,
        scope: payload.scope,
      );
      final auto = widget.preferencesController.automation;
      final next = List<AutoReplyRule>.from(auto.autoReplyRules)
        ..add(
          AutoReplyRule(
            id: id,
            keyword: payload.keyword,
            responseMessage: payload.response,
            recipientFilter: payload.scope,
          ),
        );
      widget.preferencesController.updateAutomation(
        auto.copyWith(autoReplyRules: next),
        logTitle: 'Add server auto-reply rule',
      );
      _toast('Auto-reply rule is active on the server.');
    } catch (error) {
      _toast('Unable to add rule: $error');
    }
  }

  Future<void> _deleteRule(AutoReplyRule rule) async {
    try {
      if (rule.id.length == 36) {
        await _server.deleteAutoReplyRule(rule.id);
      } else {
        await _server.deleteAutoReplyRuleBySignature(
          rule.keyword,
          rule.responseMessage,
        );
      }
      final auto = widget.preferencesController.automation;
      widget.preferencesController.updateAutomation(
        auto.copyWith(
          autoReplyRules: auto.autoReplyRules
              .where((item) => item.id != rule.id)
              .toList(),
        ),
        logTitle: 'Delete server auto-reply rule',
      );
    } catch (error) {
      _toast('Unable to delete rule: $error');
    }
  }

  Future<void> _scheduleMessage() async {
    if (widget.dataStore.conversations.isEmpty) {
      _toast('Start a conversation before scheduling a message.');
      return;
    }
    String conversationId = widget.dataStore.conversations.first.id;
    final textCtrl = TextEditingController();
    final payload = await showDialog<({String conversationId, String body})>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Schedule message'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<String>(
                initialValue: conversationId,
                decoration: const InputDecoration(labelText: 'Conversation'),
                items: widget.dataStore.conversations
                    .map(
                      (conversation) => DropdownMenuItem(
                        value: conversation.id,
                        child: Text(
                          conversation.title,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    )
                    .toList(growable: false),
                onChanged: (value) => setDialogState(
                  () => conversationId = value ?? conversationId,
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: textCtrl,
                maxLines: 4,
                decoration: const InputDecoration(labelText: 'Message'),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                final body = textCtrl.text.trim();
                if (body.isNotEmpty)
                  Navigator.pop(ctx, (
                    conversationId: conversationId,
                    body: body,
                  ));
              },
              child: const Text('Choose time'),
            ),
          ],
        ),
      ),
    );
    textCtrl.dispose();
    if (payload == null || !mounted) return;

    final initial = DateTime.now().add(const Duration(minutes: 5));
    final date = await showDatePicker(
      context: context,
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
      initialDate: initial,
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(initial),
    );
    if (time == null) return;
    final scheduledAt = DateTime(
      date.year,
      date.month,
      date.day,
      time.hour,
      time.minute,
    );
    if (!scheduledAt.isAfter(DateTime.now())) {
      _toast('Choose a future date and time.');
      return;
    }

    try {
      final id = await _server.scheduleMessage(
        conversationId: payload.conversationId,
        body: payload.body,
        scheduledAt: scheduledAt,
      );
      final conversation = widget.dataStore.conversations.firstWhere(
        (item) => item.id == payload.conversationId,
      );
      final auto = widget.preferencesController.automation;
      final next = List<ScheduledMessageEntry>.from(auto.scheduledMessages)
        ..add(
          ScheduledMessageEntry(
            id: id,
            recipientId: conversation.id,
            recipientName: conversation.title,
            text: payload.body,
            scheduledAt: scheduledAt,
          ),
        );
      widget.preferencesController.updateAutomation(
        auto.copyWith(scheduledMessages: next),
        logTitle: 'Schedule server message',
      );
      _refreshScheduled();
      _toast('Scheduled on the server for ${_formatDateTime(scheduledAt)}.');
    } catch (error) {
      _toast('Unable to schedule message: $error');
    }
  }

  Future<void> _cancelScheduled(ServerScheduledMessage item) async {
    try {
      await _server.cancelScheduledMessage(item.id);
      _refreshScheduled();
    } catch (error) {
      _toast('Unable to cancel: $error');
    }
  }

  Future<void> _addQuickReply() async {
    final shortcutCtrl = TextEditingController(text: '#');
    final titleCtrl = TextEditingController();
    final contentCtrl = TextEditingController();
    final template = await showDialog<QuickReplyTemplate>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Add quick reply'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: shortcutCtrl,
              decoration: const InputDecoration(
                labelText: 'Shortcut',
                hintText: '#thanks',
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: titleCtrl,
              decoration: const InputDecoration(labelText: 'Title'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: contentCtrl,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Template content'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              final shortcut = shortcutCtrl.text.trim();
              final content = contentCtrl.text.trim();
              if (shortcut.isNotEmpty && content.isNotEmpty) {
                Navigator.pop(
                  ctx,
                  QuickReplyTemplate(
                    shortcut: shortcut,
                    title: titleCtrl.text.trim().isEmpty
                        ? shortcut
                        : titleCtrl.text.trim(),
                    content: content,
                  ),
                );
              }
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
    shortcutCtrl.dispose();
    titleCtrl.dispose();
    contentCtrl.dispose();
    if (template == null) return;
    try {
      await _server.upsertQuickReply(
        shortcut: template.shortcut,
        title: template.title,
        content: template.content,
      );
      final auto = widget.preferencesController.automation;
      final next = List<QuickReplyTemplate>.from(auto.quickReplies)
        ..removeWhere((item) => item.shortcut == template.shortcut)
        ..add(template);
      widget.preferencesController.updateAutomation(
        auto.copyWith(quickReplies: next),
        logTitle: 'Save server quick reply',
      );
    } catch (error) {
      _toast('Unable to save quick reply: $error');
    }
  }

  Future<void> _massSend() async {
    final conversations = widget.dataStore.conversations;
    if (conversations.isEmpty) return;
    final selected = <String>{};
    final bodyCtrl = TextEditingController();
    final result = await showDialog<({List<String> ids, String body})>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: const Text('Mass sender'),
          content: SizedBox(
            width: 420,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: bodyCtrl,
                  maxLines: 3,
                  decoration: const InputDecoration(labelText: 'Message'),
                ),
                const SizedBox(height: 10),
                Flexible(
                  child: ListView(
                    shrinkWrap: true,
                    children: conversations
                        .map((conversation) {
                          return CheckboxListTile(
                            value: selected.contains(conversation.id),
                            title: Text(conversation.title),
                            onChanged: (value) => update(
                              () => value == true
                                  ? selected.add(conversation.id)
                                  : selected.remove(conversation.id),
                            ),
                          );
                        })
                        .toList(growable: false),
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                final body = bodyCtrl.text.trim();
                if (selected.isNotEmpty && body.isNotEmpty)
                  Navigator.pop(ctx, (ids: selected.toList(), body: body));
              },
              child: const Text('Send'),
            ),
          ],
        ),
      ),
    );
    bodyCtrl.dispose();
    if (result == null) return;
    try {
      final sent = await _server.massSend(
        conversationIds: result.ids,
        body: result.body,
      );
      _toast('Message sent to $sent conversation${sent == 1 ? '' : 's'}.');
    } catch (error) {
      _toast('Mass send failed: $error');
    }
  }

  @override
  Widget build(BuildContext context) {
    final auto = widget.preferencesController.automation;
    return ChatySettingsPage(
      title: 'Message Management',
      subtitle: 'Server Auto-Reply, Scheduler, Mass Sender & Quick Replies',
      children: [
        ChatySettingsSection(
          title: 'Auto-reply automation',
          description:
              'Rules execute in Supabase after matching incoming messages, even when the app is backgrounded.',
          children: [
            ChatySwitchTile(
              icon: Icons.reply_all_rounded,
              iconColor: context.colors.primary,
              title: 'Enable auto-reply engine',
              subtitle: 'Account-level master switch',
              value: auto.enableAutoReply,
              onChanged: (value) {
                widget.preferencesController.updateAutomation(
                  auto.copyWith(enableAutoReply: value),
                  logTitle: 'Enable server auto reply',
                );
                _sync();
              },
            ),
            ...auto.autoReplyRules.map(
              (rule) => ChatySettingsTile(
                icon: Icons.subtitles_rounded,
                title: 'Trigger: "${rule.keyword}"',
                subtitle: 'Reply: ${rule.responseMessage}',
                badgeText: rule.enabled ? 'ACTIVE' : 'OFF',
                badgeColor: rule.enabled ? context.colors.success : context.colors.foregroundTertiary,
                trailing: IconButton(
                  icon: Icon(
                    Icons.delete_outline_rounded,
                    color: context.colors.error,
                  ),
                  onPressed: () => _deleteRule(rule),
                ),
              ),
            ),
            ChatySettingsTile(
              icon: Icons.add_circle_outline_rounded,
              title: 'Add server auto-reply rule',
              onTap: _addAutoReplyRule,
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Message scheduler',
          description:
              'Scheduled messages are persisted and processed every minute on the server.',
          children: [
            FutureBuilder<List<ServerScheduledMessage>>(
              future: _scheduledFuture,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.waiting)
                  return const ListTile(
                    title: Text('Loading scheduled messages…'),
                    trailing: CircularProgressIndicator(),
                  );
                if (snapshot.hasError)
                  return ListTile(
                    title: const Text('Unable to load scheduler'),
                    subtitle: Text('${snapshot.error}'),
                  );
                final items = snapshot.data ?? const <ServerScheduledMessage>[];
                if (items.isEmpty)
                  return const ListTile(title: Text('No scheduled messages'));
                return Column(
                  children: items
                      .map((entry) {
                        final conv = widget.dataStore.conversations
                            .where((item) => item.id == entry.conversationId)
                            .firstOrNull;
                        return ChatySettingsTile(
                          icon: Icons.schedule_rounded,
                          title: conv?.title ?? 'Conversation',
                          subtitle:
                              '${entry.body}\n${_formatDateTime(entry.scheduledAt)}${entry.lastError == null ? '' : ' • ${entry.lastError}'}',
                          badgeText: entry.state.toUpperCase(),
                          badgeColor: entry.state == 'sent'
                              ? context.colors.success
                              : entry.state == 'failed'
                              ? context.colors.error
                              : context.colors.warning,
                          trailing: entry.state == 'pending'
                              ? IconButton(
                                  icon: const Icon(Icons.close_rounded),
                                  onPressed: () => _cancelScheduled(entry),
                                )
                              : null,
                        );
                      })
                      .toList(growable: false),
                );
              },
            ),
            ChatySettingsTile(
              icon: Icons.alarm_add_rounded,
              title: 'Schedule new message',
              subtitle: 'Choose conversation, date and time',
              onTap: _scheduleMessage,
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Mass sender',
          description:
              'Send one message to selected conversations. Server-side membership and configured forwarding limits are enforced.',
          children: [
            ChatySettingsTile(
              icon: Icons.send_time_extension_rounded,
              title: 'Open mass sender',
              subtitle: 'Select multiple conversations',
              onTap: _massSend,
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Quick replies',
          description:
              'Templates synchronize with your Chaty account and remain available across sessions.',
          children: [
            ...auto.quickReplies.map(
              (template) => ChatySettingsTile(
                icon: Icons.bolt_rounded,
                title: '${template.shortcut} (${template.title})',
                subtitle: template.content,
              ),
            ),
            ChatySettingsTile(
              icon: Icons.add_rounded,
              title: 'Add quick reply template',
              onTap: _addQuickReply,
            ),
          ],
        ),
      ],
    );
  }

  String _formatDateTime(DateTime value) {
    final local = value.toLocal();
    final minute = local.minute.toString().padLeft(2, '0');
    return '${local.day}/${local.month}/${local.year} ${local.hour}:$minute';
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
}
