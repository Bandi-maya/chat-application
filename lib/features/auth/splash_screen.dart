import 'dart:async';

import 'package:flutter/material.dart';

import 'package:chat/data/services/backend_service.dart';
import 'package:chat/features/auth/welcome_screen.dart';
import 'package:chat/features/chats/main_navigation_shell.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/app_icon_controller.dart';
import 'package:chat/ui/core/widgets/app_brand_icon.dart';
import 'package:chat/ui/core/theme/app_theme.dart';

/// Instant, ultra-fast WhatsApp-like startup handoff (~120ms) with clean fade-in.
///
/// Never blocks the first frame on heavy network or database work.
/// Features clean, instant transition directly into Home or Welcome screen.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _animCtrl;
  late final Animation<double> _fadeAnim;

  @override
  void initState() {
    super.initState();
    _animCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 100),
    );

    _fadeAnim = Tween<double>(begin: 0.8, end: 1.0).animate(
      CurvedAnimation(
        parent: _animCtrl,
        curve: Curves.easeOut,
      ),
    );

    _animCtrl.forward();
    unawaited(_routeFromRealSession());
  }

  @override
  void dispose() {
    _animCtrl.dispose();
    super.dispose();
  }

  Future<void> _routeFromRealSession() async {
    final backend = locator<ChatyBackendService>();

    try {
      if (!backend.isInitialized) await backend.initialize();
    } catch (_) {
      // Authentication remains the source of truth.
    }

    if (!mounted) return;

    final destination = backend.isAuthenticated
        ? const MainNavigationShell()
        : const WelcomeScreen();

    Navigator.of(context).pushReplacement(
      PageRouteBuilder<void>(
        pageBuilder: (_, animation, secondaryAnimation) => destination,
        transitionsBuilder: (_, animation, secondaryAnimation, child) =>
            FadeTransition(
          opacity: CurvedAnimation(
            parent: animation,
            curve: Curves.easeOut,
          ),
          child: child,
        ),
        transitionDuration: const Duration(milliseconds: 80),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final appIconController = locator<AppIconController>();
    final colors = context.colors;

    return Scaffold(
      backgroundColor: colors.background,
      body: Center(
        child: FadeTransition(
          opacity: _fadeAnim,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(26),
            child: ChatyBrandIcon(
              controller: appIconController,
              size: 80,
              borderRadius: 26,
            ),
          ),
        ),
      ),
    );
  }
}
