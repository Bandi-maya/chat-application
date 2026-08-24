import 'package:flutter/material.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/templates/template_models.dart';

/// Lightweight, deterministic composite preview of a template layout.
/// Never instantiates backend streams or real data.
class TemplateCompositePreview extends StatelessWidget {
  final ChatyTemplateDefinition template;
  final bool isSelected;
  final double height;

  const TemplateCompositePreview({
    super.key,
    required this.template,
    this.isSelected = false,
    this.height = 200,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = context.colors;

    return Container(
      height: height,
      decoration: BoxDecoration(
        color: colors.surfaceSecondary,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isSelected ? colors.primary : colors.border,
          width: isSelected ? 2.0 : 1.0,
        ),
        boxShadow: isSelected
            ? [
                BoxShadow(
                  color: colors.primary.withValues(alpha: 0.18),
                  blurRadius: 16,
                  offset: const Offset(0, 4),
                )
              ]
            : null,
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          // 1. Mini Header
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: colors.surface,
              border: Border(bottom: BorderSide(color: colors.border, width: 0.8)),
            ),
            child: Row(
              children: [
                Container(
                  width: 24,
                  height: 24,
                  decoration: BoxDecoration(
                    color: colors.primary.withValues(alpha: 0.2),
                    shape: template.chatList.avatarShape == 'circle'
                        ? BoxShape.circle
                        : BoxShape.rectangle,
                    borderRadius: template.chatList.avatarShape != 'circle'
                        ? BorderRadius.circular(6)
                        : null,
                  ),
                  child: Center(
                    child: Text(
                      'C',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w800,
                        color: colors.primary,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  template.name,
                  style: theme.textTheme.labelMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                    color: colors.foreground,
                  ),
                ),
                const Spacer(),
                Icon(Icons.search_rounded, size: 16, color: colors.foregroundSecondary),
                const SizedBox(width: 8),
                Icon(Icons.more_vert_rounded, size: 16, color: colors.foregroundSecondary),
              ],
            ),
          ),

          // 2. Mini Stories Rail (if enabled)
          if (template.home.showStoriesStrip)
            Container(
              height: 42,
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              color: colors.surface.withValues(alpha: 0.5),
              child: Row(
                children: List.generate(4, (index) {
                  final isSquircle = template.home.storiesStyle == 'Squircle';
                  return Container(
                    width: 32,
                    margin: const EdgeInsets.only(right: 8),
                    decoration: BoxDecoration(
                      shape: isSquircle ? BoxShape.rectangle : BoxShape.circle,
                      borderRadius: isSquircle ? BorderRadius.circular(8) : null,
                      border: Border.all(
                        color: index == 0 ? colors.primary : colors.border,
                        width: 1.5,
                      ),
                      color: colors.surfaceSecondary,
                    ),
                    child: Center(
                      child: Icon(
                        index == 0 ? Icons.add_rounded : Icons.person_rounded,
                        size: 14,
                        color: index == 0 ? colors.primary : colors.foregroundSecondary,
                      ),
                    ),
                  );
                }),
              ),
            ),

          // 3. Mini Chat List / Conversation Segment
          Expanded(
            child: Container(
              padding: const EdgeInsets.all(10),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  _buildMiniChatRow(colors, 'Alex Rivera', 'Hey, did you see the new template?', '12:45'),
                  _buildMiniChatRow(colors, 'Design Team', 'The component overrides work perfectly', '12:30'),
                  Align(
                    alignment: Alignment.centerRight,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      decoration: BoxDecoration(
                        color: colors.primary,
                        borderRadius: BorderRadius.circular(template.conversation.bubbleCornerRadius),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            'Looks amazing!',
                            style: TextStyle(fontSize: 10, color: Colors.white, fontWeight: FontWeight.w600),
                          ),
                          SizedBox(width: 4),
                          Icon(Icons.done_all_rounded, size: 10, color: Colors.white70),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),

          // 4. Mini Bottom Navigation Dock
          Container(
            height: 38,
            padding: const EdgeInsets.symmetric(horizontal: 14),
            decoration: BoxDecoration(
              color: colors.surface,
              border: Border(top: BorderSide(color: colors.border, width: 0.8)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildMiniNavItem(Icons.chat_bubble_rounded, true, colors),
                _buildMiniNavItem(Icons.auto_stories_rounded, false, colors),
                if (template.navigation.hasCenterAction)
                  Container(
                    width: 28,
                    height: 28,
                    decoration: BoxDecoration(
                      color: colors.primary,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      template.navigation.centerActionIcon ?? Icons.camera_alt_rounded,
                      size: 16,
                      color: Colors.white,
                    ),
                  ),
                _buildMiniNavItem(Icons.call_rounded, false, colors),
                _buildMiniNavItem(Icons.person_rounded, false, colors),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMiniChatRow(AppColors colors, String name, String snippet, String time) {
    return Row(
      children: [
        Container(
          width: 22,
          height: 22,
          decoration: BoxDecoration(
            color: colors.surfaceElevated,
            shape: template.chatList.avatarShape == 'circle'
                ? BoxShape.circle
                : BoxShape.rectangle,
            borderRadius: template.chatList.avatarShape != 'circle'
                ? BorderRadius.circular(6)
                : null,
          ),
          child: Icon(Icons.person_rounded, size: 12, color: colors.foregroundSecondary),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(name, style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: colors.foreground)),
                  Text(time, style: TextStyle(fontSize: 8, color: colors.foregroundSecondary)),
                ],
              ),
              Text(
                snippet,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 9, color: colors.foregroundSecondary),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildMiniNavItem(IconData icon, bool active, AppColors colors) {
    return Icon(
      icon,
      size: 16,
      color: active ? colors.primary : colors.foregroundSecondary,
    );
  }
}
