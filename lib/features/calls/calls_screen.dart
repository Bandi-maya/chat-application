import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/call_signaling_service.dart';
import '../../domain/models/other_models.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/widgets/app_avatar.dart';
import 'ongoing_call_screen.dart';

class CallsScreen extends StatelessWidget {
  final ThemeConfig theme;
  final ChatyDataStore dataStore;

  const CallsScreen({super.key, required this.theme, required this.dataStore});

  String _formatCallTime(DateTime dt) {
    final hour = dt.hour.toString().padLeft(2, '0');
    final min = dt.minute.toString().padLeft(2, '0');
    return '$hour:$min';
  }

  Future<void> _startCall(
    BuildContext context, {
    required String remoteUserId,
    required String remoteDisplayName,
    String? remoteAvatarInitials,
    String? remoteAvatarColorHex,
    required bool isVideo,
  }) async {
    final callService = locator<CallSignalingService>();
    try {
      await callService.initiateCall(
        remoteUserId: remoteUserId,
        remoteDisplayName: remoteDisplayName,
        remoteAvatarInitials: remoteAvatarInitials,
        remoteAvatarColorHex: remoteAvatarColorHex,
        isVideo: isVideo,
      );
      if (!context.mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => OngoingCallScreen(theme: theme),
        ),
      );
    } catch (error) {
      if (!context.mounted) return;
      ChatyToast.show(
        context,
        error.toString().replaceFirst('Bad state: ', ''),
        background: context.colors.error,
      );
    }
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
                children: [
                  Expanded(
                    child: Text(
                      'Calls',
                      style: ChatyTypography.headline(colors.foreground),
                    ),
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
                    'Your completed, missed, and declined calls will appear here.',
                iconColor: colors.primary,
                titleColor: colors.foreground,
                messageColor: colors.foregroundSecondary,
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
                    for (final call in calls)
                      Builder(
                        builder: (context) {
                          final currentUserId = dataStore.currentUser.id;
                          final remoteUserId = call.participantIds.firstWhere(
                            (id) => id.isNotEmpty && id != currentUserId,
                            orElse: () => call.callerId,
                          );
                          final contact = dataStore.getUserById(remoteUserId);
                          final isMissed = call.direction == CallDirection.missed;
                          final isVideo = call.type == CallType.video;
                          final canCall = remoteUserId.isNotEmpty &&
                              remoteUserId != currentUserId;

                          Future<void> redial() => _startCall(
                            context,
                            remoteUserId: remoteUserId,
                            remoteDisplayName:
                                contact?.displayName ?? 'Chaty contact',
                            remoteAvatarInitials: contact?.avatarInitials,
                            remoteAvatarColorHex: contact?.avatarColorHex,
                            isVideo: isVideo,
                          );

                          return ChatyListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: ChatySpacing.base,
                              vertical: ChatySpacing.md,
                            ),
                            leading: AppAvatar(
                              initials: contact?.avatarInitials ?? 'U',
                              colorHex: contact?.avatarColorHex,
                              size: 42,
                            ),
                            title: Text(
                              contact?.displayName ?? 'Chaty contact',
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
                                Flexible(
                                  child: Text(
                                    '${_formatCallTime(call.timestamp)} • ${call.durationSeconds > 0 ? '${call.durationSeconds}s' : 'No connection'}',
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: ChatyTypography.caption(
                                      colors.foregroundSecondary,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            trailing: ChatyIconButton(
                              icon: isVideo
                                  ? Icons.videocam_rounded
                                  : Icons.call_rounded,
                              tooltip: isVideo ? 'Video call' : 'Voice call',
                              size: 44,
                              iconSize: 20,
                              backgroundColor: colors.surfaceSecondary,
                              color: colors.primary,
                              onPressed: canCall ? () => redial() : null,
                            ),
                            onTap: canCall ? () => redial() : null,
                          );
                        },
                      ),
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
