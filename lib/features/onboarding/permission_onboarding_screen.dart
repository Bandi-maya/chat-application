import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../features/chats/main_navigation_shell.dart';
import '../../ui/core/design_system/chaty_haptics.dart';
import '../../ui/core/design_system/components/chaty_kit.dart';
import '../../ui/core/theme/app_theme.dart';

/// Contextual step-by-step permission onboarding after successful authentication.
///
/// Clearly explains rationale for Notifications, Camera, Microphone, and Photos
/// with rich UI cards and clear context before invoking OS requests.
class PermissionOnboardingScreen extends StatefulWidget {
  const PermissionOnboardingScreen({super.key});

  @override
  State<PermissionOnboardingScreen> createState() =>
      _PermissionOnboardingScreenState();
}

class _PermissionOnboardingScreenState
    extends State<PermissionOnboardingScreen> {
  int _currentStep = 0;
  bool _isRequesting = false;

  final List<_PermissionStepData> _steps = const [
    _PermissionStepData(
      permission: Permission.notification,
      title: 'Stay in the loop',
      description:
          'Receive instant message notifications and live call alerts so you never miss an important conversation.',
      icon: Icons.notifications_active_rounded,
      accentColor: Color(0xFF10B981),
    ),
    _PermissionStepData(
      permission: Permission.camera,
      title: 'Share your perspective',
      description:
          'Take photos, capture stories with AR filters, and start HD video calls with friends.',
      icon: Icons.photo_camera_rounded,
      accentColor: Color(0xFF6366F1),
    ),
    _PermissionStepData(
      permission: Permission.microphone,
      title: 'Clear voice & audio',
      description:
          'Send voice notes, record audio updates, and enjoy crystal-clear voice and video calls.',
      icon: Icons.mic_rounded,
      accentColor: Color(0xFFF59E0B),
    ),
    _PermissionStepData(
      permission: Permission.photos,
      title: 'Media sharing',
      description:
          'Send photos, videos, and documents directly from your gallery with ease.',
      icon: Icons.photo_library_rounded,
      accentColor: Color(0xFFEC4899),
    ),
  ];

  Future<void> _handlePermissionAction(Permission permission) async {
    setState(() => _isRequesting = true);
    ChatyHaptics.selection();
    try {
      final status = await permission.request();
      if (status.isPermanentlyDenied) {
        if (!mounted) return;
        final open = await ChatyConfirmDialog.show(
          context,
          title: 'Permission Required',
          message:
              'This feature was disabled in settings. Would you like to open settings to enable it?',
          confirmLabel: 'Open Settings',
        );
        if (open == true) {
          await openAppSettings();
        }
      }
    } finally {
      if (mounted) {
        setState(() => _isRequesting = false);
        _advance();
      }
    }
  }

  void _advance() {
    if (_currentStep < _steps.length - 1) {
      setState(() => _currentStep++);
    } else {
      _finishOnboarding();
    }
  }

  void _finishOnboarding() {
    ChatyHaptics.success();
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const MainNavigationShell()),
      (route) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final step = _steps[_currentStep];

    return Scaffold(
      backgroundColor: colors.background,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Progress indicator dots
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(_steps.length, (index) {
                  final active = index == _currentStep;
                  return AnimatedContainer(
                    duration: const Duration(milliseconds: 240),
                    margin: const EdgeInsets.symmetric(horizontal: 3),
                    width: active ? 24 : 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: active
                          ? colors.primary
                          : colors.primary.withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(4),
                    ),
                  );
                }),
              ),
              const Spacer(),

              // Step Icon
              Center(
                child: Container(
                  width: 96,
                  height: 96,
                  decoration: BoxDecoration(
                    color: step.accentColor.withValues(alpha: 0.12),
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: step.accentColor.withValues(alpha: 0.3),
                      width: 1.5,
                    ),
                  ),
                  child: Icon(step.icon, color: step.accentColor, size: 44),
                ),
              ),
              const SizedBox(height: 32),

              // Title
              Text(
                step.title,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 26,
                  fontWeight: FontWeight.w800,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: 12),

              // Description
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text(
                  step.description,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: colors.foregroundSecondary,
                    fontSize: 15,
                    height: 1.45,
                  ),
                ),
              ),
              const Spacer(),

              // Allow button
              FilledButton.icon(
                style: FilledButton.styleFrom(
                  backgroundColor: colors.primary,
                  foregroundColor: Colors.white,
                  minimumSize: const Size.fromHeight(52),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                ),
                icon: const Icon(Icons.check_circle_outline_rounded, size: 20),
                label: Text(
                  _isRequesting ? 'Requesting…' : 'Allow Permission',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                onPressed: _isRequesting
                    ? null
                    : () => _handlePermissionAction(step.permission),
              ),
              const SizedBox(height: 12),

              // Skip / Not now button
              Center(
                child: TextButton(
                  onPressed: _isRequesting ? null : _advance,
                  child: Text(
                    _currentStep == _steps.length - 1 ? 'Finish' : 'Not Now',
                    style: TextStyle(
                      color: colors.foregroundTertiary,
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }
}

class _PermissionStepData {
  final Permission permission;
  final String title;
  final String description;
  final IconData icon;
  final Color accentColor;

  const _PermissionStepData({
    required this.permission,
    required this.title,
    required this.description,
    required this.icon,
    required this.accentColor,
  });
}
