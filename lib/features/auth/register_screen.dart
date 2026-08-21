import 'dart:async';

import 'package:flutter/material.dart';

import '../../data/services/backend_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/persistence/preferences_storage.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../ui/core/widgets/username_availability_field.dart';
import '../chats/main_navigation_shell.dart';
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
  late final ChatyBackendService _backend;
  bool _obscurePassword = true;
  bool _isLoading = false;
  bool? _usernameAvailable;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _backend = locator<ChatyBackendService>();
  }

  @override
  void dispose() {
    _emailController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _openHome(ChatyBackendService backend) async {
    final user = backend.currentUser;
    if (user == null || !backend.isAuthenticated) {
      throw Exception('A verified session was not created yet.');
    }
    await LocalPreferencesStorage.setStoredUserId(user.id);
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const MainNavigationShell()),
      (route) => false,
    );
  }

  Future<void> _handleRegister() async {
    if (_isLoading) return;
    if (_formKey.currentState?.validate() != true) return;
    if (_usernameAvailable != true) {
      setState(
        () => _errorMessage =
            'Choose an available username before creating the account.',
      );
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      await _backend.registerUser(
        displayName: _usernameController.text.trim(),
        username: _usernameController.text.trim(),
        password: _passwordController.text,
        email: _emailController.text.trim(),
      );

      if (_backend.isAuthenticated) {
        await _openHome(_backend);
        return;
      }

      if (!mounted) return;
      setState(() => _isLoading = false);
      await _showVerificationDialog(_backend);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _errorMessage = _friendlyError(error);
        _isLoading = false;
      });
    }
  }

  Future<void> _showVerificationDialog(ChatyBackendService backend) async {
    final confirmed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Verify your email'),
        content: const Text(
          'Your account was created. Open the newest Chaty verification email and tap Confirm. Android can reopen Chaty through the secure chaty:// callback. Then tap Continue below.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Later'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Continue'),
          ),
        ],
      ),
    );

    if (!mounted || confirmed != true) return;
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      await backend
          .login(
            identifier: _emailController.text.trim(),
            password: _passwordController.text,
          )
          .timeout(const Duration(seconds: 15));
      await _openHome(backend);
    } on TimeoutException {
      if (!mounted) return;
      setState(() {
        _errorMessage =
            'Verification is taking too long. Check your connection, then sign in with the same email and password.';
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorMessage =
            'Email verification is still pending. Tap the confirmation link in the newest email, then sign in with the same credentials.';
        _isLoading = false;
      });
    }
  }

  String _friendlyError(Object error) {
    final value = error.toString().replaceFirst('Exception: ', '');
    if (value.contains('already registered'))
      return 'An account already exists for this email. Sign in instead.';
    if (value.contains('already taken'))
      return 'That username was just taken. Choose one of the available suggestions.';
    if (value.contains('rate limit'))
      return 'Too many attempts. Wait a moment and try again.';
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
                    const SizedBox(height: 32),
                    AuthTextField(
                      label: 'Email',
                      hintText: 'Enter email',
                      controller: _emailController,
                      theme: theme,
                      keyboardType: TextInputType.emailAddress,
                      validator: (value) {
                        final email = value?.trim() ?? '';
                        if (email.isEmpty) return 'Please enter your email';
                        final valid = RegExp(
                          r'^[^\s@]+@[^\s@]+\.[^\s@]+$',
                        ).hasMatch(email);
                        if (!valid) return 'Please enter a valid email address';
                        return null;
                      },
                    ),
                    const SizedBox(height: 18),
                    UsernameAvailabilityField(
                      controller: _usernameController,
                      backend: _backend,
                      enabled: !_isLoading,
                      onAvailabilityChanged: (value) {
                        if (mounted) setState(() => _usernameAvailable = value);
                      },
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 14,
                      ),
                      decoration: InputDecoration(
                        labelText: 'Username',
                        hintText: 'Choose a unique username',
                        filled: true,
                        fillColor: theme.cardColor,
                        labelStyle: TextStyle(color: theme.secondaryTextColor),
                        hintStyle: TextStyle(
                          color: theme.secondaryTextColor.withValues(
                            alpha: 0.65,
                          ),
                        ),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(
                            theme.cornerRadius,
                          ),
                        ),
                      ),
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
                        final password = value ?? '';
                        if (password.isEmpty)
                          return 'Please enter your password';
                        if (password.length < 8)
                          return 'Password must be at least 8 characters';
                        if (!RegExp(r'[A-Z]').hasMatch(password) ||
                            !RegExp(r'[a-z]').hasMatch(password) ||
                            !RegExp(r'[0-9]').hasMatch(password)) {
                          return 'Use upper/lowercase letters and a number';
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
                      text: 'Register',
                      onPressed: _handleRegister,
                      isLoading: _isLoading,
                      theme: theme,
                    ),
                    const SizedBox(height: 20),
                    AuthOrDivider(theme: theme),
                    const SizedBox(height: 20),
                    AuthSocialRow(theme: theme),
                    const SizedBox(height: 28),
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
    );
  }
}
