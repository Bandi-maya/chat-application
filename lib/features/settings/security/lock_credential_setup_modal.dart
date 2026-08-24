import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../data/services/local_lock_service.dart';
import 'pattern_lock_pad.dart';

enum StepState {
  enterCurrent,
  enterNew,
  confirmNew,
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
  final TextEditingController _textController = TextEditingController();
  final List<TextEditingController> _pinControllers = [];
  final List<FocusNode> _pinFocusNodes = [];

  StepState _currentStep = StepState.enterNew;
  bool _hasExistingSecret = false;
  String _newSecret = '';
  String _error = '';
  bool _busy = false;

  // Pattern states
  String? _firstPatternDrawn;
  String? _confirmPatternDrawn;
  final GlobalKey<PatternLockPadState> _padKey =
      GlobalKey<PatternLockPadState>();

  bool get _isPin => widget.method == 'PIN';
  bool get _isPattern => widget.method == 'Pattern';
  bool get _isPassword => widget.method == 'Password';
  bool get _isBiometric => widget.method == 'Biometric';
  bool get _isDeviceCredential => widget.method == 'Device Credential';

  @override
  void initState() {
    super.initState();
    _initPinControllers();
    _checkExistingSecret();
  }

  void _initPinControllers() {
    for (var i = 0; i < widget.pinLength; i++) {
      _pinControllers.add(TextEditingController());
      _pinFocusNodes.add(FocusNode());
    }
  }

  Future<void> _checkExistingSecret() async {
    final hasCred = await widget.lockService.hasCredential(widget.method);
    if (!mounted) return;
    setState(() {
      _hasExistingSecret = hasCred;
      if (hasCred) {
        _currentStep = StepState.enterCurrent;
      } else {
        _currentStep = StepState.enterNew;
      }
    });
  }

  @override
  void dispose() {
    _textController.dispose();
    for (final c in _pinControllers) {
      c.dispose();
    }
    for (final f in _pinFocusNodes) {
      f.dispose();
    }
    super.dispose();
  }

  String _getEnteredPin() {
    return _pinControllers.map((c) => c.text).join();
  }

  void _clearPinInputs() {
    for (final c in _pinControllers) {
      c.clear();
    }
    if (_pinFocusNodes.isNotEmpty) {
      _pinFocusNodes.first.requestFocus();
    }
  }

  Future<void> _handleContinue() async {
    setState(() => _error = '');

    if (_isPin) {
      final pin = _getEnteredPin();
      if (pin.length != widget.pinLength) {
        setState(() => _error = 'Enter all ${widget.pinLength} digits.');
        return;
      }

      if (_currentStep == StepState.enterCurrent) {
        setState(() => _busy = true);
        final isValid = await widget.lockService.verifyCredential('PIN', pin);
        if (!mounted) return;
        setState(() => _busy = false);
        if (isValid) {
          _clearPinInputs();
          setState(() {
            _currentStep = StepState.enterNew;
            _error = '';
          });
        } else {
          setState(() => _error = 'Current PIN is incorrect.');
          _clearPinInputs();
        }
      } else if (_currentStep == StepState.enterNew) {
        if (RegExp(r'^(\d)\1+$').hasMatch(pin)) {
          setState(() => _error = 'Weak PIN. Avoid repeating digits like 0000.');
          return;
        }
        _newSecret = pin;
        _clearPinInputs();
        setState(() {
          _currentStep = StepState.confirmNew;
          _error = '';
        });
      } else if (_currentStep == StepState.confirmNew) {
        if (pin != _newSecret) {
          setState(() => _error = 'PINs do not match. Try entering again.');
          _clearPinInputs();
          return;
        }
        await _saveFinalCredential(pin);
      }
    } else if (_isPassword) {
      final pass = _textController.text.trim();
      if (pass.isEmpty) {
        setState(() => _error = 'Please enter a password.');
        return;
      }

      if (_currentStep == StepState.enterCurrent) {
        setState(() => _busy = true);
        final isValid = await widget.lockService.verifyCredential('Password', pass);
        if (!mounted) return;
        setState(() => _busy = false);
        if (isValid) {
          _textController.clear();
          setState(() {
            _currentStep = StepState.enterNew;
            _error = '';
          });
        } else {
          setState(() => _error = 'Current password is incorrect.');
        }
      } else if (_currentStep == StepState.enterNew) {
        if (pass.length < 6) {
          setState(() => _error = 'Password must contain at least 6 characters.');
          return;
        }
        _newSecret = pass;
        _textController.clear();
        setState(() {
          _currentStep = StepState.confirmNew;
          _error = '';
        });
      } else if (_currentStep == StepState.confirmNew) {
        if (pass != _newSecret) {
          setState(() => _error = 'Passwords do not match. Try again.');
          return;
        }
        await _saveFinalCredential(pass);
      }
    }
  }

  Future<void> _saveFinalCredential(String secret) async {
    setState(() {
      _busy = true;
      _error = '';
    });
    try {
      await widget.lockService.setCredential(
        widget.method,
        secret,
        pinLength: _isPin ? widget.pinLength : null,
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = e.toString().replaceFirst('Invalid argument(s): ', '');
        });
      }
    }
  }

  // --- Pattern Flow ---
  void _onPatternComplete(String pattern) {
    final nodes = pattern.split('-').where((v) => v.isNotEmpty).toList();
    if (nodes.length < 4) {
      setState(() => _error = 'Connect at least 4 dots.');
      return;
    }

    setState(() {
      _error = '';
      if (_currentStep == StepState.enterCurrent) {
        _firstPatternDrawn = pattern;
      } else if (_currentStep == StepState.enterNew) {
        _firstPatternDrawn = pattern;
      } else {
        _confirmPatternDrawn = pattern;
      }
    });
  }

  Future<void> _handlePatternContinue() async {
    final pattern = _currentStep == StepState.confirmNew
        ? _confirmPatternDrawn
        : _firstPatternDrawn;

    if (pattern == null) {
      setState(() => _error = _currentStep == StepState.confirmNew
          ? 'Draw the confirmation pattern first.'
          : 'Draw your pattern first.');
      return;
    }

    if (_currentStep == StepState.enterCurrent) {
      setState(() => _busy = true);
      final isValid =
          await widget.lockService.verifyCredential('Pattern', pattern);
      if (!mounted) return;
      setState(() => _busy = false);
      if (isValid) {
        _padKey.currentState?.reset();
        setState(() {
          _firstPatternDrawn = null;
          _currentStep = StepState.enterNew;
          _error = '';
        });
      } else {
        _padKey.currentState?.reset();
        setState(() {
          _firstPatternDrawn = null;
          _error = 'Current pattern is incorrect.';
        });
      }
    } else if (_currentStep == StepState.enterNew) {
      _newSecret = pattern;
      _padKey.currentState?.reset();
      setState(() {
        _firstPatternDrawn = null;
        _confirmPatternDrawn = null;
        _currentStep = StepState.confirmNew;
        _error = '';
      });
    } else if (_currentStep == StepState.confirmNew) {
      if (pattern != _newSecret) {
        _padKey.currentState?.reset();
        setState(() {
          _confirmPatternDrawn = null;
          _error = 'Patterns do not match. Draw again to confirm.';
        });
        return;
      }
      await _saveFinalCredential(_newSecret);
    }
  }

  // --- Biometric / Device lock verification ---
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
            ? 'Biometric verification was cancelled or unavailable.'
            : 'Device credential verification was cancelled or unavailable.';
      });
    }
  }

  String _getStepTitle() {
    switch (_currentStep) {
      case StepState.enterCurrent:
        return 'Enter Current ${widget.method}';
      case StepState.enterNew:
        return _hasExistingSecret
            ? 'Enter New ${widget.method}'
            : 'Create ${widget.method}';
      case StepState.confirmNew:
        return 'Confirm New ${widget.method}';
    }
  }

  String _getStepSubtitle() {
    switch (_currentStep) {
      case StepState.enterCurrent:
        return 'Please authenticate with your existing ${widget.method} to continue.';
      case StepState.enterNew:
        if (_isPin) return 'Choose a ${widget.pinLength}-digit PIN code.';
        if (_isPattern) return 'Draw an unlock pattern connecting at least 4 dots.';
        if (_isPassword) return 'Enter a password (min 6 characters).';
        return 'Choose your credential.';
      case StepState.confirmNew:
        return 'Re-enter the same ${widget.method} to ensure accuracy.';
    }
  }

  Widget _buildOtpPinBoxes(ThemeData theme) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(widget.pinLength, (index) {
        return Container(
          width: 48,
          height: 56,
          margin: const EdgeInsets.symmetric(horizontal: 5),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: _pinFocusNodes[index].hasFocus
                  ? theme.colorScheme.primary
                  : theme.colorScheme.outline.withValues(alpha: 0.3),
              width: _pinFocusNodes[index].hasFocus ? 2 : 1,
            ),
          ),
          child: TextField(
            controller: _pinControllers[index],
            focusNode: _pinFocusNodes[index],
            keyboardType: TextInputType.number,
            textAlign: TextAlign.center,
            obscureText: true,
            maxLength: 1,
            style: TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: theme.colorScheme.onSurface,
            ),
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
            ],
            decoration: const InputDecoration(
              counterText: '',
              border: InputBorder.none,
            ),
            onChanged: (val) {
              if (val.isNotEmpty) {
                if (index < widget.pinLength - 1) {
                  _pinFocusNodes[index + 1].requestFocus();
                } else {
                  _pinFocusNodes[index].unfocus();
                  _handleContinue();
                }
              } else if (val.isEmpty && index > 0) {
                _pinFocusNodes[index - 1].requestFocus();
              }
            },
          ),
        );
      }),
    );
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
                        _getStepTitle(),
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        _getStepSubtitle(),
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
                      if (_currentStep == StepState.confirmNew) {
                        _confirmPatternDrawn = null;
                      } else {
                        _firstPatternDrawn = null;
                      }
                    },
                    hideTrace: false,
                    enableHaptics: true,
                  ),
                ),
              ),
              const SizedBox(height: 16),
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
                      onPressed: ((_currentStep == StepState.confirmNew
                                  ? _confirmPatternDrawn != null
                                  : _firstPatternDrawn != null) &&
                              !_busy)
                          ? _handlePatternContinue
                          : null,
                      child: _busy
                          ? const SizedBox.square(
                              dimension: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : Text(_currentStep == StepState.confirmNew
                              ? 'Confirm & Save'
                              : 'Continue'),
                    ),
                  ),
                ],
              ),
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
            ] else if (_isPin) ...[
              const SizedBox(height: 10),
              _buildOtpPinBoxes(theme),
              const SizedBox(height: 24),
              FilledButton(
                onPressed: _busy ? null : _handleContinue,
                child: _busy
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(_currentStep == StepState.confirmNew
                        ? 'Confirm PIN'
                        : 'Continue'),
              ),
            ] else ...[
              TextField(
                controller: _textController,
                enabled: !_busy,
                obscureText: true,
                keyboardType: TextInputType.visiblePassword,
                autofillHints: const <String>[],
                onSubmitted: (_) => _handleContinue(),
                decoration: InputDecoration(
                  labelText: _currentStep == StepState.enterCurrent
                      ? 'Current Password'
                      : _currentStep == StepState.enterNew
                          ? 'New Password'
                          : 'Confirm Password',
                  border: const OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: _busy ? null : _handleContinue,
                child: _busy
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(_currentStep == StepState.confirmNew
                        ? 'Save Password'
                        : 'Continue'),
              ),
            ],
            const SizedBox(height: 14),
            Text(
              'Credentials are encrypted with salted PBKDF2 hashes in hardware-backed secure storage.',
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
