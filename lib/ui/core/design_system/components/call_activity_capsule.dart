import 'dart:ui';

import 'package:flutter/material.dart';

/// Compact global call surface shown above any Chaty screen while a call is
/// active. Android's foreground notification remains the system-level surface;
/// this is the in-app counterpart so users never have to navigate back to the
/// call screen just to toggle audio route or hang up.
///
/// IMPORTANT: this widget is intentionally independent from [Tooltip]. Global
/// activity surfaces can be hosted at, or temporarily outside, a Navigator
/// overlay while routes are changing. RawTooltip requires an Overlay and used
/// to crash the app in that state. Accessibility is provided through Semantics
/// and minimum 48dp targets instead.
class ChatyCallActivityCapsule extends StatelessWidget {
  const ChatyCallActivityCapsule({
    super.key,
    required this.contactName,
    required this.status,
    required this.isVideo,
    required this.isSpeaker,
    required this.onOpen,
    required this.onToggleSpeaker,
    required this.onHangUp,
  });

  final String contactName;
  final String status;
  final bool isVideo;
  final bool isSpeaker;
  final VoidCallback onOpen;
  final VoidCallback onToggleSpeaker;
  final VoidCallback onHangUp;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SafeArea(
      bottom: false,
      minimum: const EdgeInsets.fromLTRB(14, 8, 14, 0),
      child: Material(
        color: Colors.transparent,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(24),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: Color.alphaBlend(
                  scheme.primary.withValues(alpha: .10),
                  scheme.surface.withValues(alpha: .96),
                ),
                borderRadius: BorderRadius.circular(24),
                border: Border.all(
                  color: scheme.primary.withValues(alpha: .20),
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: .14),
                    blurRadius: 24,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Semantics(
                      button: true,
                      label: 'Return to call with $contactName. $status',
                      child: InkWell(
                        onTap: onOpen,
                        borderRadius: const BorderRadius.horizontal(
                          left: Radius.circular(24),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(13, 9, 8, 9),
                          child: Row(
                            children: [
                              Container(
                                width: 40,
                                height: 40,
                                decoration: BoxDecoration(
                                  color: scheme.primary.withValues(alpha: .14),
                                  borderRadius: BorderRadius.circular(14),
                                ),
                                child: Icon(
                                  isVideo
                                      ? Icons.videocam_rounded
                                      : Icons.call_rounded,
                                  color: scheme.primary,
                                  size: 20,
                                ),
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      contactName,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        color: scheme.onSurface,
                                        fontWeight: FontWeight.w800,
                                        fontSize: 13.5,
                                      ),
                                    ),
                                    const SizedBox(height: 1),
                                    Text(
                                      status,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        color: scheme.onSurfaceVariant,
                                        fontSize: 10.5,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                  _OverlaySafeCallAction(
                    semanticsLabel: isSpeaker
                        ? 'Use earpiece'
                        : 'Use speaker',
                    icon: isSpeaker
                        ? Icons.volume_up_rounded
                        : Icons.hearing_rounded,
                    foregroundColor: scheme.onSurface,
                    onPressed: onToggleSpeaker,
                  ),
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: _OverlaySafeCallAction(
                      semanticsLabel: 'Hang up',
                      icon: Icons.call_end_rounded,
                      foregroundColor: scheme.onError,
                      backgroundColor: scheme.error,
                      onPressed: onHangUp,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _OverlaySafeCallAction extends StatelessWidget {
  const _OverlaySafeCallAction({
    required this.semanticsLabel,
    required this.icon,
    required this.foregroundColor,
    required this.onPressed,
    this.backgroundColor,
  });

  final String semanticsLabel;
  final IconData icon;
  final Color foregroundColor;
  final Color? backgroundColor;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: semanticsLabel,
      child: ConstrainedBox(
        constraints: const BoxConstraints.tightFor(width: 48, height: 48),
        child: Material(
          color: backgroundColor ?? Colors.transparent,
          shape: const CircleBorder(),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            customBorder: const CircleBorder(),
            onTap: onPressed,
            child: Center(
              child: Icon(icon, color: foregroundColor, size: 22),
            ),
          ),
        ),
      ),
    );
  }
}
