import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/contact_relationship_service.dart';
import '../../data/services/rich_chat_realtime_service.dart';
import '../../domain/models/chat_message.dart';
import '../../domain/models/contact_relationship.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/design_system/design_system.dart';
import '../messages/media_viewer_screen.dart';
import 'contact_privacy_screen.dart';

class ContactInfoScreen extends StatefulWidget {
  final ThemeConfig theme;
  final ChatyDataStore dataStore;
  final Conversation conversation;
  final UserProfile contact;
  final ContactRelationshipService relationshipService;
  final RichChatRealtimeService realtimeService;

  const ContactInfoScreen({
    super.key,
    required this.theme,
    required this.dataStore,
    required this.conversation,
    required this.contact,
    required this.relationshipService,
    required this.realtimeService,
  });

  @override
  State<ContactInfoScreen> createState() => _ContactInfoScreenState();
}

class _ContactInfoScreenState extends State<ContactInfoScreen> {
  ContactConnectionStatus _connection = const ContactConnectionStatus();
  bool _blocked = false;
  bool _loading = true;
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait<dynamic>(<Future<dynamic>>[
        widget.relationshipService.connectionStatus(widget.contact.id),
        widget.relationshipService.isBlocked(widget.contact.id),
      ]);
      if (!mounted) return;
      setState(() {
        _connection = results[0] as ContactConnectionStatus;
        _blocked = results[1] as bool;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _accept() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await widget.relationshipService.acceptConnection(widget.contact.id);
      final next = await widget.relationshipService.connectionStatus(
        widget.contact.id,
      );
      if (!mounted) return;
      setState(() => _connection = next);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _toggleBlock() async {
    if (_busy) return;
    final next = !_blocked;
    if (next) {
      final confirmed = await ChatyConfirmDialog.show(
        context,
        title: 'Block ${widget.contact.displayName}?',
        message:
            'You will stop receiving new messages from this person, and they cannot message you until you unblock them.',
        confirmLabel: 'Block',
        destructive: true,
      );
      if (confirmed != true) return;
    }
    setState(() => _busy = true);
    try {
      await widget.relationshipService.setBlocked(widget.contact.id, next);
      if (!mounted) return;
      setState(() => _blocked = next);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _presenceLabel() {
    final activity = widget.realtimeService.activityFor(
      widget.conversation.id,
      widget.contact.id,
    );
    if (activity.isRecording) return 'recording voice message…';
    if (activity.isTyping) return 'typing…';
    if (widget.realtimeService.isOnline(widget.contact.id)) return 'Online';
    final seen = widget.realtimeService.lastSeenFor(widget.contact.id);
    if (seen == null) return 'Last seen hidden';
    final local = seen.toLocal();
    final now = DateTime.now();
    final hh = local.hour.toString().padLeft(2, '0');
    final mm = local.minute.toString().padLeft(2, '0');
    if (now.year == local.year &&
        now.month == local.month &&
        now.day == local.day) {
      return 'Last seen today at $hh:$mm';
    }
    return 'Last seen ${local.day}/${local.month}/${local.year} at $hh:$mm';
  }

  List<ChatMessage> get _messages => widget.dataStore
      .getMessages(widget.conversation.id)
      .map(widget.realtimeService.hydrateMessage)
      .toList(growable: false);

  List<ChatMessage> get _media => _messages
      .where((message) {
        final type = message.attachment?.type;
        return type == 'image' || type == 'video';
      })
      .toList(growable: false);

  List<ChatMessage> get _documents => _messages
      .where((message) => message.attachment?.type == 'document')
      .toList(growable: false);

  List<String> get _links {
    final links = <String>[];
    final regex = RegExp(r'https?://[^\s]+', caseSensitive: false);
    for (final message in _messages) {
      links.addAll(
        regex
            .allMatches(message.text)
            .map((match) => match.group(0)!)
            .where((item) => item.isNotEmpty),
      );
    }
    return links.toSet().toList(growable: false);
  }

  void _openMedia(ChatMessage message) {
    final attachment = message.attachment;
    if (attachment == null) return;
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => MediaViewerScreen(
          title: attachment.name,
          type: attachment.type,
          size: attachment.size,
          storagePath: attachment.url,
          theme: widget.theme,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final media = _media;
    final documents = _documents;
    final links = _links;

    return ListenableBuilder(
      listenable: widget.realtimeService,
      builder: (context, _) => ChatyScaffold(
        appBar: const ChatyAppBar(
          title: 'Contact Info',
          leading: ChatyBackButton(),
        ),
        body: _loading
            ? const Center(child: CircularProgressIndicator(strokeWidth: 2.2))
            : ListView(
                padding: const EdgeInsets.symmetric(
                  horizontal: ChatySpacing.base,
                  vertical: ChatySpacing.md,
                ),
                children: [
                  Center(
                    child: Stack(
                      clipBehavior: Clip.none,
                      children: [
                        CircleAvatar(
                          radius: 46,
                          backgroundColor: Color(
                            int.parse(widget.contact.avatarColorHex),
                          ),
                          child: Text(
                            widget.contact.avatarInitials,
                            style: TextStyle(
                              color: context.colors.onPrimary,
                              fontSize: 28,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                        if (widget.realtimeService.isOnline(widget.contact.id))
                          Positioned(
                            right: 2,
                            top: 2,
                            child: Container(
                              width: 16,
                              height: 16,
                              decoration: BoxDecoration(
                                color: context.colors.success,
                                shape: BoxShape.circle,
                                border: Border.all(
                                  color: context.colors.background,
                                  width: 2.5,
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(height: ChatySpacing.md),
                  Text(
                    widget.contact.displayName,
                    textAlign: TextAlign.center,
                    style: ChatyTypography.headline(context.colors.foreground),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '@${widget.contact.username}',
                    textAlign: TextAlign.center,
                    style: ChatyTypography.caption(
                      context.colors.foregroundSecondary,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    _presenceLabel(),
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: widget.realtimeService.isOnline(widget.contact.id)
                          ? context.colors.success
                          : context.colors.foregroundSecondary,
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: ChatySpacing.md),
                  // Quick Action Action Row (Call, Video, Search, Share)
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      _QuickActionButton(
                        icon: Icons.call_outlined,
                        label: 'Call',
                        onTap: () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Starting voice call with ${widget.contact.displayName}...')),
                          );
                        },
                      ),
                      _QuickActionButton(
                        icon: Icons.videocam_outlined,
                        label: 'Video',
                        onTap: () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Starting video call with ${widget.contact.displayName}...')),
                          );
                        },
                      ),
                      _QuickActionButton(
                        icon: Icons.search_rounded,
                        label: 'Search',
                        onTap: () {
                          Navigator.of(context).pop('search');
                        },
                      ),
                      _QuickActionButton(
                        icon: Icons.share_outlined,
                        label: 'Share',
                        onTap: () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Sharing contact card for ${widget.contact.displayName}...')),
                          );
                        },
                      ),
                    ],
                  ),
                  if (widget.contact.about.trim().isNotEmpty) ...[
                    const SizedBox(height: ChatySpacing.base),
                    ChatyCard(
                      child: Text(
                        widget.contact.about,
                        style: TextStyle(
                          color: context.colors.foreground,
                          fontSize: 14.5,
                          height: 1.4,
                        ),
                      ),
                    ),
                  ],
                  if (_error != null) ...[
                    const SizedBox(height: ChatySpacing.sm),
                    Text(
                      _error!,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: context.colors.error,
                        fontSize: 13,
                      ),
                    ),
                  ],
                  const SizedBox(height: ChatySpacing.lg),
                  ChatyGroupedSection(
                    title: 'Connection & Calls',
                    children: [
                      ChatyListTile(
                        leading: Icon(
                          _connection.callsAllowed
                              ? Icons.verified_user_rounded
                              : Icons.person_add_alt_1_rounded,
                          color: _connection.callsAllowed
                              ? context.colors.success
                              : context.colors.primary,
                        ),
                        title: Text(
                          _connection.callsAllowed
                              ? 'Mutual contact verified'
                              : _connection.isPendingIncoming
                              ? 'Accept contact request'
                              : _connection.isWaitingForOther
                              ? 'Waiting for ${widget.contact.displayName}'
                              : 'Accept this contact',
                          style: TextStyle(
                            color: context.colors.foreground,
                            fontWeight: FontWeight.w600,
                            fontSize: 15,
                          ),
                        ),
                        subtitle: Text(
                          _connection.callsAllowed
                              ? 'Encrypted voice and video calls are unlocked.'
                              : 'Messaging stays open. Direct calls unlock when both accept.',
                          style: ChatyTypography.caption(
                            context.colors.foregroundSecondary,
                          ),
                        ),
                        trailing: !_connection.myAccepted
                            ? ChatyPrimaryButton(
                                text: 'Accept',
                                height: 36,
                                width: 88,
                                isLoading: _busy,
                                onPressed: _accept,
                              )
                            : Icon(
                                _connection.callsAllowed
                                    ? Icons.check_circle_rounded
                                    : Icons.schedule_rounded,
                                color: _connection.callsAllowed
                                    ? context.colors.success
                                    : context.colors.foregroundTertiary,
                              ),
                      ),
                    ],
                  ),
                  ChatyGroupedSection(
                    title: 'Privacy & Safety',
                    children: [
                      ChatyListTile(
                        leading: Icon(
                          Icons.tune_rounded,
                          color: context.colors.primary,
                        ),
                        title: Text(
                          'Individual Privacy Controls',
                          style: TextStyle(
                            color: context.colors.foreground,
                            fontWeight: FontWeight.w600,
                            fontSize: 15,
                          ),
                        ),
                        subtitle: Text(
                          'Read receipts, typing indicator, recording & online presence',
                          style: ChatyTypography.caption(
                            context.colors.foregroundSecondary,
                          ),
                        ),
                        trailing: Icon(
                          Icons.chevron_right_rounded,
                          color: context.colors.foregroundSecondary,
                        ),
                        onTap: () => Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => ContactPrivacyScreen(
                              contact: widget.contact,
                              relationshipService: widget.relationshipService,
                            ),
                          ),
                        ),
                      ),
                      ChatyListTile(
                        leading: Icon(
                          _blocked
                              ? Icons.lock_open_rounded
                              : Icons.block_rounded,
                          color: context.colors.error,
                        ),
                        title: Text(
                          _blocked
                              ? 'Unblock ${widget.contact.displayName}'
                              : 'Block ${widget.contact.displayName}',
                          style: TextStyle(
                            color: context.colors.error,
                            fontWeight: FontWeight.w600,
                            fontSize: 15,
                          ),
                        ),
                        subtitle: Text(
                          _blocked
                              ? 'Allow messages from this contact again.'
                              : 'Stop receiving messages from this contact.',
                          style: ChatyTypography.caption(
                            context.colors.foregroundSecondary,
                          ),
                        ),
                        onTap: _busy ? null : _toggleBlock,
                      ),
                    ],
                  ),
                  ChatyGroupedSection(
                    title: 'Shared Media & Files',
                    children: [
                      if (media.isEmpty && links.isEmpty && documents.isEmpty)
                        Padding(
                          padding: const EdgeInsets.all(ChatySpacing.base),
                          child: Center(
                            child: Text(
                              'No shared media, links or documents yet.',
                              style: ChatyTypography.caption(
                                context.colors.foregroundSecondary,
                              ),
                            ),
                          ),
                        ),
                      if (media.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.all(ChatySpacing.base),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '${media.length} Photos & Videos',
                                style: TextStyle(
                                  color: context.colors.foreground,
                                  fontWeight: FontWeight.w700,
                                  fontSize: 13,
                                ),
                              ),
                              const SizedBox(height: ChatySpacing.sm),
                              SizedBox(
                                height: 80,
                                child: ListView.separated(
                                  scrollDirection: Axis.horizontal,
                                  itemCount: media.length,
                                  separatorBuilder: (_, __) =>
                                      const SizedBox(width: ChatySpacing.sm),
                                  itemBuilder: (context, index) {
                                    final attachment = media[index].attachment!;
                                    return InkWell(
                                      onTap: () => _openMedia(media[index]),
                                      borderRadius: BorderRadius.circular(
                                        ChatyRadius.md,
                                      ),
                                      child: Container(
                                        width: 110,
                                        padding: const EdgeInsets.all(8),
                                        decoration: BoxDecoration(
                                          color:
                                              context.colors.surfaceSecondary,
                                          borderRadius: BorderRadius.circular(
                                            ChatyRadius.md,
                                          ),
                                        ),
                                        child: Column(
                                          crossAxisAlignment:
                                              CrossAxisAlignment.start,
                                          children: [
                                            Icon(
                                              attachment.type == 'video'
                                                  ? Icons
                                                        .play_circle_outline_rounded
                                                  : Icons.image_outlined,
                                              color: context.colors.primary,
                                              size: 20,
                                            ),
                                            const Spacer(),
                                            Text(
                                              attachment.name,
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: TextStyle(
                                                color:
                                                    context.colors.foreground,
                                                fontSize: 11,
                                                fontWeight: FontWeight.w600,
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                    );
                                  },
                                ),
                              ),
                            ],
                          ),
                        ),
                      if (links.isNotEmpty)
                        for (final link in links.take(5))
                          ChatyListTile(
                            leading: Icon(
                              Icons.link_rounded,
                              size: 20,
                              color: context.colors.primary,
                            ),
                            title: Text(
                              link,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                color: context.colors.primary,
                                fontSize: 13.5,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                      if (documents.isNotEmpty)
                        for (final message in documents.take(5))
                          ChatyListTile(
                            leading: Icon(
                              Icons.description_outlined,
                              color: context.colors.primary,
                              size: 22,
                            ),
                            title: Text(
                              message.attachment!.name,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                color: context.colors.foreground,
                                fontWeight: FontWeight.w600,
                                fontSize: 14,
                              ),
                            ),
                            subtitle: Text(
                              message.attachment!.size,
                              style: ChatyTypography.caption(
                                context.colors.foregroundSecondary,
                              ),
                            ),
                            onTap: () => _openMedia(message),
                          ),
                    ],
                  ),
                  ChatyGroupedSection(
                    title: 'Groups in Common',
                    children: [
                      ChatyListTile(
                        leading: CircleAvatar(
                          backgroundColor: context.colors.primary.withValues(alpha: 0.15),
                          child: Icon(Icons.groups_rounded, color: context.colors.primary),
                        ),
                        title: Text(
                          'Flutter Architects & Core Devs',
                          style: TextStyle(
                            color: context.colors.foreground,
                            fontWeight: FontWeight.w600,
                            fontSize: 15,
                          ),
                        ),
                        subtitle: Text(
                          'You, ${widget.contact.displayName}, Alex, Maya and 12 others',
                          style: ChatyTypography.caption(
                            context.colors.foregroundSecondary,
                          ),
                        ),
                      ),
                      ChatyListTile(
                        leading: CircleAvatar(
                          backgroundColor: context.colors.secondary.withValues(alpha: 0.15),
                          child: Icon(Icons.palette_rounded, color: context.colors.secondary),
                        ),
                        title: Text(
                          'Design Systems & Motion Craft',
                          style: TextStyle(
                            color: context.colors.foreground,
                            fontWeight: FontWeight.w600,
                            fontSize: 15,
                          ),
                        ),
                        subtitle: Text(
                          'You, ${widget.contact.displayName}, and 4 others',
                          style: ChatyTypography.caption(
                            context.colors.foregroundSecondary,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
      ),
    );
  }
}

class _QuickActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _QuickActionButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: context.colors.surfaceSecondary,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: context.colors.primary, size: 22),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                color: context.colors.foreground,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
