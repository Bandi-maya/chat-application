import 'package:flutter/material.dart';

import '../../../data/repositories/chaty_data_store.dart';
import '../../../data/services/local_lock_service.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/settings_primitives.dart';
import '../../../ui/core/theme/app_theme.dart';
import '../../chats/locked_chats_screen.dart';
import 'app_lock_overlay.dart';
import 'lock_credential_setup_modal.dart';
import 'lock_method_selector_sheet.dart';
import 'security_flow_plan.dart';

class SecurityCenterScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const SecurityCenterScreen({super.key, required this.preferencesController});

  @override
  State<SecurityCenterScreen> createState() => _SecurityCenterScreenState();
}

class _SecurityCenterScreenState extends State<SecurityCenterScreen> {
  static const List<String> _autoLockOptions = <String>[
    'Immediately',
    '15s',
    '30s',
    '1m',
    '5m',
    '15m',
  ];

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

  // ---------------------------------------------------------------------------
  // Credential lifecycle. Every intent runs the SAME planned pipeline:
  // verify current → choose type → set+confirm new → OS preflight → apply.
  // Nothing is persisted until every step passes (see SecurityFlowPlan).
  // ---------------------------------------------------------------------------

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
      // OS-based method: a live authentication is the proof.
      final ok = method == LockMethodType.biometric
          ? await _lockService.authenticateBiometric(
              reason: 'Confirm it is you to continue',
            )
          : await _lockService.authenticateDeviceCredential(
              reason: 'Confirm it is you to continue',
            );
      if (!ok && mounted) _toast('Authentication failed or was cancelled.');
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
      if (mounted) _toast('Verification cancelled.');
      return false;
    }
    return true;
  }

  /// Executes one security intent end-to-end. Returns true when applied.
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
    // True once this flow has proven knowledge of the current secret.
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
          // Keeping the type you already have (and just verified) does not
          // require typing a brand-new secret — matching platform behavior.
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
                  reason: 'Confirm biometric unlock works for Chaty',
                )
              : await _lockService.authenticateDeviceCredential(
                  reason: 'Confirm your device lock works for Chaty',
                );
          if (!ok) {
            if (mounted) {
              _toast(
                chosen == LockMethodType.biometric
                    ? 'Biometric unavailable or not confirmed — pick another '
                          'lock type so you cannot be locked out.'
                    : 'Device lock not confirmed — set a device screen lock '
                          'first or pick another lock type.',
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
              // Secret already persisted by the setup modal; nothing else to
              // change preference-side.
              break;
            case SecurityIntent.disableLock:
              widget.preferencesController.updateSecurity(
                latest.copyWith(isAppLockEnabled: false),
                logTitle: 'Disable App Lock',
              );
          }
      }
    }
    if (mounted) setState(() {});
    return true;
  }

  void _toast(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  void _showSafetyNumberDialog() {
    final colors = context.colors;
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            Icon(Icons.verified_user_rounded, color: colors.success),
            const SizedBox(width: 8),
            const Text('Safety Number Verification'),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: colors.surfaceSecondary,
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Text(
                '05423 89104 33812 77192\n44901 88321 00192 44381',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontFamily: 'monospace',
                  fontWeight: FontWeight.bold,
                  fontSize: 13,
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Demo Security Model • End-to-end encryption state verified with prekey identity.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: colors.foregroundSecondary),
            ),
          ],
        ),
        actions: [
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Verified'),
          ),
        ],
      ),
    );
  }

  Future<String?> _showSecretPhraseSetupDialog() async {
    final controller = TextEditingController();
    final colors = context.colors;
    return await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            Icon(Icons.key_rounded, color: colors.accent),
            const SizedBox(width: 8),
            const Text('Set Secret Search Word'),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Enter a secret word, phrase, or emoji. Typing this in the search bar will reveal your hidden locked chats.',
              style: TextStyle(fontSize: 13, color: colors.foregroundSecondary),
            ),
            const SizedBox(height: 14),
            TextField(
              controller: controller,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Secret Word or Emoji',
                hintText: 'e.g. sesame, vault, 🔒',
                border: OutlineInputBorder(),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(null),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              final text = controller.text.trim();
              if (text.isNotEmpty) {
                Navigator.of(ctx).pop(text);
              }
            },
            child: const Text('Save Word'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final security = widget.preferencesController.security;
    final lockedChats = security.lockedConversationIds.length;
    final colors = context.colors;

    return ChatySettingsPage(
      title: 'Security & App Lock',
      subtitle: 'Encryption Status, Authentication & Chaty Lock',
      children: [
        ChatySettingsSection(
          title: 'Encryption & Trust Model',
          description:
              'All conversations in Chaty utilize local end-to-end encryption simulation.',
          children: [
            ChatySettingsTile(
              icon: Icons.shield_rounded,
              iconColor: colors.success,
              title: 'Demo Security Model',
              subtitle: 'Double-ratchet session status: Active & Verified',
              badgeText: 'ENCRYPTED',
              badgeColor: colors.success,
              onTap: _showSafetyNumberDialog,
            ),
            ChatySettingsTile(
              icon: Icons.qr_code_2_rounded,
              iconColor: colors.info,
              title: 'Verify Safety Number QR',
              subtitle: 'Simulate key fingerprints for security auditing',
              onTap: _showSafetyNumberDialog,
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Chaty App Lock',
          description:
              'Require a local credential or operating-system authentication before Chaty can be used.',
          children: [
            ChatySwitchTile(
              icon: Icons.lock_rounded,
              iconColor: colors.warning,
              title: 'Enable Chaty Lock',
              subtitle: security.isAppLockEnabled
                  ? 'App lock active (${security.lockMethod})'
                  : 'Protect the application with biometrics, PIN, pattern, password, or device lock',
              value: security.isAppLockEnabled,
              onChanged: (value) => _runFlow(
                intent: value
                    ? SecurityIntent.enableLock
                    : SecurityIntent.disableLock,
              ),
            ),
            if (security.isAppLockEnabled) ...[
              ChatySettingsTile(
                icon: Icons.style_rounded,
                title: 'Lock type',
                subtitle:
                    '${security.lockMethod} • tap to verify and change type',
                trailing: Icon(
                  Icons.chevron_right_rounded,
                  size: 20,
                  color: colors.foregroundTertiary,
                ),
                onTap: () =>
                    _runFlow(intent: SecurityIntent.changeMethod, target: null),
              ),
              if (security.lockMethod == 'Biometric')
                ChatySettingsTile(
                  icon: Icons.fingerprint_rounded,
                  title: 'Biometric authentication',
                  subtitle: _biometricAvailable
                      ? 'Uses enrolled fingerprint / face authentication from this device'
                      : 'No enrolled biometric is currently available',
                  badgeText: _biometricAvailable ? 'READY' : 'UNAVAILABLE',
                  badgeColor: _biometricAvailable
                      ? colors.success
                      : colors.warning,
                  onTap: () async {
                    final ok = await _lockService.authenticateBiometric(
                      reason: 'Test biometric unlock for Chaty',
                    );
                    if (!mounted) return;
                    _toast(
                      ok
                          ? 'Biometric unlock works.'
                          : 'Biometric failed or was cancelled.',
                    );
                  },
                ),
              if (security.lockMethod == 'Device Credential')
                ChatySettingsTile(
                  icon: Icons.phonelink_lock_rounded,
                  title: 'Device screen lock',
                  subtitle:
                      'Use the PIN, pattern, password, or biometric managed by Android/iOS',
                  onTap: () async {
                    final ok = await _lockService.authenticateDeviceCredential(
                      reason: 'Test your device screen lock for Chaty',
                    );
                    if (!mounted) return;
                    _toast(
                      ok
                          ? 'Device lock works.'
                          : 'Device authentication failed or was cancelled.',
                    );
                  },
                ),
              if (security.lockMethod == 'PIN') ...[
                ChatyChoiceTile<String>(
                  title: 'PIN Length',
                  options: const <String>['4 digits', '6 digits'],
                  selectedOption: _pinLength == 6 ? '6 digits' : '4 digits',
                  optionLabel: (value) => value,
                  onSelected: (value) => _runFlow(
                    intent: SecurityIntent.changeCredential,
                    target: LockMethodType.pin,
                    setupPinLength: value == '6 digits' ? 6 : 4,
                  ),
                ),
                ChatySettingsTile(
                  icon: Icons.pin_rounded,
                  title: 'Change PIN Code',
                  subtitle:
                      '$_pinLength-digit PIN stored securely on this device',
                  onTap: () => _runFlow(
                    intent: SecurityIntent.changeCredential,
                    target: LockMethodType.pin,
                    setupPinLength: _pinLength,
                  ),
                ),
              ],
              if (security.lockMethod == 'Password')
                ChatySettingsTile(
                  icon: Icons.password_rounded,
                  title: 'Change Lock Password',
                  subtitle:
                      'Password is hashed and stored in secure device storage',
                  onTap: () => _runFlow(
                    intent: SecurityIntent.changeCredential,
                    target: LockMethodType.password,
                  ),
                ),
              if (security.lockMethod == 'Pattern') ...[
                ChatySettingsTile(
                  icon: Icons.pattern_rounded,
                  title: 'Change Unlock Pattern',
                  subtitle: 'Draw and confirm a 3×3 gesture pattern',
                  onTap: () => _runFlow(
                    intent: SecurityIntent.changeCredential,
                    target: LockMethodType.pattern,
                  ),
                ),
                ChatySwitchTile(
                  title: 'Make Pattern Invisible',
                  subtitle: 'Hide pattern lines while drawing',
                  value: security.makePatternInvisible,
                  onChanged: (value) =>
                      widget.preferencesController.updateSecurity(
                        security.copyWith(makePatternInvisible: value),
                        logTitle: 'Pattern visibility',
                      ),
                ),
                ChatySwitchTile(
                  title: 'Disable Pattern Vibration',
                  subtitle:
                      'Turn off haptic feedback while drawing the pattern',
                  value: security.disablePatternVibration,
                  onChanged: (value) =>
                      widget.preferencesController.updateSecurity(
                        security.copyWith(disablePatternVibration: value),
                        logTitle: 'Pattern vibration',
                      ),
                ),
              ],
              ChatyChoiceTile<String>(
                title: 'Auto Lock Timeout',
                options: _autoLockOptions,
                selectedOption: security.autoLockTimeout,
                optionLabel: (value) => value,
                onSelected: (value) =>
                    widget.preferencesController.updateSecurity(
                      security.copyWith(autoLockTimeout: value),
                      logTitle: 'Auto Lock Timeout',
                    ),
              ),
              ChatySwitchTile(
                icon: Icons.notifications_paused_rounded,
                title: 'Hide Notification Content when Locked',
                subtitle:
                    'Conceal message body text while the application is locked',
                value: security.hideLockNotificationContent,
                onChanged: (value) =>
                    widget.preferencesController.updateSecurity(
                      security.copyWith(hideLockNotificationContent: value),
                      logTitle: 'Lock notification privacy',
                    ),
              ),
              ChatySettingsTile(
                icon: Icons.lock_open_rounded,
                iconColor: colors.error,
                title: 'Test Lock Screen',
                subtitle:
                    'Run the same authentication gate used by the application',
                onTap: () => AppLockOverlayModal.show(
                  context,
                  preferencesController: widget.preferencesController,
                  lockService: _lockService,
                  title: 'Test Chaty Lock',
                  reason:
                      'Authenticate to verify your Chaty Lock configuration',
                ),
              ),
            ],
          ],
        ),
        ChatySettingsSection(
          title: 'Chat Lock & Hidden Chats',
          description:
              'Locked and hidden conversations require verified local authentication. Hidden chats do not appear in normal chat lists or searches.',
          children: [
            ChatySettingsTile(
              icon: Icons.lock_outline_rounded,
              iconColor: colors.accent,
              title: 'Open Locked & Hidden Chats',
              subtitle: lockedChats == 0
                  ? 'No chats protected • Tap to manage vault'
                  : '$lockedChats conversation${lockedChats == 1 ? '' : 's'} in secure vault',
              badgeText: '$lockedChats',
              badgeColor: lockedChats > 0
                  ? colors.accent
                  : colors.foregroundSecondary,
              onTap: () => LockedChatsScreen.open(
                context,
                dataStore: locator<ChatyDataStore>(),
                preferencesController: widget.preferencesController,
                themeController: locator<ThemeController>(),
              ),
            ),
            ChatySwitchTile(
              icon: Icons.touch_app_rounded,
              title: 'Unlock via Chaty Title Tap',
              subtitle:
                  'Tap the main screen Chaty header to authenticate and open locked chats',
              value: security.entryByAppTitle,
              onChanged: (value) => widget.preferencesController.updateSecurity(
                security.copyWith(entryByAppTitle: value),
                logTitle: 'Title tap vault entry',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.key_rounded,
              title: 'Secret Code Search Discovery',
              subtitle:
                  'Reveal hidden locked chats only when typing your secret word or emoji in search',
              value: security.entryBySecretPhrase,
              onChanged: (value) async {
                if (value) {
                  final phrase = await _showSecretPhraseSetupDialog();
                  if (phrase == null || phrase.isEmpty || !mounted) return;
                  await _lockService.setSecretPhrase(phrase);
                  widget.preferencesController.updateSecurity(
                    security.copyWith(entryBySecretPhrase: true),
                    logTitle: 'Secret search phrase entry',
                  );
                } else {
                  widget.preferencesController.updateSecurity(
                    security.copyWith(entryBySecretPhrase: false),
                    logTitle: 'Secret search phrase disabled',
                  );
                }
              },
            ),
            ChatySwitchTile(
              icon: Icons.screenshot_rounded,
              title: 'Protect Screen & App Previews',
              subtitle:
                  'Blank out app thumbnail in multi-tasking view and block screenshots on supported devices',
              value: security.protectFromScreenshots,
              onChanged: (value) => widget.preferencesController.updateSecurity(
                security.copyWith(protectFromScreenshots: value),
                logTitle: 'Screen & preview security',
              ),
            ),
            ChatySettingsTile(
              icon: Icons.verified_user_outlined,
              title: 'Lock Authentication Method',
              subtitle:
                  'Uses ${security.lockMethod}. Chat Lock protects specific chats even if full App Lock is off.',
              onTap: () => _runFlow(
                intent: SecurityIntent.changeCredential,
                target: _methodTypeOf(security.lockMethod),
              ),
            ),
          ],
        ),
      ],
    );
  }
}
