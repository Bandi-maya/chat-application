import 'package:flutter/material.dart';

import '../../data/services/chaty_backend_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/persistence/preferences_storage.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../chats/main_navigation_shell.dart';
import 'forgot_password_screen.dart';
import 'register_screen.dart';
import 'widgets/auth_components.dart';

class LoginScreen extends StatefulWidget {
  final bool autoPromptPermissions;

  const LoginScreen({
    super.key,
    this.autoPromptPermissions = true,
  });

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _identifierController = TextEditingController();
  final _passwordController = TextEditingController();
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
      final backend = locator<ChatyBackendService>();
      final user = await backend.login(
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
    if (value.contains('Invalid login credentials') || value.contains('invalid_credentials')) {
      return 'Incorrect email or password.';
    }
    if (value.contains('Email not confirmed')) {
      return 'Confirm your email first, then sign in.';
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
            padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 20.0),
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
                      'Login to continue using the app.',
                      style: TextStyle(color: theme.secondaryTextColor, fontSize: 14),
                    ),
                    const SizedBox(height: 36),
                    AuthTextField(
                      label: 'Email',
                      hintText: 'Enter your registered email',
                      controller: _identifierController,
                      theme: theme,
                      keyboardType: TextInputType.emailAddress,
                      validator: (value) {
                        final email = value?.trim() ?? '';
                        if (email.isEmpty) return 'Please enter your email';
                        if (!RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(email)) {
                          return 'Please enter a valid email address';
                        }
                        return null;
                      },
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
                          _obscurePassword ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                          color: theme.secondaryTextColor.withValues(alpha: 0.6),
                          size: 20,
                        ),
                        onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                      ),
                      validator: (value) {
                        if (value == null || value.isEmpty) return 'Please enter your password';
                        if (value.length < 8) return 'Password must be at least 8 characters';
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
                    const SizedBox(height: 14),
                    Center(
                      child: TextButton(
                        onPressed: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => const ForgotPasswordScreen()),
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
                    AuthOrDivider(theme: theme),
                    const SizedBox(height: 24),
                    AuthSocialRow(theme: theme),
                    const SizedBox(height: 32),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          "Didn't have account? ",
                          style: TextStyle(color: theme.secondaryTextColor, fontSize: 13.5),
                        ),
                        GestureDetector(
                          onTap: () {
                            Navigator.of(context).push(
                              MaterialPageRoute(builder: (_) => const RegisterScreen()),
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
}
