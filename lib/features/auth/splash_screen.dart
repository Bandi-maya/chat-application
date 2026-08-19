import 'dart:async';

import 'package:flutter/material.dart';

import 'package:chat/data/services/chaty_backend_service.dart';
import 'package:chat/features/auth/welcome_screen.dart';
import 'package:chat/features/chats/main_navigation_shell.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/app_icon_controller.dart';
import 'package:chat/ui/core/theme/theme_controller.dart';
import 'package:chat/ui/core/widgets/chaty_brand_icon.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _animCtrl;
  late final Animation<double> _scaleAnim;
  late final Animation<double> _opacityAnim;

  @override
  void initState() {
    super.initState();
    _animCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    );
    _scaleAnim = CurvedAnimation(parent: _animCtrl, curve: Curves.easeOutBack);
    _opacityAnim = CurvedAnimation(parent: _animCtrl, curve: Curves.easeInOut);
    _animCtrl.forward();
    unawaited(_routeFromRealSession());
  }

  Future<void> _routeFromRealSession() async {
    final started = DateTime.now();
    final backend = locator<ChatyBackendService>();
    try {
      if (!backend.isInitialized) await backend.initialize();
    } catch (_) {
      // Authentication remains the source of truth. If profile hydration fails,
      // the welcome/auth flow can recover instead of leaving a blank startup.
    }

    final elapsed = DateTime.now().difference(started);
    const minimum = Duration(milliseconds: 900);
    if (elapsed < minimum) await Future<void>.delayed(minimum - elapsed);
    if (!mounted) return;

    final destination = backend.isAuthenticated
        ? const MainNavigationShell()
        : const WelcomeScreen();
    Navigator.of(context).pushReplacement(
      PageRouteBuilder<void>(
        pageBuilder: (_, animation, secondaryAnimation) => destination,
        transitionsBuilder: (_, animation, secondaryAnimation, child) =>
            FadeTransition(opacity: animation, child: child),
        transitionDuration: const Duration(milliseconds: 280),
      ),
    );
  }

  @override
  void dispose() {
    _animCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;
    final appIconController = locator<AppIconController>();

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: Stack(
          children: [
            Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  ScaleTransition(
                    scale: _scaleAnim,
                    child: Container(
                      width: 124,
                      height: 124,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(34),
                        boxShadow: [
                          BoxShadow(
                            color: theme.accentColor.withValues(alpha: 0.35),
                            blurRadius: 36,
                            spreadRadius: 2,
                            offset: const Offset(0, 12),
                          ),
                        ],
                      ),
                      child: ChatyBrandIcon(
                        controller: appIconController,
                        size: 124,
                        borderRadius: 34,
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),
                  FadeTransition(
                    opacity: _opacityAnim,
                    child: Text(
                      'Chaty',
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 34,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.5,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 36,
              child: FadeTransition(
                opacity: _opacityAnim,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(
                        strokeWidth: 2.5,
                        valueColor: AlwaysStoppedAnimation<Color>(theme.accentColor),
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      'from',
                      style: TextStyle(
                        color: theme.secondaryTextColor.withValues(alpha: 0.6),
                        fontSize: 12,
                        fontWeight: FontWeight.w500,
                        letterSpacing: 1.2,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'LOGY BYTE',
                      style: TextStyle(
                        color: theme.accentColor,
                        fontSize: 14,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 2,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
