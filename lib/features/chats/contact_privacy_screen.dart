import 'package:flutter/material.dart';

import '../../data/services/contact_relationship_service.dart';
import '../../domain/models/contact_relationship.dart';
import '../../domain/models/user_profile.dart';

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
      final value = await widget.relationshipService.privacyFor(widget.contact.id);
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
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    return SwitchListTile.adaptive(
      secondary: Icon(icon),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
      subtitle: Text(subtitle),
      value: value,
      onChanged: _saving ? null : onChanged,
    );
  }

  @override
  Widget build(BuildContext context) {
    final value = _value;
    return Scaffold(
      appBar: AppBar(title: Text('Privacy with ${widget.contact.displayName}')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : value == null
              ? Center(child: Text(_error ?? 'Unable to load contact privacy.'))
              : ListView(
                  padding: const EdgeInsets.only(bottom: 32),
                  children: [
                    Container(
                      margin: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.primaryContainer.withValues(alpha: 0.45),
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: const Text(
                        'These controls only change what this specific contact can observe from you. They are enforced by the server for receipts, activity and presence.',
                        style: TextStyle(height: 1.35),
                      ),
                    ),
                    if (_error != null)
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                        child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                      ),
                    _tile(
                      icon: Icons.done_rounded,
                      title: 'Hide delivery receipt (second tick)',
                      subtitle: 'Messages received by you will not expose the delivered / second-tick state to this contact.',
                      value: value.hideDeliveryReceipts,
                      onChanged: (next) => _save(value.copyWith(hideDeliveryReceipts: next)),
                    ),
                    _tile(
                      icon: Icons.done_all_rounded,
                      title: 'Hide read receipt (blue ticks)',
                      subtitle: 'Reading this contact’s messages will not expose read receipts to them.',
                      value: value.hideReadReceipts,
                      onChanged: (next) => _save(value.copyWith(hideReadReceipts: next)),
                    ),
                    const Divider(height: 1),
                    _tile(
                      icon: Icons.keyboard_alt_outlined,
                      title: 'Hide typing',
                      subtitle: 'Do not publish your typing state to this contact.',
                      value: value.hideTyping,
                      onChanged: (next) => _save(value.copyWith(hideTyping: next)),
                    ),
                    _tile(
                      icon: Icons.mic_none_rounded,
                      title: 'Hide voice recording',
                      subtitle: 'Do not publish “recording…” while you record a voice message for this contact.',
                      value: value.hideRecording,
                      onChanged: (next) => _save(value.copyWith(hideRecording: next)),
                    ),
                    const Divider(height: 1),
                    _tile(
                      icon: Icons.circle,
                      title: 'Hide online status',
                      subtitle: 'This contact will receive an offline presence projection even while you are online.',
                      value: value.hideOnline,
                      onChanged: (next) => _save(value.copyWith(hideOnline: next)),
                    ),
                    _tile(
                      icon: Icons.schedule_rounded,
                      title: 'Hide last seen',
                      subtitle: 'This contact will not receive your last-seen timestamp.',
                      value: value.hideLastSeen,
                      onChanged: (next) => _save(value.copyWith(hideLastSeen: next)),
                    ),
                  ],
                ),
    );
  }
}
