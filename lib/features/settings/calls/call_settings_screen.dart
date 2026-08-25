import 'package:flutter/material.dart';
import '../../../data/repositories/chaty_data_store.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart'
    hide ChatySettingsSection;
import '../../../ui/core/design_system/settings_primitives.dart';

/// Dedicated canonical settings screen for Audio/Video Calls, privacy exceptions, and island controls.
class CallSettingsScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;

  const CallSettingsScreen({super.key, required this.preferencesController});

  static const List<String> _audienceOptions = <String>[
    'Everyone',
    'My Contacts',
    'My Contacts Except…',
    'Nobody',
  ];

  Future<void> _editCallExceptions(BuildContext context) async {
    final dataStore = locator<ChatyDataStore>();
    final selected = Set<String>.of(
      preferencesController.privacy.whoCanCallMeExceptions,
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
    preferencesController.updatePrivacy(
      preferencesController.privacy.copyWith(
        whoCanCallMeExceptions: selected.toList(growable: false),
      ),
      logTitle: 'Who Can Call Me Exceptions',
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return ListenableBuilder(
      listenable: preferencesController,
      builder: (context, _) {
        final privacy = preferencesController.privacy;

        return ChatySettingsPage(
          title: 'Calls',
          subtitle: 'Incoming call privacy, background audio & visual island',
          children: [
            // 1. INCOMING CALL PRIVACY
            ChatySettingsSection(
              title: 'Incoming Call Privacy',
              description:
                  'Control who is permitted to initiate voice and video calls with you.',
              children: [
                ChatySettingsTile(
                  icon: Icons.ring_volume_rounded,
                  iconColor: colors.primary,
                  title: 'Who Can Call Me',
                  subtitle: privacy.whoCanCallMe,
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () async {
                    final chosen = await ChatySingleChoiceModal.show<String>(
                      context: context,
                      title: 'Who Can Call Me',
                      description:
                          'Choose who can reach you via audio and video calls.',
                      value: privacy.whoCanCallMe,
                      options: _audienceOptions,
                      labelBuilder: (v) => v,
                    );
                    if (chosen != null) {
                      preferencesController.updatePrivacy(
                        privacy.copyWith(whoCanCallMe: chosen),
                        logTitle: 'Who Can Call Me',
                      );
                      if (chosen == 'My Contacts Except…' && context.mounted) {
                        _editCallExceptions(context);
                      }
                    }
                  },
                ),
                if (privacy.whoCanCallMe == 'My Contacts Except…')
                  ChatySettingsTile(
                    icon: Icons.person_off_outlined,
                    iconColor: colors.error,
                    title: 'Excluded Contacts',
                    subtitle: ' contacts excluded',
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: () => _editCallExceptions(context),
                  ),
              ],
            ),

            // 2. CALL PRESENTATION & CONTROLS
            ChatySettingsSection(
              title: 'In-App Call Presentation',
              children: [
                ChatySwitchTile(
                  icon: Icons.bubble_chart_rounded,
                  iconColor: colors.primary,
                  title: 'Dynamic Call Island',
                  subtitle:
                      'Display interactive floating capsule when navigating away from active calls.',
                  value: true,
                  onChanged: (val) {},
                ),
                ChatySwitchTile(
                  icon: Icons.picture_in_picture_rounded,
                  iconColor: colors.primary,
                  title: 'Picture-in-Picture Video',
                  subtitle:
                      'Automatically minimize video stream into PiP window when switching apps.',
                  value: true,
                  onChanged: (val) {},
                ),
              ],
            ),

            // 3. AUDIO & MEDIA QUALITY
            ChatySettingsSection(
              title: 'Audio & Connectivity',
              children: [
                ChatySwitchTile(
                  icon: Icons.speaker_phone_rounded,
                  iconColor: colors.success,
                  title: 'Low Data Usage for Calls',
                  subtitle:
                      'Optimize WebRTC bandwidth consumption on cellular connections.',
                  value: false,
                  onChanged: (val) {},
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}
