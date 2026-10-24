import 'package:flutter/material.dart';

import '../../data/services/backend_config.dart';
import '../../data/services/username_login_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/persistence/preferences_storage.dart';
import '../../ui/core/theme/theme_config.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../ui/core/validators/input_validators.dart';
import '../chats/main_navigation_shell.dart';
import 'forgot_password_screen.dart';
import 'register_organization_screen.dart';
import 'register_screen.dart';
import 'widgets/auth_components.dart';

class LoginScreen extends StatefulWidget {
  final bool autoPromptPermissions;

  const LoginScreen({super.key, this.autoPromptPermissions = true});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _identifierController = TextEditingController();
  final _passwordController = TextEditingController();
  final _workspaceController = TextEditingController();
  final UsernameLoginService _loginService = UsernameLoginService();

  bool _obscurePassword = true;
  bool _isLoading = false;
  bool _useOrganization = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final saved = await ApiBackendConfig.getSavedTenantId();
      if (!mounted) return;
      if (saved != null && saved.isNotEmpty) {
        setState(() {
          _workspaceController.text = saved;
          _useOrganization = true;
        });
      } else {
        setState(() {
          _useOrganization = !ApiBackendConfig.isUsingDefaultTenant;
        });
      }
    });
  }

  @override
  void dispose() {
    _identifierController.dispose();
    _passwordController.dispose();
    _workspaceController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (!_formKey.currentState!.validate() || _isLoading) return;

    final workspace = _workspaceController.text.trim();
    if (_useOrganization && workspace.isEmpty) {
      setState(() {
        _errorMessage = 'Please enter your Workspace / Organization ID.';
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final user = await _loginService.login(
        identifier: _identifierController.text.trim(),
        password: _passwordController.text,
        tenantId: _useOrganization ? workspace : null,
        isDefaultTenant: !_useOrganization,
      );
      await LocalPreferencesStorage.setStoredUserId(user.id);

      if (!mounted) return;
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
    if (value.toLowerCase().contains('tenant not found') ||
        value.toLowerCase().contains('tenant_not_found') ||
        value.toLowerCase().contains('x-tenant-id')) {
      return 'Workspace / Organization not found. Please check your Workspace ID or register a new organization.';
    }
    if (value.toLowerCase().contains('invalid') ||
        value.contains('Incorrect') ||
        value.contains('401')) {
      return 'Incorrect email/username or password.';
    }
    if (value.contains('Email not confirmed')) {
      return 'Confirm your email first, then sign in.';
    }
    return value;
  }

  String? _validateIdentifier(String? raw) {
    final value = raw?.trim() ?? '';
    if (value.isEmpty) return 'Enter your email or username';
    if (value.contains('@')) return ChatyValidators.validateEmail(value);
    return ChatyValidators.validateUsername(value, forRegistration: false);
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
              constraints: const BoxConstraints(maxWidth: 440),
              child: Form(
                key: _formKey,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const AuthBackButton(),
                    const SizedBox(height: 24),
                    Text(
                      'Welcome Back!',
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 28,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.5,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Sign in with your email address or Chaty username.',
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 14,
                      ),
                    ),
                    const SizedBox(height: 24),

                    // Workspace / Mode Selector
                    _buildWorkspaceModeSelector(theme),
                    const SizedBox(height: 18),

                    if (_useOrganization) ...[
                      AuthTextField(
                        label: 'Organization / Workspace ID',
                        hintText: 'e.g. acme-corp or school_123',
                        controller: _workspaceController,
                        theme: theme,
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
                      const SizedBox(height: 18),
                    ],

                    AuthTextField(
                      label: 'Email or username',
                      hintText: 'name@example.com or @username',
                      controller: _identifierController,
                      theme: theme,
                      keyboardType: TextInputType.emailAddress,
                      validator: _validateIdentifier,
                    ),
                    const SizedBox(height: 18),
                    AuthTextField(
                      label: 'Password',
                      hintText: 'Enter password',
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
                          return 'Please enter your password';
                        }
                        if (value.length < 6) {
                          return 'Password must be at least 6 characters';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 22),

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
                      text: 'Log In',
                      onPressed: _handleLogin,
                      isLoading: _isLoading,
                      theme: theme,
                    ),
                    const SizedBox(height: 14),

                    // Register Organization Link Card
                    _buildRegisterOrgCard(theme),

                    const SizedBox(height: 8),
                    Center(
                      child: TextButton(
                        onPressed: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const ForgotPasswordScreen(),
                            ),
                          );
                        },
                        child: Text(
                          'Forgot Password?',
                          style: TextStyle(
                            color: theme.secondaryTextColor,
                            fontSize: 13.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          "Don't have an account? ",
                          style: TextStyle(
                            color: theme.secondaryTextColor,
                            fontSize: 13.5,
                          ),
                        ),
                        GestureDetector(
                          onTap: () {
                            Navigator.of(context).push(
                              MaterialPageRoute(
                                builder: (_) => const RegisterScreen(),
                              ),
                            );
                          },
                          child: Text(
                            'Register',
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
    );
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
                        'Register an Organization',
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
}
