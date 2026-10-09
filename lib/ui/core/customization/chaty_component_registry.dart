import 'chaty_component_spec.dart';
import 'component_capabilities.dart';
import 'performance_profile.dart';

/// Single source of truth registry for all component variants in Chaty.
class ChatyComponentRegistry {
  ChatyComponentRegistry._() {
    _registerDefaultCatalog();
  }

  static final ChatyComponentRegistry instance = ChatyComponentRegistry._();

  final Map<ComponentId, Map<String, ComponentSpec>> _specs = {};
  final Map<ComponentId, String> _defaultVariants = {};

  void register(ComponentSpec spec, {bool isDefault = false}) {
    _specs.putIfAbsent(spec.componentId, () => {})[spec.variantId] = spec;
    if (isDefault || !_defaultVariants.containsKey(spec.componentId)) {
      _defaultVariants[spec.componentId] = spec.variantId;
    }
  }

  ComponentSpec? getSpec(ComponentId componentId, String variantId) {
    return _specs[componentId]?[variantId];
  }

  String getDefaultVariantId(ComponentId componentId) {
    return _defaultVariants[componentId] ?? '';
  }

  List<ComponentSpec> getVariantsFor(ComponentId componentId) {
    final map = _specs[componentId];
    if (map == null) return const [];
    return List.unmodifiable(map.values);
  }

  ComponentSpec nearestCompatible(
    ComponentId componentId,
    String requestedVariantId,
    VariantContext context,
  ) {
    final spec = getSpec(componentId, requestedVariantId);
    if (spec != null && spec.supports(context)) {
      return spec;
    }

    // Try finding another supported variant in the same component family
    final candidates = getVariantsFor(componentId);
    for (final candidate in candidates) {
      if (candidate.supports(context)) {
        return candidate;
      }
    }

    // If candidate isn't found, return the default spec or a fallback spec
    final defaultId = getDefaultVariantId(componentId);
    final defaultSpec = getSpec(componentId, defaultId);
    if (defaultSpec != null) {
      return defaultSpec;
    }

    return ComponentSpec(
      componentId: componentId,
      variantId: requestedVariantId,
      displayName: requestedVariantId,
    );
  }

  void _registerDefaultCatalog() {
    // -------------------------------------------------------------------------
    // NAVIGATION VARIANTS (NAV-01 .. NAV-10 per components-prd.md)
    // -------------------------------------------------------------------------
    register(
      const ComponentSpec(
        componentId: ComponentId.navigation,
        variantId: 'classic_label_bar',
        displayName: 'Classic Label Bar',
        description: 'Familiar icon + persistent label row with native platform spacing.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
      isDefault: true,
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.navigation,
        variantId: 'floating_dock',
        displayName: 'Floating Dock',
        description: 'Elevated compact dock with subtle elevation and border.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.navigation,
        variantId: 'capsule_rail',
        displayName: 'Capsule Rail',
        description: 'Active destination in an animated pill capsule.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.navigation,
        variantId: 'split_segments',
        displayName: 'Split Segments',
        description: 'Productivity segmented layout with distinct active background.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.navigation,
        variantId: 'glass_dock',
        displayName: 'Glass Dock',
        description: 'Frosted translucent backdrop surface with bounded blur.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.medium,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.navigation,
        variantId: 'compact_icon_dock',
        displayName: 'Compact Icon Dock',
        description: 'Icon-first layout with accessible tooltip on long-press.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.navigation,
        variantId: 'orbit',
        displayName: 'Orbit',
        description: 'Subtle radial active indicator around the selected icon.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.navigation,
        variantId: 'elevated_pill',
        displayName: 'Elevated Pill',
        description: 'Single continuous pill container with sliding active indicator.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.navigation,
        variantId: 'line_marker',
        displayName: 'Line Marker',
        description: 'Minimalist editorial underline / side line indicator.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.navigation,
        variantId: 'adaptive_rail',
        displayName: 'Adaptive Rail',
        description: 'Automatically shifts between compact bottom dock and side rail on wide screens.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );

    // -------------------------------------------------------------------------
    // HEADER VARIANTS (HDR-01 .. HDR-08 per components-prd.md)
    // -------------------------------------------------------------------------
    register(
      const ComponentSpec(
        componentId: ComponentId.header,
        variantId: 'standard_identity',
        displayName: 'Standard Identity',
        description: 'Leading back/avatar, title, subtitle, and contextual actions.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
      isDefault: true,
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.header,
        variantId: 'large_context',
        displayName: 'Large Context',
        description: 'Prominent identity cluster that gracefully collapses on scroll.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.header,
        variantId: 'compact',
        displayName: 'Compact Header',
        description: 'Dense toolbar maximizing message reading area.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.header,
        variantId: 'search_first',
        displayName: 'Search First',
        description: 'Inline search field integrated directly into the header.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.header,
        variantId: 'glass_strip',
        displayName: 'Glass Strip',
        description: 'Translucent floating header strip with bounded blur.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.medium,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.header,
        variantId: 'profile_led',
        displayName: 'Profile Led',
        description: 'Prominent avatar header with status beneath.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.header,
        variantId: 'selection_header',
        displayName: 'Selection Header',
        description: 'Multi-select header showing count, pin, forward, delete, star.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.header,
        variantId: 'call_context',
        displayName: 'Call Context',
        description: 'Call state banner with live elapsed timer and return-to-call action.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );

    // -------------------------------------------------------------------------
    // COMPOSER VARIANTS (CMP-01 .. CMP-08 per components-prd.md)
    // -------------------------------------------------------------------------
    register(
      const ComponentSpec(
        componentId: ComponentId.composer,
        variantId: 'chaty_capsule',
        displayName: 'Chaty Capsule',
        description: 'Balanced attachment, emoji, text field, mic/send capsule.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
      isDefault: true,
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.composer,
        variantId: 'telegram_dense',
        displayName: 'Telegram Dense',
        description: 'High density action spacing with attachment shortcut first.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.composer,
        variantId: 'imessage_expressive',
        displayName: 'iMessage Expressive',
        description: 'Expandable app rail with smooth action drawer.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.composer,
        variantId: 'messenger_action_row',
        displayName: 'Messenger Action Row',
        description: 'Prominent bottom action row around a rounded text field.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.composer,
        variantId: 'signal_quiet',
        displayName: 'Signal Quiet',
        description: 'Minimal privacy-focused input with essential controls.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.composer,
        variantId: 'instagram_media_first',
        displayName: 'Instagram Media First',
        description: 'Quick camera, gallery, voice and sticker actions.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.composer,
        variantId: 'lens_first',
        displayName: 'Lens First',
        description: 'Prominent camera and AR effect shortcut.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.composer,
        variantId: 'command_composer',
        displayName: 'Command Composer',
        description: 'Slash commands, tasks, and markdown shortcuts.',
        capabilities: ComponentCapabilities.universal,
        performanceCost: PerformanceCost.low,
      ),
    );

    // -------------------------------------------------------------------------
    // BUBBLE FAMILIES (BUB-01 .. BUB-10)
    // -------------------------------------------------------------------------
    register(
      const ComponentSpec(
        componentId: ComponentId.bubble,
        variantId: 'stock',
        displayName: 'Classic WhatsApp',
        capabilities: ComponentCapabilities.universal,
      ),
      isDefault: true,
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.bubble,
        variantId: 'ios_like',
        displayName: 'iOS Smooth',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.bubble,
        variantId: 'modern',
        displayName: 'Modern Rounded',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.bubble,
        variantId: 'minimal',
        displayName: 'Minimal Hairline',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.bubble,
        variantId: 'angular_tail',
        displayName: 'Angular Tail',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.bubble,
        variantId: 'threaded',
        displayName: 'Threaded Spine',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.bubble,
        variantId: 'fold',
        displayName: 'Fold Asymmetric',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.bubble,
        variantId: 'soft_block',
        displayName: 'Soft Block',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.bubble,
        variantId: 'editorial',
        displayName: 'Editorial Text First',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.bubble,
        variantId: 'pixel_grid',
        displayName: 'Pixel Grid',
        capabilities: ComponentCapabilities.universal,
      ),
    );

    // -------------------------------------------------------------------------
    // DELIVERY TICKS (TICK-01 .. TICK-10)
    // -------------------------------------------------------------------------
    register(
      const ComponentSpec(
        componentId: ComponentId.tick,
        variantId: 'rc_ios_11',
        displayName: 'RC iOS 11',
        capabilities: ComponentCapabilities.universal,
      ),
      isDefault: true,
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.tick,
        variantId: 'green_tick',
        displayName: 'Green Tick Classic',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.tick,
        variantId: 'bw_ticks',
        displayName: 'B.W Ticks Minimal',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.tick,
        variantId: 'circle_print',
        displayName: 'Circle Print',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.tick,
        variantId: 'split_arc',
        displayName: 'Split Arc',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.tick,
        variantId: 'dot_pair',
        displayName: 'Dot Pair',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.tick,
        variantId: 'diamond',
        displayName: 'Diamond Status',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.tick,
        variantId: 'pulse_ring',
        displayName: 'Pulse Ring',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.tick,
        variantId: 'bar',
        displayName: 'Linear Bar',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.tick,
        variantId: 'glyph',
        displayName: 'Micro Glyph',
        capabilities: ComponentCapabilities.universal,
      ),
    );

    // -------------------------------------------------------------------------
    // OVERLAYS & MODALS (OVL-01 .. OVL-10)
    // -------------------------------------------------------------------------
    register(
      const ComponentSpec(
        componentId: ComponentId.modal,
        variantId: 'center_dialog',
        displayName: 'Center Dialog (10px Radius)',
        capabilities: ComponentCapabilities.universal,
      ),
      isDefault: true,
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.modal,
        variantId: 'sheet',
        displayName: 'Adaptive Bottom Sheet',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.modal,
        variantId: 'side_sheet',
        displayName: 'Side Sheet (Expanded Layouts)',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.modal,
        variantId: 'action_sheet',
        displayName: 'Action Sheet',
        capabilities: ComponentCapabilities.universal,
      ),
    );
    register(
      const ComponentSpec(
        componentId: ComponentId.modal,
        variantId: 'context_menu',
        displayName: 'Anchored Context Menu',
        capabilities: ComponentCapabilities.universal,
      ),
    );
  }
}
