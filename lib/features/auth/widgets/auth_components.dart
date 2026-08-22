import 'package:flutter/material.dart';
import '../../../../ui/core/design_system/design_system.dart';
import '../../../../injection/locator.dart';
import '../../../../data/services/backend_service.dart';
import '../../../../data/repositories/mock_data_store.dart';
import '../../../../ui/core/persistence/preferences_storage.dart';
import '../../chats/main_navigation_shell.dart';

/// Circular back button as shown on top-left of each screen in the design.
/// Delegates to the shared [ChatyBackButton] so every screen uses the same
/// size (34) and chevron (24) as the Updates screen.
class AuthBackButton extends StatelessWidget {
  final VoidCallback? onPressed;

  const AuthBackButton({super.key, this.onPressed});

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: ChatyBackButton(onPressed: onPressed),
    );
  }
}

/// Rounded text input field matching the design with label & placeholder
class AuthTextField extends StatelessWidget {
  final String label;
  final String hintText;
  final TextEditingController? controller;
  final bool obscureText;
  final Widget? suffixIcon;
  final Widget? prefixIcon;
  final TextInputType keyboardType;
  final TextInputAction textInputAction;
  final String? Function(String?)? validator;
  final ValueChanged<String>? onChanged;
  final ThemeConfig theme;

  const AuthTextField({
    super.key,
    required this.label,
    required this.hintText,
    required this.theme,
    this.controller,
    this.obscureText = false,
    this.suffixIcon,
    this.prefixIcon,
    this.keyboardType = TextInputType.text,
    this.textInputAction = TextInputAction.next,
    this.validator,
    this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: TextStyle(
            color: theme.primaryTextColor,
            fontSize: 14,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 8),
        TextFormField(
          controller: controller,
          obscureText: obscureText,
          keyboardType: keyboardType,
          textInputAction: textInputAction,
          validator: validator,
          onChanged: onChanged,
          style: TextStyle(
            color: theme.primaryTextColor,
            fontSize: 15,
            fontWeight: FontWeight.w500,
          ),
          decoration: InputDecoration(
            hintText: hintText,
            hintStyle: TextStyle(
              color: theme.secondaryTextColor.withValues(alpha: 0.5),
              fontSize: 14,
              fontWeight: FontWeight.normal,
            ),
            filled: true,
            fillColor: context.colors.inputFill,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 18,
              vertical: 16,
            ),
            prefixIcon: prefixIcon,
            suffixIcon: suffixIcon,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(16),
              borderSide: BorderSide(color: context.colors.border, width: 1.0),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(16),
              borderSide: BorderSide(color: context.colors.border, width: 1.0),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(16),
              borderSide: BorderSide(color: theme.accentColor, width: 1.8),
            ),
            errorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(16),
              borderSide: BorderSide(color: theme.dangerColor, width: 1.2),
            ),
            focusedErrorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(16),
              borderSide: BorderSide(color: theme.dangerColor, width: 1.8),
            ),
          ),
        ),
      ],
    );
  }
}

/// Solid Primary Rounded Button matching the mockup
class AuthPrimaryButton extends StatelessWidget {
  final String text;
  final VoidCallback? onPressed;
  final bool isLoading;
  final ThemeConfig theme;

  const AuthPrimaryButton({
    super.key,
    required this.text,
    required this.onPressed,
    required this.theme,
    this.isLoading = false,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 52,
      child: ElevatedButton(
        onPressed: isLoading ? null : onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: theme.accentColor,
          foregroundColor: theme.onAccentColor,
          disabledBackgroundColor: theme.accentColor.withValues(alpha: 0.6),
          elevation: 2,
          shadowColor: theme.accentColor.withValues(alpha: 0.2),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
        child: isLoading
            ? SizedBox(
                width: 22,
                height: 22,
                child: CircularProgressIndicator(
                  strokeWidth: 2.2,
                  valueColor: AlwaysStoppedAnimation<Color>(
                    theme.onAccentColor,
                  ),
                ),
              )
            : Text(
                text,
                style: TextStyle(
                  color: theme.onAccentColor,
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.2,
                ),
              ),
      ),
    );
  }
}

/// Outlined Button matching secondary actions in mockup
class AuthSecondaryButton extends StatelessWidget {
  final String text;
  final VoidCallback? onPressed;
  final ThemeConfig theme;

  const AuthSecondaryButton({
    super.key,
    required this.text,
    required this.onPressed,
    required this.theme,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 52,
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          foregroundColor: theme.primaryTextColor,
          side: BorderSide(color: context.colors.border, width: 1.5),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
        child: Text(
          text,
          style: TextStyle(
            color: theme.primaryTextColor,
            fontSize: 16,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.2,
          ),
        ),
      ),
    );
  }
}

/// Or divider line with text in center
class AuthOrDivider extends StatelessWidget {
  final ThemeConfig theme;

  const AuthOrDivider({super.key, required this.theme});

  @override
  Widget build(BuildContext context) {
    final dividerColor = context.colors.divider;

    return Row(
      children: [
        Expanded(child: Divider(color: dividerColor, thickness: 1)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Text(
            'Or',
            style: TextStyle(
              color: theme.secondaryTextColor,
              fontSize: 13,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
        Expanded(child: Divider(color: dividerColor, thickness: 1)),
      ],
    );
  }
}

/// Social login buttons row (Facebook, Google, Apple) matching mockup
class AuthSocialRow extends StatelessWidget {
  final ThemeConfig theme;

  const AuthSocialRow({super.key, required this.theme});

  Future<void> _onSocialTap(BuildContext context, String provider) async {
    final provLower = provider.toLowerCase();

    String email;
    String displayName;
    if (provLower == 'google') {
      email = 'google.user@gmail.com';
      displayName = 'Google User';
    } else if (provLower == 'apple') {
      email = 'apple.id@icloud.com';
      displayName = 'Apple User';
    } else {
      email = 'facebook.user@fb.com';
      displayName = 'Facebook User';
    }

    // Show sleek authentic sign-in bottom sheet
    showModalBottomSheet(
      context: context,
      backgroundColor: context.colors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(26)),
      ),
      builder: (sheetCtx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 38,
                height: 4,
                decoration: BoxDecoration(
                  color: context.colors.border,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: context.colors.surfaceSecondary,
                    ),
                    child: Icon(
                      provLower == 'google'
                          ? Icons.g_mobiledata_rounded
                          : (provLower == 'apple'
                                ? Icons.apple
                                : Icons.facebook),
                      size: 24,
                      color: theme.primaryTextColor,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Sign in with $provider',
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 17,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Text(
                'Authenticate and connect your $provider account with Chaty end-to-end messaging.',
                style: TextStyle(
                  color: theme.secondaryTextColor,
                  fontSize: 13.5,
                  height: 1.4,
                ),
              ),
              const SizedBox(height: 20),

              // Account Selection Tile
              InkWell(
                onTap: () async {
                  Navigator.pop(sheetCtx);
                  try {
                    final backend = locator<ChatyBackendService>();
                    final user = await backend.loginWithSocial(
                      provider: provider,
                      email: email,
                      displayName: displayName,
                    );
                    await LocalPreferencesStorage.setStoredUserId(user.id);
                    locator<MockDataStore>().switchDemoAccount(user);

                    if (!context.mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text(
                          'Signed in successfully as $displayName! 🎉',
                        ),
                        backgroundColor: context.colors.success,
                      ),
                    );

                    Navigator.of(context).pushAndRemoveUntil(
                      MaterialPageRoute(
                        builder: (_) => const MainNavigationShell(),
                      ),
                      (route) => false,
                    );
                  } catch (e) {
                    if (!context.mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('Sign in error: $e'),
                        backgroundColor: theme.dangerColor,
                      ),
                    );
                  }
                },
                borderRadius: BorderRadius.circular(16),
                child: Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: context.colors.surfaceSecondary,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: context.colors.border),
                  ),
                  child: Row(
                    children: [
                      CircleAvatar(
                        backgroundColor: theme.accentColor,
                        radius: 20,
                        child: Text(
                          displayName.substring(0, 1),
                          style: TextStyle(
                            color: theme.onAccentColor,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              displayName,
                              style: TextStyle(
                                color: theme.primaryTextColor,
                                fontSize: 14.5,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            Text(
                              email,
                              style: TextStyle(
                                color: theme.secondaryTextColor,
                                fontSize: 12.5,
                              ),
                            ),
                          ],
                        ),
                      ),
                      Icon(
                        Icons.arrow_forward_ios_rounded,
                        size: 14,
                        color: theme.secondaryTextColor,
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 14),

              // Direct Continue Button
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton(
                  onPressed: () async {
                    Navigator.pop(sheetCtx);
                    try {
                      final backend = locator<ChatyBackendService>();
                      final user = await backend.loginWithSocial(
                        provider: provider,
                        email: email,
                        displayName: displayName,
                      );
                      await LocalPreferencesStorage.setStoredUserId(user.id);
                      locator<MockDataStore>().switchDemoAccount(user);

                      if (!context.mounted) return;
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Connected with $provider!'),
                          backgroundColor: context.colors.success,
                        ),
                      );

                      Navigator.of(context).pushAndRemoveUntil(
                        MaterialPageRoute(
                          builder: (_) => const MainNavigationShell(),
                        ),
                        (route) => false,
                      );
                    } catch (e) {
                      if (!context.mounted) return;
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Authentication failed: $e'),
                          backgroundColor: theme.dangerColor,
                        ),
                      );
                    }
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: theme.accentColor,
                    foregroundColor: theme.onAccentColor,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                  ),
                  child: Text(
                    'Continue as $displayName',
                    style: TextStyle(
                      color: theme.onAccentColor,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cardBg = context.colors.surfaceSecondary;
    final borderColor = context.colors.border;

    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        _buildSocialBtn(
          context,
          icon: Icons.facebook,
          iconColor: context.colors.primary,
          name: 'Facebook',
          cardBg: cardBg,
          borderColor: borderColor,
        ),
        const SizedBox(width: 18),
        _buildSocialBtn(
          context,
          customWidget: _buildGoogleLogo(context),
          name: 'Google',
          cardBg: cardBg,
          borderColor: borderColor,
        ),
        const SizedBox(width: 18),
        _buildSocialBtn(
          context,
          icon: Icons.apple,
          iconColor: context.colors.foreground,
          name: 'Apple',
          cardBg: cardBg,
          borderColor: borderColor,
        ),
      ],
    );
  }

  Widget _buildSocialBtn(
    BuildContext context, {
    IconData? icon,
    Widget? customWidget,
    Color? iconColor,
    required String name,
    required Color cardBg,
    required Color borderColor,
  }) {
    return InkWell(
      onTap: () => _onSocialTap(context, name),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: 64,
        height: 48,
        decoration: BoxDecoration(
          color: cardBg,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: borderColor, width: 1.2),
        ),
        alignment: Alignment.center,
        child: customWidget ?? Icon(icon, size: 26, color: iconColor),
      ),
    );
  }

  Widget _buildGoogleLogo(BuildContext context) {
    return Container(
      width: 22,
      height: 22,
      decoration: const BoxDecoration(shape: BoxShape.circle),
      child: Center(
        child: Text(
          'G',
          style: TextStyle(
            color: context.colors.primary,
            fontSize: 20,
            fontWeight: FontWeight.w900,
            fontFamily: 'Roboto',
          ),
        ),
      ),
    );
  }
}

/// Custom Vector Illustrations for Auth screens (Hero scene, Forgot password, OTP shield)
class AuthIllustration extends StatelessWidget {
  final String type; // 'welcome', 'forgot_password', 'otp'
  final ThemeConfig theme;
  final double height;

  const AuthIllustration({
    super.key,
    required this.type,
    required this.theme,
    this.height = 180,
  });

  @override
  Widget build(BuildContext context) {
    IconData mainIcon;
    String badgeText;
    Color iconColor = theme.accentColor;

    switch (type) {
      case 'forgot_password':
        mainIcon = Icons.lock_reset_rounded;
        badgeText = 'Reset Access';
        break;
      case 'otp':
        mainIcon = Icons.verified_user_rounded;
        badgeText = 'Secure OTP';
        break;
      case 'welcome':
      default:
        mainIcon = Icons.forum_rounded;
        badgeText = 'Fast & Private';
        break;
    }

    return Container(
      height: height,
      width: double.infinity,
      margin: const EdgeInsets.symmetric(vertical: 12),
      decoration: BoxDecoration(
        color: context.colors.surfaceSecondary.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: theme.accentColor.withValues(alpha: 0.15),
          width: 1.5,
        ),
      ),

      child: Stack(
        alignment: Alignment.center,
        children: [
          // Background soft gradient circles
          Positioned(
            right: -20,
            top: -20,
            child: Container(
              width: 100,
              height: 100,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: theme.accentColor.withValues(alpha: 0.08),
              ),
            ),
          ),
          Positioned(
            left: -15,
            bottom: -15,
            child: Container(
              width: 90,
              height: 90,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: theme.accentColor.withValues(alpha: 0.08),
              ),
            ),
          ),
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 76,
                height: 76,
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                ),
                child: Center(
                  child: Icon(mainIcon, size: 40, color: iconColor),
                ),
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 4,
                ),
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  badgeText,
                  style: TextStyle(
                    color: theme.accentColor,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
