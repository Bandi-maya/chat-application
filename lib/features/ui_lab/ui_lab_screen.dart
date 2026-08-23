import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../ui/core/design_system/component_state.dart';
import '../../ui/core/design_system/components/messaging_components.dart';
import '../../ui/core/design_system/components/settings_components.dart';
import '../../ui/core/design_system/components/signature_components.dart';
import '../../ui/core/design_system/components/social_components.dart';
import 'ui_lab_models.dart';
import 'ui_lab_repository.dart';

class UiLabScreen extends StatefulWidget {
  const UiLabScreen({super.key});

  @override
  State<UiLabScreen> createState() => _UiLabScreenState();
}

class _UiLabScreenState extends State<UiLabScreen> {
  final UiLabRepository _repository = const UiLabRepository();
  UiLabScenario _scenario = UiLabScenario.normal;

  @override
  Widget build(BuildContext context) {
    assert(kDebugMode, 'UI Lab must not be used as a production data source.');
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Chaty UI Lab'),
        actions: [
          PopupMenuButton<UiLabScenario>(
            tooltip: 'Change scenario',
            initialValue: _scenario,
            onSelected: (value) => setState(() => _scenario = value),
            itemBuilder: (context) => UiLabScenario.values
                .map(
                  (scenario) => PopupMenuItem<UiLabScenario>(
                    value: scenario,
                    child: Text(_label(scenario)),
                  ),
                )
                .toList(growable: false),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 32),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            child: Text(
              'Scenario: ${_label(_scenario)}',
              style: TextStyle(
                color: scheme.primary,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          _section('Inbox', _conversationPreview(context)),
          _section('Message primitives', _messagePreview(context)),
          _section('Poll', _pollPreview()),
          _section('File states', _filePreview()),
          _section('Location', _locationPreview()),
          _section('Moments & streams', _socialPreview()),
          _section('Settings & privacy', _settingsPreview()),
        ],
      ),
    );
  }

  Widget _conversationPreview(BuildContext context) {
    final conversations = _repository.conversations(_scenario);
    if (conversations.isEmpty) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: Text('No conversations in this scenario.')),
      );
    }
    return Column(
      children: [
        for (final item in conversations.take(4))
          ChatyConversationTile(
            title: item.title,
            preview: item.preview,
            timeLabel: item.timeLabel,
            unreadCount: item.unreadCount,
            isPinned: item.isPinned,
            isMuted: item.isMuted,
            presence: item.isOnline
                ? ChatyPresenceState.online
                : ChatyPresenceState.offline,
            deliveryState: ChatyDeliveryState.read,
            avatar: CircleAvatar(child: Text(item.title.characters.first)),
            onTap: () => ChatyActivityIsland.show(
              context,
              icon: Icons.chat_bubble_rounded,
              title: 'Open ${item.title}',
              subtitle: 'UI Lab action only — no backend request was made.',
            ),
            onLongPress: () => ChatyGlassSheet.show<void>(
              context,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(18, 12, 18, 24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      item.title,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 12),
                    ChatyActionDock(
                      actions: [
                        ChatyDockAction(
                          icon: Icons.push_pin_rounded,
                          label: 'Pin',
                          onPressed: () {},
                        ),
                        ChatyDockAction(
                          icon: Icons.notifications_off_rounded,
                          label: 'Mute',
                          onPressed: () {},
                        ),
                        ChatyDockAction(
                          icon: Icons.archive_rounded,
                          label: 'Archive',
                          onPressed: () {},
                        ),
                        ChatyDockAction(
                          icon: Icons.lock_rounded,
                          label: 'Lock',
                          onPressed: () {},
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }

  Widget _messagePreview(BuildContext context) {
    final messages = _repository.messages(_scenario);
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const ChatyReplyPreview(
            author: 'Maya',
            preview: 'The new composer feels much faster.',
            icon: Icons.reply_rounded,
          ),
          const SizedBox(height: 12),
          for (final message in messages)
            Align(
              alignment: message.isMine
                  ? Alignment.centerRight
                  : Alignment.centerLeft,
              child: Padding(
                padding: const EdgeInsets.only(bottom: 9),
                child: Container(
                  constraints: const BoxConstraints(maxWidth: 300),
                  padding: const EdgeInsets.fromLTRB(12, 9, 10, 7),
                  decoration: BoxDecoration(
                    color: message.isMine
                        ? Theme.of(context).colorScheme.primaryContainer
                        : Theme.of(context).colorScheme.surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Text(message.text),
                      ),
                      const SizedBox(height: 4),
                      ChatyMessageMeta(
                        timeLabel: message.timeLabel,
                        deliveryState: ChatyDeliveryState.read,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          const ChatyPinnedRail(
            label: 'Release checklist is ready for review',
            position: 1,
            total: 3,
            onTap: _noop,
          ),
        ],
      ),
    );
  }

  Widget _pollPreview() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: ChatyPollCard(
        question: 'When should we ship the next test build?',
        totalVotes: 8,
        options: const [
          ChatyPollOption(label: 'Tonight', votes: 5, selected: true),
          ChatyPollOption(label: 'Tomorrow morning', votes: 2),
          ChatyPollOption(label: 'After device QA', votes: 1),
        ],
        onVote: (_) {},
      ),
    );
  }

  Widget _filePreview() {
    final state = switch (_scenario) {
      UiLabScenario.offline => ChatyComponentState.offline,
      UiLabScenario.uploading => ChatyComponentState.uploading,
      UiLabScenario.error || UiLabScenario.failed => ChatyComponentState.error,
      UiLabScenario.locked => ChatyComponentState.locked,
      _ => ChatyComponentState.ready,
    };
    return Padding(
      padding: const EdgeInsets.all(16),
      child: ChatyFileCard(
        name: 'Product-specification.pdf',
        extension: 'PDF',
        sizeLabel: '4.8 MB',
        state: state,
        progress: state == ChatyComponentState.uploading ? .64 : null,
        onTap: () {},
      ),
    );
  }

  Widget _locationPreview() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: ChatyLocationCard(
        title: 'Live location',
        subtitle: 'Central Visakhapatnam',
        live: true,
        remainingLabel: '18 min remaining',
        onTap: () {},
      ),
    );
  }

  Widget _socialPreview() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          SizedBox(
            height: 88,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: [
                ChatyMomentRing(
                  avatar: const ColoredBox(color: Color(0xFFE1F5EF)),
                  label: 'You',
                  isMine: true,
                  onTap: () {},
                ),
                ChatyMomentRing(
                  avatar: const ColoredBox(color: Color(0xFFFFE7DF)),
                  label: 'Maya',
                  onTap: () {},
                ),
                ChatyMomentRing(
                  avatar: const ColoredBox(color: Color(0xFFE5F0FF)),
                  label: 'Pavan',
                  seen: true,
                  onTap: () {},
                ),
              ],
            ),
          ),
          ChatyStreamCard(
            avatar: const CircleAvatar(child: Icon(Icons.campaign_rounded)),
            title: 'Chaty product stream',
            preview: 'A new test build is ready for device QA',
            timeLabel: '4m',
            unreadCount: 2,
            verified: true,
            onTap: () {},
          ),
        ],
      ),
    );
  }

  Widget _settingsPreview() {
    return ChatySettingsSection(
      title: 'Privacy & security',
      subtitle: 'Preview-only controls use deterministic local state.',
      children: [
        const ChatySettingTile(
          icon: Icons.shield_rounded,
          title: 'Secure messaging',
          subtitle: 'MLS device state and verification',
          trailing: ChatyPrivacyIndicator(label: 'Protected', secure: true),
        ),
        ChatySettingSwitch(
          icon: Icons.lock_rounded,
          title: 'App lock',
          subtitle: 'Require local authentication',
          value: _scenario == UiLabScenario.locked,
          onChanged: (_) {},
        ),
      ],
    );
  }

  Widget _section(String title, Widget child) {
    final scheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 18, 16, 7),
          child: Text(
            title,
            style: TextStyle(
              color: scheme.onSurface,
              fontSize: 17,
              fontWeight: FontWeight.w900,
            ),
          ),
        ),
        child,
      ],
    );
  }

  static void _noop() {}

  String _label(UiLabScenario scenario) {
    return switch (scenario) {
      UiLabScenario.loading => 'Loading',
      UiLabScenario.empty => 'Empty',
      UiLabScenario.normal => 'Normal',
      UiLabScenario.dense => 'Dense',
      UiLabScenario.error => 'Error',
      UiLabScenario.offline => 'Offline',
      UiLabScenario.permissionDenied => 'Permission denied',
      UiLabScenario.uploading => 'Uploading',
      UiLabScenario.failed => 'Failed',
      UiLabScenario.selected => 'Selected',
      UiLabScenario.locked => 'Locked',
    };
  }
}
