import 'package:flutter/material.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../data/services/backend_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/validators/input_validators.dart';
import '../../ui/core/widgets/username_availability_field.dart';

/// Shared profile actions used by BOTH the Profile root screen and the
/// Settings screen, so there is exactly one profile editor and one logout
/// confirmation flow in the app.

/// Open the profile editor sheet. The form, live username availability
/// check and persistence path (`dataStore.updateUser`) are unchanged from
/// the original Settings implementation — only the location is shared now.
Future<void> showChatyProfileEditor(
  BuildContext context,
  MockDataStore dataStore,
) async {
  final user = dataStore.currentUser;
  final backend = locator<ChatyBackendService>();
  final formKey = GlobalKey<FormState>();
  final displayNameController = TextEditingController(text: user.displayName);
  final usernameController = TextEditingController(text: user.username);
  final aboutController = TextEditingController(text: user.about);
  final phoneController = TextEditingController(text: user.phone);
  var saving = false;
  bool? usernameAvailable = true;

  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (sheetContext) => StatefulBuilder(
      builder: (sheetContext, setSheetState) {
        Future<void> save() async {
          if (saving || formKey.currentState?.validate() != true) return;
          final normalized = ChatyValidators.normalizeUsername(
            usernameController.text,
          );
          final unchanged =
              normalized == ChatyValidators.normalizeUsername(user.username);
          if (!unchanged && usernameAvailable != true) {
            ScaffoldMessenger.of(sheetContext).showSnackBar(
              const SnackBar(
                content: Text('Choose an available username before saving.'),
              ),
            );
            return;
          }
          setSheetState(() => saving = true);
          final displayName = displayNameController.text.trim();
          final about = aboutController.text.trim();
          final phone = phoneController.text.trim();
          final updated = user.copyWith(
            displayName: displayName,
            username: normalized,
            about: about,
            phone: phone,
            avatarInitials: chatyInitialsFor(displayName),
          );
          try {
            if (!unchanged && !await backend.isUsernameAvailable(normalized)) {
              if (!sheetContext.mounted) return;
              setSheetState(() {
                saving = false;
                usernameAvailable = false;
              });
              formKey.currentState?.validate();
              return;
            }
            await dataStore.updateUser(updated);
            if (!sheetContext.mounted) return;
            Navigator.of(sheetContext).pop();
            if (context.mounted) {
              ScaffoldMessenger.of(context)
                ..hideCurrentSnackBar()
                ..showSnackBar(
                  const SnackBar(content: Text('Profile updated.')),
                );
            }
          } catch (error) {
            if (!sheetContext.mounted) return;
            setSheetState(() => saving = false);
            ScaffoldMessenger.of(sheetContext).showSnackBar(
              SnackBar(content: Text('Could not update profile: $error')),
            );
          }
        }

        return Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            4,
            20,
            MediaQuery.viewInsetsOf(sheetContext).bottom + 20,
          ),
          child: SingleChildScrollView(
            child: Form(
              key: formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Edit profile',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Update your profile. Usernames are checked live so you never submit a name that is already taken.',
                    style: Theme.of(sheetContext).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 18),
                  TextFormField(
                    controller: displayNameController,
                    enabled: !saving,
                    textInputAction: TextInputAction.next,
                    validator: ChatyValidators.validateDisplayName,
                    decoration: const InputDecoration(
                      labelText: 'Display name',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 12),
                  UsernameAvailabilityField(
                    controller: usernameController,
                    backend: backend,
                    currentUsername: user.username,
                    enabled: !saving,
                    onAvailabilityChanged: (value) {
                      usernameAvailable = value;
                    },
                    decoration: const InputDecoration(
                      labelText: 'Username',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: phoneController,
                    enabled: !saving,
                    keyboardType: TextInputType.phone,
                    textInputAction: TextInputAction.next,
                    validator: (value) =>
                        value == null || value.trim().isEmpty
                        ? null
                        : ChatyValidators.validatePhone(value),
                    decoration: const InputDecoration(
                      labelText: 'Phone',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: aboutController,
                    enabled: !saving,
                    minLines: 2,
                    maxLines: 4,
                    maxLength: 256,
                    validator: ChatyValidators.validateBio,
                    decoration: const InputDecoration(
                      labelText: 'About',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 14),
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: FilledButton.icon(
                      onPressed: saving ? null : save,
                      icon: saving
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                              ),
                            )
                          : const Icon(Icons.check_rounded),
                      label: Text(saving ? 'Saving…' : 'Save profile'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    ),
  );

  displayNameController.dispose();
  usernameController.dispose();
  aboutController.dispose();
  phoneController.dispose();
}

/// Confirm and perform logout. Same dialog copy and same
/// `ChatyBackendService.logout()` call as the original Settings flow.
Future<void> confirmChatyLogout(BuildContext context) async {
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: const Text('Log out of Chaty?'),
      content: const Text(
        'Your account will be signed out on this device. Your chats and account data remain on the server.',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(false),
          child: const Text('Cancel'),
        ),
        FilledButton(
          style: FilledButton.styleFrom(
            backgroundColor: Theme.of(dialogContext).colorScheme.error,
          ),
          onPressed: () => Navigator.of(dialogContext).pop(true),
          child: const Text('Log out'),
        ),
      ],
    ),
  );
  if (confirmed != true) return;

  try {
    await locator<ChatyBackendService>().logout();
  } catch (error) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text('Could not log out: $error')));
  }
}

/// Initials for the profile avatar, shared by the editor and the Profile
/// header so both always agree.
String chatyInitialsFor(String displayName) {
  final parts = displayName
      .trim()
      .split(RegExp(r'\s+'))
      .where((part) => part.isNotEmpty)
      .toList();
  if (parts.isEmpty) return 'CU';
  if (parts.length == 1) {
    return parts.first
        .substring(0, parts.first.length >= 2 ? 2 : 1)
        .toUpperCase();
  }
  return '${parts.first[0]}${parts.last[0]}'.toUpperCase();
}
