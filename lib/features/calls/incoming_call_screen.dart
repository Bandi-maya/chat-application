import 'dart:async';

import 'package:flutter/material.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../data/services/call_signaling_service.dart';
import '../../domain/models/call_state.dart';
import '../settings/calls/call_presentation_preferences.dart';
import 'call_screen_template.dart';
import 'ongoing_call_screen.dart';

/// Full-screen incoming call UI that displays caller details,
/// voice/video indication, and prominent Accept / Decline actions.
///
/// The layout is selected through Settings > Calls > Call Screen Style.
/// Templates are original Chaty designs; GB call color mods do not apply
/// here because the incoming screen follows the app theme.
class IncomingCallScreen extends StatelessWidget {
  final ThemeConfig theme;

  const IncomingCallScreen({super.key, required this.theme});

  @override
  Widget build(BuildContext context) {
    final callService = locator<CallSignalingService>();
    final session = callService.currentSession;

    if (session == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (Navigator.of(context).canPop()) Navigator.of(context).pop();
      });
      return const SizedBox.shrink();
    }

    Future<void> decline() async {
      await callService.declineCall();
      if (context.mounted && Navigator.of(context).canPop()) {
        Navigator.of(context).pop();
      }
    }

    Future<void> accept() async {
      await callService.acceptCall();
      if (context.mounted) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => OngoingCallScreen(theme: theme)),
        );
      }
    }

    final store = CallPresentationPreferencesStore.instance;
    unawaited(store.initialize());

    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        final template = CallScreenTemplate.fromName(
          store.value.incomingCallTemplate,
        );
        final data = _IncomingCallData(
          session: session,
          onAccept: accept,
          onDecline: decline,
        );
        return switch (template) {
          CallScreenTemplate.chatyClassic => _ClassicIncomingCall(data: data),
          CallScreenTemplate.sheet => _SheetIncomingCall(data: data),
          CallScreenTemplate.card => _CardIncomingCall(data: data),
          CallScreenTemplate.edge => _EdgeIncomingCall(data: data),
        };
      },
    );
  }
}

class _IncomingCallData {
  const _IncomingCallData({
    required this.session,
    required this.onAccept,
    required this.onDecline,
  });

  final ChatyCallSession session;
  final Future<void> Function() onAccept;
  final Future<void> Function() onDecline;

  String get initials =>
      session.remoteAvatarInitials ??
      (session.remoteDisplayName.isNotEmpty
          ? session.remoteDisplayName.substring(0, 1).toUpperCase()
          : 'U');
}

class _CallTypePill extends StatelessWidget {
  const _CallTypePill({required this.isVideo});

  final bool isVideo;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return Container(
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
            isVideo ? Icons.videocam_rounded : Icons.call_rounded,
            size: 15,
            color: colors.primary,
          ),
          const SizedBox(width: 6),
          Text(
            isVideo ? 'Incoming Video Call' : 'Incoming Voice Call',
            style: TextStyle(
              color: colors.primary,
              fontSize: 13,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _CallerName extends StatelessWidget {
  const _CallerName({required this.session, this.align = TextAlign.center});

  final ChatyCallSession session;
  final TextAlign align;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return Column(
      crossAxisAlignment: align == TextAlign.center
          ? CrossAxisAlignment.center
          : CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          session.remoteDisplayName,
          textAlign: align,
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
          textAlign: align,
          style: TextStyle(
            color: colors.foregroundSecondary,
            fontSize: 14,
            fontWeight: FontWeight.w500,
          ),
        ),
      ],
    );
  }
}

class _RoundAction extends StatelessWidget {
  const _RoundAction({
    required this.label,
    required this.icon,
    required this.color,
    required this.onColor,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final Color color;
  final Color onColor;
  final Future<void> Function() onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => unawaited(onTap()),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 68,
            height: 68,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(
                  color: color.withValues(alpha: 0.35),
                  blurRadius: 16,
                  offset: const Offset(0, 6),
                ),
              ],
            ),
            child: Icon(icon, color: onColor, size: 32),
          ),
          const SizedBox(height: ChatySpacing.sm),
          Text(
            label,
            style: TextStyle(
              color: colors.foregroundSecondary,
              fontSize: 13,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _ActionPair extends StatelessWidget {
  const _ActionPair({required this.data});

  final _IncomingCallData data;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        _RoundAction(
          label: 'Decline',
          icon: Icons.call_end_rounded,
          color: colors.error,
          onColor: colors.onError,
          onTap: data.onDecline,
        ),
        _RoundAction(
          label: 'Answer',
          icon: data.session.isVideo
              ? Icons.videocam_rounded
              : Icons.call_rounded,
          color: colors.success,
          onColor: colors.onSuccess,
          onTap: data.onAccept,
        ),
      ],
    );
  }
}

/// Chaty Classic: centered identity with floating round actions.
class _ClassicIncomingCall extends StatelessWidget {
  const _ClassicIncomingCall({required this.data});

  final _IncomingCallData data;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final session = data.session;
    return Scaffold(
      backgroundColor: colors.background,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: ChatySpacing.xl),
          child: Column(
            children: [
              const SizedBox(height: ChatySpacing.xxl),
              _CallTypePill(isVideo: session.isVideo),
              const SizedBox(height: ChatySpacing.xxl * 1.5),
              Center(
                child: AppAvatar(
                  initials: data.initials,
                  colorHex: session.remoteAvatarColorHex,
                  size: 110,
                ),
              ),
              const SizedBox(height: ChatySpacing.lg),
              _CallerName(session: session),
              const Spacer(),
              Padding(
                padding: const EdgeInsets.only(bottom: ChatySpacing.xxl),
                child: _ActionPair(data: data),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Sheet: identity on the canvas, actions grouped in a bottom sheet.
class _SheetIncomingCall extends StatelessWidget {
  const _SheetIncomingCall({required this.data});

  final _IncomingCallData data;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final session = data.session;
    return Scaffold(
      backgroundColor: colors.background,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            const SizedBox(height: ChatySpacing.xxl),
            _CallTypePill(isVideo: session.isVideo),
            const Spacer(),
            AppAvatar(
              initials: data.initials,
              colorHex: session.remoteAvatarColorHex,
              size: 96,
            ),
            const Spacer(),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(
                ChatySpacing.xl,
                ChatySpacing.xl,
                ChatySpacing.xl,
                ChatySpacing.xxl,
              ),
              decoration: BoxDecoration(
                color: colors.surfaceElevated,
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(32),
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.18),
                    blurRadius: 24,
                    offset: const Offset(0, -6),
                  ),
                ],
              ),
              child: SafeArea(
                top: false,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 36,
                      height: 4,
                      decoration: BoxDecoration(
                        color: colors.foregroundSecondary.withValues(
                          alpha: 0.3,
                        ),
                        borderRadius: BorderRadius.circular(ChatyRadius.full),
                      ),
                    ),
                    const SizedBox(height: ChatySpacing.lg),
                    _CallerName(session: session),
                    const SizedBox(height: ChatySpacing.xl),
                    _ActionPair(data: data),
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

/// Card: the whole incoming UI floats in a single centered card.
class _CardIncomingCall extends StatelessWidget {
  const _CardIncomingCall({required this.data});

  final _IncomingCallData data;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final session = data.session;
    return Scaffold(
      backgroundColor: colors.background,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(ChatySpacing.xl),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(
                horizontal: ChatySpacing.xl,
                vertical: ChatySpacing.xxl,
              ),
              decoration: BoxDecoration(
                color: colors.surfaceElevated,
                borderRadius: BorderRadius.circular(28),
                border: Border.all(
                  color: colors.primary.withValues(alpha: 0.18),
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.22),
                    blurRadius: 32,
                    offset: const Offset(0, 12),
                  ),
                ],
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _CallTypePill(isVideo: session.isVideo),
                  const SizedBox(height: ChatySpacing.xl),
                  AppAvatar(
                    initials: data.initials,
                    colorHex: session.remoteAvatarColorHex,
                    size: 88,
                  ),
                  const SizedBox(height: ChatySpacing.md),
                  _CallerName(session: session),
                  const SizedBox(height: ChatySpacing.xl),
                  _ActionPair(data: data),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Edge: minimal chrome, identity pinned top-left, full-width edge actions.
class _EdgeIncomingCall extends StatelessWidget {
  const _EdgeIncomingCall({required this.data});

  final _IncomingCallData data;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final session = data.session;
    return Scaffold(
      backgroundColor: colors.background,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(ChatySpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  AppAvatar(
                    initials: data.initials,
                    colorHex: session.remoteAvatarColorHex,
                    size: 48,
                  ),
                  const SizedBox(width: ChatySpacing.md),
                  Expanded(
                    child: _CallerName(
                      session: session,
                      align: TextAlign.start,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: ChatySpacing.md),
              _CallTypePill(isVideo: session.isVideo),
              const Spacer(),
              Row(
                children: [
                  Expanded(
                    child: SizedBox(
                      height: 56,
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(
                          backgroundColor: colors.error,
                          foregroundColor: colors.onError,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(
                              ChatyRadius.lg,
                            ),
                          ),
                        ),
                        onPressed: () => unawaited(data.onDecline()),
                        icon: const Icon(Icons.call_end_rounded),
                        label: const Text(
                          'Decline',
                          style: TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: ChatySpacing.md),
                  Expanded(
                    child: SizedBox(
                      height: 56,
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(
                          backgroundColor: colors.success,
                          foregroundColor: colors.onSuccess,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(
                              ChatyRadius.lg,
                            ),
                          ),
                        ),
                        onPressed: () => unawaited(data.onAccept()),
                        icon: Icon(
                          session.isVideo
                              ? Icons.videocam_rounded
                              : Icons.call_rounded,
                        ),
                        label: const Text(
                          'Answer',
                          style: TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
