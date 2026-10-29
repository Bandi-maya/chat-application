import 'package:flutter/material.dart';

import '../../../ui/core/design_system/settings_primitives.dart';

/// Truthful disclosure of Chaty's encryption and identity-verification scope.
///
/// This intentionally does not invent a safety number or show a verified state:
/// the settings screen has no contact-specific fingerprint verification flow.
class EncryptionTrustSection extends StatelessWidget {
  const EncryptionTrustSection({super.key});

  Future<void> _showDetails(BuildContext context) {
    return showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Encryption and identity verification'),
        content: const Text(
          'Chaty includes an MLS-based end-to-end encryption service for '
          'supported conversations. This screen does not currently derive '
          'or verify a contact-specific cryptographic fingerprint. Do not '
          'use a sample number or a status label here as proof that a '
          'contact identity has been verified.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return ChatySettingsSection(
      title: 'Encryption and identity',
      description:
          'Encryption and identity verification are different protections. '
          'This panel does not confirm that a specific contact has been verified.',
      children: [
        ListTile(
          leading: Icon(
            Icons.shield_outlined,
            color: colorScheme.primary,
          ),
          title: const Text('MLS-based encryption'),
          subtitle: const Text(
            'Used for supported conversations; contact fingerprint '
            'verification is not available here.',
          ),
          trailing: const Icon(Icons.info_outline_rounded),
          onTap: () => _showDetails(context),
        ),
      ],
    );
  }
}
