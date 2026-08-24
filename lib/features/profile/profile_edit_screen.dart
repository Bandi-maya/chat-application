import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/backend_service.dart';
import '../../data/services/profile_media_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/validators/input_validators.dart';
import '../../ui/core/widgets/username_availability_field.dart';
import 'profile_actions.dart';

/// Dedicated single screen for editing the user profile.
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
  late final TextEditingController _displayNameController;
  late final TextEditingController _usernameController;
  late final TextEditingController _aboutController;
  final _backend = locator<ChatyBackendService>();

  bool _saving = false;
  bool? _usernameAvailable = true;

  @override
  void initState() {
    super.initState();
    final user = widget.dataStore.currentUser;
    _displayNameController = TextEditingController(text: user.displayName);
    _usernameController = TextEditingController(text: user.username);
    _aboutController = TextEditingController(text: user.about);
  }

  @override
  void dispose() {
    _displayNameController.dispose();
    _usernameController.dispose();
    _aboutController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving || _formKey.currentState?.validate() != true) return;
    final user = widget.dataStore.currentUser;
    final normalized = ChatyValidators.normalizeUsername(_usernameController.text);
    final unchanged = normalized == ChatyValidators.normalizeUsername(user.username);

    if (!unchanged && _usernameAvailable != true) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Choose an available username before saving.')),
      );
      return;
    }

    setState(() => _saving = true);
    final displayName = _displayNameController.text.trim();
    final about = _aboutController.text.trim();
    final updated = user.copyWith(
      displayName: displayName,
      username: normalized,
      about: about,
      avatarInitials: chatyInitialsFor(displayName),
    );

    try {
      if (!unchanged && !await _backend.isUsernameAvailable(normalized)) {
        if (!mounted) return;
        setState(() {
          _saving = false;
          _usernameAvailable = false;
        });
        _formKey.currentState?.validate();
        return;
      }

      await widget.dataStore.updateUser(updated);
      if (!mounted) return;
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Profile updated.')));
    } catch (error) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not update profile: $error')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final user = widget.dataStore.currentUser;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: AppBar(
        backgroundColor: colors.surfaceElevated,
        elevation: 0,
        surfaceTintColor: Colors.transparent,
        leading: const ChatyBackButton(),
        title: Text(
          'Edit Profile',
          style: TextStyle(
            color: colors.foreground,
            fontWeight: FontWeight.w700,
            fontSize: 18,
          ),
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: TextButton(
              onPressed: _saving ? null : _save,
              child: _saving
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(
                      'Save',
                      style: TextStyle(
                        color: colors.primary,
                        fontWeight: FontWeight.w700,
                        fontSize: 15,
                      ),
                    ),
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 20),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: _ProfilePhotoEditSection(
                    user: user,
                    dataStore: widget.dataStore,
                  ),
                ),
                const SizedBox(height: 24),
                Text(
                  'Account Information',
                  style: TextStyle(
                    color: colors.foregroundSecondary,
                    fontSize: 12.5,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.4,
                  ),
                ),
                const SizedBox(height: 12),
                ChatyInput(
                  controller: _displayNameController,
                  enabled: !_saving,
                  textInputAction: TextInputAction.next,
                  validator: ChatyValidators.validateDisplayName,
                  label: 'Display Name',
                ),
                const SizedBox(height: 16),
                UsernameAvailabilityField(
                  controller: _usernameController,
                  backend: _backend,
                  currentUsername: user.username,
                  enabled: !_saving,
                  onAvailabilityChanged: (value) {
                    _usernameAvailable = value;
                  },
                  decoration: InputDecoration(
                    labelText: 'Username',
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                ChatyInput(
                  controller: _aboutController,
                  enabled: !_saving,
                  maxLines: 4,
                  validator: ChatyValidators.validateBio,
                  label: 'About / Bio',
                ),
                const SizedBox(height: 28),
                SizedBox(
                  width: double.infinity,
                  child: ChatyPrimaryButton(
                    text: _saving ? 'Saving...' : 'Save Changes',
                    icon: Icons.check_rounded,
                    isLoading: _saving,
                    onPressed: _saving ? null : _save,
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

class _ProfilePhotoEditSection extends StatefulWidget {
  final dynamic user;
  final ChatyDataStore dataStore;

  const _ProfilePhotoEditSection({
    required this.user,
    required this.dataStore,
  });

  @override
  State<_ProfilePhotoEditSection> createState() => _ProfilePhotoEditSectionState();
}

class _ProfilePhotoEditSectionState extends State<_ProfilePhotoEditSection> {
  bool _uploading = false;

  Future<void> _changePhoto(ProfileMediaSource source) async {
    setState(() => _uploading = true);
    try {
      final url = await ProfileMediaService().uploadAvatar(
        source: source,
        context: context,
      );
      await widget.dataStore.updateUser(widget.user.copyWith(avatarUrl: url));
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
      if (mounted) setState(() => _uploading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final user = widget.user;

    return Column(
      children: [
        Stack(
          children: [
            Container(
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: colors.primary, width: 2.5),
              ),
              child: ChatyNetworkAvatar(
                initials: user.avatarInitials,
                colorHex: user.avatarColorHex,
                url: user.avatarUrl,
                size: 88,
              ),
            ),
            if (_uploading)
              Positioned.fill(
                child: Container(
                  decoration: const BoxDecoration(
                    color: Colors.black45,
                    shape: BoxShape.circle,
                  ),
                  child: const Center(
                    child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5),
                  ),
                ),
              ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextButton.icon(
              onPressed: _uploading ? null : () => _changePhoto(ProfileMediaSource.camera),
              icon: const Icon(Icons.photo_camera_rounded, size: 18),
              label: const Text('Camera'),
            ),
            const SizedBox(width: 8),
            TextButton.icon(
              onPressed: _uploading ? null : () => _changePhoto(ProfileMediaSource.gallery),
              icon: const Icon(Icons.photo_library_rounded, size: 18),
              label: const Text('Gallery'),
            ),
          ],
        ),
      ],
    );
  }
}
