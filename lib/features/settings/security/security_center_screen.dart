import 'package:flutter/material.dart';

import '../../../data/services/local_lock_service.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../../ui/core/design_system/chaty_settings_primitives.dart';
import 'app_lock_overlay.dart';
import 'lock_credential_setup_modal.dart';

class SecurityCenterScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const SecurityCenterScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<SecurityCenterScreen> createState() => _SecurityCenterScreenState();
}

class _SecurityCenterScreenState extends State<SecurityCenterScreen> {
  static const List<String> _lockMethods = <String>[
    'Biometric',
    'Device Credential',
    'PIN',
    'Pattern',
    'Password',
  ];

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

  String? _legacySecret(String method) {
    final security = widget.preferencesController.security;
    switch (method) {
      case 'PIN':
        return security.pinCode;
      case 'Pattern':
        return security.patternCode;
      case 'Password':
        return security.password;
      default:
        return null;
    }
  }

  Future<bool> _hasConfiguredMethod(String method) async {
    if (method == 'Biometric' || method == 'Device Credential') return false;
    if (await _lockService.hasCredential(method)) return true;
    return _legacySecret(method)?.isNotEmpty == true;
  }

  Future<bool> _configureMethod(String method, {int? pinLength}) async {
    final configured = await LockCredentialSetupModal.show(
      context,
      method: method,
      pinLength: pinLength ?? _pinLength,
      lockService: _lockService,
    );
    if (configured) await _loadCapabilities();
    return configured;
  }

  Future<void> _setAppLockEnabled(bool enabled) async {
    final security = widget.preferencesController.security;
    if (!enabled) {
      widget.preferencesController.updateSecurity(
        security.copyWith(isAppLockEnabled: false),
        logTitle: 'Disable App Lock',
      );
      return;
    }

    final alreadyConfigured = await _hasConfiguredMethod(security.lockMethod);
    if (!alreadyConfigured) {
      final configured = await _configureMethod(security.lockMethod);
      if (!configured || !mounted) return;
    }

    widget.preferencesController.updateSecurity(
      widget.preferencesController.security.copyWith(isAppLockEnabled: true),
      logTitle: 'Enable App Lock',
    );
  }

  Future<void> _changeMethod(String method) async {
    final configured = await _configureMethod(method);
    if (!configured || !mounted) return;
    widget.preferencesController.updateSecurity(
      widget.preferencesController.security.copyWith(lockMethod: method),
      logTitle: 'Lock Method',
    );
  }

  Future<void> _changePinLength(String value) async {
    final length = value == '6 digits' ? 6 : 4;
    if (length == _pinLength && await _lockService.hasCredential('PIN')) return;
    final configured = await _configureMethod('PIN', pinLength: length);
    if (!configured || !mounted) return;
    setState(() => _pinLength = length);
  }

  void _showSafetyNumberDialog() {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.verified_user_rounded, color: Colors.greenAccent),
            SizedBox(width: 8),
            Text('Safety Number Verification'),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.black12,
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Text(
                '05423 89104 33812 77192\n44901 88321 00192 44381',
                textAlign: TextAlign.center,
                style: TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.bold, fontSize: 13),
              ),
            ),
            const SizedBox(height: 12),
            const Text(
              'Demo Security Model • End-to-end encryption state verified with prekey identity.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
          ],
        ),
        actions: [
          ElevatedButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('Verified')),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final security = widget.preferencesController.security;
    final lockedChats = security.lockedConversationIds.length;

    return ChatySettingsPage(
      title: 'Security & App Lock',
      subtitle: 'Encryption Status, Authentication & Chaty Lock',
      children: [
        ChatySettingsSection(
          title: 'Encryption & Trust Model',
          description: 'All conversations in Chaty utilize local end-to-end encryption simulation.',
          children: [
            ChatySettingsTile(
              icon: Icons.shield_rounded,
              iconColor: Colors.greenAccent,
              title: 'Demo Security Model',
              subtitle: 'Double-ratchet session status: Active & Verified',
              badgeText: 'ENCRYPTED',
              badgeColor: Colors.greenAccent,
              onTap: _showSafetyNumberDialog,
            ),
            ChatySettingsTile(
              icon: Icons.qr_code_2_rounded,
              iconColor: Colors.cyanAccent,
              title: 'Verify Safety Number QR',
              subtitle: 'Simulate key fingerprints for security auditing',
              onTap: _showSafetyNumberDialog,
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Chaty App Lock',
          description: 'Require a local credential or operating-system authentication before Chaty can be used.',
          children: [
            ChatySwitchTile(
              icon: Icons.lock_rounded,
              iconColor: Colors.amberAccent,
              title: 'Enable Chaty Lock',
              subtitle: security.isAppLockEnabled
                  ? 'App lock active (${security.lockMethod})'
                  : 'Protect the application with biometrics, PIN, pattern, password, or device lock',
              value: security.isAppLockEnabled,
              onChanged: _setAppLockEnabled,
            ),
            if (security.isAppLockEnabled) ...[
              ChatyChoiceTile<String>(
                title: 'Lock Method',
                options: _lockMethods,
                selectedOption: security.lockMethod,
                optionLabel: (value) => value,
                onSelected: _changeMethod,
              ),
              if (security.lockMethod == 'Biometric')
                ChatySettingsTile(
                  icon: Icons.fingerprint_rounded,
                  title: 'Biometric authentication',
                  subtitle: _biometricAvailable
                      ? 'Uses enrolled fingerprint / face authentication from this device'
                      : 'No enrolled biometric is currently available',
                  badgeText: _biometricAvailable ? 'READY' : 'UNAVAILABLE',
                  badgeColor: _biometricAvailable ? Colors.greenAccent : Colors.orangeAccent,
                  onTap: () => _configureMethod('Biometric'),
                ),
              if (security.lockMethod == 'Device Credential')
                ChatySettingsTile(
                  icon: Icons.phonelink_lock_rounded,
                  title: 'Device screen lock',
                  subtitle: 'Use the PIN, pattern, password, or biometric managed by Android/iOS',
                  onTap: () => _configureMethod('Device Credential'),
                ),
              if (security.lockMethod == 'PIN') ...[
                ChatyChoiceTile<String>(
                  title: 'PIN Length',
                  options: const <String>['4 digits', '6 digits'],
                  selectedOption: _pinLength == 6 ? '6 digits' : '4 digits',
                  optionLabel: (value) => value,
                  onSelected: _changePinLength,
                ),
                ChatySettingsTile(
                  icon: Icons.pin_rounded,
                  title: 'Change PIN Code',
                  subtitle: '${_pinLength}-digit PIN stored securely on this device',
                  onTap: () => _configureMethod('PIN', pinLength: _pinLength),
                ),
              ],
              if (security.lockMethod == 'Password')
                ChatySettingsTile(
                  icon: Icons.password_rounded,
                  title: 'Change Lock Password',
                  subtitle: 'Password is hashed and stored in secure device storage',
                  onTap: () => _configureMethod('Password'),
                ),
              if (security.lockMethod == 'Pattern') ...[
                ChatySettingsTile(
                  icon: Icons.pattern_rounded,
                  title: 'Change Unlock Pattern',
                  subtitle: 'Draw and confirm a 3×3 gesture pattern',
                  onTap: () => _configureMethod('Pattern'),
                ),
                ChatySwitchTile(
                  title: 'Make Pattern Invisible',
                  subtitle: 'Hide pattern lines while drawing',
                  value: security.makePatternInvisible,
                  onChanged: (value) => widget.preferencesController.updateSecurity(
                    security.copyWith(makePatternInvisible: value),
                    logTitle: 'Pattern visibility',
                  ),
                ),
                ChatySwitchTile(
                  title: 'Disable Pattern Vibration',
                  subtitle: 'Turn off haptic feedback while drawing the pattern',
                  value: security.disablePatternVibration,
                  onChanged: (value) => widget.preferencesController.updateSecurity(
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
                onSelected: (value) => widget.preferencesController.updateSecurity(
                  security.copyWith(autoLockTimeout: value),
                  logTitle: 'Auto Lock Timeout',
                ),
              ),
              ChatySwitchTile(
                icon: Icons.notifications_paused_rounded,
                title: 'Hide Notification Content when Locked',
                subtitle: 'Conceal message body text while the application is locked',
                value: security.hideLockNotificationContent,
                onChanged: (value) => widget.preferencesController.updateSecurity(
                  security.copyWith(hideLockNotificationContent: value),
                  logTitle: 'Lock notification privacy',
                ),
              ),
              ChatySettingsTile(
                icon: Icons.lock_open_rounded,
                iconColor: Colors.deepOrangeAccent,
                title: 'Test Lock Screen',
                subtitle: 'Run the same authentication gate used by the application',
                onTap: () => AppLockOverlayModal.show(
                  context,
                  preferencesController: widget.preferencesController,
                  lockService: _lockService,
                  title: 'Test Chaty Lock',
                  reason: 'Authenticate to verify your Chaty Lock configuration',
                ),
              ),
            ],
          ],
        ),
        ChatySettingsSection(
          title: 'Chat Lock',
          description: 'Locked conversations require the same verified local authentication before their messages open.',
          children: [
            ChatySettingsTile(
              icon: Icons.chat_bubble_outline_rounded,
              iconColor: Colors.purpleAccent,
              title: 'Locked Chats',
              subtitle: lockedChats == 0
                  ? 'No chats locked • Long-press a chat and use the lock action'
                  : '$lockedChats chat${lockedChats == 1 ? '' : 's'} currently protected',
              badgeText: '$lockedChats',
              badgeColor: lockedChats > 0 ? Colors.purpleAccent : Colors.grey,
              onTap: null,
            ),
            ChatySettingsTile(
              icon: Icons.verified_user_outlined,
              title: 'Chat Lock Authentication',
              subtitle: 'Uses ${security.lockMethod}. Chat Lock works even when full App Lock is disabled.',
              onTap: () async {
                final configured = await _hasConfiguredMethod(security.lockMethod);
                if (!configured && mounted) await _configureMethod(security.lockMethod);
              },
            ),
          ],
        ),
      ],
    );
  }
}
