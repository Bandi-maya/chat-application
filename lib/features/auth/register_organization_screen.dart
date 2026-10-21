import 'package:flutter/material.dart';

import '../../data/services/api_backend_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/persistence/preferences_storage.dart';
import '../../ui/core/theme/theme_config.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../ui/core/validators/input_validators.dart';
import '../subscription/plan_subscription_screen.dart';
import 'widgets/auth_components.dart';

class RegisterOrganizationScreen extends StatefulWidget {
  const RegisterOrganizationScreen({super.key});

  @override
  State<RegisterOrganizationScreen> createState() =>
      _RegisterOrganizationScreenState();
}

class _RegisterOrganizationScreenState
    extends State<RegisterOrganizationScreen> {
  final _formKey = GlobalKey<FormState>();

  final _orgNameController = TextEditingController();
  final _tenantIdController = TextEditingController();
  final _adminFullNameController = TextEditingController();
  final _adminUsernameController = TextEditingController();
  final _adminEmailController = TextEditingController();
  final _adminPasswordController = TextEditingController();

  final ApiBackendService _backend = locator<ApiBackendService>();

  bool _obscurePassword = true;
  bool _isLoading = false;
  bool _hasManuallyEditedSlug = false;
  String? _errorMessage;

  @override
  void dispose() {
    _orgNameController.dispose();
    _tenantIdController.dispose();
    _adminFullNameController.dispose();
    _adminUsernameController.dispose();
    _adminEmailController.dispose();
    _adminPasswordController.dispose();
    super.dispose();
  }

  void _onOrgNameChanged(String value) {
    if (!_hasManuallyEditedSlug) {
      final slug = value
          .toLowerCase()
          .replaceAll(RegExp(r'[^a-z0-9]+'), '-')
          .replaceAll(RegExp(r'^-+|-+$'), '');
      _tenantIdController.text = slug;
    }
  }

  Future<void> _handleRegisterOrganization() async {
    if (!_formKey.currentState!.validate() || _isLoading) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final orgName = _orgNameController.text.trim();
      final tenantId = _tenantIdController.text.trim().toLowerCase();
      final adminFullName = _adminFullNameController.text.trim();
      final adminUsername = _adminUsernameController.text.trim();
      final adminEmail = _adminEmailController.text.trim();
      final adminPassword = _adminPasswordController.text;

      final regResult = await _backend.registerOrganization(
        tenantId: tenantId,
        organizationName: orgName,
        adminUsername: adminUsername,
        adminEmail: adminEmail,
        adminPassword: adminPassword,
        adminFullName: adminFullName,
        tenantType: 'chat',
      );

      await LocalPreferencesStorage.setStoredUserId(regResult.user.id);

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: const Color(0xFF10B981),
          content: Text(
            'Organization "$orgName" registered! Select a plan to activate.',
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
          behavior: SnackBarBehavior.floating,
        ),
      );

      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(
          builder: (_) => PlanSubscriptionScreen(
            organizationId: regResult.organizationId,
            organizationName: regResult.organizationName,
            isNewRegistration: true,
          ),
        ),
        (route) => false,
      );
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _errorMessage = _friendlyError(error);
        _isLoading = false;
      });
    }
  }

  String _friendlyError(Object error) {
    final value = error.toString().replaceFirst('Exception: ', '');
    if (value.toLowerCase().contains('connection refused') ||
        value.toLowerCase().contains('socketexception') ||
        value.toLowerCase().contains('failed host lookup')) {
      return 'Unable to connect to the backend server. Please verify your connection.';
    }
    if (value.toLowerCase().contains('tenant id already exists')) {
      return 'This Workspace ID is already taken. Please choose a different Workspace ID.';
    }
    if (value.toLowerCase().contains('domain already mapped')) {
      return 'This domain or workspace is already mapped to an existing organization.';
    }
    return value;
  }

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(
              horizontal: 24.0,
              vertical: 20.0,
            ),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const AuthBackButton(),
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: theme.accentColor.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(14),
                          ),
                          child: Icon(
                            Icons.domain_add_rounded,
                            color: theme.accentColor,
                            size: 26,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Register Organization',
                                style: TextStyle(
                                  color: theme.primaryTextColor,
                                  fontSize: 24,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: -0.4,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                'Create a dedicated workspace for your team',
                                style: TextStyle(
                                  color: theme.secondaryTextColor,
                                  fontSize: 13.5,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 28),

                    // Section 1: Organization Details
                    _buildSectionHeader(
                      theme,
                      '1. Organization Details',
                      Icons.apartment_rounded,
                    ),
                    const SizedBox(height: 14),
                    AuthTextField(
                      label: 'Organization Name',
                      hintText: 'e.g. Acme Corporation',
                      controller: _orgNameController,
                      theme: theme,
                      onChanged: _onOrgNameChanged,
                      prefixIcon: Icon(
                        Icons.business_rounded,
                        size: 20,
                        color: theme.secondaryTextColor.withValues(alpha: 0.7),
                      ),
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Please enter your organization name';
                        }
                        if (value.trim().length < 2) {
                          return 'Name must be at least 2 characters';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    AuthTextField(
                      label: 'Workspace / Tenant ID',
                      hintText: 'e.g. acme-corp',
                      controller: _tenantIdController,
                      theme: theme,
                      onChanged: (_) => _hasManuallyEditedSlug = true,
                      prefixIcon: Icon(
                        Icons.link_rounded,
                        size: 20,
                        color: theme.secondaryTextColor.withValues(alpha: 0.7),
                      ),
                      validator: (value) {
                        final clean = value?.trim().toLowerCase() ?? '';
                        if (clean.isEmpty) {
                          return 'Workspace ID is required';
                        }
                        if (clean.length < 3) {
                          return 'Workspace ID must be at least 3 characters';
                        }
                        if (!RegExp(r'^[a-z0-9_-]+$').hasMatch(clean)) {
                          return 'Use lowercase letters, numbers, and hyphens only';
                        }
                        return null;
                      },
                    ),
                    Padding(
                      padding: const EdgeInsets.only(top: 6.0, left: 4.0),
                      child: Text(
                        'Your team members will use this Workspace ID to sign into your organization.',
                        style: TextStyle(
                          color: theme.secondaryTextColor.withValues(alpha: 0.8),
                          fontSize: 12,
                        ),
                      ),
                    ),
                    const SizedBox(height: 28),

                    // Section 2: Administrator Profile
                    _buildSectionHeader(
                      theme,
                      '2. Admin Account',
                      Icons.admin_panel_settings_rounded,
                    ),
                    const SizedBox(height: 14),
                    AuthTextField(
                      label: 'Admin Full Name',
                      hintText: 'e.g. Alex Rivera',
                      controller: _adminFullNameController,
                      theme: theme,
                      prefixIcon: Icon(
                        Icons.badge_outlined,
                        size: 20,
                        color: theme.secondaryTextColor.withValues(alpha: 0.7),
                      ),
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Please enter administrator full name';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    AuthTextField(
                      label: 'Admin Username',
                      hintText: 'e.g. alex_admin',
                      controller: _adminUsernameController,
                      theme: theme,
                      prefixIcon: Icon(
                        Icons.alternate_email_rounded,
                        size: 20,
                        color: theme.secondaryTextColor.withValues(alpha: 0.7),
                      ),
                      validator: (value) => ChatyValidators.validateUsername(
                        value,
                        forRegistration: true,
                      ),
                    ),
                    const SizedBox(height: 16),
                    AuthTextField(
                      label: 'Admin Email',
                      hintText: 'e.g. alex@acme.com',
                      controller: _adminEmailController,
                      theme: theme,
                      keyboardType: TextInputType.emailAddress,
                      prefixIcon: Icon(
                        Icons.mail_outline_rounded,
                        size: 20,
                        color: theme.secondaryTextColor.withValues(alpha: 0.7),
                      ),
                      validator: ChatyValidators.validateEmail,
                    ),
                    const SizedBox(height: 16),
                    AuthTextField(
                      label: 'Admin Password',
                      hintText: 'Minimum 6 characters',
                      controller: _adminPasswordController,
                      obscureText: _obscurePassword,
                      theme: theme,
                      textInputAction: TextInputAction.done,
                      prefixIcon: Icon(
                        Icons.lock_outline_rounded,
                        size: 20,
                        color: theme.secondaryTextColor.withValues(alpha: 0.7),
                      ),
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscurePassword
                              ? Icons.visibility_off_outlined
                              : Icons.visibility_outlined,
                          color: theme.secondaryTextColor.withValues(alpha: 0.6),
                          size: 20,
                        ),
                        onPressed: () => setState(
                          () => _obscurePassword = !_obscurePassword,
                        ),
                      ),
                      validator: (value) {
                        if (value == null || value.isEmpty) {
                          return 'Please enter a password';
                        }
                        if (value.length < 6) {
                          return 'Password must be at least 6 characters';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 24),

                    if (_errorMessage != null)
                      Container(
                        padding: const EdgeInsets.all(12),
                        margin: const EdgeInsets.only(bottom: 18),
                        decoration: BoxDecoration(
                          color: theme.dangerColor.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: theme.dangerColor.withValues(alpha: 0.4),
                          ),
                        ),
                        child: Row(
                          children: [
                            Icon(
                              Icons.error_outline_rounded,
                              color: theme.dangerColor,
                              size: 20,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Text(
                                _errorMessage!,
                                style: TextStyle(
                                  color: theme.dangerColor,
                                  fontSize: 13,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),

                    AuthPrimaryButton(
                      text: 'Create Organization',
                      onPressed: _handleRegisterOrganization,
                      isLoading: _isLoading,
                      theme: theme,
                    ),
                    const SizedBox(height: 20),

                    Center(
                      child: TextButton.icon(
                        onPressed: () => Navigator.of(context).pop(),
                        icon: Icon(
                          Icons.arrow_back_rounded,
                          size: 16,
                          color: theme.secondaryTextColor,
                        ),
                        label: Text(
                          'Already have an organization? Log in',
                          style: TextStyle(
                            color: theme.secondaryTextColor,
                            fontSize: 13.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildSectionHeader(ThemeConfig theme, String title, IconData icon) {
    return Row(
      children: [
        Icon(icon, size: 18, color: theme.accentColor),
        const SizedBox(width: 8),
        Text(
          title,
          style: TextStyle(
            color: theme.primaryTextColor,
            fontSize: 15,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.1,
          ),
        ),
      ],
    );
  }
}
