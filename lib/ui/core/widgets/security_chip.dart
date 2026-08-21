import 'package:flutter/material.dart';
import '../../../domain/models/conversation.dart';

class SecurityChip extends StatelessWidget {
  final EncryptionStatus status;
  final VoidCallback? onTap;

  const SecurityChip({super.key, required this.status, this.onTap});

  @override
  Widget build(BuildContext context) {
    Color bg;
    Color fg;
    IconData icon;
    String label;

    switch (status) {
      case EncryptionStatus.encrypted:
        bg = const Color(0xFF10B981).withValues(alpha: 0.15);
        fg = const Color(0xFF34D399);
        icon = Icons.lock_rounded;
        label = 'E2EE';
        break;
      case EncryptionStatus.verificationNeeded:
        bg = const Color(0xFFF59E0B).withValues(alpha: 0.15);
        fg = const Color(0xFFFBBF24);
        icon = Icons.shield_outlined;
        label = 'Verify';
        break;
      case EncryptionStatus.demoMode:
        bg = const Color(0xFF6366F1).withValues(alpha: 0.15);
        fg = const Color(0xFF818CF8);
        icon = Icons.info_outline_rounded;
        label = 'Demo Auth';
        break;
    }

    return InkWell(
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
    );
  }
}
