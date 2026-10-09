import 'dart:async';

import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/status_service.dart';
import '../camera/camera_capture_screen.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/templates/template_controller.dart';
import '../../ui/core/templates/template_models.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/design_system/design_system.dart';
import 'status_view_session.dart';

class UpdatesScreen extends StatefulWidget {
  final ThemeConfig theme;
  final ChatyDataStore dataStore;
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
  final StatusViewSession _viewSession = StatusViewSession();

  /// WhatsApp-style camera capture for stories. The chosen look is baked
  /// into the published image because statuses carry no metadata channel.
  Future<void> _captureStory() async {
    final result = await ChatyCameraCaptureScreen.open(
      context,
      mode: ChatyCaptureMode.story,
    );
    if (result == null || !mounted) return;
    try {
      await _statusService.publishMediaFile(
        path: result.path,
        mediaType: 'image',
        text: result.caption,
        displayName: 'chaty_story.jpg',
      );
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Status posted.')));
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

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

    final viewerId = widget.dataStore.currentUser.id;
    if (_viewSession.tryBegin(status, viewerId)) {
      unawaited(_recordStatusView(status, viewerId));
    }

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
                      if (status.userId == widget.dataStore.currentUser.id) ...[
                        ChatyIconButton(
                          icon: Icons.delete_outline_rounded,
                          tooltip: 'Delete status',
                          color: context.colors.error,
                          onPressed: () async {
                            final confirmed = await ChatyConfirmDialog.show(
                              dialogContext,
                              title: 'Delete this update?',
                              message:
                                  'This update will be deleted for everyone and removed from your status.',
                              confirmLabel: 'Delete',
                              destructive: true,
                            );
                            if (confirmed == true) {
                              await _statusService.deleteStatus(status);
                              if (dialogContext.mounted) {
                                Navigator.of(dialogContext).pop();
                              }
                            }
                          },
                        ),
                      ],
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
                      ChatySpacing.sm,
                      ChatySpacing.xl,
                      ChatySpacing.sm,
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
                if (status.userId == widget.dataStore.currentUser.id)
                  _buildViewsControl(dialogContext, status),
              ],
            ),
          ),
        );
      },
    );
  }

  Future<void> _recordStatusView(StatusRecord status, String viewerId) async {
    try {
      final published = await _statusService.markViewed(status);
      if (!published) _viewSession.rollback(status, viewerId);
    } catch (error, stackTrace) {
      _viewSession.rollback(status, viewerId);
      debugPrint('Chaty status view publication failed: $error\n$stackTrace');
    }
  }

  Widget _buildViewsControl(BuildContext dialogContext, StatusRecord status) {
    return FutureBuilder<List<StatusViewer>>(
      future: _statusService.viewersFor(status.id),
      builder: (context, snapshot) {
        final count = snapshot.data?.length ?? 0;
        return Padding(
          padding: const EdgeInsets.only(bottom: 16, top: 4),
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onVerticalDragEnd: (details) {
              if (details.primaryVelocity != null &&
                  details.primaryVelocity! < -150) {
                _openViewersSheet(dialogContext, status.id);
              }
            },
            onTap: () => _openViewersSheet(dialogContext, status.id),
            child: Semantics(
              button: true,
              label: 'Viewed by $count people. Swipe up or tap to see viewers.',
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 18,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: context.colors.surfaceElevated.withValues(alpha: 0.85),
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(
                    color: context.colors.borderSubtle,
                    width: 0.8,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: context.colors.shadow,
                      blurRadius: 10,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.keyboard_arrow_up_rounded,
                      size: 16,
                      color: context.colors.accent,
                    ),
                    const SizedBox(height: 2),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          Icons.remove_red_eye_outlined,
                          size: 16,
                          color: context.colors.accent,
                        ),
                        const SizedBox(width: 6),
                        Text(
                          '$count ${count == 1 ? "view" : "views"}',
                          style: TextStyle(
                            color: context.colors.foreground,
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  void _openViewersSheet(BuildContext context, String statusId) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      backgroundColor: context.colors.surfaceElevated,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => _StatusViewersSheet(
        statusId: statusId,
        statusService: _statusService,
      ),
    );
  }

  Widget _buildStatusSliver(List<StatusRecord> statuses, AppColors colors) {
    final template = locator<TemplateController>().updates;
    const padding = EdgeInsets.symmetric(
      horizontal: ChatySpacing.base,
      vertical: ChatySpacing.xs,
    );

    return switch (template.layoutMode) {
      UpdatesLayoutMode.circularRail => SliverPadding(
        padding: padding,
        sliver: SliverToBoxAdapter(
          child: SizedBox(
            height: 132,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(vertical: 5),
              itemCount: statuses.length,
              separatorBuilder: (_, __) => const SizedBox(width: 10),
              itemBuilder: (context, index) =>
                  _buildCircularStatusCard(statuses[index], colors),
            ),
          ),
        ),
      ),
      UpdatesLayoutMode.gridTiles => SliverPadding(
        padding: padding,
        sliver: SliverToBoxAdapter(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final columns = constraints.maxWidth >= 840
                  ? 4
                  : constraints.maxWidth >= 540
                  ? 3
                  : constraints.maxWidth >= 340
                  ? 2
                  : 1;
              return GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: statuses.length,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: columns,
                  crossAxisSpacing: 10,
                  mainAxisSpacing: 10,
                  mainAxisExtent: columns == 1 ? 112 : 174,
                ),
                itemBuilder: (context, index) {
                  final status = statuses[index];
                  if (columns == 1) {
                    return _buildSquircleStatusCard(status, colors);
                  }
                  return _buildStatusGridCard(status, colors);
                },
              );
            },
          ),
        ),
      ),
      UpdatesLayoutMode.squircleCards => SliverPadding(
        padding: padding,
        sliver: SliverToBoxAdapter(
          child: Column(
            children: [
              for (final status in statuses) ...[
                _buildSquircleStatusCard(status, colors),
                const SizedBox(height: 9),
              ],
            ],
          ),
        ),
      ),
      UpdatesLayoutMode.minimalList => SliverPadding(
        padding: padding,
        sliver: SliverToBoxAdapter(
          child: ChatyGroupedSection(
            children: [
              for (final status in statuses)
                _buildMinimalStatusTile(status, colors),
            ],
          ),
        ),
      ),
    };
  }

  bool _isMyStatus(StatusRecord status) =>
      status.userId == widget.dataStore.currentUser.id;

  String _statusTitle(StatusRecord status) {
    if (_isMyStatus(status)) return 'My Status';
    return widget.dataStore.getUser(status.userId)?.displayName ?? 'Contact';
  }

  String _statusSubtitle(StatusRecord status) {
    if (status.text.isNotEmpty) return status.text;
    return status.mediaName ?? status.mediaType;
  }

  Widget _statusAvatar(StatusRecord status, {required double size}) {
    final isMine = _isMyStatus(status);
    final user = isMine ? null : widget.dataStore.getUser(status.userId);
    return AppAvatar(
      initials: isMine
          ? widget.dataStore.currentUser.avatarInitials
          : user?.avatarInitials ?? 'U',
      colorHex: isMine
          ? widget.dataStore.currentUser.avatarColorHex
          : user?.avatarColorHex ?? '0xFF6366F1',
      size: size,
    );
  }

  Widget _buildCircularStatusCard(StatusRecord status, AppColors colors) {
    final isMine = _isMyStatus(status);
    return Semantics(
      button: true,
      label: 'Open ${_statusTitle(status)}',
      child: InkWell(
        onTap: () => _openStatus(status),
        borderRadius: BorderRadius.circular(16),
        child: SizedBox(
          width: 98,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.all(3),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    colors: isMine
                        ? [colors.primary, colors.primary.withValues(alpha: 0.35)]
                        : [colors.primary, colors.info],
                  ),
                ),
                child: Container(
                  padding: const EdgeInsets.all(2),
                  decoration: BoxDecoration(
                    color: colors.background,
                    shape: BoxShape.circle,
                  ),
                  child: _statusAvatar(status, size: 54),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                _statusTitle(status),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 11.5,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                _relativeTime(status.createdAt),
                style: ChatyTypography.caption(colors.foregroundTertiary),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatusGridCard(StatusRecord status, AppColors colors) {
    final template = locator<TemplateController>().updates;
    return Material(
      elevation: template.cardElevation,
      color: colors.surface,
      borderRadius: BorderRadius.circular(18),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => _openStatus(status),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  _statusAvatar(status, size: 42),
                  const Spacer(),
                  Icon(
                    status.hasMedia
                        ? Icons.play_circle_outline_rounded
                        : Icons.text_snippet_outlined,
                    color: colors.primary,
                    size: 19,
                  ),
                ],
              ),
              const Spacer(),
              Text(
                _statusTitle(status),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                _statusSubtitle(status),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: ChatyTypography.caption(colors.foregroundSecondary),
              ),
              const SizedBox(height: 4),
              Text(
                _relativeTime(status.createdAt),
                style: ChatyTypography.caption(colors.foregroundTertiary),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSquircleStatusCard(StatusRecord status, AppColors colors) {
    final template = locator<TemplateController>().updates;
    return Material(
      elevation: template.cardElevation,
      color: colors.surface,
      borderRadius: BorderRadius.circular(18),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => _openStatus(status),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(3),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: colors.primary, width: 1.5),
                ),
                child: _statusAvatar(status, size: 44),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _statusTitle(status),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: ChatyTypography.title(colors.foreground),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      _statusSubtitle(status),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: ChatyTypography.caption(colors.foregroundSecondary),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Text(
                _relativeTime(status.createdAt),
                style: ChatyTypography.caption(colors.foregroundTertiary),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMinimalStatusTile(StatusRecord status, AppColors colors) {
    return ChatyListTile(
      leading: _statusAvatar(status, size: 34),
      title: Text(
        _statusTitle(status),
        style: ChatyTypography.title(colors.foreground),
      ),
      subtitle: Text(
        _statusSubtitle(status),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: ChatyTypography.caption(colors.foregroundSecondary),
      ),
      trailing: Text(
        _relativeTime(status.createdAt),
        style: ChatyTypography.caption(colors.foregroundTertiary),
      ),
      onTap: () => _openStatus(status),
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
    if (status.mediaType == 'audio' &&
        locator<TemplateController>().updates.enableStatusAudio) {
      return _StatusAudioPlayer(
        url: signedUrl,
        name: status.mediaName ?? 'Audio update',
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
    return ListenableBuilder(
      listenable: locator<TemplateController>(),
      builder: (context, _) {
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
                    Semantics(
                      button: true,
                      label: 'Capture photo for status',
                      child: InkWell(
                        borderRadius: BorderRadius.circular(22),
                        onTap: _captureStory,
                        child: Padding(
                          padding: const EdgeInsets.all(8),
                          child: Icon(
                            Icons.photo_camera_rounded,
                            size: 24,
                            color: colors.primary,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 2),
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
              return _buildStatusSliver(statuses, colors);
            },
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 80)),
        ],
      ),
        );
      },
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
      },
    );
  }
}

/// Real "Viewed by" list for one of my statuses. Data comes exclusively
/// from the owner-exposed rows of status_view_events — counts are never
/// fabricated and hidden visits are filtered server-side by RLS.
class _StatusAudioPlayer extends StatefulWidget {
  final String url;
  final String name;

  const _StatusAudioPlayer({required this.url, required this.name});

  @override
  State<_StatusAudioPlayer> createState() => _StatusAudioPlayerState();
}

class _StatusAudioPlayerState extends State<_StatusAudioPlayer> {
  final AudioPlayer _player = AudioPlayer();
  StreamSubscription<Duration>? _positionSubscription;
  StreamSubscription<Duration?>? _durationSubscription;
  StreamSubscription<PlayerState>? _playerStateSubscription;
  bool _loading = true;
  bool _failed = false;
  Duration _position = Duration.zero;
  Duration _duration = Duration.zero;

  @override
  void initState() {
    super.initState();
    _positionSubscription = _player.positionStream.listen((position) {
      if (mounted) setState(() => _position = position);
    });
    _durationSubscription = _player.durationStream.listen((duration) {
      if (mounted) setState(() => _duration = duration ?? Duration.zero);
    });
    _playerStateSubscription = _player.playerStateStream.listen((state) {
      if (!mounted) return;
      setState(() {
        if (state.processingState == ProcessingState.completed) {
          _position = _duration;
        }
      });
    });
    unawaited(_loadSource());
  }

  @override
  void didUpdateWidget(covariant _StatusAudioPlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.url != widget.url) unawaited(_loadSource());
  }

  Future<void> _loadSource() async {
    if (mounted) setState(() {
      _loading = true;
      _failed = false;
      _position = Duration.zero;
      _duration = Duration.zero;
    });
    try {
      await _player.stop();
      await _player.setUrl(widget.url);
      if (mounted) setState(() => _loading = false);
    } catch (_) {
      if (mounted) setState(() {
        _loading = false;
        _failed = true;
      });
    }
  }

  Future<void> _togglePlayback() async {
    if (_loading || _failed) return;
    try {
      if (_player.playing) {
        await _player.pause();
      } else {
        if (_duration > Duration.zero &&
            _position >= _duration - const Duration(milliseconds: 250)) {
          await _player.seek(Duration.zero);
        }
        unawaited(_player.play());
      }
      if (mounted) setState(() {});
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    }
  }

  String _timeLabel(Duration value) {
    final minutes = value.inMinutes.toString().padLeft(2, '0');
    final seconds = (value.inSeconds % 60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  @override
  void dispose() {
    unawaited(_positionSubscription?.cancel());
    unawaited(_durationSubscription?.cancel());
    unawaited(_playerStateSubscription?.cancel());
    unawaited(_player.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final totalMilliseconds = _duration.inMilliseconds;
    final max = totalMilliseconds > 0 ? totalMilliseconds.toDouble() : 1.0;
    final value = _position.inMilliseconds.clamp(0, totalMilliseconds).toDouble();

    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 360),
      child: Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(22),
          border: Border.all(color: colors.borderSubtle),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 62,
              height: 62,
              decoration: BoxDecoration(
                color: colors.primary.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(19),
              ),
              child: Icon(
                Icons.graphic_eq_rounded,
                color: colors.primary,
                size: 30,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              widget.name,
              textAlign: TextAlign.center,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: ChatyTypography.title(colors.foreground),
            ),
            const SizedBox(height: 10),
            if (_failed)
              Column(
                children: [
                  Text(
                    'Audio could not be played in Chaty.',
                    textAlign: TextAlign.center,
                    style: ChatyTypography.caption(colors.error),
                  ),
                  TextButton.icon(
                    onPressed: _loadSource,
                    icon: const Icon(Icons.refresh_rounded),
                    label: const Text('Retry'),
                  ),
                ],
              )
            else ...[
              Slider(
                value: value.clamp(0.0, max),
                max: max,
                onChanged: _loading || totalMilliseconds == 0
                    ? null
                    : (milliseconds) => _player.seek(
                        Duration(milliseconds: milliseconds.round()),
                      ),
              ),
              Row(
                children: [
                  Text(
                    _timeLabel(_position),
                    style: ChatyTypography.caption(colors.foregroundSecondary),
                  ),
                  const Spacer(),
                  Text(
                    _timeLabel(_duration),
                    style: ChatyTypography.caption(colors.foregroundSecondary),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: _loading ? null : _togglePlayback,
                style: FilledButton.styleFrom(
                  shape: const StadiumBorder(),
                  padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                ),
                child: _loading
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(_player.playing ? Icons.pause_rounded : Icons.play_arrow_rounded),
                          const SizedBox(width: 8),
                          Text(_player.playing ? 'Pause audio' : 'Play audio'),
                        ],
                      ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _StatusViewersSheet extends StatefulWidget {
  final String statusId;
  final StatusService statusService;

  const _StatusViewersSheet({
    required this.statusId,
    required this.statusService,
  });

  @override
  State<_StatusViewersSheet> createState() => _StatusViewersSheetState();
}

class _StatusViewersSheetState extends State<_StatusViewersSheet> {
  late final Future<List<StatusViewer>> _viewers;

  @override
  void initState() {
    super.initState();
    _viewers = widget.statusService.viewersFor(widget.statusId);
  }

  String _relativeTime(DateTime value) {
    final diff = DateTime.now().difference(value);
    if (diff.inMinutes < 1) return 'just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    return '${diff.inDays}d ago';
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return SafeArea(
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * 0.6,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
              child: Text(
                'Viewed by',
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            Flexible(
              child: FutureBuilder<List<StatusViewer>>(
                future: _viewers,
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const Center(
                      child: Padding(
                        padding: EdgeInsets.all(24),
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                    );
                  }
                  if (snapshot.hasError) {
                    return Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 24,
                      ),
                      child: ChatyEmptyState(
                        icon: Icons.error_outline_rounded,
                        title: 'Unable to load views',
                        message:
                            'Something went wrong while fetching viewers. Please try again.',
                        actionLabel: 'Retry',
                        onAction: () {
                          setState(() {
                            _viewers = widget.statusService.viewersFor(
                              widget.statusId,
                            );
                          });
                        },
                      ),
                    );
                  }
                  final viewers = snapshot.data ?? const <StatusViewer>[];
                  if (viewers.isEmpty) {
                    return const Padding(
                      padding: EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 24,
                      ),
                      child: ChatyEmptyState(
                        icon: Icons.remove_red_eye_outlined,
                        title: 'No views yet',
                        message:
                            'Views will appear here after someone sees your update.',
                      ),
                    );
                  }
                  return ListView.separated(
                    shrinkWrap: true,
                    padding: const EdgeInsets.only(bottom: 16),
                    itemCount: viewers.length,
                    separatorBuilder: (_, _) =>
                        Divider(color: colors.divider, height: 1, indent: 66),
                    itemBuilder: (context, index) {
                      final viewer = viewers[index];
                      return ListTile(
                        leading: AppAvatar(
                          initials: viewer.avatarInitials,
                          colorHex: viewer.avatarColorHex,
                          size: 40,
                        ),
                        title: Text(
                          viewer.displayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: colors.foreground,
                            fontWeight: FontWeight.w700,
                            fontSize: 14.5,
                          ),
                        ),
                        subtitle: Text(
                          '@${viewer.username}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: colors.foregroundSecondary,
                            fontSize: 12.5,
                          ),
                        ),
                        trailing: Text(
                          _relativeTime(viewer.viewedAt),
                          style: TextStyle(
                            color: colors.foregroundTertiary,
                            fontSize: 12,
                          ),
                        ),
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
