import 'package:flutter/material.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';

class ProPricingScreen extends StatefulWidget {
  const ProPricingScreen({super.key});

  @override
  State<ProPricingScreen> createState() => _ProPricingScreenState();
}

class _ProPricingScreenState extends State<ProPricingScreen> {
  bool _isYearly = true;

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;
    final themeData = Theme.of(context);
    final isDark = themeData.brightness == Brightness.dark;

    final borderCol = isDark
        ? const Color(0xFF27272A)
        : const Color(0xFFE4E4E7);

    return ChatyScaffold(
      appBar: const ChatyAppBar(
        title: 'Pricing Plan',
        leading: ChatyBackButton(),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          physics: const BouncingScrollPhysics(),
          padding: const EdgeInsets.symmetric(
            horizontal: ChatySpacing.base,
            vertical: ChatySpacing.md,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: ChatySpacing.sm),

              // Title and Subtitle matching mockup
              Center(
                child: Column(
                  children: [
                    Text(
                      'Easily Build Better Chats\nwith Chaty Pro',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: themeData.colorScheme.onSurface,
                        fontSize: 24,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.5,
                        height: 1.25,
                      ),
                    ),
                    const SizedBox(height: ChatySpacing.sm),
                    Text(
                      'Turn ideas into conversations instantly with AI',
                      textAlign: TextAlign.center,
                      style: ChatyTypography.caption(
                        themeData.colorScheme.onSurface.withValues(alpha: 0.65),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: ChatySpacing.xl),

              // Segmented Switch (Monthly | Yearly [Save 50%])
              Center(
                child: Container(
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    color: isDark
                        ? const Color(0xFF18181B)
                        : const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(ChatyRadius.full),
                    border: Border.all(color: borderCol, width: 1),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // Monthly button
                      GestureDetector(
                        onTap: () => setState(() => _isYearly = false),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 200),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 22,
                            vertical: 10,
                          ),
                          decoration: BoxDecoration(
                            color: !_isYearly
                                ? (isDark
                                      ? Colors.white
                                      : const Color(0xFF09090B))
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(
                              ChatyRadius.full,
                            ),
                          ),
                          child: Text(
                            'Monthly',
                            style: TextStyle(
                              color: !_isYearly
                                  ? (isDark ? Colors.black : Colors.white)
                                  : theme.secondaryTextColor,
                              fontSize: 13.5,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ),

                      // Yearly button
                      GestureDetector(
                        onTap: () => setState(() => _isYearly = true),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 200),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 18,
                            vertical: 10,
                          ),
                          decoration: BoxDecoration(
                            color: _isYearly
                                ? (isDark
                                      ? Colors.white
                                      : const Color(0xFF09090B))
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(
                              ChatyRadius.full,
                            ),
                          ),
                          child: Row(
                            children: [
                              Text(
                                'Yearly',
                                style: TextStyle(
                                  color: _isYearly
                                      ? (isDark ? Colors.black : Colors.white)
                                      : theme.secondaryTextColor,
                                  fontSize: 13.5,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 8,
                                  vertical: 3,
                                ),
                                decoration: BoxDecoration(
                                  color: const Color(0xFFEF4444),
                                  borderRadius: BorderRadius.circular(
                                    ChatyRadius.sm,
                                  ),
                                ),
                                child: const Text(
                                  'Save 50%',
                                  style: TextStyle(
                                    color: Colors.white,
                                    fontSize: 10.5,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: ChatySpacing.xl),

              // Plus Plan Card matching the design tokens
              ChatyCard(
                padding: const EdgeInsets.all(ChatySpacing.lg),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Plus',
                      style: TextStyle(
                        color: themeData.colorScheme.onSurface,
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: ChatySpacing.sm),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.baseline,
                      textBaseline: TextBaseline.alphabetic,
                      children: [
                        Text(
                          _isYearly ? '\$10.25' : '\$20.50',
                          style: TextStyle(
                            color: themeData.colorScheme.onSurface,
                            fontSize: 32,
                            fontWeight: FontWeight.w900,
                            letterSpacing: -0.5,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          _isYearly
                              ? 'Per Month billed annually'
                              : 'Per Month billed monthly',
                          style: ChatyTypography.caption(
                            themeData.colorScheme.onSurface.withValues(
                              alpha: 0.6,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: ChatySpacing.lg),

                    ChatyPrimaryButton(
                      text: 'Get Started',
                      onPressed: () {
                        Navigator.pop(context);
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(
                              'Chaty Pro Plus Plan Activated! 🎉',
                              style: TextStyle(
                                color: theme.onAccentColor,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            backgroundColor: theme.accentColor,
                          ),
                        );
                      },
                    ),
                    const SizedBox(height: ChatySpacing.lg),

                    Text(
                      'Included Features',
                      style: ChatyTypography.caption(
                        themeData.colorScheme.onSurface.withValues(alpha: 0.6),
                      ),
                    ),
                    const SizedBox(height: ChatySpacing.md),

                    _buildCheckItem(
                      themeData,
                      'Smart AI messaging assistance',
                    ),
                    _buildCheckItem(
                      themeData,
                      'Access to Chaty Pro Theme Library',
                    ),
                    _buildCheckItem(themeData, 'Early access to new features'),
                    _buildCheckItem(
                      themeData,
                      'Next-generation privacy & encryption tools',
                    ),
                    _buildCheckItem(
                      themeData,
                      'Unlimited task and backup synchronization',
                    ),
                  ],
                ),
              ),
              const SizedBox(height: ChatySpacing.xxl),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCheckItem(ThemeData themeData, String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6.0),
      child: Row(
        children: [
          Container(
            width: 20,
            height: 20,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: const Color(0xFF10B981).withValues(alpha: 0.15),
            ),
            child: const Icon(
              Icons.check_rounded,
              size: 14,
              color: Color(0xFF10B981),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                color: themeData.colorScheme.onSurface,
                fontSize: 13.5,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

