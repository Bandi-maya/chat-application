import 'package:flutter/material.dart';

import '../../../data/services/local_lock_service.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import 'lock_credential_setup_modal.dart';
import 'pattern_lock_pad.dart';

class AppLockOverlayModal extends StatefulWidget {
  final ChatyPreferencesController preferencesController;
  final LocalLockService? lockService;
  final VoidCallback? onUnlocked;
  final String title;
  final String reason;

  const AppLockOverlayModal({
    super.key,
    required this.preferencesController,
    this.lockService,
    this.onUnlocked,
    this.title = 'Chaty Lock',
    this.reason = 'Authenticate to unlock Chaty',
  });

  static Future<bool?> show(
    BuildContext context, {
    required ChatyPreferencesController preferencesController,
    LocalLockService? lockService,
    String title = 'Chat Lock',
    String reason = 'Authenticate to open this locked chat',
  }) {
    return showGeneralDialog<bool>(
      context: context,
      barrierDismissible: false,
      barrierColor: Colors.black.withValues(alpha: 0.75),
      transitionDuration: const Duration(milliseconds: 180),
      pageBuilder: (ctx, animation, secondaryAnimation) => AppLockOverlayModal(
        preferencesController: preferencesController,
        lockService: lockService,
        title: title,
        reason: reason,
        onUnlocked: () => Navigator.of(ctx).pop(true),
      ),
      transitionBuilder: (context, animation, secondaryAnimation, child) =>
          FadeTransition(
            opacity: CurvedAnimation(
              parent: animation,
              curve: Curves.easeOutCubic,
            ),
            child: ScaleTransition(
              scale: Tween<double>(begin: 0.985, end: 1).animate(
                CurvedAnimation(parent: animation, curve: Curves.easeOutCubic),
              ),
              child: child,
            ),
          ),
    );
  }

  @override
  State<AppLockOverlayModal> createState() => _AppLockOverlayModalState();
}

class _AppLockOverlayModalState extends State<AppLockOverlayModal> {
  final TextEditingController _passwordController = TextEditingController();
  String _enteredPin = '';
  String _errorMessage = '';
  bool _busy = false;
  bool _biometricAvailable = false;
  bool _hasConfiguredCredential = true;
  bool _nativePromptStarted = false;
  int _pinLength = 4;
  int _cooldownSeconds = 0;

  LocalLockService get _lockService =>
      widget.lockService ?? locator<LocalLockService>();

  @override
  void initState() {
    super.initState();
    _loadCapabilities();
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _startNativeMethodIfNeeded(),
    );
  }

  @override
  void dispose() {
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _loadCapabilities() async {
    final method = widget.preferencesController.security.lockMethod;
    final pinLength = await _lockService.getPinLength();
    final biometric = await _lockService.canUseBiometrics();
    final cooldown = await _lockService.getRemainingCooldownSeconds();
    var hasCredential = true;
    if (method == 'PIN' || method == 'Pattern' || method == 'Password') {
      hasCredential = await _lockService.hasCredential(method);
    }
    if (!mounted) return;
    setState(() {
      _pinLength = pinLength;
      _biometricAvailable = biometric;
      _hasConfiguredCredential = hasCredential;
      _cooldownSeconds = cooldown;
      if (cooldown > 0) {
        _errorMessage =
            'Too many failed attempts. Try again in $cooldown seconds.';
      }
    });
  }

  void _completeUnlock() {
    if (!mounted) return;
    widget.onUnlocked?.call();
  }

  Future<void> _startNativeMethodIfNeeded() async {
    if (_nativePromptStarted || !mounted) return;
    final method = widget.preferencesController.security.lockMethod;
    if (method != 'Biometric' && method != 'Device Credential') return;
    _nativePromptStarted = true;
    await _runNativeAuthentication(method);
  }

  Future<void> _runNativeAuthentication(String method) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _errorMessage = '';
    });
    final success = method == 'Device Credential'
        ? await _lockService.authenticateDeviceCredential(reason: widget.reason)
        : await _lockService.authenticateBiometric(reason: widget.reason);
    if (!mounted) return;
    if (success) {
      _completeUnlock();
      return;
    }
    setState(() {
      _busy = false;
      _errorMessage = method == 'Device Credential'
          ? 'Device authentication was cancelled or is unavailable.'
          : 'Biometric authentication was cancelled, unavailable, or no biometric is enrolled.';
    });
  }

  Future<void> _verifySecret(String method, String value) async {
    if (_busy) return;

    final cooldown = await _lockService.getRemainingCooldownSeconds();
    if (cooldown > 0) {
      setState(() {
        _cooldownSeconds = cooldown;
        _errorMessage =
            'Too many failed attempts. Try again in $cooldown seconds.';
        _enteredPin = '';
        _passwordController.clear();
      });
      return;
    }

    setState(() {
      _busy = true;
      _errorMessage = '';
    });

    final valid = await _lockService.verifyCredential(method, value);
    if (!mounted) return;
    if (valid) {
      _completeUnlock();
      return;
    }

    final newCooldown = await _lockService.getRemainingCooldownSeconds();
    setState(() {
      _busy = false;
      _enteredPin = '';
      _passwordController.clear();
      _cooldownSeconds = newCooldown;
      if (newCooldown > 0) {
        _errorMessage =
            'Too many failed attempts. Try again in $newCooldown seconds.';
      } else {
        _errorMessage = 'Incorrect ${method.toLowerCase()}. Try again.';
      }
    });
  }

  Future<void> _verifyPinDigit(String digit) async {
    if (_busy || _enteredPin.length >= _pinLength || _cooldownSeconds > 0)
      return;
    final next = '$_enteredPin$digit';
    setState(() {
      _enteredPin = next;
      _errorMessage = '';
    });
    if (next.length == _pinLength) {
      await _verifySecret('PIN', next);
    }
  }

  Future<void> _setupMissingCredential() async {
    final method = widget.preferencesController.security.lockMethod;
    if (method != 'PIN' && method != 'Pattern' && method != 'Password') return;
    final configured = await LockCredentialSetupModal.show(
      context,
      method: method,
      pinLength: _pinLength,
      lockService: _lockService,
    );
    if (configured) await _loadCapabilities();
  }

  Widget _buildPinPad(ThemeData theme) {
    return Column(
      children: [
        Wrap(
          alignment: WrapAlignment.center,
          spacing: 14,
          children: List<Widget>.generate(_pinLength, (index) {
            final filled = index < _enteredPin.length;
            return AnimatedContainer(
              duration: const Duration(milliseconds: 120),
              width: 16,
              height: 16,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: filled ? theme.colorScheme.primary : Colors.transparent,
                border: Border.all(color: theme.colorScheme.primary, width: 2),
              ),
            );
          }),
        ),
        const SizedBox(height: 24),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 320),
          child: GridView.count(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            crossAxisCount: 3,
            mainAxisSpacing: 12,
            crossAxisSpacing: 12,
            childAspectRatio: 1.30,
            children: [
              _NumberKey(
                label: '1',
                sublabel: '',
                onTap: (_busy || _cooldownSeconds > 0)
                    ? null
                    : () => _verifyPinDigit('1'),
              ),
              _NumberKey(
                label: '2',
                sublabel: 'ABC',
                onTap: (_busy || _cooldownSeconds > 0)
                    ? null
                    : () => _verifyPinDigit('2'),
              ),
              _NumberKey(
                label: '3',
                sublabel: 'DEF',
                onTap: (_busy || _cooldownSeconds > 0)
                    ? null
                    : () => _verifyPinDigit('3'),
              ),
              _NumberKey(
                label: '4',
                sublabel: 'GHI',
                onTap: (_busy || _cooldownSeconds > 0)
                    ? null
                    : () => _verifyPinDigit('4'),
              ),
              _NumberKey(
                label: '5',
                sublabel: 'JKL',
                onTap: (_busy || _cooldownSeconds > 0)
                    ? null
                    : () => _verifyPinDigit('5'),
              ),
              _NumberKey(
                label: '6',
                sublabel: 'MNO',
                onTap: (_busy || _cooldownSeconds > 0)
                    ? null
                    : () => _verifyPinDigit('6'),
              ),
              _NumberKey(
                label: '7',
                sublabel: 'PQRS',
                onTap: (_busy || _cooldownSeconds > 0)
                    ? null
                    : () => _verifyPinDigit('7'),
              ),
              _NumberKey(
                label: '8',
                sublabel: 'TUV',
                onTap: (_busy || _cooldownSeconds > 0)
                    ? null
                    : () => _verifyPinDigit('8'),
              ),
              _NumberKey(
                label: '9',
                sublabel: 'WXYZ',
                onTap: (_busy || _cooldownSeconds > 0)
                    ? null
                    : () => _verifyPinDigit('9'),
              ),
              _PinActionKey(
                semanticsLabel: 'Use biometric',
                icon: Icons.fingerprint_rounded,
                onTap: (_busy || !_biometricAvailable)
                    ? null
                    : () => _runNativeAuthentication('Biometric'),
              ),
              _NumberKey(
                label: '0',
                sublabel: '+',
                onTap: (_busy || _cooldownSeconds > 0)
                    ? null
                    : () => _verifyPinDigit('0'),
              ),
              _PinActionKey(
                semanticsLabel: 'Delete digit',
                icon: Icons.backspace_outlined,
                onTap: (_busy || _enteredPin.isEmpty)
                    ? null
                    : () => setState(
                        () => _enteredPin = _enteredPin.substring(
                          0,
                          _enteredPin.length - 1,
                        ),
                      ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final security = widget.preferencesController.security;
    final theme = Theme.of(context);
    final method = security.lockMethod;

    return PopScope(
      canPop: false,
      child: Scaffold(
        backgroundColor: theme.scaffoldBackgroundColor,
        body: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 430),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Container(
                      width: 74,
                      height: 74,
                      decoration: BoxDecoration(
                        color: theme.colorScheme.primary.withValues(
                          alpha: 0.12,
                        ),
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        method == 'Biometric'
                            ? Icons.fingerprint_rounded
                            : method == 'Pattern'
                            ? Icons.pattern_rounded
                            : method == 'Device Credential'
                            ? Icons.phonelink_lock_rounded
                            : Icons.lock_rounded,
                        size: 36,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                    const SizedBox(height: 18),
                    Text(
                      widget.title,
                      style: theme.textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      method == 'Device Credential'
                          ? 'Use your device screen lock to continue'
                          : 'Use $method to continue',
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 22),
                    if (_errorMessage.isNotEmpty) ...[
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.errorContainer,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          _errorMessage,
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: theme.colorScheme.onErrorContainer,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],
                    if (!_hasConfiguredCredential &&
                        (method == 'PIN' ||
                            method == 'Pattern' ||
                            method == 'Password')) ...[
                      Text(
                        'This lock method has not been configured securely on this device yet.',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodyMedium,
                      ),
                      const SizedBox(height: 14),
                      FilledButton.icon(
                        onPressed: _setupMissingCredential,
                        icon: const Icon(Icons.security_rounded),
                        label: Text('Set up $method'),
                      ),
                    ] else if (method == 'PIN') ...[
                      _buildPinPad(theme),
                      const SizedBox(height: 12),
                      if (_biometricAvailable)
                        TextButton.icon(
                          onPressed: _busy
                              ? null
                              : () => _runNativeAuthentication('Biometric'),
                          icon: const Icon(Icons.fingerprint_rounded, size: 18),
                          label: const Text('Use fingerprint / Face unlock'),
                          style: TextButton.styleFrom(
                            foregroundColor: theme.colorScheme.primary,
                            padding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 8,
                            ),
                          ),
                        ),
                      TextButton(
                        onPressed: _busy
                            ? null
                            : () =>
                                  _runNativeAuthentication('Device Credential'),
                        child: Text(
                          'Can\'t remember? Use device screen lock',
                          style: TextStyle(
                            fontSize: 12.5,
                            color: theme.colorScheme.onSurfaceVariant
                                .withValues(alpha: 0.8),
                          ),
                        ),
                      ),
                    ] else if (method == 'Pattern') ...[
                      PatternLockPad(
                        hideTrace: security.makePatternInvisible,
                        enableHaptics: !security.disablePatternVibration,
                        onPatternComplete: (pattern) =>
                            _verifySecret('Pattern', pattern),
                      ),
                      const SizedBox(height: 14),
                      if (_biometricAvailable)
                        TextButton.icon(
                          onPressed: _busy
                              ? null
                              : () => _runNativeAuthentication('Biometric'),
                          icon: const Icon(Icons.fingerprint_rounded, size: 18),
                          label: const Text('Use fingerprint / Face unlock'),
                          style: TextButton.styleFrom(
                            foregroundColor: theme.colorScheme.primary,
                            padding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 8,
                            ),
                          ),
                        ),
                      TextButton(
                        onPressed: _busy
                            ? null
                            : () =>
                                  _runNativeAuthentication('Device Credential'),
                        child: Text(
                          'Can\'t remember? Use device screen lock',
                          style: TextStyle(
                            fontSize: 12.5,
                            color: theme.colorScheme.onSurfaceVariant
                                .withValues(alpha: 0.8),
                          ),
                        ),
                      ),
                    ] else if (method == 'Password') ...[
                      TextField(
                        controller: _passwordController,
                        enabled: !_busy && _cooldownSeconds == 0,
                        obscureText: true,
                        autofocus: true,
                        onSubmitted: (value) =>
                            _verifySecret('Password', value),
                        decoration: InputDecoration(
                          labelText: 'Password',
                          filled: true,
                          fillColor: theme.colorScheme.surfaceContainerHighest
                              .withValues(alpha: 0.35),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(14),
                          ),
                        ),
                      ),
                      const SizedBox(height: 14),
                      FilledButton(
                        onPressed: (_busy || _cooldownSeconds > 0)
                            ? null
                            : () => _verifySecret(
                                'Password',
                                _passwordController.text,
                              ),
                        style: FilledButton.styleFrom(
                          minimumSize: const Size(double.infinity, 48),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(14),
                          ),
                        ),
                        child: _busy
                            ? const SizedBox.square(
                                dimension: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Text('Unlock'),
                      ),
                      const SizedBox(height: 10),
                      if (_biometricAvailable)
                        TextButton.icon(
                          onPressed: _busy
                              ? null
                              : () => _runNativeAuthentication('Biometric'),
                          icon: const Icon(Icons.fingerprint_rounded),
                          label: const Text('Use biometric instead'),
                        ),
                      TextButton(
                        onPressed: _busy
                            ? null
                            : () =>
                                  _runNativeAuthentication('Device Credential'),
                        child: Text(
                          'Can\'t remember? Use device screen lock',
                          style: TextStyle(
                            fontSize: 12.5,
                            color: theme.colorScheme.onSurfaceVariant
                                .withValues(alpha: 0.8),
                          ),
                        ),
                      ),
                    ] else ...[
                      const SizedBox(height: 8),
                      if (_busy)
                        const CircularProgressIndicator()
                      else
                        FilledButton.icon(
                          onPressed: () => _runNativeAuthentication(method),
                          icon: Icon(
                            method == 'Device Credential'
                                ? Icons.phonelink_lock_rounded
                                : Icons.fingerprint_rounded,
                          ),
                          label: Text(
                            method == 'Device Credential'
                                ? 'Authenticate with device lock'
                                : 'Scan biometric',
                          ),
                        ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _NumberKey extends StatefulWidget {
  final String label;
  final String sublabel;
  final VoidCallback? onTap;

  const _NumberKey({
    required this.label,
    this.sublabel = '',
    required this.onTap,
  });

  @override
  State<_NumberKey> createState() => _NumberKeyState();
}

class _NumberKeyState extends State<_NumberKey> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primaryColor = theme.colorScheme.primary;

    return AnimatedScale(
      scale: _pressed ? 0.94 : 1.0,
      duration: const Duration(milliseconds: 100),
      curve: Curves.easeOutCubic,
      child: Material(
        color: isDark
            ? (_pressed
                  ? primaryColor.withValues(alpha: 0.22)
                  : theme.colorScheme.surfaceContainerHighest.withValues(
                      alpha: 0.45,
                    ))
            : (_pressed
                  ? primaryColor.withValues(alpha: 0.15)
                  : theme.colorScheme.surfaceContainerHighest.withValues(
                      alpha: 0.6,
                    )),
        borderRadius: BorderRadius.circular(28),
        child: InkWell(
          onTap: widget.onTap,
          onTapDown: widget.onTap == null
              ? null
              : (_) => setState(() => _pressed = true),
          onTapUp: (_) => setState(() => _pressed = false),
          onTapCancel: () => setState(() => _pressed = false),
          borderRadius: BorderRadius.circular(28),
          splashColor: primaryColor.withValues(alpha: 0.15),
          highlightColor: Colors.transparent,
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  widget.label,
                  style: theme.textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                    fontSize: 24,
                    height: 1.1,
                  ),
                ),
                if (widget.sublabel.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(
                      widget.sublabel,
                      style: TextStyle(
                        fontSize: 9.5,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 1.5,
                        color: theme.colorScheme.onSurfaceVariant.withValues(
                          alpha: 0.7,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Pin-pad action key with tactile spring physics and glassmorphism.
class _PinActionKey extends StatefulWidget {
  final String semanticsLabel;
  final IconData icon;
  final VoidCallback? onTap;

  const _PinActionKey({
    required this.semanticsLabel,
    required this.icon,
    required this.onTap,
  });

  @override
  State<_PinActionKey> createState() => _PinActionKeyState();
}

class _PinActionKeyState extends State<_PinActionKey> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Semantics(
      button: true,
      enabled: widget.onTap != null,
      label: widget.semanticsLabel,
      child: AnimatedScale(
        scale: _pressed ? 0.94 : 1.0,
        duration: const Duration(milliseconds: 100),
        curve: Curves.easeOutCubic,
        child: Material(
          color: isDark
              ? theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.3)
              : theme.colorScheme.surfaceContainerHighest.withValues(
                  alpha: 0.45,
                ),
          borderRadius: BorderRadius.circular(28),
          child: InkWell(
            onTap: widget.onTap,
            onTapDown: widget.onTap == null
                ? null
                : (_) => setState(() => _pressed = true),
            onTapUp: (_) => setState(() => _pressed = false),
            onTapCancel: () => setState(() => _pressed = false),
            borderRadius: BorderRadius.circular(28),
            child: Center(
              child: Icon(
                widget.icon,
                size: 24,
                color: widget.onTap != null
                    ? theme.colorScheme.onSurface
                    : theme.colorScheme.onSurface.withValues(alpha: 0.3),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
