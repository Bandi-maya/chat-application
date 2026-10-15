import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/design_system/design_system.dart';
import '../../data/repositories/chaty_data_store.dart';
import '../../injection/locator.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../ui/core/theme/theme_presets.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../chats/chat_detail_screen.dart';
import '../chats/hidden_vault_controller.dart';

/// Dedicated Biometric and PBKDF2 PIN Hidden Chat Vault Screen.
/// Features shake-on-error physics, decoy mode support, and isolated encrypted thread list.
class HiddenVaultScreen extends StatefulWidget {
  final HiddenVaultController? controller;
  final ChatyDataStore? dataStore;

  const HiddenVaultScreen({
    super.key,
    this.controller,
    this.dataStore,
  });

  @override
  State<HiddenVaultScreen> createState() => _HiddenVaultScreenState();
}

class _HiddenVaultScreenState extends State<HiddenVaultScreen>
    with SingleTickerProviderStateMixin {
  late final HiddenVaultController _vaultController;
  late final ChatyDataStore _dataStore;
  final StringBuffer _enteredPin = StringBuffer();
  late final AnimationController _shakeController;
  late final Animation<double> _shakeAnimation;
  String? _errorMessage;
  bool _isAuthenticating = false;

  @override
  void initState() {
    super.initState();
    _vaultController = widget.controller ??
        (locator.isRegistered<HiddenVaultController>()
            ? locator<HiddenVaultController>()
            : HiddenVaultController());
    _dataStore = widget.dataStore ?? locator<ChatyDataStore>();

    _shakeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 400),
    );
    _shakeAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _shakeController, curve: Curves.easeInOut),
    );

    // Prompt biometric on screen load if vault is locked
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_vaultController.isUnlocked) {
        _triggerBiometricAuth();
      }
    });
  }

  @override
  void dispose() {
    _shakeController.dispose();
    super.dispose();
  }

  Future<void> _triggerBiometricAuth() async {
    if (_isAuthenticating) return;
    setState(() => _isAuthenticating = true);
    final authed = await _vaultController.unlockVault();
    if (mounted) {
      setState(() => _isAuthenticating = false);
      if (authed) {
        HapticFeedback.lightImpact();
      }
    }
  }

  void _onDigitPressed(String digit) {
    if (_enteredPin.length >= 6) return;
    HapticFeedback.selectionClick();
    setState(() {
      _enteredPin.write(digit);
      _errorMessage = null;
    });

    if (_enteredPin.length == 4 || _enteredPin.length == 6) {
      _verifyPinAttempt();
    }
  }

  void _onBackspacePressed() {
    if (_enteredPin.isEmpty) return;
    HapticFeedback.selectionClick();
    setState(() {
      final str = _enteredPin.toString();
      _enteredPin.clear();
      _enteredPin.write(str.substring(0, str.length - 1));
      _errorMessage = null;
    });
  }

  Future<void> _verifyPinAttempt() async {
    final pin = _enteredPin.toString();
    final verified = await _vaultController.verifyPin(pin);
    if (!mounted) return;

    if (verified) {
      HapticFeedback.mediumImpact();
      setState(() {
        _enteredPin.clear();
        _errorMessage = null;
      });
    } else if (pin.length >= 4) {
      // Shake animation and haptic rejection
      HapticFeedback.heavyImpact();
      _shakeController.forward(from: 0.0);
      setState(() {
        _errorMessage = 'Incorrect PIN. Try again or use biometrics.';
        _enteredPin.clear();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return AnimatedBuilder(
      animation: _vaultController,
      builder: (context, _) {
        if (_vaultController.isUnlocked) {
          return _buildUnlockedVaultView(context, isDark);
        }
        return _buildLockScreenView(context, isDark);
      },
    );
  }

  Widget _buildLockScreenView(BuildContext context, bool isDark) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0D1117) : const Color(0xFFF0F4F8),
      appBar: AppBar(
        title: const Text('Hidden Vault', style: TextStyle(fontWeight: FontWeight.w700)),
        centerTitle: true,
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 16.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Spacer(),
              // Vault icon with ambient glow
              Container(
                width: 72,
                height: 72,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: theme.colorScheme.primary.withOpacity(0.14),
                  border: Border.all(
                    color: theme.colorScheme.primary.withOpacity(0.35),
                    width: 1.5,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: theme.colorScheme.primary.withOpacity(0.25),
                      blurRadius: 24,
                      spreadRadius: 2,
                    ),
                  ],
                ),
                child: Icon(
                  Icons.lock_outline_rounded,
                  size: 34,
                  color: theme.colorScheme.primary,
                ),
              ),
              const SizedBox(height: 20),
              Text(
                'Unlock Vault',
                style: DesignTypographyScale.title(context),
              ),
              const SizedBox(height: 6),
              Text(
                'Enter your secure PIN or use biometrics',
                style: DesignTypographyScale.caption(context,
                    color: isDark ? Colors.white60 : Colors.black54),
              ),
              const SizedBox(height: 24),

              // PIN Indicator dots with shake animation
              AnimatedBuilder(
                animation: _shakeAnimation,
                builder: (context, child) {
                  final offset = math.sin(_shakeAnimation.value * math.pi * 6) * 12;
                  return Transform.translate(
                    offset: Offset(offset, 0),
                    child: child,
                  );
                },
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: List.generate(4, (index) {
                    final isFilled = index < _enteredPin.length;
                    return AnimatedContainer(
                      duration: const Duration(milliseconds: 160),
                      curve: Curves.easeOutCubic,
                      margin: const EdgeInsets.symmetric(horizontal: 10),
                      width: 16,
                      height: 16,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: isFilled
                            ? theme.colorScheme.primary
                            : (isDark ? Colors.white24 : Colors.black12),
                        boxShadow: isFilled
                            ? [
                                BoxShadow(
                                  color: theme.colorScheme.primary.withOpacity(0.4),
                                  blurRadius: 8,
                                  spreadRadius: 1,
                                ),
                              ]
                            : null,
                      ),
                    );
                  }),
                ),
              ),

              if (_errorMessage != null) ...[
                const SizedBox(height: 14),
                Text(
                  _errorMessage!,
                  style: const TextStyle(
                    color: Colors.redAccent,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                  textAlign: TextAlign.center,
                ),
              ],

              const Spacer(),

              // Numeric keypad with SpringInteractiveScale
              _buildNumericPad(context, isDark),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNumericPad(BuildContext context, bool isDark) {
    final theme = Theme.of(context);
    final rows = [
      ['1', '2', '3'],
      ['4', '5', '6'],
      ['7', '8', '9'],
      ['bio', '0', 'back'],
    ];

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: rows.map((row) {
        return Padding(
          padding: const EdgeInsets.symmetric(vertical: 6.0),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: row.map((cell) {
              if (cell == 'bio') {
                return SpringInteractiveScale(
                  onTap: _triggerBiometricAuth,
                  child: Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: theme.colorScheme.primary.withOpacity(0.12),
                    ),
                    child: Icon(
                      Icons.fingerprint_rounded,
                      size: 32,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                );
              }
              if (cell == 'back') {
                return SpringInteractiveScale(
                  onTap: _onBackspacePressed,
                  child: Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: isDark ? Colors.white.withOpacity(0.06) : Colors.black.withOpacity(0.04),
                    ),
                    child: Icon(
                      Icons.backspace_outlined,
                      size: 22,
                      color: isDark ? Colors.white70 : Colors.black54,
                    ),
                  ),
                );
              }

              return SpringInteractiveScale(
                onTap: () => _onDigitPressed(cell),
                child: Container(
                  width: 72,
                  height: 72,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: isDark ? const Color(0xFF1C2128) : Colors.white,
                    border: Border.all(
                      color: isDark ? Colors.white.withOpacity(0.08) : Colors.black.withOpacity(0.06),
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(isDark ? 0.2 : 0.03),
                        blurRadius: 6,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Center(
                    child: Text(
                      cell,
                      style: TextStyle(
                        fontSize: 26,
                        fontWeight: FontWeight.w600,
                        color: isDark ? Colors.white : Colors.black87,
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildUnlockedVaultView(BuildContext context, bool isDark) {
    final lockedIds = _vaultController.lockedConversationIds;
    final allConversations = _dataStore.conversations;
    final lockedConversations =
        allConversations.where((c) => lockedIds.contains(c.id)).toList();

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0D1117) : const Color(0xFFF0F4F8),
      appBar: AppBar(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.lock_open_rounded, size: 20),
            const SizedBox(width: 8),
            Text(
              _vaultController.isDecoyMode ? 'Decoy Vault' : 'Hidden Vault',
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.lock_outline_rounded),
            tooltip: 'Lock Vault',
            onPressed: () {
              HapticFeedback.mediumImpact();
              _vaultController.lockVault();
            },
          ),
        ],
      ),
      body: lockedConversations.isEmpty
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(32.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.shield_outlined,
                      size: 64,
                      color: isDark ? Colors.white30 : Colors.black26,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      _vaultController.isDecoyMode
                          ? 'No active chats in decoy mode'
                          : 'No Hidden Conversations',
                      style: DesignTypographyScale.subtitle(context),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'To hide a conversation into this encrypted vault, select it from the inbox and choose "Lock to Vault".',
                      style: DesignTypographyScale.caption(context,
                          color: isDark ? Colors.white60 : Colors.black54),
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            )
          : ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
              itemCount: lockedConversations.length,
              separatorBuilder: (_, __) => const SizedBox(height: 8),
              itemBuilder: (context, index) {
                final conversation = lockedConversations[index];
                return BentoCard(
                  onTap: () {
                    final themeCtrl = locator.isRegistered<ThemeController>()
                        ? locator<ThemeController>()
                        : null;
                    final effectiveTheme = themeCtrl?.globalTheme ??
                        ThemePresets.getSystemDefaultTheme();
                    final prefsCtrl = locator<ChatyPreferencesController>();

                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => ChatDetailScreen(
                          conversationId: conversation.id,
                          dataStore: _dataStore,
                          theme: effectiveTheme,
                          preferencesController: prefsCtrl,
                          themeController: themeCtrl,
                        ),
                      ),
                    );
                  },
                  child: Row(
                    children: [
                      AppAvatar(
                        initials: conversation.avatarInitials ??
                            (conversation.title.isNotEmpty
                                ? conversation.title[0].toUpperCase()
                                : '?'),
                        colorHex: conversation.avatarColorHex,
                        size: 48,
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              conversation.title,
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              conversation.lastMessageText.isNotEmpty
                                  ? conversation.lastMessageText
                                  : 'Encrypted conversation',
                              style: TextStyle(
                                fontSize: 13,
                                color: isDark ? Colors.white60 : Colors.black54,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.lock_open_rounded, size: 20),
                        tooltip: 'Unlock from Vault',
                        onPressed: () {
                          _vaultController.toggleConversationLock(conversation.id);
                        },
                      ),
                    ],
                  ),
                );
              },
            ),
    );
  }
}
