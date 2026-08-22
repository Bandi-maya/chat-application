import 'package:flutter/material.dart';

import '../../data/services/contact_relationship_service.dart';
import '../../domain/models/contact_relationship.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/design_system/design_system.dart';

class ContactPrivacyScreen extends StatefulWidget {
  final UserProfile contact;
  final ContactRelationshipService relationshipService;

  const ContactPrivacyScreen({
    super.key,
    required this.contact,
    required this.relationshipService,
  });

  @override
  State<ContactPrivacyScreen> createState() => _ContactPrivacyScreenState();
}

class _ContactPrivacyScreenState extends State<ContactPrivacyScreen> {
  ContactPrivacyOverride? _value;
  bool _loading = true;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final value = await widget.relationshipService.privacyFor(
        widget.contact.id,
      );
      if (!mounted) return;
      setState(() {
        _value = value;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = error.toString();
      });
    }
  }

  Future<void> _save(ContactPrivacyOverride next) async {
    if (_saving) return;
    final previous = _value;
    setState(() {
      _value = next;
      _saving = true;
      _error = null;
    });
    try {
      await widget.relationshipService.savePrivacy(next);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _value = previous;
        _error = error.toString().replaceFirst('Exception: ', '');
      });
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Widget _tile({
    required BuildContext context,
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    final theme = Theme.of(context);
    return ChatyListTile(
      leading: Icon(icon, color: theme.colorScheme.primary, size: 22),
      title: Text(
        title,
        style: TextStyle(
          color: theme.colorScheme.onSurface,
          fontWeight: FontWeight.w600,
          fontSize: 15,
        ),
      ),
      subtitle: Text(
        subtitle,
        style: ChatyTypography.caption(
          theme.colorScheme.onSurface.withValues(alpha: 0.6),
        ),
      ),
      trailing: Switch.adaptive(
        value: value,
        activeColor: theme.colorScheme.primary,
        onChanged: _saving ? null : onChanged,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final value = _value;
    final theme = Theme.of(context);

    return ChatyScaffold(
      appBar: ChatyAppBar(
        title: 'Privacy: ${widget.contact.displayName}',
        leading: const ChatyBackButton(),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2.2))
          : value == null
          ? Center(
              child: Text(
                _error ?? 'Unable to load contact privacy.',
                style: TextStyle(color: theme.colorScheme.error),
              ),
            )
          : ListView(
              padding: const EdgeInsets.symmetric(
                horizontal: ChatySpacing.base,
                vertical: ChatySpacing.md,
              ),
              children: [
                ChatyCard(
                  padding: const EdgeInsets.all(ChatySpacing.base),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.shield_outlined,
                        color: theme.colorScheme.primary,
                        size: 22,
                      ),
                      const SizedBox(width: ChatySpacing.md),
                      Expanded(
                        child: Text(
                          'These controls change what ${widget.contact.displayName} can observe from you. They are cryptographically and server-enforced.',
                          style: TextStyle(
                            color: theme.colorScheme.onSurface.withValues(
                              alpha: 0.85,
                            ),
                            fontSize: 13.5,
                            height: 1.4,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                if (_error != null) ...[
                  const SizedBox(height: ChatySpacing.sm),
                  Text(
                    _error!,
                    style: TextStyle(
                      color: theme.colorScheme.error,
                      fontSize: 13,
                    ),
                  ),
                ],
                const SizedBox(height: ChatySpacing.base),
                ChatyGroupedSection(
                  title: 'Message Receipts',
                  children: [
                    _tile(
                      context: context,
                      icon: Icons.done_rounded,
                      title: 'Hide delivery receipt (second tick)',
                      subtitle:
                          'Messages received by you will not expose the delivered state to this contact.',
                      value: value.hideDeliveryReceipts,
                      onChanged: (next) =>
                          _save(value.copyWith(hideDeliveryReceipts: next)),
                    ),
                    _tile(
                      context: context,
                      icon: Icons.done_all_rounded,
                      title: 'Hide read receipt (blue ticks)',
                      subtitle:
                          'Reading this contact’s messages will not expose read receipts to them.',
                      value: value.hideReadReceipts,
                      onChanged: (next) =>
                          _save(value.copyWith(hideReadReceipts: next)),
                    ),
                  ],
                ),
                ChatyGroupedSection(
                  title: 'Live Indicators',
                  children: [
                    _tile(
                      context: context,
                      icon: Icons.keyboard_alt_outlined,
                      title: 'Hide typing state',
                      subtitle:
                          'Do not publish your typing state to this contact.',
                      value: value.hideTyping,
                      onChanged: (next) =>
                          _save(value.copyWith(hideTyping: next)),
                    ),
                    _tile(
                      context: context,
                      icon: Icons.mic_none_rounded,
                      title: 'Hide voice recording state',
                      subtitle:
                          'Do not publish "recording…" while recording audio messages.',
                      value: value.hideRecording,
                      onChanged: (next) =>
                          _save(value.copyWith(hideRecording: next)),
                    ),
                  ],
                ),
                ChatyGroupedSection(
                  title: 'Presence & Timestamp',
                  children: [
                    _tile(
                      context: context,
                      icon: Icons.circle_outlined,
                      title: 'Hide online status',
                      subtitle:
                          'This contact will receive an offline status projection even while active.',
                      value: value.hideOnline,
                      onChanged: (next) =>
                          _save(value.copyWith(hideOnline: next)),
                    ),
                    _tile(
                      context: context,
                      icon: Icons.schedule_rounded,
                      title: 'Hide last seen',
                      subtitle:
                          'This contact will not receive your last-seen timestamp.',
                      value: value.hideLastSeen,
                      onChanged: (next) =>
                          _save(value.copyWith(hideLastSeen: next)),
                    ),
                  ],
                ),
                const SizedBox(height: ChatySpacing.xl),
              ],
            ),
    );
  }
}
