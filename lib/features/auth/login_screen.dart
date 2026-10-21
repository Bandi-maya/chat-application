import 'package:flutter/material.dart';

import '../../data/services/username_login_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/persistence/preferences_storage.dart';
import '../../ui/core/theme/theme_config.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../ui/core/validators/input_validators.dart';
import '../chats/main_navigation_shell.dart';
import 'forgot_password_screen.dart';
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
  final UsernameLoginService _loginService = UsernameLoginService();
  bool _obscurePassword = true;
  bool _isLoading = false;
  String? _errorMessage;

  @override
  void dispose() {
    _identifierController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (!_formKey.currentState!.validate() || _isLoading) return;
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final user = await _loginService.login(
        identifier: _identifierController.text.trim(),
        password: _passwordController.text,
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
                    const SizedBox(height: 28),
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
                    const SizedBox(height: 36),
                    AuthTextField(
                      label: 'Email or username',
                      hintText: 'name@example.com or @username',
                      controller: _identifierController,
                      theme: theme,
                      keyboardType: TextInputType.emailAddress,
                      validator: _validateIdentifier,
                    ),
                    const SizedBox(height: 20),
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
                    const SizedBox(height: 24),
                    if (_errorMessage != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 16.0),
                        child: Text(
                          _errorMessage!,
                          style: TextStyle(
                            color: theme.dangerColor,
                            fontSize: 13.5,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    AuthPrimaryButton(
                      text: 'Log In',
                      onPressed: _handleLogin,
                      isLoading: _isLoading,
                      theme: theme,
                    ),
                    const SizedBox(height: 8),
                    _buildQuickDemoAccounts(theme),
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

  void _quickLogin(String username) {
    _identifierController.text = username;
    _passwordController.text = 'Password123!';
    _handleLogin();
  }

  Widget _buildQuickDemoAccounts(ThemeConfig theme) {
    final demoUsers = [
      {'name': 'Alex Rivera', 'user': 'alex_rivera', 'role': 'Engineer', 'color': 0xFF3B82F6},
      {'name': 'Sarah Connor', 'user': 'sarah_connor', 'role': 'Product', 'color': 0xFFEC4899},
      {'name': 'Chat Admin', 'user': 'chat_admin', 'role': 'Admin', 'color': 0xFF8B5CF6},
      {'name': 'David Kim', 'user': 'david_kim', 'role': 'Design', 'color': 0xFF10B981},
      {'name': 'Michael Scott', 'user': 'michael_scott', 'role': 'Lead', 'color': 0xFFF59E0B},
      {'name': 'Elena Rostova', 'user': 'elena_rostova', 'role': 'Ops', 'color': 0xFF06B6D4},
    ];

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 14),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.surfaceColor.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: theme.dividerColor.withValues(alpha: 0.25),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.bolt_rounded, size: 16, color: theme.accentColor),
              const SizedBox(width: 6),
              Text(
                'Quick Demo Accounts',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 12.5,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.2,
                ),
              ),
              const Spacer(),
              Text(
                '1-Tap Login',
                style: TextStyle(
                  color: theme.secondaryTextColor.withValues(alpha: 0.7),
                  fontSize: 11,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: demoUsers.map((u) {
              final name = (u['name'] ?? '').toString();
              final username = (u['user'] ?? '').toString();
              final colorVal = (u['color'] as int?) ?? 0xFF3B82F6;
              final parts = name.split(' ');
              final initials = parts.isNotEmpty
                  ? parts.map((e) => e.isNotEmpty ? e[0] : '').take(2).join()
                  : 'U';
              return Material(
                color: Colors.transparent,
                child: InkWell(
                  borderRadius: BorderRadius.circular(10),
                  onTap: _isLoading ? null : () => _quickLogin(username),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
                    decoration: BoxDecoration(
                      color: Color(colorVal).withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: Color(colorVal).withValues(alpha: 0.35),
                      ),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        CircleAvatar(
                          radius: 9,
                          backgroundColor: Color(colorVal),
                          child: Text(
                            initials,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 8.5,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          name,
                          style: TextStyle(
                            color: theme.primaryTextColor,
                            fontSize: 11.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}
