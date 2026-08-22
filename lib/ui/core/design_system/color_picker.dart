import 'package:flutter/material.dart';
import '../theme/theme_config.dart';
import '../theme/theme_extensions.dart';

class ChatyColorPickerModal extends StatefulWidget {
  final String title;
  final Color currentColor;
  final Color? backgroundContextColor;
  final ValueChanged<Color> onColorSelected;

  const ChatyColorPickerModal({
    super.key,
    required this.title,
    required this.currentColor,
    this.backgroundContextColor,
    required this.onColorSelected,
  });

  static Future<Color?> show(
    BuildContext context, {
    required String title,
    required Color currentColor,
    Color? backgroundContextColor,
  }) {
    return showModalBottomSheet<Color>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => ChatyColorPickerModal(
        title: title,
        currentColor: currentColor,
        backgroundContextColor: backgroundContextColor,
        onColorSelected: (c) => Navigator.of(ctx).pop(c),
      ),
    );
  }

  @override
  State<ChatyColorPickerModal> createState() => _ChatyColorPickerModalState();
}

class _ChatyColorPickerModalState extends State<ChatyColorPickerModal> {
  late Color _color;
  late TextEditingController _hexController;

  static const List<Color> _presetSwatches = [
    Color(0xFF6366F1), // Indigo Accent
    Color(0xFF3B82F6), // Blue
    Color(0xFF10B981), // Emerald
    Color(0xFFEC4899), // Pink
    Color(0xFF8B5CF6), // Purple
    Color(0xFFF59E0B), // Amber
    Color(0xFFEF4444), // Red
    Color(0xFF14B8A6), // Teal
    Color(0xFF06B6D4), // Cyan
    Color(0xFF64748B), // Slate
    Color(0xFF000000), // Black
    Color(0xFFFFFFFF), // White
  ];

  @override
  void initState() {
    super.initState();
    _color = widget.currentColor;
    _hexController = TextEditingController(text: _colorToHex(_color));
  }

  @override
  void dispose() {
    _hexController.dispose();
    super.dispose();
  }

  String _colorToHex(Color color) {
    return color
        .toARGB32()
        .toRadixString(16)
        .padLeft(8, '0')
        .toUpperCase()
        .substring(2);
  }

  void _updateFromHex(String text) {
    final clean = text.replaceAll('#', '').trim();
    if (clean.length == 6) {
      final val = int.tryParse('FF$clean', radix: 16);
      if (val != null) {
        setState(() {
          _color = Color(val);
        });
      }
    }
  }

  void _autoFixContrast() {
    if (widget.backgroundContextColor == null) return;
    final bg = widget.backgroundContextColor!;
    final double bgLum = bg.computeLuminance();

    // Pick crisp dark or crisp white based on background luminance
    Color adjusted = bgLum > 0.5
        ? context.colors.foreground
        : context.colors.onPrimary;
    setState(() {
      _color = adjusted;
      _hexController.text = _colorToHex(_color);
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final contrastRatio = widget.backgroundContextColor != null
        ? ThemeConfig.calculateContrastRatio(
            _color,
            widget.backgroundContextColor!,
          )
        : null;
    final hasContrastWarning = contrastRatio != null && contrastRatio < 4.5;

    return Container(
      decoration: BoxDecoration(
        color: theme.dialogTheme.backgroundColor ?? theme.colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  color: _color,
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 2),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  widget.title,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.close_rounded),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ],
          ),
          const SizedBox(height: 16),
          // Preview Card
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: widget.backgroundContextColor ?? theme.cardColor,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: theme.dividerColor.withValues(alpha: 0.2),
              ),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    'Sample Text Preview',
                    style: TextStyle(
                      color: _color,
                      fontSize: 15,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                if (contrastRatio != null) ...[
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: (hasContrastWarning
                              ? context.colors.error
                              : context.colors.success)
                          .withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          hasContrastWarning
                              ? Icons.warning_amber_rounded
                              : Icons.check_circle_rounded,
                          size: 14,
                          color: hasContrastWarning
                              ? context.colors.error
                              : context.colors.success,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${contrastRatio.toStringAsFixed(1)}:1',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: hasContrastWarning
                                ? context.colors.error
                                : context.colors.success,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
          if (hasContrastWarning) ...[
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Low contrast warning! Text may be hard to read.',
                    style: TextStyle(color: context.colors.error, fontSize: 12),
                  ),
                ),
                TextButton.icon(
                  onPressed: _autoFixContrast,
                  icon: const Icon(Icons.auto_fix_high_rounded, size: 16),
                  label: const Text(
                    'Auto Fix Contrast',
                    style: TextStyle(fontSize: 12),
                  ),
                  style: TextButton.styleFrom(
                    foregroundColor: context.colors.primary,
                  ),
                ),
              ],
            ),
          ],
          const SizedBox(height: 16),
          const Text(
            'Preset Swatches',
            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: _presetSwatches.map((c) {
              final isSel = c.toARGB32() == _color.toARGB32();
              return GestureDetector(
                onTap: () {
                  setState(() {
                    _color = c;
                    _hexController.text = _colorToHex(c);
                  });
                },
                child: Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: c,
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: isSel ? context.colors.primary : context.colors.border,
                      width: isSel ? 3 : 1.5,
                    ),
                  ),
                  child: isSel
                      ? Icon(
                          Icons.check_rounded,
                          size: 20,
                          color: c.computeLuminance() > 0.5
                              ? Colors.black
                              : Colors.white,
                        )
                      : null,
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _hexController,
                  decoration: InputDecoration(
                    labelText: 'HEX Color',
                    prefixText: '# ',
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  onChanged: _updateFromHex,
                ),
              ),
              const SizedBox(width: 12),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: context.colors.primary,
                  foregroundColor: context.colors.onPrimary,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 16,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                onPressed: () => widget.onColorSelected(_color),
                child: const Text(
                  'Apply Color',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
              ),
            ],
          ),

        ],
      ),
    );
  }
}
