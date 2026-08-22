import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import '../../domain/models/other_models.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../injection/locator.dart';
import '../../data/services/call_signaling_service.dart';
import 'ongoing_call_screen.dart';

class CallsScreen extends StatelessWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;

  const CallsScreen({super.key, required this.theme, required this.dataStore});

  String _formatCallTime(DateTime dt) {
    final hour = dt.hour.toString().padLeft(2, '0');
    final min = dt.minute.toString().padLeft(2, '0');
    return '$hour:$min';
  }

  @override
  Widget build(BuildContext context) {
    final calls = dataStore.calls;
    final colors = context.colors;

    return ChatyScaffold(
      safeAreaTop: true,
      safeAreaBottom: false,
      body: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                ChatySpacing.base,
                ChatySpacing.md,
                ChatySpacing.base,
                ChatySpacing.sm,
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Calls',
                    style: ChatyTypography.headline(colors.foreground),
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (kDebugMode)
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: InkWell(
                            borderRadius: BorderRadius.circular(ChatyRadius.full),
                            onTap: () {
                              final callService = locator<CallSignalingService>();
                              callService.startMockCallForQA(isVideo: true);
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => OngoingCallScreen(theme: theme),
                                ),
                              );
                            },
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: ChatySpacing.sm,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                color: colors.warning.withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(ChatyRadius.full),
                                border: Border.all(color: colors.warning.withValues(alpha: 0.4)),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    Icons.bug_report_rounded,
                                    size: 13,
                                    color: colors.warning,
                                  ),
                                  const SizedBox(width: 4),
                                  Text(
                                    'QA Call Preview',
                                    style: TextStyle(
                                      fontSize: 11.5,
                                      fontWeight: FontWeight.w700,
                                      color: colors.warning,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: ChatySpacing.sm,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: colors.primary.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(ChatyRadius.full),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.lock_rounded,
                              size: 13,
                              color: colors.primary,
                            ),
                            const SizedBox(width: 4),
                            Text(
                              'Encrypted',
                              style: TextStyle(
                                fontSize: 11.5,
                                fontWeight: FontWeight.w600,
                                color: colors.primary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          if (calls.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: ChatyEmptyState(
                icon: Icons.phone_callback_rounded,
                title: 'No recent calls',
                message:
                    'Voice and video calls with your contacts will appear here with peer-to-peer security.',
                iconColor: colors.primary,
                titleColor: colors.foreground,
                messageColor: colors.foregroundSecondary,
                actionLabel: 'Preview Call',
                onAction: () {
                  final callService = locator<CallSignalingService>();
                  callService.startMockCallForQA(isVideo: true);
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => OngoingCallScreen(theme: theme),
                    ),
                  );
                },
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.symmetric(
                horizontal: ChatySpacing.base,
                vertical: ChatySpacing.sm,
              ),
              sliver: SliverToBoxAdapter(
                child: ChatyGroupedSection(
                  children: [
                    for (int i = 0; i < calls.length; i++) ...[
                      Builder(
                        builder: (context) {
                          final call = calls[i];
                          final caller = dataStore.getUserById(call.callerId);
                          final isMissed =
                              call.direction == CallDirection.missed;
                          final isVideo = call.type == CallType.video;

                          return ChatyListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: ChatySpacing.base,
                              vertical: ChatySpacing.md,
                            ),
                            leading: AppAvatar(
                              initials: caller?.avatarInitials ?? 'U',
                              colorHex: caller?.avatarColorHex,
                              size: 42,
                            ),
                            title: Text(
                              caller?.displayName ?? 'Secure Caller',
                              style: TextStyle(
                                color: isMissed
                                    ? colors.error
                                    : colors.foreground,
                                fontWeight: FontWeight.w600,
                                fontSize: 15,
                                letterSpacing: -0.2,
                              ),
                            ),
                            subtitle: Row(
                              children: [
                                Icon(
                                  call.direction == CallDirection.incoming
                                      ? Icons.call_received_rounded
                                      : call.direction == CallDirection.outgoing
                                      ? Icons.call_made_rounded
                                      : Icons.call_missed_rounded,
                                  size: 14,
                                  color: isMissed
                                      ? colors.error
                                      : colors.foregroundSecondary,
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  '${_formatCallTime(call.timestamp)} • ${call.durationSeconds > 0 ? '${call.durationSeconds}s' : 'Missed'}',
                                  style: ChatyTypography.caption(
                                    colors.foregroundSecondary,
                                  ),
                                ),
                              ],
                            ),
                            trailing: ChatyIconButton(
                              icon: isVideo
                                  ? Icons.videocam_rounded
                                  : Icons.call_rounded,
                              size: 38,
                              iconSize: 20,
                              backgroundColor: colors.surfaceSecondary,
                              color: colors.primary,
                              onPressed: () {
                                final callService = locator<CallSignalingService>();
                                callService.initiateCall(
                                  remoteUserId: call.callerId,
                                  remoteDisplayName: caller?.displayName ?? 'Contact',
                                  remoteAvatarInitials: caller?.avatarInitials,
                                  remoteAvatarColorHex: caller?.avatarColorHex,
                                  isVideo: isVideo,
                                );
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (context) => OngoingCallScreen(
                                      theme: theme,
                                    ),
                                  ),
                                );
                              },
                            ),
                            onTap: () {
                              final callService = locator<CallSignalingService>();
                              callService.initiateCall(
                                remoteUserId: call.callerId,
                                remoteDisplayName: caller?.displayName ?? 'Contact',
                                remoteAvatarInitials: caller?.avatarInitials,
                                remoteAvatarColorHex: caller?.avatarColorHex,
                                isVideo: isVideo,
                              );
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (context) => OngoingCallScreen(
                                    theme: theme,
                                  ),
                                ),
                              );
                            },
                          );
                        },
                      ),
                    ],
                  ],
                ),
              ),
            ),
          const SliverToBoxAdapter(child: SizedBox(height: 80)),
        ],
      ),
    );
  }
}
