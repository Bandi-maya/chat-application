import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../data/services/status_service.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/design_system/design_system.dart';

class UpdatesScreen extends StatefulWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;
  final ChatyPreferencesController preferencesController;

  const UpdatesScreen({
    super.key,
    required this.theme,
    required this.dataStore,
    required this.preferencesController,
  });

  @override
  State<UpdatesScreen> createState() => _UpdatesScreenState();
}

class _UpdatesScreenState extends State<UpdatesScreen> {
  final StatusService _statusService = StatusService();

  Future<void> _openComposer() async {
    final themeData = Theme.of(context);
    final textController = TextEditingController();
    String? busyType;

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        return StatefulBuilder(
          builder: (context, setSheetState) {
            Future<void> publishMedia(String type) async {
              setSheetState(() => busyType = type);
              try {
                final status = await _statusService.pickAndPublish(
                  mediaType: type,
                  text: textController.text,
                );
                if (status != null && sheetContext.mounted) {
                  Navigator.of(sheetContext).pop();
                }
              } catch (error) {
                if (sheetContext.mounted) {
                  ScaffoldMessenger.of(sheetContext).showSnackBar(
                    SnackBar(
                      content: Text(
                        error.toString().replaceFirst('Exception: ', ''),
                      ),
                    ),
                  );
                }
              } finally {
                if (sheetContext.mounted) setSheetState(() => busyType = null);
              }
            }

            Future<void> publishText() async {
              setSheetState(() => busyType = 'text');
              try {
                await _statusService.publishText(textController.text);
                if (sheetContext.mounted) Navigator.of(sheetContext).pop();
              } catch (error) {
                if (sheetContext.mounted) {
                  ScaffoldMessenger.of(sheetContext).showSnackBar(
                    SnackBar(
                      content: Text(
                        error.toString().replaceFirst('Exception: ', ''),
                      ),
                    ),
                  );
                }
              } finally {
                if (sheetContext.mounted) setSheetState(() => busyType = null);
              }
            }

            return Container(
              padding: EdgeInsets.only(
                left: ChatySpacing.lg,
                right: ChatySpacing.lg,
                top: ChatySpacing.md,
                bottom:
                    MediaQuery.of(sheetContext).viewInsets.bottom +
                    ChatySpacing.lg,
              ),
              decoration: BoxDecoration(
                color: sheetContext.colors.surface,
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(ChatyRadius.sheet),
                ),
                border: Border(
                  top: BorderSide(
                    color: sheetContext.colors.borderSubtle,
                    width: 1.0,
                  ),
                ),
              ),
              child: SafeArea(
                top: false,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Center(
                      child: Container(
                        width: 36,
                        height: 4,
                        decoration: BoxDecoration(
                          color: themeData.colorScheme.onSurface.withValues(
                            alpha: 0.2,
                          ),
                          borderRadius: BorderRadius.circular(ChatyRadius.full),
                        ),
                      ),
                    ),
                    const SizedBox(height: ChatySpacing.base),
                    Text(
                      'New Update',
                      style: ChatyTypography.headline(
                        themeData.colorScheme.onSurface,
                      ),
                    ),
                    const SizedBox(height: ChatySpacing.xs),
                    Text(
                      'Updates expire automatically after 24 hours.',
                      style: ChatyTypography.caption(
                        themeData.colorScheme.onSurface.withValues(alpha: 0.6),
                      ),
                    ),
                    const SizedBox(height: ChatySpacing.base),
                    ChatyInput(
                      controller: textController,
                      hintText: 'Write an update or add a caption…',
                      maxLines: 3,
                    ),
                    const SizedBox(height: ChatySpacing.base),
                    Row(
                      children: [
                        Expanded(
                          child: _ComposerAction(
                            icon: Icons.image_rounded,
                            label: 'Photo',
                            busy: busyType == 'image',
                            onTap: busyType == null
                                ? () => publishMedia('image')
                                : null,
                          ),
                        ),
                        const SizedBox(width: ChatySpacing.sm),
                        Expanded(
                          child: _ComposerAction(
                            icon: Icons.videocam_rounded,
                            label: 'Video',
                            busy: busyType == 'video',
                            onTap: busyType == null
                                ? () => publishMedia('video')
                                : null,
                          ),
                        ),
                        const SizedBox(width: ChatySpacing.sm),
                        Expanded(
                          child: _ComposerAction(
                            icon: Icons.graphic_eq_rounded,
                            label: 'Audio',
                            busy: busyType == 'audio',
                            onTap: busyType == null
                                ? () => publishMedia('audio')
                                : null,
                          ),
                        ),
                        const SizedBox(width: ChatySpacing.sm),
                        Expanded(
                          child: _ComposerAction(
                            icon: Icons.description_rounded,
                            label: 'File',
                            busy: busyType == 'document',
                            onTap: busyType == null
                                ? () => publishMedia('document')
                                : null,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: ChatySpacing.lg),
                    ChatyPrimaryButton(
                      text: 'Publish Update',
                      icon: Icons.send_rounded,
                      isLoading: busyType == 'text',
                      onPressed: busyType == null ? publishText : null,
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
    textController.dispose();
  }

  Future<void> _openStatus(StatusRecord status) async {
    final themeData = Theme.of(context);
    String? signedUrl;
    if (status.hasMedia) {
      try {
        signedUrl = await _statusService.createSignedUrl(status.mediaPath!);
      } catch (_) {}
    }
    if (!mounted) return;

    await showDialog<void>(
      context: context,
      builder: (dialogContext) {
        return Dialog.fullscreen(
          backgroundColor: themeData.scaffoldBackgroundColor,
          child: SafeArea(
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: ChatySpacing.sm,
                    vertical: ChatySpacing.xs,
                  ),
                  child: Row(
                    children: [
                      ChatyBackButton(
                        onPressed: () => Navigator.of(dialogContext).pop(),
                      ),
                      const SizedBox(width: ChatySpacing.sm),
                      AppAvatar(
                        initials:
                            widget.dataStore
                                .getUser(status.userId)
                                ?.avatarInitials ??
                            'U',
                        colorHex:
                            widget.dataStore
                                .getUser(status.userId)
                                ?.avatarColorHex ??
                            '0xFF6366F1',
                        size: 38,
                      ),
                      const SizedBox(width: ChatySpacing.sm),
                      Expanded(
                        child: Text(
                          status.userId == widget.dataStore.currentUser.id
                              ? 'My Status'
                              : widget.dataStore
                                        .getUser(status.userId)
                                        ?.displayName ??
                                    'Contact',
                          style: ChatyTypography.title(
                            themeData.colorScheme.onSurface,
                          ),
                        ),
                      ),
                      if (status.userId == widget.dataStore.currentUser.id)
                        ChatyIconButton(
                          icon: Icons.delete_outline_rounded,
                          tooltip: 'Delete status',
                          color: context.colors.error,
                          onPressed: () async {
                            await _statusService.deleteStatus(status);
                            if (dialogContext.mounted) {
                              Navigator.of(dialogContext).pop();
                            }
                          },
                        ),
                    ],
                  ),
                ),
                Expanded(
                  child: Center(
                    child: Padding(
                      padding: const EdgeInsets.all(ChatySpacing.lg),
                      child: _statusContent(status, signedUrl, themeData),
                    ),
                  ),
                ),
                if (status.text.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(
                      ChatySpacing.xl,
                      0,
                      ChatySpacing.xl,
                      ChatySpacing.xl,
                    ),
                    child: Text(
                      status.text,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: themeData.colorScheme.onSurface,
                        fontSize: 16,
                        height: 1.4,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _statusContent(
    StatusRecord status,
    String? signedUrl,
    ThemeData themeData,
  ) {
    if (status.mediaType == 'text') {
      return Text(
        status.text,
        textAlign: TextAlign.center,
        style: TextStyle(
          color: themeData.colorScheme.onSurface,
          fontSize: 26,
          height: 1.35,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.5,
        ),
      );
    }
    if (signedUrl == null) {
      return Text(
        'Unable to load this update.',
        style: ChatyTypography.caption(
          themeData.colorScheme.onSurface.withValues(alpha: 0.6),
        ),
      );
    }
    if (status.mediaType == 'image') {
      return InteractiveViewer(
        child: ClipRRect(
          borderRadius: BorderRadius.circular(ChatyRadius.card),
          child: Image.network(
            signedUrl,
            fit: BoxFit.contain,
            errorBuilder: (_, __, ___) => Icon(
              Icons.broken_image_outlined,
              size: 72,
              color: themeData.colorScheme.onSurface.withValues(alpha: 0.4),
            ),
          ),
        ),
      );
    }

    final icon = switch (status.mediaType) {
      'video' => Icons.play_circle_fill_rounded,
      'audio' => Icons.graphic_eq_rounded,
      _ => Icons.description_rounded,
    };
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 84, color: themeData.colorScheme.primary),
        const SizedBox(height: ChatySpacing.base),
        Text(
          status.mediaName ?? status.mediaType,
          textAlign: TextAlign.center,
          style: ChatyTypography.title(themeData.colorScheme.onSurface),
        ),
        const SizedBox(height: ChatySpacing.base),
        ChatyPrimaryButton(
          text: 'Open Securely',
          icon: Icons.open_in_new_rounded,
          width: 200,
          onPressed: () => launchUrl(
            Uri.parse(signedUrl),
            mode: LaunchMode.externalApplication,
          ),
        ),
      ],
    );
  }

  String _relativeTime(DateTime value) {
    final diff = DateTime.now().difference(value);
    if (diff.inMinutes < 1) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m';
    if (diff.inHours < 24) return '${diff.inHours}h';
    return '${diff.inDays}d';
  }

  @override
  Widget build(BuildContext context) {
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
              child: Text(
                'Updates',
                style: ChatyTypography.headline(colors.foreground),
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: ChatySpacing.base),
            sliver: SliverToBoxAdapter(
              child: ChatyCard(
                padding: const EdgeInsets.all(ChatySpacing.sm),
                onTap: _openComposer,
                child: Row(
                  children: [
                    Stack(
                      children: [
                        AppAvatar(
                          initials: widget.dataStore.currentUser.avatarInitials,
                          colorHex: widget.dataStore.currentUser.avatarColorHex,
                          size: 48,
                        ),
                        Positioned(
                          bottom: 0,
                          right: 0,
                          child: Container(
                            padding: const EdgeInsets.all(2),
                            decoration: BoxDecoration(
                              color: colors.primary,
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: colors.surface,
                                width: 2,
                              ),
                            ),
                            child: Icon(
                              Icons.add_rounded,
                              color: colors.onPrimary,
                              size: 13,
                            ),
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(width: ChatySpacing.base),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            widget
                                    .preferencesController
                                    .home
                                    .myNameOverride
                                    .isNotEmpty
                                ? widget
                                      .preferencesController
                                      .home
                                      .myNameOverride
                                : 'My Status',
                            style: ChatyTypography.title(colors.foreground),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'Share photo, video, audio or thought',
                            style: ChatyTypography.caption(
                              colors.foregroundSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Icon(
                      Icons.chevron_right_rounded,
                      color: colors.foregroundTertiary,
                    ),
                  ],
                ),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                ChatySpacing.base + 4,
                ChatySpacing.lg,
                ChatySpacing.base,
                ChatySpacing.xs,
              ),
              child: Text(
                'RECENT UPDATES',
                style: TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w700,
                  color: colors.primary,
                  letterSpacing: 0.8,
                ),
              ),
            ),
          ),
          StreamBuilder<List<StatusRecord>>(
            stream: _statusService.watchActiveStatuses(),
            builder: (context, snapshot) {
              if (snapshot.connectionState == ConnectionState.waiting &&
                  !snapshot.hasData) {
                return const SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(
                    child: CircularProgressIndicator(strokeWidth: 2.2),
                  ),
                );
              }
              final statuses = snapshot.data ?? const <StatusRecord>[];
              if (statuses.isEmpty) {
                return SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(
                    child: Padding(
                      padding: const EdgeInsets.all(ChatySpacing.xl),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.auto_awesome_rounded,
                            size: 40,
                            color: colors.foregroundTertiary,
                          ),
                          const SizedBox(height: ChatySpacing.sm),
                          Text(
                            'No recent updates',
                            style: ChatyTypography.caption(
                              colors.foregroundSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              }
              return SliverPadding(
                padding: const EdgeInsets.symmetric(
                  horizontal: ChatySpacing.base,
                  vertical: ChatySpacing.xs,
                ),
                sliver: SliverToBoxAdapter(
                  child: ChatyGroupedSection(
                    children: [
                      for (int i = 0; i < statuses.length; i++) ...[
                        Builder(
                          builder: (context) {
                            final status = statuses[i];
                            final user = widget.dataStore.getUser(
                              status.userId,
                            );
                            final isMine =
                                status.userId ==
                                widget.dataStore.currentUser.id;

                            return ChatyListTile(
                              leading: Container(
                                padding: const EdgeInsets.all(2),
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  border: Border.all(
                                    color: colors.primary,
                                    width: 2.0,
                                  ),
                                ),
                                child: AppAvatar(
                                  initials: isMine
                                      ? widget
                                            .dataStore
                                            .currentUser
                                            .avatarInitials
                                      : user?.avatarInitials ?? 'U',
                                  colorHex: isMine
                                      ? widget
                                            .dataStore
                                            .currentUser
                                            .avatarColorHex
                                      : user?.avatarColorHex ?? '0xFF6366F1',
                                  size: 40,
                                ),
                              ),
                              title: Text(
                                isMine
                                    ? 'My Status'
                                    : user?.displayName ?? 'Contact',
                                style: ChatyTypography.title(colors.foreground),
                              ),
                              subtitle: Text(
                                status.text.isNotEmpty
                                    ? status.text
                                    : status.mediaName ?? status.mediaType,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: ChatyTypography.caption(
                                  colors.foregroundSecondary,
                                ),
                              ),
                              trailing: Text(
                                _relativeTime(status.createdAt),
                                style: ChatyTypography.caption(
                                  colors.foregroundTertiary,
                                ),
                              ),
                              onTap: () => _openStatus(status),
                            );
                          },
                        ),
                      ],
                    ],
                  ),
                ),
              );
            },
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 80)),
        ],
      ),
    );
  }
}

class _ComposerAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool busy;
  final VoidCallback? onTap;

  const _ComposerAction({
    required this.icon,
    required this.label,
    required this.busy,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return Material(
      color: colors.surfaceSecondary,
      borderRadius: BorderRadius.circular(ChatyRadius.md),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(ChatyRadius.md),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (busy)
                const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              else
                Icon(icon, size: 20, color: colors.primary),
              const SizedBox(height: 4),
              Text(
                label,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: colors.foreground,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
