import 'dart:async';

import 'package:flutter/material.dart';

import '../../data/services/api_backend_service.dart';
import '../../data/services/backend_config.dart';
import '../../injection/locator.dart';
import '../../ui/core/persistence/preferences_storage.dart';
import '../../ui/core/theme/theme_config.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../ui/core/validators/input_validators.dart';
import '../chats/main_navigation_shell.dart';
import 'register_organization_screen.dart';
import 'widgets/auth_components.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _workspaceController = TextEditingController();

  late final ApiBackendService _apiBackend;

  bool _obscurePassword = true;
  bool _isLoading = false;
  bool _useOrganization = false;
  String? _errorMessage;

  Timer? _orgCheckTimer;
  bool _isCheckingOrg = false;
  bool? _orgFound;
  String? _orgName;
  bool _allowUserRegistration = true;
  String? _orgCheckError;

  @override
  void initState() {
    super.initState();
    _apiBackend = locator<ApiBackendService>();

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final saved = await ApiBackendConfig.getSavedTenantId();
      if (!mounted) return;
      if (saved != null && saved.isNotEmpty) {
        setState(() {
          _workspaceController.text = saved;
          _useOrganization = true;
        });
        _checkOrganizationStatus(saved);
      } else {
        setState(() {
          _useOrganization = !ApiBackendConfig.isUsingDefaultTenant;
        });
      }
    });
  }

  @override
  void dispose() {
    _orgCheckTimer?.cancel();
    _emailController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    _workspaceController.dispose();
    super.dispose();
  }

  void _onWorkspaceChanged(String value) {
    _orgCheckTimer?.cancel();
    final clean = value.trim();
    if (clean.isEmpty) {
      setState(() {
        _isCheckingOrg = false;
        _orgFound = null;
        _orgName = null;
        _allowUserRegistration = true;
        _orgCheckError = null;
      });
      return;
    }

    setState(() {
      _isCheckingOrg = true;
      _orgCheckError = null;
    });

    _orgCheckTimer = Timer(const Duration(milliseconds: 400), () async {
      await _checkOrganizationStatus(clean);
    });
  }

  Future<void> _checkOrganizationStatus(String workspace) async {
    if (!mounted) return;
    try {
      final data = await _apiBackend.checkOrganization(workspace);
      if (!mounted) return;

      final dynamic flag = data['allow_user_registration'] ?? data['user_registration_enabled'];
      final bool allowReg = flag is bool ? flag : (flag?.toString().toLowerCase() != 'false');
      final String name = (data['name'] ?? workspace).toString();

      setState(() {
        _isCheckingOrg = false;
        _orgFound = true;
        _orgName = name;
        _allowUserRegistration = allowReg;
        _orgCheckError = null;
        if (!allowReg) {
          _errorMessage = null;
        }
      });
    } catch (e) {
      if (!mounted) return;
      final msg = e.toString().replaceFirst('Exception: ', '');
      setState(() {
        _isCheckingOrg = false;
        _orgFound = false;
        _orgName = null;
        _allowUserRegistration = false;
        _orgCheckError = msg;
      });
    }
  }

  Future<void> _handleRegister() async {
    if (_isLoading) return;
    if (_formKey.currentState?.validate() != true) return;

    final workspace = _workspaceController.text.trim();
    if (_useOrganization) {
      if (workspace.isEmpty) {
        setState(() {
          _errorMessage = 'Please enter your Workspace / Organization ID.';
        });
        return;
      }
      if (_isCheckingOrg) {
        setState(() {
          _errorMessage = 'Checking workspace settings, please wait...';
        });
        return;
      }
      if (!_allowUserRegistration) {
        setState(() {
          _errorMessage =
              'User self-registration is disabled for "${_orgName ?? workspace}". Contact your administrator.';
        });
        return;
      }
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      if (_useOrganization && workspace.isNotEmpty) {
        await ApiBackendConfig.setTenantId(workspace, useDefault: false);
      } else {
        await ApiBackendConfig.setTenantId(null, useDefault: true);
      }

      final user = await _apiBackend.register(
        email: _emailController.text.trim(),
        username: _usernameController.text.trim(),
        password: _passwordController.text,
      );

      await LocalPreferencesStorage.setStoredUserId(user.id);

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: const Color(0xFF10B981),
          content: Text(
            'Account created successfully! Welcome, ${user.displayName}.',
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
          behavior: SnackBarBehavior.floating,
        ),
      );

      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const MainNavigationShell()),
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
        value.toLowerCase().contains('failed host lookup') ||
        value.toLowerCase().contains('clientexception')) {
      return 'Unable to connect to server. Please ensure the backend is running.';
    }
    if (value.toLowerCase().contains('self-registration is disabled') ||
        value.toLowerCase().contains('registration is disabled') ||
        value.toLowerCase().contains('disabled for organization')) {
      return value;
    }
    if (value.toLowerCase().contains('tenant not found') ||
        value.toLowerCase().contains('tenant_not_found') ||
        value.toLowerCase().contains('x-tenant-id')) {
      return 'Workspace / Organization not found. Please check your Workspace ID or register a new organization.';
    }
    if (value.contains('already exists') || value.contains('already registered')) {
      return 'An account already exists for this email or username. Sign in instead.';
    }
    if (value.contains('already taken')) {
      return 'That username was just taken. Choose another username.';
    }
    if (value.contains('rate limit')) {
      return 'Too many attempts. Wait a moment and try again.';
    }
    return value;
  }

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
              child: const AuthBackButton(),
            ),
            Expanded(
              child: Center(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24.0,
                    vertical: 12.0,
                  ),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 440),
                    child: Form(
                      key: _formKey,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Everything You Need!',
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 28,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.5,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Create your Chaty account with a unique public username.',
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 14,
                      ),
                    ),
                    const SizedBox(height: 20),

                    // Workspace Mode Selector
                    _buildWorkspaceModeSelector(theme),
                    const SizedBox(height: 18),

                    if (_useOrganization) ...[
                      AuthTextField(
                        label: 'Organization / Workspace ID',
                        hintText: 'e.g. acme-corp or school_123',
                        controller: _workspaceController,
                        theme: theme,
                        onChanged: _onWorkspaceChanged,
                        prefixIcon: Icon(
                          Icons.apartment_rounded,
                          size: 20,
                          color: theme.secondaryTextColor.withValues(alpha: 0.7),
                        ),
                        validator: (val) {
                          if (!_useOrganization) return null;
                          if (val == null || val.trim().isEmpty) {
                            return 'Please enter your Workspace ID';
                          }
                          return null;
                        },
                      ),
                      _buildOrganizationStatusBanner(theme),
                      const SizedBox(height: 18),
                    ],

                    AuthTextField(
                      label: 'Email',
                      hintText: 'Enter email',
                      controller: _emailController,
                      theme: theme,
                      keyboardType: TextInputType.emailAddress,
                      validator: ChatyValidators.validateEmail,
                    ),
                    const SizedBox(height: 18),
                    AuthTextField(
                      label: 'Username',
                      hintText: 'Choose a unique username',
                      controller: _usernameController,
                      theme: theme,
                      prefixIcon: Icon(
                        Icons.alternate_email_rounded,
                        size: 20,
                        color: theme.secondaryTextColor.withValues(alpha: 0.7),
                      ),
                      validator: (val) => ChatyValidators.validateUsername(
                        val,
                        forRegistration: true,
                      ),
                    ),
                    const SizedBox(height: 18),
                    AuthTextField(
                      label: 'Password',
                      hintText:
                          'Minimum 6 characters',
                      controller: _passwordController,
                      obscureText: _obscurePassword,
                      theme: theme,
                      textInputAction: TextInputAction.done,
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscurePassword
                              ? Icons.visibility_off_outlined
                              : Icons.visibility_outlined,
                          color: theme.secondaryTextColor.withValues(
                            alpha: 0.6,
                          ),
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
                      Padding(
                        padding: const EdgeInsets.only(bottom: 16.0),
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: theme.dangerColor.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(
                              color: theme.dangerColor.withValues(alpha: 0.35),
                            ),
                          ),
                          child: Row(
                            children: [
                              Icon(
                                Icons.info_outline_rounded,
                                color: theme.dangerColor,
                                size: 18,
                              ),
                              const SizedBox(width: 8),
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
                      ),
                    AuthPrimaryButton(
                      text: (_useOrganization && !_allowUserRegistration)
                          ? 'Registration Disabled'
                          : 'Register',
                      onPressed: (_useOrganization && !_allowUserRegistration)
                          ? null
                          : _handleRegister,
                      isLoading: _isLoading,
                      theme: theme,
                    ),
                    const SizedBox(height: 14),

                    // Register Organization Link Card
                    _buildRegisterOrgCard(theme),

                    const SizedBox(height: 20),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          'Already have an account? ',
                          style: TextStyle(
                            color: theme.secondaryTextColor,
                            fontSize: 13.5,
                          ),
                        ),
                        GestureDetector(
                          onTap: () => Navigator.of(context).pop(),
                          child: Text(
                            'Log In',
                            style: TextStyle(
                              color: theme.accentColor,
                              fontSize: 13.5,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    ],
  ),
),
);
  }

  Widget _buildOrganizationStatusBanner(ThemeConfig theme) {
    if (!_useOrganization || _workspaceController.text.trim().isEmpty) {
      return const SizedBox.shrink();
    }

    if (_isCheckingOrg) {
      return Container(
        margin: const EdgeInsets.only(top: 8, bottom: 4),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: theme.surfaceColor.withValues(alpha: 0.5),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: theme.dividerColor.withValues(alpha: 0.2)),
        ),
        child: Row(
          children: [
            SizedBox(
              width: 14,
              height: 14,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                valueColor: AlwaysStoppedAnimation<Color>(theme.accentColor),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                'Checking workspace registration support...',
                style: TextStyle(
                  color: theme.secondaryTextColor,
                  fontSize: 12.5,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ],
        ),
      );
    }

    if (_orgFound == true) {
      if (_allowUserRegistration) {
        return Container(
          margin: const EdgeInsets.only(top: 8, bottom: 4),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
          decoration: BoxDecoration(
            color: const Color(0xFF10B981).withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: const Color(0xFF10B981).withValues(alpha: 0.35),
            ),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.only(top: 2),
                child: Icon(
                  Icons.check_circle_rounded,
                  color: Color(0xFF10B981),
                  size: 18,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _orgName != null && _orgName!.isNotEmpty
                          ? 'Organization: $_orgName'
                          : 'Valid Organization',
                      style: const TextStyle(
                        color: Color(0xFF047857),
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'User self-registration is enabled for this workspace.',
                      style: TextStyle(
                        color: const Color(0xFF047857).withValues(alpha: 0.9),
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      } else {
        // User registration DISABLED by backend organization config!
        return Container(
          margin: const EdgeInsets.only(top: 8, bottom: 4),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: const Color(0xFFF59E0B).withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: const Color(0xFFF59E0B).withValues(alpha: 0.4),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF59E0B).withValues(alpha: 0.2),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.lock_rounded,
                      color: Color(0xFFB45309),
                      size: 16,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Self-Registration Disabled',
                          style: TextStyle(
                            color: Color(0xFFB45309),
                            fontSize: 13.5,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          'Organization "${_orgName ?? _workspaceController.text}" has restricted user self-registration. Only administrators can add or invite members to this workspace.',
                          style: const TextStyle(
                            color: Color(0xFF92400E),
                            fontSize: 12,
                            height: 1.35,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () {
                        setState(() {
                          _useOrganization = false;
                          _errorMessage = null;
                        });
                      },
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        side: BorderSide(
                          color: const Color(0xFFB45309).withValues(alpha: 0.4),
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                      child: const Text(
                        'Default Mode',
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w700,
                          color: Color(0xFFB45309),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () {
                        Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => const RegisterOrganizationScreen(),
                          ),
                        );
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFB45309),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        elevation: 0,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                      child: const Text(
                        'New Org',
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        );
      }
    }

    if (_orgFound == false && _orgCheckError != null) {
      return Container(
        margin: const EdgeInsets.only(top: 8, bottom: 4),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: theme.dangerColor.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: theme.dangerColor.withValues(alpha: 0.3),
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              Icons.error_outline_rounded,
              color: theme.dangerColor,
              size: 16,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                _orgCheckError!,
                style: TextStyle(
                  color: theme.dangerColor,
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ],
        ),
      );
    }

    return const SizedBox.shrink();
  }

  Widget _buildWorkspaceModeSelector(ThemeConfig theme) {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: theme.surfaceColor.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: theme.dividerColor.withValues(alpha: 0.25),
        ),
      ),
      child: Row(
        children: [
          Expanded(
            child: _buildModeTab(
              theme: theme,
              label: 'Normal / Default',
              icon: Icons.public_rounded,
              isSelected: !_useOrganization,
              onTap: () {
                setState(() {
                  _useOrganization = false;
                  _errorMessage = null;
                });
              },
            ),
          ),
          Expanded(
            child: _buildModeTab(
              theme: theme,
              label: 'Organization',
              icon: Icons.domain_rounded,
              isSelected: _useOrganization,
              onTap: () {
                setState(() {
                  _useOrganization = true;
                  _errorMessage = null;
                });
                if (_workspaceController.text.trim().isNotEmpty) {
                  _checkOrganizationStatus(_workspaceController.text.trim());
                }
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildModeTab({
    required ThemeConfig theme,
    required String label,
    required IconData icon,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(vertical: 9, horizontal: 8),
          decoration: BoxDecoration(
            color: isSelected ? theme.accentColor : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
            boxShadow: isSelected
                ? [
                    BoxShadow(
                      color: theme.accentColor.withValues(alpha: 0.25),
                      blurRadius: 6,
                      offset: const Offset(0, 2),
                    ),
                  ]
                : null,
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                icon,
                size: 16,
                color: isSelected ? theme.onAccentColor : theme.secondaryTextColor,
              ),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  label,
                  style: TextStyle(
                    color: isSelected
                        ? theme.onAccentColor
                        : theme.secondaryTextColor,
                    fontSize: 12.5,
                    fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildRegisterOrgCard(ThemeConfig theme) {
    return Container(
      decoration: BoxDecoration(
        color: theme.accentColor.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: theme.accentColor.withValues(alpha: 0.2),
        ),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(14),
          onTap: () {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => const RegisterOrganizationScreen(),
              ),
            );
          },
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(7),
                  decoration: BoxDecoration(
                    color: theme.accentColor.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(
                    Icons.domain_add_rounded,
                    color: theme.accentColor,
                    size: 20,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Register a New Organization',
                        style: TextStyle(
                          color: theme.primaryTextColor,
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 1),
                      Text(
                        'Set up a custom workspace & team space',
                        style: TextStyle(
                          color: theme.secondaryTextColor,
                          fontSize: 11.5,
                        ),
                      ),
                    ],
                  ),
                ),
                Icon(
                  Icons.arrow_forward_ios_rounded,
                  size: 14,
                  color: theme.accentColor,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
