import 'dart:async';

import 'package:flutter/material.dart';

import 'package:chat/data/services/backend_service.dart';
import 'package:chat/features/auth/welcome_screen.dart';
import 'package:chat/features/chats/main_navigation_shell.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/app_icon_controller.dart';
import 'package:chat/ui/core/widgets/app_brand_icon.dart';
import 'package:chat/ui/core/theme/app_theme.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    unawaited(_routeFromRealSession());
  }

  Future<void> _routeFromRealSession() async {
    final backend = locator<ChatyBackendService>();
    try {
      if (!backend.isInitialized) await backend.initialize();
    } catch (_) {
      // Authentication remains the source of truth. If profile hydration fails,
      // the welcome/auth flow can recover instead of leaving a blank startup.
    }

    if (!mounted) return;

    final destination = backend.isAuthenticated
        ? const MainNavigationShell()
        : const WelcomeScreen();
    Navigator.of(context).pushReplacement(
      PageRouteBuilder<void>(
        pageBuilder: (_, animation, secondaryAnimation) => destination,
        transitionsBuilder: (_, animation, secondaryAnimation, child) =>
            FadeTransition(opacity: animation, child: child),
        transitionDuration: const Duration(milliseconds: 150),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final appIconController = locator<AppIconController>();
    return Scaffold(
      backgroundColor: context.colors.background,
      body: Center(
        child: ClipOval(
          child: ChatyBrandIcon(
            controller: appIconController,
            size: 100,
            borderRadius: 50,
          ),
        ),
      ),
    );
  }
}
