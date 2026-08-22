import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../data/services/local_lock_service.dart';
import 'pattern_lock_pad.dart';

enum PatternSetupStep {
  create,
  confirm,
}

class LockCredentialSetupModal extends StatefulWidget {
  final String method;
  final int pinLength;
  final LocalLockService lockService;

  const LockCredentialSetupModal({
    super.key,
    required this.method,
    required this.pinLength,
    required this.lockService,
  });

  static Future<bool> show(
    BuildContext context, {
    required String method,
    required int pinLength,
    required LocalLockService lockService,
  }) async {
    return await showModalBottomSheet<bool>(
          context: context,
          isScrollControlled: true,
          useSafeArea: true,
          showDragHandle: true,
          builder: (_) => LockCredentialSetupModal(
            method: method,
            pinLength: pinLength,
            lockService: lockService,
          ),
        ) ??
        false;
  }

  @override
  State<LockCredentialSetupModal> createState() =>
      _LockCredentialSetupModalState();
}

class _LockCredentialSetupModalState extends State<LockCredentialSetupModal> {
  final TextEditingController _primaryController = TextEditingController();
  final TextEditingController _confirmController = TextEditingController();
  
  // Pattern setup explicit states
  PatternSetupStep _patternStep = PatternSetupStep.create;
  String? _firstPatternDrawn;
  String? _confirmPatternDrawn;
  final GlobalKey<PatternLockPadState> _padKey = GlobalKey<PatternLockPadState>();

  String _error = '';
  bool _busy = false;

  bool get _isPin => widget.method == 'PIN';
  bool get _isPattern => widget.method == 'Pattern';
  bool get _isPassword => widget.method == 'Password';
  bool get _isBiometric => widget.method == 'Biometric';
  bool get _isDeviceCredential => widget.method == 'Device Credential';

  @override
  void dispose() {
    _primaryController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  Future<void> _saveTextCredential() async {
    final primary = _primaryController.text.trim();
    final confirmation = _confirmController.text.trim();

    if (_isPin) {
      if (!RegExp(r'^\d+$').hasMatch(primary) ||
          primary.length != widget.pinLength) {
        setState(() => _error = 'Enter exactly ${widget.pinLength} digits.');
        return;
      }
      // Check for extremely weak PINs (all same digits or simple ascending/descending)
      if (RegExp(r'^(\d)\1+$').hasMatch(primary)) {
        setState(() => _error = 'Weak PIN. Avoid repeating digits like 0000 or 1111.');
        return;
      }
    } else if (_isPassword && primary.length < 6) {
      setState(() => _error = 'Password must contain at least 6 characters.');
      return;
    }

    if (primary != confirmation) {
      setState(() => _error = 'PINs/Passwords don\'t match. Confirm again.');
      return;
    }

    setState(() {
      _busy = true;
      _error = '';
    });
    try {
      await widget.lockService.setCredential(
        widget.method,
        primary,
        pinLength: _isPin ? widget.pinLength : null,
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = error.toString().replaceFirst('Invalid argument(s): ', '');
        });
      }
    }
  }

  // --- Pattern strict 2-step handling ---

  void _onPatternComplete(String pattern) {
    final nodes = pattern
        .split('-')
        .where((value) => value.isNotEmpty)
        .toList(growable: false);

    if (nodes.length < 4) {
      setState(() => _error = 'Connect at least 4 dots.');
      return;
    }

    setState(() {
      _error = '';
      if (_patternStep == PatternSetupStep.create) {
        _firstPatternDrawn = pattern;
      } else {
        _confirmPatternDrawn = pattern;
      }
    });
  }

  void _continuePatternStep() {
    if (_firstPatternDrawn == null) {
      setState(() => _error = 'Draw your pattern first.');
      return;
    }
    setState(() {
      _patternStep = PatternSetupStep.confirm;
      _confirmPatternDrawn = null;
      _error = '';
    });
    _padKey.currentState?.reset();
  }

  Future<void> _confirmAndSavePattern() async {
    if (_confirmPatternDrawn == null) {
      setState(() => _error = 'Draw the confirmation pattern.');
      return;
    }

    if (_firstPatternDrawn != _confirmPatternDrawn) {
      setState(() {
        _confirmPatternDrawn = null;
        _error = 'Patterns don\'t match. Draw the confirmation pattern again.';
      });
      _padKey.currentState?.reset();
      return;
    }

    setState(() {
      _busy = true;
      _error = '';
    });

    try {
      await widget.lockService.setCredential('Pattern', _firstPatternDrawn!);
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = error.toString().replaceFirst('Invalid argument(s): ', '');
        });
      }
    }
  }

  void _startPatternOver() {
    setState(() {
      _patternStep = PatternSetupStep.create;
      _firstPatternDrawn = null;
      _confirmPatternDrawn = null;
      _error = '';
    });
    _padKey.currentState?.reset();
  }

  // --- Native Biometrics / Device Credential ---

  Future<void> _verifyNativeMethod() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = '';
    });
    final success = _isBiometric
        ? await widget.lockService.authenticateBiometric(
            reason: 'Verify your biometric to enable Chaty Lock',
          )
        : await widget.lockService.authenticateDeviceCredential(
            reason: 'Verify your device screen lock to enable Chaty Lock',
          );
    if (!mounted) return;
    if (success) {
      Navigator.of(context).pop(true);
    } else {
      setState(() {
        _busy = false;
        _error = _isBiometric
            ? 'Biometric verification was cancelled, unavailable, or no biometric is enrolled.'
            : 'Device credential verification was cancelled or unavailable.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;

    return Padding(
      padding: EdgeInsets.fromLTRB(24, 8, 24, 24 + bottomInset),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Container(
                  width: 46,
                  height: 46,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(
                    _isPattern
                        ? Icons.pattern_rounded
                        : _isBiometric
                        ? Icons.fingerprint_rounded
                        : _isDeviceCredential
                        ? Icons.phonelink_lock_rounded
                        : _isPassword
                        ? Icons.password_rounded
                        : Icons.pin_rounded,
                    color: theme.colorScheme.primary,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Set up ${widget.method}',
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        _isPattern
                            ? (_patternStep == PatternSetupStep.create
                                ? 'Step 1: Draw your unlock pattern (min 4 dots).'
                                : 'Step 2: Draw the same pattern again to confirm.')
                            : _isBiometric
                            ? 'Chaty uses the biometric enrolled on this device.'
                            : _isDeviceCredential
                            ? 'Use the device PIN, pattern, password, or biometric managed by the OS.'
                            : 'Create a local credential stored securely on this device.',
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            if (_error.isNotEmpty) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: theme.colorScheme.errorContainer,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  _error,
                  style: TextStyle(color: theme.colorScheme.onErrorContainer),
                ),
              ),
              const SizedBox(height: 16),
            ],
            if (_isPattern) ...[
              Center(
                child: IgnorePointer(
                  ignoring: _busy,
                  child: PatternLockPad(
                    key: _padKey,
                    clearOnFinish: false,
                    onPatternComplete: _onPatternComplete,
                    onPatternReset: () {
                      if (_patternStep == PatternSetupStep.create) {
                        _firstPatternDrawn = null;
                      } else {
                        _confirmPatternDrawn = null;
                      }
                    },
                    hideTrace: false,
                    enableHaptics: true,
                  ),
                ),
              ),
              const SizedBox(height: 16),
              if (_patternStep == PatternSetupStep.create) ...[
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _busy ? null : () => _padKey.currentState?.reset(),
                        child: const Text('Clear'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton(
                        onPressed: (_firstPatternDrawn != null && !_busy)
                            ? _continuePatternStep
                            : null,
                        child: const Text('Continue / OK'),
                      ),
                    ),
                  ],
                ),
              ] else ...[
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _busy ? null : _startPatternOver,
                        child: const Text('Start over'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton(
                        onPressed: (_confirmPatternDrawn != null && !_busy)
                            ? _confirmAndSavePattern
                            : null,
                        child: _busy
                            ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Text('Confirm Pattern'),
                      ),
                    ),
                  ],
                ),
              ],
            ] else if (_isBiometric || _isDeviceCredential) ...[
              const SizedBox(height: 8),
              Icon(
                _isBiometric
                    ? Icons.fingerprint_rounded
                    : Icons.phonelink_lock_rounded,
                size: 86,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(height: 18),
              FilledButton.icon(
                onPressed: _busy ? null : _verifyNativeMethod,
                icon: _busy
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Icon(
                        _isBiometric
                            ? Icons.fingerprint_rounded
                            : Icons.verified_user_rounded,
                      ),
                label: Text(
                  _busy
                      ? 'Verifying…'
                      : (_isBiometric
                            ? 'Verify biometric'
                            : 'Verify device lock'),
                ),
              ),
            ] else ...[
              TextField(
                controller: _primaryController,
                enabled: !_busy,
                obscureText: _isPassword,
                keyboardType: _isPin
                    ? TextInputType.number
                    : TextInputType.visiblePassword,
                maxLength: _isPin ? widget.pinLength : null,
                inputFormatters: _isPin
                    ? <TextInputFormatter>[
                        FilteringTextInputFormatter.digitsOnly,
                      ]
                    : null,
                autofillHints: const <String>[],
                decoration: InputDecoration(
                  labelText: _isPin
                      ? 'Enter ${widget.pinLength}-digit PIN'
                      : 'Enter Password',
                  border: const OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _confirmController,
                enabled: !_busy,
                obscureText: true,
                keyboardType: _isPin
                    ? TextInputType.number
                    : TextInputType.visiblePassword,
                maxLength: _isPin ? widget.pinLength : null,
                inputFormatters: _isPin
                    ? <TextInputFormatter>[
                        FilteringTextInputFormatter.digitsOnly,
                      ]
                    : null,
                autofillHints: const <String>[],
                onSubmitted: (_) => _saveTextCredential(),
                decoration: InputDecoration(
                  labelText: _isPin ? 'Confirm PIN' : 'Confirm Password',
                  border: const OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: _busy ? null : _saveTextCredential,
                child: _busy
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('Save credential'),
              ),
            ],
            const SizedBox(height: 14),
            Text(
              'Credentials are encrypted with salted PBKDF2 hashes in hardware-backed secure storage and never sent to cloud servers.',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
                fontSize: 11.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
