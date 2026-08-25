import 'package:flutter/material.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../data/services/call_signaling_service.dart';
import 'ongoing_call_screen.dart';

/// Full-screen incoming call UI that displays caller details,
/// voice/video indication, and prominent Accept / Decline actions.
class IncomingCallScreen extends StatelessWidget {
  final ThemeConfig theme;

  const IncomingCallScreen({super.key, required this.theme});

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final callService = locator<CallSignalingService>();
    final session = callService.currentSession;

    if (session == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (Navigator.of(context).canPop()) Navigator.of(context).pop();
      });
      return const SizedBox.shrink();
    }

    return Scaffold(
      backgroundColor: colors.background,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: ChatySpacing.xl),
          child: Column(
            children: [
              const SizedBox(height: ChatySpacing.xxl),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: ChatySpacing.md,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: colors.primary.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(ChatyRadius.full),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      session.isVideo
                          ? Icons.videocam_rounded
                          : Icons.call_rounded,
                      size: 15,
                      color: colors.primary,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      session.isVideo
                          ? 'Incoming Video Call'
                          : 'Incoming Voice Call',
                      style: TextStyle(
                        color: colors.primary,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: ChatySpacing.xxl * 1.5),

              // Caller Avatar with pulsing visual indicator
              Center(
                child: AppAvatar(
                  initials:
                      session.remoteAvatarInitials ??
                      (session.remoteDisplayName.isNotEmpty
                          ? session.remoteDisplayName
                                .substring(0, 1)
                                .toUpperCase()
                          : 'U'),
                  colorHex: session.remoteAvatarColorHex,
                  size: 110,
                ),
              ),
              const SizedBox(height: ChatySpacing.lg),

              // Caller Display Name
              Text(
                session.remoteDisplayName,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 26,
                  fontWeight: FontWeight.w800,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: ChatySpacing.xs),
              Text(
                'Chaty Secure Direct Call',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: colors.foregroundSecondary,
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                ),
              ),

              const Spacer(),

              // Action buttons (Decline & Accept)
              Padding(
                padding: const EdgeInsets.only(bottom: ChatySpacing.xxl),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    // Decline
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        GestureDetector(
                          onTap: () async {
                            await callService.declineCall();
                            if (context.mounted &&
                                Navigator.of(context).canPop()) {
                              Navigator.of(context).pop();
                            }
                          },
                          child: Container(
                            width: 68,
                            height: 68,
                            decoration: BoxDecoration(
                              color: colors.error,
                              shape: BoxShape.circle,
                              boxShadow: [
                                BoxShadow(
                                  color: colors.error.withValues(alpha: 0.35),
                                  blurRadius: 16,
                                  offset: const Offset(0, 6),
                                ),
                              ],
                            ),
                            child: Icon(
                              Icons.call_end_rounded,
                              color: colors.onError,
                              size: 32,
                            ),
                          ),
                        ),
                        const SizedBox(height: ChatySpacing.sm),
                        Text(
                          'Decline',
                          style: TextStyle(
                            color: colors.foregroundSecondary,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),

                    // Accept
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        GestureDetector(
                          onTap: () async {
                            await callService.acceptCall();
                            if (context.mounted) {
                              Navigator.of(context).pushReplacement(
                                MaterialPageRoute(
                                  builder: (_) =>
                                      OngoingCallScreen(theme: theme),
                                ),
                              );
                            }
                          },
                          child: Container(
                            width: 68,
                            height: 68,
                            decoration: BoxDecoration(
                              color: colors.success,
                              shape: BoxShape.circle,
                              boxShadow: [
                                BoxShadow(
                                  color: colors.success.withValues(alpha: 0.35),
                                  blurRadius: 16,
                                  offset: const Offset(0, 6),
                                ),
                              ],
                            ),
                            child: Icon(
                              session.isVideo
                                  ? Icons.videocam_rounded
                                  : Icons.call_rounded,
                              color: colors.onSuccess,
                              size: 32,
                            ),
                          ),
                        ),
                        const SizedBox(height: ChatySpacing.sm),
                        Text(
                          'Answer',
                          style: TextStyle(
                            color: colors.foregroundSecondary,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
