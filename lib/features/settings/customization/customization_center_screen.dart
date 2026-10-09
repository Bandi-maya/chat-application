import 'package:flutter/material.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/customization/customization.dart';
import '../../../ui/core/design_system/tokens/app_tokens.dart';
import '../../../ui/core/design_system/components/app_components.dart';
import '../../../ui/core/design_system/components/chaty_kit.dart';
import '../../../ui/core/components/navigation/chaty_navigation_renderer.dart';
import '../../../ui/core/components/headers/chaty_header_renderer.dart';
import '../../../ui/core/components/composer/chaty_composer_renderer.dart';
import '../../../ui/core/components/overlays/chaty_overlay_renderer.dart';
import '../../../ui/core/design_system/chaty_haptics.dart';
import '../../../ui/core/theme/app_theme.dart';

class CustomizationCenterScreen extends StatefulWidget {
  final String? conversationId;
  final String? conversationTitle;

  const CustomizationCenterScreen({
    super.key,
    this.conversationId,
    this.conversationTitle,
  });

  @override
  State<CustomizationCenterScreen> createState() => _CustomizationCenterScreenState();
}

class _CustomizationCenterScreenState extends State<CustomizationCenterScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController;
  final TextEditingController _dummyComposerText = TextEditingController(text: 'Hey! Check out this new skin ✨');
  final FocusNode _dummyFocus = FocusNode();

  final List<String> _categories = [
    'Navigation',
    'Header',
    'Bubbles',
    'Ticks',
    'Composer',
    'Overlays',
    'Motion & Density',
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _categories.length, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _dummyComposerText.dispose();
    _dummyFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final customCtrl = locator<CustomizationController>();
    final themeCtrl = locator<ThemeController>();
    final colors = context.colors;

    return ListenableBuilder(
      listenable: Listenable.merge([customCtrl, themeCtrl]),
      builder: (context, _) {
        final snapshot = customCtrl.effectiveSnapshot;
        final isDirty = customCtrl.isDirty;

        return Scaffold(
          backgroundColor: colors.background,
          appBar: ChatyAppBar(
            title: widget.conversationId != null
                ? 'Customize (${widget.conversationTitle ?? 'Chat'})'
                : 'Customization Center',
            subtitle: widget.conversationId != null
                ? 'Per-conversation visual overrides'
                : 'Deep visual dialect customizer',
            actions: [
              if (customCtrl.canUndo)
                IconButton(
                  icon: const Icon(Icons.undo_rounded),
                  tooltip: 'Undo last change',
                  onPressed: () async {
                    ChatyMotion.light();
                    await customCtrl.undo();
                    if (mounted) {
                      ChatyToast.show(context, 'Reverted previous customization');
                    }
                  },
                ),
              PopupMenuButton<String>(
                icon: const Icon(Icons.more_vert_rounded),
                onSelected: (choice) => _handleMenuChoice(choice, customCtrl),
                itemBuilder: (ctx) => [
                  const PopupMenuItem(
                    value: 'reset_current',
                    child: Text('Reset current section'),
                  ),
                  const PopupMenuItem(
                    value: 'reset_all',
                    child: Text('Reset all customizations'),
                  ),
                ],
              ),
            ],
            bottom: TabBar(
              controller: _tabController,
              isScrollable: true,
              labelColor: colors.primary,
              unselectedLabelColor: colors.foregroundSecondary,
              indicatorColor: colors.primary,
              indicatorWeight: 3.0,
              tabs: _categories.map((c) => Tab(text: c)).toList(),
            ),
          ),
          body: Column(
            children: [
              // ---------------------------------------------------------------
              // LIVE INTERACTIVE PREVIEW CANVAS
              // ---------------------------------------------------------------
              _buildLivePreviewCard(context, snapshot),

              // ---------------------------------------------------------------
              // VARIANT SELECTION TABS
              // ---------------------------------------------------------------
              Expanded(
                child: TabBarView(
                  controller: _tabController,
                  children: [
                    _buildVariantGrid(ComponentId.navigation, snapshot, customCtrl),
                    _buildVariantGrid(ComponentId.header, snapshot, customCtrl),
                    _buildVariantGrid(ComponentId.bubble, snapshot, customCtrl),
                    _buildVariantGrid(ComponentId.tick, snapshot, customCtrl),
                    _buildVariantGrid(ComponentId.composer, snapshot, customCtrl),
                    _buildVariantGrid(ComponentId.modal, snapshot, customCtrl),
                    _buildMotionDensitySettings(snapshot, customCtrl),
                  ],
                ),
              ),

              // ---------------------------------------------------------------
              // DIRTY ACTIONS BAR (Apply / Discard)
              // ---------------------------------------------------------------
              if (isDirty) _buildActionBar(context, customCtrl),
            ],
          ),
        );
      },
    );
  }

  Widget _buildLivePreviewCard(BuildContext context, CustomizationSnapshot snapshot) {
    final colors = context.colors;

    return Container(
      margin: const EdgeInsets.fromLTRB(14, 10, 14, 8),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: colors.border, width: 1.0),
        boxShadow: [
          BoxShadow(
            color: colors.shadow.withValues(alpha: 0.06),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(18),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Preview Header
            ChatyHeaderRenderer(
              variantId: snapshot.headerVariant,
              data: ChatyHeaderData(
                title: widget.conversationTitle ?? 'Alex Morgan',
                subtitle: 'online • end-to-end encrypted',
                avatar: const ChatyAvatarCore(
                  initials: 'AM',
                  color: Color(0xFF3B82F6),
                  size: 36,
                ),
                actions: [
                  IconButton(
                    icon: const Icon(Icons.call_outlined, size: 20),
                    onPressed: () {},
                  ),
                  IconButton(
                    icon: const Icon(Icons.videocam_outlined, size: 22),
                    onPressed: () {},
                  ),
                ],
              ),
            ),

            // Preview Chat Area with simulated message bubbles
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              color: colors.surfaceSecondary.withValues(alpha: 0.4),
              child: Column(
                children: [
                  // Incoming message bubble
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Container(
                      margin: const EdgeInsets.only(bottom: 6),
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      decoration: BoxDecoration(
                        color: colors.surface,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: colors.border, width: 0.6),
                      ),
                      child: Text(
                        'Previewing: ${snapshot.bubbleVariant} + ${snapshot.tickVariant}',
                        style: TextStyle(fontSize: 13, color: colors.foreground),
                      ),
                    ),
                  ),

                  // Outgoing message bubble
                  Align(
                    alignment: Alignment.centerRight,
                    child: Container(
                      margin: const EdgeInsets.only(bottom: 6),
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      decoration: BoxDecoration(
                        color: colors.primary.withValues(alpha: 0.18),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: colors.primary.withValues(alpha: 0.4), width: 0.6),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Flexible(
                            child: Text(
                              'Styling changes update here in real time!',
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: colors.primary,
                              ),
                            ),
                          ),
                          const SizedBox(width: 6),
                          Icon(Icons.done_all_rounded, size: 16, color: colors.primary),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // Preview Composer
            ChatyComposerRenderer(
              variantId: snapshot.composerVariant,
              data: ChatyComposerData(
                textController: _dummyComposerText,
                focusNode: _dummyFocus,
                hasText: true,
                onSend: () {},
                onMicPress: () {},
                onAttach: () {},
                onCamera: () {},
                onEmojiToggle: () {},
              ),
            ),

            // Preview Navigation Bar
            ChatyNavigationRenderer(
              variantId: snapshot.navigationVariant,
              items: [
                ChatyNavItemData(
                  id: 'chats',
                  label: 'Chats',
                  icon: Icons.chat_bubble_outline_rounded,
                  activeIcon: Icons.chat_bubble_rounded,
                  badgeCount: 2,
                  isSelected: true,
                  onTap: () {},
                ),
                ChatyNavItemData(
                  id: 'updates',
                  label: 'Updates',
                  icon: Icons.update_outlined,
                  activeIcon: Icons.update_rounded,
                  isSelected: false,
                  onTap: () {},
                ),
                ChatyNavItemData(
                  id: 'calls',
                  label: 'Calls',
                  icon: Icons.call_outlined,
                  activeIcon: Icons.call_rounded,
                  isSelected: false,
                  onTap: () {},
                ),
                ChatyNavItemData(
                  id: 'settings',
                  label: 'Settings',
                  icon: Icons.settings_outlined,
                  activeIcon: Icons.settings_rounded,
                  isSelected: false,
                  onTap: () {},
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildVariantGrid(
    ComponentId componentId,
    CustomizationSnapshot snapshot,
    CustomizationController controller,
  ) {
    final variants = ChatyComponentRegistry.instance.getVariantsFor(componentId);
    final activeId = _getActiveId(componentId, snapshot);
    final colors = context.colors;

    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      itemCount: variants.length,
      separatorBuilder: (_, __) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final spec = variants[index];
        final isSelected = spec.variantId == activeId;

        return InkWell(
          onTap: () {
            ChatyMotion.selection();
            controller.setVariant(componentId, spec.variantId);
          },
          borderRadius: BorderRadius.circular(14),
          child: AnimatedContainer(
            duration: ChatyMotion.fast,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: isSelected
                  ? colors.primary.withValues(alpha: 0.12)
                  : colors.surface,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: isSelected ? colors.primary : colors.border,
                width: isSelected ? 1.8 : 1.0,
              ),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        spec.displayName,
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight:
                              isSelected ? FontWeight.w800 : FontWeight.w600,
                          color: isSelected ? colors.primary : colors.foreground,
                        ),
                      ),
                      if (spec.description.isNotEmpty) ...[
                        const SizedBox(height: 2),
                        Text(
                          spec.description,
                          style: TextStyle(
                            fontSize: 12,
                            color: colors.foregroundSecondary,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                if (isSelected)
                  Icon(Icons.check_circle_rounded, color: colors.primary, size: 22)
                else
                  Icon(Icons.radio_button_off_rounded, color: colors.border, size: 22),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildMotionDensitySettings(
    CustomizationSnapshot snapshot,
    CustomizationController controller,
  ) {
    final colors = context.colors;
    final motionProfiles = ['Subtle', 'Standard', 'Expressive', 'Reduced'];
    final densityProfiles = ['Compact', 'Comfortable', 'Spacious'];

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        ChatyGroupedSection(
          title: 'Motion Profile',
          description: 'Physics and duration profiles for screen transitions and micro-interactions.',
          children: motionProfiles.map((mp) {
            final isSelected = snapshot.motionProfile.toLowerCase() == mp.toLowerCase();
            return ListTile(
              title: Text(mp, style: TextStyle(fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500)),
              trailing: isSelected ? Icon(Icons.check_rounded, color: colors.primary) : null,
              onTap: () {
                ChatyMotion.selection();
                controller.stage(snapshot.copyWith(motionProfile: mp.toLowerCase()));
              },
            );
          }).toList(),
        ),
        const SizedBox(height: 12),
        ChatyGroupedSection(
          title: 'Density Profile',
          description: 'Information density across conversation lists and item rows.',
          children: densityProfiles.map((dp) {
            final isSelected = snapshot.densityProfile.toLowerCase() == dp.toLowerCase();
            return ListTile(
              title: Text(dp, style: TextStyle(fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500)),
              trailing: isSelected ? Icon(Icons.check_rounded, color: colors.primary) : null,
              onTap: () {
                ChatyMotion.selection();
                controller.stage(snapshot.copyWith(densityProfile: dp.toLowerCase()));
              },
            );
          }).toList(),
        ),
      ],
    );
  }

  Widget _buildActionBar(BuildContext context, CustomizationController controller) {
    final colors = context.colors;

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border(top: BorderSide(color: colors.border, width: 1.0)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            Expanded(
              child: ChatySecondaryButton(
                text: 'Discard',
                onPressed: () {
                  ChatyMotion.light();
                  controller.cancelPreview();
                },
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: ChatyPrimaryButton(
                text: 'Apply Changes',
                onPressed: () async {
                  ChatyHaptics.success();
                  if (widget.conversationId != null) {
                    // Apply conversation override
                    await controller.setConversationOverride(
                      conversationId: widget.conversationId!,
                      componentId: ComponentId.bubble,
                      variantId: controller.effectiveSnapshot.bubbleVariant,
                    );
                    await controller.commit(description: 'Applied conversation overrides');
                  } else {
                    await controller.commit(description: 'Applied global visual customizations');
                  }
                  if (mounted) {
                    ChatyToast.show(context, 'Customization applied successfully!');
                  }
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _getActiveId(ComponentId id, CustomizationSnapshot snapshot) {
    switch (id) {
      case ComponentId.navigation:
        return snapshot.navigationVariant;
      case ComponentId.header:
        return snapshot.headerVariant;
      case ComponentId.composer:
        return snapshot.composerVariant;
      case ComponentId.bubble:
        return snapshot.bubbleVariant;
      case ComponentId.tick:
        return snapshot.tickVariant;
      case ComponentId.modal:
        return snapshot.popupVariant;
      default:
        return '';
    }
  }

  Future<void> _handleMenuChoice(String choice, CustomizationController controller) async {
    if (choice == 'reset_current') {
      final index = _tabController.index;
      ComponentId? compId;
      if (index == 0) compId = ComponentId.navigation;
      if (index == 1) compId = ComponentId.header;
      if (index == 2) compId = ComponentId.bubble;
      if (index == 3) compId = ComponentId.tick;
      if (index == 4) compId = ComponentId.composer;
      if (index == 5) compId = ComponentId.modal;

      if (compId != null) {
        final def = ChatyComponentRegistry.instance.getDefaultVariantId(compId);
        await controller.setVariant(compId, def, immediate: true);
        if (mounted) ChatyToast.show(context, 'Reset section to default');
      }
    } else if (choice == 'reset_all') {
      final confirmed = await ChatyOverlayRenderer.showConfirmDialog(
        context,
        title: 'Reset Customizations',
        message: 'Are you sure you want to revert all custom visual variants to default?',
        confirmLabel: 'Reset All',
        destructive: true,
      );
      if (confirmed) {
        await controller.resetAll();
        if (mounted) ChatyToast.show(context, 'All customizations reset to defaults');
      }
    }
  }
}
