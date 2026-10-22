import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../../../data/repositories/chaty_data_store.dart';
import '../../../data/services/api_backend_service.dart';
import '../../../data/services/backend_config.dart';
import '../../../domain/models/user_profile.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart'
    hide ChatySettingsSection;
import '../../../ui/core/design_system/settings_primitives.dart';
import '../../profile/profile_actions.dart';
import '../../subscription/plan_subscription_screen.dart';

/// Dedicated canonical settings screen for Account & Profile details.
class AccountSettingsScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;
  final ChatyDataStore dataStore;

  const AccountSettingsScreen({
    super.key,
    required this.preferencesController,
    required this.dataStore,
  });

  void _showEditProfileSheet(BuildContext context, UserProfile user) {
    final colors = context.colors;
    final nameCtrl = TextEditingController(text: user.displayName);
    final aboutCtrl = TextEditingController(text: user.about);
    bool isSaving = false;
    bool isUploadingPhoto = false;

    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: colors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (sheetCtx) {
        return StatefulBuilder(
          builder: (ctx, setSheetState) {
            final currentUserState = dataStore.currentUser;

            Future<void> pickAndUploadPhoto() async {
              final picker = ImagePicker();
              final picked = await picker.pickImage(
                source: ImageSource.gallery,
                maxWidth: 1024,
                maxHeight: 1024,
                imageQuality: 85,
              );
              if (picked == null) return;

              setSheetState(() => isUploadingPhoto = true);
              try {
                if (locator.isRegistered<ApiBackendService>()) {
                  final avatarUrl = await locator<ApiBackendService>().uploadProfilePhoto(File(picked.path));
                  await dataStore.updateProfile(currentUserState.copyWith(avatarUrl: avatarUrl));
                  if (ctx.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Profile photo updated!'),
                        backgroundColor: Color(0xFF10B981),
                        behavior: SnackBarBehavior.floating,
                      ),
                    );
                  }
                }
              } catch (e) {
                if (ctx.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Failed to upload photo: ${e.toString().replaceFirst('Exception: ', '')}'),
                      backgroundColor: Colors.redAccent,
                      behavior: SnackBarBehavior.floating,
                    ),
                  );
                }
              } finally {
                setSheetState(() => isUploadingPhoto = false);
              }
            }

            return Padding(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                top: 20,
                bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
              ),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Edit Profile',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: colors.foreground,
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.close_rounded),
                          onPressed: () => Navigator.pop(ctx),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    // Avatar with camera badge
                    GestureDetector(
                      onTap: isUploadingPhoto ? null : pickAndUploadPhoto,
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          ChatyAvatar(
                            initials: currentUserState.avatarInitials,
                            imageUrl: currentUserState.avatarUrl,
                            color: colors.primary,
                            size: 80,
                          ),
                          if (isUploadingPhoto)
                            Container(
                              width: 80,
                              height: 80,
                              decoration: BoxDecoration(
                                color: Colors.black45,
                                borderRadius: BorderRadius.circular(40),
                              ),
                              child: const Center(
                                child: SizedBox(
                                  width: 24,
                                  height: 24,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2.5,
                                    color: Colors.white,
                                  ),
                                ),
                              ),
                            )
                          else
                            Positioned(
                              bottom: 0,
                              right: 0,
                              child: Container(
                                padding: const EdgeInsets.all(6),
                                decoration: BoxDecoration(
                                  color: colors.primary,
                                  shape: BoxShape.circle,
                                  border: Border.all(color: colors.surface, width: 2),
                                ),
                                child: const Icon(
                                  Icons.camera_alt_rounded,
                                  size: 16,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: isUploadingPhoto ? null : pickAndUploadPhoto,
                      child: Text(
                        'Change Profile Photo',
                        style: TextStyle(
                          color: colors.primary,
                          fontWeight: FontWeight.w600,
                          fontSize: 13.5,
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: nameCtrl,
                      decoration: InputDecoration(
                        labelText: 'Display Name',
                        prefixIcon: const Icon(Icons.person_outline_rounded),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                      ),
                    ),
                    const SizedBox(height: 14),
                    TextField(
                      controller: aboutCtrl,
                      maxLines: 2,
                      decoration: InputDecoration(
                        labelText: 'About / Bio',
                        prefixIcon: const Icon(Icons.info_outline_rounded),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                      ),
                    ),
                    const SizedBox(height: 22),
                    SizedBox(
                      width: double.infinity,
                      height: 48,
                      child: ElevatedButton(
                        onPressed: isSaving
                            ? null
                            : () async {
                                setSheetState(() => isSaving = true);
                                try {
                                  final updated = currentUserState.copyWith(
                                    displayName: nameCtrl.text.trim(),
                                    about: aboutCtrl.text.trim(),
                                  );
                                  await dataStore.updateProfile(updated);
                                  if (ctx.mounted) {
                                    Navigator.pop(ctx);
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(
                                        content: Text('Profile updated successfully!'),
                                        backgroundColor: Color(0xFF10B981),
                                        behavior: SnackBarBehavior.floating,
                                      ),
                                    );
                                  }
                                } catch (e) {
                                  if (ctx.mounted) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      SnackBar(
                                        content: Text('Failed to update: ${e.toString().replaceFirst('Exception: ', '')}'),
                                        backgroundColor: Colors.redAccent,
                                        behavior: SnackBarBehavior.floating,
                                      ),
                                    );
                                  }
                                } finally {
                                  setSheetState(() => isSaving = false);
                                }
                              },
                        style: ElevatedButton.styleFrom(
                          backgroundColor: colors.primary,
                          foregroundColor: colors.onPrimary,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(16),
                          ),
                        ),
                        child: isSaving
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Text(
                                'Save Profile',
                                style: TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 15,
                                ),
                              ),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return ListenableBuilder(
      listenable: dataStore,
      builder: (context, _) {
        final user = dataStore.currentUser;

        return ChatySettingsPage(
          title: 'Account & Profile',
          subtitle: 'Profile photo, display name, username & bio',
          children: [
            // 1. PROFILE DETAILS
            ChatySettingsSection(
              title: 'Profile Information',
              children: [
                ChatySettingsTile(
                  leading: ChatyAvatar(
                    initials: user.avatarInitials,
                    imageUrl: user.avatarUrl,
                    color: colors.primary,
                    size: 42,
                  ),
                  title: user.displayName.isNotEmpty
                      ? user.displayName
                      : 'Display Name',
                  subtitle: '@${user.username}',
                  trailing: const Icon(Icons.edit_outlined, size: 20),
                  onTap: () => _showEditProfileSheet(context, user),
                ),
                ChatySettingsTile(
                  icon: Icons.info_outline_rounded,
                  iconColor: colors.primary,
                  title: 'About / Bio',
                  subtitle: user.about.isNotEmpty ? user.about : 'No bio set (Tap to edit)',
                  trailing: const Icon(Icons.chevron_right_rounded, size: 20),
                  onTap: () => _showEditProfileSheet(context, user),
                ),
              ],
            ),

            // 2. WORKSPACE & SUBSCRIPTION
            ChatySettingsSection(
              title: 'Workspace & Registration',
              children: [
                ChatySettingsTile(
                  icon: Icons.workspace_premium_rounded,
                  iconColor: const Color(0xFF10B981),
                  title: 'Subscription & Plans',
                  subtitle: 'Manage workspace plan, billing & feature limits',
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () {
                    final backend = locator<ApiBackendService>();
                    final org = backend.currentOrg;
                    final orgId = org?['uid']?.toString() ??
                        org?['tenantId']?.toString() ??
                        ApiBackendConfig.tenantId;
                    final orgName = org?['name']?.toString() ??
                        ApiBackendConfig.tenantId;
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => PlanSubscriptionScreen(
                          organizationId: orgId,
                          organizationName: orgName,
                          isNewRegistration: false,
                        ),
                      ),
                    );
                  },
                ),
                const _WorkspaceRegistrationToggle(),
              ],
            ),

            // 3. ACCOUNT ACTIONS
            ChatySettingsSection(
              title: 'Account Actions',
              children: [
                ChatySettingsTile(
                  icon: Icons.qr_code_rounded,
                  iconColor: colors.primary,
                  title: 'My QR Code Identity',
                  subtitle: 'Share your public contact card',
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () {},
                ),
                ChatySettingsTile(
                  icon: Icons.logout_rounded,
                  iconColor: colors.error,
                  title: 'Log Out',
                  subtitle: 'Sign out of your current session on this device',
                  onTap: () => confirmChatyLogout(context),
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}

class _WorkspaceRegistrationToggle extends StatefulWidget {
  const _WorkspaceRegistrationToggle();

  @override
  State<_WorkspaceRegistrationToggle> createState() =>
      _WorkspaceRegistrationToggleState();
}

class _WorkspaceRegistrationToggleState
    extends State<_WorkspaceRegistrationToggle> {
  bool _loading = false;
  bool _allowRegistration = true;

  @override
  void initState() {
    super.initState();
    _loadCurrentFlag();
  }

  Future<void> _loadCurrentFlag() async {
    final backend = locator<ApiBackendService>();
    final org = backend.currentOrg;
    final orgId = org?['uid']?.toString() ??
        org?['tenantId']?.toString() ??
        ApiBackendConfig.tenantId;

    if (orgId.isEmpty) return;

    try {
      final data = await backend.checkOrganization(orgId);
      final flag = data['allow_user_registration'] ??
          data['user_registration_enabled'];
      if (mounted) {
        setState(() {
          _allowRegistration =
              flag is bool ? flag : (flag?.toString().toLowerCase() != 'false');
        });
      }
    } catch (_) {}
  }

  Future<void> _toggle(bool val) async {
    final backend = locator<ApiBackendService>();
    final org = backend.currentOrg;
    final orgId = org?['uid']?.toString() ??
        org?['tenantId']?.toString() ??
        ApiBackendConfig.tenantId;

    setState(() => _loading = true);

    try {
      final success = await backend.updateOrganizationRegistrationSupport(
        organizationIdOrTenantId: orgId,
        allowUserRegistration: val,
      );

      if (!mounted) return;
      if (success) {
        setState(() {
          _allowRegistration = val;
          _loading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: const Color(0xFF10B981),
            behavior: SnackBarBehavior.floating,
            content: Text(
              val
                  ? 'User self-registration enabled for workspace.'
                  : 'User self-registration disabled for workspace.',
            ),
          ),
        );
      } else {
        setState(() => _loading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            backgroundColor: Colors.redAccent,
            behavior: SnackBarBehavior.floating,
            content: Text(
              'Failed to update registration settings. Admin rights required.',
            ),
          ),
        );
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: Colors.redAccent,
          behavior: SnackBarBehavior.floating,
          content: Text('Error: ${e.toString().replaceFirst('Exception: ', '')}'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return ChatySwitchTile(
      icon: Icons.person_add_rounded,
      iconColor: const Color(0xFF6366F1),
      title: 'Member Self-Registration',
      subtitle: _loading
          ? 'Updating workspace setting...'
          : _allowRegistration
              ? 'New users can self-register using workspace ID'
              : 'Self-registration disabled (invite-only)',
      value: _allowRegistration,
      enabled: !_loading,
      onChanged: _toggle,
    );
  }
}
