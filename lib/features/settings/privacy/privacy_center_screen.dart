import '../../../data/services/local_lock_service.dart';
import '../security/app_lock_overlay.dart';
import '../security/lock_credential_setup_modal.dart';
import '../security/lock_method_selector_sheet.dart';
import '../security/security_flow_plan.dart';
import '../security/security_center_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../domain/models/preferences.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';

/// Privacy and Security settings screen matching Image 3.
/// Adapts dynamically to light and dark theme using global color tokens.
class PrivacyCenterScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const PrivacyCenterScreen({super.key, required this.preferencesController});

  @override
  State<PrivacyCenterScreen> createState() => _PrivacyCenterScreenState();
}

class _PrivacyCenterScreenState extends State<PrivacyCenterScreen> {
  late final LocalLockService _lockService;
  int _pinLength = 4;
  bool _biometricAvailable = false;

  @override
  void initState() {
    super.initState();
    _lockService = locator<LocalLockService>();
    _loadCapabilities();
  }

  Future<void> _loadCapabilities() async {
    final pinLength = await _lockService.getPinLength();
    final biometricAvailable = await _lockService.canUseBiometrics();
    if (!mounted) return;
    setState(() {
      _pinLength = pinLength;
      _biometricAvailable = biometricAvailable;
    });
  }

  static LockMethodType _methodTypeOf(String stored) => switch (stored) {
    'PIN' => LockMethodType.pin,
    'Pattern' => LockMethodType.pattern,
    'Password' => LockMethodType.password,
    'Biometric' => LockMethodType.biometric,
    'Device Credential' => LockMethodType.deviceCredential,
    _ => LockMethodType.pin,
  };

  static String _storageKeyOf(LockMethodType type) => switch (type) {
    LockMethodType.pin => 'PIN',
    LockMethodType.pattern => 'Pattern',
    LockMethodType.password => 'Password',
    LockMethodType.biometric => 'Biometric',
    LockMethodType.deviceCredential => 'Device Credential',
  };

  Future<bool> _verifyCurrent(LockMethodType method) async {
    if (!SecurityFlowPlan.hasVerifiableSecret(method)) {
      final ok = method == LockMethodType.biometric
          ? await _lockService.authenticateBiometric(
              reason: 'Confirm it is you to continue',
            )
          : await _lockService.authenticateDeviceCredential(
              reason: 'Confirm it is you to continue',
            );
      if (!ok && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Authentication failed or was cancelled.')),
        );
      }
      return ok;
    }
    final unlocked = await AppLockOverlayModal.show(
      context,
      preferencesController: widget.preferencesController,
      lockService: _lockService,
      title: 'Confirm it\'s you',
      reason: 'Verify your current lock to continue',
    );
    if (unlocked != true) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Verification cancelled.')),
        );
      }
      return false;
    }
    return true;
  }

  Future<bool> _runFlow({
    required SecurityIntent intent,
    LockMethodType? target,
    int? setupPinLength,
  }) async {
    final security = widget.preferencesController.security;
    final current = _methodTypeOf(security.lockMethod);
    var chosen = target ?? current;
    var secretConfigured = await _lockService.hasCredential(
      _storageKeyOf(current),
    );
    var verifiedInFlow = false;

    for (final step in SecurityFlowPlan.plan(
      intent: intent,
      currentMethod: current,
      currentSecretConfigured: secretConfigured,
      targetMethod: chosen,
    )) {
      if (!mounted) return false;
      switch (step) {
        case SecurityFlowStep.verifyCurrent:
          final ok = await _verifyCurrent(current);
          if (!ok) return false;
          verifiedInFlow = true;

        case SecurityFlowStep.chooseMethod:
          final picked = await LockMethodSelectorSheet.show(
            context,
            lockService: _lockService,
            currentMethod: chosen,
          );
          if (picked == null || !mounted) return false;
          chosen = picked;
          secretConfigured = await _lockService.hasCredential(
            _storageKeyOf(chosen),
          );

        case SecurityFlowStep.setupNew:
          if (verifiedInFlow && secretConfigured && chosen == current) {
            break;
          }
          final configured = await LockCredentialSetupModal.show(
            context,
            method: _storageKeyOf(chosen),
            pinLength: setupPinLength ?? _pinLength,
            lockService: _lockService,
          );
          if (!configured || !mounted) return false;
          await _loadCapabilities();

        case SecurityFlowStep.preflightOsAuth:
          final ok = chosen == LockMethodType.biometric
              ? await _lockService.authenticateBiometric(
                  reason: 'Confirm biometric works for Chaty',
                )
              : await _lockService.authenticateDeviceCredential(
                  reason: 'Confirm device screen lock works for Chaty',
                );
          if (!ok) {
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Authentication not confirmed; lock was not enabled.')),
              );
            }
            return false;
          }

        case SecurityFlowStep.apply:
          final latest = widget.preferencesController.security;
          switch (intent) {
            case SecurityIntent.enableLock:
              widget.preferencesController.updateSecurity(
                latest.copyWith(
                  lockMethod: _storageKeyOf(chosen),
                  isAppLockEnabled: true,
                ),
                logTitle: 'Enable Chaty Lock (${_storageKeyOf(chosen)})',
              );
            case SecurityIntent.changeMethod:
              widget.preferencesController.updateSecurity(
                latest.copyWith(lockMethod: _storageKeyOf(chosen)),
                logTitle: 'Lock Method',
              );
            case SecurityIntent.changeCredential:
              break;
            case SecurityIntent.disableLock:
              widget.preferencesController.updateSecurity(
                latest.copyWith(isAppLockEnabled: false),
                logTitle: 'Disable App Lock',
              );
          }
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  intent == SecurityIntent.disableLock
                      ? 'Chaty lock has been turned off.'
                      : 'Chaty lock updated.',
                ),
              ),
            );
          }
          return true;
      }
    }
    return false;
  }

  void _showRecoveryQuestionDialog() {
    final qCtrl = TextEditingController(text: 'What is your favorite color?');
    final aCtrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Recovery Question'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              'Set a security question to help recover access if you forget your credential.',
              style: TextStyle(fontSize: 13),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: qCtrl,
              decoration: const InputDecoration(
                labelText: 'Security Question',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: aCtrl,
              decoration: const InputDecoration(
                labelText: 'Your Answer',
                border: OutlineInputBorder(),
              ),
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
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Recovery question saved.')),
              );
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  static const List<String> _audienceOptions = <String>[
    'Everyone',
    'My Contacts',
    'My Contacts Except…',
    'Nobody',
  ];

  void _showWhoCanCallMeDialog() {
    final prefs = widget.preferencesController.privacy;
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Who can call me?'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: _audienceOptions.map((opt) {
            return RadioListTile<String>(
              title: Text(opt),
              value: opt,
              groupValue: prefs.whoCanCallMe,
              activeColor: Theme.of(context).colorScheme.primary,
              onChanged: (val) {
                if (val != null) {
                  widget.preferencesController.updatePrivacy(
                    prefs.copyWith(whoCanCallMe: val),
                    logTitle: 'Who can call me',
                  );
                  Navigator.pop(ctx);
                }
              },
            );
          }).toList(),
        ),
      ),
    );
  }

  void _showCustomPrivacyDialog(String title) {
    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDlgState) {
          final p = widget.preferencesController;
          return AlertDialog(
            title: Text('$title Privacy Settings'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  CheckboxListTile(
                    title: const Text('Hide blue ticks'),
                    value: p.gbBool('${title}_hide_blue_ticks', fallback: false),
                    onChanged: (val) {
                      p.updateGbFeature('${title}_hide_blue_ticks', val);
                      setDlgState(() {});
                    },
                  ),
                  CheckboxListTile(
                    title: const Text('Hide second tick'),
                    value: p.gbBool('${title}_hide_second_tick', fallback: false),
                    onChanged: (val) {
                      p.updateGbFeature('${title}_hide_second_tick', val);
                      setDlgState(() {});
                    },
                  ),
                  CheckboxListTile(
                    title: const Text('Hide blue microphone'),
                    value: p.gbBool('${title}_hide_blue_mic', fallback: false),
                    onChanged: (val) {
                      p.updateGbFeature('${title}_hide_blue_mic', val);
                      setDlgState(() {});
                    },
                  ),
                  if (title != 'Broadcast') ...[
                    CheckboxListTile(
                      title: const Text('Hide typing…'),
                      value: p.gbBool('${title}_hide_typing', fallback: false),
                      onChanged: (val) {
                        p.updateGbFeature('${title}_hide_typing', val);
                        setDlgState(() {});
                      },
                    ),
                    CheckboxListTile(
                      title: const Text('Hide recording…'),
                      value: p.gbBool('${title}_hide_recording', fallback: false),
                      onChanged: (val) {
                        p.updateGbFeature('${title}_hide_recording', val);
                        setDlgState(() {});
                      },
                    ),
                  ],
                ],
              ),
            ),
            actions: [
              FilledButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Done'),
              ),
            ],
          );
        },
      ),
    );
  }

  void _resetPrivacyToDefaults() {
    HapticFeedback.heavyImpact();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Reset Privacy?'),
        content: const Text(
          'This will reset all privacy settings back to WhatsApp default configuration.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Theme.of(context).colorScheme.primary),
            onPressed: () {
              widget.preferencesController.updatePrivacy(
                const PrivacyPreferences(),
                logTitle: 'Reset Privacy',
              );
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Privacy settings reset to defaults! 🔄')),
              );
            },
            child: const Text('Reset'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    final theme = themeController.globalTheme;
    final colors = context.colors;
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: AppBar(
        backgroundColor: theme.backgroundColor,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: Padding(
          padding: const EdgeInsets.all(8.0),
          child: ChatyBackButton(
            onPressed: () => Navigator.of(context).pop(),
          ),
        ),
        title: Text(
          'Privacy and Security',
          style: TextStyle(
            color: theme.primaryTextColor,
            fontSize: 20 * theme.fontScale,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.3,
          ),
        ),
      ),
      body: SafeArea(
        top: false,
        child: ListenableBuilder(
          listenable: widget.preferencesController,
          builder: (context, _) {
            final currentPrefs = widget.preferencesController.privacy;
            final currentSec = widget.preferencesController.security;

            return ListView(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              children: [
                // Top Important Alert Banner
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: theme.accentColor.withValues(alpha: isDark ? 0.18 : 0.12),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: theme.accentColor.withValues(alpha: 0.35),
                      width: 1.0,
                    ),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.warning_amber_rounded, color: theme.accentColor, size: 24),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          'Important alert: Dear user, If you encounter a problem with a delay in sending and receiving messages. All you have to do is click on the [Reset Privacy] option at the bottom.',
                          style: TextStyle(
                            color: theme.primaryTextColor,
                            fontSize: 12.5 * theme.fontScale,
                            height: 1.35,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Card Container for Privacy, Specific Privacy, Status, Chats
                _buildCardContainer(
                  theme: theme,
                  colors: colors,
                  isDark: isDark,
                  children: [
                    // Section 1: Privacy
                    _buildSectionDivider(label: 'Privacy', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.ac_unit_rounded,
                      title: 'Freeze Last Seen',
                      subtitle: 'Please restart Chaty for changes to take effect.',
                      value: currentPrefs.freezeLastSeen,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(freezeLastSeen: val),
                          logTitle: 'Freeze Last Seen',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.remove_red_eye_outlined,
                      title: 'Anti-View Once',
                      subtitle: "Open 'view once' messages unlimited",
                      value: currentPrefs.antiViewOnce,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(antiViewOnce: val),
                          logTitle: 'Anti-View Once',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.forward_to_inbox_rounded,
                      title: 'Disable Forwarded',
                      subtitle: 'Allows you to resend messages without Forwarded tag',
                      value: currentPrefs.disableForwardedLabel,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(disableForwardedLabel: val),
                          logTitle: 'Disable Forwarded',
                        );
                      },
                    ),
                    _buildChevronRow(
                      icon: Icons.call_outlined,
                      title: 'Who can call me?',
                      subtitle: currentPrefs.whoCanCallMe,
                      theme: theme,
                      colors: colors,
                      onTap: _showWhoCanCallMeDialog,
                    ),
                    _buildChevronRow(
                      icon: Icons.admin_panel_settings_outlined,
                      title: 'Custom Privacy',
                      subtitle: 'View all',
                      theme: theme,
                      colors: colors,
                      onTap: () => _showCustomPrivacyDialog('Custom'),
                    ),

                    // Section 2: Specific Privacy
                    _buildSectionDivider(label: 'Specific Privacy', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.lock_outline_rounded,
                      title: 'Hide the privacy option',
                      subtitle: 'Hide the privacy option from the Home Screen Options menu.',
                      value: currentPrefs.hidePrivacyOption,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(hidePrivacyOption: val),
                          logTitle: 'Hide the privacy option',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.system_update_alt_rounded,
                      title: 'Hide the update option',
                      subtitle: 'Hide the Chaty Update option from the Home Screen Options menu.',
                      value: currentPrefs.hideUpdateOption,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(hideUpdateOption: val),
                          logTitle: 'Hide the update option',
                        );
                      },
                    ),

                    // Section 3: Status
                    _buildSectionDivider(label: 'Status', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.tv_off_rounded,
                      title: 'Disable channels',
                      subtitle: 'The channels will not be shown in the status screen.',
                      value: currentPrefs.disableChannels,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(disableChannels: val),
                          logTitle: 'Disable channels',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.visibility_off_rounded,
                      title: 'Hide View Status',
                      subtitle: 'Do not tell contact that you have viewed their status',
                      value: currentPrefs.hideViewStatus,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(hideViewStatus: val),
                          logTitle: 'Hide View Status',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.delete_forever_outlined,
                      title: 'Anti-Delete Status',
                      subtitle: 'Deleted status/stories will not be deleted for you',
                      value: currentPrefs.antiDeleteStatus,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(antiDeleteStatus: val),
                          logTitle: 'Anti-Delete Status',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.notification_important_outlined,
                      title: 'Status Revoke Alert',
                      subtitle: 'Show an immediate notification when one of your contacts deletes their status/Story',
                      value: currentPrefs.statusRevocationAlert,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(statusRevocationAlert: val),
                          logTitle: 'Status Revoke Alert',
                        );
                      },
                    ),

                    // Section 4: Chats
                    _buildSectionDivider(label: 'Chats', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.mark_chat_read_outlined,
                      title: 'Hide the first message',
                      subtitle: 'When this feature is enabled, the first message in hidden chats will not be shown, and you can only read it after opening the chat',
                      value: currentPrefs.hideFirstMessage,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(hideFirstMessage: val),
                          logTitle: 'Hide the first message',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.timer_outlined,
                      title: 'Anti Disappearing Messages',
                      subtitle: 'If Disappearing Messages is Enabled, Messages will not delete from your side',
                      value: currentPrefs.antiDisappearingMessages,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(antiDisappearingMessages: val),
                          logTitle: 'Anti Disappearing Messages',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.edit_note_rounded,
                      title: 'Show edited message',
                      subtitle: 'Show people normal with messages for you',
                      value: currentPrefs.showEditedMessage,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(showEditedMessage: val),
                          logTitle: 'Show edited message',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.delete_outline_rounded,
                      title: 'Anti-Delete Messages',
                      subtitle: 'Other people cannot delete messages for you',
                      value: currentPrefs.antiDeleteMessages,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(antiDeleteMessages: val),
                          logTitle: 'Anti-Delete Messages',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.notifications_active_outlined,
                      title: 'Message Revoke Alert',
                      subtitle: 'Show a notification in the status bar when someone deletes a message sent to you',
                      value: currentPrefs.messageRevokeAlert,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(messageRevokeAlert: val),
                          logTitle: 'Message Revoke Alert',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.delete_sweep_outlined,
                      title: 'Deletion of everyone',
                      subtitle: 'Activates the possibility of deleting messages to everyone at any time.',
                      value: currentPrefs.deletionOfEveryone,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(deletionOfEveryone: val),
                          logTitle: 'Deletion of everyone',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.access_time_rounded,
                      title: 'Deleted media time',
                      subtitle: 'Find out the exact moment a message or status was deleted',
                      value: currentPrefs.deletedMediaTime,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(deletedMediaTime: val),
                          logTitle: 'Deleted media time',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.mark_chat_unread_outlined,
                      title: 'Unviewed chats',
                      subtitle: 'When enabled the message counter will not disappear when you open the chat',
                      value: currentPrefs.unviewedChats,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(unviewedChats: val),
                          logTitle: 'Unviewed chats',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.done_all_rounded,
                      title: 'Show Blue Ticks after reply',
                      subtitle: 'Contact will only see blue ticks after you reply',
                      value: currentPrefs.showBlueTicksAfterReply,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updatePrivacy(
                          currentPrefs.copyWith(showBlueTicksAfterReply: val),
                          logTitle: 'Show Blue Ticks after reply',
                        );
                      },
                    ),
                    _buildChevronRow(
                      icon: Icons.contacts_outlined,
                      title: 'Contacts',
                      subtitle: 'Change privacy settings',
                      theme: theme,
                      colors: colors,
                      onTap: () => _showCustomPrivacyDialog('Contacts'),
                    ),
                    _buildChevronRow(
                      icon: Icons.groups_outlined,
                      title: 'Groups',
                      subtitle: 'Change privacy settings',
                      theme: theme,
                      colors: colors,
                      onTap: () => _showCustomPrivacyDialog('Groups'),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Card Container for Security Section
                _buildCardContainer(
                  theme: theme,
                  colors: colors,
                  isDark: isDark,
                  children: [
                    _buildSectionDivider(label: 'Security', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.lock_rounded,
                      title: 'Chaty App Lock',
                      subtitle: currentSec.isAppLockEnabled
                          ? 'Protected with '
                          : 'Enable lock screen security',
                      value: currentSec.isAppLockEnabled,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) => _runFlow(
                        intent: val ? SecurityIntent.enableLock : SecurityIntent.disableLock,
                      ),
                    ),
                    if (currentSec.isAppLockEnabled) ...[
                      _buildChevronRow(
                        icon: Icons.style_rounded,
                        title: 'Lock Type',
                        subtitle: ' • tap to change',
                        theme: theme,
                        colors: colors,
                        onTap: () => _runFlow(intent: SecurityIntent.changeMethod),
                      ),
                      if (currentSec.lockMethod == 'PIN') ...[
                        _buildChevronRow(
                          icon: Icons.pin_rounded,
                          title: 'Change PIN Code',
                          subtitle: '-digit PIN stored securely on this device',
                          theme: theme,
                          colors: colors,
                          onTap: () => _runFlow(
                            intent: SecurityIntent.changeCredential,
                            target: LockMethodType.pin,
                            setupPinLength: _pinLength,
                          ),
                        ),
                        _buildChevronRow(
                          icon: Icons.dialpad_rounded,
                          title: 'PIN Length',
                          subtitle: _pinLength == 6 ? '6 digits' : '4 digits',
                          theme: theme,
                          colors: colors,
                          onTap: () async {
                            final chosen = await showDialog<String>(
                              context: context,
                              builder: (ctx) => SimpleDialog(
                                title: const Text('Choose PIN Length'),
                                children: [
                                  SimpleDialogOption(
                                    onPressed: () => Navigator.pop(ctx, '4 digits'),
                                    child: const Text('4 digits'),
                                  ),
                                  SimpleDialogOption(
                                    onPressed: () => Navigator.pop(ctx, '6 digits'),
                                    child: const Text('6 digits'),
                                  ),
                                ],
                              ),
                            );
                            if (chosen != null) {
                              _runFlow(
                                intent: SecurityIntent.changeCredential,
                                target: LockMethodType.pin,
                                setupPinLength: chosen == '6 digits' ? 6 : 4,
                              );
                            }
                          },
                        ),
                      ],
                      if (currentSec.lockMethod == 'Pattern') ...[
                        _buildChevronRow(
                          icon: Icons.pattern_rounded,
                          title: 'Change Pattern',
                          subtitle: 'Configure gesture unlock pattern',
                          theme: theme,
                          colors: colors,
                          onTap: () => _runFlow(
                            intent: SecurityIntent.changeCredential,
                            target: LockMethodType.pattern,
                          ),
                        ),
                        _buildSwitchRow(
                          icon: Icons.visibility_off_rounded,
                          title: 'Make Pattern Invisible',
                          subtitle: 'Hide trail when drawing pattern',
                          value: currentSec.makePatternInvisible,
                          theme: theme,
                          colors: colors,
                          onChanged: (val) {
                            widget.preferencesController.updateSecurity(
                              currentSec.copyWith(makePatternInvisible: val),
                              logTitle: 'Make Pattern Invisible',
                            );
                          },
                        ),
                        _buildSwitchRow(
                          icon: Icons.vibration_rounded,
                          title: 'Disable pattern vibration',
                          subtitle: 'Suppress haptic vibration on pattern touch',
                          value: currentSec.disablePatternVibration,
                          theme: theme,
                          colors: colors,
                          onChanged: (val) {
                            widget.preferencesController.updateSecurity(
                              currentSec.copyWith(disablePatternVibration: val),
                              logTitle: 'Disable pattern vibration',
                            );
                          },
                        ),
                      ],
                      if (currentSec.lockMethod == 'Password') ...[
                        _buildChevronRow(
                          icon: Icons.password_rounded,
                          title: 'Change Password',
                          subtitle: 'Set a new alphanumeric password',
                          theme: theme,
                          colors: colors,
                          onTap: () => _runFlow(
                            intent: SecurityIntent.changeCredential,
                            target: LockMethodType.password,
                          ),
                        ),
                      ],
                      if (currentSec.lockMethod == 'Biometric')
                        _buildChevronRow(
                          icon: Icons.fingerprint_rounded,
                          title: 'Test Biometrics',
                          subtitle: _biometricAvailable
                              ? 'Enrolled biometric available'
                              : 'No biometric enrolled on device',
                          theme: theme,
                          colors: colors,
                          onTap: () async {
                            final ok = await _lockService.authenticateBiometric(
                              reason: 'Test biometric unlock for Chaty',
                            );
                            if (!mounted) return;
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(ok ? 'Biometric unlock verified.' : 'Biometric failed or cancelled.'),
                              ),
                            );
                          },
                        ),
                      _buildChevronRow(
                        icon: Icons.timer_outlined,
                        title: 'Auto-Lock Timeout',
                        subtitle: currentSec.autoLockTimeout,
                        theme: theme,
                        colors: colors,
                        onTap: () async {
                          final picked = await showDialog<String>(
                            context: context,
                            builder: (ctx) => SimpleDialog(
                              title: const Text('Auto-Lock Timeout'),
                              children: ['Immediately', '15s', '30s', '1m', '5m', '15m'].map((opt) {
                                return SimpleDialogOption(
                                  onPressed: () => Navigator.pop(ctx, opt),
                                  child: Text(opt),
                                );
                              }).toList(),
                            ),
                          );
                          if (picked != null) {
                            widget.preferencesController.updateSecurity(
                              currentSec.copyWith(autoLockTimeout: picked),
                              logTitle: 'Auto-Lock Timeout ()',
                            );
                          }
                        },
                      ),
                      _buildChevronRow(
                        icon: Icons.help_outline_rounded,
                        title: 'Recovery Question',
                        subtitle: 'Set secret question for password reset',
                        theme: theme,
                        colors: colors,
                        onTap: () => _showRecoveryQuestionDialog(),
                      ),
                      _buildChevronRow(
                        icon: Icons.shield_rounded,
                        title: 'Advanced Security & Trust Center',
                        subtitle: 'Encryption verification, QR audits & locked chats',
                        theme: theme,
                        colors: colors,
                        onTap: () => Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => SecurityCenterScreen(
                              preferencesController: widget.preferencesController,
                            ),
                          ),
                        ),
                      ),
                    ],
                    
                    _buildChevronRow(
                      icon: Icons.restart_alt_rounded,
                      title: 'Reset Privacy',
                      subtitle: 'Sets back Chaty default Privacy',
                      theme: theme,
                      colors: colors,
                      onTap: _resetPrivacyToDefaults,
                    ),
                  ],
                ),
                const SizedBox(height: 36),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildCardContainer({
    required dynamic theme,
    required dynamic colors,
    required bool isDark,
    required List<Widget> children,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E2124) : colors.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: colors.borderSubtle,
          width: 0.9,
        ),
        boxShadow: [
          BoxShadow(
            color: colors.shadow.withValues(alpha: isDark ? 0.25 : 0.04),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: children,
        ),
      ),
    );
  }

  Widget _buildSectionDivider({
    required String label,
    required Color accent,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
      child: Row(
        children: [
          Expanded(
            child: Container(
              height: 1.2,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    accent.withValues(alpha: 0.0),
                    accent.withValues(alpha: 0.7),
                  ],
                ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 5,
                  height: 5,
                  decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
                ),
                const SizedBox(width: 6),
                Text(
                  label,
                  style: TextStyle(
                    color: accent,
                    fontWeight: FontWeight.w700,
                    fontSize: 13,
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(width: 6),
                Container(
                  width: 5,
                  height: 5,
                  decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
                ),
              ],
            ),
          ),
          Expanded(
            child: Container(
              height: 1.2,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    accent.withValues(alpha: 0.7),
                    accent.withValues(alpha: 0.0),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSwitchRow({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required dynamic theme,
    required dynamic colors,
    required ValueChanged<bool> onChanged,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          onChanged(!value);
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(11),
                ),
                child: Icon(icon, color: theme.accentColor, size: 20),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 14.5 * theme.fontScale,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 11.5 * theme.fontScale,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              IgnorePointer(
                child: Switch(
                  value: value,
                  activeColor: theme.accentColor,
                  onChanged: null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildChevronRow({
    required IconData icon,
    required String title,
    required String subtitle,
    required dynamic theme,
    required dynamic colors,
    required VoidCallback onTap,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          onTap();
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
          child: Row(
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(11),
                ),
                child: Icon(icon, color: theme.accentColor, size: 20),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 14.5 * theme.fontScale,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 11.5 * theme.fontScale,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Icon(
                Icons.chevron_right_rounded,
                color: theme.accentColor,
                size: 22,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
