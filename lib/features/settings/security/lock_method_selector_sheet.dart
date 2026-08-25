import 'package:flutter/material.dart';

import '../../../data/services/local_lock_service.dart';
import '../../../ui/core/design_system/design_system.dart';
import 'security_flow_plan.dart';

/// "What type of lock do you want?" — the method selector shown whenever a
/// lock is enabled or its method changes. Original Chaty design: one card
/// per supported credential type, each with an honest availability state
/// (biometric/device options are disabled with a reason when the OS cannot
/// serve them). No WhatsApp assets or modded-WhatsApp styling.
class LockMethodSelectorSheet extends StatefulWidget {
  final LocalLockService lockService;
  final LockMethodType? currentMethod;

  const LockMethodSelectorSheet({
    super.key,
    required this.lockService,
    this.currentMethod,
  });

  /// Returns the chosen method, or null when cancelled.
  static Future<LockMethodType?> show(
    BuildContext context, {
    required LocalLockService lockService,
    LockMethodType? currentMethod,
  }) {
    return showModalBottomSheet<LockMethodType>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => LockMethodSelectorSheet(
        lockService: lockService,
        currentMethod: currentMethod,
      ),
    );
  }

  @override
  State<LockMethodSelectorSheet> createState() =>
      _LockMethodSelectorSheetState();
}

class _LockMethodSelectorSheetState extends State<LockMethodSelectorSheet> {
  bool? _biometricAvailable;

  @override
  void initState() {
    super.initState();
    _loadBiometricAvailability();
  }

  Future<void> _loadBiometricAvailability() async {
    final available = await widget.lockService.canUseBiometrics();
    if (!mounted) return;
    setState(() => _biometricAvailable = available);
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Choose a lock type',
              style: TextStyle(
                color: colors.foreground,
                fontSize: 20,
                fontWeight: FontWeight.w800,
                letterSpacing: -0.3,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'You will set it up in the next step. You can change it later.',
              style: TextStyle(color: colors.foregroundSecondary, fontSize: 13),
            ),
            const SizedBox(height: 16),
            _MethodCard(
              icon: Icons.pin_rounded,
              title: 'PIN',
              subtitle: 'A 4- or 6-digit numeric code',
              selected: widget.currentMethod == LockMethodType.pin,
              onTap: () => Navigator.pop(context, LockMethodType.pin),
            ),
            const SizedBox(height: 10),
            _MethodCard(
              icon: Icons.pattern_rounded,
              title: 'Pattern',
              subtitle: 'Draw a gesture on a 3×3 grid',
              selected: widget.currentMethod == LockMethodType.pattern,
              onTap: () => Navigator.pop(context, LockMethodType.pattern),
            ),
            const SizedBox(height: 10),
            _MethodCard(
              icon: Icons.password_rounded,
              title: 'Password',
              subtitle: 'At least 6 characters, letters and symbols allowed',
              selected: widget.currentMethod == LockMethodType.password,
              onTap: () => Navigator.pop(context, LockMethodType.password),
            ),
            const SizedBox(height: 10),
            _OsAuthCard(
              icon: Icons.fingerprint_rounded,
              title: 'Biometric',
              subtitle: _biometricAvailable == null
                  ? 'Checking device support…'
                  : _biometricAvailable!
                  ? 'Fingerprint / face enrolled on this device'
                  : 'No biometric is enrolled on this device',
              enabled: _biometricAvailable == true,
              selected: widget.currentMethod == LockMethodType.biometric,
              onEnabledTap: () =>
                  Navigator.pop(context, LockMethodType.biometric),
            ),
            const SizedBox(height: 10),
            _OsAuthCard(
              icon: Icons.phonelink_lock_rounded,
              title: 'Device lock',
              subtitle:
                  'The PIN / pattern / password managed by Android or iOS',
              enabled: true,
              selected: widget.currentMethod == LockMethodType.deviceCredential,
              onEnabledTap: () =>
                  Navigator.pop(context, LockMethodType.deviceCredential),
            ),
          ],
        ),
      ),
    );
  }
}

class _MethodCard extends StatefulWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool selected;
  final VoidCallback onTap;

  const _MethodCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.selected,
    required this.onTap,
  });

  @override
  State<_MethodCard> createState() => _MethodCardState();
}

class _MethodCardState extends State<_MethodCard> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return Semantics(
      button: true,
      selected: widget.selected,
      label: widget.selected ? '${widget.title}, selected' : widget.title,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: (_) => setState(() => _pressed = true),
        onTapCancel: () => setState(() => _pressed = false),
        onTapUp: (_) => setState(() => _pressed = false),
        onTap: widget.onTap,
        child: AnimatedScale(
          scale: _pressed ? 0.98 : 1.0,
          duration: const Duration(milliseconds: 120),
          curve: Curves.easeOutCubic,
          child: Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: colors.surfaceElevated,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: widget.selected ? colors.primary : colors.borderSubtle,
                width: widget.selected ? 1.6 : 1,
              ),
            ),
            child: Row(
              children: [
                Container(
                  width: 42,
                  height: 42,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: colors.primary.withValues(alpha: 0.12),
                  ),
                  child: Icon(widget.icon, size: 21, color: colors.primary),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.title,
                        style: TextStyle(
                          color: colors.foreground,
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        widget.subtitle,
                        style: TextStyle(
                          color: colors.foregroundSecondary,
                          fontSize: 12.5,
                        ),
                      ),
                    ],
                  ),
                ),
                Icon(
                  widget.selected
                      ? Icons.radio_button_checked_rounded
                      : Icons.radio_button_off_rounded,
                  size: 20,
                  color: widget.selected
                      ? colors.primary
                      : colors.foregroundTertiary,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Variant of the method card for OS-provided authentication where the
/// option can be honestly unavailable.
class _OsAuthCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool enabled;
  final bool selected;
  final VoidCallback onEnabledTap;

  const _OsAuthCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.enabled,
    required this.selected,
    required this.onEnabledTap,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final card = Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: colors.surfaceElevated,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: selected ? colors.primary : colors.borderSubtle,
          width: selected ? 1.6 : 1,
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: enabled
                  ? colors.primary.withValues(alpha: 0.12)
                  : colors.surfaceSecondary,
            ),
            child: Icon(
              icon,
              size: 21,
              color: enabled ? colors.primary : colors.disabled,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    color: enabled
                        ? colors.foreground
                        : colors.foregroundTertiary,
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: TextStyle(
                    color: colors.foregroundSecondary,
                    fontSize: 12.5,
                  ),
                ),
              ],
            ),
          ),
          if (!enabled)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: colors.warning.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(999),
              ),
              child: Text(
                'UNAVAILABLE',
                style: TextStyle(
                  color: colors.warning,
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.4,
                ),
              ),
            )
          else
            Icon(
              selected
                  ? Icons.radio_button_checked_rounded
                  : Icons.radio_button_off_rounded,
              size: 20,
              color: selected ? colors.primary : colors.foregroundTertiary,
            ),
        ],
      ),
    );
    if (!enabled) return card;
    return InkWell(
      onTap: onEnabledTap,
      borderRadius: BorderRadius.circular(16),
      child: card,
    );
  }
}
