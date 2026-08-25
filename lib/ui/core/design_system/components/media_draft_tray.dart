import 'package:flutter/material.dart';

import '../chaty_haptics.dart';

class ChatyMediaDraftItem {
  const ChatyMediaDraftItem({
    required this.id,
    required this.preview,
    required this.label,
    this.subtitle,
  });

  final String id;
  final Widget preview;
  final String label;
  final String? subtitle;
}

/// Review surface shown after picking media and before upload/send.
///
/// It keeps destructive/remove actions local to the draft and makes the send
/// action explicit, preventing accidental immediate uploads from the picker.
class ChatyMediaDraftTray extends StatelessWidget {
  const ChatyMediaDraftTray({
    super.key,
    required this.items,
    required this.onRemove,
    required this.onSend,
    this.captionController,
    this.onAddMore,
    this.sending = false,
  });

  final List<ChatyMediaDraftItem> items;
  final ValueChanged<String> onRemove;
  final VoidCallback onSend;
  final VoidCallback? onAddMore;
  final TextEditingController? captionController;
  final bool sending;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const SizedBox.shrink();
    final scheme = Theme.of(context).colorScheme;

    return Material(
      color: scheme.surface,
      borderRadius: BorderRadius.circular(22),
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 10),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    items.length == 1
                        ? 'Ready to send'
                        : '${items.length} items ready',
                    style: TextStyle(
                      color: scheme.onSurface,
                      fontWeight: FontWeight.w800,
                      fontSize: 14,
                    ),
                  ),
                ),
                if (onAddMore != null)
                  Semantics(
                    button: true,
                    label: 'Add more attachments',
                    child: IconButton(
                      onPressed: sending ? null : onAddMore,
                      icon: const Icon(Icons.add_rounded),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            SizedBox(
              height: 104,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: items.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (context, index) {
                  final item = items[index];
                  return _DraftTile(
                    item: item,
                    enabled: !sending,
                    onRemove: () {
                      ChatyHaptics.warning();
                      onRemove(item.id);
                    },
                  );
                },
              ),
            ),
            if (captionController != null) ...[
              const SizedBox(height: 10),
              TextField(
                controller: captionController,
                enabled: !sending,
                minLines: 1,
                maxLines: 3,
                textInputAction: TextInputAction.newline,
                decoration: InputDecoration(
                  hintText: 'Add a caption…',
                  isDense: true,
                  filled: true,
                  fillColor: scheme.surfaceContainerHighest,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(16),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
            ],
            const SizedBox(height: 10),
            Row(
              children: [
                Icon(Icons.lock_rounded, size: 15, color: scheme.primary),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    'Encrypted before upload',
                    style: TextStyle(
                      color: scheme.onSurfaceVariant,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                FilledButton.icon(
                  onPressed: sending
                      ? null
                      : () {
                          ChatyHaptics.success();
                          onSend();
                        },
                  icon: sending
                      ? const SizedBox.square(
                          dimension: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.send_rounded, size: 18),
                  label: Text(sending ? 'Sending' : 'Send'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _DraftTile extends StatelessWidget {
  const _DraftTile({
    required this.item,
    required this.enabled,
    required this.onRemove,
  });

  final ChatyMediaDraftItem item;
  final bool enabled;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SizedBox(
      width: 96,
      child: Stack(
        children: [
          Positioned.fill(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(16),
              child: ColoredBox(
                color: scheme.surfaceContainerHighest,
                child: item.preview,
              ),
            ),
          ),
          Positioned(
            left: 6,
            right: 6,
            bottom: 6,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: .58),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                child: Text(
                  item.label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ),
          Positioned(
            right: 4,
            top: 4,
            child: Semantics(
              button: true,
              label: 'Remove ${item.label}',
              child: InkResponse(
                onTap: enabled ? onRemove : null,
                radius: 18,
                child: Container(
                  width: 30,
                  height: 30,
                  decoration: const BoxDecoration(
                    color: Color(0xB8000000),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.close_rounded,
                    color: Colors.white,
                    size: 17,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
