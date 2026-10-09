import 'dart:io';
import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/backend_service.dart';
import '../../data/services/profile_media_service.dart';
import '../../domain/models/user_profile.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/validators/input_validators.dart';

/// Dedicated full screen for editing user profile (Rule 21).
/// Features:
/// - Avatar update via camera/gallery
/// - Banner photo update
/// - Form validation for display name and username
/// - Unsaved changes check on back navigation
/// - Duplicate submission prevention
/// - Immediate persistence and reactive store refresh
class ProfileEditScreen extends StatefulWidget {
  final ChatyDataStore dataStore;

  const ProfileEditScreen({super.key, required this.dataStore});

  static Future<void> open(BuildContext context, ChatyDataStore dataStore) {
    return Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ProfileEditScreen(dataStore: dataStore),
      ),
    );
  }

  @override
  State<ProfileEditScreen> createState() => _ProfileEditScreenState();
}

class _ProfileEditScreenState extends State<ProfileEditScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _displayNameCtrl;
  late final TextEditingController _usernameCtrl;
  late final TextEditingController _aboutCtrl;

  late final UserProfile _initialUser;
  bool _isSaving = false;
  bool? _usernameAvailable = true;
  String? _newAvatarUrl;
  String? _newBannerUrl;

  @override
  void initState() {
    super.initState();
    _initialUser = widget.dataStore.currentUser;
    _displayNameCtrl = TextEditingController(text: _initialUser.displayName);
    _usernameCtrl = TextEditingController(text: _initialUser.username);
    _aboutCtrl = TextEditingController(text: _initialUser.about);
  }

  @override
  void dispose() {
    _displayNameCtrl.dispose();
    _usernameCtrl.dispose();
    _aboutCtrl.dispose();
    super.dispose();
  }

  ImageProvider _resolveImageProvider(String pathOrUrl) {
    if (pathOrUrl.startsWith('http://') || pathOrUrl.startsWith('https://')) {
      return NetworkImage(pathOrUrl);
    }
    return FileImage(File(pathOrUrl));
  }

  bool get _isDirty {
    return _displayNameCtrl.text.trim() != _initialUser.displayName ||
        _usernameCtrl.text.trim() != _initialUser.username ||
        _aboutCtrl.text.trim() != _initialUser.about ||
        _newAvatarUrl != null ||
        _newBannerUrl != null;
  }

  Future<bool> _onWillPop() async {
    if (!_isDirty || _isSaving) return true;
    final discard = await ChatyConfirmDialog.show(
      context,
      title: 'Discard changes?',
      message: 'You have unsaved changes that will be lost.',
      confirmLabel: 'Discard',
      cancelLabel: 'Keep Editing',
      destructive: true,
    );
    return discard == true;
  }

  Future<ProfileMediaSource?> _promptImageSource(String title) async {
    return showModalBottomSheet<ProfileMediaSource>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => ChatyModal(
        header: ChatyModalHeader(title: title),
        content: ChatyModalContent(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ChatyActionRow(
                icon: Icons.camera_alt_outlined,
                title: 'Capture Image',
                subtitle: 'Use camera to take a new photo',
                onTap: () => Navigator.of(ctx).pop(ProfileMediaSource.camera),
              ),
              ChatyActionRow(
                icon: Icons.photo_library_outlined,
                title: 'Pick / Choose from Gallery',
                subtitle: 'Select an image from device gallery',
                onTap: () => Navigator.of(ctx).pop(ProfileMediaSource.gallery),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _pickAvatar() async {
    final source = await _promptImageSource('Change Profile Photo');
    if (source == null || !mounted) return;
    setState(() => _isSaving = true);
    try {
      final profileMedia = locator<ProfileMediaService>();
      final url = await profileMedia.uploadAvatar(source: source, context: context);
      if (mounted) {
        setState(() {
          _newAvatarUrl = url;
          _isSaving = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSaving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not upload profile photo: $e')),
        );
      }
    }
  }

  Future<void> _pickBanner() async {
    final source = await _promptImageSource('Change Banner Photo');
    if (source == null || !mounted) return;
    setState(() => _isSaving = true);
    try {
      final profileMedia = locator<ProfileMediaService>();
      final url = await profileMedia.uploadBanner(source: source, context: context);
      if (mounted) {
        setState(() {
          _newBannerUrl = url;
          _isSaving = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSaving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not upload banner photo: $e')),
        );
      }
    }
  }

  String _computeInitials(String name) {
    final trimmed = name.trim();
    if (trimmed.isEmpty) return 'U';
    final parts = trimmed.split(RegExp(r'\s+'));
    if (parts.length >= 2 && parts[0].isNotEmpty && parts[1].isNotEmpty) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return trimmed.substring(0, trimmed.length >= 2 ? 2 : 1).toUpperCase();
  }

  Future<void> _save() async {
    if (_isSaving) return;
    if (_formKey.currentState?.validate() != true) return;

    final normalized = ChatyValidators.normalizeUsername(_usernameCtrl.text);
    final unchanged =
        normalized == ChatyValidators.normalizeUsername(_initialUser.username);

    if (!unchanged && _usernameAvailable != true) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please select an available username before saving.'),
        ),
      );
      return;
    }

    setState(() => _isSaving = true);

    try {
      final backend = locator<ChatyBackendService>();
      if (!unchanged && !await backend.isUsernameAvailable(normalized)) {
        if (!mounted) return;
        setState(() {
          _isSaving = false;
          _usernameAvailable = false;
        });
        _formKey.currentState?.validate();
        return;
      }

      final updated = _initialUser.copyWith(
        displayName: _displayNameCtrl.text.trim(),
        username: normalized,
        about: _aboutCtrl.text.trim(),
        avatarUrl: _newAvatarUrl ?? _initialUser.avatarUrl,
        bannerUrl: _newBannerUrl ?? _initialUser.bannerUrl,
        avatarInitials: _computeInitials(_displayNameCtrl.text.trim()),
      );

      await widget.dataStore.updateUser(updated);

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Profile updated successfully.')),
      );
      Navigator.of(context).pop();
    } catch (error) {
      if (!mounted) return;
      setState(() => _isSaving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not save profile: $error')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final user = widget.dataStore.currentUser;
    final activeAvatarUrl = _newAvatarUrl ?? user.avatarUrl;
    final activeBannerUrl = _newBannerUrl ?? user.bannerUrl;

    return PopScope(
      canPop: !_isDirty && !_isSaving,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final shouldPop = await _onWillPop();
        if (shouldPop && context.mounted) {
          Navigator.of(context).pop();
        }
      },
      child: Scaffold(
        appBar: AppBar(
          automaticallyImplyLeading: false,
          leading: const Padding(
            padding: EdgeInsets.all(8.0),
            child: ChatyBackButton(),
          ),
          title: const Text('Edit Profile'),
          actions: [
            Padding(
              padding: const EdgeInsets.only(right: 12),
              child: _isSaving
                  ? const Center(
                      child: SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2.2),
                      ),
                    )
                  : FilledButton.tonal(
                      onPressed: _isSaving ? null : _save,
                      child: const Text('Save'),
                    ),
            ),
          ],
        ),
        body: Form(
          key: _formKey,
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Banner & Overlapping Avatar section (Rule 20)
                SizedBox(
                  height: 220,
                  child: Stack(
                    clipBehavior: Clip.none,
                    children: [
                      // Banner
                      Container(
                        height: 160,
                        width: double.infinity,
                        decoration: BoxDecoration(
                          color: colorScheme.surfaceContainerHighest,
                          image: activeBannerUrl != null && activeBannerUrl.isNotEmpty
                              ? DecorationImage(
                                  image: _resolveImageProvider(activeBannerUrl),
                                  fit: BoxFit.cover,
                                )
                              : null,
                          gradient: activeBannerUrl == null || activeBannerUrl.isEmpty
                              ? LinearGradient(
                                  colors: [
                                    colorScheme.primaryContainer,
                                    colorScheme.surfaceContainerHighest,
                                  ],
                                  begin: Alignment.topLeft,
                                  end: Alignment.bottomRight,
                                )
                              : null,
                        ),
                        child: Align(
                          alignment: Alignment.topRight,
                          child: Padding(
                            padding: const EdgeInsets.all(10),
                            child: IconButton.filledTonal(
                              tooltip: 'Change banner photo',
                              icon: const Icon(Icons.camera_alt_outlined, size: 20),
                              onPressed: _pickBanner,
                            ),
                          ),
                        ),
                      ),

                      // Avatar overlapping bottom-center edge
                      Positioned(
                        bottom: 10,
                        left: 0,
                        right: 0,
                        child: Center(
                          child: Stack(
                            children: [
                              Container(
                                width: 96,
                                height: 96,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  border: Border.all(
                                    color: colorScheme.surface,
                                    width: 3.5,
                                  ),
                                  boxShadow: [
                                    BoxShadow(
                                      color: Colors.black.withValues(alpha: 0.18),
                                      blurRadius: 12,
                                      offset: const Offset(0, 3),
                                    ),
                                  ],
                                ),
                                child: ClipOval(
                                  child: activeAvatarUrl != null && activeAvatarUrl.isNotEmpty
                                      ? (activeAvatarUrl.startsWith('http')
                                          ? Image.network(
                                              activeAvatarUrl,
                                              fit: BoxFit.cover,
                                              errorBuilder: (_, __, ___) =>
                                                  _avatarFallback(user),
                                            )
                                          : Image.file(
                                              File(activeAvatarUrl),
                                              fit: BoxFit.cover,
                                              errorBuilder: (_, __, ___) =>
                                                  _avatarFallback(user),
                                            ))
                                      : _avatarFallback(user),
                                ),
                              ),
                              Positioned(
                                bottom: 0,
                                right: 0,
                                child: Container(
                                  width: 34,
                                  height: 34,
                                  decoration: BoxDecoration(
                                    color: colorScheme.primary,
                                    shape: BoxShape.circle,
                                    border: Border.all(
                                      color: colorScheme.surface,
                                      width: 2.5,
                                    ),
                                  ),
                                  child: IconButton(
                                    padding: EdgeInsets.zero,
                                    iconSize: 17,
                                    color: colorScheme.onPrimary,
                                    tooltip: 'Change avatar',
                                    icon: const Icon(Icons.photo_camera_rounded),
                                    onPressed: _pickAvatar,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 12),

                // Form Fields
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // Display Name
                      TextFormField(
                        controller: _displayNameCtrl,
                        maxLength: 50,
                        decoration: const InputDecoration(
                          labelText: 'Display Name',
                          hintText: 'Enter your full name',
                          prefixIcon: Icon(Icons.person_outline_rounded),
                          border: OutlineInputBorder(),
                        ),
                        validator: (value) {
                          final val = value?.trim() ?? '';
                          if (val.isEmpty) return 'Display name cannot be empty';
                          if (val.length < 2) return 'Must be at least 2 characters';
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // Username
                      TextFormField(
                        controller: _usernameCtrl,
                        maxLength: 30,
                        decoration: InputDecoration(
                          labelText: 'Username',
                          hintText: 'Choose a unique username',
                          prefixText: '@',
                          prefixIcon: const Icon(Icons.alternate_email_rounded),
                          border: const OutlineInputBorder(),
                          errorText: _usernameAvailable == false
                              ? 'This username is already taken'
                              : null,
                        ),
                        validator: (value) {
                          final val = value?.trim() ?? '';
                          return ChatyValidators.validateUsername(val);
                        },
                      ),
                      const SizedBox(height: 16),

                      // About / Bio
                      TextFormField(
                        controller: _aboutCtrl,
                        maxLines: 3,
                        maxLength: 140,
                        decoration: const InputDecoration(
                          labelText: 'About / Status',
                          hintText: 'Say something about yourself or current mood',
                          prefixIcon: Icon(Icons.info_outline_rounded),
                          border: OutlineInputBorder(),
                          alignLabelWithHint: true,
                        ),
                      ),
                      const SizedBox(height: 16),

                      // Phone Number (read-only info)
                      TextFormField(
                        initialValue: user.phone.isNotEmpty
                            ? user.phone
                            : 'Not set',
                        enabled: false,
                        decoration: const InputDecoration(
                          labelText: 'Phone Number',
                          prefixIcon: Icon(Icons.phone_outlined),
                          border: OutlineInputBorder(),
                          helperText: 'Phone number is managed via Account settings',
                        ),
                      ),
                      const SizedBox(height: 24),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _avatarFallback(UserProfile user) {
    final theme = Theme.of(context);
    return Container(
      color: theme.colorScheme.primaryContainer,
      child: Center(
        child: Text(
          user.avatarInitials.isNotEmpty ? user.avatarInitials : 'U',
          style: TextStyle(
            color: theme.colorScheme.onPrimaryContainer,
            fontSize: 32,
            fontWeight: FontWeight.bold,
          ),
        ),
      ),
    );
  }
}
