import 'package:flutter/material.dart';
import '../../../domain/models/conversation.dart';
import '../design_system/design_system.dart';

class SecurityChip extends StatelessWidget {
  final EncryptionStatus status;
  final VoidCallback? onTap;

  const SecurityChip({super.key, required this.status, this.onTap});

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    Color bg;
    Color fg;
    IconData icon;
    String label;
    String semanticsLabel;

    switch (status) {
      case EncryptionStatus.encrypted:
        bg = colors.success.withValues(alpha: 0.15);
        fg = colors.success;
        icon = Icons.lock_rounded;
        label = 'E2EE';
        semanticsLabel = 'End-to-end encryption active';
        break;
      case EncryptionStatus.verificationNeeded:
        bg = colors.warning.withValues(alpha: 0.15);
        fg = colors.warning;
        icon = Icons.lock_open_rounded;
        label = 'Not E2EE';
        semanticsLabel = 'End-to-end encryption is not active';
        break;
      case EncryptionStatus.demoMode:
        bg = colors.warning.withValues(alpha: 0.15);
        fg = colors.warning;
        icon = Icons.info_outline_rounded;
        label = 'Not E2EE';
        semanticsLabel = 'End-to-end encryption is not active';
        break;
    }

    return Semantics(
      button: onTap != null,
      label: semanticsLabel,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: fg.withValues(alpha: 0.3), width: 0.8),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: fg, size: 12),
              const SizedBox(width: 4),
              Text(
                label,
                style: TextStyle(
                  color: fg,
                  fontSize: 10.5,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 0.2,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
