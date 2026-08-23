import 'dart:ui';

import 'package:flutter/material.dart';

/// Compact global call surface shown above any Chaty screen while a call is
/// active. Android's foreground notification remains the system-level surface;
/// this is the in-app counterpart so users never have to navigate back to the
/// call screen just to toggle audio route or hang up.
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
                              width: 39,
                              height: 39,
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
                  IconButton(
                    tooltip: isSpeaker ? 'Use earpiece' : 'Use speaker',
                    onPressed: onToggleSpeaker,
                    icon: Icon(
                      isSpeaker
                          ? Icons.volume_up_rounded
                          : Icons.hearing_rounded,
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: IconButton.filled(
                      tooltip: 'Hang up',
                      style: IconButton.styleFrom(
                        backgroundColor: scheme.error,
                        foregroundColor: scheme.onError,
                      ),
                      onPressed: onHangUp,
                      icon: const Icon(Icons.call_end_rounded),
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
