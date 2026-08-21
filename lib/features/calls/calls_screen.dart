import 'package:flutter/material.dart';
import '../../domain/models/other_models.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/theme/theme_config.dart';
import 'mock_call_screen.dart';

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
    final themeData = Theme.of(context);
    final isDark = themeData.brightness == Brightness.dark;

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
                    style: ChatyTypography.headline(
                      themeData.colorScheme.onSurface,
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: ChatySpacing.sm,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: themeData.colorScheme.primary.withValues(
                        alpha: 0.12,
                      ),
                      borderRadius: BorderRadius.circular(ChatyRadius.full),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          Icons.lock_rounded,
                          size: 13,
                          color: themeData.colorScheme.primary,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          'Encrypted',
                          style: TextStyle(
                            fontSize: 11.5,
                            fontWeight: FontWeight.w600,
                            color: themeData.colorScheme.primary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (calls.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(ChatySpacing.xl),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Container(
                        padding: const EdgeInsets.all(ChatySpacing.lg),
                        decoration: BoxDecoration(
                          color: themeData.colorScheme.primary.withValues(
                            alpha: 0.08,
                          ),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.phone_callback_rounded,
                          size: 48,
                          color: themeData.colorScheme.primary,
                        ),
                      ),
                      const SizedBox(height: ChatySpacing.base),
                      Text(
                        'No recent calls',
                        style: ChatyTypography.title(
                          themeData.colorScheme.onSurface,
                        ),
                      ),
                      const SizedBox(height: ChatySpacing.xs),
                      Text(
                        'Voice and video calls with your contacts will appear here with peer-to-peer security.',
                        textAlign: TextAlign.center,
                        style: ChatyTypography.caption(
                          themeData.colorScheme.onSurface.withValues(
                            alpha: 0.6,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
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
                                    ? const Color(0xFFEF4444)
                                    : themeData.colorScheme.onSurface,
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
                                      ? const Color(0xFFEF4444)
                                      : themeData.colorScheme.onSurface
                                            .withValues(alpha: 0.55),
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  '${_formatCallTime(call.timestamp)} • ${call.durationSeconds > 0 ? '${call.durationSeconds}s' : 'Missed'}',
                                  style: ChatyTypography.caption(
                                    themeData.colorScheme.onSurface.withValues(
                                      alpha: 0.6,
                                    ),
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
                              backgroundColor:
                                  isDark
                                      ? const Color(0xFF27272A)
                                      : const Color(0xFFF4F4F5),
                              color: themeData.colorScheme.primary,
                              onPressed: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (context) => MockCallScreen(
                                      theme: theme,
                                      title: caller?.displayName ?? 'Contact',
                                      isVideo: isVideo,
                                    ),
                                  ),
                                );
                              },
                            ),
                            onTap: () {
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (context) => MockCallScreen(
                                    theme: theme,
                                    title: caller?.displayName ?? 'Contact',
                                    isVideo: isVideo,
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
