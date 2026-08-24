import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'package:chat/data/services/backend_service.dart';
import 'package:chat/features/auth/welcome_screen.dart';
import 'package:chat/features/chats/main_navigation_shell.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/app_icon_controller.dart';
import 'package:chat/ui/core/widgets/app_brand_icon.dart';
import 'package:chat/ui/core/theme/app_theme.dart';

/// Ultra-fast ~380ms branded startup motion that smoothly hands off to Home.
///
/// Never blocks the first frame on heavy network or database work.
/// Features a crisp dimensional message brand mark with subtle scale & depth settle.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _animCtrl;
  late final Animation<double> _scaleAnim;
  late final Animation<double> _rotateAnim;
  late final Animation<double> _fadeAnim;

  @override
  void initState() {
    super.initState();
    _animCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 380),
    );

    _scaleAnim = TweenSequence<double>([
      TweenSequenceItem(
        tween: Tween<double>(begin: 0.90, end: 1.05)
            .chain(CurveTween(curve: Curves.easeOutCubic)),
        weight: 60,
      ),
      TweenSequenceItem(
        tween: Tween<double>(begin: 1.05, end: 1.0)
            .chain(CurveTween(curve: Curves.easeInOut)),
        weight: 40,
      ),
    ]).animate(_animCtrl);

    _rotateAnim = Tween<double>(begin: -0.06, end: 0.0).animate(
      CurvedAnimation(
        parent: _animCtrl,
        curve: const Interval(0.0, 0.75, curve: Curves.easeOutBack),
      ),
    );

    _fadeAnim = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(
        parent: _animCtrl,
        curve: const Interval(0.0, 0.45, curve: Curves.easeIn),
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
    final start = DateTime.now();

    try {
      if (!backend.isInitialized) await backend.initialize();
    } catch (_) {
      // Authentication remains the source of truth.
    }

    final elapsed = DateTime.now().difference(start).inMilliseconds;
    final remaining = 380 - elapsed;
    if (remaining > 0) {
      await Future<void>.delayed(Duration(milliseconds: remaining));
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
            curve: Curves.easeInOut,
          ),
          child: child,
        ),
        transitionDuration: const Duration(milliseconds: 140),
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
        child: AnimatedBuilder(
          animation: _animCtrl,
          builder: (context, _) {
            return FadeTransition(
              opacity: _fadeAnim,
              child: Transform.rotate(
                angle: _rotateAnim.value * math.pi,
                child: Transform.scale(
                  scale: _scaleAnim.value,
                  child: Container(
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(
                          color: colors.primary.withValues(alpha: 0.25),
                          blurRadius: 36,
                          offset: const Offset(0, 10),
                        ),
                      ],
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(26),
                      child: ChatyBrandIcon(
                        controller: appIconController,
                        size: 88,
                        borderRadius: 26,
                      ),
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
