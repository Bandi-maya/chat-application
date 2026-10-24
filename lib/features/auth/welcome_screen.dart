import 'package:flutter/material.dart';

import '../../injection/locator.dart';
import '../../ui/core/controllers/app_icon_controller.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../ui/core/widgets/app_brand_icon.dart';
import 'login_screen.dart';
import 'register_screen.dart';
import 'widgets/auth_components.dart';

class WelcomeScreen extends StatelessWidget {
  const WelcomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;
    final appIconController = locator<AppIconController>();

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 24),
              Expanded(
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      ChatyBrandIcon(
                        controller: appIconController,
                        size: 72,
                        borderRadius: 20,
                      ),
                      const SizedBox(height: 18),
                      AuthIllustration(
                        type: 'welcome',
                        theme: theme,
                        height: 220,
                      ),
                      const SizedBox(height: 22),
                      Text(
                        'Welcome to Chaty',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: theme.primaryTextColor,
                          fontSize: 30,
                          fontWeight: FontWeight.w800,
                          letterSpacing: -0.5,
                        ),
                      ),
                      const SizedBox(height: 10),
                      Text(
                        'Private, customizable, and lightning-fast messaging.',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: theme.secondaryTextColor,
                          fontSize: 15,
                          height: 1.4,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              AuthPrimaryButton(
                text: 'Log In',
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(builder: (_) => const LoginScreen()),
                ),
                theme: theme,
              ),
              const SizedBox(height: 14),
              AuthSecondaryButton(
                text: 'Register',
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => const RegisterScreen(),
                  ),
                ),
                theme: theme,
              ),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }
}
