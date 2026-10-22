import 'package:flutter/material.dart';
import '../../../data/repositories/chaty_data_store.dart';
import '../../../data/services/api_backend_service.dart';
import '../../../data/services/backend_config.dart';
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
                    color: colors.primary,
                    size: 40,
                  ),
                  title: user.displayName.isNotEmpty
                      ? user.displayName
                      : 'Display Name',
                  subtitle: '@${user.username}',
                ),
                ChatySettingsTile(
                  icon: Icons.info_outline_rounded,
                  iconColor: colors.primary,
                  title: 'About / Bio',
                  subtitle: user.about.isNotEmpty ? user.about : 'No bio set',
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
