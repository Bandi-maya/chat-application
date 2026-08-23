import 'package:flutter/material.dart';

import '../../../data/repositories/chaty_data_store.dart';
import '../../../data/services/gb_feature_backend_service.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/settings_primitives.dart';
import '../../../ui/core/theme/app_theme.dart';

class PrivacyCenterScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const PrivacyCenterScreen({super.key, required this.preferencesController});

  @override
  State<PrivacyCenterScreen> createState() => _PrivacyCenterScreenState();
}

class _PrivacyCenterScreenState extends State<PrivacyCenterScreen> {
  static const List<String> _audienceOptions = <String>[
    'Everyone',
    'My Contacts',
    'My Contacts Except…',
    'Nobody',
  ];
  static const List<String> _whoCanCallMeOptions = <String>[
    'Everyone',
    'My Contacts',
    'My Contacts Except…',
    'Nobody',
  ];

  /// Multi-select picker backing the 'My Contacts Except…' audience for
  /// Who Can Call Me. Persists the excluded user IDs on save.
  Future<void> _editCallExceptions() async {
    final dataStore = locator<ChatyDataStore>();
    final selected = Set<String>.of(
      widget.preferencesController.privacy.whoCanCallMeExceptions,
    );
    final saved = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Contacts who may not call you'),
          content: SizedBox(
            width: double.maxFinite,
            child: dataStore.contacts.isEmpty
                ? const Padding(
                    padding: EdgeInsets.symmetric(vertical: 18),
                    child: Text('No contacts found.'),
                  )
                : ListView(
                    shrinkWrap: true,
                    children: [
                      for (final contact in dataStore.contacts)
                        CheckboxListTile(
                          value: selected.contains(contact.id),
                          onChanged: (value) {
                            setDialogState(() {
                              if (value == true) {
                                selected.add(contact.id);
                              } else {
                                selected.remove(contact.id);
                              }
                            });
                          },
                          title: Text(contact.displayName),
                        ),
                    ],
                  ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
    if (saved != true) return;
    widget.preferencesController.updatePrivacy(
      widget.preferencesController.privacy.copyWith(
        whoCanCallMeExceptions: selected.toList(growable: false),
      ),
      logTitle: 'Who Can Call Me Exceptions',
    );
  }

  final GbFeatureBackendService _privacyBackend = GbFeatureBackendService();
  late Future<List<Map<String, dynamic>>> _blockedFuture;

  @override
  void initState() {
    super.initState();
    _blockedFuture = _privacyBackend.getBlockedUsers();
  }

  void _refreshBlocked() =>
      setState(() => _blockedFuture = _privacyBackend.getBlockedUsers());

  @override
  Widget build(BuildContext context) {
    final prefs = widget.preferencesController.privacy;
    final colors = context.colors;

    return ChatySettingsPage(
      title: 'Privacy Center',
      subtitle: 'Server-enforced presence, receipts, status & chat privacy',
      children: [
        ChatySettingsSection(
          title: 'Last Seen & Online Presence',
          description:
              'Freeze your last seen timestamp or restrict audience visibility.',
          children: [
            ChatySwitchTile(
              icon: Icons.ac_unit_rounded,
              iconColor: colors.info,
              title: 'Freeze Last Seen',
              subtitle: prefs.freezeLastSeen
                  ? 'Frozen at ${prefs.frozenLastSeenTime.isNotEmpty ? prefs.frozenLastSeenTime : "now"}. Server updates no longer move this timestamp.'
                  : 'Stops updating your last visible timestamp to contacts.',
              value: prefs.freezeLastSeen,
              onChanged: (value) {
                final timestamp = value
                    ? DateTime.now().toLocal().toString().substring(0, 16)
                    : '';
                widget.preferencesController.updatePrivacy(
                  prefs.copyWith(
                    freezeLastSeen: value,
                    frozenLastSeenTime: timestamp,
                  ),
                  logTitle: 'Freeze Last Seen',
                  prevVal: prefs.freezeLastSeen,
                  newVal: value,
                );
              },
            ),
            ChatyChoiceTile<String>(
              title: 'Who Can See My Last Seen',
              options: _audienceOptions,
              selectedOption: prefs.hideLastSeenAudience,
              optionLabel: (value) => value,
              onSelected: (audience) =>
                  widget.preferencesController.updatePrivacy(
                    prefs.copyWith(hideLastSeenAudience: audience),
                    logTitle: 'Hide Last Seen Audience',
                  ),
            ),
            ChatyChoiceTile<String>(
              title: 'Who Can See When I\'m Online',
              options: const <String>['Everyone', 'Same as Last Seen'],
              selectedOption: prefs.hideOnlineAudience,
              optionLabel: (value) => value,
              onSelected: (audience) =>
                  widget.preferencesController.updatePrivacy(
                    prefs.copyWith(hideOnlineAudience: audience),
                    logTitle: 'Hide Online Audience',
                  ),
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Read Receipts & Presence',
          children: [
            ChatySwitchTile(
              icon: Icons.done_all_rounded,
              iconColor: colors.primary,
              title: 'Read Receipts (Blue Ticks)',
              subtitle:
                  'The server only publishes read receipts when this is enabled.',
              value: prefs.readReceipts,
              onChanged: (value) => widget.preferencesController.updatePrivacy(
                prefs.copyWith(readReceipts: value),
                logTitle: 'Read Receipts',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.mark_chat_read_rounded,
              iconColor: colors.primary,
              title: 'Show Blue Ticks After Reply',
              subtitle:
                  'Opening a chat clears your unread count but publishes receipts only after you reply.',
              value: prefs.showBlueTicksAfterReply,
              onChanged: (value) => widget.preferencesController.updatePrivacy(
                prefs.copyWith(showBlueTicksAfterReply: value),
                logTitle: 'Show Blue Ticks After Reply',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.edit_note_rounded,
              iconColor: colors.warning,
              title: 'Typing Indicators',
              subtitle:
                  'Realtime typing state is published only while this is enabled.',
              value: prefs.typingIndicators,
              onChanged: (value) => widget.preferencesController.updatePrivacy(
                prefs.copyWith(typingIndicators: value),
                logTitle: 'Typing Indicators',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.mic_none_rounded,
              iconColor: colors.error,
              title: 'Recording Indicators',
              subtitle: 'Allow voice-note recording presence to be shown.',
              value: prefs.recordingIndicators,
              onChanged: (value) => widget.preferencesController.updatePrivacy(
                prefs.copyWith(recordingIndicators: value),
                logTitle: 'Recording Indicators',
              ),
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Anti-Delete & View-Once Safeguards',
          description:
              'Chaty preserves the original server payload while applying your private local visibility preference.',
          children: [
            ChatySwitchTile(
              icon: Icons.delete_forever_rounded,
              iconColor: colors.error,
              title: 'Anti-Delete Messages',
              subtitle:
                  'Keep the original message visible to you after the sender deletes it.',
              value: prefs.antiDeleteMessages,
              onChanged: (value) {
                widget.preferencesController.updatePrivacy(
                  prefs.copyWith(antiDeleteMessages: value),
                  logTitle: 'Anti-Delete Messages',
                );
                widget.preferencesController.updateGbFeature(
                  'yoAntiRevoke',
                  value,
                  logTitle: 'Anti-Delete Messages',
                );
              },
            ),
            ChatySwitchTile(
              icon: Icons.history_toggle_off_rounded,
              iconColor: colors.warning,
              title: 'Anti-Delete Status / Stories',
              subtitle:
                  'Keep a deleted status available until its normal 24-hour expiry.',
              value: prefs.antiDeleteStatus,
              onChanged: (value) {
                widget.preferencesController.updatePrivacy(
                  prefs.copyWith(antiDeleteStatus: value),
                  logTitle: 'Anti-Delete Status',
                );
                widget.preferencesController.updateGbFeature(
                  'yoAntiRevokeStatus',
                  value,
                  logTitle: 'Anti-Delete Status',
                );
              },
            ),
            ChatySwitchTile(
              icon: Icons.remove_red_eye_rounded,
              iconColor: colors.info,
              title: 'Anti View-Once Media',
              subtitle:
                  'Retain opened view-once media in Chaty when the sender permissions allow the stored payload to remain available.',
              value: prefs.antiViewOnce,
              onChanged: (value) {
                widget.preferencesController.updatePrivacy(
                  prefs.copyWith(antiViewOnce: value),
                  logTitle: 'Anti View Once',
                );
                widget.preferencesController.updateGbFeature(
                  'anti_vw_once',
                  value,
                  logTitle: 'Anti View Once',
                );
              },
            ),
            ChatySwitchTile(
              icon: Icons.notification_important_rounded,
              iconColor: colors.accent,
              title: 'Message & Status Revoke Alerts',
              subtitle: 'Notify when a sender revokes a message or status.',
              value: prefs.messageRevokeAlert,
              onChanged: (value) {
                widget.preferencesController.updatePrivacy(
                  prefs.copyWith(
                    messageRevokeAlert: value,
                    statusRevocationAlert: value,
                  ),
                  logTitle: 'Revoke Alerts',
                );
                widget.preferencesController.updateGbFeatures(<String, Object?>{
                  'AntiRevokeMsgNotif': value,
                  'AntiRevokeStatusNotif': value,
                }, logTitle: 'Revoke alerts');
              },
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Blocked Users',
          description:
              'Blocking is enforced server-side for direct-message sends.',
          children: [
            FutureBuilder<List<Map<String, dynamic>>>(
              future: _blockedFuture,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.waiting) {
                  return const ListTile(
                    title: Text('Loading blocked users…'),
                    trailing: SizedBox.square(
                      dimension: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  );
                }
                final users = snapshot.data ?? const <Map<String, dynamic>>[];
                if (users.isEmpty)
                  return const ListTile(
                    title: Text('No blocked users'),
                    subtitle: Text('Blocked accounts will appear here.'),
                  );
                return Column(
                  children: users
                      .map(
                        (user) => ChatySettingsTile(
                          icon: Icons.block_rounded,
                          iconColor: colors.error,
                          title: user['display_name']?.toString() ?? 'User',
                          subtitle: '@${user['username'] ?? ''}',
                          trailing: TextButton(
                            onPressed: () async {
                              try {
                                await _privacyBackend.unblockUser(
                                  user['id'].toString(),
                                );
                                _refreshBlocked();
                              } catch (error) {
                                _toast('Unable to unblock: $error');
                              }
                            },
                            child: const Text('Unblock'),
                          ),
                        ),
                      )
                      .toList(growable: false),
                );
              },
            ),
            ChatySettingsTile(
              icon: Icons.person_off_outlined,
              iconColor: colors.error,
              title: 'Block a user',
              subtitle: 'Search Chaty users by name or @username',
              onTap: _openBlockSearch,
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Call & Forwarding Controls',
          children: [
            ChatyChoiceTile<String>(
              title: 'Who Can Call Me',
              options: _whoCanCallMeOptions,
              selectedOption: prefs.whoCanCallMe,
              optionLabel: (value) => value,
              onSelected: (audience) {
                widget.preferencesController.updatePrivacy(
                  prefs.copyWith(whoCanCallMe: audience),
                  logTitle: 'Who Can Call Me',
                );
                widget.preferencesController.updateGbFeature(
                  'yoCallsPrivacy',
                  audience,
                  logTitle: 'Who Can Call Me',
                );
              },
            ),
            // Real consumer support for 'My Contacts Except…': the exclusion
            // list is persisted and enforced at ring time by the realtime
            // call gate.
            if (prefs.whoCanCallMe == 'My Contacts Except…')
              ChatySettingsTile(
                icon: Icons.block_rounded,
                iconColor: colors.error,
                title: 'Manage call exceptions',
                subtitle: prefs.whoCanCallMeExceptions.isEmpty
                    ? 'No contacts are excluded from calling you'
                    : '${prefs.whoCanCallMeExceptions.length}'
                          ' contact(s) may not call you',
                onTap: () => _editCallExceptions(),
              ),
            ChatySwitchTile(
              icon: Icons.shortcut_rounded,
              iconColor: colors.info,
              title: 'Disable Forwarded Tag',
              subtitle:
                  'Do not display a forwarded marker on your outgoing forwarded messages.',
              value: prefs.disableForwardedLabel,
              onChanged: (value) {
                widget.preferencesController.updatePrivacy(
                  prefs.copyWith(disableForwardedLabel: value),
                  logTitle: 'Disable Forwarded Label',
                );
                widget.preferencesController.updateGbFeature(
                  'yoDisableFwd',
                  value,
                  logTitle: 'Disable Forwarded Label',
                );
              },
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Advanced Settings & Recovery',
          children: [
            ChatySwitchTile(
              icon: Icons.visibility_off_rounded,
              iconColor: colors.foregroundSecondary,
              title: 'Hide Privacy Option from Main Settings',
              subtitle:
                  'Hide this Privacy entry. Restore it through Advanced Features.',
              value: prefs.hidePrivacyOption,
              onChanged: (value) => widget.preferencesController.updatePrivacy(
                prefs.copyWith(hidePrivacyOption: value),
                logTitle: 'Hide Privacy Option',
              ),
            ),
          ],
        ),
      ],
    );
  }

  Future<void> _openBlockSearch() async {
    final dataStore = locator<ChatyDataStore>();
    final searchController = TextEditingController();
    List<dynamic> results = const <dynamic>[];
    var busy = false;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, updateDialog) => AlertDialog(
          title: const Text('Block a Chaty user'),
          content: SizedBox(
            width: 420,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: searchController,
                  autofocus: true,
                  decoration: InputDecoration(
                    hintText: '@username or name',
                    suffixIcon: busy
                        ? const Padding(
                            padding: EdgeInsets.all(13),
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : IconButton(
                            icon: const Icon(Icons.search_rounded),
                            onPressed: () async {
                              final query = searchController.text.trim();
                              if (query.length < 2) return;
                              updateDialog(() => busy = true);
                              try {
                                final found = await dataStore.searchUsersRemote(
                                  query,
                                );
                                if (dialogContext.mounted)
                                  updateDialog(() => results = found);
                              } finally {
                                if (dialogContext.mounted)
                                  updateDialog(() => busy = false);
                              }
                            },
                          ),
                  ),
                ),
                const SizedBox(height: 10),
                Flexible(
                  child: ListView.builder(
                    shrinkWrap: true,
                    itemCount: results.length,
                    itemBuilder: (context, index) {
                      final user = results[index];
                      return ListTile(
                        title: Text(user.displayName),
                        subtitle: Text('@${user.username}'),
                        trailing: const Icon(Icons.chevron_right_rounded),
                        onTap: () async {
                          try {
                            await _privacyBackend.blockUser(user.id);
                            if (dialogContext.mounted)
                              Navigator.pop(dialogContext);
                            _refreshBlocked();
                            _toast('${user.displayName} blocked.');
                          } catch (error) {
                            _toast('Unable to block user: $error');
                          }
                        },
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Close'),
            ),
          ],
        ),
      ),
    );
    searchController.dispose();
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
}
