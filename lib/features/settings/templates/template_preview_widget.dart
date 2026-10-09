import 'package:flutter/material.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/templates/template_models.dart';

/// Lightweight deterministic previews. Each template deliberately has its own
/// composition so choosing a template communicates the actual layout identity.
/// This widget never starts app data streams or touches persisted user data.
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

  Color get _accent => switch (template.id) {
    ChatyTemplateId.visualSocial => const Color(0xFFE45D9C),
    ChatyTemplateId.stream => const Color(0xFF12A7B8),
    ChatyTemplateId.cameraFirst => const Color(0xFFF0A43A),
    ChatyTemplateId.messageFirst => const Color(0xFF4388F5),
    ChatyTemplateId.powerChat => const Color(0xFF20A985),
    ChatyTemplateId.community => const Color(0xFF8067E8),
  };

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final accent = _accent;
    return Container(
      height: height,
      decoration: BoxDecoration(
        color: colors.surfaceSecondary,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isSelected ? accent : colors.border,
          width: isSelected ? 2 : 1,
        ),
        boxShadow: isSelected
            ? [
                BoxShadow(
                  color: accent.withValues(alpha: 0.18),
                  blurRadius: 15,
                  offset: const Offset(0, 4),
                ),
              ]
            : null,
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          _buildHeader(colors, accent),
          Expanded(child: _buildLayout(colors, accent)),
          _buildNavigation(colors, accent),
        ],
      ),
    );
  }

  Widget _buildHeader(AppColors colors, Color accent) {
    final headerStyle = template.home.headerStyle;
    return Container(
      height: headerStyle == HomeHeaderStyle.prominentIdentity ? 38 : 32,
      padding: const EdgeInsets.symmetric(horizontal: 11),
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border(bottom: BorderSide(color: colors.border, width: 0.7)),
      ),
      child: Row(
        children: [
          Container(
            width: headerStyle == HomeHeaderStyle.prominentIdentity ? 25 : 21,
            height: headerStyle == HomeHeaderStyle.prominentIdentity ? 25 : 21,
            decoration: BoxDecoration(
              color: accent.withValues(alpha: 0.18),
              shape: template.chatList.avatarShape == 'circle'
                  ? BoxShape.circle
                  : BoxShape.rectangle,
              borderRadius: template.chatList.avatarShape == 'circle'
                  ? null
                  : BorderRadius.circular(7),
            ),
            child: Icon(
              template.id == ChatyTemplateId.community
                  ? Icons.diversity_3_rounded
                  : template.id == ChatyTemplateId.cameraFirst
                  ? Icons.camera_alt_rounded
                  : Icons.chat_bubble_rounded,
              size: 13,
              color: accent,
            ),
          ),
          const SizedBox(width: 7),
          Expanded(
            child: Text(
              template.id == ChatyTemplateId.community
                  ? 'Spaces'
                  : template.id == ChatyTemplateId.visualSocial
                  ? 'Your day'
                  : template.id == ChatyTemplateId.cameraFirst
                  ? 'Create'
                  : template.id == ChatyTemplateId.powerChat
                  ? 'Chat tools'
                  : template.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: headerStyle == HomeHeaderStyle.prominentIdentity
                    ? 12
                    : 10.5,
                fontWeight: FontWeight.w800,
                color: colors.foreground,
              ),
            ),
          ),
          if (headerStyle == HomeHeaderStyle.searchForward)
            Container(
              width: 74,
              height: 20,
              decoration: BoxDecoration(
                color: colors.surfaceSecondary,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(Icons.search_rounded,
                  size: 12, color: colors.foregroundSecondary),
            )
          else ...[
            Icon(Icons.search_rounded,
                size: 14, color: colors.foregroundSecondary),
            const SizedBox(width: 9),
            Icon(Icons.more_horiz_rounded,
                size: 15, color: colors.foregroundSecondary),
          ],
        ],
      ),
    );
  }

  Widget _buildLayout(AppColors colors, Color accent) {
    return switch (template.id) {
      ChatyTemplateId.visualSocial => _buildVisualSocial(colors, accent),
      ChatyTemplateId.stream => _buildStream(colors, accent),
      ChatyTemplateId.cameraFirst => _buildCameraFirst(colors, accent),
      ChatyTemplateId.messageFirst => _buildMessageFirst(colors, accent),
      ChatyTemplateId.powerChat => _buildPowerChat(colors, accent),
      ChatyTemplateId.community => _buildCommunity(colors, accent),
    };
  }

  Widget _buildVisualSocial(AppColors colors, Color accent) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(9, 7, 9, 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: List.generate(5, (i) {
              final size = i == 0 ? 31.0 : 27.0;
              return Expanded(
                child: Center(
                  child: Container(
                    width: size,
                    height: size,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: i == 0 ? accent : accent.withValues(alpha: .55),
                        width: 2,
                      ),
                      color: i == 0
                          ? accent.withValues(alpha: .14)
                          : colors.surface,
                    ),
                    child: Icon(
                      i == 0 ? Icons.add_rounded : Icons.person_rounded,
                      size: 14,
                      color: accent,
                    ),
                  ),
                ),
              );
            }),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: Row(
              children: [
                Expanded(
                  flex: 6,
                  child: _mediaTile(colors, accent, Icons.landscape_rounded,
                      'Highlights', tall: true),
                ),
                const SizedBox(width: 6),
                Expanded(
                  flex: 5,
                  child: Column(
                    children: [
                      Expanded(
                        child: _mediaTile(colors, accent, Icons.music_note_rounded,
                            'Audio'),
                      ),
                      const SizedBox(height: 5),
                      Expanded(
                        child: _mediaTile(colors, accent, Icons.favorite_rounded,
                            'Friends'),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStream(AppColors colors, Color accent) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      child: Column(
        children: [
          Row(
            children: [
              _chip('ALL', accent, selected: true),
              const SizedBox(width: 5),
              _chip('UNREAD', accent),
              const SizedBox(width: 5),
              _chip('GROUPS', accent),
              const Spacer(),
              Icon(Icons.filter_list_rounded,
                  size: 14, color: colors.foregroundSecondary),
            ],
          ),
          const SizedBox(height: 3),
          Expanded(
            child: Column(
              children: List.generate(
                3,
                (i) => Expanded(
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 3),
                    decoration: BoxDecoration(
                      border: i == 2
                          ? null
                          : Border(bottom: BorderSide(color: colors.border, width: .6)),
                    ),
                    child: Row(
                      children: [
                        Container(
                          width: 24,
                          height: 24,
                          decoration: BoxDecoration(
                            color: accent.withValues(alpha: .12),
                            borderRadius: BorderRadius.circular(5),
                          ),
                          child: Icon(
                            i == 1 ? Icons.groups_rounded : Icons.person_rounded,
                            size: 13,
                            color: accent,
                          ),
                        ),
                        const SizedBox(width: 7),
                        Expanded(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Container(
                                width: i == 0 ? 75 : 60,
                                height: 5,
                                decoration: BoxDecoration(
                                  color: colors.foreground.withValues(alpha: .75),
                                  borderRadius: BorderRadius.circular(3),
                                ),
                              ),
                              const SizedBox(height: 4),
                              Container(
                                width: i == 2 ? 82 : 104,
                                height: 3,
                                decoration: BoxDecoration(
                                  color: colors.foregroundSecondary.withValues(alpha: .45),
                                  borderRadius: BorderRadius.circular(3),
                                ),
                              ),
                            ],
                          ),
                        ),
                        Text(
                          i == 0 ? 'now' : i == 1 ? '2m' : '8m',
                          style: TextStyle(fontSize: 7, color: colors.foregroundSecondary),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCameraFirst(AppColors colors, Color accent) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(10, 8, 10, 6),
      child: Row(
        children: [
          Expanded(
            flex: 5,
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(13),
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    accent.withValues(alpha: .35),
                    colors.surface,
                    accent.withValues(alpha: .12),
                  ],
                ),
                border: Border.all(color: accent.withValues(alpha: .45)),
              ),
              child: Stack(
                children: [
                  Positioned(
                    top: 8,
                    left: 8,
                    child: Icon(Icons.auto_awesome_rounded, size: 13, color: accent),
                  ),
                  Center(
                    child: Container(
                      width: 38,
                      height: 38,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(color: accent, width: 3),
                      ),
                      child: Center(
                        child: Container(
                          width: 27,
                          height: 27,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: accent,
                          ),
                          child: const Icon(Icons.camera_alt_rounded,
                              size: 14, color: Colors.white),
                        ),
                      ),
                    ),
                  ),
                  const Positioned(
                    left: 8,
                    bottom: 7,
                    child: Text('QUICK CAPTURE',
                        style: TextStyle(fontSize: 7, fontWeight: FontWeight.w900,
                            letterSpacing: .7)),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            flex: 4,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _quickAction(colors, accent, Icons.photo_library_rounded, 'Gallery'),
                _quickAction(colors, accent, Icons.bolt_rounded, 'Effects'),
                _quickAction(colors, accent, Icons.send_rounded, 'Send'),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMessageFirst(AppColors colors, Color accent) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(11, 8, 11, 7),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _messageLine(colors, accent, 'Alex Rivera', 'Are we still on for 6?', '12:45'),
          _messageLine(colors, accent, 'Design Team', 'The new build is ready', '12:30',
              group: true),
          Align(
            alignment: Alignment.centerRight,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 6),
              decoration: BoxDecoration(
                color: accent,
                borderRadius: BorderRadius.circular(template.conversation.bubbleCornerRadius),
              ),
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Sounds good!', style: TextStyle(fontSize: 9,
                      color: Colors.white, fontWeight: FontWeight.w700)),
                  SizedBox(width: 4),
                  Icon(Icons.done_all_rounded, size: 10, color: Colors.white70),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPowerChat(AppColors colors, Color accent) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(9, 6, 9, 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              _chip('All', accent, selected: true),
              const SizedBox(width: 4),
              _chip('Direct', accent),
              const SizedBox(width: 4),
              _chip('Groups', accent),
              const SizedBox(width: 4),
              _chip('Pinned', accent),
            ],
          ),
          const SizedBox(height: 6),
          Expanded(
            child: Row(
              children: [
                Container(
                  width: 25,
                  decoration: BoxDecoration(
                    color: accent.withValues(alpha: .10),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      Icon(Icons.chat_bubble_rounded, size: 12, color: accent),
                      Icon(Icons.star_rounded, size: 12, color: colors.foregroundSecondary),
                      Icon(Icons.archive_rounded, size: 12, color: colors.foregroundSecondary),
                    ],
                  ),
                ),
                const SizedBox(width: 7),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      _miniDenseRow(colors, accent, 'Operations'),
                      _miniDenseRow(colors, accent, 'Maya'),
                      _miniDenseRow(colors, accent, 'Project group'),
                    ],
                  ),
                ),
              ],
            ),
          ),
          Container(
            height: 17,
            padding: const EdgeInsets.symmetric(horizontal: 8),
            decoration: BoxDecoration(
              color: colors.surface,
              borderRadius: BorderRadius.circular(7),
              border: Border.all(color: colors.border),
            ),
            child: Row(
              children: [
                Icon(Icons.add_circle_outline_rounded, size: 10, color: accent),
                const SizedBox(width: 5),
                Expanded(child: Text('Message or command…',
                    style: TextStyle(fontSize: 7, color: colors.foregroundSecondary))),
                Icon(Icons.send_rounded, size: 10, color: accent),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCommunity(AppColors colors, Color accent) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(10, 7, 10, 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            height: 31,
            padding: const EdgeInsets.symmetric(horizontal: 9),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [accent.withValues(alpha: .28), accent.withValues(alpha: .08)],
              ),
              borderRadius: BorderRadius.circular(9),
              border: Border.all(color: accent.withValues(alpha: .3)),
            ),
            child: Row(
              children: [
                Icon(Icons.hub_rounded, color: accent, size: 17),
                const SizedBox(width: 7),
                const Expanded(child: Text('Design Community',
                    style: TextStyle(fontSize: 9, fontWeight: FontWeight.w800))),
                Icon(Icons.chevron_right_rounded, color: accent, size: 14),
              ],
            ),
          ),
          const SizedBox(height: 6),
          Expanded(
            child: Row(
              children: [
                Expanded(child: _spaceCard(colors, accent, Icons.forum_rounded, 'General', '8 new')),
                const SizedBox(width: 6),
                Expanded(child: _spaceCard(colors, accent, Icons.campaign_rounded, 'Updates', '3 new')),
                const SizedBox(width: 6),
                Expanded(child: _spaceCard(colors, accent, Icons.groups_rounded, 'Teams', '5 new')),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNavigation(AppColors colors, Color accent) {
    final layout = template.navigation.layout;
    final isFloating = layout == NavigationLayoutType.floatingPill;
    final isIdentity = layout == NavigationLayoutType.identityBar;
    final isUtility = layout == NavigationLayoutType.utilityBar;
    final hasCenter = template.navigation.hasCenterAction ||
        layout == NavigationLayoutType.centerActionDock;
    return Container(
      height: layout == NavigationLayoutType.centerActionDock ? 36 : 31,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 3),
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border(top: BorderSide(color: colors.border, width: .7)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _navItem(colors, accent, Icons.chat_bubble_rounded, 'Chats', true, isIdentity),
          _navItem(colors, accent, Icons.auto_stories_rounded, 'Updates', false, isIdentity),
          if (hasCenter)
            Container(
              width: 28,
              height: 28,
              decoration: BoxDecoration(
                color: accent,
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(color: accent.withValues(alpha: .25), blurRadius: 5),
                ],
              ),
              child: Icon(
                template.navigation.centerActionIcon ?? Icons.camera_alt_rounded,
                size: 15,
                color: Colors.white,
              ),
            ),
          if (isUtility)
            _navItem(colors, accent, Icons.grid_view_rounded, 'Tools', false, false),
          _navItem(colors, accent, Icons.call_rounded, 'Calls', false, isIdentity),
          if (!isFloating && !isIdentity)
            _navItem(colors, accent, Icons.tune_rounded, 'More', false, false),
          if (isIdentity)
            _navItem(colors, accent, Icons.groups_rounded, 'Spaces', false, true),
        ],
      ),
    );
  }

  Widget _navItem(
    AppColors colors,
    Color accent,
    IconData icon,
    String label,
    bool active,
    bool identity,
  ) {
    final foreground = active ? accent : colors.foregroundSecondary;
    return Container(
      padding: EdgeInsets.symmetric(horizontal: identity ? 8 : 5, vertical: 2),
      decoration: BoxDecoration(
        color: active && template.navigation.layout == NavigationLayoutType.floatingPill
            ? accent.withValues(alpha: .12)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: foreground),
          if (identity) ...[
            const SizedBox(width: 3),
            Text(label, style: TextStyle(fontSize: 7, color: foreground, fontWeight: FontWeight.w700)),
          ],
        ],
      ),
    );
  }

  Widget _mediaTile(
    AppColors colors,
    Color accent,
    IconData icon,
    String label, {
    bool tall = false,
  }) {
    return Container(
      padding: const EdgeInsets.all(6),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [accent.withValues(alpha: .25), colors.surface],
        ),
        borderRadius: BorderRadius.circular(tall ? 12 : 9),
        border: Border.all(color: accent.withValues(alpha: .25)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: tall ? 17 : 13, color: accent),
          const Spacer(),
          Text(label, style: TextStyle(fontSize: tall ? 9 : 7,
              color: colors.foreground, fontWeight: FontWeight.w800)),
        ],
      ),
    );
  }

  Widget _chip(String label, Color accent, {bool selected = false}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
      decoration: BoxDecoration(
        color: selected ? accent.withValues(alpha: .16) : Colors.transparent,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: selected ? accent.withValues(alpha: .45) : Colors.transparent),
      ),
      child: Text(label, style: TextStyle(
        fontSize: 6.5, fontWeight: FontWeight.w800,
        color: selected ? accent : const Color(0xFF89929E),
      )),
    );
  }

  Widget _quickAction(AppColors colors, Color accent, IconData icon, String label) {
    return Row(
      children: [
        Container(
          width: 25,
          height: 25,
          decoration: BoxDecoration(
            color: accent.withValues(alpha: .12),
            borderRadius: BorderRadius.circular(7),
          ),
          child: Icon(icon, size: 13, color: accent),
        ),
        const SizedBox(width: 6),
        Text(label, style: TextStyle(fontSize: 8, color: colors.foreground,
            fontWeight: FontWeight.w700)),
      ],
    );
  }

  Widget _messageLine(AppColors colors, Color accent, String name, String snippet,
      String time, {bool group = false}) {
    return Row(
      children: [
        Container(
          width: 24,
          height: 24,
          decoration: BoxDecoration(
            color: accent.withValues(alpha: .13),
            shape: template.chatList.avatarShape == 'circle'
                ? BoxShape.circle
                : BoxShape.rectangle,
            borderRadius: template.chatList.avatarShape == 'circle'
                ? null
                : BorderRadius.circular(7),
          ),
          child: Icon(group ? Icons.groups_rounded : Icons.person_rounded,
              color: accent, size: 13),
        ),
        const SizedBox(width: 7),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  Expanded(child: Text(name, maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 8.5, color: colors.foreground, fontWeight: FontWeight.w800))),
                  Text(time, style: TextStyle(fontSize: 7, color: colors.foregroundSecondary)),
                ],
              ),
              const SizedBox(height: 2),
              Text(snippet, maxLines: 1, overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 7.5, color: colors.foregroundSecondary)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _miniDenseRow(AppColors colors, Color accent, String label) {
    return Row(
      children: [
        Container(
          width: 18,
          height: 18,
          decoration: BoxDecoration(color: accent.withValues(alpha: .12),
              borderRadius: BorderRadius.circular(5)),
          child: Icon(Icons.person_rounded, size: 10, color: accent),
        ),
        const SizedBox(width: 5),
        Expanded(child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, maxLines: 1, overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 7.5, color: colors.foreground, fontWeight: FontWeight.w700)),
            const SizedBox(height: 3),
            Container(height: 3, width: 58, decoration: BoxDecoration(
                color: colors.foregroundSecondary.withValues(alpha: .38),
                borderRadius: BorderRadius.circular(2))),
          ],
        )),
        Icon(Icons.push_pin_rounded, size: 9, color: colors.foregroundSecondary),
      ],
    );
  }

  Widget _spaceCard(AppColors colors, Color accent, IconData icon, String title, String badge) {
    return Container(
      padding: const EdgeInsets.all(6),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(9),
        border: Border.all(color: colors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(width: 22, height: 22,
            decoration: BoxDecoration(color: accent.withValues(alpha: .14),
              borderRadius: BorderRadius.circular(6)),
            child: Icon(icon, color: accent, size: 13)),
          const Spacer(),
          Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 8, fontWeight: FontWeight.w800, color: colors.foreground)),
          const SizedBox(height: 2),
          Text(badge, style: TextStyle(fontSize: 6.5, color: accent, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}
