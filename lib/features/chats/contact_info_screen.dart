import 'package:flutter/material.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../data/services/contact_relationship_service.dart';
import '../../data/services/rich_chat_realtime_service.dart';
import '../../domain/models/chat_message.dart';
import '../../domain/models/contact_relationship.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/user_profile.dart';
import '../../ui/core/theme/theme_config.dart';
import '../messages/media_viewer_screen.dart';
import 'contact_privacy_screen.dart';

class ContactInfoScreen extends StatefulWidget {
  final ThemeConfig theme;
  final MockDataStore dataStore;
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
      final next = await widget.relationshipService.connectionStatus(widget.contact.id);
      if (!mounted) return;
      setState(() => _connection = next);
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$error')));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _toggleBlock() async {
    if (_busy) return;
    final next = !_blocked;
    if (next) {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text('Block ${widget.contact.displayName}?'),
          content: const Text('You will stop receiving new messages from this person, and they cannot message you until you unblock them.'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Block')),
          ],
        ),
      );
      if (confirmed != true) return;
    }
    setState(() => _busy = true);
    try {
      await widget.relationshipService.setBlocked(widget.contact.id, next);
      if (!mounted) return;
      setState(() => _blocked = next);
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$error')));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _presenceLabel() {
    final activity = widget.realtimeService.activityFor(widget.conversation.id, widget.contact.id);
    if (activity.isRecording) return 'recording voice message…';
    if (activity.isTyping) return 'typing…';
    if (widget.realtimeService.isOnline(widget.contact.id)) return 'online';
    final seen = widget.realtimeService.lastSeenFor(widget.contact.id);
    if (seen == null) return 'last seen hidden';
    final local = seen.toLocal();
    final now = DateTime.now();
    final hh = local.hour.toString().padLeft(2, '0');
    final mm = local.minute.toString().padLeft(2, '0');
    if (now.year == local.year && now.month == local.month && now.day == local.day) return 'last seen today at $hh:$mm';
    return 'last seen ${local.day}/${local.month}/${local.year} at $hh:$mm';
  }

  List<ChatMessage> get _messages => widget.dataStore.getMessages(widget.conversation.id).map(widget.realtimeService.hydrateMessage).toList(growable: false);

  List<ChatMessage> get _media => _messages.where((message) {
        final type = message.attachment?.type;
        return type == 'image' || type == 'video';
      }).toList(growable: false);

  List<ChatMessage> get _documents => _messages.where((message) => message.attachment?.type == 'document').toList(growable: false);

  List<String> get _links {
    final links = <String>[];
    final regex = RegExp(r'https?://[^\s]+', caseSensitive: false);
    for (final message in _messages) {
      links.addAll(regex.allMatches(message.text).map((match) => match.group(0)!).where((item) => item.isNotEmpty));
    }
    return links.toSet().toList(growable: false);
  }

  void _openMedia(ChatMessage message) {
    final attachment = message.attachment;
    if (attachment == null) return;
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => MediaViewerScreen(
        title: attachment.name,
        type: attachment.type,
        size: attachment.size,
        storagePath: attachment.url,
        theme: widget.theme,
      ),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final theme = widget.theme;
    final media = _media;
    final documents = _documents;
    final links = _links;
    return ListenableBuilder(
      listenable: widget.realtimeService,
      builder: (context, _) => Scaffold(
        backgroundColor: theme.backgroundColor,
        appBar: AppBar(
          backgroundColor: theme.surfaceColor,
          foregroundColor: theme.primaryTextColor,
          title: const Text('Contact info'),
        ),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : ListView(
                padding: const EdgeInsets.fromLTRB(16, 20, 16, 32),
                children: [
                  Center(
                    child: Stack(
                      clipBehavior: Clip.none,
                      children: [
                        CircleAvatar(
                          radius: 48,
                          backgroundColor: Color(int.parse(widget.contact.avatarColorHex)),
                          child: Text(widget.contact.avatarInitials, style: const TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.w800)),
                        ),
                        if (widget.realtimeService.isOnline(widget.contact.id))
                          Positioned(
                            right: 2,
                            top: 2,
                            child: Container(
                              width: 16,
                              height: 16,
                              decoration: BoxDecoration(
                                color: theme.successColor,
                                shape: BoxShape.circle,
                                border: Border.all(color: theme.backgroundColor, width: 3),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(widget.contact.displayName, textAlign: TextAlign.center, style: TextStyle(color: theme.primaryTextColor, fontSize: 22, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 3),
                  Text('@${widget.contact.username}', textAlign: TextAlign.center, style: TextStyle(color: theme.secondaryTextColor)),
                  const SizedBox(height: 4),
                  Text(_presenceLabel(), textAlign: TextAlign.center, style: TextStyle(color: widget.realtimeService.isOnline(widget.contact.id) ? theme.successColor : theme.secondaryTextColor, fontSize: 12, fontWeight: FontWeight.w600)),
                  if (widget.contact.about.trim().isNotEmpty) ...[
                    const SizedBox(height: 14),
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(color: theme.cardColor, borderRadius: BorderRadius.circular(14)),
                      child: Text(widget.contact.about, style: TextStyle(color: theme.primaryTextColor, height: 1.4)),
                    ),
                  ],
                  if (_error != null) ...[
                    const SizedBox(height: 10),
                    Text(_error!, style: TextStyle(color: theme.dangerColor)),
                  ],
                  const SizedBox(height: 18),
                  _SectionCard(
                    theme: theme,
                    title: 'Connection & calls',
                    child: Column(
                      children: [
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: Icon(_connection.callsAllowed ? Icons.verified_user_rounded : Icons.person_add_alt_1_rounded, color: _connection.callsAllowed ? theme.successColor : theme.accentColor),
                          title: Text(_connection.callsAllowed ? 'Contact request accepted by both' : _connection.isPendingIncoming ? 'Accept contact request' : _connection.isWaitingForOther ? 'Waiting for ${widget.contact.displayName}' : 'Accept this contact', style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w700)),
                          subtitle: Text(_connection.callsAllowed ? 'Voice and video call buttons are enabled.' : 'Messaging stays available. Calls unlock only after both people accept.', style: TextStyle(color: theme.secondaryTextColor)),
                          trailing: !_connection.myAccepted
                              ? FilledButton(onPressed: _busy ? null : _accept, child: const Text('Accept'))
                              : Icon(_connection.callsAllowed ? Icons.check_circle_rounded : Icons.schedule_rounded, color: _connection.callsAllowed ? theme.successColor : theme.secondaryTextColor),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  _SectionCard(
                    theme: theme,
                    title: 'Privacy & safety',
                    child: Column(
                      children: [
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: Icon(Icons.tune_rounded, color: theme.accentColor),
                          title: Text('Custom privacy for this person', style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w700)),
                          subtitle: Text('Delivery ticks, blue ticks, typing, recording, online and last seen', style: TextStyle(color: theme.secondaryTextColor)),
                          trailing: const Icon(Icons.chevron_right_rounded),
                          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => ContactPrivacyScreen(contact: widget.contact, relationshipService: widget.relationshipService))),
                        ),
                        const Divider(height: 1),
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: Icon(_blocked ? Icons.lock_open_rounded : Icons.block_rounded, color: theme.dangerColor),
                          title: Text(_blocked ? 'Unblock ${widget.contact.displayName}' : 'Block ${widget.contact.displayName}', style: TextStyle(color: theme.dangerColor, fontWeight: FontWeight.w700)),
                          subtitle: Text(_blocked ? 'Allow messages from this contact again.' : 'Stop new messages from this contact.', style: TextStyle(color: theme.secondaryTextColor)),
                          onTap: _busy ? null : _toggleBlock,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  _SectionCard(
                    theme: theme,
                    title: 'Media, links and documents',
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (media.isEmpty && links.isEmpty && documents.isEmpty)
                          Padding(
                            padding: const EdgeInsets.symmetric(vertical: 10),
                            child: Text('No shared media, links or documents yet.', style: TextStyle(color: theme.secondaryTextColor)),
                          ),
                        if (media.isNotEmpty) ...[
                          Text('${media.length} media item${media.length == 1 ? '' : 's'}', style: TextStyle(color: theme.secondaryTextColor, fontWeight: FontWeight.w700)),
                          const SizedBox(height: 8),
                          SizedBox(
                            height: 76,
                            child: ListView.separated(
                              scrollDirection: Axis.horizontal,
                              itemCount: media.length,
                              separatorBuilder: (_, __) => const SizedBox(width: 8),
                              itemBuilder: (context, index) {
                                final attachment = media[index].attachment!;
                                return InkWell(
                                  onTap: () => _openMedia(media[index]),
                                  borderRadius: BorderRadius.circular(12),
                                  child: Container(
                                    width: 116,
                                    padding: const EdgeInsets.all(9),
                                    decoration: BoxDecoration(color: theme.cardColor, borderRadius: BorderRadius.circular(12)),
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Icon(attachment.type == 'video' ? Icons.play_circle_outline_rounded : Icons.image_outlined, color: theme.accentColor),
                                        const Spacer(),
                                        Text(attachment.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: theme.primaryTextColor, fontSize: 10.5, fontWeight: FontWeight.w700)),
                                      ],
                                    ),
                                  ),
                                );
                              },
                            ),
                          ),
                        ],
                        if (links.isNotEmpty) ...[
                          const SizedBox(height: 12),
                          Text('${links.length} link${links.length == 1 ? '' : 's'}', style: TextStyle(color: theme.secondaryTextColor, fontWeight: FontWeight.w700)),
                          ...links.take(5).map((link) => Padding(
                                padding: const EdgeInsets.only(top: 6),
                                child: Row(children: [Icon(Icons.link_rounded, size: 16, color: theme.accentColor), const SizedBox(width: 7), Expanded(child: Text(link, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: theme.primaryTextColor, fontSize: 12)))]),
                              )),
                        ],
                        if (documents.isNotEmpty) ...[
                          const SizedBox(height: 12),
                          Text('${documents.length} document${documents.length == 1 ? '' : 's'}', style: TextStyle(color: theme.secondaryTextColor, fontWeight: FontWeight.w700)),
                          ...documents.take(5).map((message) => ListTile(
                                contentPadding: EdgeInsets.zero,
                                dense: true,
                                leading: Icon(Icons.description_outlined, color: theme.accentColor),
                                title: Text(message.attachment!.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w700)),
                                subtitle: Text(message.attachment!.size, style: TextStyle(color: theme.secondaryTextColor)),
                                onTap: () => _openMedia(message),
                              )),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  final ThemeConfig theme;
  final String title;
  final Widget child;

  const _SectionCard({required this.theme, required this.title, required this.child});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 10),
      decoration: BoxDecoration(color: theme.surfaceColor, borderRadius: BorderRadius.circular(16), border: Border.all(color: theme.cardColor)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: TextStyle(color: theme.primaryTextColor, fontSize: 12, fontWeight: FontWeight.w800)),
          child,
        ],
      ),
    );
  }
}
