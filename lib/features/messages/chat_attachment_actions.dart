import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:geolocator/geolocator.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../data/services/chat_media_service.dart';
import '../../data/services/gb_feature_backend_service.dart';
import '../../domain/models/chat_message.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';

class ChatAttachmentActions {
  ChatAttachmentActions({
    required this.conversationId,
    required this.dataStore,
    required this.preferencesController,
  });

  final String conversationId;
  final MockDataStore dataStore;
  final ChatyPreferencesController preferencesController;
  final ChatMediaService _media = ChatMediaService();
  final GbFeatureBackendService _server = GbFeatureBackendService();

  Future<void> shareMedia(BuildContext context, String type) async {
    try {
      final multiple = (type == 'image' && preferencesController.gbBool('Img_share_limit')) ||
          (type == 'document' && preferencesController.gbBool('key_more_docs_send'));
      if (multiple) {
        final items = await _media.pickAndUploadMultiple(conversationId: conversationId, type: type);
        for (final attachment in items) {
          await dataStore.sendMessage(
            conversationId: conversationId,
            text: '',
            type: _messageType(type),
            attachment: attachment,
          );
        }
        if (items.isNotEmpty) _toast(context, '${items.length} ${items.length == 1 ? 'file' : 'files'} sent.');
      } else {
        final attachment = await _media.pickAndUpload(conversationId: conversationId, type: type);
        if (attachment == null) return;
        await dataStore.sendMessage(
          conversationId: conversationId,
          text: '',
          type: _messageType(type),
          attachment: attachment,
        );
      }
    } catch (error) {
      _toast(context, 'Unable to send $type: $error');
    }
  }

  Future<void> shareLocation(BuildContext context) async {
    try {
      final enabled = await Geolocator.isLocationServiceEnabled();
      if (!enabled) throw Exception('Location services are disabled. Enable GPS and try again.');
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) throw Exception('Location permission was denied.');
      if (permission == LocationPermission.deniedForever) {
        throw Exception('Location permission is permanently denied. Enable it in Android app settings.');
      }
      final position = await Geolocator.getCurrentPosition();
      final latitude = position.latitude;
      final longitude = position.longitude;
      final mapsUrl = 'https://maps.google.com/?q=$latitude,$longitude';
      await _server.sendStructuredMessage(
        conversationId: conversationId,
        type: 'location',
        body: '📍 Location\n$mapsUrl',
        metadata: <String, dynamic>{
          'latitude': latitude,
          'longitude': longitude,
          'accuracy_meters': position.accuracy,
          'maps_url': mapsUrl,
        },
      );
      await dataStore.ensureConversationLoaded(conversationId);
      _toast(context, 'Location sent.');
    } catch (error) {
      _toast(context, 'Unable to share location: $error');
    }
  }

  Future<void> shareContact(BuildContext context) async {
    try {
      final status = await FlutterContacts.permissions.request(PermissionType.read);
      final canRead = status == PermissionStatus.granted || status == PermissionStatus.limited;
      final picked = await FlutterContacts.native.showPicker(
        properties: canRead ? <ContactProperty>{ContactProperty.phone, ContactProperty.email} : null,
      );
      if (picked == null) return;
      Contact contact = picked;
      if (canRead && picked.id != null) {
        contact = await FlutterContacts.get(
              picked.id!,
              properties: <ContactProperty>{ContactProperty.phone, ContactProperty.email},
            ) ??
            picked;
      }
      final name = (contact.displayName ?? '').trim().isEmpty ? 'Contact' : contact.displayName!.trim();
      final phones = contact.phones.map((item) => item.number.trim()).where((value) => value.isNotEmpty).toList(growable: false);
      final emails = contact.emails.map((item) => item.address.trim()).where((value) => value.isNotEmpty).toList(growable: false);
      final lines = <String>['👤 $name'];
      lines.addAll(phones.take(5).map((value) => '📞 $value'));
      lines.addAll(emails.take(3).map((value) => '✉️ $value'));
      await _server.sendStructuredMessage(
        conversationId: conversationId,
        type: 'contact',
        body: lines.join('\n'),
        metadata: <String, dynamic>{'contact_name': name, 'phones': phones, 'emails': emails},
      );
      await dataStore.ensureConversationLoaded(conversationId);
      _toast(context, 'Contact sent.');
    } catch (error) {
      _toast(context, 'Unable to share contact: $error');
    }
  }

  Future<void> createPoll(BuildContext context) async {
    final payload = await showDialog<_PollDraft>(
      context: context,
      builder: (dialogContext) => const _CreatePollDialog(),
    );
    if (payload == null) return;
    try {
      await _server.createPoll(
        conversationId: conversationId,
        question: payload.question,
        options: payload.options,
        allowMultiple: payload.allowMultiple,
      );
      await dataStore.ensureConversationLoaded(conversationId);
      _toast(context, 'Poll created.');
    } catch (error) {
      _toast(context, 'Unable to create poll: $error');
    }
  }

  Future<void> openPoll(BuildContext context, String messageId) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => _PollViewerSheet(messageId: messageId, server: _server),
    );
    await dataStore.ensureConversationLoaded(conversationId);
  }

  Future<void> recordVoiceNote(BuildContext context) async {
    final result = await showModalBottomSheet<_VoiceNoteResult>(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      enableDrag: false,
      builder: (_) => const _VoiceNoteRecorderSheet(),
    );
    if (result == null) return;
    try {
      final attachment = await _media.uploadFile(
        conversationId: conversationId,
        type: 'audio',
        sourcePath: result.path,
        displayName: 'voice_note_${DateTime.now().millisecondsSinceEpoch}.m4a',
        durationSeconds: result.durationSeconds,
      );
      await dataStore.sendMessage(
        conversationId: conversationId,
        text: '',
        type: MessageType.audio,
        attachment: attachment,
      );
      _toast(context, 'Voice note sent.');
    } catch (error) {
      _toast(context, 'Unable to send voice note: $error');
    }
  }

  static bool isPollMessage(ChatMessage message) => message.text.startsWith('[POLL] ');

  static MessageType _messageType(String type) {
    switch (type) {
      case 'image': return MessageType.image;
      case 'video': return MessageType.video;
      case 'audio': return MessageType.audio;
      case 'document': return MessageType.document;
      default: return MessageType.document;
    }
  }

  static void _toast(BuildContext context, String message) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
}

class _PollDraft {
  final String question;
  final List<String> options;
  final bool allowMultiple;
  const _PollDraft(this.question, this.options, this.allowMultiple);
}

class _CreatePollDialog extends StatefulWidget {
  const _CreatePollDialog();

  @override
  State<_CreatePollDialog> createState() => _CreatePollDialogState();
}

class _CreatePollDialogState extends State<_CreatePollDialog> {
  final TextEditingController _question = TextEditingController();
  final List<TextEditingController> _options = <TextEditingController>[
    TextEditingController(),
    TextEditingController(),
  ];
  bool _multiple = false;

  @override
  void dispose() {
    _question.dispose();
    for (final controller in _options) controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Create poll'),
      content: SizedBox(
        width: 420,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: _question, autofocus: true, maxLength: 240, decoration: const InputDecoration(labelText: 'Question')),
              ...List.generate(_options.length, (index) => Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Row(
                      children: [
                        Expanded(child: TextField(controller: _options[index], maxLength: 120, decoration: InputDecoration(labelText: 'Option ${index + 1}', counterText: ''))),
                        if (_options.length > 2)
                          IconButton(
                            tooltip: 'Remove option',
                            onPressed: () => setState(() {
                              final removed = _options.removeAt(index);
                              removed.dispose();
                            }),
                            icon: const Icon(Icons.remove_circle_outline_rounded),
                          ),
                      ],
                    ),
                  )),
              if (_options.length < 12)
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton.icon(
                    onPressed: () => setState(() => _options.add(TextEditingController())),
                    icon: const Icon(Icons.add_rounded),
                    label: const Text('Add option'),
                  ),
                ),
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                value: _multiple,
                title: const Text('Allow multiple answers'),
                onChanged: (value) => setState(() => _multiple = value),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(
          onPressed: () {
            final question = _question.text.trim();
            final options = _options.map((item) => item.text.trim()).where((value) => value.isNotEmpty).toList(growable: false);
            if (question.isEmpty || options.length < 2) return;
            Navigator.pop(context, _PollDraft(question, options, _multiple));
          },
          child: const Text('Create'),
        ),
      ],
    );
  }
}

class _PollViewerSheet extends StatefulWidget {
  final String messageId;
  final GbFeatureBackendService server;
  const _PollViewerSheet({required this.messageId, required this.server});

  @override
  State<_PollViewerSheet> createState() => _PollViewerSheetState();
}

class _PollViewerSheetState extends State<_PollViewerSheet> {
  late Future<Map<String, dynamic>> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<Map<String, dynamic>> _load() async {
    final raw = await Supabase.instance.client.rpc('get_poll', params: <String, dynamic>{'p_message_id': widget.messageId});
    if (raw is Map) return Map<String, dynamic>.from(raw);
    throw Exception('Poll data is unavailable.');
  }

  Future<void> _vote(String optionId) async {
    try {
      await widget.server.votePoll(messageId: widget.messageId, optionId: optionId);
      if (mounted) setState(() => _future = _load());
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Unable to vote: $error')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(16, 14, 16, 18 + MediaQuery.viewPaddingOf(context).bottom),
      child: FutureBuilder<Map<String, dynamic>>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const SizedBox(height: 220, child: Center(child: CircularProgressIndicator()));
          }
          if (snapshot.hasError) return SizedBox(height: 180, child: Center(child: Text('${snapshot.error}')));
          final poll = snapshot.data ?? const <String, dynamic>{};
          final rawOptions = poll['options'];
          final options = rawOptions is List ? rawOptions.whereType<Map>().map((item) => Map<String, dynamic>.from(item)).toList(growable: false) : <Map<String, dynamic>>[];
          final totalVotes = poll['total_votes'] is num ? (poll['total_votes'] as num).round() : 0;
          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.poll_rounded),
                  const SizedBox(width: 8),
                  Expanded(child: Text(poll['question']?.toString() ?? 'Poll', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800))),
                  IconButton(onPressed: () => Navigator.pop(context), icon: const Icon(Icons.close_rounded)),
                ],
              ),
              const SizedBox(height: 8),
              Flexible(
                child: ListView.separated(
                  shrinkWrap: true,
                  itemCount: options.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    final option = options[index];
                    final votes = option['votes'] is num ? (option['votes'] as num).round() : 0;
                    final progress = totalVotes == 0 ? 0.0 : votes / totalVotes;
                    final mine = option['voted_by_me'] == true;
                    return InkWell(
                      borderRadius: BorderRadius.circular(14),
                      onTap: () => _vote(option['id'].toString()),
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          border: Border.all(color: mine ? Theme.of(context).colorScheme.primary : Theme.of(context).colorScheme.outlineVariant),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(children: [
                              Expanded(child: Text(option['label']?.toString() ?? 'Option', style: const TextStyle(fontWeight: FontWeight.w700))),
                              if (mine) const Icon(Icons.check_circle_rounded, size: 19),
                              const SizedBox(width: 6),
                              Text('$votes'),
                            ]),
                            const SizedBox(height: 8),
                            LinearProgressIndicator(value: progress.clamp(0, 1)),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
              const SizedBox(height: 10),
              Text('$totalVotes total vote${totalVotes == 1 ? '' : 's'}${poll['allow_multiple'] == true ? ' • multiple answers allowed' : ''}'),
            ],
          );
        },
      ),
    );
  }
}

class _VoiceNoteResult {
  final String path;
  final int durationSeconds;
  const _VoiceNoteResult(this.path, this.durationSeconds);
}

class _VoiceNoteRecorderSheet extends StatefulWidget {
  const _VoiceNoteRecorderSheet();

  @override
  State<_VoiceNoteRecorderSheet> createState() => _VoiceNoteRecorderSheetState();
}

class _VoiceNoteRecorderSheetState extends State<_VoiceNoteRecorderSheet> {
  final AudioRecorder _recorder = AudioRecorder();
  Timer? _timer;
  DateTime? _startedAt;
  String? _path;
  int _seconds = 0;
  bool _recording = false;
  bool _busy = false;

  @override
  void dispose() {
    _timer?.cancel();
    unawaited(_recorder.dispose());
    super.dispose();
  }

  Future<void> _start() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      if (!await _recorder.hasPermission()) throw Exception('Microphone permission is required.');
      final directory = await getTemporaryDirectory();
      final path = '${directory.path}/chaty_voice_${DateTime.now().millisecondsSinceEpoch}.m4a';
      await _recorder.start(
        const RecordConfig(
          encoder: AudioEncoder.aacLc,
          bitRate: 96000,
          sampleRate: 44100,
          numChannels: 1,
          autoGain: true,
          echoCancel: true,
          noiseSuppress: true,
        ),
        path: path,
      );
      _path = path;
      _startedAt = DateTime.now();
      _seconds = 0;
      _timer = Timer.periodic(const Duration(seconds: 1), (_) {
        if (!mounted) return;
        setState(() => _seconds = DateTime.now().difference(_startedAt!).inSeconds);
      });
      if (mounted) setState(() => _recording = true);
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$error')));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _finish({required bool send}) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final stopped = _recording ? await _recorder.stop() : _path;
      _timer?.cancel();
      final seconds = _startedAt == null ? _seconds : DateTime.now().difference(_startedAt!).inSeconds;
      if (!mounted) return;
      if (send && stopped != null && stopped.isNotEmpty && seconds > 0) {
        Navigator.pop(context, _VoiceNoteResult(stopped, seconds));
      } else {
        Navigator.pop(context);
      }
    } finally {
      if (mounted) setState(() {
        _busy = false;
        _recording = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final minute = (_seconds ~/ 60).toString().padLeft(2, '0');
    final second = (_seconds % 60).toString().padLeft(2, '0');
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 18, 20, 18 + MediaQuery.viewInsetsOf(context).bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text('Voice note', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 18),
          AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            width: 78,
            height: 78,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: _recording ? Theme.of(context).colorScheme.errorContainer : Theme.of(context).colorScheme.primaryContainer,
            ),
            child: Icon(_recording ? Icons.graphic_eq_rounded : Icons.mic_rounded, size: 36),
          ),
          const SizedBox(height: 12),
          Text('$minute:$second', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontFeatures: const <FontFeature>[])),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(child: OutlinedButton.icon(onPressed: _busy ? null : () => _finish(send: false), icon: const Icon(Icons.close_rounded), label: const Text('Cancel'))),
              const SizedBox(width: 10),
              Expanded(
                child: FilledButton.icon(
                  onPressed: _busy ? null : (_recording ? () => _finish(send: true) : _start),
                  icon: Icon(_recording ? Icons.send_rounded : Icons.fiber_manual_record_rounded),
                  label: Text(_recording ? 'Stop & send' : 'Record'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
