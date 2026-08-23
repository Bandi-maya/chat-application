import 'dart:io';

import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/backend_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/validators/input_validators.dart';
import '../../ui/core/widgets/username_availability_field.dart';
import '../../data/services/profile_media_service.dart';

/// Shared profile actions used by BOTH the Profile root screen and the
/// Settings screen, so there is exactly one profile editor and one logout
/// confirmation flow in the app.

/// Open the profile editor sheet. The form, live username availability
/// check and persistence path (`dataStore.updateUser`) are unchanged from
/// the original Settings implementation — only the location is shared now.
Future<void> showChatyProfileEditor(
  BuildContext context,
  ChatyDataStore dataStore,
) async {
  final user = dataStore.currentUser;
  final backend = locator<ChatyBackendService>();
  final formKey = GlobalKey<FormState>();
  final displayNameController = TextEditingController(text: user.displayName);
  final usernameController = TextEditingController(text: user.username);
  final aboutController = TextEditingController(text: user.about);
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
          final updated = user.copyWith(
            displayName: displayName,
            username: normalized,
            about: about,
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
                  _ProfilePhotoRow(user: user, dataStore: dataStore),
                  const SizedBox(height: 14),
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
                  ChatyInput(
                    controller: displayNameController,
                    enabled: !saving,
                    textInputAction: TextInputAction.next,
                    validator: ChatyValidators.validateDisplayName,
                    label: 'Display name',
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
                  ChatyInput(
                    controller: aboutController,
                    enabled: !saving,
                    maxLines: 4,
                    validator: ChatyValidators.validateBio,
                    label: 'About',
                  ),
                  const SizedBox(height: 14),
                  SizedBox(
                    width: double.infinity,
                    child: ChatyPrimaryButton(
                      text: saving ? 'Saving…' : 'Save profile',
                      icon: Icons.check_rounded,
                      isLoading: saving,
                      onPressed: saving ? null : save,
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


/// Camera/Gallery chooser + immediate upload for the profile photo. The
/// upload persists through the normal profile-update path so every device
/// converges on the same URL; cancelling text edits never rolls media back
/// (matching mainstream messaging behavior).
class _ProfilePhotoRow extends StatefulWidget {
  final dynamic user;
  final ChatyDataStore dataStore;

  const _ProfilePhotoRow({required this.user, required this.dataStore});

  @override
  State<_ProfilePhotoRow> createState() => _ProfilePhotoRowState();
}

class _ProfilePhotoRowState extends State<_ProfilePhotoRow> {
  bool _busy = false;

  Future<void> _upload(ProfileMediaSource source) async {
    setState(() => _busy = true);
    try {
      final url = await ProfileMediaService().uploadAvatar(
        source: source,
        context: context,
      );
      await widget.dataStore.updateUser(
        widget.user.copyWith(avatarUrl: url),
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Profile photo updated.')));
    } catch (error) {
      final message = error.toString();
      if (message.contains('cancelled')) return;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not update photo: $message')),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Row(
      children: [
        Stack(
          clipBehavior: Clip.none,
          children: [
            CircleAvatar(
              radius: 34,
              backgroundColor: colors.surfaceContainerHighest,
              backgroundImage: (widget.user.avatarUrl ?? '').isNotEmpty
                  ? (widget.user.avatarUrl!.startsWith('http://') ||
                          widget.user.avatarUrl!.startsWith('https://')
                      ? NetworkImage(widget.user.avatarUrl!)
                      : FileImage(
                          File(widget.user.avatarUrl!.replaceFirst('file://', '')),
                        ) as ImageProvider)
                  : null,
              child: (widget.user.avatarUrl ?? '').isNotEmpty
                  ? null
                  : Text(
                      chatyInitialsFor(widget.user.displayName),
                      style: const TextStyle(fontWeight: FontWeight.w800),
                    ),
            ),
            Positioned(
              right: -2,
              bottom: -2,
              child: Container(
                padding: const EdgeInsets.all(4),
                decoration: BoxDecoration(
                  color: colors.primary,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.photo_camera_rounded,
                  size: 13,
                  color: colors.onPrimary,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              TextButton.icon(
                onPressed: _busy
                    ? null
                    : () => _upload(ProfileMediaSource.camera),
                icon: const Icon(Icons.photo_camera_rounded, size: 18),
                label: const Text('Camera'),
              ),
              TextButton.icon(
                onPressed: _busy
                    ? null
                    : () => _upload(ProfileMediaSource.gallery),
                icon: const Icon(Icons.photo_rounded, size: 18),
                label: const Text('Gallery'),
              ),
              if (_busy)
                const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
