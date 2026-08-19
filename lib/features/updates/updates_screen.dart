import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../ui/core/theme/theme_config.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../data/services/status_service.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../injection/locator.dart';
import '../../../ui/core/theme/theme_controller.dart';

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
    final theme = locator<ThemeController>().globalTheme;
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
                    SnackBar(content: Text(error.toString().replaceFirst('Exception: ', ''))),
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
                    SnackBar(content: Text(error.toString().replaceFirst('Exception: ', ''))),
                  );
                }
              } finally {
                if (sheetContext.mounted) setSheetState(() => busyType = null);
              }
            }

            return Container(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                top: 14,
                bottom: MediaQuery.of(sheetContext).viewInsets.bottom + 20,
              ),
              decoration: BoxDecoration(
                color: theme.surfaceColor,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: SafeArea(
                top: false,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Center(
                      child: Container(
                        width: 38,
                        height: 4,
                        decoration: BoxDecoration(
                          color: theme.secondaryTextColor.withValues(alpha: 0.3),
                          borderRadius: BorderRadius.circular(4),
                        ),
                      ),
                    ),
                    const SizedBox(height: 18),
                    Text(
                      'Create update',
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 19,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Updates expire automatically after 24 hours.',
                      style: TextStyle(color: theme.secondaryTextColor, fontSize: 12.5),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: textController,
                      minLines: 2,
                      maxLines: 5,
                      maxLength: 500,
                      style: TextStyle(color: theme.primaryTextColor),
                      decoration: InputDecoration(
                        hintText: 'Write an update or add a caption…',
                        hintStyle: TextStyle(color: theme.secondaryTextColor),
                        filled: true,
                        fillColor: theme.cardColor,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(16),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 10,
                      runSpacing: 10,
                      children: [
                        _ComposerAction(
                          icon: Icons.image_rounded,
                          label: 'Image',
                          busy: busyType == 'image',
                          onTap: busyType == null ? () => publishMedia('image') : null,
                        ),
                        _ComposerAction(
                          icon: Icons.videocam_rounded,
                          label: 'Video',
                          busy: busyType == 'video',
                          onTap: busyType == null ? () => publishMedia('video') : null,
                        ),
                        _ComposerAction(
                          icon: Icons.graphic_eq_rounded,
                          label: 'Audio',
                          busy: busyType == 'audio',
                          onTap: busyType == null ? () => publishMedia('audio') : null,
                        ),
                        _ComposerAction(
                          icon: Icons.description_rounded,
                          label: 'File',
                          busy: busyType == 'document',
                          onTap: busyType == null ? () => publishMedia('document') : null,
                        ),
                      ],
                    ),
                    const SizedBox(height: 18),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: busyType == null ? publishText : null,
                        icon: busyType == 'text'
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Icon(Icons.send_rounded),
                        label: const Text('Publish text update'),
                      ),
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
    final theme = locator<ThemeController>().globalTheme;
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
          backgroundColor: theme.backgroundColor,
          child: SafeArea(
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
                  child: Row(
                    children: [
                      IconButton(
                        onPressed: () => Navigator.of(dialogContext).pop(),
                        icon: const Icon(Icons.chevron_left_rounded),
                      ),
                      const SizedBox(width: 4),
                      AppAvatar(
                        initials: widget.dataStore.getUser(status.userId)?.avatarInitials ?? 'CU',
                        colorHex: widget.dataStore.getUser(status.userId)?.avatarColorHex ?? '0xFF6366F1',
                        size: 36,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          status.userId == widget.dataStore.currentUser.id
                              ? 'My Status'
                              : widget.dataStore.getUser(status.userId)?.displayName ?? 'Chaty User',
                          style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w700),
                        ),
                      ),
                      if (status.userId == widget.dataStore.currentUser.id)
                        IconButton(
                          tooltip: 'Delete status',
                          onPressed: () async {
                            await _statusService.deleteStatus(status);
                            if (dialogContext.mounted) Navigator.of(dialogContext).pop();
                          },
                          icon: Icon(Icons.delete_outline_rounded, color: theme.dangerColor),
                        ),
                    ],
                  ),
                ),
                Expanded(
                  child: Center(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: _statusContent(status, signedUrl, theme),
                    ),
                  ),
                ),
                if (status.text.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
                    child: Text(
                      status.text,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 17,
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

  Widget _statusContent(StatusRecord status, String? signedUrl, ThemeConfig theme) {
    if (status.mediaType == 'text') {
      return Text(
        status.text,
        textAlign: TextAlign.center,
        style: TextStyle(
          color: theme.primaryTextColor,
          fontSize: 28,
          height: 1.35,
          fontWeight: FontWeight.w800,
        ),
      );
    }
    if (signedUrl == null) {
      return Text('Unable to load this update.', style: TextStyle(color: theme.secondaryTextColor));
    }
    if (status.mediaType == 'image') {
      return InteractiveViewer(
        child: Image.network(
          signedUrl,
          fit: BoxFit.contain,
          errorBuilder: (_, __, ___) => Icon(Icons.broken_image_outlined, size: 80, color: theme.secondaryTextColor),
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
        Icon(icon, size: 92, color: theme.accentColor),
        const SizedBox(height: 18),
        Text(
          status.mediaName ?? status.mediaType,
          textAlign: TextAlign.center,
          style: TextStyle(color: theme.primaryTextColor, fontSize: 16, fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 18),
        FilledButton.icon(
          onPressed: () => launchUrl(Uri.parse(signedUrl), mode: LaunchMode.externalApplication),
          icon: const Icon(Icons.open_in_new_rounded),
          label: const Text('Open securely'),
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
    final theme = locator<ThemeController>().globalTheme;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Text(
                'Updates',
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 22 * theme.fontScale,
                  fontWeight: FontWeight.w800,
                  letterSpacing: -0.5,
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: InkWell(
                borderRadius: BorderRadius.circular(18),
                onTap: _openComposer,
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Row(
                    children: [
                      Stack(
                        children: [
                          AppAvatar(
                            initials: widget.dataStore.currentUser.avatarInitials,
                            colorHex: widget.dataStore.currentUser.avatarColorHex,
                            size: 52,
                          ),
                          Positioned(
                            bottom: 0,
                            right: 0,
                            child: Container(
                              padding: const EdgeInsets.all(2),
                              decoration: BoxDecoration(
                                color: theme.accentColor,
                                shape: BoxShape.circle,
                                border: Border.all(color: theme.backgroundColor, width: 2),
                              ),
                              child: Icon(Icons.add_rounded, color: theme.onAccentColor, size: 14),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('My Status', style: TextStyle(color: theme.primaryTextColor, fontSize: 16, fontWeight: FontWeight.bold)),
                            const SizedBox(height: 2),
                            Text('Share text, image, video, audio or a file', style: TextStyle(color: theme.secondaryTextColor, fontSize: 12.5)),
                          ],
                        ),
                      ),
                      Icon(Icons.chevron_right_rounded, color: theme.secondaryTextColor),
                    ],
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 6),
              child: Text(
                'RECENT UPDATES',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  color: theme.secondaryTextColor,
                  letterSpacing: 0.8,
                ),
              ),
            ),
            Expanded(
              child: StreamBuilder<List<StatusRecord>>(
                stream: _statusService.watchActiveStatuses(),
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting && !snapshot.hasData) {
                    return Center(child: CircularProgressIndicator(color: theme.accentColor));
                  }
                  if (snapshot.hasError) {
                    return Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Text(
                          'Unable to load updates.\n${snapshot.error}',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: theme.secondaryTextColor),
                        ),
                      ),
                    );
                  }
                  final statuses = snapshot.data ?? const <StatusRecord>[];
                  if (statuses.isEmpty) {
                    return Center(
                      child: Text('No active updates', style: TextStyle(color: theme.secondaryTextColor)),
                    );
                  }
                  return ListView.separated(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    itemCount: statuses.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 2),
                    itemBuilder: (context, index) {
                      final status = statuses[index];
                      final user = widget.dataStore.getUser(status.userId);
                      final isMine = status.userId == widget.dataStore.currentUser.id;
                      return ListTile(
                        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                        leading: Container(
                          padding: const EdgeInsets.all(2),
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            border: Border.all(color: theme.accentColor, width: 2.5),
                          ),
                          child: AppAvatar(
                            initials: isMine ? widget.dataStore.currentUser.avatarInitials : user?.avatarInitials ?? 'CU',
                            colorHex: isMine ? widget.dataStore.currentUser.avatarColorHex : user?.avatarColorHex ?? '0xFF6366F1',
                            size: 44,
                          ),
                        ),
                        title: Text(
                          isMine ? 'My Status' : user?.displayName ?? 'Chaty User',
                          style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w600),
                        ),
                        subtitle: Text(
                          status.text.isNotEmpty ? status.text : status.mediaName ?? status.mediaType,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(color: theme.secondaryTextColor, fontSize: 12.5),
                        ),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(_relativeTime(status.createdAt), style: TextStyle(color: theme.secondaryTextColor, fontSize: 11)),
                            const SizedBox(width: 4),
                            Icon(Icons.chevron_right_rounded, color: theme.secondaryTextColor),
                          ],
                        ),
                        onTap: () => _openStatus(status),
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
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
    return ActionChip(
      onPressed: onTap,
      avatar: busy
          ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
          : Icon(icon, size: 18),
      label: Text(label),
    );
  }
}
