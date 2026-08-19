import 'package:flutter/material.dart';

import '../../data/services/chaty_backend_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/theme/theme_controller.dart';
import 'widgets/auth_components.dart';

class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  bool _isLoading = false;
  bool _emailSent = false;
  String? _errorMessage;

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  Future<void> _handleSendRecovery() async {
    if (!_formKey.currentState!.validate() || _isLoading) return;
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      await locator<ChatyBackendService>().resetPassword(
        _emailController.text.trim(),
      );
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _emailSent = true;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorMessage = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const AuthBackButton(),
                    const SizedBox(height: 12),
                    AuthIllustration(
                      type: 'forgot_password',
                      theme: theme,
                      height: 190,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'Forgot Password?',
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 26,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.5,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _emailSent
                          ? 'We sent a secure recovery link. Open the newest email on this device; Chaty will reopen through the reset-password callback.'
                          : 'Enter the email registered with your Chaty account.',
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 14,
                        height: 1.4,
                      ),
                    ),
                    const SizedBox(height: 28),
                    AuthTextField(
                      label: 'Email',
                      hintText: 'Enter email',
                      controller: _emailController,
                      theme: theme,
                      keyboardType: TextInputType.emailAddress,
                      validator: (value) {
                        final email = value?.trim() ?? '';
                        if (email.isEmpty) return 'Please enter your email';
                        if (!RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(email)) {
                          return 'Please enter a valid email';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 20),
                    if (_errorMessage != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 16),
                        child: Text(
                          _errorMessage!,
                          style: TextStyle(
                            color: theme.dangerColor,
                            fontSize: 13.5,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    const SizedBox(height: 12),
                    AuthPrimaryButton(
                      text: _emailSent ? 'Resend Recovery Email' : 'Send Recovery Email',
                      onPressed: _handleSendRecovery,
                      isLoading: _isLoading,
                      theme: theme,
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
}
